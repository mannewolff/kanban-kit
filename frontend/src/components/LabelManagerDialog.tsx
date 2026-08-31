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
import { useState } from 'react'
import { labelsApi as defaultLabelsApi, type Label, type LabelsApi } from '../api/labels'
import { dialogTitleSx } from './dialogChromeSx'

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

  const create = async () => {
    if (!newName.trim()) return
    try {
      await api.create(boardId, newName.trim(), newColor)
      setNewName('')
      setNewColor(DEFAULT_COLOR)
      setError(null)
      onChanged()
    } catch {
      setError('Label konnte nicht angelegt werden (evtl. Name bereits vergeben).')
    }
  }

  /**
   * `countOnEpicTile` wird nur uebergeben, wenn das Haekchen umgeschaltet wurde -- beim blossen
   * Umbenennen bleibt es aus dem Aufruf heraus (Entscheidung Manne, 2026-08-31). Dass der
   * gespeicherte Wert dabei erhalten bleibt, leistet das Backend ueber "fehlend = unveraendert"
   * (#659); ein immer mitgesendetes Feld unterliefe genau diese Semantik.
   */
  const save = async (label: Label, name: string, color: string, countOnEpicTile?: boolean) => {
    await (countOnEpicTile === undefined
      ? api.update(label.id, name.trim(), color)
      : api.update(label.id, name.trim(), color, countOnEpicTile))
    onChanged()
  }

  const remove = async (label: Label) => {
    await api.remove(label.id)
    onChanged()
  }

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={dialogTitleSx}>Labels verwalten</DialogTitle>
      <DialogContent>
        <Stack spacing={1.5} sx={{ mt: 1 }}>
          {labels.map((label) => (
            <LabelRow key={label.id} label={label} onSave={save} onDelete={remove} />
          ))}
          {labels.length === 0 && (
            <Typography color="text.secondary">Noch keine Labels.</Typography>
          )}

          <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1 }}>
            <TextField
              size="small"
              label="Neues Label"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              slotProps={{ htmlInput: { maxLength: 60, 'aria-label': 'Neues Label' } }}
            />
            <Box
              component="input"
              type="color"
              aria-label="Neue Label-Farbe"
              value={newColor}
              onChange={(e) => setNewColor((e.target as HTMLInputElement).value)}
              sx={{ width: 40, height: 36, border: 'none', background: 'none' }}
            />
            <Button variant="contained" size="small" onClick={() => void create()}>
              Anlegen
            </Button>
          </Stack>
          {error && (
            <Typography color="error" variant="caption">
              {error}
            </Typography>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Schließen</Button>
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
          slotProps={{ htmlInput: { maxLength: 60, 'aria-label': `Label ${label.name}` } }}
        />
        <Box
          component="input"
          type="color"
          aria-label={`Farbe ${label.name}`}
          value={color}
          onChange={(e) => setColor((e.target as HTMLInputElement).value)}
          sx={{ width: 40, height: 36, border: 'none', background: 'none' }}
        />
        <Button
          size="small"
          disabled={!dirty || !name.trim()}
          onClick={() => void onSave(label, name, color, zaehltGeaendert ? zaehlt : undefined)}
        >
          Speichern
        </Button>
        <IconButton size="small" aria-label={`Label ${label.name} löschen`} onClick={() => void onDelete(label)}>
          ✕
        </IconButton>
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
            slotProps={{ input: { 'aria-label': `${label.name}: auf der Vorhaben-Kachel zählen` } }}
          />
        }
        label="auf der Vorhaben-Kachel zählen"
        slotProps={{ typography: { variant: 'caption', color: 'text.secondary' } }}
      />
    </Stack>
  )
}
