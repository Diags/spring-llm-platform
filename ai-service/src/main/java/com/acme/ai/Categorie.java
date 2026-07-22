package com.acme.ai;

import java.util.Locale;

/**
 * Contrat de classification expose au metier : rien d'autre ne peut sortir.
 * Ajouter une categorie = ajouter une constante, aucun autre code a modifier.
 */
public enum Categorie {
    FACTURATION, TECHNIQUE, COMMERCIAL, AUTRE;

    /**
     * Le LLM ne respecte pas toujours le contrat ("facturation.", phrase entiere,
     * contenu null) : toute reponse hors contrat retombe sur AUTRE.
     */
    public static Categorie depuisReponseLlm(String reponse) {
        if (reponse == null) {
            return AUTRE;
        }
        String nettoyee = reponse.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
        for (Categorie categorie : values()) {
            if (categorie.name().equals(nettoyee)) {
                return categorie;
            }
        }
        return AUTRE;
    }
}
