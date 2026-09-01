import { describe, expect, it } from 'vitest'
import { nextSortState, sortCards, type SortState } from './listSort'
import type { BoardColumn } from '../api/boards'
import type { Card } from '../api/cards'
import type { Epic } from '../api/epics'

function card(number: number, over: Partial<Card> = {}): Card {
  return {
    id: number * 100,
    boardId: 1,
    columnId: 1,
    number,
    title: `Karte ${number}`,
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
    labels: [],
    derivedFrom: null,
    ...over,
  }
}

function column(id: number, name: string, position: number): BoardColumn {
  return { id, name, position, wipLimit: null }
}

function epic(number: number, memberNumbers: number[], shortcode: string | null): Epic {
  return {
    id: number * 10,
    number,
    title: `Vorhaben ${number}`,
    description: null,
    shortcode,
    done: 0,
    total: memberNumbers.length,
    memberNumbers,
    rootNumbers: [],
    requirementCardNumber: null,
  }
}

function ctxVon(columns: readonly BoardColumn[] = [], epics: readonly Epic[] = []) {
  return { columnById: new Map(columns.map((c) => [c.id, c])), epics }
}

/** Kurzform für die Prüfung: welche Karten in welcher Reihenfolge herauskommen. */
function nummern(cards: readonly Card[]): number[] {
  return cards.map((c) => c.number)
}

describe('nextSortState', () => {
  it('startet auf einer neuen Spalte aufsteigend', () => {
    expect(nextSortState(null, 'title')).toEqual({ key: 'title', dir: 'asc' })
  })

  it('schaltet auf derselben Spalte von asc auf desc', () => {
    expect(nextSortState({ key: 'title', dir: 'asc' }, 'title')).toEqual({ key: 'title', dir: 'desc' })
  })

  it('schaltet auf derselben Spalte von desc auf keine Sortierung', () => {
    expect(nextSortState({ key: 'title', dir: 'desc' }, 'title')).toBeNull()
  })

  it('startet beim Wechsel auf eine andere Spalte wieder aufsteigend, auch aus desc heraus', () => {
    expect(nextSortState({ key: 'title', dir: 'desc' }, 'number')).toEqual({ key: 'number', dir: 'asc' })
  })
})

describe('sortCards — ohne Sortierung', () => {
  it('gibt eine Kopie in unveränderter Reihenfolge zurück', () => {
    const cards = [card(3), card(1), card(2)]
    const sortiert = sortCards(cards, null, ctxVon())
    expect(nummern(sortiert)).toEqual([3, 1, 2])
    expect(sortiert).not.toBe(cards)
  })
})

describe('sortCards — Nummer', () => {
  const cards = [card(10), card(2), card(33)]

  it('sortiert numerisch aufsteigend', () => {
    expect(nummern(sortCards(cards, { key: 'number', dir: 'asc' }, ctxVon()))).toEqual([2, 10, 33])
  })

  it('sortiert numerisch absteigend', () => {
    expect(nummern(sortCards(cards, { key: 'number', dir: 'desc' }, ctxVon()))).toEqual([33, 10, 2])
  })

  it('verändert das übergebene Array nicht', () => {
    sortCards(cards, { key: 'number', dir: 'asc' }, ctxVon())
    expect(nummern(cards)).toEqual([10, 2, 33])
  })
})

describe('sortCards — Status', () => {
  // „Backlog" steht auf Position 0, „Aufgaben" auf Position 1: fachliche und alphabetische
  // Ordnung fallen auseinander, nur so belegt der Test die Spaltenposition als Vergleichswert.
  const columns = [column(1, 'Backlog', 0), column(2, 'Aufgaben', 1)]
  const cards = [card(1, { columnId: 2 }), card(2, { columnId: 1 })]

  it('sortiert nach Spaltenposition, nicht alphabetisch', () => {
    expect(nummern(sortCards(cards, { key: 'status', dir: 'asc' }, ctxVon(columns)))).toEqual([2, 1])
  })

  it('kehrt die Spaltenposition bei desc um', () => {
    expect(nummern(sortCards(cards, { key: 'status', dir: 'desc' }, ctxVon(columns)))).toEqual([1, 2])
  })

  it('setzt eine Karte ohne existierende Spalte auf Position 0', () => {
    const mitWaise = [card(1, { columnId: 2 }), card(2, { columnId: 99 }), card(3, { columnId: 1 })]
    expect(nummern(sortCards(mitWaise, { key: 'status', dir: 'asc' }, ctxVon(columns)))).toEqual([2, 3, 1])
  })

  it('stellt archivierte Karten in beiden Richtungen ans Ende', () => {
    const mitArchiv = [card(1, { columnId: 1, archived: true }), card(2, { columnId: 2 }), card(3, { columnId: 1 })]
    const ctx = ctxVon(columns)
    expect(nummern(sortCards(mitArchiv, { key: 'status', dir: 'asc' }, ctx))).toEqual([3, 2, 1])
    expect(nummern(sortCards(mitArchiv, { key: 'status', dir: 'desc' }, ctx))).toEqual([2, 3, 1])
  })

  it('ordnet mehrere archivierte Karten nach der Grundordnung, in beiden Richtungen gleich', () => {
    const archiv = [
      card(1, { columnId: 2, archived: true, positionInColumn: 0 }),
      card(2, { columnId: 1, archived: true, positionInColumn: 7 }),
      card(3, { columnId: 1, archived: true, positionInColumn: 2 }),
    ]
    const ctx = ctxVon(columns)
    expect(nummern(sortCards(archiv, { key: 'status', dir: 'asc' }, ctx))).toEqual([3, 2, 1])
    expect(nummern(sortCards(archiv, { key: 'status', dir: 'desc' }, ctx))).toEqual([3, 2, 1])
  })
})

describe('sortCards — Vorhaben', () => {
  // Zugeordnet wird über `memberNumbers` — dieselbe Regel, nach der die Zelle ihr Kürzel zeigt.
  const epics = [epic(70, [1], 'ZZ'), epic(80, [2], 'AA')]
  const cards = [card(1), card(2), card(3)]

  it('sortiert nach dem Kürzel aufsteigend, Karten ohne Vorhaben ans Ende', () => {
    expect(nummern(sortCards(cards, { key: 'epic', dir: 'asc' }, ctxVon([], epics)))).toEqual([2, 1, 3])
  })

  it('lässt Karten ohne Vorhaben auch absteigend am Ende', () => {
    expect(nummern(sortCards(cards, { key: 'epic', dir: 'desc' }, ctxVon([], epics)))).toEqual([1, 2, 3])
  })

  it('sortiert nach dem abgeleiteten Kürzel, wenn kein Kürzel gesetzt ist', () => {
    // Ohne Kürzel bildet `epicShortcode` die Initialen des Titels — „Vorhaben 70" wird zu „V7".
    const ohneKuerzel = [epic(70, [1], null), epic(80, [2], 'AA')]
    expect(nummern(sortCards([card(1), card(2)], { key: 'epic', dir: 'asc' }, ctxVon([], ohneKuerzel)))).toEqual([2, 1])
  })
})

describe('sortCards — Titel', () => {
  const cards = [card(1, { title: 'Zebra' }), card(2, { title: 'Alpha' })]

  it('sortiert alphabetisch aufsteigend', () => {
    expect(nummern(sortCards(cards, { key: 'title', dir: 'asc' }, ctxVon()))).toEqual([2, 1])
  })

  it('sortiert alphabetisch absteigend', () => {
    expect(nummern(sortCards(cards, { key: 'title', dir: 'desc' }, ctxVon()))).toEqual([1, 2])
  })
})

describe('sortCards — Beschreibung', () => {
  it('sortiert nach dem gestrippten Text, nicht nach den Markdown-Zeichen', () => {
    const cards = [card(1, { description: '## Zebra' }), card(2, { description: 'Alpha' })]
    expect(nummern(sortCards(cards, { key: 'excerpt', dir: 'asc' }, ctxVon()))).toEqual([2, 1])
  })

  it('behandelt eine Beschreibung, die nur aus Markdown-Zeichen besteht, als leer', () => {
    // Belegt den gestrippten Text als Vergleichswert: roh ist „## " nicht leer, die Zelle zeigt
    // aber nichts — die Karte gehört ans Ende.
    const cards = [card(1, { description: '## ' }), card(2, { description: 'Alpha' })]
    expect(nummern(sortCards(cards, { key: 'excerpt', dir: 'asc' }, ctxVon()))).toEqual([2, 1])
  })

  it('stellt eine fehlende Beschreibung (null) in beiden Richtungen ans Ende', () => {
    const cards = [card(1, { description: null }), card(2, { description: 'Zebra' }), card(3, { description: 'Alpha' })]
    expect(nummern(sortCards(cards, { key: 'excerpt', dir: 'asc' }, ctxVon()))).toEqual([3, 2, 1])
    expect(nummern(sortCards(cards, { key: 'excerpt', dir: 'desc' }, ctxVon()))).toEqual([2, 3, 1])
  })

  it('stellt eine leere Beschreibung ("") in beiden Richtungen ans Ende', () => {
    const cards = [card(1, { description: '' }), card(2, { description: 'Zebra' }), card(3, { description: 'Alpha' })]
    expect(nummern(sortCards(cards, { key: 'excerpt', dir: 'asc' }, ctxVon()))).toEqual([3, 2, 1])
    expect(nummern(sortCards(cards, { key: 'excerpt', dir: 'desc' }, ctxVon()))).toEqual([2, 3, 1])
  })
})

describe('sortCards — Gleichstand', () => {
  const columns = [column(1, 'Backlog', 0), column(2, 'Aufgaben', 1)]
  const cards = [
    card(1, { title: 'Gleich', columnId: 2, positionInColumn: 0 }),
    card(2, { title: 'Gleich', columnId: 1, positionInColumn: 7 }),
    card(3, { title: 'Gleich', columnId: 1, positionInColumn: 2 }),
  ]

  it('fällt auf Spaltenposition und positionInColumn zurück, in beiden Richtungen gleich', () => {
    const ctx = ctxVon(columns)
    const asc: SortState = { key: 'title', dir: 'asc' }
    const desc: SortState = { key: 'title', dir: 'desc' }
    expect(nummern(sortCards(cards, asc, ctx))).toEqual([3, 2, 1])
    expect(nummern(sortCards(cards, desc, ctx))).toEqual([3, 2, 1])
  })

  it('behandelt eine Karte ohne existierende Spalte auch im Gleichstand wie Position 0', () => {
    const mitWaise = [
      card(1, { title: 'Gleich', columnId: 2, positionInColumn: 0 }),
      card(2, { title: 'Gleich', columnId: 99, positionInColumn: 3 }),
    ]
    expect(nummern(sortCards(mitWaise, { key: 'title', dir: 'asc' }, ctxVon(columns)))).toEqual([2, 1])
  })
})
