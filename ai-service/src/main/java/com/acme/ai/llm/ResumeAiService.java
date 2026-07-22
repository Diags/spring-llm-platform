package com.acme.ai.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.acme.ai.domain.ResumeService;

import reactor.core.publisher.Flux;

/**
 * Adaptateur Spring AI du port ResumeService. Le prompt vit ICI, versionne
 * avec ce service. L'appel part vers LiteLLM (base-url) avec l'alias
 * "gpt-fast" : le provider reel est decide par LiteLLM, jamais par ce code.
 */
@Service
class ResumeAiService implements ResumeService {

    private static final String SYSTEME = """
            Tu es un assistant interne de l'entreprise Acme.
            Tu reponds en francais, de facon concise et factuelle.
            Si l'information demandee n'est pas dans le texte fourni, dis-le.
            """;

    private final ChatClient chat;

    ResumeAiService(ChatClient.Builder builder) {
        this.chat = builder.build();
    }

    /** Flux de tokens (streaming) - Reactor, coherent avec la Gateway. */
    @Override
    public Flux<String> resumerCommande(String texte) {
        return chat.prompt()
                .system(SYSTEME)
                .user(u -> u.text("Resume la commande suivante en 3 phrases maximum : {texte}")
                            .param("texte", texte))
                .stream()
                .content();
    }
}
