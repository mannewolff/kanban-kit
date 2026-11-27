import { describe, expect, it } from 'vitest'
import type { CodeAnalysisState, CodeAnalysisWeek } from '../api/dashboard'
import { de } from '../i18n/de'
import { en } from '../i18n/en'
import { codeanalyseKopf, dichteText, grundText, juengsteZaehlendeWoche, rueckgangText, wochenHoehen } from './codeanalyse'

/** Kopf, Formate und Texte der Platte „Codeanalyse“ (Issue #2278, Plan #2272 E7, E9, E11). */

const zustand = (findingsDensity: number | null, extra: Partial<CodeAnalysisState> = {}): CodeAnalysisState => ({
  bugs: 1,
  vulnerabilities: 0,
  codeSmells: 2,
  findings: 3,
  changedLines: findingsDensity === null ? 0 : 1000,
  bugsDensity: findingsDensity,
  vulnerabilitiesDensity: findingsDensity,
  codeSmellsDensity: findingsDensity,
  findingsDensity,
  ...extra,
})

const woche = (weekStart: string, extra: Partial<CodeAnalysisWeek> = {}): CodeAnalysisWeek => ({
  weekStart,
  countingMeasurements: 0,
  cards: null,
  before: null,
  final: null,
  reductionPercent: null,
  notCounting: [],
  ...extra,
})

const zaehlend = (weekStart: string, vorher: number | null, endzustand: number | null): CodeAnalysisWeek =>
  woche(weekStart, { countingMeasurements: 1, cards: 2, before: zustand(vorher), final: zustand(endzustand) })

describe('codeanalyseKopf', () => {
  it('nennt ohne späteren Datenbeginn die letzten 12 Kalenderwochen', () => {
    expect(codeanalyseKopf(null, 'de')).toBe('Kalenderwochen, letzte 12 Wochen')
    expect(codeanalyseKopf(null, 'en')).toBe('Calendar weeks, last 12 weeks')
  })

  it('nennt den Datenbeginn mit „seit“', () => {
    expect(codeanalyseKopf('2026-10-14T08:30:00Z', 'de')).toBe('Kalenderwochen, seit 14.10.2026')
    expect(codeanalyseKopf('2026-10-14T08:30:00Z', 'en')).toBe('Calendar weeks, since Oct 14, 2026')
  })

  it('schreibt das Datum in UTC, nicht in der Zone des Lesers (E11)', () => {
    // 23:30 UTC ist in Berlin (Zone der Tests) schon der nächste Tag.
    expect(codeanalyseKopf('2026-10-13T23:30:00Z', 'de')).toBe('Kalenderwochen, seit 13.10.2026')
    expect(codeanalyseKopf('2026-10-13T23:30:00Z', 'en')).toBe('Calendar weeks, since Oct 13, 2026')
  })
})

describe('dichteText', () => {
  it('zeigt eine Nachkommastelle in der Schreibweise der Sprache', () => {
    expect(dichteText(1.8, 'de')).toBe('1,8')
    expect(dichteText(1.8, 'en')).toBe('1.8')
    expect(dichteText(2, 'de')).toBe('2,0')
    expect(dichteText(1234.56, 'en')).toBe('1,234.6')
  })

  it('lässt null ohne Text', () => {
    expect(dichteText(null, 'de')).toBeNull()
    expect(dichteText(null, 'en')).toBeNull()
  })
})

describe('rueckgangText', () => {
  it('zeigt ganze Prozent', () => {
    expect(rueckgangText(80, 'de')).toBe('80')
    expect(rueckgangText(79.6, 'en')).toBe('80')
    expect(rueckgangText(0, 'de')).toBe('0')
  })

  it('zeigt einen Anstieg negativ mit Minuszeichen', () => {
    expect(rueckgangText(-33.3, 'de')).toBe('−33')
    expect(rueckgangText(-33.3, 'en')).toBe('−33')
    expect(rueckgangText(-1250, 'de')).toBe('−1.250')
  })

  it('zeigt einen kleinen Anstieg mit Minuszeichen und seiner ersten geltenden Stelle, nicht als 0', () => {
    // 1000 → 1004 Funde: Rückgang −0,4 %.
    expect(rueckgangText(-0.4, 'de')).toBe('−0,4')
    expect(rueckgangText(-0.4, 'en')).toBe('−0.4')
    expect(rueckgangText(-0.5, 'de')).toBe('−1')
    expect(rueckgangText(-0.04, 'de')).toBe('−0,04')
  })

  it('zeigt einen kleinen Rückgang mit seiner ersten geltenden Stelle, nicht als 0', () => {
    expect(rueckgangText(0.4, 'de')).toBe('0,4')
    expect(rueckgangText(0.04, 'en')).toBe('0.04')
  })

  it('kürzt einen kleinen Wert auf seine erste geltende Stelle, nicht auf mehrere Nachkommastellen', () => {
    expect(rueckgangText(0.43, 'de')).toBe('0,4')
    expect(rueckgangText(-0.043, 'en')).toBe('−0.04')
  })

  it('lässt null ohne Text', () => {
    expect(rueckgangText(null, 'de')).toBeNull()
  })
})

describe('wochenHoehen', () => {
  it('rechnet beide Summen-Dichten in Prozent der größten Dichte im Fenster, mit Lücke', () => {
    const wochen = [
      zaehlend('2026-11-02T00:00:00Z', 4, 1),
      woche('2026-11-09T00:00:00Z', { notCounting: [{ reason: 'missing-final', count: 1 }] }),
      zaehlend('2026-11-16T00:00:00Z', 8, 2),
    ]
    expect(wochenHoehen(wochen)).toEqual([{ vorher: 50, endzustand: 13 }, null, { vorher: 100, endzustand: 25 }])
  })

  it('zeigt eine Woche ohne Zeilen als Lücke', () => {
    expect(wochenHoehen([zaehlend('2026-11-02T00:00:00Z', null, null), zaehlend('2026-11-09T00:00:00Z', 2, 1)])).toEqual([
      null,
      { vorher: 100, endzustand: 50 },
    ])
  })

  it('lässt einen Zustand ohne Dichte leer, wenn der andere eine trägt', () => {
    expect(wochenHoehen([zaehlend('2026-11-02T00:00:00Z', 2, null)])).toEqual([{ vorher: 100, endzustand: null }])
    expect(wochenHoehen([zaehlend('2026-11-02T00:00:00Z', null, 2)])).toEqual([{ vorher: null, endzustand: 100 }])
  })

  it('nimmt die größte Dichte auch aus dem Endzustand', () => {
    expect(wochenHoehen([zaehlend('2026-11-02T00:00:00Z', 1, 4)])).toEqual([{ vorher: 25, endzustand: 100 }])
  })

  it('lässt eine positive Dichte neben einer viel größeren nicht auf Höhe 0 fallen', () => {
    expect(wochenHoehen([zaehlend('2026-11-02T00:00:00Z', 1000, 1)])).toEqual([{ vorher: 100, endzustand: 1 }])
    expect(wochenHoehen([zaehlend('2026-11-02T00:00:00Z', 1000, 0)])).toEqual([{ vorher: 100, endzustand: 0 }])
  })

  it('zeigt lauter Null-Dichten als Höhe 0, nicht als Lücke', () => {
    expect(wochenHoehen([zaehlend('2026-11-02T00:00:00Z', 0, 0)])).toEqual([{ vorher: 0, endzustand: 0 }])
  })

  it('liefert für ein Fenster ohne Woche nichts', () => {
    expect(wochenHoehen([])).toEqual([])
  })
})

describe('grundText', () => {
  it.each([
    ['no-measurement', 'keine Messung der Codeanalyse'],
    ['missing-before', 'Urteil vor der Codeanalyse fehlt'],
    ['missing-final', 'Urteil über den veröffentlichten Stand fehlt'],
    ['cards-removed', 'Karten vor dem Veröffentlichen herausgenommen'],
    ['incomplete', 'Messung unvollständig'],
    ['not-attributable', 'Funde nicht zuzuordnen'],
  ])('nennt %s auf Deutsch', (grund, text) => {
    expect(grundText(grund, 'de')).toBe(text)
  })

  it.each([
    ['no-measurement', 'no code analysis measurement'],
    ['missing-before', 'verdict before the code analysis missing'],
    ['missing-final', 'verdict on the published state missing'],
    ['cards-removed', 'cards removed before publishing'],
    ['incomplete', 'measurement incomplete'],
    ['not-attributable', 'findings cannot be attributed'],
  ])('nennt %s auf Englisch', (grund, text) => {
    expect(grundText(grund, 'en')).toBe(text)
  })

  it('zeigt einen unbekannten Grund, wie er gemeldet wurde', () => {
    expect(grundText('sonar-timeout', 'de')).toBe('sonar-timeout')
    expect(grundText('sonar-timeout', 'en')).toBe('sonar-timeout')
  })

  it('nimmt einen Namen aus dem Objekt-Prototyp nicht für einen bekannten Grund', () => {
    expect(grundText('toString', 'de')).toBe('toString')
    expect(grundText('constructor', 'en')).toBe('constructor')
  })
})

describe('juengsteZaehlendeWoche', () => {
  it('nimmt die jüngste Woche mit zählenden Messungen', () => {
    const juengste = zaehlend('2026-11-09T00:00:00Z', 2, 1)
    const wochen = [zaehlend('2026-11-02T00:00:00Z', 4, 1), juengste, woche('2026-11-16T00:00:00Z')]
    expect(juengsteZaehlendeWoche(wochen)).toBe(juengste)
  })

  it('liefert keine, wenn im Fenster keine Woche zählt', () => {
    const wochen = [woche('2026-11-02T00:00:00Z', { notCounting: [{ reason: 'incomplete', count: 2 }] }), woche('2026-11-09T00:00:00Z')]
    expect(juengsteZaehlendeWoche(wochen)).toBeNull()
    expect(juengsteZaehlendeWoche([])).toBeNull()
  })
})

describe('Wörterbuch LEITSTAND_CODEANALYSE', () => {
  it('trägt auf Deutsch und Englisch dieselben Schlüssel', () => {
    expect(Object.keys(en.LEITSTAND_CODEANALYSE).sort()).toEqual(Object.keys(de.LEITSTAND_CODEANALYSE).sort())
    expect(Object.keys(en.LEITSTAND_CODEANALYSE.GRUENDE).sort()).toEqual(Object.keys(de.LEITSTAND_CODEANALYSE.GRUENDE).sort())
  })

  it('nennt Titel und Leerzustände', () => {
    expect(de.LEITSTAND_CODEANALYSE.TITEL).toBe('Codeanalyse')
    expect(en.LEITSTAND_CODEANALYSE.TITEL).toBe('Code analysis')
    expect(de.LEITSTAND_CODEANALYSE.LEER).toBe('noch keine Messung')
    expect(en.LEITSTAND_CODEANALYSE.LEER).toBe('no measurement yet')
    expect(de.LEITSTAND_CODEANALYSE.KEINE_DICHTE).toBe('keine Dichte (0 Zeilen)')
    expect(en.LEITSTAND_CODEANALYSE.KEINE_DICHTE).toBe('no density (0 lines)')
    expect(de.LEITSTAND_CODEANALYSE.RUECKGANG).toBe('Rückgang der Funde')
    expect(en.LEITSTAND_CODEANALYSE.RUECKGANG).toBe('Reduction in findings')
  })

  it('liest die Wochen der Leiste vor, mit Lücke', () => {
    expect(de.LEITSTAND_CODEANALYSE.WOCHE_ARIA(46, '4,0', '1,0')).toBe('KW 46: vor der Codeanalyse 4,0, Endzustand 1,0 Funde je 1.000 Zeilen')
    expect(en.LEITSTAND_CODEANALYSE.WOCHE_ARIA(46, '4.0', '1.0')).toBe('CW 46: before the code analysis 4.0, final state 1.0 findings per 1,000 lines')
    expect(de.LEITSTAND_CODEANALYSE.WOCHE_LUECKE_ARIA(47)).toBe('KW 47: keine zählende Messung')
    expect(en.LEITSTAND_CODEANALYSE.WOCHE_LUECKE_ARIA(47)).toBe('CW 47: no counting measurement')
    expect(de.LEITSTAND_CODEANALYSE.WOCHEN_ARIA('KW 46: …')).toBe('Befunddichte je Kalenderwoche — KW 46: …')
    expect(en.LEITSTAND_CODEANALYSE.WOCHEN_ARIA('CW 46: …')).toBe('Finding density per calendar week — CW 46: …')
  })
})
