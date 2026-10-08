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
