// Test de charge de la chaine complete : Gateway -> ai-service -> LiteLLM -> provider
// Lancer : k6 run scripts/k6-streaming.js
// (idealement avec LiteLLM en mode network_mock pour mesurer l'overhead infra pur)

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const ttft = new Trend('ttft_ms', true);            // time-to-first-token
const erreurs = new Rate('taux_erreur');

export const options = {
  scenarios: {
    paliers: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '2m', target: 10 },
        { duration: '5m', target: 10 },
        { duration: '2m', target: 50 },
        { duration: '5m', target: 50 },
        { duration: '2m', target: 100 },
        { duration: '5m', target: 100 },
        { duration: '2m', target: 0 },
      ],
    },
  },
  thresholds: {
    ttft_ms: ['p(95)<1500'],        // TTFT P95 < 1,5 s
    taux_erreur: ['rate<0.01'],     // < 1 % d'erreurs
    http_req_duration: ['p(95)<30000'],
  },
};

const GATEWAY = __ENV.GATEWAY_URL || 'http://localhost:8080';

export default function () {
  const debut = Date.now();

  const res = http.post(
    `${GATEWAY}/ai/api/ai/resume`,
    JSON.stringify({ texte: 'Commande 4512 : 3 pompes centrifuges, livraison Lyon, urgence haute, client Acme Industries, montant 12 400 EUR.' }),
    {
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      timeout: '120s',
    },
  );

  // k6 http ne streame pas : on approxime le TTFT par le waiting time (TTFB)
  ttft.add(res.timings.waiting);

  const ok = check(res, {
    'statut 200': (r) => r.status === 200,
    'contenu recu': (r) => r.body && r.body.length > 0,
  });
  erreurs.add(!ok);
}
