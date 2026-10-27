import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import ArchiveOutlinedIcon from '@mui/icons-material/ArchiveOutlined'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import { useEffect, useState } from 'react'
import { Link as RouterLink, useLocation, useNavigate, useParams } from 'react-router-dom'
import { boardsApi, type Board } from '../api/boards'
import { apiErrorMessage } from '../api/client'
import { projectsApi } from '../api/projects'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { useSnackbar } from '../components/SnackbarProvider'
import { VorschauGesperrt } from '../components/VorschauGesperrt'
import { useT } from '../i18n/SpracheContext'
import { canManageBoards, canManageMembers } from '../lib/roles'
import { useRefetchOnFocus } from '../lib/useRefetchOnFocus'
import { SURFACE_HOVER_SHADOW, TABELLENZIFFERN } from '../theme'

export function ProjectBoardsPage() {
  const { projectId } = useParams()
  const id = Number.parseInt(projectId ?? '', 10)
  const validId = Number.isInteger(id) && id > 0
  const navigate = useNavigate()
  const location = useLocation()
  const notify = useSnackbar()
  const t = useT()
  const [boards, setBoards] = useState<Board[]>([])
  const [archived, setArchived] = useState<Board[]>([])
  const [role, setRole] = useState<string>('VIEWER')
  const [projectName, setProjectName] = useState<string>('')
  const [name, setName] = useState('')
  const [confirmBoard, setConfirmBoard] = useState<Board | null>(null)
  const [purgeBoard, setPurgeBoard] = useState<Board | null>(null)
  const [purgeInput, setPurgeInput] = useState('')

  const canManage = canManageBoards(role)

  const reload = () =>
    Promise.all([boardsApi.list(id), boardsApi.listArchived(id)]).then(([bs, arch]) => {
      setBoards(bs)
      setArchived(arch)
    })

  useEffect(() => {
    if (!validId) {
      return
    }
    let active = true
    void boardsApi.list(id).then((bs) => {
      if (!active) return
      setBoards(bs)
      // Bei genau einem Board direkt in dessen Leitstand, die Startansicht des Boards (#979) — nur
      // beim Erst-Aufruf oder in der Auto-Routing-Kette.
      const auto = location.key === 'default' || (location.state as { autoRoute?: boolean } | null)?.autoRoute
      if (bs.length === 1 && auto) {
        navigate(`/boards/${bs[0].id}/leitstand`, { replace: true })
      }
    })
    void boardsApi.listArchived(id).then((arch) => {
      if (active) setArchived(arch)
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
    // location/navigate bewusst nicht in den Deps: der Single-Board-Auto-Redirect soll nur
    // bei Projektwechsel (id) greifen, nicht bei jeder Navigation.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, validId])

  // Beim Zurückkehren in den Tab die Board-Liste und die eigene Rolle neu laden (ein Board kann in
  // einer anderen Session hinzugekommen oder entfernt worden sein) — ohne den Single-Board-Redirect.
  // Keine Memoisierung nötig: useRefetchOnFocus hält die jeweils aktuelle Referenz per Ref.
  const refetchOnFocus = () => {
    if (!validId) {
      return
    }
    void boardsApi.list(id).then(setBoards).catch(() => {})
    void boardsApi.listArchived(id).then(setArchived).catch(() => {})
    void projectsApi.list().then((projects) => {
      const project = projects.find((p) => p.id === id)
      if (project) {
        setRole(project.role)
        setProjectName(project.name)
      }
    })
  }
  useRefetchOnFocus(refetchOnFocus)

  const handleCreate = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!name.trim()) {
      return
    }
    try {
      await boardsApi.create(id, name.trim())
    } catch (e) {
      notify(apiErrorMessage(e, t.BOARDS.ANLEGEN_FEHLER), 'error')
      return
    }
    setName('')
    try {
      await reload()
    } catch (e) {
      // Das Board wurde angelegt — nur das Nachladen der Liste ist gescheitert.
      notify(apiErrorMessage(e, t.BOARDS.ANGELEGT_LISTE_FEHLER), 'error')
    }
  }

  // Der Bestätigungsdialog schließt erst nach dem erfolgreichen Zug: Bliebe er auch bei einem
  // Fehlschlag zu, sähe das Scheitern aus wie ein Erfolg.
  const handleArchiveBoard = async (boardId: number) => {
    try {
      await boardsApi.remove(boardId)
      setConfirmBoard(null)
      await reload()
    } catch (e) {
      notify(apiErrorMessage(e, t.BOARDS.ARCHIVIEREN_FEHLER), 'error')
    }
  }

  const handleRestoreBoard = async (boardId: number) => {
    try {
      await boardsApi.restore(boardId)
      await reload()
    } catch (e) {
      notify(apiErrorMessage(e, t.BOARDS.WIEDERHERSTELLEN_FEHLER), 'error')
    }
  }

  const closePurge = () => {
    setPurgeBoard(null)
    setPurgeInput('')
  }

  const handlePurgeBoard = async (boardId: number) => {
    try {
      await boardsApi.purge(boardId)
      closePurge()
      await reload()
    } catch (e) {
      notify(apiErrorMessage(e, t.BOARDS.ENDGUELTIG_FEHLER), 'error')
    }
  }

  if (!validId) {
    return <Alert severity="error">{t.ALLGEMEIN.UNGUELTIGE_PROJEKT_ID}</Alert>
  }

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Breadcrumbs items={[{ label: t.RAHMEN.NAV_PROJEKTE, to: '/projects' }, { label: projectName || t.RAHMEN.NAV_PROJEKT }]} />
        {canManageMembers(role) && (
          <Link component={RouterLink} to={`/projects/${id}/members`}>{t.RAHMEN.NAV_MITGLIEDER}</Link>
        )}
      </Stack>

      {canManage && (
        <Box component="form" onSubmit={handleCreate} sx={{ mb: 3 }}>
          <Stack direction="row" spacing={1}>
            <TextField size="small" label={t.BOARDS.NEUES_BOARD} value={name} onChange={(e) => setName(e.target.value)} />
            <VorschauGesperrt>
              <Button type="submit" variant="contained">
                {t.ALLGEMEIN.ANLEGEN}
              </Button>
            </VorschauGesperrt>
          </Stack>
        </Box>
      )}

      {boards.length === 0 ? (
        <Typography color="text.secondary">{t.BOARDS.LEER}</Typography>
      ) : (
        <Stack spacing={1}>
          {boards.map((board) => (
            <Paper
              key={board.id}
              variant="outlined"
              onClick={() => navigate(`/boards/${board.id}/leitstand`)}
              sx={{
                px: 2,
                py: 1.5,
                width: '100%',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 1.5,
                transition: 'box-shadow 150ms, border-color 150ms',
                '&:hover': { boxShadow: SURFACE_HOVER_SHADOW, borderColor: 'primary.main' },
              }}
            >
              <Typography variant="subtitle1" sx={{ fontWeight: 600, flexGrow: 1, minWidth: 0 }}>
                {/* Tastaturweg zum Board (Issue #1307): Der Name ist ein echter Link, die Fläche
                    behält ihren Klick. stopPropagation, damit der Klick nicht doppelt navigiert. */}
                <Link
                  component={RouterLink}
                  to={`/boards/${board.id}/leitstand`}
                  underline="none"
                  color="inherit"
                  onClick={(e) => e.stopPropagation()}
                >
                  {board.name}
                </Link>
              </Typography>
              {/* Die Zahlen stehen je Board untereinander: gleich breite Ziffern (AK 10, #960). */}
              <Typography variant="caption" color="text.secondary" sx={TABELLENZIFFERN}>
                {t.BOARDS.SPALTEN(board.columns.length)}
              </Typography>
              {canManage && (
                <VorschauGesperrt>
                  <IconButton
                    size="small"
                    aria-label={t.BOARDS.ARCHIVIEREN_ARIA(board.name)}
                    onClick={(e) => {
                      e.stopPropagation()
                      setConfirmBoard(board)
                    }}
                  >
                    <ArchiveOutlinedIcon fontSize="small" />
                  </IconButton>
                </VorschauGesperrt>
              )}
            </Paper>
          ))}
        </Stack>
      )}

      {canManage && archived.length > 0 && (
        <Box sx={{ mt: 4 }}>
          <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
            {t.BOARDS.ARCHIV}
          </Typography>
          <Stack spacing={1}>
            {archived.map((board) => (
              <Paper
                key={board.id}
                variant="outlined"
                sx={{ px: 2, py: 1.5, display: 'flex', alignItems: 'center', gap: 1.5, opacity: 0.85 }}
              >
                <Typography variant="subtitle1" sx={{ fontWeight: 600, flexGrow: 1, minWidth: 0 }}>
                  {board.name}
                </Typography>
                <VorschauGesperrt>
                  <Button size="small" onClick={() => void handleRestoreBoard(board.id)}>
                    {t.ALLGEMEIN.WIEDERHERSTELLEN}
                  </Button>
                </VorschauGesperrt>
                <VorschauGesperrt>
                  <IconButton
                    size="small"
                    color="error"
                    aria-label={t.BOARDS.ENDGUELTIG_ARIA(board.name)}
                    onClick={() => setPurgeBoard(board)}
                  >
                    <DeleteOutlineIcon fontSize="small" />
                  </IconButton>
                </VorschauGesperrt>
              </Paper>
            ))}
          </Stack>
        </Box>
      )}

      <Dialog open={confirmBoard !== null} onClose={() => setConfirmBoard(null)}>
        <DialogTitle>{t.BOARDS.ARCHIVIEREN_TITEL}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t.BOARDS.ARCHIVIEREN_TEXT(confirmBoard?.name ?? '')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmBoard(null)}>{t.ALLGEMEIN.ABBRECHEN}</Button>
          <Button color="error" onClick={() => { if (confirmBoard) void handleArchiveBoard(confirmBoard.id) }}>
            {t.BOARDS.ARCHIVIEREN}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={purgeBoard !== null} onClose={closePurge}>
        <DialogTitle>{t.BOARDS.ENDGUELTIG_TITEL}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: 2 }}>
            {t.BOARDS.ENDGUELTIG_TEXT(purgeBoard?.name ?? '')}
          </DialogContentText>
          <TextField
            fullWidth
            size="small"
            label={t.BOARDS.BOARD_NAME}
            value={purgeInput}
            onChange={(e) => setPurgeInput(e.target.value)}
            slotProps={{ htmlInput: { 'aria-label': t.BOARDS.BOARD_NAME_BESTAETIGUNG } }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={closePurge}>{t.ALLGEMEIN.ABBRECHEN}</Button>
          <Button
            color="error"
            disabled={purgeInput !== purgeBoard?.name}
            onClick={() => { if (purgeBoard) void handlePurgeBoard(purgeBoard.id) }}
          >
            {t.BOARDS.ENDGUELTIG_LOESCHEN}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
