import { describe, expect, it } from 'vitest'
import { de } from '../i18n/de'
import { en } from '../i18n/en'
import { gleichzeitigkeitDerMeldung, gleichzeitigkeitsMarken } from './gleichzeitigkeit'

/**
 * Die Deutung der drei flachen Angaben einer gleichzeitigen Nacht (Issue #1878, Plan #1876, E10):
 * dieselbe Regel wie `IngestConcurrency.toDomain` im Backend, damit eine eingelesene Datei vor dem
 * Senden zeigt, was der Server danach aufbewahrt.
 */
describe('gleichzeitigkeitDerMeldung', () => {
  it('gibt null, wenn keines der drei Felder vorhanden ist', () => {
    expect(gleichzeitigkeitDerMeldung({})).toBeNull()
  })

  it('gibt null auch bei ausdruecklich undefined in allen drei Feldern', () => {
    expect(
      gleichzeitigkeitDerMeldung({ concurrent: undefined, checkWaitMs: undefined, deferredAtMerge: undefined }),
    ).toBeNull()
  })

  it('wertet ein ausdrueckliches null als gefuehrt und liefert den Record mit null', () => {
    expect(gleichzeitigkeitDerMeldung({ concurrent: null })).toEqual({
      concurrent: null,
      checkWaitMs: null,
      deferredAtMerge: null,
    })
  })

  it('uebernimmt gemeldete Wahrheitswerte und eine ganze Wartezeit', () => {
    expect(gleichzeitigkeitDerMeldung({ concurrent: true, checkWaitMs: 776_000, deferredAtMerge: false })).toEqual({
      concurrent: true,
      checkWaitMs: 776_000,
      deferredAtMerge: false,
    })
  })

  it('fuellt ein fehlendes Feld neben einem gefuehrten als null', () => {
    expect(gleichzeitigkeitDerMeldung({ deferredAtMerge: true })).toEqual({
      concurrent: null,
      checkWaitMs: null,
      deferredAtMerge: true,
    })
  })

  it('laesst eine gemeldete Wartezeit von 0 als 0 stehen', () => {
    expect(gleichzeitigkeitDerMeldung({ concurrent: true, checkWaitMs: 0, deferredAtMerge: false })?.checkWaitMs).toBe(0)
  })

  it.each([
    ['negative Wartezeit', -1],
    ['Gleitkommazahl', 1.5],
    ['Text', '25000'],
    ['unendlich', Number.POSITIVE_INFINITY],
    ['keine Zahl', Number.NaN],
    ['jenseits der sicheren Ganzzahlen', Number.MAX_SAFE_INTEGER + 1],
  ])('wertet als Wartezeit %s als nicht gemeldet', (_fall, wert) => {
    expect(gleichzeitigkeitDerMeldung({ concurrent: true, checkWaitMs: wert, deferredAtMerge: false })).toEqual({
      concurrent: true,
      checkWaitMs: null,
      deferredAtMerge: false,
    })
  })

  it.each([['ja'], ['true'], [1], [0]])('wertet %s als Wahrheitswert als nicht gemeldet', (wert) => {
    expect(gleichzeitigkeitDerMeldung({ concurrent: wert, deferredAtMerge: wert })).toEqual({
      concurrent: null,
      checkWaitMs: null,
      deferredAtMerge: null,
    })
  })
})

describe('gleichzeitigkeitsMarken', () => {
  it('nennt beide Marken in fester Reihenfolge, nur bei wahr', () => {
    expect(gleichzeitigkeitsMarken({ concurrent: true, checkWaitMs: 5000, deferredAtMerge: true }, de)).toEqual([
      'gleichzeitig',
      'Übernahme verschoben',
    ])
  })

  it('nennt die Marken auf Englisch', () => {
    expect(gleichzeitigkeitsMarken({ concurrent: true, checkWaitMs: null, deferredAtMerge: true }, en)).toEqual([
      'concurrent',
      'merge deferred',
    ])
  })

  it('nennt nur die Marke, deren Wert wahr ist', () => {
    expect(gleichzeitigkeitsMarken({ concurrent: false, checkWaitMs: null, deferredAtMerge: true }, de)).toEqual([
      'Übernahme verschoben',
    ])
    expect(gleichzeitigkeitsMarken({ concurrent: true, checkWaitMs: null, deferredAtMerge: null }, en)).toEqual([
      'concurrent',
    ])
  })

  it('nennt bei falsch, bei nicht gemeldet und ohne Angabe keine Marke', () => {
    expect(gleichzeitigkeitsMarken({ concurrent: false, checkWaitMs: 0, deferredAtMerge: false }, de)).toEqual([])
    expect(gleichzeitigkeitsMarken({ concurrent: null, checkWaitMs: null, deferredAtMerge: null }, de)).toEqual([])
    expect(gleichzeitigkeitsMarken(null, de)).toEqual([])
  })
})

describe('Wörterbuch GLEICHZEITIGKEIT', () => {
  /** Alle Texte eines Blocks, Funktionen mit Beispielwerten aufgerufen. */
  const texte = (block: Record<string, unknown>): string[] =>
    Object.values(block).map((wert) => (typeof wert === 'function' ? String(wert(1, 2, 3)) : String(wert)))

  it('enthaelt im deutschen Block nirgends das Wort „zurückgestellt"', () => {
    const werte = texte(de.GLEICHZEITIGKEIT)
    expect(werte.length).toBeGreaterThan(0)
    for (const wert of werte) expect(wert.toLowerCase()).not.toContain('zurückgestellt')
  })

  it('fuehrt Begriffe und Werte in beiden Sprachen', () => {
    expect([de.GLEICHZEITIGKEIT.GLEICHZEITIG, en.GLEICHZEITIGKEIT.GLEICHZEITIG]).toEqual(['Gleichzeitig', 'Concurrent'])
    expect([de.GLEICHZEITIGKEIT.WARTEZEIT, en.GLEICHZEITIGKEIT.WARTEZEIT]).toEqual([
      'Wartezeit auf die Prüfung',
      'Wait for a check slot',
    ])
    expect([de.GLEICHZEITIGKEIT.VERSCHOBEN, en.GLEICHZEITIGKEIT.VERSCHOBEN]).toEqual([
      'Übernahme verschoben',
      'Merge deferred',
    ])
    expect([de.GLEICHZEITIGKEIT.JA, de.GLEICHZEITIGKEIT.NEIN, en.GLEICHZEITIGKEIT.JA, en.GLEICHZEITIGKEIT.NEIN]).toEqual([
      'ja',
      'nein',
      'yes',
      'no',
    ])
  })

  it('fuehrt „nicht gemeldet" nicht im Block, sondern im Übergabe-Block', () => {
    expect(texte(de.GLEICHZEITIGKEIT)).not.toContain('nicht gemeldet')
    expect(de.NACHTLAUF_UEBERGABE.NICHT_GEMELDET).toBe('nicht gemeldet')
  })
})
