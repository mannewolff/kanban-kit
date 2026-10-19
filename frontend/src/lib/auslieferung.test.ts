import { describe, expect, it } from 'vitest'
import {
  auslieferungKopf,
  ausfallDauer,
  ausfallText,
  frequenzEinheit,
  kennzahlDauer,
  prozentWert,
  wochenHoehen,
} from './auslieferung'

/** Kopf, Formate und Leerzustände der Platte „Auslieferung“ (Issue #1836, Plan #1831 E9, E10). */

const STUNDE = 3600
const TAG = 24 * STUNDE
const JETZT = Date.parse('2026-10-17T12:00:00Z')

describe('auslieferungKopf', () => {
  it('Szenario: Daten beginnen innerhalb des Fensters', () => {
    const vorDreiWochen = new Date(JETZT - 21 * TAG * 1000).toISOString()
    expect(auslieferungKopf(vorDreiWochen, 'de')).toBe('seit 26.9.2026')
    expect(auslieferungKopf(null, 'de')).toBe('letzte 12 Wochen')
  })

  it('formatiert das Datum in der Zone des Lesers, nicht in UTC', () => {
    // 23:30 UTC ist in Berlin (Zone der Tests) schon der nächste Tag.
    expect(auslieferungKopf('2026-09-30T23:30:00Z', 'de')).toBe('seit 1.10.2026')
  })

  it('spricht Englisch', () => {
    expect(auslieferungKopf('2026-09-26T12:00:00Z', 'en')).toBe('since Sep 26, 2026')
    expect(auslieferungKopf(null, 'en')).toBe('last 12 weeks')
  })
})

describe('frequenzEinheit', () => {
  it('nennt das Fenster ohne späteren Datenbeginn', () => {
    expect(frequenzEinheit(5, null, 'de')).toBe('Auslieferungen in 12 Wochen')
    expect(frequenzEinheit(1, null, 'de')).toBe('Auslieferung in 12 Wochen')
    expect(frequenzEinheit(0, null, 'de')).toBe('Auslieferungen in 12 Wochen')
  })

  it('nennt den Datenbeginn, wenn er im Fenster liegt', () => {
    expect(frequenzEinheit(3, '2026-09-26T12:00:00Z', 'de')).toBe('Auslieferungen seit 26.9.2026')
    expect(frequenzEinheit(1, '2026-09-26T12:00:00Z', 'de')).toBe('Auslieferung seit 26.9.2026')
    expect(frequenzEinheit(1, '2026-09-26T12:00:00Z', 'en')).toBe('delivery since Sep 26, 2026')
    expect(frequenzEinheit(3, '2026-09-26T12:00:00Z', 'en')).toBe('deliveries since Sep 26, 2026')
  })

  it('spricht Englisch, in Einzahl und Mehrzahl', () => {
    expect(frequenzEinheit(1, null, 'en')).toBe('delivery in 12 weeks')
    expect(frequenzEinheit(2, null, 'en')).toBe('deliveries in 12 weeks')
  })
})

describe('kennzahlDauer', () => {
  it('zeigt unter einem Tag Stunden mit einer Stelle', () => {
    expect(kennzahlDauer(5400, 'de')).toEqual({ wert: '1,5', einheit: 'Stunden' })
    expect(kennzahlDauer(TAG - 1, 'de')).toEqual({ wert: '24,0', einheit: 'Stunden' })
  })

  it('zeigt ab einem Tag Tage mit einer Stelle', () => {
    expect(kennzahlDauer(TAG, 'de')).toEqual({ wert: '1,0', einheit: 'Tage' })
    expect(kennzahlDauer(3.25 * TAG, 'en')).toEqual({ wert: '3.3', einheit: 'days' })
  })
})

describe('ausfallDauer', () => {
  it('nennt unter einer Stunde „unter 1 Stunde“', () => {
    expect(ausfallDauer(STUNDE - 1, 'de')).toBe('unter 1 Stunde')
    expect(ausfallDauer(0, 'de')).toBe('unter 1 Stunde')
  })

  it('nennt volle Stunden unter einem Tag', () => {
    expect(ausfallDauer(STUNDE, 'de')).toBe('1 Stunde')
    expect(ausfallDauer(3 * STUNDE + 59 * 60, 'de')).toBe('3 Stunden')
    expect(ausfallDauer(TAG - 1, 'de')).toBe('23 Stunden')
    expect(ausfallDauer(STUNDE, 'en')).toBe('1 hour')
    expect(ausfallDauer(5 * STUNDE, 'en')).toBe('5 hours')
  })

  it('nennt volle Tage ab einem Tag', () => {
    expect(ausfallDauer(TAG, 'de')).toBe('1 Tag')
    expect(ausfallDauer(2 * TAG + 23 * STUNDE, 'de')).toBe('2 Tage')
    expect(ausfallDauer(2 * TAG, 'en')).toBe('2 days')
    expect(ausfallDauer(TAG, 'en')).toBe('1 day')
    expect(ausfallDauer(STUNDE - 1, 'en')).toBe('under 1 hour')
  })
})

describe('ausfallText', () => {
  it('nennt einen laufenden Ausfall mit seiner Dauer bis jetzt', () => {
    const vorDreiStunden = new Date(JETZT - 3 * STUNDE * 1000).toISOString()
    expect(ausfallText(vorDreiStunden, JETZT, 'de')).toBe('Ausfall läuft seit 3 Stunden')
    expect(ausfallText(vorDreiStunden, JETZT, 'en')).toBe('Outage ongoing for 3 hours')
  })

  it('gibt ohne laufenden Ausfall nichts zurück', () => {
    expect(ausfallText(null, JETZT, 'de')).toBeNull()
  })

  it('zählt einen Beginn in der Zukunft (Uhrenversatz) nicht negativ', () => {
    expect(ausfallText(new Date(JETZT + 60_000).toISOString(), JETZT, 'de')).toBe('Ausfall läuft seit unter 1 Stunde')
  })
})

describe('prozentWert', () => {
  it('rundet auf ganze Prozent in der Schreibweise der Sprache', () => {
    expect(prozentWert(33.333, 'de')).toBe('33')
    expect(prozentWert(12.5, 'de')).toBe('13')
    expect(prozentWert(0, 'de')).toBe('0')
    expect(prozentWert(100, 'en')).toBe('100')
  })
})

describe('wochenHoehen', () => {
  it('rechnet Erfolg und Fehlschlag in Prozent der stärksten Woche', () => {
    expect(
      wochenHoehen([
        { weekStart: 'a', successCount: 3, failureCount: 1 },
        { weekStart: 'b', successCount: 0, failureCount: 0 },
        { weekStart: 'c', successCount: 1, failureCount: 0 },
      ]),
    ).toEqual([
      { erfolg: 75, fehlschlag: 25 },
      null,
      { erfolg: 25, fehlschlag: 0 },
    ])
  })

  it('zeigt eine Woche mit gleich vielen Erfolgen und Fehlschlägen als Balken, nicht als Lücke', () => {
    expect(wochenHoehen([{ weekStart: 'a', successCount: 2, failureCount: 2 }])).toEqual([
      { erfolg: 50, fehlschlag: 50 },
    ])
  })

  it('gibt ohne Versuch in jeder Woche nur Lücken', () => {
    expect(wochenHoehen([{ weekStart: 'a', successCount: 0, failureCount: 0 }])).toEqual([null])
  })
})
