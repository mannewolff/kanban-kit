import { describe, expect, it } from 'vitest'
import type { NightRunState } from './nightRunLog'
import { laufgruppen, standText } from './aktuellerStand'

const paket = (state: NightRunState) => ({ state })

const lauf = (nightRunId: number, projectId: number, projectName: string) => ({
  nightRunId,
  projectId,
  projectName,
})

describe('standText (#1172, AK 8 der fachlichen Quelle #1153)', () => {
  it('nennt alle vier Zustaende in der Reihenfolge Erfolg · Erfolg, Pruefung rot · gescheitert · nicht bearbeitet', () => {
    const pakete = [paket('GREY'), paket('RED'), paket('YELLOW'), paket('GREEN')]
    expect(standText(pakete)).toBe(
      '4 gemeldet · 1 Erfolg, 1 Erfolg, Prüfung rot, 1 gescheitert, 1 nicht bearbeitet',
    )
  })

  it('nennt nur Zustaende mit mindestens einem Paket', () => {
    expect(standText([paket('GREEN'), paket('GREEN'), paket('RED')])).toBe(
      '3 gemeldet · 2 Erfolg, 1 gescheitert',
    )
  })

  it('zaehlt die grauen Pakete mit in die gemeldete Summe', () => {
    expect(standText([paket('GREEN'), paket('GREY'), paket('GREY')])).toBe(
      '3 gemeldet · 1 Erfolg, 2 nicht bearbeitet',
    )
  })

  it('sagt bei null Paketen woertlich „0 gemeldet"', () => {
    expect(standText([])).toBe('0 gemeldet')
  })

  it('nennt keinen Nenner — kein „von N"', () => {
    expect(standText([paket('YELLOW')])).not.toContain('von')
  })
})

describe('laufgruppen (#1172, AK 6 und E8 aus Plan #1167)', () => {
  it('fasst zwei Laeufe eines Projekts in einer Gruppe zusammen', () => {
    const gruppen = laufgruppen(
      [lauf(7, 1, 'manban'), lauf(6, 1, 'manban')],
      [
        { nightRunId: 7, pakete: [paket('GREEN')] },
        { nightRunId: 6, pakete: [paket('RED'), paket('GREY')] },
      ],
    )
    expect(gruppen).toEqual([
      {
        projectId: 1,
        projectName: 'manban',
        eintraege: [
          { nightRunId: 7, pakete: [paket('GREEN')], stand: '1 gemeldet · 1 Erfolg' },
          {
            nightRunId: 6,
            pakete: [paket('RED'), paket('GREY')],
            stand: '2 gemeldet · 1 gescheitert, 1 nicht bearbeitet',
          },
        ],
      },
    ])
  })

  it('trennt die Laeufe zweier Projekte in zwei Gruppen', () => {
    const gruppen = laufgruppen(
      [lauf(9, 2, 'kit'), lauf(8, 3, 'doku')],
      [
        { nightRunId: 9, pakete: [paket('GREEN')] },
        { nightRunId: 8, pakete: [paket('YELLOW')] },
      ],
    )
    expect(gruppen.map((g) => g.projectId)).toEqual([2, 3])
    expect(gruppen.map((g) => g.eintraege.map((e) => e.nightRunId))).toEqual([[9], [8]])
  })

  it('gibt einem Lauf ohne Eintrag in gemeldetePakete eine leere Paketliste und „0 gemeldet"', () => {
    expect(laufgruppen([lauf(4, 1, 'manban')], [])).toEqual([
      {
        projectId: 1,
        projectName: 'manban',
        eintraege: [{ nightRunId: 4, pakete: [], stand: '0 gemeldet' }],
      },
    ])
  })

  it('laesst die Reihenfolge der Antwort unveraendert — Laeufe wie in laufende, Pakete wie geliefert', () => {
    const gruppen = laufgruppen(
      [lauf(5, 1, 'manban'), lauf(9, 2, 'kit'), lauf(3, 1, 'manban')],
      [{ nightRunId: 5, pakete: [paket('GREY'), paket('GREEN'), paket('RED')] }],
    )
    expect(gruppen.map((g) => g.projectId)).toEqual([1, 2])
    expect(gruppen[0].eintraege.map((e) => e.nightRunId)).toEqual([5, 3])
    expect(gruppen[0].eintraege[0].pakete.map((p) => p.state)).toEqual(['GREY', 'GREEN', 'RED'])
  })

  it('nimmt den Projektnamen aus laufende', () => {
    const [gruppe] = laufgruppen([lauf(1, 42, 'Leitstand')], [{ nightRunId: 1, pakete: [] }])
    expect(gruppe.projectName).toBe('Leitstand')
  })
})
