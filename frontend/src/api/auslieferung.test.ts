import { afterEach, describe, expect, it, vi } from 'vitest'
import { auslieferungApi } from './auslieferung'

afterEach(() => vi.restoreAllMocks())

describe('auslieferungApi', () => {
  it('zuletzt ruft GET /api/projects/{id}/auslieferung/zuletzt und liefert Vorschau und Veröffentlichung', async () => {
    const f = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      statusText: 'OK',
      text: () =>
        Promise.resolve(
          JSON.stringify({
            vorschau: null,
            veroeffentlichung: { url: 'https://example.org', revision: 'abc', finishedAt: '2026-11-22T10:00:00Z' },
          }),
        ),
    } as Response)
    const result = await auslieferungApi.zuletzt(3)
    expect(f.mock.calls[0][0]).toBe('/api/projects/3/auslieferung/zuletzt')
    expect(result.vorschau).toBeNull()
    expect(result.veroeffentlichung?.url).toBe('https://example.org')
  })
})
