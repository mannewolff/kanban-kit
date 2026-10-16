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

  it('requestCode ruft POST /api/auth/code/request mit der E-Mail', async () => {
    const f = mockEmptyResponse()
    await authApi.requestCode('a@x.de')
    const c = lastCall(f)
    expect(c.url).toBe('/api/auth/code/request')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ email: 'a@x.de' })
  })

  it('verifyCode ruft POST /api/auth/code/verify mit E-Mail und Code und validiert die Antwort', async () => {
    const f = mockJsonResponse(validMe)
    const result = await authApi.verifyCode('a@x.de', '042817')
    const c = lastCall(f)
    expect(c.url).toBe('/api/auth/code/verify')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ email: 'a@x.de', code: '042817' })
    expect(result.userId).toBe(1)
  })

  it('verifyCode wirft ApiError bei typverletzter Server-Antwort', async () => {
    mockJsonResponse({ nope: true })
    await expect(authApi.verifyCode('a@x.de', '042817')).rejects.toBeInstanceOf(ApiError)
  })

  it('linkLogin ruft POST /api/auth/code/link mit dem Token und validiert die Antwort', async () => {
    const f = mockJsonResponse(validMe)
    const result = await authApi.linkLogin('tok-123')
    const c = lastCall(f)
    expect(c.url).toBe('/api/auth/code/link')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ token: 'tok-123' })
    expect(result.userId).toBe(1)
  })

  it('linkLogin wirft ApiError bei typverletzter Server-Antwort', async () => {
    mockJsonResponse({ nope: true })
    await expect(authApi.linkLogin('tok-123')).rejects.toBeInstanceOf(ApiError)
  })

  it('bietet keine Passwort-Aufrufe mehr an', () => {
    for (const entfallen of ['login', 'verify', 'forgot', 'reset']) {
      expect(authApi).not.toHaveProperty(entfallen)
    }
  })

  it('logout ruft POST /api/auth/logout', async () => {
    const f = mockEmptyResponse()
    await authApi.logout()
    const c = lastCall(f)
    expect(c.url).toBe('/api/auth/logout')
    expect(c.method).toBe('POST')
  })

  it('register ruft POST /api/auth/register mit E-Mail und Anzeigenamen, ohne Passwort', async () => {
    const f = mockJsonResponse({ id: 1, email: 'a@x.de' })
    const result = await authApi.register('a@x.de', 'Ada')
    const c = lastCall(f)
    expect(c.url).toBe('/api/auth/register')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ email: 'a@x.de', displayName: 'Ada' })
    expect(result).toEqual({ id: 1, email: 'a@x.de' })
  })
})
