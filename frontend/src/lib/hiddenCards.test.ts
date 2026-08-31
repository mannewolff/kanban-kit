import { describe, expect, it } from 'vitest'
import { hiddenCardNumbers } from './hiddenCards'

function card(number: number, parentId: number | null = null) {
  return { number, parentId }
}

function epic(id: number, memberNumbers: number[]) {
  return { id, memberNumbers }
}

describe('hiddenCardNumbers', () => {
  it('blendet alle memberNumbers eines ausgeblendeten Vorhabens aus', () => {
    const cards = [card(1, 10), card(2, 10), card(3)]
    const epics = [epic(10, [1, 2])]
    const hidden = hiddenCardNumbers(cards, epics, new Set([10]), null)
    expect([...hidden].sort()).toEqual([1, 2])
  })

  it('blendet zwei gleichzeitig ausgeblendete Vorhaben aus', () => {
    const cards = [card(1, 10), card(2, 20), card(3)]
    const epics = [epic(10, [1]), epic(20, [2])]
    const hidden = hiddenCardNumbers(cards, epics, new Set([10, 20]), null)
    expect([...hidden].sort()).toEqual([1, 2])
  })

  it('zählt eine Karte in zwei ausgeblendeten Vorhaben nur einmal', () => {
    const cards = [card(1, 10)]
    const epics = [epic(10, [1]), epic(20, [1])]
    const hidden = hiddenCardNumbers(cards, epics, new Set([10, 20]), null)
    expect([...hidden]).toEqual([1])
  })

  it('liefert für ein ausgeblendetes, aber gelöschtes Vorhaben einen leeren Beitrag', () => {
    const cards = [card(1, 10)]
    const epics = [epic(10, [1])]
    const hidden = hiddenCardNumbers(cards, epics, new Set([99]), null)
    expect([...hidden]).toEqual([])
  })

  it('blendet für ein Vorhaben mit leerem memberNumbers nichts aus', () => {
    const cards = [card(1, 10)]
    const epics = [epic(10, [])]
    const hidden = hiddenCardNumbers(cards, epics, new Set([10]), null)
    expect([...hidden]).toEqual([])
  })

  it('verdeckt eine Karte ohne Vorhaben bei epicFilter == null nie', () => {
    const cards = [card(1), card(2, 10)]
    const epics = [epic(10, [2])]
    const hidden = hiddenCardNumbers(cards, epics, new Set([10]), null)
    expect(hidden.has(1)).toBe(false)
  })

  it('rechnet die Filter-Achse über parentId, nicht über memberNumbers', () => {
    // Karte 2 gehört über die Herkunft zu Vorhaben 10 (steht in memberNumbers),
    // hängt aber an Vorhaben 20 — bei Filter auf 10 bleibt sie verborgen.
    const cards = [card(1, 10), card(2, 20)]
    const epics = [epic(10, [1, 2]), epic(20, [2])]
    const hidden = hiddenCardNumbers(cards, epics, new Set(), 10)
    expect(hidden.has(2)).toBe(true)
    expect(hidden.has(1)).toBe(false)
  })

  it('vereinigt beide Achsen: Ausblenden unterläuft den Filter nicht', () => {
    const cards = [card(1, 10), card(2, 10), card(3, 20)]
    const epics = [epic(10, [1, 2]), epic(20, [2, 3])]
    const hidden = hiddenCardNumbers(cards, epics, new Set([20]), 10)
    // Karte 2 passt zum Filter (parentId 10), steckt aber im ausgeblendeten Vorhaben 20.
    expect([...hidden].sort()).toEqual([2, 3])
  })
})
