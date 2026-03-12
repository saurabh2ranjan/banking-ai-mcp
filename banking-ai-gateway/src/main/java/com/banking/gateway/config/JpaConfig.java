package com.banking.gateway.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// JpaConfig.java — isolated JPA configuration
@Configuration
@EntityScan(basePackages = "com.banking")
@EnableJpaRepositories(basePackages = "com.banking")
public class JpaConfig {
}