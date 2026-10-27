import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  apiFetch,
  ApiError,
  apiErrorMessage,
  notifyUnauthorized,
  setNeuLaden,
  setNurLesendHandler,
  setUnauthorizedHandler,
  setVorschauKennung,
  startseiteNeuLaden,
  vorschauKopf,
} from './client'
import { en } from '../i18n/en'

function mockErrorResponse(status: number, body: string, statusText = 'Error') {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: false,
    status,
    statusText,
    text: () => Promise.resolve(body),
  } as Response)
}

function mockOkResponse(body: string) {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    text: () => Promise.resolve(body),
  } as Response)
}

async function failingFetch(): Promise<ApiError> {
  try {
    await apiFetch('/api/test')
  } catch (e) {
    if (e instanceof ApiError) return e
    throw e
  }
  throw new Error('apiFetch hätte werfen müssen')
}

afterEach(() => {
  vi.restoreAllMocks()
  setUnauthorizedHandler(null)
  setVorschauKennung(null)
  setNeuLaden(null)
  setNurLesendHandler(null)
})

describe('apiFetch – Anfrage', () => {
  it('sendet Cookies mit und setzt den JSON-Header', async () => {
    mockOkResponse('')
    await apiFetch('/api/test')
    const init = vi.mocked(globalThis.fetch).mock.calls[0][1]
    expect(init?.credentials).toBe('include')
    expect(init?.headers).toEqual({ 'Content-Type': 'application/json', 'X-Vorschau': 'keine' })
  })

  it('behält eigene Kopfzeilen des Aufrufers neben Content-Type und X-Vorschau', async () => {
    mockOkResponse('')
    await apiFetch('/api/test', { method: 'PUT', headers: { Authorization: 'Bearer x' } })
    const init = vi.mocked(globalThis.fetch).mock.calls[0][1]
    expect(init?.method).toBe('PUT')
    expect(init?.headers).toEqual({
      'Content-Type': 'application/json',
      Authorization: 'Bearer x',
      'X-Vorschau': 'keine',
    })
  })
})

describe('X-Vorschau (Plan #1914, E8)', () => {
  it('trägt die Kennung der Vorschau, die der Tab zu sehen glaubt', async () => {
    setVorschauKennung('vk-42')
    mockOkResponse('')
    await apiFetch('/api/test')
    const init = vi.mocked(globalThis.fetch).mock.calls[0][1]
    expect((init?.headers as Record<string, string>)['X-Vorschau']).toBe('vk-42')
  })

  it('trägt nach dem Zurücksetzen wieder „keine“', async () => {
    setVorschauKennung('vk-42')
    setVorschauKennung(null)
    mockOkResponse('')
    await apiFetch('/api/test')
    const init = vi.mocked(globalThis.fetch).mock.calls[0][1]
    expect((init?.headers as Record<string, string>)['X-Vorschau']).toBe('keine')
  })

  it('lässt sich vom Aufrufer nicht überschreiben', async () => {
    setVorschauKennung('vk-42')
    mockOkResponse('')
    await apiFetch('/api/test', { headers: { 'X-Vorschau': 'keine' } })
    const init = vi.mocked(globalThis.fetch).mock.calls[0][1]
    expect((init?.headers as Record<string, string>)['X-Vorschau']).toBe('vk-42')
  })

  it('liefert die Kopfzeile für Direktaufrufe', () => {
    expect(vorschauKopf()).toEqual({ 'X-Vorschau': 'keine' })
    setVorschauKennung('vk-7')
    expect(vorschauKopf()).toEqual({ 'X-Vorschau': 'vk-7' })
  })
})

describe('ApiError.type', () => {
  it('übernimmt type aus dem Problem-Körper', async () => {
    mockErrorResponse(403, JSON.stringify({ type: 'vorschau-verborgen', title: 'Forbidden', status: 403 }))
    const error = await failingFetch()
    expect(error.type).toBe('vorschau-verborgen')
  })

  it.each([
    ['ohne type', JSON.stringify({ title: 'Forbidden' })],
    ['mit leerem type', JSON.stringify({ type: '', title: 'Forbidden' })],
    ['mit nicht-textlichem type', JSON.stringify({ type: 7, title: 'Forbidden' })],
    ['bei Nicht-JSON-Body', 'Forbidden'],
  ])('bleibt %s undefiniert', async (_fall, body) => {
    mockErrorResponse(403, body)
    const error = await failingFetch()
    expect(error.type).toBeUndefined()
  })
})

describe('409 vorschau-gewechselt', () => {
  it('lädt neu und wirft danach einen ApiError', async () => {
    const neuLaden = vi.fn()
    setNeuLaden(neuLaden)
    mockErrorResponse(409, JSON.stringify({ type: 'vorschau-gewechselt', status: 409, detail: 'gewechselt' }))

    const error = await failingFetch()

    expect(neuLaden).toHaveBeenCalledTimes(1)
    expect(error.status).toBe(409)
    expect(error.type).toBe('vorschau-gewechselt')
  })

  it('lädt bei einem anderen 409 nicht neu', async () => {
    const neuLaden = vi.fn()
    setNeuLaden(neuLaden)
    mockErrorResponse(409, JSON.stringify({ type: 'about:blank', status: 409, detail: 'Spalte nicht leer' }))

    await failingFetch()

    expect(neuLaden).not.toHaveBeenCalled()
  })

  it('lädt bei vorschau-gewechselt mit anderem Status nicht neu', async () => {
    const neuLaden = vi.fn()
    setNeuLaden(neuLaden)
    mockErrorResponse(400, JSON.stringify({ type: 'vorschau-gewechselt', status: 400 }))

    await failingFetch()

    expect(neuLaden).not.toHaveBeenCalled()
  })

  it('führt ohne ersetzte Funktion auf die Startseite', async () => {
    const assign = vi.fn()
    vi.stubGlobal('location', { ...window.location, assign })
    try {
      setNeuLaden(null)
      mockErrorResponse(409, JSON.stringify({ type: 'vorschau-gewechselt', status: 409 }))

      await failingFetch()

      expect(assign).toHaveBeenCalledWith('/')
    } finally {
      vi.unstubAllGlobals()
    }
  })
})

describe('startseiteNeuLaden', () => {
  it('lädt über die ersetzte Funktion neu', () => {
    const neuLaden = vi.fn()
    setNeuLaden(neuLaden)

    startseiteNeuLaden()

    expect(neuLaden).toHaveBeenCalledTimes(1)
  })

  it('führt ohne ersetzte Funktion auf die Startseite', () => {
    const assign = vi.fn()
    vi.stubGlobal('location', { ...window.location, assign })
    try {
      startseiteNeuLaden()

      expect(assign).toHaveBeenCalledWith('/')
    } finally {
      vi.unstubAllGlobals()
    }
  })
})

describe('403 vorschau-nur-lesend', () => {
  it('meldet „Vorschau – nur lesend“ über den registrierten Meldeweg', async () => {
    const melde = vi.fn()
    setNurLesendHandler(melde)
    mockErrorResponse(403, JSON.stringify({ type: 'vorschau-nur-lesend', status: 403, detail: 'nur lesend' }))

    const error = await failingFetch()

    expect(melde).toHaveBeenCalledTimes(1)
    expect(error.status).toBe(403)
    expect(error.type).toBe('vorschau-nur-lesend')
  })

  it.each([
    ['einem anderen 403', 403, 'vorschau-verborgen'],
    ['vorschau-nur-lesend mit anderem Status', 409, 'vorschau-nur-lesend'],
  ])('meldet bei %s nichts', async (_fall, status, type) => {
    const melde = vi.fn()
    setNurLesendHandler(melde)
    mockErrorResponse(status, JSON.stringify({ type, status }))

    await failingFetch()

    expect(melde).not.toHaveBeenCalled()
  })

  it('meldet nach setNurLesendHandler(null) nichts mehr und wirft trotzdem', async () => {
    const melde = vi.fn()
    setNurLesendHandler(melde)
    setNurLesendHandler(null)
    mockErrorResponse(403, JSON.stringify({ type: 'vorschau-nur-lesend', status: 403 }))

    const error = await failingFetch()

    expect(melde).not.toHaveBeenCalled()
    expect(error.type).toBe('vorschau-nur-lesend')
  })
})

describe('ApiError', () => {
  it('trägt den Namen ApiError', () => {
    expect(new ApiError(400, 'x').name).toBe('ApiError')
  })
})

describe('apiFetch – ApiError aus RFC-9457 Problem Details', () => {
  it('nutzt detail als message', async () => {
    mockErrorResponse(
      404,
      JSON.stringify({ type: 'about:blank', title: 'Not Found', status: 404, detail: 'Karte nicht gefunden' }),
    )

    const error = await failingFetch()

    expect(error.status).toBe(404)
    expect(error.message).toBe('Karte nicht gefunden')
    expect(error.fieldErrors).toBeUndefined()
  })

  it('fällt ohne detail auf title zurück', async () => {
    mockErrorResponse(409, JSON.stringify({ title: 'Conflict', status: 409 }))

    const error = await failingFetch()

    expect(error.status).toBe(409)
    expect(error.message).toBe('Conflict')
  })

  it('stellt fieldErrors typisiert bereit', async () => {
    mockErrorResponse(
      400,
      JSON.stringify({
        title: 'Bad Request',
        status: 400,
        detail: 'Validierung fehlgeschlagen',
        fieldErrors: { title: 'darf nicht leer sein', name: 'Ungültiger Wert' },
      }),
    )

    const error = await failingFetch()

    expect(error.message).toBe('Validierung fehlgeschlagen')
    expect(error.fieldErrors).toEqual({ title: 'darf nicht leer sein', name: 'Ungültiger Wert' })
  })

  it('ignoriert Nicht-String-Werte in fieldErrors', async () => {
    mockErrorResponse(
      400,
      JSON.stringify({ detail: 'Validierung fehlgeschlagen', fieldErrors: { count: 3 } }),
    )

    const error = await failingFetch()

    expect(error.fieldErrors).toBeUndefined()
  })

  it('nutzt bei Nicht-JSON-Body den Roh-Body als message (z. B. Security-401)', async () => {
    mockErrorResponse(401, 'Unauthorized-Seite')

    const error = await failingFetch()

    expect(error.status).toBe(401)
    expect(error.message).toBe('Unauthorized-Seite')
  })

  it('nutzt bei JSON-Body ohne detail/title den Roh-Body als message', async () => {
    const body = JSON.stringify({ timestamp: '2026-07-11', status: 401, error: 'Unauthorized' })
    mockErrorResponse(401, body)

    const error = await failingFetch()

    expect(error.message).toBe(body)
  })

  it('fällt bei JSON null auf den Roh-Body zurück', async () => {
    mockErrorResponse(400, 'null')

    expect((await failingFetch()).message).toBe('null')
  })

  it('fällt bei einem JSON-Primitiv (Zahl) auf den Roh-Body zurück', async () => {
    mockErrorResponse(400, '42')

    expect((await failingFetch()).message).toBe('42')
  })

  it('fällt auf statusText zurück, wenn der Body nicht lesbar ist', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 502,
      statusText: 'Bad Gateway',
      text: () => Promise.reject(new Error('Verbindung abgerissen')),
    } as Response)

    expect((await failingFetch()).message).toBe('Bad Gateway')
  })

  it('fällt bei leerem Body auf statusText zurück', async () => {
    mockErrorResponse(500, '', 'Internal Server Error')

    const error = await failingFetch()

    expect(error.message).toBe('Internal Server Error')
  })
})

describe('ApiError.detail – nur aus einem gelesenen Problem-Body', () => {
  it('übernimmt detail aus dem Problem-Body', async () => {
    mockErrorResponse(
      409,
      JSON.stringify({ title: 'Conflict', status: 409, detail: 'Spalte ist nicht leer' }),
    )

    const error = await failingFetch()

    expect(error.detail).toBe('Spalte ist nicht leer')
    expect(apiErrorMessage(error, 'Fallback')).toBe('Spalte ist nicht leer')
  })

  it('übergeht ein leeres detail und nimmt den title', async () => {
    mockErrorResponse(409, JSON.stringify({ title: 'Conflict', detail: '' }))

    expect((await failingFetch()).detail).toBe('Conflict')
  })

  it('verträgt fieldErrors: null und lässt sie undefiniert', async () => {
    mockErrorResponse(400, JSON.stringify({ detail: 'Ungültig', fieldErrors: null }))

    const error = await failingFetch()

    expect(error.detail).toBe('Ungültig')
    expect(error.fieldErrors).toBeUndefined()
  })

  it('nutzt ohne detail den title als detail', async () => {
    mockErrorResponse(409, JSON.stringify({ title: 'Conflict', status: 409 }))

    const error = await failingFetch()

    expect(error.detail).toBe('Conflict')
    expect(apiErrorMessage(error, 'Fallback')).toBe('Conflict')
  })

  it.each([
    ['Nicht-JSON-Body', 'Unauthorized-Seite'],
    ['leerer Body', ''],
    ['JSON ohne detail/title (Spring-Default)', JSON.stringify({ timestamp: '2026-07-11', status: 401, error: 'Unauthorized' })],
  ])('lässt detail bei %s undefiniert und meldet den Fallback', async (_fall, body) => {
    mockErrorResponse(401, body, 'Unauthorized')

    const error = await failingFetch()

    expect(error.detail).toBeUndefined()
    expect(apiErrorMessage(error, 'Fallback')).toBe('Fallback')
    expect(apiErrorMessage(error, 'Fallback')).not.toBe(error.message)
  })

  it('lässt detail bei einem Body nur mit fieldErrors undefiniert', async () => {
    mockErrorResponse(400, JSON.stringify({ fieldErrors: { title: 'darf nicht leer sein' } }))

    const error = await failingFetch()

    expect(error.detail).toBeUndefined()
    expect(error.fieldErrors).toEqual({ title: 'darf nicht leer sein' })
    expect(apiErrorMessage(error, 'Fallback')).toBe('Fallback')
  })
})

describe('401-Haken', () => {
  it('feuert bei einer 401-Antwort', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    mockErrorResponse(401, '', 'Unauthorized')

    await failingFetch()

    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('feuert nicht bei einer erfolgreichen Antwort (200)', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    mockOkResponse(JSON.stringify({ id: 1 }))

    await apiFetch('/api/test')

    expect(handler).not.toHaveBeenCalled()
  })

  it.each([
    [403, 'Forbidden'],
    [500, 'Internal Server Error'],
  ])('feuert nicht bei %i', async (status, statusText) => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    mockErrorResponse(status, '', statusText)

    await failingFetch()

    expect(handler).not.toHaveBeenCalled()
  })

  it('wirft den ApiError bei 401 unverändert weiter', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    mockErrorResponse(
      401,
      JSON.stringify({
        title: 'Unauthorized',
        status: 401,
        detail: 'Sitzung abgelaufen',
        fieldErrors: { email: 'unbekannt' },
      }),
    )

    const error = await failingFetch()

    expect(error.status).toBe(401)
    expect(error.message).toBe('Sitzung abgelaufen')
    expect(error.detail).toBe('Sitzung abgelaufen')
    expect(error.fieldErrors).toEqual({ email: 'unbekannt' })
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('feuert nach setUnauthorizedHandler(null) nicht mehr', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    setUnauthorizedHandler(null)
    mockErrorResponse(401, '', 'Unauthorized')

    await failingFetch()

    expect(handler).not.toHaveBeenCalled()
  })

  it('löst über notifyUnauthorized nur bei 401 aus', () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)

    notifyUnauthorized(401)
    expect(handler).toHaveBeenCalledTimes(1)

    notifyUnauthorized(403)
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('bleibt ohne registrierten Haken folgenlos', () => {
    expect(() => notifyUnauthorized(401)).not.toThrow()
  })
})

describe('apiErrorMessage', () => {
  it('meldet den Fallback bei einem leeren detail', () => {
    expect(apiErrorMessage(new ApiError(500, 'Roher Servertext', undefined, ''), 'Fallback')).toBe(
      'Fallback',
    )
  })

  it.each([
    ['undefined', undefined],
    ['null', null],
    ['String', 'Irgendein Text'],
  ])('meldet den Fallback bei %s als Fehler', (_fall, error) => {
    expect(apiErrorMessage(error, 'Fallback')).toBe('Fallback')
  })

  it('meldet ohne Fallback den deutschen Text aus dem Wörterbuch', () => {
    expect(apiErrorMessage(new Error('x'))).toBe('unbekannter Fehler')
  })

  it('meldet einen englischen Fallback aus dem Wörterbuch', () => {
    expect(apiErrorMessage(new Error('x'), en.FEHLER_UNBEKANNT)).toBe('unknown error')
  })

  it('zieht detail auch einem englischen Fallback vor', () => {
    expect(apiErrorMessage(new ApiError(409, 'x', undefined, 'Column not empty'), en.FEHLER_UNBEKANNT)).toBe(
      'Column not empty',
    )
  })
})
