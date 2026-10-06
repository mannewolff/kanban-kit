import { afterEach, describe, expect, it, vi } from 'vitest'
import { standardLabelsApi } from './standardLabels'

afterEach(() => vi.restoreAllMocks())

function antwort(body: unknown) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    text: () => Promise.resolve(JSON.stringify(body)),
  } as Response)
}

describe('standardLabelsApi', () => {
  it('satz ruft GET /api/admin/standard-labels', async () => {
    const satz = [{ name: 'kit:night', gruppe: 'Kit', farbe: '#6a1b9a' }]
    const spy = antwort(satz)

    expect(await standardLabelsApi.satz()).toEqual(satz)
    expect(spy.mock.calls[0][0]).toBe('/api/admin/standard-labels')
  })

  it('anlegen sendet POST an /api/admin/standard-labels/anlegen', async () => {
    const spy = antwort({ boards: 3, angelegt: 40, uebersprungen: 20 })

    expect(await standardLabelsApi.anlegen()).toEqual({ boards: 3, angelegt: 40, uebersprungen: 20 })
    expect(spy.mock.calls[0][0]).toBe('/api/admin/standard-labels/anlegen')
    expect(spy.mock.calls[0][1]).toMatchObject({ method: 'POST' })
  })
})
