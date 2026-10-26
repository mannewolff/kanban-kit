# Nutzung

## Registrieren & Anmelden

- **Registrieren:** über „Registrieren" auf dem Anmeldeschirm, nur mit **E-Mail-Adresse und
  Anzeigename**. Ein Passwort gibt es nicht. Nach der Registrierung kommt gleich ein Anmeldecode.
- **Anmelden:** E-Mail-Adresse eingeben → eine Mail bringt einen **sechsstelligen Code** und einen
  **Link**. Den Link öffnest du auf dem Gerät, das angemeldet werden soll; liest du die Mail auf
  einem anderen Gerät, tippst du dort den Code ab. Ein Klick meldet immer nur das Gerät an, auf
  dem er geschieht. Die erste Anmeldung per Code bestätigt zugleich deine E-Mail-Adresse — eine
  eigene Bestätigungsmail gibt es nicht. Ohne Mailserver steht der Code im Protokoll des Servers
  (siehe [Betrieb](betrieb.md#anmeldecode-und-mails-ohne-mailserver)).
- **Der Code** gilt 10 Minuten und nur einmal. Ein neu angeforderter Code macht den alten samt Link
  ungültig, nach 5 falschen Eingaben verfällt er. Auf jede Anforderung kommt dieselbe Antwort
  („Falls es ein Konto mit dieser Adresse gibt, ist ein Code unterwegs.“) — sie verrät nicht, ob es
  ein Konto gibt. Je Adresse werden höchstens 5 Anforderungen pro Stunde angenommen.
- **Nach der Anmeldung:** Hast du **genau ein** Projekt, wirst du direkt zur Boardauswahl
  geleitet; hat dieses **genau ein** Board, direkt aufs Board. Über die Seitenleiste „Projekte" bzw.
  die Zurück-Links kommst du jederzeit zu den Listen zurück (um weitere anzulegen).
- **Sitzungsdauer:** Wer das Board nutzt, bleibt angemeldet — jede Nutzung verlängert die Sitzung.
  Erst nach **30 Tagen ohne Nutzung** braucht es einen neuen Code.
- **Abmelden** beendet die Sitzung **auf diesem Gerät** sofort; andere Geräte bleiben angemeldet.
- **Fehlermeldungen** sind konkret: Ein falscher oder abgelaufener Code wird abgewiesen; ein
  **gesperrtes** Konto sieht „Konto gesperrt", ein noch nicht freigegebenes „Das Konto wartet auf
  die Freigabe".

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
- **Rollen:** Die Fläche mit den Spalten reicht bis zum unteren Fensterrand und rollt in sich,
  senkrecht wie waagerecht; die Spaltenköpfe bleiben dabei oben stehen, und der waagerechte
  Rollbalken steht immer am unteren Rand. Zieht man eine Karte an einen Rand der Fläche, rollt sie
  in diese Richtung mit — mit der Maus wie mit dem Finger.
- **Status eines Arbeitspakets:** Die fünf Prozessspalten **Backlog**, **Ready**, **In Progress**,
  **In Review** und **Done** geben einer Karte, die dort ankommt, ihren Status. Eine **eigene
  Spalte** (jeder andere Name) übernimmt den Status der nächsten Prozessspalte **links** davon; gibt
  es links keine, gilt „Backlog“. Bei `Backlog · X · Y · Ready` bekommt eine Karte in X oder Y also
  „Backlog“, auch wenn sie aus Ready zurückkommt. Das gilt bei jedem Verschieben, Anlegen und
  Übertragen auf ein anderes Board und ersetzt einen von Hand gesetzten Status. Wer die Spalten
  umsortiert, ändert den Status liegender Karten nicht. Vorhaben und Karten mit `[Idee]`,
  `[Fachlich]` oder `[Plan]` im Titel tragen keinen Status.
- **Knopf `kit:nightrun` in Ready:** Führt das Board das Label `kit:nightrun`, trägt der Kopf der
  Spalte Ready neben Kartenzahl und Sortier-Symbol einen kleinen Knopf mit diesem Namen. Ein Druck
  setzt das Label ohne Rückfrage an alle Karten, die die Spalte gerade zeigt — bei aktivem Filter
  also nur an die sichtbaren. Ausgenommen sind Vorhaben und Karten mit `[Mensch]` bzw. `[Human]` im
  Titel. Tragen alle Karten das Label schon oder kann keine es bekommen, ist der Knopf gesperrt und
  nennt beim Darüberfahren den Grund. Abnehmen lässt sich das Label wie jedes andere an der Karte
  oder über die [Mehrfachauswahl](#mehrfachauswahl).

## Karten

- **Anlegen:** über den Button **„Neu anlegen"** oben oder die Taste **`+`** — beide legen eine Karte
  stets in der **ersten Spalte** (Backlog) an. Titel + Markdown-Beschreibung.
- **Verschieben:** per Drag & Drop zwischen den Spalten. Alternativ über das **⋮-Menü** der Karte mit
  den Einträgen „Nach links verschieben" und „Nach rechts verschieben" (jeweils genau eine Spalte
  weit) — auch per Tastatur bedienbar. In der ersten bzw. letzten Spalte fehlt der jeweils sinnlose
  Eintrag.
- **⋮-Menü:** „Duplizieren", „Archivieren" — mit Board-Recht — „Verschieben…" (Spalte, Board oder Projekt) sowie „Nach
  links/rechts verschieben". Der Eintrag **„Bearbeiten"** (öffnet das Detail im Bearbeiten-Modus)
  erscheint nur bei aktivem [Editiermodus](#editiermodus); der Button „Bearbeiten" im Karten-Detail
  steht dagegen immer zur Verfügung, sofern du Bearbeitungsrecht hast. Bei **archivierten** Karten
  ist das Menü leer.
- **Auf der Karte sichtbar:** farbige **Label**-Chips, eine gesetzte **Fälligkeit** („📅 *Datum*",
  überfällige rot und fett) sowie rechts unten die **Avatare der Zuständigen** (Initialen, bis zu vier).
- **Done-Countdown:** Karten in einer Done-Spalte zeigen „wird in X Tagen archiviert"
  (steuerbar über `MANBAN_DONE_RETENTION_DAYS`).

### Karten-Detail

Klick auf eine Karte öffnet das Detail.

**Direkter Link:** `…/projects/<Projekt>/karten/<Nummer>` öffnet das Board der Karte mit offenem
Detail — zum Weitergeben, etwa im Chat. Der Link hält, auch wenn die Karte inzwischen auf einem
anderen Board liegt, denn die Kartennummer ist im Projekt fest. Abgemeldet führt er über die
Anmeldung zur Karte. Gibt es die Karte nicht oder fehlt der Zugriff, zeigt die Projektseite
„Karte #N ist nicht erreichbar.“ Beim Schließen des Details verschwindet die Karte aus der Adresse.

Das Detail zeigt:

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
- **Anläufe dieser Karte:** War die Karte schon in einem [Run](#nachtlauf) oder in einer
  interaktiven Sitzung, steht hier, was ihre Anläufe gekostet haben — die **Dauer je Art des Runs**
  (Umsetzung, Prüfung, Kette und interaktive Sitzung; eine Art, in der die Karte nie vorkam,
  steht als „nicht gelaufen"), die **Summen über alle Anläufe aus Runs** (Kosten, Eingabe, Ausgabe,
  Zwischenspeicher, jede mit ihrer Grundlage „aus *n* von *m* Anläufen"; der Verbrauch einer
  interaktiven Sitzung steht an ihrer eigenen Zeile und geht in diese Summen nicht ein), die
  **Anläufe** selbst mit Datum, [Gattung](#zwei-gattungen), Ergebnis, Dauer und Kosten, jüngster
  zuerst, und die Zahl der **Wiederaufnahmen** (ein gescheiterter Anlauf, auf den später ein echter
  Anlauf folgte; ein übersprungener zählt nicht). Fehlende Werte stehen als „nicht gemessen", nie
  als 0. Den Block sieht nur, wer auch die Auswertung der Runs sehen darf (Projekt-Rolle `OWNER`,
  oder Plattform-Admin eines teilnehmenden Projekts); Anläufe verdrängter Runs bleiben darin
  erhalten.
- **Stufenleiste der Nacht-Kette:** Eine Karte, deren Titel mit `[Fachlich]` oder `[Plan]` beginnt,
  trägt eine waagerechte Leiste mit den Stationen der [Nacht-Kette](#nachtlauf). Über sie legst du
  fest, wie weit die Kette diese Karte in der Nacht führt, und startest sie — ohne Labels von Hand
  zu setzen. Andere Karten zeigen keine Leiste.
  - **Ziel wählen:** Wählbar sind vier Ziele, je eines mit seinem Label: **Plan** (`ziel:plan`),
    **Arbeitspakete** (`ziel:pakete`), **Umsetzung** (`ziel:umsetzung`) und **Veröffentlichung
    vorbereitet** (`ziel:push-vorbereitet`). Ein Klick setzt das Ziel und nimmt ein vorher gewähltes
    ab; es gibt höchstens eines je Karte. Ohne gewähltes Ziel ist **Arbeitspakete** markiert, und
    „Kette starten“ setzt dieses markierte Ziel als Label `ziel:pakete` mit: Die Kette endet dann
    nach der Abdeckungsprüfung. Wer eine Karte von Hand mit `kit:night` startet, ohne ein Ziel-Label
    zu setzen, schickt sie durch die volle Kette bis „Veröffentlichung vorbereitet“: Die Leiste
    markiert dann dieses Ziel, weil das Kit genau das fährt, und darunter steht „Kein Ziel-Label
    gesetzt — die Kette läuft bis zur vorbereiteten Veröffentlichung.“ Trägt die Karte `kit:durchziehen`, gilt
    sie als Ziel „Umsetzung“, und ein kürzeres Ziel bliebe wirkungslos. An einem Plan sind Plan und
    Prüfung schon erbracht und nicht wählbar. Die Pfeiltasten wechseln zwischen den Zielen.
  - **Planprüfung:** An einer fachlichen Anforderung wählt ein Schalter, ob ein oder zwei Modelle
    den entstehenden Plan prüfen (`planreview:1` oder `planreview:2`); ohne Wahl gilt die Vorgabe des
    Projekts. An einem Plan, oder wenn der Plan der Anforderung schon eine Zeile „Plan-Review:“
    trägt, ist der Schalter gesperrt.
  - **Kette starten:** Der Knopf setzt `kit:night` und gibt die Karte damit einmal zur Übernahme
    durch den nächsten Runner frei; er startet keinen Runner und legt keinen Zeitpunkt fest. Ist ein
    Ziel ab „Umsetzung“ gewählt, steht vorher unter der Leiste „Mit diesem Ziel gibst du das GO für
    alle Arbeitspakete dieser Karte.“, und der Start verlangt eine ausdrückliche Bestätigung. Bei
    kürzeren Zielen genügt der eine Klick.
  - **Sperrhinweise:** Der Start ist gesperrt, und ein Hinweis nennt den Grund, wenn einer
    fachlichen Anforderung die abgeschlossene fachliche Prüfung fehlt (kein `review:fertig`), einem
    Plan die Zeile „Plan-Review:“, oder wenn an der Karte eine offene Frage auf einen Menschen
    wartet (`kit:klaeren`, `lauf:wartet`). Fehlt am Board eines der Labels, legt die Leiste es nicht
    an: Der Hinweis nennt seinen Namen, und ein Mensch legt es in den Board-Labels an — oder ein
    Plattform-Admin legt alle fehlenden auf einmal an, über [Standard-Labels](#standard-labels).
  - **Start zurücknehmen:** Solange kein Runner die Karte übernommen hat, nimmt „Start
    zurücknehmen“ `kit:night` wieder ab; danach sind Ziel und Prüferzahl wieder frei wählbar.
    Übernimmt ein Runner die Karte, nimmt er `kit:night`, `ziel:*` und `planreview:*` zusammen ab, und
    die Leiste ist nur noch Anzeige.
  - **Während und nach dem Lauf** zeigt die Leiste je Station Text und Symbol: „erledigt“, „läuft“
    (an der Prüfung mit gewählter Prüferzahl etwa „läuft (2 Prüfer)“), „wartet“ mit dem Wartegrund,
    „abgebrochen“, „steht aus“, „nicht vorgesehen“ hinter dem Ziel und „vor dem Lauf erbracht“ bei
    einem Plan als Startkarte. Das gewählte Ziel bleibt markiert; erreicht die Kette es, steht dort
    „Ziel erreicht“. Bleibt sie an einer Grenze stehen, die das Projekt verbietet, trägt die erste
    nicht erreichte Station „Projektgrenze“ mit dem gemeldeten Grund. Laufen mehrere Runner, zeigt die
    Leiste den Lauf, der diese Karte bearbeitet.

  Ziel wählen, starten und zurücknehmen darf, wer die Karte bearbeiten darf (`TICKET_UPDATE`); wer
  das nicht darf, sieht die Leiste nur. Siehe [Rollen & Rechte](rollen-und-rechte.md#projekt-rollen-rechte-matrix).
- **Abnahme durchklicken:** Eine fachliche Anforderung — eine Karte, deren Titel mit `[Fachlich]`
  oder `[Requirement]` beginnt — trägt im Kopf neben „Bearbeiten“ den Knopf **„Abnahme
  durchklicken“**, sobald ihre Beschreibung einen Abschnitt `## Szenarien` (englisch `## Scenarios`)
  mit mindestens einem Szenario hat. Andere Karten, eine fachliche Anforderung ohne Szenarien und
  das Bearbeiten-Formular zeigen ihn nicht.
  - **Liste:** Der Knopf öffnet die Ansicht „Abnahme: *Titel*“ mit den Szenarien in der Reihenfolge
    der Beschreibung, nummeriert und je mit seinen Schritten („Angenommen … Wenn … Dann …“). Du
    klickst jedes Szenario selbst nach und vermerkst **okay** oder **nicht okay**; zu „nicht okay“
    kannst du eine kurze Notiz schreiben (eine Zeile, höchstens 500 Zeichen). Unter der Liste steht
    die **Zusammenfassung**: „Alle Szenarien okay“ oder wie viele okay sind, welche nicht okay sind
    und wie viele noch offen sind.
  - **Abschicken:** „Ergebnis an den Fachplan schreiben“ ist erst frei, wenn jedes Szenario einen
    Vermerk trägt. Das Ergebnis steht danach als Kommentar an der Karte: die Überschrift „Abnahme der
    Szenarien“, eine Zeile „Ergebnis: …“ und je Szenario seine Nummer, sein Name und „okay“ oder
    „nicht okay“, bei „nicht okay“ mit der Notiz. Jede Abnahme ist ein neuer Kommentar; nach einer
    Nachbesserung nimmst du einfach erneut ab. Schließt du die Ansicht mit Vermerken, fragt sie
    nach, ob sie verworfen werden sollen.
  - **Nur ein Vermerk:** Ein „nicht okay“ bewegt nichts am Board — keine Spalte, kein Label, keine
    neue Karte. Abschicken darf, wer kommentieren darf; lehnt der Server ab, zeigt die Ansicht den
    Fehler und behält die Vermerke.
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

Sobald mindestens eine Karte gewählt ist, erscheint unten eine Aktionsleiste mit fünf Massenaktionen:

- **Labels:** öffnet ein Menü mit allen Labels des Boards. Je Label steht dort, wie weit es in der
  Auswahl vertreten ist — ein **voller Haken** („alle gewählten Karten"), ein **Strich** („einige")
  oder ein **leeres Kästchen** („keine"). Ein Klick auf ein Label ohne vollen Haken **hängt es allen**
  gewählten Karten an, ein Klick auf einen vollen Haken **nimmt es allen ab**; die übrigen Labels
  jeder Karte bleiben dabei unberührt. Das Menü bleibt offen, sodass mehrere Labels in einem Zug
  gehen. Hat das Board keine Labels, ist die Taste gesperrt.
- **Vorhaben zuordnen:** öffnet ein Menü mit allen nicht archivierten Vorhaben des Boards; ein für
  dich ausgeblendetes Vorhaben ist als „(ausgeblendet)" gekennzeichnet und ebenso wählbar. Nach der
  Wahl folgt **stets eine Rückfrage** mit der Zahl der Karten, dem Vorhaben und der Zahl der Karten,
  deren unmittelbare Zuordnung zu einem anderen Vorhaben dabei ersetzt wird. Gesetzt wird nur die
  **unmittelbare Zuordnung**: Gehört eine Karte über ihre Herkunft zu einem anderen Vorhaben, hat
  diese geerbte Zugehörigkeit in der Anzeige weiter Vorrang; abgeleitete Karten kommen über ihre
  Herkunft mit. Ist das Ziel ausgeblendet, verschwinden die Karten vom Board und kommen über
  „Ausgeblendete zeigen" zurück. Höchstens **200 Karten** in einem Zug. Gesperrt ist die Taste, wenn
  die Auswahl ein Vorhaben enthält oder das Board kein Vorhaben hat. Nach Erfolg endet die Auswahl;
  jede geänderte Karte bekommt einen Eintrag in ihrem Verlauf.
- **Verschieben:** alle gewählten Karten in eine Spalte — wahlweise in eine **andere Spalte desselben
  Boards** (vorausgewählt; etwa fünf Pakete auf einmal nach Ready) oder auf ein **anderes Board**
  bzw. in ein anderes Projekt. Nur mit Verschieberecht. Innerhalb des Boards behalten die Karten
  Nummer, Vorhaben-Zuordnung und Abhängigkeiten; beim Board-Wechsel gehen Vorhaben-Zuordnung und
  Abhängigkeiten verloren. Die Karten landen in der Reihenfolge der Auswahl am Ende der Zielspalte;
  eine Karte, die schon dort liegt, bleibt an ihrem Platz.
- **Archivieren** und **In den Papierkorb:** jeweils nach einer Rückfrage.

Alle fünf laufen als **eine Transaktion**: Scheitert eine Karte, bleibt die ganze Auswahl unverändert.
Beim Zuordnen bleiben Karten, die es auf dem Board nicht mehr gibt (archiviert, im Papierkorb,
verschoben), außen vor; die Meldung nennt ihre Zahl.
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

## Bedienung auf dem Tablet {#tablet}

Der Leitstand lässt sich auf einem **Tablet im Querformat** per Finger bedienen — gedacht für
**Safari auf dem iPad (iPadOS)** und **Chrome auf Android-Tablets**, jeweils in aktueller Version, bei
etwa 1024 bis 1366 px Breite. Im **Hochformat** und auf dem **Handy** sieht und bedient sich der
Leitstand wie bisher. Ein Tablet mit angeschlossener **Maus oder Trackpad** zeigt die
Desktop-Darstellung, ebenso ein Laptop mit Touchscreen.

- **Board:** Ein Board mit bis zu fünf Spalten passt ganz auf den Bildschirm; ab sechs Spalten
  rollt die Fläche seitlich. Die Navigation links ist beim Öffnen eingeklappt und lässt sich über
  das Menü-Symbol aufklappen.
- **Karten verschieben:** Den Finger etwa eine halbe Sekunde auf eine Karte legen — sie hebt sich
  an und folgt dem Finger. So zieht man sie in eine andere Spalte oder an eine andere Stelle
  derselben Spalte; die Ablagestelle wird beim Ziehen angezeigt. Am Rand einer Spalte oder des
  Boards rollt der Inhalt mit.
- **Wischen und Antippen:** Kurzes Wischen über eine Karte rollt Spalte oder Board und verschiebt
  nichts. Kurzes Antippen öffnet die Karte.
- **Spalten umordnen:** Im [Editiermodus](#editiermodus) den Spaltenkopf lange antippen und die
  Spalte ziehen — wie eine Karte.
- **Listen-Ansicht:** Zeilen und Spaltenköpfe werden ebenfalls per langem Antippen gezogen; die
  Breite der Beschreibung lässt sich mit dem Finger am Griff ändern.
- **Erklärungen:** Was am Desktop beim Darüberfahren erscheint, öffnet auf dem Tablet ein
  **Antippen** — etwa an einem Label auf einer Karte, einer gesperrten Station oder einem gesperrten
  Knopf. Erneutes Antippen oder Antippen daneben schließt die Erklärung; offen ist immer nur eine.
  Das Antippen eines Labels öffnet seine Erklärung, nicht die Karte.
- **Info-Symbol:** Bei Elementen, die beim Antippen etwas tun — etwa einem Eintrag im Label-Filter
  oder in einer Auswahl —, löst Antippen die Aktion aus wie gewohnt. Ihre Erklärung öffnet das
  **Info-Symbol** daneben.
- **Dialoge** passen auf den Bildschirm, auch bei eingeblendeter Bildschirmtastatur: Ihr Inhalt
  rollt senkrecht, die Knöpfe bleiben erreichbar.

## Spezifikation einlesen {#spezifikations-import}

Eine Markdown-Spezifikation lässt sich in einem Zug in Karten auflösen: Jeder Abschnitt der
gewählten Überschriftenebene wird zu einer Karte — die Überschrift zum Titel, der Text darunter zur
Beschreibung.

- **Ort:** Button **„Spezifikation einlesen"** in der **Werkzeugleiste der Board-Ansicht**. Er
  öffnet die Dateiauswahl (`.md`/`.markdown`).
- **Recht:** Der Button erscheint nur, wenn du auf diesem Board **Karten anlegen** darfst
  (`TICKET_CREATE`).
- **Zielspalte:** Im Dialog wählbar über das Feld **„Spalte"**; vorbelegt ist die **erste Spalte**
  des Boards. Es wird stets in genau eine Spalte eingelesen.
- **Trennende Ebene:** Das Feld **„Ebene"** bestimmt, welche Überschrift die Karten trennt — **H1**
  oder **H2**. Voreingestellt ist **H2**, weil H1 in exportierten Spezifikationen meist den
  Dokumenttitel trägt. Findet sich keine Überschrift der gewählten Ebene, sagt das der Dialog und
  bietet die andere Ebene an.
- **Vorschau:** Vor dem Anlegen zeigt der Dialog **alle gefundenen Abschnitte** mit Titel und einer
  einzeiligen Textvorschau. Jeder Abschnitt lässt sich **einzeln abwählen** (etwa Vorspann oder
  Inhaltsverzeichnis); der Knopf nennt die Zahl der Karten, die tatsächlich entstehen. Ein
  Ebenenwechsel setzt die Abwahl zurück.
- **Kürzungen:** Zu lange Titel und Beschreibungen werden auf die Feldgrenzen gekürzt; die Vorschau
  **weist das am betroffenen Abschnitt aus**, bevor etwas angelegt wird.
- **Obergrenze:** Höchstens **200 Karten je Import**. Darüber warnt der Dialog und das Anlegen
  bleibt gesperrt, bis genug Abschnitte abgewählt sind.
- **Die Datei bleibt bei dir:** Sie wird **nur im Browser gelesen** — weder hochgeladen noch
  verändert oder gelöscht. An den Server gehen ausschließlich die in der Vorschau gezeigten Karten.

**Ideen sammeln:** Einen projektweiten Ideen-Ablageort gibt es nicht mehr. Wer Ideen sammeln will,
legt sich dafür eine **eigene Spalte auf dem Board** an (etwa „Ideen") und schiebt Karten von dort
weiter, sobald sie eingeplant sind.

## Leitstand eines Boards {#leitstand}

Der **Leitstand** (Sidebar-Eintrag **„Leitstand“** im Board-Kontext, Route
`/boards/:boardId/leitstand`) ist die **Hauptansicht eines Boards**. Er ersetzt die frühere
Kennzahlen-Seite „Dashboard“; die alte Adresse `/boards/:boardId/dashboard` leitet auf ihn weiter.

Von oben nach unten führt er:

1. **Jüngster Run** — der jüngste Run als schmales Band: Melder (als Wechselblinker, solange er läuft),
   Titel des Runs, die berührte Karte, der Zeitpunkt, rechts „Zeit“ in Minuten und „Kosten“ in
   Dollar. Ist der Run **nicht angelaufen** (siehe [Beendete Runs](#beendete-runs)), trägt das
   Band eine braune LED und als Titel „nicht angelaufen — ‹Grund›“.
2. **Kennzahlen** — vier Kacheln: **Durchsatz · Woche**, **Durchlaufzeit**,
   **Implementierungszeit** und eine Kachel zu den Runs. Die Implementierungszeit misst, wie
   lange eine erledigte Karte insgesamt in „In Progress“ lag; mehrere Aufenthalte zählen zusammen.
3. **Verbrauch** — Token und Kosten mit Zeitraum-Wahl; ausführlich unter
   [Verbrauch (Leitstand)](#verbrauch-leitstand).
4. **Herkunft** — eine Zeile zum jüngsten Run: ob er eingeliefert oder im Browser hochgeladen
   wurde, dazu der Name des Tokens, die Zahl der Vorgänge und die der ungedeuteten Zeilen.
5. **Rumpf** — sieben Platten: **„Letzter Run · ‹Art›“** mit seinen Vorgängen (die Kartennummer
   öffnet die Karte zum Lesen), **„Durchsatz“** mit den abgeschlossenen Karten je Woche und rechts
   daneben **„Implementierungszeit“** mit dem Mittelwert je Woche, **„Abbruchgründe“** mit den Fehlerklassen über die aufbewahrten Runs, **„Nacharbeit“**
   (siehe unten), **„Vorhaben“** mit
   den offenen und **„Auslieferung“** mit vier Kennzahlen zur Auslieferung an die Nutzer; sie steht
   in voller Breite ganz unten, unter „Alter in Arbeit“ (siehe
   [Platte Auslieferung](#platte-auslieferung)). Bei einem nicht angelaufenen Run trägt die Platte „Letzter Run“ ebenfalls die
   braune LED und „nicht angelaufen — ‹Grund›“. Aus einer gleichzeitigen Nacht tragen die Vorgänge
   die Marken „gleichzeitig“ und „Übernahme verschoben“, ohne Wartezeit (siehe
   [Runner](#nachtlauf)).
6. **Fluss und Alter in Arbeit** — darunter in voller Breite **„Fluss“** und darunter **„Alter in
   Arbeit“** (siehe unten).

**Die beiden Wochenplatten** „Durchsatz“ und „Implementierungszeit“ zeigen dieselben zwölf
Wochenfenster: sieben Tage, von jetzt aus rückwärts gezählt; eine Karte zählt in der Woche, in der
sie fertig wurde. Unter jedem Balken steht die Kalenderwoche, beim Überfahren der Wert — bei der
Implementierungszeit der Mittelwert mit der Zahl der Karten („30 Minuten · 2 Karten“, ab einer
Stunde in Stunden). Eine Woche ohne gemessene Karte hat keinen Balken, sondern nur eine gestrichelte
Grundlinie („keine gemessene Karte“), nie einen Balken von 0 Minuten; ohne jede Messung steht statt
der Grafik ein Satz. Auf schmalem Bildschirm stehen die beiden Platten untereinander. **Die Fenster
decken sich nicht mit der Woche des Umschalters** im [Verbrauch](#verbrauch-leitstand), die an der
Tagesgrenze 12:00 hängt — der letzte Balken kann darum vom Wert der Kachel „Implementierungszeit“
unter „Woche“ abweichen.

**Die Platte „Nacharbeit“** zeigt, ob die Arbeit des Runners Nacharbeit erzeugt und wo. Sie zählt
zwei Signale: Ein Arbeitspaket geht in einem Run aus In Progress zurück nach Ready oder Backlog
(**„Pakete zurückgezogen“**), oder der Run hängt an einen Fachplan oder Plan das Halt-Label
`kit:clarify` bzw. `run:waiting` — in deutscher Projektsprache `kit:klaeren` bzw. `lauf:wartet`
(**„Dokumente angehalten“**). Es zählt nur, was ein Run des Runners tut, unabhängig von der Uhrzeit,
also auch eine tagsüber über „Kette starten“ gestartete Kette; was du selbst an denselben Karten
bewegst oder an Labels setzt und abnimmst, ändert keine Zahl. Jede Zeile zeigt eine Quote wie
„2 von 14“: Die Bezugszahl sind die Karten, die die Runs **bearbeitet** haben — Pakete, die ein Run
nach In Progress gezogen hat, und Fachpläne oder Pläne, an denen er einen Schritt ausgeführt hat;
jede Karte zählt höchstens einmal. Der Kopf nennt den Zeitraum, „letzte 12 Wochen“ oder „seit
‹Datum›“, wenn die Daten später beginnen. Darunter steht der Verlauf über dieselben zwölf
Wochenfenster wie „Durchsatz“, je Woche ein Balken für Pakete und einer für Dokumente. Eine Woche hat
einen von drei Zuständen: einen Balken mit der Quote (auch 0 %), eine gestrichelte Grundlinie (Daten
vorhanden, aber keine bearbeitete Karte der Gruppe) oder eine leere Woche mit „keine Daten“ beim
Überfahren. Ein Klick auf eine Quote öffnet die Kartenliste mit der Anzahl je Karte („#1712 · 2×
zurückgezogen“); die Kartennummer öffnet die Karte zum Lesen. **Grenzen der Zählung:** Halt-Labels
werden erst seit dem Einspielen der Version erfasst, die die Platte mitbringt — davor zeigt die
Dokument-Zeile „keine Daten“. Den Laufstand-Kommentar einer Karte schreibt jeder Run neu, er bezeugt
darum nur den letzten Schreiber; frühere Runs zählen über ihre übrigen Spuren. Ein Rückzug zählt nur,
wenn der Status der Karte vor der Bewegung bekannt ist.

**Die Platte „Vorhaben“** zeigt jedes Vorhaben des Boards, bis es archiviert ist — von Hand oder
automatisch, sobald alle zugehörigen Karten archiviert sind; auch ein Vorhaben ohne Pakete und eines,
dessen Pakete alle umgesetzt sind, bleibt bis dahin stehen. Der Fortschritt zählt **nur
Arbeitspakete**, und ein Paket in In Review zählt schon als fertig; alle minus fertige ergibt die Zahl
der offenen Pakete, auch im Backlog und nach einem Zurückziehen durch den Runner.
Die Zahl weicht bewusst vom Fortschritt der Vorhaben-Seite ab: Dort zählen alle zugehörigen Karten
und nur Done als fertig. Darunter steht die **Prognose** „voraussichtlich fertig: 50 % bis ‹Datum› · 85 % bis
‹Datum›“ mit „‹n› offene Pakete“: Mit dieser Wahrscheinlichkeit sind bis zu diesem Tag alle offenen
Pakete umgesetzt, gerechnet aus dem Durchsatz der letzten 30 abgeschlossenen Tage, zu dem alle
Arbeitspakete des Boards zählen, auch solche ohne Vorhaben. Ein Tag reicht von 12 bis 12 Uhr in
deiner Zeitzone und trägt das Datum seines **Endes** — anders als der [Verbrauch](#verbrauch-leitstand),
der einen Zeitraum nach seinem Beginn benennt; der laufende Tag ist der erste vorhergesagte. Der
Hinweis beim Überfahren erklärt das und sagt „Gilt, wenn das Board nur an diesem Vorhaben
arbeitet.“; haben weitere Vorhaben offene Pakete, ergänzt er „‹X› weitere Vorhaben teilen sich den
Durchsatz.“ Sonderfälle, in dieser Rangfolge:

- **„noch keine Pakete geschnitten“** — das Vorhaben hat noch kein Arbeitspaket.
- **„alle Pakete umgesetzt“** — alle Pakete liegen in In Review oder Done; folgt aus einem Plan oder
  Fachplan noch etwas, steht „alle Pakete umgesetzt, weitere folgen aus ‹Dokument›“.
- **„Zu wenig Durchsatz für eine Prognose“** — in den letzten 30 Tagen wurde an weniger als fünf
  Tagen ein Paket fertig. Der Satz erscheint auch, wenn die offenen Pakete bei diesem Durchsatz nicht
  in absehbarer Zeit fertig werden (mehr als 1 000 offene Pakete oder mehr als 100 Jahre).
- **„frühestens“ statt „bis“** — ein Plan außerhalb von Done hat noch kein Paket oder ein Fachplan
  außerhalb von Done noch keinen Plan; der Hinweis nennt das Dokument, denn daraus kommen weitere
  Pakete.

**Die Platten „Fluss“ und „Alter in Arbeit“** zeigen, wo sich die Arbeit staut und welche Karte
schon auffällig lange in Arbeit liegt. **Gezählt wird nach dem Zustand eines Arbeitspakets, nicht
nach der Spalte, in der es liegt:** Ein Paket in „Anstehend“ mit Zustand Ready zählt unter Ready;
die Backlog-Spalten des Owners („Anstehend“, „In Arbeit“, „Parken“ und jede Zusatzspalte) bewegen
nichts, solange der Zustand gleich bleibt. Es zählen alle Arbeitspakete, auch von Hand angelegte
Karten ohne Präfix; **Dokumente zählen nicht** — Fachpläne, Pläne, Ideen und Vorhaben erscheinen in
keiner der beiden Platten.

- **„Fluss“** zeigt die letzten zwölf Wochen (84 Kalendertage in deiner Zeitzone), je Tag fünf
  gestapelte Bänder, von unten Done, In Review, In Progress, Ready und Backlog. Jeder Tag zeigt den
  Stand an seinem Ende, der heutige den Stand jetzt. Done beginnt am Anfang des Zeitraums bei null
  und zählt die seither fertig gewordenen Pakete; verlässt eine Karte Done wieder, sinkt das Band.
  Beim Überfahren (oder per Tab) steht unter dem Bild die Zahl je Zustand. Ein Tag, dessen Stand
  sich aus dem Kartenverlauf nicht sicher bestimmen lässt, bleibt leer und zeigt **„keine Daten“**;
  beginnen die sicheren Daten später als vor zwölf Wochen, nennt der Kopf den Beginn
  (**„seit ‹Datum›“**).
- **„Alter in Arbeit“** listet je Zustand Ready, In Progress und In Review die Pakete, die älteste
  oben, mit Nummer, Titel und Alter seit dem letzten Wechsel in diesen Zustand; frühere Aufenthalte
  zählen nicht mit. Auf schmalem Bildschirm stehen die drei Spalten untereinander; eine leere Spalte
  sagt „Keine Karte in ‹Zustand›“. Die Kartennummer öffnet die Karte zum Lesen. Den
  **Bernstein-Melder** trägt eine Karte, die länger im Zustand liegt als 85 % der in den letzten
  zwölf Wochen abgeschlossenen Aufenthalte **desselben Boards im selben Zustand** — jeder Aufenthalt
  zählt einzeln, Karten, die noch dort liegen, nicht. **Unter fünf solchen Vergleichswerten gibt es
  keine Schwelle**: Die Karten stehen dann mit Alter, aber ohne Melder. Ist der Eintritt einer Karte
  unbekannt (der Verlauf reicht nicht so weit zurück), steht **„mindestens ‹n› Tage“** ab dem
  frühesten bekannten Zeitpunkt; der Melder erscheint, wenn schon dieser Mindestwert über der
  Schwelle liegt. Ein Aufenthalt mit unbekanntem Beginn zählt nicht als Vergleichswert.

### Platte Auslieferung {#platte-auslieferung}

Die übrigen Platten enden bei „Done“. **„Auslieferung“** zeigt, was danach geschieht: wie oft das
Projekt an seine Nutzer ausliefert, wie lange eine Änderung bis dorthin braucht, wie oft eine
Auslieferung scheitert und wie schnell es danach wieder läuft. Die vier Kennzahlen (in der Fachwelt
als DORA-Kennzahlen bekannt) stehen nebeneinander, jede mit einem kurzen Hinweis, was sie zählt.

**Woher die Zahlen kommen:** allein aus Meldungen. Jeder Auslieferungslauf des Projekts meldet seinen
Versuch mit einem board-gebundenen Token (siehe [API-Tokens](rollen-und-rechte.md#api-tokens)) —
Ausgang (erfolgreich oder gescheitert), Zeitpunkt, ausgelieferter Commit und die seither
hinzugekommenen Commits mit ihrem Commit-Zeitpunkt. Das Board fragt keinen Code-Hoster ab. Eine
Meldung zählt für das **ganze Projekt**, zu dem das Board des Tokens gehört; die Platte zeigt darum
auf jedem Board des Projekts dieselben Zahlen. Dieselbe Meldung (gleiche Kennung) zählt nur einmal,
ein erneut gestarteter Lauf trägt eine neue Kennung und zählt als eigener Versuch. Ein Lauf, der
nichts meldet, zählt nicht.

**Das Fenster** sind die letzten 12 Wochen (84 Tage bis jetzt); maßgeblich ist der gemeldete
Zeitpunkt des Ausgangs, nicht der Eingang der Meldung. Liegt die erste Meldung später, steht im Kopf
„seit ‹Datum›“ statt „letzte 12 Wochen“.

- **Deployment-Frequenz** — die Zahl der **erfolgreichen** Auslieferungen im Fenster, etwa
  „7 Auslieferungen in 12 Wochen“ bzw. „… seit ‹Datum›“. Hier ist 0 eine echte Null.
- **Lead Time for Changes** — wie lange eine Änderung braucht: je ausgeliefertem Commit die Zeit vom
  Commit bis zur erfolgreichen Auslieferung, davon der **Median** (der mittlere Wert; bei gerader
  Anzahl das Mittel der beiden mittleren). Die Wartezeit eines Commits vor dem Hochladen zählt mit.
  Scheitert ein Versuch, zählen seine Commits bei der nächsten erfolgreichen Auslieferung; jeder
  Commit zählt genau einmal. Ohne ausgelieferten Commit im Fenster steht **„keine Grundlage“**.
- **Change Failure Rate** — der Anteil gescheiterter an allen gemeldeten Versuchen im Fenster, in
  ganzen Prozent. Gescheitert ist ein Versuch genau dann, wenn sein Lauf das Scheitern meldet, auch
  wenn die Anwendung nach dem Hochfahren nicht bereit wird. **Bewusste Lücke:** Eine grün
  ausgelieferte Version mit Fehler zählt nicht — es zählen nur gemeldete Fehlschläge. Ohne Versuch im
  Fenster steht **„keine Grundlage“**.
- **Time to Restore** — wie schnell es nach einem Scheitern wieder läuft. Ein **Ausfall** reicht vom
  ersten gescheiterten Versuch einer Folge von Fehlschlägen bis zur nächsten erfolgreichen
  Auslieferung; mehrere Fehlschläge hintereinander sind ein Ausfall. Gezeigt wird der Median der im
  Fenster **beendeten** Ausfälle, jeder mit voller Dauer. Ohne beendeten Ausfall steht **„kein
  Ausfall“**. Ein noch laufender Ausfall zählt nicht in den Median, sondern steht hervorgehoben
  daneben: **„Ausfall läuft seit ‹Dauer›“**.

Unter den Zahlen zeigt eine **Wochenleiste** je Woche des Fensters die erfolgreichen und, abgesetzt,
die gescheiterten Versuche; eine Woche ohne Versuch hat nur eine Grundlinie. Lead Time und Time to
Restore haben keinen Verlauf. Liegt für das Projekt noch **keine einzige Meldung** vor, zeigt die
ganze Platte nur **„noch keine Auslieferung“** statt Nullen. Gezählt wird ab der ersten Meldung,
frühere Releases werden nicht nachgetragen.

**Was das Recht entscheidet:** das Band des jüngsten Runs, die Run-Kachel, Verbrauch, „Letzter Run“,
„Abbruchgründe“ und „Nacharbeit“ sieht nur, wer auch die [Runs](#nachtlauf) sehen darf — der **Owner** des
Projekts und **Plattform-Admins**, sofern das Projekt am
[Plattform-Leitstand](#plattform-leitstand) teilnimmt. Ohne dieses Recht entfallen sie still; die
Board-Kennzahlen, „Durchsatz“, „Implementierungszeit“, „Fluss“, „Alter in Arbeit“ und „Auslieferung“
bleiben — die drei letzten sieht wie „Durchsatz“ jedes Projektmitglied, das das Board sehen darf.

**Grundlage der Board-Kennzahlen** ist die automatisch erfasste Verweildauer jeder Karte pro
Spalte — gemessen bei **jedem** Spaltenwechsel, egal ob per Drag & Drop, ⋮-Menü oder über die API
(kanbancompat).

**Nicht mehr dargestellt:** die **Ø Verweildauer je Spalte** und die Liste der **Ausreißer**
(Karten, die über sieben Tage in einer Spalte lagen). Beide Kennzahlen werden für die Steuerung
der KI-Arbeit nicht gebraucht und sind bewusst aus dem Leitstand genommen worden; das API-Feld
`outliers` bleibt im Backend bestehen.

## Runner {#nachtlauf}

Der **Bereich „Runner"** wertet die Protokolle des Nacht-Runners aus: Er zeigt je Run, welche
Arbeitspakete durchliefen, welche stehenblieben und woran es lag. Er ist **projektweit**, nicht an
ein Board gebunden.

Erreichbar über den Sidebar-Eintrag **„Runner"** (Route `/projects/:projectId/nachtlauf`).
Sichtbar ist er nur für den **Owner** des Projekts und für **Plattform-Admins**, sofern das Projekt
am [Plattform-Leitstand](#plattform-leitstand) teilnimmt — siehe
[Rollen & Rechte](rollen-und-rechte.md#projekt-rollen-rechte-matrix).

**Normalweg: Der Runner liefert selbst ein.** Ein Run kommt ohne jeden Handgriff ans Board — der
Nacht-Runner meldet ihn über ein projektgebundenes Zugriffstoken beim Start und schreibt ihn danach
fort, bis er abgeschlossen ist. Welche Angaben dabei mitkommen, hängt an der Fassung des Runners:
Budgets, ihre Herkunft, die Stufen einer Kette, Modellzeit und Züge erscheinen erst, wenn der
Runner sie mitschickt; bis dahin steht dort „nicht angegeben" bzw. „nicht gemeldet".

**Rückfall: die Ergebnisdatei einlesen.** Kam ein Run nicht ans Board — etwa ohne Zugriffstoken
oder weil die Einlieferung scheiterte —, liest der Button **„Protokoll einlesen"** oben rechts die
Ergebnisdatei des Runners (`night-run-<datum>-<uhrzeit>.json`). Die **Datei wird im Browser
ausgewertet und nicht hochgeladen** — an den Server geht allein die verdichtete Auswertung
(Kennzahlen, Zustände, Kartennummern, Fehlerklassen und kurze Auszüge). Die Datei trägt Pfade und
die Kennzahlen der Sessions; die bleiben, wo sie sind. Dieselbe Datei lässt sich erneut wählen,
ohne die Seite neu zu laden. **Budgets und Stufen** eines so eingelesenen Runs zeigt die Seite nur
bis zum nächsten Neuladen: Der Rückfallweg liefert sie nicht an den Server.

**Der Stand am Board gewinnt.** Wird die Ergebnisdatei eines Runs eingelesen, den der Runner schon
eingeliefert hat, ändert sich an seiner Anzeige nichts: Es entsteht kein zweiter Run, kein Wert
ändert sich, und es kommt nichts hinzu, was die Einlieferung nicht kannte. Das gilt auch für Runs,
die vor dieser Regel eingeliefert wurden.

Einige Angaben stehen deshalb **nur bei einem eingelesenen, noch nicht eingelieferten Run**, weil
allein die Ergebnisdatei sie trägt. Bei einem Run, den der Runner eingeliefert hat, entfallen:

- bei einem Kettenlauf die Angaben „Ketten durchgelaufen", „Karten entstanden", „Laufzeit über alle
  Stufen" und „Kosten der Schicht",
- der Grund, an dem eine Stufe der Kette abbrach,
- die Dokumente je Stufe.

Bei einem eingelieferten **Kettenlauf** teilt die [Lage des Runs](#lage-des-runs) die Kosten in
**Planung** (die Stufen der Kette) und **Umsetzung** (der Rest), und der Kettenvorgang nennt den
**angelegten Plan und die Pakete** als Verweise — ermittelt aus der Herkunft der Karten am Board,
beschränkt auf Karten, die während des Runs entstanden. Jede Paketzeile nennt ihren Plan („Paket aus
Plan #N"). Die Dauer des Kettenvorgangs ist die Summe seiner Stufen.

Der **Titel** eines Runs nennt seine Nummer, das Startdatum und die Startzeit („Run #412 · 14.
September, 22:05"); ein eben eingelesener Run ohne Nummer heißt „Run · 14. September, 22:05".
Darüber steht die **Schicht**, zu der er gehört („Schicht vom 14.09.2026 auf den 15.09.2026").

Unmittelbar **vor dem Titel** steht die **Art des Runs als Symbol**. Es sitzt an fester Stelle und
hat bei jeder Art dieselbe Breite, damit der Titel in jeder Zeile gleich weit links beginnt. Fährst
du mit der Maus darauf, nennt ein Hinweisfähnchen das Wort; Vorlesewerkzeuge lesen es ebenso vor.
Die Symbole:

- **zwei verschränkte Kettenglieder** — Kette
- **Schraubenschlüssel** — Umsetzung
- **Lupe** — Prüfung
- **Sprechblase** — Sitzung
- **Mondsichel** — Nachtplan

Jeder Run steht als aufklappbare Zeile da — Startzeitpunkt, Art des Runs als Symbol, Dauer sowie
„N bearbeitet, M übergangen". Ein Nachtplan
wird angezeigt, aber nicht aufbewahrt — er verschwindet nach einem Neuladen der Seite wieder.
Aufgeklappt zeigt er seine Arbeitspakete, jedes mit einem der **vier Zustände**:

- **grün — „Erfolg":** das Arbeitspaket ist durchgelaufen.
- **gelb — „Erfolg, Prüfung rot":** umgesetzt, aber eine Prüfung schlug fehl.
- **rot — „gescheitert":** der Run kam an diesem Arbeitspaket nicht durch.
- **grau — „nicht bearbeitet":** übergangen, etwa weil eine Abhängigkeit noch offen war.

**Ausgang „festgefahren":** Ein rotes Arbeitspaket, das das Kit früh beendet hat, weil es sich an
derselben Prüfung immer wieder auf dieselbe Weise festfuhr, trägt den eigenen Ausgang
**„festgefahren"** — unterscheidbar von „Prüfungen rot", „Zeitbudget erschöpft", „Harter Abbruch"
und „Wartet auf Entscheidung". Dazu stehen sechs Angaben: **Prüfung**, **Fehler**, **Versuche**,
**Laufzeit bis Abbruch**, **Zeitgrenze der Sitzung** und **geschätzte gesparte Zeit** (Zeitgrenze
minus Laufzeit, nie unter 0). Was das Kit nicht gemeldet hat, steht als „nicht gemeldet" da; ohne
Zeitgrenze oder Laufzeit gilt das auch für die gesparte Zeit. Ältere Nächte, die ein festgefahrenes
Paket noch als „Prüfungen rot" gemeldet haben, bleiben so, wie sie waren.

**Gleichzeitige Nacht:** Setzt das Kit mehrere Pakete gleichzeitig um, steht beim aufgeklappten
Paket eine Liste mit drei Angaben: **„Gleichzeitig“** (ja/nein — lief neben mindestens einem
anderen Paket), **„Wartezeit auf die Prüfung“** (wie lange es auf einen freien Platz für seinen
Prüflauf wartete, in Minuten und Sekunden, etwa „7 min 0 s“) und **„Übernahme verschoben“**
(ja/nein — die Übernahme in den gemeinsamen Stand wurde verschoben). Was das Kit in einer solchen
Nacht nicht gemeldet hat, steht als „nicht gemeldet“ da; ein übergangenes Paket zeigt bei beiden
Wahrheitswerten „nein“. Zugeklappt trägt die Zeile nur die Marken **„gleichzeitig“** und
**„Übernahme verschoben“**, und zwar nur beim Wert „ja“. Dieselben Marken stehen in der
Leitstand-Platte **„Letzter Run“** und dort auch in der Beschriftung, die ein Vorlesewerkzeug
nennt; die Wartezeit steht nur in der Lauf-Ansicht. Eine Nacht, die ihre Pakete nacheinander
umsetzt, zeigt keine dieser Angaben.

Zu jedem Arbeitspaket stehen darunter der Auszug aus dem Protokoll (bei grau der Grund) und die
Herkunftskette — die fachliche Anforderung und der Plan, aus denen es entstanden ist. Die
Kartennummer ist ein Link und öffnet die Karte zum Lesen.

**Häufigkeit einer Fehlerklasse:** An einem gelben oder roten Befund eines aufbewahrten Runs steht,
in wie vielen der aufbewahrten Runs dieselbe Fehlerklasse vorkam — „Prüfungen rot: 4 von 30
aufbewahrten Runs" bzw. „zum ersten Mal" beim ersten Vorkommen. Gezählt wird **einschließlich** des
angezeigten Runs. So ist ein einmaliger Ausrutscher von einem wiederkehrenden Muster zu
unterscheiden.

**Übernahmetext:** Unter jedem gelben und roten Arbeitspaket steht ein fertiger Text zum Übernehmen
in die eigene Entwicklungssitzung (Karte, Zustand, Fehlerklasse, Auszug). Er steht **vollständig
sichtbar** in einem Textfeld, bevor der Button **„Kopieren"** ihn in die **Zwischenablage** legt —
der Text enthält Fremdtext aus dem Protokoll, und was in die eigene Sitzung wandert, soll man vorher
gesehen haben. Legt der Browser die Zwischenablage nicht frei, bleibt es beim sichtbaren Feld: von
Hand markieren und kopieren.

**Nur die letzten zwei Schichten:** Die Liste zeigt standardmäßig die Runs der laufenden und der
vorigen Schicht; darunter blendet **„Ältere Runs anzeigen (N)"** die übrigen für diesen Besuch ein.
Sichtbar bleiben außerdem ein älterer Run, der noch läuft, der über einen Verweis angesteuerte Run
und ein eben eingelesener. Begrenzt wird nur die Anzeige: Aufbewahrung, der
[Verbrauch im Leitstand](#verbrauch-leitstand) und Häufigkeiten zählen weiter alle aufbewahrten Runs.

**Aufbewahrung:** Je Projekt bleiben die **letzten 190 Runs** erhalten — genug, um bei zwei Runs
je Schicht den laufenden, den zuletzt abgeschlossenen und den Vormonat vorzuhalten; ältere fallen
heraus, sobald neue hinzukommen. Die **Arbeitspakete** eines verdrängten Runs bleiben dabei bestehen: Sie tragen
Projekt, Startzeitpunkt und Art des Runs selbst, damit die Messwerte einer Karte nicht mit dem Run
verschwinden. In der Liste der Runs erscheinen sie nicht mehr. Diese **verwaisten Arbeitspakete**
haben eine eigene Grenze: Je Projekt bleiben die **letzten 2000** erhalten, gemessen am
Startzeitpunkt ihres Runs. Runs und verwaiste Arbeitspakete werden also getrennt begrenzt — die
Pakete eines noch aufbewahrten Runs zählen nicht mit und fallen erst mit ihm.

Drei Sonderfälle, die keine Fehler sind: Ein **Probelauf** (`DRY-RUN`) wird nicht aufbewahrt — ein
Protokoll aus lauter Probeläufen erzeugt darum nur die Meldung, dass es nichts auszuwerten gibt. Ein
Run, der bereits ausgewertet wurde, wird als **„lag schon vor"** gemeldet und bleibt unangetastet;
die übrigen Runs derselben Datei entstehen trotzdem. Und ein **nachgereichter Run**, der älter ist
als alle 190 aufbewahrten, verdrängt keinen neueren — er wird angelegt und sogleich wieder verdrängt,
erscheint also nach dem Einlesen nicht in der Liste.

### Runner jetzt

Ganz oben auf der Runner-Seite sagt der Bereich **„Runner jetzt“**, was der Runner gerade tut. Er
nennt jeden Lauf, der noch nicht beendet ist, mit seiner Art (Kette oder Umsetzungsnacht) und genau
einem Zustand:

- **läuft:** Der Lauf hat sich innerhalb der Stillefrist gemeldet.
- **verstummt:** Der Lauf gilt als laufend, hat sich aber länger als die Stillefrist nicht gemeldet.
  Dann steht dort: „Runner verstummt seit *Zeit* (letzte Stufe: *Stufe*), vermutlich abgebrochen;
  die Umsetzung gilt womöglich noch als belegt.“ Hat der Lauf keine Stufe gemeldet, steht als
  Stufe „nicht gemeldet“.

Jede Zeile nennt **Beginn, Token und letzte Meldung** des Laufs, auch beim verstummten. Unter
Beginn und Token zeigt auch der Plattform-Leitstand den Lauf, so findet man ihn dort wieder.

Die **Stillefrist** ist dieselbe, nach der die Auswertung einen Lauf als verstummt führt
(`manban.nightrun.stille-frist`, Vorgabe 90 Minuten). Meldet sich ein verstummter Lauf doch noch,
läuft er wieder.

Ein verstummter Lauf bleibt sichtbar, wie alt er auch ist, **bis seine Störung im
Plattform-Leitstand quittiert ist**: Er steht dort unter „Störungen“ und wird über **„Störung
löschen“** in seiner Zeile quittiert. Danach erscheint er hier nicht mehr und ist auch am wartenden
Paket kein Grund „Runner verstummt“ mehr. Meldet sich ein quittierter Lauf später doch wieder,
erscheint er wieder als „läuft“. Den letzten Lauf darunter berührt die Quittung nicht.

Zu einem Lauf stehen seine **laufenden Stufen**: Karte und Stufe (etwa „#1665 Vorbereitung“),
Beginn, Frist und der Teilschritt in Worten, etwa „wartet auf freie Umsetzung“ oder „Prüfungen der
Veröffentlichung laufen“. Als Frist erscheint nur, was das Kit für die Stufe tatsächlich anwendet,
sonst „keine Frist vorgesehen“. Hält eine Stufe die Umsetzung, steht „hält die Umsetzung“ dabei.
Eine Stufe oder einen Teilschritt, den die Seite noch nicht kennt, zeigt sie mit dem Namen des Kits.

Läuft kein Lauf und ist keiner verstummt, steht **„kein Runner aktiv“**. Wurde im Projekt noch nie
ein Lauf gestartet, steht **„kein Runner aktiv – noch kein Lauf gestartet“**. Darunter nennt der
Bereich den **letzten Lauf** mit Art, Ende und Ergebnis, auch neben laufenden Läufen. Endete eine
Umsetzungsnacht ohne Paket, steht ihr Grund dabei, etwa „ohne Paket beendet – Umsetzung belegt
durch Kette #1665, Stufe VORBEREITUNG“. Solange ein Lauf läuft oder verstummt ist, frischt sich der
Bereich alle 30 Sekunden auf, wenn der Tab sichtbar ist.

### Warum ein Paket wartet

Im [Karten-Detail](#karten-detail) eines Pakets in Ready steht im Block „Verlauf“ der Abschnitt
**„Wartegrund“**. Er sagt, warum der Runner das Paket nicht zieht. Die Gründe kommen aus zwei Quellen:

- **Bewertung des Runners:** Der Runner meldet zu Beginn und nach jeder Runde einer Umsetzungsnacht
  sowie bei jedem Probelauf, wie er die Karten in Ready bewertet. Daraus stammen die Gründe der
  Karte: kein Arbeitspaket, sondern ein Dokument oder ein Schritt für einen Menschen; nicht für die
  Nacht freigegeben (`kit:nightrun` fehlt); wartet auf eine andere Karte; wartet auf Klärung;
  berührt eine geschützte Datei; die nötige Prüfung (Review) fehlt. Das Board bildet die Regeln des
  Runners nicht nach. Einen Grund, den die Seite noch nicht kennt, zeigt sie mit dem Wortlaut des
  Runners.
- **Laufzustand:** Aus den Läufen leitet das Board ab, ob **keine Umsetzungsnacht aktiv** ist, ob der
  **Runner verstummt** ist (derselbe Satz wie unter „Runner jetzt“) und ob die **Umsetzung belegt**
  ist, also ein anderer Lauf sie für eine andere Karte hält, bei einer Kette mit Stufe. Ob ein Paket
  wartet, weil keine Umsetzung läuft, bestimmen allein die Umsetzungsnächte. Eine Kette zählt nur,
  wenn sie die Umsetzung belegt.

Treffen mehrere Gründe zu, erscheinen alle. **„Pakete davor: *n*“** nennt, wie viele startbare
Pakete in der Reihenfolge davor stehen. Trifft kein Grund zu, steht **„startbereit – kein
Hinderungsgrund festgestellt“**: Die Karte ist bewertet, trägt keinen Grund, aus den Läufen spricht
nichts dagegen, und sie wurde seit der Bewertung nicht geändert. Das ist keine Zusage, wann sie
startet.

Zwei Vermerke sagen, wie alt das Wissen des Boards ist. Sie sind keine Wartegründe:

- **„noch nicht vom Runner bewertet (letzte Bewertung *Zeit*)“:** Die Karte steht nicht in der
  jüngsten Bewertung, etwa weil sie erst danach nach Ready kam. Gibt es noch gar keine Bewertung,
  steht „noch keine Bewertung“.
- **„seit der Bewertung geändert“:** Die Karte wurde nach der jüngsten Bewertung geändert. Dann heißt
  sie nicht startbereit, bis der Runner sie neu bewertet hat.

**In Ready** heißt hier, was auch der Runner meint: zuerst der eigene Status der Karte, ohne eigenen
Status ihre Spalte. Eine Karte mit Status „Ready“ in einer anderen Spalte trägt also einen
Wartegrund, eine Karte mit Status „Backlog“ in der Spalte Ready nicht. Steht die Karte nicht in
Ready, fehlt der Abschnitt.

Wartegrund und „Runner jetzt“ sieht, wer die Auswertung der Läufe sehen darf: der Projekt-Owner und
der Plattform-Admin, wenn das Projekt am Plattform-Leitstand teilnimmt. Andere Mitglieder sehen
keines von beiden.

### Heute Nacht

Die Übersicht **„Heute Nacht“** zeigt alle Karten des Projekts, deren Kette an der
[Stufenleiste](#karten-detail) gestartet und noch von keinem Runner übernommen wurde — über alle
Boards hinweg. Jede Zeile nennt Kartennummer (ein Link, der die Karte öffnet), Titel, Board, das
gewählte Ziel („Ziel: Umsetzung“), die Prüferzahl, falls gewählt, und eine kleine Stufenleiste mit
dem Weg der Kette. Trägt eine Karte kein Ziel-Label — etwa weil `kit:night` von Hand gesetzt wurde —,
steht dort „Veröffentlichung vorbereitet“: Ohne Ziel-Label fährt das Kit die volle Kette. Mit
`kit:durchziehen` ist es „Umsetzung“. Übernimmt ein Runner die Karte, verlässt sie die Übersicht. Ist keine Karte
freigegeben, steht dort „Für die nächste Nacht ist keine Karte freigegeben.“

Die Übersicht sieht, wer die Runner-Seite sehen darf. Ein Projektmitglied, das eine Kette startet,
ohne die Runner-Seite zu sehen, verfolgt Ziel und Fortschritt an der Stufenleiste der Karte.

### Morgenkachel „Veröffentlichung vorbereitet“

Hat ein Lauf eine Veröffentlichung vorbereitet (Ziel `ziel:push-vorbereitet`), steht in seiner
aufgeklappten Zeile die Kachel **„Veröffentlichung vorbereitet“**. Sie nennt die Version des Stands,
den Eingang der Meldung als „gemeldet um *Tag, Uhrzeit*“ — einen eigenen Zeitpunkt der Vorbereitung
meldet das Kit nicht — und einen von vier Ausgängen, jeweils mit Symbol und Text neben der Lampe:

- **grün:** Der vorbereitete Stand ist geprüft. Darunter steht „Der Stand kann von Hand
  veröffentlicht werden.“
- **grün, Prüfung offen:** Der Stand ist grün, einzelne Prüfungen stehen aber noch aus; sie stehen
  unter „Offene Prüfungen“.
- **rot:** Die fehlgeschlagene Prüfung steht unter „Fehlgeschlagene Prüfung“, die Karten, die sie
  verursacht haben, unter „Betroffene Karten“.
- **nicht vorbereitet:** In diesem Lauf wurde keine Veröffentlichung vorbereitet.

Mit „Stand:“ nennt die Kachel die Kennung des vorbereiteten Stands (den Commit), mit der du ihn
außerhalb des Boards wiederfindest. Unter „Enthaltene Arbeitspakete“ stehen alle Pakete des Stands
mit Nummer und Titel, auch solche aus anderen Ketten. **Veröffentlicht wird von Hand**, außerhalb
des Boards: Die Kachel bietet keinen Knopf dafür.

### Lage des Runs {#lage-des-runs}

Aufgeklappt beginnt jeder Run mit der **Lage des Runs**. Sie ersetzt die früheren Instrumente und
Chips und sagt in einem Blick, wo der Run steht:

- **Zustandssatz** — ein Satz statt einer Reihe von Marken: am laufenden Run etwa „Setzt Pakete #12,
  #14 um — 3 von 7 fertig“, „Prüfung zu Plan #40“ oder „Kette #38 am Ende des Wegs“, bei mehreren
  Ketten ergänzt um „+ 2 weitere Ketten“; am abgeschlossenen Run der Ausgang samt Zahlen, etwa
  „Erfolg — 5 grün · 1 gelb · 0 rot“.
- **„läuft seit“** — nur am laufenden Run; die Angabe zählt alle zehn Sekunden mit, ohne dass du die
  Seite neu lädst.
- **Kosten** als eine große Zahl in Dollar.
- **Pakete** — die Zahl der grünen, gelben und roten Pakete. Solange der Run läuft, greift sie dem
  Board vor, wie unter [Fortschritt eines laufenden Runs](#fortschritt-eines-laufenden-runs)
  beschrieben.
- **Verbrauchszeile** — leise darunter: Eingabe, Ausgabe, Cache-Quote und bei einem Kettenlauf die
  Aufteilung „Planung … · Umsetzung …“. Was nicht zu berechnen ist, steht als „nicht berechenbar“ da.

Am Fuß der Platte nennt die Zeile **„Einlieferung“**, woher der Stand kommt (vom Runner eingeliefert
oder eingelesen, samt erzeugender Fassung); sie ersetzt die frühere Angabe „Herkunft des Stands“ im
Kopf. Daneben stehen Zeitvorgaben, Kostenbudget und deren Herkunft sowie die Kennzahlen der Schicht.

### Kettenzeilen {#kettenzeilen}

Jede Kette eines laufenden Runs steht als eigene Zeile: Nummer, Plan und Titel der Anforderung, dann
von links nach rechts:

- **Stufenleiste** mit den Stufen **Plan · Prüfung · Arbeitspakete · Abdeckung · Umsetzung**. Der
  Zustand jeder Stufe ist an der **Form** erkennbar, nicht allein an der Farbe: ein **gefüllter Punkt**
  für erreicht, ein **hervorgehobener Punkt** für die laufende Stufe, ein **gestrichelter Ring** für
  offen. Fährst du mit der Maus darauf, nennt ein Hinweis Stufe und Zustand („Plan: erreicht“);
  Vorlesewerkzeuge lesen dasselbe vor.
- **Zeitbalken** an der laufenden Stufe: die verstrichene Zeit gegen die **Zeitvorgabe** der Stufe,
  etwa „12 / 30 min“, bei Überschreitung ergänzt um „· 5 min über der Vorgabe“. Ohne Vorgabe steht nur
  „seit *Dauer*“.
- **Ende des Wegs** — die Stufe Umsetzung erscheint nur, wenn die Karte, die die Kette trägt, das
  Label `kit:durchziehen` trägt. Fehlt es, endet die Kette planmäßig nach der Abdeckung; eine Flagge
  „Ende des Wegs“ zeigt das, statt einer ausstehenden Umsetzung.
- **Paketraster** — je Arbeitspaket ein Quadrat (siehe unten).
- **Kosten der Kette** rechts. Bei einer Kette **ohne Umsetzung** steht ein kupferner Balken gegen das
  Kostenbudget („… / 4 $“); bei einer Kette mit Umsetzung steht der Betrag als „Planung“ **ohne
  Balken**, weil das Budget nur die Planung deckt.

**Paketraster.** Jedes Quadrat ist ein Arbeitspaket der Kette und öffnet per Klick die Karte. Sein
Zustand ist wieder an der Form zu erkennen; der Hinweis nennt Nummer, Zustand und Titel. Die Legende
kennt fünf Zustände:

- **angelegt** — das Paket ist entstanden, aber noch nicht gezogen,
- **gezogen** — es steht zur Umsetzung bereit,
- **in Umsetzung** — es wird gerade umgesetzt,
- **fertig** — umgesetzt und in „In review“,
- **zurückgestellt** — mit Fehlschlag zurück ins Backlog gestellt.

Unter dem Raster zählt eine Zeile jeden vorkommenden Zustand, etwa „3 fertig · 1 in Umsetzung“.

### Jetzt im Werk {#jetzt-im-werk}

Solange mindestens ein Run läuft, steht zwischen „Runner jetzt“ und der Liste der Runs die Platte
**„Jetzt im Werk“** — mit der Zahl der Runs im Titel („Jetzt im Werk · 2 Runs gleichzeitig“). Sie
zeigt jeden Run als Balken auf einer gemeinsamen **Zeitachse**:

- ein **laufender** Run schraffiert in Stahl,
- ein im Zeitfenster **beendeter** Run in der Farbe seines Ausgangs,
- ein kupferner Strich markiert **„jetzt“**.

Jeder Balken trägt die Nummer des Runs und seine jüngste Stufe („#691 · Umsetzung“). Für
Vorlesewerkzeuge steht darunter eine Liste („Run #691, seit 10:32, läuft, Umsetzung“), deren Einträge
zur Platte des Runs springen. Läuft kein Run, entfällt die Platte.

### Fortschritt eines laufenden Runs

Klappst du die Kachel eines **noch laufenden** Runs auf, steht über den Vorgangszeilen ein eigener
Block mit dem Stand, den das Board zur selben Zeit zeigt:

- **Bei einer Kette** je Kette eine [Kettenzeile](#kettenzeilen) mit Stufenleiste und Paketraster.
- **Bei einer Umsetzungsnacht** nur die Umsetzung: welches Paket gerade umgesetzt wird und welche
  schon fertig sind.

Jede Kartennummer ist ein Link und öffnet die Karte.

**Welche Karten zum Run gehören.** Erstes Kriterium ist die Laufkennung: Ein Runner weist sich bei
jeder Änderung am Board mit seinem Startzeitpunkt aus (Header `X-Night-Run`). Zum Run zählen dann
genau die Karten, die in seinem Zeitfenster mindestens eine Änderung mit dieser Kennung über das
Zugriffstoken des Runs erfahren haben — auch wenn gleichzeitig ein zweiter Runner mit demselben Token
arbeitet. Nur für Läufe ohne Ausweis, also ältere Runner, gilt weiter die Zeitfenster-Regel: Eine
Karte zählt, wenn sie im Zeitfenster eine Änderung über das Zugriffstoken im Nachtbetrieb erfahren
hat. Das Zeitfenster reicht vom Start bis zum gemeldeten Ende, bei einem verstummten Run bis zu
seiner letzten Meldung, sonst bis jetzt. Was ein Mensch oder eine interaktive Sitzung mit demselben
Token bearbeitet hat, zählt darum nicht mit, ebenso wenig Karten, die vor dem Run schon fertig waren.
Jede Karte steht beim Run mit dem Zustand, den sie durch **seine eigene letzte Änderung** erreicht
hat — spätere Änderungen anderer Läufe oder von Menschen verändern nicht, was bei ihm steht. Lässt
sich die Zuordnung nicht feststellen, steht der Block auf **„unbekannt"**, statt still mitzuzählen
oder wegzulassen — in drei Fällen:

- Der Run kam per „Protokoll einlesen" ohne Zugriffstoken: Der ganze Fortschritt ist unbekannt.
- Ein Nachtlauf ohne Ausweis überschneidet sich zeitlich mit einem zweiten Nachtlauf desselben
  Projekts mit demselben Token: Karten aus der Überschneidung, die ohne Ausweis geändert wurden,
  stehen mit Nummer unter „unbekannt" und zählen nicht. Dort steht dann der Hinweis „Dieser Lauf hat
  sich nicht ausgewiesen (älterer Runner)." Ein ausgewiesener Lauf zeigt seine Karten auch in der
  Überschneidung eindeutig.
- Eine Kette lässt sich keiner fachlichen Anforderung zuordnen.

**Wie die Kopfzahlen vorgreifen.** Solange der Run läuft, nehmen „bearbeitet / übergangen" und die
Paketzahlen „grün / gelb / rot" den Stand vom Board vorweg — für jedes Paket, das der Run noch
nicht selbst gemeldet hat:

- fertig umgesetzt (in „In review") → bearbeitet und grün,
- vom Run mit Fehlschlag zurück ins Backlog gestellt → bearbeitet und rot,
- alles andere (gerade in Umsetzung, gezogen oder eben angelegt) → in keiner Zahl.

Gelb und „übergangen" kommen allein aus der Meldung des Runs. Hat der Run seinen Abschluss gemeldet,
gelten nur noch die gemeldeten Zahlen.

**Hinweis auf eine offene Frage.** Trägt eine Karte des Runs das Label `kit:klaeren` oder die
tragende Karte das Label `lauf:wartet`, wartet der Run auf eine Antwort eines Menschen; der Block
nennt das samt der Karte, an der die Frage steht. Dieser Hinweis bleibt **auch nach der
Abschlussmeldung** stehen, solange das Label sitzt — maßgeblich ist der heutige Labelstand.

**Auffrischen.** Die Seite lädt die Liste der Runs und den Fortschritt jeder aufgeklappten Kachel
alle **30 Sekunden** neu, ohne dass du die Seite neu laden musst — aber nur, solange mindestens ein
Run noch läuft und der Tab sichtbar ist. Kehrst du in den Tab zurück, frischt sie sofort auf. Ein
eben eingelesener Run oder ein Nachtplan bleibt dabei stehen.

## Verbrauch (Leitstand)

Der **Leitstand** (Sidebar-Eintrag **„Leitstand"** im Board-Kontext, Route
`/boards/:boardId/leitstand`) führt einen Bereich **„Verbrauch"**: was die Arbeit an diesem Projekt
an Claude-Code-Verbrauch gekostet hat. Sichtbar ist er — wie die [Auswertung der Runs](#nachtlauf)
— nur für den **Owner** des Projekts und für **Plattform-Admins**, sofern das Projekt am
[Plattform-Leitstand](#plattform-leitstand) teilnimmt.

### Zwei Gattungen

Gezählt werden zwei **Gattungen** von Einträgen:

- **Run** — ein Run des Nacht-Runners.
- **Interaktive Sitzung** — eine Arbeitssitzung am Rechner eines Menschen.

**„Gattung" ist nicht „Herkunft".** Die Gattung sagt, *was* ein Eintrag ist — Run oder
interaktive Sitzung. Die **Herkunft** ist eine zweite, davon unabhängige Angabe und sagt, *auf
welchem Weg* er ans Board kam: von Hand im Browser eingelesen oder maschinell mit einem
projektgebundenen Zugriffstoken gemeldet. Sie steht im Leitstand auf der eigenen Platte
„Herkunft". Eine interaktive Sitzung ist Gattung *interaktive Sitzung* und Herkunft *Token* —
beide Angaben stehen nebeneinander, keine ersetzt die andere.

### Was der Bereich zeigt

Über den Kacheln steht der gewählte Zeitraum — **Schicht · Woche · Monat · Gesamt** — und daneben,
aus wie vielen Einträgen die Zahlen stammen („*Schicht vom 17.09.2026 auf den 18.09.2026* · 2 Runs ·
5 Sitzungen", unter „Gesamt" etwa „Gesamt · 30 Runs · 12 Sitzungen"). Alle Kacheln unter dem
Umschalter hängen an dieser Wahl und wechseln mit ihr gemeinsam. Die Kacheln selbst:

- **Eingabe-Token** mit einem Balken, der die Eingabe in **„Cache gelesen"** und **„frisch"**
  aufteilt. Der Balken beantwortet eine andere Frage als die Gattungen und wird nicht auf sie
  umgewidmet.
- **Ausgabe-Token**, darunter der Verlauf über die Schichten des Zeitraums.
- **Kosten**, mit dem Vergleich zum Vorzeitraum (▲/▼ und der Unterschied in Dollar).
- **Implementierungszeit** — siehe unten.
- **Bremse** — wie oft die Bremse des Kits gegriffen hat: die Zahl der als
  [festgefahren](#nachtlauf) gemeldeten Pakete, darunter „≈ … gespart (geschätzt)" als Summe der
  geschätzten gesparten Zeit (je Sitzung einmal gezählt) und im Fuß „davon *n* ohne Zeitwert" für
  Bremsungen ohne Zeitgrenze oder Laufzeit. Bei 0 Bremsungen steht nur die 0. Gezählt wird über die
  aufbewahrten Runs, unter „Gesamt" ohne Vergleich mit dem Vorzeitraum. Die Kachel gibt es nur im
  Board-Leitstand.

Unter jeder Summe stehen die beiden **Anteile**: „aus Runs" und „aus interaktiven
Sitzungen". Die Summe ist genau ihre Addition; kein Eintrag zählt in beiden.

**Der Posten „ohne Karte"** steht an der Kosten-Kachel und trägt den Verbrauch, der keinem
Arbeitspaket zuzuordnen war. Er wird nicht auf die berührten Karten verteilt — eine Verteilung
erfände eine Genauigkeit, die niemand gemessen hat. Liegt kein gemessener Rest vor, fehlt der
Posten ganz; eine 0 behauptete, es gäbe keinen.

**Die Wahl „Gesamt"** summiert über alle aufbewahrten Runs und Sitzungen des Projekts; sie ersetzt
die frühere Kachel „Gesamt über die Laufzeit", die dauerhaft unter dem Umschalter stand. Es sind
dieselben Kacheln wie im Zeitraum, nur ohne das, was einen Zeitraum braucht: kein Vergleich mit dem
Vorzeitraum, kein Verlauf über die Schichten, keine Schichtzahl. Der Fuß der Kosten-Kachel nennt
dafür die **Abdeckung** der Summe, und zwar zweigeteilt: ab welchem Datum überhaupt ein Eintrag
aufbewahrt ist (oder „ohne aufbewahrten Eintrag") und ab wann interaktive Sitzungen erfasst werden
(oder „Sitzungen nicht erfasst"). Die Zahl ist damit die Summe des **Aufbewahrten**, nicht die des
Gelebten: Was der Ringpuffer verdrängt hat, fehlt darin.

**Die Kachel „Implementierungszeit"** zeigt den Mittelwert, wie lange die Karten **dieses Boards**
in Arbeit lagen, die im gewählten Zeitraum fertig wurden — nicht die der übrigen Boards, auch wenn
der Verbrauch daneben projektweit zählt. Schicht, Woche und Monat grenzen genauso ab wie beim
Verbrauch; unter „Gesamt" gilt der Mittelwert über alle gemessenen Karten des Boards. Der Fuß nennt,
auf wie vielen Karten der Wert beruht. Gibt es im Zeitraum keine gemessene Karte, steht „—" mit
„keine Datenbasis", nie 0 Minuten.

### Eine Sitzung zählt zum Zeitraum ihres Beginns

Ein Eintrag gehört zu dem Zeitraum, in dem er **beginnt** — bei einer Sitzung also zu dem
Zeitpunkt, an dem sie eröffnet wurde, nicht zu dem, an dem sie endete. Eine Sitzung, die über eine
Zeitraumgrenze hinweg läuft, wird nicht aufgeteilt.

Dabei gilt die **Tagesgrenze 12:00** zonenlokal: Eine **Schicht** läuft von 12:00 bis 12:00 und
enthält alle Runs, die darin starten — auch tagsüber angestoßene; wer vor 12:00 startet, gehört
zur Schicht davor. Für interaktive Sitzungen hat das eine Folge, die man kennen muss: **Eine
Sitzung, die vormittags vor 12:00 beginnt, zählt zur Schicht davor.** Wer am Donnerstag um 9:30 Uhr
zu arbeiten anfängt, findet seinen Verbrauch also unter der Schicht von Mittwoch auf Donnerstag,
nicht unter dem von Donnerstag auf Freitag. Die Regel ist
dieselbe wie für Runs — eine zweite Regel für Sitzungen machte die Summe von der Gattung
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
  dieser Karte") einen **bekannten Run oder eine bekannte Sitzung ohne Zahl**: Der Eintrag steht in
  der Liste, zu diesem einen Wert liegt aber kein Messwert vor. Anders als bei den beiden anderen
  Angaben geht es nicht um einen Zeitraum, sondern um einen einzelnen Eintrag.

Dazu kommen zwei Sätze über den Zeitraum als Ganzes, die keine Lücke der Erfassung sind: „In diesem
Zeitraum hat weder ein Run noch eine Sitzung stattgefunden." (es wurde nicht gearbeitet) und „In
diesem Zeitraum liefen Runs, ihr Verbrauch liegt aber nicht vor." (es wurde gearbeitet, die Zahlen
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
  endet dort. Archivierte Karten zählen nicht mit; sie
  unterbrechen die Kette aber auch nicht — ihre Nachfahren bleiben zugehörig.
- **Vorgang eröffnen:** An einer fachlichen Anforderung oder einem Plandokument entsteht das
  Vorhaben in einem Schritt: im Detail der Karte auf **„Vorgang eröffnen"**, Name (vorbelegt mit
  dem Kartentitel) und optional ein Kürzel eingeben. Das Vorhaben wird angelegt, die Karte wird
  seine Anforderung und ist ihm zugeordnet — ohne zweiten Handgriff. Der Knopf erscheint nur,
  wo das möglich ist: nicht an einem Vorhaben, nicht an einer archivierten Karte und nicht an
  einer Karte, die schon einem Vorhaben zugeordnet ist.
- **Vorhaben-Übersicht:** eine **Liste**, je Vorhaben eine Zeile. Oben in der Zeile stehen
  Kürzel, Titel und der Fortschritt „x von y fertig“. Darunter steht die **Anforderung**, aus der
  der Vorgang eröffnet wurde; der Verweis ist **anklickbar** und öffnet die Karte. Trägt das
  Vorhaben keine Anforderung — etwa weil es von Hand zum Gruppieren angelegt wurde —, steht dort
  „Keine Anforderung hinterlegt.“. Daneben folgen die Zusammensetzung nach Arten (Anforderungen,
  Pläne, Arbeitspakete) und die Marken der gezählten Labels; am Fuß der Zeile steht der
  **Fortschrittsbalken**. Lange Titel sind gekürzt, der volle Titel erscheint beim Überfahren mit
  der Maus.
- **Ausblenden:** Ganz rechts trägt jede Zeile einen **Schalter**. Ein ausgeblendetes Vorhaben
  verschwindet mit seinen Karten vom Board; das gilt je Board und nur im eigenen Browser. Ein eben
  ausgeblendetes Vorhaben bleibt bis zum nächsten Laden der Seite **ausgegraut** in der Liste
  stehen, damit sich ein Versehen am selben Schalter zurücknehmen lässt. Oben schaltet
  **„Ausgeblendete zeigen (n)“** die ausgeblendeten Vorhaben dazu — abgeschwächt und mit dem
  Vermerk „Ausgeblendet“, an ihrer gewohnten Stelle; n ist ihre Zahl.
- **Menü an der Zeile:** Das ⋮ bietet **„Löschen“** und erscheint nur bei Bearbeitungsrecht auf
  dem Board.
- **Baum im Detail:** Ein Klick auf die Zeile öffnet das Vorhaben-Detail, und darin steht der
  **Baum** dieses Vorhabens: die Anforderung an der Wurzel, darunter die Pläne, darunter die
  Arbeitspakete. Der Baum ist mit den Pfeiltasten bedienbar; Eingabe auf einer Zeile öffnet die
  jeweilige Karte im selben Dialog. Ist dem Vorhaben noch nichts zugeordnet, sagt das Detail
  genau das.
- **Board-Vorhaben-Filter:** auf dem Board über das Dropdown „Vorhaben-Filter" nur die Karten
  eines Vorhabens anzeigen. Dieser Filter arbeitet **nur auf den direkt zugeordneten** Karten.
  Eine geerbte Karte kann deshalb im Fortschritt mitzählen und beim Filtern trotzdem fehlen —
  das ist so gewollt: Der Filter beantwortet „was liegt hier auf dem Board unter diesem
  Vorhaben", die Zählung „was gehört zu diesem Vorhaben".
- **Abgearbeitete Vorhaben:** Der stündliche Aufräumlauf archiviert jedes Vorhaben, das keine
  Karte mehr hat, die in seinem Fortschritt mitzählt („0 von 0 fertig“) — auch eines, das nie
  eine Karte hatte. Karten im Papierkorb zählen dabei wie archivierte. Das geschieht auch, wenn
  die automatische Archivierung der Karten abgeschaltet ist (Aufbewahrung 0) und Karten nur von
  Hand archiviert werden. Ein archiviertes Vorhaben fehlt danach auf der Vorhaben-Seite (auch
  unter „Ausgeblendete zeigen“), im Anlege-Dialog, im Karten-Detail, im Board-Vorhaben-Filter und
  auf dem Leitstand und lässt sich keiner Karte mehr zuordnen. Im Detail einer Karte, deren
  Vorhaben archiviert ist, steht bis zu seiner Rückkehr nur eine Nummer (`#…`) statt Kürzel und
  Titel. Wird eine seiner Karten aus dem Archiv oder dem Papierkorb zurückgeholt und zählt sie
  danach wieder mit, kehrt das Vorhaben samt seinen Karten und seiner Anforderung zurück; eine
  Karte, die aus dem Papierkorb nur ins Archiv zurückkehrt, holt es nicht zurück. Der
  Aufräumlauf löscht kein Vorhaben.

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
- **Der Alltag bleibt unberührt:** Karten anlegen, verschieben und archivieren funktioniert
  **unabhängig** vom Editiermodus.

Unabhängig vom Editiermodus zeigt die Seite **Administration** jedem Angemeldeten den Abschnitt
**„API-Schnittstelle"**: Er öffnet die Übersicht aller Aufrufe des Leitstands
(`/administration/api`) und bietet ihre Beschreibung als OpenAPI 3 zum Herunterladen (JSON und
YAML). Die Übersicht dient dem Nachschlagen; wer Aufrufe darin ausprobieren darf, steht unter
[API-Übersicht](#api-ubersicht).

### Erscheinungsbild

Gleich unter dem Editiermodus wählt jeder Angemeldete auf der Seite **Administration** das
**Erscheinungsbild** in drei Stufen: **System**, **Hell** oder **Dunkel**. Die Wahl wirkt sofort und
wird **am Konto** gespeichert — sie gilt damit auf jedem Gerät, sobald du dich angemeldet hast.
Vorgabe ist **System**.

- **System** folgt dem Farbschema des **Browsers**. Der Browser kann das Betriebssystem
  übersteuern: Steht der Rechner auf Dunkel und bleibt der Leitstand trotzdem hell, ist im Browser
  „Hell“ eingestellt. Mit **Dunkel** stellst du den Leitstand unabhängig davon um.
- Die Seiten **vor der Anmeldung** folgen immer dem System; nach dem Abmelden gilt wieder System.
- Der **Ausdruck** bleibt in jeder Stufe hell, ebenso die Nachtlauf-Auswertung.

### Sprache / Language

Die Seite **Administration** zeigt jedem Angemeldeten den Abschnitt **„Sprache / Language"** mit
der Auswahl **Deutsch** und **English**. Die Überschrift und die Namen der Sprachen stehen in jeder
Einstellung gleich, damit auch jemand ohne Deutschkenntnisse die Einstellung findet. Ändern kann
sie **nur der Plattform-Admin**; allen anderen zeigt der Abschnitt die geltende Sprache nur an.

Die Sprache gilt für die **ganze Installation** — für jeden Nutzer und auch für die Seiten vor der
Anmeldung. Sie wirkt ohne Neustart: Der Plattform-Admin sieht die Umstellung sofort nach dem
Speichern, alle anderen **beim nächsten Laden** der Seite. Eine neue Installation startet auf
Deutsch.

### Standard-Labels

Einem **Plattform-Admin** zeigt die Seite **Administration** zusätzlich den Abschnitt
**„Standard-Labels"** mit dem Knopf **„Standard-Labels für alle Boards"**. Er öffnet einen Dialog
mit dem festen Satz der Labels, die das claude-workflow-kit, die Laufsteuerung und die
[Stufenleiste](#karten-detail) erwarten, nach Gruppen und in ihrer Farbe:

- **Kit:** `kit:carry-through`, `kit:protected`, `kit:clarify`, `kit:clarified`, `kit:night`,
  `kit:nightrun`, `kit:check`, `kit:nightplan`, `kit:nightreview`, `kit:nightissues`,
  `kit:on-hold` (ein Fachplan wartet auf ein äußeres Ereignis; nur ein Mensch hebt das Warten auf)
- **Lauf:** `run:aborted`, `run:running`, `run:waiting`
- **Review:** `review:offen`, `review:done`
- **Stufenleiste:** `goal:plan`, `goal:packages`, `goal:implementation`, `goal:push-prepared`,
  `planreview:1`, `planreview:2`

Seit Version 5.0 kennt das claude-workflow-kit nur noch englische Labels; der Satz legt darum je Sache
nur ihren englischen Namen an. Deutsche Labels auf alten Karten (etwa `kit:klaeren` oder
`lauf:wartet`) erkennt der Leitstand weiter als dieselbe Sache: Sie zählen, filtern und färben wie ihr
englischer Name. Ebenso liest der Leitstand Laufstand (`## Run status`), Textprotokoll und
Ergebnisdatei auch in ihrer englischen Form; Nächte, die vorher gemeldet wurden, erscheinen
unverändert.

Ein Systemlabel — jedes Label dieses Satzes — **erklärt sich selbst**: Fährt man mit der Maus darüber
oder springt man mit der Tastatur darauf, erscheint ein kurzer Hinweis. Er sagt, was das Label
bedeutet, wer es setzt, wann und durch wen es wieder verschwindet, ob es geschützt ist (etwa: Abnehmen
geht nur angemeldet im Leitstand, nicht über ein Token) und, wo es etwas zu tun gibt, was man tun
soll. Das gilt auf der Karte im Board, im Karten-Detail, in der Label-Auswahl, im Label-Filter der
Liste, im Menü „Labels" der Mehrfachauswahl, im Dialog der Labelverwaltung und in diesem Dialog
„Standard-Labels". Im Ableitungsbaum und in den Zählmarken der Vorhaben-Kacheln erscheint der Hinweis
nicht. Der Dialog „Standard-Labels" zeigt unter jedem Label zusätzlich seine vollständige Erklärung
als Text, sodass sich alle Erklärungen ohne Darüberfahren lesen lassen. Ein altes deutsches Label
zeigt die Erklärung seines englischen Namens; ein selbst angelegtes Label zeigt keine. Die Erklärung
steht in der Sprache, die unter [Sprache / Language](#sprache-language) eingestellt ist.

Erst **„Anlegen"** legt jedes fehlende Label des Satzes auf **allen nicht archivierten Boards aller
Projekte** an; „Abbrechen" ändert nichts. Ein Label, das ein Board schon trägt, bleibt mit Farbe und
Zuordnungen unverändert und wird übersprungen. Danach meldet der Abschnitt, auf wie vielen Boards wie
viele Labels neu angelegt wurden und wie viele schon vorhanden waren. Der Knopf lässt sich beliebig
oft drücken: Ab dem zweiten Mal legt er nichts mehr an — auf einem neuen Board aber die fehlenden.

## Plattform-Leitstand

Der **Plattform-Leitstand** ist die Startseite eines **Plattform-Admins** nach dem Anmelden (Sidebar
„Verwaltung" → „Plattform-Leitstand"). Er führt **drei Bereiche**, von oben nach unten: **Aktive
Runs**, **Beendete Runs** und **Störungen**. Alle drei zeigen ausschließlich Projekte, die am
Plattform-Leitstand **teilnehmen**.

Die Seite **frischt sich selbst auf**: Was sich ändert, erscheint von allein — du musst sie nicht
neu laden.

In allen drei Bereichen steht **direkt hinter dem Lämpchen** das Symbol der **Art des Runs** —
dieselben fünf Symbole wie auf der Läufe-Seite, mit dem Wort als Hinweisfähnchen. Weil das Symbol
bei jeder Art gleich breit ist, beginnt alles dahinter in jeder Zeile an derselben Stelle.

### Aktive Runs

Jeder Run, der **gerade arbeitet** — mit Projekt, dem Wort „läuft seit" samt bisheriger Dauer,
anklickbarer Kennung des Runs und einem **Wechselblinker**: zwei Lampen, die abwechselnd zwischen
Blau und Hellblau umschlagen. Mit „Bewegung reduzieren" im Betriebssystem stehen sie still,
die linke hell, die rechte blau. Arbeitet gerade nirgends ein Run, steht
das als ausdrücklicher Satz da statt als leere Fläche.

### Beendete Runs

Jeder **beendete** Run der laufenden und der vorigen Schicht — mit Projekt, Startzeitpunkt, anklickbarer Kennung des Runs
und seinem **Ausgang**. Auch hier steht ein ausdrücklicher Satz, solange noch kein Run beendet ist.

„Laufende Schicht" meint denselben Zeitraum, den auch die Auswertung der Runs zieht: **von 12:00 bis
12:00** zonenlokal. Über die Zugehörigkeit entscheidet der **Startzeitpunkt** des Runs, nicht sein
Ende. Der Bereich ist **zweigeteilt**: „Diese Schicht" und darunter „Vorige Schicht", je mit
ihrer Spanne („vom 21.09.2026 auf den 22.09.2026"). Um 12:00 wandern die Runs der vergangenen
Nacht deshalb nach „Vorige Schicht", statt zu verschwinden; ältere Runs stehen dort nicht mehr.
Eine Zeile mit offener Störung trägt in beiden Abschnitten den Verweis „Störung".

Im Kopf des Bereichs steht, **wie viele** Runs er zeigt: **10**, **20** oder **alle**. Gezählt
werden beide Abschnitte zusammen, zuerst „Diese Schicht"; was darüber hinausgeht, nennt ein Satz
unter dem Bereich („15 weitere Runs ausgeblendet"). Die Wahl merkt sich der Browser, Vorgabe ist 10.
„Aktive Runs" und „Störungen" bleiben davon unberührt — sie zeigen immer alles.

Der Ausgang steht als Wort da — eines von vieren:

- **gelungen** — der Run ist durch, nichts steht aus.
- **nicht gelungen** — der Run ist gescheitert, hat gar nicht gearbeitet, hat sich **selbst
  abgebrochen**, nachdem er eine Karte aufgenommen hatte, oder ist verstummt (siehe Stillefrist).
- **mit Vorbehalt** — der Run ist durch, sein maßgebliches Arbeitspaket wartet aber noch auf einen
  Menschen oder wurde zurückgestellt.
- **nicht angelaufen** — der Run hat seinen Abbruch selbst gemeldet, **bevor** er eine Karte
  aufgenommen hat. Er trägt eine braune LED.

Hat der Run sich **selbst abgebrochen**, steht hinter dem Wort sein **Grund** in einer Zeile
(„nicht gelungen — Harter Stopp (dirty-tree)"). Ein selbst gemeldeter Abbruch nach aufgenommener
Karte ist immer „nicht gelungen", auch wenn das maßgebliche Arbeitspaket noch grün, gelb oder zurückgestellt war. Den
vollständigen Grund zeigt die Auswertung des Runs im Projekt.

**„nicht angelaufen" ist keine Störung.** Meldet ein Run seinen Abbruch selbst, bevor er eine Karte
aufgenommen hat — etwa weil die Arbeitskopie nicht sauber ist —, steht er braun als „nicht
angelaufen — ‹Grund›" da, und es entsteht keine Störung, die jemand quittieren müsste. Der Run hat
nichts getan, und wer ihn gestartet hat, hat den Grund schon auf der Kommandozeile gesehen. Das gilt
für jeden Grund; entscheidend ist allein, dass noch keine Karte aufgenommen war. Davon zu
unterscheiden sind:

- Ein Run, der eine Karte aufgenommen hat (einen Plan, ein Arbeitspaket) und danach abbricht,
  bleibt **nicht gelungen**, rot und eine Störung — auch wenn nur das Vorbereiten dieser Karte
  scheiterte.
- Ein Run, den der **Wächter des Kits** nachträglich als verstummt abschließt und dabei mit einem
  Grund versieht, bleibt eine Störung, auch ohne aufgenommene Karte: Hier hat niemand eine Meldung
  auf der Kommandozeile gesehen.
- Runs aus der Zeit, bevor es „nicht angelaufen" gab, melden keine Abschlussart und behalten ihren
  bisherigen Ausgang.

**Die Stillefrist.** Ein unfertiger Run, der über diese Frist hinweg **kein Lebenszeichen** gibt,
gilt als **nicht gelungen** — sonst bliebe ein abgeschossener Runner für immer als „läuft" stehen.
Die Frist ist ein **Einstellwert der Plattform** (`manban.nightrun.stille-frist`, Vorgabe 90
Minuten): Sie gilt für alle Projekte gleich und hat bewusst **keine Oberfläche** — wer sie ändern
will, ändert die Konfiguration der Instanz.

Ein verstummter Run ist **eine Störung**: Er erscheint als „nicht gelungen" und steht im Bereich
„Störungen" — wie alt er auch ist, denn er hat sich nie abgemeldet. Erst die Quittung dort nimmt ihn
auch aus [„Runner jetzt“](#runner-jetzt) heraus. Ein Run, der sich noch innerhalb der Frist
gemeldet hat, steht dort nicht, ebenso wenig ein von Hand beendeter.

### Störungen

Jede nicht quittierte Störung aus den Runs teilnehmender Projekte — mit Projekt, Zeitpunkt,
anklickbarer Kennung des Runs, Grund und dem Knopf **„Störung löschen"**.

Der **Grund** ist bei einem selbst abgebrochenen Run sein Abbruchgrund, sonst der Grund des
maßgeblichen Arbeitspakets („Karte #721: gescheitert") oder der Satz eines Runs ohne Arbeit. Es ist
derselbe gekürzte Text und dasselbe Lämpchen wie in der Zeile des Runs unter „Beendete Runs" — eine
Seite, ein Ausgang.

Ob ein Projekt teilnimmt, entscheidet ausschließlich das Projekt selbst — OWNER oder ADMIN mit
echter Mitgliedschaft, über das Teilnahme-Ankreuzfeld im [Editiermodus](#editiermodus) der
Projektliste. Der Plattform-Admin sieht nur Runs und Störungen teilnehmender Projekte und kann die
Teilnahme selbst nicht erzwingen.

## API-Übersicht {#api-ubersicht}

Für alle Angemeldeten ohne Plattform-Rolle ADMIN ist die API-Übersicht (`/administration/api`) rein
lesend: Sie zeigt Aufrufe, Eingaben und Antworten, bietet aber kein Absenden an.

Ein **Plattform-Admin** kann jeden Aufruf dort **ausprobieren**: Eingaben ausfüllen, absenden und die
echte Antwort des Leitstands sehen, Fehlermeldungen eingeschlossen. Der Aufruf läuft mit den Rechten
der eigenen Sitzung — was der Admin im Leitstand nicht darf, wird auch hier abgelehnt. Aufrufe, die
statt der Anmeldung ein **Projekt-Token** verlangen (Schema `projektToken`), nehmen das Token über
**„Authorize"** entgegen und laufen dann mit genau dessen Rechten. Das Token liegt nur im Speicher der
Seite; nach dem Verlassen oder Neuladen der Übersicht ist es fort.

Vor jedem **ändernden** Aufruf — alles außer GET und HEAD — fragt die Übersicht nach: Der Aufruf
verändert echte Daten dieser Umgebung und geht erst nach der Bestätigung ab. Lesende Aufrufe gehen
ohne Rückfrage ab. Das gilt in jeder Umgebung, **auch in der Produktion**: Ausprobieren wirkt immer
auf die echten Daten des Leitstands, in dem die Übersicht läuft.

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
