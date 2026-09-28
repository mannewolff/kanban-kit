# kanban-kit

**KI-Leitstand und Kanban-Board in einem** — self-hostbar, mandantenfähig, quelloffen.

Auf dem **Board** wird Arbeit beschrieben: Projekte, Boards mit konfigurierbaren Spalten, Karten
mit Markdown, Vorhaben, Datei-Anhänge (Bild-/PDF-Vorschau) und eine rollenbasierte
Rechteverwaltung (Projekt- und Plattform-Rollen).

Im **Leitstand** steht, was die KI daraus gemacht hat:

- **Läufe** eines Nacht-Runners — je Lauf der Ausgang (gelungen, mit Vorbehalt, nicht gelungen)
  und je Arbeitspaket der Befund samt Übernahmetext für die eigene Entwicklungssitzung.
- **Plattform-Leitstand** — aktive Läufe, beendete Läufe und Störungen über alle teilnehmenden
  Projekte hinweg, selbstauffrischend.
- **Verbrauch** — Token und Kosten je Nacht, Woche, Monat, Vorhaben und Stufe der Kette, getrennt
  nach Läufen und interaktiven Sitzungen.

Technik: Spring Boot (Java 25) + Postgres + SeaweedFS als Objektspeicher im Backend, React + Vite im Frontend,
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
docker compose up -d
```

> **Dieser Weg stellt eine Testinstanz im Entwicklungsbetrieb her — er ist nicht für den Betrieb
> gedacht.** Der Stack läuft mit unsicheren Standardwerten und `MANBAN_DEV_MODE=true`; die
> Sitzungs-Cookies dieser Instanz sind fälschbar, weil der mitgelieferte Sitzungsschlüssel im
> öffentlichen Repository steht. Die Anwendung startet damit, schreibt aber bei jedem Start eine
> Warnung ins Log. Für einen echten Betrieb siehe [Produktivbetrieb](#produktivbetrieb).

- Es wird **nichts übersetzt**: Compose zieht das veröffentlichte Abbild von `ghcr.io`.
- `-d` startet im Hintergrund. Für Live-Logs `-d` weglassen oder:
  ```
  docker compose logs -f manban-api    # warten auf "Started ManbanApplication"
  ```

Dann im Browser: **https://localhost**

Für `localhost` verwendet Caddy ein selbst-signiertes Zertifikat → der Browser zeigt eine
Sicherheitswarnung. Einmal „Trotzdem fortfahren" bestätigen (lokal so gewollt). Für eine
echte Domain `MANBAN_DOMAIN` setzen — dann besorgt Caddy automatisch ein Let's-Encrypt-Zertifikat.

Das Datenbank-Schema wird beim Start **automatisch per Flyway** migriert — kein manuelles SQL nötig.

## Produktivbetrieb

Der zweite Weg. Er unterscheidet sich vom Schnellstart in genau zwei Punkten: eine vollständige
`.env` und ein zusätzliches Overlay.

1. **Die fünf Pflichtwerte** in die `.env` neben der `docker-compose.yml` eintragen. Fehlt einer,
   bricht schon `docker compose config` ab und nennt ihn:

   | Wert | Bedeutung |
   |---|---|
   | `MANBAN_SESSION_SECRET` | Schlüssel zum Signieren der Sitzungs-Cookies (`openssl rand -hex 32`) |
   | `MANBAN_BASE_URL` | öffentliche Adresse dieser Instanz für Links in E-Mails |
   | `OBJEKTSPEICHER_ROOT_USER` | Benutzername des Objektspeichers, frei wählbar und nicht `manban` |
   | `OBJEKTSPEICHER_ROOT_PASSWORD` | Geheimnis des Objektspeichers (`openssl rand -hex 32`) |
   | `POSTGRES_PASSWORD` | Kennwort der Datenbankrolle (`openssl rand -hex 32`) |

2. **Mit dem Betriebs-Overlay starten:**

   ```
   docker compose -f docker-compose.yml -f docker-compose.betrieb.yml up -d
   ```

   Das Overlay setzt `MANBAN_DEV_MODE` fest auf `false` — ein Eintrag in der `.env` greift dort
   nicht.

**Zwei Wege für den Reverse-Proxy:**

- **Mitgelieferter Caddy (Normalfall).** Nichts weiter zu tun: Caddy nimmt die Host-Ports 80 und
  443 und holt für `MANBAN_DOMAIN` automatisch ein Let's-Encrypt-Zertifikat.
- **Eigener Reverse-Proxy.** `MANBAN_PROXY_PROFIL=eigener-proxy` in die `.env` — dann bleibt Caddy
  unten, und `manban-api` hängt auf `127.0.0.1:8080`. Der eigene Proxy muss das TLS beenden und
  `X-Forwarded-Proto`, `-Host` und `-For` setzen.

Sicherung und Wiederherstellung: [docs/backup.md](docs/backup.md). Was ein Versionssprung von Hand
verlangt: [UPGRADING.md](UPGRADING.md).

## Aus dem Quelltext bauen

Für Entwicklung und eigene Änderungen — das Bau-Overlay ist der einzige Schalter des Baus:

```
docker compose -f docker-compose.yml -f docker-compose.bau.yml up -d --build
```

## Ersten Admin einrichten

Alle registrierten Nutzer sind zunächst Plattform-**USER**; es gibt kein vordefiniertes
Admin-Konto. Empfohlen wird der Bootstrap-Token; der Datenbank-Weg ist der Notweg — die
vollständige Anleitung steht in [docs/betrieb.md](docs/betrieb.md#den-ersten-admin-einrichten).

Kurzfassung (Notweg über die Datenbank, nach dem Registrieren):

```
docker compose exec -T postgres psql -U manban -d manban \
  -c "UPDATE app_user SET email_verified = true, platform_role = 'ADMIN', approved_at = now() WHERE email = 'DEINE@MAIL';"
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

- [Betrieb & Installation](docs/betrieb.md) — Start, Umgebungsvariablen, E-Mail, erster Admin,
  Meldeweg der interaktiven Sitzungen
- [Upgrade](UPGRADING.md) — was ein Versionssprung von Hand verlangt; eine Version ohne eigenen
  Abschnitt verlangt keine Handarbeit
- [Nutzung](docs/nutzung.md) — Projekte, Boards, Karten, Listen-Ansicht, Ideen-Pool, Vorhaben,
  [Läufe](docs/nutzung.md#nachtlauf), [Verbrauch](docs/nutzung.md#verbrauch-leitstand),
  [Plattform-Leitstand](docs/nutzung.md#plattform-leitstand), Mitglieder
- [Sicherung & Wiederherstellung](docs/backup.md) — Sicherung einrichten, privaten Schlüssel
  verwahren, auf einen Zeitpunkt zurückholen, Verfallen alter Stände
- [Rollen & Rechte](docs/rollen-und-rechte.md) — Projekt- und Plattform-Rollen, Rechte-Matrix,
  Teilnahme am Plattform-Leitstand
- [Produktions-Deployment](docs/deployment-hostinger.md) — öffentlicher Betrieb hinter Traefik
- [Dogfooding](docs/dogfooding.md) — kanban-kit als eigenes Board anbinden

Die Designsprache der Oberfläche — „Kupferwarte“, zwei Erscheinungsbilder ohne Schalter — steht in
[CLAUDE-design.md](CLAUDE-design.md); die verbindliche Vorlage ist
[docs/entwurf-leitstand.html](docs/entwurf-leitstand.html).

Als gerenderte Website (VitePress) lässt sich die Doku lokal ansehen:

```
cd docs-site
npm install
npm run dev        # http://localhost:5173
```

## Lizenz

kanban-kit steht unter der MIT-Lizenz. Der vollständige Lizenztext liegt in
[LICENSE](LICENSE).

Copyright (c) 2026 Manfred Wolff
