import { afterEach, describe, expect, it, vi } from 'vitest'
import { leseMobilBoard, mobilBoardSchluessel, schreibeMobilBoard, vergissMobilBoard } from './mobilBoard'

/**
 * localStorage-Stub über eine echte Map, wie in `boardHiddenEpics.test.ts`: Unter Node 26 ist das
 * native `localStorage` deaktiviert (siehe `src/test/setup.ts`).
 */
const stubStore = (entries: [string, string][] = []) => {
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

describe('mobilBoard', () => {
  it('merkt sich die Wahl je Konto', () => {
    const store = stubStore()
    schreibeMobilBoard(7, 42)
    schreibeMobilBoard(8, 43)
    expect(leseMobilBoard(7)).toBe(42)
    expect(leseMobilBoard(8)).toBe(43)
    expect(store.get(mobilBoardSchluessel(7))).toBe('42')
    expect(mobilBoardSchluessel(7)).toBe('manban.mobilBoard.7')
  })

  it('liefert ohne Wahl nichts', () => {
    stubStore()
    expect(leseMobilBoard(7)).toBeNull()
  })

  it.each(['kaputt', '0', '-3', '1.5'])('liefert beim unbrauchbaren Eintrag %s nichts', (eintrag) => {
    stubStore([[mobilBoardSchluessel(7), eintrag]])
    expect(leseMobilBoard(7)).toBeNull()
  })

  it('vergisst die Wahl', () => {
    stubStore()
    schreibeMobilBoard(7, 42)
    vergissMobilBoard(7)
    expect(leseMobilBoard(7)).toBeNull()
  })

  it('scheitert nicht, wenn kein Speicher verfügbar ist', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => { throw new Error('storage disabled') },
      setItem: () => { throw new Error('storage disabled') },
      removeItem: () => { throw new Error('storage disabled') },
      clear: () => {}, key: () => null, length: 0,
    })
    expect(() => schreibeMobilBoard(7, 42)).not.toThrow()
    expect(() => vergissMobilBoard(7)).not.toThrow()
    expect(leseMobilBoard(7)).toBeNull()
  })
})
