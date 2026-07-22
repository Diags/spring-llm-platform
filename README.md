# Plateforme LLM — 100% Spring : Gateway + Spring AI + LiteLLM

Architecture : `Clients -> Gateway (Spring Cloud) -> services metier (Spring) -> ai-service (Spring Boot + Spring AI) -> LiteLLM -> providers (Azure, Mistral...)`

```
llm-platform/
├── docker-compose.yml          # orchestration complete
├── .env.example                # secrets a remplir (copier en .env)
├── litellm/
│   └── litellm_config.yaml     # alias de modeles, fallback, cache, budgets
├── ai-service/                 # Spring Boot + Spring AI (WebFlux)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main|test/...
├── gateway/                    # Spring Cloud Gateway
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/resources/application.yaml
└── scripts/
    └── k6-streaming.js         # test de charge SSE par paliers
```

## Demarrage rapide (one-shot)

```powershell
.\start.ps1      # Windows
./start.sh       # Linux / macOS / Git Bash
```

Le script fait tout : creation du `.env` (secrets aleatoires), infra IA,
cle virtuelle, build des services, smoke test. Sans cle provider dans le
`.env`, la plateforme repond en mode degrade (`AUTRE` / `[indisponible]`).

Tests manuels : importer `postman_collection.json` dans Postman et
renseigner la variable `litellm_master_key` depuis le `.env`.

## Prerequis

- Docker + Docker Compose
- Java 21 + Maven (pour le dev local hors conteneur)
- Une cle API d'au moins un provider (Azure OpenAI ou Mistral)

## Etape 1 — Secrets

```bash
cp .env.example .env
# Remplir : LITELLM_MASTER_KEY, POSTGRES_PASSWORD, AZURE_* ou MISTRAL_API_KEY
# Premier boot : mettre la master key dans AI_SERVICE_VIRTUAL_KEY (remplacee a l'etape 3)
```

## Etape 2 — Demarrer l'infra IA seule et la verifier

```bash
docker compose up -d postgres redis litellm
curl http://localhost:4000/health/liveliness        # -> alive

# Test direct de l'alias gpt-fast :
curl http://localhost:4000/v1/chat/completions \
  -H "Authorization: Bearer $LITELLM_MASTER_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"gpt-fast","messages":[{"role":"user","content":"Bonjour"}]}'
```

## Etape 3 — Creer la cle virtuelle du ai-service

```bash
curl -X POST http://localhost:4000/key/generate \
  -H "Authorization: Bearer $LITELLM_MASTER_KEY" \
  -H "Content-Type: application/json" \
  -d '{"key_alias":"ai-service","max_budget":100,"budget_duration":"30d"}'
# Copier la cle "sk-..." retournee dans AI_SERVICE_VIRTUAL_KEY du .env
```

Chaque service appelant recoit SA cle : suivi des couts et budget par equipe.

## Etape 4 — Demarrer toute la plateforme

```bash
docker compose up -d --build
```

| Composant  | URL locale             |
|------------|------------------------|
| Gateway    | http://localhost:8080  |
| ai-service | http://localhost:8081  |
| LiteLLM UI | http://localhost:4000/ui (login : master key) |

## Etape 5 — Tester la chaine complete

```bash
# Classification (synchrone, avec circuit breaker + reponse degradee)
curl -X POST http://localhost:8080/ai/api/ai/classification \
  -H "Content-Type: application/json" \
  -d '{"texte":"Ma facture de mars comporte une erreur de TVA"}'
# -> {"categorie":"FACTURATION"}

# Resume (streaming SSE via la gateway, timeout 120 s)
curl -N -X POST http://localhost:8080/ai/api/ai/resume \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"texte":"Commande 4512 : 3 pompes centrifuges, livraison Lyon..."}'
```

## Etape 6 — Tests automatises

```bash
cd ai-service && mvn test
```

Le test `AiControllerTest` remplace LiteLLM par WireMock (reponse OpenAI-compatible
simulee) : il valide le contrat et le comportement sans appeler de vrai LLM.

## Etape 7 — Benchmark

```bash
# 1. Overhead infra pur : activer network_mock dans litellm_config.yaml
#    (litellm_settings: network_mock: true) puis :
k6 run scripts/k6-streaming.js

# 2. Surveiller en continu le header x-litellm-overhead-duration-ms
#    et la gauge Prometheus litellm_in_flight_requests.
```

Seuils du script : TTFT P95 < 1,5 s, erreurs < 1 %.

## Etape 8 — Dev local rapide (hors Docker)

```bash
docker compose up -d postgres redis litellm
cd ai-service && mvn spring-boot:run   # avec spring-boot-devtools pour le hot-reload
```

## Aller plus loin

- **Keycloak / OIDC** : decommenter les blocs marques dans les deux `pom.xml`
  et les deux `application.yaml` (ai-service et Gateway).
- **Kubernetes** : LiteLLM en Deployment 2+ replicas derriere un Service
  ClusterIP (jamais d'Ingress) ; NetworkPolicy restreignant l'acces au seul
  ai-service ; secrets providers via Vault/Secret K8s dans le namespace infra IA.
- **Nouveaux modeles** : ajouter une entree dans `litellm_config.yaml` — aucun
  changement Java.

## Versions

Les versions (Spring Boot 3.4.x, Spring AI 1.0.x, Spring Cloud 2024.0.x,
Resilience4j 2.2.x) sont des reperes au moment de la generation : verifier
les dernieres versions stables avant un passage en production.
