import AddIcon from '@mui/icons-material/Add'
import Box from '@mui/material/Box'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import type { Board } from '../api/boards'
import { cardsApi, type Card, type CardsApi } from '../api/cards'
import { epicsApi as defaultEpicsApi, type Epic, type EpicsApi } from '../api/epics'
import { activeCardsInColumn, applyMove } from '../lib/boardOps'
import { COLUMN_SURFACE_BG, statusColors } from '../lib/statusColors'
import { EpicBadge } from './EpicBadge'
import { NewCardModal, type NewItemInput } from './NewCardModal'

interface Props {
  board: Board
  initialCards: Card[]
  canEdit: boolean
  epics?: Epic[]
  onCardClick?: (card: Card) => void
  onEpicsChanged?: () => void
  /** Injizierbar für Tests. */
  api?: Pick<CardsApi, 'create' | 'move'>
  epicsApi?: Pick<EpicsApi, 'create'>
}

/**
 * Spaltenansicht mit Drag & Drop. Verschieben ist optimistisch: das UI aktualisiert
 * sofort und rollt bei einem API-Fehler auf den vorigen Stand zurück. Karten werden
 * über einen sichtbaren „+"-Dialog je Spalte angelegt.
 */
export function BoardView({
  board,
  initialCards,
  canEdit,
  epics = [],
  onCardClick,
  onEpicsChanged,
  api = cardsApi,
  epicsApi = defaultEpicsApi,
}: Props) {
  const [cards, setCards] = useState<Card[]>(initialCards)
  const [modalColumn, setModalColumn] = useState<{ id: number; name: string } | null>(null)

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
      setCards(previous) // Revert bei Fehler
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

  return (
    <Stack direction="row" spacing={2} sx={{ overflowX: 'auto', pb: 2, alignItems: 'flex-start' }}>
      {columns.map((column) => {
        const colors = statusColors(column.name)
        const count = activeCardsInColumn(cards, column.id).length
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
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1,
                px: 1.5,
                py: 1,
                bgcolor: colors.bg,
                color: colors.text,
              }}
            >
              <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: colors.dot, flexShrink: 0 }} />
              <Typography
                variant="subtitle2"
                sx={{ fontWeight: 700, textTransform: 'uppercase', letterSpacing: '.04em', flexGrow: 1 }}
              >
                {column.name}
              </Typography>
              <Typography variant="caption" sx={{ opacity: 0.75 }}>
                {column.wipLimit != null ? `${count}/${column.wipLimit}` : count}
              </Typography>
              {canEdit && (
                <Tooltip title="Karte anlegen">
                  <IconButton
                    size="small"
                    aria-label={`Karte in ${column.name} anlegen`}
                    onClick={() => setModalColumn({ id: column.id, name: column.name })}
                    sx={{ color: colors.text }}
                  >
                    <AddIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              )}
            </Box>

            <Stack spacing={1} sx={{ p: 1, minHeight: 24 }}>
              {activeCardsInColumn(cards, column.id).map((card) => (
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
                    cursor: canEdit ? 'grab' : 'pointer',
                    transition: 'box-shadow .15s',
                    '&:hover': { boxShadow: 3 },
                    '&:active': { cursor: canEdit ? 'grabbing' : 'pointer' },
                  }}
                >
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 0.25 }}>
                    <Typography variant="caption" color="text.secondary">
                      #{card.number}
                    </Typography>
                    {card.parentId != null && epicById.has(card.parentId) && (
                      <EpicBadge
                        epicId={card.parentId}
                        title={epicById.get(card.parentId)!.title}
                        shortcode={epicById.get(card.parentId)!.shortcode}
                      />
                    )}
                  </Box>
                  <Typography variant="body2">{card.title}</Typography>
                </Paper>
              ))}
            </Stack>
          </Paper>
        )
      })}

      <NewCardModal
        open={modalColumn !== null}
        columnName={modalColumn?.name ?? ''}
        epics={epics}
        onClose={() => setModalColumn(null)}
        onSubmit={(input) => (modalColumn ? createItem(modalColumn.id, input) : undefined)}
      />
    </Stack>
  )
}
