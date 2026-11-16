package com.orbit.app.ingestion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.orbit.app.auth.CurrentUser;
import com.orbit.app.controllers.dto.IngestionJobDto;
import com.orbit.app.controllers.dto.IngestionJobsDto;
import com.orbit.app.ingestion.dto.SourceRef;
import com.orbit.app.ingestion.job.IngestionJobService;
import com.orbit.app.ingestion.website.WebsiteContentExtractor;
import com.orbit.app.util.HashUtils;
import com.orbit.app.controllers.dto.IngestionRequest;
import com.orbit.domain.repository.UserRepository;

/**
 * Application service for ingestion request handling. Owns URL construction,
 * provenance mapping and upload validation, then queues the work as an
 * {@link com.orbit.app.ingestion.job.IngestionJob}: every method returns as
 * soon as the job is stored, and the caller polls
 * {@link #jobs(List)} for the outcome.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final IngestionJobService jobs;
    private final CurrentUser currentUser;
    private final UserRepository users;

    public IngestionService(IngestionJobService jobs, CurrentUser currentUser, UserRepository users) {
        this.jobs = jobs;
        this.currentUser = currentUser;
        this.users = users;
    }

    /** Queue a single source described by the request. */
    public IngestionJobDto ingest(IngestionRequest request) {
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
        return jobs.enqueueSource(ref, null);
    }

    /**
     * Queue a LinkedIn profile PDF. The URL is derived from the content hash, so
     * uploading the same PDF again is recognised as unchanged.
     */
    public IngestionJobDto ingestLinkedInPdf(MultipartFile file) {
        return ingestLinkedInPdf(file, false, null);
    }

    public IngestionJobDto ingestLinkedInPdf(MultipartFile file, boolean onboardingDraft, String profileMode) {
        byte[] pdfBytes = readPdf(file, "File must be uploaded as a PDF");
        validateProfileMode(onboardingDraft, profileMode);
        log.info("LinkedIn PDF upload: filename={}, size={} bytes", file.getOriginalFilename(), file.getSize());

        SourceRef ref = new SourceRef(
                "linkedin:pdf:" + HashUtils.sha256Hex(pdfBytes),
                "linkedin_pdf",
                "user_supplied",
                "user_consent",
                Map.of(),
                currentUser.userId().orElse(null),
                onboardingDraft,
                profileMode
        );
        return jobs.enqueueSource(ref, pdfBytes);
    }

    /**
     * Queue a regular, text-based CV PDF. Text extraction happens in the CV
     * adapter; the standard enrichment pipeline then derives summary and tags.
     */
    public IngestionJobDto ingestCvPdf(
            MultipartFile file, boolean onboardingDraft, String profileMode, String displayName) {
        byte[] pdfBytes = readPdf(file, "CV must be uploaded as a PDF file");
        validateProfileMode(onboardingDraft, profileMode);
        log.info("CV PDF upload: filename={}, size={} bytes", file.getOriginalFilename(), file.getSize());

        String ownerId = currentUser.userId().orElse(null);
        String originalFilename = file.getOriginalFilename();
        Map<String, String> context = new HashMap<>();
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
                "cv:pdf:" + HashUtils.sha256Hex(pdfBytes),
                "cv_pdf",
                "user_supplied",
                "user_consent",
                context,
                ownerId,
                onboardingDraft,
                profileMode);
        return jobs.enqueueSource(ref, pdfBytes);
    }

    /** Progress of the caller's own jobs among {@code ids}. */
    public IngestionJobsDto jobs(List<Long> ids) {
        String ownerId = currentUser.userId().orElse(null);
        if (ownerId == null || ids == null || ids.isEmpty()) return new IngestionJobsDto(List.of(), true);
        if (ids.size() > MAX_POLLED_JOBS) {
            throw new IllegalArgumentException("At most " + MAX_POLLED_JOBS + " jobs per request");
        }
        return jobs.jobs(ownerId, ids);
    }

    /** One onboarding queues a handful of sources; this only bounds the query. */
    private static final int MAX_POLLED_JOBS = 50;

    private static byte[] readPdf(MultipartFile file, String notPdfMessage) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && !originalFilename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException(notPdfMessage);
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
    }

    // ─── Helpers ───

    private static void validateProfileMode(boolean onboardingDraft, String profileMode) {
        if (!onboardingDraft) return;
        if (!"supply".equals(profileMode) && !"demand".equals(profileMode)) {
            throw new IllegalArgumentException("Onboarding profile mode must be supply or demand");
        }
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
