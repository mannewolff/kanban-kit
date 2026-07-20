import { afterEach, describe, expect, it, vi } from 'vitest'
import { accessTokensApi } from './accessTokens'

function spyFetch(body = '{}') {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    text: () => Promise.resolve(body),
  } as Response)
}

function lastCall(fetchSpy: ReturnType<typeof spyFetch>) {
  const [url, init] = fetchSpy.mock.calls[fetchSpy.mock.calls.length - 1]
  return { url, method: init?.method, body: init?.body }
}

afterEach(() => vi.restoreAllMocks())

describe('accessTokensApi', () => {
  it('list ruft GET /api/access-tokens und liefert die geparste Antwort', async () => {
    const token = {
      id: 1, name: 'board-cli', projectId: 3, boardId: 7,
      createdAt: '2026-01-01', lastUsedAt: null, revoked: false,
    }
    spyFetch(JSON.stringify([token]))
    const result = await accessTokensApi.list()
    expect(result).toEqual([token])
  })

  it('create ruft POST /api/access-tokens mit Name und Board-Bindung', async () => {
    const f = spyFetch(JSON.stringify({ id: 1, name: 'board-cli', plaintext: 'tk_geheim' }))
    const created = await accessTokensApi.create('board-cli', 3, 7)
    const c = lastCall(f)
    expect(c.url).toBe('/api/access-tokens')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ name: 'board-cli', projectId: 3, boardId: 7 })
    expect(created.plaintext).toBe('tk_geheim')
  })

  it('create ohne Bindung sendet Name (projectId/boardId undefined)', async () => {
    const f = spyFetch()
    await accessTokensApi.create('ungebunden')
    // JSON.stringify lässt undefined weg -> Body enthält nur den Namen.
    expect(JSON.parse(String(lastCall(f).body))).toEqual({ name: 'ungebunden' })
  })

  it('revoke ruft DELETE /api/access-tokens/{id}', async () => {
    const f = spyFetch()
    await accessTokensApi.revoke(5)
    const c = lastCall(f)
    expect(c.url).toBe('/api/access-tokens/5')
    expect(c.method).toBe('DELETE')
  })
})
