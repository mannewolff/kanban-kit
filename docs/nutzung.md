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

- Ein **neu angelegtes Projekt** enthält bereits ein Board namens **„default"** — du kannst also
  sofort loslegen.
- Innerhalb eines Projekts weitere **Boards** anlegen. Ein neues Board bekommt die Default-Spalten
  **Backlog · Ready · In Progress · In Review · Done**.
- Spalten lassen sich anlegen, umbenennen, umsortieren und (wenn leer) löschen.

## Karten

- **Anlegen:** über den Button **„Neu anlegen"** oben oder die Taste **`+`** — beide legen eine Karte
  stets in der **ersten Spalte** (Backlog) an. Titel + Markdown-Beschreibung.
- **Verschieben:** per Drag & Drop zwischen den Spalten. Alternativ über das **⋮-Menü** der Karte mit
  den Einträgen „Nach links verschieben" und „Nach rechts verschieben" (jeweils genau eine Spalte
  weit) — auch per Tastatur bedienbar. In der ersten bzw. letzten Spalte fehlt der jeweils sinnlose
  Eintrag.
- **⋮-Menü:** „Duplizieren", „Archivieren", „In den Ideen-Pool" (legt die Karte in den
  [Ideen-Pool](#ideen-pool)) — mit Board-Recht — „Auf anderes Board verschieben…" sowie „Nach
  links/rechts verschieben". Der Eintrag **„Bearbeiten"** (öffnet das Detail im Bearbeiten-Modus)
  erscheint nur bei aktivem [Editiermodus](#editiermodus); der Button „Bearbeiten" im Karten-Detail
  steht dagegen immer zur Verfügung, sofern du Bearbeitungsrecht hast. Bei **archivierten** Karten
  ist das Menü leer.
- **Auf der Karte sichtbar:** farbige **Label**-Chips, eine gesetzte **Fälligkeit** („📅 *Datum*",
  überfällige rot und fett) sowie rechts unten die **Avatare der Zuständigen** (Initialen, bis zu vier).
- **Done-Countdown:** Karten in einer Done-Spalte zeigen „wird in X Tagen archiviert"
  (steuerbar über `MANBAN_DONE_RETENTION_DAYS`).

### Karten-Detail

Klick auf eine Karte öffnet das Detail:

- **Beschreibung** als GitHub-Markdown (im Bearbeiten-Modus editierbar). **Task-Listen**
  (`- [ ]` / `- [x]`) werden als anklickbare Checkboxen gerendert; ein Klick schaltet sie um und
  speichert sofort. Lasch geschriebene Marker (`[]`, `[ x ]`, `[X]`) werden dabei toleriert. Im
  Editor gibt es eine **Kurzschreibweise**: am Zeilenanfang erzeugt `/` plus Leerzeichen eine leere,
  `/x` plus Leerzeichen eine abgehakte Checkbox — die eckigen Klammern (`- [ ]` / `- [x]`) funktionieren
  weiterhin.
- **Bearbeiten-Formular:** Titel, Markdown, Vorhaben-Zuordnung, „Fällig am", „Abhängig von (Nummern,
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

- **Filter-Chips** je Spalte + „Archiv" (blendet Status ein/aus; die Auswahl bleibt **pro Board**
  erhalten).
- **Label-Filter:** eine Reihe farbiger, umschaltbarer Label-Chips. Mehrere Labels sind kombinierbar
  (eine Karte bleibt sichtbar, wenn sie **eines** der aktiven Labels trägt). Der Label-Filter existiert
  nur in der Listen-Ansicht, nicht in der Board-Spaltenansicht.
- Zeilen mit Drag-Handle, Nummer, Status, Vorhaben-Badge, Titel, **Fälligkeit** (überfällige rot) und
  **Beschreibungs-Vorschau**.
- **Spalten umsortieren:** die Spalten-Kopfzeile per Drag verschieben (z. B. „Beschreibung" nach vorne).
  Diese Reihenfolge gilt **global** für alle Boards.
- **Beschreibung verbreitern:** den Griff links der „Beschreibung"-Spalte ziehen. Auch diese Breite
  gilt **global** für alle Boards (nur der Spaltenfilter oben bleibt pro Board).
- **Zeilen umsortieren:** über den Drag-Handle links (innerhalb derselben Spalte).

## Ideen-Pool

Der **Ideen-Pool** ist ein **projektweiter** Ablageort für Karten, die noch nicht auf einem Board
sichtbar sein sollen — ein leichtgewichtiges Grooming, kein Löschen. Er ist nicht an ein einzelnes
Board gebunden, sondern gilt für das ganze Projekt.

Erreichbar über den Sidebar-Eintrag **„Ideen"** (sichtbar, sobald ein Projekt-Kontext offen ist). Die
Seite zeigt die **Planen-Ansicht**:

- **Oben:** alle Boards des Projekts untereinander, je Board seine **erste Spalte** und ein Button
  **„Board öffnen"** (springt in die Listen-Ansicht dieses Boards).
- **Darunter:** der projektweite **Ideen-Pool** mit allen noch nicht eingeplanten Ideen.
- **Suchfeld** oben auf der Seite (Label „Suche"): filtert **nur den Pool** nach Titel — die
  Board-Zonen bleiben unverändert.

Einen Umschalter zwischen „Liste" und „Planen" gibt es nicht mehr — es bleibt bei der Planen-Ansicht.
Ideen erscheinen **nicht** in der Board-Spaltenansicht und **nicht** in der Listen-Ansicht des Boards.

- **Idee anlegen:** Button **„Idee anlegen"** auf der Ideen-Seite legt direkt eine Idee im Pool an
  (Titel + Markdown-Beschreibung, wie eine normale Karte).
- **Einplanen (Idee → Board):** eine Idee per **Drag** aus dem Pool in die erste Spalte des
  gewünschten Boards ziehen. Der Button **„Einplanen"** ist die Abkürzung dafür und plant stets auf
  das **erste Board** des Projekts. So oder so wird die Idee zur normalen Karte in der **ersten
  Spalte** und erscheint wieder auf dem Board.
- **Zurückholen (Board → Pool):** eine Karte per **Drag** aus der ersten Spalte in den Pool ziehen,
  über den Button **„In den Pool"** oder im **⋮-Menü** der Karte über „In den Ideen-Pool". Die Karte
  verschwindet vom Board.
- **Zwischen Boards verschieben:** eine Karte per **Drag** direkt von der ersten Spalte eines Boards
  in die eines anderen ziehen — sie landet in dessen erster Spalte.
- **Reihenfolge innerhalb einer Board-Zone:** Karten per **Drag** auf eine andere Zeile derselben
  Zone umsortieren.
- **Projektweite Nummer bleibt erhalten:** Eine Karte behält beim Weg in den Pool ihre **projektweite
  Nummer**; sie wird auch im Pool angezeigt und geht beim Einplanen nicht verloren.

Alle Richtungen zählen als normaler Arbeitsfluss und brauchen nur das Recht zum **Verschieben** von
Karten (kein Löschrecht). Auch der Ingest über die API (kanbancompat) kann eine Karte direkt als Idee
anlegen.

## Nachtlauf

Der **Nachtlauf-Bereich** wertet die Protokolle des Nacht-Runners aus: Er zeigt je Lauf, welche
Arbeitspakete durchliefen, welche stehenblieben und woran es lag. Er ist **projektweit**, nicht an
ein Board gebunden.

Erreichbar über den Sidebar-Eintrag **„Nachtlauf"** (Route `/projects/:projectId/nachtlauf`).
Sichtbar ist er nur für den **Owner** des Projekts und für **Plattform-Admins** — siehe
[Rollen & Rechte](rollen-und-rechte.md#projekt-rollen-rechte-matrix).

**Protokoll hineingeben:** Button **„Protokoll einlesen"** oben rechts, dann die Protokolldatei
(`.log` oder `.txt`) wählen. Die **Datei wird im Browser ausgewertet und nicht hochgeladen** — an
den Server geht allein die verdichtete Auswertung (Kennzahlen, Zustände, Kartennummern,
Fehlerklassen und kurze Auszüge). Protokolle sind mehrere Megabyte groß und tragen Quelltext,
Sitzungs-IDs und Pfade; die bleiben, wo sie sind. Dieselbe Datei lässt sich erneut wählen, ohne die
Seite neu zu laden.

Jeder Lauf steht als aufklappbare Zeile da — Startzeitpunkt, Art des Laufs („Umsetzungs-Lauf" bzw.
„Prüf-Lauf"), Dauer sowie „N bearbeitet, M übergangen". Aufgeklappt zeigt er seine Arbeitspakete,
jedes mit einem der **vier Zustände**:

- **grün — „Erfolg":** das Arbeitspaket ist durchgelaufen.
- **gelb — „Erfolg, Prüfung rot":** umgesetzt, aber eine Prüfung schlug fehl.
- **rot — „gescheitert":** der Lauf kam an diesem Arbeitspaket nicht durch.
- **grau — „nicht bearbeitet":** übergangen, etwa weil eine Abhängigkeit noch offen war.

Zu jedem Arbeitspaket stehen darunter der Auszug aus dem Protokoll (bei grau der Grund) und die
Herkunftskette — die fachliche Anforderung und der Plan, aus denen es entstanden ist. Die
Kartennummer ist ein Link und öffnet die Karte zum Lesen.

**Häufigkeit einer Fehlerklasse:** An einem gelben oder roten Befund eines aufbewahrten Laufs steht,
in wie vielen der aufbewahrten Läufe dieselbe Fehlerklasse vorkam — „Prüfungen rot: 4 von 30
aufbewahrten Läufen" bzw. „zum ersten Mal" beim ersten Vorkommen. Gezählt wird **einschließlich** des
angezeigten Laufs. So ist ein einmaliger Ausrutscher von einem wiederkehrenden Muster zu
unterscheiden.

**Übernahmetext:** Unter jedem gelben und roten Arbeitspaket steht ein fertiger Text zum Übernehmen
in die eigene Entwicklungssitzung (Karte, Zustand, Fehlerklasse, Auszug). Er steht **vollständig
sichtbar** in einem Textfeld, bevor der Button **„Kopieren"** ihn in die **Zwischenablage** legt —
der Text enthält Fremdtext aus dem Protokoll, und was in die eigene Sitzung wandert, soll man vorher
gesehen haben. Legt der Browser die Zwischenablage nicht frei, bleibt es beim sichtbaren Feld: von
Hand markieren und kopieren.

**Aufbewahrung:** Je Projekt bleiben die **letzten 30 Läufe** erhalten; ältere fallen heraus, sobald
neue hinzukommen.

Drei Sonderfälle, die keine Fehler sind: Ein **Probelauf** (`DRY-RUN`) wird nicht aufbewahrt — ein
Protokoll aus lauter Probeläufen erzeugt darum nur die Meldung, dass es nichts auszuwerten gibt. Ein
Lauf, der bereits ausgewertet wurde, wird als **„lag schon vor"** gemeldet und bleibt unangetastet;
die übrigen Läufe derselben Datei entstehen trotzdem. Und ein **nachgereichter Lauf**, der älter ist
als alle 30 aufbewahrten, verdrängt keinen neueren — er wird angelegt und sogleich wieder verdrängt,
erscheint also nach dem Einlesen nicht in der Liste.

## Dashboard (Kennzahlen)

Über den Sidebar-Eintrag **„Dashboard"** (im Board-Kontext) zeigt eine KPI-Seite, wie schnell Karten
durch das Board laufen. Grundlage ist die automatisch erfasste Verweildauer jeder Karte pro Spalte —
gemessen bei **jedem** Spaltenwechsel, egal ob per Drag & Drop, ⋮-Menü oder über die API (kanbancompat).

- **Ø Lead Time** und **Ø Cycle Time** als Kennzahl-Kacheln.
- **Ø Verweildauer je Spalte** (Balkendiagramm, in Stunden).
- **Durchsatz je Woche** — abgeschlossene Karten (Liniendiagramm).
- **Ausreißer** — Karten, die über 7 Tage in einer Spalte lagen (Tabelle mit #, Titel, Spalte, Dauer).

Das Dashboard ist für jeden sichtbar, der das Board öffnen darf (auch VIEWER).

## Vorhaben

- **Neues Vorhaben:** in der Vorhaben-Ansicht (Sidebar „Vorhaben") über den Button „Neues
  Vorhaben", oder im Anlege-Dialog Typ „Vorhaben" wählen. Optional ein Kürzel; sonst aus dem
  Titel abgeleitet.
- **Zuordnen:** eine Karte im Anlege-Dialog oder im Detail einem Vorhaben zuordnen. Zugeordnete
  Karten tragen ein **Vorhaben-Badge** und einen farbigen linken Rand.
- **Abgeleitete Zugehörigkeit:** Eine zugeordnete Karte bringt ihren **Nachfahrenbaum** mit —
  alles, was aus ihr entstanden ist, gehört ohne weiteres Zutun zum selben Vorhaben, über
  beliebig viele Stufen. Wer eine Anforderung zuordnet, aus der ein Plan und daraus
  Arbeitspakete entstanden sind, ordnet damit die ganze Kette zu. Das wirkt **rückwirkend auf
  den Bestand**: Karten, die lange vorher entstanden sind, erscheinen genauso, ohne Stichtag und
  ohne Nachpflege. Wird die Zuordnung wieder gelöst, verschwindet der ganze Teilbaum; es bleibt
  nichts zurück, was von Hand aufzuräumen wäre.
- **Grenze:** Die Zugehörigkeit endet an der Board-Grenze. Die zugeordnete Karte muss auf
  **demselben Board** liegen wie das Vorhaben, und eine Kette, die auf ein anderes Board führt,
  endet dort. Karten im Ideen-Speicher und archivierte Karten zählen nicht mit; sie
  unterbrechen die Kette aber auch nicht — ihre Nachfahren bleiben zugehörig.
- **Vorgang eröffnen:** An einer fachlichen Anforderung oder einem Plandokument entsteht das
  Vorhaben in einem Schritt: im Detail der Karte auf **„Vorgang eröffnen"**, Name (vorbelegt mit
  dem Kartentitel) und optional ein Kürzel eingeben. Das Vorhaben wird angelegt, die Karte wird
  seine Anforderung und ist ihm zugeordnet — ohne zweiten Handgriff. Der Knopf erscheint nur,
  wo das möglich ist: nicht an einem Vorhaben, nicht an einer archivierten Karte, nicht im
  Ideen-Speicher und nicht an einer Karte, die schon einem Vorhaben zugeordnet ist.
- **Vorhaben-Übersicht:** Kacheln mit **Fortschrittsbalken** („X/Y Arbeitspakete fertig"). Die
  Kachel nennt die **Anforderung**, aus der der Vorgang eröffnet wurde; der Verweis ist
  **anklickbar** und öffnet die Karte. Trägt das Vorhaben keine Anforderung — etwa weil es von
  Hand zum Gruppieren angelegt wurde —, steht dort nichts.
- **Baum im Detail:** Ein Klick auf die Kachel öffnet das Vorhaben-Detail, und darin steht der
  **Baum** dieses Vorhabens: die Anforderung an der Wurzel, darunter die Pläne, darunter die
  Arbeitspakete. Der Baum ist mit den Pfeiltasten bedienbar; Eingabe auf einer Zeile öffnet die
  jeweilige Karte im selben Dialog. Ist dem Vorhaben noch nichts zugeordnet, sagt das Detail
  genau das.
- **Board-Vorhaben-Filter:** auf dem Board über das Dropdown „Vorhaben-Filter" nur die Karten
  eines Vorhabens anzeigen. Dieser Filter arbeitet **nur auf den direkt zugeordneten** Karten.
  Eine geerbte Karte kann deshalb im Fortschritt mitzählen und beim Filtern trotzdem fehlen —
  das ist so gewollt: Der Filter beantwortet „was liegt hier auf dem Board unter diesem
  Vorhaben", die Zählung „was gehört zu diesem Vorhaben".

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
- **Der Alltag bleibt unberührt:** Karten anlegen, verschieben, archivieren und in den Ideen-Pool
  legen funktioniert **unabhängig** vom Editiermodus.
