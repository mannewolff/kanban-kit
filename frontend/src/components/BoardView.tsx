import AddIcon from '@mui/icons-material/Add'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import ButtonBase from '@mui/material/ButtonBase'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useEffect, useRef, useState, type ReactElement } from 'react'
import type { Board, BoardColumn } from '../api/boards'
import { useAuth } from '../auth/AuthContext'
import { cardsApi, type Card, type CardsApi, type LabelAction } from '../api/cards'
import { ApiError, apiErrorMessage } from '../api/client'
import { columnsApi, type SortDirection } from '../api/columns'
import { epicsApi as defaultEpicsApi, type BulkAssignResult, type Epic, type EpicsApi } from '../api/epics'
import { artAusTitel, boardName, useKitNamen, type KitNamenZustand } from '../api/kitNamen'
import type { Member } from '../api/members'
import type { Woerterbuch } from '../i18n/de'
import { useT } from '../i18n/SpracheContext'
import { ablagePosition, activeCardsInColumn, applyMove, applyMoveAt, spaltenAuswahlUmschalten } from '../lib/boardOps'
import { effektivDone, neighbourColumns } from '../lib/columnMeta'
import { randGeschwindigkeit } from '../lib/fingerZiehen'
import { useEditMode } from '../lib/EditModeContext'
import { useTabletQuer } from '../lib/tabletQuer'
import { useFingerZiehen, type FingerZiehen, type FingerZiel } from '../lib/useFingerZiehen'
import type { Label } from '../api/labels'
import { isOverdue } from '../lib/dueDate'
import { epicShortcode } from '../lib/epicMeta'
import { epicAuswahl, visibleEpics } from '../lib/epicTiles'
import { hiddenCardNumbers } from '../lib/hiddenCards'
import { readTextFile } from '../lib/readTextFile'
import { restHoehe } from '../lib/restHoehe'
import { useKeyboardShortcut } from '../lib/useKeyboardShortcut'
import {
  MELDER,
  NUT,
  PLATTE,
  PLATTE_FUSS,
  PLATTE_HOCH,
  RAND,
  SCHATTEN_NUTE,
  SCHATTEN_PLATTE,
  SCHATTEN_TASTE,
  ZAHL,
} from '../theme'
import { BoardSpalte } from './board/BoardSpalte'
import type { NachtlaufKnopf } from './board/SpaltenKopf'
import { spaltenMass, SPALTEN_OHNE_ROLLEN, type Dichte } from './boardSurfaceSx'
import { BulkActionBar, type EpicOption, type LabelOption, type LabelZustand } from './BulkActionBar'
import { NewCardModal, type NewCardInitialValues, type NewItemInput } from './NewCardModal'
import { useSnackbar } from './SnackbarProvider'
import { SpecImportDialog, type SpecCard } from './SpecImportDialog'
import { TransferCardDialog } from './TransferCardDialog'
import { VorschauGesperrt } from './VorschauGesperrt'

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
const labelSperrgrund = (t: Woerterbuch, gewaehlt: Card[], boardLabels: Label[]): string | null => {
  if (gewaehlt.some((c) => c.type === 'EPIC')) return t.BOARD.LABELS_GESPERRT_VORHABEN
  if (boardLabels.length === 0) return t.BOARD.LABELS_GESPERRT_KEINE
  return null
}

/**
 * Höchstzahl Karten je Massenaktion — dieselbe Grenze wie `CardController.MAX_CARDS_PER_BATCH`
 * im Backend (Plan #1865, A6). Hier geprüft, damit die Ablehnung beide Zahlen nennt, statt als
 * Validierungsfehler des Servers anzukommen.
 */
const MAX_KARTEN_JE_ZUG = 200

/**
 * Grund, warum „Vorhaben zuordnen" gesperrt ist; `null` heißt bedienbar. Anders als bei den Labels
 * nennt er beide Gründe, wenn beide zutreffen (Plan #1865, E8). Wählbar sind alle Vorhaben der
 * Liste, ausgeblendete eingeschlossen.
 */
const vorhabenSperrgrund = (t: Woerterbuch, gewaehlt: Card[], epics: Epic[]): string | null => {
  const gruende = [
    ...(gewaehlt.some((c) => c.type === 'EPIC') ? [t.BOARD.ZUORDNEN_GESPERRT_VORHABEN] : []),
    ...(epics.length === 0 ? [t.BOARD.ZUORDNEN_GESPERRT_KEINE] : []),
  ]
  return gruende.length === 0 ? null : gruende.join('; ')
}

/**
 * Wie viele gewählte Karten unmittelbar zu einem anderen Vorhaben gehören (Plan #1865, E4). Eine
 * geerbte Mitgliedschaft steht nicht in `parentId` und zählt deshalb nicht mit.
 */
const ueberschriebeneZuordnungen = (gewaehlt: Card[], epicId: number) =>
  gewaehlt.filter((c) => c.parentId !== null && c.parentId !== epicId).length

/**
 * Erfolgsmeldung des Zuordnens (Plan #1865, E9, E10). Gemeldet wird, was der Server tatsächlich
 * geändert hat; überschriebene Zuordnungen nur, wenn es mehr waren als in der Rückfrage genannt.
 */
const zuordnenMeldung = (
  t: Woerterbuch,
  ergebnis: BulkAssignResult,
  epic: Epic,
  ausgeblendet: boolean,
  angekuendigtUeberschrieben: number,
) => {
  const anzahl = ergebnis.changedCardIds.length
  return [
    ausgeblendet ? t.BOARD.ZUORDNEN_ERFOLG_AUSGEBLENDET(anzahl, epic.title) : t.BOARD.ZUORDNEN_ERFOLG(anzahl, epic.title),
    ...(ergebnis.overwritten > angekuendigtUeberschrieben ? [t.BOARD.ZUORDNEN_MEHR_UEBERSCHRIEBEN(ergebnis.overwritten)] : []),
    ...(ergebnis.skipped > 0 ? [t.BOARD.ZUORDNEN_UEBERGANGEN(ergebnis.skipped)] : []),
  ].join(' ')
}

/**
 * Das Label der Sache `NIGHTRUN`, wie es dieses Board führt (Issue #1708); `undefined`, solange die
 * Kit-Namen fehlen oder das Board das Label nicht kennt — dann entfällt der Knopf in Ready.
 */
const nachtlaufLabelVon = (
  kitNamen: KitNamenZustand,
  boardLabels: Label[],
): { label: Label; istMensch: (titel: string) => boolean } | undefined => {
  if (kitNamen.status !== 'fertig') return undefined
  const { daten } = kitNamen
  const name = boardName(daten, 'NIGHTRUN', boardLabels.map((l) => l.name))
  const label = boardLabels.find((l) => l.name === name)
  return label && { label, istMensch: (titel) => artAusTitel(daten, titel) === 'MENSCH' }
}

/** Sperrgrund des Knopfes `kit:nightrun`: keine Karte kann das Label bekommen, oder alle tragen es. */
const nachtlaufSperrgrund = (t: Woerterbuch, label: string, moeglich: number, ziel: number): string | null => {
  if (moeglich === 0) return t.BOARD.NACHTLAUF_KEINE_KARTE(label)
  return ziel === 0 ? t.BOARD.NACHTLAUF_ALLE_MARKIERT(label) : null
}

type KartenFilter = 'alle' | 'meine' | 'ueberfaellig'

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

/** Ruhezustand des Ziehens: keine Karte unterwegs. Konstant aus demselben Grund wie oben. */
const KEINE_KARTEN: ReadonlySet<number> = new Set<number>()

/**
 * Erfolgsmeldung nach dem Sortieren: benennt die Richtung, in der tatsächlich sortiert wurde.
 * Läuft über den Toast-Stapel, dessen `Alert` als Live-Region vorgelesen wird — ohne die Meldung
 * bliebe der Erfolg für Screenreader unsichtbar (die Kartenreihenfolge ändert sich nur visuell).
 */
const sortedByNumberMessage = (t: Woerterbuch, columnName: string, sorted: SortDirection) =>
  t.BOARD.SORTIERT(columnName, sorted === 'ASC')

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
    | 'createBatch'
    | 'get'
    | 'move'
    | 'archive'
    | 'restore'
    | 'remove'
    | 'bulkArchive'
    | 'bulkTransfer'
    | 'bulkDelete'
    | 'bulkLabels'
  >
  epicsApi?: Pick<EpicsApi, 'create' | 'bulkAssign'>
}

/** Was ein laufender Fingerzug auf dem Board zeigt (Plan #1707, E5); ohne ihn gilt der Mauspfad. */
interface FingerZugAnzeige {
  /** Die wandernden Karten — eine oder die ganze Auswahl. */
  readonly kartenIds: ReadonlySet<number>
  /** Spalte unter dem Finger, `null` außerhalb jeder Spalte. */
  readonly spalteId: number | null
  /** Stelle der Einfügelinie; nur die Einzelkarte hat eine (E15), der Sammelzug landet am Ende. */
  readonly einfuegung: FingerZiel | null
}

function fingerZugAnzeige(
  zug: FingerZiehen,
  sammelZug: (cardId: number) => boolean,
  wanderndeIds: (cardId: number) => number[],
): FingerZugAnzeige | null {
  if (zug.zustand !== 'zieht') {
    return null
  }
  const cardId = Number(zug.gezogenId)
  return {
    kartenIds: new Set(wanderndeIds(cardId)),
    spalteId: zug.ziel ? Number(zug.ziel.spalteId) : null,
    einfuegung: sammelZug(cardId) ? null : zug.ziel,
  }
}

/**
 * Die Spalte, die alle wandernden Karten schon enthält, ist keine Ablage. Stammen sie aus mehreren
 * Spalten, gibt es keine solche, und jede Spalte nimmt sie an — die schon dort liegenden bleiben.
 * Die einzelne Karte unter dem Finger darf auch in ihrer eigenen Spalte die Stelle wechseln.
 */
function herkunftsSpalte(cards: Card[], ids: ReadonlySet<number>, anzeige: FingerZugAnzeige | null): number | undefined {
  const spalten = new Set(cards.filter((c) => ids.has(c.id)).map((c) => c.columnId))
  return spalten.size === 1 && !anzeige?.einfuegung ? [...spalten][0] : undefined
}

/** Stelle der Einfügelinie in dieser Spalte; `null`, wenn sie dort keine trägt. */
function einfuegeIndexIn(anzeige: FingerZugAnzeige | null, columnId: number): number | null {
  const einfuegung = anzeige?.einfuegung
  return einfuegung && Number(einfuegung.spalteId) === columnId ? einfuegung.index : null
}

/**
 * Spaltenansicht mit Drag & Drop. Verschieben ist optimistisch (Revert bei Fehler). Karten tragen
 * Epic-Badge + farbigen Rand, ein ⋮-Menü (Bearbeiten/Archivieren/Verschieben) und auf Done einen
 * Archiv-Countdown. Angelegt wird über „Neu anlegen“ oder die Taste „+“ — stets in der ersten Spalte.
 */
/** Unter diese Höhe schrumpft die Board-Fläche nicht, auch in einem sehr niedrigen Fenster (Issue #1901). */
const FLAECHE_MIN_HOEHE_PX = 320

/**
 * Höhe der Board-Fläche: von ihrer Oberkante im Dokument bis zum unteren Fensterrand (Issue #1901).
 * Gemessen statt als feste Abzugskonstante, weil Banner, umbrechende Werkzeugleiste und Tablet die
 * Oberkante verschieben. Neu gemessen bei Größenänderung des Fensters und der Fläche; ohne
 * `ResizeObserver` bleibt das Fenster-Ereignis.
 */
function useFlaechenHoehe(): [(element: HTMLElement | null) => void, number | undefined] {
  const [element, setElement] = useState<HTMLElement | null>(null)
  const [hoehe, setHoehe] = useState<number>()
  useEffect(() => {
    if (!element) return
    const messen = () =>
      setHoehe(restHoehe(element.getBoundingClientRect().top + window.scrollY, window.innerHeight, FLAECHE_MIN_HOEHE_PX))
    messen()
    window.addEventListener('resize', messen)
    const beobachter = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(messen)
    beobachter?.observe(element)
    return () => {
      window.removeEventListener('resize', messen)
      beobachter?.disconnect()
    }
  }, [element])
  return [setElement, hoehe]
}

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
  // In der „Ansicht als XY“ bleibt sichtbar, was die Rolle zeigt, gesperrt ist alles Verändernde,
  // und Ziehen und Ablegen sind aus (Plan #1914, E15).
  const inVorschau = Boolean(useAuth().vorschau)
  // Im ⋮-Menü nur in der Vorschau umschlossen: Das Menü gibt seinem Kind Fokus und Tab-Stopp mit,
  // die ein Umschlag außerhalb der Vorschau schluckte.
  const gesperrt = (eintrag: ReactElement) =>
    inVorschau ? <VorschauGesperrt key={eintrag.key}>{eintrag}</VorschauGesperrt> : eintrag
  const tabletQuer = useTabletQuer()
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
  // Offene Rückfrage des Zuordnens. Die genannte Zahl überschriebener Zuordnungen wird beim Öffnen
  // festgehalten: Mit ihr vergleicht die Erfolgsmeldung, was der Server tatsächlich ersetzt hat (E9).
  const [epicAssign, setEpicAssign] = useState<{ epic: Epic; angekuendigtUeberschrieben: number } | null>(null)
  const [kartenFilter, setKartenFilter] = useState<KartenFilter>('alle')
  // Im Browser gelesene Spezifikationsdatei (Name nur zur Anzeige). `null` = keine Vorschau offen;
  // hochgeladen wird die Datei nie, sie existiert hier nur als Text (Issue #493, #1201).
  const [spec, setSpec] = useState<{ fileName: string; markdown: string } | null>(null)
  // Zielspalte des Imports, beim Öffnen der Vorschau mit der ersten Spalte des Boards vorbelegt.
  const [specColumnId, setSpecColumnId] = useState<number | null>(null)
  const [dichte, setDichte] = useState<Dichte>('normal')
  const notify = useSnackbar()
  const t = useT()
  const nachtlauf = nachtlaufLabelVon(useKitNamen(), boardLabels)
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
  // Fünf Spalten passen nebeneinander; der Rollhinweis gilt erst ab der sechsten.
  const hinweisAufDemTablet = columns.length > SPALTEN_OHNE_ROLLEN ? 'block' : 'none'
  useEffect(() => setColumns(sortColumns(board.columns)), [board.columns])

  // Die Taste „+“ tut dasselbe wie der Button „Neu anlegen“: anlegen in der ersten Spalte. Ohne
  // Bearbeitungsrecht oder ohne Spalte gibt es nichts anzulegen, dann bleibt das Kürzel stumm.
  useKeyboardShortcut('+', canEdit && !inVorschau && columns.length > 0, () =>
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
  // Ziehen von Karten (AK 7, AK 8, Plan #932 E15; Auswahl #1324): welche Karten bewegt werden und
  // über welcher Spalte sie gerade stehen. Beides dient allein der Darstellung; das Verschieben
  // selbst trägt weiterhin die `dataTransfer`-Nutzlast.
  const [dragCardIds, setDragCardIds] = useState<ReadonlySet<number>>(KEINE_KARTEN)
  const [ablageSpalteId, setAblageSpalteId] = useState<number | null>(null)
  // Ohne Argument: Unter `@types/react` 18.3 wählt `useRef<T>()` dieselbe Überladung, der Typ
  // bleibt `MutableRefObject<T | undefined>` (S4623). React 19 verlangt das Argument wieder.
  const zugTakt = useRef<ReturnType<typeof setTimeout>>()
  const [flaecheRef, flaechenHoehe] = useFlaechenHoehe()
  useEffect(() => () => clearTimeout(zugTakt.current), [])

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
      notify(apiErrorMessage(e, t.BOARD.SPALTEN_UMSORTIEREN_FEHLER), 'error')
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
      notify(sortedByNumberMessage(t, column.name, direction), 'success')
      onCardsChanged?.()
    } catch (e) {
      notify(apiErrorMessage(e, t.BOARD.SORTIEREN_FEHLER), 'error')
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
      notify(t.BOARD.SPALTE_GELOESCHT, 'success')
    } catch (e) {
      setDeleteError(
        e instanceof ApiError && e.status === 409
          ? t.BOARD.SPALTE_NICHT_LEER
          : t.BOARD.LOESCHEN_FEHLER,
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
        notify(t.BOARD.SPALTE_ANGELEGT, 'success')
      } else if (columnDialog) {
        const updated = await columnsApi.update(columnDialog.id, name, wip)
        setColumns((cs) => sortColumns(cs.map((c) => (c.id === updated.id ? updated : c))))
        notify(t.BOARD.SPALTE_GESPEICHERT, 'success')
      }
    } catch (e) {
      // Nur der Erfolg schließt: Bei einem Fehlschlag bliebe die getippte Eingabe sonst verloren
      // und der Nutzer müsste Name und WIP-Limit erneut eintippen, um es nochmal zu versuchen.
      notify(apiErrorMessage(e, t.BOARD.SPALTE_SPEICHERN_FEHLER), 'error')
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
  const istUeberfaellig = (c: Card) => isOverdue(c.dueDate, effektivDone(c, spaltenName.get(c.columnId) ?? ''))
  const ueberfaelligZahl = filteredCards.filter((c) => !c.archived && istUeberfaellig(c)).length
  const sichtbareKarten = filteredCards.filter((c) => {
    if (kartenFilter === 'meine') return currentUserId !== null && c.assignees.includes(currentUserId)
    if (kartenFilter === 'ueberfaellig') return istUeberfaellig(c)
    return true
  })
  // Eingeblendete Vorhaben. Der Vorhaben-Filter bietet seit Issue #785 nur daraus an — ein
  // ausgeblendetes Vorhaben zeigte über den Filter nie etwas an, weil seine Karten auf dem Board
  // grundsätzlich verdeckt bleiben. Der Anlege-Dialog bietet seit Issue #1981 wieder alle an.
  const sichtbareEpics = visibleEpics(epics, hiddenEpics)

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

  // Zur Wahl stehen alle Vorhaben, auch ausgeblendete (Plan #1865, AK 3) — nicht `sichtbareEpics`;
  // ausgeblendete nach den eingeblendeten, wie im Karten-Detail und im Anlege-Dialog (Issue #1981).
  const epicOptions: EpicOption[] = epicAuswahl(epics, hiddenEpics)

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
      notify(apiErrorMessage(e, t.BOARD.VERSCHIEBEN_FEHLER), 'error')
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
      {
        dependencies: input.dependencies,
        dueDate: input.dueDate,
        assigneeIds: input.assigneeIds,
        labelIds: input.labelIds,
      },
    )
    setCards((current) => [...current, created])
    // Eine neue Karte an einem ausgeblendeten Vorhaben ist sofort verdeckt (Issue #1981): dieselbe
    // Meldung wie bei der Massenaktion, und die neu geladene Vorhaben-Liste verdeckt sie.
    const vorhaben = epics.find((e) => e.id === input.parentId)
    if (vorhaben && hiddenEpics.has(vorhaben.id)) {
      onEpicsChanged?.()
      notify(t.BOARD.ZUORDNEN_ERFOLG_AUSGEBLENDET(1, vorhaben.title), 'success')
    }
  }

  // Die Spezifikationsdatei wird ausschließlich im Browser gelesen — kein Upload, kein
  // Objektspeicher, und die Quelldatei bleibt unangetastet (Issue #493).
  const readSpecFile = async (file: File) => {
    try {
      const markdown = await readTextFile(file)
      setSpecColumnId(columns[0].id)
      setSpec({ fileName: file.name, markdown })
    } catch {
      notify(t.BOARD.DATEI_UNLESBAR, 'error')
    }
  }

  // Bewusst ohne try/catch: Der Fehler gehört in den Dialog, der offen bleibt und seine Meldung
  // zeigt (dieselbe Regel wie bei `createItem`). Die neuen Karten hängen sofort in der Ansicht,
  // damit der Erfolg auch ohne Live-Ereignis sichtbar ist.
  const handleSpecImport = async (neueKarten: SpecCard[], columnId: number) => {
    const created = await api.createBatch(board.id, columnId, neueKarten)
    setCards((current) => [...current, ...created])
    onCardsChanged?.()
    notify(t.BOARD.KARTEN_ANGELEGT(created.length), 'success')
  }

  const archiveCard = async (card: Card) => {
    try {
      await api.archive(card.id)
      onCardsChanged?.()
    } catch (e) {
      notify(apiErrorMessage(e, t.BOARD.ARCHIVIEREN_FEHLER), 'error')
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
      notify(apiErrorMessage(e, t.BOARD.KARTE_LADEN_FEHLER), 'error')
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
  // Ganze Spalte an- oder abwählen. Übergeben werden die IDs der **angezeigten** Karten dieser
  // Spalte — was ein Filter oder ein ausgeblendetes Vorhaben verdeckt, bleibt außen vor.
  const toggleSpaltenAuswahl = (angezeigteIds: number[]) =>
    setSelectedIds((prev) => spaltenAuswahlUmschalten(angezeigteIds, prev))
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
      notify(apiErrorMessage(e, t.BOARD.ARCHIVIEREN_FEHLER), 'error')
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
      notify(apiErrorMessage(e, t.BOARD.PAPIERKORB_FEHLER), 'error')
    }
  }

  /**
   * Setzt ein Label an der Auswahl oder nimmt es ihr ab. Die Auswahl bleibt in beiden Ausgängen
   * bestehen — das Menü bleibt offen, damit mehrere Labels in einem Zug gehen.
   */
  const applyBulkLabel = (labelId: number, action: LabelAction) =>
    setzeLabels([...effectiveSelectedIds], labelId, action)

  /** Meldet die Ablehnung über der Höchstzahl; `true` heißt, die Auswahl passt in einen Zug. */
  const passtInEinenZug = (anzahl: number) => {
    if (anzahl <= MAX_KARTEN_JE_ZUG) return true
    notify(t.BOARD.ZUORDNEN_HOECHSTENS(MAX_KARTEN_JE_ZUG, anzahl), 'error')
    return false
  }

  // Wahl im Menü: Über der Höchstzahl erscheint keine Rückfrage für eine unmögliche Aktion (E6).
  // Das Vorhaben stammt aus `epicOptions` und damit aus derselben Liste.
  const vorhabenGewaehlt = (epicId: number) => {
    const epic = epics.find((e) => e.id === epicId)!
    if (!passtInEinenZug(effectiveSelectedIds.size)) return
    setEpicAssign({ epic, angekuendigtUeberschrieben: ueberschriebeneZuordnungen(gewaehlteKarten, epic.id) })
  }

  /**
   * Ordnet die wirksame Auswahl dieses Moments zu (E6) — nicht optimistisch: Erst die Antwort sagt,
   * welche Karten sich geändert haben. Nach einem Fehler bleibt alles, wie es war, auch die Auswahl.
   * Verdeckt werden die Karten eines ausgeblendeten Ziels über die neu geladene Vorhaben-Liste (E11).
   */
  const confirmBulkAssign = async (epic: Epic, angekuendigtUeberschrieben: number) => {
    setEpicAssign(null)
    const ids = [...effectiveSelectedIds]
    if (!passtInEinenZug(ids.length)) return
    try {
      const ergebnis = await epicsApi.bulkAssign(ids, epic.id)
      const geaendert = new Set(ergebnis.changedCardIds)
      setCards((current) => current.map((c) => (geaendert.has(c.id) ? { ...c, parentId: epic.id } : c)))
      exitSelection()
      onEpicsChanged?.()
      onCardsChanged?.()
      notify(zuordnenMeldung(t, ergebnis, epic, hiddenEpics.has(epic.id), angekuendigtUeberschrieben), 'success')
    } catch (e) {
      notify(apiErrorMessage(e, t.BOARD.ZUORDNEN_FEHLER), 'error')
    }
  }

  /** Gemeinsamer Weg der Label-Massenaktion und des Knopfes `kit:nightrun` in Ready (Issue #1708). */
  const setzeLabels = async (ids: number[], labelId: number, action: LabelAction) => {
    // Doppelklickschutz: Ein zweiter Klick vor der Antwort schickte denselben Batch erneut los,
    // denn der Zustand im Menü zeigt bis dahin noch den alten Stand.
    if (labelBusy) return
    setLabelBusy(true)
    try {
      const geaendert = await api.bulkLabels(ids, labelId, action)
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
      notify(apiErrorMessage(e, t.BOARD.LABELS_SETZEN_FEHLER), 'error')
    } finally {
      setLabelBusy(false)
    }
  }

  /**
   * Knopf `kit:nightrun` für die angezeigten Karten einer Spalte (Issue #1708). Ziel sind die
   * sichtbaren Karten ohne Vorhaben (für sie ist die Label-Massenaktion gesperrt), ohne
   * Menschenschritte (der Runner stellte sie nur zurück) und ohne Karten, die das Label schon tragen.
   */
  const nachtlaufKnopf = (angezeigt: Card[]): NachtlaufKnopf | undefined => {
    if (!canEdit || nachtlauf === undefined) return undefined
    const { label, istMensch } = nachtlauf
    const moeglich = angezeigt.filter((c) => c.type !== 'EPIC' && !istMensch(c.title))
    const ziel = moeglich.filter((c) => !c.labels.includes(label.id)).map((c) => c.id)
    return {
      label: label.name,
      sperrgrund: inVorschau ? t.VORSCHAU.GESPERRT : nachtlaufSperrgrund(t, label.name, moeglich.length, ziel.length),
      beschaeftigt: labelBusy,
      onNachtlaufMarkieren: () => void setzeLabels(ziel, label.id, 'ADD'),
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

  // Was mit einer gezogenen Karte wandert (#1324): im Auswahlmodus die ganze wirksame Auswahl in
  // Sichtreihenfolge, sonst die Karte allein. Ziehbar ist im Auswahlmodus nur eine ausgewählte Karte.
  const sammelZug = (cardId: number) => selectionMode && effectiveSelectedIds.has(cardId)
  const wanderndeIds = (cardId: number) => (sammelZug(cardId) ? selectedIdsInViewOrder() : [cardId])

  const zugBeginnen = (e: React.DragEvent, cardId: number) => {
    e.dataTransfer.setData('text/plain', String(cardId))
    const ids = new Set(wanderndeIds(cardId))
    // Einen Takt später: Der Browser nimmt das Ziehbild erst nach diesem Ereignis auf. Ein sofortiger
    // Zustandswechsel zeigte dort schon den Platzhalter statt der Karte.
    clearTimeout(zugTakt.current)
    zugTakt.current = setTimeout(() => setDragCardIds(ids), 0)
  }

  const zugBeenden = () => {
    clearTimeout(zugTakt.current)
    setDragCardIds(KEINE_KARTEN)
    setAblageSpalteId(null)
  }

  // Sammel-Ablage auf dem eigenen Board: ein Aufruf für alle, alles oder nichts — einzelne
  // `move`-Aufrufe ließen bei einem Fehler halb verschobene Karten zurück. Karten, die schon in der
  // Zielspalte liegen, bleiben an ihrem Platz (dieselbe Regel wie `moveCard`).
  const moveCards = async (ids: number[], toColumnId: number) => {
    const spalteJeKarte = new Map(cards.map((c) => [c.id, c.columnId]))
    const wandernd = ids.filter((id) => spalteJeKarte.get(id) !== toColumnId)
    if (wandernd.length === 0) {
      return
    }
    const previous = cards
    applyTransferred(wandernd, board.id, toColumnId)
    exitSelection()
    try {
      await api.bulkTransfer(wandernd, board.id, toColumnId)
      onCardsChanged?.()
    } catch (e) {
      setCards(previous)
      notify(apiErrorMessage(e, t.BOARD.VERSCHIEBEN_FEHLER), 'error')
    }
  }

  const karteAbgelegt = (cardId: number, toColumnId: number) => {
    if (sammelZug(cardId)) void moveCards(selectedIdsInViewOrder(), toColumnId)
    else void moveCard(cardId, toColumnId)
  }

  // Einzelkarte an einer Stelle (Plan #1707, E3): anders als `moveCard` auch innerhalb derselben
  // Spalte. `position` zählt wie der Server ohne die Karte selbst; liegt sie schon dort, gibt es
  // nichts zu tun.
  const moveCardAt = async (cardId: number, toColumnId: number, position: number) => {
    if (activeCardsInColumn(cards, toColumnId).findIndex((c) => c.id === cardId) === position) {
      return
    }
    const previous = cards
    setCards(applyMoveAt(previous, cardId, toColumnId, position))
    try {
      await api.move(cardId, toColumnId, position)
    } catch (e) {
      setCards(previous)
      notify(apiErrorMessage(e, t.BOARD.VERSCHIEBEN_FEHLER), 'error')
    }
  }

  // Ablage per Finger (E3, E15): Die Auswahl wandert wie bei der Maus gesammelt ans Spaltenende;
  // die angezeigte Einfügestelle gilt nur für die Einzelkarte.
  const karteAbgelegtAn = (cardId: number, toColumnId: number, position: number) =>
    void (sammelZug(cardId) ? moveCards(selectedIdsInViewOrder(), toColumnId) : moveCardAt(cardId, toColumnId, position))

  // Ziehen per Finger auf dem Tablet quer (Plan #1707, E3), neben dem unveränderten Mauspfad. Der
  // Hook zählt die Stelle unter den angezeigten Karten; der Server zählt verdeckte mit.
  const kartenZug = useFingerZiehen({
    aktiv: tabletQuer,
    onAblage: (id: string, ziel: FingerZiel) => {
      const cardId = Number(id)
      const toColumnId = Number(ziel.spalteId)
      const ohneKarte = (liste: Card[]) => activeCardsInColumn(liste, toColumnId).filter((c) => c.id !== cardId)
      karteAbgelegtAn(cardId, toColumnId, ablagePosition(ohneKarte(cards), ohneKarte(sichtbareKarten), ziel.index))
    },
  })
  const spaltenZug = useFingerZiehen({
    aktiv: tabletQuer && showStructureEdit,
    onAblage: (id: string, ziel: FingerZiel) => void reorderColumn(Number(id), Number(ziel.spalteId)),
  })
  // Solange der Finger eine Karte zieht, trägt er die Darstellung, die sonst der Mauspfad setzt:
  // Platzhalter der wandernden Karten und Ablagefläche unter dem Finger.
  const fingerAnzeige = fingerZugAnzeige(kartenZug, sammelZug, wanderndeIds)
  const zugKartenIds = fingerAnzeige?.kartenIds ?? dragCardIds
  const zugSpalteId = fingerAnzeige ? fingerAnzeige.spalteId : ablageSpalteId
  const herkunftsSpalteId = herkunftsSpalte(cards, zugKartenIds, fingerAnzeige)

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
            label={t.BOARD.KARTEN_FILTERN}
            wert={kartenFilter}
            onChange={setKartenFilter}
            optionen={[
              { wert: 'alle', text: t.BOARD.ALLE_KARTEN },
              ...(currentUserId === null ? [] : [{ wert: 'meine' as const, text: t.BOARD.MEINE }]),
              { wert: 'ueberfaellig', text: t.BOARD.UEBERFAELLIG, zahl: ueberfaelligZahl },
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
                htmlInput: { 'aria-label': t.BOARD.VORHABEN_FILTER },
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
              <option value="">{t.BOARD.VORHABEN_ALLE}</option>
              {sichtbareEpics.map((epic) => (
                <option key={epic.id} value={epic.id}>
                  {t.BOARD.VORHABEN(epicShortcode(epic.title, epic.shortcode), epic.title)}
                </option>
              ))}
            </TextField>
          )}
          <Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
            <ChipGruppe<Dichte>
              label={t.BOARD.DICHTE}
              wert={dichte}
              onChange={setDichte}
              optionen={[
                { wert: 'normal', text: t.BOARD.DICHTE_NORMAL },
                { wert: 'kompakt', text: t.BOARD.DICHTE_KOMPAKT },
              ]}
            />
            {canEdit && (
              <Button size="small" variant="outlined" onClick={toggleSelectionMode}>
                {selectionMode ? t.BOARD.AUSWAHL_BEENDEN : t.BOARD.AUSWAEHLEN}
              </Button>
            )}
            {canEdit && (
              /* Dateiauswahl wie beim Anhang-Upload (CardDetailModal): Button als <label> mit
                 verstecktem Input — hier zusätzlich auf Markdown eingeschränkt. */
              <VorschauGesperrt>
              <Button size="small" variant="outlined" component="label">
                {t.BOARD.SPEZIFIKATION_EINLESEN}<input
                  hidden
                  type="file"
                  accept=".md,.markdown,text/markdown"
                  aria-label={t.BOARD.MARKDOWN_WAEHLEN}
                  disabled={inVorschau}
                  onChange={(e) => {
                    const file = e.target.files?.[0]
                    // Zurücksetzen, damit dieselbe Datei erneut gewählt werden kann — sonst bleibt
                    // `change` beim zweiten Mal aus, weil sich der Wert nicht ändert.
                    e.target.value = ''
                    if (file) void readSpecFile(file)
                  }}
                />
              </Button>
              </VorschauGesperrt>
            )}
            {canEdit && (
              <VorschauGesperrt>
                <Button
                  variant="contained"
                  size="small"
                  startIcon={<AddIcon />}
                  onClick={() => setModalColumn({ id: columns[0].id, name: columns[0].name })}
                >
                  {t.BOARD.NEU_ANLEGEN}
                </Button>
              </VorschauGesperrt>
            )}
          </Box>
        </Box>
      )}

      {/* Ohne eigenen Grund und ohne eigenen Radius: Die Fläche, auf der die Panels schweben, ist
          seit #713 der Grund der ganzen Anwendung (theme.ts, APP_BACKGROUND). */}
      {/* Unterhalb von 900 px passen die Spalten nicht nebeneinander (Plan #932 E5); sie rollen
          waagerecht, und der Hinweis sagt es, statt dass Spalten unbemerkt rechts verschwinden. */}
      {/* Auf dem Tablet quer stehen bis fünf Spalten nebeneinander (Plan #1707, E6); erst ab der
          sechsten rollt die Fläche, und erst dann steht der Hinweis da. */}
      <Typography
        variant="caption"
        color="text.secondary"
        sx={{ display: tabletQuer ? hinweisAufDemTablet : { xs: 'block', md: 'none' }, px: 1 }}
      >
        {t.BOARD.WAAGERECHT_ROLLEN}
      </Typography>
      {/* Die Fläche ist ein eigenes Fenster bis zum unteren Rand (Issue #1901): Sie rollt auf beiden
          Achsen, die Spaltenköpfe bleiben oben in ihr stehen, und der waagerechte Rollbalken steht
          immer am unteren Rand. Beim Ziehen einer Karte an ihren Rand rollt sie mit — dieselbe
          Rechnung wie beim Finger, weil Browser einen verschachtelten Behälter nicht alle gleich
          mitrollen. */}
      <Box
        ref={flaecheRef}
        data-testid="board-surface"
        onDragOver={(e) => {
          if (dragCardIds.size === 0) return
          const flaeche = e.currentTarget
          const rahmen = flaeche.getBoundingClientRect()
          flaeche.scrollTop += randGeschwindigkeit(e.clientY, rahmen.top, rahmen.bottom)
          flaeche.scrollLeft += randGeschwindigkeit(e.clientX, rahmen.left, rahmen.right)
        }}
        style={{ height: flaechenHoehe }}
        sx={{ overflow: 'auto', width: '100%' }}
      >
        {/* Eigene Zeile in der Fläche: Sie wächst mit der längsten Spalte, und alle Spalten strecken
            sich auf ihre Höhe. Rollte die Fläche selbst als Zeile, endeten die Spalten an ihrer Höhe,
            und mit ihnen die klebenden Köpfe. */}
        <Stack
          data-testid="board-zeile"
          direction="row"
          spacing={spaltenMass(tabletQuer).abstand}
          sx={{
            pb: 2,
            pt: 1,
            px: 1,
            alignItems: 'stretch',
            minHeight: '100%',
          }}
        >
          {columns.map((column) => {
            // Voller Bestand, nicht der gefilterte: Der Zähler trägt die WIP-Grenze. Zählte er
            // filteredCards, meldete er bei gesetztem Vorhaben-Filter eine eingehaltene Grenze,
            // die tatsächlich verletzt ist. Dargestellt wird weiterhin filteredCards.
            const columnCards = activeCardsInColumn(cards, column.id)
            const angezeigteKarten = activeCardsInColumn(sichtbareKarten, column.id)
            return (
              <BoardSpalte
                key={column.id}
                column={column}
                count={columnCards.length}
                // Eine Zahl je Spalte: die Vereinigung beider Achsen, keine zwei Zählungen (E4).
                hiddenCount={columnCards.filter((c) => hiddenNumbers.has(c.number)).length}
                // Was die Spalte gerade zeigt — Grundlage des Spalten-Kästchens und der Karten.
                angezeigteKarten={angezeigteKarten}
                nachtlauf={nachtlaufKnopf(angezeigteKarten)}
                epics={epics}
                dichte={dichte}
                selectionMode={selectionMode}
                selectedIds={selectedIds}
                canEdit={canEdit}
                showStructureEdit={showStructureEdit}
                members={members}
                boardLabels={boardLabels}
                retentionDays={retentionDays}
                naechsteRichtung={nextSortDirection[column.id] ?? 'ASC'}
                sortiertGerade={sortingColumnId === column.id}
                dragCardIds={zugKartenIds}
                ablageSpalteId={zugSpalteId}
                herkunftsSpalteId={herkunftsSpalteId}
                kartenZug={kartenZug}
                spaltenZug={spaltenZug}
                einfuegeIndex={einfuegeIndexIn(fingerAnzeige, column.id)}
                colDrag={colDrag}
                onKarteUeber={() => setAblageSpalteId(column.id)}
                onKarteAbgelegt={(cardId) => {
                  zugBeenden()
                  if (cardId && !inVorschau) {
                    karteAbgelegt(cardId, column.id)
                  }
                }}
                onKarteZugBeginn={(e, card) => zugBeginnen(e, card.id)}
                onKarteZugEnde={zugBeenden}
                onKarteWaehlen={(card) => toggleSelect(card.id)}
                onKarteOeffnen={(card) => onCardClick?.(card)}
                onKarteMenu={(card, anchor) => setMenu({ card, anchor })}
                onEpicOpen={onEpicOpen}
                onSpaltenAuswahl={toggleSpaltenAuswahl}
                onAusblendungAufheben={showAllHidden}
                onSortieren={() => void sortColumnByNumber(column)}
                onBearbeiten={() => openColumnDialog(column)}
                onLoeschen={() => {
                  setDeleteError(null)
                  setDeleteColumn(column)
                }}
                onZugBeginn={() => setColDrag(column.id)}
                onAblage={(fromId) => void reorderColumn(fromId, column.id)}
                onZugEnde={() => setColDrag(null)}
              />
            )
          })}
          {showStructureEdit && (
            <Box sx={{ flexShrink: 0, alignSelf: 'flex-start', pt: 0.5 }}>
              {tabletQuer ? (
                // Schmal, damit die fünfte Spalte daneben noch Platz hat; der Name sagt, was er tut.
                <VorschauGesperrt>
                  <IconButton size="small" aria-label={t.BOARD.NEUE_SPALTE} onClick={() => openColumnDialog('new')}>
                    <AddIcon fontSize="small" />
                  </IconButton>
                </VorschauGesperrt>
              ) : (
                <VorschauGesperrt>
                  <Button size="small" startIcon={<AddIcon />} onClick={() => openColumnDialog('new')}>
                    {t.BOARD.SPALTE}
                  </Button>
                </VorschauGesperrt>
              )}
            </Box>
          )}
        </Stack>
      </Box>

      <Dialog open={columnDialog !== null} onClose={closeColumnDialog}>
        <DialogTitle>{columnDialog === 'new' ? t.BOARD.NEUE_SPALTE : t.BOARD.SPALTE_BEARBEITEN}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              autoFocus
              label={t.BOARD.NAME}
              value={columnName}
              onChange={(e) => setColumnName(e.target.value)}
              slotProps={{ htmlInput: { maxLength: 120, 'aria-label': t.BOARD.SPALTENNAME } }}
            />
            <TextField
              label={t.BOARD.WIP_LIMIT_OPTIONAL}
              type="number"
              value={columnWip}
              onChange={(e) => setColumnWip(e.target.value)}
              slotProps={{ htmlInput: { min: 1, 'aria-label': t.BOARD.WIP_LIMIT } }}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeColumnDialog}>{t.ALLGEMEIN.ABBRECHEN}</Button>
          <VorschauGesperrt>
            <Button
              variant="contained"
              disabled={!columnName.trim() || parsedWip() === undefined}
              onClick={() => void saveColumn()}
            >
              {t.ALLGEMEIN.SPEICHERN}
            </Button>
          </VorschauGesperrt>
        </DialogActions>
      </Dialog>

      {deleteColumn && (
        <Dialog open onClose={() => setDeleteColumn(null)}>
          <DialogTitle>{t.BOARD.SPALTE_LOESCHEN_TITEL}</DialogTitle>
          <DialogContent>
            {deleteError && <Alert severity="error" sx={{ mb: 2 }}>{deleteError}</Alert>}
            <DialogContentText>
              {t.BOARD.SPALTE_LOESCHEN_TEXT(deleteColumn.name)}
            </DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDeleteColumn(null)}>{t.ALLGEMEIN.ABBRECHEN}</Button>
            <VorschauGesperrt>
              <Button color="error" onClick={() => void handleDeleteColumn(deleteColumn)}>
                {t.ALLGEMEIN.LOESCHEN}
              </Button>
            </VorschauGesperrt>
          </DialogActions>
        </Dialog>
      )}

      <Menu anchorEl={menu?.anchor ?? null} open={menu != null} onClose={closeMenu}>
        {menu && !menu.card.archived && [
          // Bearbeiten (Bleistift) nur im Editiermodus; Duplizieren/Archivieren/Verschieben bleiben
          // als Alltags-Aktionen erhalten.
          showStructureEdit ? gesperrt(
            <MenuItem key="edit" onClick={() => { const c = menu.card; closeMenu(); onEditCard?.(c) }}>
              {t.BOARD.BEARBEITEN}
            </MenuItem>
          ) : null,
          gesperrt(<MenuItem
            key="duplicate"
            onClick={() => { const c = menu.card; closeMenu(); void duplicateCard(c) }}
          >
            {t.BOARD.DUPLIZIEREN}
          </MenuItem>),
          gesperrt(<MenuItem key="archive" onClick={() => { const c = menu.card; closeMenu(); void archiveCard(c) }}>
            {t.BOARD.ARCHIVIEREN}
          </MenuItem>),
          ...(canTransfer
            ? [
                gesperrt(<MenuItem
                  key="transfer"
                  onClick={() => { const c = menu.card; closeMenu(); setTransferCard(c) }}
                >
                  {t.BOARD.VERSCHIEBEN_DIALOG}
                </MenuItem>),
              ]
            : []),
          // Genau eine Spalte weit nach links bzw. rechts — nicht ein Eintrag je Zielspalte, sonst
          // wächst das Menü mit der Spaltenzahl. Am Rand fehlt die jeweilige Richtung.
          moveLeft ? gesperrt(
            <MenuItem
              key="move-left"
              onClick={() => { const c = menu.card; closeMenu(); void moveCard(c.id, moveLeft.id) }}
            >
              {t.BOARD.NACH_LINKS}
            </MenuItem>
          ) : null,
          moveRight ? gesperrt(
            <MenuItem
              key="move-right"
              onClick={() => { const c = menu.card; closeMenu(); void moveCard(c.id, moveRight.id) }}
            >
              {t.BOARD.NACH_RECHTS}
            </MenuItem>
          ) : null,
          // Ganz unten und rot abgesetzt: der einzige Eintrag, der Inhalt aus dem Board nimmt —
          // er soll nicht neben „Nach rechts verschieben" versehentlich getroffen werden.
          gesperrt(<MenuItem
            key="delete"
            sx={{ color: 'error.main' }}
            onClick={() => { const c = menu.card; closeMenu(); setDeleteConfirm([c.id]) }}
          >
            {t.ALLGEMEIN.LOESCHEN}
          </MenuItem>),
        ]}
      </Menu>

      {/* modalColumn ist beim Submit immer gesetzt: NewCardModal ist nur offen, solange
          open={modalColumn !== null} — der Anlegen-Button existiert also nur in diesem Zustand. */}
      {/* Der Anlege-Dialog bietet seit Issue #1981 alle Vorhaben an, ausgeblendete markiert nach den
          übrigen — die Regel aus #785 ist für ihn bewusst umgekehrt. Der Vorhaben-Filter bleibt bei
          `sichtbareEpics`, dessen Auswahl sonst ein dauerhaft leeres Board zeigte. */}
      <NewCardModal
        open={modalColumn !== null}
        columnName={modalColumn?.name ?? ''}
        epics={epics}
        hiddenEpics={hiddenEpics}
        members={members}
        boardLabels={boardLabels}
        initialValues={duplicateValues ?? undefined}
        onClose={() => { setModalColumn(null); setDuplicateValues(null) }}
        onSubmit={(input) => createItem(modalColumn!.id, input)}
      />

      {/* Erst mit gelesener Datei gemountet: `specColumnId` ist dann gesetzt, und die Vorschau
          startet mit der Vorbelegung des Öffnens statt mit einer Spalte von vorletztem Mal. */}
      {spec !== null && specColumnId !== null && (
        <SpecImportDialog
          open
          fileName={spec.fileName}
          markdown={spec.markdown}
          columns={columns}
          columnId={specColumnId}
          onColumnChange={setSpecColumnId}
          onClose={() => setSpec(null)}
          onImport={handleSpecImport}
        />
      )}

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
        <DialogTitle>{t.BOARD.ARCHIVIEREN_TITEL}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t.BOARD.ARCHIVIEREN_ANZAHL(effectiveSelectedIds.size)} {t.BOARD.ARCHIVIEREN_HINWEIS}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBulkArchiveConfirm(false)}>{t.ALLGEMEIN.ABBRECHEN}</Button>
          <VorschauGesperrt>
            <Button color="error" onClick={() => void confirmBulkArchive()}>
              {t.BOARD.ARCHIVIEREN}
            </Button>
          </VorschauGesperrt>
        </DialogActions>
      </Dialog>

      <Dialog open={deleteConfirm.length > 0} onClose={() => setDeleteConfirm([])}>
        <DialogTitle>{t.BOARD.PAPIERKORB_TITEL}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t.BOARD.PAPIERKORB_TEXT(deleteConfirm.length)}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteConfirm([])}>{t.ALLGEMEIN.ABBRECHEN}</Button>
          <VorschauGesperrt>
            <Button color="error" onClick={() => void confirmDelete()}>
              {t.BOARD.IN_DEN_PAPIERKORB}
            </Button>
          </VorschauGesperrt>
        </DialogActions>
      </Dialog>

      {epicAssign && (
        <Dialog open onClose={() => setEpicAssign(null)}>
          <DialogTitle>{t.BOARD.ZUORDNEN_TITEL}</DialogTitle>
          <DialogContent>
            <DialogContentText>
              {t.BOARD.ZUORDNEN_TEXT(effectiveSelectedIds.size, epicAssign.epic.title)}
            </DialogContentText>
            {epicAssign.angekuendigtUeberschrieben > 0 && (
              <DialogContentText sx={{ mt: 1 }}>
                {t.BOARD.ZUORDNEN_UEBERSCHREIBT(epicAssign.angekuendigtUeberschrieben)}
              </DialogContentText>
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setEpicAssign(null)}>{t.ALLGEMEIN.ABBRECHEN}</Button>
            <VorschauGesperrt>
              <Button
                variant="contained"
                onClick={() => void confirmBulkAssign(epicAssign.epic, epicAssign.angekuendigtUeberschrieben)}
              >
                {t.BOARD.ZUORDNEN_BESTAETIGEN}
              </Button>
            </VorschauGesperrt>
          </DialogActions>
        </Dialog>
      )}

      {selectionMode && effectiveSelectedIds.size > 0 && (
        <BulkActionBar
          count={effectiveSelectedIds.size}
          canMove={canTransfer}
          labelOptions={labelOptions}
          labelsDisabledReason={labelSperrgrund(t, gewaehlteKarten, boardLabels)}
          onToggleLabel={(labelId, action) => void applyBulkLabel(labelId, action)}
          epicOptions={epicOptions}
          epicDisabledReason={vorhabenSperrgrund(t, gewaehlteKarten, epics)}
          onChooseEpic={vorhabenGewaehlt}
          onArchive={() => setBulkArchiveConfirm(true)}
          onMove={() => setBulkTransferOpen(true)}
          onDelete={() => setDeleteConfirm([...effectiveSelectedIds])}
          onCancel={exitSelection}
        />
      )}

    </Box>
  )
}
