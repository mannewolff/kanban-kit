# Gesamt-Review kanban-kit — 9. September 2026

## Ergebnis und Lesereihenfolge

**25 priorisierte Befunde: 7 × P1 und 18 × P2**, gegliedert in Architektur/Betrieb, Backend und Frontend. Daneben stehen drei niedriger priorisierte bzw. bedingte Hinweise und drei noch zu verifizierende Kandidaten. Bekannte offene Themen werden gesondert genannt und nicht erneut als neue Befunde gezählt.

Die stärksten Probleme betreffen die Reichweite und den Widerruf von API-Tokens, Session-Sicherheit, den Admin-Aussperrschutz sowie inkonsistente Karten-Lebenszyklen. Im Frontend können aufeinanderfolgende Bearbeitungsschritte bestätigte Änderungen überschreiben; verspätete Antworten können Daten eines anderen Boards oder Projekts anzeigen. Im Betrieb erreicht der dokumentierte Abschaltschalter für endgültige Löschungen den Container nicht.

**Die bestehenden Qualitätsprüfungen sind grün.** Die zusätzlichen Review-Diagnosen zeigen trotzdem reproduzierbare Fehler. Die vorhandenen Tests prüfen viele einzelne Operationen gründlich; Lücken liegen vor allem in deren Kombination, Nebenläufigkeit, Kontextwechseln und dem Weg von Betriebsvariablen in die laufende Anwendung.

Dieser Bericht dokumentiert den Bestand. Er ist noch kein freigegebener Implementierungsplan. Es wurden keine Produktquellen geändert, keine Issues angelegt oder kommentiert, keine Commits erstellt und nichts gepusht.

### Bewertungsmaßstab

- **P1 — zeitnah beheben:** Sicherheitsgrenzen, Wiederherstellung des Zugangs, unerwartete endgültige Löschung oder unmittelbar reproduzierbarer Verlust gespeicherter Änderungen. Voraussetzungen stehen beim jeweiligen Befund; P1 bedeutet nicht, dass jede Installation bereits betroffen ist.
- **P2 — regulär priorisieren:** konkrete funktionale Fehler, inkonsistente Daten, nicht verlässlicher Betrieb oder unzugängliche Kernfunktionen.
- **P3 / Hinweise:** begrenzte Auswirkungen, Dokumentationsabweichungen oder Härtung unter zusätzlichen Voraussetzungen.
- **Reproduziert:** zusätzlicher Test oder isolierter Ablauf zeigt den konkreten Fehler. **Statisch bestätigt:** vollständiger relevanter Aufruf-/Datenpfad und Gegenprüfungen; keine Behauptung einer ausgeführten Benutzeraktion.

## Basis, Umfang und Grenzen

| Merkmal | Review-Basis |
|---|---|
| Repository | kanban-kit, lokaler Checkout |
| Commit | `aa1610afd33f9f0ae4ad51cf9528030bd33d2240` |
| Lokaler Remote-Stand `origin/main` | `0981f7648fb966dea28500fea38fdf81ec9d4c24` — nicht neu gefetcht |
| Version | 1.39.0; zusätzlich ein lokaler Commit für #785 |
| Arbeitsbaum zu Beginn | sauber |
| Verfahren | drei unabhängige Bereichsreviews mit frischem Kontext, anschließend Abgleich und zusätzliche Diagnosen durch den Hauptreviewer |
| Regeln | AGENTS.md, vorhandene CLAUDE-Guides einschließlich Workflow/Security/Design sowie Projekt-Memory |

Der Review-Skill sieht einen unabhängigen Opus-Reviewer vor. Dieses Modell war hier nicht verfügbar; die drei Bereichsreviews wurden mit dem verfügbaren Modell durchgeführt. Damit liegt eine unabhängige Sichtung, aber keine Prüfung durch verschiedene Modellfamilien vor.

Inventar des getrackten Bestands, als Größenordnung und Prüflandkarte — keine Behauptung, jede Testzeile oder jedes generierte Byte einzeln geprüft zu haben:

| Bereich | Dateien | Zeilen |
|---|---:|---:|
| Java-Produktquellen | 371 | 19.714 |
| Java-Tests | 211 | 39.082 |
| Flyway-Migrationen | 29 | 882 |
| Frontend TS/TSX ohne Tests | 109 | 16.464 |
| Frontend-Testdateien | 106 | 23.550 |
| CLI/Skripte/Hooks, ausführbare JS/MJS/SH | 9 | 2.141 |
| GitHub-Workflows | 3 | 166 |

Alle Fachmodule, Frontend-Seiten und zentralen Komponenten, API-Verträge, Migrationen, Composition Root, Build-/CI-/Betriebsdefinitionen sowie die getrackten CLI- und Projektskripte wurden risikoorientiert gesichtet. Tests wurden zusätzlich auf die relevanten Szenarien und Assertions geprüft. Reine Getter, generiertes Wiring und sämtliche Testassertions wurden nicht nochmals Zeile für Zeile unabhängig bewiesen.

**Explizit nicht vollständig auditiert:** Die im Installer eingebettete Implementierung des eigenständigen Workflow-Kits mit rund 8.800 lokal ausgeschriebenen Zeilen. Installer-Integration und Hooks wurden geprüft; sechs eingebettete Code-/Hook-Blobs wurden mit der installierten Fassung bytegenau abgeglichen. Build-Artefakte, Bibliotheksquellen, Bilder und Präsentationsmaterial waren keine Produktcode-Reviewziele. Keine Prüfung der Live-Produktion, kein Host-Neustart, Lasttest, Dependency-/Container-CVE-Audit oder Browser-End-to-End-Lauf. Die tatsächliche Konfiguration des Produktionsservers wurde nicht eingesehen.

## Ausgeführte Prüfungen

| Prüfung | Ergebnis dieses Laufs |
|---|---|
| `mvn verify` mit JDK 25 und lokalem Testcontainers-Docker | **grün:** 1.226 Unit-Tests, 403 Integrationstests, keine Fehler/Skips; statische Gates erfolgreich |
| JaCoCo im konfigurierten Messbereich | **100 % Lines und Branches:** 3.177 Zeilen und 806 Zweige abgedeckt |
| `mvn -Ppit -Dskip.frontend=true test` | **grün:** 1.341 von 1.341 Mutanten erkannt; Test Strength 100 % |
| `npm run build` | **grün** |
| `npm run lint` | **grün** |
| `npm run test:coverage` | **grün:** 1.676 Tests in 106 Dateien, 100 % Statements/Branches/Functions/Lines im konfigurierten Messbereich |
| `node --test cli/tbx.test.mjs` | **grün:** 48 Tests |
| Zusätzliche temporäre Backend-Diagnosen | **10 Fehlerabläufe reproduziert**, siehe Nachweistabelle |
| Zusätzliche isolierte React-Testing-Library-Diagnosen | **5 Fehlerabläufe reproduziert**, gegen unveränderte Originalquellen |
| Compose-/Hook-/Sonar-Diagnosen | fehlende Variablen, ungeprüfte Löschung und fehlerhaftes Glob-Matching bestätigt |

Die Coverage-Ausschlüsse des Projekts gelten weiterhin. PIT meldet für seine eigene Auswahl 2.114/2.118 Zeilen (gerundet 99 %); das ist eine andere Messung als JaCoCo und ändert den erfolgreichen 100-%-Mutationswert nicht. Bestehende XML-Berichte enthalten zum Teil ältere Testklassen; die oben genannten Testzahlen stammen deshalb aus den Summen des aktuellen Konsolenlaufs.

Die zusätzlichen Diagnosen **assertieren das beobachtete Fehlverhalten**. Ihr grüner Lauf ist ein Nachweis des Befunds, kein Nachweis korrekten Produktverhaltens. Bei der Umsetzung müssen daraus Tests mit der gewünschten Gegenbedingung werden. Java-Probes wurden außerhalb des Quellbaums geschrieben und nur temporär gegen vorhandene Testklassen kompiliert; die zusätzliche Klassendatei wurde anschließend entfernt.

## Befundübersicht

| ID | Prio | Thema | Nachweis |
|---|---|---|---|
| A01 | P1 | Dokumentierter Cleanup-Schalter erreicht Container nicht | Compose-Reproduktion |
| A02 | P2 | Projektlöschen lässt MinIO-Dateien zurück | Integrationstest mit echtem MinIO |
| A03 | P2 | Commit-Gate akzeptiert ungeprüfte Löschung | isolierte Git-/Gate-Reproduktion |
| A04 | P2 | Produktionsstack ohne automatischen Wiederanlauf | Konfigurationsprüfung |
| A05 | P2 | Deployment-Erfolg ohne App-Readiness | Workflow-/Konfigurationsprüfung |
| B01 | P1 | Gebundene PATs erreichen fremde Boards über allgemeine API | Integrationstest |
| B02 | P1 | Nutzung kann gleichzeitigen PAT-Widerruf rückgängig machen | deterministisches Transaktions-Interleaving |
| B03 | P1 | Gesperrte Admins hebeln Aussperrschutz aus | Integrationstest |
| B04 | P2 | Normales Speichern stellt Papierkorb-Karte wieder her | Integrationstest |
| B05 | P2 | Done → Pool → Backlog wird nach altem Datum archiviert | Integrationstest |
| B06 | P2 | Pool-Rundweg erzeugt offene, überlappende Spaltenaufenthalte | derselbe Lifecycle-Test |
| B07 | P2 | Löschen einer boardlosen Pool-Idee schlägt mit 500 fehl | Integrationstest |
| B08 | P2 | Vorhaben verschwinden über unzulässige Pool-Operationen | Integrationstest |
| B09 | P1 | Öffentlicher Default-Schlüssel erlaubt Session-Fälschung | Integrationstest mit Default-Konfiguration |
| B10 | P1 | Passwort-Reset entzieht bestehenden Sessions keinen Zugriff | Integrationstest |
| B11 | P2 | Öffentliche Auth-Endpunkte ohne Rate-Limit | Code-/Konfigurationsprüfung |
| F01 | P1 | Checkbox/zweites Bearbeiten überschreibt gespeicherte Felder | RTL-Reproduktion |
| F02 | P2 | Abgebrochener Entwurf wird beim Checkbox-Klick gespeichert | RTL-Reproduktion |
| F03 | P2 | Checkbox-Klick verändert Markdown-Codebeispiel | RTL-Reproduktion |
| F04 | P2 | Harter Nachtlaufabbruch geht in Anzeige/Persistenz verloren | Parser-/API-Vertragsprüfung |
| F05 | P2 | Nachtlaufcache verwechselt Projekte | RTL mit echtem Routerwechsel |
| F06 | P2 | Verspäteter Reload zeigt falsches Board | RTL mit umgekehrter Antwortreihenfolge |
| F07 | P2 | Zentrale Kacheln/Karten nicht per Tastatur zu öffnen | Komponenten-/Testprüfung |
| F08 | P2 | Login verliert Einladungslink und Deep-Link | Routingprüfung |
| F09 | P2 | Listenmodal ohne Zuständigen-/Label-Auswahl | Aufrufer-/Props-Prüfung |

## Durchlauf 1 — Architektur, Infrastruktur und Betrieb

Die modulweise hexagonale Struktur und der Composition Root sind im Kern nachvollziehbar; ArchUnit prüft Schichtengrenzen und Modulzyklen. Die stärksten Architekturprobleme liegen an Systemgrenzen: Konfigurationsweitergabe, Datenbank/Objektspeicher und der Aussagekraft von Commit-/Deploy-Erfolg.

### A01 — [P1] Compose ignoriert den dokumentierten Schalter zum Abschalten endgültiger Löschungen

**Ort:** [docker-compose.yml:32-54](/Users/manfredwolff/ki-projects/kanban-kit/docker-compose.yml:32) (fehlende Weitergabe im `manban-api.environment`), insbesondere `:52-54` am Ende der Liste. Das Produktions-Overlay ergänzt die Werte ebenfalls nicht.

**Trigger:** Betreiber setzt entsprechend [docs/betrieb.md:61-84](/Users/manfredwolff/ki-projects/kanban-kit/docs/betrieb.md:61) `MANBAN_CLEANUP_ENABLED=false` in `.env` bzw. `MANBAN_DONE_RETENTION_DAYS=0` und erstellt die Container neu. Compose verwendet `.env` zur Interpolation; nicht referenzierte Variablen werden ohne `env_file` nicht Container-Umgebung.

**Auswirkung:** [src/main/resources/application.yml:81-84](/Users/manfredwolff/ki-projects/kanban-kit/src/main/resources/application.yml:81) fällt auf Cleanup aktiv / 30 Tage zurück. [card/infrastructure/TrashRetentionJob.java](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/card/infrastructure/TrashRetentionJob.java) bleibt aktiv und löscht ältere Papierkorb-Karten endgültig, obwohl der Betreiber die Automatik deaktiviert hat. Auch `MANBAN_OUTBOX_ENABLED`, Poll-Intervall, Max-Attempts und Retention sowie `MANBAN_MINIO_BUCKET` erreichen den Container nicht.

**Beleg:** Tatsächliches `docker compose --env-file /dev/null config --format json`, ausgeführt mit CLEANUP=false, OUTBOX=false und DONE_RETENTION_DAYS=0: alle drei Schlüssel fehlen im aufgelösten `services.manban-api.environment`. Der Test verwendete keine Produktions-Secrets und startete keine Container. Keine alternative `env_file`-Weitergabe in Basis oder Overlay vorhanden.

**Fix:** Alle unterstützten und dokumentierten Betriebsvariablen explizit durchreichen (oder ein sorgfältig passendes `env_file` verwenden). Compose-Vertragstest: benutzerdefinierte Werte müssen nach Zusammenführen von Basis und Prod-Overlay in `manban-api.environment` erscheinen; darunter ausdrücklich `false` und `0`. Spring-Tests allein erkennen die fehlende Transportstrecke nicht.

### A02 — [P2] Projektlöschen umgeht die transaktionale Blob-Löschung

**Ort:** [src/main/java/org/mwolff/manban/project/application/ProjectService.java:166-169](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/project/application/ProjectService.java:166).

**Trigger:** Plattform-Admin löscht ein Projekt, das Karten oder Pool-Ideen mit Anhängen enthält.

**Auswirkung:** Die DB-Cascade entfernt Board/Karte/`attachment_meta`; MinIO-Dateien bleiben dauerhaft zurück. Metadaten samt Bezug zum gelöschten Projekt sind dann weg. Speicherverbrauch und aufbewahrte Nutzerdateien wachsen nach jedem Projektlöschen weiter.

**Beleg:** `ProjectRepositoryAdapter.deleteById` delegiert direkt an `jpa.deleteById`. Es gibt nur `ProjectCreatedEvent`, kein Project-Purge-Ereignis. Die Blob-Outbox entsteht über `AttachmentPurgeListener.onCardsPurged`; `CardsPurgedEvent` kommt aus Einzel-/Trash-Purge und `BoardPurgeCascade`, nie aus dem Projektlöschen. Die Flyway-FKs löschen Metadaten, kein Trigger erstellt Outbox-Aufträge. `StorageReconciliationService` berichtet nur und räumt bewusst nicht automatisch auf. `AttachmentIT` prüft Karte/Board/Trash-Purge, aber nicht Projekt-Purge.

**Zusätzlicher Laufzeitnachweis:** Echter Multipart-Upload in Testcontainers-MinIO, anschließend Projekt-DELETE: Metadaten verschwinden, das Objekt bleibt vorhanden und es entsteht kein `attachment.blob-delete`-Outbox-Auftrag.

**Fix:** Vor dem Projekt-Delete synchron ein Projekt-Purge-Ereignis veröffentlichen und im Kartenmodul alle Karten des Projekts (inkl. boardloser Ideen, Archiv, Papierkorb) in die bestehende `CardsPurgedEvent`-Kette überführen. Aufträge und Delete in derselben Transaktion. IT: Projekt mit Board- und Pool-Anhang löschen, Outbox abarbeiten, beide Objekte entfernt; Rollback muss die Löschaufträge zurückrollen.

### A03 — [P2] Commit-Gate lässt eine nach den Checks vorgenommene Dateilöschung durch

**Ort:** [.githooks/gate.mjs:123-129](/Users/manfredwolff/ki-projects/kanban-kit/.githooks/gate.mjs:123), konkret `:128`.

**Trigger:** Getrackte Datei ändern, Checks grün ausführen, anschließend die Datei löschen und die Löschung stagen, ohne nochmals zu prüfen.

**Auswirkung:** Ein Commit kann einen nicht geprüften und bereits defekten Stand enthalten. Dies benötigt weder `--no-verify` noch eine manipulierte Nachweisdatei.

**Beleg / tatsächliche Reproduktion:** In isoliertem Temp-Git-Repository wurden das echte Gate und die echte installierte `checks.mjs` kopiert. `required.txt` war verändert und vorhanden; konfigurierte Prüfung `node -e "require('fs').readFileSync('required.txt')"` war grün. Die Zusammenfassung enthielt Hash `a9c0225b2e00b102af489a25e72f286f80796a4d` für diese Datei. Anschließende Löschung und `git add -u`: **Gate Exit 0**, derselbe echte Check danach **Exit 1**. `checks.mjs:408-432` unterscheidet geprüfte Löschungen mit `null`; das Gate ignoriert diesen Tombstone und prüft bei `D` lediglich, ob der Pfad irgendwann im geprüften Satz stand. Keine bestehende Vorprüfung fängt diesen Ablauf ab.

**Fix:** Für `D` nur `geprueft[pfad] === null` zulassen; ein vorhandener Hash muss als Änderung nach dem Prüflauf abgelehnt werden. Regressionstest mit genau diesem Ablauf und Gegenfall (bereits vor den Checks gelöscht → zulässig). Der eingebettete Gate-Blob in `install.mjs` ist bytegleich und muss über den Upstream-/Blob-Generierungsweg ebenfalls aktualisiert werden.

### A04 — [P2] Der Produktionsstack startet nach Host-/Docker-Neustart nicht wieder

**Ort:** [docker-compose.prod.yml:13-15](/Users/manfredwolff/ki-projects/kanban-kit/docker-compose.prod.yml:13) und Basisdefinitionen [docker-compose.yml:2-30](/Users/manfredwolff/ki-projects/kanban-kit/docker-compose.yml:2).

**Trigger:** VPS wird für ein Systemupdate oder nach einem Ausfall neu gestartet; alternativ ein App-Prozess beendet sich unerwartet.

**Auswirkung:** Postgres, MinIO und API bleiben gestoppt, bis jemand manuell `docker compose up -d` ausführt. Eine aktive Docker-/Traefik-Installation und der als Dienst gestartete GitHub-Runner starten den Compose-Stack nicht selbst. Der Deploy-Workflow wird nur durch einen Push auf `production` ausgelöst.

**Beleg:** Zusammengeführte Compose-Definitionen enthalten für keinen Dienst `restart` oder eine andere Neustartregel. `docker compose config` bestätigt das; [docs/deployment-hostinger.md](/Users/manfredwolff/ki-projects/kanban-kit/docs/deployment-hostinger.md) beschreibt keinen unabhängigen systemd-Dienst für den Stack. Es wurde kein Host-Neustart ausgeführt; Befund beruht auf der vollständigen ausgelieferten Startkonfiguration.

**Fix:** Im Produktions-Overlay mindestens Postgres, MinIO und API mit einer passenden dauerhaften Restart-Policy (`unless-stopped`) versehen; externes Traefik hat seinen eigenen Lebenszyklus. Wiederanlauf nach Docker-/Host-Neustart im Betriebs-Smoke-Test belegen.

### A05 — [P2] Deploy meldet Erfolg, bevor Spring erfolgreich hochgefahren ist

**Ort:** [.github/workflows/deploy.yml:26](/Users/manfredwolff/ki-projects/kanban-kit/.github/workflows/deploy.yml:26).

**Trigger:** Container lässt sich starten, Spring scheitert danach beim Boot, etwa durch fehlgeschlagene Flyway-Migration oder fehlerhafte DB-Zugangsdaten eines bestehenden Postgres-Volumes.

**Auswirkung:** GitHub zeigt den Produktions-Deploy als erfolgreich, während die App anschließend beendet ist und der Proxy Fehler liefert. Das automatisierte Deployment führt die manuelle Verifikation der Betriebsanleitung nicht aus.

**Beleg:** Letzter Schritt ist ausschließlich `docker compose ... up -d --build`. Basis-Compose besitzt Healthchecks nur für Postgres/MinIO, die API hat keinen Healthcheck. Weder Dockerfile noch Workflow enthalten Readiness-/HTTP-Prüfung, Warteschleife oder `--wait`. Die Aussage ist statisch belegt; absichtlich defekte Container wurden nicht auf Nutzer-/Produktionssystemen gestartet.

**Fix:** App-Readiness prüfen, etwa API-Healthcheck plus `up --wait --wait-timeout ...` oder begrenztes HTTP-Polling nach dem Start. Fehlstart muss Exit ungleich 0 liefern; bei Fehler passende Container-Logs als Diagnose sichern. Integrationstest mit absichtlich fehlerhafter Startup-Konfiguration.


## Durchlauf 2 — Backend

### B01 — P1: Board-gebundene PATs erreichen die allgemeine API ohne Bindungsprüfung

- **Ort:** [src/main/java/org/mwolff/manban/config/SecurityConfig.java:84–85](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/config/SecurityConfig.java:84); Gegenstellen [accesstoken/web/security/PatAuthenticationFilter.java:49–57](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/accesstoken/web/security/PatAuthenticationFilter.java:49), [project/application/PermissionChecker.java:79–91](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/project/application/PermissionChecker.java:79).
- **Trigger:** Ein Nutzer mit Zugriff auf Projekte A und B erstellt ein an Board A gebundenes PAT und verwendet es für `GET /api/projects`, `GET /api/boards/{boardB}/cards` oder normale mutierende Karten-/Projekt-Endpunkte.
- **Auswirkung:** Das PAT bekommt die kompletten Rechte seines Erstellers außerhalb der zugesagten Bindung; bei einem Plattformadmin reichen die allgemeinen Projekt-Endpunkte auch für Projektanlage/-löschung. Nur `/api/admin/**` und Tokenverwaltung verlangen eine Session. Die Bindung ist damit keine Sicherheitsgrenze.
- **Beleg:** Der Filter setzt `Long userId` als Principal, legt `KanbanPrincipal` nur in `details` ab. Die allgemeine API verlangt allein `authenticated()`, sämtliche dortigen Services autorisieren allein die User-ID. Nur die Compat-Schicht liest die Bindung. [docs/dogfooding.md:9–12](/Users/manfredwolff/ki-projects/kanban-kit/docs/dogfooding.md:9) verspricht ausdrücklich eine Beschränkung wie bei einem Fine-grained-PAT. Ungebundene PATs sind hingegen bewusst für die allgemeine API vorgesehen ([docs/dogfooding.md:51–52](/Users/manfredwolff/ki-projects/kanban-kit/docs/dogfooding.md:51)).
- **Testlücke:** `AccessTokenIT` prüft Authentifizierung und Bindungspersistierung sowie gesperrte Tokenverwaltung, nicht Zugriffe eines gebundenen PAT über die allgemeinen APIs auf andere Boards/Projekte.
- **Fix:** Gebundene PATs auf autorisierte Endpunkte/Bereiche einschränken oder die Bindung zentral in jede relevante Ressourcenautorisierung einbeziehen. Bewusst unterstützte ungebundene PATs getrennt behandeln; reine Compat-Guards reichen nicht.

### B02 — P1: Ein gleichzeitiger PAT-Zugriff kann den Widerruf dauerhaft zurücknehmen

- **Ort:** [src/main/java/org/mwolff/manban/accesstoken/application/AccessTokenService.java:139–145](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/accesstoken/application/AccessTokenService.java:139); Gegenstelle `:123–130`.
- **Trigger:** Request T1 liest ein gültiges PAT (`revoked=false`) in `resolveBinding`. T2 widerruft es und committet. T1 schreibt danach `lastUsedAt` und committet.
- **Auswirkung:** Der alte `revoked=false`-Wert wird zurückgeschrieben; nach dem erfolgreich bestätigten Widerruf sind auch spätere neue Requests wieder authentifiziert. Es geht um dauerhafte Reaktivierung, nicht nur um einen bereits laufenden Request.
- **Beleg:** Beide Wege speichern ganze `AccessToken`-Records. `AccessTokenRepositoryAdapter.save()` erzeugt eine komplette neue `KanbanAccessTokenEntity`; die Entity hat weder Version noch DynamicUpdate noch Locks. Hibernate schreibt bei schmutziger Entity standardmäßig sämtliche aktualisierbaren Spalten, einschließlich `revoked`. Kein DB-Trigger/Constraint verhindert `true → false`.
- **Testlücke:** Der Ablauf create/use/revoke/use in `AccessTokenIT` ist sequenziell. Es fehlt ein erzwungener Interleaving-Test.
- **Fix:** Die Benutzung mit einem gezielten atomischen `UPDATE ... SET last_used_at=? WHERE ... AND revoked=false` vermerken und das Ergebnis für die Authentifizierung auswerten; Widerruf monoton machen. Alternativ gemeinsamen Lock-/Versionsschutz verwenden, ohne dass ein Telemetrie-Update den Widerruf überschreibt.

### B03 — P1: Der Aussperrschutz zählt gesperrte Admins als verbleibenden Zugang

- **Ort:** [src/main/java/org/mwolff/manban/auth/application/AdminService.java:53–58](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/auth/application/AdminService.java:53); weitere Stelle `:103–112`; [auth/infrastructure/persistence/AppUserJpaRepository.java:35–38](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/auth/infrastructure/persistence/AppUserJpaRepository.java:35).
- **Deterministischer Trigger:** A und B sind Plattformadmins. A sperrt B. A setzt anschließend seine eigene Plattformrolle auf USER.
- **Auswirkung:** Die Selbstdegradierung gelingt, weil noch zwei ADMIN-Zeilen gezählt werden. Danach ist der einzige verbleibende ADMIN B gesperrt: sämtliche administrativen Zugänge fallen aus. Bootstrap hilft nicht, weil es die existierende ADMIN-Zeile sieht und abbricht. Wiederherstellung benötigt direkten DB-Eingriff.
- **Zusätzlicher Trigger:** A und B können sich gleichzeitig gegenseitig sperren; `disable` sperrt/prüft keine gemeinsame Menge aktiver Admins.
- **Beleg:** `lockIdsByPlatformRole` filtert nur `platform_role`, nicht `disabled_at`. `disable` schützt allein gegen identische Actor-/Target-ID. `LoginService` und `DisabledUserGuardFilter` weisen gesperrte Konten tatsächlich ab, daher kompensiert kein nachgelagerter Pfad die Lücke.
- **Testlücke:** `RoleInvariantConcurrencyIT` testet die Owner-/Admin-Rolleninvarianten, aber nicht ihre Kombination mit Disabled-Zuständen. `AdminUserIT`/`AdminServiceTest` prüfen Sperren und Rollenwechsel getrennt.
- **Fix:** Als gemeinsame Invariante mindestens einen nutzbaren aktiven Plattformadmin erhalten; Degradieren und Sperren unter derselben serialisierenden Sperre entscheiden und Actor-Rechte nach der Sperre neu prüfen.

### B04 — P2: Speichern einer Papierkorb-Karte stellt sie unbeabsichtigt wieder her

- **Ort:** [src/main/java/org/mwolff/manban/card/infrastructure/persistence/CardEntity.java:97–105,125–128](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/card/infrastructure/persistence/CardEntity.java:97); primärer Schreibpfad [CardRepositoryAdapter.java:59–60](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/card/infrastructure/persistence/CardRepositoryAdapter.java:59).
- **Trigger:** Karte löschen, danach eine normale PATCH-Anfrage auf ihre weiterhin bekannte ID senden. Alternativ eine Herkunftskarte projektübergreifend transferieren, deren Kind im Papierkorb liegt: `doTransfer` lädt solche Kinder ausdrücklich mit und speichert `withDerivedFrom(null)`.
- **Auswirkung:** `deleted_at` wird NULL. Die Karte taucht ohne Restore-Aktion wieder im aktiven Board auf; wenn ihre frühere Position inzwischen belegt ist, scheitert stattdessen die gesamte Änderung/der Transfer mit 409. Die Lösch-/Retention-Semantik wird verletzt.
- **Beleg:** Der Domain-Record enthält `deletedAt` nicht, die Entity mappt das Feld jedoch schreibbar. Der Konstruktor aus `Card` setzt es nie, und `save` verwendet genau diese neue Entity beim Merge. `findById` ist ungefiltert; `findByDerivedFromCardId` und `findByRequirementCardId` schließen Papierkorb bewusst ein. DB-Constraints verhindern höchstens eine Positionskollision, erhalten aber keinen Löschzustand.
- **Testlücke:** `CardSoftDeleteIT` testet nur direkte SQL-Lifecycle-Methoden und Read-Filter; kein Domain-save nach Softdelete. Relevante Herkunftstests überprüfen die gelöschte Relation, nicht durchgängig den erhaltenen `deleted_at`-Wert.
- **Fix:** Den Löschzustand korrekt im Aggregat erhalten oder `deleted_at` beim normalen JPA-Schreibpfad ausdrücklich unveränderbar machen und nur über Lifecycle-Operationen ändern. Normale Updates gelöschter Karten fachlich prüfen/abweisen.

### B05 — P2: Done → Pool → Backlog behält den Done-Zeitstempel und wird falsch archiviert

- **Ort:** [src/main/java/org/mwolff/manban/card/domain/Card.java:199–210,229–241](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/card/domain/Card.java:199); Aufrufer [CardService.java:1564–1568,1548–1551](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/card/application/CardService.java:1564).
- **Trigger:** Eine erledigte Karte aus Done in den Ideen-Pool zurücklegen und später erneut ins Backlog einplanen. Die Karte muss nur insgesamt älter als ihre Done-Retention werden.
- **Auswirkung:** Sie gilt weiterhin als erledigt, obwohl sie im Backlog liegt. Der Retention-Job archiviert sie nach dem alten Zeitpunkt. Auch Herkunftsbaum, Blockerberechnung und Dashboard verwenden den erhaltenen `movedToDoneAt` und liefern falsche Erledigungs-/Durchsatzwerte.
- **Beleg:** `asPooledIdea` und `withPlannedOnBoard` übernehmen den Timestamp unverändert. `moveToIdeaStorage` und `moveBackToPool` korrigieren ihn nicht. `CardJpaRepository.findArchivableDoneCards` prüft nur `archived=false`, `deleted_at is null`, Timestamp vorhanden/alt — weder Spalte noch Pool-Zustand. Beim normalen Move wird der Timestamp beim Verlassen von Done richtigerweise gelöscht.
- **Testlücke:** `ProjectIdeaIT` prüft den Pool-Rundweg ausschließlich mit einer nie erledigten Karte. `DoneRetentionIT` setzt Done-Timestamps direkt und verbindet ihn nicht mit dem Pool-Lifecycle.
- **Fix:** Beim Verlassen des Workflows in den Pool den aktuellen Done-Zustand aufheben; Einplanen muss zu der tatsächlichen Zielspalte passende Statusdaten erzeugen. Integrationstest Done → Pool → Backlog → Retention.

### B06 — P2: Ein erneutes Einplanen erzeugt überlappende offene Spaltenaufenthalte

- **Ort:** [src/main/java/org/mwolff/manban/card/application/CardService.java:1550–1551,1564–1568](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/card/application/CardService.java:1550) (auch `moveToIdeaStorage`).
- **Trigger:** Karte liegt in Ready, wird für mehrere Tage in den Pool gelegt und wieder eingeplant. Wiederholung vermehrt die offenen Historienzeilen.
- **Auswirkung:** Der alte Ready-Aufenthalt bleibt während der gesamten Pool-Zeit und nach dem erneuten Einplanen offen. Die Karte hat danach gleichzeitig einen offenen Ready- und einen offenen Backlog-Aufenthalt. Beim nächsten Move werden beide mit demselben Zeitpunkt geschlossen; Verweildauer und Ausreißerzahlen im Dashboard werden verfälscht.
- **Beleg:** Pool-Wege rufen `transitions.closeOpen` nicht auf; `planOntoBoard` ruft bedingungslos `open` auf. V9 besitzt keinen Unique-Constraint für einen offenen Aufenthalt. `CardColumnTransitionRepositoryAdapter.closeOpen` schließt alle offenen Zeilen, `CardCycleTimeService` zählt alle gelieferten Aufenthalte. Damit ist es kein nur theoretischer inkonsistenter Record.
- **Testlücke:** `ProjectIdeaIT` überprüft Pool-/Board-Felder, aber weder Anzahl offener Transitionen noch Exklusion der Pool-Zeit aus der Spaltenverweildauer.
- **Fix:** Den tatsächlichen Spaltenaustritt beim Pool-Wechsel mit demselben Eventzeitpunkt schließen. Wiederholtes Einplanen nur für zulässige Pool-Zustände akzeptieren oder idempotent behandeln; anschließend genau einen Aufenthalt öffnen.

### B07 — P2: Die Löschaktion für boardlose Pool-Ideen endet immer mit 500

- **Ort:** [src/main/java/org/mwolff/manban/card/application/CardService.java:1701–1702](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/card/application/CardService.java:1701).
- **Trigger:** `DELETE /api/cards/{id}` für eine per Projekt-Ideen-Endpunkt angelegte boardlose Karte, mit korrekt berechtigtem Projektmitglied.
- **Auswirkung:** `softDelete` wird ausgeführt, dann wirft `requireBoardId()`; die Transaktion rollt zurück, die Idee bleibt dauerhaft liegen. Analog sind Archivieren, Wiederherstellen und endgültiges Löschen noch boardabhängig. Die Pool-Oberfläche bietet derzeit keine direkte Löschaktion; der bestätigte Fehler betrifft den API-Lifecycle. Ob Löschen fachlich angeboten oder ausdrücklich abgelehnt werden soll, muss für den Fix festgelegt werden; HTTP 500 ist in beiden Fällen falsch.
- **Beleg:** `requireCardOp` autorisiert die boardlose Idee ausdrücklich projektbasiert. Danach wird bedingungslos ein Board-Event erzeugt, obwohl `publishChangedIfOnBoard` als anderer bereits korrekter Mechanismus existiert. Es gibt keinen separaten Lösch-Endpunkt für Pool-Ideen.
- **Testlücke:** `ProjectIdeaIT`/`ProjectIdeaEditIT` decken Anlegen, Editieren und Einplanen ab, nicht DELETE einer Idee.
- **Fix:** Den Lifecycle vollständig projektbasiert gestalten, Pool-Änderungen über das Ideen-Event melden und eine passende Papierkorb-/Restore-Sicht für boardlose Karten bereitstellen.

### B08 — P2: Pool-Endpunkte lassen Vorhaben aus ihren regulären Ansichten verschwinden

- **Ort:** [src/main/java/org/mwolff/manban/card/application/CardService.java:1564–1568](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/card/application/CardService.java:1564) und `:1534–1551`.
- **Trigger:** Ein berechtigtes Mitglied sendet `PUT /api/cards/{epicId}/to-pool` für ein bestehendes Vorhaben mit zugeordneten Karten. Alternativ `PUT /api/cards/{epicId}/plan` direkt auf ein anderes Board desselben Projekts.
- **Auswirkung:** Im ersten Fall wird das Vorhaben boardlos und verschwindet aus beiden regulären Listen: Board-Epics finden es nicht mehr, Projekt-Ideen filtern ausschließlich CARD. Beim Einplanen auf einem anderen Board wandert das Vorhaben ohne seine Kinder; deren `parent_id` zeigt weiter darauf, aber die ausschließlich boardweise gerechnete Mitgliedschaft/Fortschrittsanzeige wird leer. Der dokumentierte Ausschluss von Vorhaben beim Transfer und beim Ideen-Speicher lässt sich über diese beiden alternativen Routen umgehen.
- **Beleg:** Im Gegensatz zu `moveToIdeaStorage` und `doTransfer` haben `moveBackToPool` und `planOntoBoard` keinerlei Typprüfung. V2 erzwingt nur die Werte CARD/EPIC, V18 erlaubt boardlos unabhängig vom Typ. FK- und Positions-Constraints verhindern den Vorgang nicht. `asPooledIdea`/`withPlannedOnBoard` erhalten den EPIC-Typ sowie die Relationsfelder. Die Projektgrenze bleibt geschützt; es wird keine projektübergreifende Rechteeskalation behauptet.
- **Testlücke:** `CardServiceTest.moveToIdeaStorage_rejectsEpic` deckt nur die andere Route ab. Die Tests von `moveBackToPool` und `planOntoBoard` übergeben ausschließlich reguläre CARD-Instanzen und prüfen nur Fremdprojekte.
- **Fix:** Die Pool-Wege müssen denselben CARD-/Lifecycle-Vertrag erzwingen. Einplanen nur aus tatsächlich zulässigem Pool-Zustand, Rückweg nur für geeignete Board-Karten; bei wiederholten Requests wohldefinierte Idempotenz oder fachliche Ablehnung. Beide HTTP-Routen mit EPICs einschließlich bestehender Kinder testen.

**Zusätzlicher Nachweis zu B08:** Ein Vorhaben mit Kind wird über `/to-pool` erfolgreich boardlos; es fehlt anschließend sowohl in der Vorhaben- als auch Ideenliste. `/plan` verschiebt es auf Board B, während das Kind mit unveränderter `parent_id` auf Board A bleibt. Mit Postgres/MockMvc reproduziert.

### B09 — P1: Der öffentliche Default-Schlüssel erlaubt gefälschte Admin-Sessions

- **Ort:** [src/main/java/org/mwolff/manban/auth/application/AuthProperties.java:36–40](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/auth/application/AuthProperties.java:36), [src/main/resources/application.yml:48](/Users/manfredwolff/ki-projects/kanban-kit/src/main/resources/application.yml:48), [docker-compose.yml:36](/Users/manfredwolff/ki-projects/kanban-kit/docker-compose.yml:36); Verwendung in [src/main/java/org/mwolff/manban/auth/infrastructure/security/SignedSessionTokens.java:48–52](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/auth/infrastructure/security/SignedSessionTokens.java:48).
- **Voraussetzung:** Die Anwendung läuft mit dem mitgelieferten Default-Schlüssel, etwa nach Kopieren der [.env.example](/Users/manfredwolff/ki-projects/kanban-kit/.env.example) ohne Austausch des Werts. Es wird nicht behauptet, dass der derzeitige Produktionsserver so konfiguriert ist.
- **Problem:** Fehlende/leere Konfiguration wird durch einen öffentlich im Repository stehenden festen HMAC-Schlüssel ersetzt. Wer diesen kennt, kann für eine bekannte oder erratene User-ID ein gültiges Cookie herstellen. Der Passwort-Login wird vollständig umgangen; für eine aktive Admin-ID funktionieren auch Admin-Endpunkte.
- **Nachweis:** Der temporäre Test legt einen aktiven Admin an, erzeugt außerhalb des Login-Flows mit einer neuen Default-`AuthProperties`-Instanz ein signiertes Cookie und erhält mit `GET /api/admin/users` HTTP 200. Das Prod-Overlay verlangt zwar einen nichtleeren Wert; es lehnt den unveränderten Beispielwert nicht ab. Der normale Basis-Stack erlaubt zudem öffentliche Domains.
- **Fix:** Nicht-lokalen Start ohne individuell gesetzten starken Schlüssel verweigern, bekannte Beispielwerte explizit ablehnen und einen unsicheren lokalen Modus nur bewusst aktivierbar machen. Negativtests für fehlend, leer, Beispielwert und zu kurze Werte; positiver Test mit individuell erzeugtem Schlüssel. Falls eine Instanz betroffen war, Schlüsselwechsel als bewussten Session-Widerruf behandeln.

### B10 — P1: Passwort-Reset beendet kompromittierte Sessions nicht

- **Ort:** [src/main/java/org/mwolff/manban/auth/application/ResetPasswordService.java:38–49](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/auth/application/ResetPasswordService.java:38); [src/main/java/org/mwolff/manban/auth/infrastructure/security/SignedSessionTokens.java:84–91](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/auth/infrastructure/security/SignedSessionTokens.java:84) und [src/main/java/org/mwolff/manban/auth/web/security/SessionAuthenticationFilter.java:45–56](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/auth/web/security/SessionAuthenticationFilter.java:45).
- **Trigger:** Ein Angreifer besitzt ein gültiges Session-Cookie. Der Kontoinhaber setzt daraufhin sein Passwort zurück.
- **Auswirkung:** Nur der Passwort-Hash ändert sich. Alte Cookies enthalten ausschließlich User-ID und Ablauf und bleiben standardmäßig bis zu sieben Tage nutzbar. Der Passwort-Reset stellt die Kontrolle über das Konto damit nicht vollständig wieder her. Ein Logout löscht ebenfalls nur das Cookie des aufrufenden Browsers; eine andere Kopie bleibt gültig.
- **Nachweis:** Echter Login → Reset über gültiges einmaliges Token → Zugriff mit unverändertem alten Cookie auf `/api/admin/users`: weiterhin 200. Die Ursache ist unabhängig vom verwendeten Schlüssel: Auch ein sicher gewähltes Session-Secret bindet die Cookies nicht an die Passwortänderung.
- **Fix:** Pro Benutzer eine Session-Generation oder einen Widerrufszeitpunkt führen, beim Ausstellen binden und bei jeder Anfrage prüfen. Passwort-Reset erhöht die Generation atomar; eine bewusst angebotene Funktion zum Abmelden aller Sitzungen nutzt denselben Mechanismus. Bestehende PATs sind eine getrennte Produktentscheidung. Test: Altes Cookie nach Reset abweisen, neues Login funktioniert; Replay eines widerrufenen Cookies bleibt ungültig.

### B11 — P2: Öffentliche Auth-Endpunkte besitzen keine Missbrauchsbegrenzung

- **Ort:** [src/main/java/org/mwolff/manban/auth/application/LoginService.java:29–37](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/auth/application/LoginService.java:29), [src/main/java/org/mwolff/manban/auth/application/RequestPasswordResetService.java:45–63](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/auth/application/RequestPasswordResetService.java:45), öffentliche Matcher in [src/main/java/org/mwolff/manban/config/SecurityConfig.java:63–70](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/config/SecurityConfig.java:63); zusätzlich Caddyfile und Produktions-Overlay.
- **Trigger:** Wiederholte Login-Versuche für ein Konto oder massenhafte Forgot-/Register-Anfragen an eine öffentlich erreichbare Standardinstallation.
- **Auswirkung:** Jeder Versuch läuft durch Passwortprüfung bzw. Token-/Outbox-Erzeugung. Es fehlt eine an Konto/Quelle gebundene Begrenzung mit 429 oder Backoff. Automatisiertes Passwortprobieren und das Überfluten von Reset-Mails/Outbox werden dadurch nicht auf Anwendungsebene begrenzt.
- **Beleg:** Kein Rate-Limit-Filter, keine Zähler-/Sperrlogik in den Auth-Services und keine entsprechende Middleware in den ausgelieferten Proxys. [CLAUDE-security.md](/Users/manfredwolff/ki-projects/kanban-kit/CLAUDE-security.md) fordert ausdrücklich Brute-Force-Schutz und Rate Limiting. Ein außerhalb des Repositories eingerichteter Schutz wurde nicht geprüft und kann den konkreten Server besser absichern.
- **Fix:** Begrenzung je Konto und vertrauenswürdig bestimmter Client-Quelle an der API-Grenze, eigener engerer Rahmen für Token-/Mail-Auslösung; keine dauerhafte Kontosperre allein durch fremde Fehlversuche. Tests mit kontrollierter Uhr für Grenzwert, Erholung und parallele Requests. Kein produktiver Last-/Angriffstest wurde durchgeführt.


## Durchlauf 3 — Frontend

### F01 — P1: Ein Checkbox-Klick überschreibt unmittelbar zuvor gespeicherte Kartenfelder

- Ort: [frontend/src/components/CardDetailModal.tsx:1232–1241](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/components/CardDetailModal.tsx:1232) (engster Anker 1234), außerdem `1154–1162`, `1191–1203`; Parent [frontend/src/pages/BoardPage.tsx:91–98,315–319](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/pages/BoardPage.tsx:91).
- Trigger: Karte mit Taskliste öffnen, Titel und/oder Fälligkeit/Vorhaben ändern, speichern, ohne Schließen eine Task-Checkbox anklicken. Auch erneutes Bearbeiten/Speichern reicht zum Zurücksetzen.
- Ursache/Auswirkung: `save()` übernimmt nur `deps` und Beschreibung in den gespeicherten lokalen Zustand, ignoriert die zurückgegebene Karte. `toggleTask()` sendet weiterhin `card.title`, `card.shortcode`, `card.parentId`, `card.dueDate`; `startEditing()` kopiert dieselben alten Props. BoardPage lädt die Kartenliste neu, aktualisiert aber `selectedCard` nicht. Damit überschreibt der nächste Voll-PATCH eigene bereits bestätigte Änderungen. Der Titel im Modal bleibt ebenfalls veraltet.
- Beleg: Isolierter RTL-Test gegen die Originalkomponente bestätigt zwei Requests: zuerst Titel `New saved title`, anschließend durch Checkbox wieder `Aufgabe`. Backend `CardService.update:710–720` ersetzt genau diese Werte; kein serverseitiger Merge verhindert den Verlust.
- Fehlender Test: Bestandsregressionen prüfen zweite Beschreibung und Dependencies, jedoch nicht Titel/Fälligkeit/Parent/Kürzel über zwei Schreibaktionen.
- Fix: Nach erfolgreicher Mutation vollständigen bestätigten Kartenstand konsistent halten; alle Folgeaktionen und Anzeige daraus speisen. Draft und bestätigte Karte trennen. Checkbox möglichst über schmalen Beschreibungsschreibpfad statt Voll-PATCH führen.

### F02 — P2: Abgebrochene Beschreibungsänderungen werden beim Task-Toggle doch gespeichert

- Ort: [frontend/src/components/CardDetailModal.tsx:1575](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/components/CardDetailModal.tsx:1575) (Abbrechen), `1347` (Dialog onClose), `1224–1235`, `1465` (Lesemodus nutzt `body`). Exakter Suchanker: `<Button onClick={() => setEditing(false)}>Abbrechen</Button>`.
- Trigger: Beschreibung `- [ ] Original task` zu `- [ ] Discard this draft` ändern, Abbrechen oder Escape, dann die sichtbare Task-Checkbox anklicken.
- Ursache/Auswirkung: Abbrechen setzt ausschließlich `editing=false`. `body` bleibt der verworfene Entwurf, wird im Lesemodus angezeigt und bei Checkbox-Klick vollständig persistiert. Abbrechen verwirft die Änderung tatsächlich nicht.
- Beleg: RTL-Reproduktion sendet nach Abbrechen `- [x] Discard this draft`; keine vorherige Save-Aktion. Bestands-Abbruchtest 1465ff. ändert nur den Titel und kann diesen Fehler nicht erkennen.
- Fix: Gemeinsamen cancel-Handler für Button/Escape/Backdrop, der alle Draft-Felder auf den bestätigten Stand zurücksetzt; Lesemodus und Task-Toggle ausschließlich aus bestätigter Beschreibung bedienen.

### F03 — P2: Checkbox-Index zählt Markdown-Codebeispiele und verändert falsche Textstellen

- Ort: [frontend/src/lib/markdownTasks.ts:82–90](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/lib/markdownTasks.ts:82); dazu [CardDetailModal.tsx:131–143](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/components/CardDetailModal.tsx:131) (Index der tatsächlich gerenderten Inputs).
- Trigger: Beschreibung `    - [ ] Code sample\n\n- [ ] Real task` (vier führende Leerzeichen beim Beispiel); einzige gerenderte Checkbox anklicken.
- Ursache/Auswirkung: GFM rendert den eingerückten ersten Absatz als Code, `toggleTaskAt` zählt ihn wegen `\s*` trotzdem als Task. Der Klick auf „Real task“ ändert `Code sample` zu `[x]`, während der angeklickte Task unverändert bleibt. Entsprechende Abweichungen sind auch bei Blockquotes bzw. gemischten/längeren Code-Fences möglich; der konkret geprüfte Fall genügt bereits.
- Beleg: Dritter RTL-Test bestätigt genau eine gerenderte Checkbox und anschließend den PATCH-Text `    - [x] Code sample\n\n- [ ] Real task`.
- Fix: Task-Positionen aus derselben Markdown-Struktur/Quellposition wie der Renderer gewinnen. Mindestens indented code, Blockquote-Tasks und korrekte Fence-Grenzen gemeinsam für Darstellung/Mutation behandeln. Nicht ausschließlich Regex-Ähnlichkeit behaupten.

### F04 — P2: Harter Nachtlaufabbruch auf Laufebene verschwindet aus Anzeige und Speicherung

- Ort: [frontend/src/pages/NightRunPage.tsx:160–178](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/pages/NightRunPage.tsx:160) (`ausParser`), `219–235` (`zurEinlieferung`), Anzeige `LaufPanel` ab 456.
- Trigger: Gültiger Ergebnisstand mit `abschluss: "harterStopp"`, etwa unsauberer Working Tree vor der ersten Einheit, `einheiten: []`, `fehlerText` gesetzt. Ebenso Abbruch zwischen bereits grünen Einheiten.
- Ursache/Auswirkung: Parser setzt ausdrücklich `runState=RED`, `runErrorClass=HARD_ABORT`, `runExcerpt`; beide Adapter übernehmen keines dieser Felder. UI zeigt nur „0 bearbeitet, 0 übergangen“/grüne Einheiten, ohne Abbruchgrund. Nach Upload geht der Grund endgültig verloren; erneutes Einlesen desselben abgeschlossenen Laufs wird serverseitig dedupliziert.
- Beleg: [frontend/src/lib/nightRunErgebnisstand.ts:256–278](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/lib/nightRunErgebnisstand.ts:256); Parser-Tests [nightRunErgebnisstand.test.ts:375–404](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/lib/nightRunErgebnisstand.test.ts:375) prüfen diesen echten Runner-Fall. [NightRunPage.test.tsx](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/pages/NightRunPage.test.tsx) enthält keinen Laufebenen-Abbruchtest. Backend DTO/Service/View und [api/nightRuns.ts](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/api/nightRuns.ts) haben ebenfalls kein entsprechendes Feld — reine JSX-Korrektur reicht für persistierte Läufe nicht.
- Fix: Laufzustand/Fehlerklasse/Auszug durch Anzeige, Einlieferungsvertrag und Persistenz führen, sichtbar auf Laufebene darstellen; leere hart abgebrochene Läufe testen. Bestehende gespeicherte Läufe haben diese Information nicht.

### F05 — P2: Nachtlauf-Kartencache verwechselt gleiche Nummern verschiedener Projekte

- Ort: [frontend/src/pages/NightRunPage.tsx:588–589,618–635](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/pages/NightRunPage.tsx:588) (engster Anker 619–620).
- Trigger: Unter `/projects/1/nachtlauf` Lauf mit Karte #700 aufklappen, anschließend bei gemounteter Route auf `/projects/2/nachtlauf` wechseln und dort Lauf mit eigener #700 aufklappen. Nummern sind laut API projektlokal.
- Ursache/Auswirkung: `katalogRef`, `katalog`, `geladeneLaeufe` und offenes Detail bleiben beim Parameterwechsel bestehen. Neue Liste wird geladen, die Nummer #700 gilt jedoch schon als bekannt und wird nie für Projekt 2 angefragt. Herkunft und aufrufbare Karte stammen aus Projekt 1; im geöffneten Modal ist zugleich `projectId=2`, wodurch weitere Nummernsprünge nochmals den Kontext wechseln.
- Beleg: Initial-/Ladeeffekt 591–615 aktualisiert nur Laufdaten/Zähler und setzt keine Caches zurück. Route [App.tsx](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/App.tsx) versieht die Page nicht mit Projekt-Key; [AppShell.tsx:585](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/components/AppShell.tsx:585) rendert ebenfalls nur `<Outlet />`. Isolierter RTL-Test mit echtem Router-Parameterwechsel bestätigt: Im Lauf von Projekt 2 wird Karte ID 1 aus Projekt 1 geöffnet, und `byNumber(2,700)` erfolgt nie. Keine Projektwechseltests in NightRunPage.test.tsx.
- Fix: Alle projektspezifischen Zustände und Caches an Projekt-ID binden oder Page bei ID-Wechsel remounten; laufende Ketten-/Upload-Antworten des alten Projekts ebenfalls verwerfen. Gleichnamige Nummern und laufende Requests im Regressionstest.

### F06 — P2: Verspäteter Board-Reload ersetzt nach Boardwechsel die aktuelle Ansicht

- Ort: [frontend/src/pages/BoardPage.tsx:112–123](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/pages/BoardPage.tsx:112); Effekt 128–132.
- Trigger: Auf Board A läuft SSE-/Fokus-Reload; Nutzer wechselt per BoardSwitcher zu B; B antwortet zuerst, anschließend trifft A ein. Analog zwei überlappende Reloads desselben Boards mit älterem Snapshot als letzter Antwort.
- Ursache/Auswirkung: `load()` hat weder Cancellation noch Request-Generation. Die alte Antwort ersetzt `board`, `cards`, `epics`, Projektkontext und Loading-State, obwohl URL und SSE-Abonnement bereits B meinen. Ein altes 404 kann sogar aus B wegnavigieren. Die danach angebotenen Aktionen können Karten bzw. Board A verändern, während Routing/andere Requests B verwenden.
- Beleg: Anders als AppShell/BoardSwitcher/BoardListPage fehlt hier ein Aktivitäts-/Kennungscheck vollständig. `projectIdRef` und `selectedCard` werden beim Boardwechsel ebenfalls nicht bereinigt. Isolierter RTL-Test mit Deferred Promise bestätigt: zuerst erscheint Board B, nach der verspäteten A-Antwort erscheint Board A, während der Router weiterhin `/boards/2` meldet. Bestands-ID-Wechseltests verhindern keine umgekehrte Antwortreihenfolge von `load`.
- Fix: Request-Generation je ID und Reload; Erfolg und Fehler nur für aktuellste Generation anwenden. Projektreferenzen/ausgewählte Karte bei Boardwechsel zurücksetzen und keine alten Boarddaten im neuen Kontext interaktiv lassen.

### F07 — P2: Karten sowie Vorhaben-, Projekt- und Boardkacheln lassen sich per Tastatur nicht öffnen

- Orte: [frontend/src/components/BoardView.tsx:773–778](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/components/BoardView.tsx:773); [frontend/src/pages/EpicsPage.tsx:244–248](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/pages/EpicsPage.tsx:244); [frontend/src/pages/ProjectsPage.tsx:170–175](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/pages/ProjectsPage.tsx:170); [frontend/src/pages/ProjectBoardsPage.tsx:162–165](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/pages/ProjectBoardsPage.tsx:162).
- Trigger: Navigation mit Tab und Enter/Leertaste; insbesondere Vorhaben ohne Anforderung oder Projekt/Board, das noch nicht im Board-Verlauf steht.
- Ursache/Auswirkung: MUI `Paper` rendert `div`; ausschließlich `onClick`, kein Fokusziel/Link/Keyboard-Handler. Vorhaben-Menü bietet nur Ausblenden, und der Badge erhält dort kein `onOpen`. Damit ist die zentrale Öffnen-Aktion der Kachel für Tastaturnutzer unerreichbar. Projekt-/Boardkacheln haben denselben Fehler. Auch normale BoardView-Karten haben nur Paper/onClick; speziell VIEWER sehen kein Kartenmenü und haben dort keinen Tastatur-Öffner.
- Beleg: EpicsPage-Test 763ff. bestätigt ausdrücklich, dass der nächste Tab direkt auf dem Menübutton landet; die Tastaturtests decken Menü und Anforderungsverweis ab, nicht das Öffnen des Vorhabens. `jsx-a11y` erkennt MUI-Kompositionen hier nicht automatisch.
- Fix: Echten fokussierbaren Link/Button für Titel/Öffnen anbieten; sekundäre Menü-/Delete-Aktionen getrennt halten, keine ineinander verschachtelten Buttons. Tab→Enter-Tests für alle vier Ansichten, ausdrücklich auch VIEWER-Karten.

### F08 — P2: Login verwirft Einladungslink samt Token

- Ort: [frontend/src/routes/ProtectedRoute.tsx:18](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/routes/ProtectedRoute.tsx:18); [frontend/src/pages/LoginPage.tsx:27–30](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/pages/LoginPage.tsx:27); geschützte Route in [frontend/src/App.tsx:90](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/App.tsx:90).
- Trigger: Nicht angemeldeter Empfänger öffnet `/invitations/accept?token=…`, meldet sich anschließend an.
- Ursache/Auswirkung: ProtectedRoute ersetzt die URL durch `/login`, ohne den ursprünglichen Pfad/Query zu sichern. Login navigiert anschließend immer zu `/`. AcceptInvitationPage wird nicht aufgerufen, die Einladung nicht angenommen; der Empfänger muss den ursprünglichen Mail-Link erneut öffnen. Auch normale Board-Deep-Links gehen verloren.
- Beleg: Kein `state.from`/Rücksprung in beiden Komponenten; die Accept-Seite ist vollständig hinter ProtectedRoute. Separate Seiten-Tests decken diesen zusammengesetzten Ablauf nicht ab.
- Fix: Internen ursprünglichen Pfad einschließlich Query im Login-Flow erhalten und nach Login zurückkehren; für Registrierungs-/Verifikationsstrecke ebenfalls Einladungsbezug erhalten. Nur lokale erlaubte Rücksprungziele verwenden.

### F09 — P2: Karten aus Listenansicht haben leere Zuständigen- und Label-Auswahl

- Ort: [frontend/src/pages/BoardListPage.tsx:580–587](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/pages/BoardListPage.tsx:580).
- Trigger: Bearbeitungsberechtigter öffnet eine Karte aus der Board-Liste, deren Board Labels und Projekt Mitglieder besitzt.
- Ursache/Auswirkung: Der Modal-Aufruf setzt `canEdit`, übergibt aber weder `members` noch `boardLabels`. Die Modal-Defaults sind `[]`, beide Autocompletes bleiben trotzdem aktiv; bestehende Zuweisungen/Labels erscheinen als leer und es kann niemand bzw. kein Label ausgewählt werden. `labels` wird in derselben Seite bereits für den Filter geladen, jedoch hier nicht weitergereicht.
- Beleg: [CardDetailModal.tsx:944–945](/Users/manfredwolff/ki-projects/kanban-kit/frontend/src/components/CardDetailModal.tsx:944) Defaults; `AssigneeSection`/`LabelSection` berechnen ihre sichtbaren Werte als `members.filter(...)`/`boardLabels.filter(...)`. BoardListPage hat keine membersApi-Abfrage. Bestands-Tests der Listen-Seite prüfen Label-Filter, nicht die echte Edit-Auswahl.
- Fix: Projektmitglieder laden, vorhandene Labels übergeben und Bearbeitungsoptionen bis zu vollständigem Kontext sperren bzw. bei Ladefehler lesend lassen (bereits vorhandenes Muster CardNumberSearch).


## Weitere Hinweise — nicht in den 25 Hauptbefunden gezählt

### H01 — [P3] Sonar-Exclusions behandeln `**` nur wie eine einzelne Pfadebene

**Ort:** [scripts/sync-sonar-issues-to-board.mjs:57-62](/Users/manfredwolff/ki-projects/kanban-kit/scripts/sync-sonar-issues-to-board.mjs:57).

Die Funktion ersetzt zuerst den `**`-Platzhalter durch `.*` und ersetzt anschließend dessen Stern erneut durch `[^/]*`. `docs-site/**` wird so zu `/^docs-site\/.[^/]*$/` und matcht [docs-site/.vitepress/config.ts](/Users/manfredwolff/ki-projects/kanban-kit/docs-site/.vitepress/config.ts) nicht. Dasselbe gilt für `target/site/jacoco/jacoco.xml`. Tatsächlich mit unverändert aus dem Skript extrahierter Funktion ausgeführt; die flache Migration [src/main/resources/db/migration/V1__baseline.sql](/Users/manfredwolff/ki-projects/kanban-kit/src/main/resources/db/migration/V1__baseline.sql) wird hingegen korrekt ausgeschlossen.

Auswirkung nur, wenn solche verschachtelten Pfade als offene/historische Sonar-Findings zurückkommen: Trotz konfiguriertem Ausschluss werden Karten angelegt. Der aktuelle Scanner-Scope reduziert das Auftreten; daher P3, kein gesicherter aktueller Board-Schaden. Fix: einzelne Sterne ersetzen, solange `**` noch als Platzhalter vorliegt, dann den Platzhalter in `.*` auflösen; Fälle über mehrere Ebenen und Root-Dateien testen.

### H02 — Bedingtes [P2], Security-Härtung: gültige Reset-Tokens bleiben in Pending-Outbox rückgewinnbar

**Ort:** [src/main/java/org/mwolff/manban/auth/infrastructure/mail/OutboxPasswordResetMailer.java:28-33](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/auth/infrastructure/mail/OutboxPasswordResetMailer.java:28); entsprechend Verification- und Invitation-Mailer. [common/PayloadFields.java:24-28](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/common/PayloadFields.java:24) nutzt URL-Encoding, keine Verschlüsselung.

Ein Angreifer mit **DB-Lesezugriff oder aktuellem DB-Snapshot**, der einen noch gültigen Pending-Reset erwischt, kann die vollständige URL dekodieren und den Reset ausführen. Ein normalerweise kurzes Pending-Fenster wird bei gestopptem Worker/SMTP-Ausfall länger; spätere Payload-Leerung schützt bereits existierende Snapshots nicht. Das ist keine Behauptung einer über die normale API erreichbaren Schwachstelle.

**Explizite Gegenstelle:** [CLAUDE-security.md:102](/Users/manfredwolff/ki-projects/kanban-kit/CLAUDE-security.md:102) verlangt Token-Hash-only in der DB; [OutboxMessage.java:6-9](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/outbox/application/OutboxMessage.java:6) verbietet gerade diese Klartext-URL-Payloads und behauptet Rekonstruktion aus Referenzen. Die aktuelle Implementierung verletzt beides; [OutboxPasswordResetMailerTest.java:35](/Users/manfredwolff/ki-projects/kanban-kit/src/test/java/org/mwolff/manban/auth/infrastructure/mail/OutboxPasswordResetMailerTest.java:35) testet sogar die vollständige Rückgewinnung. Das Leeren in DONE/FAILED ist vorhanden und wurde berücksichtigt. Keine Ausnahme vom Hash-only-Vertrag in den gelesenen Regeln gefunden.

**Konkreter Fix unter Erhalt asynchroner Zustellung:** Pending-URL mittels authentifizierter Verschlüsselung (AEAD mit Nonce/Key-ID) speichern und nur im Worker entschlüsseln; Schlüssel ausschließlich außerhalb der DB über Secret-Konfiguration halten und Rotation vorsehen. Alternativ den Token-Versandvertrag vollständig auf referenzierte, sicher rekonstruierbare Daten umstellen. Klartext-/reversible Tokenfreiheit der DB-Payload als Test, erfolgreicher Versand und Retry weiterhin als Tests. Im Gesamtbericht getrennt von den bestätigten Funktionsfehlern darstellen, da es die zusätzliche Voraussetzung DB-Lesezugriff braucht.

### H03 — P3: AGENTS.md verweist auf fehlende Regeln und nennt eine veraltete Java-Version

AGENTS.md verweist auf `.Codex/Codex-workflow.md` und `Codex-java.md`/`Codex-react.md`/`Codex-security.md`; diese Dateien existieren im geprüften Checkout nicht. Der aktuelle Regelbestand heißt `.claude/CLAUDE-workflow.md` und `CLAUDE-*.md`. AGENTS.md nennt Java 21, während POM, Docker, CI und Java-Guide konsistent Java 25 verwenden. Das ist kein aktueller Compilerfehler, aber ein falscher Einstieg für neue Mitwirkende und Agenten. Verweise und Baseline auf den tatsächlichen Regelbestand angleichen und gebrochene lokale Dokumentationslinks prüfen.


## Bekannte offene Themen und Abgrenzung

Die Issue-Nummern stammen aus dem Projekt-Memory und dem Code; der aktuelle Live-Boardstatus wurde nicht erneut abgefragt.

- #783 bestätigt: EpicsPage lädt ohne Loading-/Fehlerzustand, `then`-Ketten haben keinen Fehlerpfad; initial bzw. bei Fehler erscheint „Noch keine Vorhaben.“. Nicht als neuer Fund gezählt.
- #790 bestätigt als bestehende Querschnittslücke: viele Mutationen haben keine oder generische Meldungen, z. B. BoardView Move rollt ohne Hinweis zurück, BoardPage Rename lässt Promise-Rejection durch. Kein zusätzlicher pauschaler neuer Fehlerbehandlungsfund.
- #787 (fachlich noch offen laut Projekt-Memory): Präfixe [Fachlich]/[Plan] werden für Zusammensetzung erkannt, Statuswechsel bleibt an Boardspalten gebunden; als bekannte fachliche Weiterentwicklung behandelt, kein zusätzlicher Bug behauptet.
- Pool-Delete/Archive-Probleme sind unter B07 behandelt. Die Pool-Oberfläche bietet derzeit keinen direkten Delete-Pfad; kein duplizierter Frontend-Befund.
- Projektinterner Transfer ist serverseitig für CARD_MOVE freigegeben, daher IdeaPlanningBoard-Transfer für MEMBER kein Rechte-Bypass. Cross-Projekt bleibt serverseitig Owner/Admin-geprüft.
- Nachtlauf-null/undefined-Kontrakt wurde geprüft: `ausSicht` normalisiert die nullable Felder korrekt, der alte #734-Verdacht trifft nicht mehr zu.
- CardNumberSearch lädt gesonderten Bearbeitungskontext und verwirft veraltete Kontextantworten; fehlende Boardvorräte sperren Labels/Epics. Kein dortiger pauschaler Datenverlustverdacht.


## Noch zu verifizierende Kandidaten

Diese drei Kandidaten gehören nicht zu den 25 bestätigten Befunden. Für ihre endgültige Priorisierung fehlen noch gezielte Parallelitäts-/Vertragstests; die genannten P2-Wertungen sind vorläufig.

- **P2, Herkunftszyklus durch paralleles PATCH:** [CardService.java:886–889](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/card/application/CardService.java:886) prüft und speichert ohne gemeinsame Graphsperre. T1 setzt A←B und T2 B←A; beide lesen den noch kantenlosen Zustand und schreiben unterschiedliche Zeilen, daher greifen weder FK noch übliche Zeilenupdates gegeneinander. V26 verhindert Zyklen nicht. `CardDerivedFromWriteIT.zyklus_wirdAbgelehnt` ist sequenziell. Fix: im Projekt serialisieren, nach Sperre frisch lesen und Zyklus prüfen. Das robuste Ring-Rendering verhindert Endlosschleifen, aber erfüllt nicht den zugesagten Ablehnungsvertrag.
- **P2, boardfremde Labels nach Transfer:** [CardService.java:1102–1119](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/card/application/CardService.java:1102) bereinigt Dependencies/Assignees bei Projektwechsel und Parent, aber niemals `card_label`. V12 erzwingt keine Boardgleichheit zwischen Karte und Label. Labels aus A bleiben an einer Karte auf B gespeichert, werden dort wegen Labeldefinitionen von B unsichtbar und erscheinen bei Rücktransfer wieder; normales Entfernen eines weiteren Labels über die UI kann wegen mitsendeter alter IDs mit `InvalidLabelException` scheitern. Pool → anderes Board trägt denselben Altbestand. Fix: boardlokale Labelzuordnungen bei Boardwechsel bereinigen/definiert abbilden.
- **P2, NightRun-Ringpuffer ist nebenläufig nicht begrenzt:** [NightRunService.java:65–80](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/nightrun/application/NightRunService.java:65) und [NightRunRepositoryAdapter.java:69–73](/Users/manfredwolff/ki-projects/kanban-kit/src/main/java/org/mwolff/manban/nightrun/infrastructure/persistence/NightRunRepositoryAdapter.java:69): Zwei Transaktionen legen unterschiedliche Runs an und führen je den Retention-DELETE aus, während die fremde neue Zeile noch unsichtbar ist. Bei vorher `keep−1` Runs löscht keiner; nach beiden Commits bleiben `keep+1`. Kein Lock auf Projekt/Namespace. Fix: Einfügen plus Trimmen pro Projekt serialisieren. Bestehende Tests prüfen Eindeutigkeit und Trimmen sequenziell.


## Gegenprüfungen und vorhandene Absicherungen

- `tbx issue create` erstellt eine nummerierte Pool-Idee; `issue get/comment/move` finden sie über die Board-Liste bis zum menschlichen Einplanen nicht. Dies ist in `KanbanCompatService:112-129` als bewusster Pool-/Freigabe-Vertrag beschrieben. Die CLI könnte Pool-Status und eine passende URL statt `/kanban` erklären; kein ungeprüftes Umschalten auf `direct:true` empfehlen.
- Outbox-Schreibweg verlangt MANDATORY, fachlicher Rollback nimmt Einträge mit; der Dispatcher ist eigener Spring-Bean und verwendet je Eintrag Transaktion + `FOR UPDATE SKIP LOCKED`. Kein Self-Invocation-/Global-Batch-Rollback-Fund.
- Anhanglöschung und Karten-/Board-Purge sind bereits mit synchronen Ereignissen und transaktionaler Outbox abgesichert. Der Projekt-Purge ist die konkrete Lücke, kein pauschaler Vorwurf gegen sämtliche Deletes.
- MinIO-Upload schreibt Metadaten vor dem Blob-Put. Das dokumentierte Restfenster nach erfolgreichem Put und vor DB-Commit erzeugt nur eine Blob-Waise, die Reconciliation melden kann; keine neue Bean-State-Logik allein dafür gefordert.
- Modulgrenzen sind durch reguläre JUnit-ArchUnit-Tests mit Fassaden-Whitelist, frameworkfreier Domain und Zyklenprüfung abgesichert; Security-Wiring und Event-Übersetzung liegen bewusst im Composition Root.
- Deployment nutzt nur Push nach production auf self-hosted Runner; kein Fork-PR-Codeausführungsbefund.
- Java 25 ist in POM, Docker und CI konsistent. Die Java-21-Angabe in AGENTS.md ist veraltet, kein aktueller Buildfehler.
- Vorhandene PIT-/Coverage-Ausschlüsse sind dokumentiert und wurden nicht allein wegen ihrer Existenz als Fehler gewertet.

- SQL-Konkatenationen in Positions-/Sortierabfragen tragen nur generierte Platzhalter oder geschlossene Enum-Schlüsselwörter; Nutzereingaben bleiben gebunden. Kein bestätigter SQL-Injection-Fund.
- Auth-Einmaltokens werden bei Verify/Reset atomar in SQL verbraucht; der bekannte Doppelkonsumfehler ist dort bereits geschlossen.
- Card-/Column-Positionsvergabe und die meisten Reindexes besitzen gezielte Namespace-Sperren, DB-Unique-Backstops und Concurrency-ITs; keine pauschale Behauptung fehlender Nebenläufigkeitssicherheit.
- Kommentare fremder Autoren können auch vom Admin nicht editiert werden; Projektmitgliedschaft wird geprüft. Archivierte Boards erlauben Kartenkommentare/-anhänge bewusst, das ist dokumentiertes Verhalten.
- Anhang-Download erzwingt Attachment-Disposition, MIME wird serverseitig ermittelt. Card-/Board-Purge plant Blob-Löschung vor DB-Cascade transaktional ein. **Projekt-Harddelete als fehlende weitere Kaskade bestätigt, aber vom Architekturreviewer separat ausgearbeitet.**
- Outbox benutzt `ON CONFLICT DO NOTHING` und `FOR UPDATE SKIP LOCKED`; SMTP-Hänger besitzen konfigurierte Zeitgrenzen. Mindestens-einmal-Zustellung und Single-Node-SSE sind dokumentierte Einschränkungen, keine als Bugs ausgegebenen Designentscheidungen.
- Externe Dependency-Nummern im Ingest sind bewusst zulässig; Herkunftsnummern dagegen bewusst existent/project-scoped. Die beiden Verträge wurden nicht verwechselt.

## Empfohlene Umsetzungsreihenfolge

| Paket | Befunde | Fachlicher Zusammenhang / Abnahme |
|---|---|---|
| 1. Zugang und sicherer Betrieb | A01, B01–B03, B09–B11 | Token-Reichweite und Widerruf, Zugriff nach Reset, aktiver Admin und wirksames Abschalten der Löschautomatik nachweisen |
| 2. Karten bearbeiten | F01–F03 | bestätigten Zustand und Entwurf sauber trennen; zwei aufeinanderfolgende Aktionen und Abbruch testen |
| 3. Karten-Lifecycle | B04–B08, A02 | Pool/Board/Archiv/Papierkorb/Vorhaben als konsistente Zustandsübergänge prüfen; keine stillen Wiederherstellungen oder verwaisten Dateien |
| 4. Projekt-/Boardwechsel | F05–F06, F09 | Kontext und laufende Antworten korrekt wechseln; Modal immer mit vollständigem Bearbeitungskontext |
| 5. Verlässliche Abläufe | A03–A05, F04, F08 | ungeprüfte Commits/fehlgeschlagene Starts erkennen, Nachtlaufabbruch erhalten, Einladung nach Login fortsetzen |
| 6. Bedienbarkeit und Restpunkte | F07, H01–H03, bekannte Themen | Tastaturpfade, Dokumentation und gesondert zu bewertende Härtung |

Die Pakete sind eine Priorisierungshilfe, noch keine fertig geschnittenen Issues. Insbesondere das Lifecycle-Paket sollte eine kleine Zustandsmatrix erhalten: CARD/EPIC × Board/Pool × aktiv/archiviert/gelöscht; erlaubte Übergänge, Zeitstempel, Beziehungen, Spaltenhistorie und Seiteneffekte je Übergang. Die heutige Wiederholung ähnlicher Mutationsteile in CardService und separater Zustände im Detailmodal erklärt mehrere Befunde, rechtfertigt aber keinen ungezielten Komplettumbau.

### Nachweise der zusätzlichen Diagnosen

| Probe | Beobachtetes Fehlverhalten | Befund |
|---|---|---|
| `boundPatCanReadAndRenameBoardInOtherProject` | Token für A liest und benennt Board B um | B01 |
| `staleLastUsedWriteResurrectsRevokedToken` | Snapshot lesen → echter Widerruf in zweiter Transaktion → Last-used-Merge; anschließend Token wieder gültig | B02 |
| `disablingOtherAdminThenSelfDemotionLeavesNoUsableAdmin` | Sperren + Selbstdegradierung lässt keinen nutzbaren Admin übrig | B03 |
| `normalPatchRestoresSoftDeletedCard` | normaler PATCH setzt `deleted_at` auf NULL | B04 |
| `replannedBacklogCardIsArchivedUsingOldDoneTimestamp` | zwei offene Transitions; Backlog-Karte wird nach alter Done-Zeit archiviert | B05/B06 |
| `deletingPoolIdeaReturns500AndRollsBack` | HTTP 500; Idee bleibt unverändert | B07 |
| `epicDisappearsThroughPoolEndpointAndLeavesChildOnOldBoard` | EPIC verschwindet aus Listen; Kind bleibt beim Boardwechsel zurück | B08 |
| `publishedDefaultSecretAuthenticatesAsAdminWithoutLogin` | Default-Cookie erhält Admin-Zugriff ohne Login | B09 |
| `existingSessionSurvivesPasswordReset` | altes Cookie erreicht nach Reset weiterhin Admin-API | B10 |
| `projectDeleteLeavesAttachmentBlobWithoutMetadata` | echter Blob bleibt ohne Metadaten/Löschauftrag zurück | A02 |
| RTL: gespeicherter Titel + Checkbox | zweite Mutation sendet alten Titel | F01 |
| RTL: Beschreibung ändern + Abbrechen + Checkbox | verworfener Entwurf wird gespeichert | F02 |
| RTL: eingerücktes Codebeispiel vor Taskliste | Klick verändert den Marker im Codebeispiel | F03 |
| RTL: gleicher Kartenindex nach Projektwechsel | Karte aus altem Projekt wird ohne neue Auflösung geöffnet | F05 |
| RTL: A antwortet nach B | URL zeigt B, Inhalt zeigt A | F06 |

Die PAT-Nebenläufigkeitsprobe stellt gezielt die Repository-/Transaktionsoperationen des Auflösungswegs nach; sie ist keine zeitabhängige Lastprobe zweier gleichzeitig abgeschickter HTTP-Requests. Die Retention-Probe ruft den originalen Retention-Service mit einem kontrolliert in die Zukunft verschobenen Prüfzeitpunkt auf.

Lokale Diagnoseartefakte dieser Sitzung: [/tmp/ReviewProbeIT.java](/tmp/ReviewProbeIT.java), [/tmp/kanban-review-probes.log](/tmp/kanban-review-probes.log), [/tmp/kanban-frontend-review-check/reproductions.test.tsx](/tmp/kanban-frontend-review-check/reproductions.test.tsx) und zugehörige Vitest-Konfiguration. Sie sind temporär und gehören nicht zum Produkt; die Szenarien stehen deshalb zusätzlich vollständig in diesem Bericht. Protokolle der Baseline liegen unter [/tmp/kanban-review-maven.log](/tmp/kanban-review-maven.log), [/tmp/kanban-review-pit.log](/tmp/kanban-review-pit.log) und `/tmp/kanban-review-frontend-{build,lint,tests}.log`.

## Künftige Reviews gegen einen gepushten Stand

Das geht. Pro Review sollte der Basis-Commit ausdrücklich festgehalten werden. Für den noch nicht gepushten Commit-Batch auf main ist nach einem bewusst ausgeführten Fetch der Vergleich `git diff origin/main...HEAD` geeignet. Die Drei-Punkt-Form beginnt am gemeinsamen Vorfahren und zeigt die Änderungen des aktuellen Branches.

Wenn exakt zwei bereits festgelegte Zustände verglichen werden sollen, verwendet man `git diff <basis-sha> <ziel-sha>`. Uncommittete Änderungen sind darin nicht enthalten; bei Bedarf werden `git diff` und `git diff --cached` separat aufgenommen. Nur `origin/main...HEAD` nach einem bereits erfolgten Push zu verwenden, kann einen leeren Diff liefern — deshalb die frühere Basis-SHA vorab speichern.

Für Folgereviews reichen geänderte Dateien als Einstieg. Aufrufer, API-Gegenstellen, Migrationen und Lifecycle-Invarianten müssen bei Bedarf mitgelesen werden. Dieser Bericht ist die Bestandsliste; ein späterer Diff-Review beweist nicht automatisch, dass alle hier offenen Punkte erledigt sind.
