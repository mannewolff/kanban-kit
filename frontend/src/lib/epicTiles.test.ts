import { describe, expect, it } from 'vitest'
import type { Card } from '../api/cards'
import type { Epic } from '../api/epics'
import type { Label } from '../api/labels'
import { aggregateMarks, countKinds, sortEpics } from './epicTiles'

function karte(number: number, title: string, labels: number[] = []): Card {
  return {
    id: number * 10,
    boardId: 1,
    columnId: 2,
    number,
    title,
    description: null,
    positionInColumn: 0,
    archived: false,
    ideaStored: false,
    movedToDoneAt: null,
    dependencies: [],
    type: 'CARD',
    parentId: null,
    shortcode: null,
    assignees: [],
    dueDate: null,
    labels,
    derivedFrom: null,
  }
}

function vorhaben(overrides: Partial<Epic> & { id: number }): Epic {
  return {
    number: overrides.id,
    title: `Vorhaben ${overrides.id}`,
    description: null,
    shortcode: null,
    done: 0,
    total: 0,
    memberNumbers: [],
    rootNumbers: [],
    requirementCardNumber: null,
    ...overrides,
  }
}

function label(id: number, name: string, countOnEpicTile: boolean): Label {
  return { id, boardId: 1, name, color: '#0f0', countOnEpicTile }
}

describe('countKinds', () => {
  it('zählt fachliche Anforderungen, Pläne und Arbeitspakete getrennt', () => {
    const cards = [
      karte(1, '[Fachlich] Kachel zeigt Zustände'),
      karte(2, '[Plan] Weg dorthin'),
      karte(3, 'Ganz gewöhnliches Arbeitspaket'),
    ]

    expect(countKinds(vorhaben({ id: 9, memberNumbers: [1, 2, 3] }), cards)).toEqual({
      requirements: 1,
      plans: 1,
      workItems: 1,
    })
  })

  /**
   * Eine eingeplante Idee ist faktisch ein Arbeitspaket (Entscheidung Manne, 2026-08-31); Ideen
   * im Pool erscheinen ohnehin nicht, sie tragen `ideaStored`.
   */
  it('zählt eine [Idee] als Arbeitspaket', () => {
    const cards = [karte(1, '[Idee] Labels an allen Karten')]

    expect(countKinds(vorhaben({ id: 9, memberNumbers: [1] }), cards)).toEqual({
      requirements: 0,
      plans: 0,
      workItems: 1,
    })
  })

  it('zählt ein Präfix mitten im Titel als Arbeitspaket', () => {
    const cards = [karte(1, 'Nacharbeit zu [Plan] #657')]

    expect(countKinds(vorhaben({ id: 9, memberNumbers: [1] }), cards)).toEqual({
      requirements: 0,
      plans: 0,
      workItems: 1,
    })
  })

  it('liefert für ein leeres Vorhaben überall null', () => {
    expect(countKinds(vorhaben({ id: 9 }), [])).toEqual({
      requirements: 0,
      plans: 0,
      workItems: 0,
    })
  })

  /**
   * Möglich durch den Done-Retention-Job oder eine Ladelücke. Als Arbeitspaket gezählt zeigte die
   * Kachel eine Karte an, die niemand öffnen kann.
   */
  it('ignoriert eine Mitgliedsnummer ohne zugehörige Karte', () => {
    const cards = [karte(1, '[Plan] Da')]

    expect(countKinds(vorhaben({ id: 9, memberNumbers: [1, 42] }), cards)).toEqual({
      requirements: 0,
      plans: 1,
      workItems: 0,
    })
  })
})

describe('aggregateMarks', () => {
  const labels = [label(70, 'bereit', true), label(71, 'intern', false), label(80, 'stockt', true)]

  it('zählt eine Karte bei jedem ihrer gezählten Labels', () => {
    const cards = [karte(1, 'A', [70, 80])]

    expect(aggregateMarks(vorhaben({ id: 9, memberNumbers: [1] }), cards, labels)).toEqual([
      { name: 'bereit', color: '#0f0', count: 1 },
      { name: 'stockt', color: '#0f0', count: 1 },
    ])
  })

  it('lässt ein Label mit countOnEpicTile === false weg', () => {
    const cards = [karte(1, 'A', [70, 71])]

    const marks = aggregateMarks(vorhaben({ id: 9, memberNumbers: [1] }), cards, labels)

    expect(marks).toEqual([{ name: 'bereit', color: '#0f0', count: 1 }])
    expect(marks.map((m) => m.name)).not.toContain('intern')
  })

  it('summiert über mehrere Karten und lässt ungenutzte Labels weg', () => {
    const cards = [karte(1, 'A', [70]), karte(2, 'B', [70]), karte(3, 'C', [])]

    expect(aggregateMarks(vorhaben({ id: 9, memberNumbers: [1, 2, 3] }), cards, labels)).toEqual([
      { name: 'bereit', color: '#0f0', count: 2 },
    ])
  })

  it('ignoriert eine Mitgliedsnummer ohne zugehörige Karte', () => {
    const cards = [karte(1, 'A', [70])]

    expect(aggregateMarks(vorhaben({ id: 9, memberNumbers: [1, 42] }), cards, labels)).toEqual([
      { name: 'bereit', color: '#0f0', count: 1 },
    ])
  })
})

describe('sortEpics', () => {
  const labels = [label(70, 'stockt', true)]

  it('sortiert absteigend nach Karten mit mindestens einer gezählten Marke', () => {
    const cards = [karte(1, 'A', [70]), karte(2, 'B', [70]), karte(3, 'C', [])]
    const wenig = vorhaben({ id: 1, memberNumbers: [3], total: 1 })
    const viel = vorhaben({ id: 2, memberNumbers: [1, 2], total: 2 })

    expect(sortEpics([wenig, viel], cards, labels).map((e) => e.id)).toEqual([2, 1])
  })

  /** Mehrere Marken an einer Karte zählen die Karte einmal, nicht zweimal. */
  it('zählt je Karte höchstens einmal', () => {
    const zweiLabels = [label(70, 'stockt', true), label(80, 'wartet', true)]
    const cards = [karte(1, 'A', [70, 80]), karte(2, 'B', [70]), karte(3, 'C', [70])]
    const eineKarte = vorhaben({ id: 1, memberNumbers: [1], total: 1 })
    const zweiKarten = vorhaben({ id: 2, memberNumbers: [2, 3], total: 2 })

    expect(sortEpics([eineKarte, zweiKarten], cards, zweiLabels).map((e) => e.id)).toEqual([2, 1])
  })

  it('entscheidet bei Gleichstand über das Anzeige-Kürzel', () => {
    const cards = [karte(1, 'A', [70]), karte(2, 'B', [70])]
    const zeta = vorhaben({ id: 1, shortcode: 'ZET', memberNumbers: [1], total: 1 })
    const alpha = vorhaben({ id: 2, shortcode: 'ALP', memberNumbers: [2], total: 1 })

    expect(sortEpics([zeta, alpha], cards, labels).map((e) => e.id)).toEqual([2, 1])
  })

  /** `Epic.shortcode` ist nullable; dann entscheidet die Ableitung aus dem Titel. */
  it('entscheidet bei Gleichstand ohne Kürzel über die Titel-Ableitung', () => {
    const cards = [karte(1, 'A', [70]), karte(2, 'B', [70])]
    const zeta = vorhaben({ id: 1, title: 'Zebra Zaun', memberNumbers: [1], total: 1 })
    const alpha = vorhaben({ id: 2, title: 'Alpha Anker', memberNumbers: [2], total: 1 })

    // ZZ vor AA -> die Ableitung, nicht die Reihenfolge der Eingabe, entscheidet.
    expect(sortEpics([zeta, alpha], cards, labels).map((e) => e.id)).toEqual([2, 1])
  })

  it('stellt ein abgeschlossenes Vorhaben hinter die unfertigen', () => {
    const cards = [karte(1, 'A', [70]), karte(2, 'B', [70])]
    const fertig = vorhaben({ id: 1, memberNumbers: [1, 2], done: 2, total: 2 })
    const offen = vorhaben({ id: 2, memberNumbers: [1], done: 0, total: 1 })

    expect(sortEpics([fertig, offen], cards, labels).map((e) => e.id)).toEqual([2, 1])
  })

  it('stellt ein leeres Vorhaben ganz ans Ende', () => {
    const cards = [karte(1, 'A', [])]
    const leer = vorhaben({ id: 1, total: 0, requirementCardNumber: null })
    const fertig = vorhaben({ id: 2, memberNumbers: [1], done: 1, total: 1 })
    const offen = vorhaben({ id: 3, memberNumbers: [1], done: 0, total: 1 })

    expect(sortEpics([leer, fertig, offen], cards, labels).map((e) => e.id)).toEqual([3, 2, 1])
  })

  /**
   * Leer heißt `total === 0` **und** ohne Anforderung: Ein Vorhaben, das eine Anforderung trägt,
   * aber noch keine Karten hat, ist eröffnet und nicht leer.
   */
  it('behandelt ein Vorhaben mit Anforderung, aber ohne Karten nicht als leer', () => {
    const leer = vorhaben({ id: 1, total: 0, requirementCardNumber: null })
    const eroeffnet = vorhaben({ id: 2, total: 0, requirementCardNumber: 7 })

    expect(sortEpics([leer, eroeffnet], [], labels).map((e) => e.id)).toEqual([2, 1])
  })

  it('lässt das Eingabe-Array unverändert', () => {
    const cards = [karte(1, 'A', [70])]
    const eingabe = [vorhaben({ id: 1, total: 0 }), vorhaben({ id: 2, memberNumbers: [1], total: 1 })]

    sortEpics(eingabe, cards, labels)

    expect(eingabe.map((e) => e.id)).toEqual([1, 2])
  })
})
