import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { projectsApi, type Project } from '../api/projects'
import { canManageProject } from '../lib/roles'

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

      {projects.length === 0 ? (
        <Typography color="text.secondary">Noch keine Projekte. Lege oben eines an.</Typography>
      ) : (
        <Stack spacing={1}>
          {projects.map((project) => (
            <Paper
              key={project.id}
              variant="outlined"
              onClick={() => navigate(`/projects/${project.id}`)}
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
              {canManageProject(project.role) && (
                <IconButton
                  size="small"
                  aria-label={`Projekt ${project.name} löschen`}
                  onClick={(e) => {
                    e.stopPropagation()
                    void handleDelete(project.id)
                  }}
                >
                  <DeleteOutlineIcon fontSize="small" />
                </IconButton>
              )}
            </Paper>
          ))}
        </Stack>
      )}
    </Box>
  )
}
