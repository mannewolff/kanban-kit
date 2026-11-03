import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './client'
import { vorhabenKostenApi } from './vorhabenKosten'

/** Abruf der Kostenzeile je Vorhaben (Issue #2006) gegen einen `fetch`-Doppelgänger. */

function spyFetch(body: string, status = 200) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: status < 400,
    status,
    statusText: status < 400 ? 'OK' : 'Fehler',
    text: () => Promise.resolve(body),
  } as Response)
}

afterEach(() => vi.restoreAllMocks())

describe('vorhabenKostenApi', () => {
  it('jeBoard ruft GET /api/boards/{id}/night-run-epic-costs und liefert die Zeilen', async () => {
    const f = spyFetch('[{"epicId":4,"number":40,"archiviert":false,"laufend":null,"abschluss":null}]')

    const zeilen = await vorhabenKostenApi.jeBoard(12)

    expect(String(f.mock.calls[0][0])).toBe('/api/boards/12/night-run-epic-costs')
    expect(zeilen).toEqual([{ epicId: 4, number: 40, archiviert: false, laufend: null, abschluss: null }])
  })

  it('jeVorhaben ruft GET /api/cards/{id}/night-run-epic-costs und liefert die Zeile', async () => {
    const f = spyFetch('{"epicId":812,"number":81,"archiviert":true,"laufend":null,"abschluss":null}')

    const zeile = await vorhabenKostenApi.jeVorhaben(812)

    expect(String(f.mock.calls[0][0])).toBe('/api/cards/812/night-run-epic-costs')
    expect(zeile).toEqual({ epicId: 812, number: 81, archiviert: true, laufend: null, abschluss: null })
  })

  it.each([403, 404])('ergibt bei %i null', async (status) => {
    spyFetch('{}', status)

    await expect(vorhabenKostenApi.jeBoard(12)).resolves.toBeNull()
    await expect(vorhabenKostenApi.jeVorhaben(812)).resolves.toBeNull()
  })

  it('lässt jeden anderen Fehler durch', async () => {
    spyFetch('{}', 500)

    await expect(vorhabenKostenApi.jeBoard(12)).rejects.toBeInstanceOf(ApiError)
    await expect(vorhabenKostenApi.jeVorhaben(812)).rejects.toMatchObject({ status: 500 })
  })
})
