package org.yanhuang.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yanhuang.ai.model.ToolDefinition;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for McpToolIdentifier service.
 */
@DisplayName("MCP Tool Identifier Tests")
class McpToolIdentifierTest {

    private McpToolIdentifier identifier;

    @BeforeEach
    void setUp() {
        identifier = new McpToolIdentifier();
    }

    @Test
    @DisplayName("Should identify MCP tool by prefix")
    void testIsMcpTool() {
        assertTrue(identifier.isMcpTool("mcp__sequential-thinking__sequentialthinking"));
        assertTrue(identifier.isMcpTool("mcp__magic__21st_magic_component_builder"));
        assertTrue(identifier.isMcpTool("mcp__context7__resolve-library-id"));

        assertFalse(identifier.isMcpTool("regular_tool"));
        assertFalse(identifier.isMcpTool("get_weather"));
        assertFalse(identifier.isMcpTool(null));
    }

    @Test
    @DisplayName("Should extract MCP server name correctly")
    void testExtractMcpServerName() {
        assertEquals("sequential-thinking",
            identifier.extractMcpServerName("mcp__sequential-thinking__sequentialthinking"));
        assertEquals("magic",
            identifier.extractMcpServerName("mcp__magic__21st_magic_component_builder"));
        assertEquals("context7",
            identifier.extractMcpServerName("mcp__context7__resolve-library-id"));
        assertEquals("playwright",
            identifier.extractMcpServerName("mcp__playwright__browser_navigate"));

        // Tools without second separator
        assertEquals("simple-server",
            identifier.extractMcpServerName("mcp__simple-server"));

        // Non-MCP tools should return null
        assertNull(identifier.extractMcpServerName("regular_tool"));
        assertNull(identifier.extractMcpServerName(null));
    }

    @Test
    @DisplayName("Should extract tool function name correctly")
    void testExtractToolFunctionName() {
        assertEquals("sequentialthinking",
            identifier.extractToolFunctionName("mcp__sequential-thinking__sequentialthinking"));
        assertEquals("21st_magic_component_builder",
            identifier.extractToolFunctionName("mcp__magic__21st_magic_component_builder"));
        assertEquals("resolve-library-id",
            identifier.extractToolFunctionName("mcp__context7__resolve-library-id"));
        assertEquals("browser_navigate",
            identifier.extractToolFunctionName("mcp__playwright__browser_navigate"));

        // Tools without second separator
        assertEquals("simple-server",
            identifier.extractToolFunctionName("mcp__simple-server"));

        // Non-MCP tools should return original name
        assertEquals("regular_tool",
            identifier.extractToolFunctionName("regular_tool"));
    }

    @Test
    @DisplayName("Should filter MCP tools from mixed list")
    void testFilterMcpTools() {
        ToolDefinition mcpTool1 = createTool("mcp__sequential-thinking__sequentialthinking");
        ToolDefinition mcpTool2 = createTool("mcp__magic__logo_search");
        ToolDefinition nativeTool1 = createTool("get_weather");
        ToolDefinition nativeTool2 = createTool("calculate");

        List<ToolDefinition> allTools = Arrays.asList(mcpTool1, nativeTool1, mcpTool2, nativeTool2);

        List<ToolDefinition> mcpTools = identifier.filterMcpTools(allTools);

        assertEquals(2, mcpTools.size());
        assertTrue(mcpTools.contains(mcpTool1));
        assertTrue(mcpTools.contains(mcpTool2));
    }

    @Test
    @DisplayName("Should filter native tools from mixed list")
    void testFilterNativeTools() {
        ToolDefinition mcpTool1 = createTool("mcp__sequential-thinking__sequentialthinking");
        ToolDefinition mcpTool2 = createTool("mcp__magic__logo_search");
        ToolDefinition nativeTool1 = createTool("get_weather");
        ToolDefinition nativeTool2 = createTool("calculate");

        List<ToolDefinition> allTools = Arrays.asList(mcpTool1, nativeTool1, mcpTool2, nativeTool2);

        List<ToolDefinition> nativeTools = identifier.filterNativeTools(allTools);

        assertEquals(2, nativeTools.size());
        assertTrue(nativeTools.contains(nativeTool1));
        assertTrue(nativeTools.contains(nativeTool2));
    }

    @Test
    @DisplayName("Should count MCP tools correctly")
    void testCountMcpTools() {
        ToolDefinition mcpTool1 = createTool("mcp__sequential-thinking__sequentialthinking");
        ToolDefinition mcpTool2 = createTool("mcp__magic__logo_search");
        ToolDefinition mcpTool3 = createTool("mcp__context7__get-library-docs");
        ToolDefinition nativeTool1 = createTool("get_weather");
        ToolDefinition nativeTool2 = createTool("calculate");

        List<ToolDefinition> allTools = Arrays.asList(
            mcpTool1, nativeTool1, mcpTool2, nativeTool2, mcpTool3
        );

        assertEquals(3, identifier.countMcpTools(allTools));
    }

    @Test
    @DisplayName("Should handle empty and null lists")
    void testEdgeCases() {
        assertEquals(0, identifier.filterMcpTools(null).size());
        assertEquals(0, identifier.filterNativeTools(null).size());
        assertEquals(0, identifier.countMcpTools(null));

        assertEquals(0, identifier.filterMcpTools(List.of()).size());
        assertEquals(0, identifier.filterNativeTools(List.of()).size());
        assertEquals(0, identifier.countMcpTools(List.of()));
    }

    @Test
    @DisplayName("Should handle all MCP tools list")
    void testAllMcpTools() {
        ToolDefinition mcpTool1 = createTool("mcp__server1__tool1");
        ToolDefinition mcpTool2 = createTool("mcp__server2__tool2");

        List<ToolDefinition> allTools = Arrays.asList(mcpTool1, mcpTool2);

        assertEquals(2, identifier.filterMcpTools(allTools).size());
        assertEquals(0, identifier.filterNativeTools(allTools).size());
        assertEquals(2, identifier.countMcpTools(allTools));
    }

    @Test
    @DisplayName("Should handle all native tools list")
    void testAllNativeTools() {
        ToolDefinition nativeTool1 = createTool("get_weather");
        ToolDefinition nativeTool2 = createTool("calculate");

        List<ToolDefinition> allTools = Arrays.asList(nativeTool1, nativeTool2);

        assertEquals(0, identifier.filterMcpTools(allTools).size());
        assertEquals(2, identifier.filterNativeTools(allTools).size());
        assertEquals(0, identifier.countMcpTools(allTools));
    }

    // Helper method to create a tool definition with a specific name
    private ToolDefinition createTool(String name) {
        ToolDefinition tool = new ToolDefinition();
        tool.setName(name);
        tool.setDescription("Test tool: " + name);
        return tool;
    }
}
