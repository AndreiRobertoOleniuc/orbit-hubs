package com.orbit.app.controllers;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.orbit.app.controllers.dto.IngestionRequest;
import com.orbit.app.ingestion.IngestionService;

/**
 * Thin REST controller for ingestion. All business logic (URL building,
 * provenance mapping, LinkedIn PDF lifecycle, result aggregation) lives in
 * {@link IngestionService}.
 */
@RestController
@RequestMapping("/api/v1/ingestion")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public Map<String, Object> ingest(@RequestBody IngestionRequest request) {
        return ingestionService.ingest(request);
    }

    @PostMapping("/linkedin-pdf")
    public Map<String, Object> uploadLinkedInPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "onboardingDraft", defaultValue = "false") boolean onboardingDraft,
            @RequestParam(value = "profileMode", required = false) String profileMode) {
        return ingestionService.ingestLinkedInPdf(file, onboardingDraft, profileMode);
    }

    @PostMapping("/cv-pdf")
    public Map<String, Object> uploadCvPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "onboardingDraft", defaultValue = "false") boolean onboardingDraft,
            @RequestParam(value = "profileMode", required = false) String profileMode,
            @RequestParam(value = "displayName", required = false) String displayName) {
        return ingestionService.ingestCvPdf(file, onboardingDraft, profileMode, displayName);
    }
}
