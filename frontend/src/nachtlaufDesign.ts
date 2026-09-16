import { createTheme, type SxProps, type Theme } from '@mui/material/styles'
import type { NightRunState } from './lib/nightRunLog'
import { variablenAufloesen } from './lib/variablenAufloesen'
import { HELLE_VARIABLEN, theme } from './theme'

/**
 * Wertequelle der Nachtlauf-Auswertung (`/projects/:id/nachtlauf`) — die einzige bewusste
 * Ausnahme von der Designsprache „Panel" aus `CLAUDE-design.md`.
 *
 * **Warum eine eigene Datei** (Plan #911, E2/E3): `theme.ts` ist die Wertequelle der
 * Designsprache. Zwei einander widersprechende Sprachen in einer Datei ließen beim nächsten
 * Leser offen, welche gilt. Vorbild ist `lib/statusColors.ts`, das `CLAUDE-design.md` bereits
 * als benannte Ausnahme führt.
 *
 * **Herkunft der Werte:** der helle `:root`-Block von `docs/mockup-leitstand-nachtlauf.html`
 * (Stand nach Commit `3a3112e`), vom PO abgenommen. Die Dunkelmodus-Blöcke des Entwurfs sind
 * **nicht** übernommen — Nicht-Ziel 1 des Fachplans #903, Entscheidung E13 des Plans.
 *
 * **Abweichungen von der Vorlage** sind an der jeweiligen Konstante vermerkt (E16). Sie haben
 * ausnahmslos einen Grund: Accessibility steht in der Prioritätenordnung vor der visuellen
 * Präferenz, und ein verfehlter Kontrast ist nach `CLAUDE-design.md` ein Fehler.
 * `nachtlaufDesign.test.ts` rechnet jedes Paar nach.
 *
 * Die Datei trägt keine Logik und ist deshalb von der Coverage ausgenommen (`vite.config.ts`,
 * `sonar-project.properties`) — wie `theme.ts`.
 */

/** Grund der Seite außerhalb der Vorgangsblöcke. */
const GROUND = '#F7F8FA'
/** Fläche eines Vorgangsblocks. */
const SURFACE = '#FFFFFF'
/** Fließtext. */
const INK = '#0F1319'
/** Sekundärtext: Metazeile des Kopfes, Zeiten an Balken und Fußzeile. */
const INK_2 = '#4A515C'
/**
 * Labels: Vorzeile, Kennzahlen-Beschriftung, Zwischenüberschriften, Vorgangsnummer, Kosten.
 *
 * **Abweichung vom Entwurf** (E16): Der Entwurf führt `#767D89` — 3,90:1 auf dem Grund und
 * 4,15:1 im Vorgangsblock, also unter der AA-Schwelle für Text. Kein Element dieser Rolle ist
 * „großer Text" im Sinne der WCAG (die größte Schrift ist die 14px-Vorgangsnummer), damit gilt
 * 4,5:1. Abgedunkelt auf denselben Farbton, bis die Schwelle auf beiden Flächen hält.
 */
const INK_3 = '#696F7B'
/** Haarlinie: Rahmen der Vorgangsblöcke, Trennlinien von Kennzahlenreihe und Fußzeile. */
const LINE = '#DFE3E9'
/** Zweite, hellere Haarlinie: Trennung der Ergebniszeile innerhalb eines Vorgangsblocks. */
const LINE_2 = '#EDEFF3'
/** Akzent: Kartenchip im Hover, Fokusring. */
const AKZENT = '#4655C4'
/** Schiene der Balken — sie zeigt die Bezugsgröße, die Füllung den Wert. */
const RAIL = '#E4E7EC'

/**
 * Zustand „im Budget fertig geworden".
 *
 * **Abweichung vom Entwurf** (E16): Der Entwurf führt `#2F7D54`, das auf seiner eigenen
 * Füllfläche nur 4,10:1 erreicht. Die Ausgangspille trägt den Ton als Text (12px, halbfett),
 * also gilt 4,5:1. Abgedunkelt bis zur Schwelle; die Füllfläche bleibt unverändert, damit die
 * helle Flächenwirkung des Entwurfs erhalten bleibt.
 */
const GUT = '#2B744D'
/** Füllfläche der Ausgangspille „fertig". Wert des Entwurfs. */
const GUT_FILL = '#D9EDE2'
/**
 * Zustand „Budget aufgebraucht".
 *
 * **Abweichung vom Entwurf** (E16): Der Entwurf führt `#B8770F` und verfehlt damit in jeder
 * seiner Rollen die Schwelle — 3,48:1 auf dem Grund, 3,69:1 im Vorgangsblock, 3,03:1 auf der
 * eigenen Füllfläche. Er trägt Text (Begründungszeile, aufgebrauchte Zeit, Fußzeilen-Warnung)
 * und nicht nur Fläche. Abgedunkelt, bis alle drei Rollen 4,5:1 halten.
 */
const BUDGET = '#8F5B09'
/** Füllfläche der Ausgangspille „Budget aufgebraucht". Wert des Entwurfs. */
const BUDGET_FILL = '#F7E7C6'
/** Zustand „Lauf lief leer". Wert des Entwurfs; hält alle Schwellen unverändert. */
const LEER = '#A8342A'
/**
 * Füllfläche der roten Ausgangspille.
 *
 * **Ableitung** (E10): Der Entwurf kennt nur die Pillen „fertig" und „Budget", eine rote gibt es
 * dort nicht. Gebildet wie die drei vorhandenen Füllflächen — {@link LEER} zu 13 % auf
 * {@link SURFACE} —, wobei die 13 % so gewählt sind, dass die Helligkeit im Band der drei
 * Vorlagen liegt (relative Luminanz 0,808 gegenüber 0,808 / 0,810 / 0,830).
 */
const LEER_FILL = '#F4E5E3'
/**
 * Zustand „gar nicht gelaufen".
 *
 * **Abweichung vom Entwurf** (E16): Der Entwurf führt `#A5ABB5` — 2,31:1 im Vorgangsblock und
 * 1,94:1 auf der eigenen Füllfläche. Der Ton trägt die Zeitangabe einer nie gelaufenen Stufe,
 * ist also Text. Abgedunkelt bis zur Schwelle; er liegt damit nahe bei {@link INK_3}, was der
 * Entscheidung im Bestand entspricht (`theme.ts` führt Grau auf den Sekundärtext zurück, weil
 * „nicht bearbeitet" ein bedeutungstragender Zustand ist und kein deaktiviertes Bedienelement).
 */
const NIE = '#636974'
/** Füllfläche und Streifenmuster des Zustands „nie gelaufen". Wert des Entwurfs. */
const NIE_FILL = '#E9EBEF'

/** Farbtöne des Entwurfs. */
export interface NachtlaufFarben {
  ground: string
  surface: string
  ink: string
  ink2: string
  ink3: string
  line: string
  line2: string
  akzent: string
  rail: string
  gut: string
  gutFill: string
  budget: string
  budgetFill: string
  leer: string
  leerFill: string
  nie: string
  nieFill: string
}

export const NACHTLAUF_FARBEN: NachtlaufFarben = {
  ground: GROUND,
  surface: SURFACE,
  ink: INK,
  ink2: INK_2,
  ink3: INK_3,
  line: LINE,
  line2: LINE_2,
  akzent: AKZENT,
  rail: RAIL,
  gut: GUT,
  gutFill: GUT_FILL,
  budget: BUDGET,
  budgetFill: BUDGET_FILL,
  leer: LEER,
  leerFill: LEER_FILL,
  nie: NIE,
  nieFill: NIE_FILL,
}

/**
 * Schriftfamilien des Entwurfs. Sie werden mit der Anwendung ausgeliefert (#913) statt von einem
 * fremden Dienst geladen; die Rückfallketten greifen, solange das nicht geschehen ist.
 */
export const NACHTLAUF_SCHRIFTEN = {
  /** Überschrift, Kennzahlenwerte, Zwischenüberschriften. */
  display: '"Chivo", "Helvetica Neue", Arial, sans-serif',
  /** Fließtext. */
  body: '"IBM Plex Sans", "Segoe UI", system-ui, sans-serif',
  /** Zahlen und Kennungen: Vorzeile, Metazeile, Zeiten, Kartenchips, Kosten. */
  mono: '"IBM Plex Mono", ui-monospace, "SF Mono", Menlo, monospace',
} as const

/**
 * Größen und Abstände des Entwurfs. Zahlen sind Pixel; die Überschrift ist ein fertiger
 * CSS-Wert, weil ihre Größe mit der Fensterbreite läuft.
 */
export const NACHTLAUF_MASSE = {
  /** Überschrift der Nacht. */
  ueberschrift: 'clamp(28px, 5vw, 40px)',
  /** Wert einer Kennzahl. */
  kennzahlWert: 24,
  /** Vorzeile über der Überschrift. */
  vorzeile: 11,
  /** Metazeile unter der Überschrift. */
  metazeile: 13,
  /** Abstand zwischen zwei Vorgangsblöcken. */
  vorgangAbstand: 14,
  /** Polsterung eines Vorgangsblocks (oben, seitlich, unten). */
  vorgangPolsterung: '18px 20px 20px',
  /** Abstand der Zeilen innerhalb eines Vorgangsblocks. */
  vorgangInnenabstand: 16,
  /** Eckradius eines Vorgangsblocks. */
  vorgangRadius: 10,
} as const

/**
 * Zustand eines Vorgangs auf seinen Ton (E10). Als Zuordnung und nicht als Funktion mit
 * Verzweigungen: So ist an einer Stelle ablesbar, dass alle vier Zustände einen Ton haben.
 */
export const NACHTLAUF_TON: Record<NightRunState, string> = {
  GREEN: GUT,
  YELLOW: BUDGET,
  RED: LEER,
  GREY: NIE,
}

/**
 * Theme der Nachtlauf-Auswertung, für einen verschachtelten `ThemeProvider` über dem
 * Inhaltsbereich der Seite (#914).
 *
 * Die Palette steht im **ersten** Argument, damit MUI die abgeleiteten Töne des Akzents selbst
 * berechnet; die Komponenten-Vorgaben des Leitstands und `palette.nightRun` kommen im zweiten
 * hinzu. Ein verschachtelter Provider ersetzt das äußere Theme vollständig — ohne diese Übernahme
 * fielen alle Komponenten auf die MUI-Vorgabe zurück, sichtbar an jedem Dialog der Seite.
 *
 * `palette.nightRun` in `theme.ts` bleibt unverändert (E10): Es trägt weiter die beiden
 * Lauf-Arten, die in der Designsprache „Panel" dargestellt werden.
 *
 * **Die übernommenen Vorgaben tragen feste Hellwerte (#954).** Seit #951 stehen in `theme.ts`
 * Variablen, die an `:root` hängen und im dunklen Erscheinungsbild umschalten — unabhängig von
 * diesem verschachtelten Provider, und auch in Menüs und Popovern, die im Portal außerhalb der
 * Seite landen. Aufgelöst zu den Hellwerten bleibt die Ausnahme hell (Plan #932 E2).
 */
export const nachtlaufTheme = createTheme(
  {
    palette: {
      mode: 'light',
      primary: { main: AKZENT },
      text: { primary: INK, secondary: INK_2 },
      background: { default: GROUND, paper: SURFACE },
      divider: LINE,
    },
    shape: { borderRadius: 8 },
    typography: { fontFamily: NACHTLAUF_SCHRIFTEN.body },
  },
  { components: variablenAufloesen(theme.components, HELLE_VARIABLEN), palette: { nightRun: theme.palette.nightRun } },
)

/**
 * Wurzelknoten des Inhaltsbereichs der Nachtlauf-Auswertung (#954, Plan #932 E2).
 *
 * **Die Ausnahme gilt auch im dunklen Erscheinungsbild — und stellt sich nicht von selbst her.**
 * Bausteine auf der Seite lesen weiterhin Variablen des Leitstands (Status- und Vorhaben-Farben,
 * Flächen-Tokens); die hängen an `:root` und schalteten ins Dunkle. Der Wurzelknoten setzt sie für
 * seinen Teilbaum auf die Hellwerte zurück.
 *
 * **Der Grund wird hier gemalt**, nicht aus `body::before` bezogen: Jener trägt den Grund des
 * Leitstands und würde dunkel, während die Flächen der Seite hell blieben. Die negativen Ränder
 * ziehen die Fläche über die Polsterung des Inhaltsbereichs der Shell, damit dort kein dunkler
 * Streifen stehen bleibt; die Mindesthöhe reicht bis unter die Kopfleiste.
 */
export const NACHTLAUF_WURZEL_SX: SxProps<Theme> = {
  ...HELLE_VARIABLEN,
  bgcolor: GROUND,
  m: -3,
  p: 3,
  minHeight: 'calc(100vh - 64px)',
}
