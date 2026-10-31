# Rollen & Rechte

kanban-kit hat **zwei** Rollen-Ebenen. Sie werden oft verwechselt — deshalb zuerst die Abgrenzung:

| Ebene | Werte | Gültigkeit | Wo sichtbar |
|------|-------|------------|-------------|
| **Plattform-Rolle** | `USER`, `ADMIN` | instanzweit (ganzes System) | „Admin"-Bereich / Seitenleiste |
| **Projekt-Rolle** | `OWNER`, `ADMIN`, `MEMBER`, `VIEWER` | pro Projekt (Mitgliedschaft) | Mitglieder-Seite eines Projekts |

Ein Nutzer kann also plattformweit **USER** sein und in *seinem* Projekt trotzdem **OWNER** — beides ist
unabhängig. Die Mitglieder-Seite zeigt ausschließlich die **Projekt-Rolle**.

## Projekt-Rollen: Rechte-Matrix

Die Rechte sind granular als **CRUD-Matrix pro Ressource** modelliert (je Ressource und Operation ein
Recht). Der Schlüssel in der zweiten Spalte ist der technische Name des Rechts — er taucht so in der
`/roles`-Ansicht der App auf. Für die vier eingebauten Rollen sind die Rechte fest gesetzt.

**Jede Zeile der Tabelle trägt einen Schlüssel** (Issue #1165). Das ist keine Formalie: Die
`/roles`-Ansicht rendert ausschließlich, was `GET /api/roles/matrix` liefert, und dort steht nur, was
einen Schlüssel hat. Eine Zeile ohne Schlüssel wäre ein Recht, das die App nie zeigt — ein Test hält
die Regel (siehe [Woher die Matrix kommt](#woher-die-matrix-kommt)). Was an keiner Projekt-Rolle
hängt, gehört deshalb nicht in die Tabelle, sondern unter [Sonderregeln](#sonderregeln) bzw.
[Plattform-Rollen](#plattform-rollen).

| Recht | Schlüssel | VIEWER | MEMBER | ADMIN | OWNER |
|-------|-----------|:------:|:------:|:-----:|:-----:|
| Boards und Spalten lesen | `BOARD_READ` | ✓ | ✓ | ✓ | ✓ |
| Board anlegen | `BOARD_CREATE` | – | – | ✓ | ✓ |
| Board umbenennen, Spalten und Board-Labels verwalten | `BOARD_UPDATE` | – | – | ✓ | ✓ |
| Board archivieren / löschen, Karte endgültig löschen | `BOARD_DELETE` | – | – | ✓ | ✓ |
| Vorhaben lesen | `EPIC_READ` | ✓ | ✓ | ✓ | ✓ |
| Vorhaben anlegen | `EPIC_CREATE` | – | ✓ | ✓ | ✓ |
| Vorhaben bearbeiten | `EPIC_UPDATE` | – | ✓ | ✓ | ✓ |
| Vorhaben archivieren / in den Papierkorb legen | `EPIC_DELETE` | – | ✓ | ✓ | ✓ |
| Karten lesen (inkl. Dashboard, Papierkorb) | `TICKET_READ` | ✓ | ✓ | ✓ | ✓ |
| Karte anlegen, Spezifikation einlesen | `TICKET_CREATE` | – | ✓ | ✓ | ✓ |
| Karte bearbeiten (Titel, Text, Zuständige, Fälligkeit, Labels) | `TICKET_UPDATE` | – | ✓ | ✓ | ✓ |
| Nacht-Kette an der Stufenleiste: Ziel und Prüferzahl wählen, Kette starten, Start zurücknehmen | `TICKET_UPDATE` | – | ✓ | ✓ | ✓ |
| Kettenstand an der Stufenleiste einer Karte lesen | `TICKET_READ` | ✓ | ✓ | ✓ | ✓ |
| Karte archivieren, in den Papierkorb legen, wiederherstellen | `TICKET_DELETE` | – | ✓ | ✓ | ✓ |
| Karte verschieben — Spalte, **anderes Board desselben Projekts**; Spalte nach Kartennummer sortieren; Status eines Arbeitspakets setzen (`PUT /api/cards/{cardId}/status`) | `CARD_MOVE` | – | ✓ | ✓ | ✓ |
| **Karte in ein anderes Projekt verschieben** — OWNER in **beiden** Projekten | `CARD_MOVE_PROJECT` | – | – | – | ✓ |
| Kommentare lesen | `COMMENT_READ` | ✓ | ✓ | ✓ | ✓ |
| Kommentar schreiben | `COMMENT_CREATE` | – | ✓ | ✓ | ✓ |
| Kommentar bearbeiten *(nur eigenen)* | `COMMENT_UPDATE` | – | ✓ | ✓ | ✓ |
| Kommentar löschen *(Moderation)* | `COMMENT_DELETE` | – | – | ✓ | ✓ |
| Anhänge lesen / herunterladen | `ATTACHMENT_READ` | ✓ | ✓ | ✓ | ✓ |
| Anhang hochladen | `ATTACHMENT_CREATE` | – | ✓ | ✓ | ✓ |
| Anhang löschen | `ATTACHMENT_DELETE` | – | ✓ | ✓ | ✓ |
| Mitglied einladen | `MEMBER_INVITE` | – | – | ✓ | ✓ |
| Mitglied entfernen, Rolle oder Anzeigename ändern | `MEMBER_REMOVE` | – | – | ✓ | ✓ |
| Projekt umbenennen, Karten-Startnummer setzen | `PROJECT_EDIT` | – | – | – | ✓ |
| Eigentümerschaft übertragen | `PROJECT_OWNER_TRANSFER` | – | – | – | ✓ |
| **Auswertung der Läufe lesen** — Plattform-Admin auch ohne OWNER, aber nur bei Teilnahme am Plattform-Leitstand | `NIGHT_RUN_READ` | – | – | – | ✓ |
| Übersicht „Heute Nacht“ und Morgenkachel „Veröffentlichung vorbereitet“ auf der Runner-Seite sehen — dieselbe Regel wie die Auswertung der Läufe | `NIGHT_RUN_READ` | – | – | – | ✓ |
| Bereich „Runner jetzt“ auf der Runner-Seite sehen — dieselbe Regel wie die Auswertung der Läufe | `NIGHT_RUN_READ` | – | – | – | ✓ |
| Platte „Nacharbeit“ im Board-Leitstand sehen (`GET /api/boards/{boardId}/night-run-rework`, `requireNightRunAccess`) — dieselbe Regel wie die Auswertung der Läufe | `NIGHT_RUN_READ` | – | – | – | ✓ |
| Platte „Auslieferung“ im Board-Leitstand lesen (`GET /api/boards/{boardId}/dashboard/delivery`, `requireMembership`) — geprüft wird die Projektmitgliedschaft wie beim übrigen Leitstand, also jede Rolle, die das Board lesen darf; zeigt die Daten des ganzen Projekts | `BOARD_READ` | ✓ | ✓ | ✓ | ✓ |
| Abschnitt „Wartegrund“ im Kartendialog eines Pakets in Ready sehen — dieselbe Regel wie die Auswertung der Läufe | `NIGHT_RUN_READ` | – | – | – | ✓ |
| Läufe in der [mobilen Oberfläche](nutzung.md#unterwegs) unter `/mobil/laeufe` sehen (`GET /api/mobil/laeufe`, `GET /api/mobil/laeufe/{laufId}`) — die mobile Sicht folgt genau dem Recht, Läufe im Leitstand zu sehen: Wer sie dort nicht sieht, sieht sie auch mobil nicht, und ein solcher Lauf antwortet mit 404 wie ein unbekannter | `NIGHT_RUN_READ` | – | – | – | ✓ |
| **Protokoll eines Laufs hineingeben** — der Plattform-Admin immer | `NIGHT_RUN_SUBMIT` | – | – | – | ✓ |

Dieselbe Übersicht ist in der App unter **`/roles`** erreichbar (Link „Rollen & Rechte" auf der
Mitglieder-Seite).

### Sonderregeln

Einige Aktionen folgen nicht allein dem Recht aus der Tabelle:

- **Karte auf ein anderes Board verschieben:** Innerhalb *desselben* Projekts genügt `CARD_MOVE`, also
  ab MEMBER. Der Weg in ein *anderes* Projekt trägt `CARD_MOVE_PROJECT` und verlangt die Rolle **OWNER
  in beiden** Projekten — im Quell- **und** im Zielprojekt. Das Recht steht in der Matrix, die
  Bedingung „in beiden" ist die Zusatzbedingung dazu und kein zweites Recht. Grund: Projektgrenzen sind
  die Vertraulichkeitsgrenze; die Karte bekommt dabei eine neue projektweite Nummer, Abhängigkeiten und
  Zuständige entfallen.
- **Teilnahme am Plattform-Leitstand schalten:** kein Recht der Matrix, sondern die **echte
  Projekt-Rolle** `OWNER` oder `ADMIN` — und bewusst **nicht** der Plattform-Admin, obwohl der sonst
  jede Prüfung passiert. Die Teilnahme ist die Einwilligung des Projekts in die Einsicht durch den
  Betreiber, und wer sie einholt, erteilt sie sich nicht selbst. Genau deshalb hat sie keinen
  Schlüssel: Ein Häkchen in einer Rollenspalte müsste für den Plattform-Admin anders gelten als für
  alle anderen.
- **Monatsbudget festlegen, ändern oder entfernen:** nur die **echte Projekt-Rolle** `OWNER` —
  kein Durchgriff des Plattform-Admins. Das Budget ist eine Ausgabeentscheidung des Projekts.
  **Sehen** (Betrag, Stand, Warnungen, Hochrechnung) darf, wer den Verbrauch des Projekts sieht,
  siehe [Monatsbudget](nutzung.md#monatsbudget).
- **Karte endgültig löschen** (aus dem Papierkorb) hängt bewusst an `BOARD_DELETE`, ist also erst ab
  ADMIN erlaubt — anders als das Legen in den Papierkorb (`TICKET_DELETE`, ab MEMBER). Wiederherstellen
  darf, wer löschen darf: so kann ein MEMBER einen eigenen Fehlgriff selbst korrigieren.
- **Kommentar bearbeiten:** `COMMENT_UPDATE` reicht nicht allein — bearbeiten darf **nur der Autor**.
  Auch ADMIN und OWNER können fremde Kommentare nicht ändern, nur löschen.
- **Lesen** wird serverseitig über die **Mitgliedschaft** im Projekt geprüft, nicht über das jeweilige
  `*_READ`-Recht. Das Ergebnis ist dasselbe, weil alle vier Rollen sämtliche Leserechte haben; die
  Lese-Rechte in der Matrix beschreiben die Absicht und sind für konfigurierbare Rollen (siehe unten)
  vorgesehen.
- **Board-gebundene API-Tokens** siehe [API-Tokens](#api-tokens).

### Rollen im Detail

- **VIEWER:** darf ausschließlich lesen — Boards, Spalten, Vorhaben, Karten, Kommentare, Anhänge.
- **MEMBER:** zusätzlich Karten und Vorhaben anlegen, bearbeiten, verschieben und in den Papierkorb legen,
  Kommentare schreiben (und eigene bearbeiten) sowie Anhänge hochladen und löschen.
- **ADMIN:** zusätzlich Boards, Spalten und Board-Labels verwalten, Karten endgültig löschen,
  Kommentare löschen (Moderation) und Mitglieder einladen/entfernen.
- **OWNER:** alle Projekt-Rechte, dazu Projekt umbenennen, Karten projektübergreifend verschieben,
  die Eigentümerschaft an ein anderes Mitglied übertragen (dabei wird der bisherige Owner zum ADMIN)
  und die [Auswertung der Läufe](nutzung.md#nachtlauf) lesen sowie Protokolle hineingeben. Das alles
  kann bewusst **nur** der Owner, nicht der Projekt-Admin.

  Neben dem Owner passiert allerdings auch der **Plattform-Admin** diese Prüfung — als Super-User
  hat er auf alle Projekte Vollzugriff, siehe [Plattform-Rollen](#plattform-rollen). „Nur der Owner"
  grenzt also gegen die *Projekt*-Rollen ab, nicht gegen die Plattform-Ebene. Bei den Läufen gilt das
  nur eingeschränkt: Der Plattform-Admin liest die Auswertung der Läufe, die Anläufe und den
  Verbrauch eines Projekts ohne eigene Mitgliedschaft **nur, wenn das Projekt am
  Plattform-Leitstand teilnimmt**. Das Protokoll-Hineingeben (Ingest) bleibt davon unberührt.

## Plattform-Rollen

- **USER** — sieht und bearbeitet nur eigene Projekte bzw. Projekte, in denen er Mitglied ist.
- **ADMIN** — **Super-User**: Vollzugriff auf **alle** Projekte (unabhängig von der Mitgliedschaft) und
  **Nutzerverwaltung**.

### Nur mit der Plattform-Rolle ADMIN

Sechs Dinge gewährt **keine** Projekt-Rolle — auch OWNER nicht. Sie stehen deshalb hier und nicht als
Zeile der [Rechte-Matrix](#projekt-rollen-rechte-matrix) oben; eine Zeile, die in jeder Rollenspalte
leer bliebe, sähe aus wie ein vergessenes Häkchen und lüde dazu ein, es einer Rolle zu geben.

- **Plattform-Leitstand ansehen** — die Übersicht über die Läufe aller teilnehmenden Projekte, siehe
  [Plattform-Leitstand](nutzung.md#plattform-leitstand).
- **Projekt anlegen und löschen** — siehe den Absatz darunter.
- **API-Aufrufe aus der Übersicht ausprobieren** — nur der Plattform-Admin; der Aufruf läuft mit den
  Rechten der Sitzung bzw. des angegebenen Projekt-Tokens, und der Server prüft das Kennzeichen
  `X-Api-Ausprobieren`. Siehe [API-Übersicht](nutzung.md#api-ubersicht).
- **Standard-Labels für alle Boards anlegen** — nur der Plattform-Admin, über die Seite
  Administration; legt den festen Label-Satz auf allen nicht archivierten Boards aller Projekte an und
  überspringt vorhandene. Siehe [Standard-Labels](nutzung.md#standard-labels).
- **Sprache der Installation ändern** — nur der Plattform-Admin; die Einstellung gilt für alle
  Nutzer und auch für die Seiten vor der Anmeldung. Lesen darf sie jeder, auch ohne Anmeldung.
- **Ansicht als …** — nur der Plattform-Admin sieht die App nur lesend so, wie ein anderes Konto sie
  sähe; nicht als das eigene Konto und nicht als ein anderer Plattform-Admin, wohl aber als gesperrtes
  oder noch nicht freigegebenes Konto. Der Server sperrt jede Änderung, auch außerhalb der Oberfläche,
  und das Ausprobieren in der API-Übersicht ganz. Gezeigt wird nie mehr, als der Admin selbst sehen
  darf: Laufdaten eines nicht teilnehmenden Projekts bleiben verborgen, solange er dort keine eigene
  Projektrolle hat. Profil, persönliche API-Tokens und persönliche Einstellungen des Kontos bleiben
  außen vor. Die Ansicht gilt nur für die Anmeldung, aus der sie gestartet wurde; andere Anmeldungen
  und die API-Tokens des Admins bleiben unberührt. Kein neues Recht der Matrix — siehe
  [Ansicht als …](nutzung.md#ansicht-als).

**Projekte legt ausschließlich der Plattform-Admin an und löscht sie auch nur er.** Beim Anlegen
bestimmt er den **Owner** per E-Mail; dadurch wird er selbst nicht Mitglied, hat als Plattform-Admin
aber ohnehin Vollzugriff. Beim Löschen gibt es eine Sicherheitsabfrage — Boards, Vorhaben und Karten des
Projekts werden mitgelöscht (Kaskade). Dasselbe gilt beim Löschen eines Boards für dessen Vorhaben und
Karten.

## Admin-Bereich (`/admin`)

Nur für Plattform-Admins sichtbar (Eintrag „Admin" in der Seitenleiste):

- **Nutzerliste** (E-Mail, Name, verifiziert, Rolle, Status).
- **Plattform-Rolle umschalten** (USER ↔ ADMIN).
- **Konto sperren / entsperren:** über den Button „Sperren" bzw. „Entsperren" (Status-Chip „Aktiv" /
  „Gesperrt"). Ein gesperrtes Konto kann sich nicht mehr anmelden („Konto gesperrt") und wird auch aus
  bestehenden Sitzungen abgewiesen — das gilt für die Web-Anmeldung **und** für API-/Ingest-Tokens.
- **Schutz:** der letzte **nicht gesperrte** Plattform-Administrator kann weder herabgestuft noch
  gesperrt werden (kein Aussperren). Konten mit Administrator-Rolle zählen dabei **nicht** mit,
  solange sie gesperrt sind — ein gesperrter Admin hält die Plattform also nicht offen. Daneben gilt
  unverändert: man kann sich **nicht selbst** sperren. Analog kann der letzte OWNER eines Projekts
  weder degradiert noch entfernt werden.
- **Ansicht als …:** Der Knopf in einer Zeile der Nutzerliste startet die nur lesende Ansicht als
  dieses Konto und öffnet dessen Startseite. Ein fester Balken „Du siehst die App als XY. Nur lesend."
  steht auf jeder Seite; „Beenden" darin oder spätestens 30 Minuten beenden sie. Jeder Start und jedes
  Ende steht als Eintrag bei der Zeile des Zielkontos (Admin, Zeit, Grund des Endes); das Zielkonto
  sieht die Einträge im eigenen Profil.

Den ersten Admin richtet man über den Bootstrap-Token oder direkt in der DB ein — siehe
[Betrieb → Den ersten Admin einrichten](betrieb.md#den-ersten-admin-einrichten).

> **Nicht zu verwechseln:** Der Sidebar-Eintrag **„Administration"** (`/administration`) ist für
> **alle** angemeldeten Nutzer da und enthält den [Editiermodus](nutzung.md#editiermodus)-Schalter.
> Der **„Admin"-Bereich** (`/admin`) oben ist ausschließlich für Plattform-Admins. Der Editiermodus
> vergibt keine Rechte — er blendet nur die Bearbeiten-Symbole ein, sofern die Rolle das Recht
> ohnehin hat.

## Woher die Matrix kommt

Die Matrix ist die einzige Quelle der Wahrheit und wird serverseitig geliefert
(`GET /api/roles/matrix`: Rollen, Rechte mit Ressource/Operation, Grants je Rolle). Die
`/roles`-Ansicht rendert sie als **Checkbox-Grid**: Spalten = einzelne Rechte, Zeilen = Rollen. Für die
vier eingebauten Rollen sind die Haken **read-only**.

Diese Seite hier ist die einzige Rechte-Übersicht im Repository; ein Test hält sie an das
`Permission`-Enum gekoppelt, damit Code und Doku nicht auseinanderlaufen — in **beide** Richtungen:
Jedes Recht des Enums braucht eine Zeile, und jede Zeile der Matrix braucht einen Schlüssel
(`RightsDocumentationTest`, Issue #1165).

**Ausblick:** Frei konfigurierbare Zusatzrollen sind vorgesehen — dieselbe Matrix, weitere Zeilen mit
aktivierbaren Haken, ohne Änderung an den vier eingebauten Rollen.

## API-Tokens

Persönliche API-Tokens (für die Kanban-Compat-API / CLIs) erzeugt und widerruft man unter
**Administration → API-Tokens** (siehe [Dogfooding](dogfooding.md)). Ein **board-gebundenes** Token
darf nur anlegen, wer auf dem betreffenden Board das Recht hat, **Karten anzulegen** (`TICKET_CREATE`)
— ein VIEWER also nicht. Damit kann ein Token nie mehr als sein Ersteller: das Board über die API zu
treiben (Karten anlegen/verschieben) entspricht genau dem Recht, das man dafür ohnehin bräuchte.

Ein **board-gebundenes** Token kann ausschließlich die Kanban-Compat-API (`/api/kanban/**`) seines
gebundenen Boards bedienen; jeder andere `/api/**`-Zugriff wird mit **403** abgewiesen — unabhängig
von den Rollen des Erstellers, auch bei einem Plattform-Admin. Ein **ungebundenes** Token bleibt in
seinem bisherigen Umfang nutzbar.

Lesend erreicht ein board-gebundenes Token über die Kanban-Compat-API auch das **Archiv** seines
Boards: Einzelabruf nach Nummer, Suche und Herkunftskette (siehe
[Dogfooding](dogfooding.md#archivierte-karten-nummer-suche-herkunftskette)) liefern archivierte
Karten mit. Karten im **Papierkorb** bleiben unsichtbar, ebenso Karten anderer Boards — beide
antworten mit **404** wie eine unbekannte Nummer. Das gilt auch für Verlauf und Kommentare über die
ID (`/api/kanban/items/{id}/activity` und `…/comments`): Eine Karte im Papierkorb liefert dort
**404**, eine archivierte Karte bleibt lesbar. Ein **ungebundenes** Token erhält auf diese
Aufrufe **409**, weil ihm das Board fehlt.

Die **Anhänge** der Karten seines Boards erreicht ein board-gebundenes Token lesend — auflisten und
herunterladen (siehe [Dogfooding](dogfooding.md#anhange-einer-karte-lesen)), ohne eigene Erlaubnis.
Hochladen oder löschen kann es keinen Anhang. Ein unerreichbarer Anhang — an einer Karte eines
anderen Boards oder im Papierkorb, oder bereits gelöscht — wird mit **404** beantwortet wie einer, den
es nicht gibt.

**Auslieferungen melden** (siehe [Platte Auslieferung](nutzung.md#platte-auslieferung)) geht über
`POST /api/kanban/deployments`; `GET /api/kanban/deployments/latest` liefert den zuletzt gemeldeten
Stand. Beide nimmt nur ein **board-gebundenes** Token an, dessen Inhaber im Projekt des Boards das
Recht `TICKET_CREATE` hat — dieselbe Bedingung, unter der das Token angelegt werden durfte, ein
neues Recht entsteht nicht. Geprüft wird bei jedem Aufruf: Ist der Inhaber nur noch VIEWER, antwortet
das Board mit **403**; ist er kein Mitglied des Projekts mehr, mit **404**. Ein **ungebundenes** Token
erhält **409** wie auf der übrigen Kanban-Compat-API; seine Meldung erscheint in keinem Projekt.

### Freigabe-Labels des Kits

Fünf Labels des claude-workflow-kits tragen eine Freigabe-Richtung, und der Server erzwingt sie
(Issue #1421, #1667). `kit:night` und `kit:nightrun` geben Arbeit für den Nachtlauf frei: Sie setzt nur ein
Mensch, der Runner nimmt sie ab, sobald sie verbraucht sind. `kit:klaeren`, `kit:geschuetzt` und
`kit:wartet` setzt die Maschine; ihr Abnehmen ist die Freigabe und steht nur dem Menschen zu.

| | Session (Mensch) | Token (Kit, Runner, Agent) |
|---|---|---|
| `kit:night`, `kit:nightrun` setzen | erlaubt | abgewiesen (403) |
| `kit:night`, `kit:nightrun` abnehmen | erlaubt | erlaubt |
| `kit:klaeren`/`kit:clarify`, `kit:geschuetzt`/`kit:protected`, `kit:wartet`/`kit:on-hold` setzen | erlaubt | erlaubt |
| `kit:klaeren`/`kit:clarify`, `kit:geschuetzt`/`kit:protected`, `kit:wartet`/`kit:on-hold` abnehmen | erlaubt | abgewiesen (403) |

Das claude-workflow-kit lässt sich auf Englisch einstellen. Darum gilt jede Regel unter beiden Namen
derselben Sache: `kit:clarify` wie `kit:klaeren`, `kit:protected` wie `kit:geschuetzt`, `kit:on-hold` wie `kit:wartet`. `kit:night`
und `kit:nightrun` heißen in beiden Sprachen gleich und haben kein Gegenstück.

Die Labels, mit denen die [Stufenleiste](nutzung.md#karten-detail) Ziel und Prüferzahl einer Kette
festhält — `ziel:plan`, `ziel:pakete`, `ziel:umsetzung`, `ziel:push-vorbereitet` sowie `planreview:1`
und `planreview:2` —, tragen **keine** Freigabe-Richtung. Freigegeben wird allein mit `kit:night`.
Ein Token darf `ziel:*` und `planreview:*` deshalb setzen und abnehmen; der Runner nimmt sie bei der
Übernahme zusammen mit `kit:night` ab.

Entscheidend ist die serververifizierte Herkunft — gebundene wie ungebundene Token —, nicht die
Selbstauskunft im Header `X-Agent-Model`. Eine zweite Selbstauskunft ist der Header `X-Night-Run`, die
Laufkennung eines Nachtlaufs: Das Board speichert sie nur bei Token-Herkunft, sie wirkt allein auf
die Zuordnung der Karten auf der Runner-Seite und begründet kein Recht. Der Server kann Mensch und Agent am Token nicht
unterscheiden: Auch wer per CLI (`board.mjs`, `tbx`) arbeitet, setzt `kit:night` und
`kit:nightrun` nur im Board. Die Regel gilt an jedem Schreibweg: einzelnes Label setzen oder
abnehmen, alle Labels einer Karte ersetzen (geprüft wird nur, was hinzukommt oder wegfällt), die
Massenaktion und das Anlegen einer Karte mit Labels. Per Token darf zudem keine Definition dieser
vier Labels gelöscht oder umbenannt und kein anderes Label auf einen ihrer Namen umbenannt werden;
anlegen darf ein Token sie, weil das Kit seine Labels selbst anlegt. Die Namen werden getrimmt und
mit Groß-/Kleinschreibung verglichen, genau wie bei der Label-Auflösung.

### Namensraum des Kits

Bestimmte Präfixe gehören dem claude-workflow-kit: `kit:`, `lauf:`, `ziel:`, `review:`,
`planreview:`, `run:` und `goal:`. Sie werden ohne Rücksicht auf Groß- und Kleinschreibung erkannt —
`KIT:night` fällt ebenso darunter wie `Review: UX`. Ein Name in diesem Namensraum, den der Leitstand
nicht als Kit-Label kennt, ist für ein **Token** gesperrt: Setzen an einer Karte und Umbenennen auf
diesen Namen werden abgewiesen (403), die Meldung nennt den Namensraum als Grund. Anlegen und
Abnehmen bleiben einem Token erlaubt. Bekannt ist ein Name nur, wenn er nach dem Trimmen exakt einem
Kit-Label entspricht.

Das trifft auch **bestehende projekteigene Labels**: Ein Label wie `Review: UX` kann ein Token nicht
mehr setzen, ein Mensch in der Session dagegen weiterhin. In der Session bleibt alles erlaubt.

Ein neuer Name im Namensraum entsteht nur über einen Eintrag in
`src/main/java/org/mwolff/manban/common/KitNamen.java`, und der kommt über einen `[Task]` mit
menschlicher Freigabe — kein Token legt fest, welche Namen das Kit kennt.
