import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { boardsApi, type Board } from '../api/boards'
import { cardsApi, type Card } from '../api/cards'
import { configApi } from '../api/config'
import { epicsApi, type Epic } from '../api/epics'
import { projectsApi } from '../api/projects'
import { useAuth } from '../auth/AuthContext'
import { BoardView } from '../components/BoardView'
import { CardDetailModal } from '../components/CardDetailModal'
import { useSnackbar } from '../components/SnackbarProvider'
import { canEditCards, canManageProject, canModerateComments, isPlatformAdmin } from '../lib/roles'
import { useProjectName } from '../lib/useProjectName'
import { useRefetchOnFocus } from '../lib/useRefetchOnFocus'

export function BoardPage() {
  const { boardId } = useParams()
  const id = Number.parseInt(boardId ?? '', 10)
  const validId = Number.isInteger(id) && id > 0
  const { user } = useAuth()
  const navigate = useNavigate()
  const notify = useSnackbar()
  const [board, setBoard] = useState<Board | null>(null)
  const [cards, setCards] = useState<Card[]>([])
  const [epics, setEpics] = useState<Epic[]>([])
  const [fetchedRole, setFetchedRole] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [selectedCard, setSelectedCard] = useState<Card | null>(null)
  const [openEditing, setOpenEditing] = useState(false)
  const [retentionDays, setRetentionDays] = useState(30)

  // Letztes bekanntes Projekt des Boards, um bei einem 404 (Board zwischenzeitlich archiviert/
  // gelöscht) auf dessen Board-Liste zurückzuleiten. Einmal-Guard gegen doppelte Navigation.
  const projectIdRef = useRef<number | null>(null)
  const goneRef = useRef(false)

  const reloadCards = () => {
    void cardsApi.list(id).then(setCards)
  }
  const reloadEpics = () => {
    void epicsApi.list(id).then(setEpics)
  }

  const handleBoardGone = useCallback(() => {
    if (goneRef.current) {
      return
    }
    goneRef.current = true
    notify('Dieses Board wurde archiviert oder gelöscht.', 'warning')
    const projectId = projectIdRef.current
    navigate(projectId ? `/projects/${projectId}` : '/', { replace: true })
  }, [navigate, notify])

  const load = useCallback(() => {
    if (!validId || goneRef.current) {
      return
    }
    void Promise.all([boardsApi.get(id), cardsApi.list(id), epicsApi.list(id)])
      .then(([loadedBoard, loadedCards, loadedEpics]) => {
        projectIdRef.current = loadedBoard.projectId
        setBoard(loadedBoard)
        setCards(loadedCards)
        setEpics(loadedEpics)
        setLoading(false)
      })
      .catch((e: unknown) => {
        setLoading(false)
        if (e instanceof ApiError && e.status === 404) {
          handleBoardGone()
        }
      })
  }, [id, validId, handleBoardGone])

  useEffect(() => {
    goneRef.current = false
    setLoading(true)
    load()
  }, [load])

  // Kommt der Tab wieder in den Vordergrund, den Board-Zustand neu prüfen: Wurde das Board in einer
  // anderen Session entfernt, greift dabei das 404-Handling (Redirect + Hinweis).
  useRefetchOnFocus(load)

  useEffect(() => {
    void configApi.get().then((c) => setRetentionDays(c.doneRetentionDays)).catch(() => {})
  }, [])

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

  const projectName = useProjectName(board?.projectId ?? null)
  const admin = isPlatformAdmin(user)
  const effectiveRole = membershipRole ?? fetchedRole ?? 'VIEWER'
  const canEdit = canEditCards(effectiveRole, admin)
  const canModerate = canModerateComments(effectiveRole, admin)
  const canTransfer = canManageProject(effectiveRole, admin)

  if (!validId) {
    return <Alert severity="error">Ungültige Board-ID.</Alert>
  }

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
        {projectName && (
          <Box component="span" sx={{ color: 'text.secondary', fontWeight: 400 }}>
            {projectName} /{' '}
          </Box>
        )}
        <Box component="span">{board.name}</Box>
      </Typography>
      <BoardView
        board={board}
        initialCards={cards}
        canEdit={canEdit}
        epics={epics}
        retentionDays={retentionDays}
        onCardClick={(card) => { setOpenEditing(false); setSelectedCard(card) }}
        onEditCard={(card) => { setOpenEditing(true); setSelectedCard(card) }}
        onEpicsChanged={reloadEpics}
        onCardsChanged={reloadCards}
        canTransfer={canTransfer}
        platformAdmin={admin}
      />
      {selectedCard && (
        <CardDetailModal
          key={selectedCard.id}
          card={selectedCard}
          canEdit={canEdit}
          canModerateComments={canModerate}
          epics={epics}
          initialEditing={openEditing}
          columnName={board.columns.find((c) => c.id === selectedCard.columnId)?.name}
          onClose={() => setSelectedCard(null)}
          onChanged={() => {
            reloadCards()
            reloadEpics()
          }}
        />
      )}
    </Box>
  )
}
