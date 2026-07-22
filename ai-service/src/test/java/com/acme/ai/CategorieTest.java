package com.acme.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** La normalisation est le seul rempart entre le texte libre du LLM et le contrat. */
class CategorieTest {

    @Test
    void reponse_null_devient_autre() {
        assertThat(Categorie.depuisReponseLlm(null)).isEqualTo(Categorie.AUTRE);
    }

    @Test
    void casse_espaces_et_ponctuation_normalises() {
        assertThat(Categorie.depuisReponseLlm(" facturation.\n")).isEqualTo(Categorie.FACTURATION);
        assertThat(Categorie.depuisReponseLlm("Technique")).isEqualTo(Categorie.TECHNIQUE);
    }

    @Test
    void reponse_hors_contrat_devient_autre() {
        assertThat(Categorie.depuisReponseLlm("BANANE")).isEqualTo(Categorie.AUTRE);
        assertThat(Categorie.depuisReponseLlm("Je pense que c'est TECHNIQUE")).isEqualTo(Categorie.AUTRE);
    }
}
