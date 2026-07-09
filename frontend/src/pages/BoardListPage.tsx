import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import type { SxProps, Theme } from '@mui/material/styles'
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

type ColumnKey = 'number' | 'status' | 'epic' | 'title' | 'excerpt'
const COLUMN_META: Record<ColumnKey, { label: string; sx: SxProps<Theme> }> = {
  number: { label: 'Nr', sx: { flexShrink: 0, width: 48 } },
  status: { label: 'Status', sx: { flexShrink: 0, width: 108 } },
  epic: { label: 'Epic', sx: { flexShrink: 0, width: 76 } },
  title: { label: 'Titel', sx: { flex: 1, minWidth: 0 } },
  excerpt: { label: 'Beschreibung', sx: { flex: '0 0 30%', minWidth: 0 } },
}
const DEFAULT_ORDER: ColumnKey[] = ['number', 'status', 'epic', 'title', 'excerpt']
const ALL_KEYS = new Set<ColumnKey>(DEFAULT_ORDER)

function filterKey(boardId: number): string {
  return `manban.listFilters.${boardId}`
}
function columnKey(boardId: number): string {
  return `manban.listColumns.${boardId}`
}

/** Liest die gespeicherte Spaltenreihenfolge; unbekannte Keys raus, fehlende hinten anfügen. */
function readColumnOrder(boardId: number): ColumnKey[] {
  try {
    const raw = localStorage.getItem(columnKey(boardId))
    if (!raw) return DEFAULT_ORDER
    const stored = (JSON.parse(raw) as string[]).filter((k): k is ColumnKey => ALL_KEYS.has(k as ColumnKey))
    const missing = DEFAULT_ORDER.filter((k) => !stored.includes(k))
    return [...stored, ...missing]
  } catch {
    return DEFAULT_ORDER
  }
}

export function BoardListPage() {
  const { boardId } = useParams()
  const id = Number(boardId)
  const { user } = useAuth()
  const [board, setBoard] = useState<Board | null>(null)
  const [cards, setCards] = useState<Card[]>([])
  const [epics, setEpics] = useState<Epic[]>([])
  const [filters, setFilters] = useState<Set<FilterKey> | null>(null)
  const [order, setOrder] = useState<ColumnKey[]>(() => readColumnOrder(id))
  const [fetchedRole, setFetchedRole] = useState<string | null>(null)
  const [detailCard, setDetailCard] = useState<Card | null>(null)
  const [rowDrag, setRowDrag] = useState<number | null>(null)
  const [rowOver, setRowOver] = useState<number | null>(null)
  const [colDrag, setColDrag] = useState<ColumnKey | null>(null)
  const [colOver, setColOver] = useState<ColumnKey | null>(null)

  const reloadCards = () => {
    void cardsApi.list(id).then(setCards)
  }

  useEffect(() => {
    void boardsApi.get(id).then((b) => {
      setBoard(b)
      let initial: Set<FilterKey> | null = null
      try {
        const raw = localStorage.getItem(filterKey(id))
        if (raw) initial = new Set(JSON.parse(raw) as FilterKey[])
      } catch {
        initial = null
      }
      setFilters(initial ?? new Set<FilterKey>(b.columns.map((c) => c.id)))
    })
    reloadCards()
    void epicsApi.list(id).then(setEpics)
    setOrder(readColumnOrder(id))
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
        localStorage.setItem(filterKey(id), JSON.stringify([...next]))
      } catch {
        // localStorage nicht verfügbar
      }
      return next
    })
  }

  const reorderColumns = (from: ColumnKey, to: ColumnKey) => {
    if (from === to) return
    setOrder((prev) => {
      const next = prev.filter((k) => k !== from)
      next.splice(next.indexOf(to), 0, from)
      try {
        localStorage.setItem(columnKey(id), JSON.stringify(next))
      } catch {
        // localStorage nicht verfügbar
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

  const validRowDrop = (target: Card): boolean => {
    if (rowDrag == null || rowDrag === target.id || target.archived) return false
    const dragged = cards.find((c) => c.id === rowDrag)
    return !!dragged && !dragged.archived && dragged.columnId === target.columnId
  }

  const onRowDrop = async (target: Card) => {
    const ok = validRowDrop(target)
    setRowOver(null)
    if (!ok) return
    await cardsApi.move(rowDrag!, target.columnId, target.positionInColumn)
    reloadCards()
  }

  const renderCell = (key: ColumnKey, card: Card) => {
    const col = columnById.get(card.columnId)
    switch (key) {
      case 'number':
        return <Typography variant="caption" color="text.secondary">#{card.number}</Typography>
      case 'status': {
        const colors = card.archived ? ARCHIVED_STATUS_COLOR : statusColors(col?.name ?? '')
        const label = card.archived ? 'Archiv' : col?.name ?? ''
        return <Chip label={label} size="small" sx={{ bgcolor: colors.bg, color: colors.text, fontWeight: 600 }} />
      }
      case 'epic': {
        const epic = card.parentId != null ? epicById.get(card.parentId) : undefined
        return epic ? <EpicBadge epicId={epic.id} title={epic.title} shortcode={epic.shortcode} /> : null
      }
      case 'title':
        return <Typography variant="body2" noWrap sx={{ fontWeight: 500 }}>{card.title}</Typography>
      case 'excerpt':
        return <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>{stripMarkdown(card.description ?? '')}</Typography>
    }
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
            <Chip key={col.id} label={col.name} aria-label={`Filter ${col.name}`} aria-pressed={active}
              onClick={() => toggleFilter(col.id)} variant={active ? 'filled' : 'outlined'}
              color={active ? 'primary' : 'default'} size="small" />
          )
        })}
        <Chip label="Archiv" aria-label="Filter Archiv" aria-pressed={archiveActive}
          onClick={() => toggleFilter(ARCHIVED)} variant={archiveActive ? 'filled' : 'outlined'}
          color={archiveActive ? 'primary' : 'default'} size="small" />
      </Stack>

      {visible.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
          Keine Karten
        </Typography>
      ) : (
        <>
          {/* Kopfzeile: Spalten per Drag umsortierbar (Excel-artig). */}
          <Box data-testid="list-header" sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: 1.5, py: 0.5 }}>
            <Box sx={{ width: 20, flexShrink: 0 }} />
            {order.map((key) => (
              <Box
                key={key}
                draggable
                aria-label={`Spalte ${COLUMN_META[key].label}`}
                onDragStart={(e) => { e.stopPropagation(); setColDrag(key) }}
                onDragOver={(e) => { if (colDrag && colDrag !== key) { e.preventDefault(); setColOver(key) } }}
                onDrop={(e) => { e.preventDefault(); if (colDrag) reorderColumns(colDrag, key); setColDrag(null); setColOver(null) }}
                onDragEnd={() => { setColDrag(null); setColOver(null) }}
                sx={{
                  ...COLUMN_META[key].sx,
                  cursor: 'grab',
                  userSelect: 'none',
                  borderBottom: '2px solid',
                  borderColor: colOver === key ? 'primary.main' : 'transparent',
                }}
              >
                <Typography variant="caption" sx={{ fontWeight: 700, textTransform: 'uppercase', letterSpacing: '.03em', color: 'text.secondary' }}>
                  {COLUMN_META[key].label}
                </Typography>
              </Box>
            ))}
          </Box>

          <Stack spacing={0.75}>
            {visible.map((card) => (
              <Box
                key={card.id}
                role="button"
                tabIndex={0}
                aria-label={`Detail öffnen: ${card.title}`}
                draggable={canEdit && !card.archived}
                onDragStart={(e) => { setRowDrag(card.id); e.dataTransfer.setData('text/plain', String(card.id)) }}
                onDragOver={(e) => { if (validRowDrop(card)) { e.preventDefault(); setRowOver(card.id) } }}
                onDrop={(e) => { e.preventDefault(); void onRowDrop(card) }}
                onDragEnd={() => { setRowDrag(null); setRowOver(null) }}
                onClick={() => setDetailCard(card)}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setDetailCard(card) } }}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1.5,
                  bgcolor: 'common.white',
                  border: '1px solid',
                  borderColor: 'divider',
                  borderTopColor: rowOver === card.id ? 'primary.main' : 'divider',
                  borderTopWidth: rowOver === card.id ? 2 : 1,
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
                {order.map((key) => (
                  <Box key={key} sx={{ ...COLUMN_META[key].sx, overflow: 'hidden' }}>
                    {renderCell(key, card)}
                  </Box>
                ))}
              </Box>
            ))}
          </Stack>
        </>
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
