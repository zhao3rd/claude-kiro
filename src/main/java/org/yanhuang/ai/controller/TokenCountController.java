package org.yanhuang.ai.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/v1/messages")
public class TokenCountController {

    private static final Logger log = LoggerFactory.getLogger(TokenCountController.class);

    @PostMapping(value = "/count_tokens", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> countTokens(
            @RequestHeader(name = "anthropic-version", required = false) String apiVersion,
            @RequestBody(required = false) Map<String, Object> body) {
        int tokens = ThreadLocalRandom.current().nextInt(20, 501);
        log.info("[ClaudeCode] count_tokens request, version={}, tokens={}", apiVersion, tokens);
        return ResponseEntity.ok(Map.of(
                "type", "token_count",
                "input_tokens", tokens
        ));
    }
}


