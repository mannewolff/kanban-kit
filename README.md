# kanban-kit

Self-hostbares Kanban-Board — eine schlanke, mandantenfähige Trello-Alternative zum
selbst Betreiben. Projekte, Boards mit konfigurierbaren Spalten, Karten mit Markdown,
Epics, Datei-Anhänge (Bild-/PDF-Vorschau) und eine rollenbasierte Rechteverwaltung
(Projekt- und Plattform-Rollen).

Technik: Spring Boot (Java 21) + Postgres + MinIO im Backend, React + Vite im Frontend,
alles hinter einem Caddy-Reverse-Proxy mit automatischem TLS. Der ganze Stack läuft über
Docker Compose.

## Voraussetzungen

- **Docker** mit Docker Compose. Auf macOS z. B. [Colima](https://github.com/abiosoft/colima):
  ```
  colima start
  ```
  „Cannot connect to the Docker daemon" bei `docker ps` heißt: die Docker-Laufzeit läuft nicht.

## Schnellstart

```
git clone https://github.com/mannewolff/kanban-kit.git
cd kanban-kit
docker compose up --build -d
```

- `--build` baut das Image neu (Frontend-Build + Backend-Jar). **Nach jeder Codeänderung nötig** —
  ein reines `docker compose up -d` nutzt sonst das alte Image.
- `-d` startet im Hintergrund. Für Live-Logs `-d` weglassen oder:
  ```
  docker compose logs -f manban-api    # warten auf "Started ManbanApplication"
  ```

Dann im Browser: **https://localhost**

Für `localhost` verwendet Caddy ein selbst-signiertes Zertifikat → der Browser zeigt eine
Sicherheitswarnung. Einmal „Trotzdem fortfahren" bestätigen (lokal so gewollt). Für eine
echte Domain `MANBAN_DOMAIN` setzen — dann besorgt Caddy automatisch ein Let's-Encrypt-Zertifikat.

Das Datenbank-Schema wird beim Start **automatisch per Flyway** migriert — kein manuelles SQL nötig.

## Ersten Admin einrichten

Alle registrierten Nutzer sind zunächst Plattform-**USER**; es gibt kein vordefiniertes
Admin-Konto. Den ersten Admin richtet man per Bootstrap-Token oder direkt in der Datenbank
ein — die vollständige Anleitung steht in [docs/betrieb.md](docs/betrieb.md#den-ersten-admin-einrichten).

Kurzfassung (Datenbank-Weg, nach dem Registrieren):

```
docker compose exec -T postgres psql -U manban -d manban \
  -c "UPDATE app_user SET email_verified = true, platform_role = 'ADMIN' WHERE email = 'DEINE@MAIL';"
```

Danach ab- und wieder anmelden — die Rolle wird beim Login geladen.

## Mailversand aktivieren

Ohne Konfiguration werden Verifikations- und Reset-Links nur ins Log geschrieben (kein SMTP
nötig). Für echten Versand in der `.env` setzen (Beispiel Strato, 587/STARTTLS):

```
MANBAN_MAIL_ENABLED=true
MANBAN_MAIL_FROM=info@mwolff.org
MANBAN_SMTP_HOST=smtp.strato.de
MANBAN_SMTP_PORT=587
MANBAN_SMTP_USER=info@mwolff.org
MANBAN_SMTP_PASSWORD=***
```

Alle Varianten (465/SSL, Auth/STARTTLS abschalten) stehen in [.env.example](.env.example)
und [docs/betrieb.md](docs/betrieb.md).

## Dokumentation

Die ausführliche Benutzer- und Betriebsdokumentation liegt unter [`docs/`](docs/):

- [Betrieb & Installation](docs/betrieb.md) — Start, Umgebungsvariablen, E-Mail, erster Admin
- [Nutzung](docs/nutzung.md) — Projekte, Boards, Karten, Listen-Ansicht, Epics, Mitglieder
- [Rollen & Rechte](docs/rollen-und-rechte.md) — Projekt- und Plattform-Rollen, Rechte-Matrix
- [Dogfooding](docs/dogfooding.md) — kanban-kit als eigenes Board anbinden

Als gerenderte Website (VitePress) lässt sich die Doku lokal ansehen:

```
cd docs-site
npm install
npm run dev        # http://localhost:5173
```
