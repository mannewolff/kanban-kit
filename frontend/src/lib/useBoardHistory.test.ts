import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Me } from '../api/auth'
import { useBoardHistory, type BoardHistoryEntry } from './useBoardHistory'

const auth = vi.hoisted(() => ({ value: { user: null as Me | null, loading: false } }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => auth.value }))

const me = (userId: number): Me => ({
  userId,
  email: `u${userId}@example.org`,
  displayName: `U${userId}`,
  platformRole: 'USER',
  memberships: [],
})

const key = (userId: number): string => `manban.boardHistory.v1.${userId}`
const entry = (id: number, name = `B${id}`, projectName = `P${id}`): BoardHistoryEntry => ({
  id,
  name,
  projectName,
})
const stored = (userId: number): unknown => JSON.parse(localStorage.getItem(key(userId)) ?? 'null')

// Node 26 deaktiviert natives localStorage — wie in DataTable.test/AppShell.test ein
// funktionierendes Fake stubben, damit die Persistenz beobachtbar ist.
function fakeStorage(): Storage {
  const map = new Map<string, string>()
  return {
    getItem: (k) => map.get(k) ?? null,
    setItem: (k, v) => void map.set(k, String(v)),
    removeItem: (k) => void map.delete(k),
    clear: () => map.clear(),
    key: (i) => [...map.keys()][i] ?? null,
    get length() {
      return map.size
    },
  }
}

describe('useBoardHistory', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', fakeStorage())
    auth.value = { user: me(42), loading: false }
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('stellt ein besuchtes Board vorne ein und holt es bei erneutem Besuch nach vorne', () => {
    const { result } = renderHook(() => useBoardHistory())

    act(() => result.current.recordVisit(entry(1)))
    act(() => result.current.recordVisit(entry(2)))
    expect(result.current.history).toEqual([entry(2), entry(1)])

    act(() => result.current.recordVisit(entry(1)))
    expect(result.current.history).toEqual([entry(1), entry(2)])
  })

  it('laedt denselben Verlauf nach Unmount und erneutem Mount aus localStorage', () => {
    const { result, unmount } = renderHook(() => useBoardHistory())
    act(() => {
      result.current.recordVisit(entry(1))
      result.current.recordVisit(entry(2))
      result.current.recordVisit(entry(3))
    })
    expect(result.current.history).toEqual([entry(3), entry(2), entry(1)])
    unmount()

    const { result: remounted } = renderHook(() => useBoardHistory())
    expect(remounted.current.history).toEqual([entry(3), entry(2), entry(1)])
  })

  it('speichert unter dem nutzerspezifischen Schluessel nur id, name und projectName', () => {
    const withExtra: BoardHistoryEntry & { projectId: number } = {
      id: 7,
      name: 'B7',
      projectName: 'P7',
      projectId: 3,
    }
    const { result } = renderHook(() => useBoardHistory())

    act(() => result.current.recordVisit(withExtra))

    expect(localStorage.getItem(key(42))).not.toBeNull()
    expect(stored(42)).toEqual([{ id: 7, name: 'B7', projectName: 'P7' }])
  })

  it('zeigt beim Kontowechsel ohne Unmount in keinem Render den fremden Verlauf', () => {
    localStorage.setItem(key(1), JSON.stringify([entry(11)]))
    localStorage.setItem(key(2), JSON.stringify([entry(22)]))
    auth.value = { user: me(1), loading: false }
    const renders: Array<{ userId: number | null; history: readonly BoardHistoryEntry[] }> = []
    const { result, rerender } = renderHook(() => {
      const history = useBoardHistory()
      renders.push({ userId: auth.value.user?.userId ?? null, history: history.history })
      return history
    })
    expect(result.current.history).toEqual([entry(11)])

    auth.value = { user: null, loading: true }
    rerender()
    expect(result.current.history).toEqual([])

    auth.value = { user: me(2), loading: false }
    rerender()
    expect(result.current.history).toEqual([entry(22)])

    auth.value = { user: me(1), loading: false }
    rerender()
    expect(result.current.history).toEqual([entry(11)])

    expect(renders.filter((r) => r.userId !== 1 && r.history.some((e) => e.id === 11))).toEqual([])
    expect(renders.filter((r) => r.userId !== 2 && r.history.some((e) => e.id === 22))).toEqual([])
  })

  it('greift bei loading oder ohne Nutzer weder lesend noch schreibend auf den Storage zu', () => {
    const getItem = vi.spyOn(localStorage, 'getItem')
    const setItem = vi.spyOn(localStorage, 'setItem')

    auth.value = { user: null, loading: true }
    const { result: whileLoading, unmount } = renderHook(() => useBoardHistory())
    act(() => whileLoading.current.recordVisit(entry(1)))
    act(() => whileLoading.current.remove(1))
    expect(whileLoading.current.history).toEqual([])
    unmount()

    auth.value = { user: null, loading: false }
    const { result: anonymous } = renderHook(() => useBoardHistory())
    act(() => anonymous.current.recordVisit(entry(1)))
    expect(anonymous.current.history).toEqual([])

    expect(getItem).not.toHaveBeenCalled()
    expect(setItem).not.toHaveBeenCalled()
  })

  it('ergibt bei ungueltigem JSON einen leeren Verlauf', () => {
    localStorage.setItem(key(42), '{kaputt')

    const { result } = renderHook(() => useBoardHistory())

    expect(result.current.history).toEqual([])
  })

  it('ergibt bei einem Objekt statt einer Liste einen leeren Verlauf', () => {
    localStorage.setItem(key(42), JSON.stringify({ id: 1, name: 'B1', projectName: 'P1' }))

    const { result } = renderHook(() => useBoardHistory())

    expect(result.current.history).toEqual([])
  })

  it('verwirft ungueltige Eintraege und behaelt bei doppelter ID das erste Vorkommen', () => {
    localStorage.setItem(
      key(42),
      JSON.stringify([
        'kein Objekt',
        null,
        { id: '3', name: 'B3', projectName: 'P3' },
        { id: 0, name: 'B0', projectName: 'P0' },
        { id: 4.5, name: 'B4', projectName: 'P4' },
        { id: 5 },
        { id: 6, name: 'B6' },
        { id: 7, name: 'B7', projectName: 'P7' },
        { id: 7, name: 'B7 veraltet', projectName: 'P7 veraltet' },
        { id: 8, name: 'B8', projectName: 'P8' },
      ]),
    )

    const { result } = renderHook(() => useBoardHistory())

    expect(result.current.history).toEqual([entry(7), entry(8)])
  })

  it('uebernimmt beim erneuten Besuch die neuen Namen', () => {
    const { result } = renderHook(() => useBoardHistory())

    act(() => result.current.recordVisit(entry(3, 'Board alt', 'Projekt alt')))
    act(() => result.current.recordVisit(entry(9)))
    act(() => result.current.recordVisit(entry(3, 'Board neu', 'Projekt neu')))

    expect(result.current.history).toEqual([entry(3, 'Board neu', 'Projekt neu'), entry(9)])
  })

  it('waechst nicht ueber acht Eintraege', () => {
    const { result } = renderHook(() => useBoardHistory())

    act(() => {
      for (let id = 1; id <= 9; id++) {
        result.current.recordVisit(entry(id))
      }
    })

    expect(result.current.history.map((e) => e.id)).toEqual([9, 8, 7, 6, 5, 4, 3, 2])
  })

  it('begrenzt auch einen ueberlangen gespeicherten Verlauf auf acht Eintraege', () => {
    localStorage.setItem(key(42), JSON.stringify(Array.from({ length: 10 }, (_, i) => entry(i + 1))))

    const { result } = renderHook(() => useBoardHistory())

    expect(result.current.history.map((e) => e.id)).toEqual([1, 2, 3, 4, 5, 6, 7, 8])
  })

  it('entfernt einen Eintrag und laesst die Reihenfolge der uebrigen unveraendert', () => {
    const { result } = renderHook(() => useBoardHistory())
    act(() => {
      result.current.recordVisit(entry(1))
      result.current.recordVisit(entry(2))
      result.current.recordVisit(entry(3))
    })

    act(() => result.current.remove(2))

    expect(result.current.history).toEqual([entry(3), entry(1)])
    expect(stored(42)).toEqual([entry(3), entry(1)])
  })

  it('aendert bei remove mit unbekannter ID nichts und wirft nicht', () => {
    const { result } = renderHook(() => useBoardHistory())
    act(() => {
      result.current.recordVisit(entry(1))
      result.current.recordVisit(entry(2))
    })

    expect(() => act(() => result.current.remove(99))).not.toThrow()
    expect(result.current.history).toEqual([entry(2), entry(1)])
  })

  it('ergibt bei einem Lesefehler zunaechst einen leeren Verlauf', () => {
    localStorage.setItem(key(42), JSON.stringify([entry(1)]))
    vi.spyOn(localStorage, 'getItem').mockImplementation(() => {
      throw new Error('Storage gesperrt')
    })

    const { result } = renderHook(() => useBoardHistory())

    expect(result.current.history).toEqual([])
  })

  it('aktualisiert den Verlauf trotz Schreibfehler innerhalb der Hook-Instanz', () => {
    vi.spyOn(localStorage, 'setItem').mockImplementation(() => {
      throw new Error('Storage voll')
    })
    const { result } = renderHook(() => useBoardHistory())

    expect(() =>
      act(() => {
        result.current.recordVisit(entry(1))
        result.current.recordVisit(entry(2))
      }),
    ).not.toThrow()
    expect(result.current.history).toEqual([entry(2), entry(1)])

    expect(() => act(() => result.current.remove(2))).not.toThrow()
    expect(result.current.history).toEqual([entry(1)])
  })
})
