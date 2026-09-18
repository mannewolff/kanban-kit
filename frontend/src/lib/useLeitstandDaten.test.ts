import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi, type Board } from '../api/boards'
import { ApiError } from '../api/client'
import { dashboardApi, type BoardDashboardKpis } from '../api/dashboard'
import { epicsApi, type Epic } from '../api/epics'
import { nightRunsApi, type NightRunView } from '../api/nightRuns'
import { useLeitstandDaten } from './useLeitstandDaten'

vi.mock('../api/boards', () => ({ boardsApi: { get: vi.fn() } }))
vi.mock('../api/dashboard', () => ({ dashboardApi: { get: vi.fn() } }))
vi.mock('../api/epics', () => ({ epicsApi: { list: vi.fn() } }))
vi.mock('../api/nightRuns', () => ({ nightRunsApi: { list: vi.fn(), errorClassCounts: vi.fn() } }))

const m = {
  board: boardsApi.get as ReturnType<typeof vi.fn>,
  kpis: dashboardApi.get as ReturnType<typeof vi.fn>,
  epics: epicsApi.list as ReturnType<typeof vi.fn>,
  laeufe: nightRunsApi.list as ReturnType<typeof vi.fn>,
  klassen: nightRunsApi.errorClassCounts as ReturnType<typeof vi.fn>,
}

const board = (extra: Partial<Board> = {}): Board => ({
  id: 1,
  projectId: 5,
  name: 'Entwicklung',
  createdAt: '2026-09-01T00:00:00Z',
  columns: [],
  ...extra,
})

const kpis = (): BoardDashboardKpis => ({
  columnDwell: [],
  throughput: [{ weekStart: '2026-06-01T09:00:00Z', doneCount: 10 }],
  avgLeadTimeSeconds: 276_480,
  leadTimeSampleCount: 86,
  avgImplementationSeconds: 53_280,
  implementationSampleCount: 61,
  outliers: [],
})

const epic = (id: number, title: string): Epic => ({
  id,
  number: id,
  title,
  description: null,
  shortcode: null,
  done: 1,
  total: 3,
  memberNumbers: [],
  rootNumbers: [],
  requirementCardNumber: null,
})

const lauf = (id: number, startedAt: string): NightRunView => ({
  id,
  startedAt,
  mode: 'CHAIN',
  durationMs: 15_120_000,
  processedCount: 3,
  skippedCount: 0,
  unparsedCount: 0,
  unparsedSample: null,
  createdAt: startedAt,
  origin: 'TOKEN',
  tokenName: 'kette',
  complete: true,
  updatedAt: null,
  usage: null,
  items: [],
})

/** Ein Versprechen, das der Test von Hand einlöst — für die Zustände vor der Antwort. */
function offen<T>() {
  let einloesen: (wert: T) => void = () => undefined
  let ablehnen: (fehler: unknown) => void = () => undefined
  const versprechen = new Promise<T>((resolve, reject) => {
    einloesen = resolve
    ablehnen = reject
  })
  return { versprechen, einloesen: (wert: T) => einloesen(wert), ablehnen: (fehler: unknown) => ablehnen(fehler) }
}

beforeEach(() => {
  vi.clearAllMocks()
  m.board.mockResolvedValue(board())
  m.kpis.mockResolvedValue(kpis())
  m.epics.mockResolvedValue([epic(1, 'Nachtlauf-Auswertung')])
  m.laeufe.mockResolvedValue([lauf(1, '2026-09-10T21:00:00Z'), lauf(3, '2026-09-14T21:10:00Z')])
  m.klassen.mockResolvedValue({ CHECKS_RED: 11 })
})

describe('useLeitstandDaten — Board-ID', () => {
  it('lädt Board, Kennzahlen und Vorhaben zu einer gültigen ID', async () => {
    const { result } = renderHook(() => useLeitstandDaten('7'))
    expect(result.current.validId).toBe(true)
    expect(result.current.board).toEqual({ art: 'laedt' })
    await waitFor(() => expect(result.current.board).toEqual({ art: 'da', wert: board() }))
    expect(result.current.kpis).toEqual({ art: 'da', wert: kpis() })
    expect(result.current.epics).toEqual({ art: 'da', wert: [epic(1, 'Nachtlauf-Auswertung')] })
    expect(m.board).toHaveBeenCalledWith(7)
    expect(m.kpis).toHaveBeenCalledWith(7)
    expect(m.epics).toHaveBeenCalledWith(7)
  })

  it.each([
    ['keine ID', undefined],
    ['eine leere ID', ''],
    ['eine nicht-numerische ID', 'abc'],
    ['die Null', '0'],
    ['eine negative ID', '-3'],
  ])('meldet %s als ungültig und ruft nichts ab', (_name, boardId) => {
    const { result } = renderHook(() => useLeitstandDaten(boardId))
    expect(result.current.validId).toBe(false)
    expect(result.current.board).toEqual({ art: 'laedt' })
    expect(m.board).not.toHaveBeenCalled()
    expect(m.kpis).not.toHaveBeenCalled()
    expect(m.epics).not.toHaveBeenCalled()
    expect(m.laeufe).not.toHaveBeenCalled()
  })

  it('lädt zu einer neuen Board-ID neu und beginnt dabei wieder beim Laden', async () => {
    const zweites = offen<Board>()
    const { result, rerender } = renderHook((boardId: string) => useLeitstandDaten(boardId), { initialProps: '7' })
    await waitFor(() => expect(result.current.board).toEqual({ art: 'da', wert: board() }))

    m.board.mockReturnValue(zweites.versprechen)
    rerender('8')
    expect(result.current.board).toEqual({ art: 'laedt' })
    expect(m.board).toHaveBeenLastCalledWith(8)
    expect(m.kpis).toHaveBeenLastCalledWith(8)
    expect(m.epics).toHaveBeenLastCalledWith(8)

    await act(async () => {
      zweites.einloesen(board({ id: 8, name: 'Zweites' }))
    })
    expect(result.current.board).toEqual({ art: 'da', wert: board({ id: 8, name: 'Zweites' }) })
  })

  it('verwirft eine Antwort, die erst nach dem Wechsel der Board-ID eintrifft', async () => {
    const erstes = offen<Board>()
    m.board.mockReturnValue(erstes.versprechen)
    const { result, rerender } = renderHook((boardId: string) => useLeitstandDaten(boardId), { initialProps: '7' })

    m.board.mockResolvedValue(board({ id: 8, name: 'Zweites' }))
    rerender('8')
    await waitFor(() => expect(result.current.board).toEqual({ art: 'da', wert: board({ id: 8, name: 'Zweites' }) }))

    await act(async () => {
      erstes.einloesen(board({ id: 7, name: 'Erstes' }))
    })
    expect(result.current.board).toEqual({ art: 'da', wert: board({ id: 8, name: 'Zweites' }) })
  })

  it('verwirft auch einen Fehler, der erst nach dem Wechsel der Board-ID eintrifft', async () => {
    const erstes = offen<Board>()
    m.board.mockReturnValue(erstes.versprechen)
    const { result, rerender } = renderHook((boardId: string) => useLeitstandDaten(boardId), { initialProps: '7' })

    m.board.mockResolvedValue(board({ id: 8, name: 'Zweites' }))
    rerender('8')
    await waitFor(() => expect(result.current.board).toEqual({ art: 'da', wert: board({ id: 8, name: 'Zweites' }) }))

    await act(async () => {
      erstes.ablehnen(new Error('Netz'))
    })
    expect(result.current.board).toEqual({ art: 'da', wert: board({ id: 8, name: 'Zweites' }) })
  })
})

describe('useLeitstandDaten — Läufe am Projekt des Boards', () => {
  it('holt Läufe und Fehlerklassen erst, wenn das Projekt bekannt ist', async () => {
    const erstes = offen<Board>()
    m.board.mockReturnValue(erstes.versprechen)
    const { result } = renderHook(() => useLeitstandDaten('7'))
    expect(result.current.projectId).toBeNull()
    expect(m.laeufe).not.toHaveBeenCalled()
    expect(m.klassen).not.toHaveBeenCalled()

    await act(async () => {
      erstes.einloesen(board({ projectId: 42 }))
    })
    expect(result.current.projectId).toBe(42)
    await waitFor(() => expect(m.laeufe).toHaveBeenCalledWith(42))
    expect(m.klassen).toHaveBeenCalledWith(42)
    await waitFor(() => expect(result.current.klassen).toEqual({ art: 'da', wert: { CHECKS_RED: 11 } }))
  })

  it('leitet Liste, jüngsten Lauf und Vorhaben-Liste aus den geladenen Zuständen ab', async () => {
    const { result } = renderHook(() => useLeitstandDaten('7'))
    expect(result.current.liste).toBeNull()
    expect(result.current.juengster).toBeNull()
    expect(result.current.epicListe).toEqual([])

    await waitFor(() => expect(result.current.liste).toHaveLength(2))
    expect(result.current.juengster).toEqual(lauf(3, '2026-09-14T21:10:00Z'))
    expect(result.current.epicListe).toEqual([epic(1, 'Nachtlauf-Auswertung')])
  })

  it('lässt den jüngsten Lauf ohne aufbewahrten Lauf offen, die Liste aber leer statt fehlend', async () => {
    m.laeufe.mockResolvedValue([])
    const { result } = renderHook(() => useLeitstandDaten('7'))
    await waitFor(() => expect(result.current.liste).toEqual([]))
    expect(result.current.juengster).toBeNull()
  })
})

describe('useLeitstandDaten — Fehler', () => {
  it('deutet 403 als „ohne Recht"', async () => {
    m.klassen.mockRejectedValue(new ApiError(403, 'Forbidden'))
    const { result } = renderHook(() => useLeitstandDaten('7'))
    await waitFor(() => expect(result.current.klassen).toEqual({ art: 'ohneRecht' }))
  })

  it('deutet jeden anderen Fehler als Fehler', async () => {
    m.klassen.mockRejectedValue(new ApiError(500, 'Kaputt'))
    m.kpis.mockRejectedValue(new Error('Netz'))
    const { result } = renderHook(() => useLeitstandDaten('7'))
    await waitFor(() => expect(result.current.kpis).toEqual({ art: 'fehler' }))
    await waitFor(() => expect(result.current.klassen).toEqual({ art: 'fehler' }))
  })

  it('lässt Liste und Vorhaben-Liste leer, wenn Läufe und Vorhaben ausfallen', async () => {
    m.laeufe.mockRejectedValue(new ApiError(403, 'Forbidden'))
    m.epics.mockRejectedValue(new Error('Netz'))
    const { result } = renderHook(() => useLeitstandDaten('7'))
    await waitFor(() => expect(result.current.epics).toEqual({ art: 'fehler' }))
    await waitFor(() => expect(m.laeufe).toHaveBeenCalled())
    expect(result.current.liste).toBeNull()
    expect(result.current.juengster).toBeNull()
    expect(result.current.epicListe).toEqual([])
  })

  it('verarbeitet eine verspätete Antwort nach dem Verlassen der Seite nicht mehr', async () => {
    const spaet = offen<BoardDashboardKpis>()
    m.kpis.mockReturnValue(spaet.versprechen)
    const { result, unmount } = renderHook(() => useLeitstandDaten('7'))
    unmount()
    await act(async () => {
      spaet.einloesen(kpis())
    })
    expect(result.current.kpis).toEqual({ art: 'laedt' })
  })
})
