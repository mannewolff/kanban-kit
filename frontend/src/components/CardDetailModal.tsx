import Alert from '@mui/material/Alert'
import Autocomplete from '@mui/material/Autocomplete'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Divider from '@mui/material/Divider'
import IconButton from '@mui/material/IconButton'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import {
  createContext,
  memo,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ComponentPropsWithoutRef,
  type Dispatch,
  type SetStateAction,
} from 'react'
import Markdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { attachmentsApi as defaultAttachmentsApi, type Attachment, type AttachmentsApi } from '../api/attachments'
import { cardsApi as defaultCardsApi } from '../api/cards'
import type { Card, CardActivity } from '../api/cards'
import { commentsApi as defaultCommentsApi, type Comment, type CommentsApi } from '../api/comments'
import type { Epic } from '../api/epics'
import type { Label as BoardLabel } from '../api/labels'
import type { Member } from '../api/members'
import { useEditMode } from '../lib/EditModeContext'
import { dueInputToIso, formatDueDate, isOverdue } from '../lib/dueDate'
import { epicShortcode } from '../lib/epicMeta'
import { normalizeTaskLists, toggleTaskAt } from '../lib/markdownTasks'
import { CODE_BLOCK_BG, MODAL_BORDER, MODAL_TEXT_PRIMARY, statusColors } from '../lib/statusColors'
import { useAuth } from '../auth/AuthContext'
import { AttachmentPreview } from './AttachmentPreview'

/** Bilder und PDF werden in der Lightbox angezeigt; andere Typen nur heruntergeladen. */
const isPreviewable = (contentType: string) =>
  contentType.startsWith('image/') || contentType === 'application/pdf'

/**
 * Lädt für alle Bild-Anhänge einer Liste die Vorschau-URL nach (Blob → Object-URL). Als
 * Modul-Helfer ausgelagert, damit die `fetchBlob`-Kette nicht tief im Effect verschachtelt steht.
 */
function loadImagePreviews(
  list: Attachment[],
  attachmentsApi: Pick<AttachmentsApi, 'fetchBlob'>,
  setPreviews: Dispatch<SetStateAction<Record<number, string>>>,
) {
  for (const a of list) {
    if (!a.contentType.startsWith('image/')) continue
    void attachmentsApi
      .fetchBlob(a.id)
      .then((blob) => setPreviews((p) => ({ ...p, [a.id]: URL.createObjectURL(blob) })))
  }
}

/**
 * Parst die kommagetrennte Abhängigkeits-Eingabe in Nummern. Nur positive Ganzzahlen sind gültig;
 * Leer-Tokens werden ignoriert, Duplikate entfernt. valid=false bei nicht-numerischem/nicht-positivem Token.
 */
export function parseDependencyInput(input: string): { deps: number[]; valid: boolean } {
  const tokens = input.split(',').map((t) => t.trim()).filter((t) => t.length > 0)
  const deps: number[] = []
  for (const token of tokens) {
    if (!/^\d+$/.test(token)) return { deps: [], valid: false }
    const n = Number(token)
    if (n <= 0) return { deps: [], valid: false }
    if (!deps.includes(n)) deps.push(n)
  }
  return { deps, valid: true }
}

/**
 * Kontext für den Task-Checkbox-Renderer: liefert Bearbeitbarkeit, Toggle-Callback und den
 * fortlaufenden Checkbox-Index (Dokumentreihenfolge). Erlaubt, den `input`-Renderer auf
 * Modulebene zu definieren (statt in `TaskMarkdown`) und die Daten per Kontext zu übergeben.
 */
const TaskCheckboxContext = createContext<{
  canEdit: boolean
  onToggle: (index: number) => void
  nextIndex: () => number
} | null>(null)

/**
 * `input`-Renderer für react-markdown: rendert GFM-Task-Checkboxen klickbar (Index in
 * Dokumentreihenfolge über den Kontext). Wird ausschließlich innerhalb von `TaskMarkdown`
 * verwendet (immer im TaskCheckboxContext.Provider, remarkGfm ohne rehype-raw erzeugt
 * ausschließlich `type="checkbox"`-Inputs) — kein Fallback für andere Fälle nötig.
 */
function MarkdownInput(props: ComponentPropsWithoutRef<'input'>) {
  // Nicht null: MarkdownInput wird ausschließlich innerhalb von TaskCheckboxContext.Provider
  // gerendert (siehe TaskMarkdown unten).
  const ctx = useContext(TaskCheckboxContext)!
  const index = ctx.nextIndex()
  return (
    <input
      type="checkbox"
      checked={props.checked ?? false}
      disabled={!ctx.canEdit}
      onChange={() => ctx.onToggle(index)}
      aria-label={`Aufgabe ${index + 1}`}
    />
  )
}

const markdownComponents: Components = { input: MarkdownInput }

/**
 * Rendert die Karten-Beschreibung als Markdown mit klickbaren Task-Checkboxen. Als eigene,
 * memoized Komponente ausgelagert, damit Re-Renders des Modals (z. B. Nachladen von Kommentaren)
 * die Beschreibung nicht neu mounten. Der Checkbox-Index läuft in Dokumentreihenfolge (Zähler pro
 * Render zurückgesetzt); `onToggle` muss stabil sein, damit `memo` greift.
 */
const TaskMarkdown = memo(function TaskMarkdown({
  body,
  canEdit,
  onToggle,
}: {
  body: string
  canEdit: boolean
  onToggle: (index: number) => void
}) {
  // Zähler muss bei jedem tatsächlichen Render dieser (memoized) Komponente zurückgesetzt werden:
  // react-markdown ruft `nextIndex` je Checkbox in Dokumentreihenfolge auf. Da die Deps exakt den
  // Props entsprechen, gegen die `memo` oben vergleicht, fällt das useMemo-Recompute mit jedem
  // echten Funktionsaufruf zusammen — der Zähler startet dabei trotzdem frisch bei 0, weil er
  // innerhalb der Factory neu angelegt wird. Bewusst kein useRef (Schreiben während des Renders).
  const ctxValue = useMemo(() => {
    const counter = { value: 0 }
    return { canEdit, onToggle, nextIndex: () => counter.value++ }
    // body wird in der Factory nicht gelesen, muss aber in den Deps stehen: ein neuer body-Wert
    // soll den Zähler zurücksetzen, obwohl body selbst nicht in ctxValue einfließt.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [body, canEdit, onToggle])
  return (
    <TaskCheckboxContext.Provider value={ctxValue}>
      <Markdown remarkPlugins={[remarkGfm]} components={markdownComponents}>
        {normalizeTaskLists(body)}
      </Markdown>
    </TaskCheckboxContext.Provider>
  )
})

const descriptionSx = {
  border: `1px solid ${MODAL_BORDER}`,
  borderRadius: 1,
  p: 2,
  '& :first-of-type': { mt: 0 },
  '& h1, & h2': { fontWeight: 600, fontSize: '1.15rem', mt: 2, pb: 0.5, borderBottom: `1px solid ${MODAL_BORDER}` },
  '& h3, & h4': { fontWeight: 600, fontSize: '1rem', mt: 1.5, mb: 0.5 },
  '& p, & li': { lineHeight: 1.6, color: MODAL_TEXT_PRIMARY },
  '& ul, & ol': { pl: 3, my: 1 },
  '& code': { backgroundColor: CODE_BLOCK_BG, px: 0.5, borderRadius: '3px', fontFamily: 'monospace', fontSize: '0.85em' },
  '& pre': { backgroundColor: CODE_BLOCK_BG, p: 1.5, borderRadius: 1, overflowX: 'auto' },
  '& pre code': { backgroundColor: 'transparent', px: 0 },
} as const

/** Zuständige-Sektion: Autocomplete im Edit-Modus, sonst Chips oder Leer-Hinweis. */
function AssigneeSection({
  canEdit,
  members,
  assigneeIds,
  onChange,
}: Readonly<{
  canEdit: boolean
  members: Member[]
  assigneeIds: number[]
  onChange: (ids: number[]) => void
}>) {
  const memberName = (userId: number) =>
    members.find((m) => m.userId === userId)?.displayName ?? `#${userId}`
  const readOnly =
    assigneeIds.length > 0 ? (
      <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap' }}>
        {assigneeIds.map((uid) => (
          <Chip key={uid} size="small" label={memberName(uid)} />
        ))}
      </Stack>
    ) : (
      <Typography color="text.secondary">Niemand zugewiesen.</Typography>
    )
  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>
        Zuständige
      </Typography>
      {canEdit ? (
        <Autocomplete
          multiple
          size="small"
          options={members}
          getOptionLabel={(m) => m.displayName}
          isOptionEqualToValue={(a, b) => a.userId === b.userId}
          value={members.filter((m) => assigneeIds.includes(m.userId))}
          onChange={(_, selected) => onChange(selected.map((m) => m.userId))}
          renderInput={(params) => (
            <TextField {...params} label="Zuständige" slotProps={{ htmlInput: { ...params.inputProps, 'aria-label': 'Zuständige' } }} />
          )}
        />
      ) : (
        readOnly
      )}
    </Box>
  )
}

/** Label-Sektion: Autocomplete im Edit-Modus, sonst farbige Chips oder Leer-Hinweis. */
function LabelSection({
  canEdit,
  boardLabels,
  labelIds,
  onChange,
}: Readonly<{
  canEdit: boolean
  boardLabels: BoardLabel[]
  labelIds: number[]
  onChange: (ids: number[]) => void
}>) {
  const readOnly =
    labelIds.length > 0 ? (
      <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap' }}>
        {labelIds.map((id) => {
          const l = boardLabels.find((b) => b.id === id)
          return (
            <Chip
              key={id}
              size="small"
              label={l?.name ?? `#${id}`}
              sx={{ bgcolor: l?.color ?? 'grey.500', color: '#fff' }}
            />
          )
        })}
      </Stack>
    ) : (
      <Typography color="text.secondary">Keine Labels.</Typography>
    )
  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>
        Labels
      </Typography>
      {canEdit ? (
        <Autocomplete
          multiple
          size="small"
          options={boardLabels}
          getOptionLabel={(l) => l.name}
          isOptionEqualToValue={(a, b) => a.id === b.id}
          value={boardLabels.filter((l) => labelIds.includes(l.id))}
          onChange={(_, selected) => onChange(selected.map((l) => l.id))}
          renderTags={(value, getTagProps) =>
            value.map((l, index) => (
              <Chip
                {...getTagProps({ index })}
                key={l.id}
                size="small"
                label={l.name}
                sx={{ bgcolor: l.color, color: '#fff' }}
              />
            ))
          }
          renderInput={(params) => (
            <TextField {...params} label="Labels" slotProps={{ htmlInput: { ...params.inputProps, 'aria-label': 'Labels' } }} />
          )}
        />
      ) : (
        readOnly
      )}
    </Box>
  )
}

/** Kind-Karten eines Epics (nur View-Modus). */
function ChildCardsSection({ childCards }: Readonly<{ childCards: Card[] }>) {
  return (
    <>
      <Divider />
      <Box>
        <Typography variant="subtitle2" gutterBottom>
          Karten ({childCards.length})
        </Typography>
        <Stack spacing={0.5}>
          {childCards.map((c) => (
            <Typography key={c.id} variant="body2">
              #{c.number} · {c.title}
            </Typography>
          ))}
          {childCards.length === 0 && (
            <Typography color="text.secondary">Keine zugeordneten Karten.</Typography>
          )}
        </Stack>
      </Box>
    </>
  )
}

/** Anhänge-Sektion: Liste mit Vorschau/Download, Löschen und Upload (nur View-Modus). */
function AttachmentsSection({
  attachments,
  previews,
  uploadError,
  canEdit,
  onOpenPreview,
  onDelete,
  onUpload,
}: Readonly<{
  attachments: Attachment[]
  previews: Record<number, string>
  uploadError: string | null
  canEdit: boolean
  onOpenPreview: (a: Attachment) => void
  onDelete: (id: number) => void
  onUpload: (file: File) => void
}>) {
  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>
        Anhänge
      </Typography>
      {uploadError && <Alert severity="error" sx={{ mb: 1 }}>{uploadError}</Alert>}
      <Stack spacing={1}>
        {attachments.map((a) => (
          <Box key={a.id}>
            <Stack direction="row" spacing={1} alignItems="center">
              {isPreviewable(a.contentType) ? (
                <Link component="button" type="button" onClick={() => onOpenPreview(a)} sx={{ textAlign: 'left' }}>
                  {a.filename}
                </Link>
              ) : (
                <Link href={`/api/attachments/${a.id}`}>{a.filename}</Link>
              )}
              <Typography variant="caption" color="text.secondary">
                {Math.round(a.size / 1024)} KB
              </Typography>
              {canEdit && (
                <IconButton size="small" aria-label={`Anhang ${a.filename} löschen`} onClick={() => onDelete(a.id)}>
                  ✕
                </IconButton>
              )}
            </Stack>
            {previews[a.id] && (
              <Box component="img" src={previews[a.id]} alt={a.filename}
                onClick={() => onOpenPreview(a)}
                sx={{ maxWidth: 240, maxHeight: 160, mt: 0.5, borderRadius: 1, cursor: 'pointer', display: 'block' }} />
            )}
          </Box>
        ))}
        {attachments.length === 0 && <Typography color="text.secondary">Keine Anhänge.</Typography>}
      </Stack>
      {canEdit && (
        <Button component="label" size="small" sx={{ mt: 1 }}>
          Datei anhängen<input
            hidden
            type="file"
            aria-label="Datei anhängen"
            onChange={(e) => {
              const f = e.target.files?.[0]
              if (f) onUpload(f)
            }}
          />
        </Button>
      )}
    </Box>
  )
}

/** Kommentar-Sektion: Liste mit Inline-Edit/Moderation + Eingabe (nur View-Modus). */
function CommentsSection({
  comments,
  currentUserId,
  canModerateComments,
  editingCommentId,
  editingBody,
  newComment,
  onStartEdit,
  onSaveEdit,
  onCancelEdit,
  onDelete,
  onEditingBodyChange,
  onNewCommentChange,
  onAdd,
}: Readonly<{
  comments: Comment[]
  currentUserId: number | undefined
  canModerateComments: boolean
  editingCommentId: number | null
  editingBody: string
  newComment: string
  onStartEdit: (c: Comment) => void
  onSaveEdit: () => void
  onCancelEdit: () => void
  onDelete: (id: number) => void
  onEditingBodyChange: (value: string) => void
  onNewCommentChange: (value: string) => void
  onAdd: () => void
}>) {
  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>
        Kommentare
      </Typography>
      <Stack spacing={1}>
        {comments.map((c) => {
          const isAuthor = c.authorUserId === currentUserId
          return (
            <Box key={c.id}>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Typography variant="body2" fontWeight={600}>
                  {c.authorName}
                </Typography>
                <Stack direction="row" spacing={0.5}>
                  {/* Bearbeiten darf nur der Autor selbst. */}
                  {isAuthor && editingCommentId !== c.id && (
                    <IconButton size="small" aria-label="Kommentar bearbeiten" onClick={() => onStartEdit(c)}>
                      ✎
                    </IconButton>
                  )}
                  {/* Löschen ist Moderation: nur Admin/Owner (bzw. Plattform-Admin). */}
                  {canModerateComments && (
                    <IconButton size="small" aria-label="Kommentar löschen" onClick={() => onDelete(c.id)}>
                      ✕
                    </IconButton>
                  )}
                </Stack>
              </Stack>
              {editingCommentId === c.id ? (
                <Stack spacing={1}>
                  <TextField multiline size="small" value={editingBody}
                    onChange={(e) => onEditingBodyChange(e.target.value)}
                    slotProps={{ htmlInput: { maxLength: 10_000, 'aria-label': 'Kommentar bearbeiten' } }} />
                  <Stack direction="row" spacing={1}>
                    <Button size="small" variant="contained" onClick={onSaveEdit}>Speichern</Button>
                    <Button size="small" onClick={onCancelEdit}>Abbrechen</Button>
                  </Stack>
                </Stack>
              ) : (
                <Typography variant="body2">{c.body}</Typography>
              )}
            </Box>
          )
        })}
        {comments.length === 0 && <Typography color="text.secondary">Noch keine Kommentare.</Typography>}
      </Stack>
      <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
        <TextField size="small" fullWidth placeholder="Kommentar schreiben" value={newComment}
          onChange={(e) => onNewCommentChange(e.target.value)} slotProps={{ htmlInput: { 'aria-label': 'Kommentar schreiben' } }} />
        <Button variant="contained" size="small" onClick={onAdd}>
          Senden
        </Button>
      </Stack>
    </Box>
  )
}

/** Aktivitäts-Sektion: chronologische Ereignisliste (nur View-Modus). */
function ActivitySection({
  activities,
  actorName,
}: Readonly<{
  activities: CardActivity[]
  actorName: (userId: number | null) => string
}>) {
  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>
        Aktivität
      </Typography>
      <Stack spacing={0.5}>
        {activities.map((a) => (
          <Typography key={a.id} variant="caption" color="text.secondary">
            {new Date(a.createdAt).toLocaleString('de-DE')} · {actorName(a.actorUserId)} · {a.detail}
          </Typography>
        ))}
        {activities.length === 0 && (
          <Typography color="text.secondary">Keine Aktivität.</Typography>
        )}
      </Stack>
    </Box>
  )
}

/** Edit-Formular: Titel/Beschreibung immer, sonst Kürzel (Epic) bzw. Epic/Abhängigkeiten/Fälligkeit. */
function CardEditForm({
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

/** Status-Chip in der Kopfleiste: „Epic" bei Epics, sonst der Spalten-Chip (falls bekannt). */
function CardStatusChip({
  isEpic,
  columnName,
  colors,
}: Readonly<{
  isEpic: boolean
  columnName?: string
  colors: { bg: string; text: string } | null
}>) {
  if (isEpic) return <Chip label="Epic" size="small" color="secondary" />
  if (!colors) return null
  return <Chip label={columnName} size="small" sx={{ bgcolor: colors.bg, color: colors.text, fontWeight: 600 }} />
}

/** View-Modus-Inhalt: Beschreibung (Markdown mit Task-Checkboxen), Abhängigkeiten, Fälligkeitsdatum. */
function CardBodyView({
  body,
  canEdit,
  onToggleTask,
  dependencies,
  isEpic,
  dueDate,
  dueOverdue,
}: Readonly<{
  body: string
  canEdit: boolean
  onToggleTask: (index: number) => void
  dependencies: number[]
  isEpic: boolean
  dueDate: string | null
  dueOverdue: boolean
}>) {
  return (
    <>
      <Box aria-label="Beschreibung" data-testid="description-view" sx={descriptionSx}>
        {body ? (
          <TaskMarkdown body={body} canEdit={canEdit} onToggle={onToggleTask} />
        ) : (
          <Typography color="text.secondary">Keine Beschreibung.</Typography>
        )}
      </Box>
      {dependencies.length > 0 && (
        <Typography variant="body2" color="text.secondary" aria-label="Abhängigkeiten">
          Abhängig von: {dependencies.map((n) => `#${n}`).join(', ')}
        </Typography>
      )}
      {!isEpic && dueDate && (
        <Typography
          variant="body2"
          aria-label="Fälligkeitsdatum"
          color={dueOverdue ? 'error' : 'text.secondary'}
          sx={{ fontWeight: dueOverdue ? 600 : 400 }}
        >
          Fällig am {formatDueDate(dueDate)}
          {dueOverdue && ' — überfällig'}
        </Typography>
      )}
    </>
  )
}

interface Props {
  card: Card
  canEdit: boolean
  /** Ob der Nutzer Kommentare moderieren (löschen) darf — Projekt-ADMIN/OWNER oder Plattform-Admin. */
  canModerateComments?: boolean
  onClose: () => void
  onChanged?: () => void
  /** Direkt im Edit-Modus öffnen (z. B. aus dem Karten-⋮-Menü). */
  initialEditing?: boolean
  /** Spaltenname für den Status-Chip (bei Karten). */
  columnName?: string
  /** Board-Epics für das Epic-Dropdown. */
  epics?: Epic[]
  /** Kind-Karten eines Epics (nur bei type === 'EPIC'). */
  childCards?: Card[]
  /** Projektmitglieder für die Zuständigen-Auswahl (Namen + Auswahlliste). */
  members?: Member[]
  /** Board-Labels für die Label-Auswahl (Name + Farbe). */
  boardLabels?: BoardLabel[]
  commentsApi?: CommentsApi
  attachmentsApi?: AttachmentsApi
  cardsApi?: Pick<
    typeof defaultCardsApi,
    'update' | 'setAssignees' | 'setLabels' | 'getActivity' | 'restore' | 'moveToIdeaStorage'
  >
}

export function CardDetailModal({
  card,
  canEdit,
  canModerateComments = false,
  onClose,
  onChanged,
  initialEditing = false,
  columnName,
  epics = [],
  childCards = [],
  members = [],
  boardLabels = [],
  commentsApi = defaultCommentsApi,
  attachmentsApi = defaultAttachmentsApi,
  cardsApi = defaultCardsApi,
}: Readonly<Props>) {
  const { user } = useAuth()
  const { editMode } = useEditMode()
  const isEpic = card.type === 'EPIC'
  const [assigneeIds, setAssigneeIds] = useState<number[]>(card.assignees)

  const saveAssignees = async (ids: number[]) => {
    setAssigneeIds(ids)
    await cardsApi.setAssignees(card.id, ids)
    onChanged?.()
  }

  const restore = async () => {
    await cardsApi.restore(card.id)
    onChanged?.()
    onClose()
  }

  // In den Ideen-Speicher: Alltags-Aktion (an canEdit gebunden, nicht editiermodus-gegatet).
  const moveToIdeaStorage = async () => {
    await cardsApi.moveToIdeaStorage(card.id)
    onChanged?.()
    onClose()
  }

  const [activities, setActivities] = useState<CardActivity[]>([])
  const actorName = (userId: number | null) =>
    members.find((m) => m.userId === userId)?.displayName ?? 'System'

  const [labelIds, setLabelIds] = useState<number[]>(card.labels)
  const saveLabels = async (ids: number[]) => {
    setLabelIds(ids)
    await cardsApi.setLabels(card.id, ids)
    onChanged?.()
  }

  const [editing, setEditing] = useState(initialEditing)
  const [title, setTitle] = useState(card.title)
  const [body, setBody] = useState(card.description ?? '')
  const [parentId, setParentId] = useState<number | null>(card.parentId)
  const [shortcode, setShortcode] = useState(card.shortcode ?? '')
  const [dueInput, setDueInput] = useState(card.dueDate ? card.dueDate.slice(0, 10) : '')
  const [depsInput, setDepsInput] = useState(card.dependencies.join(', '))
  const [depsError, setDepsError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const [comments, setComments] = useState<Comment[]>([])
  const [newComment, setNewComment] = useState('')
  const [editingCommentId, setEditingCommentId] = useState<number | null>(null)
  const [editingBody, setEditingBody] = useState('')
  const [attachments, setAttachments] = useState<Attachment[]>([])
  const [previews, setPreviews] = useState<Record<number, string>>({})
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [preview, setPreview] = useState<{ attachment: Attachment; url: string } | null>(null)

  useEffect(() => {
    void commentsApi.list(card.id).then(setComments)
    void attachmentsApi.list(card.id).then((list) => {
      setAttachments(list)
      loadImagePreviews(list, attachmentsApi, setPreviews)
    })
  }, [card.id, commentsApi, attachmentsApi])

  useEffect(() => {
    void cardsApi.getActivity(card.id).then(setActivities).catch(() => setActivities([]))
  }, [card.id, cardsApi])

  const startEditing = () => {
    setTitle(card.title)
    setBody(card.description ?? '')
    setParentId(card.parentId)
    setShortcode(card.shortcode ?? '')
    setDueInput(card.dueDate ? card.dueDate.slice(0, 10) : '')
    setDepsInput(card.dependencies.join(', '))
    setDepsError(null)
    setEditing(true)
  }

  const save = async () => {
    if (!title.trim() || saving) return
    const { deps, valid } = parseDependencyInput(depsInput)
    if (!valid) {
      setDepsError('Nur positive Nummern, kommagetrennt (z. B. 12, 34).')
      return
    }
    setSaving(true)
    try {
      await cardsApi.update(
        card.id,
        title.trim(),
        body,
        deps,
        isEpic ? shortcode.trim() || null : undefined,
        isEpic ? undefined : parentId,
        isEpic ? undefined : dueInputToIso(dueInput),
      )
      setEditing(false)
      onChanged?.()
    } finally {
      setSaving(false)
    }
  }

  // Klick auf eine Checkbox im View-Modus: n-ten Marker im Beschreibungstext flippen und sofort
  // persistieren (optimistisch, Rollback bei Fehler) — ohne den Edit-Modus zu öffnen.
  const toggleTask = async (index: number) => {
    if (!canEdit || saving) return
    const previous = body
    const next = toggleTaskAt(previous, index)
    if (next === previous) return
    setBody(next)
    setSaving(true)
    try {
      await cardsApi.update(
        card.id,
        card.title,
        next,
        card.dependencies,
        isEpic ? (card.shortcode ?? null) : undefined,
        isEpic ? undefined : card.parentId,
        isEpic ? undefined : card.dueDate,
      )
      onChanged?.()
    } catch {
      setBody(previous)
    } finally {
      setSaving(false)
    }
  }

  const addComment = async () => {
    if (!newComment.trim()) return
    const created = await commentsApi.create(card.id, newComment.trim())
    setComments((c) => [...c, created])
    setNewComment('')
  }

  const deleteComment = async (id: number) => {
    await commentsApi.remove(id)
    setComments((c) => c.filter((x) => x.id !== id))
  }

  const startEditComment = (c: Comment) => {
    setEditingCommentId(c.id)
    setEditingBody(c.body)
  }

  const saveEditComment = async () => {
    if (editingCommentId == null || !editingBody.trim()) return
    const updated = await commentsApi.update(editingCommentId, editingBody.trim())
    setComments((cs) => cs.map((x) => (x.id === updated.id ? updated : x)))
    setEditingCommentId(null)
  }

  const uploadFile = async (file: File) => {
    setUploadError(null)
    try {
      const created = await attachmentsApi.upload(card.id, file)
      setAttachments((a) => [...a, created])
      if (created.contentType.startsWith('image/')) {
        const blob = await attachmentsApi.fetchBlob(created.id)
        setPreviews((p) => ({ ...p, [created.id]: URL.createObjectURL(blob) }))
      }
    } catch {
      setUploadError('Upload fehlgeschlagen (evtl. Anhangslimit erreicht).')
    }
  }

  const deleteAttachment = async (id: number) => {
    await attachmentsApi.remove(id)
    setAttachments((a) => a.filter((x) => x.id !== id))
  }

  const openPreview = async (attachment: Attachment) => {
    setUploadError(null)
    try {
      const blob = await attachmentsApi.fetchBlob(attachment.id)
      setPreview({ attachment, url: URL.createObjectURL(blob) })
    } catch {
      setUploadError('Vorschau konnte nicht geladen werden.')
    }
  }

  const closePreview = () => {
    setPreview((p) => {
      if (p) URL.revokeObjectURL(p.url)
      return null
    })
  }

  const colors = columnName ? statusColors(columnName) : null
  const dueOverdue =
    !isEpic && isOverdue(card.dueDate, (columnName ?? '').toLowerCase().includes('done'))

  // Aktuellen Toggle-Handler über ein Ref halten und als stabile Callback-Identität an TaskMarkdown
  // reichen, damit dessen `memo` greift (kein Remount der Beschreibung bei Kommentar-Nachladen).
  const toggleTaskRef = useRef(toggleTask)
  useEffect(() => {
    toggleTaskRef.current = toggleTask
  })
  const onToggleTask = useCallback((index: number) => {
    void toggleTaskRef.current(index)
  }, [])

  return (
    <>
    <Dialog
      open
      onClose={editing ? () => setEditing(false) : onClose}
      maxWidth={false}
      scroll="paper"
      // Im Kontextbereich zentrieren (unter der Kopfleiste, rechts der Sidebar) statt über dem ganzen
      // Viewport; Breite UND Höhe auf 90 % dieses Bereichs, damit der Rand auf allen vier Seiten
      // gleich groß ist (reines maxWidth="md" ergäbe eine von der Breakpoint-Breite abhängige,
      // meist andere horizontale Marge als die 90%-Höhe vertikal). Der Backdrop bleibt
      // bildschirmfüllend. Außerhalb der Shell (CSS-Variablen ungesetzt) fällt es auf volle
      // Zentrierung zurück (Default 0).
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
      <DialogTitle sx={{ borderBottom: `1px solid ${MODAL_BORDER}` }}>
        <Stack direction="row" alignItems="center" spacing={1} sx={{ flexWrap: 'wrap' }}>
          <CardStatusChip isEpic={isEpic} columnName={columnName} colors={colors} />
          <Typography component="span" variant="body2" color="text.secondary">
            #{card.number}
          </Typography>
          <Typography component="span" sx={{ fontWeight: 600 }}>
            {card.title}
          </Typography>
          <Box sx={{ flexGrow: 1 }} />
          {canEdit && editMode && !editing && (
            <Button size="small" variant="outlined" onClick={startEditing}>
              Bearbeiten
            </Button>
          )}
        </Stack>
      </DialogTitle>

      <DialogContent dividers sx={{ overflowY: 'auto' }}>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {editing ? (
            <CardEditForm
              isEpic={isEpic}
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
            />
          ) : (
            <CardBodyView
              body={body}
              canEdit={canEdit}
              onToggleTask={onToggleTask}
              dependencies={card.dependencies}
              isEpic={isEpic}
              dueDate={card.dueDate}
              dueOverdue={dueOverdue}
            />
          )}

          {!isEpic && (
            <AssigneeSection
              canEdit={canEdit}
              members={members}
              assigneeIds={assigneeIds}
              onChange={(ids) => void saveAssignees(ids)}
            />
          )}

          {!isEpic && (
            <LabelSection
              canEdit={canEdit}
              boardLabels={boardLabels}
              labelIds={labelIds}
              onChange={(ids) => void saveLabels(ids)}
            />
          )}

          {!editing && isEpic && <ChildCardsSection childCards={childCards} />}

          {!editing && (
            <>
              <Divider />
              <AttachmentsSection
                attachments={attachments}
                previews={previews}
                uploadError={uploadError}
                canEdit={canEdit}
                onOpenPreview={(a) => void openPreview(a)}
                onDelete={(id) => void deleteAttachment(id)}
                onUpload={(file) => void uploadFile(file)}
              />

              <Divider />
              <CommentsSection
                comments={comments}
                currentUserId={user?.userId}
                canModerateComments={canModerateComments}
                editingCommentId={editingCommentId}
                editingBody={editingBody}
                newComment={newComment}
                onStartEdit={startEditComment}
                onSaveEdit={() => void saveEditComment()}
                onCancelEdit={() => setEditingCommentId(null)}
                onDelete={(id) => void deleteComment(id)}
                onEditingBodyChange={setEditingBody}
                onNewCommentChange={setNewComment}
                onAdd={() => void addComment()}
              />

              <Divider />
              <ActivitySection activities={activities} actorName={actorName} />
            </>
          )}
        </Stack>
      </DialogContent>

      <DialogActions>
        {editing ? (
          <>
            <Button onClick={() => setEditing(false)}>Abbrechen</Button>
            <Button variant="contained" onClick={() => void save()} disabled={!title.trim() || saving}>
              Speichern
            </Button>
          </>
        ) : (
          <>
            {canEdit && card.archived && (
              <Button onClick={() => void restore()}>Wiederherstellen</Button>
            )}
            {canEdit && !card.archived && !card.ideaStored && !isEpic && (
              <Button onClick={() => void moveToIdeaStorage()}>In Ideen-Speicher</Button>
            )}
            <Button onClick={onClose}>Schließen</Button>
          </>
        )}
      </DialogActions>
    </Dialog>

    {preview && (
      <AttachmentPreview
        filename={preview.attachment.filename}
        contentType={preview.attachment.contentType}
        url={preview.url}
        downloadHref={`/api/attachments/${preview.attachment.id}`}
        onClose={closePreview}
      />
    )}
    </>
  )
}
