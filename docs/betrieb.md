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

- Browser ab **Chrome 110**, **Safari 16** und **Firefox 115**. Das Frontend nutzt Methoden aus
  ES2023 (etwa `Array.prototype.findLast`); der Vite-Build übersetzt Methoden nicht in ältere
  Fassungen, sie müssen also im Browser selbst vorhanden sein. In älteren Browsern bricht die
  Oberfläche an diesen Stellen ab.

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

> **Dieser Stack ist der Entwicklungsbetrieb.** Die `docker-compose.yml` setzt
> `MANBAN_DEV_MODE` auf `true` und liefert einen Standard-Sitzungsschlüssel mit. Die Anwendung
> startet damit, schreibt aber bei jedem Start eine Warnung ins Log — Sitzungs-Cookies dieser
> Instanz sind fälschbar, weil der Standardschlüssel im öffentlichen Repository steht. Die Zeile
> findet man mit:
>
> ```
> docker compose logs manban-api | grep "Entwicklungs-/Testbetrieb"
> ```
>
> Für einen produktiven Stand siehe den nächsten Abschnitt.

## Produktiv betreiben

**Vor dem ersten produktiven Start** wird ein eigener `MANBAN_SESSION_SECRET` gesetzt. Ohne
diesen Wert startet die Anwendung nicht — sie bricht ab, statt mit dem mitgelieferten
Standardschlüssel zu signieren. Die ausgelieferte `.env.example` lässt den Wert deshalb bewusst
leer: Niemand soll einen Schlüssel gesetzt haben, ohne ihn gewählt zu haben.

1. Schlüssel erzeugen:
   ```
   openssl rand -hex 32
   ```
   `-hex` liefert nur `0-9a-f` — damit ist kein `$`-Escaping in der `.env` nötig.
2. Den Wert in die `.env` neben der `docker-compose.yml` eintragen:
   ```
   MANBAN_SESSION_SECRET=<erzeugter Wert>
   ```
3. Mit dem Produktions-Overlay starten:
   ```
   docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
   ```
   Das Overlay setzt `MANBAN_DEV_MODE` fest auf `false` — der Entwicklungs-Schalter aus der `.env`
   greift dort nicht.

Der vollständige Server-Ablauf (Traefik, DNS, Mail, erster Admin) steht in
[docs/deployment-hostinger.md](deployment-hostinger.md) und wird hier nicht wiederholt.

## Upgrade-Hinweise

**Eigener Sitzungsschlüssel ist Startbedingung.** Eine Instanz, die bisher ohne eigenen
`MANBAN_SESSION_SECRET` lief, startet nach dem Update erst wieder, wenn ein eigener Schlüssel
gesetzt ist (siehe [Produktiv betreiben](#produktiv-betreiben)). Alle bestehenden Sitzungen sind
danach ungültig — die Nutzer melden sich einmal neu an.

Das ist gewollt: Mit dem mitgelieferten Wert kann jeder, der das öffentliche Repository kennt,
gültige Sitzungen für jedes Konto erzeugen — auch für einen Plattform-Administrator.

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
| `MANBAN_CLEANUP_ENABLED` | geplante Aufräum-Jobs aktivieren (Done-Archivierung, Papierkorb-Leerung, Outbox-Aufräumung, Aufräumung der Überlast-Abweisungen nach 90 Tagen und der Idempotenz-Schlüssel nach 24 Stunden) | `true` |
| `MANBAN_DONE_RETENTION_DAYS` | Tage bis Done-Karten automatisch archiviert werden | `30` |
| `MANBAN_OUTBOX_ENABLED` | Outbox-Worker aktivieren (abgeschaltet bleiben Aufträge liegen) | `true` |
| `MANBAN_OUTBOX_POLL_INTERVAL_MS` | Abstand zwischen zwei Worker-Läufen in Millisekunden | `5000` |
| `MANBAN_OUTBOX_MAX_ATTEMPTS` | Versuche, bevor ein Auftrag als gescheitert gilt | `8` |
| `MANBAN_OUTBOX_RETENTION_DAYS` | Tage, nach denen erledigte Outbox-Einträge gelöscht werden | `7` |
| `MANBAN_SESSION_SECRET` | HMAC-Secret der Session-Cookies. Der Dev-Default gilt **nur** im ausdrücklich eingeschalteten Entwicklungsbetrieb (`MANBAN_DEV_MODE=true`) — sonst verweigert die Anwendung den Start | Dev-Default |
| `MANBAN_DEV_MODE` | Entwicklungs-/Testbetrieb ausdrücklich einschalten; erlaubt den Start mit dem Standard-Sitzungsschlüssel, mit Warnung. Der lokale Compose-Stack setzt ihn auf `true`, das Produktions-Overlay fest auf `false` | `false` |
| `MANBAN_COOKIE_SECURE` | Session-Cookie nur über HTTPS | `true` |
| `MANBAN_DB_POOL_MAX` | Höchstzahl der Datenbankverbindungen der Anwendung | `20` |
| `MANBAN_DB_POOL_MIN_IDLE` | Verbindungen, die auch ohne Last offen bleiben | `5` |
| `MANBAN_DB_CONNECTION_TIMEOUT_MS` | Höchste Wartezeit auf eine freie Verbindung in Millisekunden | `5000` |
| `MANBAN_SERVER_THREADS_MAX` | Höchstzahl gleichzeitig bearbeiteter HTTP-Aufrufe | `600` |
| `POSTGRES_*`, `MINIO_*` | DB- und Objektspeicher-Zugangsdaten | siehe `docker-compose.yml` |

> **Verbindungspool und Server-Threads:** Die vier Stellschrauben `MANBAN_DB_POOL_MAX`,
> `MANBAN_DB_POOL_MIN_IDLE`, `MANBAN_DB_CONNECTION_TIMEOUT_MS` und `MANBAN_SERVER_THREADS_MAX`
> sind ausdrücklich gesetzt, statt auf den Vorgaben von HikariCP und Tomcat zu stehen (Issue #998).
> Die Werte sind begründete Startwerte für 50 gleichzeitig aktive Personen; die Rechnung dazu steht
> als Kommentar in `src/main/resources/application.yml`. `MANBAN_DB_POOL_MAX` muss unter
> `max_connections` der Datenbank bleiben (Postgres-Vorgabe: 100). Messergebnis und
> Mindestausstattung folgen mit dem Lastnachweis.

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

## Zählbremse gegen Massenversuche

Drei Endpunkte sind gegen Massenversuche begrenzt:

| Endpunkt | Was gezählt wird |
| --- | --- |
| `POST /api/auth/login` | nur abgewiesene Anmeldungen (`401`) — ein `400` zählt nicht |
| `POST /api/auth/register` | jeder Aufruf, auch der erfolgreiche |
| `POST /api/auth/forgot` | jeder Aufruf, auch der erfolgreiche |

Registrierung und Reset-Anforderung zählen auch bei Erfolg, weil beide von außen erfolgreich
ausgelöst werden können und sonst unbegrenzt Mails erzeugten.

**Vorgabewerte:** zehn Versuche je fünfzehn Minuten, danach fünfzehn Minuten Sperre. Gezählt wird
je **Herkunft und Vorgang** getrennt: Ausgeschöpfte Anmeldeversuche sperren die Reset-Anforderung
nicht, und eine gesperrte Herkunft sperrt kein Konto — dieselbe Anmeldung von einer anderen
Herkunft gelingt weiterhin. Die Sperre läuft ab dem ersten abgewiesenen Versuch und wird durch
weitere Versuche **nicht verlängert**. Abgewiesen wird mit `429` und einem `Retry-After`-Header.

Alle Werte lassen sich über `MANBAN_RATELIMIT_*` überschreiben, siehe `.env.example`.

**Im Protokoll** steht beim Eintritt einer Sperre genau **eine** `WARN`-Zeile je Herkunft, Vorgang
und Sperrfenster — weitere abgewiesene Versuche im selben Fenster erzeugen keine weiteren Zeilen.
Die Zeile nennt Herkunft und Vorgang. **Die E-Mail-Adresse steht nie darin**: Die Bremse liest den
Request-Body gar nicht, sie kennt ihn nicht.

**Annahme zum Proxy.** Vorgegeben ist **ein** vertrauenswürdiger Proxy vor der Anwendung — lokal der
mitgelieferte Caddy, in Produktion etwa Traefik. Ist die Anwendung **direkt** erreichbar, muss
`MANBAN_RATELIMIT_TRUSTED_PROXY_COUNT=0` gesetzt werden. Ein zu hoher Wert ist gefährlich: Die Bremse
läse dann eine Adresse, die der Client selbst mitschicken kann, und wäre umgehbar. Der mitgelieferte
`Caddyfile` ersetzt `X-Forwarded-For` zusätzlich, statt ihn anzuhängen — eine zweite Linie für den
Fall, dass an dieser Einstellung später etwas verstellt wird.

**Grenze bei mehreren Instanzen.** Der Zählstand liegt **im Arbeitsspeicher des Prozesses**. Laufen
N Instanzen hinter einem Lastverteiler, zählt jede für sich: Die tatsächliche Grenze ist dann das
N-fache der eingestellten. Das ist bekannt und bewusst nicht gelöst — das Produkt liefert eine
Instanz aus. Wer mehrere betreibt, sollte die Werte entsprechend senken oder eine Bremse im Proxy
davorsetzen.

## Durchsatzbremse für Zugriffstoken

Neben der Zählbremse oben gibt es eine **zweite, davon getrennte** Bremse (Konfigurationsblock
`manban.ratelimit.throughput`). Beide antworten mit `429` — sie unterscheiden sich darin, **wen**
sie begrenzen und **wie**:

| | Zählbremse gegen Massenversuche | Durchsatzbremse für Zugriffstoken |
| --- | --- | --- |
| Greift auf | Anmeldung, Registrierung, Reset-Anforderung | jeden Aufruf mit Zugriffstoken (CLI, Nachtlauf) |
| Zählt je | Herkunft (IP) und Vorgang | Person, über alle ihre Token hinweg |
| Regel | N Versuche je Fenster, dann feste Sperre | kontinuierlich nachgefüllt, nur der Überschuss wird abgewiesen |
| `type` im Problem-Detail | `about:blank` | `urn:manban:overload` |

Die **Weboberfläche** wird von der Durchsatzbremse **nie** gebremst: Sie meldet sich über die
Sitzung an, nicht über ein Zugriffstoken. **Vorgabe:** 60 Befehle je Person und Minute, davon 10
gleichzeitig. Wer die Minute ausschöpft, bekommt einen Befehl je Sekunde zurück — ein starres
Minutenfenster ließe dagegen 120 Befehle in zwei Sekunden über die Fenstergrenze. Ein abgewiesener
Aufruf führt nichts aus und zählt nicht auf das Kontingent; `Retry-After` nennt die Wartezeit in
Sekunden. Das mitgelieferte Werkzeug wiederholt daran selbst.

| Variable | Bedeutung | Default |
|----------|-----------|---------|
| `MANBAN_THROUGHPUT_ENABLED` | Durchsatzbremse einschalten | `true` |
| `MANBAN_THROUGHPUT_PER_MINUTE` | Befehle je Person und Minute | `60` |
| `MANBAN_THROUGHPUT_CONCURRENT` | davon gleichzeitig laufend | `10` |
| `MANBAN_THROUGHPUT_MAX_TRACKED_PERSONS` | Obergrenze der verfolgten Personen (Speicherschutz) | `100000` |

**Im Protokoll** steht bei anhaltender Überschreitung höchstens eine `WARN`-Zeile je Person und
Minute. Jede Abweisung wird außerdem je Person und Stunde aufsummiert abgelegt und nach 90 Tagen
aufgeräumt. Für mehrere Instanzen gilt dieselbe Einschränkung wie bei der Zählbremse: Jede zählt
für sich.

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

## Meldeweg der interaktiven Sitzungen

Der [Verbrauch im Leitstand](nutzung.md#verbrauch-leitstand) zählt zwei Gattungen: Nachtläufe und
interaktive Sitzungen. Nachtläufe meldet der Nacht-Runner, interaktive Sitzungen ein **Hook des
Kits**. Dieser Abschnitt sagt, was dafür vorliegen muss, was den Erfassungsbeginn setzt, wie lange
aufbewahrt wird und wo die Erfassung Lücken hat.

Die Tatsachengrundlage — welche Verbrauchsangaben das Sitzungsprotokoll führt, welche
Hook-Ereignisse Claude Code kennt und was ein frischer Worktree mitbekommt — steht in
[Befund: Verbrauchsangaben, Hook-Ereignisse und Worktrees](befund-interaktive-sitzungen.md), samt
den Kommandos, mit denen sie erhoben wurde.

### Voraussetzung: ein projektgebundenes Zugriffstoken im Arbeitsverzeichnis

Beide Gattungen gehen über **dieselbe** Strecke ins Board: `POST /api/kanban/night-runs`,
angemeldet mit einem **projektgebundenen Zugriffstoken**, ohne Sitzungs-Cookie. Das **Zielprojekt
kommt aus der Bindung des Tokens** und nicht aus dem Aufruf — eine Meldung kann nur dort landen,
wofür das Token ausgestellt wurde, und ein Token kann nie mehr als sein Besitzer. Ein Token ohne
Projektbindung wird abgewiesen.

Daraus folgt die Voraussetzung und gleichzeitig die Grenze: Erfasst wird eine Sitzung genau dann,
wenn in ihrem **Arbeitsverzeichnis** ein projektgebundenes Zugriffstoken liegt. **Ohne Token bleibt
die Sitzung außen vor** — sie erzeugt keinen Eintrag, und der Leitstand erfährt nichts von ihr. Eine
Sitzung außerhalb eines so eingerichteten Projekts (etwa die Arbeit am Kit selbst) zählt damit nicht
mit. Die Zuordnung läuft ausdrücklich **nicht** über den Repository-Pfad oder einen
Konfigurationsnamen: Nur die Bindung des Tokens entscheidet.

### Der Hook aus dem Kit

Eingerichtet wird der Hook als `hooks`-Block in der **`.claude/settings.json` des Projekts**, der
ein Skript des Kits aufruft; Skript und Block bringt das Kit über seinen Installer mit. Eine
nutzerweite Einstellung unter `~/.claude` wäre falsch — sie meldete aus jedem Verzeichnis, und die
Erfassung ist projektgebunden.

- **Gemeldet wird am Sitzungsende** (`SessionEnd`) als vollständiger Stand, und davor
  **fortschreibend und gedrosselt** — höchstens einmal je fünf Minuten — nach einem Zug (`Stop`).
  Nur am Ende zu melden verlöre jede abgestürzte oder abgebrochene Sitzung; ungedrosselt entstünde
  je Zug ein HTTP-Aufruf.
- **Mehrfache Meldungen derselben Sitzung ersetzen einander.** Der fachliche Schlüssel ist der
  Startzeitpunkt; die Strecke antwortet, ob sie den Eintrag angelegt oder einen vorhandenen ersetzt
  hat. Eine Sitzung doppelt zu melden erzeugt also keinen zweiten Eintrag.
- **Die Sitzungen des Nacht-Runners melden über diesen Weg nicht.** Der Hook schweigt, wenn
  `KIT_AGENT_MODEL` in der Umgebung gesetzt ist — dieselbe Bedingung, an der die Skills den
  Nachtbetrieb erkennen. Ihr Verbrauch steckt bereits in der Meldung des Runners; ein zweiter
  Meldeweg zählte ihn ein zweites Mal.

> **Stand.** Die Server-Seite dieses Weges steht: dieselbe Einlieferungsstrecke nimmt die Gattung
> `INTERACTIVE` an, der Erfassungsbeginn und die getrennten Aufbewahrungsgrenzen sind umgesetzt, und
> der Leitstand zeigt beide Anteile. Der **Hook selbst liegt im Kit-Repository** und wird von dort
> ausgeliefert. Solange er in einem Projekt nicht eingerichtet ist, meldet keine interaktive
> Sitzung — der Leitstand zeigt beim interaktiven Anteil dann „nicht erfasst", und das ist keine
> Störung, sondern der Zustand eines Projekts ohne Hook.

### Erfassungsbeginn je Projekt

Die Spalte `project.interactive_usage_since` trägt den **Startzeitpunkt der ersten je gemeldeten
interaktiven Sitzung** dieses Projekts. Gesetzt wird sie **beim ersten Eingang** einer solchen
Meldung und danach nie wieder; die Bedingung liegt im `UPDATE` selbst, damit zwei gleichzeitig
eingehende Sitzungen sie nicht beide passieren. Einen Handgriff und eine Umgebungsvariable dafür
gibt es nicht.

Dieser Zeitpunkt ist die Grenze, an der die Anzeige **„nicht erfasst" von einer echten 0
unterscheidet**: Ein Zeitraum ganz davor zeigt „nicht erfasst", einer, der ihn schneidet,
„teilweise erfasst", und danach ist eine 0 eine gemessene 0 (siehe
[Nutzung](nutzung.md#nicht-erfasst-teilweise-erfasst-und-nicht-gemessen)). Abgeleitet wird er
bewusst **nicht** aus dem ältesten noch vorhandenen Eintrag — der wandert mit dem Ringpuffer nach
vorn, und die Unterscheidung „nie erfasst" gegen „erfasst, dann verdrängt" ginge verloren.

Eine nachträgliche Erfassung älterer Sitzungen gibt es nicht: Die Erfassung beginnt mit ihrer
Einführung. Nachsehen lässt sich der Stand je Projekt mit:

```
docker compose exec -T postgres psql -U manban -d manban \
  -c "SELECT id, name, interactive_usage_since FROM project ORDER BY id;"
```

### Getrennte Ringpuffer-Grenzen

Aufbewahrt wird je Projekt und **je Gattung** begrenzt. Vier Werte stehen dafür in
`NightRunProperties` (`src/main/java/org/mwolff/manban/nightrun/application/`):

| Eigenschaft in `NightRunProperties` | Konfigurationsschlüssel | Was begrenzt wird | Vorgabe |
|---|---|---|---|
| `maxPerProject` | `manban.nightrun.max-per-project` | aufbewahrte **Nachtläufe** | 190 |
| `maxItemsPerProject` | `manban.nightrun.max-items-per-project` | **verwaiste** Arbeitspakete von Nachtläufen | 2000 |
| `maxInteractivePerProject` | `manban.nightrun.max-interactive-per-project` | aufbewahrte **interaktive Sitzungen** | 400 |
| `maxInteractiveItemsPerProject` | `manban.nightrun.max-interactive-items-per-project` | **verwaiste** Arbeitspakete interaktiver Sitzungen | 4000 |

Die Werte werden in `src/main/resources/application.yml` gesetzt; eine eigene `MANBAN_*`-Variable
führt die `docker-compose.yml` dafür nicht. Ein fehlender oder kleinerer Wert als 1 fällt auf die
Vorgabe zurück.

**Warum je Gattung ein eigenes Paar:** Interaktive Sitzungen sind deutlich häufiger als Nachtläufe.
Unter einer gemeinsamen Grenze verdrängten sie die Nachtläufe binnen Tagen und zerstörten die
bestehende Auswertung. Verdrängt wird deshalb **innerhalb** einer Gattung; die andere bleibt
unberührt. **Verwaist** heißt: Der Lauf oder die Sitzung ist schon verdrängt, das Arbeitspaket lebt
weiter, damit die Messwerte einer Karte nicht mit dem Lauf verschwinden. Pakete eines noch
aufbewahrten Laufs zählen nicht mit und fallen erst mit ihm.

### Einschränkung: Sitzungen in Worktrees

Eine Sitzung in einem **frisch angelegten Git-Worktree** meldet **nicht**. `.claude/` ist in diesem
Repository unversioniert — versioniert ist allein `.claude/workflow.config.json` —, ein neuer
Worktree trägt darum weder das Kit noch eine `settings.json` mit dem Hook. Unversionierte Dateien
kopiert das Werkzeug nur, wenn die Wurzel des Repositorys eine Datei `.worktreeinclude` führt;
dieses Repository hat keine.

Solche Sitzungen bleiben damit **unerfasst**: Ihr Protokoll entsteht, ihr Verbrauch erscheint im
Leitstand nicht. Der Befund dazu — samt Prüfkommandos und dem Gegenbeispiel eines älteren
Worktrees, den eine frühere Fassung des Werkzeugs noch mitversorgt hat — steht in
[Befund: Worktrees](befund-interaktive-sitzungen.md#worktrees).

**Folge für die Zahlen:** Die Kachel „Gesamt über die Laufzeit" ist die Summe des Aufbewahrten und
Erfassten. Worktree-Sitzungen und Sitzungen ohne Token fehlen darin, ohne dass die Anzeige das sagen
könnte — sie weiß von ihnen nichts.

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

Danach laufen `mvn verify` und `mvn -Ppit -Dskip.frontend=true test` durch. Beide brauchen
**keinen** Entwicklungs-Schalter: `AbstractIntegrationTest` setzt `manban.dev-mode=true` selbst.

**`mvn spring-boot:run` braucht ihn dagegen:**

```
MANBAN_DEV_MODE=true mvn spring-boot:run
```

Ohne den Schalter bricht der Start ab — jeder Start ohne ausdrückliche Einschaltung gilt als
Produktivbetrieb, und dort ist der mitgelieferte Sitzungsschlüssel nicht zugelassen. Alternativ
einen eigenen `MANBAN_SESSION_SECRET` setzen (siehe [Produktiv betreiben](#produktiv-betreiben)).

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
