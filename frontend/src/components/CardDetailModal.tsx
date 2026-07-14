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
import { memo, useCallback, useEffect, useRef, useState, type ComponentPropsWithoutRef } from 'react'
import Markdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { attachmentsApi as defaultAttachmentsApi, type Attachment, type AttachmentsApi } from '../api/attachments'
import { cardsApi as defaultCardsApi } from '../api/cards'
import type { Card } from '../api/cards'
import { commentsApi as defaultCommentsApi, type Comment, type CommentsApi } from '../api/comments'
import type { Epic } from '../api/epics'
import type { Member } from '../api/members'
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
 * Rendert die Karten-Beschreibung als Markdown mit klickbaren Task-Checkboxen. Als eigene,
 * memoized Komponente ausgelagert, damit Re-Renders des Modals (z. B. Nachladen von Kommentaren)
 * die Beschreibung nicht neu mounten. Der Checkbox-Index läuft in Dokumentreihenfolge (lokaler
 * Zähler pro Render); `onToggle` muss stabil sein, damit `memo` greift.
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
  let counter = 0
  const components: Components = {
    input: ({ node, ...props }: ComponentPropsWithoutRef<'input'> & { node?: unknown }) => {
      void node
      if (props.type !== 'checkbox') {
        return <input {...props} />
      }
      const index = counter++
      return (
        <input
          type="checkbox"
          checked={props.checked ?? false}
          disabled={!canEdit}
          onChange={() => onToggle(index)}
          aria-label={`Aufgabe ${index + 1}`}
        />
      )
    },
  }
  return (
    <Markdown remarkPlugins={[remarkGfm]} components={components}>
      {normalizeTaskLists(body)}
    </Markdown>
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
  commentsApi?: CommentsApi
  attachmentsApi?: AttachmentsApi
  cardsApi?: Pick<typeof defaultCardsApi, 'update' | 'setAssignees'>
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
  commentsApi = defaultCommentsApi,
  attachmentsApi = defaultAttachmentsApi,
  cardsApi = defaultCardsApi,
}: Props) {
  const { user } = useAuth()
  const isEpic = card.type === 'EPIC'
  const [assigneeIds, setAssigneeIds] = useState<number[]>(card.assignees)

  const memberName = (userId: number) =>
    members.find((m) => m.userId === userId)?.displayName ?? `#${userId}`

  const saveAssignees = async (ids: number[]) => {
    setAssigneeIds(ids)
    await cardsApi.setAssignees(card.id, ids)
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
      list
        .filter((a) => a.contentType.startsWith('image/'))
        .forEach((a) => {
          void attachmentsApi
            .fetchBlob(a.id)
            .then((blob) => setPreviews((p) => ({ ...p, [a.id]: URL.createObjectURL(blob) })))
        })
    })
  }, [card.id, commentsApi, attachmentsApi])

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
          {isEpic ? (
            <Chip label="Epic" size="small" color="secondary" />
          ) : (
            colors && (
              <Chip
                label={columnName}
                size="small"
                sx={{ bgcolor: colors.bg, color: colors.text, fontWeight: 600 }}
              />
            )
          )}
          <Typography component="span" variant="body2" color="text.secondary">
            #{card.number}
          </Typography>
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
      </DialogTitle>

      <DialogContent dividers sx={{ overflowY: 'auto' }}>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {editing ? (
            <>
              <TextField
                label="Titel"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                required
                autoFocus
                fullWidth
                inputProps={{ maxLength: 300, 'aria-label': 'Titel' }}
              />
              <TextField
                label="Markdown-Beschreibung"
                value={body}
                onChange={(e) => setBody(e.target.value)}
                multiline
                rows={8}
                fullWidth
                inputProps={{ maxLength: 10_000, 'aria-label': 'Markdown-Beschreibung' }}
                sx={{ '& textarea': { fontFamily: 'monospace', resize: 'vertical' } }}
              />
              {isEpic ? (
                <TextField
                  label="Kürzel"
                  value={shortcode}
                  onChange={(e) => setShortcode(e.target.value)}
                  inputProps={{ maxLength: 16, 'aria-label': 'Kürzel' }}
                  sx={{ maxWidth: 200 }}
                />
              ) : (
                <>
                  <TextField
                    select
                    SelectProps={{ native: true }}
                    label="Epic"
                    value={parentId ?? ''}
                    onChange={(e) => setParentId(e.target.value === '' ? null : Number(e.target.value))}
                    inputProps={{ 'aria-label': 'Epic' }}
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
                  <TextField
                    label="Abhängig von (Nummern, kommagetrennt)"
                    value={depsInput}
                    onChange={(e) => {
                      setDepsInput(e.target.value)
                      if (depsError) setDepsError(null)
                    }}
                    error={depsError != null}
                    helperText={depsError ?? 'z. B. 12, 34'}
                    inputProps={{ 'aria-label': 'Abhängig von' }}
                    fullWidth
                  />
                  <TextField
                    type="date"
                    label="Fällig am"
                    value={dueInput}
                    onChange={(e) => setDueInput(e.target.value)}
                    InputLabelProps={{ shrink: true }}
                    inputProps={{ 'aria-label': 'Fällig am' }}
                    sx={{ maxWidth: 200 }}
                  />
                </>
              )}
            </>
          ) : (
            <>
              <Box aria-label="Beschreibung" data-testid="description-view" sx={descriptionSx}>
                {body ? (
                  <TaskMarkdown body={body} canEdit={canEdit} onToggle={onToggleTask} />
                ) : (
                  <Typography color="text.secondary">Keine Beschreibung.</Typography>
                )}
              </Box>
              {card.dependencies.length > 0 && (
                <Typography variant="body2" color="text.secondary" aria-label="Abhängigkeiten">
                  Abhängig von: {card.dependencies.map((n) => `#${n}`).join(', ')}
                </Typography>
              )}
              {!isEpic && card.dueDate && (
                <Typography
                  variant="body2"
                  aria-label="Fälligkeitsdatum"
                  color={dueOverdue ? 'error' : 'text.secondary'}
                  sx={{ fontWeight: dueOverdue ? 600 : 400 }}
                >
                  Fällig am {formatDueDate(card.dueDate)}
                  {dueOverdue && ' — überfällig'}
                </Typography>
              )}
            </>
          )}

          {!isEpic && (
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
                  onChange={(_, selected) => void saveAssignees(selected.map((m) => m.userId))}
                  renderInput={(params) => (
                    <TextField {...params} label="Zuständige" inputProps={{ ...params.inputProps, 'aria-label': 'Zuständige' }} />
                  )}
                />
              ) : assigneeIds.length > 0 ? (
                <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap' }}>
                  {assigneeIds.map((uid) => (
                    <Chip key={uid} size="small" label={memberName(uid)} />
                  ))}
                </Stack>
              ) : (
                <Typography color="text.secondary">Niemand zugewiesen.</Typography>
              )}
            </Box>
          )}

          {!editing && isEpic && (
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
          )}

          {!editing && (
            <>
              <Divider />
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
                          <Link component="button" type="button" onClick={() => void openPreview(a)}
                            sx={{ textAlign: 'left' }}>
                            {a.filename}
                          </Link>
                        ) : (
                          <Link href={`/api/attachments/${a.id}`}>{a.filename}</Link>
                        )}
                        <Typography variant="caption" color="text.secondary">
                          {Math.round(a.size / 1024)} KB
                        </Typography>
                        {canEdit && (
                          <IconButton size="small" aria-label={`Anhang ${a.filename} löschen`}
                            onClick={() => deleteAttachment(a.id)}>
                            ✕
                          </IconButton>
                        )}
                      </Stack>
                      {previews[a.id] && (
                        <Box component="img" src={previews[a.id]} alt={a.filename}
                          onClick={() => void openPreview(a)}
                          sx={{ maxWidth: 240, maxHeight: 160, mt: 0.5, borderRadius: 1, cursor: 'pointer', display: 'block' }} />
                      )}
                    </Box>
                  ))}
                  {attachments.length === 0 && <Typography color="text.secondary">Keine Anhänge.</Typography>}
                </Stack>
                {canEdit && (
                  <Button component="label" size="small" sx={{ mt: 1 }}>
                    Datei anhängen
                    <input hidden type="file" aria-label="Datei anhängen"
                      onChange={(e) => { const f = e.target.files?.[0]; if (f) void uploadFile(f) }} />
                  </Button>
                )}
              </Box>

              <Divider />
              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  Kommentare
                </Typography>
                <Stack spacing={1}>
                  {comments.map((c) => {
                    const isAuthor = user != null && c.authorUserId === user.userId
                    return (
                      <Box key={c.id}>
                        <Stack direction="row" justifyContent="space-between" alignItems="center">
                          <Typography variant="body2" fontWeight={600}>
                            {c.authorName}
                          </Typography>
                          <Stack direction="row" spacing={0.5}>
                            {/* Bearbeiten darf nur der Autor selbst. */}
                            {isAuthor && editingCommentId !== c.id && (
                              <IconButton size="small" aria-label="Kommentar bearbeiten" onClick={() => startEditComment(c)}>
                                ✎
                              </IconButton>
                            )}
                            {/* Löschen ist Moderation: nur Admin/Owner (bzw. Plattform-Admin). */}
                            {canModerateComments && (
                              <IconButton size="small" aria-label="Kommentar löschen" onClick={() => deleteComment(c.id)}>
                                ✕
                              </IconButton>
                            )}
                          </Stack>
                        </Stack>
                        {editingCommentId === c.id ? (
                          <Stack spacing={1}>
                            <TextField multiline size="small" value={editingBody}
                              onChange={(e) => setEditingBody(e.target.value)}
                              inputProps={{ maxLength: 10_000, 'aria-label': 'Kommentar bearbeiten' }} />
                            <Stack direction="row" spacing={1}>
                              <Button size="small" variant="contained" onClick={() => void saveEditComment()}>Speichern</Button>
                              <Button size="small" onClick={() => setEditingCommentId(null)}>Abbrechen</Button>
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
                    onChange={(e) => setNewComment(e.target.value)} inputProps={{ 'aria-label': 'Kommentar schreiben' }} />
                  <Button variant="contained" size="small" onClick={addComment}>
                    Senden
                  </Button>
                </Stack>
              </Box>
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
          <Button onClick={onClose}>Schließen</Button>
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
