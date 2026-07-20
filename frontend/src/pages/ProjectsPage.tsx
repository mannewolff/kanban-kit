import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { projectsApi, type Project } from '../api/projects'
import { canManageProject, isPlatformAdmin } from '../lib/roles'
import { useAuth } from '../auth/AuthContext'
import { useEditMode } from '../lib/EditModeContext'

const ROLE_CHIP: Record<string, 'primary' | 'info' | 'default'> = {
  OWNER: 'primary',
  ADMIN: 'info',
}

function formatDate(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleDateString('de-DE')
}

export function ProjectsPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { user } = useAuth()
  const { editMode } = useEditMode()
  const admin = isPlatformAdmin(user)
  const [projects, setProjects] = useState<Project[]>([])
  const [name, setName] = useState('')
  const [ownerEmail, setOwnerEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<Project | null>(null)
  const [renameProject, setRenameProject] = useState<Project | null>(null)
  const [renameValue, setRenameValue] = useState('')
  const [renameError, setRenameError] = useState<string | null>(null)

  const reload = () => projectsApi.list().then(setProjects)

  const openRename = (project: Project) => {
    setRenameProject(project)
    setRenameValue(project.name)
    setRenameError(null)
  }
  const closeRename = () => {
    setRenameProject(null)
    setRenameError(null)
  }
  // Kein Nullable-Guard nötig: der Speichern-Button existiert nur im Dialog, der ausschließlich
  // bei gesetztem renameProject gemountet ist; bei leerem renameValue ist er zusätzlich disabled.
  const handleRename = async (project: Project) => {
    try {
      await projectsApi.rename(project.id, renameValue.trim())
      closeRename()
      await reload()
    } catch {
      setRenameError('Umbenennen fehlgeschlagen.')
    }
  }

  useEffect(() => {
    void projectsApi.list().then((ps) => {
      setProjects(ps)
      // Bei genau einem Projekt direkt zur Boardauswahl — aber nur beim Erst-Aufruf (key 'default')
      // oder in der Auto-Routing-Kette. Bewusste Navigation (Sidebar/Zurück = Push) zeigt die Liste.
      const auto = location.key === 'default' || (location.state as { autoRoute?: boolean } | null)?.autoRoute
      if (ps.length === 1 && auto) {
        navigate(`/projects/${ps[0].id}`, { replace: true, state: { autoRoute: true } })
      }
    })
    // Nur beim Mount laden: location/navigate bewusst aus den Deps ausgelassen, damit der
    // Single-Projekt-Auto-Redirect nicht bei jeder Navigation neu feuert.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleCreate = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!name.trim() || !ownerEmail.trim()) {
      return
    }
    setError(null)
    try {
      await projectsApi.create(name.trim(), ownerEmail.trim())
      setName('')
      setOwnerEmail('')
      await reload()
    } catch (e) {
      if (e instanceof ApiError && e.status === 400) {
        setError('Kein Nutzer mit dieser Owner-E-Mail gefunden.')
      } else if (e instanceof ApiError && e.status === 403) {
        setError('Nur Administratoren dürfen Projekte anlegen.')
      } else {
        setError('Anlegen fehlgeschlagen.')
      }
    }
  }

  const handleDelete = async (id: number) => {
    await projectsApi.remove(id)
    setConfirmDelete(null)
    await reload()
  }

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Projekte
      </Typography>

      {admin && (
        <Box component="form" onSubmit={handleCreate} sx={{ mb: 3 }}>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            <TextField size="small" label="Neues Projekt" value={name} onChange={(e) => setName(e.target.value)} />
            <TextField size="small" type="email" label="Owner (E-Mail)" value={ownerEmail}
              onChange={(e) => setOwnerEmail(e.target.value)} />
            <Button type="submit" variant="contained">
              Anlegen
            </Button>
          </Stack>
          {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
        </Box>
      )}

      {projects.length === 0 ? (
        <Typography color="text.secondary">
          {admin
            ? 'Noch keine Projekte. Lege oben eines an.'
            : 'Noch keine Projekte. Ein Administrator legt Projekte an.'}
        </Typography>
      ) : (
        <Stack spacing={1}>
          {projects.map((project) => (
            <Paper
              key={project.id}
              variant="outlined"
              // autoRoute mitgeben, damit die Board-Ebene bei genau einem Board direkt durchroutet —
              // auch wenn der Nutzer mehrere Projekte hat und das Projekt manuell anwählt.
              onClick={() => navigate(`/projects/${project.id}`, { state: { autoRoute: true } })}
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
                {project.name}
              </Typography>
              <Chip label={project.role} size="small" color={ROLE_CHIP[project.role] ?? 'default'} />
              {formatDate(project.createdAt) && (
                <Typography variant="caption" color="text.secondary" sx={{ minWidth: 72, textAlign: 'right' }}>
                  {formatDate(project.createdAt)}
                </Typography>
              )}
              {canManageProject(project.role, admin) && editMode && (
                <IconButton
                  size="small"
                  aria-label={`Projekt ${project.name} umbenennen`}
                  onClick={(e) => {
                    e.stopPropagation()
                    openRename(project)
                  }}
                >
                  <EditOutlinedIcon fontSize="small" />
                </IconButton>
              )}
              {admin && editMode && (
                <IconButton
                  size="small"
                  aria-label={`Projekt ${project.name} löschen`}
                  onClick={(e) => {
                    e.stopPropagation()
                    setConfirmDelete(project)
                  }}
                >
                  <DeleteOutlineIcon fontSize="small" />
                </IconButton>
              )}
            </Paper>
          ))}
        </Stack>
      )}

      <Dialog open={confirmDelete !== null} onClose={() => setConfirmDelete(null)}>
        <DialogTitle>Projekt löschen?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Das Projekt „{confirmDelete?.name}&ldquo; und alle zugehörigen Boards, Epics und Tickets werden
            unwiderruflich gelöscht.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmDelete(null)}>Abbrechen</Button>
          <Button color="error" onClick={() => { if (confirmDelete) void handleDelete(confirmDelete.id) }}>
            Löschen
          </Button>
        </DialogActions>
      </Dialog>

      {renameProject && (
        <Dialog open onClose={closeRename}>
          <DialogTitle>Projekt umbenennen</DialogTitle>
          <DialogContent>
            {renameError && <Alert severity="error" sx={{ mb: 2 }}>{renameError}</Alert>}
            <TextField
              autoFocus
              fullWidth
              label="Projektname"
              value={renameValue}
              onChange={(e) => setRenameValue(e.target.value)}
              sx={{ mt: 1 }}
              slotProps={{ htmlInput: { 'aria-label': 'Neuer Projektname' } }}
            />
          </DialogContent>
          <DialogActions>
            <Button onClick={closeRename}>Abbrechen</Button>
            <Button
              variant="contained"
              disabled={!renameValue.trim()}
              onClick={() => void handleRename(renameProject)}
            >
              Speichern
            </Button>
          </DialogActions>
        </Dialog>
      )}
    </Box>
  )
}
