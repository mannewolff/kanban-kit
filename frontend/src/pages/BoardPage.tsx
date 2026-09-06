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
import { useAuth } from '../auth/AuthContext'
import { BoardView } from '../components/BoardView'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { CardDetailModal } from '../components/CardDetailModal'
import { LabelManagerDialog } from '../components/LabelManagerDialog'
import { TrashDialog } from '../components/TrashDialog'
import { useSnackbar } from '../components/SnackbarProvider'
import { leseAusgeblendet, schreibeAusgeblendet } from '../lib/boardHiddenEpics'
import { activeCardsInColumn, applyMove } from '../lib/boardOps'
import { useEditMode } from '../lib/EditModeContext'
import { epicToCard } from '../lib/epicToCard'
import { selectableEpics } from '../lib/epicTiles'
import { canManageProject, isPlatformAdmin } from '../lib/roles'
import { useBoardEvents } from '../lib/useBoardEvents'
import { useProjectName } from '../lib/useProjectName'
import { useProjectRole } from '../lib/useProjectRole'
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
  // Ausgeblendete Vorhaben (Plan #620, Zustand hier seit Plan #717, A3): Das Board bekommt sie als
  // Prop, das Detail-Modal braucht sie im Folgepaket ebenfalls. Ein zweiter, unabhängiger Leser
  // desselben Schlüssels wären zwei Stände, die auseinanderlaufen können.
  const [hiddenEpics, setHiddenEpics] = useState<ReadonlySet<number>>(() => leseAusgeblendet(id))

  // Die Route `/boards/:boardId` hält die Komponente bei einem reinen Parameterwechsel gemountet —
  // der `useState`-Initializer läuft dann nicht erneut. Bewusst nicht in `load`: Das läuft auch bei
  // Fokus-Refetch und Board-Events und setzte die Ausblendung dann jedes Mal auf den gespeicherten
  // Stand zurück, obwohl sie inzwischen aufgehoben sein kann.
  useEffect(() => setHiddenEpics(leseAusgeblendet(id)), [id])

  // Setzen und Fortschreiben in einem Schritt: Getrennt könnte der eine Aufruf ohne den anderen
  // stehen, und der Bildschirm zeigte etwas anderes als der nächste Seitenaufruf.
  const changeHiddenEpics = (next: ReadonlySet<number>) => {
    setHiddenEpics(next)
    schreibeAusgeblendet(id, next)
  }

  const reloadLabels = () => {
    void labelsApi.list(id).then(setLabels).catch(() => {})
  }

  // Letztes bekanntes Projekt des Boards, um bei einem 404 (Board zwischenzeitlich archiviert/
  // gelöscht) auf dessen Board-Liste zurückzuleiten. Einmal-Guard gegen doppelte Navigation.
  const projectIdRef = useRef<number | null>(null)
  const goneRef = useRef(false)

  // Liefert die frische Liste zurück, damit ein Aufrufer den Serverstand direkt weiterverwenden
  // kann (Konflikt-Nachladen im Statuswechsel). Aufrufer ohne Interesse daran rufen `void`.
  const reloadCards = () => cardsApi.list(id).then((list) => {
    setCards(list)
    return list
  })
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
  // Live-Updates: bei einer Änderung durch andere (Move/Anlegen/…) das Board neu laden.
  useBoardEvents(id, load)

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

  const projectName = useProjectName(board?.projectId ?? null)
  // Plattform-Admin ist synchron aus dem Auth-Context ableitbar (kein Race) und bleibt deshalb hier;
  // die Projektrolle kommt zentral aus useProjectRole.
  const admin = isPlatformAdmin(user)
  const { effectiveRole, canEdit, canModerate } = useProjectRole(projectId ?? null)
  const { editMode } = useEditMode()
  // Board-Verwaltung (Umbenennen, Label-Verwaltung) nur im Editiermodus sichtbar; der Papierkorb-
  // Zugang bleibt als operativer Alltag an canEdit.
  const canEditStructure = canEdit && editMode
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

  /**
   * Statuswechsel aus dem Detail-Modal (Issue #752): schreibt optimistisch und rollt jeden
   * Fehlschlag vollständig zurück. Der Serverstand kommt danach über den SSE-Reload — außer bei
   * einem Konflikt (409): Dort hat jemand anders die Karte bewegt, und der tatsächliche Stand
   * wird sofort nachgeladen, damit das offene Modal nicht die falsche Spalte zeigt.
   *
   * `card` kommt aus dem Render-Zweig mit gesetztem `selectedCard` — deshalb kein Null-Guard.
   */
  const handleMove = async (card: Card, toColumnId: number) => {
    // Gleiche Spalte: nichts zu tun. Das Auswahlfeld bietet die aktuelle Spalte zwar nicht an,
    // ein zwischenzeitlicher Refetch kann sie aber zur aktuellen gemacht haben (offene Rückfrage
    // vor „Ready"), und ein Zug ins Leere wäre eine überflüssige Server-Anfrage.
    if (toColumnId === card.columnId) {
      return
    }
    const previousCards = cards
    const previousSelected = card
    const position = activeCardsInColumn(previousCards, toColumnId).length
    setCards(applyMove(previousCards, card.id, toColumnId))
    setSelectedCard({ ...card, columnId: toColumnId, positionInColumn: position })
    try {
      // Die Antwort wird verworfen (wie BoardView.moveCard) — der Serverstand kommt über SSE.
      await cardsApi.move(card.id, toColumnId, position)
    } catch (e: unknown) {
      setCards(previousCards)
      setSelectedCard(previousSelected)
      if (e instanceof ApiError && e.status === 409) {
        notify(e.message, 'error')
        try {
          const fresh = await reloadCards()
          setSelectedCard(fresh.find((c) => c.id === card.id) ?? null)
        } catch {
          // Auch das Nachladen scheitert: Der zurückgerollte Stand bleibt stehen.
          notify('Neu laden fehlgeschlagen.', 'error')
        }
        return
      }
      notify('Statuswechsel fehlgeschlagen.', 'error')
    }
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

  // Spalte der geöffneten Karte — Status-Chip und Ortspfad im Detail-Modal lesen dieselbe Angabe.
  const selectedColumnName = board.columns.find((c) => c.id === selectedCard?.columnId)?.name

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
        hiddenEpics={hiddenEpics}
        onHiddenEpicsChange={changeHiddenEpics}
        retentionDays={retentionDays}
        members={members}
        boardLabels={labels}
        onCardClick={(card) => { setOpenEditing(false); setSelectedCard(card) }}
        onEditCard={(card) => { setOpenEditing(true); setSelectedCard(card) }}
        // Der Sprung vom Kürzel öffnet das Vorhaben an Ort und Stelle im vorhandenen Detail-Dialog
        // (Plan #682, E4) — eine Deep-Link-Route auf ein einzelnes Vorhaben gibt es nicht.
        onEpicOpen={(epic) => { setOpenEditing(false); setSelectedCard(epicToCard(epic, id)) }}
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
          projectId={board.projectId}
          canModerateComments={canModerate}
          // Volle Liste für Titel und Fortschritt, gefilterter Vorrat für die Auswahl (Plan #717,
          // A2): Ein ausgeblendetes Vorhaben steht nicht mehr zur Wahl, behält aber seinen Namen.
          epics={epics}
          selectableEpics={selectableEpics(epics, hiddenEpics)}
          members={members}
          boardLabels={labels}
          initialEditing={openEditing}
          columnName={selectedColumnName}
          columns={board.columns}
          columnId={selectedCard.columnId}
          onMove={(toColumnId) => handleMove(selectedCard, toColumnId)}
          location={{
            projectId: board.projectId,
            projectName,
            board: { id: board.id, name: board.name, columnName: selectedColumnName },
          }}
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
