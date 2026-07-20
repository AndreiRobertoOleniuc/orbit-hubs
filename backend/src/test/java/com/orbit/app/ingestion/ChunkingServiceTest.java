package com.orbit.app.ingestion;

import java.util.List;

import com.orbit.app.ingestion.dto.ChunkSegment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingServiceTest {

    private final ChunkingService service = new ChunkingService();

    @Test
    void blankInputProducesNoChunks() {
        assertThat(service.recursiveSplit(null, 20, 2)).isEmpty();
        assertThat(service.recursiveSplit("   ", 20, 2)).isEmpty();
    }

    @Test
    void shortInputStaysIntact() {
        List<ChunkSegment> chunks = service.recursiveSplit("A short semantic description.", 20, 2);

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.text()).isEqualTo("A short semantic description.");
            assertThat(chunk.tokenCount()).isEqualTo(service.countTokens(chunk.text()));
        });
    }

    @Test
    void headingsBecomeSectionLabels() {
        String text = "## Skills\nJava Spring PostgreSQL testing architecture.\n\n"
                + "## Projects\nSearch ingestion matching citations evidence.";

        List<ChunkSegment> chunks = service.recursiveSplit(text, 8, 0);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks).extracting(ChunkSegment::section)
                .contains("Skills", "Projects");
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.tokenCount()).isLessThanOrEqualTo(8));
    }

    @Test
    void longUnstructuredTextFallsBackToBoundedWordChunks() {
        String text = "one two three four five six seven eight nine ten eleven twelve thirteen fourteen";

        List<ChunkSegment> chunks = service.recursiveSplit(text, 4, 0);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.tokenCount()).isLessThanOrEqualTo(4));
        assertThat(String.join(" ", chunks.stream().map(ChunkSegment::text).toList()))
                .isEqualTo(text);
    }

    @Test
    void overlapNeverBreaksMaximumWhenCoreChunksAreAlreadyFull() {
        String text = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu";

        List<ChunkSegment> chunks = service.recursiveSplit(text, 5, 2);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.tokenCount()).isLessThanOrEqualTo(5));
        assertThat(chunks.get(1).text()).isEqualTo("zeta eta theta iota kappa");
    }
}
