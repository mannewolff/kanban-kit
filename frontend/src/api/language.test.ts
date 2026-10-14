import { afterEach, describe, expect, it, vi } from 'vitest'
import { languageApi } from './language'

function antwort(body: unknown) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    text: () => Promise.resolve(JSON.stringify(body)),
  } as Response)
}

afterEach(() => vi.restoreAllMocks())

describe('languageApi', () => {
  it('lesen ruft GET /api/language und liefert den Sprachcode', async () => {
    const spy = antwort({ language: 'en' })
    await expect(languageApi.lesen()).resolves.toBe('en')
    expect(spy.mock.calls[0][0]).toBe('/api/language')
    expect(spy.mock.calls[0][1]?.method).toBeUndefined()
  })

  it.each([
    ['einen unbekannten Code', { language: 'fr' }],
    ['ein Objekt ohne language', {}],
    ['kein Objekt', 'de'],
    ['null', null],
    ['eine leere Antwort', undefined],
  ])('lesen weist %s ab', async (_fall, body) => {
    antwort(body)
    await expect(languageApi.lesen()).rejects.toThrow('Unbekannte Sprache')
  })

  it('setzen sendet PUT /api/admin/language mit dem Code', async () => {
    const spy = antwort({ language: 'en' })
    await expect(languageApi.setzen('en')).resolves.toBe('en')
    expect(spy.mock.calls[0][0]).toBe('/api/admin/language')
    expect(spy.mock.calls[0][1]).toMatchObject({
      method: 'PUT',
      body: JSON.stringify({ language: 'en' }),
    })
  })

  it('setzen weist eine unbekannte Antwort ab', async () => {
    antwort({ language: 'xx' })
    await expect(languageApi.setzen('de')).rejects.toThrow('Unbekannte Sprache')
  })
})
