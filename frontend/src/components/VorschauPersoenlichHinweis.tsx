import Alert from '@mui/material/Alert'
import { useT } from '../i18n/SpracheContext'

/**
 * Hinweis statt Profil, persönlicher API-Tokens und Farbwahl von XY in der „Ansicht als XY“
 * (AK 5, Plan #1914, E5): Der Bereich wird in der Vorschau weder gezeigt noch geändert. Die Seite
 * entscheidet, ob die Vorschau läuft; der Baustein rendert immer.
 */
export function VorschauPersoenlichHinweis() {
  const t = useT()
  return (
    <Alert severity="info" variant="outlined" role="note">
      {t.VORSCHAU.PERSOENLICH}
    </Alert>
  )
}
