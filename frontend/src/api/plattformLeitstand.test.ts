import { afterEach, describe, expect, it, vi } from 'vitest'
import { plattformLeitstandApi } from './plattformLeitstand'

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
  return { url, method: init?.method }
}

afterEach(() => vi.restoreAllMocks())

describe('plattformLeitstandApi (#1083)', () => {
  it('liste ruft GET /api/admin/disruptions und liefert die geparste Antwort', async () => {
    const antwort = [
      {
        nightRunId: 5,
        projectId: 9,
        projectName: 'Mein Projekt',
        startedAt: '2026-09-19T21:10:00Z',
        outcome: { verdict: 'FAILED', decisiveItem: null, noWorkReason: 'Ready war leer' },
      },
    ]
    const f = spyFetch(JSON.stringify(antwort))

    expect(await plattformLeitstandApi.liste()).toEqual(antwort)
    expect(lastCall(f).url).toBe('/api/admin/disruptions')
  })

  it('quittieren ruft DELETE /api/admin/disruptions/{laufId}', async () => {
    const f = spyFetch()

    await plattformLeitstandApi.quittieren(5)

    expect(lastCall(f)).toEqual({ url: '/api/admin/disruptions/5', method: 'DELETE' })
  })

  /**
   * Der Pfadstamm ist eine Sicherheitsentscheidung des Servers (Plan #1072 E24): Nur unter
   * `/api/admin` verlangt die Filterkette eine Sitzung und lässt kein Token zu. Ein Umbenennen auf
   * `/api/platform` fiele hier nicht auf, deshalb steht der Pfad ausdrücklich im Test.
   */
  it('spricht beide Wege unter /api/admin an, nicht unter /api/platform', async () => {
    const f = spyFetch('[]')

    await plattformLeitstandApi.liste()
    await plattformLeitstandApi.quittieren(7)

    expect(f.mock.calls.map(([url]) => url)).toEqual([
      '/api/admin/disruptions',
      '/api/admin/disruptions/7',
    ])
  })
})
