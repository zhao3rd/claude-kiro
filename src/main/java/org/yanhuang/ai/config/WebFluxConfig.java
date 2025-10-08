package org.yanhuang.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * WebFlux configuration to handle large request bodies
 */
@Configuration
public class WebFluxConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        // Set max in-memory size to 32MB to handle large requests
        // This applies to incoming requests to the server
        configurer.defaultCodecs().maxInMemorySize(32 * 1024 * 1024);
    }
}

