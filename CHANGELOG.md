# Changelog

Alle nennenswerten Änderungen an kanban-kit werden hier festgehalten.

Das Format orientiert sich an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/); die
Versionierung folgt der dreiteiligen Betriebsversion (siehe [RELEASING.md](RELEASING.md)). Die
Einträge je Version sind ein automatischer Auszug der Commit-Titel seit dem letzten Release,
erzeugt von `scripts/gen-changelog.mjs`.

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
