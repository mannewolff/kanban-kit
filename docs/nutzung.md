# Nutzung

## Registrieren & Anmelden

- **Registrieren:** über „Registrieren" auf dem Login-Screen (E-Mail, Passwort, Anzeigename).
- **E-Mail bestätigen:** Pflicht vor dem ersten Login. Ohne echten Mailserver steht der Link im Log
  (siehe [Betrieb](betrieb.md#e-mail-bestätigung-ohne-mailserver)).
- **Anmelden:** E-Mail + Passwort. Hast du **genau ein** Projekt, wirst du direkt zur Boardauswahl
  geleitet; hat dieses **genau ein** Board, direkt aufs Board. Über die Seitenleiste „Projekte" bzw.
  die Zurück-Links kommst du jederzeit zu den Listen zurück (um weitere anzulegen).

## Projekte

- Auf der Startseite ein Projekt **anlegen** oder eines öffnen.
- Als **OWNER** kannst du das Projekt verwalten (umbenennen/löschen) und Mitglieder einladen.
- Ein **Plattform-Admin** sieht und bearbeitet alle Projekte (siehe [Rollen & Rechte](rollen-und-rechte.md)).

## Boards & Spalten

- Innerhalb eines Projekts **Boards** anlegen. Ein neues Board bekommt die Default-Spalten
  **Backlog · Ready · In Progress · In Review · Done**.
- Spalten lassen sich anlegen, umbenennen, umsortieren und (wenn leer) löschen.

## Karten

- **Anlegen:** „+" im Spaltenkopf (Karte direkt in dieser Spalte) oder der Button **„Neues Item"**
  oben (legt in der ersten Spalte an). Titel + Markdown-Beschreibung.
- **Verschieben:** per Drag & Drop zwischen den Spalten. Alternativ über das **⋮-Menü** der Karte
  (Eintrag „Nach Zielspalte") — auch per Tastatur bedienbar.
- **⋮-Menü:** Bearbeiten (öffnet das Detail im Bearbeiten-Modus), Archivieren, Verschieben.
- **Done-Countdown:** Karten in einer Done-Spalte zeigen „wird in X Tagen archiviert"
  (steuerbar über `MANBAN_DONE_RETENTION_DAYS`).

### Karten-Detail

Klick auf eine Karte öffnet das Detail:

- **Beschreibung** als GitHub-Markdown (im Bearbeiten-Modus editierbar).
- **Bearbeiten-Formular:** Titel, Markdown, Epic-Zuordnung, „Abhängig von (Nummern, kommagetrennt)"
  — alles in einem Speichern-Vorgang.
- **Abhängigkeiten:** Verweise auf andere Kartennummern.
- **Anhänge:** hochladen, herunterladen, löschen. **Klick auf einen Bild- oder PDF-Anhang** (auf die
  Miniatur oder den Dateinamen) öffnet eine **Vorschau (Lightbox)**; andere Dateitypen werden geladen.
- **Kommentare:** schreiben; eigene Kommentare löschen (Moderation durch ADMIN/OWNER).

## Listen-Ansicht

Über den Sidebar-Eintrag „Liste" (im Board-Kontext):

- **Filter-Chips** je Spalte + „Archiv" (blendet Status ein/aus; Auswahl bleibt erhalten).
- Zeilen mit Drag-Handle, Nummer, Status, Epic-Badge, Titel und **Beschreibungs-Vorschau**.
- **Spalten umsortieren:** die Spalten-Kopfzeile per Drag verschieben (z. B. „Beschreibung" nach vorne).
- **Beschreibung verbreitern:** den Griff links der „Beschreibung"-Spalte ziehen.
- **Zeilen umsortieren:** über den Drag-Handle links (innerhalb derselben Spalte).

## Epics

- **Neues Epic:** in der Epics-Ansicht (Sidebar „Epics") über den Button „Neues Epic", oder im
  Anlege-Dialog Typ „Epic" wählen. Optional ein Kürzel; sonst aus dem Titel abgeleitet.
- **Zuordnen:** eine Karte im Anlege-Dialog oder im Detail einem Epic zuordnen. Zugeordnete Karten
  tragen ein **Epic-Badge** und einen farbigen linken Rand.
- **Epics-Übersicht:** Liste mit **Fortschrittsbalken** („X/Y Stories fertig"). Klick öffnet das
  Epic-Detail (Kürzel + Kinderliste).
- **Board-Epic-Filter:** auf dem Board über das Dropdown „Epic-Filter" nur die Karten eines Epics anzeigen.

## Mitglieder

Auf der Board-/Projektseite über „Mitglieder" (nur für OWNER/ADMIN sichtbar):

- **Einladen:** E-Mail + Rolle. Der/die Eingeladene erhält einen Annahme-Link (im Log, wenn Mail aus).
- **Rolle ändern / entfernen.** Der **letzte OWNER** kann nicht entfernt/degradiert werden.
- **Einladung annehmen:** über den Link `…/invitations/accept?token=…` (angemeldet).
