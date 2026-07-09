import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import { useEffect, useState } from 'react'

const BODY_TEMPLATE = '## Kontext\n\n## Aufgabe\n\n## Akzeptanzkriterium\n\n## Abhängigkeiten\n'

interface Props {
  open: boolean
  columnName: string
  onClose: () => void
  onSubmit: (title: string, description: string) => Promise<void> | void
}

/**
 * Zentrierter Anlage-Dialog für neue Karten (Port von KanbanNewItemModal): Titel (Pflicht)
 * + Beschreibung mit vierteiliger Markdown-Vorlage. Ersetzt das versteckte Enter-only-Feld.
 */
export function NewCardModal({ open, columnName, onClose, onSubmit }: Props) {
  const [title, setTitle] = useState('')
  const [body, setBody] = useState(BODY_TEMPLATE)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) return
    setTitle('')
    setBody(BODY_TEMPLATE)
    setSaving(false)
  }, [open])

  const canSubmit = title.trim().length > 0 && !saving

  const handleCreate = async () => {
    if (!canSubmit) return
    setSaving(true)
    try {
      await onSubmit(title.trim(), body)
      onClose()
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth aria-labelledby="new-card-title">
      <DialogTitle id="new-card-title">Neue Karte in „{columnName}“</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          <TextField
            label="Titel"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
            autoFocus
            fullWidth
            inputProps={{ maxLength: 300, 'aria-label': 'Titel' }}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) void handleCreate()
            }}
          />
          <TextField
            label="Beschreibung"
            value={body}
            onChange={(e) => setBody(e.target.value)}
            multiline
            rows={8}
            fullWidth
            inputProps={{ maxLength: 10_000, 'aria-label': 'Beschreibung' }}
            sx={{ '& textarea': { fontFamily: 'monospace', resize: 'vertical' } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Abbrechen</Button>
        <Button variant="contained" onClick={() => void handleCreate()} disabled={!canSubmit}>
          Anlegen
        </Button>
      </DialogActions>
    </Dialog>
  )
}
