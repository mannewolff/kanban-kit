import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import { boardsApi, type Board } from '../api/boards'
import { projectsApi } from '../api/projects'
import { canManageBoards, canManageMembers } from '../lib/roles'

export function ProjectBoardsPage() {
  const { projectId } = useParams()
  const id = Number(projectId)
  const navigate = useNavigate()
  const [boards, setBoards] = useState<Board[]>([])
  const [role, setRole] = useState<string>('VIEWER')
  const [projectName, setProjectName] = useState<string>('')
  const [name, setName] = useState('')

  const reload = () => boardsApi.list(id).then(setBoards)

  useEffect(() => {
    void reload()
    void projectsApi.list().then((projects) => {
      const project = projects.find((p) => p.id === id)
      if (project) {
        setRole(project.role)
        setProjectName(project.name)
      }
    })
  }, [id])

  const handleCreate = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!name.trim()) {
      return
    }
    await boardsApi.create(id, name.trim())
    setName('')
    await reload()
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
            </Paper>
          ))}
        </Stack>
      )}
    </Box>
  )
}
