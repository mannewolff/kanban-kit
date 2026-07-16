import { afterEach, describe, expect, it, vi } from 'vitest'
import { adminApi } from './admin'

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

describe('adminApi', () => {
  it('listUsers ruft GET /api/admin/users und liefert die geparste Antwort', async () => {
    spyFetch(JSON.stringify([{
      id: 1, email: 'a@x.de', displayName: 'Ada', platformRole: 'USER',
      emailVerified: true, approvedAt: null, disabled: false,
    }]))
    const result = await adminApi.listUsers()
    expect(result).toHaveLength(1)
    expect(result[0].email).toBe('a@x.de')
  })

  it('setRole ruft PATCH /api/admin/users/{id} mit der Plattform-Rolle', async () => {
    const f = spyFetch()
    await adminApi.setRole(1, 'ADMIN')
    const c = lastCall(f)
    expect(c.url).toBe('/api/admin/users/1')
    expect(c.method).toBe('PATCH')
    expect(JSON.parse(String(c.body))).toEqual({ platformRole: 'ADMIN' })
  })

  it('setDisplayName ruft PATCH /api/admin/users/{id}/display-name', async () => {
    const f = spyFetch()
    await adminApi.setDisplayName(1, 'Neuer Name')
    const c = lastCall(f)
    expect(c.url).toBe('/api/admin/users/1/display-name')
    expect(c.method).toBe('PATCH')
    expect(JSON.parse(String(c.body))).toEqual({ displayName: 'Neuer Name' })
  })

  it('approve ruft POST /api/admin/users/{id}/approve', async () => {
    const f = spyFetch()
    await adminApi.approve(1)
    const c = lastCall(f)
    expect(c.url).toBe('/api/admin/users/1/approve')
    expect(c.method).toBe('POST')
  })

  it('disable ruft POST /api/admin/users/{id}/disable', async () => {
    const f = spyFetch()
    await adminApi.disable(1)
    const c = lastCall(f)
    expect(c.url).toBe('/api/admin/users/1/disable')
    expect(c.method).toBe('POST')
  })

  it('enable ruft POST /api/admin/users/{id}/enable', async () => {
    const f = spyFetch()
    await adminApi.enable(1)
    const c = lastCall(f)
    expect(c.url).toBe('/api/admin/users/1/enable')
    expect(c.method).toBe('POST')
  })

  it('bootstrap ruft POST /api/admin/bootstrap mit dem Token', async () => {
    const f = spyFetch()
    await adminApi.bootstrap('bootstrap-tok')
    const c = lastCall(f)
    expect(c.url).toBe('/api/admin/bootstrap')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ token: 'bootstrap-tok' })
  })
})
