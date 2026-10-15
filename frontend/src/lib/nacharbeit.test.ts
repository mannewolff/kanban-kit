import { describe, expect, it } from 'vitest'
import type { NightRunRework, NightRunReworkLine, NightRunReworkWeek } from '../api/nightRuns'
import { de } from '../i18n/de'
import { en } from '../i18n/en'
import { balkenZustand, dokumentSeit, kopfText, wochenHinweis, zeilenZustand } from './nacharbeit'

/** Reine Hilfen der Platte „Nacharbeit“ (Issue #1759, Plan #1752 E8, E13). */

const datum = (iso: string) => `D(${iso.slice(0, 10)})`

const zeile = (teil: Partial<NightRunReworkLine> = {}): NightRunReworkLine => ({
  dataSince: '2026-07-01T22:00:00Z',
  affected: 0,
  processed: 0,
  cards: [],
  ...teil,
})

const woche = (weekStart: string): NightRunReworkWeek => ({
  weekStart,
  packages: { state: 'KEINE_KARTE', affected: 0, processed: 0 },
  documents: { state: 'KEINE_KARTE', affected: 0, processed: 0 },
})

const daten = (teil: Partial<NightRunRework> = {}): NightRunRework => ({
  dataSince: '2026-07-01T22:00:00Z',
  runsInPeriod: true,
  packages: zeile(),
  documents: zeile(),
  weeks: [woche('2026-07-23T06:00:00Z'), woche('2026-07-30T06:00:00Z')],
  ...teil,
})

describe('kopfText', () => {
  it('nennt „letzte 12 Wochen“, wenn die Paket-Daten vor dem ältesten Fenster beginnen', () => {
    expect(kopfText(de.LEITSTAND, daten(), datum)).toBe('letzte 2 Wochen')
    expect(kopfText(en.LEITSTAND, daten(), datum)).toBe('last 2 weeks')
  })

  it('nennt „letzte 12 Wochen“, wenn die Daten genau mit dem ältesten Fenster beginnen', () => {
    const d = daten({ packages: zeile({ dataSince: '2026-07-23T06:00:00Z' }) })
    expect(kopfText(de.LEITSTAND, d, datum)).toBe('letzte 2 Wochen')
  })

  it('nennt „letzte 12 Wochen“ ohne aufbewahrten Run', () => {
    const d = daten({ dataSince: null, packages: zeile({ dataSince: null }), documents: zeile({ dataSince: null }) })
    expect(kopfText(de.LEITSTAND, d, datum)).toBe('letzte 2 Wochen')
  })

  it('nennt den Beginn der Paket-Daten, wenn sie später beginnen (E8)', () => {
    const d = daten({ packages: zeile({ dataSince: '2026-07-24T22:00:00Z' }) })
    expect(kopfText(de.LEITSTAND, d, datum)).toBe('seit D(2026-07-24)')
    expect(kopfText(en.LEITSTAND, d, datum)).toBe('since D(2026-07-24)')
  })

  it('nennt ohne Wochen den Zeitraum', () => {
    expect(kopfText(de.LEITSTAND, daten({ weeks: [] }), datum)).toBe('letzte 0 Wochen')
  })
})

describe('dokumentSeit', () => {
  it('schweigt, wenn die Dokument-Daten nicht später als der Kopf beginnen', () => {
    expect(dokumentSeit(de.LEITSTAND, daten(), datum)).toBeNull()
    const gleich = daten({ documents: zeile({ dataSince: '2026-07-23T06:00:00Z' }) })
    expect(dokumentSeit(de.LEITSTAND, gleich, datum)).toBeNull()
  })

  it('nennt den eigenen Beginn, wenn die Dokument-Zeile nach dem ältesten Fenster beginnt', () => {
    const d = daten({ documents: zeile({ dataSince: '2026-07-25T10:00:00Z' }) })
    expect(dokumentSeit(de.LEITSTAND, d, datum)).toBe('seit D(2026-07-25)')
  })

  it('misst an einem späteren Paket-Beginn und schweigt bei gleichem Beginn', () => {
    const gleich = daten({
      packages: zeile({ dataSince: '2026-07-25T10:00:00Z' }),
      documents: zeile({ dataSince: '2026-07-25T10:00:00Z' }),
    })
    expect(dokumentSeit(de.LEITSTAND, gleich, datum)).toBeNull()
    const spaeter = daten({
      packages: zeile({ dataSince: '2026-07-25T10:00:00Z' }),
      documents: zeile({ dataSince: '2026-07-28T10:00:00Z' }),
    })
    expect(dokumentSeit(de.LEITSTAND, spaeter, datum)).toBe('seit D(2026-07-28)')
  })

  it('schweigt ohne Dokument-Daten oder ohne Wochen', () => {
    expect(dokumentSeit(de.LEITSTAND, daten({ documents: zeile({ dataSince: null }) }), datum)).toBeNull()
    const ohneWochen = daten({ weeks: [], documents: zeile({ dataSince: '2026-07-25T10:00:00Z' }) })
    expect(dokumentSeit(de.LEITSTAND, ohneWochen, datum)).toBeNull()
  })
})

describe('zeilenZustand', () => {
  it('meldet keine Runs, wenn im Zeitraum keiner startete', () => {
    expect(zeilenZustand(false, zeile({ processed: 3, affected: 1 }))).toEqual({ art: 'keineRuns' })
  })

  it('meldet keine bearbeitete Karte bei Bezugszahl 0 der Gruppe', () => {
    expect(zeilenZustand(true, zeile({ processed: 0 }))).toEqual({ art: 'keineKarte' })
  })

  it('liefert sonst die Quote', () => {
    expect(zeilenZustand(true, zeile({ affected: 2, processed: 14 }))).toEqual({ art: 'quote', betroffen: 2, bearbeitet: 14 })
    expect(zeilenZustand(true, zeile({ affected: 0, processed: 1 }))).toEqual({ art: 'quote', betroffen: 0, bearbeitet: 1 })
  })
})

describe('balkenZustand', () => {
  it('bildet die drei Zustände einer Woche ab', () => {
    expect(balkenZustand({ state: 'KEINE_DATEN', affected: 0, processed: 0 })).toEqual({ art: 'keineDaten' })
    expect(balkenZustand({ state: 'KEINE_KARTE', affected: 0, processed: 0 })).toEqual({ art: 'grundlinie' })
    expect(balkenZustand({ state: 'QUOTE', affected: 1, processed: 4 })).toEqual({ art: 'balken', prozent: 25 })
  })

  it('kennt auch 0 % und 100 % und rundet auf ganze Prozent', () => {
    expect(balkenZustand({ state: 'QUOTE', affected: 0, processed: 6 })).toEqual({ art: 'balken', prozent: 0 })
    expect(balkenZustand({ state: 'QUOTE', affected: 6, processed: 6 })).toEqual({ art: 'balken', prozent: 100 })
    expect(balkenZustand({ state: 'QUOTE', affected: 1, processed: 3 })).toEqual({ art: 'balken', prozent: 33 })
    expect(balkenZustand({ state: 'QUOTE', affected: 2, processed: 3 })).toEqual({ art: 'balken', prozent: 67 })
  })

  it('zeigt eine Quote ohne Bezugszahl als Grundlinie, nie als Balken', () => {
    expect(balkenZustand({ state: 'QUOTE', affected: 0, processed: 0 })).toEqual({ art: 'grundlinie' })
  })

  it('zeigt eine Woche ohne Karte als Grundlinie, auch wenn eine Bezugszahl mitkommt', () => {
    expect(balkenZustand({ state: 'KEINE_KARTE', affected: 1, processed: 4 })).toEqual({ art: 'grundlinie' })
  })
})

describe('wochenHinweis', () => {
  const w = (packages: NightRunReworkWeek['packages'], documents: NightRunReworkWeek['documents']): NightRunReworkWeek => ({
    weekStart: '2026-10-08T06:00:00Z',
    packages,
    documents,
  })

  it('nennt die Zahlen hinter beiden Balken', () => {
    const hinweis = wochenHinweis(
      de.LEITSTAND,
      w({ state: 'QUOTE', affected: 1, processed: 14 }, { state: 'QUOTE', affected: 0, processed: 6 }),
    )
    expect(hinweis).toBe('Pakete 1 von 14 · Dokumente 0 von 6')
  })

  it('sagt „keine Daten“, wenn keine Gruppe Daten hat', () => {
    const leer = { state: 'KEINE_DATEN', affected: 0, processed: 0 } as const
    expect(wochenHinweis(de.LEITSTAND, w(leer, leer))).toBe('keine Daten')
    expect(wochenHinweis(en.LEITSTAND, w(leer, leer))).toBe('no data')
  })

  it('benennt Grundlinie und fehlende Daten je Gruppe', () => {
    const hinweis = wochenHinweis(
      de.LEITSTAND,
      w({ state: 'KEINE_KARTE', affected: 0, processed: 0 }, { state: 'KEINE_DATEN', affected: 0, processed: 0 }),
    )
    expect(hinweis).toBe('Pakete keine bearbeitete Karte · Dokumente keine Daten')
    const englisch = wochenHinweis(
      en.LEITSTAND,
      w({ state: 'KEINE_KARTE', affected: 0, processed: 0 }, { state: 'KEINE_DATEN', affected: 0, processed: 0 }),
    )
    expect(englisch).toBe('Packages no processed card · Documents no data')
  })

  it('nennt beide Gruppen, wenn nur die Pakete keine Daten haben', () => {
    const hinweis = wochenHinweis(
      de.LEITSTAND,
      w({ state: 'KEINE_DATEN', affected: 0, processed: 0 }, { state: 'QUOTE', affected: 0, processed: 6 }),
    )
    expect(hinweis).toBe('Pakete keine Daten · Dokumente 0 von 6')
  })
})
