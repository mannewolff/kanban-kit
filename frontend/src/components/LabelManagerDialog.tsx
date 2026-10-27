import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Checkbox from '@mui/material/Checkbox'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import FormControlLabel from '@mui/material/FormControlLabel'
import IconButton from '@mui/material/IconButton'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined'
import { useState } from 'react'
import { apiErrorMessage } from '../api/client'
import { labelsApi as defaultLabelsApi, type Label, type LabelsApi } from '../api/labels'
import { useT } from '../i18n/SpracheContext'
import { dialogTitleSx } from './dialogChromeSx'
import { LabelErklaerung, useLabelErklaerung } from './LabelErklaerung'
import { useSnackbar } from './SnackbarProvider'
import { VorschauGesperrt } from './VorschauGesperrt'

interface Props {
  open: boolean
  boardId: number
  labels: Label[]
  onClose: () => void
  onChanged: () => void
  api?: Pick<LabelsApi, 'create' | 'update' | 'remove'>
}

const DEFAULT_COLOR = '#1976d2'

/**
 * Verwaltung der Board-Labels: anlegen, umbenennen/umfärben, löschen — und je Bestands-Label das
 * Häkchen „auf der Vorhaben-Kachel zählen" (#664). Genau dieser eine Handgriff ist der Grund, aus
 * dem die Einstellung am Label sitzt und nicht am Board (Entscheidung PO, #656 Frage 5).
 *
 * <p>Die **Anlege-Zeile bleibt bewusst ohne Häkchen**: Neue Labels starten mit dem Standard
 * `false` (Plan #657, E2) und werden anschließend über ihre Bestandszeile markiert; `POST` nimmt
 * das Feld ohnehin nicht an (#659).
 */
export function LabelManagerDialog({
  open,
  boardId,
  labels,
  onClose,
  onChanged,
  api = defaultLabelsApi,
}: Readonly<Props>) {
  const [newName, setNewName] = useState('')
  const [newColor, setNewColor] = useState(DEFAULT_COLOR)
  const [error, setError] = useState<string | null>(null)
  const notify = useSnackbar()
  const t = useT()

  // Zwei Fehlerkanäle mit Absicht: Die Anlege-Zeile ist ein Formular, ihre Meldung gehört neben
  // die Eingabe. Die Zeilen-Aktionen sind keins — dort gibt es kein Formular, an dem eine Meldung
  // hängen könnte, also der Toast.
  const create = async () => {
    if (!newName.trim()) return
    try {
      await api.create(boardId, newName.trim(), newColor)
      setNewName('')
      setNewColor(DEFAULT_COLOR)
      setError(null)
      onChanged()
    } catch (e) {
      setError(apiErrorMessage(e, t.LABELVERWALTUNG.ANLEGEN_FEHLER))
    }
  }

  /**
   * `countOnEpicTile` wird nur uebergeben, wenn das Haekchen umgeschaltet wurde -- beim blossen
   * Umbenennen bleibt es aus dem Aufruf heraus (Entscheidung Manne, 2026-08-31). Dass der
   * gespeicherte Wert dabei erhalten bleibt, leistet das Backend ueber "fehlend = unveraendert"
   * (#659); ein immer mitgesendetes Feld unterliefe genau diese Semantik.
   */
  const save = async (label: Label, name: string, color: string, countOnEpicTile?: boolean) => {
    try {
      await (countOnEpicTile === undefined
        ? api.update(label.id, name.trim(), color)
        : api.update(label.id, name.trim(), color, countOnEpicTile))
      onChanged()
    } catch (e) {
      notify(apiErrorMessage(e, t.LABELVERWALTUNG.SPEICHERN_FEHLER), 'error')
    }
  }

  const remove = async (label: Label) => {
    try {
      await api.remove(label.id)
      onChanged()
    } catch (e) {
      notify(apiErrorMessage(e, t.LABELVERWALTUNG.LOESCHEN_FEHLER), 'error')
    }
  }

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={dialogTitleSx}>{t.LABELVERWALTUNG.TITEL}</DialogTitle>
      <DialogContent>
        <Stack spacing={1.5} sx={{ mt: 1 }}>
          {labels.map((label) => (
            <LabelRow key={label.id} label={label} onSave={save} onDelete={remove} />
          ))}
          {labels.length === 0 && (
            <Typography color="text.secondary">{t.LABELVERWALTUNG.LEER}</Typography>
          )}

          <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1 }}>
            <TextField
              size="small"
              label={t.LABELVERWALTUNG.NEUES_LABEL}
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              slotProps={{ htmlInput: { maxLength: 60, 'aria-label': t.LABELVERWALTUNG.NEUES_LABEL } }}
            />
            <Box
              component="input"
              type="color"
              aria-label={t.LABELVERWALTUNG.NEUE_FARBE}
              value={newColor}
              onChange={(e) => setNewColor((e.target as HTMLInputElement).value)}
              sx={{ width: 40, height: 36, border: 'none', background: 'none' }}
            />
            <VorschauGesperrt>
              <Button variant="contained" size="small" onClick={() => void create()}>
                {t.ALLGEMEIN.ANLEGEN}
              </Button>
            </VorschauGesperrt>
          </Stack>
          {error && (
            <Typography color="error" variant="caption">
              {error}
            </Typography>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t.ALLGEMEIN.SCHLIESSEN}</Button>
      </DialogActions>
    </Dialog>
  )
}

function LabelRow({
  label,
  onSave,
  onDelete,
}: Readonly<{
  label: Label
  onSave: (label: Label, name: string, color: string, countOnEpicTile?: boolean) => Promise<void>
  onDelete: (label: Label) => Promise<void>
}>) {
  const t = useT()
  const systemlabel = useLabelErklaerung(label.name) !== null
  const [name, setName] = useState(label.name)
  const [color, setColor] = useState(label.color)
  const [zaehlt, setZaehlt] = useState(label.countOnEpicTile)
  const zaehltGeaendert = zaehlt !== label.countOnEpicTile
  // Dieselbe `dirty`-Erkennung wie bei Name und Farbe: Ein versehentlicher Klick auf das Haekchen
  // schreibt nichts, erst "Speichern" wirkt.
  const dirty = name !== label.name || color !== label.color || zaehltGeaendert

  return (
    <Stack spacing={0.5}>
      <Stack direction="row" spacing={1} alignItems="center">
        <TextField
          size="small"
          value={name}
          onChange={(e) => setName(e.target.value)}
          slotProps={{ htmlInput: { maxLength: 60, 'aria-label': t.LABELVERWALTUNG.LABEL_ARIA(label.name) } }}
        />
        {/* Die Erklärung hängt an einem Info-Symbol neben dem Namen, nicht am Feld: Ein Tooltip über
            einem Feld, in dem man gerade schreibt, stört (Plan #1668, E8). Maßgeblich ist der
            gespeicherte Name, nicht der Entwurf im Feld. */}
        {systemlabel && (
          <LabelErklaerung name={label.name} eigenerFokus>
            <IconButton size="small" aria-label={t.LABELVERWALTUNG.ERKLAERUNG_ARIA(label.name)}>
              <InfoOutlinedIcon fontSize="small" />
            </IconButton>
          </LabelErklaerung>
        )}
        <Box
          component="input"
          type="color"
          aria-label={t.LABELVERWALTUNG.FARBE_ARIA(label.name)}
          value={color}
          onChange={(e) => setColor((e.target as HTMLInputElement).value)}
          sx={{ width: 40, height: 36, border: 'none', background: 'none' }}
        />
        <VorschauGesperrt>
          <Button
            size="small"
            disabled={!dirty || !name.trim()}
            onClick={() => void onSave(label, name, color, zaehltGeaendert ? zaehlt : undefined)}
          >
            {t.ALLGEMEIN.SPEICHERN}
          </Button>
        </VorschauGesperrt>
        <VorschauGesperrt>
          <IconButton size="small" aria-label={t.LABELVERWALTUNG.LOESCHEN_ARIA(label.name)} onClick={() => void onDelete(label)}>
            ✕
          </IconButton>
        </VorschauGesperrt>
      </Stack>
      {/* `FormControlLabel` verknuepft die Beschriftung ueber ein echtes <label> mit der Box: der
          Text steht sichtbar im DOM. Ein blosses `aria-label` waere unsichtbar, und `jsx-a11y`
          prueft MUI-Kompositionen nicht zuverlaessig — der gruene Lint belegte die Beschriftung
          also nicht.

          Der zugaengliche Name traegt zusaetzlich den Labelnamen: Ohne ihn hiessen bei mehreren
          Labels alle Haekchen gleich, und wer per Screenreader navigiert, koennte sie nicht
          auseinanderhalten. Der sichtbare Text bleibt kurz — er wiederholte sich sonst in jeder
          Zeile, obwohl die Zeile den Bezug optisch ohnehin herstellt. Der sichtbare Text ist
          Teilstring des zugaenglichen Namens, WCAG 2.5.3 (Label in Name) bleibt gewahrt. */}
      <FormControlLabel
        control={
          <Checkbox
            size="small"
            checked={zaehlt}
            onChange={(e) => setZaehlt(e.target.checked)}
            slotProps={{ input: { 'aria-label': t.LABELVERWALTUNG.ZAEHLEN_ARIA(label.name) } }}
          />
        }
        label={t.LABELVERWALTUNG.ZAEHLEN}
        slotProps={{ typography: { variant: 'caption', color: 'text.secondary' } }}
      />
    </Stack>
  )
}
