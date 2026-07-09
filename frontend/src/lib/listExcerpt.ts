/** Reine Hilfsfunktionen für die Listen-Ansicht (portiert aus der Toolbox), frei von React. */

/** Minimale, maximale und Default-Breite der Beschreibungs-Spalte in Prozent. */
export const EXCERPT_MIN_PCT = 20
export const EXCERPT_MAX_PCT = 75
export const EXCERPT_DEFAULT_PCT = 30

/** Klemmt einen Prozentwert in den erlaubten Bereich; NaN fällt auf den Default. */
export function clampExcerptWidth(pct: number): number {
  if (Number.isNaN(pct)) return EXCERPT_DEFAULT_PCT
  return Math.min(EXCERPT_MAX_PCT, Math.max(EXCERPT_MIN_PCT, pct))
}

/**
 * Strippt Markdown-Steuerzeichen aus einem Body und macht daraus eine einzeilige Vorschau:
 * entfernt Überschriften-Hashes, Betonungszeichen und Backticks, kollabiert Zeilenumbrüche.
 */
export function stripMarkdown(raw: string): string {
  return raw
    .replace(/\r?\n/g, ' ')
    .replace(/#+\s*/g, '')
    .replace(/[*_`]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}
