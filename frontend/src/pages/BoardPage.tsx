import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { boardsApi, type Board } from '../api/boards'
import { cardsApi, type Card } from '../api/cards'
import { projectsApi } from '../api/projects'
import { BoardView } from '../components/BoardView'
import { canEditCards } from '../lib/roles'

export function BoardPage() {
  const { boardId } = useParams()
  const id = Number(boardId)
  const [board, setBoard] = useState<Board | null>(null)
  const [cards, setCards] = useState<Card[]>([])
  const [role, setRole] = useState('VIEWER')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    Promise.all([boardsApi.get(id), cardsApi.list(id)])
      .then(([loadedBoard, loadedCards]) => {
        if (!active) {
          return
        }
        setBoard(loadedBoard)
        setCards(loadedCards)
        setLoading(false)
        void projectsApi.list().then((projects) => {
          const project = projects.find((p) => p.id === loadedBoard.projectId)
          if (project && active) {
            setRole(project.role)
          }
        })
      })
      .catch(() => setLoading(false))
    return () => {
      active = false
    }
  }, [id])

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
        <CircularProgress />
      </Box>
    )
  }

  if (!board) {
    return <Typography color="text.secondary">Board nicht gefunden.</Typography>
  }

  return (
    <Box>
      <Link component={RouterLink} to={`/projects/${board.projectId}`}>← Boards</Link>
      <Typography variant="h5" sx={{ mt: 1, mb: 2 }}>
        {board.name}
      </Typography>
      <BoardView board={board} initialCards={cards} canEdit={canEditCards(role)} />
    </Box>
  )
}
