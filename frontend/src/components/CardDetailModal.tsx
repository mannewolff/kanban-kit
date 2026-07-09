import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Divider from '@mui/material/Divider'
import IconButton from '@mui/material/IconButton'
import Link from '@mui/material/Link'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { attachmentsApi as defaultAttachmentsApi, type Attachment, type AttachmentsApi } from '../api/attachments'
import { cardsApi as defaultCardsApi } from '../api/cards'
import type { Card } from '../api/cards'
import { commentsApi as defaultCommentsApi, type Comment, type CommentsApi } from '../api/comments'
import type { Epic } from '../api/epics'
import { epicShortcode } from '../lib/epicMeta'
import { useAuth } from '../auth/AuthContext'

interface Props {
  card: Card
  canEdit: boolean
  onClose: () => void
  onChanged?: () => void
  /** Board-Epics für die Epic-Zuordnung einer Karte. */
  epics?: Epic[]
  /** Setzt/löst die Epic-Zuordnung einer Karte. */
  onAssignParent?: (cardId: number, parentId: number | null) => Promise<void> | void
  /** Kind-Karten eines Epics (nur bei type === 'EPIC'). */
  children?: Card[]
  commentsApi?: CommentsApi
  attachmentsApi?: AttachmentsApi
  cardsApi?: Pick<typeof defaultCardsApi, 'update'>
}

export function CardDetailModal({
  card,
  canEdit,
  onClose,
  onChanged,
  epics = [],
  onAssignParent,
  children = [],
  commentsApi = defaultCommentsApi,
  attachmentsApi = defaultAttachmentsApi,
  cardsApi = defaultCardsApi,
}: Props) {
  const { user } = useAuth()
  const isEpic = card.type === 'EPIC'
  const [description, setDescription] = useState(card.description ?? '')
  const [shortcode, setShortcode] = useState(card.shortcode ?? '')
  const [parentId, setParentId] = useState<number | null>(card.parentId)
  const [editingDesc, setEditingDesc] = useState(false)
  const [comments, setComments] = useState<Comment[]>([])
  const [newComment, setNewComment] = useState('')
  const [attachments, setAttachments] = useState<Attachment[]>([])
  const [previews, setPreviews] = useState<Record<number, string>>({})
  const [error, setError] = useState<string | null>(null)

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

  const saveDescription = async () => {
    await cardsApi.update(
      card.id,
      card.title,
      description,
      undefined,
      isEpic ? shortcode.trim() || null : undefined,
    )
    setEditingDesc(false)
    onChanged?.()
  }

  const changeParent = async (next: number | null) => {
    setParentId(next)
    await onAssignParent?.(card.id, next)
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

  const uploadFile = async (file: File) => {
    setError(null)
    try {
      const created = await attachmentsApi.upload(card.id, file)
      setAttachments((a) => [...a, created])
      if (created.contentType.startsWith('image/')) {
        const blob = await attachmentsApi.fetchBlob(created.id)
        setPreviews((p) => ({ ...p, [created.id]: URL.createObjectURL(blob) }))
      }
    } catch {
      setError('Upload fehlgeschlagen (evtl. Anhangslimit erreicht).')
    }
  }

  const deleteAttachment = async (id: number) => {
    await attachmentsApi.remove(id)
    setAttachments((a) => a.filter((x) => x.id !== id))
  }

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        {isEpic ? 'Epic ' : ''}#{card.number} · {card.title}
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3}>
          {!isEpic && onAssignParent && (
            <Box>
              <Typography variant="subtitle2" gutterBottom>
                Epic
              </Typography>
              <TextField
                select
                size="small"
                value={parentId ?? ''}
                disabled={!canEdit}
                onChange={(e) => void changeParent(e.target.value === '' ? null : Number(e.target.value))}
                inputProps={{ 'aria-label': 'Epic-Zuordnung' }}
                sx={{ minWidth: 220 }}
              >
                <MenuItem value="">(kein Epic)</MenuItem>
                {epics.map((epic) => (
                  <MenuItem key={epic.id} value={epic.id}>
                    {epicShortcode(epic.title, epic.shortcode)} – {epic.title}
                  </MenuItem>
                ))}
              </TextField>
            </Box>
          )}

          {card.dependencies.length > 0 && (
            <Box>
              <Typography variant="subtitle2" gutterBottom>
                Abhängig von
              </Typography>
              <Stack direction="row" spacing={1}>
                {card.dependencies.map((n) => (
                  <Chip key={n} label={`#${n}`} size="small" />
                ))}
              </Stack>
            </Box>
          )}

          <Box>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Typography variant="subtitle2">Beschreibung</Typography>
              {canEdit && !editingDesc && (
                <Button size="small" onClick={() => setEditingDesc(true)}>
                  Bearbeiten
                </Button>
              )}
            </Stack>
            {editingDesc ? (
              <Stack spacing={1} sx={{ mt: 1 }}>
                {isEpic && (
                  <TextField size="small" label="Kürzel" value={shortcode}
                    onChange={(e) => setShortcode(e.target.value)}
                    inputProps={{ maxLength: 16, 'aria-label': 'Kürzel' }} sx={{ maxWidth: 200 }} />
                )}
                <TextField multiline minRows={4} fullWidth value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  inputProps={{ 'aria-label': 'Beschreibung bearbeiten' }} />
                <Stack direction="row" spacing={1}>
                  <Button variant="contained" size="small" onClick={saveDescription}>
                    Speichern
                  </Button>
                  <Button size="small" onClick={() => { setDescription(card.description ?? ''); setEditingDesc(false) }}>
                    Abbrechen
                  </Button>
                </Stack>
              </Stack>
            ) : (
              <Box sx={{ mt: 1 }} data-testid="description-view">
                {description ? (
                  <Markdown remarkPlugins={[remarkGfm]}>{description}</Markdown>
                ) : (
                  <Typography color="text.secondary">Keine Beschreibung.</Typography>
                )}
              </Box>
            )}
          </Box>

          {isEpic && (
            <>
              <Divider />
              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  Karten ({children.length})
                </Typography>
                <Stack spacing={0.5}>
                  {children.map((c) => (
                    <Typography key={c.id} variant="body2">
                      #{c.number} · {c.title}
                    </Typography>
                  ))}
                  {children.length === 0 && (
                    <Typography color="text.secondary">Keine zugeordneten Karten.</Typography>
                  )}
                </Stack>
              </Box>
            </>
          )}

          <Divider />

          <Box>
            <Typography variant="subtitle2" gutterBottom>
              Anhänge
            </Typography>
            {error && <Alert severity="error" sx={{ mb: 1 }}>{error}</Alert>}
            <Stack spacing={1}>
              {attachments.map((a) => (
                <Box key={a.id}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Link href={`/api/attachments/${a.id}`}>{a.filename}</Link>
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
                      sx={{ maxWidth: 240, maxHeight: 160, mt: 0.5, borderRadius: 1 }} />
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
              {comments.map((c) => (
                <Box key={c.id}>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography variant="body2" fontWeight={600}>
                      {c.authorName}
                    </Typography>
                    {user && c.authorUserId === user.userId && (
                      <IconButton size="small" aria-label="Kommentar löschen" onClick={() => deleteComment(c.id)}>
                        ✕
                      </IconButton>
                    )}
                  </Stack>
                  <Typography variant="body2">{c.body}</Typography>
                </Box>
              ))}
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
        </Stack>
      </DialogContent>
    </Dialog>
  )
}
