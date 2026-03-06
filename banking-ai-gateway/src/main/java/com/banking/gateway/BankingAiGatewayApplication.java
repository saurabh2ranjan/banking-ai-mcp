package com.banking.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.banking")
@EntityScan(basePackages              = "com.banking")
@EnableJpaRepositories(basePackages   = "com.banking")
@EnableAspectJAutoProxy
public class BankingAiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingAiGatewayApplication.class, args);
    }
}
