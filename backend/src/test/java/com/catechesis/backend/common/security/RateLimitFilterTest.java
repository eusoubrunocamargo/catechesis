package com.catechesis.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.catechesis.backend.common.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link RateLimitFilter}. Bypasses Spring entirely;
 * constructs the filter directly with a tiny per-test limit and drives
 * it via mock request/response objects.
 */
class RateLimitFilterTest {

    private static final int LIMIT_FOR_TEST = 3;
    private static final int SC_TOO_MANY_REQUESTS = 429;
    private RateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                "http://localhost:5173",
                new AppProperties.RateLimit(LIMIT_FOR_TEST),
                new AppProperties.Consent("v1.0.0"));
        filter = new RateLimitFilter(properties);
        chain = mock(FilterChain.class);
    }

    @Test
    void requestsUpToLimitPassThrough() throws Exception {
        for (int i = 0; i < LIMIT_FOR_TEST; i++) {
            HttpServletResponse response = doFilter("/public/registrations", "1.2.3.4");
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        }
        verify(chain, times(LIMIT_FOR_TEST))
                .doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    void requestBeyondLimitIsRejectedWith429() throws Exception {
        // Burn the bucket
        for (int i = 0; i < LIMIT_FOR_TEST; i++) {
            doFilter("/public/registrations", "1.2.3.4");
        }

        HttpServletResponse over = doFilter("/public/registrations", "1.2.3.4");

        assertThat(over.getStatus()).isEqualTo(SC_TOO_MANY_REQUESTS);
        assertThat(over.getHeader("Retry-After")).isNotNull();
        assertThat(Integer.parseInt(over.getHeader("Retry-After")))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void differentIpsHaveIndependentBuckets() throws Exception {
        for (int i = 0; i < LIMIT_FOR_TEST; i++) {
            doFilter("/public/registrations", "1.2.3.4");
        }
        // 1.2.3.4 is now exhausted; 5.6.7.8 should still be fresh.
        HttpServletResponse fresh = doFilter("/public/registrations", "5.6.7.8");

        assertThat(fresh.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void nonPublicPathsAreNotRateLimited() throws Exception {
        for (int i = 0; i < LIMIT_FOR_TEST + 5; i++) {
            HttpServletResponse response = doFilter("/admin/churches", "1.2.3.4");
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        }
        verify(chain, times(LIMIT_FOR_TEST + 5))
                .doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    void xForwardedForIsPreferredOverRemoteAddr() throws Exception {
        // Same RemoteAddr but different XFF — should be different buckets.
        for (int i = 0; i < LIMIT_FOR_TEST; i++) {
            doFilterWithXff("/public/registrations", "10.0.0.1", "1.2.3.4");
        }
        HttpServletResponse fresh =
                doFilterWithXff("/public/registrations", "10.0.0.1", "5.6.7.8");

        assertThat(fresh.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    // ----- helpers -----

    private HttpServletResponse doFilter(String path, String remoteAddr)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        request.setRemoteAddr(remoteAddr);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private HttpServletResponse doFilterWithXff(
            String path, String remoteAddr, String xForwardedFor)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        request.setRemoteAddr(remoteAddr);
        request.addHeader("X-Forwarded-For", xForwardedFor);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private static <T> T any(Class<T> type) {
        return org.mockito.ArgumentMatchers.any(type);
    }
}