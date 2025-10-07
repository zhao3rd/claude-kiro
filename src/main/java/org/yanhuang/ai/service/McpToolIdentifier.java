package org.yanhuang.ai.service;

import org.springframework.stereotype.Component;
import org.yanhuang.ai.model.ToolDefinition;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP (Model Context Protocol) tool identifier service.
 * Identifies and categorizes MCP tools based on naming conventions.
 *
 * MCP tools are identified by the "mcp__" prefix in their names.
 * Example: "mcp__sequential-thinking__sequentialthinking"
 */
@Component
public class McpToolIdentifier {

    /**
     * MCP tool name prefix used to identify MCP server tools.
     */
    public static final String MCP_TOOL_PREFIX = "mcp__";

    /**
     * Check if a tool name indicates an MCP tool.
     *
     * @param toolName Tool name to check
     * @return true if the tool is an MCP tool (starts with "mcp__"), false otherwise
     */
    public boolean isMcpTool(String toolName) {
        return toolName != null && toolName.startsWith(MCP_TOOL_PREFIX);
    }

    /**
     * Extract the MCP server name from a full MCP tool name.
     * Example: "mcp__sequential-thinking__sequentialthinking" → "sequential-thinking"
     *
     * @param mcpToolName Full MCP tool name
     * @return MCP server name, or null if not a valid MCP tool name
     */
    public String extractMcpServerName(String mcpToolName) {
        if (!isMcpTool(mcpToolName)) {
            return null;
        }

        // Remove "mcp__" prefix
        String withoutPrefix = mcpToolName.substring(MCP_TOOL_PREFIX.length());

        // Extract server name (part before the second "__")
        int secondSeparator = withoutPrefix.indexOf("__");
        if (secondSeparator > 0) {
            return withoutPrefix.substring(0, secondSeparator);
        }

        // If no second separator, the whole remaining part is the server name
        return withoutPrefix;
    }

    /**
     * Extract the tool function name from a full MCP tool name.
     * Example: "mcp__sequential-thinking__sequentialthinking" → "sequentialthinking"
     *
     * @param mcpToolName Full MCP tool name
     * @return Tool function name, or the original name if not a valid MCP tool
     */
    public String extractToolFunctionName(String mcpToolName) {
        if (!isMcpTool(mcpToolName)) {
            return mcpToolName;
        }

        // Remove "mcp__" prefix
        String withoutPrefix = mcpToolName.substring(MCP_TOOL_PREFIX.length());

        // Extract function name (part after the second "__")
        int secondSeparator = withoutPrefix.indexOf("__");
        if (secondSeparator > 0 && secondSeparator < withoutPrefix.length() - 2) {
            return withoutPrefix.substring(secondSeparator + 2);
        }

        // If no valid separator, return the part without prefix
        return withoutPrefix;
    }

    /**
     * Filter a list of tool definitions to get only MCP tools.
     *
     * @param tools List of all tool definitions
     * @return List containing only MCP tools
     */
    public List<ToolDefinition> filterMcpTools(List<ToolDefinition> tools) {
        if (tools == null) {
            return List.of();
        }

        return tools.stream()
            .filter(tool -> isMcpTool(tool.getEffectiveName()))
            .collect(Collectors.toList());
    }

    /**
     * Filter a list of tool definitions to get only non-MCP (native) tools.
     *
     * @param tools List of all tool definitions
     * @return List containing only non-MCP tools
     */
    public List<ToolDefinition> filterNativeTools(List<ToolDefinition> tools) {
        if (tools == null) {
            return List.of();
        }

        return tools.stream()
            .filter(tool -> !isMcpTool(tool.getEffectiveName()))
            .collect(Collectors.toList());
    }

    /**
     * Count MCP tools in a list of tool definitions.
     *
     * @param tools List of tool definitions
     * @return Number of MCP tools
     */
    public long countMcpTools(List<ToolDefinition> tools) {
        if (tools == null) {
            return 0;
        }

        return tools.stream()
            .filter(tool -> isMcpTool(tool.getEffectiveName()))
            .count();
    }
}
