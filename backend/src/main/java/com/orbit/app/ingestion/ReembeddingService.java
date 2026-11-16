package com.orbit.app.ingestion;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import com.orbit.app.embedding.EmbeddingKind;
import com.orbit.app.embedding.EmbeddingService;
import com.orbit.app.embedding.VectorLiteral;
import com.orbit.domain.repository.ChunkRepository;

/**
 * Re-embeds stored chunks whose vector is missing, comes from another
 * embedding model, or predates a cutoff — the recovery path for a failed
 * embedding call and the migration path when the embedding model or its input
 * changes. Content, chunking and sources are untouched; only
 * {@code embedding}, {@code embedding_model} and {@code embedded_at} change.
 * <p>
 * Runs in the background every time the application is ready:
 * <ul>
 *   <li>a chunk without a vector, or with the vector of another model than the
 *       configured one ({@link EmbeddingService#modelName()}), is always
 *       re-embedded — so switching the embedding model needs nothing but a
 *       restart. Until the run finishes, search compares the new model's query
 *       vectors with the old model's chunk vectors, which rank arbitrarily;</li>
 *   <li>with {@code orbit.embedding.reembed-before} (an ISO-8601 instant) set,
 *       so is every chunk embedded before that instant — for a change the
 *       model name does not show, such as a new prefix.</li>
 * </ul>
 * The job is idempotent — a re-embedded chunk carries the current model name
 * and a fresh {@code embedded_at} and no longer matches — so the property can
 * stay set across restarts.
 * <p>
 * Each embedding call runs outside a transaction; only the single-row write is
 * transactional, so no database connection is held across a network call.
 */
@Service
public class ReembeddingService {

    private static final Logger log = LoggerFactory.getLogger(ReembeddingService.class);

    private static final int BATCH_SIZE = 96;
    /** Chunks per embedding request. */
    private static final int EMBED_BATCH = 32;

    private final ChunkRepository chunks;
    private final EmbeddingService embeddingService;
    private final TransactionOperations transactions;
    private final String reembedBefore;

    public ReembeddingService(ChunkRepository chunks,
                              EmbeddingService embeddingService,
                              TransactionOperations transactions,
                              @Value("${orbit.embedding.reembed-before:}") String reembedBefore) {
        this.chunks = chunks;
        this.embeddingService = embeddingService;
        this.transactions = transactions;
        this.reembedBefore = reembedBefore;
    }

    /** Start the re-embedding run on its own thread, so startup is not delayed. */
    @EventListener(ApplicationReadyEvent.class)
    public void reembedOnStartup() {
        Instant before = cutoff();
        Thread worker = new Thread(() -> reembedStale(before), "chunk-reembedding");
        worker.setDaemon(true);
        worker.start();
    }

    /** The configured cutoff, or the epoch (no chunk is older) when none is set or it cannot be read. */
    Instant cutoff() {
        if (reembedBefore == null || reembedBefore.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            Instant before = Instant.parse(reembedBefore.trim());
            if (before.isAfter(Instant.now())) {
                log.warn("orbit.embedding.reembed-before={} is in the future: every chunk is re-embedded "
                        + "on each start until that instant has passed", before);
            }
            return before;
        } catch (DateTimeParseException e) {
            log.error("orbit.embedding.reembed-before=\"{}\" is not an ISO-8601 instant "
                    + "(e.g. 2026-11-15T12:00:00Z) — ignored", reembedBefore);
            return Instant.EPOCH;
        }
    }

    /**
     * Re-embed every chunk whose embedding is missing, from another model than
     * the configured one, or older than {@code before}.
     * Chunks go to the provider {@link #EMBED_BATCH} at a time — one request per
     * batch instead of one per chunk, which matters against a hosted model. When
     * a batch fails its chunks are embedded one by one, so a single bad chunk
     * costs only itself; a chunk that still fails is logged and left stale for
     * the next run.
     *
     * @return the number of chunks re-embedded
     */
    public int reembedStale(Instant before) {
        String model = embeddingService.modelName();
        int reembedded = 0;
        int failed = 0;
        long afterId = 0L;

        while (true) {
            List<Object[]> page = chunks.findStaleEmbeddings(before, model, afterId, PageRequest.of(0, BATCH_SIZE));
            if (page.isEmpty()) {
                break;
            }
            afterId = (Long) page.getLast()[0];
            for (int start = 0; start < page.size(); start += EMBED_BATCH) {
                List<Object[]> batch = page.subList(start, Math.min(page.size(), start + EMBED_BATCH));
                List<float[]> vectors = embedBatch(batch);
                for (int i = 0; i < batch.size(); i++) {
                    Long chunkId = (Long) batch.get(i)[0];
                    float[] vector = vectors.get(i);
                    if (vector == null) {
                        failed++;
                        continue;
                    }
                    String literal = VectorLiteral.of(vector);
                    Integer updated = transactions.execute(status ->
                            chunks.updateEmbedding(chunkId, literal, model, Instant.now()));
                    if (updated != null && updated > 0) {
                        reembedded++;
                    }
                }
            }
            log.info("Re-embedding progress: {} done, {} failed (up to chunk #{})", reembedded, failed, afterId);
        }

        if (reembedded > 0 || failed > 0) {
            log.info("Re-embedding with {} finished: {} chunk(s) re-embedded, {} failed", model, reembedded, failed);
        }
        return reembedded;
    }

    /** One vector per (id, content) row, in order; {@code null} where the embedding failed. */
    private List<float[]> embedBatch(List<Object[]> batch) {
        List<String> texts = batch.stream().map(row -> (String) row[1]).toList();
        try {
            List<float[]> vectors = embeddingService.embedAll(texts, EmbeddingKind.PASSAGE);
            if (vectors.size() == batch.size()) {
                return vectors;
            }
            log.warn("Re-embedding batch returned {} vectors for {} chunks — embedding them one by one",
                    vectors.size(), batch.size());
        } catch (Exception e) {
            log.warn("Re-embedding batch of {} failed ({}) — embedding them one by one",
                    batch.size(), e.getMessage());
        }
        List<float[]> vectors = new ArrayList<>(batch.size());
        for (Object[] row : batch) {
            try {
                vectors.add(embeddingService.embed((String) row[1], EmbeddingKind.PASSAGE));
            } catch (Exception e) {
                log.warn("Re-embedding failed for chunk #{}: {}", row[0], e.getMessage());
                vectors.add(null);
            }
        }
        return vectors;
    }
}
