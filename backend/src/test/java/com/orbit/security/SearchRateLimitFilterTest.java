package com.orbit.security;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SearchRateLimitFilterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final SearchRateLimitFilter filter = new SearchRateLimitFilter(2, 3, now::get);

    @Test
    void callsBeyondTheLimitAreRefusedUntilTheMinuteIsOver() throws Exception {
        assertThat(call("POST", "/api/v1/search", "10.0.0.1")).isEqualTo(200);
        assertThat(call("POST", "/api/v1/search", "10.0.0.1")).isEqualTo(200);

        MockHttpServletResponse refused = respond("POST", "/api/v1/search", "10.0.0.1");
        assertThat(refused.getStatus()).isEqualTo(429);
        assertThat(refused.getHeader("Retry-After")).isEqualTo("60");

        now.addAndGet(60_000);
        assertThat(call("POST", "/api/v1/search", "10.0.0.1")).isEqualTo(200);
    }

    @Test
    void eachAddressAndEachEndpointHasItsOwnAllowance() throws Exception {
        call("POST", "/api/v1/search", "10.0.0.1");
        call("POST", "/api/v1/search", "10.0.0.1");
        assertThat(call("POST", "/api/v1/search", "10.0.0.1")).isEqualTo(429);

        assertThat(call("POST", "/api/v1/search", "10.0.0.2")).isEqualTo(200);
        assertThat(call("POST", "/api/v1/search/rationale", "10.0.0.1")).isEqualTo(200);
    }

    @Test
    void otherRequestsAreNotCounted() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("GET", "/api/v1/board", "10.0.0.1")).isEqualTo(200);
            assertThat(call("POST", "/api/v1/requests", "10.0.0.1")).isEqualTo(200);
        }
    }

    @Test
    void aLimitOfZeroSwitchesTheCheckOff() throws Exception {
        SearchRateLimitFilter off = new SearchRateLimitFilter(0, 0, now::get);
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            off.doFilter(request("POST", "/api/v1/search", "10.0.0.1"), response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    private int call(String method, String path, String address) throws Exception {
        return respond(method, path, address).getStatus();
    }

    private MockHttpServletResponse respond(String method, String path, String address) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(method, path, address), response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletRequest request(String method, String path, String address) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(address);
        return request;
    }
}
