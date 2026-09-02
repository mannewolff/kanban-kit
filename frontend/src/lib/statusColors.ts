/**
 * Statusfarben je Spalte, aus dem Spaltennamen abgeleitet. Bewusste Ausnahme von
 * "Farben nur über das Theme": Board-Status ist eine feste, kleine, semantische Menge —
 * ein zentraler Ort statt verstreuter Hex-Werte. Entsättigt auf die Marken-Familie
 * (CLAUDE-design.md): weiche, helle Tints als Pill-Flächen, dunkler Text derselben Familie, ein
 * ruhiger Akzent-Punkt.
 *
 * Das Modul trägt ausschließlich Status-Farben (#648, E2). Jedes andere Token gehört ins Theme —
 * zwei Farbquellen laufen auseinander, ohne dass ein Test es merkt.
 */
export interface StatusColorSet {
  /** Weiche Tint-Fläche (Pills/Badges). */
  bg: string
  /** Textfarbe auf {@link bg}. */
  text: string
  /** Akzentfarbe des Status: Oberkante von Spalte und Karte, Punkt in Listenansichten. */
  dot: string
}

const NEUTRAL: StatusColorSet = { bg: '#F1F5F6', text: '#5F7A7F', dot: '#8FA6AB' }

// Reihenfolge nach Namensmuster; erster Treffer gewinnt (keine Überschneidungen).
const PALETTE: ReadonlyArray<{ match: string; colors: StatusColorSet }> = [
  { match: 'done', colors: { bg: '#E4F3EC', text: '#14624A', dot: '#2E9E7A' } },
  { match: 'review', colors: { bg: '#F7EAE4', text: '#8A3F28', dot: '#C46B4E' } },
  { match: 'progress', colors: { bg: '#FAF3E3', text: '#7A5B12', dot: '#C99A2E' } },
  { match: 'ready', colors: { bg: '#E1F0F2', text: '#1E5F68', dot: '#2F8C97' } },
  { match: 'backlog', colors: { bg: '#EDF5F6', text: '#1E5F68', dot: '#5BABB5' } },
]

/** Volles Farbset einer Spalte (Tint-Fläche, Text, Punkt). */
export function statusColors(name: string): StatusColorSet {
  const n = name.toLowerCase()
  for (const entry of PALETTE) {
    if (n.includes(entry.match)) {
      return entry.colors
    }
  }
  return NEUTRAL
}

/** Farbe für archivierte Karten (Badges/Listen). */
export const ARCHIVED_STATUS_COLOR: StatusColorSet = { bg: '#F0F2F2', text: '#5F7A7F', dot: '#9FB0B4' }
