package org.yanhuang.ai.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.yanhuang.ai.config.AppProperties;
import reactor.core.publisher.Mono;

@Component
public class TokenManager {

    private static final Logger log = LoggerFactory.getLogger(TokenManager.class);

    private final AppProperties properties;
    private final WebClient webClient;
    private final AtomicReference<String> accessToken;
    private final AtomicReference<Instant> lastRefresh;

    public TokenManager(AppProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(properties.getKiro().getRefreshUrl()).build();
        this.accessToken = new AtomicReference<>(properties.getKiro().getAccessToken());
        this.lastRefresh = new AtomicReference<>(Instant.EPOCH);
    }

    public String currentToken() {
        return accessToken.get();
    }

    public String ensureToken() {
        String token = accessToken.get();
        if (token == null || token.isBlank()) {
            token = properties.getKiro().getAccessToken();
            accessToken.set(token);
        }
        return token;
    }

    public Mono<String> refreshIfNeeded() {
        log.info("=== Token Refresh Analysis ===");

        Instant now = Instant.now();
        Duration elapsed = Duration.between(lastRefresh.get(), now);

        log.info("Current token status: has_token={}, token_length={}, last_refresh={}, elapsed_seconds={}",
            accessToken.get() != null && !accessToken.get().isBlank(),
            accessToken.get() != null ? accessToken.get().length() : 0,
            lastRefresh.get(),
            elapsed.getSeconds());

        if (elapsed.getSeconds() < properties.getKiro().getMinRefreshIntervalSeconds()) {
            log.info("Skip token refresh because of throttle window: {} < {} seconds",
                elapsed.getSeconds(), properties.getKiro().getMinRefreshIntervalSeconds());
            return Mono.just(ensureToken());
        }

        String refreshToken = properties.getKiro().getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("No refresh token configured, reuse current access token");
            return Mono.just(ensureToken());
        }

        log.info("Initiating token refresh to: {}", properties.getKiro().getRefreshUrl());
        log.info("Refresh token present: {} (length: {})",
            refreshToken != null && !refreshToken.isBlank(),
            refreshToken != null ? refreshToken.length() : 0);

        return webClient.post()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new RefreshRequest(refreshToken))
            .retrieve()
            .bodyToMono(RefreshResponse.class)
            .doOnSuccess(response -> {
                if (response != null && response.accessToken() != null && !response.accessToken().isBlank()) {
                    log.info("Token refresh successful: new_token_length={}", response.accessToken().length());
                    accessToken.set(response.accessToken());
                    lastRefresh.set(Instant.now());
                } else {
                    log.warn("Refresh response missing accessToken, reuse existing token");
                }
            })
            .doOnError(error -> {
                log.error("Token refresh failed: error_type={}, message={}",
                    error.getClass().getSimpleName(), error.getMessage());
            })
            .map(response -> {
                if (response != null && response.accessToken() != null && !response.accessToken().isBlank()) {
                    return response.accessToken();
                }
                return ensureToken();
            })
            .onErrorResume(ex -> {
                log.error("Failed to refresh token, falling back to existing token", ex);
                return Mono.just(ensureToken());
            });
    }

    private record RefreshRequest(String refreshToken) {
    }

    private record RefreshResponse(String accessToken) {
    }
}

