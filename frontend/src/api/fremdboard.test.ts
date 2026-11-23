import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiErrorMessage } from './client'
import {
  fremdBoardApi,
  STANDARD_STATUSLABELS,
  type FremdBoardAnsicht,
  type FremdBoardEingabe,
  type FremdBoardStand,
} from './fremdboard'

afterEach(() => vi.restoreAllMocks())

const ansicht: FremdBoardAnsicht = {
  angebunden: true,
  schluesselGesetzt: true,
  art: 'GITLAB',
  basisUrl: 'https://gitlab.example.org',
  projektPfad: 'gruppe/projekt',
  kitKonto: 'kit-bot',
  statuslabels: { ...STANDARD_STATUSLABELS },
  angebundenAm: '2026-11-15T10:00:00Z',
  letzterAbgleichAm: null,
  letzterFehler: null,
  zugangLesbar: true,
}

function antwort(status: number, inhalt: unknown) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: status < 400,
    status,
    statusText: '',
    text: () => Promise.resolve(JSON.stringify(inhalt)),
  } as Response)
}

describe('STANDARD_STATUSLABELS', () => {
  it('trägt die Namen der Spalten des Kits', () => {
    expect(STANDARD_STATUSLABELS).toEqual({
      backlog: 'Backlog',
      ready: 'Ready',
      inProgress: 'In progress',
      inReview: 'In review',
    })
  })
})

describe('fremdBoardApi', () => {
  it('lies ruft GET /api/projects/{id}/fremdboard und liefert die Verbindung', async () => {
    const f = antwort(200, ansicht)

    const ergebnis = await fremdBoardApi.lies(4)

    const [url, init] = f.mock.calls[f.mock.calls.length - 1]
    expect(url).toBe('/api/projects/4/fremdboard')
    expect(init?.method).toBeUndefined()
    expect(ergebnis).toEqual(ansicht)
  })

  it('speichere schickt PUT mit der Eingabe als JSON', async () => {
    const f = antwort(200, ansicht)
    const eingabe: FremdBoardEingabe = {
      art: 'GITLAB',
      basisUrl: 'https://gitlab.example.org',
      projektPfad: 'gruppe/projekt',
      zugang: 'glpat-geheim',
      kitKonto: null,
      statuslabels: { ...STANDARD_STATUSLABELS },
    }

    const ergebnis = await fremdBoardApi.speichere(4, eingabe)

    const [url, init] = f.mock.calls[f.mock.calls.length - 1]
    expect(url).toBe('/api/projects/4/fremdboard')
    expect(init?.method).toBe('PUT')
    expect(JSON.parse(init?.body as string)).toEqual(eingabe)
    expect(ergebnis).toEqual(ansicht)
  })

  it('stand ruft GET /api/projects/{id}/fremdboard/stand und reicht die Antwort durch', async () => {
    const stand: FremdBoardStand = {
      imFremdsystem: true,
      art: 'GITLAB',
      standAm: '2026-11-22T08:30:00Z',
      abgleichGescheitert: false,
    }
    const f = antwort(200, stand)

    const ergebnis = await fremdBoardApi.stand(7)

    const [url, init] = f.mock.calls[f.mock.calls.length - 1]
    expect(url).toBe('/api/projects/7/fremdboard/stand')
    expect(init?.method).toBeUndefined()
    expect(ergebnis).toEqual(stand)
  })

  it('wirft bei 409 einen ApiError mit dem Grund des Servers', async () => {
    antwort(409, { detail: 'Das Projekt hat schon Karten in kanban-kit.' })

    const fehler = await fremdBoardApi
      .speichere(4, {
        art: 'GITLAB',
        basisUrl: 'https://gitlab.com',
        projektPfad: 'g/p',
        zugang: 'x',
        kitKonto: null,
        statuslabels: { ...STANDARD_STATUSLABELS },
      })
      .catch((e: unknown) => e)

    expect(fehler).toBeInstanceOf(ApiError)
    expect((fehler as ApiError).status).toBe(409)
    expect(apiErrorMessage(fehler, 'x')).toBe('Das Projekt hat schon Karten in kanban-kit.')
  })
})
