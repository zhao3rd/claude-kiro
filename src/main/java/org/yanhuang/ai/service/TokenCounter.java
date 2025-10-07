package org.yanhuang.ai.service;

import org.springframework.stereotype.Component;
import org.yanhuang.ai.model.AnthropicChatRequest;
import org.yanhuang.ai.model.AnthropicMessage;
import org.yanhuang.ai.model.ToolDefinition;

import java.util.List;
import java.util.Map;

/**
 * Token counter service for estimating context window usage.
 * Uses character-based approximation algorithm (1 token ≈ 4 characters for English).
 */
@Component
public class TokenCounter {

    // Anthropic Claude API context window limits
    public static final int MAX_CONTEXT_TOKENS_API_MODE = 1_000_000;      // 1M tokens for API mode
    public static final int MAX_CONTEXT_TOKENS_SUBSCRIPTION = 200_000;    // 200K tokens for subscription mode

    // Conservative approximation: 1 token ≈ 4 characters (for English text)
    private static final int CHARS_PER_TOKEN = 4;

    // Additional overhead for JSON structure and metadata
    private static final double JSON_OVERHEAD_FACTOR = 1.15;  // 15% overhead for JSON formatting

    /**
     * Estimate total token count for a complete request.
     *
     * @param request The Anthropic chat request
     * @return Estimated token count
     */
    public int estimateRequestTokens(AnthropicChatRequest request) {
        int totalChars = 0;

        // Count system message tokens
        if (request.getSystem() != null) {
            totalChars += estimateContentBlocksChars(request.getSystem());
        }

        // Count conversation message tokens
        if (request.getMessages() != null) {
            for (AnthropicMessage message : request.getMessages()) {
                // Role overhead (e.g., "user:", "assistant:")
                totalChars += message.getRole() != null ? message.getRole().length() : 0;

                // Content tokens
                if (message.getContent() != null) {
                    totalChars += estimateContentBlocksChars(message.getContent());
                }
            }
        }

        // Count tool definition tokens
        if (request.getTools() != null) {
            for (ToolDefinition tool : request.getTools()) {
                totalChars += estimateToolDefinitionChars(tool);
            }
        }

        // Apply JSON overhead and convert to tokens
        int estimatedTokens = (int) Math.ceil((totalChars * JSON_OVERHEAD_FACTOR) / CHARS_PER_TOKEN);

        // Add max_tokens (output budget) to get total context usage
        if (request.getMaxTokens() != null) {
            estimatedTokens += request.getMaxTokens();
        }

        return estimatedTokens;
    }

    /**
     * Estimate character count for a list of content blocks.
     *
     * @param contentBlocks List of content blocks
     * @return Total character count
     */
    private int estimateContentBlocksChars(List<AnthropicMessage.ContentBlock> contentBlocks) {
        int totalChars = 0;

        for (AnthropicMessage.ContentBlock block : contentBlocks) {
            if (block.getType() != null) {
                totalChars += block.getType().length();
            }

            if (block.getText() != null) {
                totalChars += block.getText().length();
            }

            // Tool use content
            if (block.getName() != null) {
                totalChars += block.getName().length();
            }

            if (block.getInput() != null) {
                totalChars += estimateMapChars(block.getInput());
            }

            // Tool result content
            if (block.getToolUseId() != null) {
                totalChars += block.getToolUseId().length();
            }

            if (block.getContent() != null) {
                if (block.getContent() instanceof String) {
                    totalChars += ((String) block.getContent()).length();
                } else if (block.getContent() instanceof Map) {
                    totalChars += estimateMapChars((Map<?, ?>) block.getContent());
                }
            }

            // Image content
            if (block.getSource() != null) {
                totalChars += estimateImageSourceChars(block.getSource());
            }
        }

        return totalChars;
    }

    /**
     * Estimate character count for image source data.
     * Base64-encoded images contribute significantly to token count.
     *
     * @param imageSource Image source object
     * @return Character count
     */
    private int estimateImageSourceChars(AnthropicMessage.ImageSource imageSource) {
        int totalChars = 0;

        if (imageSource.getType() != null) {
            totalChars += imageSource.getType().length();
        }

        if (imageSource.getMediaType() != null) {
            totalChars += imageSource.getMediaType().length();
        }

        // Base64 data or URL - both count as significant characters
        if (imageSource.getData() != null) {
            totalChars += imageSource.getData().length();
        }

        return totalChars;
    }

    /**
     * Estimate character count for a tool definition.
     *
     * @param tool Tool definition
     * @return Character count
     */
    private int estimateToolDefinitionChars(ToolDefinition tool) {
        int totalChars = 0;

        if (tool.getEffectiveName() != null) {
            totalChars += tool.getEffectiveName().length();
        }

        if (tool.getEffectiveDescription() != null) {
            totalChars += tool.getEffectiveDescription().length();
        }

        if (tool.getEffectiveInputSchema() != null) {
            totalChars += estimateMapChars(tool.getEffectiveInputSchema());
        }

        return totalChars;
    }

    /**
     * Estimate character count for a generic map structure.
     *
     * @param map Map to estimate
     * @return Character count
     */
    private int estimateMapChars(Map<?, ?> map) {
        int totalChars = 0;

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            // Count key
            if (entry.getKey() != null) {
                totalChars += entry.getKey().toString().length();
            }

            // Count value
            if (entry.getValue() != null) {
                Object value = entry.getValue();
                if (value instanceof String) {
                    totalChars += ((String) value).length();
                } else if (value instanceof Map) {
                    totalChars += estimateMapChars((Map<?, ?>) value);
                } else if (value instanceof List) {
                    totalChars += estimateListChars((List<?>) value);
                } else {
                    totalChars += value.toString().length();
                }
            }
        }

        return totalChars;
    }

    /**
     * Estimate character count for a generic list structure.
     *
     * @param list List to estimate
     * @return Character count
     */
    private int estimateListChars(List<?> list) {
        int totalChars = 0;

        for (Object item : list) {
            if (item instanceof String) {
                totalChars += ((String) item).length();
            } else if (item instanceof Map) {
                totalChars += estimateMapChars((Map<?, ?>) item);
            } else if (item instanceof List) {
                totalChars += estimateListChars((List<?>) item);
            } else if (item != null) {
                totalChars += item.toString().length();
            }
        }

        return totalChars;
    }

    /**
     * Validate that request fits within context window limits.
     *
     * @param request The request to validate
     * @param maxTokens Maximum allowed tokens (use MAX_CONTEXT_TOKENS_* constants)
     * @throws IllegalArgumentException if request exceeds limit
     */
    public void validateContextWindow(AnthropicChatRequest request, int maxTokens) {
        int estimatedTokens = estimateRequestTokens(request);

        if (estimatedTokens > maxTokens) {
            throw new IllegalArgumentException(
                String.format(
                    "Request exceeds maximum context window: estimated %d tokens > limit %d tokens. " +
                    "Consider reducing message history, system prompts, or max_tokens parameter.",
                    estimatedTokens,
                    maxTokens
                )
            );
        }
    }
}
