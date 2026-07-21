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
})
