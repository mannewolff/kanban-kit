/**
 * Helfer rund um das Fälligkeitsdatum einer Karte. Reine Funktionen (fixes `now` injizierbar),
 * damit die Zeitlogik deterministisch testbar bleibt und nicht als „unrein" im Render gilt.
 */

/** Ob eine Karte überfällig ist: Fälligkeit in der Vergangenheit und nicht in einer Done-Spalte. */
export function isOverdue(dueDate: string | null, isDone: boolean, now: number = Date.now()): boolean {
  if (dueDate == null || isDone) {
    return false
  }
  return new Date(dueDate).getTime() < now
}

/** Fälligkeitsdatum menschenlesbar (de-DE, nur Datum). */
export function formatDueDate(dueDate: string): string {
  return new Date(dueDate).toLocaleDateString('de-DE')
}

/** Wandelt eine `YYYY-MM-DD`-Eingabe in einen ISO-Zeitstempel (Mitternacht UTC) oder `null`. */
export function dueInputToIso(value: string): string | null {
  return value ? `${value}T00:00:00Z` : null
}
