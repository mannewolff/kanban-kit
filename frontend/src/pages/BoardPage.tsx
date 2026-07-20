import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { boardsApi, type Board } from '../api/boards'
import { cardsApi, type Card } from '../api/cards'
import { configApi } from '../api/config'
import { epicsApi, type Epic } from '../api/epics'
import { labelsApi, type Label } from '../api/labels'
import { membersApi, type Member } from '../api/members'
import { projectsApi } from '../api/projects'
import { useAuth } from '../auth/AuthContext'
import { BoardView } from '../components/BoardView'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { CardDetailModal } from '../components/CardDetailModal'
import { LabelManagerDialog } from '../components/LabelManagerDialog'
import { TrashDialog } from '../components/TrashDialog'
import { useSnackbar } from '../components/SnackbarProvider'
import { useEditMode } from '../lib/EditModeContext'
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
  const [renameOpen, setRenameOpen] = useState(false)
  const [renameValue, setRenameValue] = useState('')
  const [members, setMembers] = useState<Member[]>([])
  const [labels, setLabels] = useState<Label[]>([])
  const [labelManagerOpen, setLabelManagerOpen] = useState(false)
  const [trashOpen, setTrashOpen] = useState(false)

  const reloadLabels = () => {
    void labelsApi.list(id).then(setLabels).catch(() => {})
  }

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

  // Projektmitglieder für Zuständigen-Auswahl/-Avatare laden, sobald das Projekt bekannt ist.
  const projectId = board?.projectId
  useEffect(() => {
    if (projectId == null) {
      return
    }
    void membersApi.list(projectId).then(setMembers).catch(() => setMembers([]))
  }, [projectId])

  useEffect(() => {
    if (!validId) {
      return
    }
    void labelsApi.list(id).then(setLabels).catch(() => setLabels([]))
  }, [id, validId])

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
  const { editMode } = useEditMode()
  const canEdit = canEditCards(effectiveRole, admin)
  // Board-Verwaltung (Umbenennen, Label-Verwaltung) nur im Editiermodus sichtbar; der Papierkorb-
  // Zugang bleibt als operativer Alltag an canEdit.
  const canEditStructure = canEdit && editMode
  const canModerate = canModerateComments(effectiveRole, admin)
  const canTransfer = canManageProject(effectiveRole, admin)

  const openRename = () => {
    if (board) {
      setRenameValue(board.name)
      setRenameOpen(true)
    }
  }
  // Kein Nullable-Guard nötig: der einzige Aufrufer ist der Speichern-Button im Umbenennen-Dialog,
  // der nur bei gesetztem board sichtbar ist (siehe die frühen Returns oben) und bei leerem
  // renameValue bereits disabled ist.
  const handleRename = async (currentBoard: Board) => {
    const updated = await boardsApi.rename(currentBoard.id, renameValue.trim())
    setBoard(updated)
    setRenameOpen(false)
  }

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
      <Stack direction="row" alignItems="center" spacing={0.5} sx={{ mb: 2 }}>
        <Breadcrumbs
          items={[
            { label: 'Projekte', to: '/' },
            ...(projectName ? [{ label: projectName, to: `/projects/${board.projectId}` }] : []),
            { label: board.name },
          ]}
        />
        {canEditStructure && (
          <IconButton size="small" aria-label="Board umbenennen" onClick={openRename}>
            <EditOutlinedIcon fontSize="small" />
          </IconButton>
        )}
        {canEditStructure && (
          <Button size="small" onClick={() => setLabelManagerOpen(true)}>
            Labels
          </Button>
        )}
        {canEdit && (
          <Button size="small" onClick={() => setTrashOpen(true)}>
            Papierkorb
          </Button>
        )}
      </Stack>
      <BoardView
        board={board}
        initialCards={cards}
        canEdit={canEdit}
        epics={epics}
        retentionDays={retentionDays}
        members={members}
        boardLabels={labels}
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
          members={members}
          boardLabels={labels}
          initialEditing={openEditing}
          columnName={board.columns.find((c) => c.id === selectedCard.columnId)?.name}
          onClose={() => setSelectedCard(null)}
          onChanged={() => {
            reloadCards()
            reloadEpics()
          }}
        />
      )}

      <LabelManagerDialog
        open={labelManagerOpen}
        boardId={id}
        labels={labels}
        onClose={() => setLabelManagerOpen(false)}
        onChanged={() => {
          reloadLabels()
          reloadCards()
        }}
      />

      <TrashDialog
        open={trashOpen}
        boardId={id}
        canPurge={admin || effectiveRole === 'OWNER' || effectiveRole === 'ADMIN'}
        onClose={() => setTrashOpen(false)}
        onChanged={reloadCards}
      />

      <Dialog open={renameOpen} onClose={() => setRenameOpen(false)}>
        <DialogTitle>Board umbenennen</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            label="Board-Name"
            value={renameValue}
            onChange={(e) => setRenameValue(e.target.value)}
            sx={{ mt: 1 }}
            slotProps={{ htmlInput: { 'aria-label': 'Neuer Board-Name' } }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRenameOpen(false)}>Abbrechen</Button>
          <Button variant="contained" disabled={!renameValue.trim()} onClick={() => void handleRename(board)}>
            Speichern
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
