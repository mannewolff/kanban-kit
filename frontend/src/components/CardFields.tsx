import TextField from '@mui/material/TextField'
import type { Epic } from '../api/epics'
import { epicShortcode } from '../lib/epicMeta'

/**
 * Kontrollierte, präsentationale Karten-Felder (Werte + onChange als Props, keine eigene
 * Persistenz). Für `EPIC` das Kürzel, für `CARD` Epic-Zuordnung/Abhängigkeiten/Fälligkeit — jeweils
 * plus Titel und Beschreibung. Gemeinsame Basis von Anlege- und Bearbeiten-Formular, damit beide
 * nicht auseinanderlaufen. Zuständige/Labels bleiben eigene Sektionen (sie speichern sofort).
 */
export function CardFields({
  isEpic,
  title,
  body,
  shortcode,
  parentId,
  epics,
  depsInput,
  depsError,
  dueInput,
  onTitleChange,
  onBodyChange,
  onShortcodeChange,
  onParentIdChange,
  onDepsInputChange,
  onDueInputChange,
}: Readonly<{
  isEpic: boolean
  title: string
  body: string
  shortcode: string
  parentId: number | null
  epics: Epic[]
  depsInput: string
  depsError: string | null
  dueInput: string
  onTitleChange: (value: string) => void
  onBodyChange: (value: string) => void
  onShortcodeChange: (value: string) => void
  onParentIdChange: (value: number | null) => void
  onDepsInputChange: (value: string) => void
  onDueInputChange: (value: string) => void
}>) {
  const nonEpicFields = (
    <>
      <TextField
        select
        label="Epic"
        value={parentId ?? ''}
        onChange={(e) => onParentIdChange(e.target.value === '' ? null : Number(e.target.value))}
        slotProps={{
          htmlInput: { 'aria-label': 'Epic' },
          select: { native: true },
          inputLabel: { shrink: true },
        }}
        fullWidth
      >
        <option value="">(kein Epic)</option>
        {epics.map((epic) => (
          <option key={epic.id} value={epic.id}>
            {epicShortcode(epic.title, epic.shortcode)} – {epic.title}
          </option>
        ))}
      </TextField>
      <TextField
        label="Abhängig von (Nummern, kommagetrennt)"
        value={depsInput}
        onChange={(e) => onDepsInputChange(e.target.value)}
        error={depsError != null}
        helperText={depsError ?? 'z. B. 12, 34'}
        slotProps={{ htmlInput: { 'aria-label': 'Abhängig von' } }}
        fullWidth
      />
      <TextField
        type="date"
        label="Fällig am"
        value={dueInput}
        onChange={(e) => onDueInputChange(e.target.value)}
        slotProps={{
          htmlInput: { 'aria-label': 'Fällig am' },
          inputLabel: { shrink: true },
        }}
        sx={{ maxWidth: 200 }}
      />
    </>
  )
  return (
    <>
      <TextField
        label="Titel"
        value={title}
        onChange={(e) => onTitleChange(e.target.value)}
        required
        autoFocus
        fullWidth
        slotProps={{ htmlInput: { maxLength: 300, 'aria-label': 'Titel' } }}
      />
      <TextField
        label="Markdown-Beschreibung"
        value={body}
        onChange={(e) => onBodyChange(e.target.value)}
        multiline
        rows={8}
        fullWidth
        slotProps={{ htmlInput: { maxLength: 10_000, 'aria-label': 'Markdown-Beschreibung' } }}
        sx={{ '& textarea': { fontFamily: 'monospace', resize: 'vertical' } }}
      />
      {isEpic ? (
        <TextField
          label="Kürzel"
          value={shortcode}
          onChange={(e) => onShortcodeChange(e.target.value)}
          slotProps={{ htmlInput: { maxLength: 16, 'aria-label': 'Kürzel' } }}
          sx={{ maxWidth: 200 }}
        />
      ) : (
        nonEpicFields
      )}
    </>
  )
}
