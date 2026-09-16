/**
 * Anzeige-Kürzel und Farbe eines Epics, rein clientseitig abgeleitet (portiert aus der Toolbox).
 * Das Backend speichert das Kürzel optional; ohne Kürzel greift die Ableitung aus dem Titel.
 */

/** Farbton und getönte Fläche eines Palettenplatzes. */
export interface EpicFarbe {
  /** Farbton: Punkt, Kürzel und linke Kante der zugehörigen Karten. */
  hue: string
  /** Getönte Fläche hinter dem Kürzel. */
  tint: string
}

/**
 * Feste Palette mittel-kräftiger Töne für Epics, je Erscheinungsbild (#952, Plan #932 E10/E11).
 * `theme.ts` legt sie als CSS-Variablen an; {@link epicColor} und {@link epicTint} liefern
 * Verweise darauf. Das Modul importiert das Theme nicht — `theme.ts` liest die Werte von hier.
 *
 * **Der Tint ist ein eigener Wert, keine Rechnung auf dem Farbton.** Bis #952 bildete `EpicBadge`
 * die Fläche als `${hue}22`. Auf einem Variablen-Verweis ergäbe das ungültiges CSS, und die Fläche
 * verschwände still. Hell ist der Tint deshalb exakt der alte Wert als `rgba` (Deckkraft 34/255);
 * dunkel ist der Farbton aufgehellt, damit das Kürzel auf seiner Fläche 4,5:1 hält.
 */
const tint = (hex: string, deckkraft: number): string => {
  const [r, g, b] = [1, 3, 5].map((i) => Number.parseInt(hex.slice(i, i + 2), 16))
  return `rgba(${r},${g},${b},${deckkraft})`
}

const HELL = ['#534AB7', '#1D9E75', '#D4537E', '#185FA5', '#BA7517', '#993C1D', '#0F6E56', '#0C447C']
const DUNKEL = ['#BAB5F4', '#6BDDB5', '#F29DBB', '#8CBBEE', '#EDBE70', '#EC9C7B', '#62D1AD', '#8FB6E6']

export const EPIC_FARBWERTE: Readonly<Record<'light' | 'dark', ReadonlyArray<EpicFarbe>>> = {
  light: HELL.map((hue) => ({ hue, tint: tint(hue, 0.133) })),
  dark: DUNKEL.map((hue) => ({ hue, tint: tint(hue, 0.14) })),
}

function hashId(id: number): number {
  const s = String(id)
  let h = 0
  for (let i = 0; i < s.length; i++) {
    // codePointAt(i) ist hier immer definiert (i < s.length) — das Non-null-`!` vermeidet den
    // unerreichbaren `?? 0`-Zweig (100 % Branch) und hält zugleich codePointAt (Sonar S7758).
    h = (h * 31 + s.codePointAt(i)!) >>> 0
  }
  return h
}

/** Palettenplatz eines Vorhabens: stabil anhand seiner ID. */
function platz(id: number): number {
  return hashId(id) % EPIC_FARBWERTE.light.length
}

/**
 * Farbe eines Vorhabens: stabil aus der Palette anhand seiner ID, als Variablen-Verweis. Sie färbt
 * die **linke Kante** der zugehörigen Karten (Designsprache „Kante", #648); die Oberkante trägt den
 * Status.
 */
export function epicColor(id: number): string {
  return `var(--mb-palette-epic-${platz(id)}-hue)`
}

/** Getönte Fläche eines Vorhabens, aus demselben Palettenplatz wie {@link epicColor}. */
export function epicTint(id: number): string {
  return `var(--mb-palette-epic-${platz(id)}-tint)`
}

/**
 * Kürzel eines Epics: ein explizit gesetztes Kürzel hat Vorrang; sonst die Initialen der
 * (max. drei ersten) Titelwörter in Großbuchstaben. Leerer Titel ohne Kürzel → „VORH".
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
  return initials || 'VORH'
}
