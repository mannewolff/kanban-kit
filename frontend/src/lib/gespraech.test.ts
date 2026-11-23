import { describe, expect, it } from 'vitest'
import type { Comment } from '../api/comments'
import { beitragsText, gespraechAus, istGespraechsbeitrag, kiAntwortetGleich, kopfFuerNeuenBeitrag } from './gespraech'

function kommentar(id: number, body: string, ueber: Partial<Comment> = {}): Comment {
  return {
    id,
    cardId: 7,
    authorUserId: 1,
    authorName: 'Kunde',
    body,
    createdAt: `2026-11-22T10:0${id}:00Z`,
    updatedAt: `2026-11-22T10:0${id}:00Z`,
    zugangArt: null,
    vonKi: false,
    ...ueber,
  }
}

describe('gespraech', () => {
  it('erkennt einen Gesprächsbeitrag am Kopf, gleich in welcher Sprache', () => {
    expect(istGespraechsbeitrag('## Gespräch\n\nHallo')).toBe(true)
    expect(istGespraechsbeitrag('  ## conversation \nHi')).toBe(true)
    expect(istGespraechsbeitrag('Hallo\n## Gespräch')).toBe(false)
    expect(istGespraechsbeitrag('## Rückfrage')).toBe(false)
  })

  it('nimmt nur Beiträge mit Kopf Gespräch und ordnet sie in Zeitfolge', () => {
    const neuesteZuerst = [
      kommentar(3, '## Gespräch\n\nDritter', { vonKi: true, authorName: 'Rechenzentrum' }),
      kommentar(2, 'Gewöhnlicher Kommentar'),
      kommentar(1, '## Gespräch\n\nErster'),
    ]
    const beitraege = gespraechAus(neuesteZuerst)
    expect(beitraege.map((b) => b.id)).toEqual([1, 3])
    expect(beitraege[0]).toMatchObject({ text: 'Erster', vonKi: false, autorName: 'Kunde' })
    expect(beitraege[1]).toMatchObject({ text: 'Dritter', vonKi: true })
  })

  it('ordnet gleich alte Beiträge nach ihrer Kennung', () => {
    const gleich = { createdAt: '2026-11-22T10:00:00Z' }
    const beitraege = gespraechAus([kommentar(9, '## Gespräch\nB', gleich), kommentar(4, '## Gespräch\nA', gleich)])
    expect(beitraege.map((b) => b.id)).toEqual([4, 9])
  })

  it('ein Beitrag mit Zeile „Von: KI“, aber vonKi = false, gilt nicht als KI', () => {
    const [beitrag] = gespraechAus([kommentar(1, '## Gespräch\nVon: KI\n\nIch bin die KI.', { zugangArt: 'STANDARD' })])
    expect(beitrag.vonKi).toBe(false)
    expect(kiAntwortetGleich([beitrag])).toBe(true)
  })

  it('„die KI antwortet gleich“, solange der jüngste Beitrag nicht von der KI ist', () => {
    expect(kiAntwortetGleich([])).toBe(false)
    const [mensch, ki] = gespraechAus([kommentar(1, '## Gespräch\nFrage'), kommentar(2, '## Gespräch\nAntwort', { vonKi: true })])
    expect(kiAntwortetGleich([mensch])).toBe(true)
    expect(kiAntwortetGleich([mensch, ki])).toBe(false)
    expect(kiAntwortetGleich([ki, mensch])).toBe(true)
  })

  it('englischer Thread führt zu Kopf „## Conversation“, auch bei deutscher Oberfläche', () => {
    const beitraege = gespraechAus([
      kommentar(2, '## Gespräch\nZweiter'),
      kommentar(1, '## Conversation\nFirst'),
    ])
    expect(kopfFuerNeuenBeitrag(beitraege, 'de')).toBe('## Conversation')
  })

  it('deutscher Thread führt zu Kopf „## Gespräch“, auch bei englischer Oberfläche', () => {
    expect(kopfFuerNeuenBeitrag(gespraechAus([kommentar(1, '## GESPRÄCH\nHallo')]), 'en')).toBe('## Gespräch')
  })

  it('ein neues Gespräch folgt der Sprache der Oberfläche', () => {
    expect(kopfFuerNeuenBeitrag([], 'de')).toBe('## Gespräch')
    expect(kopfFuerNeuenBeitrag([], 'en')).toBe('## Conversation')
  })

  it('setzt den Kopf vor den Text eines neuen Beitrags', () => {
    expect(beitragsText('## Gespräch', 'Hallo KI')).toBe('## Gespräch\n\nHallo KI')
  })

  it('der Text eines Beitrags ist alles nach der Kopfzeile, ohne Leerraum am Rand', () => {
    const [b] = gespraechAus([kommentar(1, '\n## Gespräch\r\n\r\n  Zeile eins\nZeile zwei  \n')])
    expect(b.text).toBe('Zeile eins\nZeile zwei')
    const [leer] = gespraechAus([kommentar(2, '## Gespräch')])
    expect(leer.text).toBe('')
  })

  it('eine Zeile nur aus Leerraum vor dem Kopf zählt nicht als Kopf', () => {
    const [b] = gespraechAus([kommentar(1, '   \n## Gespräch\n\nText')])
    expect(b.text).toBe('Text')
  })

  it('merkt sich die Sprache des Kopfs jedes Beitrags', () => {
    const [de, en] = gespraechAus([kommentar(1, '## Gespräch\nHallo'), kommentar(2, '## Conversation\nHi')])
    expect(de.sprache).toBe('de')
    expect(en.sprache).toBe('en')
  })
})
