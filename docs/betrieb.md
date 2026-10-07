# Betrieb & Installation

kanban-kit läuft als ein Stack aus vier Containern (über Docker Compose):
**Caddy** (TLS + Reverse-Proxy), **manban-api** (Spring-Boot-Backend, das auch das
gebaute Frontend ausliefert), **Postgres** und **objektspeicher** (SeaweedFS, S3-kompatibler
Objektspeicher für Anhänge — siehe
[Herkunft und Aktualisierung des Objektspeichers](#herkunft-und-aktualisierung-des-objektspeichers)).

## Voraussetzungen

- **Docker Compose ab 2.23.** Der Stack legt die S3-Identität des Objektspeichers als
  `configs`-Eintrag mit `content:` an und lässt Compose die Zugangsdaten aus der `.env` darin
  einsetzen — eine eingehängte Datei würde Compose nicht einsetzen, und die Zugangsdaten stünden
  dann im Repository. `content:` gibt es erst ab dieser Fassung. Prüfen mit
  `docker compose version`; ältere Fassungen starten den Speicher ohne gültige Identität, und
  jeder Anhang-Zugriff endet in 403.

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
docker compose up -d
```

- Es wird **nichts übersetzt**: Der Basis-Stack zieht das veröffentlichte Abbild von `ghcr.io`. Wer
  seinen eigenen Arbeitsstand fahren will, nimmt das Bau-Overlay —
  siehe [Aus dem Quelltext bauen](#aus-dem-quelltext-bauen).
- `-d` startet im Hintergrund; für Live-Ausgabe `-d` weglassen.

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

Der Weg für einen echten Betrieb ist das **Betriebs-Overlay** `docker-compose.betrieb.yml`. Es
schaltet `MANBAN_DEV_MODE` fest auf `false` — ein Eintrag in der `.env` greift dort nicht — und
erzwingt die **fünf Werte**, die ein Produktivbetrieb selbst setzen muss.

Nicht zu verwechseln mit `docker-compose.prod.yml`: Das ist der Projektserver hinter dem dort
vorhandenen Traefik und verlangt ein externes Netz `web`; auf fremder Hardware ist es nicht
startbar.

**Die fünf Pflichtwerte** in der `.env` neben der `docker-compose.yml`:

| Wert | Bedeutung |
|---|---|
| `MANBAN_SESSION_SECRET` | Schlüssel zum Signieren der Sitzungs-Cookies |
| `MANBAN_BASE_URL` | öffentliche Adresse dieser Instanz für Links in E-Mails, z. B. `https://kanban.example.org` |
| `OBJEKTSPEICHER_ROOT_USER` | Benutzername des Objektspeichers, frei wählbar und **nicht** `manban` |
| `OBJEKTSPEICHER_ROOT_PASSWORD` | Geheimnis des Objektspeichers |
| `POSTGRES_PASSWORD` | Kennwort der Datenbankrolle |

Die drei Geheimnisse erzeugt man je mit `openssl rand -hex 32`; `-hex` liefert nur `0-9a-f`, damit
ist kein `$`-Escaping in der `.env` nötig. Die ausgelieferte `.env.example` führt sie bewusst als
abgelehnte Platzhalter: Niemand soll einen Wert gesetzt haben, ohne ihn gewählt zu haben.

Starten:

```
docker compose -f docker-compose.yml -f docker-compose.betrieb.yml up -d
```

Fehlt einer der fünf Werte, bricht schon `docker compose config` mit
`required variable … is missing a value` ab und nennt den Namen — die früheste Stelle, an der ein
fehlender Wert auffallen kann. Die Startprüfungen der Anwendung fangen dieselben Fälle ein zweites
Mal ab; sie müssen es, weil `:?` nur leer und ungesetzt fängt, nicht einen gesetzten Vorgabewert.

**Zwei Wege für den Reverse-Proxy.** Im Normalfall läuft der mitgelieferte Caddy mit: Er nimmt die
Host-Ports 80 und 443 und holt für `MANBAN_DOMAIN` ein Let's-Encrypt-Zertifikat. Wer schon einen
Proxy betreibt, setzt `MANBAN_PROXY_PROFIL=eigener-proxy` in die `.env` — dann bleibt Caddy unten,
80 und 443 bleiben frei, und der eigene Proxy spricht `manban-api` auf `127.0.0.1:8080` an. Er muss
dabei das TLS beenden und `X-Forwarded-Proto`, `-Host` und `-For` setzen.

Der vollständige Server-Ablauf des Projektservers (Traefik, DNS, Mail, erster Admin) steht in
[docs/deployment-hostinger.md](deployment-hostinger.md) und wird hier nicht wiederholt.

## Aus dem Quelltext bauen

Der Basis-Stack zieht veröffentlichte Abbilder und baut nichts. Wer seinen eigenen Arbeitsstand
fahren will — Entwicklung, eigene Änderungen —, gibt das **Bau-Overlay** mit; es ist der einzige
Schalter des Baus:

```
docker compose -f docker-compose.yml -f docker-compose.bau.yml up -d --build
```

`docker-compose.bau.yml` nimmt die `image:`-Zeile des Basis-Stacks mit `!reset null` zurück, statt
sie nur zu ergänzen: Ohne das trüge das hier gebaute Abbild örtlich den Tag der veröffentlichten
Fassung, und ein späterer Start ohne Overlay führe stillschweigend den Arbeitsstand weiter. `!reset`
setzt Docker Compose ab 2.24 voraus; das betrifft nur diesen Weg.

Der Sicherungsdienst hat sein eigenes Bau-Overlay `docker-compose.backup-bau.yml` — siehe
[Sicherung & Wiederherstellung](backup.md).

## Upgrade-Hinweise

Was ein Versionssprung von Hand verlangt — je Version ein Abschnitt, und die Regel, dass eine
Version ohne eigenen Abschnitt keine Handarbeit verlangt —, steht an genau einer Stelle:
[UPGRADING.md](../UPGRADING.md).

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
| `MANBAN_CLEANUP_ENABLED` | geplante Aufräum-Jobs aktivieren (Done-Archivierung, Vorhaben-Archivierung, Papierkorb-Leerung, Outbox-Aufräumung, Aufräumung der Überlast-Abweisungen nach 90 Tagen und der Idempotenz-Schlüssel nach 24 Stunden). Die Vorhaben-Archivierung läuft auch bei einer Aufbewahrung von 0 Tagen, obwohl der Automationsstatus „Done-Archivierung“ im Admin dann „aus“ zeigt — er meint nur die Karten | `true` |
| `MANBAN_DONE_RETENTION_DAYS` | Tage bis Done-Karten automatisch archiviert werden | `30` |
| `MANBAN_OUTBOX_ENABLED` | Outbox-Worker aktivieren (abgeschaltet bleiben Aufträge liegen) | `true` |
| `MANBAN_OUTBOX_POLL_INTERVAL_MS` | Abstand zwischen zwei Worker-Läufen in Millisekunden | `5000` |
| `MANBAN_OUTBOX_MAX_ATTEMPTS` | Versuche, bevor ein Auftrag als gescheitert gilt | `8` |
| `MANBAN_OUTBOX_RETENTION_DAYS` | Tage, nach denen erledigte Outbox-Einträge gelöscht werden | `7` |
| `MANBAN_BACKUP_TARGET` | Ziel der Kopie außer Haus als rclone-Pfad `<remote>:<verzeichnis>` | `nextcloud:manban-sicherung` |
| `MANBAN_BACKUP_AGE_RECIPIENT` | **öffentlicher** age-Schlüssel, gegen den außer Haus verschlüsselt wird — der private wird außerhalb des Servers verwahrt | leer |
| `MANBAN_BACKUP_RETENTION_DAYS` | Tage, nach denen Basissicherungen verfallen (die jüngste nie) | `7` |
| `MANBAN_BACKUP_MIRROR_INTERVAL` | Takt des Anhang-Spiegels als ISO-8601-Dauer; zugleich die Grenze der Rückhol-Genauigkeit | `PT5M` |
| `MANBAN_BACKUP_BASE_CRON` | Takt der Basissicherung, sechsfeldrig wie in Spring | `0 0 3 * * *` |
| `MANBAN_SESSION_SECRET` | HMAC-Secret der Session-Cookies. Der Dev-Default gilt **nur** im ausdrücklich eingeschalteten Entwicklungsbetrieb (`MANBAN_DEV_MODE=true`) — sonst verweigert die Anwendung den Start | Dev-Default |
| `MANBAN_DEV_MODE` | Entwicklungs-/Testbetrieb ausdrücklich einschalten; erlaubt den Start mit dem Standard-Sitzungsschlüssel, mit Warnung. Der lokale Compose-Stack setzt ihn auf `true`, das Produktions-Overlay fest auf `false` | `false` |
| `MANBAN_COOKIE_SECURE` | Session-Cookie nur über HTTPS | `true` |
| `MANBAN_DB_POOL_MAX` | Höchstzahl der Datenbankverbindungen der Anwendung | `20` |
| `MANBAN_DB_POOL_MIN_IDLE` | Verbindungen, die auch ohne Last offen bleiben | `5` |
| `MANBAN_DB_CONNECTION_TIMEOUT_MS` | Höchste Wartezeit auf eine freie Verbindung in Millisekunden | `5000` |
| `MANBAN_SERVER_THREADS_MAX` | Höchstzahl gleichzeitig bearbeiteter HTTP-Aufrufe | `600` |
| `MANBAN_STORAGE_BUCKET` | Name des Buckets, in dem die Anhänge liegen. Derselbe Wert speist die S3-Identität des Speichers und den Anlegeschritt beim Start — eine Änderung wirkt auf alle drei Stellen zugleich, holt aber keine Anhänge aus dem alten Bucket nach | `manban` |
| `POSTGRES_*`, `OBJEKTSPEICHER_ROOT_*` | DB- und Objektspeicher-Zugangsdaten. Bewusst ohne `MANBAN_`-Präfix: Sie gehören den Bausteinen, nicht der Anwendung. Aus den `OBJEKTSPEICHER_ROOT_*` speist der Stack zugleich die S3-Identität des Speichers und den Zugang von `manban-api` | siehe `docker-compose.yml` |

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
> ein Objekt ohne Metadaten). Verwaiste Objekte bei Bedarf gezielt entfernen:
>
> ```
> docker compose exec manban-backup rclone delete speicher:<bucket>/<key>
> ```

> **Sicherung ist ausgeliefert aus:** Die fünf `MANBAN_BACKUP_*`-Werte oben wirken erst, wenn das
> Sicherungs-Overlay zugeschaltet ist (`-f docker-compose.backup.yml`). Das ist der einzige
> Schalter; `MANBAN_BACKUP_ENABLED` setzt das Overlay selbst und wird nie von Hand gesetzt. Das
> Zuschalten startet die Datenbank **einmalig** neu (WAL-Archivierung). Vollständige Anleitung:
> [Sicherung & Wiederherstellung](backup.md).

## Herkunft und Aktualisierung des Objektspeichers

Den Objektspeicher der Anhänge liefert **SeaweedFS** — ein fremdes Projekt, das mitläuft, und
darum eines, dessen Herkunft und Pflege hier stehen muss.

| | |
|---|---|
| Projekt | SeaweedFS (`seaweedfs/seaweedfs` auf GitHub) |
| Lizenz | Apache-2.0 |
| Bezugsstelle | Docker-Abbild `chrislusf/seaweedfs`, derzeit Fassung `4.47` |
| Wer die Fassung pflegt | dieses Projekt — die Fassung ist im Repository festgeschrieben, nicht `latest` |
| Sicherheitsmeldungen | <https://github.com/seaweedfs/seaweedfs/security/advisories> und die Release-Notes unter <https://github.com/seaweedfs/seaweedfs/releases> |

**Wo die Fassung steht.** An zwei Stellen, und beide gehören zusammen: die Fundstelle, die sie
fährt (`docker-compose.yml`, Dienst `objektspeicher`, und `AbstractIntegrationTest` für die
Integrationstests), und die Bausteinliste
[`scripts/bausteine.json`](../scripts/bausteine.json), die dem Selbsthoster sagt, woher sein Stack
kommt. Wer eine Fassung anhebt, hebt sie an **beiden** Stellen an; der Pflichtcheck des Bereichs
`scripts` hält die Liste in beide Richtungen gegen den Bestand.

**Wie ein Selbsthoster eine neue Fassung bekommt.**

1. Release-Notes des Projekts lesen (Link oben) — besonders auf Änderungen an der
   S3-Identitätsdatei und am Datenverzeichnis achten.
2. Die Fassung in `docker-compose.yml` und in `scripts/bausteine.json` anheben.
3. Sicherung ziehen (siehe [Sicherung & Wiederherstellung](backup.md)); das Volume
   `objektspeicher_data` trägt die Anhänge.
4. `docker compose up -d` — der Dienst kommt mit dem neuen Abbild auf demselben Volume
   wieder hoch. Ein Formatwechsel ist damit **nicht** verbunden; ändert sich das Datenformat
   zwischen zwei Fassungen, steht das in den Release-Notes und ist dann ein eigener Vorgang.
5. Anhang hoch- und herunterladen, dann den Admin-Abgleich
   (`GET /api/admin/storage/reconciliation`) auf leere Listen prüfen.

**Ob der Bezug überhaupt noch offen ist**, prüft
[`scripts/bezugspruefung.mjs`](../scripts/bezugspruefung.mjs) anonym auf HTTP-Ebene über alle
Einträge der Bausteinliste:

```
node scripts/bezugspruefung.mjs
```

Das Skript ist bewusst kein lokaler Pflichtcheck — es hängt am Netz. Es läuft als eigener CI-Job
und wöchentlich, damit eine weggefallene Bezugsstelle als benannter Fehler auffällt und nicht
erst beim nächsten Neuaufsetzen. Genau so fiel auf, dass das alte MinIO-Abbild anonym nicht mehr
beziehbar ist.

## Automatische Aktualisierung (Renovate)

Abhängigkeiten und Abbilder bleiben von selbst aktuell: **Renovate** legt gebündelte
Aktualisierungs-PRs gegen `main` an. Übernommen wird keiner von selbst — `automerge` ist aus.
Die Konfiguration steht versioniert in [`renovate.json`](../renovate.json).

**Einmal einrichten.** Die Renovate-GitHub-App von Mend auf dem Repository installieren
(<https://github.com/apps/renovate>, Zugriff nur auf dieses Repository). Ein Secret oder Token im
Repository braucht es dafür nicht: Die App arbeitet mit ihren eigenen, kurzlebigen Rechten, und
ihre PRs lösen die CI wie jeder andere PR aus. Wer einen Fork selbst betreibt, installiert die App
auf seinem Fork; ohne App bleibt `renovate.json` wirkungslos.

**Gruppen und Takt.** Je Gruppe entsteht höchstens ein PR pro Woche, montags vor 6 Uhr
(Europe/Berlin):

| Gruppe | Umfang |
|---|---|
| `Basisabbilder` | beide Dockerfiles (`Dockerfile`, `backup/Dockerfile`), die Compose-Dateien, die Bausteinliste `scripts/bausteine.json` und die Testcontainers-Abbilder in `src/test/**/*.java` |
| `Backend` | Maven-Abhängigkeiten aus `pom.xml` |
| `Frontend` | npm-Abhängigkeiten unter `frontend/` |
| `Dokumentationsseite` | npm-Abhängigkeiten unter `docs-site/` |

Fremde Abbilder sind an ihren Digest gebunden; ein Aktualisierungs-PR hebt den Digest auch bei
unverändertem Tag an und bei einem Versionswechsel Tag und Digest gemeinsam — an jeder Fundstelle
und in der Bausteinliste in einem PR, sodass der Abgleich der Bausteinliste grün bleibt. Nicht
erfasst sind die eigenen Abbilder `ghcr.io/mannewolff/kanban-kit*` (die schreibt der Release),
das Rückweg-Overlay `docker-compose.altspeicher.yml` und die GitHub Actions.

**Einen Aktualisierungs-PR lesen.**

1. **Release-Notes:** Renovate hängt sie je Abhängigkeit an die PR-Beschreibung. Auf Brüche,
   Formatwechsel und Hinweise zur Migration achten — bei Datenbank und Objektspeicher besonders.
2. **CI-Ergebnis:** Gemergt wird nur ein PR mit grüner CI — dazu gehört der Job `bezug`,
   der die Bezugsstellen anonym prüft und die Bausteinliste gegen den Bestand hält.
3. **Dependency-Dashboard:** Ein Issue mit dem Titel „Dependency Dashboard“ listet, was gerade
   ansteht, was auf den nächsten Takt wartet und was nicht als PR offen ist. Dort lässt sich ein
   PR auch vorzeitig anstoßen.

**Der Merge ist das GO.** Wer einen grünen Aktualisierungs-PR mergt, gibt ihn frei — das ist ein
zweiter Weg nach `main` neben `push main`. Was das für die lokale Arbeit heißt (vor dem nächsten
`push main` den Stand von `origin/main` nachziehen), steht im Workflow-Guide
`.claude/CLAUDE-workflow.md` unter „Zweiter Weg nach `main`: Aktualisierungs-PRs“.

## Schutz des Branches `production`

Nach `production` kommt ein Stand nur über einen Pull Request mit grünen Prüfungen. Das sichert
ein **Ruleset** des Repositorys, zusätzlich zur Regel, dass `merge production` bei roter CI
keinen Release-PR erstellt. Ein Ruleset ist keine Datei im Repository; damit sein Zustand
nachprüfbar bleibt, steht seine Definition hier, samt den Aufrufen zum Setzen und Nachlesen.

**Die Definition.** Ziel ist `refs/heads/production`. Die Regeln: ein Pull Request ist
erforderlich (`pull_request`), die sieben Jobs aus [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)
müssen grün sein (`required_status_checks`, ohne die Pflicht, den Branch vorher auf den
neuesten Stand zu bringen), kein Force-Push (`non_fast_forward`) und kein Löschen
(`deletion`). Freigaben durch Reviewer verlangt das Ruleset nicht — es gibt nur einen Menschen
mit Schreibrechten, und der kann seinen eigenen PR nicht freigeben.

```json
{
  "name": "production schuetzen",
  "target": "branch",
  "enforcement": "active",
  "bypass_actors": [],
  "conditions": {
    "ref_name": {
      "include": ["refs/heads/production"],
      "exclude": []
    }
  },
  "rules": [
    {
      "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 0,
        "dismiss_stale_reviews_on_push": false,
        "require_code_owner_review": false,
        "require_last_push_approval": false,
        "required_review_thread_resolution": false
      }
    },
    {
      "type": "required_status_checks",
      "parameters": {
        "strict_required_status_checks_policy": false,
        "required_status_checks": [
          { "context": "Backend (mvn verify)" },
          { "context": "Backend (PIT Mutation -Ppit)" },
          { "context": "Frontend (npm build)" },
          { "context": "Bezugsquellen (anonym beziehbar)" },
          { "context": "Abbild bauen (Anwendungs-Abbild)" },
          { "context": "Abbild bauen (Sicherungs-Abbild)" },
          { "context": "Sicherheit" }
        ]
      }
    },
    { "type": "non_fast_forward" },
    { "type": "deletion" }
  ]
}
```

Die Namen sind die angezeigten Namen der Jobs (`name:` in `ci.yml`); die beiden Abbild-Jobs
entstehen aus der Matrix von `abbilder`. **Kommt in `ci.yml` ein neuer Job hinzu, gehört sein
Name in diese Liste** — und das Ruleset wird mit dem Aufruf unten aktualisiert. Ein umbenannter
Job, der hier noch unter altem Namen steht, meldet sich nie: Der PR wartet dann auf eine
Prüfung, die es nicht mehr gibt.

**Warum keine Ausnahme eingetragen ist.** `bypass_actors` bleibt leer, auch für den
Repo-Inhaber. Ein Bypass für den einzigen Menschen mit Schreibrechten hebt den Schutz
vollständig auf: Jeder Merge nach `production` liefe dann an den Prüfungen vorbei. Muss im
Notfall doch einmal an ihnen vorbei gemergt werden, wird das Ruleset bewusst auf
`"enforcement": "disabled"` gestellt und danach wieder auf `active` — sichtbar und nicht still.

**`main` bleibt ungeschützt.** Auf `main` liegt kein Ruleset, damit `push main` wie bisher
funktioniert; daneben führt der Merge eines grünen Aktualisierungs-PRs dorthin (zweiter Weg
nach `main`, siehe oben). Geschützt wird erst der Übergang nach `production`.

**Setzen.** Den JSON-Block oben in eine Datei außerhalb des Repositorys kopieren (etwa
`$TMPDIR/ruleset-production.json`) und mit Admin-Rechten anlegen:

```bash
gh api --method POST repos/mannewolff/kanban-kit/rulesets --input - < "$TMPDIR/ruleset-production.json"
```

Die Antwort enthält die `id` des neuen Rulesets. Eine spätere Änderung (etwa ein neuer Job)
geht mit derselben Datei an diese `id`:

```bash
gh api --method PUT repos/mannewolff/kanban-kit/rulesets/<id> --input - < "$TMPDIR/ruleset-production.json"
```

**Nachlesen.** Die Liste zeigt Name, `id`, `target` und `enforcement` aller Rulesets — es gibt
genau eines, `production schuetzen`, mit `"enforcement": "active"`:

```bash
gh api repos/mannewolff/kanban-kit/rulesets
```

Die Einzelansicht zeigt die Regeln:

```bash
gh api repos/mannewolff/kanban-kit/rulesets/<id>
```

Daran ist der Schutz zu erkennen:

- `"bypass_actors": []` — keine Ausnahme; `"current_user_can_bypass": "never"` bestätigt es aus
  Sicht des Aufrufers.
- `conditions.ref_name.include` enthält genau `refs/heads/production`, nicht `main`.
- Unter `rules` steht ein Eintrag mit `"type": "required_status_checks"`, dessen
  `parameters.required_status_checks` die sieben Namen oben trägt, dazu `pull_request`,
  `non_fast_forward` und `deletion`.

Kurz geprüft:

```bash
gh api repos/mannewolff/kanban-kit/rulesets/<id> --jq '{bypass: .bypass_actors, ziel: .conditions.ref_name.include, pruefungen: [.rules[] | select(.type == "required_status_checks") | .parameters.required_status_checks[].context]}'
```

## Stückliste eines Release-Abbilds

Zu jedem veröffentlichten Release-Abbild — `ghcr.io/mannewolff/kanban-kit` und
`ghcr.io/mannewolff/kanban-kit-backup` — gibt es eine Stückliste im Format CycloneDX: welche
Bestandteile in welcher Fassung darin stecken. Sie entsteht im Lauf von
[`.github/workflows/release-images.yml`](../.github/workflows/release-images.yml) zum Tag. Die
Stückliste des Anwendungs-Abbilds nennt die ausgelieferten Backend-Abhängigkeiten (Maven-Scopes
`compile` und `runtime`), die des Sicherungs-Abbilds die Pakete des gebauten Abbilds.

**Als Release-Asset.** Am GitHub-Release der Fassung hängen `stueckliste-kanban-kit.cdx.json` und
`stueckliste-kanban-kit-backup.cdx.json` — abrufbar ohne `gh`, direkt von der Release-Seite. Angehängt
werden sie von `node scripts/gh-release.mjs`; das Skript bricht ab, solange der Lauf zum Tag nicht
grün abgeschlossen ist.

**Über die Attestation.** Dieselbe Stückliste ist per GitHub Artifact Attestation an den Digest des
Abbilds gebunden. Damit lässt sich prüfen, dass sie zu genau diesem Abbild gehört und aus dem Lauf
dieses Repositorys stammt:

```bash
gh attestation verify oci://ghcr.io/mannewolff/kanban-kit:<fassung> --repo mannewolff/kanban-kit --predicate-type https://cyclonedx.org/bom
```

Für das Sicherungs-Abbild entsprechend mit `oci://ghcr.io/mannewolff/kanban-kit-backup:<fassung>`.
Mit `--format json` gibt der Befehl die Stückliste selbst aus.

## Herkunft eines Release-Abbilds prüfen

Dass ein Release-Abbild vom Projekt stammt, belegt eine Herkunfts-Attestation (Build Provenance):
Der Lauf von [`.github/workflows/release-images.yml`](../.github/workflows/release-images.yml) zum
Tag bindet sie an den Digest des Abbilds und legt sie neben dem Abbild in der Registry ab. Signiert
wird schlüssellos über die GitHub-Identität des Laufs — im Repository liegt kein Schlüsselmaterial,
und es gibt keines zu verwalten. Geprüft wird mit einem Befehl (GitHub CLI `gh`):

```bash
gh attestation verify oci://ghcr.io/mannewolff/kanban-kit:<fassung> --repo mannewolff/kanban-kit
gh attestation verify oci://ghcr.io/mannewolff/kanban-kit-backup:<fassung> --repo mannewolff/kanban-kit
```

Eine erfolgreiche Prüfung endet mit `✓ Verification succeeded!` und nennt Workflow und Tag, aus dem
das Abbild stammt, etwa für die Fassung `1.4.0`:

```text
✓ Verification succeeded!

The following 1 attestation matched the policy criteria

- Attestation #1
  - Build repo:..... mannewolff/kanban-kit
  - Build workflow:. .github/workflows/release-images.yml@refs/tags/v1.4.0
  - Signer repo:.... mannewolff/kanban-kit
  - Signer workflow: .github/workflows/release-images.yml@refs/tags/v1.4.0
```

Stammt das Abbild aus einem anderen Repository oder Workflow, oder fehlt die Attestation, endet der
Befehl mit einem Fehler und einem Exitcode ungleich null.

**Warum die eigenen Abbilder ohne Digest stehen.** In `docker-compose.yml` und
`docker-compose.backup.yml` stehen `ghcr.io/mannewolff/kanban-kit` und
`ghcr.io/mannewolff/kanban-kit-backup` bewusst nur mit Tag, die fremden Betriebsabbilder dagegen mit
Digest. Der Digest der eigenen Abbilder entsteht erst im Release-Lauf auf dem Tag, der Commit dieses
Tags kann ihn also nicht tragen. Ihre Herkunft sichert stattdessen die Attestation oben.

## Sicherheitsprüfung

Jeder CI-Lauf prüft im Job „Sicherheit“ ([`.github/workflows/ci.yml`](../.github/workflows/ci.yml))
mit Trivy, was kanban-kit ausliefert, auf bekannte Schwachstellen und eingecheckte Geheimnisse. Das
Urteil fällt [`scripts/sicherheitspruefung.mjs`](../scripts/sicherheitspruefung.mjs); die
Ergebnisliste steht in der Zusammenfassung des Jobs „Sicherheit“ auf der Seite des CI-Laufs.

**Was geprüft wird.**

- die eigenen Abbilder (Anwendung und Sicherungsdienst), wie sie im CI-Lauf entstehen;
- die Stückliste der Anwendung (`target/bom.json`);
- die fremden Bausteine aus `bausteine.json` — Datenbank, Objektspeicher, Webserver und die
  Bauwerkzeuge — in der Fassung, an die das Projekt gebunden ist;
- die Sperrdateien `frontend/package-lock.json` und `docs-site/package-lock.json`;
- den Arbeitsbaum auf eingecheckte Geheimnisse.

Als schwer gelten die Schweregrade `CRITICAL` und `HIGH`. Eine schwere Lücke **ohne** Korrektur
sperrt nie, steht aber sichtbar in der Liste. Jede Befundzeile nennt den Bestandteil, in dem die
Lücke steckt (etwa das Betriebssystem-Paket oder das Programm samt Pfad), damit gleiche Befunde aus
verschiedenen Programmen unterscheidbar sind. Ein Ziel, das nicht geprüft werden konnte (Frist
überschritten, Werkzeug fehlt, Ausgabe unlesbar), sperrt als „Ziel nicht geprüft“.

**Die drei Regeln für eine schwere Lücke mit Korrektur.**

1. **Eigen sperrt sofort.** Steckt die Lücke in einem Bestandteil, den kanban-kit selbst
   einbringt, sperrt sie die Veröffentlichung, bis sie behoben oder als Ausnahme eingetragen ist.
2. **Übernommen sperrt nach 14 Tagen.** Steckt die Lücke in einem Baustein eines anderen Anbieters,
   prüft der Lauf zusätzlich die aktuelle Anbieter-Fassung: den neuesten Tag derselben Linie
   (gleiche Hauptversion, gleiche Variante wie `-alpine` oder `-bookworm`; der gebundene Tag zählt
   mit). Enthält diese Fassung die Lücke noch, sperrt der Befund nicht — Grund in der Liste:
   „Anbieter hat noch keine korrigierte Fassung“. Fehlt die Lücke dort, beginnt mit dem
   Erstellungsdatum dieser Fassung eine Frist von **14 Tagen**: Innerhalb der Frist steht der Befund
   mit „korrigierte Fassung seit `<Datum>`, Frist bis `<Datum>`“ in der Liste, danach sperrt er und
   nennt Anbieter-Fassung, Datum und abgelaufene Frist. Dann ist es Zeit, die Bindung in
   `bausteine.json` (bzw. Renovate) nachzuziehen. Ist das Erstellungsdatum unbekannt oder ein
   Platzhalter vor 2000-01-01, sperrt der Befund mit „Erscheinungstag der Anbieter-Fassung unbekannt“.
3. **Bauwerkzeuge informieren nur.** Bausteine mit `"verwendung": "bau"` in `bausteine.json`
   (Bau: Frontend, Bau: Backend, Sicherung: Werkzeugstufe) stecken nicht in der ausgelieferten
   Fassung. Ihre Befunde stehen im eigenen Abschnitt „Bauwerkzeuge (informiert, sperrt nicht)“ und
   sperren nie.

**Eigen oder übernommen — je Bestandteil.** Ein fremder Baustein ist als Ganzes übernommen. In
einem eigenen Abbild entscheidet der Bestandteil: Ein eigenes Abbild nennt in `bausteine.json` per
Feld `basis` den Baustein, auf dem es aufsetzt (etwa die Java-Laufzeit oder die Datenbank). Trägt
dieser Basis-Baustein denselben Befund mit gleicher Kennung, gleichem Paket und **gleicher
Fassung**, gilt der Befund als übernommen und folgt Regel 2 mit der Anbieter-Fassung des
Basis-Bausteins. Alles andere — etwa Bibliotheken der Anwendung oder Werkzeuge, die der
Sicherungsdienst selbst herunterlädt — ist eigen und folgt Regel 1.

**Bekannte Unschärfe.** Baut der Anbieter denselben Tag nach der Korrektur erneut, rückt das
Erstellungsdatum nach, und die 14-Tage-Frist beginnt von Neuem. Die Prüfung merkt sich keine
früheren Erstellungsdaten; weil Renovate die Digests wöchentlich nachzieht, bleibt die Lücke klein.

**Ausnahmeliste.** Was vorerst hingenommen wird, steht in
[`scripts/sicherheitsausnahmen.json`](../scripts/sicherheitsausnahmen.json):

```json
{ "kennung": "CVE-…", "ziel": "postgres:16.15", "begruendung": "…", "ablauf": "JJJJ-MM-TT" }
```

Alle vier Felder sind Pflicht; `ziel` ist das Prüfziel (Abbild, Stückliste oder Sperrdatei). Die
Ausnahme gilt einschließlich des Ablauftags, danach sperrt der nächste Lauf wieder. Ein Geheimnis
darf nur als nachweislicher Fehlalarm ausgenommen werden — mit `"art": "fehlalarm"`, Trivys
RuleID als `kennung` und dem Dateipfad als `ziel`, ohne `ablauf`. Ein Eintrag mit Formfehler gilt
nicht und sperrt selbst; ein Eintrag ohne passenden Befund erscheint als Hinweis in der
Zusammenfassung.

**Übergang bis 2026-11-01.** Für die Befunde aus CI-Lauf #281 in fremden Abbildern, Bau-Abbildern
und den übernommenen Bestandteilen der eigenen Abbilder stehen befristete Einträge in der
Ausnahmeliste (Issue #1352), alle mit `ablauf` 2026-11-01. Ab dem 2026-11-02 urteilt die Prüfung
für diese Befunde wieder allein nach den Regeln oben.

## Umstellung des Objektspeichers

Der Speicher der Anhänge wechselt von MinIO auf SeaweedFS (Plan #1222). Für die laufende Instanz
ändert sich an den Anhängen nichts: Nach dem Update sind es dieselben, mit derselben Vorschau —
den Dateityp für die Vorschau liest die Anwendung ohnehin aus ihrer Datenbank, nicht aus dem
Speicher. Dazwischen liegt genau **ein** Umzug, und der läuft in **zwei Releases**.

**Warum zwei Releases und nicht ein Handgriff im Fenster:** `.github/workflows/deploy.yml` fährt
bei jedem Push auf `production` selbsttätig `git reset --hard origin/production` und danach:

```
docker compose -f docker-compose.yml -f docker-compose.bau.yml -f docker-compose.prod.yml \
               -f docker-compose.backup.yml -f docker-compose.backup-bau.yml up -d --build
```

Fünf `-f`, und die beiden Umzugs-Overlays sind nicht dabei: **Jeder** Deploy schaltet
die Anwendung damit auf den neuen Speicher. Der Umstieg **ist** der Deploy — nicht etwas, das man im
Fenster von Hand auslöst, und auch nichts, das man ihm nehmen kann, ohne den Prozess für einen
einmaligen Vorgang umzubauen.

Daraus folgen die beiden Releases:

- **Release 1** ist der Merge, der diesen Stand auf den Server bringt. Direkt danach schaltet ein
  Aufruf von Hand Anwendung und Spiegel mit den beiden Zusatz-Overlays auf die **alte** Adresse
  zurück — und dort läuft die Vorkopie im laufenden Betrieb.
- **Release 2** ist der **nächste** Deploy. Sein Inhalt ist beliebig (es genügt der nächste Merge auf
  `production`): Weil er ohne die Zusatz-Overlays fährt, schaltet er `manban-api` und
  `manban-backup` um. Genau dieser Deploy ist der Umstieg, und das Fenster liegt um ihn herum.

Werkzeug ist `docker-compose.umzug.yml`: ein Einmal-Dienst `manban-umzug`, der mit dem `rclone` des
Sicherungs-Abbilds kopiert und danach byteweise prüft. Er hält zugleich Anwendung und Anhang-Spiegel
bei der **alten** Adresse — nur so lässt sich die Hauptmenge im laufenden Betrieb kopieren, statt
das Wartungsfenster so lang zu machen wie die Kopie.

### Vorher: die `.env` umstellen

Die Variablennamen des Speichers haben sich geändert. **Ein alter Name in der `.env` wirkt nicht
mehr**: Compose reicht ihn durch, die Anwendung liest ihn nicht — sie fiele still auf ihren
Standardwert zurück, statt zu scheitern. Umbenennen — die Werte der Anwendung behalten, die des
Speicherdienstes selbst **nicht**:

| bisher | ab dieser Version | was damit gemeint ist |
|---|---|---|
| `MANBAN_MINIO_ENDPOINT` | `MANBAN_STORAGE_ENDPOINT` | Adresse des Speichers, die die Anwendung anspricht |
| `MANBAN_MINIO_ACCESS_KEY` | `MANBAN_STORAGE_ACCESS_KEY` | Zugangskennung der Anwendung am Speicher |
| `MANBAN_MINIO_SECRET_KEY` | `MANBAN_STORAGE_SECRET_KEY` | Geheimnis der Anwendung am Speicher |
| `MANBAN_MINIO_BUCKET` | `MANBAN_STORAGE_BUCKET` | Bucket, in dem die Anhänge liegen |
| `MINIO_ROOT_USER` | `OBJEKTSPEICHER_ROOT_USER` | Kennung des Speicherdienstes selbst — **eigene, neue Werte**; `manban` wird abgelehnt |
| `MINIO_ROOT_PASSWORD` | `OBJEKTSPEICHER_ROOT_PASSWORD` | Geheimnis des Speicherdienstes selbst — **eigene, neue Werte** |

`OBJEKTSPEICHER_ROOT_USER` und `OBJEKTSPEICHER_ROOT_PASSWORD` **müssen** gesetzt sein, sonst
scheitert schon der Compose-Aufruf des Deploys (`:?` in `docker-compose.prod.yml`, Issue #1227).

**Bei diesen beiden Zeilen die bisherigen Werte nicht übernehmen.** `ObjectStorageStartupCheck`
lehnt öffentlich bekannte Standardwerte ab (Issue #1227) — darunter den Benutzernamen `manban`, den
der bisherige Stack als Vorgabe für `MINIO_ROOT_USER` fuhr. Wer ihn mitnimmt, bekommt beim Start
„Start abgebrochen: … Zugangsschlüssel (MANBAN_STORAGE_ACCESS_KEY)“, und die Anwendung kommt nicht
hoch. Genau das ist bei v2.12.0 in Produktion passiert (Issue #1243). Also beide Werte neu setzen,
etwa Benutzer `kanban-speicher` und Geheimnis aus `openssl rand -hex 32`.

**Nach jeder Änderung von `OBJEKTSPEICHER_ROOT_*` wird der Speicherdienst neu angelegt:**

```
docker compose ... up -d --force-recreate --no-deps objektspeicher
```

`objektspeicher` liest seine Identitäten aus der Compose-`config` `objektspeicher_identitaeten`.
Eine geänderte `config` erkennt `docker compose up -d` **nicht** als Änderung: Der Container behält
die alten Zugangsdaten, die Anwendung schickt die neuen, und jeder Upload scheitert mit
`InvalidAccessKeyId` — obwohl die Seite läuft. `--no-deps` hält die übrigen Dienste dabei in Ruhe.

Dazu kommen drei Werte, die **nur für den Umzug** gelten und danach wieder aus der `.env`
verschwinden — sie beschreiben den **alten** Speicher, aus dem gelesen wird:

| Variable | Bedeutung | Vorgabe |
|---|---|---|
| `UMZUG_ALT_ENDPOINT` | Adresse des alten Speichers im Netz des Stacks | `http://minio:9000` |
| `UMZUG_ALT_ACCESS_KEY` | die Kennung, mit der der alte Speicher heute läuft (bisher `MINIO_ROOT_USER`) | keine |
| `UMZUG_ALT_SECRET_KEY` | das zugehörige Geheimnis (bisher `MINIO_ROOT_PASSWORD`) | keine |

Getrennte Werte, nicht dieselben wie für den neuen Speicher: Wer beim Wechsel starke, neue
Zugangsdaten setzt — und das ist der empfohlene Weg —, braucht die alten weiterhin zum Lesen.

**`UMZUG_ALT_ACCESS_KEY` und `UMZUG_ALT_SECRET_KEY` starten zugleich den alten Speicher.**
`docker-compose.altspeicher.yml` setzt mit ihnen dessen Zugangsdaten (Issue #1233) — `MINIO_ROOT_*`
liest nach der Umbenennung niemand mehr. Die beiden Werte müssen darum **genau** die bisherigen
`MINIO_ROOT_USER` und `MINIO_ROOT_PASSWORD` sein. Ein falscher Wert startet den alten Speicher mit
Zugangsdaten, zu denen seine Daten nicht passen: Die Vorkopie und der Rückweg lesen dann nichts, bis
der Wert korrigiert und der Dienst neu gestartet ist. Die Anhänge im Volume `minio_data` bleiben
dabei unberührt.

Vor Release 1 prüfen, dass nichts leer durchgeht:

```
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
               -f docker-compose.backup.yml -f docker-compose.altspeicher.yml \
               -f docker-compose.umzug.yml config -q
```

Das Kommando darf **nichts ausgeben**. Jede Zeile ist ein Fund: `required variable … is missing a
value` nennt einen Pflichtwert, der fehlt — darunter `UMZUG_ALT_*` und `OBJEKTSPEICHER_ROOT_*` —,
`… is not set` einen Wert, der still auf seinen Standard fällt. Nur die zweite Sorte zu zählen
reicht nicht: Das ergibt auch bei einer leeren `.env` `0`, weil die Pflichtwerte mit `:?` stehen
und als eigener Fehler abbrechen.

Die Probe prüft nur, ob Werte **gesetzt** sind — nicht, ob sie **erlaubt** sind (Issue #1243). Auf
dem Server war sie grün, während `OBJEKTSPEICHER_ROOT_USER=manban` den Start sicher scheitern ließ.
Darum eine zweite Zeile, die die Werte gegen die abgelehnten Standards hält:

```
grep -E '^OBJEKTSPEICHER_ROOT_USER=manban$' .env
```

Auch dieses Kommando darf **nichts ausgeben**. Ein Treffer heißt: Die Anwendung kommt nach dem
Deploy nicht hoch. Dasselbe gilt für den Platzhalter `change-me-benutzer` aus `.env.example`.

Und: **nie `--remove-orphans`**, solange der alte MinIO der Rückweg ist. Für einen Aufruf ohne
`-f docker-compose.altspeicher.yml` ist `kanban-kit-minio-1` ein verwaister Container — das Flag
räumte ihn samt Rückweg weg.

### Welcher Weg gilt: zwei Releases oder direkt

Vor Release 1 eine Frage klären:

```
grep -E '^MINIO_ROOT_USER=' .env
```

- **Steht dort ein eigener Benutzername**, gilt der Zwei-Release-Weg unten. Er hat die kürzere
  Lücke, weil die Anwendung bis zum Umstieg weiter den alten Speicher liest.
- **Steht dort `MINIO_ROOT_USER=manban`** (der bisherige Standard), **geht der Zwei-Release-Weg
  nicht.** Release 1
  schaltet die Anwendung mit `docker-compose.umzug.yml` auf den alten Speicher zurück, und zwar mit
  `UMZUG_ALT_ACCESS_KEY` als Zugangsschlüssel — ist das `manban`, bricht derselbe Startcheck ab. Dann
  gilt der **direkte Weg**.

#### Der direkte Weg — Anwendung sofort auf den neuen Speicher

So ist der Umzug auf kanban.mwolff.org gelaufen (v2.12.0, 2026-09-27). Die Lücke ist die Kopierzeit;
bei kleinem Bestand sind das Minuten.

1. **Ankündigen.** Anhänge sind zwischen Schritt 2 und 4 nicht abrufbar, alles andere läuft.
2. **`.env` setzen:** `OBJEKTSPEICHER_ROOT_USER`/`_PASSWORD` auf eigene, neue Werte (nicht `manban`),
   `UMZUG_ALT_*` auf die bisherigen `MINIO_ROOT_*`. Dann die beiden Proben oben fahren.
3. **Stack mit drei `-f` neu starten** — die Anwendung geht damit sofort auf den neuen Speicher —,
   und `objektspeicher` dabei neu anlegen, damit er die neuen Zugangsdaten übernimmt:

   ```
   cd /root/opt/kanban-kit
   docker compose -f docker-compose.yml -f docker-compose.bau.yml -f docker-compose.prod.yml \
                  -f docker-compose.backup.yml -f docker-compose.backup-bau.yml up -d --build
   docker compose -f docker-compose.yml -f docker-compose.prod.yml \
                  -f docker-compose.backup.yml up -d --force-recreate --no-deps objektspeicher
   ```

4. **Kopie fahren**, mit den beiden Zusatz-Overlays, aber **nur** dem Einmal-Dienst — `--no-deps`
   lässt Anwendung und Spiegel dort, wo Schritt 3 sie hingestellt hat:

   ```
   docker compose -f docker-compose.yml -f docker-compose.prod.yml \
                  -f docker-compose.backup.yml -f docker-compose.altspeicher.yml \
                  -f docker-compose.umzug.yml \
                  up --no-deps --exit-code-from manban-umzug manban-umzug
   ```

   Exitcode `0` heißt: Jedes Objekt der Quelle liegt byteweise gleich im Ziel.
5. **Abgleich** über `GET /api/admin/storage/reconciliation` (siehe unten), **Sichtprüfung** eines
   Anhangs im Browser, **Sicherungskachel** auf `OK` prüfen.
6. Die Umzugs-Zeilen `UMZUG_ALT_*` bleiben in der `.env`, solange der Rückweg gilt (bis 2.13.0).

Wer den direkten Weg gegangen ist, überspringt die beiden folgenden Abschnitte.

### Release 1 — Speicher und Werkzeug auf den Server, Anwendung bleibt alt

1. **Ankündigen.** Release 1 hat eine **kurze** Lücke: Zwischen dem Ende des Deploy-Jobs und dem
   Nachfahren in Schritt 3 spricht die Anwendung den neuen, noch leeren Speicher an — Anhänge sind
   in dieser Zeit nicht abrufbar, alles andere läuft. Bei wem der Nachzug bereitliegt, sind das
   unter zwei Minuten. Wer das nicht will, kündigt für Release 1 dasselbe Fenster an wie für
   Release 2.
2. **Merge auf `production`.** Der Deploy zieht den Stand, baut und startet. Danach laufen der neue
   Speicherdienst `objektspeicher` (leer) und der alte `minio` (mit allen Anhängen) nebeneinander —
   den alten lässt der Deploy unberührt, weil er ihn ohne `-f docker-compose.altspeicher.yml` gar
   nicht kennt und verwaiste Container nicht anfasst.
3. **Direkt danach von Hand nachfahren**, mit beiden Overlays und in genau dieser Reihenfolge:

   ```
   cd /root/opt/kanban-kit
   docker compose -f docker-compose.yml -f docker-compose.prod.yml \
                  -f docker-compose.backup.yml -f docker-compose.altspeicher.yml \
                  -f docker-compose.umzug.yml up -d
   ```

   Damit lesen Anwendung und Anhang-Spiegel wieder den alten Speicher, und die **Vorkopie** läuft
   an: `manban-umzug` startet mit, kopiert und prüft. Reihenfolge zählt — `docker-compose.umzug.yml`
   steht zuletzt, weil es die Adresse überschreibt, die das Produktions-Overlay setzt.
4. **Ergebnis der Vorkopie ansehen** — und die **Dauer messen**, sie ist der Maßstab für das
   Fenster:

   ```
   docker compose ... logs manban-umzug
   docker compose ... ps -a manban-umzug
   ```

   Die Spalte `STATUS` zeigt den Exitcode: `Exited (0)` heißt, dass jedes Objekt der Quelle
   byteweise gleich im Ziel liegt. Alles andere hält den Umzug an — der Grund steht im Protokoll.
   Der Dienst meldet dabei auch dann einen Fehlschlag, wenn die **Quelle leer** ist: Ein Umzug, der
   nichts umgezogen hat, soll nicht wie ein gelungener aussehen. Die Vorkopie ist beliebig oft
   wiederholbar, nichts geht dabei verloren.
5. Der Stand aus Schritt 3 bleibt so, bis Release 2 kommt. Neue Anhänge landen weiter im alten
   Speicher und werden weiter gespiegelt; die Restkopie holt sie im Fenster nach.

### Release 2 — der Umstieg, Fenster ≤ 30 Minuten

Das Fenster ist die Summe aus Deploy, Restkopie und Prüfung. Die Vorkopie aus Release 1 hat die
Kopierzeit für den Gesamtbestand gemessen; die Restkopie betrifft nur, was seither hinzukam, die
Prüfung dagegen wieder den ganzen Bestand. Passt diese Summe nicht in **30 Minuten**, wird zuerst
die Vorkopie unmittelbar vor dem Fenster noch einmal gefahren (sie ist beliebig oft wiederholbar)
und das Fenster danach angesetzt.

1. **Ankündigen und freigeben.** Fenster von höchstens 30 Minuten, in dem Anhänge kurzzeitig nicht
   abrufbar sind.
2. **Merge auf `production`** — der nächste, inhaltlich beliebig. Der Deploy fährt mit seinen drei
   `-f` und damit ohne die beiden Zusatz-Overlays: **Dieser Deploy ist der Umstieg.** `manban-api`
   und `manban-backup` sprechen ab jetzt den neuen Speicher an, ohne dass jemand etwas umstellt.
3. **Restkopie und Prüfung**, unmittelbar nachdem der Schritt „Auf Bereitschaft der Anwendung
   warten" des Deploy-Jobs grün ist:

   ```
   cd /root/opt/kanban-kit
   docker compose -f docker-compose.yml -f docker-compose.prod.yml \
                  -f docker-compose.backup.yml -f docker-compose.altspeicher.yml \
                  -f docker-compose.umzug.yml run --rm manban-umzug
   ```

   `run --rm` legt den Exitcode direkt in die Shell. Dieser Aufruf schaltet Anwendung und Spiegel
   **nicht** zurück: `run` startet nur den einen Dienst, die anderen bleiben, wie der Deploy sie
   hinterlassen hat.
4. **Abgleich fahren** (siehe unten). Erst er beendet das Fenster.
5. Die Umzugs-Zeilen `UMZUG_ALT_*` aus der `.env` entfernen.

### Vollständigkeitsnachweis

Als Plattform-Admin:

```
GET /api/admin/storage/reconciliation   → { "orphanedObjects": [...], "missingObjects": [...] }
```

**`missingObjects` muss leer sein.** Jeder Eintrag darin ist ein Anhang, den die Datenbank kennt und
der im Speicher fehlt — dann gilt der Rückweg. **`orphanedObjects` darf gefüllt sein und bleibt
erlaubt:** Ein Anhang, der zwischen Vorkopie und Umstieg gelöscht wurde, liegt im neuen Speicher
noch als Objekt, weil der Umzug mit `rclone copy` arbeitet und auf der Zielseite nie löscht. Das ist
Absicht — ein `sync` hätte stattdessen das Neue weggeräumt.

### Rückweg

Meldet der Abgleich fehlende Objekte oder geht im Fenster etwas anderes schief, führt der Rückweg
zurück auf den alten Speicher. Er hat **zwei Stufen**, und beide sind nötig — die erste wirkt
sofort, die zweite hält:

1. **Sofort: die beiden Zusatz-Overlays wieder zuschalten.** Derselbe Aufruf wie in Release 1,
   Schritt 3:

   ```
   cd /root/opt/kanban-kit
   docker compose -f docker-compose.yml -f docker-compose.prod.yml \
                  -f docker-compose.backup.yml -f docker-compose.altspeicher.yml \
                  -f docker-compose.umzug.yml up -d
   ```

   Anwendung und Spiegel lesen damit wieder den alten Speicher. Dessen Volume `minio_data` ist
   unangetastet — der vorige Stand der Anhänge liegt vollständig darin, es wurde nur daraus
   gelesen. Das Fenster ist damit beendet; die Anhänge sind wieder da.

2. **Dauerhaft: Revert auf `production`.** Stufe 1 hält **nicht** über den nächsten Deploy: Der
   fährt wieder mit seinen drei `-f` und schaltete erneut um — aus demselben Grund, aus dem der
   Umstieg überhaupt der Deploy ist. Darum wird die Umstellung auf `production` revertet und
   gepusht; der nächste Deploy stellt den vorigen Stand dann von selbst her. Bis dahin **keinen**
   weiteren Deploy auslösen.

`docker-compose.altspeicher.yml`, `docker-compose.umzug.yml` und das Volume `minio_data` bleiben
dafür **eine Version lang unangetastet** (bis 2.13.0). Erst danach werden sie zusammen entfernt —
und damit ist der Rückweg zu. Vorher gehört der Abgleich oben grün gesehen.

### Ersatzweg, wenn das alte Abbild fehlt

`docker-compose.altspeicher.yml` fährt ein Abbild, das **anonym nicht mehr beziehbar** ist. Es läuft
nur auf einem Server, auf dem es noch im lokalen Vorrat liegt:

```
docker image ls | grep minio
```

Kommt dort keine Zeile, sind Umzugsquelle und Rückweg über den alten Dienst beide zu — dann kommen
die Anhänge aus dem **jüngsten Spiegel beziehungsweise der Sicherung** in den neuen Speicher, über
denselben Pfad, den `restore.sh` fährt (siehe [Sicherung & Wiederherstellung](backup.md)). Eine
Umschlüsselung oder ein Übersetzungsschritt ist dabei nicht nötig: Der Spiegel ist derselbe
Dateibaum. Der Nachweis ist danach derselbe — `missingObjects` leer.

## Sicherung & Wiederherstellung

Einrichtung, Schlüsselverwahrung, die beiden Rückholwege und das Verfallen alter Stände stehen in
[Sicherung & Wiederherstellung](backup.md). Der eine Satz, der dort nicht zu überlesen ist, gilt
auch hier: Der Server hält **nur den öffentlichen** Schlüssel — **ohne den außerhalb verwahrten
privaten Schlüssel ist aus den Sicherungen nichts zu holen.**

## Letzter Wiederherstellungsnachweis

Eine Sicherung, die nie zurückgeholt wurde, ist keine. Hier steht, wann zuletzt eine vollständige
Rückholung auf eine leere Maschine durchgeführt wurde — mit Datum, Commit und Ausgang. Der Eintrag
wird nach jeder Probe von Hand nachgetragen, insbesondere vor jedem Release, das die Sicherung
berührt.

| Datum | Commit | Weg | Ergebnis |
|---|---|---|---|
| — | — | — | **Noch keine Probe durchgeführt.** |

Der Nachweis bleibt bewusst ein Abschnitt dieser Seite und bekommt **keine eigene Datei**. In den
Eintrag gehören nur die vier Spalten oben — **keine Hostnamen, Pfade oder Zugangswege der eigenen
Instanz**: Alles unter `docs/` wird auf die öffentliche Doku-Site kopiert
(`docs-site/copy-docs.mjs`).

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

1. `MANBAN_BOOTSTRAP_ADMIN_TOKEN=DEIN_TOKEN` in der `.env` setzen und die Container **neu anlegen**
   (`docker compose up -d`) — Compose liest die `.env` nur beim Anlegen, ein `restart` genügt nicht.
2. Normal **registrieren** und **einloggen** (E-Mail vorher bestätigen, s. o.).
3. Eingeloggt **`https://localhost/admin/bootstrap`** öffnen, den Token eingeben → „Admin werden".

**Wichtig:** zuerst einloggen, *dann* `/admin/bootstrap` — der Bootstrap stuft den *gerade
eingeloggten* Nutzer hoch. Ohne Login leitet die Seite auf `/login`. Bei falschem Token → 403,
wenn schon ein Admin existiert → 409. Token danach aus der `.env` entfernen.

### Weg B — direkt in der Datenbank (Notweg)

Nur nehmen, wenn Weg A nicht in Frage kommt — etwa weil die Container nicht neu angelegt
werden können. Der Weg
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
