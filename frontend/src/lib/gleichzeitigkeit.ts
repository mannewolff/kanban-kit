import type { NightRunConcurrencyView } from '../api/nightRuns'
import type { Woerterbuch } from '../i18n/de'

/**
 * Die drei flachen Angaben einer gleichzeitigen Nacht, wie ein geparstes Paket sie trägt. `unknown`:
 * Gedeutet wird erst hier, und eine fremde Datei darf nichts anderes einschleusen.
 */
export interface GleichzeitigkeitsFelder {
  concurrent?: unknown
  checkWaitMs?: unknown
  deferredAtMerge?: unknown
}

const wahrheitswert = (wert: unknown): boolean | null => (typeof wert === 'boolean' ? wert : null)

const wartezeitInMs = (wert: unknown): number | null =>
  // Stryker disable next-line ConditionalExpression: gleichwertig — `Number.isSafeInteger` ist bei jeder Nicht-Zahl falsch; `typeof` verengt nur den Typ
  typeof wert === 'number' && Number.isSafeInteger(wert) && wert >= 0 ? wert : null

/**
 * Die Angaben eines geparsten Pakets in der Lese-Form (Issue #1878, Plan #1876 E10) — dieselbe Regel
 * wie `IngestConcurrency.toDomain` im Backend, damit ein eingelesener Lauf vor dem Senden zeigt, was
 * der Server aufbewahrt. `null` nur, wenn keines der drei Felder vorhanden ist; ein ausdrückliches
 * `null` gilt als geführt (E2). Übernommen wird allein ein Wahrheitswert bzw. eine ganze Zahl ≥ 0,
 * alles andere wird `null` = „nicht gemeldet“; eine gemeldete 0 bleibt 0 (E3).
 */
export function gleichzeitigkeitDerMeldung(item: GleichzeitigkeitsFelder): NightRunConcurrencyView | null {
  if (item.concurrent === undefined && item.checkWaitMs === undefined && item.deferredAtMerge === undefined) {
    return null
  }
  return {
    concurrent: wahrheitswert(item.concurrent),
    checkWaitMs: wartezeitInMs(item.checkWaitMs),
    deferredAtMerge: wahrheitswert(item.deferredAtMerge),
  }
}

/**
 * Die Marken eines Pakets für Platte und zugeklappte Zeile (K3): „gleichzeitig“ und „Übernahme
 * verschoben“, in dieser Reihenfolge und nur beim Wert `true` — bei `false`, „nicht gemeldet“ und
 * ohne Angabe keine. Die Wartezeit trägt keine Marke.
 */
export function gleichzeitigkeitsMarken(concurrency: NightRunConcurrencyView | null, t: Woerterbuch): string[] {
  return [
    ...(concurrency?.concurrent === true ? [t.GLEICHZEITIGKEIT.MARKE_GLEICHZEITIG] : []),
    ...(concurrency?.deferredAtMerge === true ? [t.GLEICHZEITIGKEIT.MARKE_VERSCHOBEN] : []),
  ]
}
