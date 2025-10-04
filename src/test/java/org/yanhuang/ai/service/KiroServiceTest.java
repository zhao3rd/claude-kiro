package org.yanhuang.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.yanhuang.ai.config.AppProperties;
import org.yanhuang.ai.model.AnthropicChatRequest;
import org.yanhuang.ai.model.AnthropicMessage;
import org.yanhuang.ai.model.ToolDefinition;
import org.yanhuang.ai.parser.BracketToolCallParser;
import org.yanhuang.ai.parser.CodeWhispererEventParser;
import org.yanhuang.ai.parser.ToolCallDeduplicator;

class KiroServiceTest {

    @Mock
    private AppProperties properties;
    @Mock
    private TokenManager tokenManager;
    @Mock
    private CodeWhispererEventParser eventParser;
    @Mock
    private BracketToolCallParser bracketToolCallParser;
    @Mock
    private ToolCallDeduplicator toolCallDeduplicator;
    private ObjectMapper mapper = new ObjectMapper();

    private WebClient webClient = WebClient.builder()
        .baseUrl("http://localhost")
        .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .build();

    private KiroService kiroService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        AppProperties.KiroProperties kiroProps = new AppProperties.KiroProperties();
        kiroProps.setBaseUrl("http://localhost");
        kiroProps.setProfileArn("test");
        kiroProps.setAccessToken("token");
        kiroProps.setRefreshToken("refresh");
        kiroProps.setRefreshUrl("http://localhost/refresh");
        when(properties.getKiro()).thenReturn(kiroProps);
        when(tokenManager.currentToken()).thenReturn("token");

        kiroService = new KiroService(properties, tokenManager, eventParser, bracketToolCallParser, toolCallDeduplicator, WebClient.builder(), mapper);
    }

    @Test
    void toolChoiceMappingCreatesSpecificStructure() {
        AnthropicChatRequest request = new AnthropicChatRequest();
        request.setModel("claude-sonnet-4-5-20250929");
        request.setMessages(List.of(buildUserMessage("Hello")));
        request.setMaxTokens(32);
        ToolDefinition tool = new ToolDefinition();
        tool.setName("weather");
        tool.setDescription("desc");
        tool.setInputSchema(Map.of("type", "object"));
        request.setTools(List.of(tool));
        request.setToolChoice(Map.of("type", "required", "name", "weather"));
        request.setStopSequences(List.of("stop"));

        ObjectNode payload = invokeBuildPayload(request);

        JsonNode context = payload.path("conversationState").path("currentMessage").path("userInputMessage").path("userInputMessageContext");
        assertThat(context.isMissingNode()).isFalse();
        assertThat(context.path("toolChoice").path("type").asText()).isEqualTo("REQUIRED");
        assertThat(context.path("toolChoice").path("name").asText()).isEqualTo("weather");
        assertThat(payload.path("conversationState").path("currentMessage").path("userInputMessage").path("stopSequences").get(0).asText()).isEqualTo("stop");
    }

    private ObjectNode invokeBuildPayload(AnthropicChatRequest request) {
        try {
            var method = KiroService.class.getDeclaredMethod("buildKiroPayload", AnthropicChatRequest.class);
            method.setAccessible(true);
            return (ObjectNode) method.invoke(kiroService, request);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AnthropicMessage buildUserMessage(String text) {
        AnthropicMessage message = new AnthropicMessage();
        message.setRole("user");
        AnthropicMessage.ContentBlock block = new AnthropicMessage.ContentBlock();
        block.setType("text");
        block.setText(text);
        message.setContent(List.of(block));
        return message;
    }
}
