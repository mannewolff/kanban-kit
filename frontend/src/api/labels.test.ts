import { afterEach, describe, expect, it, vi } from 'vitest'
import { labelsApi } from './labels'

function spyFetch() {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    text: () => Promise.resolve('{}'),
  } as Response)
}

function lastCall(f: ReturnType<typeof spyFetch>) {
  const [url, init] = f.mock.calls[f.mock.calls.length - 1]
  return { url, method: init?.method, body: init?.body }
}

afterEach(() => vi.restoreAllMocks())

describe('labelsApi', () => {
  it('list ruft GET /api/boards/{id}/labels', async () => {
    const f = spyFetch()
    await labelsApi.list(3)
    expect(lastCall(f).url).toBe('/api/boards/3/labels')
  })

  it('create ruft POST mit Name und Farbe', async () => {
    const f = spyFetch()
    await labelsApi.create(3, 'Bug', '#f00')
    const c = lastCall(f)
    expect(c.url).toBe('/api/boards/3/labels')
    expect(c.method).toBe('POST')
    expect(JSON.parse(String(c.body))).toEqual({ name: 'Bug', color: '#f00' })
  })

  it('update ruft PATCH /api/labels/{id}', async () => {
    const f = spyFetch()
    await labelsApi.update(7, 'Defekt', '#00f')
    const c = lastCall(f)
    expect(c.url).toBe('/api/labels/7')
    expect(c.method).toBe('PATCH')
    expect(JSON.parse(String(c.body))).toEqual({ name: 'Defekt', color: '#00f' })
  })

  it('update sendet countOnEpicTile nur, wenn es übergeben wurde', async () => {
    const f = spyFetch()
    await labelsApi.update(7, 'Bug', '#f00', true)
    expect(JSON.parse(String(lastCall(f).body))).toEqual({
      name: 'Bug',
      color: '#f00',
      countOnEpicTile: true,
    })
  })

  // Dreiwertig: explizites false muss im Body landen. Würde es wie "fehlend" behandelt, ließe
  // sich ein einmal gesetztes Label nie wieder abschalten.
  it('update sendet ein ausdrückliches false mit', async () => {
    const f = spyFetch()
    await labelsApi.update(7, 'Bug', '#f00', false)
    expect(JSON.parse(String(lastCall(f).body))).toEqual({
      name: 'Bug',
      color: '#f00',
      countOnEpicTile: false,
    })
  })

  it('remove ruft DELETE /api/labels/{id}', async () => {
    const f = spyFetch()
    await labelsApi.remove(7)
    const c = lastCall(f)
    expect(c.url).toBe('/api/labels/7')
    expect(c.method).toBe('DELETE')
  })
})
