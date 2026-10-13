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
import { useKitNamen } from '../api/kitNamen'
import {
  standardLabelsApi,
  type StandardLabel,
  type StandardLabelErgebnis,
} from '../api/standardLabels'
import { useAuth } from '../auth/AuthContext'
import { useT } from '../i18n/SpracheContext'
import { labelErklaerung } from '../lib/labelErklaerung'
import { isPlatformAdmin } from '../lib/roles'
import { ErklaerungsText, LabelErklaerung } from './LabelErklaerung'
import { labelChipSx } from './labelChipSx'

/**
 * Standard-Labels der Installation (Issue #1486, nur Plattform-Admin): Der Knopf öffnet einen
 * Dialog mit dem festen Satz aus dem Backend (#1485) und legt ihn erst nach der Bestätigung auf
 * allen nicht archivierten Boards aller Projekte an. Das Anlegen ist idempotent; vorhandene Labels
 * bleiben unverändert. Für Nicht-Admins rendert der Abschnitt nichts.
 */
export function StandardLabelsSection() {
  const { user } = useAuth()
  const t = useT()
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
      .catch(() => setFehler(t.STANDARD_LABELS.LADEN_FEHLGESCHLAGEN))
  }

  const anlegen = async () => {
    setLaeuft(true)
    setFehler(null)
    try {
      setErgebnis(await standardLabelsApi.anlegen())
      setOffen(false)
    } catch (e) {
      setFehler(apiErrorMessage(e, t.STANDARD_LABELS.ANLEGEN_FEHLGESCHLAGEN))
    } finally {
      setLaeuft(false)
    }
  }

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={1.5}>
        <Typography variant="h6" component="h2">
          {t.STANDARD_LABELS.TITEL}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t.STANDARD_LABELS.HINWEIS}
        </Typography>
        <Stack direction="row">
          <Button variant="outlined" size="small" onClick={oeffnen}>
            {t.STANDARD_LABELS.KNOPF}
          </Button>
        </Stack>
        {ergebnis && (
          <Alert severity="success">
            {t.STANDARD_LABELS.ERGEBNIS(ergebnis.boards, ergebnis.angelegt, ergebnis.uebersprungen)}
          </Alert>
        )}
      </Stack>

      <Dialog open={offen} onClose={() => setOffen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{t.STANDARD_LABELS.KNOPF}</DialogTitle>
        <DialogContent>
          <Stack spacing={2}>
            <Typography variant="body2">
              {t.STANDARD_LABELS.DIALOG_HINWEIS}
            </Typography>
            {fehler && <Alert severity="error">{fehler}</Alert>}
            {satz && <SatzListe satz={satz} />}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOffen(false)}>{t.ALLGEMEIN.ABBRECHEN}</Button>
          <Button
            variant="contained"
            onClick={() => void anlegen()}
            disabled={satz === null || laeuft}
          >
            {t.ALLGEMEIN.ANLEGEN}
          </Button>
        </DialogActions>
      </Dialog>
    </Paper>
  )
}

/**
 * Der Satz nach Gruppen; unter jedem Label steht seine Erklärung als Text, damit der PO alle ohne
 * Darüberfahren liest (Issue #1672, Plan #1668 E9). Das Namen-Register lädt erst mit dem Dialog.
 */
function SatzListe({ satz }: Readonly<{ satz: StandardLabel[] }>) {
  const t = useT()
  const kitNamen = useKitNamen()
  return gruppieren(satz).map(([gruppe, labels]) => (
    <Stack key={gruppe} spacing={0.75}>
      <Typography variant="subtitle2" component="h3">
        {gruppe}
      </Typography>
      <Stack component="ul" spacing={1} sx={{ listStyle: 'none', m: 0, p: 0 }}>
        {labels.map((l) => {
          const teile = kitNamen.status === 'fertig' ? labelErklaerung(kitNamen.daten, l.name, t) : null
          return (
            <Stack key={l.name} component="li" spacing={0.5} sx={{ alignItems: 'flex-start' }}>
              <LabelErklaerung name={l.name}>
                <Chip size="small" label={l.name} sx={labelChipSx(l.farbe)} />
              </LabelErklaerung>
              {teile && <ErklaerungsText teile={teile} variant="body2" color="text.secondary" />}
            </Stack>
          )
        })}
      </Stack>
    </Stack>
  ))
}

/** Der Satz nach Gruppen, in der Reihenfolge, in der die Gruppen zuerst auftreten. */
function gruppieren(satz: StandardLabel[]): [string, StandardLabel[]][] {
  const gruppen = new Map<string, StandardLabel[]>()
  for (const label of satz) {
    gruppen.set(label.gruppe, [...(gruppen.get(label.gruppe) ?? []), label])
  }
  return [...gruppen.entries()]
}
