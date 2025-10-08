package org.yanhuang.ai.service;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.yanhuang.ai.model.AnthropicChatRequest;
import org.yanhuang.ai.model.AnthropicChatResponse;
import org.yanhuang.ai.model.AnthropicMessage;
import org.yanhuang.ai.model.ToolDefinition;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

@SpringBootTest
@TestPropertySource(properties = {
    "app.kiro.disable-tools=false",
    "app.kiro.disable-history=false",
    "app.kiro.max-history-messages=10",
    "app.kiro.max-history-size=131072"
})
class KiroServiceToolRepairIT {
    private static final Logger log = LoggerFactory.getLogger(KiroServiceToolRepairIT.class);

    @Autowired
    private KiroService kiroService;

    @Test
    void serviceRepairsMalformedCcToolsThenSuccess() {
        log.info("=== Service repairs malformed Claude Code tools and expect success ===");
        AnthropicChatRequest req = new AnthropicChatRequest();
        req.setModel("claude-sonnet-4-5-20250929");
        req.setStream(false);

        // Minimal message
        AnthropicMessage msg = new AnthropicMessage();
        msg.setRole("user");
        AnthropicMessage.ContentBlock text = new AnthropicMessage.ContentBlock();
        text.setType("text");
        text.setText("请简单回答：1+1=?");
        List<AnthropicMessage.ContentBlock> blocks = new ArrayList<>();
        blocks.add(text);
        msg.setContent(blocks);
        req.setMessages(Collections.singletonList(msg));

        // CC-style tools with function format
        List<ToolDefinition> tools = new ArrayList<>();
        // Tool 1: missing description and parameters
        tools.add(ccFunctionTool("Read", null, null));
        // Tool 2: has description, missing parameters
        tools.add(ccFunctionTool("Write", "Writes to filesystem", null));
        // Tool 3: has parameters without required
        Map<String, Object> params3 = new HashMap<>();
        params3.put("type", "object");
        params3.put("properties", new HashMap<String, Object>());
        tools.add(ccFunctionTool("Edit", "Edit file content", params3));
        req.setTools(tools);

        // Invoke service – this should internally repair tool specs
        Mono<AnthropicChatResponse> mono = kiroService.createCompletion(req);
        AnthropicChatResponse resp = mono.block(Duration.ofSeconds(60));
        org.junit.jupiter.api.Assertions.assertNotNull(resp, "Expected non-null response from KiroService");
        log.info("Service repaired tools: SUCCESS");
    }

    private ToolDefinition ccFunctionTool(String name, String description, Map<String, Object> parameters) {
        ToolDefinition t = new ToolDefinition();
        t.setType("function");
        Map<String, Object> fn = new HashMap<>();
        fn.put("name", name);
        if (description != null) fn.put("description", description);
        if (parameters != null) fn.put("parameters", parameters);
        t.setFunction(fn);
        return t;
    }

    @Test
    void serviceRepairsManyCcToolsThenSuccess() {
        log.info("=== Service repairs many CC tools and expect success ===");
        AnthropicChatRequest req = new AnthropicChatRequest();
        req.setModel("claude-sonnet-4-5-20250929");
        req.setStream(false);

        AnthropicMessage msg = new AnthropicMessage();
        msg.setRole("user");
        AnthropicMessage.ContentBlock text = new AnthropicMessage.ContentBlock();
        text.setType("text");
        text.setText("用多工具测试：简单回答 2+2=?");
        msg.setContent(Collections.singletonList(text));
        req.setMessages(Collections.singletonList(msg));

        List<ToolDefinition> tools = new ArrayList<>();
        String[] names = {"Task","Bash","Glob","Grep","ExitPlanMode","Read","Edit","Write","NotebookEdit","WebFetch","TodoWrite","WebSearch","BashOutput","KillShell","SlashCommand"};
        for (int i = 0; i < names.length; i++) {
            String n = names[i];
            String desc = (i % 3 == 0) ? null : ("Tool " + n + " description");
            Map<String, Object> params = null;
            if (i % 4 == 0) {
                params = new HashMap<>();
                params.put("type", "object");
                params.put("properties", new HashMap<String, Object>());
            }
            tools.add(ccFunctionTool(n, desc, params));
        }
        req.setTools(tools);

        AnthropicChatResponse resp = kiroService.createCompletion(req).block(Duration.ofSeconds(120));
        org.junit.jupiter.api.Assertions.assertNotNull(resp, "Expected non-null response from KiroService for many tools");
        log.info("Service repaired many tools: SUCCESS");
    }



}