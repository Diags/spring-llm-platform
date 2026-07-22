# ============================================================
# Lancement one-shot de la plateforme LLM (Windows / PowerShell)
#   .\start.ps1
# Fait TOUT : installe Docker si absent, .env, infra IA,
# cle virtuelle, build, services, smoke test.
# ============================================================
$ErrorActionPreference = 'Stop'
$racine = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $racine

function Etape($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }

# ---- 0. Environnement : Docker installe ET demarre ----
function Assurer-Docker {
    docker info *> $null
    if ($LASTEXITCODE -eq 0) { return }

    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Etape "Docker absent - installation via winget"
        if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
            throw "winget indisponible : installer Docker Desktop manuellement (https://docs.docker.com/desktop/setup/install/windows-install/) puis relancer ce script."
        }
        winget install --id Docker.DockerDesktop -e --accept-source-agreements --accept-package-agreements
        if ($LASTEXITCODE -ne 0) { throw "Echec de l'installation de Docker Desktop via winget." }
        Write-Host "  Docker Desktop installe. Un redemarrage de session Windows peut etre necessaire (WSL2)." -ForegroundColor Yellow
        # le PATH du process courant ne connait pas encore docker.exe
        $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
    }

    Etape "Demarrage de Docker Desktop (daemon arrete)"
    $exe = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    if (Test-Path $exe) { Start-Process $exe | Out-Null }
    $limite = (Get-Date).AddMinutes(5)
    do {
        Start-Sleep -Seconds 5
        docker info *> $null
        if ($LASTEXITCODE -eq 0) { Write-Host "  Daemon Docker pret."; return }
    } while ((Get-Date) -lt $limite)
    throw "Le daemon Docker n'a pas demarre en 5 min. Premiere installation : redemarrer la session Windows puis relancer .\start.ps1."
}
Assurer-Docker

# ---- 1. .env : cree avec des secrets aleatoires si absent ----
$fichierEnv = Join-Path $racine '.env'
if (-not (Test-Path $fichierEnv)) {
    Etape "Creation du .env (secrets aleatoires)"
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $b = New-Object byte[] 32; $rng.GetBytes($b)
    $masterKey = 'sk-' + (($b | ForEach-Object { $_.ToString('x2') }) -join '')
    $b2 = New-Object byte[] 16; $rng.GetBytes($b2)
    $pgPass = ($b2 | ForEach-Object { $_.ToString('x2') }) -join ''
    @(
        "LITELLM_MASTER_KEY=$masterKey"
        "POSTGRES_PASSWORD=$pgPass"
        "# Remplacee automatiquement par une cle virtuelle au premier lancement"
        "AI_SERVICE_VIRTUAL_KEY=$masterKey"
        "AZURE_API_KEY="
        "AZURE_API_BASE=https://mon-instance.openai.azure.com"
        "MISTRAL_API_KEY="
    ) | Out-File $fichierEnv -Encoding utf8
    Write-Host "  .env cree - renseigner AZURE_*/MISTRAL_API_KEY pour de vraies reponses LLM." -ForegroundColor Yellow
}

$lignesEnv = Get-Content $fichierEnv
$masterKey = ($lignesEnv | Where-Object { $_ -match '^LITELLM_MASTER_KEY=' }) -replace '^LITELLM_MASTER_KEY=', ''
$cleVirtuelle = ($lignesEnv | Where-Object { $_ -match '^AI_SERVICE_VIRTUAL_KEY=' }) -replace '^AI_SERVICE_VIRTUAL_KEY=', ''

# ---- 2. Infra IA ----
Etape "Demarrage postgres + redis + litellm"
docker compose up -d postgres redis litellm
if ($LASTEXITCODE -ne 0) { throw "Echec du demarrage de l'infra IA." }

Etape "Attente de LiteLLM (healthcheck)"
$limite = (Get-Date).AddMinutes(3)
do {
    Start-Sleep -Seconds 3
    $etat = docker inspect --format '{{.State.Health.Status}}' litelmm-gatewaye-litellm-1
    if ((Get-Date) -gt $limite) { throw "LiteLLM n'est pas devenu healthy en 3 min (docker compose logs litellm)." }
} while ($etat -ne 'healthy')
Write-Host "  LiteLLM healthy."

# ---- 3. Cle virtuelle du ai-service (si la master key est encore utilisee) ----
if ($cleVirtuelle -eq $masterKey -or $cleVirtuelle -match 'remplacer') {
    Etape "Generation de la cle virtuelle ai-service (budget 100 / 30d)"
    try {
        $reponse = Invoke-RestMethod -Method Post -Uri 'http://localhost:4000/key/generate' `
            -Headers @{ Authorization = "Bearer $masterKey" } -ContentType 'application/json' `
            -Body '{"key_alias":"ai-service","max_budget":100,"budget_duration":"30d"}'
        $contenu = (Get-Content $fichierEnv -Raw) -replace [regex]::Escape("AI_SERVICE_VIRTUAL_KEY=$cleVirtuelle"), "AI_SERVICE_VIRTUAL_KEY=$($reponse.key)"
        Set-Content $fichierEnv $contenu -Encoding utf8 -NoNewline
        Write-Host "  Cle virtuelle enregistree dans .env."
    } catch {
        Write-Host "  Generation impossible ($_) - la master key reste utilisee (OK pour un test local)." -ForegroundColor Yellow
    }
}

# ---- 4. Services Spring ----
Etape "Build + demarrage ai-service et gateway"
docker compose up -d --build ai-service gateway
if ($LASTEXITCODE -ne 0) { throw "Echec du build/demarrage des services Spring." }

Etape "Attente de la gateway (healthcheck)"
$limite = (Get-Date).AddMinutes(5)
do {
    Start-Sleep -Seconds 3
    $etat = docker inspect --format '{{.State.Health.Status}}' litelmm-gatewaye-gateway-1
    if ((Get-Date) -gt $limite) { throw "La gateway n'est pas devenue healthy en 5 min (docker compose logs gateway)." }
} while ($etat -ne 'healthy')

# ---- 5. Smoke test ----
Etape "Smoke test : classification via la gateway"
$resultat = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/ai/api/ai/classification' `
    -ContentType 'application/json' -Body '{"texte":"Ma facture de mars comporte une erreur de TVA"}'
Write-Host "  Reponse : $($resultat | ConvertTo-Json -Compress)"
if ($resultat.categorie -eq 'AUTRE') {
    Write-Host "  (AUTRE = reponse degradee : normal sans cle provider dans le .env)" -ForegroundColor Yellow
}

Etape "Plateforme lancee"
Write-Host @"
  Gateway     http://localhost:8080
  ai-service  http://localhost:8081
  LiteLLM UI  http://localhost:4000/ui   (login : master key du .env)

  Tests Postman : importer postman_collection.json
  Arret         : docker compose down
"@
