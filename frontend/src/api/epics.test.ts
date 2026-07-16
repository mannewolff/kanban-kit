import { afterEach, describe, expect, it, vi } from 'vitest'
import { epicsApi } from './epics'

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

describe('epicsApi', () => {
  it('list ruft GET /api/boards/{id}/epics und liefert die geparste Antwort', async () => {
    spyFetch(JSON.stringify([{ id: 1, number: 5, title: 'Epic', description: null, shortcode: 'EPC', done: 1, total: 3 }]))
    const result = await epicsApi.list(3)
    expect(result).toEqual([{ id: 1, number: 5, title: 'Epic', description: null, shortcode: 'EPC', done: 1, total: 3 }])
  })

  it('create ruft POST /api/boards/{id}/cards mit type EPIC', async () => {
    const f = spyFetch()
    await epicsApi.create(3, 'Neues Epic', 'Beschreibung', 'NE')
    const c = lastCall(f)
    expect(c.url).toBe('/api/boards/3/cards')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({
      type: 'EPIC', title: 'Neues Epic', description: 'Beschreibung', shortcode: 'NE',
    })
  })

  it('assign ruft PATCH /api/cards/{id}/parent mit parentId', async () => {
    const f = spyFetch()
    await epicsApi.assign(7, 1)
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/7/parent')
    expect(c.method).toBe('PATCH')
    expect(JSON.parse(String(c.body))).toEqual({ parentId: 1 })
  })

  it('assign mit null löst die Epic-Zuordnung', async () => {
    const f = spyFetch()
    await epicsApi.assign(7, null)
    const c = lastCall(f)
    expect(JSON.parse(String(c.body))).toEqual({ parentId: null })
  })

  it('remove ruft DELETE /api/cards/{id}', async () => {
    const f = spyFetch()
    await epicsApi.remove(1)
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/1')
    expect(c.method).toBe('DELETE')
  })
})
