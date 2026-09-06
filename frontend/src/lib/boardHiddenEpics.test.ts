import { afterEach, describe, expect, it, vi } from 'vitest'
import { hiddenEpicsStorageKey, leseAusgeblendet, schreibeAusgeblendet } from './boardHiddenEpics'

describe('hiddenEpicsStorageKey', () => {
  it('bildet den Schlüssel je Board im bestehenden manban-Namensraum', () => {
    expect(hiddenEpicsStorageKey(1)).toBe('manban.boardHiddenEpics.1')
  })

  it('trennt die Boards voneinander', () => {
    expect(hiddenEpicsStorageKey(7)).not.toBe(hiddenEpicsStorageKey(1))
  })
})

/**
 * localStorage-Stub über eine echte Map — vorbelegbar und nach dem Test wieder auslesbar. Nötig
 * statt des nativen `localStorage`: Unter Node 26 ist es deaktiviert (siehe `src/test/setup.ts`),
 * ein Test gegen das globale Objekt wäre „grün lokal, rot in CI".
 */
const stubStore = (entries: [string, string][]) => {
  const store = new Map<string, string>(entries)
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => store.get(k) ?? null,
    setItem: (k: string, v: string) => { store.set(k, v) },
    removeItem: (k: string) => { store.delete(k) },
    clear: () => store.clear(), key: () => null, length: 0,
  })
  return store
}

afterEach(() => vi.unstubAllGlobals())

describe('leseAusgeblendet', () => {
  it('liefert ohne Eintrag die leere Menge', () => {
    stubStore([])
    expect([...leseAusgeblendet(1)]).toEqual([])
  })

  it('liest die gespeicherten IDs des eigenen Boards', () => {
    stubStore([[hiddenEpicsStorageKey(1), JSON.stringify([9, 8])]])
    expect([...leseAusgeblendet(1)].sort()).toEqual([8, 9])
  })

  it('liefert die leere Menge, wenn localStorage nicht verfügbar ist', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => { throw new Error('storage disabled') },
      setItem: () => { throw new Error('storage disabled') },
      removeItem: () => { throw new Error('storage disabled') },
      clear: () => {}, key: () => null, length: 0,
    })
    expect([...leseAusgeblendet(1)]).toEqual([])
  })

  it('liefert bei ungültigem JSON die leere Menge statt zu scheitern', () => {
    stubStore([[hiddenEpicsStorageKey(1), 'kein json']])
    expect([...leseAusgeblendet(1)]).toEqual([])
  })
})

describe('schreibeAusgeblendet', () => {
  it('schreibt eine nicht-leere Menge als JSON-Array', () => {
    const store = stubStore([])
    schreibeAusgeblendet(1, new Set([9, 8]))
    expect(JSON.parse(store.get(hiddenEpicsStorageKey(1)) ?? '[]')).toEqual([9, 8])
  })

  it('löscht den Schlüssel bei leerer Menge, statt ein leeres Array zu hinterlassen', () => {
    // Ein zurückbleibendes `[]` wäre kein Fehler beim Lesen, aber ein Eintrag ohne Aussage: Er
    // stünde für „nichts ausgeblendet" — genau das, was auch ein fehlender Schlüssel bedeutet.
    const store = stubStore([[hiddenEpicsStorageKey(1), JSON.stringify([9])]])
    schreibeAusgeblendet(1, new Set())
    expect(store.has(hiddenEpicsStorageKey(1))).toBe(false)
  })

  it('schluckt ein scheiterndes setItem', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => null,
      setItem: () => { throw new Error('storage disabled') },
      removeItem: () => { throw new Error('storage disabled') },
      clear: () => {}, key: () => null, length: 0,
    })
    expect(() => schreibeAusgeblendet(1, new Set([9]))).not.toThrow()
  })

  it('schluckt ein scheiterndes removeItem', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => null,
      setItem: () => { throw new Error('storage disabled') },
      removeItem: () => { throw new Error('storage disabled') },
      clear: () => {}, key: () => null, length: 0,
    })
    expect(() => schreibeAusgeblendet(1, new Set())).not.toThrow()
  })
})
