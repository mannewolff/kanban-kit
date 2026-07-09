import { createTheme } from '@mui/material/styles'

/** manban-Branding: ruhiges Grün, klare Flächen. */
export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: '#2f6f4f' },
    secondary: { main: '#b5651d' },
    background: { default: '#f4f1ea' },
  },
  shape: { borderRadius: 8 },
})
