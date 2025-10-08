package org.yanhuang.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Test to verify that large request bodies are handled correctly
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class WebFluxConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void testLargeRequestBody() {
        // Create a large request body (> 256KB)
        Map<String, Object> largeBody = new HashMap<>();
        
        // Add a large string to exceed the default 256KB limit
        StringBuilder largeString = new StringBuilder();
        for (int i = 0; i < 30000; i++) {
            largeString.append("This is a test string to create a large request body. ");
        }
        
        largeBody.put("system", largeString.toString());
        largeBody.put("model", "claude-sonnet-4-20250514");
        largeBody.put("max_tokens", 1024);
        
        // This should not throw DataBufferLimitException with the new configuration
        webTestClient.post()
            .uri("/v1/messages/count_tokens")
            .contentType(MediaType.APPLICATION_JSON)
            .header("anthropic-version", "2023-06-01")
            .bodyValue(largeBody)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.type").isEqualTo("token_count")
            .jsonPath("$.input_tokens").isNumber();
    }

    @Test
    void testNormalRequestBody() {
        // Test with a normal-sized request body
        Map<String, Object> body = new HashMap<>();
        body.put("model", "claude-sonnet-4-20250514");
        body.put("max_tokens", 1024);
        
        webTestClient.post()
            .uri("/v1/messages/count_tokens")
            .contentType(MediaType.APPLICATION_JSON)
            .header("anthropic-version", "2023-06-01")
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.type").isEqualTo("token_count")
            .jsonPath("$.input_tokens").isNumber();
    }
}

