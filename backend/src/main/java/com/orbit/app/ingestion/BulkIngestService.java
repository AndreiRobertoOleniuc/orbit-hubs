package com.orbit.app.ingestion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.orbit.app.controllers.dto.BulkIngestRequest;
import com.orbit.app.controllers.dto.BulkIngestRequest.BulkProfile;
import com.orbit.app.controllers.dto.BulkIngestRequest.BulkSource;
import com.orbit.app.ingestion.dto.IngestionResult;
import com.orbit.app.ingestion.dto.SourceRef;

import com.orbit.domain.entity.Hub;
import com.orbit.domain.entity.HubMembership;
import com.orbit.domain.entity.HubMembershipId;
import com.orbit.domain.entity.User;
import com.orbit.domain.repository.HubMembershipRepository;
import com.orbit.domain.repository.HubRepository;
import com.orbit.domain.repository.UserRepository;

/**
 * Test-only bulk ingest service. Runs asynchronously — POST returns a job ID
 * immediately, processing happens in a background thread. Poll
 * {@code getJobStatus(jobId)} for progress.
 * <p>
 * <b>Delete this class and {@code BulkIngestController} when testing is done.</b>
 */
@Service
public class BulkIngestService {

    private static final Logger log = LoggerFactory.getLogger(BulkIngestService.class);

    private final IngestionOrchestrator orchestrator;
    private final IngestionService ingestionService;
    private final HubRepository hubs;
    private final HubMembershipRepository hubMemberships;
    private final UserRepository users;

    // In-memory job tracker (test-only, lost on restart)
    private final Map<String, JobStatus> jobs = new ConcurrentHashMap<>();

    public BulkIngestService(IngestionOrchestrator orchestrator,
                             IngestionService ingestionService,
                             HubRepository hubs,
                             HubMembershipRepository hubMemberships,
                             UserRepository users) {
        this.orchestrator = orchestrator;
        this.ingestionService = ingestionService;
        this.hubs = hubs;
        this.hubMemberships = hubMemberships;
        this.users = users;
    }

    /**
     * Job status — updated live as profiles are processed.
     */
    public record JobStatus(
            String jobId,
            String status,           // "running" | "done" | "error"
            int total,
            int processed,
            int profilesCreated,
            int profileErrors,
            int totalSources,
            int totalChunks,
            String currentProfile,
            List<String> errorDetails
    ) {}

    /**
     * Kick off a bulk ingest job. Returns immediately with a job ID.
     */
    public JobStatus startJob(BulkIngestRequest request) {
        List<BulkProfile> profiles = request.profiles();
        if (profiles == null || profiles.isEmpty()) {
            return new JobStatus(null, "error", 0, 0, 0, 0, 0, 0, null, List.of("No profiles provided"));
        }

        String jobId = "bulk-" + UUID.randomUUID().toString().substring(0, 8);
        JobStatus initial = new JobStatus(jobId, "running", profiles.size(), 0, 0, 0, 0, 0, null, new ArrayList<>());
        jobs.put(jobId, initial);

        // Run in background thread — the controller returns immediately
        Thread worker = new Thread(() -> processJob(jobId, profiles), "bulk-ingest-" + jobId);
        worker.setDaemon(true);
        worker.start();

        return initial;
    }

    public JobStatus getJobStatus(String jobId) {
        return jobs.get(jobId);
    }

    private void processJob(String jobId, List<BulkProfile> profiles) {
        log.info("Bulk ingest job {} started: {} profiles", jobId, profiles.size());

        int processed = 0;
        int profilesCreated = 0;
        int profileErrors = 0;
        int totalSources = 0;
        int totalChunks = 0;
        List<String> errorDetails = new ArrayList<>();

        for (int i = 0; i < profiles.size(); i++) {
            BulkProfile profile = profiles.get(i);
            String fakeOwnerId = "bulk-test-" + UUID.randomUUID();

            // Create a fake user row so the entities.owner_id FK is satisfied
            if (!users.existsById(fakeOwnerId)) {
                users.save(new User(fakeOwnerId, null, profile.name()));
            }

            // Update job status with current profile
            updateJob(jobId, "running", profiles.size(), processed, profilesCreated,
                    profileErrors, totalSources, totalChunks, profile.name(), errorDetails);

            try {
                int profileSources = 0;
                int profileChunks = 0;
                Long entityId = null;

                for (BulkSource source : profile.sources()) {
                    try {
                        String url = ingestionService.buildUrl(
                                new com.orbit.app.controllers.dto.IngestionRequest(
                                        source.sourceType(), source.context()));
                        String[] prov = ingestionService.provenanceFor(source.sourceType());

                        SourceRef ref = new SourceRef(
                                url, source.sourceType(), prov[0], prov[1],
                                source.context(), fakeOwnerId);

                        List<IngestionResult> results = orchestrator.ingestMulti(ref);
                        profileSources += results.size();
                        profileChunks += results.stream()
                                .mapToInt(IngestionResult::chunkCount).sum();

                        if (entityId == null) {
                            entityId = results.stream()
                                    .filter(r -> r.entityId() != null)
                                    .map(IngestionResult::entityId)
                                    .findFirst().orElse(null);
                        }

                        log.info("Job {} profile #{} ({}) source {}: {} results, {} chunks",
                                jobId, i, profile.name(), source.sourceType(),
                                results.size(),
                                results.stream().mapToInt(IngestionResult::chunkCount).sum());

                    } catch (Exception e) {
                        log.error("Job {} source {} failed for profile #{} ({}): {}",
                                jobId, source.sourceType(), i, profile.name(), e.getMessage());
                        errorDetails.add("Profile #" + i + " (" + profile.name() +
                                ") source " + source.sourceType() + ": " + e.getMessage());
                    }
                }

                // Assign hubs
                if (entityId != null && profile.hubs() != null) {
                    final Long eid = entityId;
                    for (String hubSlug : profile.hubs()) {
                        hubs.findBySlug(hubSlug).ifPresent(hub -> {
                            if (!hubMemberships.existsById(new HubMembershipId(eid, hub.getId()))) {
                                hubMemberships.save(new HubMembership(
                                        eid, hub.getId(), "FHNW researcher", "confirmed"));
                                log.info("Job {} assigned entity #{} to hub '{}'", jobId, eid, hubSlug);
                            } else {
                                log.debug("Job {} entity #{} already in hub '{}'", jobId, eid, hubSlug);
                            }
                        });
                    }
                }

                if (profileSources > 0) profilesCreated++;
                totalSources += profileSources;
                totalChunks += profileChunks;

            } catch (Exception e) {
                profileErrors++;
                errorDetails.add("Profile #" + i + " (" + profile.name() + "): " + e.getMessage());
                log.error("Job {} bulk ingest failed for profile #{} ({}): {}",
                        jobId, i, profile.name(), e.getMessage());
            }

            processed++;
            updateJob(jobId, "running", profiles.size(), processed, profilesCreated,
                    profileErrors, totalSources, totalChunks, profile.name(), errorDetails);

            // Delay between profiles to avoid API rate limits (OpenAlex, etc.)
            try {
                Thread.sleep(2000); // 2 seconds between profiles
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        String finalStatus = profileErrors == 0 ? "done" : "done";
        updateJob(jobId, finalStatus, profiles.size(), processed, profilesCreated,
                profileErrors, totalSources, totalChunks, null, errorDetails);

        log.info("Bulk ingest job {} complete: {} profiles, {} sources, {} chunks, {} errors",
                jobId, profilesCreated, totalSources, totalChunks, profileErrors);
    }

    private void updateJob(String jobId, String status, int total, int processed,
                           int created, int errors, int sources, int chunks,
                           String current, List<String> errorDetails) {
        jobs.put(jobId, new JobStatus(jobId, status, total, processed, created,
                errors, sources, chunks, current, new ArrayList<>(errorDetails)));
    }
}