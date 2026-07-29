package com.orbit.app.ingestion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.orbit.app.auth.CurrentUser;
import com.orbit.app.ingestion.dto.IngestionResult;
import com.orbit.app.ingestion.dto.IngestionStatus;
import com.orbit.app.ingestion.dto.SourceRef;
import com.orbit.app.ingestion.website.WebsiteContentExtractor;
import com.orbit.app.util.HashUtils;
import com.orbit.app.controllers.dto.IngestionRequest;
import com.orbit.domain.repository.UserRepository;

/**
 * Application service for ingestion request handling. Owns URL construction,
 * provenance mapping, LinkedIn PDF temp-file lifecycle, and result aggregation
 * — everything that was previously inline in {@code IngestionController}.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final IngestionOrchestrator orchestrator;
    private final CurrentUser currentUser;
    private final UserRepository users;

    public IngestionService(IngestionOrchestrator orchestrator, CurrentUser currentUser, UserRepository users) {
        this.orchestrator = orchestrator;
        this.currentUser = currentUser;
        this.users = users;
    }

    /**
     * Ingest a single source. Builds the SourceRef from the request, delegates
     * to the orchestrator, and returns a summary map.
     */
    public Map<String, Object> ingest(IngestionRequest request) {
        validateProfileMode(request.createsDraft(), request.profileMode());
        String ownerId = currentUser.userId().orElse(null);
        log.info("Ingestion request from user {}: sourceType={}, context={}",
                ownerId, request.sourceType(), request.context());

        Map<String, String> context = new HashMap<>(request.context());
        if ("manual_text".equals(request.sourceType()) && ownerId != null) {
            users.findById(ownerId)
                    .map(user -> user.getDisplayName())
                    .filter(name -> !name.isBlank())
                    .ifPresent(name -> context.putIfAbsent("name", name));
        }

        IngestionRequest enrichedRequest = new IngestionRequest(
                request.sourceType(), context, request.onboardingDraft(), request.profileMode());
        String url = buildUrl(enrichedRequest);
        String[] provenance = provenanceFor(request.sourceType());
        SourceRef ref = new SourceRef(
                url,
                request.sourceType(),
                provenance[0],
                provenance[1],
                context,
                ownerId,
                request.createsDraft(),
                request.profileMode()
        );

        List<IngestionResult> results = orchestrator.ingestMulti(ref);
        return summarizeResults(request.sourceType(), results);
    }

    /**
     * Upload and ingest a LinkedIn profile PDF. Saves to a temp file, builds a
     * content-hash-based SourceRef for idempotency, ingests, and cleans up.
     */
    public Map<String, Object> ingestLinkedInPdf(MultipartFile file) {
        return ingestLinkedInPdf(file, false, null);
    }

    public Map<String, Object> ingestLinkedInPdf(MultipartFile file, boolean onboardingDraft, String profileMode) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && !originalFilename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("File must be uploaded as a PDF");
        }
        validateProfileMode(onboardingDraft, profileMode);

        log.info("LinkedIn PDF upload: filename={}, size={} bytes",
                file.getOriginalFilename(), file.getSize());

        Path tempFile = null;
        try {
            byte[] pdfBytes = file.getBytes();
            String contentHash = HashUtils.sha256Hex(pdfBytes);
            tempFile = Files.createTempFile("orbit-linkedin-", ".pdf");
            Files.write(tempFile, pdfBytes);

            String ownerId = currentUser.userId().orElse(null);
            Map<String, String> context = new HashMap<>();
            context.put("file_path", tempFile.toString());

            SourceRef ref = new SourceRef(
                    "linkedin:pdf:" + contentHash,
                    "linkedin_pdf",
                    "user_supplied",
                    "user_consent",
                    context,
                    ownerId,
                    onboardingDraft,
                    profileMode
            );

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
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    log.warn("Failed to delete temp file: {}", tempFile);
                }
            }
        }
    }

    /**
     * Upload a regular, text-based CV PDF. Text extraction happens in the CV
     * adapter; the standard enrichment pipeline then derives summary and tags.
     */
    public Map<String, Object> ingestCvPdf(
            MultipartFile file, boolean onboardingDraft, String profileMode, String displayName) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && !originalFilename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("CV must be uploaded as a PDF file");
        }
        validateProfileMode(onboardingDraft, profileMode);

        log.info("CV PDF upload: filename={}, size={} bytes", file.getOriginalFilename(), file.getSize());
        Path tempFile = null;
        try {
            byte[] pdfBytes = file.getBytes();
            String contentHash = HashUtils.sha256Hex(pdfBytes);
            tempFile = Files.createTempFile("orbit-cv-", ".pdf");
            Files.write(tempFile, pdfBytes);

            String ownerId = currentUser.userId().orElse(null);
            Map<String, String> context = new HashMap<>();
            context.put("file_path", tempFile.toString());
            context.put("filename", originalFilename == null ? "Lebenslauf.pdf" : originalFilename);
            if (displayName != null && !displayName.isBlank()) {
                context.put("name", displayName.trim());
            } else if (ownerId != null) {
                users.findById(ownerId)
                        .map(user -> user.getDisplayName())
                        .filter(name -> !name.isBlank())
                        .ifPresent(name -> context.put("name", name));
            }

            SourceRef ref = new SourceRef(
                    "cv:pdf:" + contentHash,
                    "cv_pdf",
                    "user_supplied",
                    "user_consent",
                    context,
                    ownerId,
                    onboardingDraft,
                    profileMode);
            return summarizeResults("cv_pdf", orchestrator.ingestMulti(ref));
        } catch (Exception e) {
            log.error("CV PDF upload failed: {}", e.getMessage(), e);
            return Map.of(
                    "sourceType", "cv_pdf",
                    "sources", 0,
                    "ingested", 0,
                    "skipped", 0,
                    "errors", 1,
                    "totalChunks", 0,
                    "results", List.of(),
                    "error", e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    log.warn("Failed to delete CV temp file: {}", tempFile);
                }
            }
        }
    }

    // ─── Helpers ───

    private static void validateProfileMode(boolean onboardingDraft, String profileMode) {
        if (!onboardingDraft) return;
        if (!"supply".equals(profileMode) && !"demand".equals(profileMode)) {
            throw new IllegalArgumentException("Onboarding profile mode must be supply or demand");
        }
    }

    public Map<String, Object> summarizeResults(String sourceType, List<IngestionResult> results) {
        int totalChunks = results.stream().mapToInt(IngestionResult::chunkCount).sum();
        int ingested = (int) results.stream().filter(r -> r.status() == IngestionStatus.INGESTED).count();
        int skipped = (int) results.stream().filter(r -> r.status() == IngestionStatus.SKIPPED).count();
        int errors = (int) results.stream().filter(r -> r.status() == IngestionStatus.ERROR).count();

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

    /** [provenance, consentBasis] for a given source type. */
    public String[] provenanceFor(String sourceType) {
        return switch (sourceType) {
            case "github", "orcid_pub", "zefix" -> new String[]{"harvested", "public_data"};
            case "linkedin_pdf", "cv_pdf" -> new String[]{"user_supplied", "user_consent"};
            case "manual_text" -> new String[]{"user_supplied", "self_declared"};
            case "personal_site", "company_site" -> new String[]{"harvested", "public_data"};
            default -> new String[]{"harvested", "public_data"};
        };
    }

    public String buildUrl(IngestionRequest request) {
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
                yield text != null ? "manual:text:" + HashUtils.sha256Hex(text.getBytes()) : "manual:text:unknown";
            }
            case "personal_site", "company_site" -> {
                String url = request.context().get("url");
                yield url != null ? request.sourceType() + ":" + WebsiteContentExtractor.normalizeDomain(url) : "unknown";
            }
            default -> request.context().getOrDefault("url", "unknown");
        };
    }
}
