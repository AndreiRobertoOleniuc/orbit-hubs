package com.orbit.app.ingestion.github;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.orbit.app.ingestion.IngestionRejectedException;
import com.orbit.app.ingestion.dto.SourceRef;
import com.orbit.app.llm.LlmSummaryService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * How GitHub discovery reports accounts it cannot import from, against a local
 * stand-in for the GitHub API. The 0-repo case is the production report: the
 * account exists, GitHub's search answers 422 for it, and the source failed as
 * "could not be read" instead of saying why.
 */
class GithubAdapterDiscoveryTest {

    private HttpServer server;
    private GithubAdapter adapter;
    private final List<String> requests = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::answer);
        server.start();
        adapter = new GithubAdapter(mock(LlmSummaryService.class),
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void anAccountWithoutPublicReposIsRejectedWithoutSearching() {
        assertThatThrownBy(() -> adapter.discoverSources(ref("empty")))
                .isInstanceOf(IngestionRejectedException.class)
                .hasMessageContaining("keine öffentlichen Repositories");
        assertThat(requests).noneMatch(path -> path.startsWith("/search"));
    }

    @Test
    void aSearchThatGithubRefusesCountsAsNoRepos() {
        assertThatThrownBy(() -> adapter.discoverSources(ref("unsearchable")))
                .isInstanceOf(IngestionRejectedException.class)
                .hasMessageContaining("keine öffentlichen Repositories");
        assertThat(requests).anyMatch(path -> path.startsWith("/search"));
    }

    @Test
    void anUnknownAccountSaysSo() {
        assertThatThrownBy(() -> adapter.discoverSources(ref("ghost")))
                .isInstanceOf(IngestionRejectedException.class)
                .hasMessageContaining("„ghost“ wurde nicht gefunden");
    }

    private static SourceRef ref(String login) {
        return new SourceRef("https://github.com/" + login, "github", "self_declared", "consent",
                Map.of("github_login", login), "user_1");
    }

    private void answer(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requests.add(path);
        switch (path) {
            case "/users/empty" -> send(exchange, 200, user("empty", 0));
            case "/users/unsearchable" -> send(exchange, 200, user("unsearchable", 2));
            case "/search/repositories" -> send(exchange, 422,
                    "{\"message\":\"Validation Failed\",\"errors\":[{\"message\":\"The listed users and repositories "
                            + "cannot be searched\",\"resource\":\"Search\",\"field\":\"q\",\"code\":\"invalid\"}]}");
            default -> send(exchange, 404, "{\"message\":\"Not Found\"}");
        }
    }

    private static String user(String login, int publicRepos) {
        return "{\"login\":\"%s\",\"name\":\"%s\",\"public_repos\":%d,\"followers\":0}"
                .formatted(login, login, publicRepos);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
