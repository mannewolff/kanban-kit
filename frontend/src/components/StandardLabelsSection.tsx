import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useState } from 'react'
import { apiErrorMessage } from '../api/client'
import {
  standardLabelsApi,
  type StandardLabel,
  type StandardLabelErgebnis,
} from '../api/standardLabels'
import { useAuth } from '../auth/AuthContext'
import { isPlatformAdmin } from '../lib/roles'
import { labelChipSx } from './labelChipSx'

/**
 * Standard-Labels der Installation (Issue #1486, nur Plattform-Admin): Der Knopf öffnet einen
 * Dialog mit dem festen Satz aus dem Backend (#1485) und legt ihn erst nach der Bestätigung auf
 * allen nicht archivierten Boards aller Projekte an. Das Anlegen ist idempotent; vorhandene Labels
 * bleiben unverändert. Für Nicht-Admins rendert der Abschnitt nichts.
 */
export function StandardLabelsSection() {
  const { user } = useAuth()
  const [offen, setOffen] = useState(false)
  const [satz, setSatz] = useState<StandardLabel[] | null>(null)
  const [fehler, setFehler] = useState<string | null>(null)
  const [laeuft, setLaeuft] = useState(false)
  const [ergebnis, setErgebnis] = useState<StandardLabelErgebnis | null>(null)

  if (!isPlatformAdmin(user)) {
    return null
  }

  const oeffnen = () => {
    setOffen(true)
    setSatz(null)
    setFehler(null)
    standardLabelsApi
      .satz()
      .then(setSatz)
      .catch(() => setFehler('Die Standard-Labels konnten nicht geladen werden.'))
  }

  const anlegen = async () => {
    setLaeuft(true)
    setFehler(null)
    try {
      setErgebnis(await standardLabelsApi.anlegen())
      setOffen(false)
    } catch (e) {
      setFehler(apiErrorMessage(e, 'Die Standard-Labels konnten nicht angelegt werden.'))
    } finally {
      setLaeuft(false)
    }
  }

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={1.5}>
        <Typography variant="h6" component="h2">
          Standard-Labels
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Legt die Labels, die Kit, Laufsteuerung und Stufenleiste erwarten, auf allen Boards aller
          Projekte an. Fehlende kommen dazu, vorhandene bleiben, wie sie sind — der Knopf lässt sich
          beliebig oft drücken.
        </Typography>
        <Stack direction="row">
          <Button variant="outlined" size="small" onClick={oeffnen}>
            Standard-Labels für alle Boards
          </Button>
        </Stack>
        {ergebnis && (
          <Alert severity="success">
            Auf {ergebnis.boards} Boards: {ergebnis.angelegt} Labels neu angelegt,{' '}
            {ergebnis.uebersprungen} schon vorhanden.
          </Alert>
        )}
      </Stack>

      <Dialog open={offen} onClose={() => setOffen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Standard-Labels für alle Boards</DialogTitle>
        <DialogContent>
          <Stack spacing={2}>
            <Typography variant="body2">
              Diese Labels werden auf allen nicht archivierten Boards aller Projekte angelegt.
              Vorhandene Labels bleiben unverändert und werden übersprungen.
            </Typography>
            {fehler && <Alert severity="error">{fehler}</Alert>}
            {satz &&
              gruppieren(satz).map(([gruppe, labels]) => (
                <Stack key={gruppe} spacing={0.75}>
                  <Typography variant="subtitle2" component="h3">
                    {gruppe}
                  </Typography>
                  <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.75 }}>
                    {labels.map((l) => (
                      <Chip key={l.name} size="small" label={l.name} sx={labelChipSx(l.farbe)} />
                    ))}
                  </Stack>
                </Stack>
              ))}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOffen(false)}>Abbrechen</Button>
          <Button
            variant="contained"
            onClick={() => void anlegen()}
            disabled={satz === null || laeuft}
          >
            Anlegen
          </Button>
        </DialogActions>
      </Dialog>
    </Paper>
  )
}

/** Der Satz nach Gruppen, in der Reihenfolge, in der die Gruppen zuerst auftreten. */
function gruppieren(satz: StandardLabel[]): [string, StandardLabel[]][] {
  const gruppen = new Map<string, StandardLabel[]>()
  for (const label of satz) {
    gruppen.set(label.gruppe, [...(gruppen.get(label.gruppe) ?? []), label])
  }
  return [...gruppen.entries()]
}
