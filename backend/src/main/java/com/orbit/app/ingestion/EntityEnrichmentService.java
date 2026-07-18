package com.orbit.app.ingestion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.app.ingestion.dto.SourceContent;
import com.orbit.app.llm.LlmSummaryService;
import com.orbit.app.util.SlugUtils;
import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.entity.EntityTag;
import com.orbit.domain.entity.EntityTagId;
import com.orbit.domain.entity.Source;
import com.orbit.domain.entity.Tag;
import com.orbit.domain.repository.ChunkRepository;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.EntityTagRepository;
import com.orbit.domain.repository.SourceRepository;
import com.orbit.domain.repository.TagRepository;

/**
 * Post-ingestion enrichment: re-generates the entity's competence summary and
 * derives tags from ALL its sources after each ingestion.
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
 * The summary is re-generated from scratch each time, aggregating all sources,
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
    private final TagRepository tagRepo;
    private final EntityTagRepository entityTagRepo;
    private final LlmSummaryService llm;
    private final EntityLinkResolver linkResolver;

    public EntityEnrichmentService(DiscoveryEntityRepository entities,
                                    SourceRepository sources,
                                    ChunkRepository chunks,
                                    TagRepository tagRepo,
                                    EntityTagRepository entityTagRepo,
                                    LlmSummaryService llm,
                                    EntityLinkResolver linkResolver) {
        this.entities = entities;
        this.sources = sources;
        this.chunks = chunks;
        this.tagRepo = tagRepo;
        this.entityTagRepo = entityTagRepo;
        this.llm = llm;
        this.linkResolver = linkResolver;
    }

    /**
     * Enrich an entity after a source has been ingested: regenerate the
     * competence summary from all sources and re-derive tags.
     * <p>
     * This is called by {@link IngestionOrchestrator} after step 6 (embed +
     * persist chunks). It runs in the same transaction as the ingestion.
     *
     * @param entityId the entity to enrich
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

        log.info("Enriching entity #{} ({} sources, {} content blocks)",
                entityId, sourceContents.size(), sourceContents.size());

        // 2. Generate competence summary
        try {
            String summary = llm.generateCompetenceSummary(
                    entity.getDisplayName(),
                    entity.getEntityType(),
                    entity.getHeadline(),
                    sourceContents);
            if (summary != null && !summary.isBlank()) {
                entity.setSummary(summary);

                // Fill a role-style headline ONLY when the entity has none yet.
                // This gives harvested (esp. OpenAlex) profiles a real title
                // instead of the duplicated institution name — and, because it
                // only fills when blank, it never overwrites a user's manual edit
                // (§2a) or re-derives on every re-harvest.
                if (entity.getHeadline() == null || entity.getHeadline().isBlank()) {
                    String headline = llm.generateRoleHeadline(
                            entity.getDisplayName(), entity.getEntityType(), summary);
                    if (headline != null && !headline.isBlank()) {
                        entity.setHeadline(headline);
                        log.info("Derived role headline for entity #{}: {}", entityId, headline);
                    }
                }

                entities.save(entity);
                entities.flush();
                log.info("Updated summary for entity #{}: {} chars", entityId, summary.length());
            } else {
                log.warn("Competence summary LLM returned null/empty for entity #{}", entityId);
            }
        } catch (Exception e) {
            log.warn("Competence summary generation failed for entity #{}: {}", entityId, e.getMessage());
        }

        // 3. Derive tags
        try {
            List<String> derivedLabels = llm.deriveTags(
                    entity.getDisplayName(),
                    entity.getEntityType(),
                    sourceContents,
                    MAX_TAGS);
            if (!derivedLabels.isEmpty()) {
                upsertDerivedTags(entityId, derivedLabels);
                log.info("Upserted {} derived tags for entity #{}", derivedLabels.size(), entityId);
            } else {
                log.warn("Tag derivation LLM returned empty list for entity #{}", entityId);
            }
        } catch (Exception e) {
            log.warn("Tag derivation failed for entity #{}: {}", entityId, e.getMessage());
        }

        // 4. Resolve Person↔Org link (best-effort; links only to an existing org)
        try {
            linkResolver.resolveForPerson(entity);
        } catch (Exception e) {
            log.warn("Person↔Org link resolution failed for entity #{}: {}", entityId, e.getMessage());
        }
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
        List<Source> entitySources = sources.findByEntityIdOrderByCreatedAtAsc(entityId);
        if (entitySources.isEmpty()) {
            log.warn("Enrichment: entity #{} has no sources", entityId);
            return List.of();
        }

        // Load chunk content WITHOUT embeddings (lightweight query)
        List<Object[]> chunkRows = chunks.findContentByEntityId(entityId);
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
     * Upsert derived tags: delete old derived tags, mint new tag vocabulary
     * entries, and insert fresh derived entity_tags. Manual tags are untouched.
     */
    private void upsertDerivedTags(Long entityId, List<String> tagLabels) {
        // 1. Remove old derived tags (manual tags stay)
        List<EntityTag> oldDerived = entityTagRepo.findByEntityIdAndOrigin(entityId, "derived");
        if (!oldDerived.isEmpty()) {
            entityTagRepo.deleteAll(oldDerived);
            entityTagRepo.flush();
        }

        // 2. Mint or find tags in the controlled vocabulary
        //    Deduplicate by slug, preserving insertion order (first occurrence wins)
        Map<String, Tag> slugToTag = new LinkedHashMap<>();
        List<String> slugsToLookup = new ArrayList<>();
        for (String label : tagLabels) {
            String slug = SlugUtils.slugify(label);
            if (slug.isEmpty() || slugToTag.containsKey(slug)) continue;
            slugsToLookup.add(slug);
            // Placeholder — will be replaced with the actual Tag after lookup/mint
            slugToTag.put(slug, null);
        }

        // 3. Batch-lookup existing tags by slug
        if (!slugsToLookup.isEmpty()) {
            List<Tag> existing = tagRepo.findBySlugIn(slugsToLookup);
            for (Tag t : existing) {
                slugToTag.put(t.getSlug(), t);
            }
        }

        // 4. Mint missing tags and build entity_tags
        for (String label : tagLabels) {
            String slug = SlugUtils.slugify(label);
            Tag tag = slugToTag.get(slug);
            if (tag == null) {
                // Mint new tag
                tag = tagRepo.save(new Tag(slug, label));
                slugToTag.put(slug, tag);
                log.debug("Minted new tag: {} (slug={})", label, slug);
            }
            // Insert derived entity_tag (skip if already manual — manual wins)
            EntityTagId id = new EntityTagId(entityId, tag.getId());
            EntityTag existing = entityTagRepo.findById(id).orElse(null);
            if (existing == null) {
                entityTagRepo.save(new EntityTag(entityId, tag.getId(), "derived"));
            }
            // If a manual tag already exists for this (entity, tag), leave it — manual is sticky
        }
    }


}