import { afterEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from './boards'

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

describe('boardsApi', () => {
  it('list ruft GET /api/projects/{id}/boards und liefert die geparste Antwort', async () => {
    spyFetch(JSON.stringify([{ id: 1, projectId: 3, name: 'Board', createdAt: '2026-01-01', columns: [] }]))
    const result = await boardsApi.list(3)
    expect(result).toEqual([{ id: 1, projectId: 3, name: 'Board', createdAt: '2026-01-01', columns: [] }])
  })

  it('listArchived ruft GET /api/projects/{id}/boards/archived', async () => {
    const f = spyFetch()
    await boardsApi.listArchived(3)
    const c = lastCall(f)
    expect(c.url).toBe('/api/projects/3/boards/archived')
    expect(c.method).toBeUndefined()
  })

  it('get ruft GET /api/boards/{id} und liefert das Board', async () => {
    spyFetch(JSON.stringify({ id: 9, projectId: 3, name: 'Board', createdAt: '2026-01-01', columns: [] }))
    const result = await boardsApi.get(9)
    expect(result.id).toBe(9)
  })

  it('create ruft POST /api/projects/{id}/boards mit dem Namen', async () => {
    const f = spyFetch()
    await boardsApi.create(3, 'Neues Board')
    const c = lastCall(f)
    expect(c.url).toBe('/api/projects/3/boards')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ name: 'Neues Board' })
  })

  it('remove (Archivieren) ruft DELETE /api/boards/{id}', async () => {
    const f = spyFetch()
    await boardsApi.remove(9)
    const c = lastCall(f)
    expect(c.url).toBe('/api/boards/9')
    expect(c.method).toBe('DELETE')
  })

  it('restore ruft POST /api/boards/{id}/restore', async () => {
    const f = spyFetch()
    await boardsApi.restore(9)
    const c = lastCall(f)
    expect(c.url).toBe('/api/boards/9/restore')
    expect(c.method).toBe('POST')
  })

  it('purge (endgültiges Löschen) ruft DELETE /api/boards/{id}/purge', async () => {
    const f = spyFetch()
    await boardsApi.purge(9)
    const c = lastCall(f)
    expect(c.url).toBe('/api/boards/9/purge')
    expect(c.method).toBe('DELETE')
  })
})
