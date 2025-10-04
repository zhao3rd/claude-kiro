package org.yanhuang.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Utility class for test helper methods
 */
public class TestUtil {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Create a WebTestClient with custom configuration
     */
    public static WebTestClient createWebTestClient(int port) {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    /**
     * Convert object to JSON string
     */
    public static String toJson(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert object to JSON", e);
        }
    }

    /**
     * Convert JSON string to object
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert JSON to object", e);
        }
    }

    /**
     * Create a consumer for setting request headers
     */
    public static Consumer<WebTestClient.RequestHeadersSpec<?>> withAuthHeaders(String apiKey, String apiVersion) {
        return headersSpec -> headersSpec
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("x-api-key", apiKey)
                .header("anthropic-version", apiVersion);
    }

    /**
     * Create a consumer for setting invalid auth headers
     */
    public static Consumer<WebTestClient.RequestHeadersSpec<?>> withInvalidAuthHeaders() {
        return headersSpec -> headersSpec
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("x-api-key", "invalid-key")
                .header("anthropic-version", "2023-06-01");
    }

    /**
     * Create a consumer for setting missing auth headers
     */
    public static Consumer<WebTestClient.RequestHeadersSpec<?>> withMissingAuthHeaders() {
        return headersSpec -> headersSpec
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE);
    }

    /**
     * Create a consumer for setting streaming headers
     */
    public static Consumer<WebTestClient.RequestHeadersSpec<?>> withStreamingHeaders() {
        return headersSpec -> headersSpec
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", "text/event-stream");
    }

    /**
     * Wait for a reactive operation to complete with timeout
     */
    public static <T> T waitForMono(Mono<T> mono, Duration timeout) {
        return mono.block(timeout);
    }

    /**
     * Wait for a reactive operation to complete with default timeout
     */
    public static <T> T waitForMono(Mono<T> mono) {
        return mono.block(Duration.ofSeconds(10));
    }

    /**
     * Create a delay for testing timing scenarios
     */
    public static void delay(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Generate random string for testing
     */
    public static String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }

    /**
     * Generate random email for testing
     */
    public static String generateRandomEmail() {
        return generateRandomString(8).toLowerCase() + "@test.com";
    }

    /**
     * Generate random UUID string
     */
    public static String generateUUID() {
        return java.util.UUID.randomUUID().toString();
    }

    /**
     * Assert that a string contains specific substrings
     */
    public static boolean containsAll(String text, String... substrings) {
        for (String substring : substrings) {
            if (!text.contains(substring)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Assert that a string is null or empty
     */
    public static boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Assert that a string is not null or empty
     */
    public static boolean isNotNullOrEmpty(String str) {
        return !isNullOrEmpty(str);
    }

    /**
     * Count occurrences of a substring in a string
     */
    public static int countOccurrences(String text, String substring) {
        if (isNullOrEmpty(text) || isNullOrEmpty(substring)) {
            return 0;
        }

        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

    /**
     * Extract a number from a string using regex
     */
    public static Integer extractNumber(String text) {
        if (isNullOrEmpty(text)) {
            return null;
        }

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d+");
        java.util.regex.Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }

        return null;
    }

    /**
     * Validate JSON format
     */
    public static boolean isValidJson(String json) {
        try {
            mapper.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Pretty print JSON for debugging
     */
    public static String prettyPrintJson(Object object) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (Exception e) {
            return object.toString();
        }
    }
}