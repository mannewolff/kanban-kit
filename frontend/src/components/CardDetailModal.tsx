import ArrowBackIcon from '@mui/icons-material/ArrowBack'
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
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import {
  createContext,
  Fragment,
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
import { boardsApi as defaultBoardsApi } from '../api/boards'
import { cardsApi as defaultCardsApi } from '../api/cards'
import type { Card, CardActivity, CardByNumber, CardDetail } from '../api/cards'
import { commentsApi as defaultCommentsApi, type Comment, type CommentsApi } from '../api/comments'
import type { Epic } from '../api/epics'
import type { Label as BoardLabel } from '../api/labels'
import type { Member } from '../api/members'
import { Breadcrumbs } from './Breadcrumbs'
import { isTooLong, tooLongMessage } from '../lib/textLimits'
import { CardFields } from './CardFields'
import { cardLocationCrumbs, type CardLocation } from '../lib/cardLocation'
import { dueInputToIso, formatDueDate, isOverdue } from '../lib/dueDate'
import { normalizeTaskLists, toggleTaskAt } from '../lib/markdownTasks'
import { safeImageSrc, safeLinkHref } from '../lib/markdownUrls'
import { CODE_BLOCK_BG, MODAL_BORDER, MODAL_TEXT_PRIMARY, statusColors } from '../lib/statusColors'
import { useAuth } from '../auth/AuthContext'
import { AttachmentPreview } from './AttachmentPreview'
import { useSnackbar } from './SnackbarProvider'

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
function MarkdownInput(props: Readonly<ComponentPropsWithoutRef<'input'>>) {
  // Nicht null: MarkdownInput wird ausschließlich innerhalb von TaskCheckboxContext.Provider
  // gerendert (siehe TaskMarkdown unten).
  const ctx = useContext(TaskCheckboxContext)!
  const index = ctx.nextIndex()
  return (
    <input
      type="checkbox"
      checked={props.checked === true}
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
}: Readonly<{
  body: string
  canEdit: boolean
  onToggle: (index: number) => void
}>) {
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

/**
 * Markdown-Grundstil für Beschreibung und Kommentar-Body. Ohne ihn rendert `<Markdown>` die
 * Browser-Defaults, und eine `##`-Überschrift im Text wirkte größer als der Modal-Titel — daher der
 * Deckel auf den Überschriftsgrößen. Lange Tokens (URLs, Hashes) brechen um; Codeblöcke und breite
 * GFM-Tabellen scrollen in ihrem eigenen Bereich, damit der Modal-Inhalt selbst keinen
 * horizontalen Scrollbalken bekommt. Genau solche Inhalte stehen in Review-Kommentaren (#575).
 */
const markdownBodySx = {
  overflowWrap: 'anywhere',
  '& :first-of-type': { mt: 0 },
  '& h1, & h2': { fontWeight: 600, fontSize: '1.15rem', mt: 2, pb: 0.5, borderBottom: `1px solid ${MODAL_BORDER}` },
  '& h3, & h4': { fontWeight: 600, fontSize: '1rem', mt: 1.5, mb: 0.5 },
  '& p, & li': { lineHeight: 1.6, color: MODAL_TEXT_PRIMARY },
  '& ul, & ol': { pl: 3, my: 1 },
  '& code': { backgroundColor: CODE_BLOCK_BG, px: 0.5, borderRadius: '3px', fontFamily: 'monospace', fontSize: '0.85em' },
  '& pre': { backgroundColor: CODE_BLOCK_BG, p: 1.5, borderRadius: 1, overflowX: 'auto' },
  '& pre code': { backgroundColor: 'transparent', px: 0 },
  // `display: block` + `width: max-content` macht die Tabelle zum eigenen Scrollbereich; als
  // echtes Table-Layout würde sie stattdessen auf die Modalbreite gestaucht.
  '& table': { display: 'block', width: 'max-content', maxWidth: '100%', overflowX: 'auto' },
} as const

const descriptionSx = {
  border: `1px solid ${MODAL_BORDER}`,
  borderRadius: 1,
  p: 2,
  ...markdownBodySx,
} as const

/**
 * `input`-Renderer für Kommentare: GFM-Task-Listen werden als Checkboxen angezeigt, aber gesperrt.
 * `readOnly` zusätzlich zu `disabled`, damit React das kontrollierte `checked` ohne `onChange`
 * akzeptiert.
 */
function CommentCheckbox(props: Readonly<ComponentPropsWithoutRef<'input'>>) {
  return <input type="checkbox" checked={props.checked === true} disabled readOnly />
}

/** `a`-Renderer: `href` nur bei erlaubtem Schema — sonst bleibt der Linktext ohne Ziel stehen. */
function CommentLink({ href, children }: Readonly<ComponentPropsWithoutRef<'a'>>) {
  return <a href={safeLinkHref(href)}>{children}</a>
}

/** `img`-Renderer: `src` nur bei erlaubtem Schema — sonst wird nichts abgerufen. */
function CommentImage({ src, alt }: Readonly<ComponentPropsWithoutRef<'img'>>) {
  return <img src={safeImageSrc(src)} alt={alt} />
}

const commentComponents: Components = { input: CommentCheckbox, a: CommentLink, img: CommentImage }

/**
 * Kommentar-Body als Markdown (#575). Vorher stand er in einem `<Typography>`, das als `<p>` jede
 * Folge von Whitespace zu einem Leerzeichen kollabiert — lange Berichte wurden so zur Textwand.
 *
 * Bewusst nicht `TaskMarkdown`: dessen Checkbox-Toggles schreiben über `cardsApi.update` in die
 * Karten-**Beschreibung** zurück; für Kommentare gibt es keinen solchen Persistenzpfad, ein Klick
 * liefe in die falsche Datenquelle. Task-Listen werden hier daher nur angezeigt. `normalizeTaskLists`
 * läuft trotzdem mit, damit dieselbe Schreibweise in Beschreibung und Kommentar gleich rendert.
 */
function CommentBody({ body }: Readonly<{ body: string }>) {
  return (
    <Box data-testid="comment-body" sx={markdownBodySx}>
      <Markdown remarkPlugins={[remarkGfm]} components={commentComponents}>
        {normalizeTaskLists(body)}
      </Markdown>
    </Box>
  )
}

/**
 * Feld-Konfiguration für Kommentare: Verfassen und Bearbeiten teilen sie, damit beide Felder gleich
 * aussehen und mehrzeilige Kommentare auch bequem entstehen können — nicht nur bearbeitbar sind.
 */
export const commentFieldProps = { multiline: true, minRows: 3 } as const

/** Zuständige-Sektion: Autocomplete im Edit-Modus, sonst Chips oder Leer-Hinweis. */
export function AssigneeSection({
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
export function LabelSection({
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
                  <TextField {...commentFieldProps} size="small" value={editingBody}
                    onChange={(e) => onEditingBodyChange(e.target.value)}
                    error={isTooLong(editingBody)}
                    helperText={isTooLong(editingBody) ? tooLongMessage(editingBody.length) : undefined}
                    slotProps={{ htmlInput: { 'aria-label': 'Kommentar bearbeiten' } }} />
                  <Stack direction="row" spacing={1}>
                    <Button size="small" variant="contained" onClick={onSaveEdit} disabled={isTooLong(editingBody)}>Speichern</Button>
                    <Button size="small" onClick={onCancelEdit}>Abbrechen</Button>
                  </Stack>
                </Stack>
              ) : (
                <CommentBody body={c.body} />
              )}
            </Box>
          )
        })}
        {comments.length === 0 && <Typography color="text.secondary">Noch keine Kommentare.</Typography>}
      </Stack>
      {/* alignItems explizit: sonst zieht der Default `stretch` den Senden-Button auf Feldhöhe. */}
      <Stack direction="row" spacing={1} alignItems="flex-start" sx={{ mt: 1 }}>
        <TextField {...commentFieldProps} size="small" fullWidth placeholder="Kommentar schreiben" value={newComment}
          onChange={(e) => onNewCommentChange(e.target.value)}
          error={isTooLong(newComment)}
          helperText={isTooLong(newComment) ? tooLongMessage(newComment.length) : undefined}
          slotProps={{ htmlInput: { 'aria-label': 'Kommentar schreiben' } }} />
        <Button variant="contained" size="small" onClick={onAdd} disabled={isTooLong(newComment)}>
          Senden
        </Button>
      </Stack>
    </Box>
  )
}

/** Aktivitäts-Sektion: chronologische Ereignisliste (nur View-Modus). */
/**
 * Aktivitätsverlauf mit Herkunfts-Kennzeichnung (#518): Token-Einträge tragen einen gefüllten Chip
 * mit dem server-verifizierten Token-Namen (Tatsache), eine vorhandene Modell-Angabe einen
 * outlined-Chip in Kursivschrift (Client-Selbstauskunft) — die beiden Verlässlichkeitsklassen
 * dürfen nicht gleich aussehen. Session- und Alt-Einträge bleiben unmarkiert: der Mensch ist der
 * Default, der Verlauf bleibt ruhig.
 */
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
          <Stack key={a.id} direction="row" spacing={0.5} alignItems="center" flexWrap="wrap">
            <Typography variant="caption" color="text.secondary">
              {new Date(a.createdAt).toLocaleString('de-DE')} · {actorName(a.actorUserId)} ·{' '}
              {a.detail}
            </Typography>
            {a.origin === 'TOKEN' && (
              <Chip
                data-testid="activity-token"
                size="small"
                label={a.tokenName ?? 'Token'}
                aria-label={`Über Access-Token: ${a.tokenName ?? 'unbenannt'}`}
                sx={{ height: 18, fontSize: '0.65rem' }}
              />
            )}
            {a.agent && (
              <Tooltip title="Angabe des Clients, nicht verifiziert">
                <Chip
                  data-testid="activity-agent"
                  size="small"
                  variant="outlined"
                  label={a.agent}
                  aria-label={`Modell-Angabe des Clients, nicht verifiziert: ${a.agent}`}
                  sx={{ height: 18, fontSize: '0.65rem', fontStyle: 'italic' }}
                />
              </Tooltip>
            )}
          </Stack>
        ))}
        {activities.length === 0 && (
          <Typography color="text.secondary">Keine Aktivität.</Typography>
        )}
      </Stack>
    </Box>
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

/**
 * Abhängigkeits-Zeile im Lesemodus. Mit `onOpen` wird jede Nummer ein echter Button (Tastatur und
 * sichtbarer Fokus kommen von MUIs `Link component="button"`), ohne bleibt es reiner Text — so
 * entsteht ohne bekannte `projectId` kein Verweis, der nirgendwohin führen könnte.
 */
function DependencyList({
  dependencies,
  onOpen,
}: Readonly<{ dependencies: number[]; onOpen?: (number: number) => void }>) {
  return (
    <Typography variant="body2" color="text.secondary" aria-label="Abhängigkeiten">
      Abhängig von:{' '}
      {dependencies.map((n, index) => (
        <Fragment key={n}>
          {index > 0 && ', '}
          {onOpen ? (
            <Link
              component="button"
              type="button"
              variant="body2"
              aria-label={`Karte #${n} öffnen`}
              onClick={() => onOpen(n)}
            >
              #{n}
            </Link>
          ) : (
            `#${n}`
          )}
        </Fragment>
      ))}
    </Typography>
  )
}

/** View-Modus-Inhalt: Beschreibung (Markdown mit Task-Checkboxen), Abhängigkeiten, Fälligkeitsdatum. */
function CardBodyView({
  body,
  canEdit,
  onToggleTask,
  dependencies,
  onOpenDependency,
  isEpic,
  dueDate,
  dueOverdue,
}: Readonly<{
  body: string
  canEdit: boolean
  onToggleTask: (index: number) => void
  dependencies: number[]
  onOpenDependency?: (number: number) => void
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
        <DependencyList dependencies={dependencies} onOpen={onOpenDependency} />
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
  card: CardDetail
  canEdit: boolean
  /**
   * Projekt der Karte. Nur mit dieser Angabe sind die `#N`-Abhängigkeitsverweise anklickbar — die
   * Nummer ist projektweit vergeben und wird über `cardsApi.byNumber` aufgelöst.
   */
  projectId?: number
  /** Ob der Nutzer Kommentare moderieren (löschen) darf — Projekt-ADMIN/OWNER oder Plattform-Admin. */
  canModerateComments?: boolean
  onClose: () => void
  onChanged?: () => void
  /** Direkt im Edit-Modus öffnen (z. B. aus dem Karten-⋮-Menü). */
  initialEditing?: boolean
  /** Spaltenname für den Status-Chip (bei Karten). */
  columnName?: string
  /**
   * Ort der Karte (Projekt / Board / Spalte) für den Modal-Kopf. Optional: Aufrufer ohne diesen
   * Kontext (Epics-Seite, Ideen-Planung) zeigen wie bisher keinen Pfad — ein leerer Platzhalter
   * wäre schlechter als keine Angabe. `null` ist gleichbedeutend mit „nicht gesetzt", damit
   * Aufrufer mit noch nicht geladenem Board den Wert direkt durchreichen können.
   */
  location?: CardLocation | null
  /** Board-Epics für das Epic-Dropdown. */
  epics?: Epic[]
  /**
   * Ob die Epic-Zuordnung geändert werden darf (Default `true`). `false` heißt: Der Aufrufer konnte
   * den Optionsvorrat nicht laden (archiviertes Board, board-lose Idee). Ein leeres Dropdown böte
   * dann nur „(kein Epic)" an und löschte auf Klick eine bestehende Zuordnung, ohne sie je gezeigt
   * zu haben — deshalb bleibt das Feld dort lesend, während die übrigen Felder bearbeitbar sind.
   */
  canEditEpic?: boolean
  /** Ob die Labels geändert werden dürfen (Default `true`) — dieselbe Begründung wie {@link canEditEpic}. */
  canEditLabels?: boolean
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
    | 'update'
    | 'setAssignees'
    | 'setLabels'
    | 'getActivity'
    | 'restore'
    | 'moveToIdeaStorage'
    | 'byNumber'
  >
  boardsApi?: Pick<typeof defaultBoardsApi, 'get'>
}

/**
 * Interne Props der Detail-Ansicht: Die Navigation über `#N`-Verweise steuert der `CardDetailModal`-
 * Wrapper, die Ansicht selbst kennt nur die beiden Callbacks.
 */
type ViewProps = Props & {
  /** Öffnet den Verweis auf die Kartennummer; fehlt, wenn keine Auflösung möglich ist. */
  onOpenDependency?: (number: number) => void
  /** Eine Ebene im Verweis-Stack zurück; fehlt auf der Ausgangskarte. */
  onBack?: () => void
}

function CardDetailModalView({
  card,
  canEdit,
  canModerateComments = false,
  onClose,
  onChanged,
  initialEditing = false,
  columnName,
  location,
  epics = [],
  canEditEpic = true,
  canEditLabels = true,
  childCards = [],
  members = [],
  boardLabels = [],
  commentsApi = defaultCommentsApi,
  attachmentsApi = defaultAttachmentsApi,
  cardsApi = defaultCardsApi,
  onOpenDependency,
  onBack,
}: Readonly<ViewProps>) {
  const { user } = useAuth()
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

  // In den Ideen-Pool: Alltags-Aktion (an canEdit gebunden, nicht editiermodus-gegatet).
  const moveToIdeaStorage = async () => {
    try {
      await cardsApi.moveToIdeaStorage(card.id)
      onChanged?.()
      notify('In den Ideen-Pool verschoben — unter Ideen zu finden.', 'success')
      onClose()
    } catch {
      // Bei einem Fehler bleibt der Dialog offen — die Karte verschwindet nicht.
      notify('In den Ideen-Pool verschieben fehlgeschlagen.', 'error')
    }
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
  // Abhängigkeiten als lokaler Zustand wie Titel/Beschreibung (#537): Die `card`-Prop des Parents
  // bleibt nach dem Speichern veraltet — ohne diesen State zeigte der Lesemodus die frisch
  // gespeicherten Abhängigkeiten erst nach Schließen und Neuöffnen, und ein Task-Toggle hätte sie
  // mit dem alten Prop-Stand überschrieben. Kein Sync-Effect nötig: der Wrapper remountet die View
  // per `key` je Karte.
  const [deps, setDeps] = useState(card.dependencies)
  const [depsInput, setDepsInput] = useState(card.dependencies.join(', '))
  const [depsError, setDepsError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const notify = useSnackbar()

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
    // Aus dem lokalen Zustand, nicht aus der Prop — sonst verwürfe erneutes Bearbeiten nach
    // einem Save die gerade gespeicherten Abhängigkeiten (#537).
    setDepsInput(deps.join(', '))
    setDepsError(null)
    setEditing(true)
  }

  const save = async () => {
    // Kein `!title.trim() || saving`-Guard nötig: der einzige Aufrufer ist der Speichern-Button,
    // der exakt unter diesen Bedingungen disabled ist (siehe DialogActions) — der Guard wäre tot.
    const { deps: parsedDeps, valid } = parseDependencyInput(depsInput)
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
        parsedDeps,
        isEpic ? shortcode.trim() || null : undefined,
        isEpic ? undefined : parentId,
        isEpic ? undefined : dueInputToIso(dueInput),
      )
      setDeps(parsedDeps)
      setEditing(false)
      onChanged?.()
      notify('Karte gespeichert.', 'success')
    } catch {
      notify('Speichern fehlgeschlagen.', 'error')
    } finally {
      setSaving(false)
    }
  }

  // Klick auf eine Checkbox im View-Modus: n-ten Marker im Beschreibungstext flippen und sofort
  // persistieren (optimistisch, Rollback bei Fehler) — ohne den Edit-Modus zu öffnen.
  const toggleTask = async (index: number) => {
    if (!canEdit || saving) return
    const previous = body
    // `index` stammt immer aus einer real gerenderten Checkbox (MarkdownInput zählt in derselben
    // Marker-Logik wie toggleTaskAt), daher findet toggleTaskAt stets einen Treffer und flippt —
    // ein No-op-Ergebnis (next === previous) ist ausgeschlossen, kein toter Guard nötig.
    const next = toggleTaskAt(previous, index)
    setBody(next)
    setSaving(true)
    try {
      await cardsApi.update(
        card.id,
        card.title,
        next,
        // Lokaler Abhängigkeits-Zustand statt Prop (#537): sonst rollte ein Checkbox-Klick nach
        // einem Abhängigkeits-Save die frisch gespeicherten Werte auf den alten Stand zurück.
        deps,
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
          {onBack && (
            <IconButton size="small" aria-label="Zurück zur vorherigen Karte" onClick={onBack}>
              <ArrowBackIcon fontSize="small" />
            </IconButton>
          )}
          <CardStatusChip isEpic={isEpic} columnName={columnName} colors={colors} />
          {/* Legacy-Pool-Ideen ohne projektweite Nummer zeigen kein nacktes „#". */}
          {card.number != null && (
            <Typography component="span" variant="body2" color="text.secondary">
              #{card.number}
            </Typography>
          )}
          <Typography component="span" sx={{ fontWeight: 600 }}>
            {card.title}
          </Typography>
          <Box sx={{ flexGrow: 1 }} />
          {canEdit && !editing && (
            <Button size="small" variant="outlined" onClick={startEditing}>
              Bearbeiten
            </Button>
          )}
        </Stack>
        {/*
          Ortspfad unter dem Titel und klar untergeordnet (kleinere Schrift, sekundäre Farbe): Die
          Hauptaussage bleibt die Karte, der Pfad beantwortet nur „wo liegt sie". Als `span` statt
          als Überschrift — der `DialogTitle` ist bereits ein `h2` und nimmt nur Phrasing-Content
          auf — und ohne `aria-current`: Die aktuelle Seite ist nicht die Spalte, sondern die
          geöffnete Karte.
        */}
        {location && (
          <Box component="span" sx={{ display: 'block', mt: 0.5 }}>
            <Breadcrumbs
              items={cardLocationCrumbs(location)}
              variant="body2"
              component="span"
              currentPage={false}
            />
          </Box>
        )}
      </DialogTitle>

      <DialogContent dividers sx={{ overflowY: 'auto' }}>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {editing ? (
            <CardFields
              isEpic={isEpic}
              title={title}
              body={body}
              shortcode={shortcode}
              parentId={parentId}
              epics={epics}
              epicReadOnly={!canEditEpic}
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
              dependencies={deps}
              onOpenDependency={onOpenDependency}
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
              canEdit={canEdit && canEditLabels}
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
            <Button
              variant="contained"
              onClick={() => void save()}
              disabled={!title.trim() || saving || isTooLong(body)}
            >
              Speichern
            </Button>
          </>
        ) : (
          <>
            {canEdit && card.archived && (
              <Button onClick={() => void restore()}>Wiederherstellen</Button>
            )}
            {canEdit && !card.archived && !card.ideaStored && !isEpic && (
              <Button onClick={() => void moveToIdeaStorage()}>In den Ideen-Pool</Button>
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

/** Eine über einen `#N`-Verweis geöffnete Karte samt aufgelöstem Spaltennamen für den Status-Chip. */
interface LinkedCard {
  card: CardByNumber
  columnName?: string
}

/**
 * Spaltenname der verknüpften Karte, aufgelöst über ihr eigenes Board — der Spaltenname der
 * Ausgangskarte gilt nicht, weil der Verweis projektweit auf ein anderes Board zeigen kann. Eine
 * board-lose Pool-Idee hat keine Spalte; scheitert der Board-Abruf, bleibt nur der Chip leer,
 * statt die ganze Navigation abzubrechen.
 */
async function resolveColumnName(
  linked: CardByNumber,
  boardsApi: Pick<typeof defaultBoardsApi, 'get'>,
): Promise<string | undefined> {
  if (linked.boardId == null) return undefined
  try {
    const board = await boardsApi.get(linked.boardId)
    return board.columns.find((c) => c.id === linked.columnId)?.name
  } catch {
    return undefined
  }
}

/**
 * Detail-Modal einer Karte mit Navigation über die `#N`-Abhängigkeitsverweise. Der Wrapper hält den
 * Verweis-Stack und rendert immer genau eine Karte: die Ausgangskarte oder die zuletzt geöffnete
 * verknüpfte Karte. Ein Stack (statt eines einstufigen Zurück) ist nötig, damit „Zurück“ nach
 * mehreren Sprüngen dorthin führt, wo man tatsächlich herkam.
 *
 * Eine über einen Verweis geöffnete Karte wird bewusst **nur gelesen**: Ihr board-spezifischer
 * Bearbeitungskontext (Epics, Labels, Spalten) gehört zum Board des Aufrufers und wäre für ein
 * fremdes Board falsch. Zum Bearbeiten öffnet man die Karte auf ihrem eigenen Board. Aus demselben
 * Grund erbt sie auch den Ortspfad (`location`) der Ausgangskarte nicht — er zeigte sonst auf ein
 * fremdes Board. Lieber kein Pfad als ein falscher; der Status-Chip nennt weiterhin ihre Spalte.
 */
export function CardDetailModal(props: Readonly<Props>) {
  const { projectId, cardsApi = defaultCardsApi, boardsApi = defaultBoardsApi } = props
  const [stack, setStack] = useState<LinkedCard[]>([])
  const notify = useSnackbar()
  const top = stack.at(-1)

  // Nicht auflösbar (gelöscht, kein Zugriff): Hinweis statt totem Sprung — die aktuelle Karte
  // bleibt offen. Eine Vorab-Prüfung aller Verweise würde je geöffneter Karte N Requests kosten.
  const openDependency = async (project: number, number: number) => {
    try {
      const linked = await cardsApi.byNumber(project, number)
      const columnName = await resolveColumnName(linked, boardsApi)
      setStack((s) => [...s, { card: linked, columnName }])
    } catch {
      notify(`Karte #${number} nicht gefunden — gelöscht oder kein Zugriff.`, 'error')
    }
  }
  const onOpenDependency =
    projectId == null ? undefined : (n: number) => void openDependency(projectId, n)

  if (!top) return <CardDetailModalView {...props} onOpenDependency={onOpenDependency} />
  return (
    <CardDetailModalView
      // Kartenwechsel remountet die Ansicht, damit ihr aus `card` abgeleiteter Zustand frisch ist.
      key={top.card.id}
      card={top.card}
      canEdit={false}
      canModerateComments={props.canModerateComments}
      columnName={top.columnName}
      members={props.members}
      commentsApi={props.commentsApi}
      attachmentsApi={props.attachmentsApi}
      cardsApi={cardsApi}
      onClose={props.onClose}
      onOpenDependency={onOpenDependency}
      onBack={() => setStack((s) => s.slice(0, -1))}
    />
  )
}
