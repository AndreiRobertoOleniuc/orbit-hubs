package com.orbit.controllers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.orbit.app.auth.CurrentUser;
import com.orbit.app.ingestion.IngestionOrchestrator;
import com.orbit.app.ingestion.dto.IngestionResult;
import com.orbit.app.ingestion.dto.SourceRef;
import com.orbit.app.ingestion.website.WebsiteContentExtractor;

/**
 * REST API for triggering entity ingestion from external sources.
 * <p>
 * In Stage 1, this is an admin/internal endpoint for testing ingestion.
 * Later stages will add onboarding flows with user consent.
 */
@RestController
@RequestMapping("/api/v1/ingestion")
public class IngestionController {

    private static final Logger log = LoggerFactory.getLogger(IngestionController.class);

    private final IngestionOrchestrator orchestrator;
    private final CurrentUser currentUser;

    public IngestionController(IngestionOrchestrator orchestrator, CurrentUser currentUser) {
        this.orchestrator = orchestrator;
        this.currentUser = currentUser;
    }

    /**
     * Ingest a single source. The request specifies the source type and
     * source-specific context (e.g. GitHub username, ORCID iD).
     * <p>
     * Example:
     * <pre>{@code
     * POST /api/v1/ingestion
     * {
     *   "sourceType": "github",
     *   "context": { "github_login": "torvalds" }
     * }
     * }</pre>
     */
    @PostMapping
    public Map<String, Object> ingest(@RequestBody IngestionRequest request) {
        String ownerId = currentUser.userId().orElse(null);
        log.info("Ingestion request from user {}: sourceType={}, context={}", ownerId, request.sourceType(), request.context());

        String url = buildUrl(request);
        String[] provenance = provenanceFor(request.sourceType());
        SourceRef ref = new SourceRef(
                url,
                request.sourceType(),
                provenance[0],
                provenance[1],
                request.context(),
                ownerId
        );

        List<IngestionResult> results = orchestrator.ingestMulti(ref);

        return summarizeResults(request.sourceType(), results);
    }

    /**
     * Upload and ingest a LinkedIn profile PDF.
     * <p>
     * The PDF is sent as a multipart file upload in the request body.
     * The server saves it to a temp file, parses it, and ingests it.
     * The temp file is deleted after processing.
     * <p>
     * Example (curl):
     * <pre>{@code
     * POST /api/v1/ingestion/linkedin-pdf
     * Content-Type: multipart/form-data
     *
     * file=@Profile.pdf
     * }</pre>
     */
    @PostMapping("/linkedin-pdf")
    public Map<String, Object> uploadLinkedInPdf(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        log.info("LinkedIn PDF upload: filename={}, size={} bytes", file.getOriginalFilename(), file.getSize());

        // 1. Save uploaded bytes to a temp file
        Path tempFile = null;
        try {
            byte[] pdfBytes = file.getBytes();
            String contentHash = sha256Hex(pdfBytes);
            tempFile = Files.createTempFile("orbit-linkedin-", ".pdf");
            Files.write(tempFile, pdfBytes);

            // 2. Build SourceRef with content-hash-based idempotency URL
            //    Re-uploading the same PDF → same hash → dedup skip
            String ownerId = currentUser.userId().orElse(null);
            Map<String, String> context = new HashMap<>();
            context.put("file_path", tempFile.toString());

            SourceRef ref = new SourceRef(
                    "linkedin:pdf:" + contentHash,
                    "linkedin_pdf",
                    "user_supplied",
                    "user_consent",
                    context,
                    ownerId
            );

            // 3. Ingest
            List<IngestionResult> results = orchestrator.ingestMulti(ref);

            return summarizeResults("linkedin_pdf", results);

        } catch (Exception e) {
            log.error("LinkedIn PDF upload failed: {}", e.getMessage(), e);
            return Map.of(
                    "sourceType", "linkedin_pdf",
                    "sources", 0,
                    "ingested", 0,
                    "skipped", 0,
                    "errors", 1,
                    "totalChunks", 0,
                    "results", List.of(),
                    "error", e.getMessage()
            );
        } finally {
            // 4. Clean up temp file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    log.warn("Failed to delete temp file: {}", tempFile);
                }
            }
        }
    }

    private Map<String, Object> summarizeResults(String sourceType, List<IngestionResult> results) {

        int totalChunks = results.stream().mapToInt(IngestionResult::chunkCount).sum();
        int ingested = (int) results.stream().filter(r -> r.status() == IngestionResult.Status.INGESTED).count();
        int skipped = (int) results.stream().filter(r -> r.status() == IngestionResult.Status.SKIPPED).count();
        int errors = (int) results.stream().filter(r -> r.status() == IngestionResult.Status.ERROR).count();

        return Map.of(
                "sourceType", sourceType,
                "sources", results.size(),
                "ingested", ingested,
                "skipped", skipped,
                "errors", errors,
                "totalChunks", totalChunks,
                "results", results
        );
    }

    /**
     * Return [provenance, consentBasis] for a given source type.
     */
    private String[] provenanceFor(String sourceType) {
        return switch (sourceType) {
            case "github", "orcid_pub", "zefix" -> new String[]{"harvested", "public_data"};
            case "linkedin_pdf" -> new String[]{"user_supplied", "user_consent"};
            case "manual_text" -> new String[]{"user_supplied", "self_declared"};
            case "personal_site", "company_site" -> new String[]{"harvested", "public_data"};
            default -> new String[]{"harvested", "public_data"};
        };
    }

    private String buildUrl(IngestionRequest request) {
        return switch (request.sourceType()) {
            case "github" -> "https://github.com/" + request.context().get("github_login");
            case "orcid_pub" -> {
                String orcid = request.context().get("orcid");
                if (orcid != null && !orcid.isBlank()) {
                    yield "https://orcid.org/" + orcid;
                }
                String authorId = request.context().get("openalex_author_id");
                if (authorId != null && !authorId.isBlank()) {
                    yield "https://openalex.org/authors/" + authorId;
                }
                yield "unknown";
            }
            case "linkedin_pdf" -> {
                // Use the file path as the source URL for idempotency.
                // In production, this would be the LinkedIn profile URL.
                String filePath = request.context().get("file_path");
                yield filePath != null ? "linkedin:pdf:" + filePath : "linkedin:pdf:unknown";
            }
            case "zefix" -> {
                String uid = request.context().get("zefix_uid");
                if (uid != null && !uid.isBlank()) {
                    yield "zefix:" + uid;
                }
                String companyName = request.context().get("company_name");
                yield companyName != null ? "zefix:name:" + companyName : "zefix:unknown";
            }
            case "manual_text" -> {
                String text = request.context().get("text");
                yield text != null ? "manual:text:" + sha256Hex(text.getBytes()) : "manual:text:unknown";
            }
            case "personal_site", "company_site" -> {
                String url = request.context().get("url");
                yield url != null ? request.sourceType() + ":" + WebsiteContentExtractor.normalizeDomain(url) : "unknown";
            }
            default -> request.context().getOrDefault("url", "unknown");
        };
    }

    public record IngestionRequest(
            String sourceType,
            Map<String, String> context
    ) {}

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}