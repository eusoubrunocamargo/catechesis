package com.catechesis.backend.common.security;

import com.catechesis.backend.common.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP rate limit for public, unauthenticated endpoints under
 * {@code /public/**}. Other paths pass through untouched.
 *
 * <p>Uses Bucket's in-memory token-bucket primitive: each client IP
 * gets its own bucket, refilled at a constant rate of N tokens per
 * hour where N is configured via {@code app.rate-limit.public-
 * registration-per-hour}. A request consumes one token; if no tokens
 * are available, the response is {@code 429 Too Many Requests} with
 * a {@code Retry-After} header in seconds.
 *
 * <p>Buckets live in an in-process {@link ConcurrentHashMap}. This is
 * sufficient for MVP scale (one Spring instance, one parish) but does
 * not survive restarts and does not coordinate across instances. A
 * Sprint 4+ revisit can swap to a distributed implementation
 * (e.g., Bucket4j-Redis) without changing this filter's API surface.
 *
 * <p>Client IP is read from {@code X-Forwarded-For} when present,
 * falling back to {@code request.getRemoteAddr()}. The header is
 * trivially spoofable when no trusted proxy fronts the application;
 * Sprint 4's deployment hardening will define the trusted-proxy
 * boundary and tighten the IP-extraction logic accordingly.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String PUBLIC_PATH_PREFIX = "/public/";
    private static final int SC_TOO_MANY_REQUESTS = 429;

    private final ConcurrentMap<String, Bucket> bucketsByIp = new ConcurrentHashMap<>();
    private final int requestsPerHour;

    public RateLimitFilter(AppProperties appProperties) {
        this.requestsPerHour = appProperties.rateLimit().publicRegistrationPerHour();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        if (!request.getRequestURI().startsWith(PUBLIC_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        Bucket bucket = bucketsByIp.computeIfAbsent(clientIp, ip -> newBucket());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds =
                Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds();
        response.setStatus(SC_TOO_MANY_REQUESTS);
        response.setHeader("Retry-After", String.valueOf(Math.max(retryAfterSeconds, 1)));
        log.warn("Rate limit exceeded for IP {} on {}",
                clientIp, request.getRequestURI());
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerHour)
                .refillGreedy(requestsPerHour, Duration.ofHours(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private static String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may contain a chain: "client, proxy1, proxy2"
            int comma = forwarded.indexOf(',');
            return (comma == -1 ? forwarded : forwarded.substring(0, comma)).trim();
        }
        return request.getRemoteAddr();
    }
}