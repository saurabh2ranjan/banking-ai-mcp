package com.banking.gateway;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * Single shared @TestConfiguration for all @SpringBootTest classes.
 *
 * Using ONE external class (vs identical nested classes in each test) means:
 *  1. Spring's context cache key is the same across all test classes → context is reused
 *  2. No BeanDefinitionOverrideException from two different Class objects defining the same bean names
 *
 * Import via @Import(SharedAiTestConfig.class) on each @SpringBootTest class.
 */
@TestConfiguration
public class SharedAiTestConfig {

    @Bean @Primary
    public ChatClient bankingChatClient() { return mock(ChatClient.class); }

    @Bean @Primary
    public ChatModel chatModel() { return mock(ChatModel.class); }

    @Bean @Primary
    public ToolCallbackProvider allBankingMcpTools() { return () -> new ToolCallback[0]; }
}
