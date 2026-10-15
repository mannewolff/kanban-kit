import { afterEach, describe, expect, it, vi } from 'vitest'
import { dashboardApi } from './dashboard'

afterEach(() => vi.restoreAllMocks())

describe('dashboardApi', () => {
  it('get ruft GET /api/boards/{id}/dashboard und liefert die KPIs', async () => {
    const kpis = {
      columnDwell: [],
      throughput: [],
      avgLeadTimeSeconds: 100,
      leadTimeSampleCount: 3,
      avgImplementationSeconds: null,
      implementationSampleCount: 0,
      outliers: [],
    }
    const f = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      statusText: 'OK',
      text: () => Promise.resolve(JSON.stringify(kpis)),
    } as Response)

    const result = await dashboardApi.get(7)

    const [url, init] = f.mock.calls[f.mock.calls.length - 1]
    expect(url).toBe('/api/boards/7/dashboard')
    expect(init?.method).toBeUndefined()
    expect(result).toEqual(kpis)
  })
})

describe('dashboardApi.implementationTime', () => {
  const antwort = () =>
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      statusText: 'OK',
      text: () => Promise.resolve(JSON.stringify({ avgImplementationSeconds: 5400, implementationSampleCount: 4 })),
    } as Response)

  it('ruft ohne Grenzen den Abruf über alle gemessenen Karten', async () => {
    const f = antwort()

    const result = await dashboardApi.implementationTime(7)

    expect(f.mock.calls[f.mock.calls.length - 1][0]).toBe('/api/boards/7/dashboard/implementation-time')
    expect(result).toEqual({ avgImplementationSeconds: 5400, implementationSampleCount: 4 })
  })

  it('hängt die Grenzen des Zeitraums als from und to an', async () => {
    const f = antwort()

    await dashboardApi.implementationTime(7, { from: '2026-09-14T10:00:00Z', to: '2026-09-15T10:00:00Z' })

    expect(f.mock.calls[f.mock.calls.length - 1][0]).toBe(
      '/api/boards/7/dashboard/implementation-time?from=2026-09-14T10%3A00%3A00Z&to=2026-09-15T10%3A00%3A00Z',
    )
  })
})

describe('dashboardApi.flow und aging', () => {
  const antwort = (body: unknown) =>
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      statusText: 'OK',
      text: () => Promise.resolve(JSON.stringify(body)),
    } as Response)

  it('flow ruft ohne Zone die Zone des Lesers ab', async () => {
    const fluss = { days: [], dataSince: null }
    const f = antwort(fluss)
    const zone = Intl.DateTimeFormat().resolvedOptions().timeZone

    const result = await dashboardApi.flow(7)

    expect(f.mock.calls[f.mock.calls.length - 1][0]).toBe(
      `/api/boards/7/dashboard/flow?zone=${encodeURIComponent(zone)}`,
    )
    expect(result).toEqual(fluss)
  })

  it('flow hängt eine übergebene Zone URL-kodiert an', async () => {
    const f = antwort({ days: [], dataSince: null })

    await dashboardApi.flow(7, 'America/Argentina/Buenos_Aires')

    expect(f.mock.calls[f.mock.calls.length - 1][0]).toBe(
      '/api/boards/7/dashboard/flow?zone=America%2FArgentina%2FBuenos_Aires',
    )
  })

  it('aging ruft GET /api/boards/{id}/dashboard/aging', async () => {
    const leer = { comparisonCount: 0, thresholdSeconds: null, cards: [] }
    const alter = { ready: leer, inProgress: leer, inReview: leer }
    const f = antwort(alter)

    const result = await dashboardApi.aging(7)

    const [url, init] = f.mock.calls[f.mock.calls.length - 1]
    expect(url).toBe('/api/boards/7/dashboard/aging')
    expect(init?.method).toBeUndefined()
    expect(result).toEqual(alter)
  })
})
