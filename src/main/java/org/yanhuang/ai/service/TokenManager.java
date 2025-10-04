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
        Instant now = Instant.now();
        Duration elapsed = Duration.between(lastRefresh.get(), now);
        if (elapsed.getSeconds() < properties.getKiro().getMinRefreshIntervalSeconds()) {
            log.info("Skip token refresh because of throttle window");
            return Mono.just(ensureToken());
        }

        String refreshToken = properties.getKiro().getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("No refresh token configured, reuse current access token");
            return Mono.just(ensureToken());
        }

        return webClient.post()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new RefreshRequest(refreshToken))
            .retrieve()
            .bodyToMono(RefreshResponse.class)
            .map(response -> {
                if (response != null && response.accessToken() != null && !response.accessToken().isBlank()) {
                    accessToken.set(response.accessToken());
                    lastRefresh.set(Instant.now());
                    return response.accessToken();
                }
                log.warn("Refresh response missing accessToken, reuse existing token");
                return ensureToken();
            })
            .onErrorResume(ex -> {
                log.error("Failed to refresh token", ex);
                return Mono.just(ensureToken());
            });
    }

    private record RefreshRequest(String refreshToken) {
    }

    private record RefreshResponse(String accessToken) {
    }
}

