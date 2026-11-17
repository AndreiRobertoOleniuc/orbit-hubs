package com.orbit.app.ingestion;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.orbit.app.auth.CurrentUser;
import com.orbit.app.controllers.dto.IngestionJobDto;
import com.orbit.app.controllers.dto.IngestionRequest;
import com.orbit.app.ingestion.dto.IngestionResult;
import com.orbit.app.ingestion.dto.IngestionSummary;
import com.orbit.app.ingestion.job.IngestionJobService;
import com.orbit.app.ingestion.dto.SourceRef;
import com.orbit.domain.entity.User;
import com.orbit.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionServiceTest {

    private static final IngestionJobDto QUEUED =
            new IngestionJobDto(1L, "source", "manual_text", "queued", 0, null, null, null, null, null, null);

    private IngestionJobService jobs;
    private CurrentUser currentUser;
    private UserRepository users;
    private IngestionService service;

    @BeforeEach
    void setUp() {
        jobs = mock(IngestionJobService.class);
        when(jobs.enqueueSource(any(), any())).thenReturn(QUEUED);
        currentUser = mock(CurrentUser.class);
        users = mock(UserRepository.class);
        service = new IngestionService(jobs, currentUser, users);
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
        assertThat(service.provenanceFor("cv_pdf")).containsExactly("user_supplied", "user_consent");
        assertThat(service.provenanceFor("github")).containsExactly("harvested", "public_data");
        assertThat(service.provenanceFor("unknown")).containsExactly("harvested", "public_data");
    }

    @Test
    void manualIngestionAddsAuthenticatedDisplayNameAndQueuesAJob() {
        when(currentUser.userId()).thenReturn(Optional.of("user-1"));
        User user = new User("user-1", "ada@example.org", "Ada Lovelace");
        when(users.findById("user-1")).thenReturn(Optional.of(user));

        IngestionJobDto job = service.ingest(new IngestionRequest(
                "manual_text", Map.of("text", "Builds systems")));

        assertThat(job).isSameAs(QUEUED);
        verify(jobs).enqueueSource(org.mockito.ArgumentMatchers.argThat(ref ->
                ref.ownerId().equals("user-1") && ref.context().get("name").equals("Ada Lovelace")),
                isNull());
    }

    @Test
    void theSummaryCountsEveryOutcomeAndChunk() {
        IngestionSummary summary = IngestionSummary.of("github", List.of(
                IngestionResult.ingested(7L, 8L, 3), IngestionResult.skipped(7L), IngestionResult.error(null)));

        assertThat(summary.sources()).isEqualTo(3);
        assertThat(summary.ingested()).isEqualTo(1);
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.errors()).isEqualTo(1);
        assertThat(summary.totalChunks()).isEqualTo(3);
    }

    @Test
    void pollingIsLimitedToTheCallersOwnJobs() {
        assertThat(service.jobs(List.of(1L)).settled()).isTrue();   // signed out: nothing to report
        verify(jobs, never()).jobs(any(), any());

        when(currentUser.userId()).thenReturn(Optional.of("user-1"));
        service.jobs(List.of(1L, 2L));
        verify(jobs).jobs("user-1", List.of(1L, 2L));
        assertThatThrownBy(() -> service.jobs(java.util.stream.LongStream.rangeClosed(1, 51).boxed().toList()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void suppliedNameIsNotOverwritten() {
        when(currentUser.userId()).thenReturn(Optional.of("user-1"));
        when(users.findById("user-1")).thenReturn(Optional.of(new User("user-1", "x@y.ch", "Token Name")));

        service.ingest(new IngestionRequest("manual_text", Map.of("text", "Text", "name", "Chosen Name")));

        verify(jobs).enqueueSource(org.mockito.ArgumentMatchers.argThat(ref ->
                ref.context().get("name").equals("Chosen Name")), isNull());
    }

    @Test
    void onboardingOptionsArePropagatedToTheSourceReference() {
        when(currentUser.userId()).thenReturn(Optional.of("user-1"));

        service.ingest(new IngestionRequest(
                "manual_text", Map.of("text", "Text"), true, "demand"));

        verify(jobs).enqueueSource(org.mockito.ArgumentMatchers.argThat(ref ->
                ref.onboardingDraft()
                        && "demand".equals(ref.profileMode())
                        && "user-1".equals(ref.ownerId())), isNull());
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
    void pdfUsesHashedPseudoUrlAndQueuesTheFileWithTheJob() {
        when(currentUser.userId()).thenReturn(Optional.of("user-1"));
        byte[] pdf = "pdf-content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "profile.pdf", "application/pdf", pdf);

        assertThat(service.ingestLinkedInPdf(file)).isSameAs(QUEUED);

        verify(jobs).enqueueSource(org.mockito.ArgumentMatchers.argThat((SourceRef ref) ->
                ref.url().startsWith("linkedin:pdf:") && ref.ownerId().equals("user-1")
                        && !ref.context().containsKey("file_path")), eq(pdf));
    }

    @Test
    void cvPdfUsesItsOwnSourceTypeAndAuthenticatedDisplayName() {
        when(currentUser.userId()).thenReturn(Optional.of("user-1"));
        when(users.findById("user-1"))
                .thenReturn(Optional.of(new User("user-1", "ada@example.org", "Ada Lovelace")));
        MockMultipartFile file = new MockMultipartFile(
                "file", "cv.pdf", "application/pdf", "pdf-content".getBytes(StandardCharsets.UTF_8));

        service.ingestCvPdf(file, true, "demand", null);

        verify(jobs).enqueueSource(org.mockito.ArgumentMatchers.argThat((SourceRef ref) ->
                ref.url().startsWith("cv:pdf:")
                        && "cv_pdf".equals(ref.sourceType())
                        && "Ada Lovelace".equals(ref.context().get("name"))
                        && "cv.pdf".equals(ref.context().get("filename"))
                        && ref.onboardingDraft()
                        && "demand".equals(ref.profileMode())), any());
    }

    @Test
    void cvUploadRejectsNonPdfFilenames() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cv.txt", "text/plain", "not a PDF".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.ingestCvPdf(file, true, "demand", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF");
    }

    private String url(String type, Map<String, String> context) {
        return service.buildUrl(new IngestionRequest(type, context));
    }
}
