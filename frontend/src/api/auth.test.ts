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

  it('übernimmt die Farbwahl des Kontos (#1911)', async () => {
    mockJsonResponse({ ...validMe, colorScheme: 'DARK' })

    expect((await authApi.me()).colorScheme).toBe('DARK')
  })

  it('fällt bei fehlender Farbwahl auf SYSTEM zurück', async () => {
    mockJsonResponse(validMe)

    expect((await authApi.me()).colorScheme).toBe('SYSTEM')
  })

  it('fällt bei unbekannter Farbwahl auf SYSTEM zurück', async () => {
    mockJsonResponse({ ...validMe, colorScheme: 'PURPLE' })

    expect((await authApi.me()).colorScheme).toBe('SYSTEM')
  })
})

describe('authApi.me – Vorschau „Ansicht als XY“ (Plan #1914, E14)', () => {
  const vorschau = {
    kennung: 'vk-42',
    zielName: 'Grace',
    adminName: 'Ada',
    laeuftAbAm: '2026-10-27T10:30:00Z',
  }

  it('übernimmt die laufende Vorschau', async () => {
    mockJsonResponse({ ...validMe, vorschau })

    expect((await authApi.me()).vorschau).toEqual(vorschau)
  })

  it.each([
    ['null', null],
    ['ein fehlendes Feld', undefined],
  ])('setzt bei %s keine Vorschau', async (_fall, wert) => {
    mockJsonResponse({ ...validMe, vorschau: wert })

    expect((await authApi.me()).vorschau).toBeNull()
  })

  it.each([
    ['ohne Kennung', { ...vorschau, kennung: undefined }],
    ['mit leerer Kennung', { ...vorschau, kennung: '' }],
    ['ohne Namen von XY', { ...vorschau, zielName: 7 }],
    ['ohne Namen des Admins', { ...vorschau, adminName: null }],
    ['ohne Ablauf', { ...vorschau, laeuftAbAm: undefined }],
    ['als Text', 'vk-42'],
  ])('wirft ApiError bei einer Vorschau %s', async (_fall, wert) => {
    mockJsonResponse({ ...validMe, vorschau: wert })

    await expect(authApi.me()).rejects.toBeInstanceOf(ApiError)
  })
})

describe('authApi – restliche Endpunkte', () => {
  it('vorschauBeenden ruft DELETE /api/vorschau', async () => {
    const f = mockEmptyResponse()
    await authApi.vorschauBeenden()
    const c = lastCall(f)
    expect(c.url).toBe('/api/vorschau')
    expect(c.method).toBe('DELETE')
  })

  it('eigenesVorschauProtokoll ruft GET /api/me/vorschau-protokoll und liefert die Einträge', async () => {
    const eintrag = {
      adminName: 'Ada',
      adminKontoGeloescht: false,
      gestartetAm: '2026-10-27T10:00:00Z',
      laeuftAbAm: '2026-10-27T10:30:00Z',
      beendetAm: null,
      endgrund: null,
    }
    const f = mockJsonResponse([eintrag])
    const result = await authApi.eigenesVorschauProtokoll()
    const c = lastCall(f)
    expect(c.url).toBe('/api/me/vorschau-protokoll')
    expect(c.method).toBeUndefined()
    expect(result).toEqual([eintrag])
  })

  it('updateProfile ruft PATCH /api/me mit dem Anzeigenamen und validiert die Antwort', async () => {
    const f = mockJsonResponse(validMe)
    const result = await authApi.updateProfile('Neuer Name')
    const c = lastCall(f)
    expect(c.url).toBe('/api/me')
    expect(c.method).toBe('PATCH')
    expect(JSON.parse(String(c.body))).toEqual({ displayName: 'Neuer Name' })
    expect(result.userId).toBe(1)
  })

  it('setColorScheme ruft PUT /api/me/color-scheme mit der Wahl und validiert die Antwort', async () => {
    const f = mockJsonResponse({ ...validMe, colorScheme: 'LIGHT' })
    const result = await authApi.setColorScheme('LIGHT')
    const c = lastCall(f)
    expect(c.url).toBe('/api/me/color-scheme')
    expect(c.method).toBe('PUT')
    expect(JSON.parse(String(c.body))).toEqual({ colorScheme: 'LIGHT' })
    expect(result.colorScheme).toBe('LIGHT')
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
