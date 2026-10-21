import type { Melder } from './leitstand'

/**
 * Die Zeitachse der gleichzeitigen Runs für „Jetzt im Werk“ (Plan #1882 E2) — reine Rechnung,
 * damit sie der Mutationsprüfung unterliegt; die Platte ruft nur auf.
 */

/** Ein Run der Lauf-Liste, soweit die Zeitachse ihn braucht. */
export interface Zeitleistenlauf {
  /** Die Run-Nummer; `undefined` an einem Run ohne Nummer. */
  nummer: number | undefined
  startedAt: string
  durationMs: number
  laeuft: boolean
  melder: Melder
}

/** Ein Balken der Zeitachse. */
export interface Zeitbalken {
  nummer: number | undefined
  startedAt: string
  /** Anfang des Balkens, am Fensteranfang abgeschnitten. */
  anfangMs: number
  /** Ende des Balkens; `null` heißt offen — der Run läuft noch. */
  endeMs: number | null
  melder: Melder
  /** Die laufende Stufe; `null` am beendeten Run und wo `/now` keine nennt. */
  stufe: string | null
}

/** Die Zeitachse: Fenster und Balken, ältester Start zuerst. */
export interface Zeitleiste {
  vonMs: number
  bisMs: number
  balken: Zeitbalken[]
}

/** Das Fenster reicht mindestens so weit zurück. */
const MINDESTFENSTER_MS = 60 * 60_000

/**
 * Die Zeitachse der laufenden Runs (Plan #1882 E2): vom frühesten Start eines laufenden Runs,
 * mindestens 60 Minuten, bis `jetzt`. Runs, die in diesem Fenster endeten, bleiben mit ihrem
 * Melder darin. Ohne laufenden Run `null` — dann erscheint die Platte nicht.
 *
 * @param stufenJeLauf die jüngste Stufe je Run-Nummer, aus `/now`
 */
export function zeitleiste(
  laeufe: readonly Zeitleistenlauf[],
  stufenJeLauf: ReadonlyMap<number, string>,
  jetzt: number,
): Zeitleiste | null {
  const laufende = laeufe.filter((l) => l.laeuft)
  if (laufende.length === 0) {
    return null
  }
  const vonMs = Math.min(jetzt - MINDESTFENSTER_MS, ...laufende.map((l) => Date.parse(l.startedAt)))
  const balken = laeufe
    .map((l) => ({ lauf: l, start: Date.parse(l.startedAt) }))
    .filter(({ lauf, start }) => lauf.laeuft || start + lauf.durationMs > vonMs)
    .sort((a, b) => a.start - b.start)
    .map(({ lauf, start }) => ({
      nummer: lauf.nummer,
      startedAt: lauf.startedAt,
      anfangMs: Math.max(start, vonMs),
      endeMs: lauf.laeuft ? null : Math.min(start + lauf.durationMs, jetzt),
      melder: lauf.melder,
      stufe: lauf.laeuft && lauf.nummer !== undefined ? (stufenJeLauf.get(lauf.nummer) ?? null) : null,
    }))
  return { vonMs, bisMs: jetzt, balken }
}

/**
 * Das Sprungziel der Platte eines Runs (Issue #1887): ihr `id`, gebildet aus dem Start — der ist
 * der Schlüssel der Lauf-Liste und steht auch an einem Run ohne Nummer.
 */
export function laufSprungziel(startedAt: string): string {
  return `lauf-${startedAt}`
}
