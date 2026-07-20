import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import FormControlLabel from '@mui/material/FormControlLabel'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Switch from '@mui/material/Switch'
import TextField from '@mui/material/TextField'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import { useEffect, useState } from 'react'
import { accessTokensApi, type AccessToken, type CreatedAccessToken } from '../api/accessTokens'
import { boardsApi, type Board } from '../api/boards'
import { projectsApi, type Project } from '../api/projects'
import { useAuth } from '../auth/AuthContext'
import { useEditMode } from '../lib/EditModeContext'
import { canEditCards, isPlatformAdmin } from '../lib/roles'

/**
 * Administrations-/Einstellungsseite (für alle angemeldeten Nutzer): Editiermodus-Schalter und die
 * Verwaltung persönlicher API-Tokens.
 */
export function AdministrationPage() {
  const { editMode, setEditMode } = useEditMode()

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Administration
      </Typography>
      <Stack spacing={2} sx={{ maxWidth: 720 }}>
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={1}>
            <Typography variant="h6" component="h2">
              Editiermodus
            </Typography>
            <FormControlLabel
              control={
                <Switch
                  checked={editMode}
                  onChange={(e) => setEditMode(e.target.checked)}
                  slotProps={{ input: { 'aria-label': 'Editiermodus aktivieren' } }}
                />
              }
              label="Bearbeiten aktivieren"
            />
            <Typography variant="body2" color="text.secondary">
              Ist der Editiermodus aktiv, erscheinen die Bearbeiten-Symbole (etwa zum Umbenennen von
              Projekten, Boards und Spalten), sofern du die nötigen Rechte hast. Der Modus ist beim
              Start immer aus und wird nicht über einen Neustart hinweg gemerkt.
            </Typography>
          </Stack>
        </Paper>

        <ApiTokensSection />
      </Stack>
    </Box>
  )
}

/** Verwaltung persönlicher API-Tokens (Liste, Erzeugen mit Board-Bindung, Widerrufen). */
function ApiTokensSection() {
  const { user } = useAuth()
  const platformAdmin = isPlatformAdmin(user)

  const [tokens, setTokens] = useState<AccessToken[]>([])
  const [projects, setProjects] = useState<Project[]>([])
  const [created, setCreated] = useState<CreatedAccessToken | null>(null)
  const [copied, setCopied] = useState(false)
  const [dialogOpen, setDialogOpen] = useState(false)

  const reloadTokens = () => {
    void accessTokensApi.list().then(setTokens)
  }

  useEffect(() => {
    reloadTokens()
    // Nur Projekte anbieten, in denen der Nutzer Karten anlegen darf — ein board-gebundenes Token
    // erlaubt genau das (via kanbancompat). Das Backend erzwingt dasselbe (TICKET_CREATE).
    void projectsApi
      .list()
      .then((ps) => setProjects(ps.filter((p) => canEditCards(p.role, platformAdmin))))
  }, [platformAdmin])

  const projectName = (id: number | null): string =>
    id == null ? '' : (projects.find((p) => p.id === id)?.name ?? `#${id}`)

  const binding = (t: AccessToken): string =>
    t.projectId != null && t.boardId != null
      ? `Projekt „${projectName(t.projectId)}“ · Board ${t.boardId}`
      : 'ungebunden'

  const copyPlaintext = async () => {
    if (!created) return
    await navigator.clipboard.writeText(created.plaintext)
    setCopied(true)
  }

  const revoke = async (id: number) => {
    if (!window.confirm('Dieses Token widerrufen? Clients, die es nutzen, verlieren den Zugriff.')) {
      return
    }
    await accessTokensApi.revoke(id)
    reloadTokens()
  }

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={1.5}>
        <Stack direction="row" alignItems="center" spacing={1}>
          <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>
            API-Tokens
          </Typography>
          <Button
            variant="outlined"
            size="small"
            disabled={projects.length === 0}
            onClick={() => {
              setCreated(null)
              setDialogOpen(true)
            }}
          >
            Token erzeugen
          </Button>
        </Stack>

        <Typography variant="body2" color="text.secondary">
          Ein board-gebundenes Token treibt ein Board über die API (z. B. die Stellwerk-CLI), ohne
          Login — gebunden an genau ein Board. Der Klartext wird beim Erzeugen{' '}
          <strong>nur einmal</strong> angezeigt.
        </Typography>
        {projects.length === 0 && (
          <Typography variant="body2" color="text.secondary">
            Du bist in keinem Projekt, in dem du Karten anlegen darfst — daher kannst du kein
            board-gebundenes Token erzeugen.
          </Typography>
        )}

        {created && (
          <Alert
            severity="success"
            action={
              <Button
                color="inherit"
                size="small"
                aria-label="Token kopieren"
                startIcon={<ContentCopyIcon fontSize="small" />}
                onClick={() => void copyPlaintext()}
              >
                {copied ? 'Kopiert' : 'Kopieren'}
              </Button>
            }
          >
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              Token „{created.name}“ erzeugt — wird nur einmal angezeigt:
            </Typography>
            <Box
              component="code"
              aria-label="Token-Klartext"
              sx={{ display: 'block', mt: 0.5, fontFamily: 'monospace', wordBreak: 'break-all' }}
            >
              {created.plaintext}
            </Box>
          </Alert>
        )}

        {tokens.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            Noch keine Tokens.
          </Typography>
        ) : (
          <Stack spacing={0.75}>
            {tokens.map((t) => (
              <Box
                key={t.id}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1.5,
                  border: '1px solid',
                  borderColor: 'divider',
                  borderRadius: 1.5,
                  px: 1.5,
                  py: 1,
                  opacity: t.revoked ? 0.5 : 1,
                }}
              >
                <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                  <Typography variant="body2" sx={{ fontWeight: 500 }}>
                    {t.name}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                    {binding(t)}
                  </Typography>
                </Box>
                <Chip
                  size="small"
                  label={t.revoked ? 'Widerrufen' : 'Aktiv'}
                  color={t.revoked ? 'default' : 'success'}
                  variant={t.revoked ? 'outlined' : 'filled'}
                />
                {!t.revoked && (
                  <Tooltip title="Widerrufen">
                    <IconButton
                      size="small"
                      aria-label={`Token ${t.name} widerrufen`}
                      onClick={() => void revoke(t.id)}
                    >
                      <DeleteOutlineIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
              </Box>
            ))}
          </Stack>
        )}
      </Stack>

      <CreateTokenDialog
        open={dialogOpen}
        projects={projects}
        onClose={() => setDialogOpen(false)}
        onCreated={(res) => {
          setCreated(res)
          setCopied(false)
          setDialogOpen(false)
          reloadTokens()
        }}
      />
    </Paper>
  )
}

/** Anlege-Dialog: Name + Projekt→Board-Kaskade; erzeugt ein board-gebundenes Token. */
function CreateTokenDialog({
  open,
  projects,
  onClose,
  onCreated,
}: Readonly<{
  open: boolean
  projects: Project[]
  onClose: () => void
  onCreated: (created: CreatedAccessToken) => void
}>) {
  const [name, setName] = useState('')
  const [projectId, setProjectId] = useState<number | ''>('')
  const [boards, setBoards] = useState<Board[]>([])
  const [boardId, setBoardId] = useState<number | ''>('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) return
    setName('')
    setProjectId('')
    setBoards([])
    setBoardId('')
    setError(null)
    setSaving(false)
  }, [open])

  useEffect(() => {
    if (projectId === '') {
      setBoards([])
      return
    }
    void boardsApi.list(projectId).then(setBoards)
  }, [projectId])

  const canSubmit = name.trim().length > 0 && projectId !== '' && boardId !== '' && !saving

  const submit = async () => {
    // canSubmit garantiert projectId/boardId als number (TS-Alias-Narrowing).
    if (!canSubmit) return
    setSaving(true)
    setError(null)
    try {
      const res = await accessTokensApi.create(name.trim(), projectId, boardId)
      onCreated(res)
    } catch {
      setError(
        'Token konnte nicht erzeugt werden. Für ein board-gebundenes Token brauchst du das Recht, ' +
          'auf diesem Board Karten anzulegen.',
      )
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Neues API-Token</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField
            label="Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            fullWidth
            autoFocus
            slotProps={{ htmlInput: { maxLength: 120, 'aria-label': 'Name' } }}
          />
          <TextField
            select
            label="Projekt"
            value={projectId === '' ? '' : String(projectId)}
            onChange={(e) => {
              setProjectId(e.target.value === '' ? '' : Number(e.target.value))
              setBoardId('')
            }}
            slotProps={{
              htmlInput: { 'aria-label': 'Projekt' },
              select: { native: true },
              inputLabel: { shrink: true },
            }}
            fullWidth
          >
            <option value="">(Projekt wählen)</option>
            {projects.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </TextField>
          <TextField
            select
            label="Board"
            value={boardId === '' ? '' : String(boardId)}
            onChange={(e) => setBoardId(e.target.value === '' ? '' : Number(e.target.value))}
            disabled={projectId === ''}
            slotProps={{
              htmlInput: { 'aria-label': 'Board' },
              select: { native: true },
              inputLabel: { shrink: true },
            }}
            fullWidth
          >
            <option value="">(Board wählen)</option>
            {boards.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </TextField>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Abbrechen</Button>
        <Button variant="contained" onClick={() => void submit()} disabled={!canSubmit}>
          Erzeugen
        </Button>
      </DialogActions>
    </Dialog>
  )
}
