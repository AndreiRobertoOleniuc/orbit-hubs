package com.orbit.app.ingestion.cv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.orbit.app.ingestion.IngestionException;
import com.orbit.app.ingestion.dto.RawContent;
import com.orbit.app.ingestion.dto.SourceRef;

class CvPdfAdapterTest {

    @TempDir
    Path tempDir;

    private final CvPdfAdapter adapter = new CvPdfAdapter();

    @Test
    void extractsTextWithoutGenerativeAiAndKeepsTheProvidedName() throws Exception {
        Path pdf = writePdf("Experience: Embedded systems and Java");
        RawContent raw = adapter.fetch(ref(pdf, "Ada Lovelace"));

        assertThat(raw.text()).contains("Embedded systems and Java");
        assertThat(adapter.extractHighSignalCore(raw)).startsWith("## Lebenslauf");
        assertThat(adapter.deriveProfile(raw).displayName()).isEqualTo("Ada Lovelace");
        assertThat(adapter.buildLabel(raw)).isEqualTo("Lebenslauf · cv.pdf");
    }

    @Test
    void rejectsImageOnlyOrEmptyPdfs() throws Exception {
        Path pdf = tempDir.resolve("empty.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }

        assertThatThrownBy(() -> adapter.fetch(ref(pdf, "Ada")))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("no extractable text");
    }

    private Path writePdf(String text) throws Exception {
        Path pdf = tempDir.resolve("cv.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            document.save(pdf.toFile());
        }
        return pdf;
    }

    private SourceRef ref(Path pdf, String name) {
        return new SourceRef(
                "cv:test", "cv_pdf", "user_supplied", "user_consent",
                Map.of("file_path", pdf.toString(), "filename", "cv.pdf", "name", name),
                "user-1", true, "demand");
    }
}
