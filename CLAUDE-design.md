# CLAUDE-design.md — Designsprache des Leitstands

Diese Datei ist die Designquelle der Anwendung **kanban-kit**. Sie beschreibt, **was** die Oberfläche trägt: Palette, Font, Radien, Tiefe, Kontrast, Erscheinungsbilder, Zustandsformen. **Wie** diese Werte im Code angewendet werden — Theme-zentral, über die `sx`-Prop, keine hartcodierten Werte — regelt [CLAUDE-react.md](CLAUDE-react.md).

**Geltungsbereich:** ausschließlich der Leitstand, also diese Anwendung. Regeln für Veröffentlichungen (Blog, LinkedIn, Whitepaper, Website) und für Präsentationen gelten hier **nicht** — sie stammen aus einem anderen Geltungsbereich und sind schon einmal fälschlich auf die Anwendung übertragen worden (siehe [Historie](#historie)).

**Bindend** sind diese Datei und [`frontend/src/theme.ts`](frontend/src/theme.ts) (Plan #932, E3). Der Gestaltungsentwurf [`docs/entwurf-leitstand.html`](docs/entwurf-leitstand.html) ist Zielbild, nicht Wertequelle.

---

## 🎨 Palette

Sieben Farben, übernommen aus der persönlichen Marke des Autors, die für Veröffentlichungen gilt. Ab hier sind es die Farben **des Leitstands**: Sie ändern sich mit dieser Anwendung und nicht mit jener Marke. Die Tabelle nennt das **helle** Erscheinungsbild; das dunkle leitet sich daraus ab (siehe [Erscheinungsbilder](#erscheinungsbilder)).

| Rolle | Wert | Verwendung |
|---|---|---|
| Teal (Primärfarbe) | `#2F8C97` | Primäraktionen, Akzente, Fokusring |
| Teal dunkel | `#1E5F68` | Sekundärfarbe, dunkle Akzente; dunkel die Fläche der Kopfleiste |
| Teal hell | `#5BABB5` | Fläche der Kopfleiste |
| Tinte | `#243539` | Fließtext (`text.primary`), Grundton aller hellen Schatten |
| Gedämpft | `#54696E` | Sekundärtext (`text.secondary`) |
| Rand | `#D8ECEE` | Haarlinien, Divider |
| Eis | `#EDF5F6` | hellste getönte Fläche, Panel-Köpfe |

---

## 🌗 Erscheinungsbilder

**Zwei Erscheinungsbilder, beide vollwertig gestaltet, kein Schalter** (Fachplan #925, AK 17; Plan #932, E9). Die Anwendung folgt der Einstellung des Betriebssystems: `theme.ts` trägt MUI-CSS-Variablen mit `cssVariables: { colorSchemeSelector: 'media', cssVarPrefix: 'mb' }`, die Dunkelwerte stehen damit unter `@media (prefers-color-scheme: dark)`. Es gibt keinen Zustand, keine Persistenz und kein Bedienelement, über das jemand das Erscheinungsbild wählen könnte — das Einstellen ist ein Nicht-Ziel des Fachplans. Ein Selektor `class` oder `data` wäre ein solcher Zustand und ist deshalb ausgeschlossen; `theme.test.ts` hält `media` fest.

**Herkunft der dunklen Töne:** abgeleitet aus der hellen Teal-Familie, keine eigene Vorlage. Die Rollen bleiben, die Helligkeit kehrt sich um: Grund, Papierfläche und Eis sind fast schwarze Teal-Töne in drei Stufen — dunkel trägt die Höhe einer Fläche ihre Helligkeit, nicht ihr Schatten. Schrift und Akzente sind aufgehellt, damit sie auf dunklem Grund dieselbe Rolle behalten; die Kopfleiste trägt den dunklen Teal der Palette. Status-, Vorhaben- und Nachtlauf-Farben haben je einen eigenen Dunkelwert.

**Tokens sind Verweise, keine Werte.** Die Konstanten aus `theme.ts` (`SURFACE_TINT`, `APP_BACKGROUND`, `CARD_SHADOW` usw.) tragen `var(--mb-…)`; `statusColors()` und `epicColor()`/`epicTint()` liefern ebenfalls Variablen-Verweise. Wer einen Wert braucht, liest ihn je Erscheinungsbild aus `theme.colorSchemes`. `theme.palette.*` liefert nur den hellen Wert — eine Lesung beim Modulstart schaltet deshalb nicht um; dort gilt `theme.vars.palette.*`.

**Gefüllte Meldungen** (die Toasts) tragen eigene Flächen je Zustand mit weißer Schrift, gleich in beiden Erscheinungsbildern: MUIs Ableitung verfehlte 4,5:1 bei vier Paaren (#960).

---

## ✒️ Typografie

- **Carlito, ersatzweise Calibri** (metrikgleich), danach die Systemschriften.
- **Zwei Gewichte, mehr nicht:** 400 für Fließtext, 700 für Titel, Schaltflächen und Zwischenüberschriften. Kein 300, kein 500, kein 600. Der Bestand hält das noch nicht überall — siehe [Offene Abweichungen](#offene-abweichungen).
- **Tabellenziffern für Zahlen untereinander** (AK 10): rechtsbündige Tabellenzellen zentral über `MuiTableCell`, Kennzahl-Kacheln, Nummernspalte der Liste, Fortschritt der Vorhaben und Spaltenzahl der Boards über den Baustein `TABELLENZIFFERN`. **Eine große Einzelzahl trägt sie nicht** — gleich breite Ziffern lassen sie auseinanderfallen (Hero-Zahl des Dashboards).

---

## 📐 Radien

Drei Werte, drei Bedeutungen:

| Ebene | Radius | Token |
|---|---|---|
| Karte | 10 px | `CARD_RADIUS` |
| Panel (Spalte, Vorhaben-Kachel, Block des Kartenblatts) | 14 px | `PANEL_RADIUS` |
| Bedienelement (Grundradius) | 8 px | `shape.borderRadius` |

Der Grundradius liegt bewusst zwischen den beiden anderen: rund genug, dass Schaltflächen und Eingabefelder zur Panel-Sprache passen, ohne zu Pillen zu werden.

---

## 🌓 Tiefe

**Flächen tragen Tiefe, Bedienelemente nicht.** Karten und Panels stehen auf Schattenebenen; Schaltflächen, Menüs und Eingabefelder bleiben flach mit Haarlinie. Tiefe ist ein Mittel, Ebenen zu unterscheiden — nicht, Dinge hervorzuheben.

**Alle Schatten führen eine Tinte, nie Schwarz.** Hell ist es die Marken-Tinte `rgba(36,53,57,…)`, dunkel eine fast schwarze Teal-Tinte `rgba(4,14,16,…)`. Ein Schatten in der Grundfarbe wirkt wie Licht, ein schwarzer wie Schmutz — auf dunklem Grund nicht anders. Die Lichtkante an der Oberkante einer Karte ist hell ein weißer Pixel, dunkel ein gedämpfter.

**Getönter Grund, Inhaltsflächen aus Papier.** Der Grund der Anwendung ist leicht getönt, Karten und Panels tragen die Papierfläche (`background.paper`) — nur so haben die Schattenebenen etwas, wogegen sie wirken. Umgesetzt in Issue #713: Der Grund ist `APP_BACKGROUND` — eine getönte Grundfläche und zwei radiale Verläufe aus dem Eis an den oberen Ecken, in beiden Erscheinungsbildern aus deren eigenen Tönen. Er liegt auf einer fixierten eigenen Schicht (`body::before`) und gilt damit für die ganze Anwendung, auch für die Anmeldeseiten außerhalb der Shell. **Nicht** über `background-attachment: fixed`: iOS Safari ignoriert das und fällt auf `scroll` zurück.

---

## ♿ Kontrast

**WCAG AA ist das Mindestmaß: 4,5:1 für Fließtext**, 3:1 für großen Text und für bedeutungstragende Grafikelemente — **in beiden Erscheinungsbildern** (AK 14). Eine Farbkombination, die das verfehlt, ist ein Fehler — auch wenn sie gefällt. Der Kontrast wird gegen die Fläche gerechnet, auf der der Text tatsächlich steht, nicht gegen Weiß aus Gewohnheit.

Gerechnet wird mit [`frontend/src/lib/kontrast.ts`](frontend/src/lib/kontrast.ts), der einzigen Stelle dieser Rechnung. Die Tabelle über beide Erscheinungsbilder steht in [`frontend/src/theme.test.ts`](frontend/src/theme.test.ts): Fließ- und Sekundärtext auf Papier, Grund, Eis und Code-Grund, Primärfarbe, Kopfleiste, Nachtlauf-Zustände, dunkle Status- und Vorhaben-Farben, gefüllte und getönte Meldungen. Wo der helle Bestand die Schwelle noch verfehlt, steht das unter [Offene Abweichungen](#offene-abweichungen).

---

## 🧭 Zustandsformen, Dichte, Bewegung

Zustände sind an **Form** erkennbar, nicht allein an Farbe (AK 4–8). Die Bausteine dafür liegen an je einer Stelle, damit sie überall dasselbe bedeuten:

- **Status an der Kante:** links an Karte und Listenzeile, oben an der Spalte (`STATUS_EDGE_WIDTH`, [`boardSurfaceSx.ts`](frontend/src/components/boardSurfaceSx.ts)).
- **Belastungsgrenze:** ein Auslastungsbalken unter dem Spaltenkopf, der bei erreichter Grenze über die volle Breite läuft, höher wird und eine Fehlerkante trägt; der Text `n/Grenze` bleibt stehen. Der Spaltenkopf selbst wird nicht eingefärbt — er ist im Struktur-Editiermodus ein Bedienelement (AK 2).
- **Ziehen:** Die gezogene Karte oder Zeile bleibt als Platzhalter an ihrer Stelle (zurückgenommen, gestrichelt, Inhalt unsichtbar bei gleicher Höhe, `PLATZHALTER_SX`); die Stelle, an der sie landen würde, trägt die Ablagefläche (gestrichelter Rahmen in der Primärfarbe, `ablageflaecheSx`). Board, Ideen-Board und Listenansicht nutzen dieselben Bausteine.
- **Dichte:** Echte Tabellen (`DataTable`) tragen die kleine Dichtestufe zentral über `MuiTableCell`. Die Listenansicht ist keine MUI-Tabelle und setzt ihre Dichte selbst; auf 1440 × 900 zeigt sie 20 statt vorher 13 Zeilen (#957). Titel werden nicht abgeschnitten, der Auszug bleibt einzeilig.
- **Kennzahlen:** Jede nennt ihren Zeitraum und ihre Datenbasis; eine Zahl ohne Datenbasis erscheint als „keine Messung" bzw. als eigener Hinweis, nie als Null (AK 11).
- **Bewegung reduzieren** (AK 16): eine zentrale Regel im `MuiCssBaseline` setzt unter `prefers-reduced-motion: reduce` Übergangs- und Animationsdauer auf null. `theme.transitions` bleibt unberührt; eigene Vorbehalte je Fläche gibt es nicht.
- **Tastaturfokus** (AK 15): ein sichtbarer Ring von 2 px in der Primärfarbe an `:focus-visible` und an MUIs `Mui-focusVisible`. Eingabefelder zeigen den Fokus an ihrer Rahmenlinie. Die Shell beginnt mit einer Sprungmarke zum Inhalt.
- **Mindestbreite** (AK 19, E5): 768 px sind vollständig bedienbar. Unterhalb von 900 px (`md`) liegt die Navigation in einer Schublade hinter einer Schaltfläche, Dialoge sitzen mittig, und das Board weist auf das waagerechte Rollen der Spalten hin.

---

## 🖨️ Ausdruck

**Bewusst schlicht und immer hell** (Fachplan-Frage 6, E6). Eine `@media print`-Regel im `MuiCssBaseline` nimmt den getönten Grund, alle Verläufe und alle Schatten heraus und setzt sämtliche Variablen auf das helle Erscheinungsbild zurück — auch wenn das Betriebssystem dunkel steht. Tiefe und Tönung tragen auf Papier keine Information und kosten Farbe; ein dunkler Grund auf Papier ist unlesbar.

---

## 🔢 Weitere Tokens

**Einzige Wertequelle für alle übrigen Design-Tokens ist [`frontend/src/theme.ts`](frontend/src/theme.ts).** Dort stehen unter anderem `STATUS_EDGE_WIDTH`, `EPIC_EDGE_WIDTH`, `SURFACE_TINT`, `CODE_BG`, `APP_BACKGROUND`, die Schatten `CARD_SHADOW`, `CARD_SHADOW_HOVER` und `PANEL_SHADOW`, `TABELLENZIFFERN`, die Flächen der gefüllten Meldungen und `HELLE_VARIABLEN` — jeweils für beide Erscheinungsbilder. Diese Datei nennt ihre Werte **absichtlich nicht**: Zwei Wertequellen laufen auseinander, ohne dass ein Test es merkt.

Status- und Vorhaben-Farben sind eigene, bewusste Ausnahmen: Ihre Werte beider Erscheinungsbilder liegen in [`frontend/src/lib/statusColors.ts`](frontend/src/lib/statusColors.ts) und [`frontend/src/lib/epicMeta.ts`](frontend/src/lib/epicMeta.ts); `theme.ts` legt sie als Variablen an. Die getönte Fläche eines Vorhaben-Abzeichens ist ein eigener Wert je Palettenplatz, keine Rechnung auf dem Farbton.

---

## 🗺️ Ansicht → Regel

Zu jeder darstellenden Route aus [`frontend/src/App.tsx`](frontend/src/App.tsx) steht hier, welchen Regeln sie folgt (AK 3, E20). Die reine Weiterleitung `/boards/:boardId/epics` stellt nichts dar und fehlt deshalb. Alle Ansichten folgen zusätzlich den allgemeinen Regeln oben — Palette, Erscheinungsbilder, Typografie, Radien, Tiefe, Kontrast, Bewegung, Fokus, Ausdruck. Abgenommen wird je Ansicht mit einem Bildschirmfoto in beiden Erscheinungsbildern gegen diese Tabelle (E7). `frontend/src/designQuelle.test.ts` hält die Tabelle gegen die Routen aus `App.tsx`.

| Route | Ansicht | Regel |
|---|---|---|
| `/login` | Anmelden | Panel: Auth-Karte auf getöntem Grund, Akzentkante links; Meldungen in Zustandsfarben |
| `/signup` | Registrieren | Panel: Auth-Karte; Meldungen in Zustandsfarben |
| `/verify` | E-Mail bestätigen | Panel: Auth-Karte; Meldungen in Zustandsfarben |
| `/forgot` | Passwort vergessen | Panel: Auth-Karte; Meldungen in Zustandsfarben |
| `/reset` | Passwort neu setzen | Panel: Auth-Karte; Meldungen in Zustandsfarben |
| `/` | Projekte | Panel: Flächen mit Haarlinie und Hover-Schatten; Shell |
| `/projects/:projectId` | Boards eines Projekts | Panel: Flächen mit Haarlinie; Tabellenziffern für die Spaltenzahl; Shell |
| `/projects/:projectId/ideas` | Ideen-Board | Zustandsformen: Status an der Kante, Platzhalter und Ablagefläche beim Ziehen; Shell |
| `/projects/:projectId/members` | Mitglieder | Dichte: Datentabelle mit Tabellenziffern; Shell |
| `/projects/:projectId/nachtlauf` | Nachtlauf-Auswertung | **Ausnahme Nachtlauf** (eigener Abschnitt), auch im dunklen Erscheinungsbild hell; Shell und Kartendialog folgen Panel |
| `/boards/:boardId` | Board | Zustandsformen: Status oben an der Spalte und links an der Karte, Auslastungsbalken, Platzhalter und Ablagefläche; Rollhinweis unter 900 px; Kartenblatt in drei Blöcken; Shell |
| `/boards/:boardId/list` | Listenansicht | Dichte der Liste, Nummernspalte in Tabellenziffern, Status an der linken Kante, Platzhalter und Ablagefläche beim Umsortieren; Kartenblatt in drei Blöcken; Shell |
| `/boards/:boardId/vorhaben` | Vorhaben | Panel: Kacheln mit Radius und Schatten, Vorhaben-Abzeichen mit eigenem Tint, Fortschritt in Tabellenziffern; Shell |
| `/boards/:boardId/dashboard` | Kennzahlen | Stand der letzten Nacht oben; Kennzahlen mit Zeitraum und Datenbasis, Kacheln in Tabellenziffern, Hero-Zahl ohne; Leerwerte als Hinweis; Shell |
| `/admin` | Plattform-Administration | Dichte: Datentabelle mit Tabellenziffern; Shell |
| `/admin/bootstrap` | Ersten Admin festlegen | Panel: Formular; Meldungen in Zustandsfarben; Shell |
| `/roles` | Rollen und Rechte | Panel: Tabelle mit Haarlinien; Shell |
| `/profil` | Profil | Panel: Formular; Meldungen in Zustandsfarben; Shell |
| `/administration` | Administration | Panel: Flächen und Tokenliste; Meldungen in Zustandsfarben; Shell |
| `/invitations/accept` | Einladung annehmen | Panel: Meldungen in Zustandsfarben; Shell |

**Shell** heißt: Kopfleiste und Navigation in beiden Erscheinungsbildern, Sprungmarke zum Inhalt, sichtbarer Fokus an jedem Navigationsziel, Schublade unterhalb von 900 px. **Kartenblatt in drei Blöcken** heißt: Der Kartendialog gliedert sich in „Beschreibung und Details", „Zuordnung" und „Verlauf", jeder Block mit eigener Fläche und Haarlinie auf getöntem Grund (AK 13).

---

## 🌙 Ausnahme: Nachtlauf-Auswertung

**Genau eine Seite weicht bewusst von dieser Datei ab**, und zwar in der Designsprache *und* in der Typografie: die Nachtlauf-Auswertung unter `/projects/:id/nachtlauf`, umgesetzt in [`frontend/src/pages/NightRunPage.tsx`](frontend/src/pages/NightRunPage.tsx) und den Bausteinen unter [`frontend/src/components/nachtlauf/`](frontend/src/components/nachtlauf/).

**Die Ausnahme gilt nur für den Inhaltsbereich dieser einen Seite.** Nicht für AppBar und Drawer der `AppShell`, die sie umgeben, und **nicht für den Kartendialog**, der von ihr aus geöffnet wird — der gehört zur übrigen Anwendung und trägt „Panel“. Das Entwurfs-Theme liegt als verschachtelter `ThemeProvider` über dem Inhaltsbereich, nicht als globale CSS-Regel; der Dialog steht außerhalb dieses Teilbaums.

**Die Ausnahme gilt auch im dunklen Erscheinungsbild: Der Inhaltsbereich bleibt hell** (Plan #932, E2), während Shell und Kartendialog dunkel werden — der Preis der Ausnahme, sichtbar gewollt. **Das geschieht nicht von selbst**, weil die Variablen des Leitstands an `:root` hängen und unabhängig vom verschachtelten Provider umschalten. Hergestellt wird es an zwei Stellen (#954): Der Wurzelknoten der Seite (`NACHTLAUF_WURZEL_SX`) setzt alle Variablen für seinen Teilbaum auf die Hellwerte (`HELLE_VARIABLEN`) und malt den Grund des Entwurfs selbst, statt ihn aus `body::before` zu beziehen; und `nachtlaufTheme` übernimmt die Komponenten-Vorgaben des Leitstands mit aufgelösten Hellwerten statt mit Variablen (`lib/variablenAufloesen.ts`), damit auch Menüs und Popover im Portal hell bleiben. Nachgewiesen in `nachtlaufDesign.test.ts` und `NightRunPage.test.tsx`.

**Warum sie abweicht:** Sie ist das Vorführstück des Leitstands und trägt eine eigene, vom PO abgenommene Vorlage — [`docs/mockup-leitstand-nachtlauf.html`](docs/mockup-leitstand-nachtlauf.html). Abgenommen wird sie durch Nebeneinanderlegen von Entwurf und Bildschirmfoto; Tests und Gates können Aussehen nicht prüfen, und genau daran ist die erste Umsetzung vorbeigelaufen.

**Wo ihre Werte liegen:** [`frontend/src/nachtlaufDesign.ts`](frontend/src/nachtlaufDesign.ts) — Farben, Schriftfamilien, Größen und Abstände des Entwurfs sowie das Theme, das die Seite überlagert. Die Datei ist in `COLOR_SOURCES` von [`frontend/src/designGuard.test.ts`](frontend/src/designGuard.test.ts) geführt und wie `theme.ts` von der Coverage ausgenommen. [`frontend/src/theme.ts`](frontend/src/theme.ts) bleibt die Wertequelle der Designsprache; die Nachtlauf-Ausnahme liest von dort nur die hellen Variablen und die Komponenten-Vorgaben. `palette.nightRun` trägt unverändert die beiden Lauf-Arten, die in „Panel“ dargestellt werden.

**Was außer den Farben abweicht:** Die Seite trägt **drei Schriftfamilien mit sieben Gewichten** — Chivo (600, 800), IBM Plex Sans (400, 500, 600) und IBM Plex Mono (400, 600) — und damit nicht die Zwei-Gewichte-Regel dieser Datei. Sie werden über `@fontsource` mit der Anwendung ausgeliefert und im lazy geladenen Route-Chunk der Seite geladen, nicht in `main.tsx`: Eine Instanz ohne Internetzugang zeigt dasselbe Schriftbild, und die übrigen Seiten laden die sieben Schnitte nicht mit.

**Der Kontrastanspruch gilt hier unverändert**: 4,5:1 für Fließtext, 3:1 für großen Text und für bedeutungstragende Flächen. Eine Kombination unter AA ist auch in der Ausnahme ein Fehler — vier Töne der Vorlage haben ihn verfehlt und sind abgedunkelt worden. Nachgerechnet wird in [`frontend/src/nachtlaufDesign.test.ts`](frontend/src/nachtlaufDesign.test.ts), die Abweichungen sind an den betroffenen Konstanten vermerkt.

**Diese Ausnahme gilt nirgendwo sonst und ist kein Vorbild.** Wer eine weitere Seite anders gestalten will, braucht dafür eine eigene Entscheidung des PO und einen eigenen Abschnitt hier — nicht den Verweis auf diesen.

---

## ⚠️ Offene Abweichungen

Stellen, an denen der Bestand dieser Datei noch nicht folgt. Sie stehen hier, damit keine Regel etwas verlangt, das die Oberfläche stillschweigend nicht zeigt — und jede ist eine **eigene, offene Aufgabe**, nicht Teil des Vorhabens „Oberfläche als Leitstand“ (Plan #932, E21).

**Zwei-Gewichte-Regel.** Das Kommando

```bash
grep -rn "fontWeight: 5\|fontWeight: 6" frontend/src --include=*.tsx | grep -v test | wc -l
```

findet **36** Stellen mit `fontWeight: 500` oder `600`. Davon liegen 11 unter `frontend/src/components/nachtlauf/` und gehören zur Nachtlauf-Ausnahme, die eigene Gewichte trägt; die übrigen 25 verletzen die Regel (u. a. `CardDetailModal.tsx`, `RolesPage.tsx`, `DashboardPage.tsx`, `BoardListPage.tsx`). `frontend/src/designQuelle.test.ts` hält die Zahl hier gegen den Quelltext: Wer eine Stelle bereinigt oder hinzufügt, muss sie hier nachführen.

**Kontrast im hellen Bestand.** Die dunklen Werte sind neu gewählt und halten AA; im hellen Erscheinungsbild verfehlen unveränderte Bestandstöne die Schwelle noch (nachgerechnet in #951 und #952):

- Schrift auf gefüllten Primärschaltflächen: Weiß auf `#2F8C97`, 3,95:1.
- Statuspunkte unter 3:1 gegen Papier, Grund und Eis: In Arbeit, Backlog, Neutral, Archiv.
- Statustext auf seiner Fläche unter 4,5:1: Neutral und Archiv.
- Kürzel eines Vorhaben-Abzeichens auf seinem Tint unter 4,5:1: drei der acht Palettenplätze.

---

## 📜 Historie

Die Designsprache heißt **„Panel“**: runder, zwei Ebenen Tiefe, Fläche nicht durchgehend weiß.

Davor stand kurzzeitig eine Variante **„Kante“** (Radius 4 px, kein Ruheschatten, weiß auf weiß), geschnitten in Plandokument #617. Ihr Kernsatz „die Fläche ist weiß“ stammte wörtlich aus einer Publikationsregel für Veröffentlichungen — auf eine Arbeitsanwendung übertragen, wo sie nie hingehörte. Am 2026-08-31 wurde auf „Panel“ gewechselt.

Seit dem Vorhaben „Oberfläche als Leitstand“ (Fachplan #925, Plan #932) trägt „Panel“ zwei Erscheinungsbilder, Zustandsformen, eine dichtere Liste, einen schlichten Ausdruck und die Tabelle „Ansicht → Regel“.

**#617 gilt nicht mehr.** Wer es findet, liest ein überholtes Dokument. Maßgeblich sind diese Datei und `theme.ts`.
