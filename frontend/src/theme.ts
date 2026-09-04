import { createTheme } from '@mui/material/styles'

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
 * **Getönter Grund, weiße Inhaltsflächen.** Der Grund der ganzen Anwendung ({@link APP_BACKGROUND})
 * liegt am `body`; Karten und Panels sind weiß. Bis #713 trug allein das Board einen eigenen
 * Verlauf, alles daneben stand weiß auf weiß.
 */

// Tokens der Designsprache (siehe CLAUDE-design.md).
const TEAL = '#2F8C97'
const TEAL_DEEP = '#1E5F68'
const TEAL_LIGHT = '#5BABB5'
const TITLE = '#243539'
// Sekundärtext, abgedunkelt in #713. Der Vorgängerton hielt AA nur auf reinem Weiß (4,59:1) und
// verfehlte sie schon im Bestand auf SURFACE_TINT (4,37:1); auf dem getönten Grund läge er tiefer.
const MUTED = '#54696E'
const BORDER = '#D8ECEE'
const ICE = '#EDF5F6'

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
 * **Alle Schatten führen `rgba(36,53,57,…)`** — die Marken-Tinte {@link TITLE}, kein Schwarz. Ein
 * Schatten in der Grundfarbe wirkt wie Licht, ein schwarzer wie Schmutz.
 */

/** Anheben einer einfachen Fläche beim Hover — Teal der Palette, kein schwarzer Farbanteil. */
export const SURFACE_HOVER_SHADOW = '0 2px 8px rgba(47,140,151,0.18)'

/**
 * Fläche der Kopfleiste: der helle Teal der Palette ({@link TEAL_LIGHT}).
 *
 * **Warum der helle und nicht der Marken-Teal:** Die Leiste trug bis #653 den mittleren Teal
 * `#2F8C97` mit weißer Schrift; dieser Kontrast liegt bei 3,85:1 und verfehlt die AA-Schwelle von
 * 4,5:1 für normalen Text. #653 hat sie daraufhin auf Weiß gestellt und die Schrift auf
 * `text.primary` gesetzt. Mit `#5BABB5` als Fläche und derselben dunklen Schrift kommt die Farbe
 * zurück, und der Kontrast steigt auf 4,97:1 — die Leiste ist farbig **und** barrierefrei. Weiße
 * Schrift auf diesem Ton wäre mit 2,64:1 deutlich schlechter als der Zustand, den #653 behoben hat.
 */
export const HEADER_BG = TEAL_LIGHT

/** Breite der linken Status-Kante an Spalte und Karte (px). */
export const STATUS_EDGE_WIDTH = 3

/** Breite einer linken Akzentkante an hervorgehobenen Flächen (Kennzahl-Kacheln, Auth-Karte). */
export const EPIC_EDGE_WIDTH = 4

/** Eckradius einer Karte (px) — Variante „Panel". */
export const CARD_RADIUS = 10

/** Eckradius eines Panels: Spalte, Vorhaben-Kachel (px). */
export const PANEL_RADIUS = 14

/**
 * Ruhezustand einer Karte: eine Lichtkante an der Oberkante plus zwei Schattenebenen. Die
 * Lichtkante (`inset 0 1px 0 #FFFFFF`) trägt den plastischen Eindruck — mehr als ein Pixel Licht
 * braucht es dafür nicht.
 */
export const CARD_SHADOW =
  'inset 0 1px 0 #FFFFFF, 0 1px 2px rgba(36,53,57,0.05), 0 4px 10px rgba(36,53,57,0.05)'

/** Karte unter dem Zeiger: dieselbe Lichtkante, deutlich weiter geöffneter Schatten. */
export const CARD_SHADOW_HOVER =
  'inset 0 1px 0 #FFFFFF, 0 4px 8px rgba(36,53,57,0.07), 0 14px 30px rgba(36,53,57,0.12)'

/** Panel (Spalte, Kachel): schwebt über der Board-Fläche, ohne selbst Licht zu tragen. */
export const PANEL_SHADOW = '0 1px 2px rgba(36,53,57,0.04), 0 6px 20px rgba(36,53,57,0.07)'

/** Neutral getönte Fläche (Spalten, Zebra-Zeilen, Menü-Hover, Grundfläche der Anwendung). */
export const SURFACE_TINT = '#F6FAFB'

/**
 * Grund der ganzen Anwendung: eine durchgehend getönte Fläche aus {@link SURFACE_TINT}, darüber
 * zwei weit ausgelaufene Verläufe aus {@link ICE} an den oberen Ecken. Ohne ihn stünde Weiß auf
 * Weiß, und die Schattenebenen der Panels hätten keinen Grund, gegen den sie wirken. Er gilt für
 * Board, Listen, Vorhaben, Dashboard, Administration und die Anmeldeseiten gleichermaßen — bis
 * #713 trug ihn allein das Board.
 *
 * **Die Grundfläche ist getönt und nicht weiß, und das ist der tragende Teil.** In der ersten
 * Fassung aus #713 stand dort `#FFFFFF`; getönt war die Fläche dann nur, soweit die Verläufe
 * reichten — bei 1920px Breite deckten sie ab etwa 660px gar nichts mehr, und die Mitte, der
 * ganze untere Bereich und beide unteren Ecken blieben reines Weiß. Auf einem breiten Bildschirm
 * war von der Tönung nichts zu sehen. Die Radien sind aus demselben Grund gewachsen: Ein Verlauf,
 * der auf einem Drittel der Fläche ausläuft, trägt keinen Grund, er setzt einen Akzent.
 */
export const APP_BACKGROUND = [
  `radial-gradient(1600px 1100px at 0% 0%,   ${ICE} 0%, rgba(255,255,255,0) 70%)`,
  `radial-gradient(1400px 1000px at 100% 0%, ${ICE} 0%, rgba(255,255,255,0) 65%)`,
  SURFACE_TINT,
].join(', ')

/** Kopf eines Panels: sehr flacher Verlauf nach Weiß, trennt ohne einen Kasten zu bauen. */
export const PANEL_HEAD_GRADIENT = `linear-gradient(180deg,${ICE} 0%,#FFFFFF 100%)`

/** Anheben einer Karte unter dem Zeiger (px, negativ = nach oben). */
export const CARD_LIFT = -3

/** Hintergrund für Inline-Code und Codeblöcke; ohne Entsprechung in der Marken-Palette. */
export const CODE_BG = '#f4f5f7'

/**
 * Die vier Zustandsfarben der Nachtlauf-Auswertung (Plan #718, A15).
 *
 * **Warum ein eigener Palette-Eintrag und nicht `success`/`warning`/`error`:** Diese Namen sind im
 * Frontend an Dutzenden Nicht-Test-Stellen in Gebrauch — `color="error"` an Lösch-Buttons,
 * `severity` an Alerts, Feldfehler in Formularen. Sie umzudefinieren färbte all das mit um, und MUI
 * leitet `light`, `dark` und `contrastText` aus `main` ab; aus einem Markengrün könnte dabei weiße
 * Schrift unter 4,5:1 entstehen.
 *
 * **Alle vier Töne sind gegen die strengere der beiden Schwellen aus `CLAUDE-design.md` (Zeile 58)
 * geprüft** — 4,5:1, wie für Text — und decken damit die 3:1 eines Farbfelds mit ab. Das trägt
 * beide heutigen Verwendungen: als Textfarbe (Herkunftskette, Übernahmetext) und als ausgefüllte
 * Ampel-Fläche am Arbeitspaket (#738). Gemessen gegen die drei tatsächlichen Flächen des Leitstands
 * (Weiß, {@link SURFACE_TINT}, {@link ICE}); `theme.test.ts` rechnet sie nach.
 *
 * **Grau ist der Sekundärtext** {@link MUTED} und nicht `text.disabled`: Letzteres ist hier gar
 * nicht gesetzt, es gälte der MUI-Default `rgba(0,0,0,0.38)` mit rund 2,8:1 — und „vom Lauf nicht
 * bearbeitet" ist ein bedeutungstragender Zustand, kein deaktiviertes Bedienelement.
 */
export interface NightRunPalette {
  green: string
  yellow: string
  red: string
  grey: string
}

const NIGHT_RUN_GREEN = '#1F6B4A'
const NIGHT_RUN_YELLOW = '#8A5A00'
const NIGHT_RUN_RED = '#A32B22'

declare module '@mui/material/styles' {
  interface Palette {
    nightRun: NightRunPalette
  }
  interface PaletteOptions {
    nightRun?: NightRunPalette
  }
}

export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: TEAL, light: TEAL_LIGHT, dark: TEAL_DEEP, contrastText: '#FFFFFF' },
    secondary: { main: TEAL_DEEP },
    text: { primary: TITLE, secondary: MUTED },
    background: { default: '#FFFFFF', paper: '#FFFFFF' },
    divider: BORDER,
    nightRun: {
      green: NIGHT_RUN_GREEN,
      yellow: NIGHT_RUN_YELLOW,
      red: NIGHT_RUN_RED,
      grey: MUTED,
    },
  },
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
    // Kopfleiste: weiße Fläche aus der Palette statt `primary`, Haarlinie statt Elevation. Der
    // eigene `elevation: 0` ist nötig, weil MuiAppBar seinen Default 4 selbst setzt und der
    // MuiPaper-Default darauf nicht durchgreift.
    MuiAppBar: {
      defaultProps: { elevation: 0 },
      styleOverrides: {
        root: ({ theme: t }) => ({
          backgroundColor: HEADER_BG,
          // Die Leiste traegt ihre Textfarbe selbst: geerbtes Weiss aus primary.contrastText waere
          // auf dem hellen Teal mit 2,64:1 schlechter lesbar als die dunkle Marken-Tinte (4,97:1).
          color: t.palette.text.primary,
          borderBottom: `1px solid ${t.palette.divider}`,
        }),
      },
    },
    // Flach: keine Schlagschatten, Haarlinien-Ränder.
    MuiPaper: {
      defaultProps: { elevation: 0 },
      styleOverrides: {
        root: { backgroundImage: 'none' },
        outlined: { borderColor: BORDER },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: { root: { boxShadow: 'none' } },
    },
    // Dropdown-/Menü-Flyouts: Rand statt Schatten.
    MuiMenu: {
      styleOverrides: { paper: { boxShadow: 'none', border: `1px solid ${BORDER}` } },
    },
    MuiPopover: {
      styleOverrides: { paper: { boxShadow: 'none', border: `1px solid ${BORDER}` } },
    },
    MuiMenuItem: {
      styleOverrides: {
        root: {
          fontSize: 14,
          minHeight: 36,
          '&:hover': { backgroundColor: SURFACE_TINT },
          '&.Mui-selected': { backgroundColor: ICE },
          '&.Mui-selected:hover': { backgroundColor: ICE },
        },
      },
    },
    // Text-Felder / Selects: feiner Rand, dünne Teal-Fokuslinie (kein 2px-Ring).
    MuiOutlinedInput: {
      styleOverrides: {
        notchedOutline: { borderColor: BORDER },
        root: {
          '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: TEAL_LIGHT },
          '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: TEAL, borderWidth: 1 },
        },
      },
    },
    MuiSelect: {
      styleOverrides: { icon: { color: TEAL } },
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
