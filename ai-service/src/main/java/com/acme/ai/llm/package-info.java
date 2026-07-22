/**
 * Adaptateurs sortants vers le LLM : implementations Spring AI des ports
 * du domaine. Les PROMPTS vivent ici, versionnes avec le service.
 *
 * <p>REGLE : c'est le SEUL package qui connait Spring AI. L'appel part
 * toujours vers LiteLLM avec un alias ({@code gpt-fast}) — jamais un nom
 * de provider. Remplacer Spring AI ou LiteLLM ne touche que ce package.</p>
 *
 * <p>Les classes sont package-private : le reste du code ne voit que les
 * ports du domaine.</p>
 */
package com.acme.ai.llm;
