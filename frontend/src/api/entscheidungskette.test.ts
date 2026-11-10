import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './client'
import { entscheidungsketteApi } from './entscheidungskette'

/** Abruf der Entscheidungskette (Issue #2118) gegen einen `fetch`-Doppelgänger. */

function spyFetch(body: string, status = 200) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: status < 400,
    status,
    statusText: status < 400 ? 'OK' : 'Fehler',
    text: () => Promise.resolve(body),
  } as Response)
}

afterEach(() => vi.restoreAllMocks())

describe('entscheidungsketteApi', () => {
  it('kette ruft GET /api/cards/{id}/entscheidungskette und liefert die Kette', async () => {
    const f = spyFetch('{"sichtbar":false,"art":null,"abschnitte":[],"nachfahren":[]}')

    const kette = await entscheidungsketteApi.kette(1962)

    expect(String(f.mock.calls[0][0])).toBe('/api/cards/1962/entscheidungskette')
    expect(kette).toEqual({ sichtbar: false, art: null, abschnitte: [], nachfahren: [] })
  })

  it('reicht einen Fehler als ApiError weiter', async () => {
    spyFetch('{}', 404)

    await expect(entscheidungsketteApi.kette(7)).rejects.toBeInstanceOf(ApiError)
  })
})
