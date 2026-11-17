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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.orbit.app.ingestion.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import com.orbit.app.embedding.EmbeddingKind;
import com.orbit.app.embedding.EmbeddingService;
import com.orbit.app.inference.InferenceUnavailableException;
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
 *   <li>DEDUP — idempotent short-circuit on the hash of the fetched content,
 *       before any LLM or embedding call</li>
 *   <li>PREPARE — core-text extraction (often an LLM call), chunking, and one
 *       batch embedding call for all chunks</li>
 *   <li>PERSIST — in one short transaction: resolve identity (find the entity by
 *       external ids or owner, or create it), create the source row or update
 *       it in place (same id), replace its chunks</li>
 * </ol>
 * Steps 1–3 are network calls and run outside any transaction, so no database
 * connection is held while a provider is slow. A failed embedding fails the
 * source instead of storing a chunk without a vector: the caller (the
 * ingestion job) retries, and the dedup step makes a retry cheap.
 * <p>
 * Multi-source ingestion (GitHub repos, OpenAlex publications) processes the
 * FIRST sub-source sequentially (creates the entity), then processes the
 * remaining sub-sources in PARALLEL using a thread pool.
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
    private final TransactionOperations transactions;

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
                                  TransactionOperations transactions) {
        this.adapterRegistry = adapterRegistry;
        this.entities = entities;
        this.identifiers = identifiers;
        this.sources = sources;
        this.chunks = chunks;
        this.embeddingService = embeddingService;
        this.chunkingService = chunkingService;
        this.enrichmentService = enrichmentService;
        this.transactions = transactions;
    }

    /**
     * Ingest a single source. This is the standard pipeline for sources that
     * produce exactly one source row (LinkedIn, website). Not transactional
     * itself: only the final write is (see the class comment).
     *
     * @throws com.orbit.app.inference.InferenceUnavailableException when the
     *         embedding provider fails — retryable, nothing was written
     */
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

        // 2. DEDUP — idempotent short-circuit, decided on what was FETCHED.
        //    The hash must not depend on extractHighSignalCore(): for most adapters
        //    that is an LLM call whose wording differs between runs, so hashing its
        //    output re-ingested (and re-embedded) every unchanged source.
        String hash = HashUtils.sha256Hex(normalize(fingerprint(adapter, raw)));
        Optional<Source> existing = sources.findBySourceUrl(ref.url());
        List<ExternalId> extIds = adapter.extractExternalIds(raw);   // from fetched metadata, no network
        rejectIfOwnedByAnotherAccount(existing, extIds, ref.ownerId());
        if (existing.isPresent() && hash.equals(existing.get().getContentHash())) {
            if (ref.onboardingDraft()) {
                transactions.executeWithoutResult(status -> prepareExistingSourceForOnboarding(existing.get(), ref));
            }
            log.debug("Skipping unchanged source: {}", ref.url());
            return IngestionResult.skipped(existing.get().getEntityId());
        }

        // 3. PREPARE — every network call happens here, before the transaction.
        String coreText = adapter.extractHighSignalCore(raw);
        String label = adapter.buildLabel(raw);
        List<ChunkSegment> segments = chunkingService.recursiveSplit(coreText, 400, 60);
        List<float[]> vectors = segments.isEmpty() ? List.of()
                : embeddingService.embedAll(segments.stream().map(ChunkSegment::text).toList(), EmbeddingKind.PASSAGE);
        if (vectors.size() != segments.size()) {
            throw new IllegalStateException("Embedding returned %d vectors for %d chunks"
                    .formatted(vectors.size(), segments.size()));
        }

        // 4. PERSIST — one short transaction.
        IngestionResult result = transactions.execute(status ->
                persist(adapter, ref, raw, hash, label, extIds, segments, vectors));
        log.info("Ingested source {} → entity #{}, {} chunks", ref.url(), result.entityId(), segments.size());
        return result;
    }

    /** Identity, source row and chunks of one prepared source — the transactional part of {@link #ingest}. */
    private IngestionResult persist(SourceAdapter adapter, SourceRef ref, RawContent raw, String hash, String label,
                                    List<ExternalId> extIds, List<ChunkSegment> segments, List<float[]> vectors) {
        // Re-read: another ingestion may have created the row since the dedup check.
        Optional<Source> existing = sources.findBySourceUrl(ref.url());
        rejectIfOwnedByAnotherAccount(existing, extIds, ref.ownerId());

        // Resolution order: external ids → the requesting user's own entity → create new.
        // The owner fallback makes all sources of one onboarding converge onto ONE
        // entity even when their external id types are disjoint (zefix_uid vs domain).
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

        Source source = upsertSource(entityId, ref, label, raw, hash, existing);

        chunks.deleteBySourceId(source.getId());
        chunks.flush();
        Instant embeddedAt = Instant.now();
        for (int i = 0; i < segments.size(); i++) {
            ChunkSegment seg = segments.get(i);
            chunks.save(new Chunk(
                    source.getId(), entityId, i, seg.text(), seg.section(),
                    seg.tokenCount(), vectors.get(i), embeddingService.modelName(), embeddedAt));
        }

        // Enrichment is NOT run here: it needs every source of the entity, and an
        // enrichment failure must not roll back the ingested source. The caller
        // runs it once, after all sources are in (ingestMulti / the ingestion job).
        return IngestionResult.ingested(entityId, source.getId(), segments.size());
    }

    /** {@link #ingestMulti(SourceRef, boolean)} followed by enrichment. */
    public List<IngestionResult> ingestMulti(SourceRef ref) {
        return ingestMulti(ref, true);
    }

    /**
     * For multi-source adapters (GitHub repos, OpenAlex publications):
     * discover sub-sources → ingest each one.
     * <p>
     * The FIRST sub-source is processed sequentially (creates the entity),
     * then remaining sub-sources are processed in PARALLEL via a thread pool.
     * A sub-source whose embedding fails does not stop the others; once they are
     * done, the failure is rethrown so the caller can retry the whole input
     * (already ingested sub-sources are then skipped as unchanged).
     *
     * @param enrich whether to enrich the entity afterwards. The ingestion job
     *               passes {@code false} and enriches once after the user's last
     *               queued source, instead of once per source type.
     */
    public List<IngestionResult> ingestMulti(SourceRef ref, boolean enrich) {
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

        List<IngestionResult> results = new ArrayList<>();
        RuntimeException retryable = null;

        // 1. First sub-source SEQUENTIALLY — it creates the entity the others join.
        long startTime = System.currentTimeMillis();
        results.add(ingest(subSources.getFirst()));

        if (subSources.size() > 1) {
            log.info("Multi-source ingestion: {} → {} sub-sources (first took {}ms, {} in parallel)",
                    ref.url(), subSources.size(), System.currentTimeMillis() - startTime, subSources.size() - 1);

            // 2. Remaining sub-sources in PARALLEL
            List<CompletableFuture<IngestionResult>> futures = subSources.subList(1, subSources.size()).stream()
                    .map(sub -> CompletableFuture.supplyAsync(() -> ingest(sub), ingestionPool))
                    .toList();
            for (CompletableFuture<IngestionResult> future : futures) {
                try {
                    results.add(future.join());
                } catch (CompletionException e) {
                    if (e.getCause() instanceof InferenceUnavailableException unavailable) {
                        retryable = unavailable;
                    } else if (e.getCause() instanceof IngestionRejectedException rejected) {
                        throw rejected;
                    } else {
                        log.error("Parallel ingestion failed: {}", e.getMessage(), e);
                        results.add(IngestionResult.error(null));
                    }
                }
            }
        }
        if (retryable != null) throw retryable;

        // 3. ENRICHMENT — after every sub-source has committed, so it sees all chunks.
        //    An enrichment failure leaves the ingested sources in place.
        if (enrich) {
            Long entityId = firstEntityId(results);
            if (entityId != null) {
                try {
                    enrichmentService.enrich(entityId);
                } catch (Exception e) {
                    log.warn("Enrichment failed for entity #{}: {}", entityId, e.getMessage());
                }
            }
        }
        return results;
    }

    /** The entity the results landed on, or {@code null} when none did. */
    public static Long firstEntityId(List<IngestionResult> results) {
        return results.stream()
                .map(IngestionResult::entityId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
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
                enrichmentService.enrich(entityId);
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
                allResults.addAll(ingestMulti(parentRef.get(), false));
            } catch (InferenceUnavailableException e) {
                throw e;   // retryable: the resync job runs again
            } catch (Exception e) {
                log.error("Resync failed for sourceType={} on entity #{}: {}", sourceType, entityId, e.getMessage(), e);
                allResults.add(IngestionResult.error(entityId));
            }
        }

        // One enrichment after all source types are re-synced
        try {
            enrichmentService.enrich(entityId);
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
     * A signed-in user's source must never land on, or be moved off, a profile
     * another account owns. Without this, a LinkedIn PDF, GitHub login or
     * company id already stored on someone else's profile attached the user's
     * upload to that profile and re-enriched it, and the user was left with no
     * profile of their own. Unowned (harvested) profiles stay claimable.
     * Ingestion without an owner (system jobs) is not restricted.
     */
    private void rejectIfOwnedByAnotherAccount(Optional<Source> existing, List<ExternalId> extIds, String ownerId) {
        if (ownerId == null) return;
        boolean conflict = existing.map(source -> ownedByAnother(source.getEntityId(), ownerId)).orElse(false)
                || findEntityByAnyId(extIds).map(entityId -> ownedByAnother(entityId, ownerId)).orElse(false);
        if (conflict) {
            log.warn("Ingestion rejected for user {}: source belongs to a profile owned by another account", ownerId);
            throw new IngestionRejectedException(
                    "Diese Quelle gehört bereits zu einem Profil eines anderen Orbit-Kontos. "
                            + "Melde dich mit jenem Konto an oder kontaktiere uns.");
        }
    }

    private boolean ownedByAnother(Long entityId, String ownerId) {
        return entities.findById(entityId)
                .map(DiscoveryEntity::getOwnerId)
                .map(owner -> !owner.equals(ownerId))
                .orElse(false);
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
        if (existing.isPresent()) {
            // Update in place: the row keeps its id and its active flag, so
            // citations and the user's curation choice survive a re-ingestion.
            Source source = existing.get();
            source.refresh(entityId, label, ref.provenance(), ref.consentBasis(),
                    raw.text(), hash, "ok", Instant.now());
            return sources.save(source);
        }
        Source source = new Source(
                entityId, ref.sourceType(), ref.url(), label,
                ref.provenance(), ref.consentBasis(),
                raw.text(), hash, "ok", Instant.now());
        source.setActive(!(ref.onboardingDraft() && isCuratable(ref.sourceType())));
        return sources.save(source);
    }

    /** The adapter's content fingerprint; the raw text if an adapter supplies none. */
    private static String fingerprint(SourceAdapter adapter, RawContent raw) {
        String fingerprint = adapter.contentFingerprint(raw);
        return fingerprint != null ? fingerprint : raw.text();
    }

    private void prepareExistingSourceForOnboarding(Source source, SourceRef ref) {
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
