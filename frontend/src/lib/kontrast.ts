/**
 * Kontrastrechnung nach WCAG 2.1 — die eine Stelle, an der sie im Frontend steht. Vorher lag sie
 * als lokale Hilfsfunktion in `theme.test.ts` und `nachtlaufDesign.test.ts`; zwei Kopien einer
 * Rechenvorschrift laufen auseinander, ohne dass ein Test es merkt.
 */

const HEX = /^#(?:[0-9a-f]{3}|[0-9a-f]{6})$/i

/** Normiert `#abc` und `#aabbcc` (beliebige Schreibung) auf sechs Stellen; alles andere wirft. */
function sechsStellen(hex: string): string {
  if (!HEX.test(hex)) {
    throw new Error(`Kein Hexwert der Form #rgb oder #rrggbb: ${JSON.stringify(hex)}`)
  }
  const ziffern = hex.slice(1)
  return ziffern.length === 3
    ? [...ziffern].map((z) => z + z).join('')
    : ziffern
}

/** Linearisierter Farbkanal aus zwei Hexziffern. */
function kanal(paar: string): number {
  const v = Number.parseInt(paar, 16) / 255
  return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4
}

/** Relative Leuchtdichte (0 für Schwarz bis 1 für Weiß) eines Hexwerts `#rgb` oder `#rrggbb`. */
export function leuchtdichte(hex: string): number {
  const z = sechsStellen(hex)
  return 0.2126 * kanal(z.slice(0, 2)) + 0.7152 * kanal(z.slice(2, 4)) + 0.0722 * kanal(z.slice(4, 6))
}

/** Kontrastverhältnis zweier Hexwerte, von 1 (gleich) bis 21 (Schwarz gegen Weiß); symmetrisch. */
export function kontrast(hexA: string, hexB: string): number {
  const a = leuchtdichte(hexA)
  const b = leuchtdichte(hexB)
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05)
}
