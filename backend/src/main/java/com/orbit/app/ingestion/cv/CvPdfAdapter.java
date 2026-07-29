package com.orbit.app.ingestion.cv;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import com.orbit.app.ingestion.IngestionException;
import com.orbit.app.ingestion.SourceAdapter;
import com.orbit.app.ingestion.dto.EntityProfile;
import com.orbit.app.ingestion.dto.ExternalId;
import com.orbit.app.ingestion.dto.RawContent;
import com.orbit.app.ingestion.dto.SourceRef;

/**
 * Generic CV PDF adapter. PDFBox performs deterministic text extraction; the
 * normal post-ingestion enrichment pipeline generates the summary and tags.
 * Image-only/scanned documents deliberately fail instead of pretending that
 * OCR happened.
 */
@Component
public class CvPdfAdapter implements SourceAdapter {

    @Override
    public String sourceType() {
        return "cv_pdf";
    }

    @Override
    public RawContent fetch(SourceRef ref) throws IngestionException {
        String filePath = ref.context().get("file_path");
        if (filePath == null || filePath.isBlank()) {
            throw new IngestionException("file_path is required in context for CV PDF ingestion");
        }

        String text;
        try {
            byte[] bytes = Files.readAllBytes(Path.of(filePath));
            try (PDDocument document = Loader.loadPDF(bytes)) {
                text = new PDFTextStripper().getText(document).trim();
            }
        } catch (Exception e) {
            throw new IngestionException("CV PDF could not be read: " + e.getMessage(), e);
        }
        if (text.isBlank()) {
            throw new IngestionException("CV PDF contains no extractable text; scanned PDFs are not supported");
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("text", text);
        metadata.put("name", ref.context().getOrDefault("name", ""));
        metadata.put("filename", ref.context().getOrDefault("filename", "Lebenslauf.pdf"));
        return new RawContent(text, metadata, Instant.now());
    }

    @Override
    public String extractHighSignalCore(RawContent raw) {
        return "## Lebenslauf\n" + raw.text().trim();
    }

    @Override
    public String buildLabel(RawContent raw) {
        Object filename = raw.metadata().get("filename");
        return filename instanceof String value && !value.isBlank()
                ? "Lebenslauf · " + value
                : "Lebenslauf";
    }

    @Override
    public List<ExternalId> extractExternalIds(RawContent raw) {
        return List.of();
    }

    @Override
    public EntityProfile deriveProfile(RawContent raw) {
        Object candidate = raw.metadata().get("name");
        String name = candidate instanceof String value && !value.isBlank()
                ? value.trim()
                : "Eigenes Profil";
        return new EntityProfile(
                "person", name, null, null, null, null, Map.of("source", "cv_pdf"));
    }
}
