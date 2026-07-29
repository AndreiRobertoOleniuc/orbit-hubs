package com.orbit.app.ingestion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.orbit.app.ingestion.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.app.embedding.EmbeddingKind;
import com.orbit.app.embedding.EmbeddingService;
import com.orbit.app.util.HashUtils;
import com.orbit.domain.entity.Chunk;
import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.entity.EntityIdentifier;
import com.orbit.domain.entity.Source;
import com.orbit.domain.repository.ChunkRepository;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.EntityIdentifierRepository;
import com.orbit.domain.repository.SourceRepository;

/**
 * The core ingestion pipeline — source-agnostic, delegates all source-specific
 * logic to the {@link SourceAdapter}.
 * <p>
 * Implements the algorithm from §4.2 of the reference doc:
 * <ol>
 *   <li>FETCH — adapter fetches raw content</li>
 *   <li>DEDUP — idempotent short-circuit on content hash</li>
 *   <li>RESOLVE IDENTITY — find existing entity by external IDs, or create new</li>
 *   <li>UPSERT SOURCE — create or update the source row</li>
 *   <li>RE-CHUNK — delete old chunks, rebuild from core text</li>
 *   <li>EMBED + PERSIST — embed each chunk with "passage: " prefix, store</li>
 * </ol>
 * <p>
 * Multi-source ingestion (GitHub repos, OpenAlex publications) processes the
 * FIRST sub-source sequentially (creates the entity), then processes the
 * remaining sub-sources in PARALLEL using a thread pool. Each parallel
 * ingestion runs in its own transaction via the Spring proxy (self-injection).
 */
@Service
public class IngestionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(IngestionOrchestrator.class);

    private final SourceAdapterRegistry adapterRegistry;
    private final DiscoveryEntityRepository entities;
    private final EntityIdentifierRepository identifiers;
    private final SourceRepository sources;
    private final ChunkRepository chunks;
    private final EmbeddingService embeddingService;
    private final ChunkingService chunkingService;
    private final EntityEnrichmentService enrichmentService;
    private final IngestionOrchestrator self; // for proxy-based @Transactional in parallel calls

    // Thread pool for parallel per-repo ingestion
    private final ExecutorService ingestionPool = Executors.newFixedThreadPool(5);

    public IngestionOrchestrator(SourceAdapterRegistry adapterRegistry,
                                  DiscoveryEntityRepository entities,
                                  EntityIdentifierRepository identifiers,
                                  SourceRepository sources,
                                  ChunkRepository chunks,
                                  EmbeddingService embeddingService,
                                  ChunkingService chunkingService,
                                  EntityEnrichmentService enrichmentService,
                                  @Lazy IngestionOrchestrator self) {
        this.adapterRegistry = adapterRegistry;
        this.entities = entities;
        this.identifiers = identifiers;
        this.sources = sources;
        this.chunks = chunks;
        this.embeddingService = embeddingService;
        this.chunkingService = chunkingService;
        this.enrichmentService = enrichmentService;
        this.self = self;
    }

    /**
     * Ingest a single source. This is the standard pipeline for sources that
     * produce exactly one source row (LinkedIn, website).
     */
    @Transactional
    public IngestionResult ingest(SourceRef ref) {
        SourceAdapter adapter = adapterRegistry.getAdapter(ref.sourceType());

        // 1. FETCH
        RawContent raw;
        try {
            raw = adapter.fetch(ref);
        } catch (IngestionException e) {
            log.error("Fetch failed for source {}: {}", ref.url(), e.getMessage());
            return IngestionResult.error(null);
        }

        String coreText = adapter.extractHighSignalCore(raw);
        String label = adapter.buildLabel(raw);
        String hash = HashUtils.sha256Hex(normalize(coreText));

        // 2. DEDUP — idempotent short-circuit
        Optional<Source> existing = sources.findBySourceUrl(ref.url());
        if (existing.isPresent() && hash.equals(existing.get().getContentHash())) {
            prepareExistingSourceForOnboarding(existing.get(), ref);
            log.debug("Skipping unchanged source: {}", ref.url());
            return IngestionResult.skipped(existing.get().getEntityId());
        }

        // 3. RESOLVE IDENTITY (before creating an entity)
        // Resolution order: external ids → the requesting user's own entity → create new.
        // The owner fallback makes all sources of one onboarding converge onto ONE
        // entity even when their external id types are disjoint (zefix_uid vs domain).
        List<ExternalId> extIds = adapter.extractExternalIds(raw);
        Optional<Long> existingEntityId = findEntityByAnyId(extIds)
                .or(() -> findEntityByOwner(ref.ownerId()));

        Long entityId;
        if (existingEntityId.isEmpty()) {
            EntityProfile profile = adapter.deriveProfile(raw);
            DiscoveryEntity entity = new DiscoveryEntity(
                    profile.entityType(),
                    profile.displayName(),
                    profile.headline(),
                    profile.orgName());
            entity.setOwnerId(ref.ownerId());
            if (ref.onboardingDraft()) {
                entity.setStatus("draft");
                entity.setProfileMode(ref.profileMode());
            }
            if (profile.latitude() != null) entity.setLatitude(profile.latitude());
            if (profile.longitude() != null) entity.setLongitude(profile.longitude());
            entity = entities.save(entity);
            entityId = entity.getId();
            log.info("Created new entity #{}: {} ({})", entityId, profile.displayName(), profile.entityType());

            for (ExternalId id : extIds) {
                identifiers.save(new EntityIdentifier(entityId, id.idType(), id.idValue()));
            }
        } else {
            entityId = existingEntityId.get();
            log.debug("Reusing existing entity #{}", entityId);
            claimIfUnowned(entityId, ref.ownerId());
            applyOnboardingState(entityId, ref);
            for (ExternalId id : extIds) {
                if (!identifiers.existsByIdTypeAndIdValue(id.idType(), id.idValue())) {
                    identifiers.save(new EntityIdentifier(entityId, id.idType(), id.idValue()));
                }
            }
        }

        // 4. UPSERT SOURCE
        Source source = upsertSource(entityId, ref, label, raw, hash, existing);

        // 5. RE-CHUNK
        chunks.deleteBySourceId(source.getId());
        chunks.flush();

        List<ChunkSegment> segments = chunkingService.recursiveSplit(coreText, 400, 60);
        log.info("Source {} → {} chunks", ref.url(), segments.size());

        // 6. EMBED + PERSIST CHUNKS
        int embeddedCount = 0;
        for (int i = 0; i < segments.size(); i++) {
            ChunkSegment seg = segments.get(i);
            try {
                float[] vec = embeddingService.embed("passage: " + seg.text(), EmbeddingKind.PASSAGE);
                chunks.save(new Chunk(
                        source.getId(), entityId, i, seg.text(), seg.section(),
                        seg.tokenCount(), vec, "multilingual-e5-large", Instant.now()));
                embeddedCount++;
            } catch (Exception e) {
                log.error("Embedding failed for chunk {} of source {}: {}", i, ref.url(), e.getMessage());
                chunks.save(new Chunk(
                        source.getId(), entityId, i, seg.text(), seg.section(),
                        seg.tokenCount(), null, "multilingual-e5-large", null));
            }
        }

        log.info("Ingested source {} → entity #{}, {} chunks ({} embedded)",
                ref.url(), entityId, segments.size(), embeddedCount);

        // NOTE: Enrichment (step 7) is NOT called here. It used to run inside
        // this @Transactional method, but a PSQLException from the enrichment
        // would mark the transaction as rollback-only — rolling back the entire
        // ingestion. Enrichment now runs in a SEPARATE transaction via
        // finalEnrichment() in ingestMulti(), after ingest() has committed.

        return IngestionResult.ingested(entityId, source.getId(), segments.size());
    }

    /**
     * For multi-source adapters (GitHub repos, OpenAlex publications):
     * discover sub-sources → ingest each one.
     * <p>
     * The FIRST sub-source is processed sequentially (creates the entity),
     * then remaining sub-sources are processed in PARALLEL via a thread pool.
     * Each parallel call goes through the Spring proxy (self.ingest()) so
     * @Transactional is properly applied per-thread.
     */
    public List<IngestionResult> ingestMulti(SourceRef ref) {
        SourceAdapter adapter = adapterRegistry.getAdapter(ref.sourceType());
        List<SourceRef> subSources;
        try {
            subSources = adapter.discoverSources(ref);
        } catch (IngestionException e) {
            log.error("discoverSources failed for {}: {}", ref.url(), e.getMessage(), e);
            return List.of(IngestionResult.error(null));
        }

        if (subSources.isEmpty()) {
            return List.of();
        }

        List<IngestionResult> results;

        if (subSources.size() == 1) {
            results = new ArrayList<>(List.of(self.ingest(subSources.getFirst())));
        } else {
            log.info("Multi-source ingestion: {} → {} sub-sources (1 sequential + {} parallel)", ref.url(), subSources.size(), subSources.size() - 1);

            results = new ArrayList<>();

            // 1. Process first sub-source SEQUENTIALLY — creates the entity
            long startTime = System.currentTimeMillis();
            IngestionResult first = self.ingest(subSources.getFirst());
            results.add(first);
            log.info("First sub-source processed in {}ms (entity created)", System.currentTimeMillis() - startTime);

            // 2. Process remaining sub-sources in PARALLEL
            List<SourceRef> remaining = subSources.subList(1, subSources.size());
            List<CompletableFuture<IngestionResult>> futures = remaining.stream()
                    .map(sub -> CompletableFuture.supplyAsync(() -> self.ingest(sub), ingestionPool))
                    .toList();

            // Wait for all parallel ingestions to complete
            long parallelStart = System.currentTimeMillis();
            for (CompletableFuture<IngestionResult> future : futures) {
                try {
                    results.add(future.join());
                } catch (Exception e) {
                    log.error("Parallel ingestion failed: {}", e.getMessage());
                    results.add(IngestionResult.error(null));
                }
            }
            log.info("Parallel ingestion of {} sources completed in {}ms",
                    remaining.size(), System.currentTimeMillis() - parallelStart);
        }

        // 3. FINAL ENRICHMENT — runs for BOTH single-source and multi-source.
        //    Runs in a SEPARATE transaction (via self proxy) after all ingest()
        //    calls have committed. This isolation is critical: if enrichment
        //    fails (e.g. LLM error, DB constraint), the ingestion transactions
        //    are already committed and not affected.
        Long entityId = results.stream()
                .map(IngestionResult::entityId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (entityId != null) {
            try {
                self.finalEnrichment(entityId);
            } catch (Exception e) {
                log.warn("Final enrichment pass failed for entity #{}: {}", entityId, e.getMessage());
            }
        }

        return results;
    }

    /**
     * Resync an entity: re-ingest ALL its sources, then run a final enrichment
     * pass. Sources that can't be re-fetched (CV/LinkedIn PDF uploads, manual text)
     * are skipped — their chunks stay intact. The dedup mechanism ensures
     * unchanged sources are skipped without re-embedding.
     * <p>
     * This is the backend side of the "Quellen neu auswerten" button in the
     * profile UI. It reconstructs parent {@link SourceRef}s from the entity's
     * stored sources + identifiers, then calls {@link #ingestMulti(SourceRef)}
     * for each source type group.
     *
     * @param entityId the entity to resync
     * @return list of ingestion results, one entry per source-type group
     */
    public List<IngestionResult> resyncEntity(Long entityId) {
        DiscoveryEntity entity = entities.findById(entityId).orElse(null);
        if (entity == null) {
            log.warn("Resync skipped — entity #{} not found", entityId);
            return List.of(IngestionResult.error(null));
        }

        List<Source> entitySources = sources.findByEntityIdOrderByCreatedAtAsc(entityId);
        if (entitySources.isEmpty()) {
            log.warn("Resync skipped — entity #{} has no sources", entityId);
            return List.of();
        }

        // Build identifier map for context reconstruction
        Map<String, String> idMap = new HashMap<>();
        for (EntityIdentifier ei : identifiers.findByEntityId(entityId)) {
            idMap.put(ei.getIdType(), ei.getIdValue());
        }

        // Group sources by sourceType (skip non-re-fetchable types)
        Set<String> reFetchableTypes = Set.of("github", "orcid_pub", "zefix", "personal_site", "company_site");
        Map<String, List<Source>> grouped = new LinkedHashMap<>();
        for (Source s : entitySources) {
            if (reFetchableTypes.contains(s.getSourceType())) {
                grouped.computeIfAbsent(s.getSourceType(), k -> new ArrayList<>()).add(s);
            }
        }

        if (grouped.isEmpty()) {
            log.info("Resync for entity #{}: no re-fetchable sources, running enrichment only", entityId);
            try {
                self.finalEnrichment(entityId);
            } catch (Exception e) {
                log.warn("Enrichment-only resync failed for entity #{}: {}", entityId, e.getMessage());
            }
            return List.of();
        }

        log.info("Resyncing entity #{} ({}): {} source-type groups: {}",
                entityId, entity.getDisplayName(), grouped.size(), grouped.keySet());

        String ownerId = entity.getOwnerId();
        List<IngestionResult> allResults = new ArrayList<>();

        for (Map.Entry<String, List<Source>> entry : grouped.entrySet()) {
            String sourceType = entry.getKey();
            List<Source> typeSources = entry.getValue();

            Optional<SourceRef> parentRef = buildResyncRef(sourceType, typeSources, idMap, ownerId);
            if (parentRef.isEmpty()) {
                log.warn("Could not reconstruct ref for sourceType={} on entity #{}, skipping", sourceType, entityId);
                continue;
            }

            log.info("Resyncing {} for entity #{}: {} existing sources",
                    sourceType, entityId, typeSources.size());

            try {
                List<IngestionResult> results = self.ingestMulti(parentRef.get());
                allResults.addAll(results);
            } catch (Exception e) {
                log.error("Resync failed for sourceType={} on entity #{}: {}", sourceType, entityId, e.getMessage(), e);
                allResults.add(IngestionResult.error(entityId));
            }
        }

        // Final enrichment after all source types are re-synced
        try {
            self.finalEnrichment(entityId);
        } catch (Exception e) {
            log.warn("Post-resync enrichment failed for entity #{}: {}", entityId, e.getMessage());
        }

        log.info("Resync complete for entity #{}: {} results", entityId, allResults.size());
        return allResults;
    }

    /**
     * Reconstruct a parent {@link SourceRef} for resync from stored sources +
     * entity identifiers. The parent ref is what the user originally submitted
     * — {@link #ingestMulti(SourceRef)} will re-discover sub-sources, re-fetch,
     * re-chunk, and re-embed. Dedup skips unchanged sources.
     */
    private Optional<SourceRef> buildResyncRef(
            String sourceType,
            List<Source> typeSources,
            Map<String, String> idMap,
            String ownerId) {

        String firstUrl = typeSources.getFirst().getSourceUrl();
        String provenance = typeSources.getFirst().getProvenance();
        String consentBasis = typeSources.getFirst().getConsentBasis();
        Map<String, String> context = new HashMap<>();

        return switch (sourceType) {
            case "github" -> {
                String login = idMap.getOrDefault("github_login",
                        extractPathSegment(firstUrl, "https://github.com/", 0));
                if (login == null || login.isBlank()) yield Optional.empty();
                context.put("github_login", login);
                yield Optional.of(new SourceRef(
                        "https://github.com/" + login, "github",
                        provenance, consentBasis, context, ownerId));
            }

            case "orcid_pub" -> {
                String orcid = idMap.get("orcid");
                String authorId = idMap.get("openalex_author_id");
                if (orcid != null && !orcid.isBlank()) {
                    context.put("orcid", orcid);
                    yield Optional.of(new SourceRef(
                            "https://orcid.org/" + orcid, "orcid_pub",
                            provenance, consentBasis, context, ownerId));
                }
                if (authorId != null && !authorId.isBlank()) {
                    context.put("openalex_author_id", authorId);
                    yield Optional.of(new SourceRef(
                            "https://openalex.org/authors/" + authorId, "orcid_pub",
                            provenance, consentBasis, context, ownerId));
                }
                yield Optional.empty();
            }

            case "zefix" -> {
                // entity_identifiers stores the normalized form (CHE-316.574.745)
                // but the Zefix API expects the raw form (CHE316574745, no dashes/dots).
                String uid = idMap.get("zefix_uid");
                if (uid != null && !uid.isBlank()) {
                    String rawUid = uid.replaceAll("[^A-Za-z0-9]", "");
                    context.put("zefix_uid", rawUid);
                    yield Optional.of(new SourceRef(
                            "zefix:" + rawUid, "zefix",
                            provenance, consentBasis, context, ownerId));
                }
                if (firstUrl != null && firstUrl.startsWith("zefix:")) {
                    String value = firstUrl.substring("zefix:".length());
                    if (value.startsWith("name:")) {
                        context.put("company_name", value.substring("name:".length()));
                    } else {
                        context.put("zefix_uid", value.replaceAll("[^A-Za-z0-9]", ""));
                    }
                    yield Optional.of(new SourceRef(
                            firstUrl, "zefix",
                            provenance, consentBasis, context, ownerId));
                }
                yield Optional.empty();
            }

            case "personal_site", "company_site" -> {
                // Source URL: {sourceType}:{domain} — domain was stripped by
                // normalizeDomain() which removes the protocol. The crawl()
                // method expects an absolute URL, so prepend https://
                if (firstUrl != null && firstUrl.startsWith(sourceType + ":")) {
                    String domain = firstUrl.substring((sourceType + ":").length());
                    String absUrl = domain.startsWith("http") ? domain : "https://" + domain;
                    context.put("url", absUrl);
                    yield Optional.of(new SourceRef(
                            firstUrl, sourceType,
                            provenance, consentBasis, context, ownerId));
                }
                yield Optional.empty();
            }

            default -> Optional.empty();
        };
    }

    /**
     * Extract the Nth path segment from a URL (0-indexed).
     */
    private static String extractPathSegment(String url, String prefix, int index) {
        if (url == null || !url.startsWith(prefix)) return null;
        String path = url.substring(prefix.length());
        String[] parts = path.split("/");
        return index < parts.length ? parts[index] : null;
    }

    // ─── helpers ───

    /**
     * Final enrichment pass after all sub-sources of a multi-source ingestion
     * are complete. Runs in its own transaction (separate from the parallel
     * per-source transactions) so it sees ALL committed chunks.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalEnrichment(Long entityId) {
        enrichmentService.enrich(entityId);
    }

    private Optional<Long> findEntityByAnyId(List<ExternalId> extIds) {
        for (ExternalId id : extIds) {
            Optional<Long> entityId = identifiers.findEntityIdByIdTypeAndIdValue(id.idType(), id.idValue());
            if (entityId.isPresent()) {
                return entityId;
            }
        }
        return Optional.empty();
    }

    /**
     * Fallback resolution for signed-in ingestion: a user's sources always land
     * on the entity they already own. Onboarding is one entity per account, so
     * the newest owned entity is the only one under normal operation.
     */
    private Optional<Long> findEntityByOwner(String ownerId) {
        if (ownerId == null) {
            return Optional.empty();
        }
        return entities.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .findFirst()
                .map(DiscoveryEntity::getId);
    }

    /**
     * When external ids resolve onto a previously harvested (unowned) entity,
     * the ingesting user takes ownership so it appears under /me/entities.
     * Provisional Stage-1 behavior — proper claim verification is a later stage.
     */
    private void claimIfUnowned(Long entityId, String ownerId) {
        if (ownerId == null) {
            return;
        }
        entities.findById(entityId).ifPresent(entity -> {
            if (entity.getOwnerId() == null) {
                entity.setOwnerId(ownerId);
                entities.save(entity);
                log.info("Entity #{} claimed by user {}", entityId, ownerId);
            }
        });
    }

    private Source upsertSource(Long entityId, SourceRef ref, String label,
                                 RawContent raw, String hash, Optional<Source> existing) {
        boolean active = existing.map(Source::isActive)
                .orElse(!(ref.onboardingDraft() && isCuratable(ref.sourceType())));
        if (existing.isPresent()) {
            sources.delete(existing.get());
            sources.flush();
        }
        Source source = new Source(
                entityId, ref.sourceType(), ref.url(), label,
                ref.provenance(), ref.consentBasis(),
                raw.text(), hash, "ok", Instant.now());
        source.setActive(active);
        return sources.save(source);
    }

    private void prepareExistingSourceForOnboarding(Source source, SourceRef ref) {
        if (!ref.onboardingDraft()) return;
        claimIfUnowned(source.getEntityId(), ref.ownerId());
        applyOnboardingState(source.getEntityId(), ref);
        if (isCuratable(source.getSourceType()) && isOwnedBy(source.getEntityId(), ref.ownerId())) {
            source.setActive(false);
            sources.save(source);
        }
    }

    private void applyOnboardingState(Long entityId, SourceRef ref) {
        if (!ref.onboardingDraft() || !isOwnedBy(entityId, ref.ownerId())) return;
        entities.findById(entityId).ifPresent(entity -> {
            entity.setStatus("draft");
            entity.setProfileMode(ref.profileMode());
            entities.save(entity);
        });
    }

    private boolean isOwnedBy(Long entityId, String ownerId) {
        if (ownerId == null) return false;
        return entities.findById(entityId)
                .map(entity -> ownerId.equals(entity.getOwnerId()))
                .orElse(false);
    }

    private static boolean isCuratable(String sourceType) {
        return "github".equals(sourceType) || "orcid_pub".equals(sourceType);
    }



    private static String normalize(String text) {
        if (text == null) return "";
        return text.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
