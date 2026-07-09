import { createTheme } from '@mui/material/styles'

/** manban-Branding: Petrol/Teal, klare Flächen (an der Toolbox angelehnt). */
export const theme = createTheme({
  palette: {
    mode: 'light',
    // Petrol/Teal — Header, Drawer-Active-State, primärer Button.
    primary: {
      main: '#3d8a98',
      light: '#6cb4c3',
      dark: '#256270',
      contrastText: '#ffffff',
    },
    secondary: { main: '#b5651d' },
    background: { default: '#f7f8fa' },
  },
  shape: { borderRadius: 8 },
})
