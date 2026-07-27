import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import DragIndicatorIcon from '@mui/icons-material/DragIndicator'
import NorthOutlinedIcon from '@mui/icons-material/NorthOutlined'
import SouthOutlinedIcon from '@mui/icons-material/SouthOutlined'
import ViewListIcon from '@mui/icons-material/ViewList'
import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { boardsApi, type Board } from '../api/boards'
import { cardsApi, type Card } from '../api/cards'
import { ideasApi, type Idea } from '../api/ideas'
import { membersApi, type Member } from '../api/members'
import { CardDetailModal } from './CardDetailModal'
import { useSnackbar } from './SnackbarProvider'
import { useRefetchOnFocus } from '../lib/useRefetchOnFocus'

/** Erste Spalte eines Boards (kleinste Position); `null`, wenn das Board keine Spalte hat. */
function firstColumnOf(board: Board): number | null {
  if (board.columns.length === 0) return null
  return [...board.columns].sort((a, b) => a.position - b.position)[0].id
}

/**
 * Quelle eines laufenden Drags: aus dem projektweiten Ideen-Pool oder aus einem konkreten Board
 * (dann trägt der Drag die Herkunfts-Board-ID mit, um Quelle und Ziel unterscheiden zu können).
 */
type DragState =
  | { source: 'pool'; id: number }
  | { source: 'board'; boardId: number; id: number }

// Ab diesem Zeiger-Abstand (px) zum oberen/unteren Fensterrand scrollt die Seite beim Ziehen mit.
const EDGE_PX = 80
const SCROLL_STEP_PX = 16

/**
 * Gestapelte Planungs-Ansicht (Jira-Stil): alle Boards des Projekts untereinander, je Board seine
 * erste Spalte, darunter der projektweite, board-lose Ideen-Pool. Ideen werden per Drag & Drop (oder
 * per Button) auf ein beliebiges Board eingeplant und Board-Karten zurück in den Pool geholt. Das
 * Umsortieren innerhalb einer Board-Spalte ist möglich; eine bereits eingeplante Karte lässt sich
 * per Drag von einem Board auf ein anderes verschieben (Transfer in dessen erste Spalte).
 */
export function IdeaPlanningBoard({
  projectId,
  canEdit,
}: Readonly<{ projectId: number; canEdit: boolean }>) {
  const [boards, setBoards] = useState<Board[]>([])
  // Backlog-Karten je Board-ID (erste Spalte, aktiv, keine Ideen), nach Position sortiert.
  const [cardsByBoard, setCardsByBoard] = useState<Record<number, Card[]>>({})
  const [pool, setPool] = useState<Idea[]>([])
  const [members, setMembers] = useState<Member[]>([])
  // Board-lose Pool-Idee, die im Detail-Modal geöffnet ist (null = geschlossen).
  const [selectedIdea, setSelectedIdea] = useState<Idea | null>(null)
  const [dragged, setDragged] = useState<DragState | null>(null)
  const navigate = useNavigate()
  const notify = useSnackbar()

  useEffect(() => {
    let active = true
    void boardsApi.list(projectId).then((bs) => {
      if (!active) return
      setBoards(bs)
    })
    return () => {
      active = false
    }
  }, [projectId])

  const loadBacklogs = useCallback(async () => {
    const entries = await Promise.all(
      boards.map(async (board) => {
        const columnId = firstColumnOf(board)
        if (columnId === null) return [board.id, [] as Card[]] as const
        const cs = await cardsApi.list(board.id)
        return [
          board.id,
          cs
            .filter((c) => c.columnId === columnId && !c.archived && !c.ideaStored)
            .sort((a, b) => a.positionInColumn - b.positionInColumn),
        ] as const
      }),
    )
    setCardsByBoard(Object.fromEntries(entries))
  }, [boards])

  const loadPool = useCallback(
    () => ideasApi.list(projectId).then((is) => setPool(is.filter((i) => i.boardId === null))),
    [projectId],
  )

  useEffect(() => {
    void loadBacklogs()
  }, [loadBacklogs])
  useEffect(() => {
    void loadPool()
  }, [loadPool])

  // Projektmitglieder für die Zuständigen-Auswahl im Detail-Modal (Namen + Autocomplete-Optionen).
  useEffect(() => {
    void membersApi.list(projectId).then(setMembers).catch(() => setMembers([]))
  }, [projectId])

  useRefetchOnFocus(() => {
    void loadBacklogs().catch(() => {})
    void loadPool().catch(() => {})
  })

  // Auto-Scroll: nähert sich der Zeiger während eines Drags dem oberen/unteren Fensterrand, scrollt
  // die Seite mit. HTML5-Drag scrollt von sich aus nicht — ohne das ist die gestapelte Ansicht bei
  // vielen Boards unbenutzbar (Pool unten, Zielboard womöglich weit oben).
  useEffect(() => {
    if (dragged === null) return
    const onDragOver = (e: DragEvent) => {
      if (e.clientY < EDGE_PX) window.scrollBy(0, -SCROLL_STEP_PX)
      else if (e.clientY > window.innerHeight - EDGE_PX) window.scrollBy(0, SCROLL_STEP_PX)
    }
    window.addEventListener('dragover', onDragOver)
    return () => window.removeEventListener('dragover', onDragOver)
  }, [dragged])

  const reload = useCallback(
    () => Promise.all([loadBacklogs(), loadPool()]),
    [loadBacklogs, loadPool],
  )

  const plan = useCallback(
    async (cardId: number, boardId: number) => {
      await ideasApi.planOntoBoard(cardId, boardId)
      await reload()
    },
    [reload],
  )

  const toPool = useCallback(
    async (cardId: number) => {
      await ideasApi.moveBackToPool(cardId)
      await reload()
    },
    [reload],
  )

  // Eine bereits eingeplante Karte von einem Board auf ein anderes verschieben: in die erste Spalte
  // des Zielboards. Ohne optimistisches Entfernen — erst der Reload nach Erfolg ändert die Ansicht,
  // scheitert der Transfer, bleibt sie unverändert und der Nutzer bekommt eine verständliche Meldung.
  const transfer = useCallback(
    async (cardId: number, target: Board) => {
      const targetColumnId = firstColumnOf(target)
      if (targetColumnId === null) return
      try {
        await cardsApi.transfer(cardId, target.id, targetColumnId)
        await reload()
      } catch {
        notify('Verschieben auf das andere Board fehlgeschlagen.', 'error')
      }
    },
    [reload, notify],
  )

  const startPoolDrag = (id: number) => (e: React.DragEvent) => {
    e.dataTransfer.setData('text/plain', String(id))
    setDragged({ source: 'pool', id })
  }

  const startBoardDrag = (boardId: number, id: number) => (e: React.DragEvent) => {
    e.dataTransfer.setData('text/plain', String(id))
    setDragged({ source: 'board', boardId, id })
  }

  // Drop auf die Zone eines Boards: aus dem Pool → auf dieses Board einplanen; von einem anderen
  // Board → dorthin verschieben (Transfer). Ein Drop auf das Herkunfts-Board tut nichts.
  const handleBoardDrop = (board: Board) => (e: React.DragEvent) => {
    e.preventDefault()
    const d = dragged
    setDragged(null)
    if (d === null) return
    if (d.source === 'pool') void plan(d.id, board.id)
    else if (d.boardId !== board.id) void transfer(d.id, board)
  }

  // Drop auf die Pool-Zone: eine Board-Karte zurück in den board-losen Pool holen.
  const handlePoolDrop = (e: React.DragEvent) => {
    e.preventDefault()
    const d = dragged
    setDragged(null)
    if (d === null) return
    if (d.source === 'board') void toPool(d.id)
  }

  const reorder = async (cardId: number, columnId: number, position: number) => {
    await cardsApi.move(cardId, columnId, position)
    await loadBacklogs()
  }

  // Drop einer Board-Karte auf eine andere Zeile DESSELBEN Boards: an deren Position einsortieren.
  // Andere Fälle (Pool-Quelle, fremdes Board, Drop auf sich selbst, kein Drag) durchreichen — den
  // Pool→Board-Drop verarbeitet dann die Zonen-Ebene (handleBoardDrop).
  const handleBoardRowDrop = (boardId: number, target: Card) => (e: React.DragEvent) => {
    const d = dragged
    if (d === null || d.source !== 'board' || d.boardId !== boardId || d.id === target.id) return
    e.preventDefault()
    e.stopPropagation()
    setDragged(null)
    void reorder(d.id, target.columnId, target.positionInColumn)
  }

  if (boards.length === 0) {
    return (
      <Alert severity="info">
        Dieses Projekt hat noch kein Board. Lege zuerst ein Board an, um Ideen einplanen zu können.
      </Alert>
    )
  }

  return (
    <Box>
      {/* Alle Boards des Projekts untereinander, je Board seine erste Spalte (Ziel beim Einplanen). */}
      {boards.map((board) => {
        const cards = cardsByBoard[board.id] ?? []
        return (
          <Box key={board.id} sx={{ mb: 3 }}>
            <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
              <Typography
                variant="subtitle2"
                sx={{ fontWeight: 700, textTransform: 'uppercase', letterSpacing: '.03em', color: 'text.secondary', flex: 1, minWidth: 0 }}
              >
                {board.name}
              </Typography>
              {/* Direkt in die Listenansicht dieses Boards springen. */}
              <Button
                size="small"
                variant="outlined"
                startIcon={<ViewListIcon />}
                aria-label={`Board ${board.name} öffnen`}
                onClick={() => navigate(`/boards/${board.id}/list`)}
              >
                Board öffnen
              </Button>
            </Stack>

            <Box
              data-testid={`board-zone-${board.id}`}
              onDragOver={(e) => e.preventDefault()}
              onDrop={handleBoardDrop(board)}
              sx={{ minHeight: 64, borderRadius: 1.5, p: 1, bgcolor: 'background.default' }}
            >
              {cards.length === 0 ? (
                <Typography variant="body2" color="text.secondary" sx={{ py: 1 }}>
                  Kein Backlog — zieh eine Idee herauf.
                </Typography>
              ) : (
                <Stack spacing={0.75}>
                  {cards.map((cardItem) => (
                    <Paper
                      key={cardItem.id}
                      data-testid={`board-item-${cardItem.id}`}
                      variant="outlined"
                      draggable={canEdit}
                      onDragStart={startBoardDrag(board.id, cardItem.id)}
                      onDragOver={(e) => e.preventDefault()}
                      onDrop={handleBoardRowDrop(board.id, cardItem)}
                      sx={{ px: 1.5, py: 1, display: 'flex', alignItems: 'center', gap: 1.5, cursor: canEdit ? 'grab' : 'default' }}
                    >
                      {canEdit && (
                        <DragIndicatorIcon
                          fontSize="small"
                          aria-label="Ziehen"
                          sx={{ flexShrink: 0, color: 'action.disabled' }}
                        />
                      )}
                      <Typography variant="caption" color="text.secondary" sx={{ width: 48, flexShrink: 0 }}>
                        #{cardItem.number}
                      </Typography>
                      <Typography variant="body2" noWrap sx={{ flex: 1, minWidth: 0, fontWeight: 500 }}>
                        {cardItem.title}
                      </Typography>
                      {canEdit && (
                        <Button
                          size="small"
                          startIcon={<SouthOutlinedIcon />}
                          aria-label={`Karte ${cardItem.title} in den Pool`}
                          onClick={() => void toPool(cardItem.id)}
                        >
                          In den Pool
                        </Button>
                      )}
                    </Paper>
                  ))}
                </Stack>
              )}
            </Box>
          </Box>
        )
      })}

      <Box sx={{ borderTop: '2px dashed', borderColor: 'divider', my: 2 }} />

      {/* Projektweiter, board-loser Ideen-Pool (Quelle beim Einplanen). */}
      <Box
        data-testid="pool-zone"
        onDragOver={(e) => e.preventDefault()}
        onDrop={handlePoolDrop}
        sx={{ minHeight: 80, borderRadius: 1.5, p: 1 }}
      >
        <Typography
          variant="subtitle2"
          sx={{ fontWeight: 700, textTransform: 'uppercase', letterSpacing: '.03em', color: 'text.secondary', mb: 1 }}
        >
          Ideen-Pool
        </Typography>
        {pool.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 1 }}>
            Keine Ideen im Pool.
          </Typography>
        ) : (
          <Stack spacing={0.75}>
            {pool.map((idea) => (
              <Paper
                key={idea.id}
                data-testid={`pool-item-${idea.id}`}
                variant="outlined"
                draggable={canEdit}
                onDragStart={startPoolDrag(idea.id)}
                sx={{ px: 1.5, py: 1, display: 'flex', alignItems: 'center', gap: 1.5, bgcolor: 'action.hover', cursor: canEdit ? 'grab' : 'default' }}
              >
                {canEdit && (
                  <DragIndicatorIcon
                    fontSize="small"
                    aria-label="Ziehen"
                    sx={{ flexShrink: 0, color: 'action.disabled' }}
                  />
                )}
                {/* #402: neue Pool-Ideen tragen eine projektweite Nummer; Legacy-Ideen ohne Nummer
                    zeigen kein nacktes „#". */}
                {idea.number != null && (
                  <Typography variant="caption" color="text.secondary" sx={{ width: 48, flexShrink: 0 }}>
                    #{idea.number}
                  </Typography>
                )}
                {/* Dedizierte Öffnen-Affordanz: Klick auf den Titel öffnet das Detail-Modal. Der Drag
                    bleibt an der Zeile — ein Klick startet keinen Drag, kein Konflikt. */}
                <Link
                  component="button"
                  type="button"
                  variant="body2"
                  underline="hover"
                  color="text.primary"
                  onClick={() => setSelectedIdea(idea)}
                  sx={{ flex: 1, minWidth: 0, fontWeight: 500, textAlign: 'left', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', cursor: 'pointer' }}
                >
                  {idea.title}
                </Link>
                {canEdit && (
                  <Button
                    size="small"
                    startIcon={<NorthOutlinedIcon />}
                    aria-label={`Idee ${idea.title} einplanen`}
                    onClick={() => void plan(idea.id, boards[0].id)}
                  >
                    Einplanen
                  </Button>
                )}
              </Paper>
            ))}
          </Stack>
        )}
      </Box>

      {/* Board-lose Pool-Idee im vollen Detail-Modal öffnen. Board-spezifische Teile bleiben leer
          (kein columnName/Board-Labels/Epics) — das Modal toleriert das. */}
      {selectedIdea && (
        <CardDetailModal
          card={selectedIdea}
          canEdit={canEdit}
          members={members}
          epics={[]}
          boardLabels={[]}
          onClose={() => setSelectedIdea(null)}
          onChanged={() => void reload()}
        />
      )}
    </Box>
  )
}
