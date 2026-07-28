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
import { cardsApi } from '../api/cards'
import { projectsApi, type Project } from '../api/projects'

interface Props {
  /** Eine oder mehrere zu verschiebende Karten. */
  cardIds: number[]
  currentBoardId: number
  /** Projekt des Quellboards: im Dialog vorausgewählt, bleibt frei änderbar. */
  currentProjectId: number
  /**
   * Ordnungsposition der Quellspalte (Index in der nach `position` sortierten Spaltenliste des
   * Quellboards) oder `null`, wenn die Auswahl mehrere Spalten umfasst. Die Zielspalte wird über
   * diese Position vorbelegt, nicht über den Namen — hat das Zielboard dort keine Spalte, bleibt
   * das Feld leer.
   */
  sourceColumnPosition: number | null
  /** Plattform-Admin darf in alle Projekte verschieben, sonst nur in eigene OWNER-Projekte. */
  platformAdmin: boolean
  onClose: () => void
  onTransferred: () => void
}

/**
 * Auswahl-Dialog für das board-/projektübergreifende Verschieben einer oder mehrerer Karten:
 * Projekt → Board → Spalte. Es werden nur Projekte angeboten, in denen der Nutzer OWNER ist
 * (Plattform-Admin: alle); die Durchsetzung erfolgt zusätzlich serverseitig.
 */
export function TransferCardDialog({
  cardIds,
  currentBoardId,
  currentProjectId,
  sourceColumnPosition,
  platformAdmin,
  onClose,
  onTransferred,
}: Readonly<Props>) {
  const [projects, setProjects] = useState<Project[]>([])
  const [boards, setBoards] = useState<Board[]>([])
  const [projectId, setProjectId] = useState<number | ''>(currentProjectId)
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

  const sortedColumns = (b: Board) => [...b.columns].sort((x, y) => x.position - y.position)
  const selectedBoard = boards.find((b) => b.id === boardId)
  const columns = selectedBoard ? sortedColumns(selectedBoard) : []

  const submit = async () => {
    setBusy(true)
    setError(null)
    try {
      // boardId/columnId sind hier garantiert Zahlen (der Verschieben-Button ist bei leerer
      // Auswahl disabled); Number(...) verengt number|'' ohne toten Guard-Zweig.
      await cardsApi.bulkTransfer(cardIds, Number(boardId), Number(columnId))
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
          {cardIds.length === 1 ? 'Die Karte wird' : `Die ${cardIds.length} Karten werden`} in das
          gewählte Board verschoben. Dabei gehen Epic-Zuordnung und Abhängigkeiten verloren;
          Kommentare und Anhänge bleiben erhalten.
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
            slotProps={{ htmlInput: { 'aria-label': 'Zielprojekt' } }}
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
              const target = boards.find((b) => String(b.id) === e.target.value)
              setBoardId(target?.id ?? '')
              // Zielspalte über die Ordnungsposition der Quellspalte vorbelegen. Ist die
              // Quellspalte nicht eindeutig oder hat das Zielboard dort keine Spalte, bleibt das
              // Feld leer. Die Vorbelegung wird bei jedem Board-Wechsel neu bestimmt, damit eine
              // zuvor manuell gewählte Spalte nicht auf das nächste Board durchschlägt.
              setColumnId(
                target && sourceColumnPosition !== null
                  ? (sortedColumns(target)[sourceColumnPosition]?.id ?? '')
                  : '',
              )
            }}
            slotProps={{ htmlInput: { 'aria-label': 'Zielboard' } }}
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
            slotProps={{ htmlInput: { 'aria-label': 'Zielspalte' } }}
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
