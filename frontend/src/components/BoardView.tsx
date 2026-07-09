import AddIcon from '@mui/icons-material/Add'
import MoreVertIcon from '@mui/icons-material/MoreVert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import IconButton from '@mui/material/IconButton'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import type { Board } from '../api/boards'
import { cardsApi, type Card, type CardsApi } from '../api/cards'
import { epicsApi as defaultEpicsApi, type Epic, type EpicsApi } from '../api/epics'
import { activeCardsInColumn, applyMove } from '../lib/boardOps'
import { cleanupCountdownLabel, cleanupDaysRemaining } from '../lib/cleanupCountdown'
import { epicColor } from '../lib/epicMeta'
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

  useEffect(() => setCards(initialCards), [initialCards])

  const epicById = new Map(epics.map((e) => [e.id, e]))
  const columns = [...board.columns].sort((a, b) => a.position - b.position)

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
      {canEdit && columns.length > 0 && (
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 1 }}>
          <Button
            variant="contained"
            size="small"
            startIcon={<AddIcon />}
            onClick={() => setModalColumn({ id: columns[0].id, name: columns[0].name })}
          >
            Neues Item
          </Button>
        </Box>
      )}

      <Stack direction="row" spacing={2} sx={{ overflowX: 'auto', pb: 2, alignItems: 'flex-start' }}>
        {columns.map((column) => {
          const colors = statusColors(column.name)
          const count = activeCardsInColumn(cards, column.id).length
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
              sx={{ width: 280, minWidth: 280, bgcolor: COLUMN_SURFACE_BG, borderRadius: 2, overflow: 'hidden' }}
            >
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 1.5, py: 1, bgcolor: colors.bg, color: colors.text }}>
                <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: colors.dot, flexShrink: 0 }} />
                <Typography variant="subtitle2" sx={{ fontWeight: 700, textTransform: 'uppercase', letterSpacing: '.04em', flexGrow: 1 }}>
                  {column.name}
                </Typography>
                <Typography variant="caption" sx={{ opacity: 0.75 }}>
                  {column.wipLimit != null ? `${count}/${column.wipLimit}` : count}
                </Typography>
                {canEdit && (
                  <Tooltip title="Karte anlegen">
                    <IconButton size="small" aria-label={`Karte in ${column.name} anlegen`}
                      onClick={() => setModalColumn({ id: column.id, name: column.name })} sx={{ color: colors.text }}>
                      <AddIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
              </Box>

              <Stack spacing={1} sx={{ p: 1, minHeight: 24 }}>
                {activeCardsInColumn(cards, column.id).map((card) => {
                  const epic = card.parentId != null ? epicById.get(card.parentId) : undefined
                  const showDone = done && card.movedToDoneAt != null
                  return (
                    <Paper
                      key={card.id}
                      data-testid={`card-${card.id}`}
                      draggable={canEdit}
                      onDragStart={(e) => e.dataTransfer.setData('text/plain', String(card.id))}
                      onClick={() => onCardClick?.(card)}
                      elevation={1}
                      sx={{
                        p: 1.25,
                        borderRadius: 1.5,
                        borderLeft: epic ? `4px solid ${epicColor(epic.id)}` : undefined,
                        cursor: canEdit ? 'grab' : 'pointer',
                        transition: 'box-shadow .15s',
                        '&:hover': { boxShadow: 3 },
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
