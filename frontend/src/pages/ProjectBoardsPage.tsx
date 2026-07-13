import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import ArchiveOutlinedIcon from '@mui/icons-material/ArchiveOutlined'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import { useEffect, useState } from 'react'
import { Link as RouterLink, useLocation, useNavigate, useParams } from 'react-router-dom'
import { boardsApi, type Board } from '../api/boards'
import { projectsApi } from '../api/projects'
import { canManageBoards, canManageMembers } from '../lib/roles'
import { useRefetchOnFocus } from '../lib/useRefetchOnFocus'

export function ProjectBoardsPage() {
  const { projectId } = useParams()
  const id = Number.parseInt(projectId ?? '', 10)
  const validId = Number.isInteger(id) && id > 0
  const navigate = useNavigate()
  const location = useLocation()
  const [boards, setBoards] = useState<Board[]>([])
  const [archived, setArchived] = useState<Board[]>([])
  const [role, setRole] = useState<string>('VIEWER')
  const [projectName, setProjectName] = useState<string>('')
  const [name, setName] = useState('')
  const [confirmBoard, setConfirmBoard] = useState<Board | null>(null)
  const [purgeBoard, setPurgeBoard] = useState<Board | null>(null)
  const [purgeInput, setPurgeInput] = useState('')

  const canManage = canManageBoards(role)

  const reload = () =>
    Promise.all([boardsApi.list(id), boardsApi.listArchived(id)]).then(([bs, arch]) => {
      setBoards(bs)
      setArchived(arch)
    })

  useEffect(() => {
    if (!validId) {
      return
    }
    let active = true
    void boardsApi.list(id).then((bs) => {
      if (!active) return
      setBoards(bs)
      // Bei genau einem Board direkt aufs Board — nur beim Erst-Aufruf oder in der Auto-Routing-Kette.
      const auto = location.key === 'default' || (location.state as { autoRoute?: boolean } | null)?.autoRoute
      if (bs.length === 1 && auto) {
        navigate(`/boards/${bs[0].id}`, { replace: true })
      }
    })
    void boardsApi.listArchived(id).then((arch) => {
      if (active) setArchived(arch)
    })
    void projectsApi.list().then((projects) => {
      if (!active) return
      const project = projects.find((p) => p.id === id)
      if (project) {
        setRole(project.role)
        setProjectName(project.name)
      }
    })
    return () => {
      active = false
    }
    // location/navigate bewusst nicht in den Deps: der Single-Board-Auto-Redirect soll nur
    // bei Projektwechsel (id) greifen, nicht bei jeder Navigation.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, validId])

  // Beim Zurückkehren in den Tab die Board-Liste und die eigene Rolle neu laden (ein Board kann in
  // einer anderen Session hinzugekommen oder entfernt worden sein) — ohne den Single-Board-Redirect.
  // Keine Memoisierung nötig: useRefetchOnFocus hält die jeweils aktuelle Referenz per Ref.
  const refetchOnFocus = () => {
    if (!validId) {
      return
    }
    void boardsApi.list(id).then(setBoards).catch(() => {})
    void boardsApi.listArchived(id).then(setArchived).catch(() => {})
    void projectsApi.list().then((projects) => {
      const project = projects.find((p) => p.id === id)
      if (project) {
        setRole(project.role)
        setProjectName(project.name)
      }
    })
  }
  useRefetchOnFocus(refetchOnFocus)

  const handleCreate = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!name.trim()) {
      return
    }
    await boardsApi.create(id, name.trim())
    setName('')
    await reload()
  }

  const handleArchiveBoard = async (boardId: number) => {
    await boardsApi.remove(boardId)
    setConfirmBoard(null)
    await reload()
  }

  const handleRestoreBoard = async (boardId: number) => {
    await boardsApi.restore(boardId)
    await reload()
  }

  const closePurge = () => {
    setPurgeBoard(null)
    setPurgeInput('')
  }

  const handlePurgeBoard = async (boardId: number) => {
    await boardsApi.purge(boardId)
    closePurge()
    await reload()
  }

  if (!validId) {
    return <Alert severity="error">Ungültige Projekt-ID.</Alert>
  }

  return (
    <Box>
      <Link component={RouterLink} to="/">← Projekte</Link>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mt: 1, mb: 2 }}>
        <Typography variant="h5">
          <Box component="span">{projectName || 'Projekt'}</Box>
          <Box component="span" sx={{ color: 'text.secondary', fontWeight: 400 }}> / Boards</Box>
        </Typography>
        {canManageMembers(role) && (
          <Link component={RouterLink} to={`/projects/${id}/members`}>Mitglieder</Link>
        )}
      </Stack>

      {canManage && (
        <Box component="form" onSubmit={handleCreate} sx={{ mb: 3 }}>
          <Stack direction="row" spacing={1}>
            <TextField size="small" label="Neues Board" value={name} onChange={(e) => setName(e.target.value)} />
            <Button type="submit" variant="contained">
              Anlegen
            </Button>
          </Stack>
        </Box>
      )}

      {boards.length === 0 ? (
        <Typography color="text.secondary">Noch keine Boards.</Typography>
      ) : (
        <Stack spacing={1}>
          {boards.map((board) => (
            <Paper
              key={board.id}
              variant="outlined"
              onClick={() => navigate(`/boards/${board.id}`)}
              sx={{
                px: 2,
                py: 1.5,
                width: '100%',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 1.5,
                transition: 'box-shadow 150ms, border-color 150ms',
                '&:hover': { boxShadow: 3, borderColor: 'primary.main' },
              }}
            >
              <Typography variant="subtitle1" sx={{ fontWeight: 600, flexGrow: 1, minWidth: 0 }}>
                {board.name}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {board.columns.length} Spalten
              </Typography>
              {canManage && (
                <IconButton
                  size="small"
                  aria-label={`Board ${board.name} archivieren`}
                  onClick={(e) => {
                    e.stopPropagation()
                    setConfirmBoard(board)
                  }}
                >
                  <ArchiveOutlinedIcon fontSize="small" />
                </IconButton>
              )}
            </Paper>
          ))}
        </Stack>
      )}

      {canManage && archived.length > 0 && (
        <Box sx={{ mt: 4 }}>
          <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
            Archiv
          </Typography>
          <Stack spacing={1}>
            {archived.map((board) => (
              <Paper
                key={board.id}
                variant="outlined"
                sx={{ px: 2, py: 1.5, display: 'flex', alignItems: 'center', gap: 1.5, opacity: 0.85 }}
              >
                <Typography variant="subtitle1" sx={{ fontWeight: 600, flexGrow: 1, minWidth: 0 }}>
                  {board.name}
                </Typography>
                <Button size="small" onClick={() => void handleRestoreBoard(board.id)}>
                  Wiederherstellen
                </Button>
                <IconButton
                  size="small"
                  color="error"
                  aria-label={`Board ${board.name} endgültig löschen`}
                  onClick={() => setPurgeBoard(board)}
                >
                  <DeleteOutlineIcon fontSize="small" />
                </IconButton>
              </Paper>
            ))}
          </Stack>
        </Box>
      )}

      <Dialog open={confirmBoard !== null} onClose={() => setConfirmBoard(null)}>
        <DialogTitle>Board archivieren?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Das Board „{confirmBoard?.name}&ldquo; wird archiviert. Es verschwindet aus der Liste,
            bleibt aber mit allen Epics und Tickets erhalten und lässt sich im Archiv
            wiederherstellen.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmBoard(null)}>Abbrechen</Button>
          <Button color="error" onClick={() => { if (confirmBoard) void handleArchiveBoard(confirmBoard.id) }}>
            Archivieren
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={purgeBoard !== null} onClose={closePurge}>
        <DialogTitle>Board endgültig löschen?</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: 2 }}>
            Das Board „{purgeBoard?.name}&ldquo; und alle zugehörigen Epics und Tickets werden
            unwiderruflich gelöscht. Gib zur Bestätigung den Board-Namen ein.
          </DialogContentText>
          <TextField
            fullWidth
            size="small"
            label="Board-Name"
            value={purgeInput}
            onChange={(e) => setPurgeInput(e.target.value)}
            inputProps={{ 'aria-label': 'Board-Name zur Bestätigung' }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={closePurge}>Abbrechen</Button>
          <Button
            color="error"
            disabled={purgeInput !== purgeBoard?.name}
            onClick={() => { if (purgeBoard) void handlePurgeBoard(purgeBoard.id) }}
          >
            Endgültig löschen
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
