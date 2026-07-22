package com.acme.ai.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.acme.ai.domain.Categorie;
import com.acme.ai.domain.ClassificationService;

/**
 * Adaptateur Spring AI du port ClassificationService.
 * Sortie systematiquement ramenee au contrat via Categorie (jamais de texte libre).
 */
@Service
class ClassificationAiService implements ClassificationService {

    private static final String SYSTEME = """
            Tu es un classifieur de tickets support.
            Reponds UNIQUEMENT par un de ces mots : FACTURATION, TECHNIQUE, COMMERCIAL, AUTRE.
            """;

    private final ChatClient chat;

    ClassificationAiService(ChatClient.Builder builder) {
        this.chat = builder.build();
    }

    /** Appel synchrone court, reponse complete. */
    @Override
    public Categorie classifierTicket(String texte) {
        String reponse = chat.prompt()
                .system(SYSTEME)
                .user(u -> u.text("Classifie ce ticket : {texte}").param("texte", texte))
                .call()
                .content();
        return Categorie.depuisReponseLlm(reponse);
    }
}
