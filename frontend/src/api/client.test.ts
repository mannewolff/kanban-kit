import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  apiFetch,
  ApiError,
  apiErrorMessage,
  notifyUnauthorized,
  setUnauthorizedHandler,
} from './client'

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
})
