package org.yanhuang.ai.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.yanhuang.ai.model.ToolCall;

@Component
public class ToolCallDeduplicator {

    public List<ToolCall> deduplicate(List<ToolCall> calls) {
        if (calls == null || calls.isEmpty()) {
            return List.of();
        }

        Map<String, ToolCall> unique = new LinkedHashMap<>();
        for (ToolCall call : calls) {
            if (call == null || call.getFunction() == null) {
                continue;
            }
            String key = call.getFunction().getName() + "::" + call.getFunction().getArguments();
            unique.putIfAbsent(key, call);
        }

        return unique.values().stream().collect(Collectors.toList());
    }
}

