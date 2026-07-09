/**
 * Anzeige-Kürzel und Farbe eines Epics, rein clientseitig abgeleitet (portiert aus der Toolbox).
 * Das Backend speichert das Kürzel optional; ohne Kürzel greift die Ableitung aus dem Titel.
 */

/** Feste Palette mittel-kräftiger Töne für Epics. */
export const EPIC_PALETTE: readonly string[] = [
  '#534AB7', '#1D9E75', '#D4537E', '#185FA5',
  '#BA7517', '#993C1D', '#0F6E56', '#0C447C',
]

function hashId(id: number): number {
  const s = String(id)
  let h = 0
  for (let i = 0; i < s.length; i++) {
    h = (h * 31 + s.charCodeAt(i)) >>> 0
  }
  return h
}

/** Farbe eines Epics: stabil aus der Palette anhand seiner ID. */
export function epicColor(id: number): string {
  return EPIC_PALETTE[hashId(id) % EPIC_PALETTE.length]
}

/**
 * Kürzel eines Epics: ein explizit gesetztes Kürzel hat Vorrang; sonst die Initialen der
 * (max. drei ersten) Titelwörter in Großbuchstaben. Leerer Titel ohne Kürzel → „EPIC".
 */
export function epicShortcode(title: string, explicit?: string | null): string {
  const trimmed = explicit?.trim()
  if (trimmed) return trimmed
  const initials = title
    .split(/\s+/)
    .filter(Boolean)
    .map((w) => w[0])
    .join('')
    .slice(0, 3)
    .toUpperCase()
  return initials || 'EPIC'
}
