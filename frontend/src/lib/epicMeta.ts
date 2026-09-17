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
 * Die Schild-Töne des Leitstand-Entwurfs (#978, `docs/entwurf-leitstand.html` Z. 780–790: Kupfer,
 * Stahl, Grün, Bernstein), je Erscheinungsbild. `theme.ts` legt sie als CSS-Variablen an;
 * {@link epicColor} und {@link epicTint} liefern Verweise darauf. Das Modul importiert das Theme
 * nicht — `theme.ts` liest die Werte von hier.
 *
 * Das Schild des Entwurfs trägt den Ton als Schrift und Rand auf 13 % desselben Tons. Hell hält die
 * Schrift dort 4,5:1 (auf Platte, Platte hoch und Platte-Fuß) nur nachgedunkelt; dunkel halten die
 * Entwurfstöne bis auf Stahl, der eine Stufe aufgehellt ist.
 *
 * **Der Tint ist ein eigener Wert, keine Rechnung auf dem Farbton.** Auf einem Variablen-Verweis
 * ergäbe `${hue}22` ungültiges CSS, und die Fläche verschwände still.
 */
const tint = (hex: string, deckkraft: number): string => {
  const [r, g, b] = [1, 3, 5].map((i) => Number.parseInt(hex.slice(i, i + 2), 16))
  return `rgba(${r},${g},${b},${deckkraft})`
}

const HELL = ['#935327', '#2A63B3', '#25713E', '#835C10']
const DUNKEL = ['#D08A52', '#629BF1', '#46C46F', '#E0AE49']

export const EPIC_FARBWERTE: Readonly<Record<'light' | 'dark', ReadonlyArray<EpicFarbe>>> = {
  light: HELL.map((hue) => ({ hue, tint: tint(hue, 0.13) })),
  dark: DUNKEL.map((hue) => ({ hue, tint: tint(hue, 0.13) })),
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
