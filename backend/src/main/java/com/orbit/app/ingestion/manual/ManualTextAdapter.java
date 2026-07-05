package com.orbit.app.ingestion.manual;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.orbit.app.ingestion.IngestionException;
import com.orbit.app.ingestion.SourceAdapter;
import com.orbit.app.ingestion.dto.EntityProfile;
import com.orbit.app.ingestion.dto.ExternalId;
import com.orbit.app.ingestion.dto.RawContent;
import com.orbit.app.ingestion.dto.SourceRef;
import com.orbit.app.llm.LlmSummaryService;

/**
 * Manual Text adapter — the simplest source type.
 * <p>
 * The user types 2-3 sentences describing themselves or their organization.
 * No API calls, no crawling, no PDF parsing — the text IS the content.
 * <p>
 * If the text is very short (e.g. "React Dev"), Gemini expands it into a
 * richer description for better embedding quality — but only elaborates on
 * what the user wrote, never invents new skills or experience.
 * <p>
 * {@code provenance = "user_supplied"}, {@code consent_basis = "self_declared"}.
 * No external identifiers — this is self-declared content.
 */
@Component
public class ManualTextAdapter implements SourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(ManualTextAdapter.class);

    /**
     * Below this character count, we ask Gemini to expand the text
     * into a richer description for better embedding quality.
     */
    private static final int EXPANSION_THRESHOLD = 200;

    private final LlmSummaryService llmSummaryService;

    public ManualTextAdapter(LlmSummaryService llmSummaryService) {
        this.llmSummaryService = llmSummaryService;
    }

    @Override
    public String sourceType() {
        return "manual_text";
    }

    // ─── fetch: no fetching, text is already in context ───

    @Override
    public RawContent fetch(SourceRef ref) throws IngestionException {
        String text = ref.context().get("text");
        if (text == null || text.isBlank()) {
            throw new IngestionException("text is required in context for manual text ingestion");
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("text", text);
        metadata.put("name", ref.context().getOrDefault("name", ""));
        metadata.put("headline", ref.context().getOrDefault("headline", ""));
        metadata.put("entity_type", ref.context().getOrDefault("entity_type", "person"));

        return new RawContent(text, metadata, Instant.now());
    }

    // ─── extractHighSignalCore: expand short text via Gemini ───

    @Override
    public String extractHighSignalCore(RawContent raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = raw.metadata();
        String text = (String) meta.getOrDefault("text", "");
        String name = (String) meta.getOrDefault("name", "");

        String coreText = text;

        // If the text is very short, expand it for better embedding quality
        if (text.length() < EXPANSION_THRESHOLD) {
            log.info("Manual text is short ({} chars) — expanding via Gemini", text.length());
            String expanded = llmSummaryService.enrichManualText(text, name);
            if (expanded != null && !expanded.isBlank()) {
                coreText = expanded;
                log.info("Expanded manual text: {} chars → {} chars", text.length(), expanded.length());
            }
        }

        // Wrap in a section heading for ChunkingService
        StringBuilder sb = new StringBuilder();
        sb.append("## Eigene Beschreibung\n");
        sb.append(coreText);

        return sb.toString().trim();
    }

    @Override
    public String buildLabel(RawContent raw) {
        return "Eigene Beschreibung";
    }

    @Override
    public List<ExternalId> extractExternalIds(RawContent raw) {
        // Manual text provides no external identifiers
        return List.of();
    }

    @Override
    public EntityProfile deriveProfile(RawContent raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = raw.metadata();

        String entityType = (String) meta.getOrDefault("entity_type", "person");
        String name = (String) meta.getOrDefault("name", "");
        String headline = (String) meta.getOrDefault("headline", "");
        String text = (String) meta.getOrDefault("text", "");

        // Name: from context, or fallback
        if (name == null || name.isBlank()) {
            name = "Manual Entry";
        }

        // Headline: from context, or first sentence of text
        if (headline == null || headline.isBlank()) {
            if (text != null && !text.isBlank()) {
                // First sentence (up to 100 chars)
                int dot = text.indexOf('.');
                headline = dot > 0 && dot <= 100
                        ? text.substring(0, dot)
                        : text.substring(0, Math.min(text.length(), 100));
            } else {
                headline = "Self-described";
            }
        }

        return new EntityProfile(
                entityType,
                name,
                headline,
                null,   // orgName — not provided in manual text
                null,   // latitude
                null,   // longitude
                Map.of("source", "manual_text")
        );
    }
}