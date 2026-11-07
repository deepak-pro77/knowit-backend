package com.knowitai.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Allows each client IP a limited number of API calls per minute, so one person
 * cannot burn through your Gemini quota. In-memory, so it suits a single server instance.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;

    private final int limit;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${knowit.rate-limit.requests-per-minute:10}") int limit) {
        this.limit = limit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/objects/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long retryAfterSeconds = tryAcquire(request.getRemoteAddr(), System.currentTimeMillis());
        if (retryAfterSeconds > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Please wait a moment and try again.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Returns 0 if the request is allowed, otherwise how many seconds to wait. */
    private long tryAcquire(String key, long now) {
        if (hits.size() > 10_000) {
            hits.values().removeIf(d -> {
                synchronized (d) {
                    return d.isEmpty() || now - d.peekLast() >= WINDOW_MS;
                }
            });
        }
        Deque<Long> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_MS) {
                times.pollFirst();
            }
            if (times.size() >= limit) {
                long waitMs = WINDOW_MS - (now - times.peekFirst());
                return Math.max(1, (waitMs + 999) / 1000);
            }
            times.addLast(now);
            return 0;
        }
    }
}
