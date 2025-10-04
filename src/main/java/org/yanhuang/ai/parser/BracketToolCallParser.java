package org.yanhuang.ai.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yanhuang.ai.model.ToolCall;

@Component
public class BracketToolCallParser {

    private static final Logger log = LoggerFactory.getLogger(BracketToolCallParser.class);

    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile("\\[Called\\s+(\\w+)\\s+with\\s+args:\\s*(\\{.*?\\})\\]", Pattern.DOTALL);
    private static final Pattern TOOL_CALL_SIMPLE_PATTERN = Pattern.compile("\\[Called\\s+(\\w+)\\]");

    public List<ToolCall> parse(String text) {
        List<ToolCall> result = new ArrayList<>();
        if (StringUtils.isBlank(text)) {
            return result;
        }

        Matcher matcher = TOOL_CALL_PATTERN.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1);
            String args = matcher.group(2);
            parseToolCall(name, args).ifPresent(result::add);
        }

        if (!result.isEmpty()) {
            return result;
        }

        Matcher simpleMatcher = TOOL_CALL_SIMPLE_PATTERN.matcher(text);
        while (simpleMatcher.find()) {
            String name = simpleMatcher.group(1);
            ToolCall call = new ToolCall();
            call.setId(randomId());
            call.setType("function");
            ToolCall.ToolFunction function = new ToolCall.ToolFunction();
            function.setName(name);
            function.setArguments("{}");
            call.setFunction(function);
            result.add(call);
        }

        return result;
    }

    private Optional<ToolCall> parseToolCall(String name, String arguments) {
        try {
            ToolCall call = new ToolCall();
            call.setId(randomId());
            call.setType("function");
            ToolCall.ToolFunction function = new ToolCall.ToolFunction();
            function.setName(name);
            function.setArguments(arguments);
            call.setFunction(function);
            return Optional.of(call);
        } catch (Exception ex) {
            log.error("Failed to parse tool call", ex);
            return Optional.empty();
        }
    }

    private String randomId() {
        return "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}

