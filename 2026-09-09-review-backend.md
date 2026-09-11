# Backend-Review — aa1610afd33f9f0ae4ad51cf9528030bd33d2240

Unabhängige statische Prüfung vom 09.09.2026. Keine Produktdateien verändert. Gesamtchecks und temporäre Integration-Probes führt der Hauptreviewer aus. Rückmeldung des Hauptreviewers: B1, B2, B3, B4, B5, B6 und B7 wurden durch temporäre Integration-Probes gegen das tatsächliche Backend reproduziert. B2 reproduziert den originalen Repository-/Transaktionspfad mit gelesenem Snapshot, parallel ausgeführtem echtem `revoke` und anschließendem `lastUsedAt`-Save.

## Belastbare Befunde

### B1 — P1: Board-gebundene PATs erreichen die allgemeine API ohne Bindungsprüfung

- **Ort:** `src/main/java/org/mwolff/manban/config/SecurityConfig.java:84–85`; Gegenstellen `accesstoken/web/security/PatAuthenticationFilter.java:49–57`, `project/application/PermissionChecker.java:79–91`.
- **Trigger:** Ein Nutzer mit Zugriff auf Projekte A und B erstellt ein an Board A gebundenes PAT und verwendet es für `GET /api/projects`, `GET /api/boards/{boardB}/cards` oder normale mutierende Karten-/Projekt-Endpunkte.
- **Auswirkung:** Das PAT bekommt die kompletten Rechte seines Erstellers außerhalb der zugesagten Bindung; bei einem Plattformadmin reichen die allgemeinen Projekt-Endpunkte auch für Projektanlage/-löschung. Nur `/api/admin/**` und Tokenverwaltung verlangen eine Session. Die Bindung ist damit keine Sicherheitsgrenze.
- **Beleg:** Der Filter setzt `Long userId` als Principal, legt `KanbanPrincipal` nur in `details` ab. Die allgemeine API verlangt allein `authenticated()`, sämtliche dortigen Services autorisieren allein die User-ID. Nur die Compat-Schicht liest die Bindung. `docs/dogfooding.md:9–12` verspricht ausdrücklich eine Beschränkung wie bei einem Fine-grained-PAT. Ungebundene PATs sind hingegen bewusst für die allgemeine API vorgesehen (`docs/dogfooding.md:51–52`).
- **Testlücke:** `AccessTokenIT` prüft Authentifizierung und Bindungspersistierung sowie gesperrte Tokenverwaltung, nicht Zugriffe eines gebundenen PAT über die allgemeinen APIs auf andere Boards/Projekte.
- **Fix:** Gebundene PATs auf autorisierte Endpunkte/Bereiche einschränken oder die Bindung zentral in jede relevante Ressourcenautorisierung einbeziehen. Bewusst unterstützte ungebundene PATs getrennt behandeln; reine Compat-Guards reichen nicht.

### B2 — P1: Ein gleichzeitiger PAT-Zugriff kann den Widerruf dauerhaft zurücknehmen

- **Ort:** `src/main/java/org/mwolff/manban/accesstoken/application/AccessTokenService.java:139–145`; Gegenstelle `:123–130`.
- **Trigger:** Request T1 liest ein gültiges PAT (`revoked=false`) in `resolveBinding`. T2 widerruft es und committet. T1 schreibt danach `lastUsedAt` und committet.
- **Auswirkung:** Der alte `revoked=false`-Wert wird zurückgeschrieben; nach dem erfolgreich bestätigten Widerruf sind auch spätere neue Requests wieder authentifiziert. Es geht um dauerhafte Reaktivierung, nicht nur um einen bereits laufenden Request.
- **Beleg:** Beide Wege speichern ganze `AccessToken`-Records. `AccessTokenRepositoryAdapter.save()` erzeugt eine komplette neue `KanbanAccessTokenEntity`; die Entity hat weder Version noch DynamicUpdate noch Locks. Hibernate schreibt bei schmutziger Entity standardmäßig sämtliche aktualisierbaren Spalten, einschließlich `revoked`. Kein DB-Trigger/Constraint verhindert `true → false`.
- **Testlücke:** Der Ablauf create/use/revoke/use in `AccessTokenIT` ist sequenziell. Es fehlt ein erzwungener Interleaving-Test.
- **Fix:** Die Benutzung mit einem gezielten atomischen `UPDATE ... SET last_used_at=? WHERE ... AND revoked=false` vermerken und das Ergebnis für die Authentifizierung auswerten; Widerruf monoton machen. Alternativ gemeinsamen Lock-/Versionsschutz verwenden, ohne dass ein Telemetrie-Update den Widerruf überschreibt.

### B3 — P1: Der Aussperrschutz zählt gesperrte Admins als verbleibenden Zugang

- **Ort:** `src/main/java/org/mwolff/manban/auth/application/AdminService.java:53–58`; weitere Stelle `:103–112`; `auth/infrastructure/persistence/AppUserJpaRepository.java:35–38`.
- **Deterministischer Trigger:** A und B sind Plattformadmins. A sperrt B. A setzt anschließend seine eigene Plattformrolle auf USER.
- **Auswirkung:** Die Selbstdegradierung gelingt, weil noch zwei ADMIN-Zeilen gezählt werden. Danach ist der einzige verbleibende ADMIN B gesperrt: sämtliche administrativen Zugänge fallen aus. Bootstrap hilft nicht, weil es die existierende ADMIN-Zeile sieht und abbricht. Wiederherstellung benötigt direkten DB-Eingriff.
- **Zusätzlicher Trigger:** A und B können sich gleichzeitig gegenseitig sperren; `disable` sperrt/prüft keine gemeinsame Menge aktiver Admins.
- **Beleg:** `lockIdsByPlatformRole` filtert nur `platform_role`, nicht `disabled_at`. `disable` schützt allein gegen identische Actor-/Target-ID. `LoginService` und `DisabledUserGuardFilter` weisen gesperrte Konten tatsächlich ab, daher kompensiert kein nachgelagerter Pfad die Lücke.
- **Testlücke:** `RoleInvariantConcurrencyIT` testet die Owner-/Admin-Rolleninvarianten, aber nicht ihre Kombination mit Disabled-Zuständen. `AdminUserIT`/`AdminServiceTest` prüfen Sperren und Rollenwechsel getrennt.
- **Fix:** Als gemeinsame Invariante mindestens einen nutzbaren aktiven Plattformadmin erhalten; Degradieren und Sperren unter derselben serialisierenden Sperre entscheiden und Actor-Rechte nach der Sperre neu prüfen.

### B4 — P2: Speichern einer Papierkorb-Karte stellt sie unbeabsichtigt wieder her

- **Ort:** `src/main/java/org/mwolff/manban/card/infrastructure/persistence/CardEntity.java:97–105,125–128`; primärer Schreibpfad `CardRepositoryAdapter.java:59–60`.
- **Trigger:** Karte löschen, danach eine normale PATCH-Anfrage auf ihre weiterhin bekannte ID senden. Alternativ eine Herkunftskarte projektübergreifend transferieren, deren Kind im Papierkorb liegt: `doTransfer` lädt solche Kinder ausdrücklich mit und speichert `withDerivedFrom(null)`.
- **Auswirkung:** `deleted_at` wird NULL. Die Karte taucht ohne Restore-Aktion wieder im aktiven Board auf; wenn ihre frühere Position inzwischen belegt ist, scheitert stattdessen die gesamte Änderung/der Transfer mit 409. Die Lösch-/Retention-Semantik wird verletzt.
- **Beleg:** Der Domain-Record enthält `deletedAt` nicht, die Entity mappt das Feld jedoch schreibbar. Der Konstruktor aus `Card` setzt es nie, und `save` verwendet genau diese neue Entity beim Merge. `findById` ist ungefiltert; `findByDerivedFromCardId` und `findByRequirementCardId` schließen Papierkorb bewusst ein. DB-Constraints verhindern höchstens eine Positionskollision, erhalten aber keinen Löschzustand.
- **Testlücke:** `CardSoftDeleteIT` testet nur direkte SQL-Lifecycle-Methoden und Read-Filter; kein Domain-save nach Softdelete. Relevante Herkunftstests überprüfen die gelöschte Relation, nicht durchgängig den erhaltenen `deleted_at`-Wert.
- **Fix:** Den Löschzustand korrekt im Aggregat erhalten oder `deleted_at` beim normalen JPA-Schreibpfad ausdrücklich unveränderbar machen und nur über Lifecycle-Operationen ändern. Normale Updates gelöschter Karten fachlich prüfen/abweisen.

### B5 — P2: Done → Pool → Backlog behält den Done-Zeitstempel und wird falsch archiviert

- **Ort:** `src/main/java/org/mwolff/manban/card/domain/Card.java:199–210,229–241`; Aufrufer `CardService.java:1564–1568,1548–1551`.
- **Trigger:** Eine erledigte Karte aus Done in den Ideen-Pool zurücklegen und später erneut ins Backlog einplanen. Die Karte muss nur insgesamt älter als ihre Done-Retention werden.
- **Auswirkung:** Sie gilt weiterhin als erledigt, obwohl sie im Backlog liegt. Der Retention-Job archiviert sie nach dem alten Zeitpunkt. Auch Herkunftsbaum, Blockerberechnung und Dashboard verwenden den erhaltenen `movedToDoneAt` und liefern falsche Erledigungs-/Durchsatzwerte.
- **Beleg:** `asPooledIdea` und `withPlannedOnBoard` übernehmen den Timestamp unverändert. `moveToIdeaStorage` und `moveBackToPool` korrigieren ihn nicht. `CardJpaRepository.findArchivableDoneCards` prüft nur `archived=false`, `deleted_at is null`, Timestamp vorhanden/alt — weder Spalte noch Pool-Zustand. Beim normalen Move wird der Timestamp beim Verlassen von Done richtigerweise gelöscht.
- **Testlücke:** `ProjectIdeaIT` prüft den Pool-Rundweg ausschließlich mit einer nie erledigten Karte. `DoneRetentionIT` setzt Done-Timestamps direkt und verbindet ihn nicht mit dem Pool-Lifecycle.
- **Fix:** Beim Verlassen des Workflows in den Pool den aktuellen Done-Zustand aufheben; Einplanen muss zu der tatsächlichen Zielspalte passende Statusdaten erzeugen. Integrationstest Done → Pool → Backlog → Retention.

### B6 — P2: Ein erneutes Einplanen erzeugt überlappende offene Spaltenaufenthalte

- **Ort:** `src/main/java/org/mwolff/manban/card/application/CardService.java:1550–1551,1564–1568` (auch `moveToIdeaStorage`).
- **Trigger:** Karte liegt in Ready, wird für mehrere Tage in den Pool gelegt und wieder eingeplant. Wiederholung vermehrt die offenen Historienzeilen.
- **Auswirkung:** Der alte Ready-Aufenthalt bleibt während der gesamten Pool-Zeit und nach dem erneuten Einplanen offen. Die Karte hat danach gleichzeitig einen offenen Ready- und einen offenen Backlog-Aufenthalt. Beim nächsten Move werden beide mit demselben Zeitpunkt geschlossen; Verweildauer und Ausreißerzahlen im Dashboard werden verfälscht.
- **Beleg:** Pool-Wege rufen `transitions.closeOpen` nicht auf; `planOntoBoard` ruft bedingungslos `open` auf. V9 besitzt keinen Unique-Constraint für einen offenen Aufenthalt. `CardColumnTransitionRepositoryAdapter.closeOpen` schließt alle offenen Zeilen, `CardCycleTimeService` zählt alle gelieferten Aufenthalte. Damit ist es kein nur theoretischer inkonsistenter Record.
- **Testlücke:** `ProjectIdeaIT` überprüft Pool-/Board-Felder, aber weder Anzahl offener Transitionen noch Exklusion der Pool-Zeit aus der Spaltenverweildauer.
- **Fix:** Den tatsächlichen Spaltenaustritt beim Pool-Wechsel mit demselben Eventzeitpunkt schließen. Wiederholtes Einplanen nur für zulässige Pool-Zustände akzeptieren oder idempotent behandeln; anschließend genau einen Aufenthalt öffnen.

### B7 — P2: Die Löschaktion für boardlose Pool-Ideen endet immer mit 500

- **Ort:** `src/main/java/org/mwolff/manban/card/application/CardService.java:1701–1702`.
- **Trigger:** `DELETE /api/cards/{id}` für eine per Projekt-Ideen-Endpunkt angelegte boardlose Karte, mit korrekt berechtigtem Projektmitglied.
- **Auswirkung:** `softDelete` wird ausgeführt, dann wirft `requireBoardId()`; die Transaktion rollt zurück, die Idee bleibt dauerhaft liegen. Analog sind Archivieren, Wiederherstellen und endgültiges Löschen noch boardabhängig. Die exakte UI-Erreichbarkeit wird vom Frontendreviewer gegengeprüft.
- **Beleg:** `requireCardOp` autorisiert die boardlose Idee ausdrücklich projektbasiert. Danach wird bedingungslos ein Board-Event erzeugt, obwohl `publishChangedIfOnBoard` als anderer bereits korrekter Mechanismus existiert. Es gibt keinen separaten Lösch-Endpunkt für Pool-Ideen.
- **Testlücke:** `ProjectIdeaIT`/`ProjectIdeaEditIT` decken Anlegen, Editieren und Einplanen ab, nicht DELETE einer Idee.
- **Fix:** Den Lifecycle vollständig projektbasiert gestalten, Pool-Änderungen über das Ideen-Event melden und eine passende Papierkorb-/Restore-Sicht für boardlose Karten bereitstellen.

### B8 — P2: Pool-Endpunkte lassen Vorhaben aus ihren regulären Ansichten verschwinden

- **Ort:** `src/main/java/org/mwolff/manban/card/application/CardService.java:1564–1568` und `:1534–1551`.
- **Trigger:** Ein berechtigtes Mitglied sendet `PUT /api/cards/{epicId}/to-pool` für ein bestehendes Vorhaben mit zugeordneten Karten. Alternativ `PUT /api/cards/{epicId}/plan` direkt auf ein anderes Board desselben Projekts.
- **Auswirkung:** Im ersten Fall wird das Vorhaben boardlos und verschwindet aus beiden regulären Listen: Board-Epics finden es nicht mehr, Projekt-Ideen filtern ausschließlich CARD. Beim Einplanen auf einem anderen Board wandert das Vorhaben ohne seine Kinder; deren `parent_id` zeigt weiter darauf, aber die ausschließlich boardweise gerechnete Mitgliedschaft/Fortschrittsanzeige wird leer. Der dokumentierte Ausschluss von Vorhaben beim Transfer und beim Ideen-Speicher lässt sich über diese beiden alternativen Routen umgehen.
- **Beleg:** Im Gegensatz zu `moveToIdeaStorage` und `doTransfer` haben `moveBackToPool` und `planOntoBoard` keinerlei Typprüfung. V2 erzwingt nur die Werte CARD/EPIC, V18 erlaubt boardlos unabhängig vom Typ. FK- und Positions-Constraints verhindern den Vorgang nicht. `asPooledIdea`/`withPlannedOnBoard` erhalten den EPIC-Typ sowie die Relationsfelder. Die Projektgrenze bleibt geschützt; es wird keine projektübergreifende Rechteeskalation behauptet.
- **Testlücke:** `CardServiceTest.moveToIdeaStorage_rejectsEpic` deckt nur die andere Route ab. Die Tests von `moveBackToPool` und `planOntoBoard` übergeben ausschließlich reguläre CARD-Instanzen und prüfen nur Fremdprojekte.
- **Fix:** Die Pool-Wege müssen denselben CARD-/Lifecycle-Vertrag erzwingen. Einplanen nur aus tatsächlich zulässigem Pool-Zustand, Rückweg nur für geeignete Board-Karten; bei wiederholten Requests wohldefinierte Idempotenz oder fachliche Ablehnung. Beide HTTP-Routen mit EPICs einschließlich bestehender Kinder testen.

## Weitere konkrete Kandidaten für Gegenprüfung / Priorisierung

Diese Punkte sind statisch nachvollziehbar, wurden in diesem Teilreview aber nicht zusätzlich ausgeführt. Sie sind separat aufgeführt, damit der Hauptreviewer nicht stillschweigend Laufzeitnachweise behauptet.

- **P2, Herkunftszyklus durch paralleles PATCH:** `CardService.java:886–889` prüft und speichert ohne gemeinsame Graphsperre. T1 setzt A←B und T2 B←A; beide lesen den noch kantenlosen Zustand und schreiben unterschiedliche Zeilen, daher greifen weder FK noch übliche Zeilenupdates gegeneinander. V26 verhindert Zyklen nicht. `CardDerivedFromWriteIT.zyklus_wirdAbgelehnt` ist sequenziell. Fix: im Projekt serialisieren, nach Sperre frisch lesen und Zyklus prüfen. Das robuste Ring-Rendering verhindert Endlosschleifen, aber erfüllt nicht den zugesagten Ablehnungsvertrag.
- **P2, boardfremde Labels nach Transfer:** `CardService.java:1102–1119` bereinigt Dependencies/Assignees bei Projektwechsel und Parent, aber niemals `card_label`. V12 erzwingt keine Boardgleichheit zwischen Karte und Label. Labels aus A bleiben an einer Karte auf B gespeichert, werden dort wegen Labeldefinitionen von B unsichtbar und erscheinen bei Rücktransfer wieder; normales Entfernen eines weiteren Labels über die UI kann wegen mitsendeter alter IDs mit `InvalidLabelException` scheitern. Pool → anderes Board trägt denselben Altbestand. Fix: boardlokale Labelzuordnungen bei Boardwechsel bereinigen/definiert abbilden.
- **P2, NightRun-Ringpuffer ist nebenläufig nicht begrenzt:** `NightRunService.java:65–80` und `NightRunRepositoryAdapter.java:69–73`: Zwei Transaktionen legen unterschiedliche Runs an und führen je den Retention-DELETE aus, während die fremde neue Zeile noch unsichtbar ist. Bei vorher `keep−1` Runs löscht keiner; nach beiden Commits bleiben `keep+1`. Kein Lock auf Projekt/Namespace. Fix: Einfügen plus Trimmen pro Projekt serialisieren. Bestehende Tests prüfen Eindeutigkeit und Trimmen sequenziell.

## Prüfumfang und widerlegte Verdachte

Risikoorientiert über alle vorhandenen Fachmodule gearbeitet: Auth/Bootstrap/Registration/Reset/Admin, PAT/Compat, Project/Membership/RBAC/Einladungen, Board/Columns/SSE, Card/Create/Update/Move/Transfer/Bulk/Softdelete/Archive/Pool/Retention/Search, Labels/Assignees/Dependencies, Epic/Lineage/Requirement-Graph, KPI/Spaltenhistorie, Comments, Attachments/MinIO/Purge/Reconciliation, Outbox/Mail-Handler und NightRun/Persistenz/API. Migrationspfade und entscheidende FK-/Unique-/Lifecycle-Constraints einbezogen, Integrationstestbestände und relevante Assertions gegengeprüft. Guidelines aus AGENTS.md und ersatzweise `.claude/CLAUDE-workflow.md`, `CLAUDE-java.md`, `CLAUDE-security.md` gelesen.

- SQL-Konkatenationen in Positions-/Sortierabfragen tragen nur generierte Platzhalter oder geschlossene Enum-Schlüsselwörter; Nutzereingaben bleiben gebunden. Kein bestätigter SQL-Injection-Fund.
- Auth-Einmaltokens werden bei Verify/Reset atomar in SQL verbraucht; der bekannte Doppelkonsumfehler ist dort bereits geschlossen.
- Card-/Column-Positionsvergabe und die meisten Reindexes besitzen gezielte Namespace-Sperren, DB-Unique-Backstops und Concurrency-ITs; keine pauschale Behauptung fehlender Nebenläufigkeitssicherheit.
- Kommentare fremder Autoren können auch vom Admin nicht editiert werden; Projektmitgliedschaft wird geprüft. Archivierte Boards erlauben Kartenkommentare/-anhänge bewusst, das ist dokumentiertes Verhalten.
- Anhang-Download erzwingt Attachment-Disposition, MIME wird serverseitig ermittelt. Card-/Board-Purge plant Blob-Löschung vor DB-Cascade transaktional ein. **Projekt-Harddelete als fehlende weitere Kaskade bestätigt, aber vom Architekturreviewer separat ausgearbeitet.**
- Outbox benutzt `ON CONFLICT DO NOTHING` und `FOR UPDATE SKIP LOCKED`; SMTP-Hänger besitzen konfigurierte Zeitgrenzen. Mindestens-einmal-Zustellung und Single-Node-SSE sind dokumentierte Einschränkungen, keine als Bugs ausgegebenen Designentscheidungen.
- Externe Dependency-Nummern im Ingest sind bewusst zulässig; Herkunftsnummern dagegen bewusst existent/project-scoped. Die beiden Verträge wurden nicht verwechselt.

Grenzen: Keine vollständige unabhängige Ausführung der Tests in diesem Teilreview; die Hauptprüfung übernimmt Maven/Frontend sowie ausgewählte Regression-Probes (B1–B7 bereits bestätigt, siehe Einleitung). Keine Vollprüfung jeder Getter-/Boilerplate-Testassertion, kein Production-Zugang, keine Aussage zur tatsächlichen Live-Konfiguration. Die parallelen Zusatzkandidaten sind aus Transaktions-/ORM-/SQL-Semantik abgeleitet und benötigen für den Endbericht eine klare Kennzeichnung bzw. Probe. Default-Session-Secret und Passwortreset ohne Session-Widerruf prüft der Hauptreviewer separat.
