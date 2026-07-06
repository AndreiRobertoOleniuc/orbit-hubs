package com.orbit.app.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.orbit.app.ingestion.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.app.embedding.EmbeddingKind;
import com.orbit.app.embedding.EmbeddingService;
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
                                  @Lazy IngestionOrchestrator self) {
        this.adapterRegistry = adapterRegistry;
        this.entities = entities;
        this.identifiers = identifiers;
        this.sources = sources;
        this.chunks = chunks;
        this.embeddingService = embeddingService;
        this.chunkingService = chunkingService;
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
        String hash = sha256(normalize(coreText));

        // 2. DEDUP — idempotent short-circuit
        Optional<Source> existing = sources.findBySourceUrl(ref.url());
        if (existing.isPresent() && hash.equals(existing.get().getContentHash())) {
            log.debug("Skipping unchanged source: {}", ref.url());
            return IngestionResult.skipped(existing.get().getEntityId());
        }

        // 3. RESOLVE IDENTITY (before creating an entity)
        List<ExternalId> extIds = adapter.extractExternalIds(raw);
        Optional<Long> existingEntityId = findEntityByAnyId(extIds);

        Long entityId;
        if (existingEntityId.isEmpty()) {
            EntityProfile profile = adapter.deriveProfile(raw);
            DiscoveryEntity entity = new DiscoveryEntity(
                    profile.entityType(),
                    profile.displayName(),
                    profile.headline(),
                    profile.orgName());
            entity.setOwnerId(ref.ownerId());
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

        if (subSources.size() == 1) {
            return List.of(self.ingest(subSources.get(0)));
        }

        log.info("Multi-source ingestion: {} → {} sub-sources (1 sequential + {} parallel)", ref.url(), subSources.size(), subSources.size() - 1);

        List<IngestionResult> results = new ArrayList<>();

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

        return results;
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

    private Source upsertSource(Long entityId, SourceRef ref, String label,
                                 RawContent raw, String hash, Optional<Source> existing) {
        if (existing.isPresent()) {
            sources.delete(existing.get());
            sources.flush();
        }
        Source source = new Source(
                entityId, ref.sourceType(), ref.url(), label,
                ref.provenance(), ref.consentBasis(),
                raw.text(), hash, "ok", Instant.now());
        return sources.save(source);
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return text.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}