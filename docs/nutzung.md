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
  [Ideen-Pool](#ideen-pool)) — mit Board-Recht — „Verschieben…" (Spalte, Board oder Projekt) sowie „Nach
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
- **Anläufe dieser Karte:** War die Karte schon in einem [Lauf](#nachtlauf) oder in einer
  interaktiven Sitzung, steht hier, was ihre Anläufe gekostet haben — die **Dauer je Lauf-Art**
  (Umsetzungs-, Prüf-, Ketten-Lauf und interaktive Sitzung; eine Art, in der die Karte nie vorkam,
  steht als „nicht gelaufen"), die **Summen über alle Lauf-Anläufe** (Kosten, Eingabe, Ausgabe,
  Zwischenspeicher, jede mit ihrer Grundlage „aus *n* von *m* Anläufen"; der Verbrauch einer
  interaktiven Sitzung steht an ihrer eigenen Zeile und geht in diese Summen nicht ein), die
  **Anläufe** selbst mit Datum, [Gattung](#zwei-gattungen), Ergebnis, Dauer und Kosten, jüngster
  zuerst, und die Zahl der **Wiederaufnahmen** (ein gescheiterter Anlauf, auf den später ein echter
  Anlauf folgte; ein übersprungener zählt nicht). Fehlende Werte stehen als „nicht gemessen", nie
  als 0. Den Block sieht nur, wer auch die Auswertung der Läufe sehen darf (Projekt-Rolle `OWNER`,
  oder Plattform-Admin eines teilnehmenden Projekts); Anläufe verdrängter Läufe bleiben darin
  erhalten.
- **Kommentare:** schreiben; eigene Kommentare löschen (Moderation durch ADMIN/OWNER).
- **Aktivität:** ein chronologischer Verlauf am Ende des Details — „*Zeitpunkt* · *Person* · *Aktion*".
  Protokolliert werden Anlegen, Bearbeiten, Zuständige geändert, Verschieben, Archivieren und
  Wiederherstellen (Label-Änderungen werden nicht protokolliert).

### Mehrfachauswahl

Der Button **„Auswählen"** über dem Board schaltet in den Auswahlmodus: Jede Karte bekommt ein
Kästchen, ein Klick auf die Karte hakt sie an oder ab (statt das Detail zu öffnen).

Im Spaltenkopf steht dazu ein eigenes Kästchen: Es wählt **alle Karten einer Spalte** auf einmal —
und zwar die gerade **angezeigten**; was ein Filter oder ein ausgeblendetes Vorhaben verdeckt, bleibt
außen vor. Ein **Strich** zeigt an, dass nur einige Karten der Spalte gewählt sind; der nächste Klick
ergänzt die fehlenden. Sind alle gewählt, hebt der Klick die Auswahl **dieser** Spalte wieder auf —
Karten anderer Spalten bleiben gewählt, sodass sich mehrere Spalten nacheinander einsammeln lassen.

Sobald mindestens eine Karte gewählt ist, erscheint unten eine Aktionsleiste mit vier Massenaktionen:

- **Labels:** öffnet ein Menü mit allen Labels des Boards. Je Label steht dort, wie weit es in der
  Auswahl vertreten ist — ein **voller Haken** („alle gewählten Karten"), ein **Strich** („einige")
  oder ein **leeres Kästchen** („keine"). Ein Klick auf ein Label ohne vollen Haken **hängt es allen**
  gewählten Karten an, ein Klick auf einen vollen Haken **nimmt es allen ab**; die übrigen Labels
  jeder Karte bleiben dabei unberührt. Das Menü bleibt offen, sodass mehrere Labels in einem Zug
  gehen. Hat das Board keine Labels, ist die Taste gesperrt.
- **Verschieben:** alle gewählten Karten in eine Spalte — wahlweise in eine **andere Spalte desselben
  Boards** (vorausgewählt; etwa fünf Pakete auf einmal nach Ready) oder auf ein **anderes Board**
  bzw. in ein anderes Projekt. Nur mit Verschieberecht. Innerhalb des Boards behalten die Karten
  Nummer, Vorhaben-Zuordnung und Abhängigkeiten; beim Board-Wechsel gehen Vorhaben-Zuordnung und
  Abhängigkeiten verloren. Die Karten landen in der Reihenfolge der Auswahl am Ende der Zielspalte;
  eine Karte, die schon dort liegt, bleibt an ihrem Platz.
- **Archivieren** und **In den Papierkorb:** jeweils nach einer Rückfrage.

Alle vier laufen als **eine Transaktion**: Scheitert eine Karte, bleibt die ganze Auswahl unverändert.
Sie treffen immer nur, was der aktive Anzeige-Filter gerade zeigt.

## Labels

Labels sind **pro Board** definiert und werden über den Button **„Labels"** in der Board-Kopfzeile
verwaltet (nur mit Bearbeitungsrecht sichtbar):

- **Anlegen:** Name + Farbe wählen, „Anlegen". Namen müssen je Board eindeutig sein.
- **Ändern/Löschen:** je Label Name und Farbe anpassen und „Speichern", oder über „✕" löschen.
- **Vergeben:** im Karten-Detail über das Feld „Labels" (siehe oben) oder für mehrere Karten auf
  einmal über die [Mehrfachauswahl](#mehrfachauswahl) des Boards. **Filtern** nach Labels in der
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

## Leitstand eines Boards {#leitstand}

Der **Leitstand** (Sidebar-Eintrag **„Leitstand“** im Board-Kontext, Route
`/boards/:boardId/leitstand`) ist die **Hauptansicht eines Boards**. Er ersetzt die frühere
Kennzahlen-Seite „Dashboard“; die alte Adresse `/boards/:boardId/dashboard` leitet auf ihn weiter.

Von oben nach unten führt er:

1. **Laufband** — der jüngste Lauf als schmales Band: Melder (pulsierend, solange er läuft),
   Titel des Laufs, die berührte Karte, der Zeitpunkt, rechts „Zeit“ in Minuten und „Kosten“ in
   Dollar.
2. **Kennzahlen** — vier Kacheln: **Durchsatz · Woche**, **Durchlaufzeit**,
   **Implementierungszeit** und eine Kachel zu den Läufen. Die Implementierungszeit misst, wie
   lange eine erledigte Karte insgesamt in „In Progress“ lag; mehrere Aufenthalte zählen zusammen.
3. **Verbrauch** — Token und Kosten mit Zeitraum-Wahl; ausführlich unter
   [Verbrauch (Leitstand)](#verbrauch-leitstand).
4. **Herkunft** — eine Zeile zum jüngsten Lauf: ob er eingeliefert oder im Browser hochgeladen
   wurde, dazu der Name des Tokens, die Zahl der Vorgänge und die der ungedeuteten Zeilen.
5. **Rumpf** — vier Platten: **„Letzter Lauf · ‹Art›“** mit seinen Vorgängen (die Kartennummer
   öffnet die Karte zum Lesen), **„Durchsatz“** mit den abgeschlossenen Karten je Woche,
   **„Abbruchgründe“** mit den Fehlerklassen über die aufbewahrten Läufe und **„Vorhaben“** mit
   den offenen.

**Was das Recht entscheidet:** Laufband, Lauf-Kachel, Verbrauch, „Letzter Lauf“ und
„Abbruchgründe“ sieht nur, wer auch die [Läufe](#nachtlauf) sehen darf — der **Owner** des
Projekts und **Plattform-Admins**, sofern das Projekt am
[Plattform-Leitstand](#plattform-leitstand) teilnimmt. Ohne dieses Recht entfallen sie still; die
Board-Kennzahlen und „Durchsatz“ bleiben.

**Grundlage der Board-Kennzahlen** ist die automatisch erfasste Verweildauer jeder Karte pro
Spalte — gemessen bei **jedem** Spaltenwechsel, egal ob per Drag & Drop, ⋮-Menü oder über die API
(kanbancompat).

**Nicht mehr dargestellt:** die **Ø Verweildauer je Spalte** und die Liste der **Ausreißer**
(Karten, die über sieben Tage in einer Spalte lagen). Beide Kennzahlen werden für die Steuerung
der KI-Arbeit nicht gebraucht und sind bewusst aus dem Leitstand genommen worden; das API-Feld
`outliers` bleibt im Backend bestehen.

## Läufe {#nachtlauf}

Der **Bereich „Läufe"** wertet die Protokolle des Nacht-Runners aus: Er zeigt je Lauf, welche
Arbeitspakete durchliefen, welche stehenblieben und woran es lag. Er ist **projektweit**, nicht an
ein Board gebunden.

Erreichbar über den Sidebar-Eintrag **„Läufe"** (Route `/projects/:projectId/nachtlauf`).
Sichtbar ist er nur für den **Owner** des Projekts und für **Plattform-Admins**, sofern das Projekt
am [Plattform-Leitstand](#plattform-leitstand) teilnimmt — siehe
[Rollen & Rechte](rollen-und-rechte.md#projekt-rollen-rechte-matrix).

**Normalweg: Der Runner liefert selbst ein.** Ein Lauf kommt ohne jeden Handgriff ans Board — der
Nacht-Runner meldet ihn über ein projektgebundenes Zugriffstoken beim Start und schreibt ihn danach
fort, bis er abgeschlossen ist. Welche Angaben dabei mitkommen, hängt an der Fassung des Runners:
Budgets, ihre Herkunft, die Stufen einer Kette, Modellzeit und Züge erscheinen erst, wenn der
Runner sie mitschickt; bis dahin steht dort „nicht angegeben" bzw. „nicht gemeldet".

**Rückfall: die Ergebnisdatei einlesen.** Kam ein Lauf nicht ans Board — etwa ohne Zugriffstoken
oder weil die Einlieferung scheiterte —, liest der Button **„Protokoll einlesen"** oben rechts die
Ergebnisdatei des Runners (`night-run-<datum>-<uhrzeit>.json`). Die **Datei wird im Browser
ausgewertet und nicht hochgeladen** — an den Server geht allein die verdichtete Auswertung
(Kennzahlen, Zustände, Kartennummern, Fehlerklassen und kurze Auszüge). Die Datei trägt Pfade und
die Kennzahlen der Sessions; die bleiben, wo sie sind. Dieselbe Datei lässt sich erneut wählen,
ohne die Seite neu zu laden. **Budgets und Stufen** eines so eingelesenen Laufs zeigt die Seite nur
bis zum nächsten Neuladen: Der Rückfallweg liefert sie nicht an den Server.

**Der Stand am Board gewinnt.** Wird die Ergebnisdatei eines Laufs eingelesen, den der Runner schon
eingeliefert hat, ändert sich an seiner Anzeige nichts: Es entsteht kein zweiter Lauf, kein Wert
ändert sich, und es kommt nichts hinzu, was die Einlieferung nicht kannte. Das gilt auch für Läufe,
die vor dieser Regel eingeliefert wurden.

Einige Angaben stehen deshalb **nur bei einem eingelesenen, noch nicht eingelieferten Lauf**, weil
allein die Ergebnisdatei sie trägt. Bei einem Lauf, den der Runner eingeliefert hat, entfallen:

- bei einem Kettenlauf die Angaben „Ketten durchgelaufen", „Karten entstanden", „Laufzeit über alle
  Stufen" und „Kosten des Zyklus",
- der Grund, an dem eine Stufe der Kette abbrach,
- die Dokumente je Stufe.

Bei einem eingelieferten **Kettenlauf** trägt jede erreichte Stufe im Stufenband ihre Kosten, die
Kostenkachel teilt das Gesamt in **Planung** (die Stufen der Kette) und **Umsetzung** (der Rest),
und der Kettenvorgang nennt den **angelegten Plan und die Pakete** als Verweise — ermittelt aus der
Herkunft der Karten am Board, beschränkt auf Karten, die während des Laufs entstanden. Jede
Paketzeile nennt ihren Plan („Paket aus Plan #N"). Die Dauer des Kettenvorgangs ist die Summe seiner
Stufen.

Der **Titel** eines Laufs nennt seine Nummer, das Startdatum und die Startzeit („Lauf #412 · 14.
September, 22:05"); ein eben eingelesener Lauf ohne Nummer heißt „Lauf · 14. September, 22:05".
Darüber steht der **Zyklus**, zu dem er gehört („Zyklus vom 14.09.2026 auf den 15.09.2026"), und die
Art des Laufs.

Jeder Lauf steht als aufklappbare Zeile da — Startzeitpunkt, Art des Laufs („Umsetzungs-Lauf",
„Prüf-Lauf" oder „Nachtplan-Lauf"), Dauer sowie „N bearbeitet, M übergangen". Ein Nachtplan-Lauf
wird angezeigt, aber nicht aufbewahrt — er verschwindet nach einem Neuladen der Seite wieder.
Aufgeklappt zeigt er seine Arbeitspakete, jedes mit einem der **vier Zustände**:

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

**Nur die letzten zwei Zyklen:** Die Liste zeigt standardmäßig die Läufe des laufenden und des
vorigen Zyklus; darunter blendet **„Ältere Läufe anzeigen (N)"** die übrigen für diesen Besuch ein.
Sichtbar bleiben außerdem ein älterer Lauf, der noch läuft, der über einen Verweis angesteuerte Lauf
und ein eben eingelesener. Begrenzt wird nur die Anzeige: Aufbewahrung, Verbrauchsauswertung und
Häufigkeiten zählen weiter alle aufbewahrten Läufe.

**Aufbewahrung:** Je Projekt bleiben die **letzten 190 Läufe** erhalten — genug, um bei zwei Läufen
je Zyklus den laufenden, den zuletzt abgeschlossenen und den Vormonat vorzuhalten; ältere fallen
heraus, sobald neue hinzukommen. Die **Arbeitspakete** eines verdrängten Laufs bleiben dabei bestehen: Sie tragen
Projekt, Startzeitpunkt und Lauf-Art selbst, damit die Messwerte einer Karte nicht mit dem Lauf
verschwinden. In der Liste der Läufe erscheinen sie nicht mehr. Diese **verwaisten Arbeitspakete**
haben eine eigene Grenze: Je Projekt bleiben die **letzten 2000** erhalten, gemessen am
Startzeitpunkt ihres Laufs. Läufe und verwaiste Arbeitspakete werden also getrennt begrenzt — die
Pakete eines noch aufbewahrten Laufs zählen nicht mit und fallen erst mit ihm.

Drei Sonderfälle, die keine Fehler sind: Ein **Probelauf** (`DRY-RUN`) wird nicht aufbewahrt — ein
Protokoll aus lauter Probeläufen erzeugt darum nur die Meldung, dass es nichts auszuwerten gibt. Ein
Lauf, der bereits ausgewertet wurde, wird als **„lag schon vor"** gemeldet und bleibt unangetastet;
die übrigen Läufe derselben Datei entstehen trotzdem. Und ein **nachgereichter Lauf**, der älter ist
als alle 190 aufbewahrten, verdrängt keinen neueren — er wird angelegt und sogleich wieder verdrängt,
erscheint also nach dem Einlesen nicht in der Liste.

## Verbrauch (Leitstand)

Der **Leitstand** (Sidebar-Eintrag **„Leitstand"** im Board-Kontext, Route
`/boards/:boardId/leitstand`) führt einen Bereich **„Verbrauch"**: was die Arbeit an diesem Projekt
an Claude-Code-Verbrauch gekostet hat. Sichtbar ist er — wie die [Auswertung der Läufe](#nachtlauf)
— nur für den **Owner** des Projekts und für **Plattform-Admins**, sofern das Projekt am
[Plattform-Leitstand](#plattform-leitstand) teilnimmt.

### Zwei Gattungen

Gezählt werden zwei **Gattungen** von Einträgen:

- **Lauf** — ein Lauf des Nacht-Runners.
- **Interaktive Sitzung** — eine Arbeitssitzung am Rechner eines Menschen.

**„Gattung" ist nicht „Herkunft".** Die Gattung sagt, *was* ein Eintrag ist — Lauf oder
interaktive Sitzung. Die **Herkunft** ist eine zweite, davon unabhängige Angabe und sagt, *auf
welchem Weg* er ans Board kam: von Hand im Browser eingelesen oder maschinell mit einem
projektgebundenen Zugriffstoken gemeldet. Sie steht im Leitstand auf der eigenen Platte
„Herkunft". Eine interaktive Sitzung ist Gattung *interaktive Sitzung* und Herkunft *Token* —
beide Angaben stehen nebeneinander, keine ersetzt die andere.

### Was der Bereich zeigt

Über den Kacheln steht der gewählte Zeitraum — **Zyklus · Woche · Monat** — und daneben, aus wie
vielen Einträgen die Zahlen stammen („*Zyklus vom 17.09.2026 auf den 18.09.2026* · 2 Läufe ·
5 Sitzungen"). Die Kacheln selbst:

- **Eingabe-Token** mit einem Balken, der die Eingabe in **„Cache gelesen"** und **„frisch"**
  aufteilt. Der Balken beantwortet eine andere Frage als die Gattungen und wird nicht auf sie
  umgewidmet.
- **Ausgabe-Token**, darunter der Verlauf über die Zyklen des Zeitraums.
- **Kosten**, mit dem Vergleich zum Vorzeitraum (▲/▼ und der Unterschied in Dollar).
- **Gesamt über die Laufzeit** — siehe unten.

Unter jeder Summe stehen die beiden **Anteile**: „aus Läufen" und „aus interaktiven
Sitzungen". Die Summe ist genau ihre Addition; kein Eintrag zählt in beiden.

**Der Posten „ohne Karte"** steht an der Kosten-Kachel und trägt den Verbrauch, der keinem
Arbeitspaket zuzuordnen war. Er wird nicht auf die berührten Karten verteilt — eine Verteilung
erfände eine Genauigkeit, die niemand gemessen hat. Liegt kein gemessener Rest vor, fehlt der
Posten ganz; eine 0 behauptete, es gäbe keinen.

**Die Kachel „Gesamt über die Laufzeit"** summiert über alle aufbewahrten Läufe und Sitzungen des
Projekts und hängt nicht am gewählten Zeitraum — ein Klick auf „Woche" ändert an ihr nichts. Ihr
Fuß nennt die **Abdeckung** dieser Summe, und zwar zweigeteilt: ab welchem Datum überhaupt ein
Eintrag aufbewahrt ist (oder „ohne aufbewahrten Eintrag") und ab wann interaktive Sitzungen erfasst
werden (oder „Sitzungen nicht erfasst"). Die Zahl ist damit die Summe des **Aufbewahrten**, nicht
die des Gelebten: Was der Ringpuffer verdrängt hat, fehlt darin.

### Kosten je Stufe der Kette

Die Verbrauchsauswertung auf der Seite [„Läufe"](#nachtlauf) — Ansicht Zyklus, Woche oder Monat —
führt unter den Zyklen und der Aufstellung je Vorhaben die Platte **„Stufen der Kette"**: je Stufe
(**Plan**, **Prüfung**, **Pakete**, **Abdeckung**) die Kosten im Zeitraum und wie viele Vorgänge
sie durchlaufen haben, dazu ein Balken im Verhältnis zur teuersten Stufe. Die Reihenfolge ist die
der Kette.

- **Läufe ohne Stufen erscheinen darin nicht** — ein Umsetzungs- oder Prüf-Lauf hat keine. Liefen
  im Zeitraum keine Ketten, fehlt die Platte ganz.
- **Es gibt keine Zeile „ohne Stufe"**, anders als „Ohne Vorhaben" in der Aufstellung je Vorhaben:
  Sie trüge bei einem Umsetzungs-Lauf den Verbrauch eines ganzen Zyklus, und die Aufstellung handelt
  von der Kette.
- Fehlen die Kosten einer Stufe, steht dort „nicht gemessen" und kein Balken — nie eine 0.

### Eine Sitzung zählt zum Zeitraum ihres Beginns

Ein Eintrag gehört zu dem Zeitraum, in dem er **beginnt** — bei einer Sitzung also zu dem
Zeitpunkt, an dem sie eröffnet wurde, nicht zu dem, an dem sie endete. Eine Sitzung, die über eine
Zeitraumgrenze hinweg läuft, wird nicht aufgeteilt.

Dabei gilt die **Tagesgrenze 12:00** zonenlokal: Ein **Zyklus** läuft von 12:00 bis 12:00 und
enthält alle Läufe, die darin starten — auch tagsüber angestoßene; wer vor 12:00 startet, gehört
zum Zyklus davor. Für interaktive Sitzungen hat das eine Folge, die man kennen muss: **Eine
Sitzung, die vormittags vor 12:00 beginnt, zählt zum Zyklus davor.** Wer am Donnerstag um 9:30 Uhr
zu arbeiten anfängt, findet seinen Verbrauch also unter dem Zyklus von Mittwoch auf Donnerstag,
nicht unter dem von Donnerstag auf Freitag. Die Regel ist
dieselbe wie für Läufe — eine zweite Regel für Sitzungen machte die Summe von der Gattung
abhängig.

### „nicht erfasst", „teilweise erfasst" und „nicht gemessen"

Drei Angaben, die alle drei **keine Null** sind und sich paarweise unterscheiden:

- **„nicht erfasst"** meint einen **Zeitraum ohne Messung**: Er liegt ganz vor dem Beginn der
  Erfassung interaktiver Sitzungen in diesem Projekt, es existiert für ihn also überhaupt keine
  Zahl. Anders als bei „teilweise erfasst" liegt hier kein Teil der Messung vor, und anders als bei
  „nicht gemessen" geht es nicht um einen einzelnen bekannten Eintrag.
- **„teilweise erfasst"** meint einen **Zeitraum, der den Erfassungsbeginn schneidet**: Die Zahlen
  stehen da, sie decken aber nur den späteren Teil ab, der interaktive Anteil ist deshalb zu klein.
  Anders als bei „nicht erfasst" fehlt hier nicht die Messung, sondern ein Stück von ihr.
- **„nicht gemessen"** meint auf dem Kartenblatt (siehe [Karten-Detail](#karten-detail), „Anläufe
  dieser Karte") einen **bekannten Lauf oder eine bekannte Sitzung ohne Zahl**: Der Eintrag steht in
  der Liste, zu diesem einen Wert liegt aber kein Messwert vor. Anders als bei den beiden anderen
  Angaben geht es nicht um einen Zeitraum, sondern um einen einzelnen Eintrag.

Dazu kommen zwei Sätze über den Zeitraum als Ganzes, die keine Lücke der Erfassung sind: „In diesem
Zeitraum hat weder ein Lauf noch eine Sitzung stattgefunden." (es wurde nicht gearbeitet) und „In
diesem Zeitraum liefen Läufe, ihr Verbrauch liegt aber nicht vor." (es wurde gearbeitet, die Zahlen
fehlen).

### Wo Zahlen fehlen können

Erfasst wird eine interaktive Sitzung nur, wenn in ihrem Arbeitsverzeichnis ein projektgebundenes
Zugriffstoken liegt; Sitzungen in einem frisch angelegten Git-Worktree bleiben außen vor. Beides —
samt Hook, Erfassungsbeginn und Aufbewahrungsgrenzen — steht in
[Betrieb: Meldeweg der interaktiven Sitzungen](betrieb.md#meldeweg-der-interaktiven-sitzungen).
Die Tatsachengrundlage dazu steht in
[Befund: Verbrauchsangaben, Hook-Ereignisse und Worktrees](befund-interaktive-sitzungen.md).

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
- **Teilnahme am Plattform-Leitstand:** In der Projektliste erscheint bei aktivem Editiermodus je
  Projekt ein **Ankreuzfeld**, mit dem OWNER und ADMIN mit echter Mitgliedschaft die Teilnahme
  ihres Projekts am [Plattform-Leitstand](#plattform-leitstand) schalten — der Plattform-Admin
  schaltet es für ein fremdes Projekt nicht.
- **Der Alltag bleibt unberührt:** Karten anlegen, verschieben, archivieren und in den Ideen-Pool
  legen funktioniert **unabhängig** vom Editiermodus.

## Plattform-Leitstand

Der **Plattform-Leitstand** ist die Startseite eines **Plattform-Admins** nach dem Anmelden (Sidebar
„Verwaltung" → „Plattform-Leitstand"). Er führt **drei Bereiche**, von oben nach unten: **Aktive
Läufe**, **Beendete Läufe** und **Störungen**. Alle drei zeigen ausschließlich Projekte, die am
Plattform-Leitstand **teilnehmen**.

Die Seite **frischt sich selbst auf**: Was sich ändert, erscheint von allein — du musst sie nicht
neu laden.

### Aktive Läufe

Jeder Lauf, der **gerade arbeitet** — mit Projekt, dem Wort „läuft seit" samt bisheriger Dauer,
anklickbarer Lauf-Kennung und einem pulsierenden Melder. Arbeitet gerade nirgends ein Lauf, steht
das als ausdrücklicher Satz da statt als leere Fläche.

### Beendete Läufe

Jeder **beendete** Lauf des laufenden Zyklus — mit Projekt, Startzeitpunkt, anklickbarer Lauf-Kennung
und seinem **Ausgang**. Auch hier steht ein ausdrücklicher Satz, solange noch kein Lauf beendet ist.

„Laufender Zyklus" meint denselben Zeitraum, den auch die Auswertung der Läufe zieht: **von 12:00 bis
12:00** zonenlokal. Über die Zugehörigkeit entscheidet der **Startzeitpunkt** des Laufs, nicht sein
Ende. Um 12:00 wechselt der Bereich deshalb auf den neuen Zyklus und ist zunächst leer.

Der Ausgang steht als Wort da — eines von dreien:

- **gelungen** — der Lauf ist durch, nichts steht aus.
- **nicht gelungen** — der Lauf ist gescheitert, hat gar nicht gearbeitet oder ist verstummt (siehe
  Stillefrist).
- **mit Vorbehalt** — der Lauf ist durch, sein maßgebliches Arbeitspaket wartet aber noch auf einen
  Menschen oder wurde zurückgestellt.

**Die Stillefrist.** Ein unfertiger Lauf, der über diese Frist hinweg **kein Lebenszeichen** gibt,
gilt als **nicht gelungen** — sonst bliebe ein abgeschossener Runner für immer als „läuft" stehen.
Die Frist ist ein **Einstellwert der Plattform** (`manban.nightrun.stille-frist`, Vorgabe 90
Minuten): Sie gilt für alle Projekte gleich und hat bewusst **keine Oberfläche** — wer sie ändern
will, ändert die Konfiguration der Instanz.

Ein durch Stille beendeter Lauf ist **keine Störung**. Er erscheint hier als „nicht gelungen" und
taucht im Bereich „Störungen" nicht auf.

### Störungen

Jede nicht quittierte Störung aus den Läufen teilnehmender Projekte — mit Projekt, Zeitpunkt,
anklickbarer Lauf-Kennung, Grund und dem Knopf **„Störung löschen"**.

Ob ein Projekt teilnimmt, entscheidet ausschließlich das Projekt selbst — OWNER oder ADMIN mit
echter Mitgliedschaft, über das Teilnahme-Ankreuzfeld im [Editiermodus](#editiermodus) der
Projektliste. Der Plattform-Admin sieht nur Läufe und Störungen teilnehmender Projekte und kann die
Teilnahme selbst nicht erzwingen.

## Board-Befehle unter Last {#board-befehle-unter-last}

Das Board begrenzt die Befehle, die eine Person über ein Zugriffstoken schickt (siehe
[Durchsatzbremse](betrieb.md#durchsatzbremse-für-zugriffstoken)). Ein Befehl über dem Kontingent
wird mit `429` abgewiesen und führt nichts aus. Der Board-Adapter des claude-workflow-kit
(`node .claude/kit/board.mjs`, ab Kit 3.0.1) wartet dann selbst und wiederholt. Du musst nichts tun,
solange er sich nicht mit einer Fehlermeldung zurückmeldet.

**Was wiederholt wird.** Eine Abweisung wegen Überlast (`429` mit dem Problem-Detail
`type: urn:manban:overload`) bei jedem Befehl, dazu Zeitablauf und abgebrochene Verbindungen. Bei
einem Serverfehler (`5xx`) nur, wenn die Wiederholung gefahrlos ist: bei lesenden Befehlen, beim
Verschieben und Ändern, und beim Anlegen einer Karte oder eines Kommentars, weil diese beiden einen
Idempotenz-Schlüssel tragen. Ein `429` **ohne** `urn:manban:overload` kommt nicht von dieser Bremse
und wird nicht wiederholt, ebenso wenig ein ungültiges Token.

**Wie lange.** Jeder Versuch hat eine eigene Zeitgrenze; die Wartezeit dazwischen wächst und folgt
dem `Retry-After` des Servers. Für alle Versuche zusammen gilt ein **Gesamtbudget von 30 Sekunden**,
im Nachtlauf (gesetztes `KIT_AGENT_MODEL`) von **120 Sekunden**. Jede Wiederholung meldet sich mit
einer Zeile auf stderr, damit Warten von Hängen zu unterscheiden ist:

```
board: POST /api/kanban/items — Versuch 1 endete mit HTTP 429, erneut in 1000 ms (Frist 30 s)
```

**Die drei Rückmeldungen.** Wie ein Befehl ausging, sagt der Adapter in einer von drei Lagen:

- **ausgeführt** — die normale JSON-Ausgabe auf stdout, Exit-Code 0.
- **nicht ausgeführt** — `Fehler: …` auf stderr, Exit-Code 1. Der Befehl hat nichts bewirkt, etwa
  weil das Budget unter Abweisungen ablief, der Server nicht erreichbar war oder die Anfrage
  ungültig ist. Du kannst ihn gefahrlos erneut absetzen.
- **Ausgang unklar** — `Fehler: …` mit dem Zusatz „Ausgang unklar". Ein schreibender Befehl ging
  hinaus, blieb aber ohne verwertbare Antwort (Zeitablauf, Verbindungsabbruch oder `5xx`). Die
  Wirkung kann eingetreten sein.

**Was bei „Ausgang unklar" zu tun ist.** Die Meldung nennt den Idempotenz-Schlüssel und das
vollständige Wiederholkommando. Wiederhole den Befehl **mit genau diesem Schlüssel**, nie ohne:

```
node .claude/kit/board.mjs issue comment 1004 --text-file bericht.md --idempotency-key 3f2c…
```

Derselbe Schlüssel führt die Wirkung höchstens einmal aus: Kam der erste Versuch an, liefert das
Board dessen Ergebnis, statt eine zweite Karte oder einen zweiten Kommentar anzulegen. Ohne den
Schlüssel wäre die Wiederholung ein neuer Auftrag. Trägt die Meldung keinen Schlüssel (andere
schreibende Befehle, etwa ein Label), sieh erst am Board nach, bevor du wiederholst.

**Der Schalter `--idempotency-key <wert>`** gibt es bei `issue create` und `issue comment`. Er setzt
den Schlüssel von außen, statt ihn je Auftrag neu zu erzeugen. Das Board hält einen Schlüssel
24 Stunden. Einen Schlüssel für einen **anderen** Befehl als beim ersten Mal weist es mit `409` ab:
Für einen neuen Befehl gehört ein neuer Schlüssel.

**Mit `tbx`.** Das mitgelieferte Kommandozeilen-Werkzeug `cli/tbx.mjs` verhält sich genauso: dieselben
Wiederholregeln, dieselben drei Rückmeldungen und der Schalter `--idempotency-key <wert>` bei
`tbx issue create` und `tbx issue comment`. Das Wiederholkommando in der Meldung beginnt mit `tbx`.
Der einzige Unterschied: `tbx` kennt keinen Nachtlauf, sein Gesamtbudget ist **immer 30 Sekunden**.
