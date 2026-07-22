package com.acme.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Adaptateur HTTP (contrat OpenAPI) consomme par les services metier.
 * Aucune logique metier ici : le controleur depend des ports
 * ResumeService / ClassificationService, jamais de Spring AI.
 * Vocabulaire METIER en entree ("resume cette commande"), jamais de prompt.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    /** Taille bornee : protege le budget LLM et la latence contre les payloads geants. */
    public record TexteRequete(@NotBlank @Size(max = 8000) String texte) {}
    public record Classification(String categorie) {}

    private final ResumeService resume;
    private final ClassificationService classification;

    public AiController(ResumeService resume, ClassificationService classification) {
        this.resume = resume;
        this.classification = classification;
    }

    /**
     * Streaming SSE : chaque token part vers l'appelant des qu'il arrive.
     * Pas de TimeLimiter ici — un stream legitime peut durer plus de 60 s,
     * c'est le timeout LiteLLM (60 s cote provider) qui protege.
     */
    @PostMapping(value = "/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> resumer(@Valid @RequestBody TexteRequete requete) {
        return resume.resumerCommande(requete.texte())
                     .onErrorReturn("[indisponible] Le service IA est momentanement indisponible.");
    }

    /**
     * Appel synchrone court : circuit breaker + timeout + reponse degradee.
     * Si LiteLLM (et donc tous ses fallbacks providers) est down,
     * on repond AUTRE plutot qu'une 500 — le metier decide quoi en faire.
     */
    @PostMapping(value = "/classification", produces = MediaType.APPLICATION_JSON_VALUE)
    @CircuitBreaker(name = "litellm", fallbackMethod = "classificationDegradee")
    @TimeLimiter(name = "litellm")
    public Mono<Classification> classifier(@Valid @RequestBody TexteRequete requete) {
        return Mono.fromCallable(() -> classification.classifierTicket(requete.texte()))
                   .subscribeOn(Schedulers.boundedElastic())
                   .map(categorie -> new Classification(categorie.name()));
    }

    @SuppressWarnings("unused")
    private Mono<Classification> classificationDegradee(TexteRequete requete, Throwable cause) {
        return Mono.just(new Classification(Categorie.AUTRE.name()));
    }
}
