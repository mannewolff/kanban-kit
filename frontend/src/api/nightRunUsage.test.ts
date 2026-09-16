import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './client'
import { leserZone, nightRunUsageApi } from './nightRunUsage'

/** Abruf der Verbrauchs-Auswertung (Issue #940) gegen einen `fetch`-Doppelgänger. */

function spyFetch(body = '{}', status = 200) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: status < 400,
    status,
    statusText: status < 400 ? 'OK' : 'Forbidden',
    text: () => Promise.resolve(body),
  } as Response)
}

function lastUrl(fetchSpy: ReturnType<typeof spyFetch>): URL {
  const [url] = fetchSpy.mock.calls[fetchSpy.mock.calls.length - 1]
  return new URL(String(url), 'http://localhost')
}

const nichts = {
  costUsd: null,
  inputTokens: null,
  outputTokens: null,
  cachedInputTokens: null,
  cachedInputSharePercent: null,
}

afterEach(() => vi.restoreAllMocks())

describe('nightRunUsageApi', () => {
  it('night ruft GET /api/projects/{id}/night-run-usage/night mit Datum und Zone', async () => {
    const f = spyFetch(
      JSON.stringify({
        night: '2026-09-15',
        runCount: 2,
        durationMs: 4000,
        cardCount: 1,
        usage: { total: nichts, cardShare: nichts, remainder: nichts },
        aborted: false,
        cards: [],
      }),
    )

    const nacht = await nightRunUsageApi.night(4, '2026-09-15', 'Europe/Berlin')

    const url = lastUrl(f)
    expect(url.pathname).toBe('/api/projects/4/night-run-usage/night')
    expect(url.searchParams.get('date')).toBe('2026-09-15')
    expect(url.searchParams.get('zone')).toBe('Europe/Berlin')
    expect(nacht.runCount).toBe(2)
  })

  it('period ruft GET /api/projects/{id}/night-run-usage mit Art, Rueckschritt und Zone', async () => {
    const f = spyFetch('{}')

    await nightRunUsageApi.period(4, 'WEEK', 2, 'America/New_York')

    const url = lastUrl(f)
    expect(url.pathname).toBe('/api/projects/4/night-run-usage')
    expect(url.searchParams.get('type')).toBe('WEEK')
    expect(url.searchParams.get('stepsBack')).toBe('2')
    expect(url.searchParams.get('zone')).toBe('America/New_York')
  })

  it('schickt ohne ausdrueckliche Zone die Zone des Lesers mit', async () => {
    const f = spyFetch('{}')

    await nightRunUsageApi.period(4, 'DAY', 0)
    await nightRunUsageApi.night(4, '2026-09-15')

    expect(lastUrl(f).searchParams.get('zone')).toBe(
      Intl.DateTimeFormat().resolvedOptions().timeZone,
    )
    expect(leserZone()).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone)
  })

  it('kodiert die Zone, statt sie roh in die Adresse zu schreiben', async () => {
    const f = spyFetch('{}')

    await nightRunUsageApi.night(4, '2026-09-15', 'Etc/GMT+2')

    const [roh] = f.mock.calls[0]
    expect(String(roh)).toContain('zone=Etc%2FGMT%2B2')
  })

  /** „Nicht gemessen" bleibt `null` — nie 0 (Plan E5). */
  it('laesst ein null-Feld der Antwort null', async () => {
    spyFetch(
      JSON.stringify({
        current: {
          type: 'MONTH',
          firstDay: '2026-08-01',
          lastDay: '2026-08-31',
          from: '2026-08-01T10:00:00Z',
          to: '2026-09-01T10:00:00Z',
          coverage: 'COMPLETE',
          noRuns: false,
          runCount: 1,
          durationMs: 1,
          cardCount: 1,
          usage: { total: { ...nichts, costUsd: 2.5 }, cardShare: nichts, remainder: nichts },
        },
      }),
    )

    const zeitraum = await nightRunUsageApi.period(4, 'MONTH', 0, 'Europe/Berlin')

    expect(zeitraum.current.usage.total.costUsd).toBe(2.5)
    expect(zeitraum.current.usage.total.inputTokens).toBeNull()
    expect(zeitraum.current.usage.cardShare.costUsd).toBeNull()
  })

  it('reicht einen Fehlerstatus als ApiError weiter', async () => {
    spyFetch('{"detail":"verboten"}', 403)

    await expect(nightRunUsageApi.period(4, 'DAY', 0, 'UTC')).rejects.toBeInstanceOf(ApiError)
    await expect(nightRunUsageApi.night(4, '2026-09-15', 'UTC')).rejects.toMatchObject({
      status: 403,
    })
  })
})
