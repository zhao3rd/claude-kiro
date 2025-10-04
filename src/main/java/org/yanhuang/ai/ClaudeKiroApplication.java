package org.yanhuang.ai;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.yanhuang.ai.config.AppProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class ClaudeKiroApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaudeKiroApplication.class, args);
    }
}

