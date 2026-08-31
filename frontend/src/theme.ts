import { createTheme } from '@mui/material/styles'

/**
 * kanban-kit im Markenstil von Manfred Wolff (brand.md): weiß, fein, Teal-Familie,
 * Haarlinien statt Schatten, Carlito (Calibri-metrik-gleich). Zwei Gewichte (400/700).
 */

// Marken-Tokens (brand.md).
const TEAL = '#2F8C97'
const TEAL_DEEP = '#1E5F68'
const TEAL_LIGHT = '#5BABB5'
const TITLE = '#243539'
const MUTED = '#5F7A7F'
const BORDER = '#D8ECEE'
const ICE = '#EDF5F6'

/**
 * Design-Tokens der Designsprache „Kante" (#648, Plandokument #617). Sie liegen hier und nicht in
 * einem Lib-Modul, weil `src/theme.ts` als Token-Objekt ohne Logik von der Coverage ausgenommen
 * ist: Tokens in `lib/` verlangten Tests für Werte, die niemand sinnvoll testen kann. Ihre
 * Existenz und ihre Werte sichert stattdessen `theme.test.ts` ab.
 */

/** Anheben einer Fläche beim Hover — Teal der Palette, kein schwarzer Farbanteil. */
export const SURFACE_HOVER_SHADOW = '0 2px 8px rgba(47,140,151,0.18)'

/** Breite der Status-Oberkante an Spalte und Karte (px). */
export const STATUS_EDGE_WIDTH = 3

/** Breite der linken Vorhaben-Kante (px). */
export const EPIC_EDGE_WIDTH = 4

/** Neutral getönte Fläche (Spalten, Zebra-Zeilen, Menü-Hover). */
export const SURFACE_TINT = '#F6FAFB'

/** Hintergrund für Inline-Code und Codeblöcke; ohne Entsprechung in der Marken-Palette. */
export const CODE_BG = '#f4f5f7'

export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: TEAL, light: TEAL_LIGHT, dark: TEAL_DEEP, contrastText: '#FFFFFF' },
    secondary: { main: TEAL_DEEP },
    text: { primary: TITLE, secondary: MUTED },
    background: { default: '#FFFFFF', paper: '#FFFFFF' },
    divider: BORDER,
  },
  shape: { borderRadius: 4 },
  typography: {
    fontFamily: 'Carlito, Calibri, "Segoe UI", system-ui, -apple-system, sans-serif',
    // Titel Bold, Fließtext Regular (brand.md).
    h4: { fontWeight: 700 },
    h5: { fontWeight: 700 },
    h6: { fontWeight: 700 },
    subtitle1: { fontWeight: 700 },
    subtitle2: { fontWeight: 700 },
    button: { textTransform: 'none', fontWeight: 700 },
  },
  components: {
    // Kopfleiste: weiße Fläche aus der Palette statt `primary`, Haarlinie statt Elevation. Der
    // eigene `elevation: 0` ist nötig, weil MuiAppBar seinen Default 4 selbst setzt und der
    // MuiPaper-Default darauf nicht durchgreift.
    MuiAppBar: {
      defaultProps: { elevation: 0 },
      styleOverrides: {
        root: ({ theme: t }) => ({
          backgroundColor: t.palette.background.paper,
          // Seit #653 traegt die Leiste auch ihre Textfarbe: geerbtes Weiss aus primary.contrastText
          // waere auf der weissen Flaeche unsichtbar.
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
