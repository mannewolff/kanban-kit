import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Typography from '@mui/material/Typography'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { boardsApi, type Board } from '../api/boards'
import { ideasApi, type Idea } from '../api/ideas'
import { projectsApi } from '../api/projects'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { IdeaPlanningBoard } from '../components/IdeaPlanningBoard'
import { NewCardModal, type NewItemInput } from '../components/NewCardModal'
import { canEditCards } from '../lib/roles'
import { useRefetchOnFocus } from '../lib/useRefetchOnFocus'

/** Filter nach notiertem Zielboard: alle, ohne Zielboard, oder eine konkrete Board-ID. */
type BoardFilter = 'all' | 'none' | number

/** Ansicht der Ideen-Seite: die filterbare Liste oder das gestapelte Planungs-Board. */
type ViewMode = 'liste' | 'planen'

const VIEW_STORAGE_KEY = 'manban.ideasView'

function readView(): ViewMode {
  try {
    return localStorage.getItem(VIEW_STORAGE_KEY) === 'planen' ? 'planen' : 'liste'
  } catch {
    return 'liste'
  }
}

function writeView(view: ViewMode): void {
  try {
    localStorage.setItem(VIEW_STORAGE_KEY, view)
  } catch {
    // localStorage nicht verfügbar — kein Hard-Fail
  }
}

/**
 * Projektweite Ideen-Seite (Projekt-Ebene, Geschwister von „Boards"): listet den board-losen
 * Ideen-Pool des Projekts, legt board-lose Ideen an, plant sie auf ein Board ein (Zielboard aus
 * dem Ingest vorausgewählt) und holt eingeplante/Legacy-Karten zurück in den Pool.
 */
export function IdeasPage() {
  const { projectId } = useParams()
  const id = Number.parseInt(projectId ?? '', 10)
  const validId = Number.isInteger(id) && id > 0
  const navigate = useNavigate()

  const [ideas, setIdeas] = useState<Idea[]>([])
  const [boards, setBoards] = useState<Board[]>([])
  const [role, setRole] = useState<string>('VIEWER')
  const [projectName, setProjectName] = useState<string>('')
  const [textFilter, setTextFilter] = useState('')
  const [boardFilter, setBoardFilter] = useState<BoardFilter>('all')
  const [createOpen, setCreateOpen] = useState(false)
  const [planTarget, setPlanTarget] = useState<Idea | null>(null)
  const [planBoardId, setPlanBoardId] = useState<number>(0)
  const [view, setView] = useState<ViewMode>(readView)

  const canEdit = canEditCards(role)

  const changeView = (next: ViewMode) => {
    setView(next)
    writeView(next)
  }

  const reload = useCallback(() => {
    if (!validId) {
      return Promise.resolve()
    }
    return Promise.all([ideasApi.list(id), boardsApi.list(id)]).then(([is, bs]) => {
      setIdeas(is)
      setBoards(bs)
    })
  }, [id, validId])

  useEffect(() => {
    if (!validId) {
      return
    }
    let active = true
    void ideasApi.list(id).then((is) => {
      if (active) setIdeas(is)
    })
    void boardsApi.list(id).then((bs) => {
      if (active) setBoards(bs)
    })
    void projectsApi.list().then((projects) => {
      if (!active) return
      const project = projects.find((p) => p.id === id)
      if (project) {
        setRole(project.role)
        setProjectName(project.name)
      }
    })
    return () => {
      active = false
    }
  }, [id, validId])

  // Beim Zurückkehren in den Tab neu laden (Ideen können per Ingest in einer anderen Session
  // hinzugekommen sein). useRefetchOnFocus hält die aktuelle Referenz per Ref.
  useRefetchOnFocus(() => {
    void reload().catch(() => {})
  })

  const boardName = useMemo(() => new Map(boards.map((b) => [b.id, b.name])), [boards])

  const filtered = useMemo(() => {
    const text = textFilter.trim().toLowerCase()
    return ideas.filter((idea) => {
      if (text && !idea.title.toLowerCase().includes(text)) return false
      if (boardFilter === 'all') return true
      if (boardFilter === 'none') return idea.targetBoardId === null
      return idea.targetBoardId === boardFilter
    })
  }, [ideas, textFilter, boardFilter])

  const handleCreate = async (input: NewItemInput) => {
    await ideasApi.create(id, { title: input.title, description: input.description })
    await reload()
  }

  const openPlan = (idea: Idea) => {
    // Zielboard vorwählen: das notierte target_board_id (sofern das Board noch existiert), sonst
    // das erste Board. Der „Einplanen"-Button ist bei leerer Board-Liste deaktiviert, daher gibt es
    // hier stets mindestens ein Board.
    const noted = idea.targetBoardId !== null && boardName.has(idea.targetBoardId) ? idea.targetBoardId : null
    setPlanBoardId(noted ?? boards[0].id)
    setPlanTarget(idea)
  }

  const doPlan = async (cardId: number, target: number) => {
    await ideasApi.planOntoBoard(cardId, target)
    setPlanTarget(null)
    navigate(`/boards/${target}/list`)
  }

  const handleBackToPool = async (idea: Idea) => {
    await ideasApi.moveBackToPool(idea.id)
    await reload()
  }

  if (!validId) {
    return <Alert severity="error">Ungültige Projekt-ID.</Alert>
  }

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Breadcrumbs
          items={[
            { label: 'Projekte', to: '/' },
            { label: projectName || 'Projekt', to: `/projects/${id}` },
            { label: 'Ideen' },
          ]}
        />
        <Stack direction="row" spacing={1} alignItems="center">
          <ToggleButtonGroup
            size="small"
            exclusive
            value={view}
            onChange={(_, next) => {
              if (next !== null) changeView(next)
            }}
            aria-label="Ansicht"
          >
            <ToggleButton value="liste">Liste</ToggleButton>
            <ToggleButton value="planen">Planen</ToggleButton>
          </ToggleButtonGroup>
          {view === 'liste' && canEdit && (
            <Button variant="contained" onClick={() => setCreateOpen(true)}>
              Idee anlegen
            </Button>
          )}
        </Stack>
      </Stack>

      {view === 'planen' ? (
        <IdeaPlanningBoard projectId={id} canEdit={canEdit} />
      ) : (
        <>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mb: 3 }}>
        <TextField
          size="small"
          label="Suche"
          value={textFilter}
          onChange={(e) => setTextFilter(e.target.value)}
          slotProps={{ htmlInput: { 'aria-label': 'Ideen durchsuchen' } }}
        />
        <TextField
          select
          size="small"
          label="Zielboard"
          value={boardFilter === 'all' ? 'all' : String(boardFilter)}
          onChange={(e) => {
            const value = e.target.value
            if (value === 'all' || value === 'none') setBoardFilter(value)
            else setBoardFilter(Number(value))
          }}
          slotProps={{ htmlInput: { 'aria-label': 'Nach Zielboard filtern' }, select: { native: true } }}
          sx={{ minWidth: 180 }}
        >
          <option value="all">Alle</option>
          <option value="none">(ohne Zielboard)</option>
          {boards.map((board) => (
            <option key={board.id} value={board.id}>
              {board.name}
            </option>
          ))}
        </TextField>
      </Stack>

      {filtered.length === 0 ? (
        <Typography color="text.secondary">
          {ideas.length === 0 ? 'Noch keine Ideen im Pool.' : 'Keine Ideen für diesen Filter.'}
        </Typography>
      ) : (
        <Stack spacing={1}>
          {filtered.map((idea) => {
            const notedBoard = idea.targetBoardId !== null ? boardName.get(idea.targetBoardId) : undefined
            return (
              <Paper
                key={idea.id}
                variant="outlined"
                sx={{ px: 2, py: 1.5, display: 'flex', alignItems: 'center', gap: 1.5 }}
              >
                <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                    {idea.title}
                  </Typography>
                  <Stack direction="row" spacing={1} sx={{ mt: 0.5 }} alignItems="center">
                    {idea.boardId !== null && (
                      <Chip size="small" label={`Auf Board: ${boardName.get(idea.boardId) ?? idea.boardId}`} />
                    )}
                    {notedBoard !== undefined && (
                      <Typography variant="caption" color="text.secondary">
                        Zielboard: {notedBoard}
                      </Typography>
                    )}
                  </Stack>
                </Box>
                {canEdit &&
                  (idea.boardId !== null ? (
                    <Button size="small" onClick={() => void handleBackToPool(idea)}>
                      Zurück in Pool
                    </Button>
                  ) : (
                    <Button
                      size="small"
                      variant="outlined"
                      disabled={boards.length === 0}
                      onClick={() => openPlan(idea)}
                    >
                      Einplanen
                    </Button>
                  ))}
              </Paper>
            )
          })}
        </Stack>
      )}

      <NewCardModal
        open={createOpen}
        columnName=""
        epics={[]}
        ideaOnly
        onClose={() => setCreateOpen(false)}
        onSubmit={handleCreate}
      />

      <Dialog open={planTarget !== null} onClose={() => setPlanTarget(null)}>
        <DialogTitle>Idee einplanen</DialogTitle>
        <DialogContent>
          <TextField
            select
            fullWidth
            label="Zielboard"
            value={String(planBoardId)}
            onChange={(e) => setPlanBoardId(Number(e.target.value))}
            slotProps={{
              htmlInput: { 'aria-label': 'Zielboard wählen' },
              select: { native: true },
              inputLabel: { shrink: true },
            }}
            sx={{ mt: 1, minWidth: 240 }}
          >
            {boards.map((board) => (
              <option key={board.id} value={board.id}>
                {board.name}
              </option>
            ))}
          </TextField>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPlanTarget(null)}>Abbrechen</Button>
          <Button
            variant="contained"
            onClick={() => {
              if (planTarget) void doPlan(planTarget.id, planBoardId)
            }}
          >
            Einplanen
          </Button>
        </DialogActions>
      </Dialog>
        </>
      )}
    </Box>
  )
}
