import { afterEach, describe, expect, it, vi } from 'vitest'
import { configApi } from './config'

afterEach(() => vi.restoreAllMocks())

describe('configApi', () => {
  it('get ruft GET /api/config und liefert die geparste Antwort', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      statusText: 'OK',
      text: () => Promise.resolve(JSON.stringify({ doneRetentionDays: 30 })),
    } as Response)
    const result = await configApi.get()
    expect(result).toEqual({ doneRetentionDays: 30 })
    expect(spy.mock.calls[0][0]).toBe('/api/config')
  })

  it('getDoneRetention ruft GET /api/admin/done-retention', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      statusText: 'OK',
      text: () => Promise.resolve(JSON.stringify({ effective: 30, override: null })),
    } as Response)
    const result = await configApi.getDoneRetention()
    expect(result).toEqual({ effective: 30, override: null })
    expect(spy.mock.calls[0][0]).toBe('/api/admin/done-retention')
  })

  it('setDoneRetention sendet PUT mit dem Tageswert', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      statusText: 'OK',
      text: () => Promise.resolve(JSON.stringify({ effective: 0, override: 0 })),
    } as Response)
    const result = await configApi.setDoneRetention(0)
    expect(result).toEqual({ effective: 0, override: 0 })
    expect(spy.mock.calls[0][0]).toBe('/api/admin/done-retention')
    expect(spy.mock.calls[0][1]).toMatchObject({ method: 'PUT', body: JSON.stringify({ days: 0 }) })
  })
})
