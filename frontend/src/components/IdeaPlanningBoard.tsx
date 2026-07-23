import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import NorthOutlinedIcon from '@mui/icons-material/NorthOutlined'
import SouthOutlinedIcon from '@mui/icons-material/SouthOutlined'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { boardsApi, type Board } from '../api/boards'
import { cardsApi, type Card } from '../api/cards'
import { ideasApi, type Idea } from '../api/ideas'
import { useRefetchOnFocus } from '../lib/useRefetchOnFocus'

/** Merkt das zuletzt im Planungs-Board gewählte Board je Projekt. */
function boardStorageKey(projectId: number): string {
  return `manban.ideaBoard.${projectId}`
}

function readSelectedBoard(projectId: number): number | null {
  try {
    const raw = localStorage.getItem(boardStorageKey(projectId))
    return raw ? Number(raw) : null
  } catch {
    return null
  }
}

function writeSelectedBoard(projectId: number, boardId: number): void {
  try {
    localStorage.setItem(boardStorageKey(projectId), String(boardId))
  } catch {
    // localStorage nicht verfügbar — kein Hard-Fail
  }
}

/** Quelle eines laufenden Drags: aus dem Pool oder aus dem Backlog. */
type DragSource = 'pool' | 'backlog'

/**
 * Gestapeltes Sprint-Planning-Board (Jira-Stil): oben das Backlog (erste Spalte) des gewählten
 * Boards, unten der projektweite, board-lose Ideen-Pool. Ideen werden per Drag & Drop (oder per
 * Button) hoch ins Backlog eingeplant und Backlog-Karten runter in den Pool geholt. Das Umsortieren
 * innerhalb des Backlogs ist bewusst nicht Teil dieser Komponente (Folge-Issue).
 */
export function IdeaPlanningBoard({
  projectId,
  canEdit,
}: Readonly<{ projectId: number; canEdit: boolean }>) {
  const [boards, setBoards] = useState<Board[]>([])
  // 0 = noch kein Board gewählt/geladen; sobald Boards da sind, steht hier eine echte Board-ID.
  const [selectedBoardId, setSelectedBoardId] = useState<number>(0)
  const [backlog, setBacklog] = useState<Card[]>([])
  const [pool, setPool] = useState<Idea[]>([])
  const [dragged, setDragged] = useState<{ source: DragSource; id: number } | null>(null)

  useEffect(() => {
    let active = true
    void boardsApi.list(projectId).then((bs) => {
      if (!active) return
      setBoards(bs)
      const stored = readSelectedBoard(projectId)
      const initial = stored !== null && bs.some((b) => b.id === stored) ? stored : (bs[0]?.id ?? 0)
      setSelectedBoardId(initial)
    })
    return () => {
      active = false
    }
  }, [projectId])

  const firstColumnId = useMemo(() => {
    const cols = boards.find((b) => b.id === selectedBoardId)?.columns ?? []
    if (cols.length === 0) return null
    return [...cols].sort((a, b) => a.position - b.position)[0].id
  }, [boards, selectedBoardId])

  const loadBacklog = useCallback(() => {
    if (firstColumnId === null) {
      setBacklog([])
      return Promise.resolve()
    }
    const columnId = firstColumnId
    return cardsApi.list(selectedBoardId).then((cs) => {
      setBacklog(
        cs
          .filter((c) => c.columnId === columnId && !c.archived && !c.ideaStored)
          .sort((a, b) => a.positionInColumn - b.positionInColumn),
      )
    })
  }, [selectedBoardId, firstColumnId])

  const loadPool = useCallback(
    () => ideasApi.list(projectId).then((is) => setPool(is.filter((i) => i.boardId === null))),
    [projectId],
  )

  useEffect(() => {
    void loadBacklog()
  }, [loadBacklog])
  useEffect(() => {
    void loadPool()
  }, [loadPool])

  useRefetchOnFocus(() => {
    void loadBacklog().catch(() => {})
    void loadPool().catch(() => {})
  })

  const reload = useCallback(
    () => Promise.all([loadBacklog(), loadPool()]),
    [loadBacklog, loadPool],
  )

  const plan = useCallback(
    async (cardId: number) => {
      await ideasApi.planOntoBoard(cardId, selectedBoardId)
      await reload()
    },
    [selectedBoardId, reload],
  )

  const toPool = useCallback(
    async (cardId: number) => {
      await ideasApi.moveBackToPool(cardId)
      await reload()
    },
    [reload],
  )

  const onSelectBoard = (value: number) => {
    setSelectedBoardId(value)
    writeSelectedBoard(projectId, value)
  }

  const startDrag = (source: DragSource, id: number) => (e: React.DragEvent) => {
    e.dataTransfer.setData('text/plain', String(id))
    setDragged({ source, id })
  }

  const handleDrop = (zone: DragSource) => (e: React.DragEvent) => {
    e.preventDefault()
    const d = dragged
    setDragged(null)
    if (d === null) return
    if (zone === 'backlog' && d.source === 'pool') void plan(d.id)
    else if (zone === 'pool' && d.source === 'backlog') void toPool(d.id)
  }

  const reorder = async (cardId: number, columnId: number, position: number) => {
    await cardsApi.move(cardId, columnId, position)
    await loadBacklog()
  }

  // Drop einer Backlog-Karte auf eine andere: an deren Position einsortieren (gleiche Spalte).
  // Andere Fälle (Pool-Quelle, Drop auf sich selbst, kein Drag) durchreichen — der Pool→Backlog-
  // Drop wird dann von der Zonen-Ebene (handleDrop) verarbeitet.
  const handleBacklogRowDrop = (target: Card) => (e: React.DragEvent) => {
    const d = dragged
    if (d === null || d.source !== 'backlog' || d.id === target.id) return
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
      <TextField
        select
        size="small"
        label="Board"
        value={String(selectedBoardId)}
        onChange={(e) => onSelectBoard(Number(e.target.value))}
        slotProps={{ htmlInput: { 'aria-label': 'Board wählen' }, select: { native: true } }}
        sx={{ minWidth: 220, mb: 2 }}
      >
        {boards.map((board) => (
          <option key={board.id} value={board.id}>
            {board.name}
          </option>
        ))}
      </TextField>

      {/* Obere Zone: Backlog des gewählten Boards (Ziel beim Einplanen). */}
      <Box
        data-testid="backlog-zone"
        onDragOver={(e) => e.preventDefault()}
        onDrop={handleDrop('backlog')}
        sx={{ minHeight: 80, borderRadius: 1.5, p: 1 }}
      >
        <Typography
          variant="subtitle2"
          sx={{ fontWeight: 700, textTransform: 'uppercase', letterSpacing: '.03em', color: 'text.secondary', mb: 1 }}
        >
          Backlog
        </Typography>
        {backlog.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 1 }}>
            Kein Backlog — zieh eine Idee herauf.
          </Typography>
        ) : (
          <Stack spacing={0.75}>
            {backlog.map((card) => (
              <Paper
                key={card.id}
                data-testid={`backlog-item-${card.id}`}
                variant="outlined"
                draggable={canEdit}
                onDragStart={startDrag('backlog', card.id)}
                onDragOver={(e) => e.preventDefault()}
                onDrop={handleBacklogRowDrop(card)}
                sx={{ px: 1.5, py: 1, display: 'flex', alignItems: 'center', gap: 1.5, cursor: canEdit ? 'grab' : 'default' }}
              >
                <Typography variant="caption" color="text.secondary" sx={{ width: 48, flexShrink: 0 }}>
                  #{card.number}
                </Typography>
                <Typography variant="body2" noWrap sx={{ flex: 1, minWidth: 0, fontWeight: 500 }}>
                  {card.title}
                </Typography>
                {canEdit && (
                  <Button
                    size="small"
                    startIcon={<SouthOutlinedIcon />}
                    aria-label={`Karte ${card.title} in den Pool`}
                    onClick={() => void toPool(card.id)}
                  >
                    In den Pool
                  </Button>
                )}
              </Paper>
            ))}
          </Stack>
        )}
      </Box>

      <Box sx={{ borderTop: '2px dashed', borderColor: 'divider', my: 2 }} />

      {/* Untere Zone: projektweiter, board-loser Ideen-Pool (Quelle beim Einplanen). */}
      <Box
        data-testid="pool-zone"
        onDragOver={(e) => e.preventDefault()}
        onDrop={handleDrop('pool')}
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
                onDragStart={startDrag('pool', idea.id)}
                sx={{ px: 1.5, py: 1, display: 'flex', alignItems: 'center', gap: 1.5, bgcolor: 'action.hover', cursor: canEdit ? 'grab' : 'default' }}
              >
                <Typography variant="body2" noWrap sx={{ flex: 1, minWidth: 0, fontWeight: 500 }}>
                  {idea.title}
                </Typography>
                {canEdit && (
                  <Button
                    size="small"
                    startIcon={<NorthOutlinedIcon />}
                    aria-label={`Idee ${idea.title} einplanen`}
                    onClick={() => void plan(idea.id)}
                  >
                    Einplanen
                  </Button>
                )}
              </Paper>
            ))}
          </Stack>
        )}
      </Box>
    </Box>
  )
}
