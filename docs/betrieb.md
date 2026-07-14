# Betrieb & Installation

kanban-kit läuft als ein Stack aus vier Containern (über Docker Compose):
**Caddy** (TLS + Reverse-Proxy), **manban-api** (Spring-Boot-Backend, das auch das
gebaute Frontend ausliefert), **Postgres** und **MinIO** (Objektspeicher für Anhänge).

## Voraussetzungen

- Docker-Laufzeit. Auf macOS z. B. **Colima**:
  ```
  colima status || colima start
  ```
  Symptom für „Docker läuft nicht": `docker ps` meldet „Cannot connect to the Docker daemon".

## Starten

Im Repo-Verzeichnis (dort liegt `docker-compose.yml`):

```
docker compose up --build -d
```

- `--build` baut das Image neu (npm-Build des Frontends + Maven-Jar). **Nach jeder Codeänderung nötig** —
  ein reines `docker compose up -d` nutzt sonst das alte Image.
- `-d` startet im Hintergrund; die Build-Ausgabe erscheint dann erst am Ende. Für Live-Ausgabe `-d` weglassen.

Status prüfen:
```
docker compose ps
docker compose logs -f manban-api   # warten auf "Started ManbanApplication"
```

## Aufruf

Im Browser: **`https://localhost`**

Für `localhost` nutzt Caddy ein selbst-signiertes Zertifikat → der Browser zeigt eine
Sicherheitswarnung. Einmal „Trotzdem fortfahren" akzeptieren (lokal so gewollt). Für eine
echte Domain `MANBAN_DOMAIN` setzen (dann automatisch Let's-Encrypt).

Direkte Deep-Links und Reload (z. B. `https://localhost/boards/1`, `/roles`) funktionieren —
das Backend liefert für Nicht-API-Pfade die Single-Page-App aus (SPA-Fallback).

## Umgebungsvariablen

Am einfachsten über eine **`.env`** neben der `docker-compose.yml` (wird von Compose automatisch
geladen und ist per `.gitignore` ausgeschlossen).

| Variable | Bedeutung | Default |
|----------|-----------|---------|
| `MANBAN_BASE_URL` | Basis-URL für Links in E-Mails | `https://localhost` |
| `MANBAN_BOOTSTRAP_ADMIN_TOKEN` | Einmal-Token für den ersten Admin (leer = deaktiviert) | leer |
| `MANBAN_MAIL_ENABLED` | echten Mailversand aktivieren | `false` (Links werden geloggt) |
| `MANBAN_CLEANUP_ENABLED` | geplante Aufräum-Jobs aktivieren (Done-Archivierung **und** Papierkorb-Leerung) | `true` |
| `MANBAN_DONE_RETENTION_DAYS` | Tage bis Done-Karten automatisch archiviert werden | `30` |
| `MANBAN_SESSION_SECRET` | HMAC-Secret der Session-Cookies (in Produktion setzen!) | Dev-Default |
| `MANBAN_COOKIE_SECURE` | Session-Cookie nur über HTTPS | `true` |
| `POSTGRES_*`, `MINIO_*` | DB- und Objektspeicher-Zugangsdaten | siehe `docker-compose.yml` |

> **Papierkorb-Aufbewahrung:** Karten im Papierkorb werden nach **30 Tagen** automatisch endgültig
> gelöscht. Diese Frist ist derzeit fest eingestellt (nicht über eine Umgebungsvariable steuerbar);
> abschalten lässt sich die Automatik nur global über `MANBAN_CLEANUP_ENABLED=false`.

## E-Mail-Bestätigung (ohne Mailserver)

Im Standard ist der Mailversand **aus** (`MANBAN_MAIL_ENABLED=false`). Verifikations-, Passwort-Reset-
und Einladungs-Links werden stattdessen **ins Log geschrieben**:

```
docker compose logs manban-api | grep "Verifikations-Link"
```

Den geloggten Link (`https://localhost/verify?token=…`) im Browser öffnen → E-Mail bestätigt.

## Den ersten Admin einrichten

Alle registrierten Nutzer sind zunächst Plattform-**USER**. Es gibt kein vordefiniertes
Admin-Konto — „Admin" ist eine Rolle, die einem echten (E-Mail-)Account verliehen wird.

### Weg A — Bootstrap-Token (vorgesehen)

Wirkt **nur, solange kein Admin existiert** (selbstheilend, kein Aussperren).

1. `MANBAN_BOOTSTRAP_ADMIN_TOKEN=DEIN_TOKEN` in der `.env` setzen und **neu bauen** (`docker compose up --build -d`).
2. Normal **registrieren** und **einloggen** (E-Mail vorher bestätigen, s. o.).
3. Eingeloggt **`https://localhost/admin/bootstrap`** öffnen, den Token eingeben → „Admin werden".

**Wichtig:** zuerst einloggen, *dann* `/admin/bootstrap` — der Bootstrap stuft den *gerade
eingeloggten* Nutzer hoch. Ohne Login leitet die Seite auf `/login`. Bei falschem Token → 403,
wenn schon ein Admin existiert → 409. Token danach aus der `.env` entfernen.

### Weg B — direkt in der Datenbank

Registrieren, dann per SQL freischalten und zum Admin machen (spart Token + Verifikations-Link):

```
docker compose exec -T postgres psql -U manban -d manban \
  -c "UPDATE app_user SET email_verified = true, platform_role = 'ADMIN' WHERE email = 'DEINE@MAIL';"
```

Danach **ab- und wieder anmelden** — das Frontend lädt die Rolle nur beim Login (`/api/me`).
Anschließend erscheint **„Admin"** in der Seitenleiste.
