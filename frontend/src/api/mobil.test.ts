import { afterEach, describe, expect, it, vi } from 'vitest'
import { mobilApi } from './mobil'

function spyFetch(body = '[]') {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    text: () => Promise.resolve(body),
  } as Response)
}

describe('mobilApi', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('liest die Läufe in der übergebenen Zone', async () => {
    const fetchSpy = spyFetch('[{"laufId":4}]')
    await expect(mobilApi.laeufe('Europe/Berlin')).resolves.toEqual([{ laufId: 4 }])
    expect(fetchSpy.mock.calls[0][0]).toBe('/api/mobil/laeufe?zone=Europe%2FBerlin')
  })

  it('nimmt ohne Angabe die Zone des Lesers', async () => {
    const fetchSpy = spyFetch()
    await mobilApi.laeufe()
    const zone = Intl.DateTimeFormat().resolvedOptions().timeZone
    expect(fetchSpy.mock.calls[0][0]).toBe(`/api/mobil/laeufe?${new URLSearchParams({ zone })}`)
  })

  it('liest einen Lauf', async () => {
    const fetchSpy = spyFetch('{"lauf":{"laufId":4711},"meldung":null}')
    await expect(mobilApi.lauf(4711)).resolves.toEqual({ lauf: { laufId: 4711 }, meldung: null })
    expect(fetchSpy.mock.calls[0][0]).toBe('/api/mobil/laeufe/4711')
  })
})
