import { describe, expect, it } from 'vitest'
import { ABGEBROCHEN, AUSPROBIER_HEADER, HINWEIS_AENDERND, istAendernd, markiere } from './apiAusprobieren'

describe('istAendernd', () => {
  it.each([
    ['GET', false],
    ['HEAD', false],
    ['get', false],
    ['head', false],
    ['Get', false],
    ['POST', true],
    ['PUT', true],
    ['PATCH', true],
    ['DELETE', true],
    ['post', true],
    ['put', true],
    ['patch', true],
    ['delete', true],
  ])('%s → %s', (methode, erwartet) => {
    expect(istAendernd(methode)).toBe(erwartet)
  })
})

describe('markiere', () => {
  it('setzt das Ausprobier-Kennzeichen auf 1', () => {
    expect(AUSPROBIER_HEADER).toBe('X-Api-Ausprobieren')
    const markiert = markiere({ url: '/api/projects', method: 'GET', headers: {} })
    expect(markiert.headers[AUSPROBIER_HEADER]).toBe('1')
  })

  it('lässt bestehende Header und übrige Felder unverändert, auch X-Kanban-Token', () => {
    const anfrage = {
      url: '/api/kanban/runs',
      method: 'POST',
      headers: { 'X-Kanban-Token': 'geheim', 'Content-Type': 'application/json' },
    }
    const markiert = markiere(anfrage)
    expect(markiert).toEqual({
      url: '/api/kanban/runs',
      method: 'POST',
      headers: {
        'X-Kanban-Token': 'geheim',
        'Content-Type': 'application/json',
        [AUSPROBIER_HEADER]: '1',
      },
    })
  })

  it('kommt ohne vorhandene Header aus', () => {
    expect(markiere({ url: '/api/x', headers: undefined }).headers).toEqual({ [AUSPROBIER_HEADER]: '1' })
  })
})

describe('Texte', () => {
  it('nennt den Abbruch wörtlich', () => {
    expect(ABGEBROCHEN).toBe('Abgebrochen, nichts gesendet')
  })

  it('warnt, dass echte Daten dieser Umgebung verändert werden', () => {
    expect(HINWEIS_AENDERND).toBe('Dieser Aufruf verändert echte Daten dieser Umgebung.')
  })
})
