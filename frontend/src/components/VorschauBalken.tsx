import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import { useState } from 'react'
import { authApi } from '../api/auth'
import { apiErrorMessage, startseiteNeuLaden } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useT } from '../i18n/SpracheContext'
import { KUPFER, PLATTE_HOCH, SCHATTEN_PLATTE } from '../theme'
import { useSnackbar } from './SnackbarProvider'

/** Höhe des Vorschau-Balkens; AppShell schiebt Kopf und Inhalt um diesen Wert nach unten. */
export const VORSCHAU_BALKEN_HEIGHT = 40

interface VorschauBalkenProps {
  /** Abstand zum oberen Rand — unter dem Band des Editiermodus, wenn beide stehen. */
  oben: number
}

/**
 * Fester Balken auf jeder Seite, solange die nur lesende „Ansicht als XY“ läuft (AK 7, Plan #1914,
 * E14): Name von XY, „Nur lesend“ und „Beenden“. Rendert nichts außerhalb der Vorschau.
 *
 * Grund ist eine Platte mit kupferner Unterkante: Die Schrift steht in der Grundtinte auf der Platte
 * und hält AA in beiden Erscheinungsbildern; die Melderfarben tragen nur Zustände von Läufen.
 */
export function VorschauBalken({ oben }: Readonly<VorschauBalkenProps>) {
  const { vorschau } = useAuth()
  const t = useT()
  const notify = useSnackbar()
  const [beendet, setBeendet] = useState(false)
  if (!vorschau) {
    return null
  }

  // Nach dem Beenden lädt die Oberfläche auf `/` neu, wo `StartRedirect` die eigene Startseite des
  // Admins wählt (E13); bis dahin bleibt der Knopf gesperrt, damit kein zweiter Aufruf folgt.
  const beenden = async () => {
    setBeendet(true)
    try {
      await authApi.vorschauBeenden()
      startseiteNeuLaden()
    } catch (e) {
      setBeendet(false)
      notify(apiErrorMessage(e, t.VORSCHAU.BEENDEN_FEHLGESCHLAGEN), 'error')
    }
  }

  return (
    <Box
      role="status"
      aria-label={t.VORSCHAU.BALKEN_BEREICH(vorschau.zielName)}
      sx={{
        position: 'fixed',
        top: oben,
        left: 0,
        right: 0,
        height: VORSCHAU_BALKEN_HEIGHT,
        boxSizing: 'border-box',
        zIndex: (theme) => theme.zIndex.drawer + 2,
        bgcolor: PLATTE_HOCH,
        color: 'text.primary',
        borderBottom: 2,
        borderColor: KUPFER,
        boxShadow: SCHATTEN_PLATTE,
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        px: 2,
      }}
    >
      <VisibilityOutlinedIcon fontSize="small" sx={{ color: KUPFER }} />
      <Typography variant="body2" sx={{ fontWeight: 600, flexGrow: 1 }}>
        {t.VORSCHAU.BALKEN(vorschau.zielName)}
      </Typography>
      <Button variant="contained" size="small" disabled={beendet} onClick={() => void beenden()}>
        {t.VORSCHAU.BEENDEN}
      </Button>
    </Box>
  )
}
