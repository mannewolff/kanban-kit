import type { BoardColumn } from '../api/boards'

/** Ob eine Spalte fachlich "Done" ist (aus dem Namen). */
export const isDoneColumn = (name: string): boolean => name.toLowerCase().includes('done')

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
