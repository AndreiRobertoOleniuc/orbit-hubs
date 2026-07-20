package com.orbit.app.ingestion.manual;

import java.util.Map;

import com.orbit.app.ingestion.IngestionException;
import com.orbit.app.ingestion.dto.EntityProfile;
import com.orbit.app.ingestion.dto.RawContent;
import com.orbit.app.ingestion.dto.SourceRef;
import com.orbit.app.llm.LlmSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualTextAdapterTest {

    private LlmSummaryService llm;
    private ManualTextAdapter adapter;

    @BeforeEach
    void setUp() {
        llm = mock(LlmSummaryService.class);
        adapter = new ManualTextAdapter(llm);
    }

    @Test
    void fetchRequiresNonBlankText() {
        assertThatThrownBy(() -> adapter.fetch(ref(Map.of())))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("text is required");
    }

    @Test
    void shortTextUsesLlmExpansionAndKeepsSectionHeading() {
        when(llm.enrichManualText("Java developer", "Ada")).thenReturn("Ada develops Java systems.");
        RawContent raw = adapter.fetch(ref(Map.of("text", "Java developer", "name", "Ada")));

        assertThat(adapter.extractHighSignalCore(raw))
                .isEqualTo("## Eigene Beschreibung\nAda develops Java systems.");
    }

    @Test
    void blankExpansionFallsBackToOriginalText() {
        when(llm.enrichManualText("Java developer", "Ada")).thenReturn(" ");
        RawContent raw = adapter.fetch(ref(Map.of("text", "Java developer", "name", "Ada")));

        assertThat(adapter.extractHighSignalCore(raw)).endsWith("Java developer");
    }

    @Test
    void longTextDoesNotCallLlm() {
        String text = "Architecture and engineering. ".repeat(10);
        RawContent raw = adapter.fetch(ref(Map.of("text", text)));

        assertThat(adapter.extractHighSignalCore(raw)).contains(text.trim());
        verify(llm, never()).enrichManualText(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void profileUsesContextAndDerivesHeadlineFallback() {
        RawContent raw = adapter.fetch(ref(Map.of(
                "text", "Builds reliable search systems. Additional detail.",
                "name", "Ada", "entity_type", "person")));

        EntityProfile profile = adapter.deriveProfile(raw);

        assertThat(profile.displayName()).isEqualTo("Ada");
        assertThat(profile.headline()).isEqualTo("Builds reliable search systems");
        assertThat(profile.entityType()).isEqualTo("person");
        assertThat(adapter.buildLabel(raw)).isEqualTo("Eigene Beschreibung");
        assertThat(adapter.extractExternalIds(raw)).isEmpty();
    }

    @Test
    void missingIdentityUsesSafeDefaults() {
        RawContent raw = adapter.fetch(ref(Map.of("text", "Independent consultant")));

        EntityProfile profile = adapter.deriveProfile(raw);

        assertThat(profile.displayName()).isEqualTo("Eigenes Profil");
        assertThat(profile.headline()).isEqualTo("Independent consultant");
    }

    private static SourceRef ref(Map<String, String> context) {
        return new SourceRef("manual:test", "manual_text", "user_supplied",
                "self_declared", context, "user-1");
    }
}
