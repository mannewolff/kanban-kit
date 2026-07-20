import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import IconButton from '@mui/material/IconButton'
import Snackbar from '@mui/material/Snackbar'
import Stack from '@mui/material/Stack'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import type { SxProps, Theme } from '@mui/material/styles'
import AddIcon from '@mui/icons-material/Add'
import DragIndicatorIcon from '@mui/icons-material/DragIndicator'
import LightbulbOutlinedIcon from '@mui/icons-material/LightbulbOutlined'
import NorthOutlinedIcon from '@mui/icons-material/NorthOutlined'
import RestoreOutlinedIcon from '@mui/icons-material/RestoreOutlined'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { boardsApi, type Board } from '../api/boards'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { NewCardModal, type NewItemInput } from '../components/NewCardModal'
import { cardsApi, type Card } from '../api/cards'
import { epicsApi, type Epic } from '../api/epics'
import { labelsApi, type Label } from '../api/labels'
import { projectsApi } from '../api/projects'
import { useAuth } from '../auth/AuthContext'
import { CardDetailModal } from '../components/CardDetailModal'
import { EpicBadge } from '../components/EpicBadge'
import { clampExcerptWidth, EXCERPT_DEFAULT_PCT, stripMarkdown } from '../lib/listExcerpt'
import { canEditCards, canModerateComments, isPlatformAdmin } from '../lib/roles'
import { useProjectName } from '../lib/useProjectName'
import { formatDueDate, isOverdue } from '../lib/dueDate'
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
function excerptKey(boardId: number): string {
  return `manban.listExcerptWidth.${boardId}`
}

function readExcerptWidth(boardId: number): number {
  try {
    const raw = localStorage.getItem(excerptKey(boardId))
    return raw == null ? EXCERPT_DEFAULT_PCT : clampExcerptWidth(Number.parseFloat(raw))
  } catch {
    return EXCERPT_DEFAULT_PCT
  }
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
  const id = Number.parseInt(boardId ?? '', 10)
  const validId = Number.isInteger(id) && id > 0
  const { user } = useAuth()
  const [board, setBoard] = useState<Board | null>(null)
  const [cards, setCards] = useState<Card[]>([])
  const [epics, setEpics] = useState<Epic[]>([])
  const [labels, setLabels] = useState<Label[]>([])
  const [labelFilter, setLabelFilter] = useState<Set<number>>(new Set())
  const [filters, setFilters] = useState<Set<FilterKey> | null>(null)
  const [order, setOrder] = useState<ColumnKey[]>(() => readColumnOrder(id))
  const [fetchedRole, setFetchedRole] = useState<string | null>(null)
  const [detailCard, setDetailCard] = useState<Card | null>(null)
  const [snackbar, setSnackbar] = useState<string | null>(null)
  const [newIdeaOpen, setNewIdeaOpen] = useState(false)
  const [rowDrag, setRowDrag] = useState<number | null>(null)
  const [rowOver, setRowOver] = useState<number | null>(null)
  const [colDrag, setColDrag] = useState<ColumnKey | null>(null)
  const [colOver, setColOver] = useState<ColumnKey | null>(null)
  const [excerptWidth, setExcerptWidth] = useState<number>(() => readExcerptWidth(id))
  const viewRef = useRef<HTMLDivElement>(null)
  const resizingRef = useRef(false)
  const resizeCleanupRef = useRef<(() => void) | null>(null)

  const reloadCards = () => {
    void cardsApi.list(id).then(setCards)
  }

  const restoreCard = async (cardId: number) => {
    await cardsApi.restore(cardId)
    reloadCards()
  }

  useEffect(() => {
    if (!validId) {
      return
    }
    let active = true
    void boardsApi.get(id).then((b) => {
      if (!active) return
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
    void cardsApi.list(id).then((cs) => {
      if (active) setCards(cs)
    })
    void epicsApi.list(id).then((es) => {
      if (active) setEpics(es)
    })
    void labelsApi.list(id).then((ls) => {
      if (active) setLabels(ls)
    })
    setOrder(readColumnOrder(id))
    setExcerptWidth(readExcerptWidth(id))
    return () => {
      active = false
    }
  }, [id, validId])

  // Laufenden Resize-Drag bei Unmount abräumen.
  useEffect(() => () => resizeCleanupRef.current?.(), [])

  const startResize = (e: React.MouseEvent) => {
    const width = viewRef.current?.getBoundingClientRect().width ?? 0
    const startX = e.clientX
    const startPct = excerptWidth
    resizingRef.current = true
    const onMove = (ev: MouseEvent) => {
      if (!width) return
      // Nach links ziehen verbreitert die Beschreibungs-Spalte.
      setExcerptWidth(clampExcerptWidth(startPct + ((startX - ev.clientX) / width) * 100))
    }
    const detach = () => {
      document.removeEventListener('mousemove', onMove)
      document.removeEventListener('mouseup', onUp)
      resizeCleanupRef.current = null
    }
    const onUp = () => {
      detach()
      setExcerptWidth((w) => {
        try {
          localStorage.setItem(excerptKey(id), String(w))
        } catch {
          // localStorage nicht verfügbar
        }
        return w
      })
      // Flag erst nach dem Click-Event zurücksetzen, damit kein Detail-Modal aufgeht.
      setTimeout(() => {
        resizingRef.current = false
      }, 0)
    }
    document.addEventListener('mousemove', onMove)
    document.addEventListener('mouseup', onUp)
    resizeCleanupRef.current = detach
  }

  const cellSx = (key: ColumnKey): SxProps<Theme> =>
    key === 'excerpt' ? { ...COLUMN_META.excerpt.sx, flex: `0 0 ${excerptWidth}%` } : COLUMN_META[key].sx

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
  const effectiveRole = membershipRole ?? fetchedRole ?? 'VIEWER'
  const canEdit = canEditCards(effectiveRole, isPlatformAdmin(user))
  const canModerate = canModerateComments(effectiveRole, isPlatformAdmin(user))
  const projectName = useProjectName(board?.projectId ?? null)

  const columns = useMemo(() => [...(board?.columns ?? [])].sort((a, b) => a.position - b.position), [board])
  const columnById = useMemo(() => new Map(columns.map((c) => [c.id, c])), [columns])
  const epicById = useMemo(() => new Map(epics.map((e) => [e.id, e])), [epics])

  // Ideen-Speicher-Aktionen (Alltag, an canEdit gebunden): optimistisch mit Rollback bei Fehler.
  const promoteCard = async (cardId: number) => {
    const previous = cards
    const firstColumnId = columns[0]?.id
    setCards((cur) =>
      cur.map((c) =>
        c.id === cardId ? { ...c, ideaStored: false, columnId: firstColumnId ?? c.columnId } : c,
      ),
    )
    try {
      await cardsApi.promote(cardId)
      reloadCards()
    } catch {
      setCards(previous)
      setSnackbar('Hochziehen fehlgeschlagen.')
    }
  }

  const demoteCard = async (cardId: number) => {
    const previous = cards
    setCards((cur) => cur.map((c) => (c.id === cardId ? { ...c, ideaStored: true } : c)))
    try {
      await cardsApi.moveToIdeaStorage(cardId)
      reloadCards()
    } catch {
      setCards(previous)
      setSnackbar('In den Ideen-Speicher fehlgeschlagen.')
    }
  }

  const createIdea = async (input: NewItemInput) => {
    const firstColumnId = columns[0]?.id
    if (firstColumnId == null) return
    await cardsApi.create(id, firstColumnId, input.title, input.description, input.parentId, true)
    reloadCards()
  }

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

  const toggleLabel = (labelId: number) => {
    setLabelFilter((prev) => {
      const next = new Set(prev)
      if (next.has(labelId)) next.delete(labelId)
      else next.add(labelId)
      return next
    })
  }

  const archiveActive = filters?.has(ARCHIVED) ?? false
  // Obere Zone: aktive Karten wie bisher — Ideen (ideaStored) sind hier ausgeblendet.
  const visible = cards
    .filter((c) => !c.ideaStored && (c.archived ? archiveActive : (filters?.has(c.columnId) ?? false)))
    .filter((c) => labelFilter.size === 0 || c.labels.some((l) => labelFilter.has(l)))
    .sort((a, b) => {
      const pa = columnById.get(a.columnId)?.position ?? 0
      const pb = columnById.get(b.columnId)?.position ?? 0
      return pa - pb || a.positionInColumn - b.positionInColumn
    })

  // Untere Zone: alle Ideen des Boards, unsortierbar, nach Kartennummer sortiert.
  const ideas = cards
    .filter((c) => c.ideaStored && !c.archived)
    .sort((a, b) => a.number - b.number)

  const validRowDrop = (target: Card): boolean => {
    if (rowDrag == null || rowDrag === target.id || target.archived) return false
    const dragged = cards.find((c) => c.id === rowDrag)
    return !!dragged && !dragged.archived && dragged.columnId === target.columnId
  }

  const onRowDrop = async (target: Card) => {
    const dragId = rowDrag
    const ok = validRowDrop(target)
    setRowOver(null)
    if (!ok || dragId == null) return
    await cardsApi.move(dragId, target.columnId, target.positionInColumn)
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
      case 'title': {
        const overdue = isOverdue(card.dueDate, (col?.name ?? '').toLowerCase().includes('done'))
        return (
          <Box>
            <Typography variant="body2" noWrap sx={{ fontWeight: 500 }}>{card.title}</Typography>
            {card.dueDate != null && (
              <Typography
                variant="caption"
                aria-label={`Fällig ${card.title}`}
                color={overdue ? 'error' : 'text.secondary'}
                sx={{ display: 'block', fontWeight: overdue ? 600 : 400 }}
              >
                📅 {formatDueDate(card.dueDate)}
              </Typography>
            )}
          </Box>
        )
      }
      case 'excerpt':
        return <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>{stripMarkdown(card.description ?? '')}</Typography>
    }
  }

  if (!validId) {
    return <Alert severity="error">Ungültige Board-ID.</Alert>
  }

  return (
    <Box ref={viewRef}>
      <Box sx={{ mb: 2 }}>
        <Breadcrumbs
          items={[
            { label: 'Projekte', to: '/' },
            ...(board && projectName ? [{ label: projectName, to: `/projects/${board.projectId}` }] : []),
            ...(board ? [{ label: board.name, to: `/boards/${id}` }] : []),
            { label: 'Liste' },
          ]}
        />
      </Box>

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

      {labels.length > 0 && (
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mb: 2 }}>
          {labels.map((label) => {
            const active = labelFilter.has(label.id)
            return (
              <Chip
                key={label.id}
                label={label.name}
                aria-label={`Label-Filter ${label.name}`}
                aria-pressed={active}
                onClick={() => toggleLabel(label.id)}
                size="small"
                variant={active ? 'filled' : 'outlined'}
                sx={active ? { bgcolor: label.color, color: '#fff' } : { borderColor: label.color, color: label.color }}
              />
            )
          })}
        </Stack>
      )}

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
                onDrop={(e) => { e.preventDefault(); if (colDrag) { reorderColumns(colDrag, key) } setColDrag(null); setColOver(null) }}
                onDragEnd={() => { setColDrag(null); setColOver(null) }}
                sx={{
                  ...cellSx(key),
                  display: 'flex',
                  alignItems: 'center',
                  cursor: 'grab',
                  userSelect: 'none',
                  borderBottom: '2px solid',
                  borderColor: colOver === key ? 'primary.main' : 'transparent',
                }}
              >
                {key === 'excerpt' && (
                  <Box
                    role="separator"
                    aria-label="Beschreibung-Spalte breiter ziehen"
                    onMouseDown={(e) => { e.stopPropagation(); e.preventDefault(); startResize(e) }}
                    onDragStart={(e) => e.preventDefault()}
                    onClick={(e) => e.stopPropagation()}
                    sx={{
                      alignSelf: 'stretch',
                      width: '6px',
                      flexShrink: 0,
                      mr: 0.5,
                      cursor: 'col-resize',
                      borderRight: '2px solid',
                      borderColor: 'divider',
                      '&:hover': { borderColor: 'primary.main' },
                    }}
                  />
                )}
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
                onClick={() => { if (!resizingRef.current) setDetailCard(card) }}
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
                  <Box key={key} sx={{ ...cellSx(key), overflow: 'hidden' }}>
                    {renderCell(key, card)}
                  </Box>
                ))}
                {canEdit && card.archived && (
                  <Tooltip title="Wiederherstellen">
                    <IconButton
                      size="small"
                      aria-label={`Karte ${card.title} wiederherstellen`}
                      onClick={(e) => {
                        e.stopPropagation()
                        void restoreCard(card.id)
                      }}
                      sx={{ flexShrink: 0 }}
                    >
                      <RestoreOutlinedIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
                {canEdit && !card.archived && (
                  <Tooltip title="In Ideen-Speicher">
                    <IconButton
                      size="small"
                      aria-label={`Karte ${card.title} in Ideen-Speicher`}
                      onClick={(e) => {
                        e.stopPropagation()
                        void demoteCard(card.id)
                      }}
                      sx={{ flexShrink: 0 }}
                    >
                      <LightbulbOutlinedIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
              </Box>
            ))}
          </Stack>
        </>
      )}

      {/* Zweite Zone: Ideen-Speicher, deutlich abgesetzt. */}
      <Box sx={{ mt: 4 }} data-testid="idea-zone">
        <Stack
          direction="row"
          alignItems="center"
          spacing={1}
          sx={{ mb: 1.5, pt: 2, borderTop: '2px dashed', borderColor: 'divider' }}
        >
          <LightbulbOutlinedIcon fontSize="small" color="action" />
          <Typography
            variant="subtitle2"
            sx={{ fontWeight: 700, textTransform: 'uppercase', letterSpacing: '.03em', color: 'text.secondary' }}
          >
            Ideen-Speicher
          </Typography>
          <Box sx={{ flex: 1 }} />
          {canEdit && (
            <Button size="small" startIcon={<AddIcon />} onClick={() => setNewIdeaOpen(true)}>
              Idee anlegen
            </Button>
          )}
        </Stack>

        {ideas.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 1 }}>
            Keine Ideen
          </Typography>
        ) : (
          <Stack spacing={0.75}>
            {ideas.map((card) => (
              <Box
                key={card.id}
                role="button"
                tabIndex={0}
                aria-label={`Detail öffnen: ${card.title}`}
                onClick={() => setDetailCard(card)}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setDetailCard(card) } }}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1.5,
                  bgcolor: 'action.hover',
                  border: '1px dashed',
                  borderColor: 'divider',
                  borderRadius: 1.5,
                  px: 1.5,
                  py: 1,
                  cursor: 'pointer',
                  '&:hover': { boxShadow: 1 },
                }}
              >
                <Typography variant="caption" color="text.secondary" sx={{ width: 48, flexShrink: 0 }}>
                  #{card.number}
                </Typography>
                <Typography variant="body2" noWrap sx={{ flex: 1, minWidth: 0, fontWeight: 500 }}>
                  {card.title}
                </Typography>
                {canEdit && (
                  <Tooltip title="Ins Backlog holen">
                    <IconButton
                      size="small"
                      aria-label={`Idee ${card.title} ins Backlog`}
                      onClick={(e) => {
                        e.stopPropagation()
                        void promoteCard(card.id)
                      }}
                      sx={{ flexShrink: 0 }}
                    >
                      <NorthOutlinedIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
              </Box>
            ))}
          </Stack>
        )}
      </Box>

      {detailCard && (
        <CardDetailModal
          key={detailCard.id}
          card={detailCard}
          canEdit={canEdit}
          canModerateComments={canModerate}
          epics={epics}
          columnName={columnById.get(detailCard.columnId)?.name}
          onClose={() => setDetailCard(null)}
          onChanged={() => { reloadCards(); void epicsApi.list(id).then(setEpics) }}
        />
      )}

      <NewCardModal
        open={newIdeaOpen}
        columnName=""
        epics={epics}
        ideaOnly
        onClose={() => setNewIdeaOpen(false)}
        onSubmit={createIdea}
      />

      <Snackbar
        open={snackbar != null}
        autoHideDuration={4000}
        onClose={() => setSnackbar(null)}
        message={snackbar ?? ''}
      />
    </Box>
  )
}
