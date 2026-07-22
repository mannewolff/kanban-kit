import { afterEach, describe, expect, it, vi } from 'vitest'
import { ideasApi } from './ideas'

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

describe('ideasApi', () => {
  it('list ruft GET /api/projects/{id}/ideas und liefert die geparste Antwort', async () => {
    spyFetch(JSON.stringify([{ id: 1, boardId: null, title: 'Idee' }]))
    const result = await ideasApi.list(5)
    expect(result).toEqual([{ id: 1, boardId: null, title: 'Idee' }])
  })

  it('list nutzt keine Methode (GET)', async () => {
    const f = spyFetch('[]')
    await ideasApi.list(5)
    const c = lastCall(f)
    expect(c.url).toBe('/api/projects/5/ideas')
    expect(c.method).toBeUndefined()
  })

  it('create ruft POST /api/projects/{id}/ideas mit Titel/Beschreibung/Zielboard', async () => {
    const f = spyFetch()
    await ideasApi.create(5, { title: 'T', description: 'd', targetBoardId: 9 })
    const c = lastCall(f)
    expect(c.url).toBe('/api/projects/5/ideas')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ title: 'T', description: 'd', targetBoardId: 9 })
  })

  it('planOntoBoard ruft PUT /api/cards/{id}/plan mit dem Zielboard', async () => {
    const f = spyFetch()
    await ideasApi.planOntoBoard(3, 9)
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/3/plan')
    expect(c.method).toBe('PUT')
    expect(JSON.parse(String(c.body))).toEqual({ targetBoardId: 9 })
  })

  it('moveBackToPool ruft PUT /api/cards/{id}/to-pool', async () => {
    const f = spyFetch()
    await ideasApi.moveBackToPool(3)
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/3/to-pool')
    expect(c.method).toBe('PUT')
  })
})
