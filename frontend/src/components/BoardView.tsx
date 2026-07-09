import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import type { Board } from '../api/boards'
import { cardsApi, type Card, type CardsApi } from '../api/cards'
import { activeCardsInColumn, applyMove } from '../lib/boardOps'
import { columnColor } from '../lib/statusColors'

interface Props {
  board: Board
  initialCards: Card[]
  canEdit: boolean
  onCardClick?: (card: Card) => void
  /** Injizierbar für Tests. */
  api?: Pick<CardsApi, 'create' | 'move'>
}

/**
 * Spaltenansicht mit Drag & Drop. Verschieben ist optimistisch: das UI aktualisiert
 * sofort und rollt bei einem API-Fehler auf den vorigen Stand zurück.
 */
export function BoardView({ board, initialCards, canEdit, onCardClick, api = cardsApi }: Props) {
  const [cards, setCards] = useState<Card[]>(initialCards)
  const [drafts, setDrafts] = useState<Record<number, string>>({})

  useEffect(() => setCards(initialCards), [initialCards])

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

  const addCard = async (columnId: number) => {
    const title = (drafts[columnId] ?? '').trim()
    if (!title) {
      return
    }
    const created = await api.create(board.id, columnId, title)
    setCards((current) => [...current, created])
    setDrafts((current) => ({ ...current, [columnId]: '' }))
  }

  return (
    <Stack direction="row" spacing={2} sx={{ overflowX: 'auto', pb: 2, alignItems: 'flex-start' }}>
      {columns.map((column) => (
        <Paper
          key={column.id}
          data-testid={`column-${column.id}`}
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => {
            e.preventDefault()
            const id = Number(e.dataTransfer.getData('text/plain'))
            if (id) {
              void moveCard(id, column.id)
            }
          }}
          sx={{ width: 280, minWidth: 280, p: 1, bgcolor: 'background.paper' }}
        >
          <Box sx={{ borderLeft: `4px solid ${columnColor(column.name)}`, pl: 1, mb: 1 }}>
            <Typography variant="subtitle1" fontWeight={600}>
              {column.name}
              {column.wipLimit != null && (
                <Typography component="span" color="text.secondary" sx={{ ml: 1 }}>
                  ({activeCardsInColumn(cards, column.id).length}/{column.wipLimit})
                </Typography>
              )}
            </Typography>
          </Box>

          <Stack spacing={1}>
            {activeCardsInColumn(cards, column.id).map((card) => (
              <Paper
                key={card.id}
                data-testid={`card-${card.id}`}
                draggable={canEdit}
                onDragStart={(e) => e.dataTransfer.setData('text/plain', String(card.id))}
                onClick={() => onCardClick?.(card)}
                variant="outlined"
                sx={{ p: 1, cursor: canEdit ? 'grab' : 'pointer' }}
              >
                <Typography variant="body2" color="text.secondary">
                  #{card.number}
                </Typography>
                <Typography variant="body1">{card.title}</Typography>
              </Paper>
            ))}
          </Stack>

          {canEdit && (
            <TextField
              size="small"
              fullWidth
              sx={{ mt: 1 }}
              placeholder="+ Karte (Enter)"
              value={drafts[column.id] ?? ''}
              onChange={(e) => setDrafts((current) => ({ ...current, [column.id]: e.target.value }))}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault()
                  void addCard(column.id)
                }
              }}
              inputProps={{ 'aria-label': `Karte in ${column.name} anlegen` }}
            />
          )}
        </Paper>
      ))}
    </Stack>
  )
}
