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
    // memberNumbers und rootNumbers werden unveraendert durchgereicht (Issue #634); rootNumbers
    // ist dabei stets eine Teilmenge von memberNumbers.
    const epic = {
      id: 1, number: 5, title: 'Epic', description: null, shortcode: 'EPC', done: 1, total: 3,
      memberNumbers: [7, 8, 9], rootNumbers: [7], requirementCardNumber: 7,
    }
    spyFetch(JSON.stringify([epic]))
    const result = await epicsApi.list(3)
    expect(result).toEqual([epic])
    expect(result[0].memberNumbers).toEqual([7, 8, 9])
    expect(result[0].rootNumbers).toEqual([7])
    // Die Anforderungsnummer wird unveraendert durchgereicht (Issue #641).
    expect(result[0].requirementCardNumber).toBe(7)
  })

  it('list reicht eine fehlende Anforderung als null durch', async () => {
    // Gegenprobe: `null` ist ein gueltiger Dauerzustand, kein Ladezustand — es darf weder zu 0
    // noch zu undefined werden.
    spyFetch(
      JSON.stringify([
        {
          id: 1, number: 5, title: 'Epic', description: null, shortcode: null, done: 0, total: 0,
          memberNumbers: [], rootNumbers: [], requirementCardNumber: null,
        },
      ]),
    )
    const result = await epicsApi.list(3)
    expect(result[0].requirementCardNumber).toBeNull()
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
