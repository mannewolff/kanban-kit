import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useState } from 'react'
import { labelsApi as defaultLabelsApi, type Label, type LabelsApi } from '../api/labels'

interface Props {
  open: boolean
  boardId: number
  labels: Label[]
  onClose: () => void
  onChanged: () => void
  api?: Pick<LabelsApi, 'create' | 'update' | 'remove'>
}

const DEFAULT_COLOR = '#1976d2'

/** Verwaltung der Board-Labels: anlegen, umbenennen/umfärben, löschen. */
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

  const save = async (label: Label, name: string, color: string) => {
    await api.update(label.id, name.trim(), color)
    onChanged()
  }

  const remove = async (label: Label) => {
    await api.remove(label.id)
    onChanged()
  }

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Labels verwalten</DialogTitle>
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
  onSave: (label: Label, name: string, color: string) => Promise<void>
  onDelete: (label: Label) => Promise<void>
}>) {
  const [name, setName] = useState(label.name)
  const [color, setColor] = useState(label.color)
  const dirty = name !== label.name || color !== label.color

  return (
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
      <Button size="small" disabled={!dirty || !name.trim()} onClick={() => void onSave(label, name, color)}>
        Speichern
      </Button>
      <IconButton size="small" aria-label={`Label ${label.name} löschen`} onClick={() => void onDelete(label)}>
        ✕
      </IconButton>
    </Stack>
  )
}
