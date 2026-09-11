# Architektur-, Infrastruktur- und CLI-Review

Stand: `aa1610afd33f9f0ae4ad51cf9528030bd33d2240`, 2026-09-09. Nur gelesen; Reproduktionen in temporären Verzeichnissen. Kein Produktcode geändert, kein Commit/Push, keine externen Kommentare. Alle Pfade beziehen sich auf `/Users/manfredwolff/ki-projects/kanban-kit`.

## Bestätigte Befunde

### A1 — [P1] Compose ignoriert den dokumentierten Schalter zum Abschalten endgültiger Löschungen

**Ort:** `docker-compose.yml:32-54` (fehlende Weitergabe im `manban-api.environment`), insbesondere `:52-54` am Ende der Liste. Das Produktions-Overlay ergänzt die Werte ebenfalls nicht.

**Trigger:** Betreiber setzt entsprechend `docs/betrieb.md:61-84` `MANBAN_CLEANUP_ENABLED=false` in `.env` bzw. `MANBAN_DONE_RETENTION_DAYS=0` und erstellt die Container neu. Compose verwendet `.env` zur Interpolation; nicht referenzierte Variablen werden ohne `env_file` nicht Container-Umgebung.

**Auswirkung:** `src/main/resources/application.yml:81-84` fällt auf Cleanup aktiv / 30 Tage zurück. `card/infrastructure/TrashRetentionJob.java` bleibt aktiv und löscht ältere Papierkorb-Karten endgültig, obwohl der Betreiber die Automatik deaktiviert hat. Auch `MANBAN_OUTBOX_ENABLED`, Poll-Intervall, Max-Attempts und Retention sowie `MANBAN_MINIO_BUCKET` erreichen den Container nicht.

**Beleg:** Tatsächliches `docker compose --env-file /dev/null config --format json`, ausgeführt mit CLEANUP=false, OUTBOX=false und DONE_RETENTION_DAYS=0: alle drei Schlüssel fehlen im aufgelösten `services.manban-api.environment`. Der Test verwendete keine Produktions-Secrets und startete keine Container. Keine alternative `env_file`-Weitergabe in Basis oder Overlay vorhanden.

**Fix:** Alle unterstützten und dokumentierten Betriebsvariablen explizit durchreichen (oder ein sorgfältig passendes `env_file` verwenden). Compose-Vertragstest: benutzerdefinierte Werte müssen nach Zusammenführen von Basis und Prod-Overlay in `manban-api.environment` erscheinen; darunter ausdrücklich `false` und `0`. Spring-Tests allein erkennen die fehlende Transportstrecke nicht.

### A2 — [P2] Projektlöschen umgeht die transaktionale Blob-Löschung

**Ort:** `src/main/java/org/mwolff/manban/project/application/ProjectService.java:166-169`.

**Trigger:** Plattform-Admin löscht ein Projekt, das Karten oder Pool-Ideen mit Anhängen enthält.

**Auswirkung:** Die DB-Cascade entfernt Board/Karte/`attachment_meta`; MinIO-Dateien bleiben dauerhaft zurück. Metadaten samt Bezug zum gelöschten Projekt sind dann weg. Speicherverbrauch und aufbewahrte Nutzerdateien wachsen nach jedem Projektlöschen weiter.

**Beleg:** `ProjectRepositoryAdapter.deleteById` delegiert direkt an `jpa.deleteById`. Es gibt nur `ProjectCreatedEvent`, kein Project-Purge-Ereignis. Die Blob-Outbox entsteht über `AttachmentPurgeListener.onCardsPurged`; `CardsPurgedEvent` kommt aus Einzel-/Trash-Purge und `BoardPurgeCascade`, nie aus dem Projektlöschen. Die Flyway-FKs löschen Metadaten, kein Trigger erstellt Outbox-Aufträge. `StorageReconciliationService` berichtet nur und räumt bewusst nicht automatisch auf. `AttachmentIT` prüft Karte/Board/Trash-Purge, aber nicht Projekt-Purge.

**Fix:** Vor dem Projekt-Delete synchron ein Projekt-Purge-Ereignis veröffentlichen und im Kartenmodul alle Karten des Projekts (inkl. boardloser Ideen, Archiv, Papierkorb) in die bestehende `CardsPurgedEvent`-Kette überführen. Aufträge und Delete in derselben Transaktion. IT: Projekt mit Board- und Pool-Anhang löschen, Outbox abarbeiten, beide Objekte entfernt; Rollback muss die Löschaufträge zurückrollen.

### A3 — [P2] Commit-Gate lässt eine nach den Checks vorgenommene Dateilöschung durch

**Ort:** `.githooks/gate.mjs:123-129`, konkret `:128`.

**Trigger:** Getrackte Datei ändern, Checks grün ausführen, anschließend die Datei löschen und die Löschung stagen, ohne nochmals zu prüfen.

**Auswirkung:** Ein Commit kann einen nicht geprüften und bereits defekten Stand enthalten. Dies benötigt weder `--no-verify` noch eine manipulierte Nachweisdatei.

**Beleg / tatsächliche Reproduktion:** In isoliertem Temp-Git-Repository wurden das echte Gate und die echte installierte `checks.mjs` kopiert. `required.txt` war verändert und vorhanden; konfigurierte Prüfung `node -e "require('fs').readFileSync('required.txt')"` war grün. Die Zusammenfassung enthielt Hash `a9c0225b2e00b102af489a25e72f286f80796a4d` für diese Datei. Anschließende Löschung und `git add -u`: **Gate Exit 0**, derselbe echte Check danach **Exit 1**. `checks.mjs:408-432` unterscheidet geprüfte Löschungen mit `null`; das Gate ignoriert diesen Tombstone und prüft bei `D` lediglich, ob der Pfad irgendwann im geprüften Satz stand. Keine bestehende Vorprüfung fängt diesen Ablauf ab.

**Fix:** Für `D` nur `geprueft[pfad] === null` zulassen; ein vorhandener Hash muss als Änderung nach dem Prüflauf abgelehnt werden. Regressionstest mit genau diesem Ablauf und Gegenfall (bereits vor den Checks gelöscht → zulässig). Der eingebettete Gate-Blob in `install.mjs` ist bytegleich und muss über den Upstream-/Blob-Generierungsweg ebenfalls aktualisiert werden.

### A4 — [P2] Der Produktionsstack startet nach Host-/Docker-Neustart nicht wieder

**Ort:** `docker-compose.prod.yml:13-15` und Basisdefinitionen `docker-compose.yml:2-30`.

**Trigger:** VPS wird für ein Systemupdate oder nach einem Ausfall neu gestartet; alternativ ein App-Prozess beendet sich unerwartet.

**Auswirkung:** Postgres, MinIO und API bleiben gestoppt, bis jemand manuell `docker compose up -d` ausführt. Eine aktive Docker-/Traefik-Installation und der als Dienst gestartete GitHub-Runner starten den Compose-Stack nicht selbst. Der Deploy-Workflow wird nur durch einen Push auf `production` ausgelöst.

**Beleg:** Zusammengeführte Compose-Definitionen enthalten für keinen Dienst `restart` oder eine andere Neustartregel. `docker compose config` bestätigt das; `docs/deployment-hostinger.md` beschreibt keinen unabhängigen systemd-Dienst für den Stack. Es wurde kein Host-Neustart ausgeführt; Befund beruht auf der vollständigen ausgelieferten Startkonfiguration.

**Fix:** Im Produktions-Overlay mindestens Postgres, MinIO und API mit einer passenden dauerhaften Restart-Policy (`unless-stopped`) versehen; externes Traefik hat seinen eigenen Lebenszyklus. Wiederanlauf nach Docker-/Host-Neustart im Betriebs-Smoke-Test belegen.

### A5 — [P2] Deploy meldet Erfolg, bevor Spring erfolgreich hochgefahren ist

**Ort:** `.github/workflows/deploy.yml:26`.

**Trigger:** Container lässt sich starten, Spring scheitert danach beim Boot, etwa durch fehlgeschlagene Flyway-Migration oder fehlerhafte DB-Zugangsdaten eines bestehenden Postgres-Volumes.

**Auswirkung:** GitHub zeigt den Produktions-Deploy als erfolgreich, während die App anschließend beendet ist und der Proxy Fehler liefert. Das automatisierte Deployment führt die manuelle Verifikation der Betriebsanleitung nicht aus.

**Beleg:** Letzter Schritt ist ausschließlich `docker compose ... up -d --build`. Basis-Compose besitzt Healthchecks nur für Postgres/MinIO, die API hat keinen Healthcheck. Weder Dockerfile noch Workflow enthalten Readiness-/HTTP-Prüfung, Warteschleife oder `--wait`. Die Aussage ist statisch belegt; absichtlich defekte Container wurden nicht auf Nutzer-/Produktionssystemen gestartet.

**Fix:** App-Readiness prüfen, etwa API-Healthcheck plus `up --wait --wait-timeout ...` oder begrenztes HTTP-Polling nach dem Start. Fehlstart muss Exit ungleich 0 liefern; bei Fehler passende Container-Logs als Diagnose sichern. Integrationstest mit absichtlich fehlerhafter Startup-Konfiguration.

## Weitere belegte, niedriger gewichtete Hinweise

### A6 — [P3] Sonar-Exclusions behandeln `**` nur wie eine einzelne Pfadebene

**Ort:** `scripts/sync-sonar-issues-to-board.mjs:57-62`.

Die Funktion ersetzt zuerst den `**`-Platzhalter durch `.*` und ersetzt anschließend dessen Stern erneut durch `[^/]*`. `docs-site/**` wird so zu `/^docs-site\/.[^/]*$/` und matcht `docs-site/.vitepress/config.ts` nicht. Dasselbe gilt für `target/site/jacoco/jacoco.xml`. Tatsächlich mit unverändert aus dem Skript extrahierter Funktion ausgeführt; die flache Migration `src/main/resources/db/migration/V1__baseline.sql` wird hingegen korrekt ausgeschlossen.

Auswirkung nur, wenn solche verschachtelten Pfade als offene/historische Sonar-Findings zurückkommen: Trotz konfiguriertem Ausschluss werden Karten angelegt. Der aktuelle Scanner-Scope reduziert das Auftreten; daher P3, kein gesicherter aktueller Board-Schaden. Fix: einzelne Sterne ersetzen, solange `**` noch als Platzhalter vorliegt, dann den Platzhalter in `.*` auflösen; Fälle über mehrere Ebenen und Root-Dateien testen.

### A7 — Bedingtes [P2], Security-Härtung: gültige Reset-Tokens bleiben in Pending-Outbox rückgewinnbar

**Ort:** `src/main/java/org/mwolff/manban/auth/infrastructure/mail/OutboxPasswordResetMailer.java:28-33`; entsprechend Verification- und Invitation-Mailer. `common/PayloadFields.java:24-28` nutzt URL-Encoding, keine Verschlüsselung.

Ein Angreifer mit **DB-Lesezugriff oder aktuellem DB-Snapshot**, der einen noch gültigen Pending-Reset erwischt, kann die vollständige URL dekodieren und den Reset ausführen. Ein normalerweise kurzes Pending-Fenster wird bei gestopptem Worker/SMTP-Ausfall länger; spätere Payload-Leerung schützt bereits existierende Snapshots nicht. Das ist keine Behauptung einer über die normale API erreichbaren Schwachstelle.

**Explizite Gegenstelle:** `CLAUDE-security.md:102` verlangt Token-Hash-only in der DB; `OutboxMessage.java:6-9` verbietet gerade diese Klartext-URL-Payloads und behauptet Rekonstruktion aus Referenzen. Die aktuelle Implementierung verletzt beides; `OutboxPasswordResetMailerTest.java:35` testet sogar die vollständige Rückgewinnung. Das Leeren in DONE/FAILED ist vorhanden und wurde berücksichtigt. Keine Ausnahme vom Hash-only-Vertrag in den gelesenen Regeln gefunden.

**Konkreter Fix unter Erhalt asynchroner Zustellung:** Pending-URL mittels authentifizierter Verschlüsselung (AEAD mit Nonce/Key-ID) speichern und nur im Worker entschlüsseln; Schlüssel ausschließlich außerhalb der DB über Secret-Konfiguration halten und Rotation vorsehen. Alternativ den Token-Versandvertrag vollständig auf referenzierte, sicher rekonstruierbare Daten umstellen. Klartext-/reversible Tokenfreiheit der DB-Payload als Test, erfolgreicher Versand und Retry weiterhin als Tests. Im Gesamtbericht getrennt von den bestätigten Funktionsfehlern darstellen, da es die zusätzliche Voraussetzung DB-Lesezugriff braucht.

## Bewusst nicht als Fehler berichtet / widerlegte Verdachte

- `tbx issue create` erstellt eine nummerierte Pool-Idee; `issue get/comment/move` finden sie über die Board-Liste bis zum menschlichen Einplanen nicht. Dies ist in `KanbanCompatService:112-129` als bewusster Pool-/Freigabe-Vertrag beschrieben. Die CLI könnte Pool-Status und eine passende URL statt `/kanban` erklären; kein ungeprüftes Umschalten auf `direct:true` empfehlen.
- Outbox-Schreibweg verlangt MANDATORY, fachlicher Rollback nimmt Einträge mit; der Dispatcher ist eigener Spring-Bean und verwendet je Eintrag Transaktion + `FOR UPDATE SKIP LOCKED`. Kein Self-Invocation-/Global-Batch-Rollback-Fund.
- Anhanglöschung und Karten-/Board-Purge sind bereits mit synchronen Ereignissen und transaktionaler Outbox abgesichert. Der Projekt-Purge ist die konkrete Lücke, kein pauschaler Vorwurf gegen sämtliche Deletes.
- MinIO-Upload schreibt Metadaten vor dem Blob-Put. Das dokumentierte Restfenster nach erfolgreichem Put und vor DB-Commit erzeugt nur eine Blob-Waise, die Reconciliation melden kann; keine neue Bean-State-Logik allein dafür gefordert.
- Modulgrenzen sind durch reguläre JUnit-ArchUnit-Tests mit Fassaden-Whitelist, frameworkfreier Domain und Zyklenprüfung abgesichert; Security-Wiring und Event-Übersetzung liegen bewusst im Composition Root.
- Deployment nutzt nur Push nach production auf self-hosted Runner; kein Fork-PR-Codeausführungsbefund.
- Java 25 ist in POM, Docker und CI konsistent. Die Java-21-Angabe in AGENTS.md ist veraltet, kein aktueller Buildfehler.
- Vorhandene PIT-/Coverage-Ausschlüsse sind dokumentiert und wurden nicht allein wegen ihrer Existenz als Fehler gewertet.

## Coverage-Inventar und Grenzen

| Bereich | Sichtung |
|---|---|
| Ausgelieferte Betriebsdefinitionen | Dockerfile, beide Compose-Dateien, Caddyfile, .dockerignore, .env.example vollständig; README/Betrieb/Hostinger-/Deployment-Spezifikation als Vertragsabgleich |
| CI/Build | Alle drei getrackten GitHub-Workflows, POM (Lifecycle, Abhängigkeiten, Gates, Frontend-/Docs-Bündelung), sonar-project.properties; Checkstyle/PMD/SpotBugs-Regelsets und Toolchains-Beispiel auf Einbindung/Ausnahmen geprüft |
| DB | Alle 29 Flyway-Migrationen auf FKs, Cascades, Constraints, Nummern-/Backfill- und Outbox-Vertrag gesichtet; bestehende Migrationen nicht verändert |
| Composition Root/Architektur | Alle config-Klassen + Application-Bootstrap; ArchitectureTest/Fassaden- und Zyklenregeln; relevante Aufrufer und Löschketten in project/board/card/attachment |
| Seiteneffekte | Gesamtes outbox-Modul (application/domain/infrastructure/persistence); Storage-Adapter/-Config, Upload/Delete/Reconciliation/Purge-Listener; alle Mail-Outbox-Adapter/-Handler; zugehörige Testfälle gezielt als Gegenbeleg geprüft |
| CLI/Skripte | cli/tbx.mjs und komplette Testsuite; alle sechs ausführbaren scripts/*.mjs (Versionsbump, Changelog, Release, Migration, Querverweise, Sonar-Sync) vollständig; keine externen Mutationen ausgeführt |
| Docs-Build | docs-site/package.json, copy-docs.mjs, .vitepress/config.ts, Anbindung an POM/Docker und SpaWebConfig |
| Installer/Hooks | install.mjs ausführbarer Installer-Teil vollständig, beide getrackten .githooks-Dateien vollständig; getrackte workflow.config.json; relevante checks.mjs-Integration/Tombstones zur Repro |

**Explizite Grenze:** `install.mjs` enthält mehrere hundert KB eingebetteten Upstream-Kit-Code und Skills. Die vier lokal ausgeschriebenen `.claude/kit/*.mjs` (~8.800 Zeilen) sind nicht getrackt; es handelt sich um ein weiteres Werkzeugprodukt. Bytegleichheit der sechs Code-/Hook-Blobs (board, checks, night, spec, gate, pre-commit) mit der installierten Fassung wurde verifiziert. Kein vollständiger semantischer Audit aller eingebetteten Board-Adapter, Nacht-Runner-/SDD-Implementierungen und Skilltexte vorgenommen. Nicht als „jeder Byte des gesamten Repos geprüft“ ausgeben. Frontend-/fachliche Backend-Details liegen bei den anderen Reviewern. Keine Live-Produktionsprüfung, kein Host-Reboot, kein Docker-Image-/CVE-Audit, kein Lasttest.

## Tatsächlich ausgeführte Verifikation

- `node --test cli/tbx.test.mjs`: 48 Tests grün, 0 Fehler.
- Compose-Konfigurationsrepro für Cleanup-/Outbox-/Retention-Weitergabe, ohne `.env` und ohne Containerstart: Fehler bestätigt.
- Isolierter echter Git-/Checks-/Gate-Ablauf für nachträgliche Löschung: Gate grün, konfigurierte Prüfung rot.
- Originale Sonar-Glob-Funktion in isoliertem Node-Kontext: verschachtelte Pfade nicht ausgeschlossen, flache Migration ausgeschlossen.
- Base64-Blobs im Installer dekodiert und bytegenau mit installierten Code-/Hook-Dateien verglichen: alle sechs identisch.
- Hauptagent meldet zusätzlich bereits grüne Baseline: 1.226 Java-Unit-Tests + 403 ITs, 1.676 Frontend-Tests in 106 Dateien. Diese Läufe wurden hier nicht nochmals ausgeführt.
