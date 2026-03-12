package com.banking.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication(scanBasePackages = "com.banking")
@EnableAspectJAutoProxy
public class BankingAiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingAiGatewayApplication.class, args);
    }
}
