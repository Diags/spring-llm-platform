package com.acme.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * UNIQUE point de construction du ChatClient : les options transverses
 * (defaults, advisors, observabilite) se configurent ici, pas dans les
 * adaptateurs. Les options modele (alias, temperature) restent dans
 * application.yaml (spring.ai.openai.chat.options).
 */
@Configuration
public class ChatClientConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
