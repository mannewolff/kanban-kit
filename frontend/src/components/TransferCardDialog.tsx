import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import { useEffect, useState } from 'react'
import { boardsApi, type Board } from '../api/boards'
import { cardsApi, type Card } from '../api/cards'
import { projectsApi, type Project } from '../api/projects'

interface Props {
  card: Card
  currentBoardId: number
  /** Plattform-Admin darf in alle Projekte verschieben, sonst nur in eigene OWNER-Projekte. */
  platformAdmin: boolean
  onClose: () => void
  onTransferred: () => void
}

/**
 * Auswahl-Dialog für das board-/projektübergreifende Verschieben einer Karte: Projekt → Board →
 * Spalte. Es werden nur Projekte angeboten, in denen der Nutzer OWNER ist (Plattform-Admin: alle);
 * die Durchsetzung erfolgt zusätzlich serverseitig.
 */
export function TransferCardDialog({
  card,
  currentBoardId,
  platformAdmin,
  onClose,
  onTransferred,
}: Props) {
  const [projects, setProjects] = useState<Project[]>([])
  const [boards, setBoards] = useState<Board[]>([])
  const [projectId, setProjectId] = useState<number | ''>('')
  const [boardId, setBoardId] = useState<number | ''>('')
  const [columnId, setColumnId] = useState<number | ''>('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    void projectsApi
      .list()
      .then((ps) => setProjects(platformAdmin ? ps : ps.filter((p) => p.role === 'OWNER')))
  }, [platformAdmin])

  useEffect(() => {
    setBoardId('')
    setColumnId('')
    if (projectId === '') {
      setBoards([])
      return
    }
    // Das aktuelle Board ist kein sinnvolles Ziel (die Karte liegt bereits dort).
    void boardsApi.list(projectId).then((bs) => setBoards(bs.filter((b) => b.id !== currentBoardId)))
  }, [projectId, currentBoardId])

  const selectedBoard = boards.find((b) => b.id === boardId)
  const columns = selectedBoard ? [...selectedBoard.columns].sort((a, b) => a.position - b.position) : []

  const submit = async () => {
    if (boardId === '' || columnId === '') {
      return
    }
    setBusy(true)
    setError(null)
    try {
      await cardsApi.transfer(card.id, boardId, columnId)
      onTransferred()
    } catch {
      setError('Verschieben fehlgeschlagen.')
      setBusy(false)
    }
  }

  const nativeSelect = { select: true, SelectProps: { native: true }, InputLabelProps: { shrink: true }, fullWidth: true }

  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Auf anderes Board verschieben</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Die Karte wird in das gewählte Board verschoben. Dabei gehen die Epic-Zuordnung und die
          Abhängigkeiten der Karte verloren; Kommentare und Anhänge bleiben erhalten.
        </DialogContentText>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            {...nativeSelect}
            label="Projekt"
            value={projectId}
            onChange={(e) => setProjectId(e.target.value === '' ? '' : Number(e.target.value))}
            inputProps={{ 'aria-label': 'Zielprojekt' }}
          >
            <option value="">(wählen)</option>
            {projects.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </TextField>
          <TextField
            {...nativeSelect}
            label="Board"
            value={boardId}
            disabled={projectId === ''}
            onChange={(e) => {
              setBoardId(e.target.value === '' ? '' : Number(e.target.value))
              setColumnId('')
            }}
            inputProps={{ 'aria-label': 'Zielboard' }}
          >
            <option value="">(wählen)</option>
            {boards.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </TextField>
          <TextField
            {...nativeSelect}
            label="Spalte"
            value={columnId}
            disabled={boardId === ''}
            onChange={(e) => setColumnId(e.target.value === '' ? '' : Number(e.target.value))}
            inputProps={{ 'aria-label': 'Zielspalte' }}
          >
            <option value="">(wählen)</option>
            {columns.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </TextField>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Abbrechen</Button>
        <Button
          variant="contained"
          disabled={busy || boardId === '' || columnId === ''}
          onClick={() => void submit()}
        >
          Verschieben
        </Button>
      </DialogActions>
    </Dialog>
  )
}
