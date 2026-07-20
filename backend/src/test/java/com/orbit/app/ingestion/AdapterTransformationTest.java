package com.orbit.app.ingestion;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.orbit.app.ingestion.dto.RawContent;
import com.orbit.app.ingestion.github.GithubAdapter;
import com.orbit.app.ingestion.openalex.OpenAlexAdapter;
import com.orbit.app.ingestion.website.WebsiteContentExtractor;
import com.orbit.app.ingestion.website.company.CompanyWebsiteAdapter;
import com.orbit.app.ingestion.website.personal.PersonalWebsiteAdapter;
import com.orbit.app.ingestion.zefix.ZefixAdapter;
import com.orbit.app.llm.LlmSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdapterTransformationTest {

    private LlmSummaryService llm;

    @BeforeEach
    void setUp() {
        llm = mock(LlmSummaryService.class);
    }

    @Test
    void githubUsesSummaryAndBuildsIdentityProfileAndLabel() {
        GithubAdapter adapter = new GithubAdapter(llm);
        RawContent raw = raw(Map.ofEntries(
                Map.entry("repo_name", "orbit"), Map.entry("repo_owner", "ada"),
                Map.entry("github_login", "ada"), Map.entry("description", "Semantic discovery"),
                Map.entry("language", "Java"), Map.entry("topics", List.of("search")),
                Map.entry("readme", "# Orbit\nDetails"), Map.entry("display_name", "Ada"),
                Map.entry("bio", "Search engineer"), Map.entry("company", "FHNW"),
                Map.entry("location", "Baden"), Map.entry("blog", "https://ada.example")));
        when(llm.summarizeGithubRepo("orbit", "Semantic discovery", "Java",
                List.of("search"), "# Orbit\nDetails")).thenReturn("Focused repository summary");

        assertThat(adapter.extractHighSignalCore(raw)).isEqualTo("Focused repository summary");
        assertThat(adapter.buildLabel(raw)).isEqualTo("github.com/ada/orbit");
        assertThat(adapter.extractExternalIds(raw)).containsExactly(
                new com.orbit.app.ingestion.dto.ExternalId("github_login", "ada"));
        assertThat(adapter.deriveProfile(raw)).satisfies(profile -> {
            assertThat(profile.displayName()).isEqualTo("Ada");
            assertThat(profile.orgName()).isEqualTo("FHNW");
        });
    }

    @Test
    void githubFallsBackMechanicallyWhenLlmReturnsBlank() {
        GithubAdapter adapter = new GithubAdapter(llm);
        RawContent raw = raw(Map.of(
                "repo_name", "orbit", "repo_owner", "ada", "description", "Discovery",
                "language", "Java", "topics", List.of("search"), "stars", 4,
                "pushed_at", "2026-07-01", "readme", "# Orbit\nUseful content"));
        when(llm.summarizeGithubRepo(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any())).thenReturn("");

        assertThat(adapter.extractHighSignalCore(raw))
                .contains("Repo: orbit", "Language: Java", "Useful content");
    }

    @Test
    void openAlexBuildsSearchablePublicationAndAllIdentifiers() {
        OpenAlexAdapter adapter = new OpenAlexAdapter();
        RawContent raw = raw(Map.of(
                "work_primary_topic", "Requirements Engineering",
                "work_title", "<b>Reliable Systems</b>",
                "work_abstract", "Evidence based engineering.", "work_year", "2026",
                "work_cited_by", 12, "orcid", "0000-0001", "openalex_author_id", "A1",
                "work_doi", "10.1/test", "display_name", "Ada", "affiliation", "FHNW"));

        assertThat(adapter.extractHighSignalCore(raw))
                .contains("## Requirements Engineering", "Reliable Systems", "Evidence based engineering");
        assertThat(adapter.buildLabel(raw)).isEqualTo("«<b>Reliable Systems</b>» (2026)");
        assertThat(adapter.extractExternalIds(raw)).extracting(id -> id.idType())
                .containsExactly("orcid", "openalex_author_id", "doi");
        assertThat(adapter.deriveProfile(raw).orgName()).isEqualTo("FHNW");
    }

    @Test
    void zefixNormalizesUidAndMarksOrganizationVerified() {
        ZefixAdapter adapter = new ZefixAdapter(llm);
        RawContent raw = raw(Map.of(
                "name", "Orbit AG", "purpose_raw", "Entwicklung von Software",
                "legal_form_short", "AG", "legal_seat", "Baden", "canton", "AG",
                "status", "active", "uid", "CHE107721785"));
        when(llm.cleanZweck("Orbit AG", "Entwicklung von Software"))
                .thenReturn("Develops software products.");

        assertThat(adapter.extractHighSignalCore(raw))
                .contains("## Orbit AG", "AG based in Baden, AG", "Develops software products");
        assertThat(adapter.extractExternalIds(raw)).singleElement().satisfies(id ->
                assertThat(id.idValue()).isEqualTo("CHE-107.721.785"));
        assertThat(adapter.deriveProfile(raw)).satisfies(profile -> {
            assertThat(profile.entityType()).isEqualTo("organization");
            assertThat(profile.metadata()).containsEntry("hr_verified", true);
        });
    }

    @Test
    void websiteAdaptersUseSummariesAndDeriveDistinctEntityTypes() {
        WebsiteContentExtractor extractor = mock(WebsiteContentExtractor.class);
        PersonalWebsiteAdapter personal = new PersonalWebsiteAdapter(extractor, llm);
        CompanyWebsiteAdapter company = new CompanyWebsiteAdapter(extractor, llm);
        RawContent raw = raw(Map.of("domain", "orbit.ch", "site_name", "Orbit",
                "raw_text", "Long cleaned page content", "page_count", 3,
                "zefix_uid", "CHE-107.721.785"));
        when(llm.summarizeWebsiteContent("orbit.ch", "Orbit", "person", "Long cleaned page content"))
                .thenReturn("Personal competence summary");
        when(llm.summarizeWebsiteContent("orbit.ch", "Orbit", "organization", "Long cleaned page content"))
                .thenReturn("Company competence summary");

        assertThat(personal.extractHighSignalCore(raw)).isEqualTo("Personal competence summary");
        assertThat(company.extractHighSignalCore(raw)).isEqualTo("Company competence summary");
        assertThat(personal.deriveProfile(raw).entityType()).isEqualTo("person");
        assertThat(company.deriveProfile(raw).entityType()).isEqualTo("organization");
        assertThat(company.extractExternalIds(raw)).hasSize(2);
    }

    private static RawContent raw(Map<String, Object> metadata) {
        return new RawContent("raw", metadata, Instant.now());
    }
}
