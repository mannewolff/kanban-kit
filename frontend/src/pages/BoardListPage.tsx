import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import DragIndicatorIcon from '@mui/icons-material/DragIndicator'
import { useEffect, useMemo, useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { boardsApi, type Board } from '../api/boards'
import { cardsApi, type Card } from '../api/cards'
import { epicsApi, type Epic } from '../api/epics'
import { projectsApi } from '../api/projects'
import { useAuth } from '../auth/AuthContext'
import { CardDetailModal } from '../components/CardDetailModal'
import { EpicBadge } from '../components/EpicBadge'
import { stripMarkdown } from '../lib/listExcerpt'
import { canEditCards } from '../lib/roles'
import { ARCHIVED_STATUS_COLOR, statusColors } from '../lib/statusColors'

const ARCHIVED = 'archived'
type FilterKey = number | typeof ARCHIVED

function storageKey(boardId: number): string {
  return `manban.listFilters.${boardId}`
}

export function BoardListPage() {
  const { boardId } = useParams()
  const id = Number(boardId)
  const { user } = useAuth()
  const [board, setBoard] = useState<Board | null>(null)
  const [cards, setCards] = useState<Card[]>([])
  const [epics, setEpics] = useState<Epic[]>([])
  const [filters, setFilters] = useState<Set<FilterKey> | null>(null)
  const [fetchedRole, setFetchedRole] = useState<string | null>(null)
  const [detailCard, setDetailCard] = useState<Card | null>(null)
  const [dragId, setDragId] = useState<number | null>(null)
  const [dragOverId, setDragOverId] = useState<number | null>(null)

  const reloadCards = () => {
    void cardsApi.list(id).then(setCards)
  }

  useEffect(() => {
    void boardsApi.get(id).then((b) => {
      setBoard(b)
      // Filter aus localStorage oder Default (alle Spalten aktiv, Archiv aus).
      let initial: Set<FilterKey> | null = null
      try {
        const raw = localStorage.getItem(storageKey(id))
        if (raw) {
          initial = new Set(JSON.parse(raw) as FilterKey[])
        }
      } catch {
        initial = null
      }
      setFilters(initial ?? new Set<FilterKey>(b.columns.map((c) => c.id)))
    })
    reloadCards()
    void epicsApi.list(id).then(setEpics)
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
  const canEdit = canEditCards(membershipRole ?? fetchedRole ?? 'VIEWER')

  const columns = useMemo(() => [...(board?.columns ?? [])].sort((a, b) => a.position - b.position), [board])
  const columnById = useMemo(() => new Map(columns.map((c) => [c.id, c])), [columns])
  const epicById = useMemo(() => new Map(epics.map((e) => [e.id, e])), [epics])

  const toggleFilter = (key: FilterKey) => {
    setFilters((prev) => {
      const next = new Set(prev ?? [])
      if (next.has(key)) next.delete(key)
      else next.add(key)
      try {
        localStorage.setItem(storageKey(id), JSON.stringify([...next]))
      } catch {
        // localStorage nicht verfügbar — Filter bleiben sitzungslokal.
      }
      return next
    })
  }

  const archiveActive = filters?.has(ARCHIVED) ?? false
  const visible = cards
    .filter((c) => (c.archived ? archiveActive : (filters?.has(c.columnId) ?? false)))
    .sort((a, b) => {
      const pa = columnById.get(a.columnId)?.position ?? 0
      const pb = columnById.get(b.columnId)?.position ?? 0
      return pa - pb || a.positionInColumn - b.positionInColumn
    })

  const validDrop = (target: Card): boolean => {
    if (dragId == null || dragId === target.id || target.archived) return false
    const dragged = cards.find((c) => c.id === dragId)
    return !!dragged && !dragged.archived && dragged.columnId === target.columnId
  }

  const onDrop = async (target: Card) => {
    const ok = validDrop(target)
    setDragOverId(null)
    if (!ok) return
    await cardsApi.move(dragId!, target.columnId, target.positionInColumn)
    reloadCards()
  }

  return (
    <Box>
      <Link component={RouterLink} to={`/boards/${id}`}>← Board</Link>
      <Typography variant="h5" sx={{ mt: 1, mb: 2 }}>
        {board?.name ?? 'Liste'}
      </Typography>

      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mb: 2 }}>
        {columns.map((col) => {
          const active = filters?.has(col.id) ?? false
          return (
            <Chip
              key={col.id}
              label={col.name}
              aria-label={`Filter ${col.name}`}
              aria-pressed={active}
              onClick={() => toggleFilter(col.id)}
              variant={active ? 'filled' : 'outlined'}
              color={active ? 'primary' : 'default'}
              size="small"
            />
          )
        })}
        <Chip
          label="Archiv"
          aria-label="Filter Archiv"
          aria-pressed={archiveActive}
          onClick={() => toggleFilter(ARCHIVED)}
          variant={archiveActive ? 'filled' : 'outlined'}
          color={archiveActive ? 'primary' : 'default'}
          size="small"
        />
      </Stack>

      {visible.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
          Keine Karten
        </Typography>
      ) : (
        <Stack spacing={0.75}>
          {visible.map((card) => {
            const col = columnById.get(card.columnId)
            const colors = card.archived ? ARCHIVED_STATUS_COLOR : statusColors(col?.name ?? '')
            const label = card.archived ? 'Archiv' : col?.name ?? ''
            const epic = card.parentId != null ? epicById.get(card.parentId) : undefined
            return (
              <Box
                key={card.id}
                role="button"
                tabIndex={0}
                aria-label={`Detail öffnen: ${card.title}`}
                draggable={canEdit && !card.archived}
                onDragStart={(e) => { setDragId(card.id); e.dataTransfer.setData('text/plain', String(card.id)) }}
                onDragOver={(e) => { if (validDrop(card)) { e.preventDefault(); setDragOverId(card.id) } }}
                onDrop={(e) => { e.preventDefault(); void onDrop(card) }}
                onDragEnd={() => { setDragId(null); setDragOverId(null) }}
                onClick={() => setDetailCard(card)}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setDetailCard(card) } }}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1.5,
                  bgcolor: 'common.white',
                  border: '1px solid',
                  borderColor: 'divider',
                  borderTopColor: dragOverId === card.id ? 'primary.main' : 'divider',
                  borderTopWidth: dragOverId === card.id ? 2 : 1,
                  borderRadius: 1.5,
                  px: 1.5,
                  py: 1,
                  cursor: 'pointer',
                  userSelect: 'none',
                  transition: 'box-shadow 150ms',
                  '&:hover': { boxShadow: 2 },
                }}
              >
                <DragIndicatorIcon fontSize="small" aria-label="Reihenfolge ändern"
                  sx={{ flexShrink: 0, color: 'action.disabled', visibility: card.archived ? 'hidden' : 'visible' }} />
                <Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0, width: 40 }}>
                  #{card.number}
                </Typography>
                <Chip label={label} size="small" sx={{ flexShrink: 0, bgcolor: colors.bg, color: colors.text, fontWeight: 600 }} />
                {epic && <EpicBadge epicId={epic.id} title={epic.title} shortcode={epic.shortcode} />}
                <Typography variant="body2" sx={{ flex: 1, minWidth: 0, fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {card.title}
                </Typography>
                <Typography variant="caption" color="text.secondary"
                  sx={{ flex: '0 0 35%', minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {stripMarkdown(card.description ?? '')}
                </Typography>
              </Box>
            )
          })}
        </Stack>
      )}

      {detailCard && (
        <CardDetailModal
          key={detailCard.id}
          card={detailCard}
          canEdit={canEdit}
          epics={epics}
          columnName={columnById.get(detailCard.columnId)?.name}
          onClose={() => setDetailCard(null)}
          onChanged={() => { reloadCards(); void epicsApi.list(id).then(setEpics) }}
        />
      )}
    </Box>
  )
}
