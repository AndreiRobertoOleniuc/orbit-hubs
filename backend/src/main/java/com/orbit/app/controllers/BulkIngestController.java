package com.orbit.app.controllers;

import com.orbit.app.controllers.dto.BulkIngestRequest;
import com.orbit.app.ingestion.BulkIngestService;
import com.orbit.app.ingestion.BulkIngestService.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * TEST-ONLY endpoint for bulk profile creation. No auth required.
 * <p>
 * POST /api/v1/test/bulk-ingest — starts a background job, returns immediately
 * with a job ID. Processing (fetch → LLM → embed → enrich) runs in a daemon
 * thread. Poll GET /api/v1/test/bulk-ingest/{jobId} for progress.
 * <p>
 * <b>Delete this class, BulkIngestService, and BulkIngestRequest when done.</b>
 */
@RestController
public class BulkIngestController {

    private static final Logger log = LoggerFactory.getLogger(BulkIngestController.class);

    private final BulkIngestService bulkIngestService;

    public BulkIngestController(BulkIngestService bulkIngestService) {
        this.bulkIngestService = bulkIngestService;
    }

    @PostMapping("/api/v1/test/bulk-ingest")
    public ResponseEntity<JobStatus> bulkIngest(@RequestBody BulkIngestRequest request) {
        int count = request.profiles() != null ? request.profiles().size() : 0;
        log.info("Bulk ingest request: {} profiles", count);
        JobStatus status = bulkIngestService.startJob(request);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/api/v1/test/bulk-ingest/{jobId}")
    public ResponseEntity<JobStatus> jobStatus(@PathVariable String jobId) {
        JobStatus status = bulkIngestService.getJobStatus(jobId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }
}