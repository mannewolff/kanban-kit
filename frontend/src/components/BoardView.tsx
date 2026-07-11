import AddIcon from '@mui/icons-material/Add'
import MoreVertIcon from '@mui/icons-material/MoreVert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import IconButton from '@mui/material/IconButton'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import type { Board } from '../api/boards'
import { cardsApi, type Card, type CardsApi } from '../api/cards'
import { epicsApi as defaultEpicsApi, type Epic, type EpicsApi } from '../api/epics'
import { activeCardsInColumn, applyMove } from '../lib/boardOps'
import { cleanupCountdownLabel, cleanupDaysRemaining } from '../lib/cleanupCountdown'
import { epicColor, epicShortcode } from '../lib/epicMeta'
import { COLUMN_SURFACE_BG, statusColors } from '../lib/statusColors'
import { EpicBadge } from './EpicBadge'
import { NewCardModal, type NewItemInput } from './NewCardModal'

const isDoneColumn = (name: string) => name.toLowerCase().includes('done')

interface Props {
  board: Board
  initialCards: Card[]
  canEdit: boolean
  epics?: Epic[]
  retentionDays?: number
  onCardClick?: (card: Card) => void
  onEditCard?: (card: Card) => void
  onEpicsChanged?: () => void
  onCardsChanged?: () => void
  /** Injizierbar für Tests. */
  api?: Pick<CardsApi, 'create' | 'move' | 'archive' | 'restore' | 'remove'>
  epicsApi?: Pick<EpicsApi, 'create'>
}

/**
 * Spaltenansicht mit Drag & Drop. Verschieben ist optimistisch (Revert bei Fehler). Karten tragen
 * Epic-Badge + farbigen Rand, ein ⋮-Menü (Bearbeiten/Archivieren/Verschieben) und auf Done einen
 * Archiv-Countdown. Karten werden über einen sichtbaren „+"-Dialog angelegt.
 */
export function BoardView({
  board,
  initialCards,
  canEdit,
  epics = [],
  retentionDays = 30,
  onCardClick,
  onEditCard,
  onEpicsChanged,
  onCardsChanged,
  api = cardsApi,
  epicsApi = defaultEpicsApi,
}: Props) {
  const [cards, setCards] = useState<Card[]>(initialCards)
  const [modalColumn, setModalColumn] = useState<{ id: number; name: string } | null>(null)
  const [menu, setMenu] = useState<{ card: Card; anchor: HTMLElement } | null>(null)
  const [epicFilter, setEpicFilter] = useState<number | null>(() => {
    try {
      const raw = localStorage.getItem(`manban.boardEpicFilter.${board.id}`)
      return raw ? Number(raw) : null
    } catch {
      return null
    }
  })

  useEffect(() => setCards(initialCards), [initialCards])

  const epicById = new Map(epics.map((e) => [e.id, e]))
  const columns = [...board.columns].sort((a, b) => a.position - b.position)
  // Anzeige-Filter nach Epic (nur Darstellung; Move/Anlegen arbeiten auf dem vollen Bestand).
  const filteredCards = epicFilter == null ? cards : cards.filter((c) => c.parentId === epicFilter)

  const changeEpicFilter = (value: number | null) => {
    setEpicFilter(value)
    try {
      if (value == null) localStorage.removeItem(`manban.boardEpicFilter.${board.id}`)
      else localStorage.setItem(`manban.boardEpicFilter.${board.id}`, String(value))
    } catch {
      // localStorage nicht verfügbar
    }
  }

  const moveCard = async (cardId: number, toColumnId: number) => {
    const card = cards.find((c) => c.id === cardId)
    if (!card || card.columnId === toColumnId) {
      return
    }
    const previous = cards
    const endIndex = activeCardsInColumn(previous, toColumnId).length
    setCards(applyMove(previous, cardId, toColumnId))
    try {
      await api.move(cardId, toColumnId, endIndex)
    } catch {
      setCards(previous)
    }
  }

  const createItem = async (columnId: number, input: NewItemInput) => {
    if (input.type === 'EPIC') {
      await epicsApi.create(board.id, input.title, input.description, input.shortcode)
      onEpicsChanged?.()
      return
    }
    const created = await api.create(board.id, columnId, input.title, input.description, input.parentId)
    setCards((current) => [...current, created])
  }

  const archiveCard = async (card: Card) => {
    await api.archive(card.id)
    onCardsChanged?.()
  }

  const closeMenu = () => setMenu(null)

  return (
    <Box>
      {(epics.length > 0 || (canEdit && columns.length > 0)) && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
          {epics.length > 0 && (
            <TextField
              select
              SelectProps={{ native: true }}
              size="small"
              label="Epic-Filter"
              value={epicFilter ?? ''}
              onChange={(e) => changeEpicFilter(e.target.value === '' ? null : Number(e.target.value))}
              inputProps={{ 'aria-label': 'Epic-Filter' }}
              InputLabelProps={{ shrink: true }}
              sx={{ minWidth: 200 }}
            >
              <option value="">Alle Epics</option>
              {epics.map((epic) => (
                <option key={epic.id} value={epic.id}>
                  {epicShortcode(epic.title, epic.shortcode)} – {epic.title}
                </option>
              ))}
            </TextField>
          )}
          <Box sx={{ flexGrow: 1 }} />
          {canEdit && columns.length > 0 && (
            <Button
              variant="contained"
              size="small"
              startIcon={<AddIcon />}
              onClick={() => setModalColumn({ id: columns[0].id, name: columns[0].name })}
            >
              Neues Item
            </Button>
          )}
        </Box>
      )}

      <Stack
        direction="row"
        spacing={2}
        sx={{
          overflowX: 'auto',
          pb: 2,
          width: '100%',
          alignItems: 'stretch',
          // Spalten füllen die Höhe bis nahe an den Viewport-Rand (Offset ≈ AppBar + Header).
          minHeight: 'calc(100vh - 210px)',
        }}
      >
        {columns.map((column) => {
          const colors = statusColors(column.name)
          const count = activeCardsInColumn(filteredCards, column.id).length
          const done = isDoneColumn(column.name)
          return (
            <Paper
              key={column.id}
              data-testid={`column-${column.id}`}
              elevation={0}
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => {
                e.preventDefault()
                const id = Number(e.dataTransfer.getData('text/plain'))
                if (id) {
                  void moveCard(id, column.id)
                }
              }}
              sx={{
                flex: '1 1 0',
                minWidth: 240,
                display: 'flex',
                flexDirection: 'column',
                bgcolor: COLUMN_SURFACE_BG,
                borderRadius: 2,
                overflow: 'hidden',
              }}
            >
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 1.5, py: 1, bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider' }}>
                <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: colors.dot, flexShrink: 0 }} />
                <Typography variant="caption" sx={{ fontWeight: 700, textTransform: 'uppercase', letterSpacing: '.08em', color: 'text.secondary', flexGrow: 1 }}>
                  {column.name}
                </Typography>
                <Typography variant="caption" sx={{ color: 'text.secondary', bgcolor: COLUMN_SURFACE_BG, border: 1, borderColor: 'divider', borderRadius: 10, px: 0.75, lineHeight: 1.6 }}>
                  {column.wipLimit != null ? `${count}/${column.wipLimit}` : count}
                </Typography>
                {canEdit && (
                  <Tooltip title="Karte anlegen">
                    <IconButton size="small" aria-label={`Karte in ${column.name} anlegen`}
                      onClick={() => setModalColumn({ id: column.id, name: column.name })} sx={{ color: 'text.secondary' }}>
                      <AddIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
              </Box>

              <Stack spacing={1} sx={{ p: 1, flex: 1 }}>
                {activeCardsInColumn(filteredCards, column.id).map((card) => {
                  const epic = card.parentId != null ? epicById.get(card.parentId) : undefined
                  const showDone = done && card.movedToDoneAt != null
                  return (
                    <Paper
                      key={card.id}
                      data-testid={`card-${card.id}`}
                      draggable={canEdit}
                      onDragStart={(e) => e.dataTransfer.setData('text/plain', String(card.id))}
                      onClick={() => onCardClick?.(card)}
                      elevation={0}
                      sx={{
                        p: 1.25,
                        borderRadius: 1.5,
                        bgcolor: 'background.paper',
                        border: 1,
                        borderColor: 'divider',
                        borderLeft: epic ? `4px solid ${epicColor(epic.id)}` : undefined,
                        cursor: canEdit ? 'grab' : 'pointer',
                        transition: 'border-color .15s',
                        '&:hover': { borderColor: 'primary.light' },
                        '&:active': { cursor: canEdit ? 'grabbing' : 'pointer' },
                      }}
                    >
                      {epic && <EpicBadge epicId={epic.id} title={epic.title} shortcode={epic.shortcode} sx={{ mb: 0.5 }} />}
                      <Stack direction="row" alignItems="flex-start" spacing={0.5}>
                        <Typography variant="body2" sx={{ flex: 1, minWidth: 0 }}>
                          <Box component="span" sx={{ color: 'text.secondary' }}>#{card.number} – </Box>
                          <Box component="span" sx={{ fontWeight: 600 }}>{card.title}</Box>
                        </Typography>
                        {canEdit && (
                          <IconButton
                            size="small"
                            aria-label={`Menü ${card.title}`}
                            onClick={(e) => {
                              e.stopPropagation()
                              setMenu({ card, anchor: e.currentTarget })
                            }}
                            sx={{ mt: -0.5, mr: -0.5 }}
                          >
                            <MoreVertIcon fontSize="small" />
                          </IconButton>
                        )}
                      </Stack>
                      {showDone && (
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                          {cleanupCountdownLabel(cleanupDaysRemaining(card.movedToDoneAt!, retentionDays))}
                        </Typography>
                      )}
                    </Paper>
                  )
                })}
              </Stack>
            </Paper>
          )
        })}
      </Stack>

      <Menu anchorEl={menu?.anchor ?? null} open={menu != null} onClose={closeMenu}>
        {menu && !menu.card.archived && [
          <MenuItem key="edit" onClick={() => { const c = menu.card; closeMenu(); onEditCard?.(c) }}>
            Bearbeiten
          </MenuItem>,
          <MenuItem key="archive" onClick={() => { const c = menu.card; closeMenu(); void archiveCard(c) }}>
            Archivieren
          </MenuItem>,
          ...columns
            .filter((col) => col.id !== menu.card.columnId)
            .map((col) => (
              <MenuItem key={`move-${col.id}`} onClick={() => { const c = menu.card; closeMenu(); void moveCard(c.id, col.id) }}>
                Nach {col.name}
              </MenuItem>
            )),
        ]}
      </Menu>

      <NewCardModal
        open={modalColumn !== null}
        columnName={modalColumn?.name ?? ''}
        epics={epics}
        onClose={() => setModalColumn(null)}
        onSubmit={(input) => (modalColumn ? createItem(modalColumn.id, input) : undefined)}
      />
    </Box>
  )
}
