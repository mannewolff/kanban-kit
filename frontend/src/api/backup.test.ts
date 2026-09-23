import { afterEach, describe, expect, it, vi } from 'vitest'
import { backupApi, type BackupStatus } from './backup'
import { ApiError, apiErrorMessage } from './client'

afterEach(() => vi.restoreAllMocks())

const stand: BackupStatus = {
  verdict: 'OK',
  enabled: true,
  alertMailEnabled: false,
  targetLabel: 'Nextcloud',
  kinds: [
    {
      kind: 'BASIS',
      lastStartedAt: '2026-09-23T03:00:00Z',
      lastFinishedAt: '2026-09-23T03:00:30Z',
      lastOutcome: 'ERFOLG',
      detail: null,
      bytes: 4096,
      lastSuccessAt: '2026-09-23T03:00:00Z',
      ageSeconds: 7200,
      warnAfterSeconds: 172_800,
      stale: false,
    },
  ],
}

describe('backupApi', () => {
  it('getStatus ruft GET /api/admin/backup/status und liefert den Stand', async () => {
    const f = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      statusText: 'OK',
      text: () => Promise.resolve(JSON.stringify(stand)),
    } as Response)

    const ergebnis = await backupApi.getStatus()

    const [url, init] = f.mock.calls[f.mock.calls.length - 1]
    expect(url).toBe('/api/admin/backup/status')
    expect(init?.method).toBeUndefined()
    expect(ergebnis).toEqual(stand)
  })

  it('wirft bei 403 einen ApiError mit Status und Server-Meldung', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 403,
      statusText: 'Forbidden',
      text: () => Promise.resolve(JSON.stringify({ detail: 'Kein Admin-Zugriff.' })),
    } as Response)

    const fehler = await backupApi.getStatus().catch((e: unknown) => e)

    expect(fehler).toBeInstanceOf(ApiError)
    expect((fehler as ApiError).status).toBe(403)
    expect(apiErrorMessage(fehler, 'Stand der Sicherung nicht abrufbar.')).toBe('Kein Admin-Zugriff.')
  })
})
