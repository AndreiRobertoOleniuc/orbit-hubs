package com.orbit.security;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Caps how often one client address may call the public search endpoints.
 * <p>
 * {@code POST /api/v1/search} and {@code POST /api/v1/search/rationale} need no
 * login, and every call is paid for: LLM calls, hosted embeddings, and several
 * seconds of the hosted cross-encoder, which serves one request at a time — a
 * script calling search in a loop would slow it for everyone. The limits are
 * per address and per minute, generous enough for a room of people behind one
 * network address, and answer {@code 429} with {@code Retry-After} beyond that.
 * A limit of 0 switches that check off.
 * <p>
 * Counters live in memory: they reset on restart and are per instance.
 */
@Component
public class SearchRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SearchRateLimitFilter.class);

    static final String SEARCH_PATH = "/api/v1/search";
    static final String RATIONALE_PATH = "/api/v1/search/rationale";

    private static final long WINDOW_MILLIS = 60_000;
    /** Above this many tracked addresses, finished windows are swept. */
    private static final int SWEEP_ABOVE = 5_000;

    private final int searchesPerMinute;
    private final int rationalesPerMinute;
    private final LongSupplier clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public SearchRateLimitFilter(
            @Value("${orbit.search.rate-limit.searches-per-minute:60}") int searchesPerMinute,
            @Value("${orbit.search.rate-limit.rationales-per-minute:300}") int rationalesPerMinute) {
        this(searchesPerMinute, rationalesPerMinute, System::currentTimeMillis);
    }

    SearchRateLimitFilter(int searchesPerMinute, int rationalesPerMinute, LongSupplier clock) {
        this.searchesPerMinute = searchesPerMinute;
        this.rationalesPerMinute = rationalesPerMinute;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || limitFor(request.getRequestURI()) <= 0;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        int limit = limitFor(path);
        long now = clock.getAsLong();
        // With server.forward-headers-strategy set, this is the address the proxy saw.
        String key = request.getRemoteAddr() + "|" + path;

        Window window = windows.compute(key, (k, current) ->
                current == null || now - current.start >= WINDOW_MILLIS ? new Window(now) : current);
        int count;
        synchronized (window) {
            count = ++window.count;
        }
        if (count > limit) {
            long retryAfterSeconds = Math.max(1, (window.start + WINDOW_MILLIS - now + 999) / 1000);
            if (count == limit + 1) {
                log.warn("Rate limit reached for {} on {} ({} per minute)", request.getRemoteAddr(), path, limit);
            }
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
            return;
        }
        if (windows.size() > SWEEP_ABOVE) {
            windows.values().removeIf(w -> now - w.start >= WINDOW_MILLIS);
        }
        chain.doFilter(request, response);
    }

    private int limitFor(String path) {
        if (SEARCH_PATH.equals(path)) return searchesPerMinute;
        if (RATIONALE_PATH.equals(path)) return rationalesPerMinute;
        return 0;
    }

    /** Calls of one address to one endpoint since {@code start}. */
    private static final class Window {
        final long start;
        int count;

        Window(long start) {
            this.start = start;
        }
    }
}
