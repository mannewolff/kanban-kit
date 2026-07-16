import AddIcon from '@mui/icons-material/Add'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import MoreVertIcon from '@mui/icons-material/MoreVert'
import Alert from '@mui/material/Alert'
import Avatar from '@mui/material/Avatar'
import AvatarGroup from '@mui/material/AvatarGroup'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Checkbox from '@mui/material/Checkbox'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Snackbar from '@mui/material/Snackbar'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import type { Board, BoardColumn } from '../api/boards'
import { cardsApi, type Card, type CardsApi } from '../api/cards'
import { ApiError } from '../api/client'
import { columnsApi } from '../api/columns'
import { epicsApi as defaultEpicsApi, type Epic, type EpicsApi } from '../api/epics'
import type { Member } from '../api/members'
import { activeCardsInColumn, applyMove } from '../lib/boardOps'
import { cleanupCountdownLabel, cleanupDaysRemaining } from '../lib/cleanupCountdown'
import type { Label } from '../api/labels'
import { formatDueDate, isOverdue } from '../lib/dueDate'
import { epicColor, epicShortcode } from '../lib/epicMeta'
import { COLUMN_SURFACE_BG, statusColors } from '../lib/statusColors'
import { BulkActionBar } from './BulkActionBar'
import { EpicBadge } from './EpicBadge'
import { NewCardModal, type NewCardInitialValues, type NewItemInput } from './NewCardModal'
import { TransferCardDialog } from './TransferCardDialog'

const isDoneColumn = (name: string) => name.toLowerCase().includes('done')

/** Initialen (max. 2 Zeichen) aus einem Anzeigenamen für Assignee-Avatare. */
function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter((p) => p.length > 0)
  const first = parts[0]?.charAt(0) ?? ''
  const last = parts.length > 1 ? (parts.at(-1)?.charAt(0) ?? '') : ''
  return (first + last).toUpperCase() || '?'
}

/**
 * Farbige Label-Chips einer Karte. Als eigene Komponente ausgelagert, damit die
 * `find`-Suche nicht innerhalb der tief verschachtelten Spalten-/Karten-`map` steht.
 */
function CardLabels({ labelIds, boardLabels, cardTitle }: Readonly<{ labelIds: number[]; boardLabels: Label[]; cardTitle: string }>) {
  if (labelIds.length === 0) return null
  return (
    <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', mb: 0.5 }} aria-label={`Labels ${cardTitle}`}>
      {labelIds.map((labelId) => {
        const l = boardLabels.find((b) => b.id === labelId)
        return (
          <Chip
            key={labelId}
            size="small"
            label={l?.name ?? `#${labelId}`}
            sx={{ bgcolor: l?.color ?? 'grey.500', color: '#fff', height: 18, '& .MuiChip-label': { px: 0.75, fontSize: '0.65rem' } }}
          />
        )
      })}
    </Stack>
  )
}

/**
 * Zuständigen-Avatare einer Karte. Analog zu {@link CardLabels} ausgelagert, um die
 * `find`-Suche aus der verschachtelten Karten-`map` zu holen.
 */
function CardAssignees({ assigneeIds, members, cardTitle }: Readonly<{ assigneeIds: number[]; members: Member[]; cardTitle: string }>) {
  if (assigneeIds.length === 0) return null
  return (
    <Stack direction="row" justifyContent="flex-end" sx={{ mt: 0.5 }}>
      <AvatarGroup
        max={4}
        aria-label={`Zuständige ${cardTitle}`}
        sx={{ '& .MuiAvatar-root': { width: 24, height: 24, fontSize: '0.7rem' } }}
      >
        {assigneeIds.map((uid) => {
          const name = members.find((m) => m.userId === uid)?.displayName ?? `#${uid}`
          return (
            <Avatar key={uid} title={name}>
              {initials(name)}
            </Avatar>
          )
        })}
      </AvatarGroup>
    </Stack>
  )
}

interface Props {
  board: Board
  initialCards: Card[]
  canEdit: boolean
  epics?: Epic[]
  retentionDays?: number
  /** Projektmitglieder für die Zuständigen-Avatare auf den Karten. */
  members?: Member[]
  /** Board-Labels für die farbigen Label-Chips auf den Karten. */
  boardLabels?: Label[]
  onCardClick?: (card: Card) => void
  onEditCard?: (card: Card) => void
  onEpicsChanged?: () => void
  onCardsChanged?: () => void
  /** Ob der Nutzer Karten board-/projektübergreifend verschieben darf (OWNER/Plattform-Admin). */
  canTransfer?: boolean
  /** Ob der Nutzer Plattform-Admin ist (darf in alle Projekte verschieben). */
  platformAdmin?: boolean
  /** Injizierbar für Tests. */
  api?: Pick<
    CardsApi,
    'create' | 'move' | 'archive' | 'restore' | 'remove' | 'bulkArchive' | 'bulkTransfer' | 'bulkDelete'
  >
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
  members = [],
  boardLabels = [],
  onCardClick,
  onEditCard,
  onEpicsChanged,
  onCardsChanged,
  canTransfer = false,
  platformAdmin = false,
  api = cardsApi,
  epicsApi = defaultEpicsApi,
}: Readonly<Props>) {
  const [cards, setCards] = useState<Card[]>(initialCards)
  const [modalColumn, setModalColumn] = useState<{ id: number; name: string } | null>(null)
  const [duplicateValues, setDuplicateValues] = useState<NewCardInitialValues | null>(null)
  const [menu, setMenu] = useState<{ card: Card; anchor: HTMLElement } | null>(null)
  const [transferCard, setTransferCard] = useState<Card | null>(null)
  // Auswahlmodus für Bulk-Aktionen: blendet Checkboxen ein, Klick selektiert statt zu öffnen.
  const [selectionMode, setSelectionMode] = useState(false)
  const [selectedIds, setSelectedIds] = useState<Set<number>>(() => new Set())
  const [bulkArchiveConfirm, setBulkArchiveConfirm] = useState(false)
  const [bulkDeleteConfirm, setBulkDeleteConfirm] = useState(false)
  const [bulkTransferOpen, setBulkTransferOpen] = useState(false)
  const [snackbar, setSnackbar] = useState<string | null>(null)
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
  const sortColumns = (cols: BoardColumn[]) => [...cols].sort((a, b) => a.position - b.position)
  const [columns, setColumns] = useState<BoardColumn[]>(() => sortColumns(board.columns))
  useEffect(() => setColumns(sortColumns(board.columns)), [board.columns])

  // Spalten-Dialog: 'new' = anlegen, ein Column-Objekt = bearbeiten.
  const [columnDialog, setColumnDialog] = useState<'new' | BoardColumn | null>(null)
  const [columnName, setColumnName] = useState('')
  const [columnWip, setColumnWip] = useState('')

  const openColumnDialog = (target: 'new' | BoardColumn) => {
    setColumnDialog(target)
    setColumnName(target === 'new' ? '' : target.name)
    setColumnWip(target === 'new' || target.wipLimit == null ? '' : String(target.wipLimit))
  }
  const closeColumnDialog = () => setColumnDialog(null)

  // Spalten-Reihenfolge per Drag & Drop (getrennt vom Karten-Drag, das dataTransfer nutzt).
  const [colDrag, setColDrag] = useState<number | null>(null)
  const reorderColumn = async (fromId: number, toId: number) => {
    if (fromId === toId) {
      return
    }
    const previous = columns
    const fromIdx = columns.findIndex((c) => c.id === fromId)
    const toIdx = columns.findIndex((c) => c.id === toId)
    if (fromIdx < 0 || toIdx < 0) {
      return
    }
    const next = [...columns]
    const [moved] = next.splice(fromIdx, 1)
    next.splice(toIdx, 0, moved)
    setColumns(next) // optimistisch
    try {
      const updated = await columnsApi.reorder(board.id, next.map((c) => c.id))
      setColumns(sortColumns(updated))
    } catch {
      setColumns(previous)
    }
  }

  const [deleteColumn, setDeleteColumn] = useState<BoardColumn | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const handleDeleteColumn = async () => {
    if (!deleteColumn) {
      return
    }
    setDeleteError(null)
    try {
      await columnsApi.remove(deleteColumn.id)
      setColumns((cs) => cs.filter((c) => c.id !== deleteColumn.id))
      setDeleteColumn(null)
    } catch (e) {
      setDeleteError(
        e instanceof ApiError && e.status === 409
          ? 'Spalte enthält noch Karten und kann nicht gelöscht werden.'
          : 'Löschen fehlgeschlagen.',
      )
    }
  }

  const parsedWip = (): number | null | undefined => {
    const raw = columnWip.trim()
    if (raw === '') return null
    const n = Number(raw)
    return Number.isInteger(n) && n > 0 ? n : undefined // undefined = ungültig
  }
  const saveColumn = async () => {
    const name = columnName.trim()
    const wip = parsedWip()
    if (!name || wip === undefined) {
      return
    }
    if (columnDialog === 'new') {
      const created = await columnsApi.create(board.id, name, wip)
      setColumns((cs) => sortColumns([...cs, created]))
    } else if (columnDialog) {
      const updated = await columnsApi.update(columnDialog.id, name, wip)
      setColumns((cs) => sortColumns(cs.map((c) => (c.id === updated.id ? updated : c))))
    }
    closeColumnDialog()
  }
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

  const exitSelection = () => {
    setSelectionMode(false)
    setSelectedIds(new Set())
  }
  const toggleSelectionMode = () => (selectionMode ? exitSelection() : setSelectionMode(true))
  const toggleSelect = (cardId: number) =>
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(cardId)) next.delete(cardId)
      else next.add(cardId)
      return next
    })
  // Bulk-Archivieren: nach Bestätigung optimistisch aus der Ansicht nehmen, bei Fehler zurückrollen.
  const confirmBulkArchive = async () => {
    const ids = [...selectedIds]
    const previous = cards
    setCards(previous.filter((c) => !selectedIds.has(c.id)))
    setBulkArchiveConfirm(false)
    exitSelection()
    try {
      await api.bulkArchive(ids)
      onCardsChanged?.()
    } catch {
      setCards(previous)
      setSnackbar('Archivieren fehlgeschlagen.')
    }
  }

  // Bulk-Löschen: nach Bestätigung optimistisch aus der Ansicht nehmen, bei Fehler zurückrollen.
  const confirmBulkDelete = async () => {
    const ids = [...selectedIds]
    const previous = cards
    setCards(previous.filter((c) => !selectedIds.has(c.id)))
    setBulkDeleteConfirm(false)
    exitSelection()
    try {
      await api.bulkDelete(ids)
      onCardsChanged?.()
    } catch {
      setCards(previous)
      setSnackbar('In den Papierkorb verschieben fehlgeschlagen.')
    }
  }

  // Bulk-Verschieben: der Dialog erledigt den Transfer; danach die Karten aus der Ansicht nehmen.
  const onBulkTransferred = (movedIds: number[]) => {
    const moved = new Set(movedIds)
    setCards((current) => current.filter((c) => !moved.has(c.id)))
    setBulkTransferOpen(false)
    exitSelection()
    onCardsChanged?.()
  }

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
              slotProps={{ htmlInput: { 'aria-label': 'Epic-Filter' } }}
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
            <Button size="small" onClick={toggleSelectionMode}>
              {selectionMode ? 'Auswahl beenden' : 'Auswählen'}
            </Button>
          )}
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
              <Box
                data-testid={`column-header-${column.id}`}
                draggable={canEdit}
                onDragStart={canEdit ? (e) => { e.stopPropagation(); setColDrag(column.id) } : undefined}
                onDragOver={canEdit ? (e) => { if (colDrag != null && colDrag !== column.id) { e.preventDefault(); e.stopPropagation() } } : undefined}
                onDrop={canEdit ? (e) => {
                  if (colDrag != null) { e.preventDefault(); e.stopPropagation(); void reorderColumn(colDrag, column.id) }
                  setColDrag(null)
                } : undefined}
                onDragEnd={() => setColDrag(null)}
                sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 1.5, py: 1, bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider', cursor: canEdit ? 'grab' : undefined }}
              >
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
                {canEdit && (
                  <Tooltip title="Spalte bearbeiten">
                    <IconButton size="small" aria-label={`Spalte ${column.name} bearbeiten`}
                      onClick={() => openColumnDialog(column)} sx={{ color: 'text.secondary' }}>
                      <EditOutlinedIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
                {canEdit && (
                  <Tooltip title="Spalte löschen">
                    <IconButton size="small" aria-label={`Spalte ${column.name} löschen`}
                      onClick={() => { setDeleteError(null); setDeleteColumn(column) }} sx={{ color: 'text.secondary' }}>
                      <DeleteOutlineIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
              </Box>

              <Stack spacing={1} sx={{ p: 1, flex: 1 }}>
                {activeCardsInColumn(filteredCards, column.id).map((card) => {
                  const epic = card.parentId != null ? epicById.get(card.parentId) : undefined
                  const doneAt = done ? card.movedToDoneAt : null
                  const overdue = isOverdue(card.dueDate, done)
                  const selected = selectedIds.has(card.id)
                  // Nur greifbar (Drag-Cursor), wenn bearbeitbar und nicht im Auswahlmodus —
                  // ersetzt zwei verschachtelte Cursor-Ternaries (S3358).
                  const grabbable = canEdit && !selectionMode
                  return (
                    <Paper
                      key={card.id}
                      data-testid={`card-${card.id}`}
                      draggable={canEdit && !selectionMode}
                      onDragStart={(e) => e.dataTransfer.setData('text/plain', String(card.id))}
                      onClick={() => (selectionMode ? toggleSelect(card.id) : onCardClick?.(card))}
                      elevation={0}
                      sx={{
                        p: 1.25,
                        borderRadius: 1.5,
                        bgcolor: selected ? 'action.selected' : 'background.paper',
                        border: 1,
                        borderColor: selected ? 'primary.main' : 'divider',
                        borderLeft: epic ? `4px solid ${epicColor(epic.id)}` : undefined,
                        cursor: grabbable ? 'grab' : 'pointer',
                        transition: 'border-color .15s',
                        '&:hover': { borderColor: 'primary.light' },
                        '&:active': { cursor: grabbable ? 'grabbing' : 'pointer' },
                      }}
                    >
                      {epic && <EpicBadge epicId={epic.id} title={epic.title} shortcode={epic.shortcode} sx={{ mb: 0.5 }} />}
                      <CardLabels labelIds={card.labels} boardLabels={boardLabels} cardTitle={card.title} />
                      <Stack direction="row" alignItems="flex-start" spacing={0.5}>
                        {selectionMode && (
                          <Checkbox
                            size="small"
                            checked={selected}
                            onChange={() => toggleSelect(card.id)}
                            onClick={(e) => e.stopPropagation()}
                            slotProps={{ input: { 'aria-label': `Karte ${card.title} auswählen` } }}
                            sx={{ p: 0, mt: 0.25 }}
                          />
                        )}
                        <Typography variant="body2" sx={{ flex: 1, minWidth: 0 }}>
                          <Box component="span" sx={{ color: 'text.secondary' }}>#{card.number} – </Box>
                          <Box component="span" sx={{ fontWeight: 600 }}>{card.title}</Box>
                        </Typography>
                        {canEdit && !selectionMode && (
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
                      {doneAt != null && (
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                          {cleanupCountdownLabel(cleanupDaysRemaining(doneAt, retentionDays))}
                        </Typography>
                      )}
                      {card.dueDate != null && (
                        <Typography
                          variant="caption"
                          aria-label={`Fällig ${card.title}`}
                          color={overdue ? 'error' : 'text.secondary'}
                          sx={{ display: 'block', mt: 0.5, fontWeight: overdue ? 600 : 400 }}
                        >
                          📅 {formatDueDate(card.dueDate)}
                        </Typography>
                      )}
                      <CardAssignees assigneeIds={card.assignees} members={members} cardTitle={card.title} />
                    </Paper>
                  )
                })}
              </Stack>
            </Paper>
          )
        })}
        {canEdit && (
          <Box sx={{ flexShrink: 0, alignSelf: 'flex-start', pt: 0.5 }}>
            <Button size="small" startIcon={<AddIcon />} onClick={() => openColumnDialog('new')}>
              Spalte
            </Button>
          </Box>
        )}
      </Stack>

      <Dialog open={columnDialog !== null} onClose={closeColumnDialog}>
        <DialogTitle>{columnDialog === 'new' ? 'Neue Spalte' : 'Spalte bearbeiten'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              autoFocus
              label="Name"
              value={columnName}
              onChange={(e) => setColumnName(e.target.value)}
              slotProps={{ htmlInput: { maxLength: 120, 'aria-label': 'Spaltenname' } }}
            />
            <TextField
              label="WIP-Limit (optional)"
              type="number"
              value={columnWip}
              onChange={(e) => setColumnWip(e.target.value)}
              slotProps={{ htmlInput: { min: 1, 'aria-label': 'WIP-Limit' } }}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeColumnDialog}>Abbrechen</Button>
          <Button
            variant="contained"
            disabled={!columnName.trim() || parsedWip() === undefined}
            onClick={() => void saveColumn()}
          >
            Speichern
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={deleteColumn !== null} onClose={() => setDeleteColumn(null)}>
        <DialogTitle>Spalte löschen?</DialogTitle>
        <DialogContent>
          {deleteError && <Alert severity="error" sx={{ mb: 2 }}>{deleteError}</Alert>}
          <DialogContentText>
            Die Spalte „{deleteColumn?.name}&ldquo; wird gelöscht.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteColumn(null)}>Abbrechen</Button>
          <Button color="error" onClick={() => void handleDeleteColumn()}>
            Löschen
          </Button>
        </DialogActions>
      </Dialog>

      <Menu anchorEl={menu?.anchor ?? null} open={menu != null} onClose={closeMenu}>
        {menu && !menu.card.archived && [
          <MenuItem key="edit" onClick={() => { const c = menu.card; closeMenu(); onEditCard?.(c) }}>
            Bearbeiten
          </MenuItem>,
          <MenuItem
            key="duplicate"
            onClick={() => {
              const c = menu.card
              closeMenu()
              if (columns.length === 0) return
              // Die Kopie ist ein neues Item und soll den kompletten Prozess durchlaufen —
              // deshalb immer in die erste Spalte ("Backlog"), nicht in die Spalte der
              // Quellkarte (analog zum board-weiten "+"-Button, der ebenfalls columns[0] nutzt).
              setDuplicateValues({ title: c.title, description: c.description ?? '', parentId: c.parentId })
              setModalColumn({ id: columns[0].id, name: columns[0].name })
            }}
          >
            Duplizieren
          </MenuItem>,
          <MenuItem key="archive" onClick={() => { const c = menu.card; closeMenu(); void archiveCard(c) }}>
            Archivieren
          </MenuItem>,
          ...(canTransfer
            ? [
                <MenuItem
                  key="transfer"
                  onClick={() => { const c = menu.card; closeMenu(); setTransferCard(c) }}
                >
                  Auf anderes Board verschieben…
                </MenuItem>,
              ]
            : []),
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
        initialValues={duplicateValues ?? undefined}
        onClose={() => { setModalColumn(null); setDuplicateValues(null) }}
        onSubmit={(input) => (modalColumn ? createItem(modalColumn.id, input) : undefined)}
      />

      {transferCard && (
        <TransferCardDialog
          cardIds={[transferCard.id]}
          currentBoardId={board.id}
          platformAdmin={platformAdmin}
          onClose={() => setTransferCard(null)}
          onTransferred={() => {
            const c = transferCard
            setTransferCard(null)
            setCards((current) => current.filter((x) => x.id !== c.id))
            onCardsChanged?.()
          }}
        />
      )}

      {bulkTransferOpen && (
        <TransferCardDialog
          cardIds={[...selectedIds]}
          currentBoardId={board.id}
          platformAdmin={platformAdmin}
          onClose={() => setBulkTransferOpen(false)}
          onTransferred={() => onBulkTransferred([...selectedIds])}
        />
      )}

      <Dialog open={bulkArchiveConfirm} onClose={() => setBulkArchiveConfirm(false)}>
        <DialogTitle>Karten archivieren?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {selectedIds.size === 1
              ? 'Die ausgewählte Karte wird archiviert.'
              : `${selectedIds.size} Karten werden archiviert.`}{' '}
            Sie verschwinden aus dem Board, bleiben aber erhalten und lassen sich einzeln
            wiederherstellen.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBulkArchiveConfirm(false)}>Abbrechen</Button>
          <Button color="error" onClick={() => void confirmBulkArchive()}>
            Archivieren
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={bulkDeleteConfirm} onClose={() => setBulkDeleteConfirm(false)}>
        <DialogTitle>In den Papierkorb verschieben?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {selectedIds.size === 1
              ? 'Die ausgewählte Karte wird in den Papierkorb verschoben.'
              : `${selectedIds.size} Karten werden in den Papierkorb verschoben.`}{' '}
            Von dort lassen sie sich wiederherstellen oder endgültig löschen.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBulkDeleteConfirm(false)}>Abbrechen</Button>
          <Button color="error" onClick={() => void confirmBulkDelete()}>
            In den Papierkorb
          </Button>
        </DialogActions>
      </Dialog>

      {selectionMode && selectedIds.size > 0 && (
        <BulkActionBar
          count={selectedIds.size}
          canMove={canTransfer}
          onArchive={() => setBulkArchiveConfirm(true)}
          onMove={() => setBulkTransferOpen(true)}
          onDelete={() => setBulkDeleteConfirm(true)}
          onCancel={exitSelection}
        />
      )}

      <Snackbar
        open={snackbar !== null}
        autoHideDuration={5000}
        onClose={() => setSnackbar(null)}
        message={snackbar ?? ''}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      />
    </Box>
  )
}
