import Alert from '@mui/material/Alert'
import { useAuth } from '../auth/AuthContext'
import { useT } from '../i18n/SpracheContext'

/**
 * Hinweis statt Inhalt an einer Stelle, die XY sähe, die Vorschau aber nicht zeigt (Plan #1914, E16):
 * Der Server antwortet dort mit `403 vorschau-verborgen`, weil der Admin selbst den Bereich nicht sehen
 * darf. Leere Daten sähen aus wie „XY hat hier nichts“ — darum nennt der Hinweis XY beim Namen.
 * Rendert nichts außerhalb der Vorschau.
 */
export function VorschauVerborgenHinweis() {
  const { vorschau } = useAuth()
  const t = useT()
  if (!vorschau) {
    return null
  }
  return (
    <Alert severity="info" variant="outlined" role="note">
      {t.VORSCHAU.VERBORGEN(vorschau.zielName)}
    </Alert>
  )
}
