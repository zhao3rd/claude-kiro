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
public class ToolCallParser {

    private static final Logger log = LoggerFactory.getLogger(ToolCallParser.class);

    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile("\\[Called\\s+(\\w+)\\s+with\\s+args:\\s*(\\{.*?\\})\\]", Pattern.DOTALL);
    private static final Pattern TOOL_NAME_ONLY_PATTERN = Pattern.compile("\\[Called\\s+(\\w+)\\]");

    public List<ToolCall> parse(String text) {
        List<ToolCall> result = new ArrayList<>();
        if (StringUtils.isBlank(text)) {
            return result;
        }

        Matcher matcher = TOOL_CALL_PATTERN.matcher(text);
        while (matcher.find()) {
            String functionName = matcher.group(1);
            String argumentsRaw = matcher.group(2);
            parseToolCall(functionName, argumentsRaw).ifPresent(result::add);
        }

        if (result.isEmpty()) {
            Matcher nameOnlyMatcher = TOOL_NAME_ONLY_PATTERN.matcher(text);
            while (nameOnlyMatcher.find()) {
                String functionName = nameOnlyMatcher.group(1);
                ToolCall call = new ToolCall();
                call.setId("call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
                call.setType("function");
                ToolCall.ToolFunction function = new ToolCall.ToolFunction();
                function.setName(functionName);
                function.setArguments("{}");
                call.setFunction(function);
                result.add(call);
            }
        }

        return result;
    }

    private Optional<ToolCall> parseToolCall(String functionName, String argumentsRaw) {
        try {
            ToolCall call = new ToolCall();
            call.setId("call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
            call.setType("function");

            ToolCall.ToolFunction function = new ToolCall.ToolFunction();
            function.setName(functionName);
            function.setArguments(argumentsRaw);
            call.setFunction(function);

            return Optional.of(call);
        } catch (Exception e) {
            log.error("Failed to parse tool call", e);
            return Optional.empty();
        }
    }
}

