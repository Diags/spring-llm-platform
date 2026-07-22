package com.acme.ai.domain;

/** Port metier : classification synchrone d'un ticket support. */
public interface ClassificationService {

    Categorie classifierTicket(String texte);
}
