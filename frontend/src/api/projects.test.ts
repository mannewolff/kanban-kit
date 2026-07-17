import { afterEach, describe, expect, it, vi } from 'vitest'
import { projectsApi } from './projects'

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

describe('projectsApi', () => {
  it('list ruft GET /api/projects und liefert die geparste Antwort', async () => {
    spyFetch(JSON.stringify([{ id: 1, name: 'Kanban Kit', role: 'OWNER', createdAt: '2026-01-01' }]))
    const result = await projectsApi.list()
    expect(result).toEqual([{ id: 1, name: 'Kanban Kit', role: 'OWNER', createdAt: '2026-01-01' }])
  })

  it('create ruft POST /api/projects mit Name und Owner-E-Mail', async () => {
    const f = spyFetch()
    await projectsApi.create('Neu', 'owner@x.de')
    const c = lastCall(f)
    expect(c.url).toBe('/api/projects')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ name: 'Neu', ownerEmail: 'owner@x.de' })
  })

  it('rename ruft PATCH /api/projects/{id} mit dem Namen', async () => {
    const f = spyFetch()
    await projectsApi.rename(3, 'Umbenannt')
    const c = lastCall(f)
    expect(c.url).toBe('/api/projects/3')
    expect(c.method).toBe('PATCH')
    expect(JSON.parse(String(c.body))).toEqual({ name: 'Umbenannt' })
  })

  it('remove ruft DELETE /api/projects/{id}', async () => {
    const f = spyFetch()
    await projectsApi.remove(3)
    const c = lastCall(f)
    expect(c.url).toBe('/api/projects/3')
    expect(c.method).toBe('DELETE')
  })

  it('transferOwner ruft POST /api/projects/{id}/owner mit der neuen Owner-ID', async () => {
    const f = spyFetch()
    await projectsApi.transferOwner(3, 42)
    const c = lastCall(f)
    expect(c.url).toBe('/api/projects/3/owner')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ newOwnerUserId: 42 })
  })
})
