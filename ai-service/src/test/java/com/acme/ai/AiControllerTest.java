package com.acme.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Le test remplace LiteLLM par WireMock : on valide le comportement
 * du ai-service (contrat, parsing, validation, degradation) sans vrai LLM.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "30s")   // le 1er appel paie l'init a froid du contexte
class AiControllerTest {

    static WireMockServer wm = new WireMockServer(
            WireMockConfiguration.wireMockConfig().dynamicPort());

    @DynamicPropertySource
    static void liteLlmMock(DynamicPropertyRegistry registry) {
        wm.start();
        registry.add("spring.ai.openai.base-url", wm::baseUrl);
        registry.add("spring.ai.openai.api-key", () -> "sk-test");
    }

    @AfterAll
    static void arret() {
        wm.stop();
    }

    @Autowired
    WebTestClient client;

    @BeforeEach
    void reinitStubs() {
        wm.resetAll();
    }

    /** Stub d'une reponse OpenAI-compatible non streamee. */
    private static void stubReponseLlm(String contenu) {
        wm.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "chatcmpl-test",
                      "object": "chat.completion",
                      "model": "gpt-fast",
                      "choices": [{
                        "index": 0,
                        "message": {"role": "assistant", "content": "%s"},
                        "finish_reason": "stop"
                      }],
                      "usage": {"prompt_tokens": 10, "completion_tokens": 1, "total_tokens": 11}
                    }
                    """.formatted(contenu))));
    }

    private WebTestClient.ResponseSpec classifier(String texte) {
        return client.post().uri("/api/ai/classification")
                     .contentType(MediaType.APPLICATION_JSON)
                     .bodyValue("{\"texte\":\"" + texte + "\"}")
                     .exchange();
    }

    @Test
    void classification_nominale() {
        stubReponseLlm("FACTURATION");
        classifier("Ma facture de mars est erronee")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.categorie").isEqualTo("FACTURATION");
    }

    @Test
    void classification_normalisee_si_llm_repond_hors_format() {
        stubReponseLlm(" technique.\\n");
        classifier("Le serveur ne repond plus")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.categorie").isEqualTo("TECHNIQUE");
    }

    @Test
    void classification_hors_contrat_devient_autre() {
        stubReponseLlm("BANANE");
        classifier("N'importe quoi")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.categorie").isEqualTo("AUTRE");
    }

    @Test
    void litellm_down_reponse_degradee() {
        wm.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
            .willReturn(serverError()));
        classifier("Ma facture de mars est erronee")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.categorie").isEqualTo("AUTRE");
    }

    @Test
    void texte_vide_rejete() {
        classifier("")
            .expectStatus().isBadRequest();
    }

    @Test
    void texte_trop_long_rejete() {
        classifier("x".repeat(8001))
            .expectStatus().isBadRequest();
    }

    @Test
    void resume_streaming_sse() {
        wm.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "text/event-stream")
                .withBody("""
                    data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-fast","choices":[{"index":0,"delta":{"role":"assistant","content":"Trois pompes"},"finish_reason":null}]}

                    data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-fast","choices":[{"index":0,"delta":{"content":" pour Lyon."},"finish_reason":"stop"}]}

                    data: [DONE]

                    """)));

        var corps = client.post().uri("/api/ai/resume")
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.TEXT_EVENT_STREAM)
              .bodyValue("{\"texte\":\"Commande 4512 : 3 pompes centrifuges, livraison Lyon\"}")
              .exchange()
              .expectStatus().isOk()
              .returnResult(String.class)
              .getResponseBody()
              .collectList()
              .block(Duration.ofSeconds(10));

        assertThat(String.join("", corps)).contains("Trois pompes").contains("pour Lyon.");
    }
}
