package com.orbit.app.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.orbit.app.controllers.dto.IngestionJobDto;
import com.orbit.app.controllers.dto.IngestionJobsDto;
import com.orbit.app.controllers.dto.IngestionRequest;
import com.orbit.app.ingestion.IngestionService;

/**
 * Thin REST controller for ingestion. All business logic (URL building,
 * provenance mapping, upload validation, queueing) lives in
 * {@link IngestionService}. Submitting a source answers {@code 202 Accepted}
 * with the queued job; {@code GET /jobs?ids=…} reports its progress.
 */
@RestController
@RequestMapping("/api/v1/ingestion")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ResponseEntity<IngestionJobDto> ingest(@RequestBody IngestionRequest request) {
        return ResponseEntity.accepted().body(ingestionService.ingest(request));
    }

    @GetMapping("/jobs")
    public IngestionJobsDto jobs(@RequestParam("ids") List<Long> ids) {
        return ingestionService.jobs(ids);
    }

    @PostMapping("/linkedin-pdf")
    public ResponseEntity<IngestionJobDto> uploadLinkedInPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "onboardingDraft", defaultValue = "false") boolean onboardingDraft,
            @RequestParam(value = "profileMode", required = false) String profileMode) {
        return ResponseEntity.accepted().body(ingestionService.ingestLinkedInPdf(file, onboardingDraft, profileMode));
    }

    @PostMapping("/cv-pdf")
    public ResponseEntity<IngestionJobDto> uploadCvPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "onboardingDraft", defaultValue = "false") boolean onboardingDraft,
            @RequestParam(value = "profileMode", required = false) String profileMode,
            @RequestParam(value = "displayName", required = false) String displayName) {
        return ResponseEntity.accepted()
                .body(ingestionService.ingestCvPdf(file, onboardingDraft, profileMode, displayName));
    }
}
