/**
 * Statusfarben je Spalte, aus dem Spaltennamen abgeleitet (portiert aus der Toolbox,
 * kit/board-ui.mjs). Bewusste Ausnahme von "Farben nur über das Theme": Board-Status ist
 * eine feste, kleine, semantische Menge — ein zentraler Ort statt verstreuter Hex-Werte.
 */
export interface StatusColorSet {
  /** Hintergrund für Spalten-Header. */
  bg: string
  /** Textfarbe auf {@link bg}. */
  text: string
  /** Farbe des Status-Punkts im Header. */
  dot: string
}

const NEUTRAL: StatusColorSet = { bg: '#dfe1e6', text: '#42526e', dot: '#6b7280' }

// Reihenfolge nach Namensmuster; erster Treffer gewinnt (keine Überschneidungen).
const PALETTE: ReadonlyArray<{ match: string; colors: StatusColorSet }> = [
  { match: 'done', colors: { bg: '#e3fcef', text: '#006644', dot: '#0e8a16' } },
  { match: 'review', colors: { bg: '#ffedeb', text: '#bf2600', dot: '#d93f0b' } },
  { match: 'progress', colors: { bg: '#fffae6', text: '#7a6000', dot: '#e4b400' } },
  { match: 'ready', colors: { bg: '#deebff', text: '#0747a6', dot: '#0075ca' } },
]

/** Volles Farbset einer Spalte (Header-Hintergrund, Text, Punkt). */
export function statusColors(name: string): StatusColorSet {
  const n = name.toLowerCase()
  for (const entry of PALETTE) {
    if (n.includes(entry.match)) {
      return entry.colors
    }
  }
  return NEUTRAL
}

/** Neutrale Spaltenfläche (Kit: #ebecf0). */
export const COLUMN_SURFACE_BG = '#ebecf0'
