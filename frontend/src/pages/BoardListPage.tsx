import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import IconButton from '@mui/material/IconButton'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import type { SxProps, Theme } from '@mui/material/styles'
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import DragIndicatorIcon from '@mui/icons-material/DragIndicator'
import RestoreOutlinedIcon from '@mui/icons-material/RestoreOutlined'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { boardsApi, type Board } from '../api/boards'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { cardsApi, type Card } from '../api/cards'
import { apiErrorMessage } from '../api/client'
import { epicsApi, type Epic } from '../api/epics'
import { labelsApi, type Label } from '../api/labels'
import { membersApi, type Member } from '../api/members'
import { CardDetailModal } from '../components/CardDetailModal'
import { useSnackbar } from '../components/SnackbarProvider'
import { EpicBadge } from '../components/EpicBadge'
import { epicOfCard } from '../lib/cardEpic'
import { epicToCard } from '../lib/epicToCard'
import { clampExcerptWidth, EXCERPT_DEFAULT_PCT, stripMarkdown } from '../lib/listExcerpt'
import { nextSortState, sortCards, type ColumnKey, type SortState } from '../lib/listSort'
import { useBoardEvents } from '../lib/useBoardEvents'
import { useBoardRole } from '../lib/useBoardRole'
import { useProjectName } from '../lib/useProjectName'
import { formatDueDate, isOverdue } from '../lib/dueDate'
import { ARCHIVED_STATUS_COLOR, statusColors } from '../lib/statusColors'
import {
  ETIKETT,
  GRUND_TIEF,
  KUPFER,
  KUPFER_SCHIMMER,
  LED_RING,
  MELDER,
  NUT,
  PANEL_RADIUS,
  PLATTE,
  PLATTE_FUSS,
  PLATTE_HOCH,
  RAND,
  SCHATTEN_NUTE,
  SCHATTEN_PLATTE,
  SCHATTEN_TASTE,
  SCHRIFT_ANZEIGE,
  TABELLENZIFFERN,
  TEXT_SCHWACH,
  ZAHL,
} from '../theme'
import { epicColor } from '../lib/epicMeta'
import { ablageflaecheSx, PLATZHALTER_SX } from '../components/boardSurfaceSx'
import { labelChipSx } from '../components/labelChipSx'

const ARCHIVED = 'archived'
type FilterKey = number | typeof ARCHIVED

const COLUMN_META: Record<ColumnKey, { label: string; sx: SxProps<Theme> }> = {
  number: { label: 'Nr', sx: { flexShrink: 0, width: 48 } },
  status: { label: 'Status', sx: { flexShrink: 0, width: 108 } },
  epic: { label: 'Vorhaben', sx: { flexShrink: 0, width: 76 } },
  title: { label: 'Titel', sx: { flex: 1, minWidth: 0 } },
  excerpt: { label: 'Beschreibung', sx: { flex: '0 0 30%', minWidth: 0 } },
}
const DEFAULT_ORDER: ColumnKey[] = ['number', 'status', 'epic', 'title', 'excerpt']
const ALL_KEYS = new Set<ColumnKey>(DEFAULT_ORDER)

function filterKey(boardId: number): string {
  return `manban.listFilters.${boardId}`
}
// Spaltenreihenfolge und Beschreibungsbreite gelten global (Issue #432): Wer die Beschreibung
// einmal nach vorne zieht, will sie auf jedem Board vorne haben. Der Spalten-FILTER bleibt dagegen
// board-gebunden — welche Spalten es gibt, unterscheidet sich je Board.
const COLUMN_KEY = 'manban.listColumns'
/** Gruppierung der Liste (#980): nach Vorhaben — der Standard, ausdrücklich gewünscht — oder keine. */
const GRUPPIERUNG_KEY = 'manban.listGruppierung'
type Gruppierung = 'vorhaben' | 'keine'

function readGruppierung(): Gruppierung {
  try {
    return localStorage.getItem(GRUPPIERUNG_KEY) === 'keine' ? 'keine' : 'vorhaben'
  } catch {
    return 'vorhaben'
  }
}

/** Filtertaste der Werkzeugleiste (Entwurf `.chip`, Z. 812–826). */
const chipSx = (aktiv: boolean): SxProps<Theme> => ({
  height: 26,
  borderRadius: '6px',
  fontSize: 11.5,
  fontWeight: 500,
  color: aktiv ? 'text.primary' : 'text.secondary',
  border: `1px solid ${aktiv ? RAND : 'transparent'}`,
  bgcolor: 'transparent',
  ...(aktiv && { background: `linear-gradient(180deg, ${PLATTE_HOCH}, ${PLATTE})`, boxShadow: SCHATTEN_TASTE }),
  '&&:hover': { bgcolor: aktiv ? PLATTE : 'transparent' },
  '& .MuiChip-label': { px: '10px' },
})

/** Eingelassene Gruppe von Filtertasten (Entwurf `.chip-gruppe`). */
const CHIP_GRUPPE_SX = {
  display: 'inline-flex',
  flexWrap: 'wrap',
  gap: '3px',
  p: '3px',
  bgcolor: NUT,
  border: `1px solid ${RAND}`,
  borderRadius: '9px',
  boxShadow: SCHATTEN_NUTE,
} as const
const EXCERPT_KEY = 'manban.listExcerptWidth'
/** Alt-Schlüssel aus der Zeit, als beide Einstellungen pro Board gespeichert wurden. */
const LEGACY_VIEW_KEY = /^manban\.(listColumns|listExcerptWidth)\.\d+$/

/**
 * Entfernt die board-gebundenen Alt-Schlüssel. Bewusst ohne Migration: Sie müsste willkürlich
 * festlegen, welches Board gewinnt — die Einstellung wird einmal auf Standard zurückgesetzt.
 */
function purgeLegacyViewKeys(): void {
  try {
    const stale: string[] = []
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i)
      if (k !== null && LEGACY_VIEW_KEY.test(k)) stale.push(k)
    }
    stale.forEach((k) => localStorage.removeItem(k))
  } catch {
    // localStorage nicht verfügbar — dann gibt es auch nichts aufzuräumen.
  }
}

function readExcerptWidth(): number {
  try {
    const raw = localStorage.getItem(EXCERPT_KEY)
    return raw == null ? EXCERPT_DEFAULT_PCT : clampExcerptWidth(Number.parseFloat(raw))
  } catch {
    return EXCERPT_DEFAULT_PCT
  }
}

/** Liest die gespeicherte Spaltenreihenfolge; unbekannte Keys raus, fehlende hinten anfügen. */
function readColumnOrder(): ColumnKey[] {
  try {
    const raw = localStorage.getItem(COLUMN_KEY)
    if (!raw) return DEFAULT_ORDER
    const stored = (JSON.parse(raw) as string[]).filter((k): k is ColumnKey => ALL_KEYS.has(k as ColumnKey))
    const missing = DEFAULT_ORDER.filter((k) => !stored.includes(k))
    return [...stored, ...missing]
  } catch {
    return DEFAULT_ORDER
  }
}

/** Gruppenkopf als eingelassene Nut (Entwurf `.gruppe`, Z. 941–953): Mal, Name, Fortschritt. */
function GruppenKopf({ epic, zahl }: Readonly<{ epic: Epic | null; zahl: number }>) {
  const farbe = epic === null ? TEXT_SCHWACH : epicColor(epic.id)
  const name = epic?.title ?? 'Ohne Vorhaben'
  return (
    <Box
      role="heading"
      aria-level={3}
      aria-label={name}
      data-testid={`gruppe-${epic?.id ?? 'ohne'}`}
      sx={{ display: 'flex', alignItems: 'center', gap: '10px', px: '12px', py: '8px', color: farbe, borderBottom: `1px solid ${RAND}`, background: `linear-gradient(180deg, ${NUT}, color-mix(in srgb, ${NUT} 82%, var(--mb-palette-warte-grund)))`, boxShadow: SCHATTEN_NUTE }}
    >
      <Box component="span" aria-hidden sx={{ width: 8, height: 8, borderRadius: '2px', bgcolor: 'currentColor', flex: 'none' }} />
      <Box component="span" sx={{ fontFamily: SCHRIFT_ANZEIGE, fontStretch: '112%', fontSize: 11.5, fontWeight: 700, letterSpacing: '.04em', color: 'text.primary' }}>
        {name}
      </Box>
      <Box component="span" sx={{ ...ZAHL, fontSize: 11, color: TEXT_SCHWACH }}>
        {epic === null ? (zahl === 1 ? '1 Karte' : `${zahl} Karten`) : `${epic.done} von ${epic.total} fertig`}
      </Box>
      {epic !== null && epic.total > 0 && (
        <Box sx={{ width: 96, height: 5, borderRadius: '3px', ml: 'auto', bgcolor: `color-mix(in srgb, ${GRUND_TIEF} 70%, transparent)`, boxShadow: SCHATTEN_NUTE, overflow: 'hidden' }}>
          <Box data-testid={`gruppe-fortschritt-${epic.id}`} sx={{ height: '100%', width: `${Math.round((epic.done / epic.total) * 100)}%`, borderRadius: '3px', bgcolor: 'currentColor', boxShadow: '0 0 7px -2px currentColor' }} />
        </Box>
      )}
    </Box>
  )
}

export function BoardListPage() {
  const { boardId } = useParams()
  const id = Number.parseInt(boardId ?? '', 10)
  const validId = Number.isInteger(id) && id > 0
  const notify = useSnackbar()
  const [board, setBoard] = useState<Board | null>(null)
  const [cards, setCards] = useState<Card[]>([])
  const [epics, setEpics] = useState<Epic[]>([])
  const [labels, setLabels] = useState<Label[]>([])
  const [labelFilter, setLabelFilter] = useState<Set<number>>(new Set())
  const [members, setMembers] = useState<Member[]>([])
  const [gruppierung, setGruppierung] = useState<Gruppierung>(() => readGruppierung())
  const [nurUeberfaellig, setNurUeberfaellig] = useState(false)
  const [zustaendig, setZustaendig] = useState<number | null>(null)
  const [filters, setFilters] = useState<Set<FilterKey> | null>(null)
  const [order, setOrder] = useState<ColumnKey[]>(() => readColumnOrder())
  const [detailCard, setDetailCard] = useState<Card | null>(null)
  const [rowDrag, setRowDrag] = useState<number | null>(null)
  const [rowOver, setRowOver] = useState<number | null>(null)
  // Darstellung der bewegten Zeile (AK 7, AK 8, #957): einen Takt nach Ziehbeginn gesetzt, damit
  // das Ziehbild des Browsers die Zeile zeigt und nicht schon den Platzhalter.
  const [bewegteZeile, setBewegteZeile] = useState<number | null>(null)
  const zugTakt = useRef<ReturnType<typeof setTimeout>>(undefined)
  useEffect(() => () => clearTimeout(zugTakt.current), [])
  const [colDrag, setColDrag] = useState<ColumnKey | null>(null)
  const [colOver, setColOver] = useState<ColumnKey | null>(null)
  const [excerptWidth, setExcerptWidth] = useState<number>(() => readExcerptWidth())
  // Die Sortierung nach Spalteninhalt ist ein Blick, keine Einrichtung: Sie wird bewusst nicht in
  // localStorage gehalten und beim Board-Wechsel zurückgesetzt (Issue #700).
  const [sort, setSort] = useState<SortState>(null)
  const viewRef = useRef<HTMLDivElement>(null)
  const resizingRef = useRef(false)
  const headerDragRef = useRef(false)
  const resizeCleanupRef = useRef<(() => void) | null>(null)

  const reloadCards = () => {
    void cardsApi.list(id).then(setCards)
  }

  // Live-Updates: bei einer Änderung durch andere die Kartenliste neu laden.
  useBoardEvents(id, reloadCards)

  // `reloadCards()` läuft im `finally`: Nach einem Konflikt zeigt die Liste sonst weiter den Stand
  // von vor dem Fehlschlag, obwohl der Server einen anderen hält.
  const restoreCard = async (cardId: number) => {
    try {
      await cardsApi.restore(cardId)
    } catch (e) {
      notify(apiErrorMessage(e, 'Karte wiederherstellen fehlgeschlagen.'), 'error')
    } finally {
      reloadCards()
    }
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
      // Zuständige für den Filter „Zuständig" (#980); ohne sie bleibt der Filter weg.
      membersApi.list(b.projectId).then(
        (ms) => {
          if (active) setMembers(ms)
        },
        () => {
          if (active) setMembers([])
        },
      )
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
    return () => {
      active = false
    }
  }, [id, validId])

  // Alt-Schlüssel aus der Pro-Board-Zeit einmalig aufräumen (Issue #432).
  useEffect(purgeLegacyViewKeys, [])

  // Beim Board-Wechsel bleibt die Komponente gemountet (Route `/boards/:boardId/list`) — ohne
  // dieses Zurücksetzen wirkte die Sortierung des vorigen Boards weiter und blockierte dort still
  // das Umordnen per Drag.
  useEffect(() => {
    setSort(null)
  }, [id])

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
          localStorage.setItem(EXCERPT_KEY, String(w))
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

  /**
   * Beschriftung einer Kopfzelle. Das Präfix `Spalte <Label>` bleibt unangetastet und der
   * Sortierzustand hängt hinten an — `aria-sort` scheidet aus, weil es eine
   * `role="columnheader"`-Struktur voraussetzt, die den Zeilen ihre eigene Button-Rolle nähme.
   */
  const headerLabel = (key: ColumnKey): string => {
    const label = `Spalte ${COLUMN_META[key].label}`
    if (sort?.key !== key) return label
    return `${label}, ${sort.dir === 'asc' ? 'aufsteigend' : 'absteigend'} sortiert`
  }

  const toggleSort = (key: ColumnKey) => setSort((prev) => nextSortState(prev, key))

  const cellSx = (key: ColumnKey): SxProps<Theme> =>
    key === 'excerpt' ? { ...COLUMN_META.excerpt.sx, flex: `0 0 ${excerptWidth}%` } : COLUMN_META[key].sx

  const { canEdit, canModerate } = useBoardRole(board)
  const projectName = useProjectName(board?.projectId ?? null)

  const columns = useMemo(() => [...(board?.columns ?? [])].sort((a, b) => a.position - b.position), [board])
  const columnById = useMemo(() => new Map(columns.map((c) => [c.id, c])), [columns])

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
        localStorage.setItem(COLUMN_KEY, JSON.stringify(next))
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

  // filters ist nur bis zum ersten Board-Load null; sobald das Board (und damit die Spalten-Chips)
  // gerendert werden, ist es gesetzt. Der Null-Fall wird hier einmal zentral abgefangen, damit die
  // einzelnen Zugriffe (Filterleiste, Sichtbarkeit, Spalten-Chips) ohne eigenen Null-Guard auskommen.
  const activeFilters = filters ?? new Set<FilterKey>()
  const archiveActive = activeFilters.has(ARCHIVED)
  // Die Liste zeigt nur aktive Karten. Board-lose bzw. board-gebundene Ideen (ideaStored) leben
  // jetzt in der projektweiten Ideen-Seite und sind hier ausgeblendet.
  const istUeberfaellig = (c: Card) =>
    !c.archived && isOverdue(c.dueDate, (columnById.get(c.columnId)?.name ?? '').toLowerCase().includes('done'))
  const ueberfaelligZahl = cards.filter((c) => !c.ideaStored && istUeberfaellig(c)).length
  const inBoardOrder = cards
    .filter((c) => !c.ideaStored && (c.archived ? archiveActive : activeFilters.has(c.columnId)))
    .filter((c) => labelFilter.size === 0 || c.labels.some((l) => labelFilter.has(l)))
    .filter((c) => !nurUeberfaellig || istUeberfaellig(c))
    .filter((c) => zustaendig === null || c.assignees.includes(zustaendig))
    .sort((a, b) => {
      const pa = columnById.get(a.columnId)?.position ?? 0
      const pb = columnById.get(b.columnId)?.position ?? 0
      return pa - pb || a.positionInColumn - b.positionInColumn
    })
  // Die Board-Reihenfolge bleibt der Grundzustand und der Gleichstand-Anker; die Sortierung nach
  // Spalteninhalt legt sich darüber.
  const visible = sortCards(inBoardOrder, sort, { columnById, epics })

  // Umsortieren per Drag nur, wenn genau eine echte Spalte gefiltert ist: die Liste zeigt sonst
  // mehrere Spalten gemischt und ein Cross-Spalten-Drop wäre mehrdeutig (und würde den Status
  // ändern). Archiv zählt nicht (archivierte Karten haben keine aktive Position). Unter einer
  // aktiven Sortierung ebenfalls nicht: Der Zeilen-Drop schreibt `target.positionInColumn` und
  // verlässt sich damit darauf, dass die angezeigte Reihenfolge die gespeicherte ist — sonst
  // landete die Karte woanders als dort, wohin gezogen wurde.
  const activeColumns = columns.filter((c) => activeFilters.has(c.id))
  const sortable = canEdit && !archiveActive && activeColumns.length === 1 && sort === null && gruppierung === 'keine'

  const gruppierungWaehlen = (wert: Gruppierung) => {
    setGruppierung(wert)
    try {
      localStorage.setItem(GRUPPIERUNG_KEY, wert)
    } catch {
      // localStorage nicht verfügbar
    }
  }

  // Gruppen nach Vorhaben (Entwurf Z. 941–953, 1893–1904): je Vorhaben mit sichtbaren Karten eine
  // Gruppe in der Reihenfolge der Vorhaben-Liste, Karten ohne Vorhaben am Ende.
  const gruppen: Array<{ epic: Epic | null; karten: Card[] }> =
    gruppierung === 'keine'
      ? [{ epic: null, karten: visible }]
      : [
          ...epics.map((epic) => ({ epic, karten: visible.filter((c) => epicOfCard(c, epics)?.id === epic.id) })),
          { epic: null, karten: visible.filter((c) => epicOfCard(c, epics) === undefined) },
        ].filter((g) => g.karten.length > 0)

  // Nur im sortierbaren Zustand (genau eine echte Spalte) ist ein Zeilen-Drop gültig. Dort liegen
  // alle sichtbaren Karten in derselben, nicht-archivierten Spalte — die frühere Spalten-/Archiv-
  // Prüfung ist damit redundant; es bleibt: ein laufender Drag, nicht auf sich selbst.
  const validRowDrop = (target: Card): boolean =>
    sortable && rowDrag !== null && rowDrag !== target.id

  const onRowDrop = async (target: Card) => {
    const dragId = rowDrag
    const ok = validRowDrop(target)
    setRowOver(null)
    if (!ok || dragId == null) return
    try {
      await cardsApi.move(dragId, target.columnId, target.positionInColumn)
    } catch (e) {
      notify(apiErrorMessage(e, 'Verschieben fehlgeschlagen.'), 'error')
    } finally {
      reloadCards()
    }
  }

  /** Statusfarbe einer Zeile; archiviert schlaegt die Spaltenfarbe. */
  const rowStatusColor = (card: Card) =>
    (card.archived ? ARCHIVED_STATUS_COLOR : statusColors(columnById.get(card.columnId)?.name ?? '')).dot

  const renderCell = (key: ColumnKey, card: Card) => {
    const col = columnById.get(card.columnId)
    switch (key) {
      case 'number':
        // Zahlen untereinander: rechtsbündig in Tabellenziffern (AK 10).
        return (
          <Typography variant="caption" sx={{ ...ZAHL, display: 'block', textAlign: 'right', color: TEXT_SCHWACH, ...TABELLENZIFFERN }}>
            #{card.number}
          </Typography>
        )
      case 'status': {
        // Die Farbe traegt allein die linke Zeilenkante; der Text bleibt, weil Farbe nie
        // alleiniger Informationstraeger sein darf (#650).
        const label = card.archived ? 'Archiv' : col?.name ?? ''
        // Zustandsplakette des Entwurfs (`.zustand`, Z. 955–965): LED in der Statusfarbe und Name.
        return (
          <Box
            component="span"
            data-testid={`zustand-${card.id}`}
            sx={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: 11.5, color: 'text.secondary', pl: '6px', pr: '8px', py: '2px', borderRadius: '6px', border: `1px solid ${RAND}`, bgcolor: NUT, boxShadow: SCHATTEN_NUTE, whiteSpace: 'nowrap', maxWidth: '100%', overflow: 'hidden', textOverflow: 'ellipsis' }}
          >
            <Box component="span" aria-hidden data-testid={`zustand-led-${card.id}`} sx={{ width: 9, height: 9, borderRadius: '50%', flex: 'none', bgcolor: rowStatusColor(card), color: rowStatusColor(card), boxShadow: `${LED_RING}, 0 0 8px -1px currentColor` }} />
            {label}
          </Box>
        )
      }
      case 'epic': {
        const epic = epicOfCard(card, epics)
        return epic ? (
          <EpicBadge
            epicId={epic.id}
            title={epic.title}
            shortcode={epic.shortcode}
            // Der Sprung öffnet das Vorhaben an Ort und Stelle im vorhandenen Detail-Dialog
            // (Plan #682, E4) — derselbe Zustand, über den die Liste auch Karten öffnet.
            onOpen={() => setDetailCard(epicToCard(epic, id))}
          />
        ) : null
      }
      case 'title': {
        const overdue = isOverdue(card.dueDate, (col?.name ?? '').toLowerCase().includes('done'))
        return (
          // Fälligkeit neben dem Titel statt darunter: eine Zeile weniger je fälliger Karte (AK 12).
          // Reicht der Platz nicht, bricht sie um, statt den Titel zu kürzen.
          <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'baseline', columnGap: 1 }}>
            {/* Ohne `noWrap`: Der Titel wird nicht abgeschnitten (AK 12). Der Auszug daneben bleibt
                bewusst einzeilig (Plan #932 E16). */}
            <Typography variant="body2" sx={{ fontSize: 12.5, fontWeight: 500, overflowWrap: 'anywhere' }}>{card.title}</Typography>
            {card.dueDate != null && (
              <Typography
                variant="caption"
                aria-label={`Fällig ${card.title}`}
                data-ueberfaellig={overdue ? 'ja' : undefined}
                sx={{ ...ZAHL, whiteSpace: 'nowrap', fontSize: 11, fontWeight: overdue ? 600 : 400, color: overdue ? MELDER.zinnob : TEXT_SCHWACH }}
              >
                {`fällig ${formatDueDate(card.dueDate)}`}
              </Typography>
            )}
          </Box>
        )
      }
      case 'excerpt':
        return <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>{stripMarkdown(card.excerpt ?? '')}</Typography>
    }
  }

  if (!validId) {
    return <Alert severity="error">Ungültige Board-ID.</Alert>
  }

  // Der Hinweis unter den Filtern: bei aktiver Sortierung erklärt er sie, sonst — und nur, wo das
  // Umordnen überhaupt zusteht — wie man die Kartenreihenfolge ändert. Die Reihenfolge der Prüfung
  // bleibt: unter einer Sortierung kommt der Umordnen-Hinweis nie zusätzlich.
  // Die Richtung trägt das Pfeil-Icon und das aria-label der Kopfzelle, nicht dieser Satz.
  // Das Umordnen kommt nur zur Sprache, wo es überhaupt zusteht: Sortieren ist eine
  // Ansichtsfunktion und steht auch Nur-Lesern offen.
  let listHint: string | null = null
  if (sort !== null) {
    listHint = `Sortiert nach ${COLUMN_META[sort.key].label}.${
      canEdit ? ' Zum Ändern der Kartenreihenfolge die Sortierung aufheben.' : ''
    }`
  } else if (canEdit && gruppierung === 'vorhaben') {
    listHint = 'Kartenreihenfolge ändern: dazu die Gruppierung aufheben.'
  } else if (canEdit && !sortable) {
    listHint = 'Kartenreihenfolge ändern: dazu genau einen Status-Filter wählen und den Archiv-Filter abwählen.'
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

      {/* Werkzeugleiste (Entwurf `.werkzeugleiste`, Z. 797–842, 1867–1885). */}
      <Box
        sx={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap', mb: '14px', px: '11px', py: '9px', borderRadius: '10px', border: `1px solid ${RAND}`, background: `linear-gradient(180deg, ${PLATTE_HOCH}, ${PLATTE_FUSS})`, boxShadow: SCHATTEN_PLATTE }}
      >
        <Box role="group" aria-label="Status-Filter" sx={CHIP_GRUPPE_SX}>
          {columns.map((col) => {
            const aktiv = activeFilters.has(col.id)
            return (
              <Chip key={col.id} label={col.name} aria-label={`Filter ${col.name}`} aria-pressed={aktiv}
                onClick={() => toggleFilter(col.id)} size="small" sx={chipSx(aktiv)} />
            )
          })}
          <Chip label="Archiv" aria-label="Filter Archiv" aria-pressed={archiveActive}
            onClick={() => toggleFilter(ARCHIVED)} size="small" sx={chipSx(archiveActive)} />
        </Box>
        <Box sx={CHIP_GRUPPE_SX}>
          <Chip
            label={
              <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                Überfällig
                {ueberfaelligZahl > 0 && (
                  <Box component="span" sx={{ ...ZAHL, fontSize: 10, px: '4px', borderRadius: '4px', color: MELDER.zinnob, bgcolor: `color-mix(in srgb, ${MELDER.zinnob} 18%, transparent)` }}>
                    {ueberfaelligZahl}
                  </Box>
                )}
              </Box>
            }
            aria-label="Filter Überfällig"
            aria-pressed={nurUeberfaellig}
            onClick={() => setNurUeberfaellig((v) => !v)}
            size="small"
            sx={chipSx(nurUeberfaellig)}
          />
        </Box>
        {members.length > 0 && (
          /* Wähler ohne sichtbare Beschriftung (Entwurf `.waehler`, Z. 833–842): Die Benennung trägt
             der Wert („Zuständig: …"), der zugängliche Name bleibt am Feld (Issue #986). */
          <TextField
            select
            size="small"
            value={zustaendig ?? ''}
            onChange={(e) => setZustaendig(e.target.value === '' ? null : Number(e.target.value))}
            slotProps={{ htmlInput: { 'aria-label': 'Zuständig' }, select: { native: true } }}
            sx={{
              '& .MuiOutlinedInput-root': { bgcolor: NUT, boxShadow: SCHATTEN_NUTE, borderRadius: '7px', fontSize: '11.5px', fontWeight: 500, color: 'text.secondary' },
              '& .MuiNativeSelect-select': { py: '4px', pl: '9px' },
            }}
          >
            <option value="">Zuständig: alle</option>
            {members.map((m) => (
              <option key={m.userId} value={m.userId}>
                Zuständig: {m.displayName}
              </option>
            ))}
          </TextField>
        )}
        {labels.length > 0 && (
          <Box role="group" aria-label="Label-Filter" sx={CHIP_GRUPPE_SX}>
            {labels.map((label) => {
              const aktiv = labelFilter.has(label.id)
              return (
                <Chip
                  key={label.id}
                  label={label.name}
                  aria-label={`Label-Filter ${label.name}`}
                  aria-pressed={aktiv}
                  onClick={() => toggleLabel(label.id)}
                  size="small"
                  sx={aktiv ? { ...labelChipSx(label.color), height: 26, borderRadius: '6px' } : { ...chipSx(false), color: label.color }}
                />
              )
            })}
          </Box>
        )}
        <Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Typography component="span" sx={{ fontSize: 11.5, color: 'text.secondary' }}>
            Gruppieren
          </Typography>
          <Box role="group" aria-label="Gruppieren" sx={CHIP_GRUPPE_SX}>
            <Chip label="Vorhaben" aria-pressed={gruppierung === 'vorhaben'} onClick={() => gruppierungWaehlen('vorhaben')} size="small" sx={chipSx(gruppierung === 'vorhaben')} />
            <Chip label="keine" aria-pressed={gruppierung === 'keine'} onClick={() => gruppierungWaehlen('keine')} size="small" sx={chipSx(gruppierung === 'keine')} />
          </Box>
        </Box>
      </Box>

      {listHint !== null && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
          {listHint}
        </Typography>
      )}

      {visible.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
          Keine Karten
        </Typography>
      ) : (
        <>
          <Box
            component="section"
            aria-label="Karten als Liste"
            sx={{ borderRadius: `${PANEL_RADIUS}px`, border: `1px solid ${RAND}`, bgcolor: PLATTE, boxShadow: SCHATTEN_PLATTE, overflow: 'clip' }}
          >
          {/* Kopfzeile: erhaben und mitlaufend (Entwurf `.tafel thead th`, Z. 880–893); Spalten per Drag
              umsortierbar (Excel-artig), per Klick nach Inhalt sortierbar. */}
          <Box
            data-testid="list-header"
            sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: 1.5, py: '10px', position: 'sticky', top: 'var(--app-content-top, 0px)', zIndex: 5, background: `linear-gradient(180deg, ${PLATTE_HOCH}, ${PLATTE_FUSS})`, borderBottom: `1px solid ${RAND}` }}
          >
            <Box sx={{ width: 20, flexShrink: 0 }} />
            {order.map((key) => (
              <Box
                key={key}
                draggable
                role="button"
                tabIndex={0}
                aria-label={headerLabel(key)}
                // Der Guard entspricht dem `resizingRef` beim Breiten-Ziehen (siehe `startResize`):
                // Ein HTML5-Drag löst zwar üblicherweise keinen Click aus, aber die Sortierung soll
                // sich darauf nicht verlassen. Kein zweites Muster für dasselbe Problem.
                onClick={() => { if (!headerDragRef.current) toggleSort(key) }}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggleSort(key) } }}
                onDragStart={(e) => { e.stopPropagation(); headerDragRef.current = true; setColDrag(key) }}
                onDragOver={(e) => { if (colDrag && colDrag !== key) { e.preventDefault(); setColOver(key) } }}
                onDrop={(e) => { e.preventDefault(); if (colDrag) { reorderColumns(colDrag, key) } setColDrag(null); setColOver(null) }}
                onDragEnd={() => {
                  setColDrag(null)
                  setColOver(null)
                  // Flag erst nach einem etwaigen Click-Event zurücksetzen — wie beim Resize.
                  setTimeout(() => { headerDragRef.current = false }, 0)
                }}
                sx={{
                  ...cellSx(key),
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: key === 'number' ? 'flex-end' : undefined,
                  cursor: 'grab',
                  userSelect: 'none',
                  borderBottom: '2px solid',
                  borderColor: colOver === key ? KUPFER : 'transparent',
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
                <Typography component="span" sx={{ ...ETIKETT, fontStretch: '116%', letterSpacing: '.13em', color: sort?.key === key ? KUPFER : TEXT_SCHWACH }}>
                  {COLUMN_META[key].label}
                </Typography>
                {sort?.key === key &&
                  (sort.dir === 'asc'
                    ? <ArrowUpwardIcon fontSize="inherit" sx={{ ml: 0.5, color: KUPFER }} />
                    : <ArrowDownwardIcon fontSize="inherit" sx={{ ml: 0.5, color: KUPFER }} />)}
              </Box>
            ))}
          </Box>

          {/* Dichte (AK 12, Plan #932 E16 a): Die Liste ist keine MUI-Tabelle, ihre Dichte steht deshalb
              hier. Vor #957 zeigte sie auf 1440 x 900 13 Zeilen bei 8 px Polsterung und 6 px Abstand. */}
          <Stack spacing={0.25} useFlexGap data-testid="listen-zeilen">
            {gruppen.map((gruppe) => [
              gruppierung === 'vorhaben' && <GruppenKopf key={`gruppe-${gruppe.epic?.id ?? 'ohne'}`} epic={gruppe.epic} zahl={gruppe.karten.length} />,
              ...gruppe.karten.map((card) => (
              <Box
                key={card.id}
                role="button"
                tabIndex={0}
                aria-label={`Detail öffnen: ${card.title}`}
                draggable={sortable}
                onDragStart={(e) => {
                  setRowDrag(card.id)
                  e.dataTransfer.setData('text/plain', String(card.id))
                  clearTimeout(zugTakt.current)
                  zugTakt.current = setTimeout(() => setBewegteZeile(card.id), 0)
                }}
                onDragOver={(e) => { if (validRowDrop(card)) { e.preventDefault(); setRowOver(card.id) } }}
                onDrop={(e) => { e.preventDefault(); void onRowDrop(card) }}
                onDragEnd={() => {
                  clearTimeout(zugTakt.current)
                  setRowDrag(null)
                  setRowOver(null)
                  setBewegteZeile(null)
                }}
                data-zieh-zustand={bewegteZeile === card.id ? 'bewegt' : undefined}
                data-ablage={rowOver === card.id ? 'aktiv' : undefined}
                onClick={() => { if (!resizingRef.current) setDetailCard(card) }}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setDetailCard(card) } }}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1.5,
                  // Papierfläche als Token: schaltet mit dem Erscheinungsbild (#957).
                  // Zeile der Tafel (Entwurf `.tafel td`, Z. 900–905): Haarlinie unten statt eigenem
                  // Kasten; der Status steht als Plakette in der Zeile, nicht als Kante.
                  bgcolor: 'background.paper',
                  borderBottom: `1px solid color-mix(in srgb, ${RAND} 50%, transparent)`,
                  px: 1.5,
                  py: 0.25,
                  cursor: 'pointer',
                  userSelect: 'none',
                  transition: 'background 120ms ease',
                  '&:hover': { background: `color-mix(in srgb, ${PLATTE_HOCH} 80%, ${KUPFER_SCHIMMER})` },
                  // Dieselben Bausteine wie auf dem Board: Platzhalter an der verlassenen Stelle,
                  // Ablagefläche an der Zeile, an deren Platz die bewegte landet.
                  ...(bewegteZeile === card.id ? PLATZHALTER_SX : {}),
                  ...ablageflaecheSx(rowOver === card.id),
                }}
              >
                {sortable && (
                  <DragIndicatorIcon
                    fontSize="small"
                    aria-label="Reihenfolge ändern"
                    sx={{ flexShrink: 0, color: 'action.disabled' }}
                  />
                )}
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
              </Box>
              )),
            ])}
          </Stack>
          </Box>
        </>
      )}

      {detailCard && (
        <CardDetailModal
          key={detailCard.id}
          card={detailCard}
          canEdit={canEdit}
          projectId={board?.projectId}
          canModerateComments={canModerate}
          epics={epics}
          columnName={columnById.get(detailCard.columnId)?.name}
          location={
            // Ohne geladenes Board gibt es keinen Ort zu nennen; die Liste zeigt dann ohnehin
            // keine Karten, aus denen sich das Modal öffnen ließe.
            board && {
              projectId: board.projectId,
              projectName,
              board: { id: board.id, name: board.name, columnName: columnById.get(detailCard.columnId)?.name },
            }
          }
          onClose={() => setDetailCard(null)}
          onChanged={() => { reloadCards(); void epicsApi.list(id).then(setEpics) }}
        />
      )}
    </Box>
  )
}
