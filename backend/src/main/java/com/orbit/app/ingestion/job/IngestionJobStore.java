package com.orbit.app.ingestion.job;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionOperations;

/**
 * The {@code ingestion_jobs} queue (V20). Claims are serialized by a
 * transaction-scoped advisory lock, so the ordering rules below hold across
 * several application instances too:
 * <ul>
 *   <li>one job at a time per owner, in submission order — the first source of
 *       an onboarding creates the entity the later ones join;</li>
 *   <li>an enrichment waits until the owner has no source or resync job left,
 *       so one onboarding is enriched once, not once per source;</li>
 *   <li>at most {@code maxRunningPerSourceType} jobs per source type run at
 *       once, which bounds the load on each external provider.</li>
 * </ul>
 */
@Repository
public class IngestionJobStore {

    /** Advisory lock key of the claim; any constant, as long as every claimer uses it. */
    private static final long CLAIM_LOCK = 7_105_196_501L;

    private static final String COLUMNS = """
            id, owner_id, kind, source_type, source_ref::text AS source_ref, payload, entity_id, status,
            attempts, max_attempts, result::text AS result, error, created_at, finished_at""";

    private static final String CLAIM_SQL = """
            UPDATE ingestion_jobs
            SET status = 'running', attempts = attempts + 1, locked_at = now(), updated_at = now()
            WHERE id = (
              SELECT j.id FROM ingestion_jobs j
              WHERE j.status = 'queued' AND j.next_attempt_at <= now()
                AND (j.owner_id IS NULL OR NOT EXISTS (
                      SELECT 1 FROM ingestion_jobs o
                      WHERE o.owner_id = j.owner_id AND o.id <> j.id
                        AND (o.status = 'running'
                             OR (o.status = 'queued' AND o.kind <> 'enrich'
                                 AND (j.kind = 'enrich' OR o.id < j.id)))))
                AND (j.source_type IS NULL OR (
                      SELECT count(*) FROM ingestion_jobs r
                      WHERE r.status = 'running' AND r.source_type = j.source_type) < ?)
              ORDER BY j.id
              LIMIT 1
              FOR UPDATE SKIP LOCKED)
            RETURNING %s
            """.formatted(COLUMNS);

    private final JdbcTemplate jdbc;
    private final TransactionOperations transactions;

    public IngestionJobStore(JdbcTemplate jdbc, TransactionOperations transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    /** Queue a source; {@code payload} is an uploaded file, or null. */
    public long enqueueSource(String ownerId, String sourceType, String sourceRefJson, byte[] payload) {
        return jdbc.queryForObject("""
                INSERT INTO ingestion_jobs (owner_id, kind, source_type, source_ref, payload)
                VALUES (?, 'source', ?, CAST(? AS jsonb), ?)
                RETURNING id
                """, Long.class, ownerId, sourceType, sourceRefJson, payload);
    }

    /** Queue a re-ingestion of every source of {@code entityId}. */
    public long enqueueResync(String ownerId, long entityId) {
        return jdbc.queryForObject("""
                INSERT INTO ingestion_jobs (owner_id, kind, entity_id) VALUES (?, 'resync', ?)
                RETURNING id
                """, Long.class, ownerId, entityId);
    }

    /**
     * Queue a resync of each given entity that has a source — of every such
     * entity when {@code entityIds} is null or empty. An entity whose resync is
     * already queued or running is left out. Each job belongs to the entity's
     * owner, so it waits its turn behind that owner's other jobs.
     *
     * @return the number of jobs queued
     */
    public int enqueueResyncs(Collection<Long> entityIds) {
        Long[] ids = entityIds == null || entityIds.isEmpty() ? null : entityIds.toArray(Long[]::new);
        return jdbc.update("""
                INSERT INTO ingestion_jobs (owner_id, kind, entity_id)
                SELECT e.owner_id, 'resync', e.id
                FROM entities e
                WHERE (CAST(? AS bigint[]) IS NULL OR e.id = ANY(CAST(? AS bigint[])))
                  AND EXISTS (SELECT 1 FROM sources s WHERE s.entity_id = e.id)
                  AND NOT EXISTS (SELECT 1 FROM ingestion_jobs j
                                  WHERE j.entity_id = e.id AND j.kind = 'resync'
                                    AND j.status IN ('queued', 'running'))
                ORDER BY e.id
                """, ids, ids);
    }

    /** Number of resync jobs per status (finished ones only within the retention period). */
    public Map<String, Long> resyncCounts() {
        Map<String, Long> counts = new HashMap<>();
        jdbc.query("SELECT status, count(*) AS n FROM ingestion_jobs WHERE kind = 'resync' GROUP BY status",
                (RowCallbackHandler) rs -> counts.put(rs.getString("status"), rs.getLong("n")));
        return counts;
    }

    /**
     * Queue the enrichment of {@code entityId}, or push back the one already
     * queued. It runs no earlier than {@code delay} from now, and only once the
     * owner's queued sources are done — the delay covers the gap between two
     * submissions of one onboarding.
     */
    public void armEnrichment(String ownerId, long entityId, Duration delay) {
        jdbc.update("""
                INSERT INTO ingestion_jobs (owner_id, kind, entity_id, next_attempt_at)
                VALUES (?, 'enrich', ?, now() + make_interval(secs => ?))
                ON CONFLICT (entity_id) WHERE kind = 'enrich' AND status = 'queued'
                DO UPDATE SET next_attempt_at = EXCLUDED.next_attempt_at, updated_at = now()
                """, ownerId, entityId, seconds(delay));
    }

    /** Claim the next runnable job, marking it running. */
    public Optional<IngestionJob> claimNext(int maxRunningPerSourceType) {
        return transactions.execute(status -> {
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class, CLAIM_LOCK);
            return jdbc.query(CLAIM_SQL, IngestionJobStore::map, maxRunningPerSourceType).stream().findFirst();
        });
    }

    /** Refresh the heartbeat of jobs this process is running. */
    public void heartbeat(Collection<Long> ids) {
        if (ids.isEmpty()) return;
        jdbc.update("UPDATE ingestion_jobs SET locked_at = now() WHERE status = 'running' AND id = ANY(?)",
                (Object) ids.toArray(Long[]::new));
    }

    public void succeed(long id, Long entityId, String resultJson) {
        jdbc.update("""
                UPDATE ingestion_jobs
                SET status = 'succeeded', entity_id = COALESCE(?, entity_id), result = CAST(? AS jsonb),
                    error = NULL, payload = NULL, locked_at = NULL, finished_at = now(), updated_at = now()
                WHERE id = ?
                """, entityId, resultJson, id);
    }

    /** Queue the job again after {@code delay}; the upload, if any, is kept for the retry. */
    public void retryLater(long id, String error, Duration delay) {
        jdbc.update("""
                UPDATE ingestion_jobs
                SET status = 'queued', error = ?, locked_at = NULL,
                    next_attempt_at = now() + make_interval(secs => ?), updated_at = now()
                WHERE id = ?
                """, error, seconds(delay), id);
    }

    public void fail(long id, String error) {
        jdbc.update("""
                UPDATE ingestion_jobs
                SET status = 'failed', error = ?, payload = NULL, locked_at = NULL,
                    finished_at = now(), updated_at = now()
                WHERE id = ?
                """, error, id);
    }

    /**
     * Running jobs whose heartbeat is older than {@code staleAfter} belonged to a
     * process that stopped: queue them again, or fail them when out of attempts.
     *
     * @return the number of jobs recovered
     */
    public int recoverStale(Duration staleAfter) {
        return jdbc.update("""
                UPDATE ingestion_jobs
                SET status = CASE WHEN attempts < max_attempts THEN 'queued' ELSE 'failed' END,
                    finished_at = CASE WHEN attempts < max_attempts THEN NULL ELSE now() END,
                    payload = CASE WHEN attempts < max_attempts THEN payload END,
                    error = 'The worker stopped while running this job',
                    locked_at = NULL, next_attempt_at = now(), updated_at = now()
                WHERE status = 'running' AND locked_at < now() - make_interval(secs => ?)
                """, seconds(staleAfter));
    }

    /** Delete jobs finished longer than {@code retention} ago. */
    public int purgeFinished(Duration retention) {
        return jdbc.update("DELETE FROM ingestion_jobs WHERE finished_at < now() - make_interval(secs => ?)",
                seconds(retention));
    }

    /** The given jobs, if they belong to {@code ownerId}, in id order. */
    public List<IngestionJob> findOwned(String ownerId, Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return jdbc.query("SELECT " + COLUMNS + " FROM ingestion_jobs WHERE owner_id = ? AND id = ANY(?) ORDER BY id",
                IngestionJobStore::map, ownerId, ids.toArray(Long[]::new));
    }

    /** Whether an enrichment of the owner's entities is queued or running. */
    public boolean enrichmentPending(String ownerId) {
        Boolean pending = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM ingestion_jobs
                               WHERE owner_id = ? AND kind = 'enrich' AND status IN ('queued', 'running'))
                """, Boolean.class, ownerId);
        return Boolean.TRUE.equals(pending);
    }

    private static double seconds(Duration duration) {
        return duration.toMillis() / 1000.0;
    }

    private static IngestionJob map(ResultSet rs, int row) throws SQLException {
        return new IngestionJob(
                rs.getLong("id"),
                rs.getString("owner_id"),
                rs.getString("kind"),
                rs.getString("source_type"),
                rs.getString("source_ref"),
                rs.getBytes("payload"),
                rs.getObject("entity_id", Long.class),
                rs.getString("status"),
                rs.getInt("attempts"),
                rs.getInt("max_attempts"),
                rs.getString("result"),
                rs.getString("error"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("finished_at")));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
