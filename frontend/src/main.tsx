// Schriften der Kupferwarte (#978), offline ausgeliefert: Archivo mit variabler Breite und
// variablem Gewicht für Titel und Etiketten, IBM Plex Sans für Fließtext, IBM Plex Mono für Zahlen.
import '@fontsource-variable/archivo/wdth.css'
import '@fontsource/ibm-plex-sans/latin-400.css'
import '@fontsource/ibm-plex-sans/latin-500.css'
import '@fontsource/ibm-plex-sans/latin-600.css'
import '@fontsource/ibm-plex-sans/latin-700.css'
import '@fontsource/ibm-plex-mono/latin-400.css'
import '@fontsource/ibm-plex-mono/latin-500.css'
import '@fontsource/ibm-plex-mono/latin-600.css'
import CssBaseline from '@mui/material/CssBaseline'
import { ThemeProvider } from '@mui/material/styles'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from './App'
import { AuthProvider } from './auth/AuthContext'
import { SnackbarProvider } from './components/SnackbarProvider'
import { EditModeProvider } from './lib/EditModeContext'
import { theme } from './theme'

const container = document.getElementById('root')
if (!container) {
  throw new Error('Root-Element #root nicht gefunden')
}

createRoot(container).render(
  <StrictMode>
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <AuthProvider>
          <EditModeProvider>
            <SnackbarProvider>
              <App />
            </SnackbarProvider>
          </EditModeProvider>
        </AuthProvider>
      </BrowserRouter>
    </ThemeProvider>
  </StrictMode>,
)
