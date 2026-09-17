# CLAUDE-design.md — Designsprache des Leitstands: Kupferwarte

Diese Datei ist die Designquelle der Anwendung **kanban-kit**. Sie beschreibt, **was** die Oberfläche trägt: Vorlage, Erscheinungsbilder, Palette, Schrift, Radien, Tiefe, Kontrast, Rahmen und Zustandsformen. **Wie** diese Werte im Code angewendet werden — Theme-zentral, über die `sx`-Prop, keine hartcodierten Werte — regelt [CLAUDE-react.md](CLAUDE-react.md).

**Geltungsbereich:** ausschließlich der Leitstand, also diese Anwendung. Regeln für Veröffentlichungen (Blog, LinkedIn, Whitepaper, Website) und für Präsentationen gelten hier **nicht**.

---

## 📌 Vorlage und Abnahme

**Die Vorlage ist verbindlich:** [`docs/entwurf-leitstand.html`](docs/entwurf-leitstand.html) — „Kupferwarte", entstanden in der Designsession am 2026-09-16 und von Manne als Ziel der Oberfläche entschieden. Sie gilt für **Aussehen und Aufbau**: Farben, Schriften, Radien, Tiefe, Rahmen und die Gestalt jeder Ansicht (Leitstand, Board, Liste, Karte). Was die Vorlage zeigt, ist das Ziel; wo diese Datei und die Vorlage auseinandergehen, gilt die Vorlage, und diese Datei wird nachgeführt.

**Bindend sind** die Vorlage, diese Datei und [`frontend/src/theme.ts`](frontend/src/theme.ts) als einzige Wertequelle im Code. Ein Plan oder ein Arbeitspaket entscheidet keine Gestaltungsfrage gegen die Vorlage — eine Abweichung wird Manne vorgelegt.

**Abgenommen wird visuell:** je Ansicht ein Bildschirmfoto bei 1440 × 900 neben der Vorlage, im hellen und im dunklen Erscheinungsbild. Tests und Gates sichern Werte, Kontrast und Verhalten; ob eine Ansicht aussieht wie die Vorlage, sagen sie nicht.

**Umgesetzt wird 1:1, auch die neuen Ideen der Vorlage** (Entscheidung Manne, 2026-09-16): Bedienelemente, die kanban-kit heute nicht kann — etwa die Filter „Meine" und „Überfällig", die Gruppierung der Liste nach Vorhaben —, werden als echte Funktion gebaut, sofern die Daten dafür vorliegen. Wofür es keine Daten gibt (z. B. Kostenbudget, Stufen der laufenden Kette), erscheint nichts statt einer erfundenen Zahl; das steht als offener Punkt im Abschlussbericht. Nach der Umsetzung wird gemeinsam angesehen und geändert.

**Eine bewusste Abweichung von der Vorlage:** Die Ansichtswahl oben (Reiter Leitstand · Board · Liste · Karte) entfällt. Die Ansichten stehen **links in der Schiene**, wie in der Anwendung gewohnt (Entscheidung Manne, 2026-09-16).

**Eine zweite bewusste Abweichung:** Die Platte „Liegengeblieben" des Leitstands (Entwurf Z. 713–722, 1655–1680) — Karten, die über sieben Tage in einer Spalte lagen — entfällt ersatzlos. Die Kennzahl wird nicht gebraucht (Entscheidung Manne, 2026-09-17); die rechte Spalte des Rumpfs trägt danach „Abbruchgründe" und „Vorhaben". Berechnung und API-Feld `outliers` im Backend bleiben vorerst bestehen, werden im Leitstand aber nicht mehr dargestellt.

**Die Anwendung ist der KI-Leitstand 2.0, kein Kanban-Board mit Zusatzseite** (Manne, 2026-09-16). Das Board bleibt ein wichtiger Bestandteil; der Kern ist der Leitstand mit den detaillierten, verdichteten Informationen über die KI-Arbeit (Läufe, Durchsatz, Verbrauch, Abbrüche, Vorhaben). Kennzahlen des bisherigen Dashboards wie die **Verweildauer je Spalte** sind dagegen unwichtig und gehören nicht in den Leitstand.

---

## 🌗 Erscheinungsbilder

**Zwei Erscheinungsbilder, beide aus der Vorlage, kein Schalter** (Fachplan #925, AK 17). Die Vorlage führt helle Werte an `:root` und dunkle unter `prefers-color-scheme: dark`; genau so trägt es die Anwendung: `theme.ts` nutzt MUI-CSS-Variablen mit `cssVariables: { colorSchemeSelector: 'media', cssVarPrefix: 'mb' }`, die Dunkelwerte stehen damit unter `@media (prefers-color-scheme: dark)`. Es gibt keinen Zustand, keine Persistenz und kein Bedienelement, über das jemand das Erscheinungsbild wählen könnte — das Einstellen ist ein Nicht-Ziel des Fachplans. Den Umschalter `data-theme`, den die Vorlage zum Vorführen trägt, übernimmt die Anwendung nicht.

**Tokens sind Verweise, keine Werte.** Die Konstanten aus `theme.ts` tragen `var(--mb-…)`; `statusColors()` und `epicColor()`/`epicTint()` liefern ebenfalls Variablen-Verweise. `theme.palette.*` liefert nur den hellen Wert — eine Lesung beim Modulstart schaltet nicht um; dort gilt `theme.vars.palette.*`.

---

## 🎨 Palette

Die Rollen und Werte der Vorlage (Entwurf Z. 10–150). Die **Leitfarbe ist Kupfer**; die Melder tragen ausschließlich Zustände und werden nie als Akzent verwendet.

| Rolle | Hell | Dunkel | Verwendung |
|---|---|---|---|
| Grund | `#E7E9ED` | `#0D1014` | Grund der Anwendung (`background.default`) |
| Grund tief | `#D8DBE2` | `#090B0E` | oberes Ende der Schiene |
| Nut | `#D5D9E0` | `#080A0D` | eingelassene Flächen: Schiene, Suche, Filtergruppen, Spalten, Zähler |
| Platte | `#FDFDFE` | `#171B22` | Inhaltsflächen: Karten, Kacheln, Platten (`background.paper`) |
| Platte Fuß | `#F2F4F7` | `#12151B` | unteres Ende eines Tastenverlaufs |
| Platte hoch | `#FFFFFF` | `#1E242D` | abgehobene Flächen, oberes Ende eines Tastenverlaufs |
| Rand | `#CDD2DA` | `#262C36` | Haarlinien (`divider`) |
| Rand stark | `#B7BEC9` | `#333B47` | betonte Linien, Tastenkappen |
| Kante | `rgba(255,255,255,.9)` | `rgba(255,255,255,.075)` | Lichtkante an der Oberkante erhabener Flächen |
| Text | `#14181E` | `#E7EAEF` | Fließtext (`text.primary`) |
| Text matt | `#58606C` | `#98A1AE` | Sekundärtext (`text.secondary`), Navigation |
| Text schwach | `#868E9B` | `#69717E` | Etiketten, Zähler, Hinweise — siehe [Kontrast](#kontrast) |
| Kupfer | `#A85F2C` | `#D08A52` | Leitfarbe (`primary`): aktive Navigation, Primärtaste, Fokusring, Füllungen |
| Kupfer hell | `#C2743C` | `#E3A26C` | oberes Ende von Kupferverläufen |
| Kupfer-Schimmer | `rgba(168,95,44,.16)` | `rgba(208,138,82,.18)` | Schimmer im Grund, Schatten der Kupfertaste |
| Grün | `#2F8F4E` | `#46C46F` | Melder: erfolgreich, fertig |
| Bernstein | `#B07C15` | `#E0AE49` | Melder: Warnung, Grenze erreicht |
| Zinnober | `#C8393E` | `#F0575C` | Melder: gescheitert, überfällig |
| Stahl | `#2F6FC9` | `#5B96F0` | Melder: laufend, Information |
| Grau | `#8A929E` | `#6E7681` | Melder: nicht bearbeitet |

**Grund der Anwendung:** der Grund mit einem Kupfer-Schimmer oben links (`radial-gradient(1100px 600px at 18% -8%, Kupfer-Schimmer, transparent 62%)`, Entwurf Z. 152–160), in beiden Erscheinungsbildern.

---

## ✒️ Typografie

| Rolle | Schrift | Einsatz |
|---|---|---|
| Titel, Anzeige | **Archivo** (variable Breite, `font-stretch` 110–118 %) | Überschriften, Markenname, Pfad, Etiketten, Spaltennamen, Plattentitel |
| Fließtext | **IBM Plex Sans**, 14 px, Zeilenhöhe 1,5 | alles Lesbare |
| Zahlen und Kennungen | **IBM Plex Mono** mit `tabular-nums` | Kartennummern, Dauern, Kosten, Token, Commit-Hashes, Tastenkürzel, Kennzahlen |

- **Gewichte wie in der Vorlage:** 400, 500 (Navigation, Kartentitel), 600 (Tasten, Etiketten, Pfad, Plattentitel), 700 (Markenname, große Zahlen).
- **Etikett** (`.etikett`, Entwurf Z. 185–194): Archivo, 10 px, 600, Versalien, Laufweite 0,14 em, Farbe „Text schwach".
- Alle Schriften werden **offline mit der Anwendung ausgeliefert** (`@fontsource`); eine Instanz ohne Internetzugang zeigt dasselbe Schriftbild.
- **Eine große Einzelzahl** trägt keine Tabellenziffern, Zahlen untereinander immer (AK 10).

---

## 📐 Radien

| Ebene | Radius | Token |
|---|---|---|
| Platte, Kachel, Laufband, Spalte | 14 px | `PANEL_RADIUS` (`--r-gross`) |
| Karte, Navigationseintrag | 10 px | `CARD_RADIUS` (`--r-mittel`) |
| Bedienelement, Fokusring | 6 px | `shape.borderRadius` (`--r-klein`) |

---

## 🌓 Tiefe

**Vier Stufen: Nut < Grund < Platte < Abgehoben** (Entwurf Z. 6–8). Eingelassenes liegt als Nut im Grund (Schiene, Suche, Filtergruppen, Spalten, Zähler); Inhalte liegen als Platte darauf; was gerade bewegt wird oder über allem schwebt (gezogene Karte, Massenleiste), ist abgehoben. Tasten sind kleine erhabene Flächen mit Lichtkante, die beim Drücken zur Nut werden.

| Token | Rolle |
|---|---|
| `--schatten-nute` | Innenschatten eingelassener Flächen |
| `--schatten-platte` | Lichtkante plus zwei Schattenebenen der Platte |
| `--schatten-hoch` | abgehobene Fläche, weiter geöffnet |
| `--schatten-taste` | Lichtkante plus kurzer Schatten einer Taste |

Die Schattenfarbe folgt der Vorlage: hell eine dunkle Blaugrau-Tinte `rgba(18,24,33,…)`, dunkel Schwarz mit hoher Deckkraft — auf fast schwarzem Grund trägt nur dieser Schatten eine sichtbare Stufe.

---

## ♿ Kontrast

**WCAG AA ist das Mindestmaß: 4,5:1 für Text**, 3:1 für großen Text und bedeutungstragende Grafikelemente — in beiden Erscheinungsbildern (AK 14). Gerechnet wird gegen die Fläche, auf der das Element tatsächlich steht, mit [`frontend/src/lib/kontrast.ts`](frontend/src/lib/kontrast.ts); die Tabelle über beide Erscheinungsbilder steht in [`frontend/src/theme.test.ts`](frontend/src/theme.test.ts).

**Die Vorlage verfehlt AA an wenigen Stellen.** Dort wird der Ton **minimal im selben Farbton** nachgedunkelt bzw. aufgehellt, bis die Schwelle hält; die Abweichung steht an der Konstante in `theme.ts` und im Test — die Schwelle wird nie gesenkt. Nachgerechnet am 2026-09-16:

- **Text schwach als Schrift:** hell 2,3–3,3:1 (auf Nut, Grund, Platte), dunkel 3,2–4,0:1.
- **Text matt auf der Nut, hell:** 4,49:1.
- **Weiße Schrift auf Kupfer, dunkel:** 2,8:1 — die Schrift auf der Kupfertaste ist dunkel die Grundtinte.
- **Melder auf der Nut, hell:** Grün 2,9, Bernstein 2,6, Grau 2,2:1 — als Füllung auf der Nut nachgedunkelt oder auf eine Platte gesetzt.

---

## 🧱 Rahmen

Vorlage: Entwurf CSS Z. 196–389, HTML Z. 1101–1199.

- **Warte:** zweispaltig, links die Schiene (224 px), rechts der Inhalt.
- **Schiene:** eingelassene Nut mit Verlauf von „Grund tief" nach „Nut", Innenschatten, Haarlinie rechts. Oben die **Marke** (Kupfer-Mal mit drei Balken, Name, Version); darunter **Navigationsblöcke** mit Etikett-Titel, immer offen: **Projekt ‹Name›** (Leitstand, Board, Liste, Vorhaben, Ideen, Nachtläufe), **Übersicht** (Projekte, Boards — nur wenn es etwas zu wählen gibt; der Entwurf kennt den Block nicht, er hält den Weg zurück zur Auswahl) und **Verwaltung** (Mitglieder, Rollen & Rechte, Admin); unten der **Fuß** (Administration, Dokumentation, Einklappen). Die Schiene ist der einzige Ort, an dem man zwischen den Ansichten wechselt. Ein Eintrag ist ein echter Link mit Symbol aus der Vorlage und Beschriftung; der aktive Eintrag (`aria-current="page"`, der längste passende Pfad) ist eine erhabene Taste mit kupfernem Symbol. Zahlen an Einträgen und der Hinweis auf die letzte Kette erscheinen erst, wenn die Shell diese Daten kennt. Eingeklappt (64 px) bleiben nur die Symbole.
- **Kopf:** klebt oben, leicht getönt mit Weichzeichner; **Pfad** (Projekt / Board, in Archivo, beide verlinkt), **Suche** als Nut mit der Tastenkappe des echten Kürzels `/` (die Vorlage zeigt `⌘K`; Modifikator-Kürzel überlässt die Anwendung dem Browser), **Taste** „Board" für den Board-Wechsel (Kürzel `b`), **Nutzer** als rundes Mal mit Kürzel, das ein Menü mit „Profil bearbeiten" und „Abmelden" öffnet. Die Kupfertaste für die Hauptaktion der Ansicht bringt das jeweilige Ansichtspaket mit.
- **Keine Ansichtswahl oben.** Abweichung von der Vorlage (siehe [Vorlage und Abnahme](#vorlage-und-abnahme)): Leitstand, Board und Liste wählt man in der Schiene; die Karte öffnet sich wie bisher aus Board, Liste oder Leitstand.
- **Bühne:** Innenabstand 22/26/44 px, Abstand zwischen Bereichen 20 px.
- **Fokusring:** 2 px Kupfer mit 2 px Abstand an jedem Tastaturziel; Eingabefelder zeigen den Fokus an ihrer Rahmenlinie.
- **Mindestbreite** (AK 19): 768 px vollständig bedienbar; unterhalb von 900 px liegt die Schiene hinter einer Schaltfläche.

---

## 🧭 Ansichten und Zustandsformen

Die Gestalt jeder Ansicht folgt der Vorlage. Zustände sind an **Form** erkennbar, nicht allein an Farbe (AK 4–8).

- **Leitstand** (Entwurf CSS Z. 390–722, 977–1017; HTML Z. 1200–1678) — die Hauptansicht der Anwendung, nicht eine Statistikseite neben dem Board. Laufband des jüngsten Laufs mit LED, Stufen, Zeit und Kosten; Kennzahl-Kacheln mit Wert, Einheit, Sparkline, Delta und Basis; Verbrauch mit Zeitraum-Wahl und Stapelbalken; Platten „Letzter Lauf", „Durchsatz", „Abbruchgründe", „Vorhaben" (die Platte „Liegengeblieben" der Vorlage entfällt, siehe [Vorlage und Abnahme](#vorlage-und-abnahme)).
- **Board** (CSS Z. 723–875; HTML Z. 1679–1864): Werkzeugleiste als Platte mit den Filtern „Alle Karten · Meine · Überfällig" (Zahl in Zinnober), Vorhaben-Filter und Dichte „normal · kompakt"; Spalten als Nut im Grund mit LED in der Statusfarbe, Name in Archivo und Zahl in Plex Mono; die **Belastungsgrenze** als Segmentskala neben dem Kopf (belegte Plätze kupfern, bei erreichter Grenze das letzte bernstein, bernsteinfarbene Haarlinie der Spalte); Karten als Platte ohne farbige Kante: Kopf mit Nummer (Plex Mono) und Zuständigen-Malen, Titel, Labels, Fuß mit Vorhaben-Schild und Frist (überfällig zinnober). Die gezogene Karte hinterlässt ihren Platz als Vertiefung.
- **Liste** (CSS Z. 876–976; HTML Z. 1865–2054): Werkzeugleiste mit Status-, Überfällig-, Zuständig- und Label-Filter und „Gruppieren: Vorhaben · keine"; die Tafel als Platte mit erhabener, mitlaufender Kopfzeile (Etiketten, sortierte Spalte kupfern); **standardmäßig nach Vorhaben gruppiert**, jede Gruppe als Nut mit Mal, Name und Fortschritt, Karten ohne Vorhaben am Ende; Status als Plakette mit LED. Umordnen per Ziehen nur ungruppiert.
- **Wähler in der Werkzeugleiste** (`.waehler`, CSS Z. 833–842; HTML Z. 1687–1690) — Vorhaben-Filter des Boards und Zuständig-Filter der Liste: **keine Beschriftung über dem Feld**. Die Benennung trägt der Wert selbst — „Vorhaben: alle", „Vorhaben: ‹Kürzel› – ‹Titel›", „Zuständig: alle", „Zuständig: ‹Name›" —, sichtbar wie in der Vorlage, und auch ein gewählter Wert sagt so, wonach gefiltert wird. Maße wie die Filtertasten daneben (Schrift 11,5 px, Innenabstand 4/9 px, Radius 7 px), damit die Leiste in einer Linie steht. Den **zugänglichen Namen** („Vorhaben-Filter", „Zuständig") behält das Feld: Ohne sichtbare Beschriftung ist er der einzige (#986).
- **Karte** (CSS Z. 1018–1078; HTML Z. 2055–2229): Kartendialog als Instrumententafel auf dem Grund — links das Blatt „Beschreibung und Details" und darunter „Verlauf", rechts „Zuordnung" als Felder-Platte; Nummer in Kupfer, Titel in Archivo. Die drei Blöcke aus #958 bleiben benannte Bereiche.
- **Herkunftsbaum** (im Vorhaben-Dialog, [`frontend/src/components/DerivationTree.tsx`](frontend/src/components/DerivationTree.tsx)): Jede Zeile beginnt mit einer **festen Hakenspalte ganz links**, unabhängig von der Einrückung — die Einrückung nach der Tiefe beginnt erst dahinter, damit die Spalte über alle Ebenen bündig steht. Ein grüner Haken (Melder Grün) steht dort bei einer **erledigten Karte**, bei einem **`[Fachlich]`**, sobald mindestens ein `[Plan]` direkt darunter hängt, und bei einem **`[Plan]`**, sobald mindestens ein Arbeitspaket direkt darunter hängt; sonst bleibt sie leer. Gezählt werden nur direkte Kinder. Der Haken trägt seine Aussage als Text für Screenreader — „erledigt" an der Karte, „abgearbeitet" an `[Fachlich]` und `[Plan]`; eine zusätzliche Marke „erledigt" am rechten Zeilenende gibt es nicht (#985).
- **Bewegung reduzieren** (AK 16): eine zentrale Regel setzt unter `prefers-reduced-motion: reduce` Übergangs- und Animationsdauern auf null, auch das Pulsieren der LED.
- **Ausdruck** (E6): schlicht und immer hell — ohne Grund, Verläufe und Schatten.

---

## 🔢 Weitere Tokens

**Einzige Wertequelle im Code ist [`frontend/src/theme.ts`](frontend/src/theme.ts)**, gespeist aus der Vorlage: Palette beider Erscheinungsbilder, Schatten, Radien, Grund, Schriften, `TABELLENZIFFERN`, Flächen der gefüllten Meldungen und `HELLE_VARIABLEN`. Status- und Vorhaben-Farben liegen in [`frontend/src/lib/statusColors.ts`](frontend/src/lib/statusColors.ts) und [`frontend/src/lib/epicMeta.ts`](frontend/src/lib/epicMeta.ts) und bilden auf die Melder und Schild-Töne der Vorlage ab. Diese Datei nennt außer der Palettentabelle der Vorlage keine Tokenwerte.

---

## 🗺️ Ansicht → Regel

Zu jeder darstellenden Route aus [`frontend/src/App.tsx`](frontend/src/App.tsx) steht hier, welcher Stelle der Vorlage sie folgt (AK 3). Die reinen Weiterleitungen `/boards/:boardId/epics` und `/boards/:boardId/dashboard` (auf den Leitstand) fehlen. Alle Ansichten tragen den Rahmen, die Palette, die Schrift und die Tiefe der Vorlage. „Umsetzung" nennt das Paket, das die Gestalt herstellt. `frontend/src/designQuelle.test.ts` hält die Tabelle gegen die Routen.

| Route | Ansicht | Regel | Umsetzung |
|---|---|---|---|
| `/login` | Anmelden | Rahmenlose Platte auf dem Grund der Vorlage; Kupfertaste; Meldungen in Melderfarben | #978 |
| `/signup` | Registrieren | wie Anmelden | #978 |
| `/verify` | E-Mail bestätigen | wie Anmelden | #978 |
| `/forgot` | Passwort vergessen | wie Anmelden | #978 |
| `/reset` | Passwort neu setzen | wie Anmelden | #978 |
| `/` | Projekte | Rahmen; Projekte als Platten | #978 |
| `/projects/:projectId` | Boards eines Projekts | Rahmen; Boards als Platten, Zahlen in Plex Mono | #978 |
| `/projects/:projectId/ideas` | Ideen | Rahmen; Nut-Zonen und Platten wie das Board | #978, #980 |
| `/projects/:projectId/members` | Mitglieder | Rahmen; Tabelle als Platte, Zahlen in Plex Mono | #978 |
| `/projects/:projectId/nachtlauf` | Nachtläufe | Verbrauchs-Bereich (Zeitraum-Sicht) nach `docs/mockup-nachtlauf-verbrauch.html` und Laufblöcke nach `docs/mockup-nachtlauf-lauf.html`, beide in Kupferwarte; der übrige Inhalt **Ausnahme Nachtlauf** (eigener Abschnitt); Rahmen der Vorlage | #978, #987, #988 |
| `/boards/:boardId` | Board | Board der Vorlage (Entwurf Z. 1679–1864) | #980 |
| `/boards/:boardId/list` | Liste | Liste der Vorlage, nach Vorhaben gruppiert (Entwurf Z. 1865–2054) | #980 |
| `/boards/:boardId/vorhaben` | Vorhaben | Rahmen; Vorhaben als Platten mit Fortschritt | #978 |
| `/boards/:boardId/leitstand` | Leitstand | Leitstand der Vorlage (Entwurf Z. 1200–1678); ersetzt die Kennzahlen-Ansicht, ohne Verweildauer je Spalte | #979 |
| `/admin` | Plattform-Administration | Rahmen; Tabelle als Platte | #978 |
| `/admin/bootstrap` | Ersten Admin festlegen | Rahmen; Formular als Platte | #978 |
| `/roles` | Rollen und Rechte | Rahmen; Tabelle als Platte | #978 |
| `/profil` | Profil | Rahmen; Formular als Platte | #978 |
| `/administration` | Administration | Rahmen; Platten | #978 |
| `/invitations/accept` | Einladung annehmen | Rahmen; Meldungen in Melderfarben | #978 |

---

## 🌙 Ausnahme: Nachtlauf-Auswertung

**Ein Rest der Nachtlauf-Auswertung unter `/projects/:id/nachtlauf` behält vorerst seine eigene, vom PO abgenommene Vorlage** [`docs/mockup-leitstand-nachtlauf.html`](docs/mockup-leitstand-nachtlauf.html), umgesetzt in [`frontend/src/pages/NightRunPage.tsx`](frontend/src/pages/NightRunPage.tsx) und der Wertequelle [`frontend/src/nachtlaufDesign.ts`](frontend/src/nachtlaufDesign.ts). Welcher Rest das noch ist, steht unten; ob auch er in Kupferwarte aufgeht, ist offen.

**Die Ausnahme gilt nur für den Inhaltsbereich dieser Seite** — Schiene, Kopf und Kartendialog folgen Kupferwarte. Das Entwurfs-Theme liegt als verschachtelter `ThemeProvider` über dem Inhaltsbereich.

**Der Verbrauchs-Bereich (Zeitraum-Sicht) steht seit #987 außerhalb der Ausnahme.** Er folgt Kupferwarte — Tokens aus [`frontend/src/theme.ts`](frontend/src/theme.ts), Bausteine aus [`frontend/src/components/leitstand/LeitstandBausteine.tsx`](frontend/src/components/leitstand/LeitstandBausteine.tsx) — und seine Vorlage ist [`docs/mockup-nachtlauf-verbrauch.html`](docs/mockup-nachtlauf-verbrauch.html), von Manne am 2026-09-17 abgenommen: Kopfzeile „Verbrauch" mit Zeitraum-Wahl, zwei Platten mit je vier Kacheln, darunter Nächte und Vorhaben. **Er folgt damit auch im Dunkeln dem Erscheinungsbild**, wie der Leitstand; hergestellt in [`frontend/src/components/nachtlauf/KupferwarteBereich.tsx`](frontend/src/components/nachtlauf/KupferwarteBereich.tsx), das Theme und Variablen (`ERSCHEINUNGSBILD_SX`) für seinen Teilbaum zurückstellt. **Die Nachtansicht darunter bleibt in der Ausnahme.**

**Die Laufblöcke stehen seit #988 ebenfalls außerhalb der Ausnahme.** Ihre Vorlage ist [`docs/mockup-nachtlauf-lauf.html`](docs/mockup-nachtlauf-lauf.html), von Manne am 2026-09-17 abgenommen: **jeder Lauf eine aufklappbare Platte** — Kopf mit Aufklapp-Pfeil, LED nach Ergebnis, Etikett „Nachtlauf · ‹Art›", Titel 15 px, Metazeile und rechts Zustand und Herkunft (zugeklappt zusätzlich die Kosten); aufgeklappt sechs eingelassene Instrumente (Kosten, Eingabe, Ausgabe, Cache-Quote, Dauer, Pakete) und die Vorgänge als kompakte Zeilen wie „Letzter Lauf" im Leitstand, deren Befund mit „Kopieren" an der Zeile aufklappt. Tokens aus [`frontend/src/theme.ts`](frontend/src/theme.ts), Bausteine aus [`LeitstandBausteine.tsx`](frontend/src/components/leitstand/LeitstandBausteine.tsx) (Platte, LED, Instrument, Klassenmarke, Taste) und den Laufbausteinen unter [`frontend/src/components/nachtlauf/`](frontend/src/components/nachtlauf/); sie stehen im [`KupferwarteBereich`](frontend/src/components/nachtlauf/KupferwarteBereich.tsx) und folgen damit auch im Dunkeln dem Erscheinungsbild.

**Nicht gemessen ist nicht 0.** Wo eine Zahl fehlt, steht in Instrument und Kostenspalte „—"; den Grund („nicht gemessen") trägt ein Text, der nur Vorlesewerkzeugen gilt (`NUR_LESER_SX`). Eine Null behauptete eine Messung, die es nicht gab.

**In der Ausnahme bleibt damit nur noch,** was außerhalb der Laufblöcke und außerhalb der Zeitraum-Sicht auf der Seite steht: Brotkrumenpfad, „Protokoll einlesen", die Meldungszeile und die Nachtansicht des Verbrauchs.

**Auch im dunklen Erscheinungsbild bleibt der Inhaltsbereich hell.** Hergestellt wird das am Wurzelknoten (`NACHTLAUF_WURZEL_SX` setzt `HELLE_VARIABLEN` und malt den Grund selbst) und in `nachtlaufTheme` (Komponenten-Vorgaben mit aufgelösten Hellwerten, `lib/variablenAufloesen.ts`); nachgewiesen in `nachtlaufDesign.test.ts` und `NightRunPage.test.tsx`.

**Schriften und Kontrast:** Die Seite trägt Chivo, IBM Plex Sans und IBM Plex Mono im lazy geladenen Route-Chunk; der Kontrastanspruch gilt unverändert und wird in `nachtlaufDesign.test.ts` nachgerechnet.

---

## 📜 Historie

- **bis 2026-08-31 „Kante"** (Plandokument #617): Radius 4 px, kein Ruheschatten, weiß auf weiß. Gilt nicht mehr.
- **2026-08-31 bis 2026-09-16 „Panel"**: Teal-Familie, Carlito, zwei Gewichte, zwei Tiefenebenen. Das Vorhaben „Oberfläche als Leitstand" (#925, Plan #932, Pakete #950–#961) brachte zwei Erscheinungsbilder, Tokens als Variablen, Kontrastrechner, Zustandsformen und Dichte — ließ aber „Panel" verbindlich und setzte die Vorlage deshalb nicht um.
- **seit 2026-09-16 „Kupferwarte"**: Die Vorlage `docs/entwurf-leitstand.html` ist verbindlich (Entscheidung Manne). Die Lehre daraus: Bei einem Gestaltungswechsel wird **zuerst** diese Datei umgestellt, dann geplant und umgesetzt — ein Plan folgt der Quelle, die im Repository als bindend markiert ist.
