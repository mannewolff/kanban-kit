import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import type { BoardColumn } from '../../api/boards'
import type { Card } from '../../api/cards'
import type { SortDirection } from '../../api/columns'
import type { Epic } from '../../api/epics'
import type { Label } from '../../api/labels'
import type { Member } from '../../api/members'
import { epicOfCard } from '../../lib/cardEpic'
import { MELDER, NUT, PANEL_RADIUS, RAND, SCHATTEN_NUTE } from '../../theme'
import { ablageflaecheSx, type Dichte } from '../boardSurfaceSx'
import { Belastungsskala } from './Belastungsskala'
import { BoardKarte } from './BoardKarte'
import { SpaltenKopf } from './SpaltenKopf'

interface Props {
  column: BoardColumn
  /**
   * Voller aktiver Bestand der Spalte. Nicht der gefilterte: Der Zähler trägt die Belastungsgrenze
   * und meldete sonst bei gesetztem Vorhaben-Filter eine eingehaltene Grenze, die verletzt ist.
   */
  count: number
  /** Wie viele Karten der Spalte gerade verdeckt sind — die Vereinigung beider Achsen (E4). */
  hiddenCount: number
  /** Die Karten, die die Spalte gerade zeigt. */
  angezeigteKarten: Card[]
  epics: Epic[]
  /** Done-Spalte: Die Karten tragen Archiv-Countdown und Überfälligkeit. */
  done: boolean
  dichte: Dichte
  selectionMode: boolean
  selectedIds: ReadonlySet<number>
  canEdit: boolean
  showStructureEdit: boolean
  members: Member[]
  boardLabels: Label[]
  retentionDays: number
  /** Richtung, die der nächste Klick auf den Sortier-Knopf auslöst. */
  naechsteRichtung: SortDirection
  /** Für diese Spalte läuft gerade ein Sortier-Aufruf. */
  sortiertGerade: boolean
  /** Welche Karte gerade gezogen wird, `null` = keine. */
  dragCardId: number | null
  /** Über welcher Spalte die gezogene Karte gerade steht. */
  ablageSpalteId: number | null
  /** Aus welcher Spalte die gezogene Karte stammt — die Herkunft ist keine Ablage. */
  herkunftsSpalteId: number | undefined
  /** Welche Spalte gerade gezogen wird (Spalten-Umsortierung), `null` = keine. */
  colDrag: number | null
  /** Eine gezogene Karte steht über dieser Spalte. */
  onKarteUeber: () => void
  /** Eine Karte wurde hier abgelegt; `cardId` ist `0`, wenn die Nutzlast keine Karte trug. */
  onKarteAbgelegt: (cardId: number) => void
  onKarteZugBeginn: (e: React.DragEvent, card: Card) => void
  onKarteZugEnde: () => void
  onKarteWaehlen: (card: Card) => void
  onKarteOeffnen: (card: Card) => void
  onKarteMenu: (card: Card, anchor: HTMLElement) => void
  onEpicOpen?: (epic: Epic) => void
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
 * Eine Spalte des Boards als Nut im Grund (Entwurf `.spalte`, Z. 735–743): Kopf, Belastungsskala und
 * die Ablagefläche mit den Karten. Bei erreichter Belastungsgrenze trägt sie eine bernsteinfarbene
 * Haarlinie (`.spalte-warn`, Z. 868).
 *
 * Eigene Datei seit Plan #1042 (P12): In `BoardView` trug der Callback von `columns.map` laut
 * SonarCloud die kognitive Komplexität 31.
 */
export function BoardSpalte({
  column,
  count,
  hiddenCount,
  angezeigteKarten,
  epics,
  done,
  dichte,
  selectionMode,
  selectedIds,
  canEdit,
  showStructureEdit,
  members,
  boardLabels,
  retentionDays,
  naechsteRichtung,
  sortiertGerade,
  dragCardId,
  ablageSpalteId,
  herkunftsSpalteId,
  colDrag,
  onKarteUeber,
  onKarteAbgelegt,
  onKarteZugBeginn,
  onKarteZugEnde,
  onKarteWaehlen,
  onKarteOeffnen,
  onKarteMenu,
  onEpicOpen,
  onSpaltenAuswahl,
  onAusblendungAufheben,
  onSortieren,
  onBearbeiten,
  onLoeschen,
  onZugBeginn,
  onAblage,
  onZugEnde,
}: Readonly<Props>) {
  // Einmal berechnet, zweimal gebraucht (Rand der Spalte, Zähler im Kopf). Zwei Fassungen desselben
  // Vergleichs liefen bei einer Änderung auseinander (Plan #1042, P12).
  const grenzeErreicht = column.wipLimit != null && count >= column.wipLimit
  // Dieselbe Überlegung für die Ablagefläche: Kennung (`data-ablage`) und Fläche lesen einen Wert.
  const ablageAktiv = dragCardId != null && ablageSpalteId === column.id && herkunftsSpalteId !== column.id
  // Vorab als Konstante, damit im `border` kein Template-Literal im Template-Literal steht
  // (#1028, S4624) — verschachtelt war der Ausdruck kaum noch zu lesen.
  const randFarbe = grenzeErreicht ? `color-mix(in srgb, ${MELDER.bernst} 42%, ${RAND})` : RAND
  const angezeigteIds = angezeigteKarten.map((c) => c.id)
  return (
    <Paper
      data-testid={`column-${column.id}`}
      elevation={0}
      onDragOver={(e) => {
        e.preventDefault()
        if (dragCardId != null) onKarteUeber()
      }}
      onDrop={(e) => {
        e.preventDefault()
        onKarteAbgelegt(Number(e.dataTransfer.getData('text/plain')))
      }}
      sx={{
        flex: '1 1 0',
        minWidth: 230,
        display: 'flex',
        flexDirection: 'column',
        gap: '9px',
        p: '10px',
        background: `linear-gradient(180deg, ${NUT}, color-mix(in srgb, ${NUT} 80%, var(--mb-palette-warte-grund)))`,
        border: `1px solid ${randFarbe}`,
        borderRadius: `${PANEL_RADIUS}px`,
        boxShadow: SCHATTEN_NUTE,
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '8px', px: '4px', pt: '2px' }}>
        <SpaltenKopf
          column={column}
          count={count}
          grenzeErreicht={grenzeErreicht}
          hiddenCount={hiddenCount}
          angezeigteIds={angezeigteIds}
          selectedIds={selectedIds}
          selectionMode={selectionMode}
          canEdit={canEdit}
          showStructureEdit={showStructureEdit}
          naechsteRichtung={naechsteRichtung}
          sortiertGerade={sortiertGerade}
          colDrag={colDrag}
          onSpaltenAuswahl={onSpaltenAuswahl}
          onAusblendungAufheben={onAusblendungAufheben}
          onSortieren={onSortieren}
          onBearbeiten={onBearbeiten}
          onLoeschen={onLoeschen}
          onZugBeginn={onZugBeginn}
          onAblage={onAblage}
          onZugEnde={onZugEnde}
        />
        <Belastungsskala column={column} count={count} />
      </Box>

      <Stack
        spacing={1}
        data-testid={`ablage-${column.id}`}
        data-ablage={ablageAktiv ? 'aktiv' : undefined}
        sx={{
          flex: 1,
          gap: '9px',
          minHeight: 120,
          '& > :not(style) ~ :not(style)': { mt: 0 },
          ...ablageflaecheSx(ablageAktiv),
        }}
      >
        {angezeigteKarten.map((card) => (
          <BoardKarte
            key={card.id}
            card={card}
            epic={epicOfCard(card, epics)}
            done={done}
            dichte={dichte}
            selectionMode={selectionMode}
            selected={selectedIds.has(card.id)}
            canEdit={canEdit}
            bewegt={dragCardId === card.id}
            members={members}
            boardLabels={boardLabels}
            retentionDays={retentionDays}
            onDragStart={(e) => onKarteZugBeginn(e, card)}
            onDragEnd={onKarteZugEnde}
            onSelect={() => onKarteWaehlen(card)}
            onOpen={() => onKarteOeffnen(card)}
            onMenu={(anchor) => onKarteMenu(card, anchor)}
            onEpicOpen={onEpicOpen}
          />
        ))}
      </Stack>
    </Paper>
  )
}
