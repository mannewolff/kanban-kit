import { afterEach, describe, expect, it, vi } from 'vitest'
import { commentsApi } from './comments'

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

describe('commentsApi', () => {
  it('list ruft GET /api/cards/{id}/comments und liefert die geparste Antwort', async () => {
    const f = spyFetch(JSON.stringify([{
      id: 1, cardId: 7, authorUserId: 5, authorName: 'Ada', body: 'Hallo',
      createdAt: '2026-01-01', updatedAt: '2026-01-01',
    }]))
    const result = await commentsApi.list(7)
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/7/comments')
    expect(c.method ?? 'GET').toBe('GET')
    expect(result).toHaveLength(1)
    expect(result[0].body).toBe('Hallo')
  })

  it('list liefert Art des Zugangs und Absender KI, wie der Server sie meldet (Issue #2219)', async () => {
    spyFetch(JSON.stringify([{
      id: 1, cardId: 7, authorUserId: 5, authorName: 'Rechenzentrum', body: '## Gespräch\n\nHallo',
      createdAt: '2026-01-01', updatedAt: '2026-01-01', zugangArt: 'STANDARD', vonKi: true,
    }]))
    const [beitrag] = await commentsApi.list(7)
    expect(beitrag.zugangArt).toBe('STANDARD')
    expect(beitrag.vonKi).toBe(true)
  })

  it('create ruft POST /api/cards/{id}/comments mit dem Text', async () => {
    const f = spyFetch()
    await commentsApi.create(7, 'Neuer Kommentar')
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/7/comments')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ body: 'Neuer Kommentar' })
  })

  it('update ruft PATCH /api/comments/{id} mit dem Text', async () => {
    const f = spyFetch()
    await commentsApi.update(3, 'Geändert')
    const c = lastCall(f)
    expect(c.url).toBe('/api/comments/3')
    expect(c.method).toBe('PATCH')
    expect(JSON.parse(String(c.body))).toEqual({ body: 'Geändert' })
  })

  it('remove ruft DELETE /api/comments/{id}', async () => {
    const f = spyFetch()
    await commentsApi.remove(3)
    const c = lastCall(f)
    expect(c.url).toBe('/api/comments/3')
    expect(c.method).toBe('DELETE')
  })
})
