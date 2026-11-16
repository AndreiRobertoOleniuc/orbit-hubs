package com.orbit.app.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.orbit.app.embedding.EmbeddingKind;
import com.orbit.app.embedding.EmbeddingService;
import com.orbit.app.embedding.VectorLiteral;
import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.repository.DiscoveryEntityRepository;

/**
 * Keeps each entity's profile document — name, headline, organisation, summary
 * and tags in one embedded, full-text-indexed row of {@code profile_documents}
 * — in step with the profile.
 * <p>
 * Source chunks carry no profile-level text, so without this document a broad
 * query ("Softwareentwicklung") or a name has nothing to match. The document is
 * a search index only: {@code hybrid_search} retrieves with it, but evidence and
 * citations come from sources alone.
 * <p>
 * Refreshes run after the surrounding transaction commits, on one background
 * thread, so a profile edit never waits for (or fails on) an embedding call. An
 * unchanged document is not re-embedded. A refresh that fails leaves the old
 * document in place; the next start-up sync repairs it.
 */
@Service
public class ProfileDocumentService {

    private static final Logger log = LoggerFactory.getLogger(ProfileDocumentService.class);

    /** A summary plus tags fits well within this, and within the 512 tokens the smallest supported model (e5) reads. */
    static final int MAX_CONTENT_CHARS = 2000;

    private static final String TAGS_SQL = """
            SELECT et.entity_id, t.label
            FROM entity_tags et
            JOIN tags t ON t.id = et.tag_id
            %s
            ORDER BY et.entity_id, t.label
            """;

    private static final String STORED_SQL = """
            SELECT entity_id, content, embedding_model, embedding IS NOT NULL AS embedded
            FROM profile_documents
            %s
            """;

    private static final String UPSERT_SQL = """
            INSERT INTO profile_documents (entity_id, content, embedding, embedding_model, embedded_at, updated_at)
            VALUES (?, ?, CAST(? AS vector), ?, now(), now())
            ON CONFLICT (entity_id) DO UPDATE SET
              content = EXCLUDED.content,
              embedding = EXCLUDED.embedding,
              embedding_model = EXCLUDED.embedding_model,
              embedded_at = EXCLUDED.embedded_at,
              updated_at = EXCLUDED.updated_at
            """;

    private final DiscoveryEntityRepository entities;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbc;
    private final boolean syncOnStartup;
    private final Executor executor;

    @Autowired
    public ProfileDocumentService(DiscoveryEntityRepository entities,
                                  EmbeddingService embeddingService,
                                  JdbcTemplate jdbc,
                                  @Value("${orbit.search.profile-documents.sync-on-startup:true}") boolean syncOnStartup) {
        this(entities, embeddingService, jdbc, syncOnStartup, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "profile-documents");
            thread.setDaemon(true);
            return thread;
        }));
    }

    /** {@code executor} runs refreshes; one thread keeps two refreshes of an entity from racing. */
    ProfileDocumentService(DiscoveryEntityRepository entities, EmbeddingService embeddingService,
                           JdbcTemplate jdbc, boolean syncOnStartup, Executor executor) {
        this.entities = entities;
        this.embeddingService = embeddingService;
        this.jdbc = jdbc;
        this.syncOnStartup = syncOnStartup;
        this.executor = executor;
    }

    /**
     * Refresh the entity's document once the current transaction has committed
     * (immediately, in the background, when there is none). Call after anything
     * that changes the name, headline, organisation, summary or tags.
     */
    public void refreshAfterCommit(Long entityId) {
        if (entityId == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(entityId);
                }
            });
        } else {
            submit(entityId);
        }
    }

    private void submit(Long entityId) {
        executor.execute(() -> {
            try {
                refresh(entityId);
            } catch (Exception e) {
                log.warn("Profile document refresh failed for entity #{}: {}", entityId, e.getMessage());
            }
        });
    }

    /**
     * Rebuild one entity's document and embed it if it changed.
     *
     * @return whether a new vector was stored
     */
    public boolean refresh(Long entityId) {
        DiscoveryEntity entity = entities.findById(entityId).orElse(null);
        if (entity == null) return false;   // deleted: the row went with it (ON DELETE CASCADE)
        String content = buildContent(entity, tagsByEntity("WHERE et.entity_id = ?", entityId)
                .getOrDefault(entityId, List.of()));
        return store(entityId, content, storedDocuments("WHERE entity_id = ?", entityId).get(entityId));
    }

    /** Bring every entity's document up to date in the background once the application is ready. */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        if (!syncOnStartup) return;
        Thread worker = new Thread(this::syncAll, "profile-documents-sync");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Embed every entity whose document is missing, changed, or embedded by
     * another model. A failure is logged and left for the next run.
     *
     * @return the number of documents embedded
     */
    public int syncAll() {
        Map<Long, StoredDocument> stored = storedDocuments("");
        Map<Long, List<String>> tags = tagsByEntity("");
        int embedded = 0;
        int failed = 0;
        for (DiscoveryEntity entity : entities.findAll()) {
            Long entityId = entity.getId();
            try {
                String content = buildContent(entity, tags.getOrDefault(entityId, List.of()));
                if (store(entityId, content, stored.get(entityId))) embedded++;
            } catch (Exception e) {
                failed++;
                if (failed == 1) {
                    log.warn("Profile document sync failed for entity #{}", entityId, e);
                } else {
                    log.warn("Profile document sync failed for entity #{}: {}", entityId, e.getMessage());
                }
            }
        }
        if (embedded > 0 || failed > 0) {
            log.info("Profile document sync: {} embedded, {} failed", embedded, failed);
        }
        return embedded;
    }

    /** Write {@code content} unless the stored document already holds it, embedded by the current model. */
    private boolean store(Long entityId, String content, StoredDocument existing) {
        if (existing != null && existing.current(content, embeddingService.modelName())) return false;
        if (content.isBlank()) {
            jdbc.update("DELETE FROM profile_documents WHERE entity_id = ?", entityId);
            return false;
        }
        String vector = VectorLiteral.of(embeddingService.embed(content, EmbeddingKind.PASSAGE));
        jdbc.update(UPSERT_SQL, entityId, content, vector, embeddingService.modelName());
        return true;
    }

    /**
     * The document text: one line each for name, headline, organisation, summary
     * and the tags, blanks left out, capped at {@link #MAX_CONTENT_CHARS}.
     */
    static String buildContent(DiscoveryEntity entity, List<String> tagLabels) {
        List<String> lines = new ArrayList<>();
        addIfPresent(lines, entity.getDisplayName());
        addIfPresent(lines, entity.getHeadline());
        addIfPresent(lines, entity.getOrgName());
        addIfPresent(lines, entity.getSummary());
        if (tagLabels != null && !tagLabels.isEmpty()) addIfPresent(lines, String.join(", ", tagLabels));
        String content = String.join("\n", lines);
        return content.length() > MAX_CONTENT_CHARS ? content.substring(0, MAX_CONTENT_CHARS) : content;
    }

    private static void addIfPresent(List<String> lines, String value) {
        if (value != null && !value.isBlank()) lines.add(value.trim().replaceAll("\\s+", " "));
    }

    private Map<Long, List<String>> tagsByEntity(String where, Object... args) {
        Map<Long, List<String>> tags = new HashMap<>();
        jdbc.query(TAGS_SQL.formatted(where), rs -> {
            tags.computeIfAbsent(rs.getLong("entity_id"), id -> new ArrayList<>()).add(rs.getString("label"));
        }, args);
        return tags;
    }

    private Map<Long, StoredDocument> storedDocuments(String where, Object... args) {
        Map<Long, StoredDocument> stored = new HashMap<>();
        jdbc.query(STORED_SQL.formatted(where), rs -> {
            stored.put(rs.getLong("entity_id"), new StoredDocument(
                    rs.getString("content"), rs.getString("embedding_model"), rs.getBoolean("embedded")));
        }, args);
        return stored;
    }

    /** What is stored for an entity: its text, the model that embedded it, whether a vector exists. */
    record StoredDocument(String content, String embeddingModel, boolean embedded) {
        /** Whether the stored vector still stands for {@code newContent} under the embedding {@code model} in use. */
        boolean current(String newContent, String model) {
            return embedded && model.equals(embeddingModel) && content.equals(newContent);
        }
    }
}
