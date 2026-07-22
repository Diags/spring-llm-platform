package com.acme.ai;

import reactor.core.publisher.Flux;

/** Port metier : resume d'une commande en streaming. */
public interface ResumeService {

    Flux<String> resumerCommande(String texte);
}
