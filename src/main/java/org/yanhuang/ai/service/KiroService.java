package org.yanhuang.ai.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
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
    private final McpToolIdentifier mcpToolIdentifier;
    private final WebClient webClient;
    private final ObjectMapper mapper;

    public KiroService(AppProperties properties,
                       TokenManager tokenManager,
                       CodeWhispererEventParser eventParser,
                       BracketToolCallParser bracketToolCallParser,
                       ToolCallDeduplicator toolCallDeduplicator,
                       McpToolIdentifier mcpToolIdentifier,
                       WebClient.Builder webClientBuilder,
                       ObjectMapper mapper) {
        this.properties = properties;
        this.tokenManager = tokenManager;
        this.eventParser = eventParser;
        this.bracketToolCallParser = bracketToolCallParser;
        this.toolCallDeduplicator = toolCallDeduplicator;
        this.mcpToolIdentifier = mcpToolIdentifier;
        this.webClient = webClientBuilder.baseUrl(properties.getKiro().getBaseUrl()).build();
        this.mapper = mapper;
    }

    public Mono<AnthropicChatResponse> createCompletion(AnthropicChatRequest request) {
        return callKiroEvents(request)
            .map(events -> mapResponse(events, request));
    }

    public Flux<String> streamCompletion(AnthropicChatRequest request) {
        if (log.isDebugEnabled()) {
            log.debug("=== Starting stream completion ===");
        }
        return callKiroEvents(request)
            .map(events -> {
                if (log.isDebugEnabled()) {
                    log.debug("=== Map events to response ===");
                }
                AnthropicChatResponse response = mapResponse(events, request);
                if (log.isDebugEnabled()) {
                    log.debug("Response has {} content blocks", response.getContent() != null ? response.getContent().size() : 0);
                    log.debug("Stop reason: {}", response.getStopReason());
                }
                return response;
            })
            .flatMapMany(response -> {
                if (log.isDebugEnabled()) {
                    log.debug("=== Building stream events ===");
                }
                List<String> events = buildStreamEvents(response);
                if (log.isDebugEnabled()) {
                    log.debug("Built {} stream events", events.size());
                    for (int i = 0; i < events.size(); i++) {
                        log.debug("Stream event {}: {}", i, events.get(i));
                    }
                }
                return Flux.fromIterable(events);
            });
    }

    private Mono<List<JsonNode>> callKiroEvents(AnthropicChatRequest request) {
        ObjectNode payload = buildKiroPayload(request);
        String token = tokenManager.ensureToken();

        if (log.isDebugEnabled()) {
            log.debug("=== Kiro API Request Debug ===");
            log.debug("URL: {}", properties.getKiro().getBaseUrl());
            log.debug("Authorization: Bearer {}...", token.substring(0, Math.min(token.length(), 20)));
            log.debug("Content-Type: {}", MediaType.APPLICATION_JSON);
            log.debug("Accept: {}", MediaType.TEXT_EVENT_STREAM);
            log.debug("Profile ARN: {}", properties.getKiro().getProfileArn());
            log.debug("Payload size: {} characters", payload.toString().length());
            try {
                log.debug("Payload-from-cc: {}", new ObjectMapper().writeValueAsString(request));
                log.debug("Payload-to-kiro: {}", payload);
            } catch (JsonProcessingException e) {
                log.warn("print payload error", e);
            }
        }

        return webClient.post()
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(byte[].class)
            .map(bytes -> {
                List<JsonNode> events = eventParser.parse(bytes);
                if (log.isDebugEnabled()) {
                    log.debug("Parsed {} events from Kiro response", events.size());
                    for (int i = 0; i < events.size(); i++) {
                        log.debug("Event {}: {}", i, events.get(i).toString());
                    }
                }
                return events;
            })
            .timeout(Duration.ofSeconds(120))
            .onErrorResume(error -> {
                log.error("=== Kiro API Error Debug ===");
                if (error instanceof WebClientResponseException) {
                    WebClientResponseException webEx = (WebClientResponseException) error;
                    log.error("Status Code: {}", webEx.getStatusCode());
                    log.error("Status Text: {}", webEx.getStatusText());
                    log.error("Response Headers: {}", webEx.getHeaders());
                    log.error("Response Body: {}", webEx.getResponseBodyAsString());
                } else {
                    log.error("Error Type: {}", error.getClass().getSimpleName());
                    log.error("Error Message: {}", error.getMessage());
                }
                log.error("Request URL: {}", properties.getKiro().getBaseUrl());
                log.error("Original Payload size: {} characters", payload.toString().length());

                return tokenManager.refreshIfNeeded()
                    .flatMap(refreshed -> {
                        if (log.isDebugEnabled()) {
                            log.debug("=== Kiro API Retry Debug ===");
                            log.debug("Token refreshed: {}", refreshed);
                            log.debug("Retrying with new token...");
                        }
                        String newToken = tokenManager.ensureToken();
                        if (log.isDebugEnabled()) {
                            log.debug("New Authorization: Bearer {}...", newToken.substring(0, Math.min(newToken.length(), 20)));
                        }

                        return webClient.post()
                            .header("Authorization", "Bearer " + newToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.TEXT_EVENT_STREAM)
                            .bodyValue(payload)
                            .retrieve()
                            .bodyToMono(byte[].class)
                            .map(eventParser::parse)
                            .timeout(Duration.ofSeconds(120))
                            .onErrorResume(retryError -> {
                                log.error("=== Retry Failed ===");
                                if (retryError instanceof WebClientResponseException) {
                                    WebClientResponseException retryWebEx = (WebClientResponseException) retryError;
                                    log.error("Retry Status Code: {}", retryWebEx.getStatusCode());
                                    log.error("Retry Status Text: {}", retryWebEx.getStatusText());
                                    log.error("Retry Response Body: {}", retryWebEx.getResponseBodyAsString());
                                }
                                log.error("Retry Error: {}", retryError.getMessage());
                                return Mono.error(retryError);
                            });
                    });
            });
    }

    // Package-private for testing
    ObjectNode buildKiroPayload(AnthropicChatRequest request) {
        if (log.isDebugEnabled()) {
            log.debug("=== Building Kiro Payload ===");
        }

        String conversationId = UUID.randomUUID().toString();
        if (log.isDebugEnabled()) {
            log.debug("Generated conversationId: {}", conversationId);
        }

        ObjectNode conversationState = mapper.createObjectNode();
        conversationState.put("chatTriggerType", "MANUAL");
        conversationState.put("conversationId", conversationId);

        ObjectNode currentMessage = mapper.createObjectNode();
        ObjectNode userInput = mapper.createObjectNode();

        String content = buildCurrentMessageContent(request);
        String modelId = mapModel(request.getModel());

        userInput.put("content", content);
        userInput.put("modelId", modelId);
        userInput.put("origin", "AI_EDITOR");

        if (log.isDebugEnabled()) {
            log.debug("Current message: content_length={}, modelId={}", content.length(), modelId);
        }

        // Log tools context status
        boolean disableToolsContext = properties.getKiro().isDisableTools();
        if (log.isDebugEnabled()) {
            log.debug("Tools context disabled: {}", disableToolsContext);
        }

        if (!disableToolsContext && !CollectionUtils.isEmpty(request.getTools())) {
            // Log MCP tool detection
            long mcpToolCount = mcpToolIdentifier.countMcpTools(request.getTools());
            if (mcpToolCount > 0) {
                if (log.isDebugEnabled()) {
                    log.debug("Request contains {} MCP tools out of {} total tools", mcpToolCount, request.getTools().size());
                    // Log detailed MCP tool information
                    mcpToolIdentifier.filterMcpTools(request.getTools()).forEach(tool -> {
                        String serverName = mcpToolIdentifier.extractMcpServerName(tool.getEffectiveName());
                        String functionName = mcpToolIdentifier.extractToolFunctionName(tool.getEffectiveName());
                        log.debug("  MCP Tool: server={}, function={}, fullName={}", serverName, functionName, tool.getEffectiveName());
                    });
                }
            }

            ObjectNode context = mapper.createObjectNode();
            ArrayNode toolsNode = mapper.createArrayNode();
            request.getTools().forEach(tool -> {
                ObjectNode specNode = mapper.createObjectNode();
                ObjectNode toolSpec = mapper.createObjectNode();

                // Use effective methods to handle both direct and Anthropic function format
                String effectiveName = tool.getEffectiveName();
                String effectiveDescription = tool.getEffectiveDescription();
                Map<String, Object> effectiveInputSchema = tool.getEffectiveInputSchema();

                // Always include required fields, using defaults if missing
                // Name is required and must not be null
                if (effectiveName != null) {
                    toolSpec.put("name", effectiveName);
                } else {
                    log.warn("Tool definition missing name, using default");
                    toolSpec.put("name", "general_tool");
                }

                // Description should always be present (use empty string if missing)
                if (effectiveDescription != null && !effectiveDescription.isEmpty()) {
                    toolSpec.put("description", effectiveDescription);
                } else {
                    toolSpec.put("description", toolSpec.get("name"));
                }

                // InputSchema should always be present
                // Ensure it at least has "type": "object" for Kiro API compliance
                if (effectiveInputSchema != null && !effectiveInputSchema.isEmpty()) {
                    // If the schema doesn't have a "type" field, add it
                    if (!effectiveInputSchema.containsKey("type")) {
                        effectiveInputSchema = new HashMap<>(effectiveInputSchema);
                        effectiveInputSchema.put("type", "object");
                    }
                    toolSpec.set("inputSchema", mapper.createObjectNode().set("json", mapper.valueToTree(effectiveInputSchema)));
                } else {
                    // Create minimal valid schema with type and properties
                    ObjectNode minimalSchema = mapper.createObjectNode();
                    minimalSchema.put("type", "object");
                    minimalSchema.set("properties", mapper.createObjectNode());
                    toolSpec.set("inputSchema", mapper.createObjectNode().set("json", minimalSchema));
                }

                specNode.set("toolSpecification", toolSpec);
                toolsNode.add(specNode);

                if (log.isDebugEnabled()) {
                    log.debug("Added tool: name={}, description={}, hasInputSchema={}",
                        effectiveName != null ? effectiveName : "null",
                        effectiveDescription != null ? effectiveDescription : "empty",
                        effectiveInputSchema != null && !effectiveInputSchema.isEmpty());
                }
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

        // Add thinking parameter if present (for extended thinking mode)
        if (request.getThinking() != null && !request.getThinking().isEmpty()) {
            userInput.set("thinking", mapper.valueToTree(request.getThinking()));
            if (log.isDebugEnabled()) {
                log.debug("Extended thinking enabled with config: {}", request.getThinking());
            }
        }

        currentMessage.set("userInputMessage", userInput);

        ArrayNode history = buildHistory(request);
        conversationState.set("currentMessage", currentMessage);
        conversationState.set("history", history);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("profileArn", properties.getKiro().getProfileArn());
        payload.set("conversationState", conversationState);

        // Final payload analysis
        if (log.isDebugEnabled()) {
            String payloadString = payload.toString();
            log.debug("=== Kiro Payload Analysis Complete ===");
            log.debug("Final payload size: {} characters ({} KB)", payloadString.length(), payloadString.length() / 1024);
            log.debug("Profile ARN: {}", properties.getKiro().getProfileArn());
            log.debug("History messages: {}", history.size());
            log.debug("Payload preview: {}", truncate(payloadString, 500));
        }

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
                    } else if ("image".equalsIgnoreCase(block.getType()) && block.getSource() != null) {
                        // Include a concise image marker so downstream can be aware of images
                        AnthropicMessage.ImageSource src = block.getSource();
                        String media = src.getMediaType() != null ? src.getMediaType() : "unknown";
                        String srcType = src.getType() != null ? src.getType() : "unknown";
                        segments.add("[" + lastMessage.getRole() + "] " + "<image media=" + media + ", type=" + srcType + ">");
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

        // Track tool calls being built from streaming events
        Map<String, ToolCallBuilder> toolCallBuilders = new HashMap<>();

        if (log.isDebugEnabled()) {
            log.debug("=== Parsing {} events from Kiro response ===", events.size());
        }
        for (int i = 0; i < events.size(); i++) {
            JsonNode event = events.get(i);
            if (log.isDebugEnabled()) {
                log.debug("Event {}: {}", i, event.toPrettyString());
            }

            // Handle text content
            if (event.hasNonNull("content")) {
                String content = event.get("content").asText();
                contentBuilder.append(content);
                if (log.isDebugEnabled()) {
                    log.debug("Event {} content: {}", i, content);
                }
            }

            // Handle tool use events from Kiro
            if (event.hasNonNull("name") && event.hasNonNull("toolUseId")) {
                String toolUseId = event.get("toolUseId").asText();
                String name = event.get("name").asText();

                if (log.isDebugEnabled()) {
                    log.debug("Event {} has tool event: name={}, toolUseId={}", i, name, toolUseId);
                }

                // Initialize or update tool call builder
                ToolCallBuilder builder = toolCallBuilders.computeIfAbsent(toolUseId,
                    id -> {
                        if (log.isDebugEnabled()) {
                            log.debug("Creating new ToolCallBuilder for {}", id);
                        }
                        return new ToolCallBuilder(id, name);
                    });

                // Append input chunks if present
                if (event.hasNonNull("input")) {
                    String input = event.get("input").asText();
                    builder.appendInput(input);
                    if (log.isDebugEnabled()) {
                        log.debug("Event {} appending input for {}: {}", i, name, input);
                    }
                }

                // Check if this is the final event for this tool call
                if (event.hasNonNull("stop")) {
                    boolean stopValue = event.get("stop").asBoolean();
                    if (log.isDebugEnabled()) {
                        log.debug("Event {} has stop field: {}", i, stopValue);
                    }
                    if (stopValue) {
                        ToolCall toolCall = builder.build();
                        toolCalls.add(toolCall);
                        log.info("Completed tool call from Kiro events: {} with args: {}",
                            name, builder.getInputBuilder().toString());
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Event {} does not have stop field", i);
                    }
                }
            }

            // Fallback: Handle legacy toolCalls field format
            if (event.hasNonNull("toolCalls")) {
                if (log.isDebugEnabled()) {
                    log.debug("Event {} has toolCalls field", i);
                }
                event.get("toolCalls").forEach(callNode -> {
                    ToolCall call = mapper.convertValue(callNode, ToolCall.class);
                    toolCalls.add(call);
                    log.info("Added tool call from toolCalls: {}", call.getFunction().getName());
                });
            }

            // Fallback: Parse bracket format from rawText
            if (event.hasNonNull("rawText")) {
                String rawText = event.get("rawText").asText();
                if (log.isDebugEnabled()) {
                    log.debug("Event {} rawText: {}", i, rawText);
                }
                List<ToolCall> bracketCalls = bracketToolCallParser.parse(rawText);
                if (bracketCalls != null && !bracketCalls.isEmpty()) {
                    log.info("Parsed {} tool calls from bracket format in event {}", bracketCalls.size(), i);
                    for (ToolCall call : bracketCalls) {
                        log.info("  Tool call: {}", call.getFunction().getName());
                    }
                    toolCalls.addAll(bracketCalls);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("No bracket format tool calls found in event {}", i);
                    }
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("=== Total tool calls found: {} ===", toolCalls.size());
        }

        List<ToolCall> uniqueToolCalls = toolCallDeduplicator.deduplicate(toolCalls);

        // Determine stop_reason based on response characteristics
        String stopReason = determineStopReason(events, uniqueToolCalls, contentBuilder, request);
        response.setStopReason(stopReason);

        // Check for stop sequences in content
        if ("stop_sequence".equals(stopReason) && request.getStopSequences() != null) {
            for (String seq : request.getStopSequences()) {
                if (contentBuilder.toString().contains(seq)) {
                    response.setStopSequence(seq);
                    break;
                }
            }
        }

        if (uniqueToolCalls.isEmpty()) {
            AnthropicMessage.ContentBlock block = new AnthropicMessage.ContentBlock();
            block.setType("text");

            // Add thinking mode warning if requested but not supported
            String finalText = contentBuilder.toString();
            if (request.getThinking() != null && !request.getThinking().isEmpty()) {
                String warning = "[Note: Extended thinking mode is not supported by Kiro Gateway. Response generated in standard mode.]\n\n";
                finalText = warning + finalText;
                log.info("Added thinking mode unsupported warning to response");
            }

            // If the last message carried images, append a short note for E2E verification
            if (!CollectionUtils.isEmpty(request.getMessages())) {
                AnthropicMessage last = request.getMessages().get(request.getMessages().size() - 1);
                if (!CollectionUtils.isEmpty(last.getContent())) {
                    long imageCount = last.getContent().stream()
                        .filter(cb -> "image".equalsIgnoreCase(cb.getType()) && cb.getSource() != null)
                        .count();
                    if (imageCount > 0) {
                        finalText = String.format("[Note: %d image(s) received by Kiro]\n\n", imageCount) + finalText;
                    }
                }
            }

            block.setText(finalText);
            response.addContentBlock(block);
        } else {
            uniqueToolCalls.forEach(call -> {
                AnthropicMessage.ContentBlock block = new AnthropicMessage.ContentBlock();
                block.setType("tool_use");
                block.setName(call.getFunction().getName());
                // Use Anthropic-compliant tool ID format
                block.setId(call.getId() != null ? call.getId() : "toolu_" + UUID.randomUUID().toString().replace("-", ""));
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

        // Apply history limit to control payload size
        int maxHistoryMessages = properties.getKiro().getMaxHistoryMessages();
        boolean disableHistory = properties.getKiro().isDisableHistory();

        if (log.isDebugEnabled()) {
            log.debug("History settings: disabled={}, max_messages={}, actual_messages={}",
                disableHistory, maxHistoryMessages, historicalMessages.size());
        }

        if (disableHistory) {
            log.info("History completely disabled");
            return history;
        }

        // Limit history to the most recent messages
        if (historicalMessages.size() > maxHistoryMessages) {
            int skipCount = historicalMessages.size() - maxHistoryMessages;
            historicalMessages = historicalMessages.subList(skipCount, historicalMessages.size());
            log.info("Limited history by skipping {} oldest messages, keeping {} most recent",
                skipCount, maxHistoryMessages);
        }

        // Step 1: Process messages into (role, content) pairs
        // This step merges tool results and handles special cases
        List<MessagePair> processedMessages = new ArrayList<>();
        final int maxHistorySize = properties.getKiro().getMaxHistorySize();
        int totalContentSize = 0;

        for (AnthropicMessage message : historicalMessages) {
            String content = buildMessageContent(message);

            // Check size limit
            if (totalContentSize + content.length() > maxHistorySize) {
                if (log.isDebugEnabled()) {
                    log.debug("Skipping message due to size limit: current={}, message_size={}, limit={}",
                        totalContentSize, content.length(), maxHistorySize);
                }
                break;
            }

            totalContentSize += content.length();

            if ("user".equalsIgnoreCase(message.getRole())) {
                processedMessages.add(new MessagePair("user", content));
            } else if ("assistant".equalsIgnoreCase(message.getRole())) {
                processedMessages.add(new MessagePair("assistant", content));
            }
            // Note: tool messages are not directly supported in Anthropic format
            // They would be part of content blocks in user/assistant messages
        }

        // Step 2: Build history pairs ensuring alternating pattern
        // According to ki2api/app.py (lines 695-742), history must strictly alternate:
        // userInputMessage -> assistantResponseMessage -> userInputMessage -> assistantResponseMessage
        String historyModelId = mapModel(request.getModel());
        int i = 0;
        while (i < processedMessages.size()) {
            MessagePair current = processedMessages.get(i);

            if ("user".equals(current.role)) {
                // Add userInputMessage
                ObjectNode userNode = mapper.createObjectNode();
                userNode.set("userInputMessage", mapper.createObjectNode()
                    .put("content", current.content)
                    .put("modelId", historyModelId)
                    .put("origin", "AI_EDITOR"));
                history.add(userNode);
                if (log.isDebugEnabled()) {
                    log.debug("History userInputMessage added: content_length={}", current.content.length());
                }

                // Look for assistant response
                if (i + 1 < processedMessages.size() && "assistant".equals(processedMessages.get(i + 1).role)) {
                    // Found paired assistant response
                    MessagePair assistant = processedMessages.get(i + 1);
                    ObjectNode assistantNode = mapper.createObjectNode();
                    assistantNode.set("assistantResponseMessage", mapper.createObjectNode()
                        .put("content", assistant.content));
                    history.add(assistantNode);
                    if (log.isDebugEnabled()) {
                        log.debug("History assistantResponseMessage added: content_length={}", assistant.content.length());
                    }
                    i += 2;
                } else {
                    // No assistant response, add placeholder
                    ObjectNode assistantNode = mapper.createObjectNode();
                    assistantNode.set("assistantResponseMessage", mapper.createObjectNode()
                        .put("content", "I understand."));
                    history.add(assistantNode);
                    log.debug("History assistantResponseMessage placeholder added");
                    i += 1;
                }
            } else if ("assistant".equals(current.role)) {
                // Orphaned assistant message - add placeholder user message first
                ObjectNode userNode = mapper.createObjectNode();
                userNode.set("userInputMessage", mapper.createObjectNode()
                    .put("content", "Continue")
                    .put("modelId", historyModelId)
                    .put("origin", "AI_EDITOR"));
                history.add(userNode);
                log.debug("History userInputMessage placeholder added for orphaned assistant");

                ObjectNode assistantNode = mapper.createObjectNode();
                assistantNode.set("assistantResponseMessage", mapper.createObjectNode()
                    .put("content", current.content));
                history.add(assistantNode);
                if (log.isDebugEnabled()) {
                    log.debug("History orphaned assistantResponseMessage added: content_length={}", current.content.length());
                }
                i += 1;
            } else {
                i += 1;
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Final history: {} messages, {} characters", history.size(), totalContentSize);
        }

        return history;
    }

    /**
     * Helper class to hold processed message pairs
     */
    private static class MessagePair {
        final String role;
        final String content;

        MessagePair(String role, String content) {
            this.role = role;
            this.content = content;
        }
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
            } else if ("tool_result".equalsIgnoreCase(block.getType())) {
                // Handle tool result - include in message content for Kiro
                builder.append("[Tool ")
                    .append(block.getToolUseId())
                    .append(" returned: ")
                    .append(serializeToolResult(block.getContent()))
                    .append("]");
            }
        });
        return builder.toString();
    }

    /**
     * Serialize tool result content to string format
     */
    private String serializeToolResult(Object content) {
        if (content == null) {
            return "null";
        }
        if (content instanceof String) {
            return (String) content;
        }
        try {
            return mapper.writeValueAsString(content);
        } catch (Exception ex) {
            log.error("Failed to serialize tool result", ex);
            return content.toString();
        }
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

    /**
     * Determine stop_reason based on response characteristics
     * Anthropic API supports: end_turn, max_tokens, stop_sequence, tool_use, content_filter
     */
    private String determineStopReason(List<JsonNode> events, List<ToolCall> toolCalls,
                                      StringBuilder contentBuilder, AnthropicChatRequest request) {
        // Priority 1: Tool use
        if (!toolCalls.isEmpty()) {
            return "tool_use";
        }

        // Priority 2: Check for explicit stop indicators in events
        for (JsonNode event : events) {
            // Check for content filter or moderation flags
            if (event.hasNonNull("contentFilter") || event.hasNonNull("moderation")) {
                boolean filtered = event.path("contentFilter").asBoolean(false) ||
                                 event.path("moderation").asBoolean(false);
                if (filtered) {
                    return "content_filter";
                }
            }

            // Check for explicit finish reason from Kiro
            if (event.hasNonNull("finishReason")) {
                String kiroFinishReason = event.get("finishReason").asText();
                // Map Kiro finish reasons to Anthropic format
                switch (kiroFinishReason.toLowerCase()) {
                    case "max_tokens":
                    case "length":
                        return "max_tokens";
                    case "stop_sequence":
                    case "stop":
                        return "stop_sequence";
                    case "content_filter":
                    case "filtered":
                        return "content_filter";
                    default:
                        break;
                }
            }
        }

        // Priority 3: Check for stop sequences in content
        if (request.getStopSequences() != null && !request.getStopSequences().isEmpty()) {
            String content = contentBuilder.toString();
            for (String seq : request.getStopSequences()) {
                if (content.contains(seq)) {
                    return "stop_sequence";
                }
            }
        }

        // Priority 4: Estimate if max_tokens was reached
        int estimatedOutputTokens = estimateTokens(contentBuilder.toString());
        if (request.getMaxTokens() != null && estimatedOutputTokens >= request.getMaxTokens() - 10) {
            // Allow small margin for token estimation inaccuracy
            return "max_tokens";
        }

        // Default: Normal completion
        return "end_turn";
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

        // Add empty content array (official Anthropic API requirement)
        messageNode.set("content", mapper.createArrayNode());

        messageNode.putNull("stop_reason");
        messageNode.putNull("stop_sequence");

        // Add usage object with initial values (official Anthropic API requirement)
        if (response.getUsage() != null) {
            ObjectNode usageNode = mapper.createObjectNode();
            usageNode.put("input_tokens", response.getUsage().getInputTokens());
            usageNode.put("output_tokens", 0);  // Streaming starts with 0 output tokens
            messageNode.set("usage", usageNode);
        }

        messageNode.put("created_at", response.getCreatedAt());
        messageStart.set("message", messageNode);
        events.add(toSseEvent("message_start", messageStart));

        List<AnthropicMessage.ContentBlock> contentBlocks = response.getContent();
        if (contentBlocks != null && !contentBlocks.isEmpty()) {
            for (int index = 0; index < contentBlocks.size(); index++) {
                AnthropicMessage.ContentBlock block = contentBlocks.get(index);
                String blockType = block.getType();

                // Send content_block_start event
                ObjectNode blockStart = mapper.createObjectNode();
                blockStart.put("type", "content_block_start");
                blockStart.put("index", index);
                ObjectNode blockNode = mapper.createObjectNode();
                blockNode.put("type", blockType);
                if ("tool_use".equals(blockType)) {
                    // For tool_use, only include id and name in start event (no input yet)
                    blockNode.put("id", block.getId());
                    blockNode.put("name", block.getName());
                } else if ("text".equals(blockType)) {
                    blockNode.put("text", "");
                }
                blockStart.set("content_block", blockNode);
                events.add(toSseEvent("content_block_start", blockStart));

                // Send content_block_delta event(s)
                if ("text".equals(blockType)) {
                    ObjectNode delta = mapper.createObjectNode();
                    delta.put("type", "content_block_delta");
                    delta.put("index", index);
                    ObjectNode deltaNode = mapper.createObjectNode();
                    deltaNode.put("type", "text_delta");
                    deltaNode.put("text", block.getText() != null ? block.getText() : "");
                    delta.set("delta", deltaNode);
                    events.add(toSseEvent("content_block_delta", delta));
                } else if ("tool_use".equals(blockType)) {
                    // For tool_use, stream the input as JSON deltas
                    String inputJson = serializeToolInput(block.getInput());
                    List<String> jsonChunks = chunkJsonString(inputJson);
                    for (String chunk : jsonChunks) {
                        ObjectNode delta = mapper.createObjectNode();
                        delta.put("type", "content_block_delta");
                        delta.put("index", index);
                        ObjectNode deltaNode = mapper.createObjectNode();
                        deltaNode.put("type", "input_json_delta");
                        deltaNode.put("partial_json", chunk);
                        delta.set("delta", deltaNode);
                        events.add(toSseEvent("content_block_delta", delta));
                    }
                }

                // Send content_block_stop event
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

    /**
     * Serialize tool input Map to JSON string
     */
    private String serializeToolInput(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return "{}";
        }
        try {
            return mapper.writeValueAsString(input);
        } catch (Exception ex) {
            log.error("Failed to serialize tool input", ex);
            return "{}";
        }
    }

    /**
     * Chunk JSON string for streaming as partial_json deltas
     * Anthropic API requires valid JSON fragments in each chunk
     */
    private List<String> chunkJsonString(String json) {
        List<String> chunks = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return chunks;
        }

        // Simple chunking strategy: split at reasonable boundaries
        // For production, could be more sophisticated to ensure valid JSON fragments
        int chunkSize = 50; // Characters per chunk
        int length = json.length();

        for (int i = 0; i < length; i += chunkSize) {
            int end = Math.min(i + chunkSize, length);
            String chunk = json.substring(i, end);
            chunks.add(chunk);
        }

        // If only one small chunk, return it directly
        if (chunks.isEmpty()) {
            chunks.add(json);
        }

        return chunks;
    }

    private String mapModel(String modelId) {
        return switch (modelId) {
            case "claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022" -> "auto";
            default -> "CLAUDE_SONNET_4_5_20250929_V1_0";
        };
    }

    // Helper class to build tool calls from streaming events
    private static class ToolCallBuilder {
        private final String id;
        private final String name;
        private final StringBuilder inputBuilder;

        ToolCallBuilder(String id, String name) {
            this.id = id;
            this.name = name;
            this.inputBuilder = new StringBuilder();
        }

        void appendInput(String input) {
            inputBuilder.append(input);
        }

        StringBuilder getInputBuilder() {
            return inputBuilder;
        }

        ToolCall build() {
            ToolCall call = new ToolCall();
            call.setId(id);
            call.setType("function");

            ToolCall.ToolFunction function = new ToolCall.ToolFunction();
            function.setName(name);
            function.setArguments(inputBuilder.toString());
            call.setFunction(function);

            return call;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 3)) + "...";
    }

}
 
