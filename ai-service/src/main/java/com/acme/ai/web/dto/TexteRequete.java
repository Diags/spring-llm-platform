package com.acme.ai.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Taille bornee : protege le budget LLM et la latence contre les payloads geants. */
public record TexteRequete(@NotBlank @Size(max = 8000) String texte) {}
