import { createTheme } from '@mui/material/styles'
import { EPIC_FARBWERTE, type EpicFarbe } from './lib/epicMeta'
import { STATUS_FARBWERTE, type StatusColorSet } from './lib/statusColors'

/**
 * Designsprache des Leitstands (siehe CLAUDE-design.md): fein, Teal-Familie, Carlito
 * (Calibri-metrik-gleich), zwei Gewichte (400/700).
 *
 * **Flächen tragen Tiefe, Bedienelemente nicht.** Karten und Panels stehen auf zwei Schattenebenen
 * in der Marken-Tinte (siehe die Panel-Tokens unten); Schaltflächen, Menüs und Eingabefelder
 * bleiben flach mit Haarlinie. Die frühere Regel „Haarlinien statt Schatten" galt ausnahmslos und
 * ließ das Board flach wirken — die Tiefe ist jetzt auf die tragenden Flächen beschränkt, statt
 * überall zu fehlen.
 *
 * **Getönter Grund, Inhaltsflächen aus Papier.** Der Grund der ganzen Anwendung
 * ({@link APP_BACKGROUND}) liegt am `body`; Karten und Panels tragen die Papierfläche. Bis #713 trug
 * allein das Board einen eigenen Verlauf, alles daneben stand weiß auf weiß.
 *
 * **Zwei Erscheinungsbilder, kein Schalter (#951, Plan #932 E9).** Das Theme trägt ein helles und
 * ein dunkles Erscheinungsbild als MUI-CSS-Variablen mit `colorSchemeSelector: 'media'`: Die
 * Anwendung folgt der Einstellung des Betriebssystems, und es gibt weder Zustand noch Persistenz,
 * die jemand als Schalter anbieten könnte — der Fachplan #925 schließt das Einstellen aus. Beide
 * Wertesätze stehen in dieser Datei (E10); eine eigene Datei für die Dunkelwerte wäre eine zweite
 * Wertequelle, vor der `CLAUDE-design.md` warnt.
 *
 * **Die exportierten Tokens sind Verweise, keine Werte.** `SURFACE_TINT` & Co. bleiben
 * Zeichenketten, tragen aber `var(--mb-palette-panel-…)`; ihre Fundstellen außerhalb dieser Datei
 * ändern sich dadurch nicht. Wer einen Wert braucht, liest ihn je Erscheinungsbild aus
 * `theme.colorSchemes` — `theme.palette` liefert nur den hellen, und nur `theme.vars` schaltet um.
 */

// Tokens der Designsprache, hell (siehe CLAUDE-design.md).
const TEAL = '#2F8C97'
const TEAL_DEEP = '#1E5F68'
const TEAL_LIGHT = '#5BABB5'
const TITLE = '#243539'
// Sekundärtext, abgedunkelt in #713. Der Vorgängerton hielt AA nur auf reinem Weiß (4,59:1) und
// verfehlte sie schon im Bestand auf SURFACE_TINT (4,37:1); auf dem getönten Grund läge er tiefer.
const MUTED = '#54696E'
const BORDER = '#D8ECEE'
const ICE = '#EDF5F6'
const PAPER = '#FFFFFF'
const TINT = '#F6FAFB'

/**
 * Tokens der Designsprache, dunkel (#951).
 *
 * **Herkunft:** keine Vorlage, abgeleitet aus der hellen Teal-Familie. Der Plan #932 (E3) macht
 * `CLAUDE-design.md` und diese Datei zur bindenden Quelle; der Gestaltungsentwurf
 * `docs/entwurf-leitstand.html` trägt eine andere Leitfarbe und ist Zielbild, nicht Wert. Die
 * Rollen bleiben dieselben, nur die Helligkeit kehrt sich um: Die Flächen sind fast schwarze
 * Teal-Töne, die Papierfläche liegt eine Stufe über dem Grund, das Eis der Panel-Köpfe eine weitere
 * darüber — dunkel trägt Höhe Helligkeit, nicht Schatten. Die Akzente werden aufgehellt, damit sie
 * auf dunklem Grund dieselbe Rolle behalten: Der Marken-Teal `#2F8C97` läge auf dem Papier bei
 * rund 2,4:1 und verlöre die 3:1 für bedeutungstragende Elemente.
 *
 * **Die Kopfleiste trägt den dunklen Teal der Palette** ({@link TEAL_DEEP}) — dieselbe Leiste mit
 * derselben Farbfamilie, mit heller Schrift bei rund 6,3:1.
 *
 * Alle Paare rechnet `theme.test.ts` in einer Tabelle über beide Erscheinungsbilder nach.
 */
const D_TEAL = '#5CBCC7'
const D_TEAL_LIGHT = '#8FD4DC'
const D_TITLE = '#E6F0F1'
const D_MUTED = '#A6BCC0'
const D_BORDER = '#2C4A50'
const D_ICE = '#1E363B'
const D_PAPER = '#172A2E'
const D_TINT = '#0F1C1F'

/**
 * Design-Tokens der Designsprache „Panel". Sie liegen hier und nicht in einem Lib-Modul, weil
 * `src/theme.ts` als Token-Objekt ohne Logik von der Coverage ausgenommen ist: Tokens in `lib/`
 * verlangten Tests für Werte, die niemand sinnvoll testen kann. Ihre Existenz und ihre Werte
 * sichert stattdessen `theme.test.ts` ab.
 *
 * **Herkunft der Werte:** Artifact „kanban-kit Board-Studien" vom 2026-08-22, Variante 3 „Panel".
 * Die Studie hatte drei Varianten; gewählt und in Plandokument #617 geschnitten wurde Variante 2
 * „Kante" (Radius 4px, kein Ruheschatten, weiß auf weiß). Am 2026-08-31 hat der Nutzer festgestellt,
 * dass er Panel meinte — runder, zwei Ebenen Tiefe, Fläche nicht durchgehend weiß. Die Studie sagte
 * über Panel selbst: „Das ist die Variante, nach der du vermutlich gefragt hast."
 *
 * **Alle Schatten führen eine Tinte, nie Schwarz.** Hell ist es die Marken-Tinte `rgba(36,53,57,…)`
 * ({@link TITLE}), dunkel eine fast schwarze Teal-Tinte `rgba(4,14,16,…)`. Ein Schatten in der
 * Grundfarbe wirkt wie Licht, ein schwarzer wie Schmutz — auf dunklem Grund nicht anders.
 */
export interface PanelPalette {
  /** Neutral getönte Fläche (Spalten, Zebra-Zeilen, Menü-Hover, Grundfläche der Anwendung). */
  surfaceTint: string
  /** Hellste getönte Fläche: Panel-Köpfe, ausgewählte Menüeinträge, Verläufe des Grunds. */
  ice: string
  /** Grund der ganzen Anwendung, siehe {@link APP_BACKGROUND}. */
  appBackground: string
  /** Fläche der Kopfleiste, siehe {@link HEADER_BG}. */
  headerBg: string
  /** Kopf eines Panels, siehe {@link PANEL_HEAD_GRADIENT}. */
  panelHeadGradient: string
  /** Ruhezustand einer Karte, siehe {@link CARD_SHADOW}. */
  cardShadow: string
  /** Karte unter dem Zeiger, siehe {@link CARD_SHADOW_HOVER}. */
  cardShadowHover: string
  /** Panel (Spalte, Kachel), siehe {@link PANEL_SHADOW}. */
  panelShadow: string
  /** Anheben einer einfachen Fläche beim Hover, siehe {@link SURFACE_HOVER_SHADOW}. */
  surfaceHoverShadow: string
  /** Hintergrund für Inline-Code und Codeblöcke, siehe {@link CODE_BG}. */
  codeBg: string
}

/**
 * Grund der ganzen Anwendung: eine durchgehend getönte Fläche, darüber zwei weit ausgelaufene
 * Verläufe aus dem Eis an den oberen Ecken. Ohne ihn stünde Papier auf Papier, und die
 * Schattenebenen der Panels hätten keinen Grund, gegen den sie wirken. Er gilt für Board, Listen,
 * Vorhaben, Dashboard, Administration und die Anmeldeseiten gleichermaßen — bis #713 trug ihn
 * allein das Board.
 *
 * **Die Grundfläche ist getönt und nicht die Papierfläche, und das ist der tragende Teil.** In der
 * ersten Fassung aus #713 stand dort `#FFFFFF`; getönt war die Fläche dann nur, soweit die Verläufe
 * reichten — bei 1920px Breite deckten sie ab etwa 660px gar nichts mehr, und die Mitte, der
 * ganze untere Bereich und beide unteren Ecken blieben reines Weiß. Auf einem breiten Bildschirm
 * war von der Tönung nichts zu sehen. Die Radien sind aus demselben Grund gewachsen: Ein Verlauf,
 * der auf einem Drittel der Fläche ausläuft, trägt keinen Grund, er setzt einen Akzent.
 */
const grund = (eis: string, tint: string, durchsichtig: string): string =>
  [
    `radial-gradient(1600px 1100px at 0% 0%,   ${eis} 0%, ${durchsichtig} 70%)`,
    `radial-gradient(1400px 1000px at 100% 0%, ${eis} 0%, ${durchsichtig} 65%)`,
    tint,
  ].join(', ')

/** Kopf eines Panels: sehr flacher Verlauf auf die Papierfläche, trennt ohne einen Kasten zu bauen. */
const panelKopf = (eis: string, papier: string): string =>
  `linear-gradient(180deg,${eis} 0%,${papier} 100%)`

const PANEL_HELL: PanelPalette = {
  surfaceTint: TINT,
  ice: ICE,
  appBackground: grund(ICE, TINT, 'rgba(255,255,255,0)'),
  /*
   * Die Leiste trug bis #653 den mittleren Teal `#2F8C97` mit weißer Schrift; dieser Kontrast liegt
   * bei 3,85:1 und verfehlt die AA-Schwelle von 4,5:1 für normalen Text. #653 hat sie daraufhin auf
   * Weiß gestellt und die Schrift auf `text.primary` gesetzt. Mit `#5BABB5` als Fläche und derselben
   * dunklen Schrift kommt die Farbe zurück, und der Kontrast hält die Schwelle — die Leiste ist
   * farbig **und** barrierefrei. Weiße Schrift auf diesem Ton wäre mit 2,64:1 deutlich schlechter
   * als der Zustand, den #653 behoben hat.
   */
  headerBg: TEAL_LIGHT,
  panelHeadGradient: panelKopf(ICE, PAPER),
  // Die Lichtkante (`inset 0 1px 0 #FFFFFF`) trägt den plastischen Eindruck — mehr als ein Pixel
  // Licht braucht es dafür nicht.
  cardShadow: 'inset 0 1px 0 #FFFFFF, 0 1px 2px rgba(36,53,57,0.05), 0 4px 10px rgba(36,53,57,0.05)',
  cardShadowHover:
    'inset 0 1px 0 #FFFFFF, 0 4px 8px rgba(36,53,57,0.07), 0 14px 30px rgba(36,53,57,0.12)',
  panelShadow: '0 1px 2px rgba(36,53,57,0.04), 0 6px 20px rgba(36,53,57,0.07)',
  surfaceHoverShadow: '0 2px 8px rgba(47,140,151,0.18)',
  // Ohne Entsprechung in der Marken-Palette.
  codeBg: '#f4f5f7',
}

const PANEL_DUNKEL: PanelPalette = {
  surfaceTint: D_TINT,
  ice: D_ICE,
  appBackground: grund(D_ICE, D_TINT, 'rgba(15,28,31,0)'),
  headerBg: TEAL_DEEP,
  panelHeadGradient: panelKopf(D_ICE, D_PAPER),
  // Die Lichtkante bleibt, aber gedämpft: Ein voll weißer Pixel wäre auf dunkler Karte eine grelle
  // Linie statt eines Lichtrands. Die Schatten öffnen stärker, weil sie auf dunklem Grund sonst
  // nicht zu sehen sind.
  cardShadow:
    'inset 0 1px 0 rgba(255,255,255,0.06), 0 1px 2px rgba(4,14,16,0.45), 0 4px 10px rgba(4,14,16,0.35)',
  cardShadowHover:
    'inset 0 1px 0 rgba(255,255,255,0.08), 0 4px 8px rgba(4,14,16,0.5), 0 14px 30px rgba(4,14,16,0.6)',
  panelShadow: '0 1px 2px rgba(4,14,16,0.4), 0 6px 20px rgba(4,14,16,0.5)',
  surfaceHoverShadow: '0 2px 8px rgba(92,188,199,0.22)',
  codeBg: '#223A3F',
}

/**
 * Die vier Zustandsfarben der Nachtlauf-Auswertung (Plan #718, A15).
 *
 * **Warum ein eigener Palette-Eintrag und nicht `success`/`warning`/`error`:** Diese Namen sind im
 * Frontend an Dutzenden Nicht-Test-Stellen in Gebrauch — `color="error"` an Lösch-Buttons,
 * `severity` an Alerts, Feldfehler in Formularen. Sie umzudefinieren färbte all das mit um, und MUI
 * leitet `light`, `dark` und `contrastText` aus `main` ab; aus einem Markengrün könnte dabei weiße
 * Schrift unter 4,5:1 entstehen.
 *
 * **Alle vier Töne dienen ausschließlich als ausgefüllte Ampel-Fläche am Arbeitspaket** (#738),
 * nicht als Text- oder Randfarbe. Damit gilt die schwächere der beiden Schwellen aus
 * `CLAUDE-design.md` — 3:1 für bedeutungstragende Grafikelemente, nicht 4,5:1 für Text.
 * **Rot ist deshalb bewusst kräftiger als grün/gelb** (PO-Entscheidung 2026-09-04, direkt am
 * Livesystem: „wirklich rot" statt des ursprünglich für Textkontrast gewählten Tons) — es hält 3:1
 * gegen die Flächen des Leitstands, aber nicht mehr 4,5:1. Dunkel bleibt es ein kräftiges Rot,
 * aufgehellt, damit es auf den dunklen Flächen dieselbe Schwelle hält. `theme.test.ts` rechnet
 * beide Erscheinungsbilder nach.
 *
 * **Grau ist der Sekundärtext** und nicht `text.disabled`: Letzteres ist hier gar nicht gesetzt, es
 * gälte der MUI-Default `rgba(0,0,0,0.38)` mit rund 2,8:1 — und „vom Lauf nicht bearbeitet" ist ein
 * bedeutungstragender Zustand, kein deaktiviertes Bedienelement.
 */
export interface NightRunPalette {
  green: string
  yellow: string
  red: string
  grey: string
}

const NIGHT_RUN_HELL: NightRunPalette = { green: '#1F6B4A', yellow: '#8A5A00', red: '#FF0000', grey: MUTED }
const NIGHT_RUN_DUNKEL: NightRunPalette = { green: '#4CC38A', yellow: '#E3AE4A', red: '#FF5C5C', grey: D_MUTED }

declare module '@mui/material/styles' {
  interface Palette {
    nightRun: NightRunPalette
    panel: PanelPalette
    status: typeof STATUS_FARBWERTE.light
    epic: ReadonlyArray<EpicFarbe>
  }
  interface PaletteOptions {
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
        primary: { main: TEAL, light: TEAL_LIGHT, dark: TEAL_DEEP, contrastText: '#FFFFFF' },
        secondary: { main: TEAL_DEEP },
        text: { primary: TITLE, secondary: MUTED },
        background: { default: PAPER, paper: PAPER },
        divider: BORDER,
        nightRun: NIGHT_RUN_HELL,
        panel: PANEL_HELL,
        // Status- und Vorhaben-Farben: Werte aus ihren Modulen, Variablen von hier (#952).
        status: STATUS_FARBWERTE.light,
        epic: EPIC_FARBWERTE.light,
      },
    },
    dark: {
      palette: {
        // Die Schrift auf einer gefüllten Primärfläche ist dunkel die Grundtinte: Weiß auf dem
        // aufgehellten Teal läge unter 3:1.
        primary: { main: D_TEAL, light: D_TEAL_LIGHT, dark: TEAL, contrastText: D_TINT },
        secondary: { main: D_TEAL_LIGHT },
        text: { primary: D_TITLE, secondary: D_MUTED },
        background: { default: D_TINT, paper: D_PAPER },
        divider: D_BORDER,
        nightRun: NIGHT_RUN_DUNKEL,
        panel: PANEL_DUNKEL,
        status: STATUS_FARBWERTE.dark,
        epic: EPIC_FARBWERTE.dark,
      },
    },
  },
} as const

/** Die Variablen-Verweise, wie MUI sie erzeugt — ohne sie von Hand nachzubauen. */
const VARIABLEN = createTheme(ERSCHEINUNGSBILDER).vars.palette

/** Anheben einer einfachen Fläche beim Hover — Teal der Palette, kein schwarzer Farbanteil. */
export const SURFACE_HOVER_SHADOW = VARIABLEN.panel.surfaceHoverShadow

/** Fläche der Kopfleiste: hell der helle Teal, dunkel der dunkle Teal der Palette. */
export const HEADER_BG = VARIABLEN.panel.headerBg

/** Breite der linken Status-Kante an Spalte und Karte (px). */
export const STATUS_EDGE_WIDTH = 3

/** Breite einer linken Akzentkante an hervorgehobenen Flächen (Kennzahl-Kacheln, Auth-Karte). */
export const EPIC_EDGE_WIDTH = 4

/** Eckradius einer Karte (px) — Variante „Panel". */
export const CARD_RADIUS = 10

/** Eckradius eines Panels: Spalte, Vorhaben-Kachel (px). */
export const PANEL_RADIUS = 14

/** Ruhezustand einer Karte: eine Lichtkante an der Oberkante plus zwei Schattenebenen. */
export const CARD_SHADOW = VARIABLEN.panel.cardShadow

/** Karte unter dem Zeiger: dieselbe Lichtkante, deutlich weiter geöffneter Schatten. */
export const CARD_SHADOW_HOVER = VARIABLEN.panel.cardShadowHover

/** Panel (Spalte, Kachel): schwebt über der Board-Fläche, ohne selbst Licht zu tragen. */
export const PANEL_SHADOW = VARIABLEN.panel.panelShadow

/** Neutral getönte Fläche (Spalten, Zebra-Zeilen, Menü-Hover, Grundfläche der Anwendung). */
export const SURFACE_TINT = VARIABLEN.panel.surfaceTint

/** Grund der ganzen Anwendung, je Erscheinungsbild aus dessen Flächentönen gebaut. */
export const APP_BACKGROUND = VARIABLEN.panel.appBackground

/** Kopf eines Panels: sehr flacher Verlauf auf die Papierfläche. */
export const PANEL_HEAD_GRADIENT = VARIABLEN.panel.panelHeadGradient

/** Anheben einer Karte unter dem Zeiger (px, negativ = nach oben). */
export const CARD_LIFT = -3

/** Hintergrund für Inline-Code und Codeblöcke; ohne Entsprechung in der Marken-Palette. */
export const CODE_BG = VARIABLEN.panel.codeBg

export const theme = createTheme({
  ...ERSCHEINUNGSBILDER,
  // Grundradius der Bedienelemente. Karten und Panels setzen ihren eigenen (CARD_RADIUS,
  // PANEL_RADIUS); 8 ist der Kompromiss dazwischen — deutlich runder als die 4 der Variante
  // „Kante", ohne Schaltflächen und Eingabefelder zu Pillen zu machen.
  shape: { borderRadius: 8 },
  typography: {
    fontFamily: 'Carlito, Calibri, "Segoe UI", system-ui, -apple-system, sans-serif',
    // Titel Bold, Fließtext Regular (CLAUDE-design.md).
    h4: { fontWeight: 700 },
    h5: { fontWeight: 700 },
    h6: { fontWeight: 700 },
    subtitle1: { fontWeight: 700 },
    subtitle2: { fontWeight: 700 },
    button: { textTransform: 'none', fontWeight: 700 },
  },
  // Die Overrides tragen ausschließlich Variablen, keine Hexwerte: Ein fester Wert bliebe im
  // dunklen Erscheinungsbild hell. Sie lesen dabei nicht `t.vars` aus dem Funktionsargument, weil
  // `nachtlaufDesign.ts` diese Overrides in ein Theme ohne Variablen übernimmt — dort ist `vars`
  // nicht gesetzt.
  components: {
    // Der Grund liegt auf einer eigenen, fixierten Schicht hinter dem Inhalt — nicht als
    // `background-attachment: fixed` am `body`: iOS Safari ignoriert das und fällt auf `scroll`
    // zurück, womit auf einem langen Board die Mitte des Verlaufs in den Scrollbereich rutschte.
    MuiCssBaseline: {
      styleOverrides: {
        'body::before': {
          content: '""',
          position: 'fixed',
          inset: 0,
          zIndex: -1,
          background: APP_BACKGROUND,
        },
      },
    },
    // Kopfleiste: eigene Fläche statt `primary`, Haarlinie statt Elevation. Der eigene
    // `elevation: 0` ist nötig, weil MuiAppBar seinen Default 4 selbst setzt und der
    // MuiPaper-Default darauf nicht durchgreift.
    MuiAppBar: {
      defaultProps: { elevation: 0 },
      styleOverrides: {
        root: {
          backgroundColor: HEADER_BG,
          // Die Leiste traegt ihre Textfarbe selbst: geerbtes Weiss aus primary.contrastText waere
          // auf dem hellen Teal mit 2,64:1 schlechter lesbar als die dunkle Marken-Tinte.
          color: VARIABLEN.text.primary,
          borderBottom: `1px solid ${VARIABLEN.divider}`,
        },
      },
    },
    // Flach: keine Schlagschatten, Haarlinien-Ränder.
    MuiPaper: {
      defaultProps: { elevation: 0 },
      styleOverrides: {
        root: { backgroundImage: 'none' },
        outlined: { borderColor: VARIABLEN.divider },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: { root: { boxShadow: 'none' } },
    },
    // Dropdown-/Menü-Flyouts: Rand statt Schatten.
    MuiMenu: {
      styleOverrides: { paper: { boxShadow: 'none', border: `1px solid ${VARIABLEN.divider}` } },
    },
    MuiPopover: {
      styleOverrides: { paper: { boxShadow: 'none', border: `1px solid ${VARIABLEN.divider}` } },
    },
    MuiMenuItem: {
      styleOverrides: {
        root: {
          fontSize: 14,
          minHeight: 36,
          '&:hover': { backgroundColor: SURFACE_TINT },
          '&.Mui-selected': { backgroundColor: VARIABLEN.panel.ice },
          '&.Mui-selected:hover': { backgroundColor: VARIABLEN.panel.ice },
        },
      },
    },
    // Text-Felder / Selects: feiner Rand, dünne Teal-Fokuslinie (kein 2px-Ring).
    MuiOutlinedInput: {
      styleOverrides: {
        notchedOutline: { borderColor: VARIABLEN.divider },
        root: {
          '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: VARIABLEN.primary.light },
          '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
            borderColor: VARIABLEN.primary.main,
            borderWidth: 1,
          },
        },
      },
    },
    MuiSelect: {
      styleOverrides: { icon: { color: VARIABLEN.primary.main } },
    },
    // Zebra für alle Daten-Tabellen: nur gerade Zeilen im TableBody dezent tönen. Header-Zeilen
    // liegen im TableHead und bleiben ungestreift; das Hover-Verhalten bleibt unberührt.
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
