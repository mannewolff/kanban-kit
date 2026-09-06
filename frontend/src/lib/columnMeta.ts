import type { BoardColumn } from '../api/boards'

/** Ob eine Spalte fachlich "Done" ist (aus dem Namen). */
export const isDoneColumn = (name: string): boolean => name.toLowerCase().includes('done')

/** Die fünf kanonischen Kanban-Zustände. */
export type CanonicalState = 'BACKLOG' | 'READY' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE'

const CANONICAL_BY_NORMALIZED: Record<string, CanonicalState> = {
  backlog: 'BACKLOG',
  ready: 'READY',
  inprogress: 'IN_PROGRESS',
  inreview: 'IN_REVIEW',
  done: 'DONE',
}

/**
 * Bildet einen Spaltennamen auf einen kanonischen Zustand ab; `undefined`, wenn kein Treffer.
 *
 * Wortgleiche Nachbildung von `KanbanCompatService.canonicalKey` (Java,
 * `kanbancompat/application/KanbanCompatService.java`) — inklusive der ASCII-Normalisierung
 * (`[^a-z]` statt `\p{L}`). Beide Stellen müssen stets gemeinsam geändert werden, sonst laufen
 * Frontend-Anzeige und Kanban-kompatibler Ingest auseinander.
 */
export function canonicalColumnKey(name: string): CanonicalState | undefined {
  return CANONICAL_BY_NORMALIZED[name.toLowerCase().replace(/[^a-z]/g, '')]
}

/**
 * Die kanonischen Spalten des Boards außer der aktuellen — nach `position` sortiert, ohne das
 * übergebene Array zu verändern. Mehrere Spalten mit demselben kanonischen Zustand werden alle
 * geliefert (wie `keyByColumn` im Backend). Ist `currentColumnId` unbekannt oder die aktuelle
 * Spalte selbst nicht-kanonisch, entfällt schlicht der Ausschluss.
 */
export function otherCanonicalColumns(
  columns: BoardColumn[],
  currentColumnId: number,
): BoardColumn[] {
  return [...columns]
    .sort((a, b) => a.position - b.position)
    .filter((c) => c.id !== currentColumnId && canonicalColumnKey(c.name) !== undefined)
}

/**
 * Die unmittelbar benachbarten Spalten. `columns` ist stets nach `position` sortiert — "links" und
 * "rechts" sind daher schlicht der vorige bzw. nächste Index. Am Rand (und bei einer unbekannten
 * Spalte) fehlt der jeweilige Nachbar.
 */
export function neighbourColumns(
  columns: BoardColumn[],
  columnId: number,
): { left: BoardColumn | null; right: BoardColumn | null } {
  const index = columns.findIndex((c) => c.id === columnId)
  if (index < 0) {
    return { left: null, right: null }
  }
  return {
    left: index > 0 ? columns[index - 1] : null,
    right: index < columns.length - 1 ? columns[index + 1] : null,
  }
}
