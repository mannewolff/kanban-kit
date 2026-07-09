import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import List from '@mui/material/List'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemText from '@mui/material/ListItemText'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import { boardsApi, type Board } from '../api/boards'
import { projectsApi } from '../api/projects'
import { canManageBoards } from '../lib/roles'

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
      <Typography variant="h5" sx={{ mt: 1, mb: 2 }}>
        {projectName || 'Projekt'}
      </Typography>

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

      <List>
        {boards.map((board) => (
          <ListItemButton key={board.id} onClick={() => navigate(`/boards/${board.id}`)}>
            <ListItemText primary={board.name} secondary={`${board.columns.length} Spalten`} />
          </ListItemButton>
        ))}
        {boards.length === 0 && <Typography color="text.secondary">Noch keine Boards.</Typography>}
      </List>
    </Box>
  )
}
