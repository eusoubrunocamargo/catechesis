package com.catechesis.backend.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Confirms that {@link RateLimitFilter} is registered in the security
 * chain and fires on {@code /public/**} requests.
 *
 * <p>Sets the per-hour limit very low for this test so that exhausting
 * the bucket takes only a handful of requests. The filter's actual
 * limiting logic is covered exhaustively by {@link RateLimitFilterTest};
 * this test exists only to prove the wiring.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.rate-limit.public-registration-per-hour=2"
})
class RateLimitFilterIntegrationTests {

    @Autowired MockMvc mockMvc;

    @Test
    void publicEndpointEventuallyReturns429() throws Exception {
        // The /public/** path doesn't need to resolve to a real handler
        // — the filter fires before routing. Two requests pass (404 from
        // the non-existent handler), the third hits 429 from the filter.

        mockMvc.perform(post("/public/anything")).andExpect(status().isNotFound());
        mockMvc.perform(post("/public/anything")).andExpect(status().isNotFound());
        mockMvc.perform(post("/public/anything"))
                .andExpect(status().isTooManyRequests());
    }
}