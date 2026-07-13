# Produktions-Deployment (Hostinger, hinter Traefik)

Diese Anleitung beschreibt den öffentlichen Betrieb von kanban-kit unter
**`https://kanban.mwolff.org`** auf dem Hostinger-Server. Für den **lokalen** Betrieb
(eigener Caddy, self-signed TLS) siehe [Betrieb & Installation](betrieb.md) — dort stehen
auch die gemeinsamen Konzepte (Umgebungsvariablen, erster Admin, E-Mail-Verifikation).

## Überblick

Auf dem Server läuft bereits **Traefik** (Reverse-Proxy + Let's Encrypt) auf Port 80/443.
kanban-kit wird deshalb **hinter dieses Traefik** gehängt statt seinen eigenen Caddy zu nutzen:

- `manban-api` hängt am externen Docker-Netz **`web`** und wird über Traefik-Labels
  veröffentlicht (TLS über den certresolver **`mytlschallenge`**).
- Der lokale **Caddy**-Container startet in Produktion **nicht** (Compose-Profil `local-tls`).
- **Postgres** und **MinIO** bleiben rein intern (keine Host-Ports, nur das interne Netz).

Aktiviert wird das über das Overlay `docker-compose.prod.yml` zusätzlich zur Basis
`docker-compose.yml`:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

## Erstinbetriebnahme (auf dem Server)

1. **Traefik/Netz prüfen.** Auf dem Server (`srv1014330.hstgr.cloud`) sicherstellen, dass
   Traefik läuft und das externe Netz `web` existiert:
   ```bash
   docker ps                     # Traefik auf 80/443?
   docker network ls | grep web  # sonst: docker network create web
   ```
   Der certresolver heißt `mytlschallenge`, die Entrypoints `web,websecure` — diese Werte
   stehen bereits im Overlay. Nur bei Abweichung die Labels in `docker-compose.prod.yml`
   anpassen.

2. **DNS prüfen.** Der A-Record ist bei Strato vorbereitet:
   ```bash
   dig kanban.mwolff.org +short  # -> 72.60.131.171
   ```

3. **Code holen.** Repo (Branch `production`) nach `/root/opt/manban` klonen (bzw. `git pull`):
   ```bash
   git clone -b production https://github.com/mannewolff/kanban-kit.git /root/opt/manban
   cd /root/opt/manban
   ```

4. **`.env` anlegen** (aus `.env.example`) und die echten Prod-Werte setzen:
   ```bash
   cp .env.example .env
   ```
   Mindestens:
   - `MANBAN_BASE_URL=https://kanban.mwolff.org`
   - `MANBAN_SESSION_SECRET=$(openssl rand -hex 32)` — **ohne diesen Wert bricht der
     Start absichtlich ab** (fail-fast). `-hex` liefert nur `0-9a-f`, also kein `$`-Escaping nötig.
   - `MANBAN_COOKIE_SECURE=true`
   - `POSTGRES_PASSWORD`, `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` — starke Werte.
   - Mail (Strato): `MANBAN_MAIL_ENABLED=true`, `MANBAN_SMTP_*`, `MANBAN_MAIL_FROM=info@mwolff.org`,
     echtes SMTP-Passwort. Ohne echten Mailversand können sich Nutzer nicht selbst verifizieren
     (Links landen nur im Log).
   - `MANBAN_BOOTSTRAP_ADMIN_TOKEN=<Zufallswert>` — für den ersten Admin.

   > **⚠️ Sonderzeichen in Secrets (`$`).** Docker Compose interpoliert `$` in `.env`-Werten.
   > Enthält ein Wert ein `$` (typisch: ein vorgegebenes SMTP-Passwort), muss **jedes `$` als `$$`**
   > geschrieben werden — sonst wird der Wert stillschweigend verstümmelt und z. B. die SMTP-Auth
   > schlägt fehl. Symptom beim Start: `WARN The "…" variable is not set. Defaulting to a blank string.`
   > Beispiel: Passwort `ab$cd` → `ab$$cd`. Für **selbst erzeugte** Tokens `$`-freie Erzeugung
   > bevorzugen (`openssl rand -hex 32`), dann entfällt das Escaping.
   > **Kontrolle** (muss `0` liefern):
   > ```bash
   > docker compose -f docker-compose.yml -f docker-compose.prod.yml config 2>&1 >/dev/null \
   >   | grep -c "is not set"
   > ```

5. **Starten:**
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
   docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
   docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f manban-api
   ```
   Erwartet: `manban-api`, `postgres`, `minio` laufen (**kein** `caddy`); Flyway-Migrationen
   grün; „Started ManbanApplication".

6. **Ersten Admin einrichten.** Registrieren → Verifikations-Mail (Strato) bestätigen →
   eingeloggt `https://kanban.mwolff.org/admin/bootstrap` mit dem Bootstrap-Token aufrufen.
   Details und der DB-Fallback stehen in [Betrieb & Installation](betrieb.md#den-ersten-admin-einrichten).
   Danach `MANBAN_BOOTSTRAP_ADMIN_TOKEN` in der `.env` leeren und neu erstellen:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --force-recreate manban-api
   ```

## Update-Routine

```bash
cd /root/opt/manban
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Flyway migriert die Datenbank automatisch beim Boot der `manban-api`. Neue
Umgebungsvariablen zuerst in die `.env` eintragen, dann mit `--force-recreate` neu erstellen
(Container lesen Env nur beim Erstellen).

## Verifikation

- `curl -I https://kanban.mwolff.org` → `200` mit gültigem Let's-Encrypt-Zertifikat
  (kein Selbstsigniert-Warnhinweis).
- Registrierung löst eine **echte** Verifikations-Mail über Strato aus.
- Das Session-Cookie ist `Secure` + `HttpOnly`.
- Postgres und MinIO sind von außen **nicht** erreichbar (kein offener Host-Port —
  in `docker compose … config` erscheinen für sie keine `ports:`-Mappings).
- Smoke-Test: Projekt/Board/Karte anlegen, Datei-Anhang hochladen (MinIO), Kommentar.

Server-Interna (Pfade, Traefik-Details des Gesamt-Setups) liegen im privaten Memory-Vault
(`Wissen/Toolbox-Deployment-Hostinger.md`), nicht in diesem öffentlichen Repo.
