package com.orbit.app.ingestion.job;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * Pulls ingestion jobs from {@link IngestionJobStore} and runs them on a small
 * thread pool. Polls every {@code orbit.ingestion.jobs.poll-interval}; a
 * heartbeat keeps running jobs from being taken for abandoned, and jobs whose
 * heartbeat stopped (the process died mid-run) are queued again.
 */
@Component
public class IngestionJobWorker {

    private static final Logger log = LoggerFactory.getLogger(IngestionJobWorker.class);

    private final IngestionJobStore store;
    private final IngestionJobRunner runner;
    private final boolean enabled;
    private final int workers;
    private final int maxRunningPerSourceType;
    private final Duration staleAfter;
    private final Duration retention;
    private final ExecutorService pool;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();
    private volatile Instant nextMaintenance = Instant.EPOCH;

    public IngestionJobWorker(IngestionJobStore store,
                              IngestionJobRunner runner,
                              @Value("${orbit.ingestion.jobs.enabled:true}") boolean enabled,
                              @Value("${orbit.ingestion.jobs.workers:2}") int workers,
                              @Value("${orbit.ingestion.jobs.max-running-per-source-type:2}") int maxRunningPerSourceType,
                              @Value("${orbit.ingestion.jobs.stale-after:PT2M}") Duration staleAfter,
                              @Value("${orbit.ingestion.jobs.retention:P7D}") Duration retention) {
        this.store = store;
        this.runner = runner;
        this.enabled = enabled;
        this.workers = Math.max(1, workers);
        this.maxRunningPerSourceType = Math.max(1, maxRunningPerSourceType);
        this.staleAfter = staleAfter;
        this.retention = retention;
        this.pool = Executors.newFixedThreadPool(this.workers, task -> {
            Thread thread = new Thread(task, "ingestion-job");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Claim jobs while a worker thread is free. */
    @Scheduled(fixedDelayString = "${orbit.ingestion.jobs.poll-interval:PT1S}")
    public void poll() {
        if (!enabled) return;
        try {
            maintain();
            while (running.size() < workers) {
                Optional<IngestionJob> claimed = store.claimNext(maxRunningPerSourceType);
                if (claimed.isEmpty()) return;
                IngestionJob job = claimed.get();
                running.add(job.id());
                pool.execute(() -> {
                    try {
                        runner.run(job);
                    } finally {
                        running.remove(job.id());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Ingestion job poll failed: {}", e.getMessage());
        }
    }

    /** Keep this process's running jobs from looking abandoned. */
    @Scheduled(fixedDelayString = "${orbit.ingestion.jobs.heartbeat-interval:PT20S}")
    public void heartbeat() {
        if (!enabled || running.isEmpty()) return;
        try {
            store.heartbeat(List.copyOf(running));
        } catch (Exception e) {
            log.warn("Ingestion job heartbeat failed: {}", e.getMessage());
        }
    }

    /** Once a minute: requeue abandoned jobs, delete old finished ones. */
    private void maintain() {
        Instant now = Instant.now();
        if (now.isBefore(nextMaintenance)) return;
        nextMaintenance = now.plusSeconds(60);
        int recovered = store.recoverStale(staleAfter);
        if (recovered > 0) log.warn("Recovered {} ingestion job(s) whose worker stopped", recovered);
        store.purgeFinished(retention);
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}
