import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Card, CardActivity } from '../api/cards'
import { anlagezeit, ermittleErzeugnisse, imFenster, kandidaten } from './kettenErzeugnisse'

const { boardsList, cardsList, getActivity } = vi.hoisted(() => ({
  boardsList: vi.fn(),
  cardsList: vi.fn(),
  getActivity: vi.fn(),
}))

vi.mock('../api/boards', () => ({ boardsApi: { list: boardsList } }))
vi.mock('../api/cards', () => ({ cardsApi: { list: cardsList, getActivity } }))

afterEach(() => vi.resetAllMocks())

const karte = (id: number, number: number, derivedFrom: number | null): Card =>
  ({ id, number, derivedFrom }) as Card

const angelegt = (createdAt: string, type = 'CREATED'): CardActivity =>
  ({ id: 1, type, createdAt }) as CardActivity

describe('kandidaten', () => {
  it('nimmt Pläne mit der Anforderung als Herkunft und Pakete unter diesen Plänen', () => {
    const karten = [karte(1, 10, 5), karte(2, 11, 10), karte(3, 12, 99), karte(4, 13, null)]
    const { plaene, pakete } = kandidaten(karten, 5)
    expect(plaene.map((k) => k.number)).toEqual([10])
    expect(pakete.map((k) => k.number)).toEqual([11])
  })
})

describe('anlagezeit', () => {
  it('liest den Zeitpunkt des Anlegens aus dem Verlauf', () => {
    expect(anlagezeit([angelegt('2026-09-01T10:00:00Z', 'MOVED'), angelegt('2026-09-01T09:00:00Z')])).toBe(
      Date.parse('2026-09-01T09:00:00Z'),
    )
  })

  it('kennt ohne Eintrag oder mit unlesbarem Zeitpunkt keinen', () => {
    expect(anlagezeit([])).toBeUndefined()
    expect(anlagezeit([angelegt('kein Datum')])).toBeUndefined()
  })
})

describe('imFenster', () => {
  const fenster = { von: 100, bis: 200 }
  it('zählt die Ränder mit und eine unbekannte Zeit nie', () => {
    expect(imFenster(100, fenster)).toBe(true)
    expect(imFenster(200, fenster)).toBe(true)
    expect(imFenster(99, fenster)).toBe(false)
    expect(imFenster(201, fenster)).toBe(false)
    expect(imFenster(undefined, fenster)).toBe(false)
  })
})

describe('ermittleErzeugnisse', () => {
  const fenster = { von: Date.parse('2026-09-01T22:00:00Z'), bis: Date.parse('2026-09-01T23:00:00Z') }

  it('liefert je Anforderung die im Lauf angelegten Pläne und Pakete, über alle Boards', async () => {
    boardsList.mockResolvedValue([{ id: 1 }, { id: 2 }])
    cardsList.mockImplementation((boardId: number) =>
      Promise.resolve(
        boardId === 1
          ? [karte(73, 773, 754), karte(75, 775, 773), karte(60, 600, 754), karte(61, 601, 600)]
          : [karte(74, 774, 773), karte(62, 602, 773)],
      ),
    )
    const zeiten: Record<number, string> = {
      73: '2026-09-01T22:10:00Z',
      74: '2026-09-01T22:20:00Z',
      75: '2026-09-01T22:21:00Z',
      60: '2026-08-01T10:00:00Z',
      61: '2026-09-01T22:30:00Z',
      62: '2026-08-30T10:00:00Z',
    }
    getActivity.mockImplementation((id: number) => Promise.resolve([angelegt(zeiten[id])]))

    const ergebnis = await ermittleErzeugnisse(5, [754, 800], fenster)

    expect(boardsList).toHaveBeenCalledWith(5)
    // #601 ist neu, hängt aber unter dem alten Plan #600; #602 ist alt.
    expect(ergebnis.get(754)).toEqual({ plaene: [773], pakete: [774, 775], planJePaket: { 774: 773, 775: 773 } })
    expect(ergebnis.get(800)).toEqual({ plaene: [], pakete: [], planJePaket: {} })
    // Der Verlauf wird nur für Kandidaten geholt, je Karte einmal.
    expect(getActivity.mock.calls.map(([id]) => id).sort((a, b) => a - b)).toEqual([60, 61, 62, 73, 74, 75])
  })

  it('wirft, wenn ein Abruf scheitert — ein Teilergebnis wäre eine Falschaussage', async () => {
    boardsList.mockRejectedValue(new Error('403'))
    await expect(ermittleErzeugnisse(5, [754], fenster)).rejects.toThrow('403')
  })
})
