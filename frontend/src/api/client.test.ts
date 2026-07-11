import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, ApiError } from './client'

function mockErrorResponse(status: number, body: string, statusText = 'Error') {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: false,
    status,
    statusText,
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

  it('fällt bei leerem Body auf statusText zurück', async () => {
    mockErrorResponse(500, '', 'Internal Server Error')

    const error = await failingFetch()

    expect(error.message).toBe('Internal Server Error')
  })
})
