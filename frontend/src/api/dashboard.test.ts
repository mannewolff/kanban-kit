import { afterEach, describe, expect, it, vi } from 'vitest'
import { dashboardApi } from './dashboard'

afterEach(() => vi.restoreAllMocks())

describe('dashboardApi', () => {
  it('get ruft GET /api/boards/{id}/dashboard und liefert die KPIs', async () => {
    const kpis = {
      columnDwell: [],
      throughput: [],
      avgLeadTimeSeconds: 100,
      avgCycleTimeSeconds: null,
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
