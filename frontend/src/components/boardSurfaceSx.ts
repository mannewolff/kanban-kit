import type { SxProps, Theme } from '@mui/material/styles'
import {
  AUSWAHL,
  CARD_LIFT,
  CARD_RADIUS,
  CARD_SHADOW,
  CARD_SHADOW_HOVER,
  GRUND_TIEF,
  KUPFER,
  KUPFER_SCHIMMER,
  PLATTE,
  PLATTE_HOCH,
  RAND,
  RAND_STARK,
  SCHATTEN_NUTE,
} from '../theme'

/**
 * Flächen der Karte nach dem Leitstand-Entwurf (#980, `docs/entwurf-leitstand.html` Z. 751–778):
 * **Spalten liegen als Nut im Grund, Karten liegen als Platte darauf.** Ein gemeinsamer Baustein für
 * Board und Ideen-Board, damit eine Karte auf beiden Seiten gleich aussieht.
 *
 * **Keine farbige Status-Kante mehr.** Der Entwurf trägt den Zustand am Spaltenkopf (LED) und an
 * der Karte über ihre Marken, nicht über eine Kante; die Kante aus „Kante"/„Panel" entfällt.
 *
 * **Tiefe im Ruhezustand, nicht erst beim Hover:** Lichtkante und Schatten der Platte stehen immer,
 * unter dem Zeiger hebt die Karte ab.
 */
export function karteSx(options: {
  /** Die Karte ist im Auswahlmodus gewählt: kupferne Tönung und kupferne Haarlinie (Entwurf Z. 905). */
  gewaehlt?: boolean
  /** Die Karte wird gerade gezogen — an ihrer Stelle bleibt die Vertiefung (siehe {@link PLATZHALTER_SX}). */
  bewegt?: boolean
} = {}): SxProps<Theme> & Record<string, unknown> {
  const randfarbe = options.gewaehlt ? `color-mix(in srgb, ${KUPFER} 52%, ${RAND})` : RAND
  return {
    border: `1px solid ${randfarbe}`,
    borderRadius: `${CARD_RADIUS}px`,
    background: options.gewaehlt ? AUSWAHL : `linear-gradient(180deg, ${PLATTE_HOCH}, ${PLATTE})`,
    boxShadow: CARD_SHADOW,
    transition: 'transform .16s cubic-bezier(.22,.7,.3,1), box-shadow .16s ease',
    // Wer Bewegung abgestellt hat, bekommt dieselbe Tiefe ohne Übergang: Die Dauer setzt die
    // zentrale Regel in `theme.ts` auf null (#953).
    '&:hover': { boxShadow: CARD_SHADOW_HOVER, transform: `translateY(${CARD_LIFT}px)` },
    ...(options.bewegt ? PLATZHALTER_SX : {}),
  }
}

/**
 * Wie dicht die Karten einer Spalte stehen — Wahlschalter der Werkzeugleiste (#980). Der Typ steht
 * beim Maß und nicht bei der Ansicht, damit Board und Karte dieselbe Quelle lesen (#1056).
 */
export type Dichte = 'normal' | 'kompakt'

/** Innenabstände einer Karte, in einer Form, die beide Dichten mit denselben Schlüsseln beschreibt. */
export interface KarteDichteSx {
  gap: string
  px: string
  py: string
}

/**
 * Abstände der Karte nach gewählter Dichte (#1056). Als Tabelle statt als drei Ternäre im `sx`:
 * Die beiden Maßsätze stehen nebeneinander und sind als Ganzes lesbar, und die Karte trägt die
 * Fallunterscheidung nicht mehr in ihrer eigenen Komplexität (Plan #1042, P11).
 */
export function karteDichteSx(dichte: Dichte): KarteDichteSx {
  return dichte === 'kompakt'
    ? { gap: '4px', px: '9px', py: '6px' }
    : { gap: '7px', px: '11px', py: '10px' }
}

/**
 * Der verlassene Platz einer gezogenen Karte (Entwurf `.karte-platz`, Z. 766–772): eine Vertiefung
 * mit gestrichelter Linie, Inhalt unsichtbar. `visibility` statt Entfernen: Der Platz behält die
 * Höhe, und das Element bleibt im Dokument — verschwände die Quelle, bräche das native Ziehen ab.
 */
export const PLATZHALTER_SX = {
  border: `1px dashed ${RAND_STARK}`,
  background: `color-mix(in srgb, ${GRUND_TIEF} 60%, transparent)`,
  boxShadow: SCHATTEN_NUTE,
  '& > *': { visibility: 'hidden' },
  '&:hover': { boxShadow: SCHATTEN_NUTE, transform: 'none' },
} as const

/**
 * Ablagefläche während eines Ziehvorgangs (AK 8, #956): ein gestrichelter Rahmen in Kupfer auf
 * kupfernem Schimmer, nach innen versetzt, damit er die Kanten der Spalte nicht überdeckt.
 */
export function ablageflaecheSx(aktiv: boolean): Record<string, unknown> {
  if (!aktiv) {
    return {}
  }
  return {
    outline: '2px dashed',
    outlineColor: 'primary.main',
    outlineOffset: '-4px',
    bgcolor: KUPFER_SCHIMMER,
    borderRadius: `${CARD_RADIUS}px`,
  }
}
