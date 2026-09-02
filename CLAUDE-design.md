# CLAUDE-design.md — Designsprache des Leitstands

Diese Datei ist die Designquelle der Anwendung **kanban-kit**. Sie beschreibt, **was** die Oberfläche trägt: Palette, Font, Radien, Tiefe, Kontrast. **Wie** diese Werte im Code angewendet werden — Theme-zentral, über die `sx`-Prop, keine hartcodierten Werte — regelt [CLAUDE-react.md](CLAUDE-react.md).

**Geltungsbereich:** ausschließlich der Leitstand, also diese Anwendung. Regeln für Veröffentlichungen (Blog, LinkedIn, Whitepaper, Website) und für Präsentationen gelten hier **nicht** — sie stammen aus einem anderen Geltungsbereich und sind schon einmal fälschlich auf die Anwendung übertragen worden (siehe [Historie](#historie)).

---

## 🎨 Palette

Sieben Farben, übernommen aus der persönlichen Marke des Autors, die für Veröffentlichungen gilt. Ab hier sind es die Farben **des Leitstands**: Sie ändern sich mit dieser Anwendung und nicht mit jener Marke.

| Rolle | Wert | Verwendung |
|---|---|---|
| Teal (Primärfarbe) | `#2F8C97` | Primäraktionen, Akzente |
| Teal dunkel | `#1E5F68` | Sekundärfarbe, dunkle Akzente |
| Teal hell | `#5BABB5` | Fläche der Kopfleiste |
| Tinte | `#243539` | Fließtext (`text.primary`), Grundton aller Schatten |
| Gedämpft | `#54696E` | Sekundärtext (`text.secondary`) |
| Rand | `#D8ECEE` | Haarlinien, Divider |
| Eis | `#EDF5F6` | hellste getönte Fläche, Panel-Köpfe |

---

## ✒️ Typografie

- **Carlito, ersatzweise Calibri** (metrikgleich), danach die Systemschriften.
- **Zwei Gewichte, mehr nicht:** 400 für Fließtext, 700 für Titel, Schaltflächen und Zwischenüberschriften. Kein 300, kein 500, kein 600.

---

## 📐 Radien

Drei Werte, drei Bedeutungen:

| Ebene | Radius | Token |
|---|---|---|
| Karte | 10 px | `CARD_RADIUS` |
| Panel (Spalte, Vorhaben-Kachel) | 14 px | `PANEL_RADIUS` |
| Bedienelement (Grundradius) | 8 px | `shape.borderRadius` |

Der Grundradius liegt bewusst zwischen den beiden anderen: rund genug, dass Schaltflächen und Eingabefelder zur Panel-Sprache passen, ohne zu Pillen zu werden.

---

## 🌓 Tiefe

**Flächen tragen Tiefe, Bedienelemente nicht.** Karten und Panels stehen auf Schattenebenen; Schaltflächen, Menüs und Eingabefelder bleiben flach mit Haarlinie. Tiefe ist ein Mittel, Ebenen zu unterscheiden — nicht, Dinge hervorzuheben.

**Alle Schatten führen die Marken-Tinte `rgba(36,53,57,…)`, nie Schwarz.** Ein Schatten in der Grundfarbe wirkt wie Licht, ein schwarzer wie Schmutz.

**Getönter Grund, weiße Inhaltsflächen.** Der Grund der Anwendung ist leicht getönt, Karten und Panels sind weiß — nur so haben die Schattenebenen etwas, wogegen sie wirken. Umgesetzt in Issue #713: Der Grund ist `APP_BACKGROUND` — zwei radiale Verläufe aus dem Eis der Palette an den oberen Ecken, auslaufend nach Weiß. Er liegt auf einer fixierten eigenen Schicht (`body::before`) und gilt damit für die ganze Anwendung, auch für die Anmeldeseiten außerhalb der Shell. **Nicht** über `background-attachment: fixed`: iOS Safari ignoriert das und fällt auf `scroll` zurück.

---

## ♿ Kontrast

**WCAG AA ist das Mindestmaß: 4,5:1 für Fließtext**, 3:1 für großen Text und für bedeutungstragende Grafikelemente. Eine Farbkombination, die das verfehlt, ist ein Fehler — auch wenn sie gefällt. Der Kontrast wird gegen die Fläche gerechnet, auf der der Text tatsächlich steht, nicht gegen Weiß aus Gewohnheit.

---

## 🔢 Weitere Tokens

**Einzige Wertequelle für alle übrigen Design-Tokens ist [`frontend/src/theme.ts`](frontend/src/theme.ts).** Dort stehen unter anderem `STATUS_EDGE_WIDTH`, `EPIC_EDGE_WIDTH`, `SURFACE_TINT`, `CODE_BG`, `APP_BACKGROUND` sowie die Schatten `CARD_SHADOW`, `CARD_SHADOW_HOVER` und `PANEL_SHADOW`. Diese Datei nennt ihre Werte **absichtlich nicht**: Zwei Wertequellen laufen auseinander, ohne dass ein Test es merkt.

Statusfarben der Spalten sind eine eigene, bewusste Ausnahme und liegen in [`frontend/src/lib/statusColors.ts`](frontend/src/lib/statusColors.ts).

---

## 📜 Historie

Die Designsprache heißt **„Panel“**: runder, zwei Ebenen Tiefe, Fläche nicht durchgehend weiß.

Davor stand kurzzeitig eine Variante **„Kante“** (Radius 4 px, kein Ruheschatten, weiß auf weiß), geschnitten in Plandokument #617. Ihr Kernsatz „die Fläche ist weiß“ stammte wörtlich aus einer Publikationsregel für Veröffentlichungen — auf eine Arbeitsanwendung übertragen, wo sie nie hingehörte. Am 2026-08-31 wurde auf „Panel“ gewechselt.

**#617 gilt nicht mehr.** Wer es findet, liest ein überholtes Dokument. Maßgeblich sind diese Datei und `theme.ts`.
