#!/usr/bin/env bash
# ============================================================
# Lancement one-shot de la plateforme LLM (Linux / macOS / Git Bash)
#   ./start.sh
# Fait TOUT : installe Docker si absent, .env, infra IA,
# cle virtuelle, build, services, smoke test.
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

etape() { printf '\n==> %s\n' "$1"; }

# hex aleatoire meme sans openssl
aleatoire() { openssl rand -hex "$1" 2>/dev/null || head -c "$1" /dev/urandom | od -An -tx1 | tr -d ' \n'; }

# ---- 0. Environnement : Docker installe ET demarre ----
assurer_docker() {
  docker info >/dev/null 2>&1 && return

  if ! command -v docker >/dev/null 2>&1; then
    etape "Docker absent — installation"
    case "$(uname -s)" in
      Linux)
        curl -fsSL https://get.docker.com | sh \
          || { echo "Echec installation Docker (https://docs.docker.com/engine/install/)." >&2; exit 1; }
        sudo usermod -aG docker "$USER" 2>/dev/null || true
        echo "  Docker installe (nouveau groupe docker : reconnexion possible requise)."
        ;;
      Darwin)
        command -v brew >/dev/null 2>&1 \
          || { echo "Homebrew absent : installer Docker Desktop manuellement (https://docs.docker.com/desktop/setup/install/mac-install/)." >&2; exit 1; }
        brew install --cask docker
        ;;
      *)
        echo "OS non gere ($(uname -s)) : installer Docker manuellement puis relancer." >&2; exit 1
        ;;
    esac
  fi

  etape "Demarrage du daemon Docker"
  case "$(uname -s)" in
    Linux)  sudo systemctl start docker 2>/dev/null || sudo service docker start 2>/dev/null || true ;;
    Darwin) open -a Docker 2>/dev/null || true ;;
  esac
  for i in $(seq 1 60); do
    docker info >/dev/null 2>&1 && { echo "  Daemon Docker pret."; return; }
    sleep 5
  done
  echo "Le daemon Docker n'a pas demarre en 5 min." >&2; exit 1
}
assurer_docker

docker compose version >/dev/null 2>&1 \
  || { echo "Le plugin docker compose est absent (https://docs.docker.com/compose/install/)." >&2; exit 1; }

# ---- 1. .env : cree avec des secrets aleatoires si absent ----
if [ ! -f .env ]; then
  etape "Creation du .env (secrets aleatoires)"
  master_key="sk-$(aleatoire 32)"
  pg_pass="$(aleatoire 16)"
  cat > .env <<EOF
LITELLM_MASTER_KEY=${master_key}
POSTGRES_PASSWORD=${pg_pass}
# Remplacee automatiquement par une cle virtuelle au premier lancement
AI_SERVICE_VIRTUAL_KEY=${master_key}
OPENAI_API_KEY=
AZURE_API_KEY=
AZURE_API_BASE=https://mon-instance.openai.azure.com
MISTRAL_API_KEY=
EOF
  echo "  .env cree — renseigner OPENAI_API_KEY (ou AZURE_*/MISTRAL_API_KEY) pour de vraies reponses LLM."
fi

master_key="$(grep '^LITELLM_MASTER_KEY=' .env | cut -d= -f2-)"
cle_virtuelle="$(grep '^AI_SERVICE_VIRTUAL_KEY=' .env | cut -d= -f2-)"

# ---- 2. Infra IA ----
etape "Demarrage postgres + redis + litellm"
docker compose up -d postgres redis litellm

etape "Attente de LiteLLM (healthcheck)"
for i in $(seq 1 60); do
  etat="$(docker inspect --format '{{.State.Health.Status}}' spring-llm-platform-litellm-1 2>/dev/null || echo starting)"
  [ "$etat" = "healthy" ] && break
  [ "$i" = 60 ] && { echo "LiteLLM n'est pas healthy apres 3 min (docker compose logs litellm)." >&2; exit 1; }
  sleep 3
done
echo "  LiteLLM healthy."

# ---- 3. Cle virtuelle du ai-service (si la master key est encore utilisee) ----
if [ "$cle_virtuelle" = "$master_key" ] || printf '%s' "$cle_virtuelle" | grep -q remplacer; then
  etape "Generation de la cle virtuelle ai-service (quota 0.001 USD, seuil 0.0009 USD, 30d)"
  if nouvelle_cle="$(curl -sf -X POST http://localhost:4000/key/generate \
        -H "Authorization: Bearer ${master_key}" \
        -H "Content-Type: application/json" \
        -d '{"key_alias":"ai-service","max_budget":0.001,"soft_budget":0.0009,"budget_duration":"30d"}' \
        | sed -n 's/.*"key"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')" && [ -n "$nouvelle_cle" ]; then
    sed -i.bak "s|^AI_SERVICE_VIRTUAL_KEY=.*|AI_SERVICE_VIRTUAL_KEY=${nouvelle_cle}|" .env && rm -f .env.bak
    echo "  Cle virtuelle enregistree dans .env."
  else
    echo "  Generation impossible — la master key reste utilisee (OK pour un test local)."
  fi
fi

# ---- 4. Services Spring ----
etape "Build + demarrage ai-service et gateway"
docker compose up -d --build ai-service gateway

etape "Attente de la gateway (healthcheck)"
for i in $(seq 1 100); do
  etat="$(docker inspect --format '{{.State.Health.Status}}' spring-llm-platform-gateway-1 2>/dev/null || echo starting)"
  [ "$etat" = "healthy" ] && break
  [ "$i" = 100 ] && { echo "Gateway pas healthy apres 5 min (docker compose logs gateway)." >&2; exit 1; }
  sleep 3
done

# ---- 5. Smoke test ----
etape "Smoke test : classification via la gateway"
reponse="$(curl -sf -X POST http://localhost:8080/ai/api/ai/classification \
  -H 'Content-Type: application/json' \
  -d '{"texte":"Ma facture de mars comporte une erreur de TVA"}')"
echo "  Reponse : ${reponse}"
case "$reponse" in *AUTRE*) echo "  (AUTRE = reponse degradee : normal sans cle provider dans le .env)";; esac

etape "Plateforme lancee"
cat <<'EOF'
  Gateway     http://localhost:8080
  ai-service  http://localhost:8081
  LiteLLM UI  http://localhost:4000/ui   (login : master key du .env)

  Tests Postman : importer postman_collection.json
  Arret         : docker compose down
EOF
