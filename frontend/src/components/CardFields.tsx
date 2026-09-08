import TextField from '@mui/material/TextField'
import type { KeyboardEvent, Ref } from 'react'
import type { Epic } from '../api/epics'
import { epicShortcode } from '../lib/epicMeta'
import { useCheckboxShortcut } from '../lib/useCheckboxShortcut'
import { isTooLong, tooLongMessage } from '../lib/textLimits'

/**
 * Native Vorhaben-Auswahl: „(kein Vorhaben)" plus alle übergebenen Vorhaben mit Kürzel. Eigene
 * Komponente, weil neben dieser Feldbasis auch der schlanke Anlege-Zweig des `NewCardModal`
 * (Idee/Karte ohne Zusatzfelder) dieselbe Auswahl braucht — vorher zwei wortgleiche Kopien (#781).
 */
export function EpicSelectField({
  parentId,
  epics,
  onParentIdChange,
}: Readonly<{
  parentId: number | null
  epics: Epic[]
  onParentIdChange: (value: number | null) => void
}>) {
  return (
    <TextField
      select
      label="Vorhaben"
      value={parentId ?? ''}
      onChange={(e) => onParentIdChange(e.target.value === '' ? null : Number(e.target.value))}
      slotProps={{
        htmlInput: { 'aria-label': 'Vorhaben' },
        select: { native: true },
        inputLabel: { shrink: true },
      }}
      fullWidth
    >
      <option value="">(kein Vorhaben)</option>
      {epics.map((epic) => (
        <option key={epic.id} value={epic.id}>
          {epicShortcode(epic.title, epic.shortcode)} – {epic.title}
        </option>
      ))}
    </TextField>
  )
}

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
  epicReadOnlyLabel,
  depsInput,
  depsError,
  herkunftInput = '',
  herkunftError = null,
  dueInput,
  onTitleChange,
  onBodyChange,
  onShortcodeChange,
  onParentIdChange,
  onDepsInputChange,
  onHerkunftInputChange,
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
   * Optionsvorrat nicht laden können (archiviertes Board, board-lose Idee) oder deren Zuordnung
   * nicht darin vorkommt (ausgeblendetes Vorhaben, Plan #717): Das Dropdown böte dann
   * ausschließlich „(kein Epic)" an, und ein Klick darauf löschte eine bestehende Zuordnung, ohne
   * sie je gezeigt zu haben (#586).
   */
  epicReadOnly?: boolean
  /**
   * Anzeigetext des lesenden Felds, wenn der Aufrufer die Zuordnung benennen kann. Ohne ihn bleibt
   * es bei der nackten Nummer: Wer den Titel nicht kennt, soll ihn nicht erfinden. Auflösen kann
   * ihn nur der Aufrufer — er hält die volle Vorhaben-Liste, dieses Formular nur den Optionsvorrat.
   */
  epicReadOnlyLabel?: string
  depsInput: string
  depsError: string | null
  /**
   * Herkunftsnummer als Rohtext; leer bedeutet „keine Herkunft" (Issue #608). Optional, weil der
   * Anlege-Dialog die Herkunft bewusst nicht anbietet — sie wird nachträglich gepflegt. Ohne
   * `onHerkunftInputChange` erscheint das Feld gar nicht.
   */
  herkunftInput?: string
  herkunftError?: string | null
  dueInput: string
  onTitleChange: (value: string) => void
  onBodyChange: (value: string) => void
  onShortcodeChange: (value: string) => void
  onParentIdChange: (value: number | null) => void
  onDepsInputChange: (value: string) => void
  onHerkunftInputChange?: (value: string) => void
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
        // Nennt der Aufrufer die Zuordnung, steht sie hier; sonst bleibt die nackte ID. Beides
        // belegt sichtbar, dass eine Zuordnung besteht, und hält sie zugleich außer Reichweite
        // jeder versehentlichen Änderung.
        <TextField
          label="Vorhaben"
          value={parentId === null ? '(kein Vorhaben)' : (epicReadOnlyLabel ?? `#${parentId}`)}
          helperText="Vorhaben hier nicht auswählbar (ausgeblendet oder Liste nicht verfügbar) — die Zuordnung bleibt unverändert."
          slotProps={{
            htmlInput: { 'aria-label': 'Vorhaben' },
            input: { readOnly: true },
            inputLabel: { shrink: true },
          }}
          fullWidth
        />
      ) : (
        <EpicSelectField parentId={parentId} epics={epics} onParentIdChange={onParentIdChange} />
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
      {!isEpic && onHerkunftInputChange && (
        <TextField
          label="Herkunft (Kartennummer)"
          value={herkunftInput}
          onChange={(e) => onHerkunftInputChange(e.target.value)}
          error={herkunftError != null}
          helperText={herkunftError ?? 'Nummer der Karte, aus der diese hervorging — leer = keine'}
          slotProps={{ htmlInput: { 'aria-label': 'Herkunft' } }}
          fullWidth
        />
      )}
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
