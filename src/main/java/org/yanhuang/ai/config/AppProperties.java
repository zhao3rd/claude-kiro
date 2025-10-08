package org.yanhuang.ai.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private static final Logger log = LoggerFactory.getLogger(AppProperties.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @NotBlank
    private String apiKey;

    @NotBlank
    private String anthropicVersion;

    private final KiroProperties kiro = new KiroProperties();

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAnthropicVersion() {
        return anthropicVersion;
    }

    public void setAnthropicVersion(String anthropicVersion) {
        this.anthropicVersion = anthropicVersion;
    }

    public KiroProperties getKiro() {
        return kiro;
    }

    @PostConstruct
    void loadTokensFromFile() {
        kiro.resolveTokenFiles();
    }

    public static class KiroProperties {

        @NotBlank
        private String baseUrl;

        @NotBlank
        private String profileArn;

        private String accessToken;

        private String refreshToken;

        private String accessTokenFile;

        private String refreshTokenFile;

        @NotBlank
        private String refreshUrl;

        private int minRefreshIntervalSeconds = 5;

        private boolean disableTools = false;

        private boolean disableHistory = false;

        private int maxHistoryMessages = 10;

        private int maxHistorySize = 131072;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getProfileArn() {
            return profileArn;
        }

        public void setProfileArn(String profileArn) {
            this.profileArn = profileArn;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        public String getAccessTokenFile() {
            return accessTokenFile;
        }

        public void setAccessTokenFile(String accessTokenFile) {
            this.accessTokenFile = accessTokenFile;
        }

        public String getRefreshTokenFile() {
            return refreshTokenFile;
        }

        public void setRefreshTokenFile(String refreshTokenFile) {
            this.refreshTokenFile = refreshTokenFile;
        }

        public String getRefreshUrl() {
            return refreshUrl;
        }

        public void setRefreshUrl(String refreshUrl) {
            this.refreshUrl = refreshUrl;
        }

        public int getMinRefreshIntervalSeconds() {
            return minRefreshIntervalSeconds;
        }

        public void setMinRefreshIntervalSeconds(int minRefreshIntervalSeconds) {
            this.minRefreshIntervalSeconds = minRefreshIntervalSeconds;
        }

        public boolean isDisableTools() {
            return disableTools;
        }

        public void setDisableTools(boolean disableTools) {
            this.disableTools = disableTools;
        }

        public boolean isDisableHistory() {
            return disableHistory;
        }

        public void setDisableHistory(boolean disableHistory) {
            this.disableHistory = disableHistory;
        }

        public int getMaxHistoryMessages() {
            return maxHistoryMessages;
        }

        public void setMaxHistoryMessages(int maxHistoryMessages) {
            this.maxHistoryMessages = maxHistoryMessages;
        }

        public int getMaxHistorySize() {
            return maxHistorySize;
        }

        public void setMaxHistorySize(int maxHistorySize) {
            this.maxHistorySize = maxHistorySize;
        }

        void resolveTokenFiles() {
            if (isPopulated(accessToken) && isPopulated(refreshToken)) {
                return;
            }

            List<String> candidates = new ArrayList<>();
            if (isPopulated(accessTokenFile)) {
                candidates.add(accessTokenFile);
            }
            if (isPopulated(refreshTokenFile)) {
                candidates.add(refreshTokenFile);
            }
            defaultCachePath().ifPresent(candidates::add);

            Set<String> visited = new HashSet<>();
            for (String candidate : candidates) {
                if (!visited.add(candidate)) {
                    continue;
                }
                Path path = Path.of(candidate);
                if (!Files.exists(path)) {
                    continue;
                }
                TokenPair pair = readTokenCache(path).orElseGet(() -> readPlainToken(path));
                if (pair == null) {
                    continue;
                }
                if (!isPopulated(accessToken) && isPopulated(pair.accessToken())) {
                    accessToken = pair.accessToken();
                    log.debug("Loaded Kiro access token from {}", path);
                }
                if (!isPopulated(refreshToken) && isPopulated(pair.refreshToken())) {
                    refreshToken = pair.refreshToken();
                    log.debug("Loaded Kiro refresh token from {}", path);
                }
                if (isPopulated(accessToken) && isPopulated(refreshToken)) {
                    break;
                }
            }
        }

        private Optional<TokenPair> readTokenCache(Path path) {
            try {
                String json = Files.readString(path);
                JsonNode node = mapper.readTree(json);
                String access = optionalText(node, "accessToken");
                String refresh = optionalText(node, "refreshToken");
                if (!isPopulated(access) && node.has("token")) {
                    access = optionalText(node, "token");
                }
                if (!isPopulated(access) && node.has("access_token")) {
                    access = optionalText(node, "access_token");
                }
                if (!isPopulated(refresh) && node.has("refresh_token")) {
                    refresh = optionalText(node, "refresh_token");
                }
                if (isPopulated(access) || isPopulated(refresh)) {
                    return Optional.of(new TokenPair(access, refresh));
                }
            } catch (IOException ex) {
                log.debug("Failed to parse token cache at {}: {}", path, ex.getMessage());
            }
            return Optional.empty();
        }

        private TokenPair readPlainToken(Path path) {
            try {
                String value = Files.readString(path).trim();
                if (!value.isEmpty()) {
                    return new TokenPair(value, null);
                }
            } catch (IOException ex) {
                log.debug("Failed to read token file {}: {}", path, ex.getMessage());
            }
            return null;
        }

        private Optional<String> defaultCachePath() {
            String osName = System.getProperty("os.name", "").toLowerCase();
            String home = System.getProperty("user.home");
            if (home == null || home.isBlank()) {
                home = System.getenv("USERPROFILE");
            }
            if (home == null || home.isBlank()) {
                return Optional.empty();
            }
            Path path;
            if (osName.contains("win")) {
                path = Path.of(home, ".aws", "sso", "cache", "kiro-auth-token.json");
            } else {
                path = Path.of(home, ".aws/sso/cache/kiro-auth-token.json");
            }
            return Optional.of(path.toString());
        }

        private boolean isPopulated(String value) {
            return value != null && !value.isBlank();
        }

        private String optionalText(JsonNode node, String field) {
            JsonNode value = node.get(field);
            return value != null && !value.isNull() ? value.asText() : null;
        }

        private record TokenPair(String accessToken, String refreshToken) {}
    }
}

