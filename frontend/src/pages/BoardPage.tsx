import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { boardsApi, type Board } from '../api/boards'
import { cardsApi, type Card } from '../api/cards'
import { projectsApi } from '../api/projects'
import { useAuth } from '../auth/AuthContext'
import { BoardView } from '../components/BoardView'
import { CardDetailModal } from '../components/CardDetailModal'
import { canEditCards } from '../lib/roles'

export function BoardPage() {
  const { boardId } = useParams()
  const id = Number(boardId)
  const { user } = useAuth()
  const [board, setBoard] = useState<Board | null>(null)
  const [cards, setCards] = useState<Card[]>([])
  const [fetchedRole, setFetchedRole] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [selectedCard, setSelectedCard] = useState<Card | null>(null)

  const reloadCards = () => {
    void cardsApi.list(id).then(setCards)
  }

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
      })
      .catch(() => setLoading(false))
    return () => {
      active = false
    }
  }, [id])

  // Rolle bevorzugt synchron aus den Memberships (kein Race). Ist das Projekt dort noch nicht
  // bekannt (z. B. frisch in dieser Session angelegt), einmal frisch nachladen.
  const membershipRole = board
    ? user?.memberships.find((m) => m.projectId === board.projectId)?.role
    : undefined

  useEffect(() => {
    if (!board || membershipRole) {
      setFetchedRole(null)
      return
    }
    let active = true
    projectsApi
      .list()
      .then((projects) => {
        if (active) {
          setFetchedRole(projects.find((p) => p.id === board.projectId)?.role ?? 'VIEWER')
        }
      })
      .catch(() => active && setFetchedRole('VIEWER'))
    return () => {
      active = false
    }
  }, [board, membershipRole])

  const canEdit = canEditCards(membershipRole ?? fetchedRole ?? 'VIEWER')

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
      <BoardView board={board} initialCards={cards} canEdit={canEdit} onCardClick={setSelectedCard} />
      {selectedCard && (
        <CardDetailModal
          card={selectedCard}
          canEdit={canEdit}
          onClose={() => setSelectedCard(null)}
          onChanged={reloadCards}
        />
      )}
    </Box>
  )
}
