package com.orbit.app.ingestion.github;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.orbit.app.ingestion.EntityProfile;
import com.orbit.app.ingestion.ExternalId;
import com.orbit.app.ingestion.IngestionException;
import com.orbit.app.ingestion.RawContent;
import com.orbit.app.ingestion.SourceAdapter;
import com.orbit.app.ingestion.SourceRef;
import com.orbit.app.llm.LlmSummaryService;

/**
 * GitHub adapter — ingests a user's top repositories as per-repo sources.
 * <p>
 * Flow:
 * <ol>
 *   <li>{@code discoverSources()} — fetch user profile + search repos by stars.
 *       Returns one SourceRef per repo (up to 15). The profile data is cached
 *       in each SourceRef's context for entity creation.</li>
 *   <li>{@code fetch()} — fetch the README for a specific repo. The repo metadata
 *       is already in the context from discoverSources.</li>
 *   <li>{@code extractHighSignalCore()} — strip badges/HTML from README, build
 *       per-repo core text (description + topics + README).</li>
 * </ol>
 * <p>
 * The user profile (name, bio, company, location) is used only for entity
 * creation, NOT as a source. The repos ARE the searchable content.
 */
@Component
public class GithubAdapter implements SourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(GithubAdapter.class);
    private static final String API_BASE = "https://api.github.com";
    private static final int MAX_REPOS = 15;
    private static final int MAX_PINNED = 6; // GitHub allows max 6 pinned repos

    private final RestClient restClient;
    private final RestClient graphqlClient;
    private final LlmSummaryService llmSummaryService;

    @Value("${orbit.github.token:}")
    private String githubToken;

    public GithubAdapter(LlmSummaryService llmSummaryService) {
        this.llmSummaryService = llmSummaryService;
        this.restClient = RestClient.builder()
                .baseUrl(API_BASE)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
        this.graphqlClient = RestClient.builder()
                .baseUrl("https://api.github.com/graphql")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String sourceType() {
        return "github";
    }

    // ─── discoverSources: fetch profile + repos, return one SourceRef per repo ───

    @Override
    public List<SourceRef> discoverSources(SourceRef ref) throws IngestionException {
        String username = ref.context().get("github_login");
        if (username == null || username.isBlank()) {
            throw new IngestionException("github_login is required in context");
        }

        // 1. Fetch user profile (for entity creation)
        GithubUser user = fetchUser(username);
        log.info("GitHub user {}: {} ({} repos, {} followers)",
                username, user.displayName(), user.publicRepos(), user.followers());

        // 2. Try pinned repos first (via GraphQL — requires PAT)
        //    Pinned repos are the highest signal: the user explicitly chose to showcase them.
        //    They can include repos from other orgs (e.g. tiangolo pins fastapi/fastapi).
        List<GithubRepo> pinnedRepos = List.of();
        if (githubToken != null && !githubToken.isBlank()) {
            pinnedRepos = fetchPinnedRepos(username);
            log.info("GitHub user {}: found {} pinned repos", username, pinnedRepos.size());
        } else {
            log.debug("GitHub user {}: no PAT configured, skipping pinned repos", username);
        }

        // 3. Build the repo list: pinned repos first, then top personal repos by stars
        List<GithubRepo> allRepos = new ArrayList<>(pinnedRepos);
        
        // Supplement with top personal repos by stars (excluding already-pinned repos)
        int remaining = MAX_REPOS - pinnedRepos.size();
        if (remaining > 0) {
            List<GithubRepo> personalRepos = searchReposByStars(username);
            // Filter out repos already in pinned (match by owner/name)
            var pinnedKeys = pinnedRepos.stream()
                    .map(r -> r.ownerLogin() + "/" + r.name())
                    .collect(java.util.stream.Collectors.toSet());
            for (GithubRepo repo : personalRepos) {
                if (!pinnedKeys.contains(repo.ownerLogin() + "/" + repo.name())) {
                    allRepos.add(repo);
                    if (allRepos.size() >= MAX_REPOS) break;
                }
            }
        }
        
        log.info("GitHub user {}: {} pinned + {} personal = {} total repos",
                username, pinnedRepos.size(), allRepos.size() - pinnedRepos.size(), allRepos.size());

        if (allRepos.isEmpty()) {
            log.warn("GitHub user {} has no public repos and no pinned repos", username);
            return List.of();
        }

        // 4. Build one SourceRef per repo
        List<SourceRef> subSources = new ArrayList<>();
        int limit = Math.min(allRepos.size(), MAX_REPOS);
        for (int i = 0; i < limit; i++) {
            GithubRepo repo = allRepos.get(i);
            String sourceUrl = "https://github.com/" + repo.ownerLogin() + "/" + repo.name();

            Map<String, String> context = new HashMap<>();
            context.put("github_login", username);
            context.put("repo_owner", repo.ownerLogin());
            context.put("repo_name", repo.name());
            context.put("repo_url", sourceUrl);
            if (i < pinnedRepos.size()) {
                context.put("is_pinned", "true");
            }
            // Cache profile data for entity creation on the first repo
            if (i == 0) {
                context.put("display_name", user.displayName());
                context.put("bio", user.bio() != null ? user.bio() : "");
                context.put("company", user.company() != null ? user.company() : "");
                context.put("location", user.location() != null ? user.location() : "");
                context.put("blog", user.blog() != null ? user.blog() : "");
                context.put("is_first_repo", "true");
            }

            subSources.add(new SourceRef(
                    sourceUrl,
                    "github",
                    "harvested",
                    "public_data",
                    context
            ));
        }

        return subSources;
    }

    // ─── fetch: get README for a specific repo ───

    @Override
    public RawContent fetch(SourceRef ref) throws IngestionException {
        String owner = ref.context().get("repo_owner");
        String repo = ref.context().get("repo_name");
        if (owner == null || repo == null) {
            throw new IngestionException("repo_owner and repo_name are required in context");
        }

        // Fetch repo metadata (in case it wasn't cached)
        GithubRepo repoData = fetchRepo(owner, repo);

        // Fetch README (may not exist)
        String readme = "";
        try {
            readme = fetchReadme(owner, repo);
        } catch (IngestionException e) {
            log.debug("No README for {}/{} — using description only", owner, repo);
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("github_login", ref.context().get("github_login"));
        metadata.put("repo_owner", owner);
        metadata.put("repo_name", repoData.name());
        metadata.put("repo_url", ref.context().get("repo_url"));
        metadata.put("description", repoData.description());
        metadata.put("language", repoData.language());
        metadata.put("topics", repoData.topics());
        metadata.put("stars", repoData.stargazersCount());
        metadata.put("pushed_at", repoData.pushedAt());
        metadata.put("readme", readme);

        // Also include profile data if this is the first repo
        if ("true".equals(ref.context().get("is_first_repo"))) {
            metadata.put("display_name", ref.context().get("display_name"));
            metadata.put("bio", ref.context().get("bio"));
            metadata.put("company", ref.context().get("company"));
            metadata.put("location", ref.context().get("location"));
            metadata.put("blog", ref.context().get("blog"));
        }

        return new RawContent(readme, metadata, Instant.now());
    }

    // ─── extractHighSignalCore: LLM summarization with mechanical fallback ───

    @Override
    public String extractHighSignalCore(RawContent raw) {
        Map<String, Object> meta = raw.metadata();
        String repoName = (String) meta.get("repo_name");
        String description = (String) meta.get("description");
        String language = (String) meta.get("language");
        @SuppressWarnings("unchecked")
        List<String> topics = (List<String>) meta.get("topics");
        String readme = (String) meta.get("readme");

        // Try LLM summarization first — produces clean, focused, searchable text
        if (readme != null && !readme.isBlank()) {
            String summary = llmSummaryService.summarizeGithubRepo(
                    repoName, description, language, topics, readme);
            if (summary != null && !summary.isBlank()) {
                log.debug("LLM summary for {}: {} chars", repoName, summary.length());
                return summary;
            }
            // LLM failed — fall back to mechanical stripping
            log.warn("LLM summarization failed for {}, falling back to mechanical stripping", repoName);
            return buildMechanicalCoreText(meta);
        }

        // No README — use description only
        if (description != null && !description.isBlank()) {
            return "Repo: " + repoName + "\nDescription: " + description;
        }
        return "Repo: " + repoName;
    }

    /**
     * Fallback: build core text by mechanically stripping boilerplate from the README.
     * Used when the LLM is unavailable or fails.
     */
    private String buildMechanicalCoreText(Map<String, Object> meta) {
        String repoName = (String) meta.get("repo_name");
        String description = (String) meta.get("description");
        String language = (String) meta.get("language");
        @SuppressWarnings("unchecked")
        List<String> topics = (List<String>) meta.get("topics");
        Integer stars = (Integer) meta.get("stars");
        String pushedAt = (String) meta.get("pushed_at");
        String readme = (String) meta.get("readme");

        StringBuilder sb = new StringBuilder();
        sb.append("Repo: ").append(repoName).append("\n");
        if (description != null && !description.isBlank())
            sb.append("Description: ").append(description).append("\n");
        if (language != null && !language.isBlank())
            sb.append("Language: ").append(language).append("\n");
        if (topics != null && !topics.isEmpty())
            sb.append("Topics: ").append(String.join(", ", topics)).append("\n");
        if (stars != null)
            sb.append("Stars: ").append(stars).append("\n");
        if (pushedAt != null && !pushedAt.isBlank())
            sb.append("Last Pushed: ").append(pushedAt).append("\n");
        sb.append("\n");
        if (readme != null && !readme.isBlank())
            sb.append(stripReadmeBoilerplate(readme));
        else if (description != null)
            sb.append(description);
        return sb.toString().trim();
    }

    @Override
    public String buildLabel(RawContent raw) {
        Map<String, Object> meta = raw.metadata();
        String owner = (String) meta.get("repo_owner");
        String repoName = (String) meta.get("repo_name");
        return "github.com/" + owner + "/" + repoName;
    }

    @Override
    public List<ExternalId> extractExternalIds(RawContent raw) {
        Map<String, Object> meta = raw.metadata();
        String githubLogin = (String) meta.get("github_login");
        if (githubLogin != null && !githubLogin.isBlank()) {
            return List.of(new ExternalId("github_login", githubLogin));
        }
        return List.of();
    }

    @Override
    public EntityProfile deriveProfile(RawContent raw) {
        Map<String, Object> meta = raw.metadata();
        String displayName = (String) meta.getOrDefault("display_name", "");
        String bio = (String) meta.getOrDefault("bio", "");
        String company = (String) meta.getOrDefault("company", "");
        String location = (String) meta.getOrDefault("location", "");

        String name = displayName != null && !displayName.isBlank() ? displayName : "GitHub User";
        String headline = bio != null && !bio.isBlank()
                ? bio.substring(0, Math.min(bio.length(), 120))
                : (company != null && !company.isBlank() ? "Developer at " + company : "Developer");

        return new EntityProfile(
                "person",
                name,
                headline,
                company != null && !company.isBlank() ? company : null,
                null,  // latitude — geocode from location in Stage 3
                null,  // longitude
                Map.of("location", location != null ? location : "", "blog", meta.getOrDefault("blog", ""))
        );
    }

    // ─── GitHub API calls ───

    /**
     * Fetch pinned repos via GraphQL API (requires PAT).
     * Pinned repos are the repos the user explicitly chose to showcase on their profile.
     * They can include repos from other orgs (e.g. tiangolo pins fastapi/fastapi).
     * GitHub allows max 6 pinned repos.
     */
    private List<GithubRepo> fetchPinnedRepos(String username) throws IngestionException {
        String query = """
                query {
                  user(login: "%s") {
                    pinnedItems(first: %d, types: [REPOSITORY]) {
                      nodes {
                        ... on Repository {
                          nameWithOwner
                          description
                          url
                          primaryLanguage { name }
                          stargazerCount
                          repositoryTopics(first: 20) { nodes { topic { name } } }
                          pushedAt
                        }
                      }
                    }
                  }
                }""".formatted(username, MAX_PINNED);

        try {
            String requestBody = "{\"query\": " + escapeJson(query) + "}";
            JsonNode response = graphqlClient.post()
                    .header("Authorization", "Bearer " + githubToken)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            List<GithubRepo> repos = new ArrayList<>();
            JsonNode nodes = response.path("data").path("user").path("pinnedItems").path("nodes");
            for (JsonNode node : nodes) {
                String nameWithOwner = node.path("nameWithOwner").asText();
                String[] parts = nameWithOwner.split("/", 2);
                String owner = parts[0];
                String name = parts.length > 1 ? parts[1] : nameWithOwner;
                
                List<String> topics = new ArrayList<>();
                JsonNode topicNodes = node.path("repositoryTopics").path("nodes");
                for (JsonNode tn : topicNodes) {
                    topics.add(tn.path("topic").path("name").asText());
                }
                
                repos.add(new GithubRepo(
                        owner,
                        name,
                        node.path("description").asText(""),
                        node.path("primaryLanguage").path("name").asText(""),
                        topics,
                        node.path("stargazerCount").asInt(0),
                        node.path("pushedAt").asText("")
                ));
            }
            return repos;
        } catch (Exception e) {
            log.warn("Failed to fetch pinned repos for {} via GraphQL: {}", username, e.getMessage());
            return List.of(); // non-fatal — fall back to REST search
        }
    }

    /** Escape a string for embedding in a JSON string value. */
    private static String escapeJson(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private GithubUser fetchUser(String username) throws IngestionException {
        try {
            var request = restClient.get().uri("/users/" + username);
            if (githubToken != null && !githubToken.isBlank()) {
                request = request.header("Authorization", "Bearer " + githubToken);
            }
            JsonNode node = request.retrieve().body(JsonNode.class);
            return new GithubUser(
                    node.path("login").asText(),
                    node.path("name").asText(node.path("login").asText()),
                    node.path("bio").asText(""),
                    node.path("company").asText(""),
                    node.path("location").asText(""),
                    node.path("blog").asText(""),
                    node.path("public_repos").asInt(0),
                    node.path("followers").asInt(0)
            );
        } catch (Exception e) {
            throw new IngestionException("Failed to fetch GitHub user " + username, e);
        }
    }

    private List<GithubRepo> searchReposByStars(String username) throws IngestionException {
        try {
            String query = "user:" + username + "+fork:false";
            var request = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search/repositories")
                            .queryParam("q", query)
                            .queryParam("sort", "stars")
                            .queryParam("order", "desc")
                            .queryParam("per_page", MAX_REPOS)
                            .build());
            if (githubToken != null && !githubToken.isBlank()) {
                request = request.header("Authorization", "Bearer " + githubToken);
            }
            JsonNode node = request.retrieve().body(JsonNode.class);

            List<GithubRepo> repos = new ArrayList<>();
            JsonNode items = node.path("items");
            for (JsonNode item : items) {
                String ownerLogin = item.path("owner").path("login").asText();
                String name = item.path("name").asText();
                String description = item.path("description").asText("");
                String language = item.path("language").asText("");
                int stars = item.path("stargazers_count").asInt(0);
                String pushedAt = item.path("pushed_at").asText("");

                // Fetch topics — requires a separate API call per repo, or use the search result
                // The search API doesn't include topics. We'd need GET /repos/{owner}/{repo}
                // For now, leave topics empty — we'll fetch them in fetchRepo() during fetch()
                List<String> topics = List.of();

                repos.add(new GithubRepo(ownerLogin, name, description, language, topics, stars, pushedAt));
            }
            return repos;
        } catch (Exception e) {
            throw new IngestionException("Failed to search repos for " + username, e);
        }
    }

    private GithubRepo fetchRepo(String owner, String repo) throws IngestionException {
        try {
            var request = restClient.get().uri("/repos/" + owner + "/" + repo);
            if (githubToken != null && !githubToken.isBlank()) {
                request = request.header("Authorization", "Bearer " + githubToken);
            }
            JsonNode node = request.retrieve().body(JsonNode.class);

            List<String> topics = new ArrayList<>();
            JsonNode topicsNode = node.path("topics");
            for (JsonNode t : topicsNode) {
                topics.add(t.asText());
            }

            return new GithubRepo(
                    owner,
                    node.path("name").asText(),
                    node.path("description").asText(""),
                    node.path("language").asText(""),
                    topics,
                    node.path("stargazers_count").asInt(0),
                    node.path("pushed_at").asText("")
            );
        } catch (Exception e) {
            throw new IngestionException("Failed to fetch repo " + owner + "/" + repo, e);
        }
    }

    private String fetchReadme(String owner, String repo) throws IngestionException {
        try {
            var request = restClient.get()
                    .uri("/repos/" + owner + "/" + repo + "/readme")
                    .header("Accept", "application/vnd.github.raw");
            if (githubToken != null && !githubToken.isBlank()) {
                request = request.header("Authorization", "Bearer " + githubToken);
            }
            return request.retrieve().body(String.class);
        } catch (Exception e) {
            throw new IngestionException("No README for " + owner + "/" + repo, e);
        }
    }

    // ─── README boilerplate stripping ───

    static String stripReadmeBoilerplate(String readme) {
        if (readme == null || readme.isBlank()) return "";

        String stripped = readme
                // Remove badge images: ![...](https://img.shields.io/...)
                .replaceAll("!\\[[^\\]]*\\]\\(https://img\\.shields\\.io[^\\)]*\\)", "")
                // Remove badge images from other badge services
                .replaceAll("!\\[[^\\]]*\\]\\(https://[^\\)]*badge[^\\)]*\\)", "")
                // Remove GitHub Actions workflow badges
                .replaceAll("!\\[[^\\]]*\\]\\(https://github\\.com[^\\)]*/actions/workflows[^\\)]*\\)", "")
                // Remove badge-style links: [![...](...)](...)
                .replaceAll("\\[!\\[[^\\]]*\\]\\([^\\)]*\\)\\]\\([^\\)]*\\)", "")
                // Remove standalone image references (svg/png/gif/jpg)
                .replaceAll("!\\[[^\\]]*\\]\\(https://[^\\)]*(?:svg|png|gif|jpg)\\)", "")
                // Remove HTML comments
                .replaceAll("<!--[\\s\\S]*?-->", "")
                // Remove HTML tags (div, img, a, p, br, hr, table, h1-h6, details, summary)
                .replaceAll("(?i)</?(?:div|img|a|p|br|hr|table|tr|td|th|thead|tbody|h[1-6]|details|summary)[^>]*>", "")
                // Remove YAML code blocks (often CI/CD configs)
                .replaceAll("(?is)```ya?ml[\\s\\S]*?```", "")
                // Collapse multiple blank lines
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return stripped;
    }

    // ─── Value objects ───

    record GithubUser(
            String login,
            String displayName,
            String bio,
            String company,
            String location,
            String blog,
            int publicRepos,
            int followers
    ) {}

    record GithubRepo(
            String ownerLogin,
            String name,
            String description,
            String language,
            List<String> topics,
            int stargazersCount,
            String pushedAt
    ) {}
}