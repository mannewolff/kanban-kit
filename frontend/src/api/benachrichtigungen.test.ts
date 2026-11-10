import { afterEach, describe, expect, it, vi } from 'vitest'
import { benachrichtigungenApi } from './benachrichtigungen'

function spyFetch(body = '') {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    text: () => Promise.resolve(body),
  } as Response)
}

describe('benachrichtigungenApi', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('liest die eigenen Einstellungen', async () => {
    const fetchSpy = spyFetch('{"projekte":[],"ruhezeit":null}')
    await expect(benachrichtigungenApi.einstellungen()).resolves.toEqual({ projekte: [], ruhezeit: null })
    expect(fetchSpy.mock.calls[0][0]).toBe('/api/me/benachrichtigungen')
    expect(fetchSpy.mock.calls[0][1]?.method).toBeUndefined()
  })

  it.each([true, false])('schaltet die eigene Mail je Projekt auf %s', async (an) => {
    const fetchSpy = spyFetch()
    await benachrichtigungenApi.schaltePerson(7, an)
    expect(fetchSpy.mock.calls[0][0]).toBe('/api/me/benachrichtigungen/projekte/7')
    expect(fetchSpy.mock.calls[0][1]?.method).toBe('PUT')
    expect(fetchSpy.mock.calls[0][1]?.body).toBe(JSON.stringify({ an }))
  })

  it('setzt die Ruhezeit', async () => {
    const fetchSpy = spyFetch()
    await benachrichtigungenApi.setzeRuhezeit({ beginn: '22:00', ende: '07:00', zeitzone: 'Europe/Berlin' })
    expect(fetchSpy.mock.calls[0][0]).toBe('/api/me/benachrichtigungen/ruhezeit')
    expect(fetchSpy.mock.calls[0][1]?.method).toBe('PUT')
    expect(fetchSpy.mock.calls[0][1]?.body).toBe(
      JSON.stringify({ beginn: '22:00', ende: '07:00', zeitzone: 'Europe/Berlin' }),
    )
  })

  it('löscht die Ruhezeit', async () => {
    const fetchSpy = spyFetch()
    await benachrichtigungenApi.loescheRuhezeit()
    expect(fetchSpy.mock.calls[0][0]).toBe('/api/me/benachrichtigungen/ruhezeit')
    expect(fetchSpy.mock.calls[0][1]?.method).toBe('DELETE')
    expect(fetchSpy.mock.calls[0][1]?.body).toBeUndefined()
  })

  it('liest den Schalter eines Projekts', async () => {
    const fetchSpy = spyFetch('{"an":true}')
    await expect(benachrichtigungenApi.projektschalter(7)).resolves.toEqual({ an: true })
    expect(fetchSpy.mock.calls[0][0]).toBe('/api/projects/7/benachrichtigung')
    expect(fetchSpy.mock.calls[0][1]?.method).toBeUndefined()
  })

  it.each([true, false])('schaltet ein Projekt auf %s und liefert den neuen Zustand', async (an) => {
    const fetchSpy = spyFetch(JSON.stringify({ an }))
    await expect(benachrichtigungenApi.schalteProjekt(7, an)).resolves.toEqual({ an })
    expect(fetchSpy.mock.calls[0][0]).toBe('/api/projects/7/benachrichtigung')
    expect(fetchSpy.mock.calls[0][1]?.method).toBe('PUT')
    expect(fetchSpy.mock.calls[0][1]?.body).toBe(JSON.stringify({ an }))
  })
})
