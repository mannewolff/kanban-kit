import CssBaseline from '@mui/material/CssBaseline'
import { ThemeProvider, createTheme } from '@mui/material/styles'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'

const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: '#2f6f4f' },
  },
})

const container = document.getElementById('root')
if (!container) {
  throw new Error('Root-Element #root nicht gefunden')
}

createRoot(container).render(
  <StrictMode>
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <App />
    </ThemeProvider>
  </StrictMode>,
)
