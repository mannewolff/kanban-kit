import { afterEach, describe, expect, it, vi } from 'vitest'
import { membersApi } from './members'

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

describe('membersApi', () => {
  it('list ruft GET /api/projects/{id}/members und liefert die geparste Antwort', async () => {
    spyFetch(JSON.stringify([{ userId: 1, email: 'a@x.de', displayName: 'Ada', role: 'MEMBER' }]))
    const result = await membersApi.list(3)
    expect(result).toEqual([{ userId: 1, email: 'a@x.de', displayName: 'Ada', role: 'MEMBER' }])
  })

  it('changeRole ruft PATCH /api/projects/{id}/members/{userId} mit der Rolle', async () => {
    const f = spyFetch()
    await membersApi.changeRole(3, 5, 'ADMIN')
    const c = lastCall(f)
    expect(c.url).toBe('/api/projects/3/members/5')
    expect(c.method).toBe('PATCH')
    expect(JSON.parse(String(c.body))).toEqual({ role: 'ADMIN' })
  })

  it('changeDisplayName ruft PATCH /api/projects/{id}/members/{userId}/display-name', async () => {
    const f = spyFetch()
    await membersApi.changeDisplayName(3, 5, 'Neuer Name')
    const c = lastCall(f)
    expect(c.url).toBe('/api/projects/3/members/5/display-name')
    expect(c.method).toBe('PATCH')
    expect(JSON.parse(String(c.body))).toEqual({ displayName: 'Neuer Name' })
  })

  it('remove ruft DELETE /api/projects/{id}/members/{userId}', async () => {
    const f = spyFetch()
    await membersApi.remove(3, 5)
    const c = lastCall(f)
    expect(c.url).toBe('/api/projects/3/members/5')
    expect(c.method).toBe('DELETE')
  })

  it('invite ruft POST /api/projects/{id}/invitations mit E-Mail und Rolle', async () => {
    const f = spyFetch()
    await membersApi.invite(3, 'neu@x.de', 'MEMBER')
    const c = lastCall(f)
    expect(c.url).toBe('/api/projects/3/invitations')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ email: 'neu@x.de', role: 'MEMBER' })
  })

  it('accept ruft POST /api/invitations/accept mit dem Token und liefert das Mitglied', async () => {
    spyFetch(JSON.stringify({ userId: 5, email: 'neu@x.de', displayName: 'Neu', role: 'MEMBER' }))
    const result = await membersApi.accept('tok-123')
    expect(result).toEqual({ userId: 5, email: 'neu@x.de', displayName: 'Neu', role: 'MEMBER' })
  })
})
