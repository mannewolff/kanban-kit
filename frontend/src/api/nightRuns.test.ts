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
})
