/**
 * Adaptateur entrant HTTP : controleurs, DTOs et gestion d'erreurs.
 *
 * <p>REGLES :</p>
 * <ul>
 *   <li>Les controleurs dependent des ports du domaine, jamais de Spring AI.</li>
 *   <li>Vocabulaire METIER en entree ("resume cette commande"), jamais de prompt.</li>
 *   <li>Toute entree est validee (Bean Validation) et bornee en taille.</li>
 *   <li>Toute erreur sort au format uniforme {@code {code, message, details}}
 *       via {@link com.acme.ai.web.GlobalExceptionHandler} — aucune fuite interne.</li>
 *   <li>La resilience (circuit breaker, time limiter, reponse degradee)
 *       se declare ici, a la frontiere.</li>
 * </ul>
 */
package com.acme.ai.web;
