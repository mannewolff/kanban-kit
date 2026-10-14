import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import SortIcon from '@mui/icons-material/Sort'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Checkbox from '@mui/material/Checkbox'
import IconButton from '@mui/material/IconButton'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import type { BoardColumn } from '../../api/boards'
import type { SortDirection } from '../../api/columns'
import { useT } from '../../i18n/SpracheContext'
import type { Woerterbuch } from '../../i18n/de'
import { spaltenAuswahl, type SpaltenAuswahlZustand } from '../../lib/boardOps'
import { statusColors } from '../../lib/statusColors'
import { useTabletQuer } from '../../lib/tabletQuer'
import type { FingerZiehen } from '../../lib/useFingerZiehen'
import { FINGER_GRIFF_SX } from '../boardSurfaceSx'
import {
  LED_RING,
  MELDER,
  NUT,
  RAND,
  SCHATTEN_NUTE,
  SCHRIFT_ANZEIGE,
  TEXT_SCHWACH,
  ZAHL,
} from '../../theme'
import { Belastungsskala } from './Belastungsskala'

/** Zugänglicher Name des Spalten-Kästchens: Er sagt, was der nächste Klick tut. */
const spaltenAuswahlLabel = (t: Woerterbuch, zustand: SpaltenAuswahlZustand, spalte: string) =>
  zustand === 'alle' ? t.BOARD.AUSWAHL_AUFHEBEN(spalte) : t.BOARD.ALLE_AUSWAEHLEN(spalte)

/** Beschriftung des Sortier-Toggles: benennt die Richtung, die der nächste Klick auslöst. */
const sortByNumberLabel = (t: Woerterbuch, columnName: string, next: SortDirection) =>
  t.BOARD.NACH_NUMMER_SORTIEREN(columnName, next === 'ASC')

/**
 * Zugänglicher Name der Spaltenmarke: Zahl, Objekt und Rückweg in einem. Achsenneutral formuliert,
 * denn gezählt wird die Vereinigung aus ausgeblendeten Vorhaben und Vorhaben-Filter (Plan #620,
 * E4/E9) — eine Spalte meldet eine Zahl, nicht zwei Ursachen.
 */
/**
 * Ereignisse der Spalten-Umsortierung. Sie hängen am Kopf, weil er im Struktur-Editiermodus das
 * Ziehbild der ganzen Spalte trägt, und sie stehen als eigene Funktion da: Ihre drei Verzweigungen
 * zählten sonst in der Komplexität des Kopfes mit (Plan #1042, P12). Ausserhalb des
 * Struktur-Editiermodus liefert sie keine Handler — genau wie die frühere `? … : undefined`-Form.
 */
function spaltenZiehHandler(
  showStructureEdit: boolean,
  p: Readonly<{
    columnId: number
    colDrag: number | null
    onZugBeginn: () => void
    onAblage: (fromId: number) => void
    onZugEnde: () => void
  }>,
) {
  if (!showStructureEdit) return {}
  return {
    onDragStart: (e: React.DragEvent) => {
      e.stopPropagation()
      p.onZugBeginn()
    },
    onDragOver: (e: React.DragEvent) => {
      if (p.colDrag != null && p.colDrag !== p.columnId) {
        e.preventDefault()
        e.stopPropagation()
      }
    },
    onDrop: (e: React.DragEvent) => {
      if (p.colDrag != null) {
        e.preventDefault()
        e.stopPropagation()
        p.onAblage(p.colDrag)
      }
      p.onZugEnde()
    },
  }
}

interface Props {
  column: BoardColumn
  /** Zähler über den vollen Bestand der Spalte — er trägt die Belastungsgrenze. */
  count: number
  /** Die Belastungsgrenze ist erreicht: der Zähler steht bernstein statt grau. */
  grenzeErreicht: boolean
  /** Wie viele Karten der Spalte gerade verdeckt sind; `0` = keine Marke. */
  hiddenCount: number
  /** IDs der angezeigten Karten — Grundlage des Spalten-Kästchens. */
  angezeigteIds: number[]
  selectedIds: ReadonlySet<number>
  selectionMode: boolean
  canEdit: boolean
  showStructureEdit: boolean
  /** Richtung, die der nächste Klick auf den Sortier-Knopf auslöst. */
  naechsteRichtung: SortDirection
  /** Für diese Spalte läuft gerade ein Sortier-Aufruf; der Knopf bleibt so lange gesperrt. */
  sortiertGerade: boolean
  /** Welche Spalte gerade gezogen wird (Spalten-Umsortierung), `null` = keine. */
  colDrag: number | null
  onSpaltenAuswahl: (angezeigteIds: number[]) => void
  onAusblendungAufheben: () => void
  onSortieren: () => void
  onBearbeiten: () => void
  onLoeschen: () => void
  onZugBeginn: () => void
  onAblage: (fromId: number) => void
  onZugEnde: () => void
  /** Handler des Fingerzugs aus `useFingerZiehen` (Plan #1707, E3); greifen nur im Tablet-Modus. */
  fingerGriff?: ReturnType<FingerZiehen['griff']>
  /** Stand des Fingerzugs, solange diese Spalte gegriffen ist. */
  fingerZustand?: 'wartet' | 'zieht'
}

/**
 * Kopf einer Spalte: Status-LED, Name, Zähler mit Belastungsgrenze, Marke der ausgeblendeten
 * Karten, Sortieren und — im Struktur-Editiermodus — Bearbeiten, Löschen und das Ziehen der Spalte.
 * Die Belastungsskala gehört seit Plan #1707 (E6) mit hierher, weil sie auf dem Tablet quer in die
 * zweite Zeile des Kopfes wandert.
 *
 * Eigene Datei seit Plan #1042 (P12): In der Spalten-Schleife von `BoardView` trug der Callback die
 * kognitive Komplexität 31.
 */
export function SpaltenKopf({
  column,
  count,
  grenzeErreicht,
  hiddenCount,
  angezeigteIds,
  selectedIds,
  selectionMode,
  canEdit,
  showStructureEdit,
  naechsteRichtung,
  sortiertGerade,
  colDrag,
  onSpaltenAuswahl,
  onAusblendungAufheben,
  onSortieren,
  onBearbeiten,
  onLoeschen,
  onZugBeginn,
  onAblage,
  onZugEnde,
  fingerGriff,
  fingerZustand,
}: Readonly<Props>) {
  const t = useT()
  const tabletQuer = useTabletQuer()
  const colors = statusColors(column.name)
  const spaltenZustand = spaltenAuswahl(angezeigteIds, selectedIds)
  const sortierLabel = sortByNumberLabel(t, column.name, naechsteRichtung)
  // Auf dem Tablet quer ist die Spalte rund 160 px schmal; mit drei Symbolknöpfen und Skala in
  // einer Zeile bliebe vom Namen nichts. Darum dort zwei Zeilen, am Desktop eine wie bisher (E6).
  const zweizeilig = tabletQuer && showStructureEdit
  // Auf dem Tablet quer greift der Finger den Kopf über Pointer-Ereignisse (Plan #1707, E3); natives
  // Ziehen bleibt dort aus, damit iPadOS-Safari kein zweites Ziehen daneben startet.
  const kopf = {
    'data-testid': `column-header-${column.id}`,
    draggable: showStructureEdit && !tabletQuer,
    ...spaltenZiehHandler(showStructureEdit, { columnId: column.id, colDrag, onZugBeginn, onAblage, onZugEnde }),
    onDragEnd: onZugEnde,
    'data-zieh-zustand': fingerZustand,
    ...(zweizeilig ? fingerGriff : {}),
  }
  const griffSx = zweizeilig ? FINGER_GRIFF_SX : {}
  const kennung = (
    <>
      {selectionMode && (
        // Ganze Spalte auf einmal: dieselbe Bildsprache wie der Haken an der Karte, mit
        // dem Strich für „einige". Der Klick bleibt im Kästchen, damit die Handler des
        // Kopfes (Sortieren, Ziehen) nicht mitlaufen.
        <Checkbox
          size="small"
          checked={spaltenZustand === 'alle'}
          indeterminate={spaltenZustand === 'einige'}
          disabled={angezeigteIds.length === 0}
          onChange={() => onSpaltenAuswahl(angezeigteIds)}
          onClick={(e) => e.stopPropagation()}
          slotProps={{ input: { 'aria-label': spaltenAuswahlLabel(t, spaltenZustand, column.name) } }}
          sx={{ p: 0, flex: 'none' }}
        />
      )}
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
        sx={{ ...ZAHL, fontSize: 10.5, color: grenzeErreicht ? MELDER.bernst : TEXT_SCHWACH }}
      >
        {column.wipLimit != null ? `${count}/${column.wipLimit}` : count}
      </Typography>
      {hiddenCount > 0 && (
        // Ausgeblendet heißt sichtbar ausgeblendet: Jede Spalte, aus der etwas
        // verschwunden ist, sagt es an und trägt den Rückweg an Ort und Stelle. Ein
        // echter Button, damit der Rückweg auch mit der Tastatur erreichbar ist.
        <Button
          size="small"
          onClick={onAusblendungAufheben}
          aria-label={t.BOARD.AUSGEBLENDET_EINBLENDEN(hiddenCount)}
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
          {t.BOARD.AUSGEBLENDET(hiddenCount)}
        </Button>
      )}
    </>
  )
  const knoepfe = (
    <>
      {canEdit && (
        <Tooltip title={sortierLabel}>
          {/* Kein span-Wrapper um den Button: MUI legt den Tooltip-Titel als aria-label auf
              sein direktes Kind, ein Wrapper trüge den Namen also doppelt (span + Button).
              Preis dafür: während des laufenden Aufrufs (disabled) zeigt der Tooltip nicht. */}
          <IconButton size="small"
            aria-label={sortierLabel}
            disabled={sortiertGerade}
            onClick={onSortieren} sx={{ color: 'text.secondary' }}>
            <SortIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      )}
      {showStructureEdit && (
        <Tooltip title={t.BOARD.SPALTE_BEARBEITEN}>
          <IconButton size="small" aria-label={t.BOARD.SPALTE_BEARBEITEN_ARIA(column.name)}
            onClick={onBearbeiten} sx={{ color: 'text.secondary' }}>
            <EditOutlinedIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      )}
      {showStructureEdit && (
        <Tooltip title={t.BOARD.SPALTE_LOESCHEN}>
          <IconButton size="small" aria-label={t.BOARD.SPALTE_LOESCHEN_ARIA(column.name)}
            onClick={onLoeschen} sx={{ color: 'text.secondary' }}>
            <DeleteOutlineIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      )}
    </>
  )
  const skala = <Belastungsskala column={column} count={count} />
  if (zweizeilig) {
    // Beide Zeilen liegen im ziehbaren Kopf: Die Spalte bleibt an jeder Stelle des Kopfes greifbar.
    return (
      <Box {...kopf} sx={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1, minWidth: 0, cursor: 'grab', ...griffSx }}>
        <Box data-testid={`column-header-kennung-${column.id}`} sx={{ display: 'flex', alignItems: 'center', gap: '8px', minWidth: 0 }}>
          {kennung}
        </Box>
        <Box data-testid={`column-header-werkzeuge-${column.id}`} sx={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
          {skala}
          <Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center' }}>{knoepfe}</Box>
        </Box>
      </Box>
    )
  }
  // Am Desktop steht die Skala wie bisher neben dem Kopf, nicht in ihm: Das Fragment legt sie als
  // Geschwister in die Kopfzeile der Spalte.
  return (
    <>
      <Box {...kopf} sx={{ display: 'flex', alignItems: 'center', gap: '8px', flex: 1, minWidth: 0, cursor: showStructureEdit ? 'grab' : undefined }}>
        {kennung}
        {knoepfe}
      </Box>
      {skala}
    </>
  )
}
