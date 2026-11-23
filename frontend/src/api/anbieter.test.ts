import { afterEach, describe, expect, it, vi } from 'vitest'
import { anbieterApi } from './anbieter'

afterEach(() => vi.restoreAllMocks())

function antwort(daten: unknown) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    text: () => Promise.resolve(JSON.stringify(daten)),
  } as Response)
}

describe('anbieterApi', () => {
  it('wartetAufDich ruft GET /api/projects/{id}/wartet-auf-dich und liefert die Karten', async () => {
    const f = antwort([{ cardId: 812, boardId: 3, number: 1404, title: 'Export', grund: 'RUECKFRAGE' }])
    const result = await anbieterApi.wartetAufDich(3)
    expect(f.mock.calls[0][0]).toBe('/api/projects/3/wartet-auf-dich')
    expect(result[0].grund).toBe('RUECKFRAGE')
  })

  it('lage ruft GET /api/projects/{id}/anbieter/lage und liefert die Lage', async () => {
    const f = antwort({ nichtErreichbar: true, boards: [] })
    const result = await anbieterApi.lage(7)
    expect(f.mock.calls[0][0]).toBe('/api/projects/7/anbieter/lage')
    expect(result.nichtErreichbar).toBe(true)
  })
})
