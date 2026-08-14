import TextField from '@mui/material/TextField'
import type { KeyboardEvent, Ref } from 'react'
import type { Epic } from '../api/epics'
import { epicShortcode } from '../lib/epicMeta'
import { useCheckboxShortcut } from '../lib/useCheckboxShortcut'
import { isTooLong, tooLongMessage } from '../lib/textLimits'

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
  epicReadOnly = false,
  depsInput,
  depsError,
  dueInput,
  onTitleChange,
  onBodyChange,
  onShortcodeChange,
  onParentIdChange,
  onDepsInputChange,
  onDueInputChange,
  titleInputRef,
  onTitleKeyDown,
}: Readonly<{
  isEpic: boolean
  title: string
  body: string
  shortcode: string
  parentId: number | null
  epics: Epic[]
  /**
   * Zeigt die Epic-Zuordnung nur an, statt sie zur Auswahl zu stellen. Für Aufrufer, die den
   * Optionsvorrat nicht laden können (archiviertes Board, board-lose Idee): Das Dropdown böte dann
   * ausschließlich „(kein Epic)" an, und ein Klick darauf löschte eine bestehende Zuordnung, ohne
   * sie je gezeigt zu haben (#586).
   */
  epicReadOnly?: boolean
  depsInput: string
  depsError: string | null
  dueInput: string
  onTitleChange: (value: string) => void
  onBodyChange: (value: string) => void
  onShortcodeChange: (value: string) => void
  onParentIdChange: (value: number | null) => void
  onDepsInputChange: (value: string) => void
  onDueInputChange: (value: string) => void
  /** Optionaler Ref auf das Titel-Input (Anlege-Dialog selektiert den Titel beim Öffnen). */
  titleInputRef?: Ref<HTMLInputElement>
  /** Optionaler KeyDown-Handler am Titel (Anlege-Dialog: Cmd/Ctrl+Enter legt an). */
  onTitleKeyDown?: (event: KeyboardEvent) => void
}>) {
  const bodyShortcut = useCheckboxShortcut(onBodyChange)
  const nonEpicFields = (
    <>
      {epicReadOnly ? (
        // Ohne Epic-Liste bleibt nur die nackte ID: Sie belegt sichtbar, dass eine Zuordnung
        // besteht, und hält sie zugleich außer Reichweite jeder versehentlichen Änderung.
        <TextField
          label="Epic"
          value={parentId === null ? '(kein Epic)' : `#${parentId}`}
          helperText="Epic-Liste hier nicht verfügbar — die Zuordnung bleibt unverändert."
          slotProps={{
            htmlInput: { 'aria-label': 'Epic' },
            input: { readOnly: true },
            inputLabel: { shrink: true },
          }}
          fullWidth
        />
      ) : (
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
      )}
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
        inputRef={titleInputRef}
        onKeyDown={onTitleKeyDown}
        slotProps={{ htmlInput: { maxLength: 300, 'aria-label': 'Titel' } }}
      />
      <TextField
        label="Markdown-Beschreibung"
        value={body}
        onChange={bodyShortcut.onChange}
        onKeyDown={bodyShortcut.onKeyDown}
        multiline
        rows={8}
        fullWidth
        error={isTooLong(body)}
        helperText={isTooLong(body) ? tooLongMessage(body.length) : undefined}
        slotProps={{ htmlInput: { 'aria-label': 'Markdown-Beschreibung' } }}
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
