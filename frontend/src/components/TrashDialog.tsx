import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { cardsApi as defaultCardsApi, type Card } from '../api/cards'

interface Props {
  open: boolean
  boardId: number
  /** Ob endgültiges Löschen (Purge) erlaubt ist — Projekt-Admin/Owner. */
  canPurge: boolean
  onClose: () => void
  /** Wird nach Wiederherstellen/Purge aufgerufen (Board neu laden). */
  onChanged: () => void
  api?: Pick<typeof defaultCardsApi, 'listTrash' | 'restoreDeleted' | 'purge'>
}

/** Papierkorb eines Boards: gelöschte Karten wiederherstellen oder endgültig entfernen. */
export function TrashDialog({
  open,
  boardId,
  canPurge,
  onClose,
  onChanged,
  api = defaultCardsApi,
}: Props) {
  const [cards, setCards] = useState<Card[]>([])

  const reload = () => {
    void api.listTrash(boardId).then(setCards).catch(() => setCards([]))
  }

  useEffect(() => {
    if (open) reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, boardId])

  const restore = async (card: Card) => {
    await api.restoreDeleted(card.id)
    reload()
    onChanged()
  }

  const purge = async (card: Card) => {
    await api.purge(card.id)
    reload()
    onChanged()
  }

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Papierkorb</DialogTitle>
      <DialogContent>
        <Stack spacing={1} sx={{ mt: 1 }}>
          {cards.map((card) => (
            <Stack key={card.id} direction="row" alignItems="center" spacing={1}>
              <Typography variant="body2" sx={{ flexGrow: 1 }}>
                #{card.number} · {card.title}
              </Typography>
              <Button size="small" aria-label={`${card.title} wiederherstellen`} onClick={() => void restore(card)}>
                Wiederherstellen
              </Button>
              {canPurge && (
                <Button
                  size="small"
                  color="error"
                  aria-label={`${card.title} endgültig löschen`}
                  onClick={() => void purge(card)}
                >
                  Endgültig löschen
                </Button>
              )}
            </Stack>
          ))}
          {cards.length === 0 && <Typography color="text.secondary">Der Papierkorb ist leer.</Typography>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Schließen</Button>
      </DialogActions>
    </Dialog>
  )
}
