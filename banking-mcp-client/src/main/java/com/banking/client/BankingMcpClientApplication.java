package com.banking.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BankingMcpClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingMcpClientApplication.class, args);
    }
}