import { afterEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from './boards'
import { columnsApi } from './columns'

function spyFetch() {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    text: () => Promise.resolve('{}'),
  } as Response)
}

function lastCall(fetchSpy: ReturnType<typeof spyFetch>) {
  const [url, init] = fetchSpy.mock.calls[fetchSpy.mock.calls.length - 1]
  return { url, method: init?.method, body: init?.body }
}

afterEach(() => vi.restoreAllMocks())

describe('columnsApi', () => {
  it('create ruft POST /api/boards/{id}/columns mit Name und WIP-Limit', async () => {
    const f = spyFetch()
    await columnsApi.create(3, 'Todo', 5)
    const c = lastCall(f)
    expect(c.url).toBe('/api/boards/3/columns')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ name: 'Todo', wipLimit: 5 })
  })

  it('update ruft PATCH /api/columns/{id} mit Name und WIP-Limit', async () => {
    const f = spyFetch()
    await columnsApi.update(7, 'Neu', null)
    const c = lastCall(f)
    expect(c.url).toBe('/api/columns/7')
    expect(c.method).toBe('PATCH')
    expect(JSON.parse(String(c.body))).toEqual({ name: 'Neu', wipLimit: null })
  })

  it('remove ruft DELETE /api/columns/{id}', async () => {
    const f = spyFetch()
    await columnsApi.remove(7)
    const c = lastCall(f)
    expect(c.url).toBe('/api/columns/7')
    expect(c.method).toBe('DELETE')
  })

  it('reorder ruft PUT /api/boards/{id}/columns/order mit columnIds', async () => {
    const f = spyFetch()
    await columnsApi.reorder(3, [2, 1])
    const c = lastCall(f)
    expect(c.url).toBe('/api/boards/3/columns/order')
    expect(c.method).toBe('PUT')
    expect(JSON.parse(String(c.body))).toEqual({ columnIds: [2, 1] })
  })
})

describe('boardsApi.rename', () => {
  it('ruft PATCH /api/boards/{id} mit dem Namen', async () => {
    const f = spyFetch()
    await boardsApi.rename(9, 'Umbenannt')
    const c = lastCall(f)
    expect(c.url).toBe('/api/boards/9')
    expect(c.method).toBe('PATCH')
    expect(JSON.parse(String(c.body))).toEqual({ name: 'Umbenannt' })
  })
})
