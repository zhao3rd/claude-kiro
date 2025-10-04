package org.yanhuang.ai.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/models")
public class ModelController {

    @GetMapping
    public Map<String, Object> listModels() {
        List<Map<String, Object>> data = List.of(
            model("claude-sonnet-4-5-20250929", "Claude Sonnet 4", "ki2api"),
            model("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", "ki2api")
        );
        return Map.of(
            "object", "list",
            "data", data
        );
    }

    private Map<String, Object> model(String id, String name, String owner) {
        return Map.of(
            "id", id,
            "object", "model",
            "created", Instant.now().getEpochSecond(),
            "owned_by", owner,
            "name", name
        );
    }
}

