import { afterEach, describe, expect, it, vi } from 'vitest'
import { authApi } from './auth'
import { ApiError } from './client'

const validMe = { userId: 1, email: 'a@x.de', displayName: 'Ada', platformRole: 'USER', memberships: [] }

function mockJsonResponse(body: unknown) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    text: () => Promise.resolve(JSON.stringify(body)),
  } as Response)
}

function mockEmptyResponse() {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    text: () => Promise.resolve(''),
  } as Response)
}

function lastCall(fetchSpy: ReturnType<typeof mockJsonResponse>) {
  const [url, init] = fetchSpy.mock.calls[fetchSpy.mock.calls.length - 1]
  return { url, method: init?.method, body: init?.body }
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('authApi.me – Laufzeit-Validierung der Me-Antwort', () => {
  it('gibt Me bei valider Antwort zurück', async () => {
    mockJsonResponse({
      userId: 1,
      email: 'a@x.de',
      displayName: 'Ada',
      platformRole: 'USER',
      memberships: [],
    })

    const me = await authApi.me()

    expect(me.userId).toBe(1)
  })

  it('wirft ApiError bei typverletzter Server-Antwort', async () => {
    mockJsonResponse({ userId: 'nope', email: 'a@x.de' })

    await expect(authApi.me()).rejects.toBeInstanceOf(ApiError)
  })

  it('wirft ApiError bei null als Antwort', async () => {
    mockJsonResponse(null)

    await expect(authApi.me()).rejects.toBeInstanceOf(ApiError)
  })
})

describe('authApi – restliche Endpunkte', () => {
  it('updateProfile ruft PATCH /api/me mit dem Anzeigenamen und validiert die Antwort', async () => {
    const f = mockJsonResponse(validMe)
    const result = await authApi.updateProfile('Neuer Name')
    const c = lastCall(f)
    expect(c.url).toBe('/api/me')
    expect(c.method).toBe('PATCH')
    expect(JSON.parse(String(c.body))).toEqual({ displayName: 'Neuer Name' })
    expect(result.userId).toBe(1)
  })

  it('login ruft POST /api/auth/login mit E-Mail und Passwort und validiert die Antwort', async () => {
    const f = mockJsonResponse(validMe)
    const result = await authApi.login('a@x.de', 'geheim')
    const c = lastCall(f)
    expect(c.url).toBe('/api/auth/login')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ email: 'a@x.de', password: 'geheim' })
    expect(result.userId).toBe(1)
  })

  it('login wirft ApiError bei typverletzter Server-Antwort', async () => {
    mockJsonResponse({ nope: true })
    await expect(authApi.login('a@x.de', 'geheim')).rejects.toBeInstanceOf(ApiError)
  })

  it('logout ruft POST /api/auth/logout', async () => {
    const f = mockEmptyResponse()
    await authApi.logout()
    const c = lastCall(f)
    expect(c.url).toBe('/api/auth/logout')
    expect(c.method).toBe('POST')
  })

  it('register ruft POST /api/auth/register mit E-Mail, Passwort und Anzeigenamen', async () => {
    const f = mockJsonResponse({ id: 1, email: 'a@x.de' })
    const result = await authApi.register('a@x.de', 'geheim', 'Ada')
    const c = lastCall(f)
    expect(c.url).toBe('/api/auth/register')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ email: 'a@x.de', password: 'geheim', displayName: 'Ada' })
    expect(result).toEqual({ id: 1, email: 'a@x.de' })
  })

  it('verify ruft GET /api/auth/verify mit URL-kodiertem Token', async () => {
    const f = mockEmptyResponse()
    await authApi.verify('a token/with?chars')
    const c = lastCall(f)
    expect(c.url).toBe(`/api/auth/verify?token=${encodeURIComponent('a token/with?chars')}`)
  })

  it('forgot ruft POST /api/auth/forgot mit der E-Mail', async () => {
    const f = mockEmptyResponse()
    await authApi.forgot('a@x.de')
    const c = lastCall(f)
    expect(c.url).toBe('/api/auth/forgot')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ email: 'a@x.de' })
  })

  it('reset ruft POST /api/auth/reset mit Token und neuem Passwort', async () => {
    const f = mockEmptyResponse()
    await authApi.reset('tok-123', 'neuesPasswort')
    const c = lastCall(f)
    expect(c.url).toBe('/api/auth/reset')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ token: 'tok-123', newPassword: 'neuesPasswort' })
  })
})
