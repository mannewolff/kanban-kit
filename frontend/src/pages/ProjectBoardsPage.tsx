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
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import { useEffect, useState } from 'react'
import { Link as RouterLink, useLocation, useNavigate, useParams } from 'react-router-dom'
import { boardsApi, type Board } from '../api/boards'
import { projectsApi } from '../api/projects'
import { canManageBoards, canManageMembers } from '../lib/roles'

export function ProjectBoardsPage() {
  const { projectId } = useParams()
  const id = Number.parseInt(projectId ?? '', 10)
  const validId = Number.isInteger(id) && id > 0
  const navigate = useNavigate()
  const location = useLocation()
  const [boards, setBoards] = useState<Board[]>([])
  const [role, setRole] = useState<string>('VIEWER')
  const [projectName, setProjectName] = useState<string>('')
  const [name, setName] = useState('')
  const [confirmBoard, setConfirmBoard] = useState<Board | null>(null)

  const reload = () => boardsApi.list(id).then(setBoards)

  useEffect(() => {
    if (!validId) {
      return
    }
    void boardsApi.list(id).then((bs) => {
      setBoards(bs)
      // Bei genau einem Board direkt aufs Board — nur beim Erst-Aufruf oder in der Auto-Routing-Kette.
      const auto = location.key === 'default' || (location.state as { autoRoute?: boolean } | null)?.autoRoute
      if (bs.length === 1 && auto) {
        navigate(`/boards/${bs[0].id}`, { replace: true })
      }
    })
    void projectsApi.list().then((projects) => {
      const project = projects.find((p) => p.id === id)
      if (project) {
        setRole(project.role)
        setProjectName(project.name)
      }
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, validId])

  const handleCreate = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!name.trim()) {
      return
    }
    await boardsApi.create(id, name.trim())
    setName('')
    await reload()
  }

  const handleDeleteBoard = async (boardId: number) => {
    await boardsApi.remove(boardId)
    setConfirmBoard(null)
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
          {projectName || 'Projekt'}
        </Typography>
        {canManageMembers(role) && (
          <Link component={RouterLink} to={`/projects/${id}/members`}>Mitglieder</Link>
        )}
      </Stack>

      {canManageBoards(role) && (
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
              {canManageBoards(role) && (
                <IconButton
                  size="small"
                  aria-label={`Board ${board.name} löschen`}
                  onClick={(e) => {
                    e.stopPropagation()
                    setConfirmBoard(board)
                  }}
                >
                  <DeleteOutlineIcon fontSize="small" />
                </IconButton>
              )}
            </Paper>
          ))}
        </Stack>
      )}

      <Dialog open={confirmBoard !== null} onClose={() => setConfirmBoard(null)}>
        <DialogTitle>Board löschen?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Das Board „{confirmBoard?.name}" und alle zugehörigen Epics und Tickets werden
            unwiderruflich gelöscht.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmBoard(null)}>Abbrechen</Button>
          <Button color="error" onClick={() => { if (confirmBoard) void handleDeleteBoard(confirmBoard.id) }}>
            Löschen
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
