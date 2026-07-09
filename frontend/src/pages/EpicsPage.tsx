import Box from '@mui/material/Box'
import LinearProgress from '@mui/material/LinearProgress'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { boardsApi, type Board } from '../api/boards'
import { cardsApi, type Card } from '../api/cards'
import { epicsApi, type Epic } from '../api/epics'
import { projectsApi } from '../api/projects'
import { useAuth } from '../auth/AuthContext'
import { CardDetailModal } from '../components/CardDetailModal'
import { EpicBadge } from '../components/EpicBadge'
import { canEditCards, isPlatformAdmin } from '../lib/roles'

function epicToCard(epic: Epic, boardId: number): Card {
  return {
    id: epic.id, boardId, columnId: 0, number: epic.number, title: epic.title,
    description: epic.description, positionInColumn: 0, archived: false, movedToDoneAt: null,
    dependencies: [], type: 'EPIC', parentId: null, shortcode: epic.shortcode,
  }
}

export function EpicsPage() {
  const { boardId } = useParams()
  const id = Number(boardId)
  const { user } = useAuth()
  const [board, setBoard] = useState<Board | null>(null)
  const [epics, setEpics] = useState<Epic[]>([])
  const [cards, setCards] = useState<Card[]>([])
  const [fetchedRole, setFetchedRole] = useState<string | null>(null)
  const [selected, setSelected] = useState<Epic | null>(null)

  const reload = () => {
    void epicsApi.list(id).then(setEpics)
    void cardsApi.list(id).then(setCards)
  }

  useEffect(() => {
    void boardsApi.get(id).then(setBoard)
    reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const membershipRole = board
    ? user?.memberships.find((m) => m.projectId === board.projectId)?.role
    : undefined
  useEffect(() => {
    if (!board || membershipRole) {
      setFetchedRole(null)
      return
    }
    void projectsApi.list().then((ps) => setFetchedRole(ps.find((p) => p.id === board.projectId)?.role ?? 'VIEWER'))
  }, [board, membershipRole])
  const canEdit = canEditCards(membershipRole ?? fetchedRole ?? 'VIEWER', isPlatformAdmin(user))

  return (
    <Box>
      <Link component={RouterLink} to={`/boards/${id}`}>← Board</Link>
      <Typography variant="h5" sx={{ mt: 1, mb: 2 }}>
        Epics
      </Typography>

      <Stack spacing={1.5}>
        {epics.map((epic) => {
          const pct = epic.total > 0 ? (epic.done / epic.total) * 100 : 0
          return (
            <Paper
              key={epic.id}
              variant="outlined"
              onClick={() => setSelected(epic)}
              sx={{ p: 2, cursor: 'pointer', '&:hover': { boxShadow: 2 } }}
            >
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
                <EpicBadge epicId={epic.id} title={epic.title} shortcode={epic.shortcode} />
                <Typography variant="subtitle1" sx={{ fontWeight: 600, flexGrow: 1 }}>
                  {epic.title}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {epic.done}/{epic.total}
                </Typography>
              </Stack>
              <LinearProgress
                variant="determinate"
                value={pct}
                aria-label={`Fortschritt ${epic.title}`}
                sx={{ height: 8, borderRadius: 1 }}
              />
            </Paper>
          )
        })}
        {epics.length === 0 && <Typography color="text.secondary">Noch keine Epics.</Typography>}
      </Stack>

      {selected && (
        <CardDetailModal
          card={epicToCard(selected, id)}
          canEdit={canEdit}
          children={cards.filter((c) => c.parentId === selected.id)}
          onClose={() => setSelected(null)}
          onChanged={reload}
        />
      )}
    </Box>
  )
}
