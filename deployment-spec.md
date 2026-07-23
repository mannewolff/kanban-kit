# Deployment-Spezifikation: Automatisiertes Deploy per self-hosted Runner

## Ziel

Bei jedem Push auf den `production`-Branch soll der VPS den aktuellen Stand ziehen und die Container neu bauen. Der bisher manuelle Ablauf (SSH auf den Server, `git pull`, `docker compose up -d --build`) wird automatisiert, ohne dass ein Schlüssel bei GitHub hinterlegt oder ein eingehender Port geöffnet werden muss.

## Grundprinzip: Pull statt Push

Ein self-hosted Runner läuft dauerhaft als Prozess auf dem VPS und fragt von sich aus bei GitHub nach (ausgehendes HTTPS auf Port 443). Sobald auf `production` ein Push liegt, stellt GitHub einen Job in die Warteschlange, der Runner holt ihn ab und führt die Schritte lokal auf der Maschine aus.

Konsequenzen dieser Architektur:

- Kein eingehender SSH-Zugriff von GitHub auf den Server.
- Kein privater Deploy-Schlüssel in GitHub-Secrets.
- Die Initiative bleibt vollständig auf der Server-Seite.

## Voraussetzungen

- VPS mit Docker und Docker Compose (Plugin-Variante, `docker compose`).
- Dedizierter `deploy`-User (nicht root, nicht der persönliche User).
- Schreibrechte des `deploy`-Users auf das Zielverzeichnis.
- Ausgehende HTTPS-Verbindungen (443) erlaubt.

## Einrichtung

### 1. Runner installieren

Die exakten Befehle inklusive aktueller Version und Registrierungs-Token liegen vorausgefüllt unter Repository, Settings, Actions, Runners, New self-hosted runner. Version und Token immer von dort übernehmen, da beide kurzlebig sind.

```bash
# als deploy-User, nicht als root
mkdir actions-runner && cd actions-runner
curl -o runner.tar.gz -L https://github.com/actions/runner/releases/download/vX.X.X/actions-runner-linux-x64-X.X.X.tar.gz
tar xzf runner.tar.gz
./config.sh --url https://github.com/DEIN-USER/DEIN-REPO --token DER_TOKEN
```

### 2. Als Dienst einrichten

Damit der Runner Reboots übersteht und nicht an einer SSH-Sitzung hängt:

```bash
sudo ./svc.sh install deploy
sudo ./svc.sh start
```

### 3. Docker-Rechte vergeben

Der `deploy`-User muss Docker fahren dürfen:

```bash
sudo usermod -aG docker deploy   # danach einmal neu einloggen
```

## Workflow-Definition

Datei: [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml) — **umgesetzt**.

**Entschieden: Variante B** (bestehendes Server-Verzeichnis, deterministisches `git reset --hard`). Der reale Workflow liegt in der Datei oben; die folgenden zwei Varianten dokumentieren die Abwägung. Variante A ist der GitHub-Standard, Variante B bildet den bisher manuellen Ablauf eins zu eins nach.

### Variante A: Checkout in das Runner-Arbeitsverzeichnis

```yaml
name: Deploy
on:
  push:
    branches: [production]

jobs:
  deploy:
    runs-on: self-hosted
    steps:
      - uses: actions/checkout@v4
      - name: Build and restart
        run: docker compose up -d --build
```

`actions/checkout` legt den Code in das Arbeitsverzeichnis des Runners (`~/actions-runner/_work/REPO/REPO`).

- Vorteil: Die Authentifizierung läuft automatisch über das `GITHUB_TOKEN`. Auf dem Server werden kein Deploy-Key und kein PAT für git benötigt.
- Preis: Volumes, relative Pfade in der `docker-compose.yml` und eine eventuelle `.env` müssen aus diesem neuen Verzeichnis heraus funktionieren.

### Variante B: Bestehendes Verzeichnis, manueller Ablauf gespiegelt

```yaml
name: Deploy
on:
  push:
    branches: [production]

jobs:
  deploy:
    runs-on: self-hosted
    steps:
      - name: Pull production und Container neu bauen
        run: |
          cd /root/opt/manban
          git fetch origin production
          git reset --hard origin/production
          docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Entspricht dem manuellen Ablauf aus [docs/deployment-hostinger.md](docs/deployment-hostinger.md), mit zwei bewussten Schärfungen:

- **`git reset --hard origin/production`** statt `git pull` — deterministisch (auch nach Rebase/Force-Push auf `production`); die gitignorete `.env` bleibt unberührt.
- **Beide Compose-Files** (`-f docker-compose.yml -f docker-compose.prod.yml`) — sonst fehlte das Prod-Overlay (Traefik/TLS).

- Vorteil: Keine Änderung an Pfaden, Volumes oder `.env`.
- Preis: Der Runner läuft im gewählten Verzeichnis (hier `/root/opt/manban`, also als root) und muss dort fetchen/resetten dürfen. Für ein öffentliches Repo ist kein git-Credential nötig (HTTPS-Fetch anonym).

### Entscheidung

**Umgesetzt ist Variante B** — mental am nächsten am bekannten Ablauf, keine Überraschungen bei Pfaden/`.env`. Variante A (Checkout ins Runner-Workdir, ohne git-Credentials) bleibt als spätere Option, sobald sichergestellt ist, dass die Compose-Pfade den Verzeichniswechsel mitmachen.

## Sicherheit (relevant bei geplanter Open-Source-Veröffentlichung)

Self-hosted Runner und öffentliche Repositories sind eine bekannte scharfe Kante: Über einen Pull Request aus einem Fork könnte fremder Code auf dem VPS ausgeführt werden. Zwei Absicherungen:

1. **Trigger strikt begrenzen.** Nur `push` auf `production`, niemals `pull_request`. Ein Push auf `production` kann nur, wer Schreibrechte hat. Ein Fork-PR löst den Deploy nicht aus. Die Workflows oben sind bereits so gebaut.

2. **Fork-Runs absichern.** In den Repo-Settings unter Actions die Freigabe für Runs von Forks auf Approval stellen. Dieser Riegel greift, falls später doch ein PR-getriggerter Job ergänzt wird.

Solange der Deploy ausschließlich am `production`-Push hängt, ist die Konfiguration sauber abgesichert.

## Offene Punkte

- **Einmalige Server-/GitHub-Einrichtung (manuell):** Runner als Dienst installieren (`config.sh` + `svc.sh`, siehe oben), Docker-Rechte, und in den Repo-Actions-Settings Fork-Runs auf „Approval" stellen.
- Optional: Health-Check nach dem Deploy ergänzen, um fehlgeschlagene Builds sichtbar zu machen.

Erledigt: Verzeichnis (`/root/opt/manban`) und `.env` sind über [docs/deployment-hostinger.md](docs/deployment-hostinger.md) geklärt; Variante B ist als Workflow umgesetzt.
