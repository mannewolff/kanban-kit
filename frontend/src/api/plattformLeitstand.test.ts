import { afterEach, describe, expect, it, vi } from 'vitest'
import { leserZone } from './nightRunUsage'
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

describe('plattformLeitstandApi (#1083, drei Listen seit #1098)', () => {
  const leer = '{"laufende":[],"durchgefuehrte":[],"stoerungen":[]}'

  it('leitstand ruft GET /api/admin/leitstand und liefert die drei geparsten Listen', async () => {
    const antwort = {
      laufende: [
        {
          nightRunId: 8,
          projectId: 9,
          projectName: 'Mein Projekt',
          startedAt: '2026-09-21T01:10:00Z',
          outcome: { abortReason: null, verdict: 'RUNNING', decisiveItem: null, noWorkReason: null },
        },
      ],
      durchgefuehrte: [
        {
          nightRunId: 5,
          projectId: 9,
          projectName: 'Mein Projekt',
          startedAt: '2026-09-19T21:10:00Z',
          outcome: { abortReason: null, verdict: 'FAILED', decisiveItem: null, noWorkReason: 'Ready war leer' },
        },
      ],
      stoerungen: [],
    }
    const f = spyFetch(JSON.stringify(antwort))

    expect(await plattformLeitstandApi.leitstand('Europe/Berlin')).toEqual(antwort)
    expect(lastCall(f).url).toBe('/api/admin/leitstand?zone=Europe%2FBerlin')
  })

  /**
   * Die Zone gehört an den Abruf, weil die Nachtgrenze 12:00 zonenlokal gezogen wird (Plan #1088
   * E6): Im Container läuft die JVM regelmäßig in UTC, und „die laufende Nacht" läge dann um
   * Stunden verschoben gegen die, die die Nachtlauf-Auswertung zieht.
   */
  it('sendet die Zone als Abfrageparameter', async () => {
    const f = spyFetch(leer)

    await plattformLeitstandApi.leitstand('America/New_York')

    expect(new URL(String(lastCall(f).url), 'https://x').searchParams.get('zone')).toBe(
      'America/New_York',
    )
  })

  /** Ohne Argument die Zone des Lesers — dieselbe Quelle wie in `api/nightRunUsage.ts`. */
  it('nimmt ohne Argument die Zone des Lesers', async () => {
    const f = spyFetch(leer)

    await plattformLeitstandApi.leitstand()

    expect(new URL(String(lastCall(f).url), 'https://x').searchParams.get('zone')).toBe(leserZone())
  })

  /**
   * Issue #1173: Die vierte Liste — die gemeldeten Pakete der laufenden Läufe (#1170) — geht
   * ungefiltert an die Seite. Sie steht neben `laufende` und nicht an deren Zeilen (Plan #1167 E1).
   */
  it('reicht die gemeldeten Pakete der laufenden Runs durch', async () => {
    const antwort = {
      laufende: [],
      durchgefuehrte: [],
      durchgefuehrteVoriger: [],
      stoerungen: [],
      gemeldetePakete: [
        {
          nightRunId: 8,
          pakete: [
            { cardNumber: 721, title: 'Erstes Paket', state: 'GREEN', errorClass: null, cardExists: true },
            {
              cardNumber: 722,
              title: 'Zweites Paket',
              state: 'RED',
              errorClass: 'CHECKS_RED',
              cardExists: false,
            },
          ],
        },
      ],
    }
    const f = spyFetch(JSON.stringify(antwort))

    expect(await plattformLeitstandApi.leitstand('Europe/Berlin')).toEqual(antwort)
    expect(lastCall(f).url).toBe('/api/admin/leitstand?zone=Europe%2FBerlin')
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
    const f = spyFetch(leer)

    await plattformLeitstandApi.leitstand('Europe/Berlin')
    await plattformLeitstandApi.quittieren(7)

    expect(f.mock.calls.map(([url]) => url)).toEqual([
      '/api/admin/leitstand?zone=Europe%2FBerlin',
      '/api/admin/disruptions/7',
    ])
  })
})
