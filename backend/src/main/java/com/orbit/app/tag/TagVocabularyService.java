package com.orbit.app.tag;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.orbit.app.embedding.EmbeddingKind;
import com.orbit.app.embedding.EmbeddingService;
import com.orbit.app.embedding.VectorLiteral;
import com.orbit.app.llm.LlmSummaryService;
import com.orbit.app.llm.dto.TopicPair;
import com.orbit.app.util.SlugUtils;
import com.orbit.domain.entity.Tag;
import com.orbit.domain.repository.TagRepository;

/**
 * Keeps the tag vocabulary from splitting one topic into several tags.
 * <p>
 * Tags used to be minted by slug alone, so a translation or a spelling variant
 * of an existing tag became a new tag ("Innovationsmanagement" next to
 * "Innovation Management"), and request distribution — which matches on tag
 * overlap — rarely reached a second profile. Before a label is minted it is
 * mapped onto an existing tag, in two steps:
 * <ol>
 *   <li><b>Spelling key</b> ({@link #canonicalKey}): case, umlauts and ß, a trailing
 *       acronym in brackets, hyphens and a plural ending are ignored. Database
 *       only, so every tag-writing path uses it ({@link #existingLabel}).</li>
 *   <li><b>Meaning</b> ({@link #canonicalLabels}, enrichment only): the nearest
 *       existing tags by embedding, at least {@code orbit.tags.canonical-similarity},
 *       are offered to the LLM, which accepts a pair only when both labels name
 *       the same topic (a translation, a variant). Embedding similarity alone is
 *       not enough: "Genomik" and "Genetik" (0.76 with voyage-4-lite) are as close
 *       as many translations.</li>
 * </ol>
 * A label is only ever mapped onto one existing tag, never merged transitively,
 * and existing tags are never merged. When the embedding or the LLM is
 * unavailable the label is minted as before.
 * <p>
 * Tag vectors are stored in {@code tags.embedding} (V21) with the name of the
 * model that produced them. Tags without a vector, or with another model's,
 * are embedded in the background on start-up and after each enrichment
 * ({@link #embedMissingAsync}).
 */
@Service
public class TagVocabularyService {

    private static final Logger log = LoggerFactory.getLogger(TagVocabularyService.class);

    /** Existing tags offered to the LLM per new label. */
    static final int CANDIDATES_PER_LABEL = 2;

    private static final String LABELS_SQL = "SELECT label FROM tags";

    private static final String NEAREST_SQL = """
            WITH q AS MATERIALIZED (SELECT CAST(? AS vector) AS v)
            SELECT t.label, -(t.embedding <#> q.v) AS similarity
            FROM tags t, q
            WHERE t.embedding IS NOT NULL AND t.embedding_model = ?
            ORDER BY t.embedding <#> q.v, t.id
            LIMIT ?
            """;

    private static final String UNEMBEDDED_SQL = """
            SELECT id, label FROM tags
            WHERE embedding IS NULL OR embedding_model IS DISTINCT FROM ?
            ORDER BY id
            LIMIT 200
            """;

    private static final String UPDATE_SQL = """
            UPDATE tags SET embedding = CAST(? AS vector), embedding_model = ?, embedded_at = now()
            WHERE id = ?
            """;

    private final TagRepository tags;
    private final JdbcTemplate jdbc;
    private final EmbeddingService embeddingService;
    private final LlmSummaryService llm;
    private final double similarityFloor;
    private final boolean syncOnStartup;
    private final Executor executor;
    private final AtomicBoolean embedQueued = new AtomicBoolean();

    @Autowired
    public TagVocabularyService(TagRepository tags,
                                JdbcTemplate jdbc,
                                EmbeddingService embeddingService,
                                LlmSummaryService llm,
                                @Value("${orbit.tags.canonical-similarity}") double similarityFloor,
                                @Value("${orbit.tags.sync-on-startup:true}") boolean syncOnStartup) {
        this(tags, jdbc, embeddingService, llm, similarityFloor, syncOnStartup, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tag-vocabulary");
            thread.setDaemon(true);
            return thread;
        }));
    }

    TagVocabularyService(TagRepository tags, JdbcTemplate jdbc, EmbeddingService embeddingService, LlmSummaryService llm,
                         double similarityFloor, boolean syncOnStartup, Executor executor) {
        this.tags = tags;
        this.jdbc = jdbc;
        this.embeddingService = embeddingService;
        this.llm = llm;
        this.similarityFloor = similarityFloor;
        this.syncOnStartup = syncOnStartup;
        this.executor = executor;
    }

    // ─── spelling key ───

    /**
     * The spelling-insensitive key of a label: lower case, umlauts and ß folded,
     * other accents dropped, a trailing bracket ("(UX)", "(LCA)") removed,
     * hyphens/slashes as spaces, and a plural ending of the last word stripped.
     * "Life Cycle Assessments (LCA)" and "life-cycle assessment" share a key.
     */
    public static String canonicalKey(String label) {
        if (label == null) return "";
        String key = label.trim().toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        key = Normalizer.normalize(key, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
        key = key.replaceAll("\\s*\\([^)]*\\)\\s*$", "")
                .replaceAll("[-_/]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        // Plural of the last word: Assessments → Assessment, Zwillinge → Zwilling.
        return key.replaceAll("(?<=\\p{L}{4})(es|s|en|e|n)$", "");
    }

    /**
     * The existing tag label with the same spelling key as {@code label}, if any.
     * Database only — safe inside a transaction.
     */
    public Optional<String> existingLabel(String label) {
        String key = canonicalKey(label);
        if (key.isEmpty()) return Optional.empty();
        return keyIndex().getOrDefault(key, List.of()).stream().findFirst();
    }

    /**
     * The tag for {@code label}: the existing tag with the same spelling key or
     * slug, or a newly minted one (embedded later by {@link #embedMissingAsync}).
     * The one place tags are created — profile tags, subscriptions, request tags
     * and derived tags all go through it, so they share one vocabulary.
     * Database only; runs in the caller's transaction.
     *
     * @return the tag, or empty for a label without any letter or digit
     */
    public Optional<Tag> findOrMint(String label) {
        if (label == null || label.isBlank()) return Optional.empty();
        String trimmed = label.trim();
        String canonical = existingLabel(trimmed).orElse(trimmed);
        String slug = SlugUtils.slugify(canonical);
        if (slug.isEmpty()) return Optional.empty();
        return Optional.of(tags.findBySlug(slug).orElseGet(() -> tags.save(new Tag(slug, canonical))));
    }

    // ─── meaning (enrichment) ───

    /**
     * Map freshly derived labels onto the existing vocabulary: each label is
     * returned as is, or replaced by the label of the existing tag it names.
     * Calls the embedding service and the LLM, so never call it inside a
     * transaction. Order is kept; duplicates after mapping are dropped.
     */
    public List<String> canonicalLabels(List<String> labels) {
        if (labels == null || labels.isEmpty()) return List.of();
        Map<String, List<String>> byKey = keyIndex();

        Map<String, String> mapped = new LinkedHashMap<>();
        List<String> unmatched = new ArrayList<>();
        for (String label : labels) {
            if (label == null || label.isBlank() || mapped.containsKey(label)) continue;
            List<String> sameKey = byKey.getOrDefault(canonicalKey(label), List.of());
            if (!sameKey.isEmpty()) {
                mapped.put(label, sameKey.get(0));
            } else {
                mapped.put(label, label);
                unmatched.add(label);
            }
        }

        if (!unmatched.isEmpty()) {
            try {
                mapByMeaning(unmatched, mapped);
            } catch (Exception e) {
                log.warn("Tag canonicalisation by meaning skipped ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            }
        }

        List<String> result = new ArrayList<>();
        for (String label : mapped.values()) {
            if (!result.contains(label)) result.add(label);
        }
        return result;
    }

    private void mapByMeaning(List<String> unmatched, Map<String, String> mapped) {
        List<float[]> vectors = embeddingService.embedAll(unmatched, EmbeddingKind.QUERY);
        List<TopicPair> pairs = new ArrayList<>();
        Map<Integer, String> pairLabel = new HashMap<>();
        for (int i = 0; i < unmatched.size(); i++) {
            String label = unmatched.get(i);
            List<String> nearest = jdbc.query(NEAREST_SQL,
                    (rs, row) -> rs.getDouble("similarity") >= similarityFloor ? rs.getString("label") : null,
                    VectorLiteral.of(vectors.get(i)), embeddingService.modelName(), CANDIDATES_PER_LABEL);
            for (String candidate : nearest) {
                if (candidate == null || candidate.equals(label)) continue;
                pairLabel.put(pairs.size(), label);
                pairs.add(new TopicPair(label, candidate));
            }
        }
        if (pairs.isEmpty()) return;

        List<Boolean> same = llm.sameTopics(pairs);
        for (int i = 0; i < pairs.size(); i++) {
            String label = pairLabel.get(i);
            // First confirmed candidate wins; candidates come nearest first.
            if (Boolean.TRUE.equals(same.get(i)) && mapped.get(label).equals(label)) {
                mapped.put(label, pairs.get(i).existing());
                log.info("Tag \"{}\" mapped onto existing tag \"{}\"", label, pairs.get(i).existing());
            }
        }
    }

    // ─── tag vectors ───

    /** Embed every tag that has no vector (or another model's) on start-up, in the background. */
    @EventListener(ApplicationReadyEvent.class)
    public void embedOnStartup() {
        if (syncOnStartup) embedMissingAsync();
    }

    /** Embed new tags in the background; coalesces calls that arrive while one is queued. */
    public void embedMissingAsync() {
        if (!embedQueued.compareAndSet(false, true)) return;
        executor.execute(() -> {
            embedQueued.set(false);
            try {
                embedMissing();
            } catch (Exception e) {
                log.warn("Tag embedding failed: {}", e.getMessage());
            }
        });
    }

    /** @return the number of tags embedded */
    int embedMissing() {
        int embedded = 0;
        while (true) {
            List<Map<String, Object>> page = jdbc.queryForList(UNEMBEDDED_SQL, embeddingService.modelName());
            if (page.isEmpty()) break;
            List<String> labels = page.stream().map(row -> (String) row.get("label")).toList();
            List<float[]> vectors = embeddingService.embedAll(labels, EmbeddingKind.QUERY);
            for (int i = 0; i < page.size(); i++) {
                jdbc.update(UPDATE_SQL, VectorLiteral.of(vectors.get(i)), embeddingService.modelName(),
                        ((Number) page.get(i).get("id")).longValue());
            }
            embedded += page.size();
        }
        if (embedded > 0) log.info("Embedded {} tag(s)", embedded);
        return embedded;
    }

    /** Existing labels by spelling key, oldest tag first. */
    private Map<String, List<String>> keyIndex() {
        Map<String, List<String>> byKey = new HashMap<>();
        for (String label : jdbc.queryForList(LABELS_SQL + " ORDER BY id", String.class)) {
            byKey.computeIfAbsent(canonicalKey(label), k -> new ArrayList<>()).add(label);
        }
        return byKey;
    }
}
