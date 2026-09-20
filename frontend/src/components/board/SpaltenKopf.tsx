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
import { spaltenAuswahl, type SpaltenAuswahlZustand } from '../../lib/boardOps'
import { statusColors } from '../../lib/statusColors'
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

/** Zugänglicher Name des Spalten-Kästchens: Er sagt, was der nächste Klick tut. */
const spaltenAuswahlLabel = (zustand: SpaltenAuswahlZustand, spalte: string) =>
  zustand === 'alle' ? `Auswahl in ${spalte} aufheben` : `Alle Karten in ${spalte} auswählen`

/** Beschriftung des Sortier-Toggles: benennt die Richtung, die der nächste Klick auslöst. */
const sortByNumberLabel = (columnName: string, next: SortDirection) =>
  `Spalte ${columnName} nach Nummer ${next === 'ASC' ? 'aufsteigend' : 'absteigend'} sortieren`

/**
 * Zugänglicher Name der Spaltenmarke: Zahl, Objekt und Rückweg in einem. Achsenneutral formuliert,
 * denn gezählt wird die Vereinigung aus ausgeblendeten Vorhaben und Vorhaben-Filter (Plan #620,
 * E4/E9) — eine Spalte meldet eine Zahl, nicht zwei Ursachen.
 */
const hiddenBadgeLabel = (count: number) =>
  `${count} ${count === 1 ? 'Karte' : 'Karten'} ausgeblendet, einblenden`

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
}

/**
 * Kopf einer Spalte: Status-LED, Name, Zähler mit Belastungsgrenze, Marke der ausgeblendeten
 * Karten, Sortieren und — im Struktur-Editiermodus — Bearbeiten, Löschen und das Ziehen der Spalte.
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
}: Readonly<Props>) {
  const colors = statusColors(column.name)
  const spaltenZustand = spaltenAuswahl(angezeigteIds, selectedIds)
  const sortierLabel = sortByNumberLabel(column.name, naechsteRichtung)
  return (
    <Box
      data-testid={`column-header-${column.id}`}
      draggable={showStructureEdit}
      {...spaltenZiehHandler(showStructureEdit, { columnId: column.id, colDrag, onZugBeginn, onAblage, onZugEnde })}
      onDragEnd={onZugEnde}
      sx={{ display: 'flex', alignItems: 'center', gap: '8px', flex: 1, minWidth: 0, cursor: showStructureEdit ? 'grab' : undefined }}
    >
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
          slotProps={{ input: { 'aria-label': spaltenAuswahlLabel(spaltenZustand, column.name) } }}
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
        <Tooltip title="Spalte bearbeiten">
          <IconButton size="small" aria-label={`Spalte ${column.name} bearbeiten`}
            onClick={onBearbeiten} sx={{ color: 'text.secondary' }}>
            <EditOutlinedIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      )}
      {showStructureEdit && (
        <Tooltip title="Spalte löschen">
          <IconButton size="small" aria-label={`Spalte ${column.name} löschen`}
            onClick={onLoeschen} sx={{ color: 'text.secondary' }}>
            <DeleteOutlineIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      )}
    </Box>
  )
}
