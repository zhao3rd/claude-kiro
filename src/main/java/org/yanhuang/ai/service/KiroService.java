package org.yanhuang.ai.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import org.yanhuang.ai.config.AppProperties;
import org.yanhuang.ai.model.AnthropicChatRequest;
import org.yanhuang.ai.model.AnthropicChatResponse;
import org.yanhuang.ai.model.AnthropicMessage;
import org.yanhuang.ai.model.ToolCall;
import org.yanhuang.ai.parser.BracketToolCallParser;
import org.yanhuang.ai.parser.CodeWhispererEventParser;
import org.yanhuang.ai.parser.ToolCallDeduplicator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class KiroService {

    private static final Logger log = LoggerFactory.getLogger(KiroService.class);

    private final AppProperties properties;
    private final TokenManager tokenManager;
    private final CodeWhispererEventParser eventParser;
    private final BracketToolCallParser bracketToolCallParser;
    private final ToolCallDeduplicator toolCallDeduplicator;
    private final WebClient webClient;
    private final ObjectMapper mapper;

    public KiroService(AppProperties properties,
                       TokenManager tokenManager,
                       CodeWhispererEventParser eventParser,
                       BracketToolCallParser bracketToolCallParser,
                       ToolCallDeduplicator toolCallDeduplicator,
                       WebClient.Builder webClientBuilder,
                       ObjectMapper mapper) {
        this.properties = properties;
        this.tokenManager = tokenManager;
        this.eventParser = eventParser;
        this.bracketToolCallParser = bracketToolCallParser;
        this.toolCallDeduplicator = toolCallDeduplicator;
        this.webClient = webClientBuilder.baseUrl(properties.getKiro().getBaseUrl()).build();
        this.mapper = mapper;
    }

    public Mono<AnthropicChatResponse> createCompletion(AnthropicChatRequest request) {
        return callKiroEvents(request)
            .map(events -> mapResponse(events, request));
    }

    public Flux<String> streamCompletion(AnthropicChatRequest request) {
        return callKiroEvents(request)
            .map(events -> mapResponse(events, request))
            .flatMapMany(response -> Flux.fromIterable(buildStreamEvents(response)));
    }

    private Mono<List<JsonNode>> callKiroEvents(AnthropicChatRequest request) {
        ObjectNode payload = buildKiroPayload(request);
        log.info("Sending request to Kiro API: {}", properties.getKiro().getBaseUrl());
        log.info("Payload: {}", payload.toString());
        log.info("Using Profile ARN: {}", properties.getKiro().getProfileArn());

        return webClient.post()
            .header("Authorization", "Bearer " + tokenManager.ensureToken())
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(byte[].class)
            .map(eventParser::parse)
            .timeout(Duration.ofSeconds(120))
            .onErrorResume(error -> {
                log.error("Kiro API call failed. Status: {}, Error: {}",
                    error instanceof WebClientResponseException ?
                    ((WebClientResponseException) error).getStatusCode() : "Unknown",
                    error.getMessage());
                if (error instanceof WebClientResponseException) {
                    WebClientResponseException webEx = (WebClientResponseException) error;
                    log.error("Response body: {}", webEx.getResponseBodyAsString());
                }
                return tokenManager.refreshIfNeeded()
                    .flatMap(refreshed -> {
                        log.info("Retrying with refreshed token...");
                        return webClient.post()
                            .header("Authorization", "Bearer " + tokenManager.ensureToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.TEXT_EVENT_STREAM)
                            .bodyValue(payload)
                            .retrieve()
                            .bodyToMono(byte[].class)
                            .map(eventParser::parse)
                            .timeout(Duration.ofSeconds(120));
                    });
            });
    }

    private ObjectNode buildKiroPayload(AnthropicChatRequest request) {
        ObjectNode conversationState = mapper.createObjectNode();
        conversationState.put("chatTriggerType", "MANUAL");
        conversationState.put("conversationId", UUID.randomUUID().toString());

        ObjectNode currentMessage = mapper.createObjectNode();
        ObjectNode userInput = mapper.createObjectNode();
        userInput.put("content", buildCurrentMessageContent(request));
        userInput.put("modelId", mapModel(request.getModel()));
        userInput.put("origin", "AI_EDITOR");

        if (!CollectionUtils.isEmpty(request.getTools())) {
            ObjectNode context = mapper.createObjectNode();
            ArrayNode toolsNode = mapper.createArrayNode();
            request.getTools().forEach(tool -> {
                ObjectNode specNode = mapper.createObjectNode();
                specNode.set("toolSpecification", mapper.createObjectNode()
                    .put("name", tool.getName())
                    .put("description", tool.getDescription())
                    .set("inputSchema", mapper.createObjectNode().set("json", mapper.valueToTree(tool.getInputSchema()))));
                toolsNode.add(specNode);
            });
            context.set("tools", toolsNode);
            if (request.getToolChoice() != null && !request.getToolChoice().isEmpty()) {
                context.set("toolChoice", convertToolChoice(request.getToolChoice()));
            }
            userInput.set("userInputMessageContext", context);
        }

        if (request.getStopSequences() != null && !request.getStopSequences().isEmpty()) {
            ArrayNode stopArray = mapper.createArrayNode();
            request.getStopSequences().forEach(stopArray::add);
            userInput.set("stopSequences", stopArray);
        }
        currentMessage.set("userInputMessage", userInput);

        conversationState.set("currentMessage", currentMessage);
        conversationState.set("history", buildHistory(request));

        ObjectNode payload = mapper.createObjectNode();
        payload.put("profileArn", properties.getKiro().getProfileArn());
        payload.set("conversationState", conversationState);
        return payload;
    }

    private String buildCurrentMessageContent(AnthropicChatRequest request) {
        List<String> segments = new ArrayList<>();
        if (!CollectionUtils.isEmpty(request.getSystem())) {
            request.getSystem().forEach(block -> {
                if ("text".equalsIgnoreCase(block.getType())) {
                    segments.add("[System] " + block.getText());
                }
            });
        }

        // Only process the last message (current one)
        if (!CollectionUtils.isEmpty(request.getMessages())) {
            AnthropicMessage lastMessage = request.getMessages().get(request.getMessages().size() - 1);
            if (!CollectionUtils.isEmpty(lastMessage.getContent())) {
                lastMessage.getContent().forEach(block -> {
                    if ("text".equalsIgnoreCase(block.getType())) {
                        segments.add("[" + lastMessage.getRole() + "] " + block.getText());
                    }
                });
            }
        }
        return String.join("\n", segments);
    }

    private AnthropicChatResponse mapResponse(List<JsonNode> events, AnthropicChatRequest request) {
        AnthropicChatResponse response = new AnthropicChatResponse();
        response.setId("msg_" + UUID.randomUUID().toString().replace("-", ""));
        response.setType("message");
        response.setModel(request.getModel());
        response.setCreatedAt(Instant.now().getEpochSecond());

        StringBuilder contentBuilder = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();

        for (JsonNode event : events) {
            if (event.hasNonNull("content")) {
                contentBuilder.append(event.get("content").asText());
            }
            if (event.hasNonNull("toolCalls")) {
                event.get("toolCalls").forEach(callNode -> {
                    ToolCall call = mapper.convertValue(callNode, ToolCall.class);
                    toolCalls.add(call);
                });
            }
            if (event.hasNonNull("rawText")) {
                List<ToolCall> bracketCalls = bracketToolCallParser.parse(event.get("rawText").asText());
                toolCalls.addAll(bracketCalls);
            }
        }

        List<ToolCall> uniqueToolCalls = toolCallDeduplicator.deduplicate(toolCalls);

        if (uniqueToolCalls.isEmpty()) {
            AnthropicMessage.ContentBlock block = new AnthropicMessage.ContentBlock();
            block.setType("text");
            block.setText(contentBuilder.toString());
            response.addContentBlock(block);
            response.setStopReason("end_turn");
        } else {
            response.setStopReason("tool_use");
            uniqueToolCalls.forEach(call -> {
                AnthropicMessage.ContentBlock block = new AnthropicMessage.ContentBlock();
                block.setType("tool_use");
                block.setName(call.getFunction().getName());
                block.setId(call.getId() != null ? call.getId() : "tool_" + UUID.randomUUID().toString().replace("-", ""));
                block.setInput(parseArguments(call.getFunction().getArguments()));
                response.addContentBlock(block);
            });
        }

        AnthropicChatResponse.Usage usage = new AnthropicChatResponse.Usage();
        usage.setInputTokens(estimateTokens(request));
        usage.setOutputTokens(estimateTokens(contentBuilder.toString()));
        response.setUsage(usage);

        return response;
    }

    private ArrayNode buildHistory(AnthropicChatRequest request) {
        ArrayNode history = mapper.createArrayNode();
        if (CollectionUtils.isEmpty(request.getMessages()) || request.getMessages().size() <= 1) {
            return history;
        }

        // Exclude the last message (current one) from history
        List<AnthropicMessage> historicalMessages = request.getMessages().subList(0, request.getMessages().size() - 1);

        historicalMessages.forEach(message -> {
            String content = buildMessageContent(message);
            if ("user".equalsIgnoreCase(message.getRole())) {
                ObjectNode userNode = mapper.createObjectNode();
                userNode.set("userInputMessage", mapper.createObjectNode()
                    .put("content", content)
                    .put("modelId", mapModel(request.getModel()))
                    .put("origin", "AI_EDITOR"));
                history.add(userNode);
            } else if ("assistant".equalsIgnoreCase(message.getRole())) {
                ObjectNode assistantNode = mapper.createObjectNode();
                assistantNode.set("assistantResponseMessage", mapper.createObjectNode()
                    .put("content", content));
                history.add(assistantNode);
            }
        });

        return history;
    }

    private String buildMessageContent(AnthropicMessage message) {
        if (CollectionUtils.isEmpty(message.getContent())) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        message.getContent().forEach(block -> {
            if ("text".equalsIgnoreCase(block.getType())) {
                // Add role prefix for user messages
                if ("user".equalsIgnoreCase(message.getRole())) {
                    builder.append("[user] ");
                }
                builder.append(block.getText());
            } else if ("tool_use".equalsIgnoreCase(block.getType())) {
                builder.append("[Called ")
                    .append(block.getName())
                    .append(" with args: ")
                    .append(block.getInput())
                    .append("]");
            }
        });
        return builder.toString();
    }

    private int estimateTokens(AnthropicChatRequest request) {
        if (CollectionUtils.isEmpty(request.getMessages())) {
            return 0;
        }
        int total = 0;
        for (AnthropicMessage message : request.getMessages()) {
            if (CollectionUtils.isEmpty(message.getContent())) {
                continue;
            }
            for (AnthropicMessage.ContentBlock block : message.getContent()) {
                total += estimateTokens(block.getText());
            }
        }
        return total;
    }

    private int estimateTokens(String text) {
        if (text == null) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    private Map<String, Object> parseArguments(String arguments) {
        try {
            if (arguments == null || arguments.isBlank()) {
                return Map.of();
            }
            return mapper.readValue(arguments, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            log.error("Failed to parse tool arguments", ex);
            return Map.of();
        }
    }

    private ObjectNode convertToolChoice(Map<String, Object> toolChoice) {
        ObjectNode node = mapper.createObjectNode();
        Object type = toolChoice.get("type");
        if (type instanceof String choiceType) {
            switch (choiceType) {
                case "auto":
                    node.put("type", "AUTO");
                    break;
                case "any":
                    node.put("type", "AUTO");
                    break;
                case "none":
                    node.put("type", "NONE");
                    break;
                case "required":
                    node.put("type", "REQUIRED");
                    if (toolChoice.containsKey("name")) {
                        node.put("name", String.valueOf(toolChoice.get("name")));
                    }
                    break;
                default:
                    node.put("type", "SPECIFIC");
                    node.put("name", choiceType);
            }
        } else {
            node.put("type", "AUTO");
        }
        if (toolChoice.containsKey("name") && !node.has("name")) {
            node.put("name", String.valueOf(toolChoice.get("name")));
        }
        return node;
    }

    private List<String> buildStreamEvents(AnthropicChatResponse response) {
        List<String> events = new ArrayList<>();
        String messageId = response.getId();

        ObjectNode messageStart = mapper.createObjectNode();
        messageStart.put("type", "message_start");
        ObjectNode messageNode = mapper.createObjectNode();
        messageNode.put("id", messageId);
        messageNode.put("type", "message");
        messageNode.put("role", response.getRole());
        messageNode.put("model", response.getModel());
        messageNode.putNull("stop_reason");
        messageNode.putNull("stop_sequence");
        messageNode.put("created_at", response.getCreatedAt());
        messageStart.set("message", messageNode);
        events.add(toSseEvent("message_start", messageStart));

        List<AnthropicMessage.ContentBlock> contentBlocks = response.getContent();
        if (contentBlocks != null && !contentBlocks.isEmpty()) {
            for (int index = 0; index < contentBlocks.size(); index++) {
                AnthropicMessage.ContentBlock block = contentBlocks.get(index);
                String blockType = block.getType();

                ObjectNode blockStart = mapper.createObjectNode();
                blockStart.put("type", "content_block_start");
                blockStart.put("index", index);
                ObjectNode blockNode = mapper.createObjectNode();
                blockNode.put("type", blockType);
                if ("tool_use".equals(blockType)) {
                    blockNode.put("id", block.getId());
                    blockNode.put("name", block.getName());
                    blockNode.set("input", mapper.valueToTree(block.getInput()));
                }
                blockStart.set("content_block", blockNode);
                events.add(toSseEvent("content_block_start", blockStart));

                if ("text".equals(blockType)) {
                    ObjectNode delta = mapper.createObjectNode();
                    delta.put("type", "content_block_delta");
                    delta.put("index", index);
                    ObjectNode deltaNode = mapper.createObjectNode();
                    deltaNode.put("type", "text_delta");
                    deltaNode.put("text", block.getText() != null ? block.getText() : "");
                    delta.set("delta", deltaNode);
                    events.add(toSseEvent("content_block_delta", delta));
                }

                ObjectNode blockStop = mapper.createObjectNode();
                blockStop.put("type", "content_block_stop");
                blockStop.put("index", index);
                events.add(toSseEvent("content_block_stop", blockStop));
            }
        }

        ObjectNode messageDelta = mapper.createObjectNode();
        messageDelta.put("type", "message_delta");
        ObjectNode deltaNode = mapper.createObjectNode();
        deltaNode.put("stop_reason", response.getStopReason());
        if (response.getStopSequence() != null) {
            deltaNode.put("stop_sequence", response.getStopSequence());
        } else {
            deltaNode.putNull("stop_sequence");
        }
        messageDelta.set("delta", deltaNode);
        if (response.getUsage() != null) {
            ObjectNode usageNode = mapper.createObjectNode();
            usageNode.put("input_tokens", response.getUsage().getInputTokens());
            usageNode.put("output_tokens", response.getUsage().getOutputTokens());
            messageDelta.set("usage", usageNode);
        }
        events.add(toSseEvent("message_delta", messageDelta));

        ObjectNode messageStop = mapper.createObjectNode();
        messageStop.put("type", "message_stop");
        events.add(toSseEvent("message_stop", messageStop));

        return events;
    }

    private String toSseEvent(String eventName, ObjectNode payload) {
        try {
            String data = mapper.writeValueAsString(payload);
            return "event: " + eventName + "\n" + "data: " + data + "\n\n";
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize SSE payload", e);
        }
    }

    private String mapModel(String modelId) {
        return switch (modelId) {
            case "claude-sonnet-4-5-20250929" -> "CLAUDE_SONNET_4_5_20250929_V1_0";
            case "claude-3-5-sonnet-20241022" -> "CLAUDE_3_5_SONNET_20241022_V1_0";
            case "claude-3-5-haiku-20241022" -> "auto";
            default -> "CLAUDE_3_5_SONNET_20241022_V1_0";
        };
    }

}
 
