package com.orbit.controllers;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.app.ingestion.IngestionOrchestrator;
import com.orbit.app.ingestion.dto.IngestionResult;
import com.orbit.app.ingestion.dto.SourceRef;

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

    public IngestionController(IngestionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
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
        log.info("Ingestion request: sourceType={}, context={}", request.sourceType(), request.context());

        String url = buildUrl(request);
        SourceRef ref = new SourceRef(
                url,
                request.sourceType(),
                "harvested",
                "public_data",
                request.context()
        );

        List<IngestionResult> results = orchestrator.ingestMulti(ref);

        int totalChunks = results.stream().mapToInt(IngestionResult::chunkCount).sum();
        int ingested = (int) results.stream().filter(r -> r.status() == IngestionResult.Status.INGESTED).count();
        int skipped = (int) results.stream().filter(r -> r.status() == IngestionResult.Status.SKIPPED).count();
        int errors = (int) results.stream().filter(r -> r.status() == IngestionResult.Status.ERROR).count();

        return Map.of(
                "sourceType", request.sourceType(),
                "sources", results.size(),
                "ingested", ingested,
                "skipped", skipped,
                "errors", errors,
                "totalChunks", totalChunks,
                "results", results
        );
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
            default -> request.context().getOrDefault("url", "unknown");
        };
    }

    public record IngestionRequest(
            String sourceType,
            Map<String, String> context
    ) {}
}