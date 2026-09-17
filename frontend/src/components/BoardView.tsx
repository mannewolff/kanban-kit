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
import ButtonBase from '@mui/material/ButtonBase'
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
import { useEffect, useRef, useState } from 'react'
import type { Board, BoardColumn } from '../api/boards'
import { cardsApi, type Card, type CardsApi, type LabelAction } from '../api/cards'
import { ApiError, apiErrorMessage } from '../api/client'
import { columnsApi, type SortDirection } from '../api/columns'
import { epicsApi as defaultEpicsApi, type Epic, type EpicsApi } from '../api/epics'
import type { Member } from '../api/members'
import { activeCardsInColumn, applyMove } from '../lib/boardOps'
import { epicOfCard } from '../lib/cardEpic'
import { cleanupCountdownLabel, cleanupDaysRemaining } from '../lib/cleanupCountdown'
import { neighbourColumns } from '../lib/columnMeta'
import { useEditMode } from '../lib/EditModeContext'
import type { Label } from '../api/labels'
import { formatDueDate, isOverdue } from '../lib/dueDate'
import { epicShortcode } from '../lib/epicMeta'
import { selectableEpics } from '../lib/epicTiles'
import { hiddenCardNumbers } from '../lib/hiddenCards'
import { useKeyboardShortcut } from '../lib/useKeyboardShortcut'
import { statusColors } from '../lib/statusColors'
import {
  KUPFER,
  LED_RING,
  MELDER,
  NUT,
  NUTZER_MAL_SX,
  PANEL_RADIUS,
  PLATTE,
  PLATTE_FUSS,
  PLATTE_HOCH,
  RAND,
  SCHATTEN_NUTE,
  SCHATTEN_PLATTE,
  SCHATTEN_TASTE,
  SCHRIFT_ANZEIGE,
  TEXT_SCHWACH,
  ZAHL,
} from '../theme'
import { labelChipSx } from './labelChipSx'
import { ablageflaecheSx, karteSx } from './boardSurfaceSx'
import { BulkActionBar, type LabelOption, type LabelZustand } from './BulkActionBar'
import { EpicBadge } from './EpicBadge'
import { NewCardModal, type NewCardInitialValues, type NewItemInput } from './NewCardModal'
import { useSnackbar } from './SnackbarProvider'
import { TransferCardDialog } from './TransferCardDialog'

const isDoneColumn = (name: string) => name.toLowerCase().includes('done')

/** Wie weit ein Label in der Auswahl vertreten ist — `alle` nur, wenn jede gewählte Karte es trägt. */
const labelZustand = (treffer: number, gesamt: number): LabelZustand => {
  if (treffer === 0) return 'keine'
  return treffer === gesamt ? 'alle' : 'einige'
}

/**
 * Grund, warum die Massenaktion „Labels" gesperrt ist; `null` heißt bedienbar. Ein Vorhaben in der
 * Auswahl lässt den Server den ganzen Batch ablehnen — die gesperrte Taste sagt das vorher, statt
 * die Auswahl in einen Fehler laufen zu lassen.
 */
const labelSperrgrund = (gewaehlt: Card[], boardLabels: Label[]): string | null => {
  if (gewaehlt.some((c) => c.type === 'EPIC')) return 'Vorhaben tragen keine Labels'
  if (boardLabels.length === 0) return 'Das Board hat keine Labels'
  return null
}

type KartenFilter = 'alle' | 'meine' | 'ueberfaellig'

/** Zustand eines Segments der Belastungsskala. */
const segmentZustand = (belegt: boolean, grenze: boolean): string => {
  if (grenze) return 'grenze'
  return belegt ? 'belegt' : 'frei'
}
type Dichte = 'normal' | 'kompakt'

/** Wahlschalter der Werkzeugleiste (Entwurf `.chip-gruppe`, `.chip`, Z. 807–826). */
function ChipGruppe<T extends string>({
  label,
  wert,
  optionen,
  onChange,
}: Readonly<{ label: string; wert: T; optionen: ReadonlyArray<{ wert: T; text: string; zahl?: number }>; onChange: (wert: T) => void }>) {
  return (
    <Box
      role="group"
      aria-label={label}
      sx={{ display: 'inline-flex', gap: '3px', p: '3px', bgcolor: NUT, border: `1px solid ${RAND}`, borderRadius: '9px', boxShadow: SCHATTEN_NUTE }}
    >
      {optionen.map((o) => {
        const gewaehlt = o.wert === wert
        return (
          <ButtonBase
            key={o.wert}
            aria-pressed={gewaehlt}
            onClick={() => onChange(o.wert)}
            sx={{
              fontSize: 11.5,
              fontWeight: 500,
              color: gewaehlt ? 'text.primary' : 'text.secondary',
              border: `1px solid ${gewaehlt ? RAND : 'transparent'}`,
              borderRadius: '6px',
              px: '10px',
              py: '4px',
              gap: '6px',
              ...(gewaehlt && { background: `linear-gradient(180deg, ${PLATTE_HOCH}, ${PLATTE})`, boxShadow: SCHATTEN_TASTE }),
            }}
          >
            {o.text}
            {o.zahl !== undefined && o.zahl > 0 && (
              <Box
                component="span"
                sx={{ ...ZAHL, fontSize: 10, px: '4px', borderRadius: '4px', color: MELDER.zinnob, bgcolor: `color-mix(in srgb, ${MELDER.zinnob} 18%, transparent)` }}
              >
                {o.zahl}
              </Box>
            )}
          </ButtonBase>
        )
      })}
    </Box>
  )
}

/**
 * Default der `hiddenEpics`-Prop. Steht als Konstante da und nicht als `new Set()` in der
 * Parameterliste: Ein bei jedem Render frisch erzeugtes Set wäre eine neue Identität und triebe
 * jede abgeleitete Berechnung unnötig neu an.
 */
const LEERE_AUSBLENDUNG: ReadonlySet<number> = new Set<number>()

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

/**
 * Zugänglicher Name der Spaltenmarke: Zahl, Objekt und Rückweg in einem. Achsenneutral formuliert,
 * denn gezählt wird die Vereinigung aus ausgeblendeten Vorhaben und Vorhaben-Filter (Plan #620,
 * E4/E9) — eine Spalte meldet eine Zahl, nicht zwei Ursachen.
 */
const hiddenBadgeLabel = (count: number) =>
  `${count} ${count === 1 ? 'Karte' : 'Karten'} ausgeblendet, einblenden`

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
        return (
          <Chip
            key={labelId}
            size="small"
            label={l?.name ?? `#${labelId}`}
            sx={{ ...labelChipSx(l?.color), height: 18, '& .MuiChip-label': { px: 0.75, fontSize: '0.65rem' } }}
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
    <Stack direction="row" justifyContent="flex-end" sx={{ ml: 'auto', flex: 'none' }}>
      {/* Kürzel wie im Entwurf (`.kuerzel`, Z. 845–853): kleine dunkle Male. */}
      <AvatarGroup
        max={4}
        aria-label={`Zuständige ${cardTitle}`}
        sx={{
          '& .MuiAvatar-root': {
            ...NUTZER_MAL_SX,
            width: 19,
            height: 19,
            fontSize: 9,
            fontWeight: 600,
            letterSpacing: '.02em',
            border: 'none',
            ml: '-4px',
          },
        }}
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
  /**
   * Auf diesem Board ausgeblendete Vorhaben (`Epic.id`). Der Zustand liegt bei `BoardPage`, weil
   * ihn dort auch das Detail-Modal braucht (Plan #717, A3) — das Board kennt weder den
   * Speicherort noch den Schlüssel.
   */
  hiddenEpics?: ReadonlySet<number>
  /**
   * Meldet die neue Menge; der Aufrufer setzt seinen Zustand und schreibt sie fort. Ein roher
   * State-Setter reichte nicht: Das Fortschreiben gehört zur Änderung, nicht daneben.
   */
  onHiddenEpicsChange?: (next: ReadonlySet<number>) => void
  retentionDays?: number
  /** Projektmitglieder für die Zuständigen-Avatare auf den Karten. */
  members?: Member[]
  /** Board-Labels für die farbigen Label-Chips auf den Karten. */
  boardLabels?: Label[]
  onCardClick?: (card: Card) => void
  onEditCard?: (card: Card) => void
  /**
   * Gesetzt: Das Kürzel auf der Karte wird ein Bedienelement, das zu seinem Vorhaben führt.
   * Fehlt sie: reine Anzeige. Wohin der Sprung führt, entscheidet die Seite (Issue #688).
   */
  onEpicOpen?: (epic: Epic) => void
  onEpicsChanged?: () => void
  onCardsChanged?: () => void
  /** Ob der Nutzer Karten board-/projektübergreifend verschieben darf (OWNER/Plattform-Admin). */
  canTransfer?: boolean
  /** Ob der Nutzer Plattform-Admin ist (darf in alle Projekte verschieben). */
  platformAdmin?: boolean
  /** Der angemeldete Nutzer — Grundlage des Filters „Meine" (#980). Ohne ihn entfällt der Filter. */
  currentUserId?: number | null
  /** Injizierbar für Tests. */
  api?: Pick<
    CardsApi,
    | 'create'
    | 'get'
    | 'move'
    | 'archive'
    | 'moveToIdeaStorage'
    | 'restore'
    | 'remove'
    | 'bulkArchive'
    | 'bulkTransfer'
    | 'bulkDelete'
    | 'bulkLabels'
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
  hiddenEpics = LEERE_AUSBLENDUNG,
  onHiddenEpicsChange = () => {},
  retentionDays = 30,
  members = [],
  boardLabels = [],
  onCardClick,
  onEditCard,
  onEpicOpen,
  onEpicsChanged,
  onCardsChanged,
  canTransfer = false,
  platformAdmin = false,
  currentUserId = null,
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
  // Läuft gerade ein bulk-labels-Aufruf? Sperrt den zweiten Klick, solange die Antwort aussteht.
  const [labelBusy, setLabelBusy] = useState(false)
  const [kartenFilter, setKartenFilter] = useState<KartenFilter>('alle')
  const [dichte, setDichte] = useState<Dichte>('normal')
  const notify = useSnackbar()
  const [epicFilter, setEpicFilter] = useState<number | null>(() => {
    try {
      const raw = localStorage.getItem(`manban.boardEpicFilter.${board.id}`)
      return raw ? Number(raw) : null
    } catch {
      return null
    }
  })

  // Ein per localStorage gemerkter Filter kann auf ein Vorhaben zeigen, das seither ausgeblendet
  // wurde — die beiden Achsen kennen sich sonst nicht. Ohne diesen Abgleich bliebe das Board nach
  // jedem Laden leer, ohne dass der Dropdown das gewählte Vorhaben überhaupt noch als Option führt.
  useEffect(() => {
    if (epicFilter === null || !hiddenEpics.has(epicFilter)) return
    setEpicFilter(null)
    try {
      localStorage.removeItem(`manban.boardEpicFilter.${board.id}`)
    } catch {
      // localStorage nicht verfügbar — der State-Reset wirkt trotzdem.
    }
  }, [epicFilter, hiddenEpics, board.id])

  useEffect(() => setCards(initialCards), [initialCards])

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
  // Ziehen einer Karte (AK 7, AK 8, Plan #932 E15): welche Karte bewegt wird und über welcher Spalte
  // sie gerade steht. Beides dient allein der Darstellung; das Verschieben selbst trägt weiterhin
  // die `dataTransfer`-Nutzlast.
  const [dragCardId, setDragCardId] = useState<number | null>(null)
  const [ablageSpalteId, setAblageSpalteId] = useState<number | null>(null)
  const zugTakt = useRef<ReturnType<typeof setTimeout>>(undefined)
  useEffect(() => () => clearTimeout(zugTakt.current), [])

  const zugBeginnen = (e: React.DragEvent, cardId: number) => {
    e.dataTransfer.setData('text/plain', String(cardId))
    // Einen Takt später: Der Browser nimmt das Ziehbild erst nach diesem Ereignis auf. Ein sofortiger
    // Zustandswechsel zeigte dort schon den Platzhalter statt der Karte.
    clearTimeout(zugTakt.current)
    zugTakt.current = setTimeout(() => setDragCardId(cardId), 0)
  }

  const zugBeenden = () => {
    clearTimeout(zugTakt.current)
    setDragCardId(null)
    setAblageSpalteId(null)
  }

  const herkunftsSpalteId = cards.find((c) => c.id === dragCardId)?.columnId
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
    } catch (e) {
      // Der Rollback allein bliebe stumm: die zurückspringende Reihenfolge erklärt nicht, warum.
      setColumns(previous)
      notify(apiErrorMessage(e, 'Spalten umsortieren fehlgeschlagen.'), 'error')
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
    } catch (e) {
      notify(apiErrorMessage(e, 'Sortieren fehlgeschlagen.'), 'error')
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
    try {
      if (columnDialog === 'new') {
        const created = await columnsApi.create(board.id, name, wip)
        setColumns((cs) => sortColumns([...cs, created]))
        notify('Spalte angelegt.', 'success')
      } else if (columnDialog) {
        const updated = await columnsApi.update(columnDialog.id, name, wip)
        setColumns((cs) => sortColumns(cs.map((c) => (c.id === updated.id ? updated : c))))
        notify('Spalte gespeichert.', 'success')
      }
    } catch (e) {
      // Nur der Erfolg schließt: Bei einem Fehlschlag bliebe die getippte Eingabe sonst verloren
      // und der Nutzer müsste Name und WIP-Limit erneut eintippen, um es nochmal zu versuchen.
      notify(apiErrorMessage(e, 'Spalte speichern fehlgeschlagen.'), 'error')
      return
    }
    closeColumnDialog()
  }
  // Verdeckte Karten beider Achsen — ausgeblendete Vorhaben und Vorhaben-Filter — als eine Menge.
  // Nur Darstellung: Move/Anlegen arbeiten weiter auf dem vollen Bestand (`cards`).
  const hiddenNumbers = hiddenCardNumbers(cards, epics, hiddenEpics, epicFilter)
  const filteredCards = cards.filter((c) => !hiddenNumbers.has(c.number))
  // Filter der Werkzeugleiste (#980, Entwurf Z. 1682–1686): „Meine" sind Karten, denen der
  // angemeldete Nutzer zugeordnet ist; „Überfällig" Karten mit einer Frist vor heute außerhalb von Done.
  const spaltenName = new Map(columns.map((c) => [c.id, c.name]))
  const istUeberfaellig = (c: Card) => isOverdue(c.dueDate, isDoneColumn(spaltenName.get(c.columnId) ?? ''))
  const ueberfaelligZahl = filteredCards.filter((c) => !c.archived && !c.ideaStored && istUeberfaellig(c)).length
  const sichtbareKarten = filteredCards.filter((c) => {
    if (kartenFilter === 'meine') return currentUserId !== null && c.assignees.includes(currentUserId)
    if (kartenFilter === 'ueberfaellig') return istUeberfaellig(c)
    return true
  })
  // Einblendbare Vorhaben (weder ausgeblendet). Sowohl Vorhaben-Filter als auch Anlege-Dialog
  // bieten seit Issue #785 nur noch daraus an — ein ausgeblendetes Vorhaben zeigte über den
  // Filter ohnehin nie etwas an, weil seine Karten auf dem Board grundsätzlich verdeckt bleiben.
  const sichtbareEpics = selectableEpics(epics, hiddenEpics)

  // Wirksame Auswahl für Massenaktionen: die Schnittmenge aus der Auswahl und dem, was der
  // Anzeige-Filter gerade zeigt. Jede Bulk-Stelle (Zählung, Dialogtexte, Verschieben, Archivieren,
  // Löschen) liest ausschließlich diese Menge — so kann keine Aktion eine Karte treffen, die der
  // Nutzer nicht sieht. Abgeleitet, statt die Auswahl beim Filterwechsel zu beschneiden: sie kommt
  // zurück, sobald die Karten wieder sichtbar sind, statt unwiderruflich zu verschwinden. Die
  // Reihenfolge folgt weiter der Klickhistorie (Iteration über die Auswahl, nicht über die Karten).
  // Nicht hierüber läuft der Haken an der Karte selbst (`selected` in der Render-Schleife): der
  // steht ohnehin nur an sichtbaren Karten und müsste sonst nach dem Filterwechsel verschwinden.
  const visibleCardIds = new Set(sichtbareKarten.map((c) => c.id))
  const effectiveSelectedIds = new Set([...selectedIds].filter((id) => visibleCardIds.has(id)))

  // Label-Massenaktion (#994): Zustand je Board-Label über dieselbe wirksame Auswahl wie jede
  // andere Massenaktion — so kann ein Klick keine Karte treffen, die der Filter gerade verdeckt.
  const gewaehlteKarten = sichtbareKarten.filter((c) => effectiveSelectedIds.has(c.id))
  const labelOptions: LabelOption[] = boardLabels.map((label) => ({
    label,
    zustand: labelZustand(
      gewaehlteKarten.filter((c) => c.labels.includes(label.id)).length,
      gewaehlteKarten.length,
    ),
  }))

  const changeEpicFilter = (value: number | null) => {
    setEpicFilter(value)
    try {
      if (value == null) localStorage.removeItem(`manban.boardEpicFilter.${board.id}`)
      else localStorage.setItem(`manban.boardEpicFilter.${board.id}`, String(value))
    } catch {
      // localStorage nicht verfügbar
    }
  }

  // Rückweg an der Spaltenmarke: hebt beide Achsen zusammen auf. Getrennte Rückwege gäbe es nicht
  // zu bedienen — die Marke nennt eine Zahl, nicht zwei Ursachen (E4). Das Vergessen der
  // Vorhaben-Ausblendung erledigt der Aufrufer in `onHiddenEpicsChange`; ohne das Löschen dort wäre
  // nach dem nächsten Reload alles wieder ausgeblendet.
  const showAllHidden = () => {
    onHiddenEpicsChange(new Set())
    setEpicFilter(null)
    try {
      localStorage.removeItem(`manban.boardEpicFilter.${board.id}`)
    } catch {
      // localStorage nicht verfügbar — aufgehoben ist die Ausblendung trotzdem.
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
    } catch (e) {
      // Die zurückspringende Karte allein erklärt nichts — die Meldung benennt den Fehlschlag.
      setCards(previous)
      notify(apiErrorMessage(e, 'Verschieben fehlgeschlagen.'), 'error')
    }
  }

  // Bewusst ohne eigenes try/catch: Ein hier geschluckter Fehler ließe `onSubmit` erfolgreich
  // erscheinen, und `NewCardModal` schlösse trotz Fehlschlag — die getippte Eingabe wäre weg.
  // Der Fehler propagiert stattdessen an den Dialog, der ihn seit Issue #808 selbst anzeigt.
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
    try {
      await api.archive(card.id)
      onCardsChanged?.()
    } catch (e) {
      notify(apiErrorMessage(e, 'Archivieren fehlgeschlagen.'), 'error')
    }
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
    } catch (e) {
      setCards(previous)
      notify(apiErrorMessage(e, 'In den Ideen-Pool verschieben fehlgeschlagen.'), 'error')
    }
  }

  // Duplizieren: Die Board-Liste liefert die Beschreibung nicht mehr mit (Issue #771), deshalb
  // wird die Quellkarte vor dem Öffnen des Dialogs einzeln nachgeladen. Schlägt das fehl, geht
  // kein Dialog auf — sonst entstünde eine Kopie mit leerer Beschreibung.
  const duplicateCard = async (c: Card) => {
    if (columns.length === 0) return
    let full: Card
    try {
      full = await api.get(c.id)
    } catch (e) {
      notify(apiErrorMessage(e, 'Karte konnte nicht geladen werden.'), 'error')
      return
    }
    // Die Kopie ist eine neue Karte und soll den kompletten Prozess durchlaufen — deshalb immer
    // in die erste Spalte ("Backlog"), nicht in die Spalte der Quellkarte (analog zum
    // board-weiten "+"-Button, der ebenfalls columns[0] nutzt).
    setDuplicateValues({ title: c.title, description: full.description ?? '', parentId: c.parentId })
    setModalColumn({ id: columns[0].id, name: columns[0].name })
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
    const ids = [...effectiveSelectedIds]
    const previous = cards
    setCards(previous.filter((c) => !effectiveSelectedIds.has(c.id)))
    setBulkArchiveConfirm(false)
    exitSelection()
    try {
      await api.bulkArchive(ids)
      onCardsChanged?.()
    } catch (e) {
      setCards(previous)
      notify(apiErrorMessage(e, 'Archivieren fehlgeschlagen.'), 'error')
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
    } catch (e) {
      setCards(previous)
      notify(apiErrorMessage(e, 'In den Papierkorb verschieben fehlgeschlagen.'), 'error')
    }
  }

  /**
   * Setzt ein Label an der Auswahl oder nimmt es ihr ab. Die Auswahl bleibt in beiden Ausgängen
   * bestehen — das Menü bleibt offen, damit mehrere Labels in einem Zug gehen.
   */
  const applyBulkLabel = async (labelId: number, action: LabelAction) => {
    // Doppelklickschutz: Ein zweiter Klick vor der Antwort schickte denselben Batch erneut los,
    // denn der Zustand im Menü zeigt bis dahin noch den alten Stand.
    if (labelBusy) return
    setLabelBusy(true)
    try {
      const geaendert = await api.bulkLabels([...effectiveSelectedIds], labelId, action)
      // Nur die Labels aus der Antwort übernehmen: Sie ist eine Einzelkarten-Sicht (Volltext in
      // `description`, `excerpt` leer), die Board-Liste genau umgekehrt (#771). Die ganze Karte zu
      // ersetzen mischte beide Antwortformen in einen Zustand.
      const labelsJeKarte = new Map(geaendert.map((c) => [c.id, c.labels]))
      setCards((current) =>
        current.map((c) => {
          const labels = labelsJeKarte.get(c.id)
          return labels === undefined ? c : { ...c, labels }
        }),
      )
      onCardsChanged?.()
    } catch (e) {
      notify(apiErrorMessage(e, 'Labels setzen fehlgeschlagen.'), 'error')
    } finally {
      setLabelBusy(false)
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
        .filter((c) => effectiveSelectedIds.has(c.id))
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

  // Nach dem Verschieben: Auf ein fremdes Board verlassen die Karten die Ansicht, auf dem eigenen
  // wechseln sie nur die Spalte und müssen dort auftauchen (#1043). `applyMove` hängt je Karte ans
  // Ende — in derselben Reihenfolge wie der Server, der die Eingabereihenfolge anhängt.
  const applyTransferred = (movedIds: number[], targetBoardId: number, targetColumnId: number) => {
    if (targetBoardId !== board.id) {
      const moved = new Set(movedIds)
      setCards((current) => current.filter((c) => !moved.has(c.id)))
      return
    }
    setCards((current) => movedIds.reduce((acc, id) => applyMove(acc, id, targetColumnId), current))
  }

  // Bulk-Verschieben: der Dialog erledigt den Transfer, danach die Ansicht nachziehen.
  const onBulkTransferred = (movedIds: number[], targetBoardId: number, targetColumnId: number) => {
    applyTransferred(movedIds, targetBoardId, targetColumnId)
    setBulkTransferOpen(false)
    exitSelection()
    onCardsChanged?.()
  }

  return (
    <Box>
      {/* Werkzeugleiste (Entwurf `.werkzeugleiste`, Z. 797–842): Filter links, Dichte und Aktionen rechts. */}
      {columns.length > 0 && (
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            flexWrap: 'wrap',
            mb: '14px',
            px: '11px',
            py: '9px',
            borderRadius: '10px',
            border: `1px solid ${RAND}`,
            background: `linear-gradient(180deg, ${PLATTE_HOCH}, ${PLATTE_FUSS})`,
            boxShadow: SCHATTEN_PLATTE,
          }}
        >
          <ChipGruppe<KartenFilter>
            label="Karten filtern"
            wert={kartenFilter}
            onChange={setKartenFilter}
            optionen={[
              { wert: 'alle', text: 'Alle Karten' },
              ...(currentUserId === null ? [] : [{ wert: 'meine' as const, text: 'Meine' }]),
              { wert: 'ueberfaellig', text: 'Überfällig', zahl: ueberfaelligZahl },
            ]}
          />
          {sichtbareEpics.length > 0 && (
            /* Wähler ohne sichtbare Beschriftung (Entwurf `.waehler`, Z. 833–842): Die Benennung
               trägt der Wert („Vorhaben: …"), der zugängliche Name bleibt am Feld (Issue #986). */
            <TextField
              select
              size="small"
              value={epicFilter ?? ''}
              onChange={(e) => changeEpicFilter(e.target.value === '' ? null : Number(e.target.value))}
              slotProps={{
                htmlInput: { 'aria-label': 'Vorhaben-Filter' },
                select: { native: true },
              }}
              sx={{
                '& .MuiOutlinedInput-root': {
                  bgcolor: NUT,
                  boxShadow: SCHATTEN_NUTE,
                  borderRadius: '7px',
                  fontSize: '11.5px',
                  fontWeight: 500,
                  color: 'text.secondary',
                },
                '& .MuiNativeSelect-select': { py: '4px', pl: '9px' },
              }}
            >
              <option value="">Vorhaben: alle</option>
              {sichtbareEpics.map((epic) => (
                <option key={epic.id} value={epic.id}>
                  Vorhaben: {epicShortcode(epic.title, epic.shortcode)} – {epic.title}
                </option>
              ))}
            </TextField>
          )}
          <Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
            <ChipGruppe<Dichte>
              label="Dichte"
              wert={dichte}
              onChange={setDichte}
              optionen={[
                { wert: 'normal', text: 'normal' },
                { wert: 'kompakt', text: 'kompakt' },
              ]}
            />
            {canEdit && (
              <Button size="small" variant="outlined" onClick={toggleSelectionMode}>
                {selectionMode ? 'Auswahl beenden' : 'Auswählen'}
              </Button>
            )}
            {canEdit && (
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
        </Box>
      )}

      {/* Ohne eigenen Grund und ohne eigenen Radius: Die Fläche, auf der die Panels schweben, ist
          seit #713 der Grund der ganzen Anwendung (theme.ts, APP_BACKGROUND). */}
      {/* Unterhalb von 900 px passen die Spalten nicht nebeneinander (Plan #932 E5); sie rollen
          waagerecht, und der Hinweis sagt es, statt dass Spalten unbemerkt rechts verschwinden. */}
      <Typography variant="caption" color="text.secondary" sx={{ display: { xs: 'block', md: 'none' }, px: 1 }}>
        Weitere Spalten: waagerecht rollen
      </Typography>
      <Stack
        data-testid="board-surface"
        direction="row"
        spacing={2}
        sx={{
          overflowX: 'auto',
          pb: 2,
          pt: 1,
          px: 1,
          width: '100%',
          alignItems: 'stretch',
          // Spalten füllen die Höhe bis nahe an den Viewport-Rand (Offset ≈ AppBar + Header).
          minHeight: 'calc(100vh - 210px)',
        }}
      >
        {columns.map((column) => {
          const colors = statusColors(column.name)
          // Voller Bestand, nicht der gefilterte: Der Zähler trägt die WIP-Grenze. Zählte er
          // filteredCards, meldete er bei gesetztem Vorhaben-Filter eine eingehaltene Grenze,
          // die tatsächlich verletzt ist. Dargestellt wird weiterhin filteredCards.
          const columnCards = activeCardsInColumn(cards, column.id)
          const count = columnCards.length
          // Eine Zahl je Spalte: die Vereinigung beider Achsen, keine zwei Zählungen (E4).
          const hiddenCount = columnCards.filter((c) => hiddenNumbers.has(c.number)).length
          const done = isDoneColumn(column.name)
          return (
            <Paper
              key={column.id}
              data-testid={`column-${column.id}`}
              elevation={0}
              onDragOver={(e) => {
                e.preventDefault()
                if (dragCardId != null) setAblageSpalteId(column.id)
              }}
              onDrop={(e) => {
                e.preventDefault()
                zugBeenden()
                const id = Number(e.dataTransfer.getData('text/plain'))
                if (id) {
                  void moveCard(id, column.id)
                }
              }}
              // Spalte als Nut im Grund (Entwurf `.spalte`, Z. 735–743); bei erreichter
              // Belastungsgrenze bernsteinfarbene Haarlinie (`.spalte-warn`, Z. 868).
              sx={{
                flex: '1 1 0',
                minWidth: 230,
                display: 'flex',
                flexDirection: 'column',
                gap: '9px',
                p: '10px',
                background: `linear-gradient(180deg, ${NUT}, color-mix(in srgb, ${NUT} 80%, var(--mb-palette-warte-grund)))`,
                border: `1px solid ${column.wipLimit != null && count >= column.wipLimit ? `color-mix(in srgb, ${MELDER.bernst} 42%, ${RAND})` : RAND}`,
                borderRadius: `${PANEL_RADIUS}px`,
                boxShadow: SCHATTEN_NUTE,
              }}
            >
              <Box sx={{ display: 'flex', alignItems: 'center', gap: '8px', px: '4px', pt: '2px' }}>
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
                sx={{ display: 'flex', alignItems: 'center', gap: '8px', flex: 1, minWidth: 0, cursor: showStructureEdit ? 'grab' : undefined }}
              >
                {/* Zustand der Spalte als Melder-LED (Entwurf Z. 1767): die Farbe des Status. */}
                <Box
                  component="span"
                  aria-hidden
                  data-testid={`status-${column.id}`}
                  sx={{ width: 9, height: 9, borderRadius: '50%', flex: 'none', bgcolor: colors.dot, color: colors.dot, boxShadow: `${LED_RING}, 0 0 8px -1px currentColor` }}
                />
                <Typography
                  component="h3"
                  sx={{ m: 0, fontFamily: SCHRIFT_ANZEIGE, fontStretch: '114%', fontSize: 11, fontWeight: 700, letterSpacing: '.1em', textTransform: 'uppercase', color: 'text.primary', flexGrow: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                >
                  {column.name}
                </Typography>
                <Typography
                  component="span"
                  sx={{ ...ZAHL, fontSize: 10.5, color: column.wipLimit != null && count >= column.wipLimit ? MELDER.bernst : TEXT_SCHWACH }}
                >
                  {column.wipLimit != null ? `${count}/${column.wipLimit}` : count}
                </Typography>
                {hiddenCount > 0 && (
                  // Ausgeblendet heißt sichtbar ausgeblendet: Jede Spalte, aus der etwas
                  // verschwunden ist, sagt es an und trägt den Rückweg an Ort und Stelle. Ein
                  // echter Button, damit der Rückweg auch mit der Tastatur erreichbar ist.
                  <Button
                    size="small"
                    onClick={showAllHidden}
                    aria-label={hiddenBadgeLabel(hiddenCount)}
                    sx={{
                      minWidth: 0,
                      px: 0.75,
                      py: 0,
                      textTransform: 'none',
                      fontSize: '0.75rem',
                      lineHeight: 1.6,
                      color: 'text.secondary',
                      bgcolor: NUT,
                      border: `1px solid ${RAND}`,
                      boxShadow: SCHATTEN_NUTE,
                      borderRadius: '5px',
                    }}
                  >
                    {`${hiddenCount} ausgeblendet`}
                  </Button>
                )}
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
              {/* Belastungsgrenze als Segmentskala (Entwurf `.wip`, Z. 855–866): ein Segment je Platz,
                  belegte Plätze kupfern, bei erreichter Grenze das letzte bernstein. Ein eigenes
                  Element neben dem Kopf, keine Einfärbung des Kopfes — der ist im
                  Struktur-Editiermodus anklickbar und ziehbar (AK 2, #925). */}
              {column.wipLimit != null && (
                <Box
                  role="meter"
                  aria-label={`Auslastung ${column.name}`}
                  aria-valuemin={0}
                  aria-valuemax={column.wipLimit}
                  aria-valuenow={Math.min(count, column.wipLimit)}
                  aria-valuetext={`${count} von ${column.wipLimit}`}
                  data-grenze={count >= column.wipLimit ? 'erreicht' : 'offen'}
                  sx={{ display: 'inline-flex', gap: '2px', alignItems: 'center', flex: 'none' }}
                >
                  {Array.from({ length: Math.min(column.wipLimit, 12) }, (_, i) => {
                    const grenze = column.wipLimit!
                    const segmente = Math.min(grenze, 12)
                    const belegt = i < Math.round((Math.min(count, grenze) / grenze) * segmente)
                    const letztes = count >= grenze && i === segmente - 1
                    const farbe = letztes ? MELDER.bernst : KUPFER
                    return (
                      <Box
                        key={i}
                        component="i"
                        data-testid="segment"
                        data-segment={segmentZustand(belegt, letztes)}
                        sx={{
                          width: 5,
                          height: 11,
                          borderRadius: '2px',
                          display: 'block',
                          ...(belegt ? { bgcolor: farbe, boxShadow: `0 0 6px -2px ${farbe}` } : { bgcolor: NUT, boxShadow: SCHATTEN_NUTE }),
                        }}
                      />
                    )
                  })}
                </Box>
              )}
              </Box>

              <Stack
                spacing={1}
                data-testid={`ablage-${column.id}`}
                data-ablage={dragCardId != null && ablageSpalteId === column.id && herkunftsSpalteId !== column.id ? 'aktiv' : undefined}
                sx={{
                  flex: 1,
                  gap: '9px',
                  minHeight: 120,
                  '& > :not(style) ~ :not(style)': { mt: 0 },
                  ...ablageflaecheSx(dragCardId != null && ablageSpalteId === column.id && herkunftsSpalteId !== column.id),
                }}
              >
                {activeCardsInColumn(sichtbareKarten, column.id).map((card) => {
                  const epic = epicOfCard(card, epics)
                  const doneAt = done ? card.movedToDoneAt : null
                  const overdue = isOverdue(card.dueDate, done)
                  const selected = selectedIds.has(card.id)
                  // Nur greifbar (Drag-Cursor), wenn bearbeitbar und nicht im Auswahlmodus —
                  // ersetzt zwei verschachtelte Cursor-Ternaries (S3358).
                  const grabbable = canEdit && !selectionMode
                  return (
                    <Paper
                      key={card.id}
                      component="article"
                      data-testid={`card-${card.id}`}
                      data-dichte={dichte}
                      draggable={canEdit && !selectionMode}
                      onDragStart={(e) => zugBeginnen(e, card.id)}
                      onDragEnd={zugBeenden}
                      data-zieh-zustand={dragCardId === card.id ? 'bewegt' : undefined}
                      onClick={() => (selectionMode ? toggleSelect(card.id) : onCardClick?.(card))}
                      elevation={0}
                      // Karte als Platte (Entwurf `.karte`, Z. 751–778): Kopf mit Nummer und
                      // Zuständigen, Titel, Labels, Fuß mit Schild und Frist. Kompakt bleiben Kopf
                      // und Titel.
                      sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        gap: dichte === 'kompakt' ? '4px' : '7px',
                        px: dichte === 'kompakt' ? '9px' : '11px',
                        py: dichte === 'kompakt' ? '6px' : '10px',
                        ...karteSx({ gewaehlt: selected, bewegt: dragCardId === card.id }),
                        cursor: grabbable ? 'grab' : 'pointer',
                        '&:active': { cursor: grabbable ? 'grabbing' : 'pointer' },
                      }}
                    >
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: '7px', minHeight: 20 }}>
                        {selectionMode && (
                          <Checkbox
                            size="small"
                            checked={selected}
                            onChange={() => toggleSelect(card.id)}
                            onClick={(e) => e.stopPropagation()}
                            slotProps={{ input: { 'aria-label': `Karte ${card.title} auswählen` } }}
                            sx={{ p: 0 }}
                          />
                        )}
                        <Box component="span" sx={{ ...ZAHL, fontSize: 11, color: TEXT_SCHWACH }}>{`#${card.number}`}</Box>
                        {dichte === 'normal' && (
                          <CardAssignees assigneeIds={card.assignees} members={members} cardTitle={card.title} />
                        )}
                        {canEdit && !selectionMode && (
                          <IconButton
                            size="small"
                            aria-label={`Menü ${card.title}`}
                            onClick={(e) => {
                              e.stopPropagation()
                              setMenu({ card, anchor: e.currentTarget })
                            }}
                            sx={{ ml: card.assignees.length > 0 && dichte === 'normal' ? 0 : 'auto', mr: '-6px', my: '-4px', p: '2px', color: TEXT_SCHWACH }}
                          >
                            <MoreVertIcon fontSize="small" />
                          </IconButton>
                        )}
                      </Box>
                      <Typography component="h4" sx={{ m: 0, fontSize: 12.5, fontWeight: 500, lineHeight: 1.35 }}>
                        {card.title}
                      </Typography>
                      {dichte === 'normal' && <CardLabels labelIds={card.labels} boardLabels={boardLabels} cardTitle={card.title} />}
                      {(epic || card.dueDate != null || (doneAt != null && retentionDays > 0)) && (
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                          {epic && (
                            <EpicBadge epicId={epic.id} title={epic.title} shortcode={epic.shortcode}
                              onOpen={onEpicOpen ? () => onEpicOpen(epic) : undefined} />
                          )}
                          {doneAt != null && retentionDays > 0 && (
                            <Box component="span" sx={{ ...ZAHL, ml: 'auto', fontSize: 10.5, color: TEXT_SCHWACH }}>
                              {cleanupCountdownLabel(cleanupDaysRemaining(doneAt, retentionDays))}
                            </Box>
                          )}
                          {card.dueDate != null && (
                            // Frist wie im Entwurf (`.frist`, `.frist-eng`, Z. 792–793): Plex Mono,
                            // überfällig zinnoberrot.
                            <Box
                              component="span"
                              aria-label={`Fällig ${card.title}`}
                              data-ueberfaellig={overdue ? 'ja' : undefined}
                              sx={{ ...ZAHL, ml: 'auto', fontSize: 10.5, fontWeight: overdue ? 600 : 400, color: overdue ? MELDER.zinnob : TEXT_SCHWACH }}
                            >
                              {`fällig ${formatDueDate(card.dueDate)}`}
                            </Box>
                          )}
                        </Box>
                      )}
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
            onClick={() => { const c = menu.card; closeMenu(); void duplicateCard(c) }}
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
                  Verschieben…
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
      {/* Anlege-Dialog und Vorhaben-Filter bieten seit Issue #785 beide nur einblendbare Vorhaben an
          (Plan #717, A4 galt bisher nur hier): Eine neue Karte kann keine bestehende Zuordnung
          verlieren, und was auf dem Board unsichtbar ist, soll man ihr nicht geben — dasselbe gilt
          jetzt für den Filter, dessen Auswahl sonst ein dauerhaft leeres Board zeigte. Das
          Karten-Kürzel (EpicBadge) bleibt bewusst an der vollen Liste `epics`: Eine Karte behält
          ihre Zuordnung sichtbar, auch wenn das Vorhaben inzwischen ausgeblendet wurde. */}
      <NewCardModal
        open={modalColumn !== null}
        columnName={modalColumn?.name ?? ''}
        epics={sichtbareEpics}
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
          onTransferred={(targetBoardId, targetColumnId) => {
            const c = transferCard
            setTransferCard(null)
            applyTransferred([c.id], targetBoardId, targetColumnId)
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
          onTransferred={(targetBoardId, targetColumnId) =>
            onBulkTransferred(selectedIdsInViewOrder(), targetBoardId, targetColumnId)
          }
        />
      )}

      <Dialog open={bulkArchiveConfirm} onClose={() => setBulkArchiveConfirm(false)}>
        <DialogTitle>Karten archivieren?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {effectiveSelectedIds.size === 1
              ? 'Die ausgewählte Karte wird archiviert.'
              : `${effectiveSelectedIds.size} Karten werden archiviert.`}{' '}
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

      {selectionMode && effectiveSelectedIds.size > 0 && (
        <BulkActionBar
          count={effectiveSelectedIds.size}
          canMove={canTransfer}
          labelOptions={labelOptions}
          labelsDisabledReason={labelSperrgrund(gewaehlteKarten, boardLabels)}
          onToggleLabel={(labelId, action) => void applyBulkLabel(labelId, action)}
          onArchive={() => setBulkArchiveConfirm(true)}
          onMove={() => setBulkTransferOpen(true)}
          onDelete={() => setDeleteConfirm([...effectiveSelectedIds])}
          onCancel={exitSelection}
        />
      )}

    </Box>
  )
}
