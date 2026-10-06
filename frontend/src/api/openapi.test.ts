import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './client'
import { OPENAPI_JSON_PFAD, OPENAPI_YAML_PFAD, openapiApi } from './openapi'

afterEach(() => vi.restoreAllMocks())

describe('openapiApi', () => {
  it('nennt die Pfade der JSON- und der YAML-Beschreibung', () => {
    expect(OPENAPI_JSON_PFAD).toBe('/api/openapi')
    expect(OPENAPI_YAML_PFAD).toBe('/api/openapi.yaml')
  })

  it('lade ruft GET /api/openapi und liefert die Spezifikation', async () => {
    const spec = { openapi: '3.1.0', info: { title: 'kanban-kit', version: '2.20.0' }, paths: {} }
    const f = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      statusText: 'OK',
      text: () => Promise.resolve(JSON.stringify(spec)),
    } as Response)

    const ergebnis = await openapiApi.lade()

    const [url, init] = f.mock.calls[f.mock.calls.length - 1]
    expect(url).toBe('/api/openapi')
    expect(init?.method).toBeUndefined()
    expect(ergebnis).toEqual(spec)
  })

  it('wirft bei einer Fehlerantwort einen ApiError mit Status', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 500,
      statusText: 'Internal Server Error',
      text: () => Promise.resolve(''),
    } as Response)

    const fehler = await openapiApi.lade().catch((e: unknown) => e)

    expect(fehler).toBeInstanceOf(ApiError)
    expect((fehler as ApiError).status).toBe(500)
  })
})
