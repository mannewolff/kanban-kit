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

  Wer das Projekt nicht nur betreibt, sondern auch **baut und testet**, braucht unter Colima
  zusätzlich zwei Umgebungsvariablen — siehe [Testsuite lokal starten](#testsuite-lokal-starten).

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
| `MANBAN_OUTBOX_ENABLED` | Outbox-Worker aktivieren (abgeschaltet bleiben Aufträge liegen) | `true` |
| `MANBAN_OUTBOX_POLL_INTERVAL_MS` | Abstand zwischen zwei Worker-Läufen in Millisekunden | `5000` |
| `MANBAN_OUTBOX_MAX_ATTEMPTS` | Versuche, bevor ein Auftrag als gescheitert gilt | `8` |
| `MANBAN_OUTBOX_RETENTION_DAYS` | Tage, nach denen erledigte Outbox-Einträge gelöscht werden | `7` |
| `MANBAN_SESSION_SECRET` | HMAC-Secret der Session-Cookies (in Produktion setzen!) | Dev-Default |
| `MANBAN_COOKIE_SECURE` | Session-Cookie nur über HTTPS | `true` |
| `POSTGRES_*`, `MINIO_*` | DB- und Objektspeicher-Zugangsdaten | siehe `docker-compose.yml` |

> **Papierkorb-Aufbewahrung:** Karten im Papierkorb werden nach **30 Tagen** automatisch endgültig
> gelöscht. Diese Frist ist derzeit fest eingestellt (nicht über eine Umgebungsvariable steuerbar);
> abschalten lässt sich die Automatik nur global über `MANBAN_CLEANUP_ENABLED=false`.

> **Outbox-Rückstand:** Seiteneffekte, die auch nach `MANBAN_OUTBOX_MAX_ATTEMPTS` Versuchen nicht
> durchgehen, bleiben als Zeile mit `status = 'FAILED'` in der Tabelle `outbox_entry` stehen — samt
> Ereignistyp, Versuchszahl und letzter Fehlermeldung. Der Aufräum-Job löscht **nur** erledigte
> Einträge, damit ein nie ausgeführter Auftrag nicht lautlos verschwindet. Der Inhalt (`payload`)
> ist bei erledigten wie gescheiterten Einträgen bewusst geleert, damit keine Klartext-Geheimnisse
> dauerhaft in der Datenbank liegen. Prüfen mit:
>
> ```sql
> SELECT id, event_type, idempotency_key, attempts, last_error FROM outbox_entry
> WHERE status = 'FAILED' ORDER BY completed_at DESC;
> ```

> **E-Mail-Zustellung läuft über die Outbox:** Seit Issue #502 bestätigt eine erfolgreiche
> HTTP-Antwort (Registrierung, Passwort-Reset, Einladung, Projektanlage) die **gespeicherte
> fachliche Operation, nicht die Mail-Zustellung**. Die Mail wird in derselben Transaktion
> vorgemerkt und nach dem Commit vom Worker mit Wiederholungen versandt. Ein SMTP-Ausfall rollt
> also keine Registrierung oder Einladung mehr zurück (früherer 502 beim Einladen entfällt) —
> hängengebliebene Mails erscheinen als `FAILED`-Einträge in der Abfrage oben.

> **Objektspeicher-Abgleich (Anhänge):** Blob-Löschungen (Anhang löschen, Karten-/Board-Purge)
> laufen seit Issue #503 ebenfalls über die Outbox. Verwaiste Blobs (z. B. Altbestand aus Purges
> vor #503) und fehlende Objekte findet der Admin-Abgleich:
>
> ```
> GET /api/admin/storage/reconciliation   → { "orphanedObjects": [...], "missingObjects": [...] }
> ```
>
> Der Abgleich **berichtet nur** und löscht nichts automatisch (ein laufender Upload hat kurzzeitig
> ein Objekt ohne Metadaten). Verwaiste Objekte bei Bedarf gezielt über die MinIO-Konsole oder
> `mc rm` entfernen.

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

### Weg A — Bootstrap-Token (empfohlen)

Der vorgesehene Pfad: Er läuft über die reguläre Anwendungslogik und hinterlässt einen
vollständig eingerichteten Admin. Wirkt **nur, solange kein Admin existiert** (selbstheilend,
kein Aussperren).

1. `MANBAN_BOOTSTRAP_ADMIN_TOKEN=DEIN_TOKEN` in der `.env` setzen und **neu bauen** (`docker compose up --build -d`).
2. Normal **registrieren** und **einloggen** (E-Mail vorher bestätigen, s. o.).
3. Eingeloggt **`https://localhost/admin/bootstrap`** öffnen, den Token eingeben → „Admin werden".

**Wichtig:** zuerst einloggen, *dann* `/admin/bootstrap` — der Bootstrap stuft den *gerade
eingeloggten* Nutzer hoch. Ohne Login leitet die Seite auf `/login`. Bei falschem Token → 403,
wenn schon ein Admin existiert → 409. Token danach aus der `.env` entfernen.

### Weg B — direkt in der Datenbank (Notweg)

Nur nehmen, wenn Weg A nicht in Frage kommt — etwa weil kein Neubau möglich ist. Der Weg
umgeht jede Anwendungslogik, jedes Feld muss von Hand stimmen. Registrieren, dann per SQL
freischalten und zum Admin machen (spart Token + Verifikations-Link):

```
docker compose exec -T postgres psql -U manban -d manban \
  -c "UPDATE app_user SET email_verified = true, platform_role = 'ADMIN', approved_at = now() WHERE email = 'DEINE@MAIL';"
```

`approved_at` gehört mit in den Befehl: Neue Konten durchlaufen ein Freigabe-Gate. Ein
Plattform-Admin gilt zwar auch ohne Zeitstempel als freigegeben und kann sich anmelden — ohne
`approved_at` führt die Benutzerübersicht ihn aber weiterhin als wartend.

Danach **ab- und wieder anmelden** — das Frontend lädt die Rolle nur beim Login (`/api/me`).
Anschließend erscheint **„Admin"** in der Seitenleiste.

## Testsuite lokal starten

Betrifft nur, wer das Repository klont und selbst baut — für den reinen Betrieb über
`docker compose` ist nichts davon nötig.

Die Integrationstests starten ihre eigene Postgres-Instanz über **Testcontainers**. Unter
**Colima** findet Testcontainers die Docker-Laufzeit nicht von allein; nötig sind:

```
colima start
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

Danach laufen `mvn verify` und `mvn -Ppit -Dskip.frontend=true test` durch.

**Die beiden Variablen beantworten zwei verschiedene Fragen — keine ersetzt die andere.**

`DOCKER_HOST` sagt, **wo Testcontainers mit dem Daemon spricht**. Auf dem Mac existiert nur
`~/.colima/default/docker.sock`; ein `/var/run/docker.sock` gibt es dort nicht. Der `docker`-Befehl
findet den Daemon trotzdem, weil er dem Docker-*Context* folgt — Testcontainers tut das nicht,
wenn `~/.testcontainers.properties` eine feste Strategie vorgibt (`UnixSocketClientProviderStrategy`
sucht genau unter `/var/run/docker.sock`).

`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` sagt, **welchen Pfad Testcontainers in den Container
hineinreicht**. Der Aufräum-Container *Ryuk* bekommt den Docker-Socket als Bind-Mount, und dieser
Pfad muss **innerhalb der VM** gültig sein — dort heißt der Socket `/var/run/docker.sock`.

### Symptome

| Fehlt | Symptom |
|---|---|
| Docker läuft nicht | Alle Integrationstests fallen mit `ExceptionInInitializerError` in `AbstractIntegrationTest` aus; im Log darunter `Could not find a valid Docker environment`. |
| `DOCKER_HOST` | Dasselbe Bild — die Ursachenzeile nennt `NoSuchFileException (/var/run/docker.sock)`. |
| `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` | Der Client verbindet, aber Ryuk startet nicht: `Status 500: error while creating mount source path '/Users/…/.colima/default/docker.sock': mkdir …: operation not supported`. |

Der mittlere und der untere Fall sehen im Testbericht sehr ähnlich aus, haben aber verschiedene
Ursachen — die Unterscheidung steht in der Zeile nach `Caused by`.
