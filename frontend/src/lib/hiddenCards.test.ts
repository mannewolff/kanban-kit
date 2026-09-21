import { describe, expect, it } from 'vitest'
import { hiddenCardNumbers } from './hiddenCards'

function card(number: number) {
  return { number }
}

function epic(id: number, memberNumbers: number[]) {
  return { id, memberNumbers }
}

describe('hiddenCardNumbers', () => {
  it('blendet alle memberNumbers eines ausgeblendeten Vorhabens aus', () => {
    const cards = [card(1), card(2), card(3)]
    const epics = [epic(10, [1, 2])]
    const hidden = hiddenCardNumbers(cards, epics, new Set([10]), null)
    expect([...hidden].sort()).toEqual([1, 2])
  })

  it('blendet zwei gleichzeitig ausgeblendete Vorhaben aus', () => {
    const cards = [card(1), card(2), card(3)]
    const epics = [epic(10, [1]), epic(20, [2])]
    const hidden = hiddenCardNumbers(cards, epics, new Set([10, 20]), null)
    expect([...hidden].sort()).toEqual([1, 2])
  })

  it('zählt eine Karte in zwei ausgeblendeten Vorhaben nur einmal', () => {
    const cards = [card(1)]
    const epics = [epic(10, [1]), epic(20, [1])]
    const hidden = hiddenCardNumbers(cards, epics, new Set([10, 20]), null)
    expect([...hidden]).toEqual([1])
  })

  it('liefert für ein ausgeblendetes, aber gelöschtes Vorhaben einen leeren Beitrag', () => {
    const cards = [card(1)]
    const epics = [epic(10, [1])]
    const hidden = hiddenCardNumbers(cards, epics, new Set([99]), null)
    expect([...hidden]).toEqual([])
  })

  it('blendet für ein Vorhaben mit leerem memberNumbers nichts aus', () => {
    const cards = [card(1)]
    const epics = [epic(10, [])]
    const hidden = hiddenCardNumbers(cards, epics, new Set([10]), null)
    expect([...hidden]).toEqual([])
  })

  it('verdeckt eine Karte ohne Vorhaben bei epicFilter == null nie', () => {
    const cards = [card(1), card(2)]
    const epics = [epic(10, [2])]
    const hidden = hiddenCardNumbers(cards, epics, new Set([10]), null)
    expect(hidden.has(1)).toBe(false)
  })

  it('verdeckt ohne Filter und ohne Ausblenden keine Karte', () => {
    const cards = [card(1), card(2), card(3)]
    const epics = [epic(10, [1, 2])]
    const hidden = hiddenCardNumbers(cards, epics, new Set(), null)
    expect([...hidden]).toEqual([])
  })

  it('zeigt beim Filter Wurzel, Plan und Arbeitspakete des Vorhabens', () => {
    // Karte 1 ist die Wurzel, die Karten 2 und 3 gehören über ihre Herkunft dazu
    // (Plan und Arbeitspaket) — alle drei stehen in memberNumbers.
    const cards = [card(1), card(2), card(3)]
    const epics = [epic(10, [1, 2, 3])]
    const hidden = hiddenCardNumbers(cards, epics, new Set(), 10)
    expect([...hidden]).toEqual([])
  })

  it('verdeckt beim Filter Karten ohne Vorhaben und Karten eines anderen Vorhabens', () => {
    const cards = [card(1), card(2), card(3)]
    const epics = [epic(10, [1]), epic(20, [2])]
    const hidden = hiddenCardNumbers(cards, epics, new Set(), 10)
    // Karte 2 hängt am Vorhaben 20, Karte 3 an keinem.
    expect([...hidden].sort()).toEqual([2, 3])
  })

  it('zeigt eine Karte zweier Vorhaben beim Filter auf jedes der beiden', () => {
    const cards = [card(1), card(2), card(3)]
    const epics = [epic(10, [1, 2]), epic(20, [2, 3])]
    expect(hiddenCardNumbers(cards, epics, new Set(), 10).has(2)).toBe(false)
    expect(hiddenCardNumbers(cards, epics, new Set(), 20).has(2)).toBe(false)
  })

  it('vereinigt beide Achsen: Ausblenden unterläuft den Filter nicht', () => {
    const cards = [card(1), card(2), card(3)]
    const epics = [epic(10, [1, 2]), epic(20, [2, 3])]
    const hidden = hiddenCardNumbers(cards, epics, new Set([20]), 10)
    // Karte 2 gehört zum gefilterten Vorhaben 10, steckt aber im ausgeblendeten Vorhaben 20.
    expect([...hidden].sort()).toEqual([2, 3])
    expect(hidden.has(1)).toBe(false)
  })

  it('verdeckt beim Filter auf ein nicht vorhandenes Vorhaben alle Karten', () => {
    const cards = [card(1), card(2)]
    const epics = [epic(10, [1, 2])]
    const hidden = hiddenCardNumbers(cards, epics, new Set(), 99)
    expect([...hidden].sort()).toEqual([1, 2])
  })
})
