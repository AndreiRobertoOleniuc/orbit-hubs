package com.orbit.app.ingestion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import com.orbit.app.ingestion.dto.SourceContent;
import com.orbit.app.llm.LlmSummaryService;
import com.orbit.app.search.ProfileDocumentService;
import com.orbit.app.tag.TagVocabularyService;
import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.entity.EntityTag;
import com.orbit.domain.entity.EntityTagId;
import com.orbit.domain.entity.Source;
import com.orbit.domain.entity.Tag;
import com.orbit.domain.repository.ChunkRepository;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.EntityTagRepository;
import com.orbit.domain.repository.SourceRepository;

/**
 * Post-ingestion enrichment: re-generates the entity's competence summary and
 * derives tags from its active sources after each ingestion.
 * <p>
 * This implements step 7 of the reference doc §4.2 ingestion algorithm:
 * <ul>
 *   <li>Gather all source content (from chunks) for the entity</li>
 *   <li>Call the LLM to generate a rich competence summary → update {@code entities.summary}</li>
 *   <li>Call the LLM to derive tags → upsert into {@code tags} + {@code entity_tags}</li>
 * </ul>
 * <p>
 * Derived tags use {@code origin='derived'} and are replaced on each re-harvest.
 * Manual tags ({@code origin='manual'}) are sticky — they are never removed by
 * re-derivation. User edits that promote a derived tag to manual are preserved.
 * <p>
 * The summary is re-generated from scratch each time, aggregating active sources,
 * so it gets richer as more sources are ingested.
 */
@Service
public class EntityEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(EntityEnrichmentService.class);

    private static final int MAX_TAGS = 10;
    private static final int MAX_CHUNKS_PER_SOURCE = 5;
    private static final int MAX_CHARS_PER_SOURCE = 1500;

    private final DiscoveryEntityRepository entities;
    private final SourceRepository sources;
    private final ChunkRepository chunks;
    private final TagVocabularyService tagVocabulary;
    private final EntityTagRepository entityTagRepo;
    private final LlmSummaryService llm;
    private final EntityLinkResolver linkResolver;
    private final ProfileDocumentService profileDocuments;
    private final TransactionOperations transactions;

    public EntityEnrichmentService(DiscoveryEntityRepository entities,
                                    SourceRepository sources,
                                    ChunkRepository chunks,
                                    TagVocabularyService tagVocabulary,
                                    EntityTagRepository entityTagRepo,
                                    LlmSummaryService llm,
                                    EntityLinkResolver linkResolver,
                                    ProfileDocumentService profileDocuments,
                                    TransactionOperations transactions) {
        this.entities = entities;
        this.sources = sources;
        this.chunks = chunks;
        this.tagVocabulary = tagVocabulary;
        this.entityTagRepo = entityTagRepo;
        this.llm = llm;
        this.linkResolver = linkResolver;
        this.profileDocuments = profileDocuments;
        this.transactions = transactions;
    }

    /**
     * Enrich an entity after its sources have been ingested: regenerate the
     * competence summary from all active sources and re-derive tags.
     * <p>
     * Called once all of an ingestion's sources have committed. Not
     * transactional itself: the LLM calls run first, outside any transaction,
     * and their results are written in one short transaction afterwards, so no
     * database connection is held while the LLM answers. A failed LLM call
     * leaves that part of the profile as it was.
     *
     * @param entityId the entity to enrich
     */
    public void enrich(Long entityId) {
        DiscoveryEntity entity = entities.findById(entityId).orElse(null);
        if (entity == null) {
            log.warn("Enrichment skipped — entity #{} not found", entityId);
            return;
        }

        // 1. Gather source content from chunks
        List<SourceContent> sourceContents = gatherSourceContents(entityId);
        if (sourceContents.isEmpty()) {
            log.warn("Enrichment skipped — entity #{} has no source content (no chunks and no raw_content)", entityId);
            return;
        }
        log.info("Enriching entity #{} ({} content blocks)", entityId, sourceContents.size());

        // 2. LLM calls — outside any transaction.
        String summary = null;
        String headline = null;
        try {
            summary = llm.generateCompetenceSummary(
                    entity.getDisplayName(), entity.getEntityType(), entity.getHeadline(), sourceContents);
            // A role-style headline is derived ONLY when the entity has none yet,
            // so harvested (esp. OpenAlex) profiles get a real title, and a user's
            // manual edit (§2a) is never overwritten or re-derived.
            if (summary != null && !summary.isBlank()
                    && (entity.getHeadline() == null || entity.getHeadline().isBlank())) {
                headline = llm.generateRoleHeadline(entity.getDisplayName(), entity.getEntityType(), summary);
            }
        } catch (Exception e) {
            log.warn("Competence summary generation failed for entity #{}: {}", entityId, e.getMessage());
        }
        List<String> derivedLabels = List.of();
        try {
            derivedLabels = llm.deriveTags(entity.getDisplayName(), entity.getEntityType(), sourceContents, MAX_TAGS);
        } catch (Exception e) {
            log.warn("Tag derivation failed for entity #{}: {}", entityId, e.getMessage());
        }
        // Map new labels onto existing tags that name the same topic (embedding + LLM).
        derivedLabels = tagVocabulary.canonicalLabels(derivedLabels);

        // 3. Write the results — one short transaction.
        String newSummary = summary;
        String newHeadline = headline;
        List<String> labels = derivedLabels;
        transactions.executeWithoutResult(status -> {
            DiscoveryEntity fresh = entities.findById(entityId).orElse(null);
            if (fresh == null) return;   // deleted meanwhile
            if (newSummary != null && !newSummary.isBlank()) {
                fresh.setSummary(newSummary);
                if (newHeadline != null && !newHeadline.isBlank()
                        && (fresh.getHeadline() == null || fresh.getHeadline().isBlank())) {
                    fresh.setHeadline(newHeadline);
                    log.info("Derived role headline for entity #{}: {}", entityId, newHeadline);
                }
                entities.save(fresh);
                log.info("Updated summary for entity #{}: {} chars", entityId, newSummary.length());
            } else {
                log.warn("Competence summary LLM returned null/empty for entity #{}", entityId);
            }
            if (!labels.isEmpty()) {
                upsertDerivedTags(entityId, labels);
                log.info("Upserted {} derived tags for entity #{}", labels.size(), entityId);
            } else {
                log.warn("Tag derivation LLM returned empty list for entity #{}", entityId);
            }
            // Person↔Org link (best-effort; links only to an existing org)
            try {
                linkResolver.resolveForPerson(fresh);
            } catch (Exception e) {
                log.warn("Person↔Org link resolution failed for entity #{}: {}", entityId, e.getMessage());
            }
            // Re-index the profile document once this commits (summary, headline and tags may have changed)
            profileDocuments.refreshAfterCommit(entityId);
        });
        tagVocabulary.embedMissingAsync();
    }

    // ─── helpers ───

    /**
     * Gather high-signal source content for the LLM, one block per source.
     * <p>
     * Primary approach: load chunk content via a lightweight JPQL query that
     * avoids the expensive vector(1024) embedding column. If chunks are empty
     * for a source (e.g. embedding failed), falls back to the source's
     * {@code raw_content} field.
     */
    private List<SourceContent> gatherSourceContents(Long entityId) {
        List<Source> entitySources = sources.findByEntityIdAndActiveTrueOrderByCreatedAtAsc(entityId);
        if (entitySources.isEmpty()) {
            log.warn("Enrichment: entity #{} has no active sources", entityId);
            return List.of();
        }

        // Load chunk content WITHOUT embeddings (lightweight query)
        List<Long> activeSourceIds = entitySources.stream().map(Source::getId).toList();
        List<Object[]> chunkRows = chunks.findContentBySourceIds(activeSourceIds);
        Map<Long, List<String>> contentBySource = new HashMap<>();
        for (Object[] row : chunkRows) {
            String content = (String) row[0];
            Long sourceId = (Long) row[1];
            contentBySource.computeIfAbsent(sourceId, k -> new ArrayList<>()).add(content);
        }
        log.info("Enrichment: entity #{}, {} sources, {} chunk rows loaded",
                entityId, entitySources.size(), chunkRows.size());

        List<SourceContent> result = new ArrayList<>();
        for (Source src : entitySources) {
            List<String> srcChunkTexts = contentBySource.get(src.getId());
            String coreText;

            if (srcChunkTexts != null && !srcChunkTexts.isEmpty()) {
                // Use chunk content — take first N chunks, cap total chars
                StringBuilder text = new StringBuilder();
                int taken = 0;
                for (String ct : srcChunkTexts) {
                    if (taken >= MAX_CHUNKS_PER_SOURCE) break;
                    if (text.length() >= MAX_CHARS_PER_SOURCE) break;
                    if (!text.isEmpty()) text.append("\n\n");
                    text.append(ct);
                    taken++;
                }
                coreText = text.length() > MAX_CHARS_PER_SOURCE
                        ? text.substring(0, MAX_CHARS_PER_SOURCE) + "…"
                        : text.toString();
            } else {
                // Fallback: use source raw_content if chunks are empty
                coreText = src.getRawContent();
                if (coreText != null && coreText.length() > MAX_CHARS_PER_SOURCE) {
                    coreText = coreText.substring(0, MAX_CHARS_PER_SOURCE) + "…";
                }
                log.info("Enrichment: no chunks for source {} ({}), using raw_content fallback ({} chars)",
                        src.getId(), src.getSourceType(),
                        coreText != null ? coreText.length() : 0);
            }

            if (coreText != null && !coreText.isBlank()) {
                result.add(new SourceContent(src.getSourceType(), src.getLabel(), coreText));
            }
        }

        log.info("Enrichment: gathered {} content blocks for entity #{}", result.size(), entityId);
        return result;
    }

    /**
     * Upsert derived tags: delete old derived tags and insert the fresh ones,
     * reusing or minting vocabulary entries through {@link TagVocabularyService}.
     * Manual tags are untouched — a label that is already a manual tag stays manual.
     */
    private void upsertDerivedTags(Long entityId, List<String> tagLabels) {
        List<EntityTag> oldDerived = entityTagRepo.findByEntityIdAndOrigin(entityId, "derived");
        if (!oldDerived.isEmpty()) {
            entityTagRepo.deleteAll(oldDerived);
            entityTagRepo.flush();
        }

        Set<Long> seen = new HashSet<>();
        for (String label : tagLabels) {
            Tag tag = tagVocabulary.findOrMint(label).orElse(null);
            if (tag == null || !seen.add(tag.getId())) continue;
            EntityTagId id = new EntityTagId(entityId, tag.getId());
            if (entityTagRepo.findById(id).isEmpty()) {
                entityTagRepo.save(new EntityTag(entityId, tag.getId(), "derived"));
            }
        }
    }

}
