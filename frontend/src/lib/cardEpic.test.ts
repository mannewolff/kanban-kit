import { describe, expect, it } from 'vitest'
import { epicOfCard, vorhabenFeld } from './cardEpic'
import type { Card } from '../api/cards'
import type { Epic } from '../api/epics'

function card(number: number, parentId: number | null = null): Card {
  return {
    id: number * 100,
    boardId: 1,
    columnId: 1,
    number,
    title: `Karte ${number}`,
    description: null,
    excerpt: null,
    positionInColumn: 0,
    archived: false,
    ideaStored: false,
    movedToDoneAt: null,
    dependencies: [],
    type: 'CARD',
    parentId,
    shortcode: null,
    assignees: [],
    dueDate: null,
    labels: [],
    derivedFrom: null,
  }
}

function epic(number: number, memberNumbers: number[], rootNumbers: number[]): Epic {
  return {
    id: number * 10,
    number,
    title: `Vorhaben ${number}`,
    description: null,
    shortcode: `V${number}`,
    done: 0,
    total: memberNumbers.length,
    memberNumbers,
    rootNumbers,
    requirementCardNumber: null,
  }
}

describe('epicOfCard', () => {
  it('findet das Vorhaben, zu dem die Karte über die Herkunft gehört', () => {
    const treffer = epicOfCard(card(5), [epic(70, [5], [])])
    expect(treffer?.number).toBe(70)
  })

  it('findet das Vorhaben, dem die Karte ausdrücklich zugeordnet ist', () => {
    const treffer = epicOfCard(card(5, 700), [epic(70, [5], [5])])
    expect(treffer?.number).toBe(70)
  })

  it('gibt der Herkunft den Vorrang vor der ausdrücklichen Zuordnung', () => {
    const ausdruecklich = epic(70, [5], [5])
    const ueberHerkunft = epic(80, [5], [])
    expect(epicOfCard(card(5, 700), [ausdruecklich, ueberHerkunft])?.number).toBe(80)
    // Reihenfolge der Vorhaben-Liste darf das Ergebnis nicht drehen.
    expect(epicOfCard(card(5, 700), [ueberHerkunft, ausdruecklich])?.number).toBe(80)
  })

  it('liefert undefined, wenn die Karte in keinem Vorhaben steht', () => {
    expect(epicOfCard(card(5), [epic(70, [1, 2], [1])])).toBeUndefined()
  })

  it('liefert undefined ohne jedes Vorhaben', () => {
    expect(epicOfCard(card(5), [])).toBeUndefined()
  })

  it('wählt bei zwei über die Herkunft erreichten Vorhaben die kleinere Nummer', () => {
    const treffer = epicOfCard(card(5), [epic(80, [5], []), epic(70, [5], [])])
    expect(treffer?.number).toBe(70)
  })

  it('wählt bei zwei ausdrücklichen Zuordnungen die kleinere Nummer', () => {
    const treffer = epicOfCard(card(5, 800), [epic(80, [5], [5]), epic(70, [5], [5])])
    expect(treffer?.number).toBe(70)
  })

  it('liefert undefined für eine Karte mit parentId, die in keinem memberNumbers steht', () => {
    // Der Fall der archivierten Karte: Der Server filtert `archived` und `ideaStored` aus der
    // Zugehörigkeit (`EpicMembership.zaehlt()`), die Anzeige folgt ihm. Der alte
    // `parentId`-Lookup hätte hier noch ein Vorhaben gezeigt — der Unterschied ist gewollt
    // (Entscheidung Manne, 2026-09-01).
    const archiviert = { ...card(5, 700), archived: true }
    expect(epicOfCard(archiviert, [epic(70, [], [])])).toBeUndefined()
  })
})

describe('vorhabenFeld', () => {
  const v70 = epic(70, [], [])
  const v80 = epic(80, [], [])

  it('nimmt ohne eigenen Optionsvorrat die volle Vorhaben-Liste', () => {
    const feld = vorhabenFeld({
      canEditEpic: true,
      parentId: null,
      selectableEpics: undefined,
      epics: [v70, v80],
    })
    expect(feld.epicOptionen).toEqual([v70, v80])
  })

  it('nimmt als Optionsvorrat die angegebene Liste, nicht die volle', () => {
    const feld = vorhabenFeld({
      canEditEpic: true,
      parentId: null,
      selectableEpics: [v70],
      epics: [v70, v80],
    })
    expect(feld.epicOptionen).toEqual([v70])
  })

  it('bleibt bearbeitbar und ohne Lesetext, solange keine Zuordnung besteht', () => {
    const feld = vorhabenFeld({
      canEditEpic: true,
      parentId: null,
      selectableEpics: [v70],
      epics: [v70],
    })
    expect(feld.epicLesend).toBe(false)
    expect(feld.epicLesendText).toBeUndefined()
  })

  it('ist lesend, wenn der Aufrufer die Zuordnung nicht ändern darf', () => {
    const feld = vorhabenFeld({
      canEditEpic: false,
      parentId: null,
      selectableEpics: [v70],
      epics: [v70],
    })
    expect(feld.epicLesend).toBe(true)
  })

  it('bleibt bearbeitbar, wenn das zugeordnete Vorhaben zur Auswahl steht', () => {
    const feld = vorhabenFeld({
      canEditEpic: true,
      parentId: v70.id,
      selectableEpics: [v70],
      epics: [v70],
    })
    expect(feld.epicLesend).toBe(false)
    expect(feld.epicLesendText).toBe('V70 – Vorhaben 70')
  })

  it('bleibt bearbeitbar, wenn das zugeordnete Vorhaben eines unter mehreren zur Auswahl ist', () => {
    // Es genügt, dass **ein** Eintrag des Optionsvorrats passt — nicht, dass alle passen.
    const feld = vorhabenFeld({
      canEditEpic: true,
      parentId: v70.id,
      selectableEpics: [v70, v80],
      epics: [v70, v80],
    })
    expect(feld.epicLesend).toBe(false)
  })

  it('ist lesend, wenn das zugeordnete Vorhaben nicht zur Auswahl steht, und nennt es trotzdem', () => {
    const feld = vorhabenFeld({
      canEditEpic: true,
      parentId: v80.id,
      selectableEpics: [v70],
      epics: [v70, v80],
    })
    expect(feld.epicLesend).toBe(true)
    expect(feld.epicLesendText).toBe('V80 – Vorhaben 80')
  })

  it('ist lesend und ohne Text, wenn das zugeordnete Vorhaben auch in der vollen Liste fehlt', () => {
    const feld = vorhabenFeld({
      canEditEpic: true,
      parentId: 999,
      selectableEpics: [v70],
      epics: [v70],
    })
    expect(feld.epicLesend).toBe(true)
    expect(feld.epicLesendText).toBeUndefined()
  })

  it('bildet das Kürzel aus dem Titel, wenn das Vorhaben keines gesetzt hat', () => {
    const ohneKuerzel: Epic = { ...v70, shortcode: null, title: 'Neue Warte bauen' }
    const feld = vorhabenFeld({
      canEditEpic: true,
      parentId: ohneKuerzel.id,
      selectableEpics: [ohneKuerzel],
      epics: [ohneKuerzel],
    })
    expect(feld.epicLesendText).toBe('NWB – Neue Warte bauen')
  })
})
