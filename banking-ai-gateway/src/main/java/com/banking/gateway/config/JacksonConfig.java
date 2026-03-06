package com.banking.gateway.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

/**
 * Jackson 3.0 configuration (Spring Boot 4).
 * Uses JsonMapperBuilderCustomizer — the replacement for the deprecated
 * Jackson2ObjectMapperBuilderCustomizer in Spring Boot 4 / Jackson 3.
 *
 * Note: WRITE_DATES_AS_TIMESTAMPS is already false by default in Jackson 3
 * (ISO-8601 strings are the new default), so only FAIL_ON_UNKNOWN_PROPERTIES
 * needs to be explicitly disabled.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
