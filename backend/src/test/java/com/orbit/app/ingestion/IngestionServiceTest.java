package com.orbit.app.ingestion;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.orbit.app.auth.CurrentUser;
import com.orbit.app.controllers.dto.IngestionRequest;
import com.orbit.app.ingestion.dto.IngestionResult;
import com.orbit.app.ingestion.dto.SourceRef;
import com.orbit.domain.entity.User;
import com.orbit.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionServiceTest {

    private IngestionOrchestrator orchestrator;
    private CurrentUser currentUser;
    private UserRepository users;
    private IngestionService service;

    @BeforeEach
    void setUp() {
        orchestrator = mock(IngestionOrchestrator.class);
        currentUser = mock(CurrentUser.class);
        users = mock(UserRepository.class);
        service = new IngestionService(orchestrator, currentUser, users);
    }

    @Test
    void buildsCanonicalUrlsForEverySourceType() {
        assertThat(url("github", Map.of("github_login", "ada"))).isEqualTo("https://github.com/ada");
        assertThat(url("orcid_pub", Map.of("orcid", "0000-0001"))).isEqualTo("https://orcid.org/0000-0001");
        assertThat(url("orcid_pub", Map.of("openalex_author_id", "A123")))
                .isEqualTo("https://openalex.org/authors/A123");
        assertThat(url("zefix", Map.of("zefix_uid", "CHE-1"))).isEqualTo("zefix:CHE-1");
        assertThat(url("personal_site", Map.of("url", "https://www.example.org/about")))
                .isEqualTo("personal_site:example.org");
        assertThat(url("custom", Map.of("url", "custom:value"))).isEqualTo("custom:value");
        assertThat(url("manual_text", Map.of("text", "same text")))
                .startsWith("manual:text:").doesNotContain("same text");
    }

    @Test
    void assignsCorrectProvenancePolicies() {
        assertThat(service.provenanceFor("manual_text")).containsExactly("user_supplied", "self_declared");
        assertThat(service.provenanceFor("linkedin_pdf")).containsExactly("user_supplied", "user_consent");
        assertThat(service.provenanceFor("github")).containsExactly("harvested", "public_data");
        assertThat(service.provenanceFor("unknown")).containsExactly("harvested", "public_data");
    }

    @Test
    void manualIngestionAddsAuthenticatedDisplayNameAndSummarizesResults() {
        when(currentUser.userId()).thenReturn(Optional.of("user-1"));
        User user = new User("user-1", "ada@example.org", "Ada Lovelace");
        when(users.findById("user-1")).thenReturn(Optional.of(user));
        when(orchestrator.ingestMulti(any())).thenReturn(List.of(
                IngestionResult.ingested(7L, 8L, 3), IngestionResult.skipped(7L)));

        Map<String, Object> result = service.ingest(new IngestionRequest(
                "manual_text", Map.of("text", "Builds systems")));

        assertThat(result).containsEntry("ingested", 1).containsEntry("skipped", 1)
                .containsEntry("errors", 0).containsEntry("totalChunks", 3);
        verify(orchestrator).ingestMulti(org.mockito.ArgumentMatchers.argThat(ref ->
                ref.ownerId().equals("user-1") && ref.context().get("name").equals("Ada Lovelace")));
    }

    @Test
    void suppliedNameIsNotOverwritten() {
        when(currentUser.userId()).thenReturn(Optional.of("user-1"));
        when(users.findById("user-1")).thenReturn(Optional.of(new User("user-1", "x@y.ch", "Token Name")));
        when(orchestrator.ingestMulti(any())).thenReturn(List.of());

        service.ingest(new IngestionRequest("manual_text", Map.of("text", "Text", "name", "Chosen Name")));

        verify(orchestrator).ingestMulti(org.mockito.ArgumentMatchers.argThat(ref ->
                ref.context().get("name").equals("Chosen Name")));
    }

    @Test
    void onboardingOptionsArePropagatedToTheSourceReference() {
        when(currentUser.userId()).thenReturn(Optional.of("user-1"));
        when(orchestrator.ingestMulti(any())).thenReturn(List.of());

        service.ingest(new IngestionRequest(
                "manual_text", Map.of("text", "Text"), true, "demand"));

        verify(orchestrator).ingestMulti(org.mockito.ArgumentMatchers.argThat(ref ->
                ref.onboardingDraft()
                        && "demand".equals(ref.profileMode())
                        && "user-1".equals(ref.ownerId())));
    }

    @Test
    void onboardingRejectsAnUnknownProfileMode() {
        assertThatThrownBy(() -> service.ingest(new IngestionRequest(
                "manual_text", Map.of("text", "Text"), true, "unknown")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mode");
    }

    @Test
    void emptyPdfIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);

        assertThatThrownBy(() -> service.ingestLinkedInPdf(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void pdfUsesHashedPseudoUrlAndDeletesTemporaryFile() {
        when(currentUser.userId()).thenReturn(Optional.of("user-1"));
        when(orchestrator.ingestMulti(any())).thenReturn(List.of(IngestionResult.ingested(1L, 2L, 4)));
        MockMultipartFile file = new MockMultipartFile("file", "profile.pdf", "application/pdf",
                "pdf-content".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> result = service.ingestLinkedInPdf(file);

        assertThat(result).containsEntry("ingested", 1).containsEntry("totalChunks", 4);
        verify(orchestrator).ingestMulti(org.mockito.ArgumentMatchers.argThat((SourceRef ref) ->
                ref.url().startsWith("linkedin:pdf:") && ref.ownerId().equals("user-1")));
    }

    private String url(String type, Map<String, String> context) {
        return service.buildUrl(new IngestionRequest(type, context));
    }
}
