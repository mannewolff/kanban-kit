import TextField from '@mui/material/TextField'
import type { KeyboardEvent, Ref } from 'react'
import type { Epic } from '../api/epics'
import { epicShortcode } from '../lib/epicMeta'
import { useCheckboxShortcut } from '../lib/useCheckboxShortcut'
import { isTooLong, tooLongMessage } from '../lib/textLimits'
import { useSprache, useT } from '../i18n/SpracheContext'
import { DatumFeld } from './DatumFeld'

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
  const t = useT().KARTENFELDER
  return (
    <TextField
      select
      label={t.VORHABEN}
      value={parentId ?? ''}
      onChange={(e) => onParentIdChange(e.target.value === '' ? null : Number(e.target.value))}
      slotProps={{
        htmlInput: { 'aria-label': t.VORHABEN },
        select: { native: true },
        inputLabel: { shrink: true },
      }}
      fullWidth
    >
      <option value="">{t.KEIN_VORHABEN}</option>
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
  titleError = null,
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
  /**
   * Serverseitige Feldmeldung zum Titel; ohne sie bleibt das Feld fehlerfrei. Optional, weil nur
   * Aufrufer mit eigener Fehlerbehandlung sie füllen können — das Bearbeiten-Formular meldet
   * anders (Issue #808).
   */
  titleError?: string | null
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
  const { sprache } = useSprache()
  const t = useT().KARTENFELDER
  const bodyShortcut = useCheckboxShortcut(onBodyChange)
  const nonEpicFields = (
    <>
      {epicReadOnly ? (
        // Nennt der Aufrufer die Zuordnung, steht sie hier; sonst bleibt die nackte ID. Beides
        // belegt sichtbar, dass eine Zuordnung besteht, und hält sie zugleich außer Reichweite
        // jeder versehentlichen Änderung.
        <TextField
          label={t.VORHABEN}
          value={parentId === null ? t.KEIN_VORHABEN : (epicReadOnlyLabel ?? `#${parentId}`)}
          helperText={t.VORHABEN_NUR_LESEN}
          slotProps={{
            htmlInput: { 'aria-label': t.VORHABEN },
            input: { readOnly: true },
            inputLabel: { shrink: true },
          }}
          fullWidth
        />
      ) : (
        <EpicSelectField parentId={parentId} epics={epics} onParentIdChange={onParentIdChange} />
      )}
      <TextField
        label={t.ABHAENGIG}
        value={depsInput}
        onChange={(e) => onDepsInputChange(e.target.value)}
        error={depsError != null}
        helperText={depsError ?? t.ABHAENGIG_BEISPIEL}
        slotProps={{ htmlInput: { 'aria-label': t.ABHAENGIG_KURZ } }}
        fullWidth
      />
      {!isEpic && onHerkunftInputChange && (
        <TextField
          label={t.HERKUNFT}
          value={herkunftInput}
          onChange={(e) => onHerkunftInputChange(e.target.value)}
          error={herkunftError != null}
          helperText={herkunftError ?? t.HERKUNFT_HINWEIS}
          slotProps={{ htmlInput: { 'aria-label': t.HERKUNFT_KURZ } }}
          fullWidth
        />
      )}
      <DatumFeld label={t.FAELLIG} value={dueInput} onChange={onDueInputChange} />
    </>
  )
  return (
    <>
      <TextField
        label={t.TITEL}
        value={title}
        onChange={(e) => onTitleChange(e.target.value)}
        required
        autoFocus
        fullWidth
        inputRef={titleInputRef}
        onKeyDown={onTitleKeyDown}
        error={titleError != null}
        helperText={titleError ?? undefined}
        slotProps={{ htmlInput: { maxLength: 300, 'aria-label': t.TITEL } }}
      />
      <TextField
        label={t.BESCHREIBUNG}
        value={body}
        onChange={bodyShortcut.onChange}
        onKeyDown={bodyShortcut.onKeyDown}
        multiline
        rows={8}
        fullWidth
        error={isTooLong(body)}
        helperText={isTooLong(body) ? tooLongMessage(body.length, sprache) : undefined}
        slotProps={{ htmlInput: { 'aria-label': t.BESCHREIBUNG } }}
        sx={{ '& textarea': { fontFamily: 'monospace', resize: 'vertical' } }}
      />
      {isEpic ? (
        <TextField
          label={t.KUERZEL}
          value={shortcode}
          onChange={(e) => onShortcodeChange(e.target.value)}
          slotProps={{ htmlInput: { maxLength: 16, 'aria-label': t.KUERZEL } }}
          sx={{ maxWidth: 200 }}
        />
      ) : (
        nonEpicFields
      )}
    </>
  )
}
