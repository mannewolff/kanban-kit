import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import IconButton from '@mui/material/IconButton'
import List from '@mui/material/List'
import ListItem from '@mui/material/ListItem'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemText from '@mui/material/ListItemText'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { projectsApi, type Project } from '../api/projects'
import { canManageProject } from '../lib/roles'

export function ProjectsPage() {
  const navigate = useNavigate()
  const [projects, setProjects] = useState<Project[]>([])
  const [name, setName] = useState('')

  const reload = () => projectsApi.list().then(setProjects)

  useEffect(() => {
    void reload()
  }, [])

  const handleCreate = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!name.trim()) {
      return
    }
    await projectsApi.create(name.trim())
    setName('')
    await reload()
  }

  const handleDelete = async (id: number) => {
    await projectsApi.remove(id)
    await reload()
  }

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Projekte
      </Typography>

      <Box component="form" onSubmit={handleCreate} sx={{ mb: 3 }}>
        <Stack direction="row" spacing={1}>
          <TextField size="small" label="Neues Projekt" value={name} onChange={(e) => setName(e.target.value)} />
          <Button type="submit" variant="contained">
            Anlegen
          </Button>
        </Stack>
      </Box>

      <List>
        {projects.map((project) => (
          <ListItem
            key={project.id}
            disablePadding
            secondaryAction={
              canManageProject(project.role) ? (
                <IconButton edge="end" aria-label={`Projekt ${project.name} löschen`}
                  onClick={() => handleDelete(project.id)}>
                  ✕
                </IconButton>
              ) : null
            }
          >
            <ListItemButton onClick={() => navigate(`/projects/${project.id}`)}>
              <ListItemText primary={project.name} secondary={`Rolle: ${project.role}`} />
            </ListItemButton>
          </ListItem>
        ))}
        {projects.length === 0 && (
          <Typography color="text.secondary">Noch keine Projekte. Lege oben eines an.</Typography>
        )}
      </List>
    </Box>
  )
}
