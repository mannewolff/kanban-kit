import { createTheme } from '@mui/material/styles'
import { EPIC_FARBWERTE, type EpicFarbe } from './lib/epicMeta'
import { STATUS_FARBWERTE, type StatusColorSet } from './lib/statusColors'

/**
 * Designsprache „Kupferwarte" (#978, siehe CLAUDE-design.md). Vorlage ist der Entwurf
 * `docs/entwurf-leitstand.html`; seine Tokens (Z. 10–150) stehen hier 1:1 als Werte beider
 * Erscheinungsbilder. Wo ein Entwurfston auf seiner tatsächlichen Fläche die AA-Schwelle verfehlt,
 * ist er minimal im selben Farbton nachgedunkelt bzw. aufgehellt — die Abweichung steht an der
 * Konstante, `theme.test.ts` rechnet jedes Paar nach.
 *
 * **Tiefenmodell: Nut < Grund < Platte < Abgehoben.** Eingelassene Flächen (Schiene, Spalten,
 * Suchfeld) tragen einen Innenschatten, Platten (Karten, Kacheln, Dialoge) einen Schatten mit
 * Lichtkante, abgehobene Platten (Hover, gezogene Karte) einen längeren. Tasten liegen knapp über
 * ihrer Fläche.
 *
 * **Zwei Erscheinungsbilder, kein Schalter (#951).** Das Theme trägt beide als MUI-CSS-Variablen
 * mit `colorSchemeSelector: 'media'`: Die Anwendung folgt der Einstellung des Betriebssystems.
 * Beide Wertesätze stehen in dieser Datei — eine zweite Datei für die Dunkelwerte wäre eine zweite
 * Wertequelle.
 *
 * **Die exportierten Tokens sind Verweise, keine Werte.** `SURFACE_TINT`, `NUT` & Co. tragen
 * `var(--mb-palette-…)`. Wer einen Wert braucht, liest ihn je Erscheinungsbild aus
 * `theme.colorSchemes` — `theme.palette` liefert nur den hellen, und nur `theme.vars` schaltet um.
 */

/** Die Rollen des Entwurfs, je Erscheinungsbild. Namen wie im Entwurf, ohne Bindestrich. */
export interface WartePalette {
  /** `--grund`: Grund der Anwendung. */
  grund: string
  /** `--grund-tief`: oberes Ende der Schiene, verlassener Platz einer gezogenen Karte. */
  grundTief: string
  /** `--nute`: eingelassene Fläche (Schiene, Spalte, Suchfeld, Segment). */
  nute: string
  /** `--platte`: Fläche einer Platte (Karte, Kachel, Dialog). */
  platte: string
  /** `--platte-fuss`: unteres Ende einer Taste, Fuß einer Platte. */
  platteFuss: string
  /** `--platte-hoch`: oberes Ende einer Platte oder Taste. */
  platteHoch: string
  /** `--rand`: Haarlinie. */
  rand: string
  /** `--rand-stark`: kräftigere Linie (Tastenkappe, Platzhalter). */
  randStark: string
  /** `--kante`: Lichtkante an der Oberkante erhabener Flächen. */
  kante: string
  /** `--text-schwach`: Etiketten, Zähler, Fristen. */
  textSchwach: string
  /** `--kupfer-hell`: oberes Ende einer Kupferfläche. */
  kupferHell: string
  /** `--kupfer-schat`: kupferner Schimmer (Grund, Laufband, Auswahlring). */
  kupferSchimmer: string
  /** Oberes Ende der Kupfertaste — `--kupfer-hell`, wo Schrift darauf steht. */
  kupferTaste: string
  /** Schrift auf Kupfer. */
  aufKupfer: string
  /** Gewählte Zeile: Kupfer zu 9 % über der Platte (Entwurf Z. 905). */
  auswahl: string
  /** `--schatten-nute`. */
  schattenNute: string
  /** `--schatten-platte`. */
  schattenPlatte: string
  /** `--schatten-hoch`. */
  schattenHoch: string
  /** `--schatten-taste`. */
  schattenTaste: string
  /** Fläche des Kopfs: Grund zu 86 % mit der Platte (Entwurf Z. 288). */
  kopf: string
  /**
   * Die helle Phase des Wechselblinkers eines laufenden Laufs (Issue #1136) — eine aufgehellte
   * Variante des Stahl-Melders, gegen die der Melder selbst deutlich umschlägt.
   */
  blinkerHell: string
}

/**
 * Die Melder des Entwurfs: Semantik, nie Akzent. Als Füllung (LED, Kante, Punkt) halten sie 3:1
 * gegen jede Fläche der Warte, die Nut eingeschlossen.
 */
export interface MelderPalette {
  gruen: string
  bernst: string
  zinnob: string
  stahl: string
  grau: string
}

/**
 * Tokens der früheren Designsprache „Panel". Die Namen bleiben, weil sie an vielen Stellen in
 * Gebrauch sind; ihre Werte kommen seit #978 aus den Rollen der Warte (siehe `panelAus`).
 */
export interface PanelPalette {
  /** Platte-Fuß: Zebra-Zeilen, Menü-Hover. */
  surfaceTint: string
  /** Platte hoch: oberes Ende eines Plattenkopfs. */
  ice: string
  /** Grund der Anwendung mit dem Kupfer-Schimmer, siehe {@link APP_BACKGROUND}. */
  appBackground: string
  /** Fläche des Kopfs, siehe {@link HEADER_BG}. */
  headerBg: string
  /** Kopf einer Platte, siehe {@link PANEL_HEAD_GRADIENT}. */
  panelHeadGradient: string
  /** `--schatten-platte`. */
  cardShadow: string
  /** `--schatten-hoch`. */
  cardShadowHover: string
  /** `--schatten-platte`. */
  panelShadow: string
  /** `--schatten-platte` als Anheben einer Taste. */
  surfaceHoverShadow: string
  /** Nut: Inline-Code und Codeblöcke. */
  codeBg: string
}

/** Die Zustandsfarben der Nachtlauf-Auswertung, seit #978 aus den Meldern. */
export interface NightRunPalette {
  green: string
  yellow: string
  red: string
  grey: string
}

// ---------------------------------------------------------------------------------------------
// Hell (Entwurf Z. 17–60)
// ---------------------------------------------------------------------------------------------

const HELL = {
  grund: '#E7E9ED',
  grundTief: '#D8DBE2',
  nute: '#D5D9E0',
  platte: '#FDFDFE',
  platteFuss: '#F2F4F7',
  platteHoch: '#FFFFFF',
  rand: '#CDD2DA',
  randStark: '#B7BEC9',
  kante: 'rgba(255,255,255,.9)',
  text: '#14181E',
  /** Abweichung: Entwurf `#58606C` hält auf der Nut 4,49:1 — um eine Stufe nachgedunkelt. */
  textMatt: '#575F6B',
  /**
   * Abweichung: Entwurf `#868E9B` erreicht als Schrift nur 2,3–3,3:1 (Nut, Grund, Platte). Etiketten
   * sind 10 px — kein großer Text, also 4,5:1. Nachgedunkelt bis zur Schwelle auf der Nut; damit
   * liegt der Ton nahe am matten Text, die Hierarchie trägt das Etikett über Größe und Versalien.
   */
  textSchwach: '#5A5F68',
  kupfer: '#A85F2C',
  kupferHell: '#C2743C',
  /** Abweichung: Weiß auf Entwurf `#C2743C` hält 3,6:1 — für die Tastenschrift nachgedunkelt. */
  kupferTaste: '#AA6635',
  kupferTief: '#7B421C',
  kupferSchimmer: 'rgba(168,95,44,.16)',
  auswahl: '#F5EFEB',
  kopf: '#EAECEF',
  blinkerHell: '#A9C8F4',
} as const

/**
 * Melder hell. Abweichungen: Entwurf `#2F8F4E` (Grün), `#B07C15` (Bernstein) und `#8A929E` (Grau)
 * verfehlen auf der Nut 3:1 (2,9 / 2,6 / 2,2:1) und sind bis zur Schwelle nachgedunkelt;
 * Zinnober und Stahl halten unverändert.
 */
const MELDER_HELL: MelderPalette = {
  gruen: '#2E8B4C',
  bernst: '#A17113',
  zinnob: '#C8393E',
  stahl: '#2F6FC9',
  grau: '#757B86',
}

// ---------------------------------------------------------------------------------------------
// Dunkel (Entwurf Z. 63–108)
// ---------------------------------------------------------------------------------------------

const DUNKEL = {
  grund: '#0D1014',
  grundTief: '#090B0E',
  nute: '#080A0D',
  platte: '#171B22',
  platteFuss: '#12151B',
  platteHoch: '#1E242D',
  rand: '#262C36',
  randStark: '#333B47',
  kante: 'rgba(255,255,255,.075)',
  text: '#E7EAEF',
  textMatt: '#98A1AE',
  /** Abweichung: Entwurf `#69717E` erreicht als Schrift 3,2–4,0:1 — aufgehellt bis 4,5:1. */
  textSchwach: '#848B95',
  kupfer: '#D08A52',
  kupferHell: '#E3A26C',
  kupferTaste: '#E3A26C',
  kupferTief: '#A85F2C',
  kupferSchimmer: 'rgba(208,138,82,.18)',
  auswahl: '#282526',
  kopf: '#0E1216',
  blinkerHell: '#BFD6FB',
} as const

/** Melder dunkel: Werte des Entwurfs, sie halten 3:1 auf allen dunklen Flächen. */
const MELDER_DUNKEL: MelderPalette = {
  gruen: '#46C46F',
  bernst: '#E0AE49',
  zinnob: '#F0575C',
  stahl: '#5B96F0',
  grau: '#6E7681',
}

type Werte = typeof HELL | typeof DUNKEL

/**
 * Die vier Schatten des Entwurfs. Hell tragen sie eine Tinte `rgba(18,24,33,…)`, dunkel Schwarz —
 * so, wie der Entwurf sie führt.
 */
const SCHATTEN_HELL = {
  schattenPlatte: `0 1px 0 ${HELL.kante} inset, 0 1px 2px rgba(18,24,33,.10), 0 10px 24px -14px rgba(18,24,33,.35)`,
  schattenHoch: `0 1px 0 ${HELL.kante} inset, 0 2px 4px rgba(18,24,33,.10), 0 18px 34px -16px rgba(18,24,33,.42)`,
  schattenNute: `0 2px 5px rgba(18,24,33,.14) inset, 0 -1px 0 ${HELL.kante} inset`,
  schattenTaste: `0 1px 0 ${HELL.kante} inset, 0 1px 2px rgba(18,24,33,.18)`,
}

const SCHATTEN_DUNKEL = {
  schattenPlatte: `0 1px 0 ${DUNKEL.kante} inset, 0 1px 2px rgba(0,0,0,.5), 0 12px 28px -16px rgba(0,0,0,.85)`,
  schattenHoch: '0 1px 0 rgba(255,255,255,.11) inset, 0 2px 6px rgba(0,0,0,.55), 0 22px 40px -18px rgba(0,0,0,.95)',
  schattenNute: '0 3px 7px rgba(0,0,0,.6) inset, 0 -1px 0 rgba(255,255,255,.05) inset',
  schattenTaste: '0 1px 0 rgba(255,255,255,.08) inset, 0 1px 2px rgba(0,0,0,.6)',
}

const warteAus = (w: Werte, schatten: typeof SCHATTEN_HELL, aufKupfer: string): WartePalette => ({
  grund: w.grund,
  grundTief: w.grundTief,
  nute: w.nute,
  platte: w.platte,
  platteFuss: w.platteFuss,
  platteHoch: w.platteHoch,
  rand: w.rand,
  randStark: w.randStark,
  kante: w.kante,
  textSchwach: w.textSchwach,
  kupferHell: w.kupferHell,
  kupferSchimmer: w.kupferSchimmer,
  kupferTaste: w.kupferTaste,
  aufKupfer,
  auswahl: w.auswahl,
  kopf: w.kopf,
  blinkerHell: w.blinkerHell,
  ...schatten,
})

/**
 * Grund der Anwendung (Entwurf Z. 152–160): der Grundton, darüber oben links ein kupferner
 * Schimmer.
 */
const grundMitSchimmer = (w: Werte): string =>
  `radial-gradient(1100px 600px at 18% -8%, ${w.kupferSchimmer}, transparent 62%), ${w.grund}`

const panelAus = (w: Werte, schatten: typeof SCHATTEN_HELL): PanelPalette => ({
  surfaceTint: w.platteFuss,
  ice: w.platteHoch,
  appBackground: grundMitSchimmer(w),
  headerBg: w.kopf,
  // Kopf einer Platte (Entwurf Z. 550–556).
  panelHeadGradient: `linear-gradient(180deg,${w.platteHoch} 0%,${w.platte} 100%)`,
  cardShadow: schatten.schattenPlatte,
  cardShadowHover: schatten.schattenHoch,
  panelShadow: schatten.schattenPlatte,
  surfaceHoverShadow: schatten.schattenPlatte,
  codeBg: w.nute,
})

/**
 * Flächen der gefüllten Meldungen — die Toasts der Anwendung (`SnackbarProvider`, #960). Der
 * Entwurf kennt keine Toasts; die Flächen bleiben gesättigte, dunkle Zustandstöne mit weißer
 * Schrift, die auf hellem wie auf dunklem Grund als Meldung erkennbar sind.
 */
const MELDUNGEN_GEFUELLT = {
  errorFilledBg: '#C62828',
  errorFilledColor: '#FFFFFF',
  warningFilledBg: '#A34700',
  warningFilledColor: '#FFFFFF',
  infoFilledBg: '#01579B',
  infoFilledColor: '#FFFFFF',
  successFilledBg: '#2E7D32',
  successFilledColor: '#FFFFFF',
}

declare module '@mui/material/styles' {
  interface Palette {
    warte: WartePalette
    melder: MelderPalette
    nightRun: NightRunPalette
    panel: PanelPalette
    status: typeof STATUS_FARBWERTE.light
    epic: ReadonlyArray<EpicFarbe>
  }
  interface PaletteOptions {
    warte?: WartePalette
    melder?: MelderPalette
    nightRun?: NightRunPalette
    panel?: PanelPalette
    status?: Readonly<Record<string, StatusColorSet>>
    epic?: ReadonlyArray<EpicFarbe>
  }
  interface CssThemeVariables {
    enabled: true
  }
}

/** Beide Erscheinungsbilder samt Variablen-Schalter — Grundlage für die Tokens und das Theme. */
const ERSCHEINUNGSBILDER = {
  cssVariables: { colorSchemeSelector: 'media', cssVarPrefix: 'mb' },
  colorSchemes: {
    light: {
      palette: {
        primary: { main: HELL.kupfer, light: HELL.kupferHell, dark: HELL.kupferTief, contrastText: '#FFFFFF' },
        secondary: { main: HELL.kupferTief },
        text: { primary: HELL.text, secondary: HELL.textMatt },
        background: { default: HELL.grund, paper: HELL.platte },
        divider: HELL.rand,
        warte: warteAus(HELL, SCHATTEN_HELL, '#FFFFFF'),
        melder: MELDER_HELL,
        // Die Ampel der Nachtlauf-Auswertung spricht die Melder; Grau ist dort Sekundärtext.
        nightRun: { green: MELDER_HELL.gruen, yellow: MELDER_HELL.bernst, red: MELDER_HELL.zinnob, grey: HELL.textMatt },
        panel: panelAus(HELL, SCHATTEN_HELL),
        status: STATUS_FARBWERTE.light,
        epic: EPIC_FARBWERTE.light,
        Alert: MELDUNGEN_GEFUELLT,
      },
    },
    dark: {
      palette: {
        // Abweichung: Weiß auf Kupfer hält dunkel nur 2,8:1 — die Schrift ist dort die Grundtinte.
        primary: { main: DUNKEL.kupfer, light: DUNKEL.kupferHell, dark: DUNKEL.kupferTief, contrastText: DUNKEL.grund },
        secondary: { main: DUNKEL.kupferHell },
        text: { primary: DUNKEL.text, secondary: DUNKEL.textMatt },
        background: { default: DUNKEL.grund, paper: DUNKEL.platte },
        divider: DUNKEL.rand,
        warte: warteAus(DUNKEL, SCHATTEN_DUNKEL, DUNKEL.grund),
        melder: MELDER_DUNKEL,
        nightRun: {
          green: MELDER_DUNKEL.gruen,
          yellow: MELDER_DUNKEL.bernst,
          red: MELDER_DUNKEL.zinnob,
          grey: DUNKEL.textMatt,
        },
        panel: panelAus(DUNKEL, SCHATTEN_DUNKEL),
        status: STATUS_FARBWERTE.dark,
        epic: EPIC_FARBWERTE.dark,
        Alert: MELDUNGEN_GEFUELLT,
      },
    },
  },
} as const

const VARIABLEN_THEME = createTheme(ERSCHEINUNGSBILDER)

/** Die Variablen-Verweise, wie MUI sie erzeugt — ohne sie von Hand nachzubauen. */
const VARIABLEN = VARIABLEN_THEME.vars.palette

/**
 * Alle Variablen des hellen Erscheinungsbilds samt `color-scheme: light`, so wie MUI sie an
 * `:root` schreibt. Der Druckblock setzt sie zurück, damit der Ausdruck auch bei dunklem System
 * hell bleibt (#953); die Nachtlauf-Auswertung setzt sie an ihrem Wurzelknoten, damit ihre
 * Ausnahme auch im Dunkeln hell bleibt (#954).
 */
const BLAETTER = VARIABLEN_THEME.generateStyleSheets()

/** Der `:root`-Block des dunklen Erscheinungsbilds, so wie MUI ihn in die Media-Query schreibt. */
const WURZEL_DUNKEL: Readonly<Record<string, string>> =
  (
    BLAETTER.find((blatt) => '@media (prefers-color-scheme: dark)' in blatt)?.['@media (prefers-color-scheme: dark)'] as
      | Record<string, Record<string, string>>
      | undefined
  )?.[':root'] ?? {}

export const HELLE_VARIABLEN: Readonly<Record<string, string>> = (() => {
  const hell = BLAETTER.map((blatt) => blatt[':root'] as Record<string, string> | undefined).find(
    (wurzel) => wurzel?.colorScheme === 'light',
  )
  const dunkel = WURZEL_DUNKEL
  // Einige Variablen legt MUI nur dunkel an: die Aufhellung erhöhter Flächen (`overlays`) und
  // zwei Farben, die nur dunkle Komponenten-Stile lesen. Hell gibt es für sie keinen Wert zum
  // Zurücksetzen; im Druck bekommen sie deshalb den hellen Gegenwert.
  const palette = VARIABLEN_THEME.colorSchemes.light!.palette
  const ersatz: Record<string, string> = {
    '--mb-palette-text-icon': palette.action.active,
    '--mb-palette-AppBar-darkBg': palette.background.paper,
    '--mb-palette-AppBar-darkColor': palette.text.primary,
  }
  const nurDunkel = Object.keys(dunkel).filter((name) => name.startsWith('--') && !(name in (hell ?? {})))
  return {
    ...hell,
    ...Object.fromEntries(
      nurDunkel
        .map((name) => [name, name.startsWith('--mb-overlays-') ? 'none' : ersatz[name]] as const)
        .filter(([, wert]) => wert !== undefined),
    ),
  }
})()

/**
 * Beide Erscheinungsbilder für einen Teilbaum, der nicht an `:root` hängen kann (#987).
 *
 * <p>Die Nachtlauf-Auswertung setzt an ihrem Wurzelknoten die hellen Variablen fest, damit ihre
 * Ausnahme auch im Dunkeln hell bleibt (#954). Ein Bereich **darin**, der wieder Kupferwarte trägt,
 * kann die Variablen von `:root` nicht zurückholen — CSS kennt kein „erbe wieder von oben". Er
 * schreibt deshalb beide Sätze selbst, mit derselben Media-Query, die auch `:root` benutzt: hell als
 * Grundlage, dunkel im dunklen System.
 */
export const ERSCHEINUNGSBILD_SX: Readonly<Record<string, string | Readonly<Record<string, string>>>> = {
  ...HELLE_VARIABLEN,
  '@media (prefers-color-scheme: dark)': WURZEL_DUNKEL,
}

// ---------------------------------------------------------------------------------------------
// Schriften (Entwurf Z. 2–4, 162–183)
// ---------------------------------------------------------------------------------------------

/** Archivo mit variabler Breite: Titel, Marke, Etiketten, Spaltennamen. */
export const SCHRIFT_ANZEIGE = '"Archivo Variable", Archivo, system-ui, sans-serif'
/** IBM Plex Sans: Fließtext und Bedienelemente. */
export const SCHRIFT_TEXT = '"IBM Plex Sans", system-ui, -apple-system, "Segoe UI", sans-serif'
/** IBM Plex Mono: Nummern, Kennungen, Zahlen. */
export const SCHRIFT_MONO = '"IBM Plex Mono", ui-monospace, "SF Mono", Menlo, monospace'

/**
 * Tabellenziffern für Zahlen, die untereinander stehen: rechtsbündige Tabellenzellen und
 * Kennzahl-Kacheln. Eine große Einzelzahl trägt sie bewusst nicht — dort lassen gleich breite
 * Ziffern die Zahl auseinanderfallen (Kachelwerte im Leitstand).
 */
export const TABELLENZIFFERN = { fontVariantNumeric: 'tabular-nums' } as const

/** Zahl oder Kennung in Plex Mono mit Tabellenziffern (Entwurf `.mono`, `.zahl`). */
export const ZAHL = { fontFamily: SCHRIFT_MONO, ...TABELLENZIFFERN } as const

/** Überschrift in Archivo, leicht gestreckt (Entwurf Z. 168–173). */
export const ANZEIGE = { fontFamily: SCHRIFT_ANZEIGE, fontStretch: '112%' } as const

/** Etikett (Entwurf Z. 185–194): Versalien, gesperrt, schwache Schrift. */
export const ETIKETT = {
  fontFamily: SCHRIFT_ANZEIGE,
  fontStretch: '118%',
  fontSize: 10,
  fontWeight: 600,
  letterSpacing: '.14em',
  textTransform: 'uppercase',
  lineHeight: 1.5,
  color: VARIABLEN.warte.textSchwach,
} as const

// ---------------------------------------------------------------------------------------------
// Exportierte Tokens (Verweise)
// ---------------------------------------------------------------------------------------------

/** Fokusring der Anwendung: zwei Pixel Kupfer mit Abstand (Entwurf Z. 180–184). */
const FOKUSRING = { outline: `2px solid ${VARIABLEN.primary.main}`, outlineOffset: '2px' }

/** Grund der Anwendung. */
export const GRUND = VARIABLEN.warte.grund
/** Oberes Ende der Schiene. */
export const GRUND_TIEF = VARIABLEN.warte.grundTief
/** Eingelassene Fläche. */
export const NUT = VARIABLEN.warte.nute
/** Fläche einer Platte. */
export const PLATTE = VARIABLEN.warte.platte
/** Unteres Ende einer Taste. */
export const PLATTE_FUSS = VARIABLEN.warte.platteFuss
/** Oberes Ende einer Platte oder Taste. */
export const PLATTE_HOCH = VARIABLEN.warte.platteHoch
/** Haarlinie. */
export const RAND = VARIABLEN.warte.rand
/** Kräftigere Linie. */
export const RAND_STARK = VARIABLEN.warte.randStark
/**
 * Matter Text: Sekundärtext und Navigation (`CLAUDE-design.md`, Palettentabelle „Text matt").
 *
 * <p>Derselbe Verweis, den der Palettenpfad `text.secondary` in `sx` erzeugt — als benannter Token
 * neben `RAND`, `PLATTE`, `NUT` und `TEXT_SCHWACH`, damit ein Baustein seine Farben aus **einer**
 * Reihe nimmt und nicht aus zwei Schreibweisen (#1041). Eine eigene Variable wäre eine zweite
 * Wertequelle für dieselbe Rolle.
 */
export const TEXT_MATT = VARIABLEN.text.secondary
/** Schwache Schrift: Etiketten, Zähler, Fristen. */
export const TEXT_SCHWACH = VARIABLEN.warte.textSchwach
/** Leitfarbe. */
export const KUPFER = VARIABLEN.primary.main
/** Oberes Ende einer Kupferfläche. */
export const KUPFER_HELL = VARIABLEN.primary.light
/** Kupferner Schimmer. */
export const KUPFER_SCHIMMER = VARIABLEN.warte.kupferSchimmer
/** Gewählte Zeile. */
export const AUSWAHL = VARIABLEN.warte.auswahl
/** Helle Phase des Wechselblinkers eines laufenden Laufs (Issue #1136). */
export const BLINKER_HELL = VARIABLEN.warte.blinkerHell
/** Innenschatten einer Nut. */
export const SCHATTEN_NUTE = VARIABLEN.warte.schattenNute
/** Schatten einer Platte. */
export const SCHATTEN_PLATTE = VARIABLEN.warte.schattenPlatte
/** Schatten einer abgehobenen Platte. */
export const SCHATTEN_HOCH = VARIABLEN.warte.schattenHoch
/** Schatten einer Taste. */
export const SCHATTEN_TASTE = VARIABLEN.warte.schattenTaste
/** Die Melder als Verweise. */
export const MELDER = VARIABLEN.melder
/**
 * Die Ampel als Verweise — dieselben vier Lampen, die die Nachtlauf-Auswertung spricht (Issue #833).
 *
 * Bewusst nicht `statusColors`: Das Modul leitet Farben aus **Spaltennamen** ab und trägt laut
 * eigenem Kopfkommentar ausschließlich Status-Farben. Ein Sicherungszustand ist keine Board-Spalte.
 */
export const AMPEL = VARIABLEN.nightRun

/** Eingelassene Fläche mit Rand und Innenschatten (Entwurf: Schiene, Suche, Spalte). */
export const NUT_SX = {
  backgroundColor: NUT,
  border: `1px solid ${RAND}`,
  boxShadow: SCHATTEN_NUTE,
} as const

/** Erhabene Taste (Entwurf `.taste`, Z. 322–338). */
export const TASTE_SX = {
  background: `linear-gradient(180deg, ${PLATTE_HOCH}, ${PLATTE_FUSS})`,
  border: `1px solid ${RAND}`,
  boxShadow: SCHATTEN_TASTE,
  color: VARIABLEN.text.primary,
} as const

/**
 * Kupfer-Mal der Marke (Entwurf Z. 218–228): Kupfer hell → Kupfer → tiefes Kupfer, mit Lichtkante.
 * Der tiefe Endton ist in beiden Erscheinungsbildern derselbe, wie im Entwurf.
 */
export const MARKE_MAL_SX = {
  background: `linear-gradient(155deg, ${VARIABLEN.primary.light}, ${KUPFER} 62%, ${HELL.kupferTief})`,
  boxShadow: '0 1px 0 rgba(255,255,255,.35) inset, 0 2px 6px rgba(0,0,0,.35)',
} as const

/**
 * Dunkler Innenring einer Melder-LED (Entwurf Z. 421): gibt der Leuchtfläche eine Fassung. Schwarz
 * wie im Entwurf, in beiden Erscheinungsbildern — ein Ring in der Leuchtfarbe verschwömme mit ihr.
 */
export const LED_RING = '0 0 0 1px rgba(0,0,0,.22) inset'

/** Rundes Nutzer-Mal im Kopf (Entwurf Z. 350–356), in beiden Erscheinungsbildern gleich. */
export const NUTZER_MAL_SX = {
  background: 'linear-gradient(160deg, #47505d, #2b323c)',
  color: '#FFFFFF',
  boxShadow: '0 1px 0 rgba(255,255,255,.18) inset, 0 2px 5px rgba(0,0,0,.35)',
} as const

/**
 * Text, der nur Vorlesewerkzeugen gilt: aus dem Fluss genommen, aber nicht `display: none` — sonst
 * läse ihn niemand. Für Stellen, an denen die Gestalt eine Angabe auf ein Zeichen verkürzt (eine
 * Marke, ein Strich) und der volle Satz trotzdem erreichbar bleiben muss.
 */
export const NUR_LESER_SX = {
  position: 'absolute',
  width: 1,
  height: 1,
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
} as const

/** Anheben einer Taste beim Hover. */
export const SURFACE_HOVER_SHADOW = VARIABLEN.panel.surfaceHoverShadow

/** Fläche des Kopfs. */
export const HEADER_BG = VARIABLEN.panel.headerBg

/** Breite der linken Status-Kante an Spalte und Karte (px). */
export const STATUS_EDGE_WIDTH = 3

/** Breite einer linken Akzentkante an hervorgehobenen Flächen (Kennzahl-Kacheln, Auth-Karte). */
export const EPIC_EDGE_WIDTH = 4

/** `--r-mittel`: Karte, Navigationseintrag (px). */
export const CARD_RADIUS = 10

/** `--r-gross`: Platte, Spalte, Kachel (px). */
export const PANEL_RADIUS = 14

/** `--r-klein`: Grundradius der Bedienelemente (px). */
export const KLEIN_RADIUS = 6

/** Ruhezustand einer Karte: `--schatten-platte`. */
export const CARD_SHADOW = VARIABLEN.panel.cardShadow

/** Karte unter dem Zeiger: `--schatten-hoch`. */
export const CARD_SHADOW_HOVER = VARIABLEN.panel.cardShadowHover

/** Platte (Spalte, Kachel): `--schatten-platte`. */
export const PANEL_SHADOW = VARIABLEN.panel.panelShadow

/** Platte-Fuß: Zebra-Zeilen, Menü-Hover. */
export const SURFACE_TINT = VARIABLEN.panel.surfaceTint

/** Grund der ganzen Anwendung mit dem Kupfer-Schimmer. */
export const APP_BACKGROUND = VARIABLEN.panel.appBackground

/** Kopf einer Platte: von Platte hoch auf die Platte. */
export const PANEL_HEAD_GRADIENT = VARIABLEN.panel.panelHeadGradient

/** Anheben einer Karte unter dem Zeiger (px, negativ = nach oben; Entwurf Z. 761). */
export const CARD_LIFT = -2

/** Hintergrund für Inline-Code und Codeblöcke: die Nut. */
export const CODE_BG = VARIABLEN.panel.codeBg

export const theme = createTheme({
  ...ERSCHEINUNGSBILDER,
  shape: { borderRadius: KLEIN_RADIUS },
  typography: {
    fontFamily: SCHRIFT_TEXT,
    // Fließtext 14 px, Zeilenhöhe 1,5 (Entwurf Z. 162–166).
    body1: { fontSize: '0.875rem', lineHeight: 1.5 },
    body2: { fontSize: '0.8125rem', lineHeight: 1.5 },
    h1: { ...ANZEIGE, fontWeight: 700 },
    h2: { ...ANZEIGE, fontWeight: 700 },
    h3: { ...ANZEIGE, fontWeight: 700 },
    h4: { ...ANZEIGE, fontWeight: 700, fontSize: '1.625rem', letterSpacing: '-0.01em' },
    h5: { ...ANZEIGE, fontWeight: 700, fontSize: '1.25rem' },
    h6: { ...ANZEIGE, fontWeight: 600, fontSize: '1rem' },
    subtitle1: { ...ANZEIGE, fontWeight: 600 },
    subtitle2: { ...ANZEIGE, fontWeight: 600, fontSize: '0.84375rem' },
    button: { textTransform: 'none', fontWeight: 600, fontSize: '0.78125rem' },
    overline: ETIKETT,
    caption: { fontSize: '0.75rem', lineHeight: 1.45 },
  },
  // Die Overrides tragen ausschließlich Variablen, keine Hexwerte: Ein fester Wert bliebe im
  // dunklen Erscheinungsbild hell. Sie lesen dabei nicht `t.vars` aus dem Funktionsargument, weil
  // `nachtlaufDesign.ts` diese Overrides in ein Theme ohne Variablen übernimmt.
  components: {
    // Der Grund liegt auf einer eigenen, fixierten Schicht hinter dem Inhalt — nicht als
    // `background-attachment: fixed` am `body`: iOS Safari ignoriert das.
    MuiCssBaseline: {
      styleOverrides: {
        body: { WebkitFontSmoothing: 'antialiased' },
        'body::before': {
          content: '""',
          position: 'fixed',
          inset: 0,
          zIndex: -1,
          background: APP_BACKGROUND,
        },
        // Bewegung reduzieren (AK 16): eine zentrale Regel statt je Fundstelle ein Vorbehalt.
        '@media (prefers-reduced-motion: reduce)': {
          '*, *::before, *::after': {
            transitionDuration: '0s !important',
            animationDuration: '0s !important',
            animationIterationCount: '1 !important',
            scrollBehavior: 'auto !important',
          },
        },
        // Ausdruck (E6): schlicht und immer hell — kein Grund, keine Verläufe, keine Schatten.
        // `:root:root` übertrifft die Spezifität der Dunkel-Regel an `:root`.
        '@media print': {
          ':root:root': HELLE_VARIABLEN,
          'body::before': { background: 'none' },
          '*, *::before, *::after': {
            backgroundImage: 'none !important',
            boxShadow: 'none !important',
            textShadow: 'none !important',
          },
        },
        // Sichtbarer Tastaturfokus (AK 15) an jedem Element, das ihn per Tastatur erhält.
        ':focus-visible': FOKUSRING,
        // Eingabefelder tragen den Fokus an ihrer Rahmenlinie (MuiOutlinedInput unten).
        '.MuiInputBase-input:focus-visible': { outline: 'none' },
      },
    },
    MuiButtonBase: {
      styleOverrides: { root: { '&.Mui-focusVisible': FOKUSRING } },
    },
    // Die Shell baut ihren Kopf selbst; ein AppBar außerhalb davon steht im Kopfton mit Haarlinie.
    MuiAppBar: {
      defaultProps: { elevation: 0 },
      styleOverrides: {
        root: {
          backgroundColor: HEADER_BG,
          color: VARIABLEN.text.primary,
          borderBottom: `1px solid ${VARIABLEN.divider}`,
        },
      },
    },
    // Platten: Rand und Schatten der Platte; ein Dialog liegt abgehoben.
    MuiPaper: {
      defaultProps: { elevation: 0 },
      styleOverrides: {
        root: { backgroundImage: 'none' },
        outlined: { borderColor: VARIABLEN.divider },
      },
    },
    MuiDialog: {
      styleOverrides: {
        paper: { border: `1px solid ${RAND}`, boxShadow: SCHATTEN_HOCH, borderRadius: PANEL_RADIUS },
      },
    },
    // Tasten (Entwurf Z. 322–348): umrandet = Taste, gefüllt primär = Kupfertaste.
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: { boxShadow: 'none', borderRadius: 9, transition: 'transform .1s ease, box-shadow .14s ease' },
        outlined: {
          ...TASTE_SX,
          '&:hover': { ...TASTE_SX, boxShadow: SCHATTEN_PLATTE },
          '&:active': { transform: 'translateY(1px)', boxShadow: SCHATTEN_NUTE },
        },
        containedPrimary: {
          color: VARIABLEN.warte.aufKupfer,
          background: `linear-gradient(180deg, ${VARIABLEN.warte.kupferTaste}, ${KUPFER})`,
          border: `1px solid ${VARIABLEN.primary.dark}`,
          boxShadow: `0 1px 0 rgba(255,255,255,.35) inset, 0 2px 8px -2px ${KUPFER_SCHIMMER}, 0 4px 12px -6px rgba(0,0,0,.5)`,
          '&:hover': { background: `linear-gradient(180deg, ${VARIABLEN.warte.kupferTaste}, ${KUPFER})` },
          '&:active': { transform: 'translateY(1px)' },
        },
      },
    },
    // Menüs liegen als abgehobene Platte.
    MuiMenu: {
      styleOverrides: { paper: { boxShadow: SCHATTEN_HOCH, border: `1px solid ${RAND}` } },
    },
    MuiPopover: {
      styleOverrides: { paper: { boxShadow: SCHATTEN_HOCH, border: `1px solid ${RAND}` } },
    },
    MuiMenuItem: {
      styleOverrides: {
        root: {
          fontSize: 13,
          minHeight: 34,
          '&:hover': { backgroundColor: SURFACE_TINT },
          '&.Mui-selected': { backgroundColor: AUSWAHL },
          '&.Mui-selected:hover': { backgroundColor: AUSWAHL },
        },
      },
    },
    // Eingabefelder liegen als Nut (Entwurf `.suche`, Z. 304–320): Fokus an der Rahmenlinie.
    MuiOutlinedInput: {
      styleOverrides: {
        notchedOutline: { borderColor: RAND },
        root: {
          backgroundColor: PLATTE,
          '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: RAND_STARK },
          '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
            borderColor: VARIABLEN.primary.main,
            borderWidth: 1,
          },
        },
      },
    },
    MuiSelect: {
      styleOverrides: { icon: { color: TEXT_SCHWACH } },
    },
    MuiChip: {
      styleOverrides: { root: { borderRadius: 5 } },
    },
    // Dichte der echten MUI-Tabellen (AK 12): kleine Zellen, Zahlen in Tabellenziffern.
    MuiTableCell: {
      defaultProps: { size: 'small' },
      styleOverrides: {
        head: ETIKETT,
        sizeSmall: { paddingTop: 4, paddingBottom: 4 },
        alignRight: TABELLENZIFFERN,
      },
    },
    // Zebra für alle Daten-Tabellen: nur gerade Zeilen im TableBody dezent tönen.
    MuiTable: {
      styleOverrides: {
        root: {
          '& .MuiTableBody-root .MuiTableRow-root:nth-of-type(even)': {
            backgroundColor: SURFACE_TINT,
          },
        },
      },
    },
  },
})
