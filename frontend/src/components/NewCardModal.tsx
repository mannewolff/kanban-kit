import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import { type KeyboardEvent, useEffect, useRef, useState } from 'react'
import type { CardType } from '../api/cards'
import type { Epic } from '../api/epics'
import type { Label as BoardLabel } from '../api/labels'
import type { Member } from '../api/members'
import { epicShortcode } from '../lib/epicMeta'
import { dueInputToIso } from '../lib/dueDate'
import { CardFields } from './CardFields'
import { AssigneeSection, LabelSection, parseDependencyInput } from './CardDetailModal'

const BODY_TEMPLATE = '## Kontext\n\n## Aufgabe\n\n## Akzeptanzkriterium\n\n## Abhängigkeiten\n'

export interface NewItemInput {
  type: CardType
  title: string
  description: string
  parentId: number | null
  shortcode: string | null
  /** Kartennummern, von denen die neue Karte abhängt (leer bei Idee/Epic). */
  dependencies: number[]
  /** Fälligkeit als ISO-String (null bei Idee/Epic oder leerer Eingabe). */
  dueDate: string | null
  /** Zuständige (User-IDs); leer bei Idee/Epic. */
  assigneeIds: number[]
  /** Labels (IDs); leer bei Idee/Epic. */
  labelIds: number[]
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
  /** Nur Idee anlegen: Typ fest CARD, ohne Typ-Auswahl (für die Ideen-Speicher-Zone). */
  ideaOnly?: boolean
  /** Vorbefüllung für „Duplizieren"; ohne Angabe startet der Dialog leer. */
  initialValues?: NewCardInitialValues
  /** Projektmitglieder für die Zuständigen-Auswahl (nur voller Karten-Anlege-Modus). */
  members?: Member[]
  /** Board-Labels für die Label-Auswahl (nur voller Karten-Anlege-Modus). */
  boardLabels?: BoardLabel[]
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
  ideaOnly = false,
  initialValues,
  members = [],
  boardLabels = [],
}: Readonly<Props>) {
  const [type, setType] = useState<CardType>(epicOnly ? 'EPIC' : 'CARD')
  const [title, setTitle] = useState('')
  const [body, setBody] = useState(BODY_TEMPLATE)
  const [parentId, setParentId] = useState<number | null>(null)
  const [shortcode, setShortcode] = useState('')
  const [depsInput, setDepsInput] = useState('')
  const [depsError, setDepsError] = useState<string | null>(null)
  const [dueInput, setDueInput] = useState('')
  const [assigneeIds, setAssigneeIds] = useState<number[]>([])
  const [labelIds, setLabelIds] = useState<number[]>([])
  const [saving, setSaving] = useState(false)
  const titleInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!open) return
    setType(epicOnly ? 'EPIC' : 'CARD')
    setTitle(initialValues?.title ?? '')
    setBody(initialValues?.description ?? BODY_TEMPLATE)
    setParentId(initialValues?.parentId ?? null)
    setShortcode('')
    setDepsInput('')
    setDepsError(null)
    setDueInput('')
    setAssigneeIds([])
    setLabelIds([])
    setSaving(false)
    // Titel selektieren, damit ein Überschreiben (z. B. beim Duplizieren) ohne Löschen möglich ist.
    // Beim ersten Effektlauf ist der Input wegen der Dialog-Transition ggf. noch nicht gemountet
    // (current === null); bei späteren Läufen (z. B. geänderte initialValues) greift die Selektion.
    titleInputRef.current?.select()
  }, [open, epicOnly, initialValues])

  const canSubmit = title.trim().length > 0 && !saving
  // Voller Karten-Anlege-Modus: nur echte Karten (kein Epic) außerhalb des schlanken Ideen-Dialogs
  // bekommen Abhängigkeiten, Fälligkeit, Zuständige und Labels.
  const fullCard = type === 'CARD' && !ideaOnly

  // Kein verschachteltes Ternary im JSX (Sonar S3358): der Titel richtet sich nach dem Modus.
  let dialogTitle
  if (type === 'EPIC') {
    dialogTitle = 'Neues Epic'
  } else if (ideaOnly) {
    dialogTitle = 'Neue Idee'
  } else {
    dialogTitle = `Neue Karte in „${columnName}“`
  }

  const handleCreate = async () => {
    if (!canSubmit) return
    let dependencies: number[] = []
    let dueDate: string | null = null
    if (fullCard) {
      const parsed = parseDependencyInput(depsInput)
      if (!parsed.valid) {
        setDepsError('Nur positive Nummern, kommagetrennt (z. B. 12, 34).')
        return
      }
      dependencies = parsed.deps
      dueDate = dueInputToIso(dueInput)
    }
    setSaving(true)
    try {
      await onSubmit({
        type,
        title: title.trim(),
        description: body,
        parentId: type === 'CARD' ? parentId : null,
        shortcode: type === 'EPIC' ? shortcode.trim() || null : null,
        dependencies,
        dueDate,
        assigneeIds: fullCard ? assigneeIds : [],
        labelIds: fullCard ? labelIds : [],
      })
      onClose()
    } finally {
      setSaving(false)
    }
  }

  const handleTitleKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) void handleCreate()
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
      <DialogTitle id="new-card-title">{dialogTitle}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {!epicOnly && !ideaOnly && (
            <TextField
              select
              label="Typ"
              value={type}
              onChange={(e) => setType(e.target.value as CardType)}
              slotProps={{ htmlInput: { 'aria-label': 'Typ' }, select: { native: true } }}
              fullWidth
            >
              <option value="CARD">Karte</option>
              <option value="EPIC">Epic</option>
            </TextField>
          )}

          {fullCard ? (
            <>
              {/* Karte: gemeinsame Feldbasis mit dem Bearbeiten (#326) plus die deferred
                  gesammelten Zuständigen/Labels — im Anlegen wird alles atomar mitgeschickt (#325). */}
              <CardFields
                isEpic={false}
                title={title}
                body={body}
                shortcode={shortcode}
                parentId={parentId}
                epics={epics}
                depsInput={depsInput}
                depsError={depsError}
                dueInput={dueInput}
                onTitleChange={setTitle}
                onBodyChange={setBody}
                onShortcodeChange={setShortcode}
                onParentIdChange={setParentId}
                onDepsInputChange={(value) => {
                  setDepsInput(value)
                  if (depsError) setDepsError(null)
                }}
                onDueInputChange={setDueInput}
                titleInputRef={titleInputRef}
                onTitleKeyDown={handleTitleKeyDown}
              />
              <AssigneeSection
                canEdit
                members={members}
                assigneeIds={assigneeIds}
                onChange={setAssigneeIds}
              />
              <LabelSection
                canEdit
                boardLabels={boardLabels}
                labelIds={labelIds}
                onChange={setLabelIds}
              />
            </>
          ) : (
            <>
              {/* Ideen-Speicher (ideaOnly) und Epic bleiben bewusst schlank: nur die bisherigen
                  Felder, keine Zuständigen/Labels/Fälligkeit/Abhängigkeiten. */}
              {type === 'EPIC' ? (
                <TextField
                  label="Kürzel (optional)"
                  value={shortcode}
                  onChange={(e) => setShortcode(e.target.value)}
                  placeholder={epicShortcode(title)}
                  helperText="Leer lassen, um es aus dem Titel abzuleiten."
                  slotProps={{ htmlInput: { maxLength: 16, 'aria-label': 'Kürzel' } }}
                  fullWidth
                />
              ) : (
                <TextField
                  select
                  label="Epic"
                  value={parentId ?? ''}
                  onChange={(e) => setParentId(e.target.value === '' ? null : Number(e.target.value))}
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
                label="Titel"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                required
                autoFocus
                fullWidth
                inputRef={titleInputRef}
                slotProps={{ htmlInput: { maxLength: 300, 'aria-label': 'Titel' } }}
                onKeyDown={handleTitleKeyDown}
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
            </>
          )}
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
