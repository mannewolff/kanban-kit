import { afterEach, describe, expect, it, vi } from 'vitest'
import { nightRunsApi, type NightRunSubmission } from './nightRuns'

function spyFetch(body = '{}') {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    text: () => Promise.resolve(body),
  } as Response)
}

function lastCall(fetchSpy: ReturnType<typeof spyFetch>) {
  const [url, init] = fetchSpy.mock.calls[fetchSpy.mock.calls.length - 1]
  return { url, method: init?.method, body: init?.body }
}

const lauf: NightRunSubmission = {
  startedAt: '2026-08-31T22:00:00Z',
  mode: 'IMPLEMENTATION',
  durationMs: 1234,
  processedCount: 1,
  skippedCount: 0,
  unparsedCount: 0,
  items: [
    {
      cardNumber: 721,
      title: 'Persistenz',
      state: 'RED',
      errorClass: 'CHECKS_RED',
      excerpt: 'mvn verify rot',
    },
  ],
}

afterEach(() => vi.restoreAllMocks())

describe('nightRunsApi', () => {
  it('submit ruft POST /api/projects/{id}/night-runs mit den Laeufen', async () => {
    const f = spyFetch(JSON.stringify([{ startedAt: '2026-08-31T22:00:00Z', created: true }]))
    const ergebnis = await nightRunsApi.submit(4, [lauf])
    const c = lastCall(f)
    expect(c.url).toBe('/api/projects/4/night-runs')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ runs: [lauf] })
    expect(ergebnis).toEqual([{ startedAt: '2026-08-31T22:00:00Z', created: true }])
  })

  it('list ruft GET /api/projects/{id}/night-runs und liefert die geparste Antwort', async () => {
    const f = spyFetch(
      JSON.stringify([
        {
          id: 11,
          startedAt: '2026-08-31T22:00:00Z',
          mode: 'IMPLEMENTATION',
          durationMs: 1234,
          processedCount: 1,
          skippedCount: 0,
          unparsedCount: 0,
          createdAt: '2026-09-01T06:00:00Z',
          items: [{ id: 21, cardNumber: 721, title: 'Persistenz', state: 'GREEN' }],
        },
      ]),
    )
    const laeufe = await nightRunsApi.list(4)
    const c = lastCall(f)
    expect(c.url).toBe('/api/projects/4/night-runs')
    expect(c.method).toBeUndefined()
    expect(laeufe).toHaveLength(1)
    expect(laeufe[0].items[0].cardNumber).toBe(721)
  })

  it('errorClassCounts ruft GET /api/projects/{id}/night-runs/error-class-counts', async () => {
    const f = spyFetch(JSON.stringify({ CHECKS_RED: 2 }))
    const zahlen = await nightRunsApi.errorClassCounts(4)
    const c = lastCall(f)
    expect(c.url).toBe('/api/projects/4/night-runs/error-class-counts')
    expect(c.method).toBeUndefined()
    expect(zahlen.CHECKS_RED).toBe(2)
  })

  it('anlaeufeDerKarte ruft GET /api/projects/{id}/night-runs/items?cardNumber={n}', async () => {
    const f = spyFetch(
      JSON.stringify([
        {
          startedAt: '2026-09-02T22:00:00Z',
          mode: 'CHAIN',
          kind: 'NIGHT',
          state: 'GREEN',
          errorClass: null,
          durationMs: 1000,
          commitHash: null,
          usage: null,
        },
      ]),
    )
    const anlaeufe = await nightRunsApi.anlaeufeDerKarte(4, 968)
    const c = lastCall(f)
    expect(c.url).toBe('/api/projects/4/night-runs/items?cardNumber=968')
    expect(c.method).toBeUndefined()
    expect(anlaeufe[0].usage).toBeNull()
    expect(anlaeufe[0].kind).toBe('NIGHT')
  })

  it('progress ruft GET /api/projects/{id}/night-runs/{runId}/progress', async () => {
    const f = spyFetch(
      JSON.stringify({
        zuordnung: 'OK',
        ketten: [],
        pakete: [{ karte: { number: 1376, title: 'API', boardId: 3 }, zustand: 'IN_UMSETZUNG' }],
        unbekannt: [],
        unbekanntOhneAusweis: false,
        offeneFragen: [],
      }),
    )
    const fortschritt = await nightRunsApi.progress(4, 77)
    const c = lastCall(f)
    expect(c.url).toBe('/api/projects/4/night-runs/77/progress')
    expect(c.method).toBeUndefined()
    expect(fortschritt.pakete[0].zustand).toBe('IN_UMSETZUNG')
  })

  it('kettenstand ruft GET /api/cards/{cardId}/night-chain und liefert den Stand (Issue #1453)', async () => {
    const f = spyFetch(
      JSON.stringify({
        ziel: 'UMSETZUNG',
        pruefer: 2,
        zielErreicht: false,
        grenze: null,
        stationen: [{ station: 'REVIEW', zustand: 'LAEUFT', text: 'läuft (2 Prüfer)', grund: null }],
        uebernommen: true,
        planReviewVorhanden: false,
        lauf: '2026-10-05T01:00:00Z',
      }),
    )
    const stand = await nightRunsApi.kettenstand(812)
    const c = lastCall(f)
    expect(c.url).toBe('/api/cards/812/night-chain')
    expect(c.method).toBeUndefined()
    expect(stand.stationen[0]).toEqual({
      station: 'REVIEW',
      zustand: 'LAEUFT',
      text: 'läuft (2 Prüfer)',
      grund: null,
    })
    expect(stand.uebernommen).toBe(true)
  })

  it('heuteNacht ruft GET /api/projects/{id}/night-runs/tonight und liefert die Karten (Issue #1455)', async () => {
    const karte = {
      number: 1420,
      title: '[Fachlich] Export als CSV',
      boardName: 'Entwicklung',
      start: 'FACHPLAN',
      ziel: 'UMSETZUNG',
      pruefer: 2,
    }
    const f = spyFetch(JSON.stringify([karte]))
    const karten = await nightRunsApi.heuteNacht(4)
    const c = lastCall(f)
    expect(c.url).toBe('/api/projects/4/night-runs/tonight')
    expect(c.method).toBeUndefined()
    expect(karten).toEqual([karte])
  })

  it('progress reicht einen Fehler des Servers durch', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 404,
      statusText: 'Not Found',
      text: () => Promise.resolve(JSON.stringify({ detail: 'Lauf nicht gefunden' })),
    } as Response)
    await expect(nightRunsApi.progress(4, 77)).rejects.toMatchObject({
      status: 404,
      detail: 'Lauf nicht gefunden',
    })
  })
})
