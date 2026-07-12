package com.orbit.app.ingestion.linkedin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.orbit.app.ingestion.dto.EntityProfile;
import com.orbit.app.ingestion.dto.ExternalId;
import com.orbit.app.ingestion.dto.RawContent;
import com.orbit.app.ingestion.dto.SourceRef;
import com.orbit.app.ingestion.IngestionException;
import com.orbit.app.ingestion.SourceAdapter;
import com.orbit.app.ingestion.linkedin.dto.ParsedLinkedInProfile;
import com.orbit.app.llm.LlmSummaryService;

/**
 * LinkedIn PDF adapter — ingests a user-uploaded LinkedIn profile PDF.
 * <p>
 * Flow:
 * <ol>
 *   <li>{@code fetch()} — load the PDF file, extract raw text with PDFBox,
 *       parse into a {@link ParsedLinkedInProfile} via {@link LinkedInPdfParser}.</li>
 *   <li>{@code extractHighSignalCore()} — build structured text from the parsed
 *       profile: name, headline, summary, experience descriptions, skills.</li>
 *   <li>{@code extractExternalIds()} — extract the LinkedIn URL as the
 *       primary external identifier for entity resolution.</li>
 * </ol>
 * <p>
 * LinkedIn PDFs have section headers in English, German, or mixed (e.g.
 * "Kontakt" + "Languages" + "Zusammenfassung"). The parser handles all
 * these variants. The PDF is user-uploaded, so
 * {@code provenance = "user_supplied"}, {@code consent_basis = "user_consent"}.
 */
@Component
public class LinkedInPdfAdapter implements SourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(LinkedInPdfAdapter.class);

    private final LinkedInPdfParser parser = new LinkedInPdfParser();
    private final LlmSummaryService llmSummaryService;

    public LinkedInPdfAdapter(LlmSummaryService llmSummaryService) {
        this.llmSummaryService = llmSummaryService;
    }

    @Override
    public String sourceType() {
        return "linkedin_pdf";
    }

    @Override
    public RawContent fetch(SourceRef ref) throws IngestionException {
        String filePath = ref.context().get("file_path");
        if (filePath == null || filePath.isBlank()) {
            throw new IngestionException("file_path is required in context for LinkedIn PDF ingestion");
        }

        log.info("Loading LinkedIn PDF: {}", filePath);

        // 1. Load PDF and extract raw text
        String rawText;
        try {
            rawText = extractPdfText(filePath);
        } catch (IngestionException e) {
            throw e;
        }

        if (rawText == null || rawText.isBlank()) {
            throw new IngestionException("PDF text extraction returned empty text for: " + filePath);
        }

        log.debug("Extracted {} chars from PDF", rawText.length());

        // 2. Parse into structured profile
        ParsedLinkedInProfile profile = parser.parse(rawText);

        if (profile.name() == null || profile.name().isBlank()) {
            throw new IngestionException("Could not extract name from LinkedIn PDF: " + filePath);
        }

        log.info("Parsed LinkedIn profile: {} ({})", profile.name(), profile.linkedInUrl());

        // 3. Build metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", profile.name());
        metadata.put("headline", profile.headline());
        metadata.put("location", profile.location());
        metadata.put("email", profile.email());
        metadata.put("phone", profile.phone());
        metadata.put("linkedin_url", profile.linkedInUrl());
        metadata.put("personal_website", profile.personalWebsite());
        metadata.put("top_skills", profile.topSkills());
        metadata.put("languages", profile.languages());
        metadata.put("current_company", profile.currentCompany());
        metadata.put("summary", profile.summary());
        metadata.put("experience_text", profile.experienceText());
        metadata.put("education_text", profile.educationText());
        metadata.put("file_path", filePath);

        // The raw text stored in sources.raw_content is the parsed profile text
        // (not the original PDF bytes — those are too large and PII-heavy)
        String parsedText = buildParsedText(profile);

        return new RawContent(parsedText, metadata, Instant.now());
    }

    @Override
    public String extractHighSignalCore(RawContent raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = raw.metadata();

        String name = (String) meta.getOrDefault("name", "");
        String headline = (String) meta.getOrDefault("headline", "");
        String location = (String) meta.getOrDefault("location", "");
        String summary = (String) meta.getOrDefault("summary", "");
        String experienceText = (String) meta.getOrDefault("experience_text", "");
        String educationText = (String) meta.getOrDefault("education_text", "");
        @SuppressWarnings("unchecked")
        List<String> topSkills = (List<String>) meta.getOrDefault("top_skills", List.of());
        @SuppressWarnings("unchecked")
        List<String> languages = (List<String>) meta.getOrDefault("languages", List.of());

        StringBuilder sb = new StringBuilder();

        // Header with name and headline
        sb.append("## ").append(name).append("\n\n");
        if (headline != null && !headline.isBlank()) {
            sb.append(headline).append("\n");
        }
        if (location != null && !location.isBlank()) {
            sb.append("Location: ").append(location).append("\n");
        }
        sb.append("\n");

        // Summary section
        if (summary != null && !summary.isBlank()) {
            sb.append("## Summary\n");
            sb.append(summary).append("\n\n");
        }

        // Experience section (the richest signal for semantic search)
        if (experienceText != null && !experienceText.isBlank()) {
            sb.append("## Experience\n");
            sb.append(experienceText).append("\n\n");
        }

        // Education section
        if (educationText != null && !educationText.isBlank()) {
            sb.append("## Education\n");
            sb.append(educationText).append("\n\n");
        }

        // Skills
        if (topSkills != null && !topSkills.isEmpty()) {
            sb.append("## Skills\n");
            sb.append(String.join(", ", topSkills)).append("\n\n");
        }

        // Languages
        if (languages != null && !languages.isEmpty()) {
            sb.append("## Languages\n");
            sb.append(String.join(", ", languages)).append("\n");
        }

        // Translate to English — LinkedIn profiles may be in German, and the
        // search FTS leg uses an English tsvector. Other adapters already
        // produce English via their LLM prompts; LinkedIn is the only one that
        // builds structured text without LLM processing.
        String coreText = sb.toString().trim();
        String englishText = llmSummaryService.translateToEnglish(coreText);
        if (!englishText.equals(coreText)) {
            log.info("Translated LinkedIn core text to English ({} → {} chars)",
                    coreText.length(), englishText.length());
        }
        return englishText;
    }

    @Override
    public String buildLabel(RawContent raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = raw.metadata();
        String name = (String) meta.getOrDefault("name", "Unknown");
        return "LinkedIn-Profil (" + name + ")";
    }

    @Override
    public List<ExternalId> extractExternalIds(RawContent raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = raw.metadata();
        String linkedInUrl = (String) meta.get("linkedin_url");

        if (linkedInUrl != null && !linkedInUrl.isBlank()) {
            // Normalize: strip "www." prefix and "(LinkedIn)" suffix
            String normalized = linkedInUrl
                    .replace("www.", "")
                    .replace("(LinkedIn)", "")
                    .trim();
            return List.of(new ExternalId("linkedin_url", normalized));
        }
        return List.of();
    }

    @Override
    public EntityProfile deriveProfile(RawContent raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = raw.metadata();

        String name = (String) meta.getOrDefault("name", "Unknown");
        String headline = (String) meta.getOrDefault("headline", "");
        String currentCompany = (String) meta.getOrDefault("current_company", "");
        String location = (String) meta.getOrDefault("location", "");

        // Truncate headline to 200 chars for the entity
        String truncatedHeadline = headline != null && headline.length() > 200
                ? headline.substring(0, 200)
                : (headline != null ? headline : "");

        Map<String, Object> profileMeta = new HashMap<>();
        profileMeta.put("location", location != null ? location : "");
        profileMeta.put("email", meta.getOrDefault("email", ""));
        profileMeta.put("linkedin_url", meta.getOrDefault("linkedin_url", ""));
        profileMeta.put("personal_website", meta.getOrDefault("personal_website", ""));
        profileMeta.put("top_skills", meta.getOrDefault("top_skills", List.of()));
        profileMeta.put("languages", meta.getOrDefault("languages", List.of()));
        profileMeta.put("current_company", currentCompany != null ? currentCompany : "");

        return new EntityProfile(
                "person",
                name,
                truncatedHeadline,
                currentCompany != null && !currentCompany.isBlank() ? currentCompany : null,
                null,  // latitude — geocode from location in Stage 3
                null,  // longitude
                profileMeta
        );
    }

    // ─── PDF text extraction ───

    private String extractPdfText(String filePath) throws IngestionException {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new IngestionException("PDF file not found: " + filePath);
        }

        try {
            byte[] pdfBytes = Files.readAllBytes(path);
            return extractPdfTextFromBytes(pdfBytes);
        } catch (IOException e) {
            throw new IngestionException("Failed to read PDF file: " + filePath, e);
        }
    }

    static String extractPdfTextFromBytes(byte[] pdfBytes) throws IngestionException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } catch (Exception e) {
            throw new IngestionException("Failed to extract text from PDF", e);
        }
    }

    /**
     * Build the parsed text stored as raw_content in the sources table.
     * This is the human-readable structured text, not the original PDF bytes.
     */
    private String buildParsedText(ParsedLinkedInProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(profile.name()).append("\n");
        sb.append("Headline: ").append(profile.headline()).append("\n");
        sb.append("Location: ").append(profile.location()).append("\n");
        if (profile.email() != null) sb.append("Email: ").append(profile.email()).append("\n");
        if (profile.linkedInUrl() != null) sb.append("LinkedIn: ").append(profile.linkedInUrl()).append("\n");
        if (profile.personalWebsite() != null) sb.append("Website: ").append(profile.personalWebsite()).append("\n");
        if (profile.topSkills() != null && !profile.topSkills().isEmpty())
            sb.append("Skills: ").append(String.join(", ", profile.topSkills())).append("\n");
        if (profile.languages() != null && !profile.languages().isEmpty())
            sb.append("Languages: ").append(String.join(", ", profile.languages())).append("\n");
        if (profile.summary() != null && !profile.summary().isBlank())
            sb.append("\nSummary:\n").append(profile.summary()).append("\n");
        if (profile.experienceText() != null && !profile.experienceText().isBlank())
            sb.append("\nExperience:\n").append(profile.experienceText()).append("\n");
        if (profile.educationText() != null && !profile.educationText().isBlank())
            sb.append("\nEducation:\n").append(profile.educationText()).append("\n");
        return sb.toString();
    }
}