import { describe, expect, it } from 'vitest'
import type { NightRunModelRow, NightRunModels } from '../api/nightRuns'
import { de } from '../i18n/de'
import { en } from '../i18n/en'
import { formatierer } from '../i18n/format'
import { denktiefe, erfolgErklaerung, kopfText, kostenText, modellzeitText, quoteText, teilmenge, zeilenName } from './modelle'

/** Reine Hilfen der Platte „Modelle“ (Issue #1949, Plan #1905 E10–E12), in beiden Sprachen. */

const datum = (iso: string) => `D(${iso.slice(0, 10)})`
const T = { de: de.LEITSTAND, en: en.LEITSTAND }

const daten = (teil: Partial<NightRunModels> = {}): NightRunModels => ({
  dataSince: '2026-08-01T22:00:00Z',
  lastTwelveWeeks: true,
  withoutModel: 0,
  attemptsInPeriod: 5,
  stages: [],
  ...teil,
})

const zeile = (teil: Partial<NightRunModelRow> = {}): NightRunModelRow => ({
  model: 'claude-opus-5-5',
  effort: null,
  attempts: 5,
  succeeded: 4,
  enoughData: true,
  costMedianUsd: 1.25,
  costFrom: 5,
  modelTimeMedianMs: 95_000,
  modelTimeFrom: 5,
  ...teil,
})

describe('kopfText', () => {
  it('nennt die zwölf Wochen, wenn die Daten im ältesten Fenster beginnen', () => {
    expect(kopfText(T.de, daten(), datum)).toBe('letzte 12 Wochen')
    expect(kopfText(T.en, daten(), datum)).toBe('last 12 weeks')
  })

  it('nennt den Datenbeginn, wenn er später liegt', () => {
    expect(kopfText(T.de, daten({ lastTwelveWeeks: false }), datum)).toBe('seit D(2026-08-01)')
    expect(kopfText(T.en, daten({ lastTwelveWeeks: false }), datum)).toBe('since D(2026-08-01)')
  })

  it('bleibt ohne Datenbeginn bei den zwölf Wochen', () => {
    expect(kopfText(T.de, daten({ dataSince: null, lastTwelveWeeks: false }), datum)).toBe('letzte 12 Wochen')
  })

  it('nennt die Vorgänge ohne Modellangabe, einzeln und in der Mehrzahl', () => {
    expect(kopfText(T.de, daten({ withoutModel: 3 }), datum)).toBe('letzte 12 Wochen · 3 Vorgänge ohne Modellangabe nicht gezählt')
    expect(kopfText(T.de, daten({ withoutModel: 1 }), datum)).toBe('letzte 12 Wochen · 1 Vorgang ohne Modellangabe nicht gezählt')
    expect(kopfText(T.en, daten({ withoutModel: 3 }), datum)).toBe('last 12 weeks · 3 attempts without a reported model not counted')
    expect(kopfText(T.en, daten({ withoutModel: 1 }), datum)).toBe('last 12 weeks · 1 attempt without a reported model not counted')
  })
})

describe('zeilenName und denktiefe', () => {
  it('lässt den Modellnamen wie gemeldet und hängt nichts an ohne Denktiefe', () => {
    expect(zeilenName(T.de, zeile({ model: 'gpt-astra' }))).toBe('gpt-astra')
  })

  it('übersetzt die bekannten Denktiefen', () => {
    expect(zeilenName(T.de, zeile({ effort: 'medium' }))).toBe('claude-opus-5-5 · mittel')
    expect(zeilenName(T.en, zeile({ effort: 'medium' }))).toBe('claude-opus-5-5 · medium')
    expect(['low', 'medium', 'high', 'xhigh', 'max'].map((e) => denktiefe(T.de, e))).toEqual(['niedrig', 'mittel', 'hoch', 'sehr hoch', 'maximal'])
    expect(['low', 'medium', 'high', 'xhigh', 'max'].map((e) => denktiefe(T.en, e))).toEqual(['low', 'medium', 'high', 'extra high', 'max'])
  })

  it('lässt eine unbekannte Denktiefe wie gemeldet', () => {
    expect(zeilenName(T.de, zeile({ effort: 'turbo' }))).toBe('claude-opus-5-5 · turbo')
    expect(denktiefe(T.en, 'toString')).toBe('toString')
  })
})

describe('quoteText', () => {
  it('nennt die Erfolgsquote ab genug Daten', () => {
    expect(quoteText(T.de, zeile())).toBe('4 von 5 erfolgreich')
    expect(quoteText(T.en, zeile())).toBe('4 of 5 successful')
  })

  it('sagt „zu wenig Daten“, wenn das Backend zu wenige Vorgänge meldet', () => {
    expect(quoteText(T.de, zeile({ attempts: 3, succeeded: 3, enoughData: false }))).toBe('zu wenig Daten')
    expect(quoteText(T.en, zeile({ attempts: 3, succeeded: 3, enoughData: false }))).toBe('too little data')
  })
})

describe('erfolgErklaerung', () => {
  it('erklärt je Stufe, was erfolgreich heißt', () => {
    expect(erfolgErklaerung(T.de, 'REVIEW')).toContain('Die Prüfung ist gelungen')
    expect(erfolgErklaerung(T.de, 'UMSETZUNG')).toContain('nicht aus In Progress zurückgezogen')
    expect(erfolgErklaerung(T.en, 'UMSETZUNG')).toContain('not pulled back from In Progress')
    expect(erfolgErklaerung(T.en, 'PLAN')).toContain('the plan succeeded')
    const stufen = ['PLAN', 'REVIEW', 'PAKETE', 'ABDECKUNG', 'UMSETZUNG'] as const
    expect(new Set(stufen.map((s) => erfolgErklaerung(T.de, s))).size).toBe(5)
    expect(new Set(stufen.map((s) => erfolgErklaerung(T.en, s))).size).toBe(5)
  })
})

describe('teilmenge', () => {
  it('steht ohne Zusatz, wenn alle Vorgänge den Wert tragen', () => {
    expect(teilmenge(T.de, '1,25 $', 12, 12)).toBe('1,25 $')
  })

  it('nennt die Teilmenge, wenn weniger Vorgänge den Wert tragen', () => {
    expect(teilmenge(T.de, '1,25 $', 9, 12)).toBe('1,25 $ (aus 9 von 12)')
    expect(teilmenge(T.en, '$1.25', 9, 12)).toBe('$1.25 (from 9 of 12)')
  })

  it('sagt „nicht gemessen“, wenn kein Vorgang den Wert trägt', () => {
    expect(teilmenge(T.de, null, 0, 12)).toBe('nicht gemessen')
    expect(teilmenge(T.en, null, 0, 12)).toBe('not measured')
  })
})

describe('kostenText und modellzeitText', () => {
  const f = { de: formatierer('de'), en: formatierer('en') }

  it('formatiert Kosten als Dollar und die Modellzeit als Dauer', () => {
    expect(kostenText(T.en, f.en, zeile())).toBe('$1.25')
    expect(modellzeitText(T.de, f.de, zeile())).toBe(f.de.dauer(95))
    expect(modellzeitText(T.en, f.en, zeile({ modelTimeMedianMs: 30_000 }))).toBe('30 s')
  })

  it('nennt Teilmenge und fehlende Messung', () => {
    expect(kostenText(T.en, f.en, zeile({ attempts: 12, costFrom: 9 }))).toBe('$1.25 (from 9 of 12)')
    expect(kostenText(T.de, f.de, zeile({ costMedianUsd: null, costFrom: 0 }))).toBe('nicht gemessen')
    expect(modellzeitText(T.de, f.de, zeile({ modelTimeMedianMs: null, modelTimeFrom: 0 }))).toBe('nicht gemessen')
    expect(modellzeitText(T.en, f.en, zeile({ attempts: 12, modelTimeMedianMs: 30_000, modelTimeFrom: 9 }))).toBe('30 s (from 9 of 12)')
  })
})
