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

Datei: `.github/workflows/deploy.yml`

Es gibt zwei Varianten. Variante A ist der GitHub-Standard, Variante B bildet den bestehenden manuellen Ablauf eins zu eins nach.

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
      - name: Pull and rebuild
        run: |
          cd /var/www/app
          git pull origin production
          docker compose up -d --build
```

Dies entspricht exakt dem bisher manuellen Vorgehen.

- Vorteil: Keine Änderung an Pfaden, Volumes oder `.env`.
- Preis: Der Runner läuft als `deploy`-User. Dieser User muss in `/var/www/app` das `git pull` durchführen dürfen (Verzeichnis-Ownership und git-Credentials). Wird bisher als anderer User gepullt, fehlt dem Runner diese Berechtigung.

### Empfehlung

Für den Einstieg ist Variante B mental am nächsten am bekannten Ablauf und vermeidet Überraschungen bei Pfaden. Variante A ist langfristig sauberer, weil sie ohne git-Credentials auf dem Server auskommt. Der Wechsel lohnt sich, sobald sichergestellt ist, dass die Compose-Pfade den Verzeichniswechsel mitmachen.

## Sicherheit (relevant bei geplanter Open-Source-Veröffentlichung)

Self-hosted Runner und öffentliche Repositories sind eine bekannte scharfe Kante: Über einen Pull Request aus einem Fork könnte fremder Code auf dem VPS ausgeführt werden. Zwei Absicherungen:

1. **Trigger strikt begrenzen.** Nur `push` auf `production`, niemals `pull_request`. Ein Push auf `production` kann nur, wer Schreibrechte hat. Ein Fork-PR löst den Deploy nicht aus. Die Workflows oben sind bereits so gebaut.

2. **Fork-Runs absichern.** In den Repo-Settings unter Actions die Freigabe für Runs von Forks auf Approval stellen. Dieser Riegel greift, falls später doch ein PR-getriggerter Job ergänzt wird.

Solange der Deploy ausschließlich am `production`-Push hängt, ist die Konfiguration sauber abgesichert.

## Offene Punkte

- Prüfen, ob die Pfade und Volumes der `docker-compose.yml` mit dem gewählten Arbeitsverzeichnis (Variante A oder B) zusammenpassen.
- Klären, ob eine `.env` auf dem Server liegt und aus dem gewählten Verzeichnis erreichbar ist.
- Optional: Health-Check nach dem Deploy ergänzen, um fehlgeschlagene Builds sichtbar zu machen.
