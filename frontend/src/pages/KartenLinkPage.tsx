import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import { useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { cardsApi } from '../api/cards'
import { useSnackbar } from '../components/SnackbarProvider'
import { useT } from '../i18n/SpracheContext'

/** Eine positive Ganzzahl aus einem Pfadsegment, sonst `null` — `12abc` ist keine Nummer. */
const positiveZahl = (wert: string | undefined): number | null =>
  wert != null && /^\d+$/.test(wert) && Number(wert) > 0 ? Number(wert) : null

/**
 * Der direkte Link auf eine Karte: `/projects/:projectId/karten/:nummer` (Issue #1915).
 *
 * Die Kartennummer ist im Projekt fest, das Board kann sich durch Verschieben ändern — darum löst
 * die Seite die Nummer erst beim Aufruf auf und leitet auf `/boards/{boardId}?karte={nummer}`
 * weiter, wo das Board den Dialog öffnet. Ist die Karte nicht erreichbar, geht es mit derselben
 * Meldung auf die Projektseite, gleich aus welchem Grund: Ein Nichtmitglied soll nicht erfahren,
 * ob es die Karte gibt.
 *
 * **Der Name endet auf `Redirect`:** `designQuelle.test.ts` nimmt Weiterleitungen an diesem Suffix
 * von der Tabelle „Ansicht → Regel" in `CLAUDE-design.md` aus.
 */
export function KartenLinkRedirect() {
  const params = useParams()
  const projectId = positiveZahl(params.projectId)
  const nummer = positiveZahl(params.nummer)
  const navigate = useNavigate()
  const notify = useSnackbar()
  const t = useT()

  useEffect(() => {
    if (projectId == null || nummer == null) {
      return
    }
    let aktiv = true
    cardsApi
      .byNumber(projectId, nummer)
      .then((karte) => {
        if (aktiv) {
          navigate(`/boards/${karte.boardId}?karte=${nummer}`, { replace: true })
        }
      })
      .catch(() => {
        if (aktiv) {
          notify(t.KARTE.NICHT_ERREICHBAR(nummer), 'warning')
          navigate(`/projects/${projectId}`, { replace: true })
        }
      })
    return () => {
      aktiv = false
    }
  }, [projectId, nummer, navigate, notify, t])

  if (projectId == null || nummer == null) {
    return <Alert severity="error">{t.KARTE.LINK_UNGUELTIG}</Alert>
  }
  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
      <CircularProgress />
    </Box>
  )
}
