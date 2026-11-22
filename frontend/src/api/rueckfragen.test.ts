import { afterEach, describe, expect, it, vi } from 'vitest'
import { rueckfragenApi } from './rueckfragen'

function spyFetch(body = '{}', status = 200) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status,
    statusText: 'OK',
    text: () => Promise.resolve(body),
  } as Response)
}

function lastCall(fetchSpy: ReturnType<typeof spyFetch>) {
  const [url, init] = fetchSpy.mock.calls[fetchSpy.mock.calls.length - 1]
  return { url, method: init?.method, body: init?.body }
}

afterEach(() => vi.restoreAllMocks())

describe('rueckfragenApi', () => {
  it('liste ruft GET /api/cards/{id}/rueckfragen und liefert die Rückfragen', async () => {
    const f = spyFetch(JSON.stringify([{ kommentarId: 4, zustand: 'OFFEN', eintraege: [] }]))
    const result = await rueckfragenApi.liste(7)
    expect(lastCall(f).url).toBe('/api/cards/7/rueckfragen')
    expect(lastCall(f).method ?? 'GET').toBe('GET')
    expect(result[0].kommentarId).toBe(4)
  })

  it('antworten ruft POST …/rueckfragen/{kommentarId}/antwort mit dem Auftrag', async () => {
    const f = spyFetch(JSON.stringify({ kommentarId: 4, zustand: 'BEANTWORTET', eintraege: [] }), 201)
    const result = await rueckfragenApi.antworten(7, 4, { ja: true, antworten: [{ nr: 2, text: 'Grün' }] })
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/7/rueckfragen/4/antwort')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ ja: true, antworten: [{ nr: 2, text: 'Grün' }] })
    expect(result.zustand).toBe('BEANTWORTET')
  })
})
