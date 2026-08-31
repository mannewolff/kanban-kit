import AddIcon from '@mui/icons-material/Add'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import MoreVertIcon from '@mui/icons-material/MoreVert'
import SortIcon from '@mui/icons-material/Sort'
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
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import type { Board, BoardColumn } from '../api/boards'
import { cardsApi, type Card, type CardsApi } from '../api/cards'
import { ApiError } from '../api/client'
import { columnsApi, type SortDirection } from '../api/columns'
import { epicsApi as defaultEpicsApi, type Epic, type EpicsApi } from '../api/epics'
import type { Member } from '../api/members'
import { activeCardsInColumn, applyMove } from '../lib/boardOps'
import { cleanupCountdownLabel, cleanupDaysRemaining } from '../lib/cleanupCountdown'
import { neighbourColumns } from '../lib/columnMeta'
import { useEditMode } from '../lib/EditModeContext'
import type { Label } from '../api/labels'
import { formatDueDate, isOverdue } from '../lib/dueDate'
import { epicColor, epicShortcode } from '../lib/epicMeta'
import { useKeyboardShortcut } from '../lib/useKeyboardShortcut'
import { statusColors } from '../lib/statusColors'
import { STATUS_EDGE_WIDTH, SURFACE_TINT, theme } from '../theme'
import { edgeSurfaceSx } from './boardSurfaceSx'
import { BulkActionBar } from './BulkActionBar'
import { EpicBadge } from './EpicBadge'
import { NewCardModal, type NewCardInitialValues, type NewItemInput } from './NewCardModal'
import { useSnackbar } from './SnackbarProvider'
import { TransferCardDialog } from './TransferCardDialog'

const isDoneColumn = (name: string) => name.toLowerCase().includes('done')

/** Beschriftung des Sortier-Toggles: benennt die Richtung, die der nächste Klick auslöst. */
const sortByNumberLabel = (columnName: string, next: SortDirection) =>
  `Spalte ${columnName} nach Nummer ${next === 'ASC' ? 'aufsteigend' : 'absteigend'} sortieren`

/**
 * Erfolgsmeldung nach dem Sortieren: benennt die Richtung, in der tatsächlich sortiert wurde.
 * Läuft über den Toast-Stapel, dessen `Alert` als Live-Region vorgelesen wird — ohne die Meldung
 * bliebe der Erfolg für Screenreader unsichtbar (die Kartenreihenfolge ändert sich nur visuell).
 */
const sortedByNumberMessage = (columnName: string, sorted: SortDirection) =>
  `Spalte ${columnName} ${sorted === 'ASC' ? 'aufsteigend' : 'absteigend'} sortiert`

/** Initialen (max. 2 Zeichen) aus einem Anzeigenamen für Assignee-Avatare. */
function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter((p) => p.length > 0)
  // Leerer/nur-Whitespace-Name hat kein Wort -> Platzhalter. Danach ist parts garantiert nicht
  // leer, sodass parts[0] und das letzte Element ohne Optional-Chaining/Default zugreifbar sind.
  // Das Non-null-`!` bei .at(-1) vermeidet den unerreichbaren Optional-Zweig (100 % Branch) und
  // hält zugleich die von Sonar (S7755) bevorzugte .at()-Form.
  if (parts.length === 0) return '?'
  const first = parts[0].charAt(0)
  const last = parts.length > 1 ? parts.at(-1)!.charAt(0) : ''
  return (first + last).toUpperCase()
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
        // Labelfarben sind nutzerdefiniert: auf einem hellen Label waere weisser Text unlesbar.
        // getContrastText waehlt die lesbare Seite (Accessibility vor visueller Praeferenz).
        const bg = l?.color ?? theme.palette.grey[500]
        return (
          <Chip
            key={labelId}
            size="small"
            label={l?.name ?? `#${labelId}`}
            sx={{ bgcolor: bg, color: theme.palette.getContrastText(bg), height: 18, '& .MuiChip-label': { px: 0.75, fontSize: '0.65rem' } }}
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
    | 'create'
    | 'move'
    | 'archive'
    | 'moveToIdeaStorage'
    | 'restore'
    | 'remove'
    | 'bulkArchive'
    | 'bulkTransfer'
    | 'bulkDelete'
  >
  epicsApi?: Pick<EpicsApi, 'create'>
}

/**
 * Spaltenansicht mit Drag & Drop. Verschieben ist optimistisch (Revert bei Fehler). Karten tragen
 * Epic-Badge + farbigen Rand, ein ⋮-Menü (Bearbeiten/Archivieren/Verschieben) und auf Done einen
 * Archiv-Countdown. Angelegt wird über „Neu anlegen“ oder die Taste „+“ — stets in der ersten Spalte.
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
  const { editMode } = useEditMode()
  // Struktur-Affordances (Spalten anlegen/bearbeiten/löschen/umsortieren, Karte bearbeiten) sind
  // nur im Editiermodus sichtbar. Karten-Alltag (anlegen, verschieben, archivieren) bleibt an canEdit.
  const showStructureEdit = canEdit && editMode
  const [cards, setCards] = useState<Card[]>(initialCards)
  const [modalColumn, setModalColumn] = useState<{ id: number; name: string } | null>(null)
  const [duplicateValues, setDuplicateValues] = useState<NewCardInitialValues | null>(null)
  const [menu, setMenu] = useState<{ card: Card; anchor: HTMLElement } | null>(null)
  const [transferCard, setTransferCard] = useState<Card | null>(null)
  // Auswahlmodus für Bulk-Aktionen: blendet Checkboxen ein, Klick selektiert statt zu öffnen.
  const [selectionMode, setSelectionMode] = useState(false)
  const [selectedIds, setSelectedIds] = useState<Set<number>>(() => new Set())
  const [bulkArchiveConfirm, setBulkArchiveConfirm] = useState(false)
  // Zu löschende Karten-IDs; leer = Dialog zu. Ein Zustand für beide Wege (⋮-Menü einer einzelnen
  // Karte und Auswahlmodus), damit die Zusage „wiederherstellbar" nur an einer Stelle steht.
  const [deleteConfirm, setDeleteConfirm] = useState<number[]>([])
  const [bulkTransferOpen, setBulkTransferOpen] = useState(false)
  const notify = useSnackbar()
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

  // Die Taste „+“ tut dasselbe wie der Button „Neu anlegen“: anlegen in der ersten Spalte. Ohne
  // Bearbeitungsrecht oder ohne Spalte gibt es nichts anzulegen, dann bleibt das Kürzel stumm.
  useKeyboardShortcut('+', canEdit && columns.length > 0, () =>
    setModalColumn({ id: columns[0].id, name: columns[0].name }),
  )

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

  // Sortier-Toggle je Spalte: die Richtung, die der nächste Klick auslöst. Nur im Frontend
  // (keine Persistenz gefordert); nicht eingetragene Spalten starten bei ASC. Gewechselt wird
  // erst nach erfolgreichem Aufruf, damit ein fehlgeschlagener Versuch dieselbe Richtung behält.
  //
  // Bewusste Entscheidung: der Zustand spiegelt die eigene Klickhistorie dieser Sitzung, nicht den
  // Serverzustand. Sortiert ein anderer Nutzer dieselbe Spalte (oder wird die Seite neu geladen),
  // weicht die angezeigte nächste Richtung von der zuletzt tatsächlich angewandten ab. Die Sortierung
  // selbst bleibt korrekt — der Aufruf schickt die Richtung mit —, nur die Beschriftung kann dann
  // eine Runde „hinterherhinken“. Für eine echte Spiegelung müsste die Richtung serverseitig je
  // Spalte persistiert werden; das verlangt das Feature nicht.
  const [nextSortDirection, setNextSortDirection] = useState<Record<number, SortDirection>>({})
  // Spalte mit gerade laufendem Sortier-Aufruf: sperrt genau deren Button gegen den zweiten Klick
  // (Doppel-Request, springendes Feedback). Andere Spalten bleiben bedienbar.
  const [sortingColumnId, setSortingColumnId] = useState<number | null>(null)
  const sortColumnByNumber = async (column: BoardColumn) => {
    const direction = nextSortDirection[column.id] ?? 'ASC'
    setSortingColumnId(column.id)
    try {
      await columnsApi.sortByNumber(column.id, direction)
      setNextSortDirection((prev) => ({ ...prev, [column.id]: direction === 'ASC' ? 'DESC' : 'ASC' }))
      notify(sortedByNumberMessage(column.name, direction), 'success')
      onCardsChanged?.()
    } catch {
      notify('Sortieren fehlgeschlagen.', 'error')
    } finally {
      setSortingColumnId(null)
    }
  }

  const [deleteColumn, setDeleteColumn] = useState<BoardColumn | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  // Kein Nullable-Guard nötig: der Dialog (und damit der einzige Aufrufer) existiert nur,
  // solange deleteColumn gesetzt ist — siehe die {deleteColumn && (...)}-Bedingung unten.
  const handleDeleteColumn = async (column: BoardColumn) => {
    setDeleteError(null)
    try {
      await columnsApi.remove(column.id)
      setColumns((cs) => cs.filter((c) => c.id !== column.id))
      setDeleteColumn(null)
      notify('Spalte gelöscht.', 'success')
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
    // Kein Guard auf leeren Namen / ungültiges WIP nötig: Der "Speichern"-Button ist über exakt
    // dieselbe Bedingung deaktiviert (disabled={!columnName.trim() || parsedWip() === undefined}),
    // solange die Eingabe ungültig ist — saveColumn läuft also nur mit gültigem Zustand.
    const name = columnName.trim()
    const wip = parsedWip()
    if (columnDialog === 'new') {
      const created = await columnsApi.create(board.id, name, wip)
      setColumns((cs) => sortColumns([...cs, created]))
      notify('Spalte angelegt.', 'success')
    } else if (columnDialog) {
      const updated = await columnsApi.update(columnDialog.id, name, wip)
      setColumns((cs) => sortColumns(cs.map((c) => (c.id === updated.id ? updated : c))))
      notify('Spalte gespeichert.', 'success')
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
    const created = await api.create(
      board.id,
      columnId,
      input.title,
      input.description,
      input.parentId,
      false,
      {
        dependencies: input.dependencies,
        dueDate: input.dueDate,
        assigneeIds: input.assigneeIds,
        labelIds: input.labelIds,
      },
    )
    setCards((current) => [...current, created])
  }

  const archiveCard = async (card: Card) => {
    await api.archive(card.id)
    onCardsChanged?.()
  }

  // In den Ideen-Speicher: Alltags-Aktion (nicht editiermodus-gegatet). Optimistisch aus der
  // Board-Ansicht nehmen (ideaStored filtert activeCardsInColumn), bei Fehler zurückrollen.
  const moveToIdeaStorageCard = async (card: Card) => {
    const previous = cards
    setCards((current) => current.map((c) => (c.id === card.id ? { ...c, ideaStored: true } : c)))
    try {
      await api.moveToIdeaStorage(card.id)
      onCardsChanged?.()
      notify('In den Ideen-Pool verschoben — unter Ideen zu finden.', 'success')
    } catch {
      setCards(previous)
      notify('In den Ideen-Pool verschieben fehlgeschlagen.', 'error')
    }
  }

  const closeMenu = () => setMenu(null)

  // Ziele der beiden Verschieben-Einträge im ⋮-Menü: je genau eine Spalte weit.
  const { left: moveLeft, right: moveRight } = menu
    ? neighbourColumns(columns, menu.card.columnId)
    : { left: null, right: null }

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
      notify('Archivieren fehlgeschlagen.', 'error')
    }
  }

  // Löschen (eine Karte aus dem ⋮-Menü oder die Auswahl): nach Bestätigung optimistisch aus der
  // Ansicht nehmen, bei Fehler zurückrollen. Beide Wege laufen über bulk-delete — ein Endpunkt,
  // eine Transaktion, eine Rechteprüfung.
  const confirmDelete = async () => {
    const ids = deleteConfirm
    const previous = cards
    setCards(previous.filter((c) => !ids.includes(c.id)))
    setDeleteConfirm([])
    exitSelection()
    try {
      await api.bulkDelete(ids)
      onCardsChanged?.()
    } catch {
      setCards(previous)
      notify('In den Papierkorb verschieben fehlgeschlagen.', 'error')
    }
  }

  // Die Auswahl liegt als Set vor und gibt damit die Klick-Reihenfolge wieder. Für den Transfer
  // zählt aber, was der Nutzer sieht: die API hängt die Karten in Eingabereihenfolge ans Ende der
  // Zielspalte, also muss die Eingabe der Sichtreihenfolge des Quellboards folgen. Sortierschlüssel
  // ist Spaltenposition (columns ist bereits danach sortiert), dann Position in der Spalte — dieselbe
  // zweistufige Regel wie in der Listenansicht, nötig weil die Auswahl mehrere Spalten umfassen kann.
  // Archivieren und Löschen brauchen das nicht; dort ist die Reihenfolge bedeutungslos.
  const selectedIdsInViewOrder = () =>
    columns.flatMap((column) =>
      activeCardsInColumn(cards, column.id)
        .filter((c) => selectedIds.has(c.id))
        .map((c) => c.id),
    )

  // Ordnungsposition der Quellspalte für die Vorbelegung der Zielspalte im Verschieben-Dialog.
  // Eindeutig nur, wenn alle zu verschiebenden Karten in derselben Spalte liegen — sonst null,
  // dann bleibt das Feld im Dialog leer statt zu raten. `columns` ist bereits nach Position sortiert.
  const sourceColumnPosition = (ids: number[]): number | null => {
    const sourceColumnIds = new Set(cards.filter((c) => ids.includes(c.id)).map((c) => c.columnId))
    return sourceColumnIds.size === 1
      ? columns.findIndex((c) => c.id === [...sourceColumnIds][0])
      : null
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
              size="small"
              label="Vorhaben-Filter"
              value={epicFilter ?? ''}
              onChange={(e) => changeEpicFilter(e.target.value === '' ? null : Number(e.target.value))}
              slotProps={{
                htmlInput: { 'aria-label': 'Vorhaben-Filter' },
                select: { native: true },
                inputLabel: { shrink: true },
              }}
              sx={{ minWidth: 200 }}
            >
              <option value="">Alle Vorhaben</option>
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
              Neu anlegen
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
                bgcolor: 'background.paper',
                border: 1,
                borderColor: 'divider',
                borderTop: `${STATUS_EDGE_WIDTH}px solid ${colors.dot}`,
                overflow: 'hidden',
              }}
            >
              <Box
                data-testid={`column-header-${column.id}`}
                draggable={showStructureEdit}
                onDragStart={showStructureEdit ? (e) => { e.stopPropagation(); setColDrag(column.id) } : undefined}
                onDragOver={showStructureEdit ? (e) => { if (colDrag != null && colDrag !== column.id) { e.preventDefault(); e.stopPropagation() } } : undefined}
                onDrop={showStructureEdit ? (e) => {
                  if (colDrag != null) { e.preventDefault(); e.stopPropagation(); void reorderColumn(colDrag, column.id) }
                  setColDrag(null)
                } : undefined}
                onDragEnd={() => setColDrag(null)}
                sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 1.5, py: 1, bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider', cursor: showStructureEdit ? 'grab' : undefined }}
              >
                <Typography variant="caption" sx={{ fontWeight: 700, textTransform: 'uppercase', letterSpacing: '.08em', color: 'text.secondary', flexGrow: 1 }}>
                  {column.name}
                </Typography>
                <Typography variant="caption" sx={{ color: 'text.secondary', bgcolor: SURFACE_TINT, border: 1, borderColor: 'divider', borderRadius: 10, px: 0.75, lineHeight: 1.6 }}>
                  {column.wipLimit != null ? `${count}/${column.wipLimit}` : count}
                </Typography>
                {canEdit && (
                  <Tooltip title={sortByNumberLabel(column.name, nextSortDirection[column.id] ?? 'ASC')}>
                    {/* Kein span-Wrapper um den Button: MUI legt den Tooltip-Titel als aria-label auf
                        sein direktes Kind, ein Wrapper trüge den Namen also doppelt (span + Button).
                        Preis dafür: während des laufenden Aufrufs (disabled) zeigt der Tooltip nicht. */}
                    <IconButton size="small"
                      aria-label={sortByNumberLabel(column.name, nextSortDirection[column.id] ?? 'ASC')}
                      disabled={sortingColumnId === column.id}
                      onClick={() => void sortColumnByNumber(column)} sx={{ color: 'text.secondary' }}>
                      <SortIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
                {showStructureEdit && (
                  <Tooltip title="Spalte bearbeiten">
                    <IconButton size="small" aria-label={`Spalte ${column.name} bearbeiten`}
                      onClick={() => openColumnDialog(column)} sx={{ color: 'text.secondary' }}>
                      <EditOutlinedIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
                {showStructureEdit && (
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
                        bgcolor: selected ? 'action.selected' : 'background.paper',
                        ...edgeSurfaceSx({
                          statusColor: colors.dot,
                          epicColor: epic ? epicColor(epic.id) : undefined,
                          hairlineColor: selected ? 'primary.main' : undefined,
                        }),
                        cursor: grabbable ? 'grab' : 'pointer',
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
                      {doneAt != null && retentionDays > 0 && (
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
        {showStructureEdit && (
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

      {deleteColumn && (
        <Dialog open onClose={() => setDeleteColumn(null)}>
          <DialogTitle>Spalte löschen?</DialogTitle>
          <DialogContent>
            {deleteError && <Alert severity="error" sx={{ mb: 2 }}>{deleteError}</Alert>}
            <DialogContentText>
              Die Spalte „{deleteColumn.name}&ldquo; wird gelöscht.
            </DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDeleteColumn(null)}>Abbrechen</Button>
            <Button color="error" onClick={() => void handleDeleteColumn(deleteColumn)}>
              Löschen
            </Button>
          </DialogActions>
        </Dialog>
      )}

      <Menu anchorEl={menu?.anchor ?? null} open={menu != null} onClose={closeMenu}>
        {menu && !menu.card.archived && [
          // Bearbeiten (Bleistift) nur im Editiermodus; Duplizieren/Archivieren/Verschieben bleiben
          // als Alltags-Aktionen erhalten.
          showStructureEdit ? (
            <MenuItem key="edit" onClick={() => { const c = menu.card; closeMenu(); onEditCard?.(c) }}>
              Bearbeiten
            </MenuItem>
          ) : null,
          <MenuItem
            key="duplicate"
            onClick={() => {
              const c = menu.card
              closeMenu()
              if (columns.length === 0) return
              // Die Kopie ist eine neue Karte und soll den kompletten Prozess durchlaufen —
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
          <MenuItem key="idea-storage" onClick={() => { const c = menu.card; closeMenu(); void moveToIdeaStorageCard(c) }}>
            In den Ideen-Pool
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
          // Genau eine Spalte weit nach links bzw. rechts — nicht ein Eintrag je Zielspalte, sonst
          // wächst das Menü mit der Spaltenzahl. Am Rand fehlt die jeweilige Richtung.
          moveLeft ? (
            <MenuItem
              key="move-left"
              onClick={() => { const c = menu.card; closeMenu(); void moveCard(c.id, moveLeft.id) }}
            >
              Nach links verschieben
            </MenuItem>
          ) : null,
          moveRight ? (
            <MenuItem
              key="move-right"
              onClick={() => { const c = menu.card; closeMenu(); void moveCard(c.id, moveRight.id) }}
            >
              Nach rechts verschieben
            </MenuItem>
          ) : null,
          // Ganz unten und rot abgesetzt: der einzige Eintrag, der Inhalt aus dem Board nimmt —
          // er soll nicht neben „Nach rechts verschieben" versehentlich getroffen werden.
          <MenuItem
            key="delete"
            sx={{ color: 'error.main' }}
            onClick={() => { const c = menu.card; closeMenu(); setDeleteConfirm([c.id]) }}
          >
            Löschen
          </MenuItem>,
        ]}
      </Menu>

      {/* modalColumn ist beim Submit immer gesetzt: NewCardModal ist nur offen, solange
          open={modalColumn !== null} — der Anlegen-Button existiert also nur in diesem Zustand. */}
      <NewCardModal
        open={modalColumn !== null}
        columnName={modalColumn?.name ?? ''}
        epics={epics}
        members={members}
        boardLabels={boardLabels}
        initialValues={duplicateValues ?? undefined}
        onClose={() => { setModalColumn(null); setDuplicateValues(null) }}
        onSubmit={(input) => createItem(modalColumn!.id, input)}
      />

      {transferCard && (
        <TransferCardDialog
          cardIds={[transferCard.id]}
          currentBoardId={board.id}
          currentProjectId={board.projectId}
          sourceColumnPosition={sourceColumnPosition([transferCard.id])}
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
          cardIds={selectedIdsInViewOrder()}
          currentBoardId={board.id}
          currentProjectId={board.projectId}
          sourceColumnPosition={sourceColumnPosition(selectedIdsInViewOrder())}
          platformAdmin={platformAdmin}
          onClose={() => setBulkTransferOpen(false)}
          onTransferred={() => onBulkTransferred(selectedIdsInViewOrder())}
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

      <Dialog open={deleteConfirm.length > 0} onClose={() => setDeleteConfirm([])}>
        <DialogTitle>In den Papierkorb verschieben?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {deleteConfirm.length === 1
              ? 'Die Karte wird in den Papierkorb verschoben. Von dort lässt sie sich wiederherstellen oder endgültig löschen.'
              : `${deleteConfirm.length} Karten werden in den Papierkorb verschoben. Von dort lassen sie sich wiederherstellen oder endgültig löschen.`}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteConfirm([])}>Abbrechen</Button>
          <Button color="error" onClick={() => void confirmDelete()}>
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
          onDelete={() => setDeleteConfirm([...selectedIds])}
          onCancel={exitSelection}
        />
      )}

    </Box>
  )
}
