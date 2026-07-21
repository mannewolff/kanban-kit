# Nutzung

## Registrieren & Anmelden

- **Registrieren:** über „Registrieren" auf dem Login-Screen (E-Mail, Passwort, Anzeigename).
- **E-Mail bestätigen:** Pflicht vor dem ersten Login. Ohne echten Mailserver steht der Link im Log
  (siehe [Betrieb](betrieb.md#e-mail-bestätigung-ohne-mailserver)).
- **Anmelden:** E-Mail + Passwort. Hast du **genau ein** Projekt, wirst du direkt zur Boardauswahl
  geleitet; hat dieses **genau ein** Board, direkt aufs Board. Über die Seitenleiste „Projekte" bzw.
  die Zurück-Links kommst du jederzeit zu den Listen zurück (um weitere anzulegen).
- **Fehlermeldungen** sind konkret: falsche Zugangsdaten ergeben „Ungültige Anmeldedaten." (ohne zu
  verraten, ob E-Mail oder Passwort falsch war); noch nicht bestätigte, noch nicht freigegebene oder
  **gesperrte** Konten nennen den jeweiligen Grund (z. B. „Konto gesperrt").

## Projekte

- Auf der Startseite ein Projekt **anlegen** oder eines öffnen.
- Als **OWNER** kannst du das Projekt verwalten (umbenennen/löschen) und Mitglieder einladen.
- Ein **Plattform-Admin** sieht und bearbeitet alle Projekte (siehe [Rollen & Rechte](rollen-und-rechte.md)).

## Boards & Spalten

- Innerhalb eines Projekts **Boards** anlegen. Ein neues Board bekommt die Default-Spalten
  **Backlog · Ready · In Progress · In Review · Done**.
- Spalten lassen sich anlegen, umbenennen, umsortieren und (wenn leer) löschen.

## Karten

- **Anlegen:** „+" im Spaltenkopf (Karte direkt in dieser Spalte) oder der Button **„Neu anlegen"**
  oben (legt in der ersten Spalte an). Titel + Markdown-Beschreibung.
- **Verschieben:** per Drag & Drop zwischen den Spalten. Alternativ über das **⋮-Menü** der Karte
  (Eintrag „Nach *Zielspalte*") — auch per Tastatur bedienbar.
- **⋮-Menü:** „Bearbeiten" (öffnet das Detail im Bearbeiten-Modus), „Duplizieren", „Archivieren",
  „In Ideen-Speicher" (legt die Karte in den [Ideen-Speicher](#ideen-speicher)), „Nach *Zielspalte*"
  und — mit Board-Recht — „Auf anderes Board verschieben…".
- **Auf der Karte sichtbar:** farbige **Label**-Chips, eine gesetzte **Fälligkeit** („📅 *Datum*",
  überfällige rot und fett) sowie rechts unten die **Avatare der Zuständigen** (Initialen, bis zu vier).
- **Done-Countdown:** Karten in einer Done-Spalte zeigen „wird in X Tagen archiviert"
  (steuerbar über `MANBAN_DONE_RETENTION_DAYS`).

### Karten-Detail

Klick auf eine Karte öffnet das Detail:

- **Beschreibung** als GitHub-Markdown (im Bearbeiten-Modus editierbar). **Task-Listen**
  (`- [ ]` / `- [x]`) werden als anklickbare Checkboxen gerendert; ein Klick schaltet sie um und
  speichert sofort. Lasch geschriebene Marker (`[]`, `[ x ]`, `[X]`) werden dabei toleriert.
- **Bearbeiten-Formular:** Titel, Markdown, Epic-Zuordnung, „Fällig am", „Abhängig von (Nummern,
  kommagetrennt)" — alles in einem Speichern-Vorgang.
- **Zuständige:** ein oder mehrere Projektmitglieder über das Feld „Zuständige" zuweisen (Mehrfachauswahl).
  Ohne Bearbeitungsrecht werden die Zuständigen nur als Chips angezeigt.
- **Fällig am:** ein Fälligkeitsdatum setzen. Im Ansichtsmodus steht darunter „Fällig am *TT.MM.JJJJ*";
  liegt das Datum in der Vergangenheit und ist die Karte nicht in einer Done-Spalte, wird es rot als
  „— überfällig" markiert.
- **Labels:** dem Board angelegte Labels über das Feld „Labels" (Mehrfachauswahl) an die Karte hängen;
  sie erscheinen als farbige Chips.
- **Abhängigkeiten:** Verweise auf andere Kartennummern.
- **Anhänge:** hochladen, herunterladen, löschen. **Klick auf einen Bild- oder PDF-Anhang** (auf die
  Miniatur oder den Dateinamen) öffnet eine **Vorschau (Lightbox)**; andere Dateitypen werden geladen.
- **Kommentare:** schreiben; eigene Kommentare löschen (Moderation durch ADMIN/OWNER).
- **Aktivität:** ein chronologischer Verlauf am Ende des Details — „*Zeitpunkt* · *Person* · *Aktion*".
  Protokolliert werden Anlegen, Bearbeiten, Zuständige geändert, Verschieben, Archivieren und
  Wiederherstellen (Label-Änderungen werden nicht protokolliert).

## Labels

Labels sind **pro Board** definiert und werden über den Button **„Labels"** in der Board-Kopfzeile
verwaltet (nur mit Bearbeitungsrecht sichtbar):

- **Anlegen:** Name + Farbe wählen, „Anlegen". Namen müssen je Board eindeutig sein.
- **Ändern/Löschen:** je Label Name und Farbe anpassen und „Speichern", oder über „✕" löschen.
- **Vergeben:** im Karten-Detail über das Feld „Labels" (siehe oben). **Filtern** nach Labels in der
  [Listen-Ansicht](#listen-ansicht).

## Papierkorb

Gelöschte Karten landen zunächst im **Papierkorb** (Soft-Delete), statt sofort verloren zu gehen. Der
Papierkorb wird über den Button **„Papierkorb"** in der Board-Kopfzeile geöffnet (mit Bearbeitungsrecht):

- **Wiederherstellen:** holt eine Karte zurück aufs Board.
- **Endgültig löschen:** entfernt eine Karte unwiderruflich — nur für ADMIN/OWNER bzw. Plattform-Admin.
- **Automatik:** Karten im Papierkorb werden nach der konfigurierten Aufbewahrungsfrist (Standard
  30 Tage) automatisch endgültig gelöscht.

> **Hinweis:** Das Löschen einer Karte ist derzeit nur über die API (`DELETE /api/cards/{id}`)
> ausgelöst — im ⋮-Menü gibt es dafür (noch) keinen Eintrag. Der Papierkorb selbst ist voll bedienbar.

## Listen-Ansicht

Über den Sidebar-Eintrag „Liste" (im Board-Kontext):

- **Filter-Chips** je Spalte + „Archiv" (blendet Status ein/aus; Auswahl bleibt erhalten).
- **Label-Filter:** eine Reihe farbiger, umschaltbarer Label-Chips. Mehrere Labels sind kombinierbar
  (eine Karte bleibt sichtbar, wenn sie **eines** der aktiven Labels trägt). Der Label-Filter existiert
  nur in der Listen-Ansicht, nicht in der Board-Spaltenansicht.
- Zeilen mit Drag-Handle, Nummer, Status, Epic-Badge, Titel, **Fälligkeit** (überfällige rot) und
  **Beschreibungs-Vorschau**.
- **Spalten umsortieren:** die Spalten-Kopfzeile per Drag verschieben (z. B. „Beschreibung" nach vorne).
- **Beschreibung verbreitern:** den Griff links der „Beschreibung"-Spalte ziehen.
- **Zeilen umsortieren:** über den Drag-Handle links (innerhalb derselben Spalte).

## Ideen-Speicher

Der **Ideen-Speicher** ist ein Ablageort für Karten, die noch nicht auf dem Board sichtbar sein
sollen — ein leichtgewichtiges Grooming, kein Löschen. Die Listen-Ansicht zeigt ihn als **zweite
Zone** unterhalb der aktiven Karten (abgesetzt durch die Überschrift „Ideen-Speicher"):

- **Obere Zone:** die aktiven Karten wie gewohnt (mit Spalten- und Label-Filtern).
- **Untere Zone:** alle Ideen des Boards, nach Kartennummer sortiert (nicht umsortierbar).

Ideen erscheinen **nicht** in der Board-Spaltenansicht und halten keine aktive Position — sie
kollidieren also nicht mit den Karten der Spalten.

- **Idee anlegen:** Button **„Idee anlegen"** in der Ideen-Zone (Titel + Markdown-Beschreibung, wie
  eine normale Karte, nur direkt als Idee).
- **In den Ideen-Speicher legen (Demotion):** über das **⋮-Menü** einer Karte oder im Karten-Detail
  den Eintrag **„In Ideen-Speicher"**. Die Karte verschwindet aus dem Board und taucht in der unteren
  Zone auf.
- **Ins Backlog holen (Promotion):** je Idee der Button **„→ Backlog"**. Die Idee wird zur normalen
  Karte in der **ersten Spalte** (Backlog), ans Ende, und erscheint wieder auf dem Board.

Beide Richtungen zählen als normaler Arbeitsfluss und brauchen nur das Recht zum **Verschieben** von
Karten (kein Löschrecht). Auch der Ingest über die API (kanbancompat) kann eine Karte direkt als Idee
anlegen.

## Dashboard (Kennzahlen)

Über den Sidebar-Eintrag **„Dashboard"** (im Board-Kontext) zeigt eine KPI-Seite, wie schnell Karten
durch das Board laufen. Grundlage ist die automatisch erfasste Verweildauer jeder Karte pro Spalte —
gemessen bei **jedem** Spaltenwechsel, egal ob per Drag & Drop, ⋮-Menü oder über die API (kanbancompat).

- **Ø Lead Time** und **Ø Cycle Time** als Kennzahl-Kacheln.
- **Ø Verweildauer je Spalte** (Balkendiagramm, in Stunden).
- **Durchsatz je Woche** — abgeschlossene Karten (Liniendiagramm).
- **Ausreißer** — Karten, die über 7 Tage in einer Spalte lagen (Tabelle mit #, Titel, Spalte, Dauer).

Das Dashboard ist für jeden sichtbar, der das Board öffnen darf (auch VIEWER).

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

## Editiermodus

Der **Editiermodus** trennt den Kanban-Alltag von den strukturellen Änderungen. Er wird über den
Sidebar-Eintrag **„Administration"** ein- und ausgeschaltet:

- **Standardmäßig aus** und **nicht dauerhaft gemerkt** — nach einem Neustart ist er wieder aus.
- **Ist er aktiv,** erscheinen die **Bearbeiten-/Umbenennen-Symbole** (Bleistifte) — etwa zum
  Umbenennen von Projekten, Boards und Spalten sowie zum Anlegen/Bearbeiten/Löschen von Spalten —,
  **sofern du die nötigen Rechte hast**.
- **Der Alltag bleibt unberührt:** Karten anlegen, verschieben, archivieren und in den Ideen-Speicher
  legen funktioniert **unabhängig** vom Editiermodus.
