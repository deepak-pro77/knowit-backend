package com.knowitai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private MockHttpServletResponse call(RateLimitFilter filter, String path, String ip)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void blocksRequestsOverTheLimitAndSetsRetryAfter() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2);

        assertEquals(200, call(filter, "/api/v1/objects/analyze", "1.1.1.1").getStatus());
        assertEquals(200, call(filter, "/api/v1/objects/analyze", "1.1.1.1").getStatus());
        MockHttpServletResponse blocked = call(filter, "/api/v1/objects/analyze", "1.1.1.1");

        assertEquals(429, blocked.getStatus());
        assertNotNull(blocked.getHeader("Retry-After"));
    }

    @Test
    void limitsEachClientSeparately() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1);

        assertEquals(200, call(filter, "/api/v1/objects/analyze", "1.1.1.1").getStatus());
        assertEquals(429, call(filter, "/api/v1/objects/analyze", "1.1.1.1").getStatus());
        assertEquals(200, call(filter, "/api/v1/objects/analyze", "2.2.2.2").getStatus());
    }

    @Test
    void ignoresPathsOutsideTheObjectsApi() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1);

        assertEquals(200, call(filter, "/actuator/health", "1.1.1.1").getStatus());
        assertEquals(200, call(filter, "/actuator/health", "1.1.1.1").getStatus());
    }
}
