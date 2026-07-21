# Changelog

Alle nennenswerten Änderungen an kanban-kit werden hier festgehalten.

Das Format orientiert sich an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/); die
Versionierung folgt der dreiteiligen Betriebsversion (siehe [RELEASING.md](RELEASING.md)). Die
Einträge je Version sind ein automatischer Auszug der Commit-Titel seit dem letzten Release,
erzeugt von `scripts/gen-changelog.mjs`.

## [1.4.0] – 2026-07-21

- Version 1.3.1 (push main)
- Live-Board: EventSource-Hook + Verdrahtung in Board- und Listen-Ansicht ([#343](https://github.com/mannewolff/kanban-kit/issues/343))
- Live-Board: BoardChangedEvent bei allen board-relevanten Card-Mutationen publizieren ([#342](https://github.com/mannewolff/kanban-kit/issues/342))
- Live-Board: SSE-Infrastruktur (Endpoint + Emitter-Registry + Heartbeat + Auth) ([#341](https://github.com/mannewolff/kanban-kit/issues/341))
- Administration: Done-Aufbewahrung anzeigen/ändern + Countdown auf effektiven Wert ([#340](https://github.com/mannewolff/kanban-kit/issues/340))
- Global änderbare Done-Aufbewahrung: Persistenz + Admin-Endpoint + effektiver Wert ([#339](https://github.com/mannewolff/kanban-kit/issues/339))
- „Boards" in der Navigation auf Projekte-Ebene hochziehen ([#338](https://github.com/mannewolff/kanban-kit/issues/338))
- DataTable: Spalten-Ziehgriff sichtbar machen ([#337](https://github.com/mannewolff/kanban-kit/issues/337))
- BoardPage-Tests gegen reloadEpics-Coverage-Flake härten ([#336](https://github.com/mannewolff/kanban-kit/issues/336))
- Sonar java.libraries: Tilde-Pfad durch target/dependency ersetzen ([#335](https://github.com/mannewolff/kanban-kit/issues/335))
- Board-Button "Neues Item" neutral benennen ([#333](https://github.com/mannewolff/kanban-kit/issues/333))

## [1.3.0] – 2026-07-21

- Projektmitglieder-Liste auf DataTable umstellen ([#332](https://github.com/mannewolff/kanban-kit/issues/332))
- Nutzerverwaltung (/admin) auf DataTable umstellen ([#331](https://github.com/mannewolff/kanban-kit/issues/331))
- Wiederverwendbare DataTable-Komponente ([#330](https://github.com/mannewolff/kanban-kit/issues/330))
- Zebra-Zeilenfarben für alle Tabellen global im Theme ([#329](https://github.com/mannewolff/kanban-kit/issues/329))
- Namen-Bleistift in der Nutzerverwaltung an den Editiermodus koppeln ([#328](https://github.com/mannewolff/kanban-kit/issues/328))
- Karten-Anlegen mit vollem Feldsatz verdrahten ([#327](https://github.com/mannewolff/kanban-kit/issues/327))
- Gemeinsame CardFields-Komponente aus CardEditForm extrahieren ([#326](https://github.com/mannewolff/kanban-kit/issues/326))
- Karten-Anlegen: Fälligkeit, Zuständige und Labels atomar im Create-Endpoint ([#325](https://github.com/mannewolff/kanban-kit/issues/325))
- Item-/Epic-Bearbeiten aus dem Editiermodus-Gate lösen ([#324](https://github.com/mannewolff/kanban-kit/issues/324))
- Sonar S7755/S7758: .at()/codePointAt() statt Index/charCodeAt (New Code)
- Frontend-Coverage auf echte 100/100/100/100 + finaler Ratchet ([#323](https://github.com/mannewolff/kanban-kit/issues/323))
- Sonar-Duplikate: useBoardRole-Hook extrahieren + config.ts testen ([#322](https://github.com/mannewolff/kanban-kit/issues/322))
- Sonar-Duplikate: gemeinsame Basis fuer die Auth-Token-Entities ([#321](https://github.com/mannewolff/kanban-kit/issues/321))

## [1.2.0] – 2026-07-20

- Release 1.1.1 (push main)
- Toten Namen 'Stellwerk' aus Produkt und Doku entfernen
- Token-Dialog: Select-Labels shrinken lassen (Fix zu #319)
- API-Tokens: Dogfooding-Doku auf UI-Weg umstellen ([#320](https://github.com/mannewolff/kanban-kit/issues/320))
- API-Tokens: Erzeugen/Listen/Widerrufen auf der Administration-Seite ([#319](https://github.com/mannewolff/kanban-kit/issues/319))
- API-Tokens: Frontend-API-Wrapper ([#318](https://github.com/mannewolff/kanban-kit/issues/318))

## [1.1.0] – 2026-07-20

- Release 1.0.2 (push main)
- Doku-Inhalt nachziehen: Ideen-Speicher + Editiermodus ([#316](https://github.com/mannewolff/kanban-kit/issues/316))
- "Dokumentation"-Eintrag im Administrations-Bereich, oeffnet /docs/ ([#315](https://github.com/mannewolff/kanban-kit/issues/315))
- Doku-Auslieferung unter /docs/ (VitePress in die App bündeln) ([#314](https://github.com/mannewolff/kanban-kit/issues/314))
- Release 1.0.1 (push main)
- Sonar-Findings beheben: MarkdownInput-Props Readonly + Ternary aufloesen (#250, #312)

## [1.0.0] – 2026-07-20

- Release 0.8.2 (push main)
- Sonar-Sync: Severity-Filter + NUL-Bytes bereinigen + Fehlerausgabe ([#310](https://github.com/mannewolff/kanban-kit/issues/310))
- ESLint-Leitplanken: testing-library/recommended + no-deprecated ([#309](https://github.com/mannewolff/kanban-kit/issues/309))
- TaskMarkdown-Props als Readonly markieren ([#250](https://github.com/mannewolff/kanban-kit/issues/250))
- Ideen-Speicher: Zwei-Zonen-Listenansicht mit Hochziehen und Idee-anlegen ([#247](https://github.com/mannewolff/kanban-kit/issues/247))
- Ideen-Speicher: Frontend-API + Board-Unsichtbarkeit + Karten-Aktion ([#246](https://github.com/mannewolff/kanban-kit/issues/246))
- Ideen-Speicher: Service-Logik + Endpoints + kanbancompat-Ingest ([#245](https://github.com/mannewolff/kanban-kit/issues/245))
- Ideen-Speicher: Datenmodell + Migration V16 ([#244](https://github.com/mannewolff/kanban-kit/issues/244))
- docs-site: statischen VitePress-Build reparieren (srcDir außerhalb Root)
- Editiermodus-Gating der Bleistifte auf den Projekt-Seiten ([#243](https://github.com/mannewolff/kanban-kit/issues/243))
- Editiermodus-Gating der Bleistifte auf den Board-Seiten ([#242](https://github.com/mannewolff/kanban-kit/issues/242))
- Administration-Eintrag unten in der Sidebar ([#241](https://github.com/mannewolff/kanban-kit/issues/241))
- AdministrationPage mit Editiermodus-Schalter ([#240](https://github.com/mannewolff/kanban-kit/issues/240))
- Editiermodus-Context einführen ([#239](https://github.com/mannewolff/kanban-kit/issues/239))

## [0.8.0] – 2026-07-17

- Coverage-Schwellen in vite.config.ts final anheben ([#237](https://github.com/mannewolff/kanban-kit/issues/237))
- Restliche Fehlerpfade in bestehenden Seiten-Tests bündeln ([#236](https://github.com/mannewolff/kanban-kit/issues/236))
- ProjectMembersPage.tsx Coverage-Lücken schließen ([#235](https://github.com/mannewolff/kanban-kit/issues/235))
- BoardListPage.tsx Coverage-Lücken schließen ([#234](https://github.com/mannewolff/kanban-kit/issues/234))
- BoardPage.tsx Coverage-Lücken schließen ([#233](https://github.com/mannewolff/kanban-kit/issues/233))
- CardDetailModal.tsx Coverage-Lücken schließen ([#232](https://github.com/mannewolff/kanban-kit/issues/232))
- BoardView.tsx Coverage-Lücken schließen ([#231](https://github.com/mannewolff/kanban-kit/issues/231))
- AppShell.tsx Coverage-Lücken schließen ([#230](https://github.com/mannewolff/kanban-kit/issues/230))
- AuthContext.tsx direkt testen ([#229](https://github.com/mannewolff/kanban-kit/issues/229))
- Vier leere Auth-Flow-Seiten testen ([#228](https://github.com/mannewolff/kanban-kit/issues/228))
- CardDetailModal.tsx: erneut aufgetretene Sonar-Findings nach #214 bündeln ([#222](https://github.com/mannewolff/kanban-kit/issues/222))
- NewCardModal.tsx: SelectProps/InputLabelProps auf slotProps migrieren ([#224](https://github.com/mannewolff/kanban-kit/issues/224))
- BoardView.tsx: SelectProps/InputLabelProps auf slotProps migrieren ([#221](https://github.com/mannewolff/kanban-kit/issues/221))
- DashboardPage.tsx: Props als Readonly markieren ([#227](https://github.com/mannewolff/kanban-kit/issues/227))
- SnackbarProvider.tsx: Props als Readonly markieren ([#226](https://github.com/mannewolff/kanban-kit/issues/226))
- PasswordField.tsx: Props als Readonly markieren ([#225](https://github.com/mannewolff/kanban-kit/issues/225))
- LabelManagerDialog.tsx: Props als Readonly markieren ([#223](https://github.com/mannewolff/kanban-kit/issues/223))
- AuthCard.tsx: Props als Readonly markieren ([#220](https://github.com/mannewolff/kanban-kit/issues/220))
- AppShell.tsx: primaryTypographyProps auf slotProps migrieren ([#219](https://github.com/mannewolff/kanban-kit/issues/219))
- AuthContext.tsx: Props als Readonly markieren ([#218](https://github.com/mannewolff/kanban-kit/issues/218))
- SonarQube-Sync: PROJECTS_PAT statt GITHUB_TOKEN fuer GitHub-Projects-v2-Zugriff
- Kopierte Karte immer nach Backlog statt in die Ursprungsspalte ([#207](https://github.com/mannewolff/kanban-kit/issues/207))
- Release-Tag-Handling: annotated Tags auf den Release-Commit ([#206](https://github.com/mannewolff/kanban-kit/issues/206))
- SonarCloud-Trigger von production auf main umstellen
- Frontend-Coverage-Philosophie in CLAUDE-react.md verankern ([#217](https://github.com/mannewolff/kanban-kit/issues/217))
- Frontend-API-Schicht direkt testen statt ausschließen ([#216](https://github.com/mannewolff/kanban-kit/issues/216))
- sonar.coverage.exclusions ergänzen: Sonar-Scope an lokale Gates angleichen ([#215](https://github.com/mannewolff/kanban-kit/issues/215))
- CardDetailModal.tsx: alle Sonar-Findings bündeln ([#214](https://github.com/mannewolff/kanban-kit/issues/214))
- SonarCloud-Sync: auf Compute-Engine-Task warten ([#213](https://github.com/mannewolff/kanban-kit/issues/213))
- Restliche Sonar-Code-Smells abräumen ([#212](https://github.com/mannewolff/kanban-kit/issues/212))
- markdownTasks.ts: Regex-Backtracking + Template-Literals + exec ([#210](https://github.com/mannewolff/kanban-kit/issues/210))
- BoardView.tsx: Komplexitäts-Smells reduzieren ([#211](https://github.com/mannewolff/kanban-kit/issues/211))
- Component-Props als Readonly markieren ([#209](https://github.com/mannewolff/kanban-kit/issues/209))
- MUI inputProps-Deprecation auf slotProps migrieren ([#208](https://github.com/mannewolff/kanban-kit/issues/208))
- SonarCloud: Analyse immer als main-Branch melden (Free-Plan-Fix)

## [0.7.0] – 2026-07-15

- Archivierte Karten wiederherstellen: Restore in Listenansicht und Detail-Modal ([#204](https://github.com/mannewolff/kanban-kit/issues/204))
- Bulk-Aktion 'In den Papierkorb': BulkActionBar + Anbindung mit Bestätigung ([#203](https://github.com/mannewolff/kanban-kit/issues/203))
- Bulk-Löschen mehrerer Karten in den Papierkorb: POST /api/cards/bulk-delete ([#202](https://github.com/mannewolff/kanban-kit/issues/202))
- Breadcrumb auf restliche Seiten ausrollen: Mitglieder, Liste, Epics, Dashboard ([#201](https://github.com/mannewolff/kanban-kit/issues/201))
- Wiederverwendbare Breadcrumb-Komponente: vollständiger Pfad, nur letztes Segment fett ([#200](https://github.com/mannewolff/kanban-kit/issues/200))
- Auto-Routing: bei manueller Projektauswahl mit einem Board durchrouten ([#199](https://github.com/mannewolff/kanban-kit/issues/199))
- Bulk-Aktionen anbinden: API, Bulk-Transfer-Dialog und Archiv-Bestätigung ([#198](https://github.com/mannewolff/kanban-kit/issues/198))
- Karten-Auswahlmodus im Board mit Mehrfachauswahl und BulkActionBar-Gerüst ([#197](https://github.com/mannewolff/kanban-kit/issues/197))
- Bulk-Verschieben mehrerer Karten auf ein anderes Board: POST /api/cards/bulk-transfer ([#196](https://github.com/mannewolff/kanban-kit/issues/196))
- Bulk-Archivieren mehrerer Karten: POST /api/cards/bulk-archive ([#195](https://github.com/mannewolff/kanban-kit/issues/195))
- Release-Changelog-Tooling: gen-changelog.mjs, CHANGELOG.md, Git-Tags ([#194](https://github.com/mannewolff/kanban-kit/issues/194))

## [0.6.0] – 2026-07-15

- Marken-Assets und Deployment-Spec einchecken
- CI-Frontend grün: localStorage zwischen Tests zurücksetzen ([#192](https://github.com/mannewolff/kanban-kit/issues/192))
- Einladung: Mail-Fehler sprengt die Einladung nicht mehr als 500 ([#191](https://github.com/mannewolff/kanban-kit/issues/191))
- Anzeigename ändern: Projekt-Owner/Admin in der Mitgliederliste ([#190](https://github.com/mannewolff/kanban-kit/issues/190))
- Anzeigename ändern: Plattform-Admin in der Nutzerverwaltung ([#189](https://github.com/mannewolff/kanban-kit/issues/189))
- Anzeigename ändern: Self-Service + Fundament withDisplayName ([#188](https://github.com/mannewolff/kanban-kit/issues/188))
- Doku: Benutzer- & Betriebsdoku auf Funktionsstand nachziehen
- E-Mail-Texte: Betreffe auf kanban-kit + Einladungs-Weg erklären ([#187](https://github.com/mannewolff/kanban-kit/issues/187))
- Login: konkrete Fehlermeldung statt pauschalem E-Mail-Hinweis ([#186](https://github.com/mannewolff/kanban-kit/issues/186))
- Task-Checkboxen mit Leerzeichen-Varianten robust rendern ([#185](https://github.com/mannewolff/kanban-kit/issues/185))
- Neues Springer-Icon für Favicon und Header
- Papierkorb: Karten-Löschung soft machen und selbst wiederherstellen ([#179](https://github.com/mannewolff/kanban-kit/issues/179))
- Admin: Nutzerkonten sperren/entsperren (deaktivieren) ([#178](https://github.com/mannewolff/kanban-kit/issues/178))
- Karten-Aktivitätsverlauf: wer hat wann was geändert ([#177](https://github.com/mannewolff/kanban-kit/issues/177))
- Labels/Tags an Karten vergeben und danach filtern ([#176](https://github.com/mannewolff/kanban-kit/issues/176))
- Fälligkeitsdatum an Karten setzen und überfällige hervorheben ([#175](https://github.com/mannewolff/kanban-kit/issues/175))
- Karten-Zuweisung: ein oder mehrere Assignees je Karte ([#174](https://github.com/mannewolff/kanban-kit/issues/174))
- Spalten-Zykluszeit: Board-Dashboard-Seite mit KPI-Charts ([#173](https://github.com/mannewolff/kanban-kit/issues/173))
- Spalten-Zykluszeit: KPI-Aggregation + Dashboard-Endpoint ([#172](https://github.com/mannewolff/kanban-kit/issues/172))
- Spalten-Zykluszeit: Transition-Hooks in CardService ([#171](https://github.com/mannewolff/kanban-kit/issues/171))
- Spalten-Zykluszeit: Persistenz-Fundament ([#170](https://github.com/mannewolff/kanban-kit/issues/170))
