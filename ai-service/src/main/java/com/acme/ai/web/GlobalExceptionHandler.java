package com.acme.ai.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import com.acme.ai.web.dto.ErreurReponse;

/**
 * Toutes les erreurs de l'API sortent d'ici avec le meme corps JSON
 * {code, message, details} : jamais de stack trace ni de detail interne
 * expose a l'appelant.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Bean valide mais contraintes violees (@NotBlank, @Size...). */
    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErreurReponse validation(WebExchangeBindException e) {
        List<String> details = e.getFieldErrors().stream()
                .map(err -> err.getField() + " : " + err.getDefaultMessage())
                .toList();
        return new ErreurReponse("VALIDATION", "Requete invalide.", details);
    }

    /** Corps illisible : JSON malforme, type inattendu, body absent. */
    @ExceptionHandler(ServerWebInputException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErreurReponse entreeIllisible(ServerWebInputException e) {
        return ErreurReponse.de("REQUETE_INVALIDE", "Corps de requete absent ou malforme.");
    }

    /** Filet de securite : log complet cote serveur, message neutre cote client. */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErreurReponse interne(Exception e) {
        log.error("Erreur interne non geree", e);
        return ErreurReponse.de("ERREUR_INTERNE", "Une erreur interne est survenue.");
    }
}
