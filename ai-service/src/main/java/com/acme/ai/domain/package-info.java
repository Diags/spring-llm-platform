/**
 * Cœur metier : ports (interfaces) et types du contrat.
 *
 * <p>REGLE : ce package ne depend d'AUCUNE technologie (ni Spring, ni
 * Spring AI, ni HTTP). Les autres packages dependent de celui-ci,
 * jamais l'inverse : {@code web -> domain <- llm}.</p>
 *
 * <p>Pour un nouveau cas d'usage IA : ajouter ici le port (interface)
 * et ses types de retour, puis l'implementer dans {@code llm}.</p>
 */
package com.acme.ai.domain;
