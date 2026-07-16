import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import { useEffect, useRef, useState } from 'react'
import type { CardType } from '../api/cards'
import type { Epic } from '../api/epics'
import { epicShortcode } from '../lib/epicMeta'

const BODY_TEMPLATE = '## Kontext\n\n## Aufgabe\n\n## Akzeptanzkriterium\n\n## Abhängigkeiten\n'

export interface NewItemInput {
  type: CardType
  title: string
  description: string
  parentId: number | null
  shortcode: string | null
}

/** Vorbefüllung für „Duplizieren": Titel/Beschreibung/Epic-Zuordnung der Quellkarte. */
export interface NewCardInitialValues {
  title: string
  description: string
  parentId: number | null
}

interface Props {
  open: boolean
  columnName: string
  epics: Epic[]
  onClose: () => void
  onSubmit: (input: NewItemInput) => Promise<void> | void
  /** Nur Epic anlegen: Typ vorbelegt EPIC, ohne Typ-/Zuordnungs-Auswahl (für die Epics-Ansicht). */
  epicOnly?: boolean
  /** Vorbefüllung für „Duplizieren"; ohne Angabe startet der Dialog leer. */
  initialValues?: NewCardInitialValues
}

/**
 * Zentrierter Anlage-Dialog für neue Karten und Epics (Port von KanbanNewItemModal):
 * Typ (Karte/Epic); bei Karte optionale Epic-Zuordnung, bei Epic optionales Kürzel;
 * Titel (Pflicht) + Beschreibung mit vierteiliger Markdown-Vorlage.
 */
export function NewCardModal({
  open,
  columnName,
  epics,
  onClose,
  onSubmit,
  epicOnly = false,
  initialValues,
}: Readonly<Props>) {
  const [type, setType] = useState<CardType>(epicOnly ? 'EPIC' : 'CARD')
  const [title, setTitle] = useState('')
  const [body, setBody] = useState(BODY_TEMPLATE)
  const [parentId, setParentId] = useState<number | null>(null)
  const [shortcode, setShortcode] = useState('')
  const [saving, setSaving] = useState(false)
  const titleInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!open) return
    setType(epicOnly ? 'EPIC' : 'CARD')
    setTitle(initialValues?.title ?? '')
    setBody(initialValues?.description ?? BODY_TEMPLATE)
    setParentId(initialValues?.parentId ?? null)
    setShortcode('')
    setSaving(false)
    // Titel selektieren, damit ein Überschreiben (z. B. beim Duplizieren) ohne Löschen möglich ist.
    titleInputRef.current?.select()
  }, [open, epicOnly, initialValues])

  const canSubmit = title.trim().length > 0 && !saving

  const handleCreate = async () => {
    if (!canSubmit) return
    setSaving(true)
    try {
      await onSubmit({
        type,
        title: title.trim(),
        description: body,
        parentId: type === 'CARD' ? parentId : null,
        shortcode: type === 'EPIC' ? (shortcode.trim() || null) : null,
      })
      onClose()
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth={false}
      aria-labelledby="new-card-title"
      // Im Kontextbereich zentrieren, 90 % Breite/Höhe — identisch zum Kartendetail-Dialog
      // (CardDetailModal.tsx), damit Anlegen und Bearbeiten optisch konsistent sind.
      sx={{
        '& .MuiDialog-container': {
          position: 'absolute',
          top: 'var(--app-content-top, 0px)',
          left: 'var(--app-content-left, 0px)',
          right: 0,
          bottom: 0,
          height: 'auto',
        },
      }}
      slotProps={{
        paper: { sx: { width: '90%', maxWidth: '90%', height: '90%', maxHeight: '90%', m: 0 } },
      }}
    >
      <DialogTitle id="new-card-title">
        {type === 'EPIC' ? 'Neues Epic' : `Neue Karte in „${columnName}“`}
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {!epicOnly && (
            <TextField
              select
              SelectProps={{ native: true }}
              label="Typ"
              value={type}
              onChange={(e) => setType(e.target.value as CardType)}
              slotProps={{ htmlInput: { 'aria-label': 'Typ' } }}
              fullWidth
            >
              <option value="CARD">Karte</option>
              <option value="EPIC">Epic</option>
            </TextField>
          )}

          {type === 'CARD' && (
            <TextField
              select
              SelectProps={{ native: true }}
              label="Epic"
              value={parentId ?? ''}
              onChange={(e) => setParentId(e.target.value === '' ? null : Number(e.target.value))}
              slotProps={{ htmlInput: { 'aria-label': 'Epic' } }}
              InputLabelProps={{ shrink: true }}
              fullWidth
            >
              <option value="">(kein Epic)</option>
              {epics.map((epic) => (
                <option key={epic.id} value={epic.id}>
                  {epicShortcode(epic.title, epic.shortcode)} – {epic.title}
                </option>
              ))}
            </TextField>
          )}

          {type === 'EPIC' && (
            <TextField
              label="Kürzel (optional)"
              value={shortcode}
              onChange={(e) => setShortcode(e.target.value)}
              placeholder={epicShortcode(title)}
              helperText="Leer lassen, um es aus dem Titel abzuleiten."
              slotProps={{ htmlInput: { maxLength: 16, 'aria-label': 'Kürzel' } }}
              fullWidth
            />
          )}

          <TextField
            label="Titel"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
            autoFocus
            fullWidth
            inputRef={titleInputRef}
            slotProps={{ htmlInput: { maxLength: 300, 'aria-label': 'Titel' } }}
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
            slotProps={{ htmlInput: { maxLength: 10_000, 'aria-label': 'Beschreibung' } }}
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
