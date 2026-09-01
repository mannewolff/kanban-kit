import { describe, expect, it } from 'vitest'
import { epicToCard } from './epicToCard'
import type { Epic } from '../api/epics'

const vorhaben: Epic = {
  id: 700,
  number: 71,
  title: 'Vorhaben Kuerzel',
  description: 'Beschreibung des Vorhabens',
  shortcode: 'VK',
  done: 2,
  total: 5,
  memberNumbers: [1, 2, 3, 4, 5],
  rootNumbers: [1],
  requirementCardNumber: 9,
}

describe('epicToCard', () => {
  it('uebernimmt Titel, Nummer und ID des Vorhabens', () => {
    const karte = epicToCard(vorhaben, 12)
    expect(karte.title).toBe('Vorhaben Kuerzel')
    expect(karte.number).toBe(71)
    expect(karte.id).toBe(700)
  })

  it('traegt die uebergebene boardId', () => {
    expect(epicToCard(vorhaben, 12).boardId).toBe(12)
    // Nicht aus dem Vorhaben abgeleitet: dasselbe Vorhaben, ein anderes Board.
    expect(epicToCard(vorhaben, 34).boardId).toBe(34)
  })

  it('laesst die Zustaendigen leer', () => {
    expect(epicToCard(vorhaben, 12).assignees).toEqual([])
  })

  it('setzt die Herkunft auf null, weil Vorhaben keine tragen (Issue #607)', () => {
    expect(epicToCard(vorhaben, 12).derivedFrom).toBeNull()
  })

  it('erzeugt eine Karte vom Typ Vorhaben', () => {
    expect(epicToCard(vorhaben, 12).type).toBe('EPIC')
  })
})
