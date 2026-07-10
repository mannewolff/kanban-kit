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
const CARD_BG = '#F6FAFB'
const ICE = '#EDF5F6'

export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: TEAL, light: TEAL_LIGHT, dark: TEAL_DEEP, contrastText: '#FFFFFF' },
    secondary: { main: TEAL_DEEP },
    text: { primary: TITLE, secondary: MUTED },
    background: { default: '#FFFFFF', paper: '#FFFFFF' },
    divider: BORDER,
  },
  shape: { borderRadius: 6 },
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
          '&:hover': { backgroundColor: CARD_BG },
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
  },
})
