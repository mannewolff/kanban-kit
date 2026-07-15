# Changelog

Alle nennenswerten Änderungen an kanban-kit werden hier festgehalten.

Das Format orientiert sich an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/); die
Versionierung folgt der dreiteiligen Betriebsversion (siehe [RELEASING.md](RELEASING.md)). Die
Einträge je Version sind ein automatischer Auszug der Commit-Titel seit dem letzten Release,
erzeugt von `scripts/gen-changelog.mjs`.

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
