package com.acme.ai.web.dto;

import java.util.List;

/** Corps d'erreur uniforme de l'API (voir GlobalExceptionHandler). */
public record ErreurReponse(String code, String message, List<String> details) {

    public static ErreurReponse de(String code, String message) {
        return new ErreurReponse(code, message, List.of());
    }
}
