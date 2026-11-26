import { act, fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it, test } from 'vitest'
import type { CodeAnalysisState, CodeAnalysisView, CodeAnalysisWeek } from '../../api/dashboard'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { CodeanalysePlatte } from './CodeanalysePlatte'

/** Die Platte „Codeanalyse“ (Issue #2280, Plan #2272 E9, E10, E11, E15). */

/** Zwölf Kalenderwochen ab Montag, 7. September 2026 (KW 37) bis Montag, 23. November 2026 (KW 48). */
const ERSTE_WOCHE = Date.parse('2026-09-07T00:00:00Z')
const WOCHE_MS = 7 * 24 * 3600 * 1000

const zustand = (teile: Partial<CodeAnalysisState>): CodeAnalysisState => ({
  bugs: 0,
  vulnerabilities: 0,
  codeSmells: 0,
  findings: 0,
  changedLines: 0,
  bugsDensity: null,
  vulnerabilitiesDensity: null,
  codeSmellsDensity: null,
  findingsDensity: null,
  ...teile,
})

const leereWoche = (i: number): CodeAnalysisWeek => ({
  weekStart: new Date(ERSTE_WOCHE + i * WOCHE_MS).toISOString(),
  countingMeasurements: 0,
  cards: null,
  before: null,
  final: null,
  reductionPercent: null,
  notCounting: [],
})

/** KW 43: zählend, aber ohne geänderte Zeilen — Funde und Zeilen ja, Dichte nein. */
const OHNE_ZEILEN = 6
/** KW 45: die Funde steigen, alle sechs Gründe und ein unbekannter. */
const ANSTIEG = 8
/** KW 47: die jüngste zählende Woche. */
const JUENGSTE = 10
/** KW 48: nur eine nicht zählende Messung. */
const LUECKE = 11

const wochen = (): CodeAnalysisWeek[] => {
  const w = Array.from({ length: 12 }, (_, i) => leereWoche(i))
  w[OHNE_ZEILEN] = {
    ...w[OHNE_ZEILEN],
    countingMeasurements: 1,
    cards: 1,
    before: zustand({ bugs: 2, findings: 2 }),
    final: zustand({ bugs: 1, findings: 1 }),
    reductionPercent: 50,
  }
  w[ANSTIEG] = {
    ...w[ANSTIEG],
    countingMeasurements: 1,
    cards: 2,
    before: zustand({ codeSmells: 4, findings: 4, changedLines: 200, bugsDensity: 0, vulnerabilitiesDensity: 0, codeSmellsDensity: 20, findingsDensity: 20 }),
    final: zustand({ codeSmells: 5, findings: 5, changedLines: 200, bugsDensity: 0, vulnerabilitiesDensity: 0, codeSmellsDensity: 25, findingsDensity: 25 }),
    reductionPercent: -25,
    notCounting: [
      { reason: 'no-measurement', count: 1 },
      { reason: 'missing-before', count: 2 },
      { reason: 'missing-final', count: 3 },
      { reason: 'cards-removed', count: 4 },
      { reason: 'incomplete', count: 5 },
      { reason: 'not-attributable', count: 6 },
      { reason: 'kaputt-neu', count: 7 },
    ],
  }
  w[JUENGSTE] = {
    ...w[JUENGSTE],
    countingMeasurements: 2,
    cards: 5,
    before: zustand({
      bugs: 3,
      vulnerabilities: 1,
      codeSmells: 8,
      findings: 12,
      changedLines: 400,
      bugsDensity: 7.5,
      vulnerabilitiesDensity: 2.5,
      codeSmellsDensity: 20,
      findingsDensity: 30,
    }),
    final: zustand({
      bugs: 1,
      codeSmells: 2,
      findings: 3,
      changedLines: 400,
      bugsDensity: 2.5,
      vulnerabilitiesDensity: 0,
      codeSmellsDensity: 5,
      findingsDensity: 7.5,
    }),
    reductionPercent: 75,
    notCounting: [{ reason: 'no-measurement', count: 1 }],
  }
  w[LUECKE] = { ...w[LUECKE], notCounting: [{ reason: 'missing-final', count: 1 }] }
  return w
}

const daten = (teile: Partial<CodeAnalysisView> = {}): CodeAnalysisView => ({
  hasMeasurements: true,
  dataSince: null,
  weeks: wochen(),
  ...teile,
})

const platte = (name = 'Codeanalyse') => screen.getByRole('region', { name })
const kopf = () => screen.getByTestId('codeanalyse-kopf')
const bereich = () => screen.getByTestId('codeanalyse-woche')
const woche = (name: string | RegExp) => screen.getByRole('button', { name })

describe('CodeanalysePlatte', () => {
  test('Szenario: Ein Projekt ohne Messung', () => {
    renderMitSprache(<CodeanalysePlatte daten={{ hasMeasurements: false, dataSince: null, weeks: [] }} />)
    const p = platte()
    expect(within(p).getByTestId('codeanalyse-leer')).toHaveTextContent('noch keine Messung')
    expect(within(p).queryByRole('button')).not.toBeInTheDocument()
    expect(within(p).queryByTestId('codeanalyse-kopf')).not.toBeInTheDocument()
    expect(p.textContent).not.toMatch(/\d/)
    expect(p.textContent).not.toMatch(/Dichte/)
  })

  it('zeigt ohne Messung auf Englisch den Satz statt Zahlen', () => {
    renderMitSprache(<CodeanalysePlatte daten={{ hasMeasurements: false, dataSince: null, weeks: [] }} />, 'en')
    const p = platte('Code analysis')
    expect(within(p).getByTestId('codeanalyse-leer')).toHaveTextContent('no measurement yet')
    expect(p.textContent).not.toMatch(/\d/)
  })

  it('nennt im Kopf die Kalenderwochen der letzten zwölf Wochen', () => {
    renderMitSprache(<CodeanalysePlatte daten={daten()} />)
    expect(platte()).toHaveTextContent('Kalenderwochen, letzte 12 Wochen')
  })

  it('nennt „seit“ mit dem Datum in UTC, auf Deutsch und Englisch (AK 7, E11)', () => {
    const { unmount } = renderMitSprache(<CodeanalysePlatte daten={daten({ dataSince: '2026-10-14T23:30:00Z' })} />)
    expect(platte()).toHaveTextContent('Kalenderwochen, seit 14.10.2026')
    unmount()
    renderMitSprache(<CodeanalysePlatte daten={daten({ dataSince: '2026-10-14T23:30:00Z' })} />, 'en')
    expect(platte('Code analysis')).toHaveTextContent('Calendar weeks, since Oct 14, 2026')
  })

  it('zeigt oben die Zahlen der jüngsten zählenden Woche (AK 14)', () => {
    renderMitSprache(<CodeanalysePlatte daten={daten()} />)
    const k = kopf()
    expect(within(k).getByRole('heading', { name: 'Jüngste Woche mit zählenden Messungen: KW 47' })).toBeInTheDocument()
    const zeile = (art: string) => within(k).getByRole('row', { name: new RegExp(`^${art}`) })
    expect(zeile('Fehler')).toHaveTextContent(/7,5.*Funde 3.*Zeilen 400.*2,5.*Funde 1.*Zeilen 400/)
    expect(zeile('Sicherheitslücken')).toHaveTextContent(/2,5.*Funde 1.*0,0.*Funde 0/)
    expect(zeile('Wartbarkeitsmängel')).toHaveTextContent(/20,0.*Funde 8.*5,0.*Funde 2/)
    expect(zeile('Summe')).toHaveTextContent(/30,0.*Funde 12.*Zeilen 400.*7,5.*Funde 3.*Zeilen 400/)
    expect(within(k).getByRole('columnheader', { name: 'vor der Codeanalyse' })).toBeInTheDocument()
    expect(within(k).getByRole('columnheader', { name: 'Endzustand' })).toBeInTheDocument()
    expect(k).toHaveTextContent('Funde je 1.000 Zeilen')
    expect(angabe(k, 'Messungen')).toBe('2')
    expect(angabe(k, 'Karten')).toBe('5')
    expect(angabe(k, 'Rückgang der Funde')).toBe('75 %')
    // Die nicht zählenden Messungen stehen erst im Bereich der gewählten Woche.
    expect(k).not.toHaveTextContent('nicht zählende Messungen')
  })

  it('zeigt die Kopfzahlen auf Englisch', () => {
    renderMitSprache(<CodeanalysePlatte daten={daten()} />, 'en')
    const k = kopf()
    expect(within(k).getByRole('heading', { name: 'Latest week with counting measurements: CW 47' })).toBeInTheDocument()
    expect(within(k).getByRole('row', { name: /^Total/ })).toHaveTextContent(/30\.0.*Findings 12.*Lines 400.*7\.5.*Findings 3/)
    expect(within(k).getByRole('columnheader', { name: 'before the code analysis' })).toBeInTheDocument()
    expect(angabe(k, 'Measurements')).toBe('2')
    expect(angabe(k, 'Cards')).toBe('5')
    expect(angabe(k, 'Reduction in findings')).toBe('75 %')
  })

  it('lässt die Kopfzahlen stehen, wenn eine ältere Woche gewählt ist (E10)', () => {
    renderMitSprache(<CodeanalysePlatte daten={daten()} />)
    fireEvent.click(woche(/^KW 45:/))
    expect(within(kopf()).getByRole('heading')).toHaveTextContent('KW 47')
    expect(within(kopf()).getByRole('row', { name: /^Summe/ })).toHaveTextContent(/30,0/)
    expect(within(bereich()).getByRole('heading')).toHaveTextContent('KW 45')
  })

  it('zeigt oben einen Satz statt Zahlen, wenn im Fenster keine Messung zählt', () => {
    const nurNichtZaehlend = Array.from({ length: 12 }, (_, i) => leereWoche(i))
    nurNichtZaehlend[3] = { ...nurNichtZaehlend[3], notCounting: [{ reason: 'incomplete', count: 2 }] }
    const { unmount } = renderMitSprache(<CodeanalysePlatte daten={daten({ weeks: nurNichtZaehlend })} />)
    expect(kopf()).toHaveTextContent('In diesem Zeitraum zählt keine Messung.')
    expect(kopf().textContent).not.toMatch(/\d/)
    expect(within(kopf()).queryByRole('table')).not.toBeInTheDocument()
    unmount()
    renderMitSprache(<CodeanalysePlatte daten={daten({ weeks: nurNichtZaehlend })} />, 'en')
    expect(kopf()).toHaveTextContent('No measurement counts in this period.')
  })

  it('zeigt ohne Wahl nur den Hinweis, wie eine Woche gewählt wird', () => {
    renderMitSprache(<CodeanalysePlatte daten={daten()} />)
    expect(bereich()).toHaveTextContent('Eine Woche überfahren, antippen oder mit der Tastatur wählen, um alle ihre Zahlen zu sehen.')
    expect(within(bereich()).queryByRole('table')).not.toBeInTheDocument()
  })

  it.each([
    ['Überfahren', (k: HTMLElement) => fireEvent.mouseEnter(k)],
    ['Tastaturfokus', (k: HTMLElement) => act(() => k.focus())],
    ['Antippen', (k: HTMLElement) => fireEvent.click(k)],
  ])('wählt eine Woche durch %s', (_weg, waehlen) => {
    renderMitSprache(<CodeanalysePlatte daten={daten()} />)
    const k = woche(/^KW 45:/)
    waehlen(k)
    expect(k).toHaveAttribute('aria-pressed', 'true')
    expect(within(bereich()).getByRole('heading')).toHaveTextContent('KW 45')
    expect(woche(/^KW 47:/)).toHaveAttribute('aria-pressed', 'false')
  })

  it('zeigt im Bereich der gewählten Woche alle Zahlen samt nicht zählenden Messungen und Gründen (AK 3)', () => {
    renderMitSprache(<CodeanalysePlatte daten={daten()} />)
    fireEvent.click(woche(/^KW 45:/))
    const b = bereich()
    expect(within(b).getByRole('row', { name: /^Summe/ })).toHaveTextContent(/20,0.*Funde 4.*Zeilen 200.*25,0.*Funde 5.*Zeilen 200/)
    expect(within(b).getByRole('row', { name: /^Wartbarkeitsmängel/ })).toHaveTextContent(/20,0.*Funde 4.*25,0.*Funde 5/)
    expect(angabe(b, 'Messungen')).toBe('1')
    expect(angabe(b, 'Karten')).toBe('2')
    expect(angabe(b, 'Rückgang der Funde')).toBe('−25 %')
    const gruende = within(b).getByRole('list', { name: 'nicht zählende Messungen' })
    expect(within(gruende).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
      'keine Messung der Codeanalyse: 1',
      'Urteil vor der Codeanalyse fehlt: 2',
      'Urteil über den veröffentlichten Stand fehlt: 3',
      'Karten vor dem Veröffentlichen herausgenommen: 4',
      'Messung unvollständig: 5',
      'Funde nicht zuzuordnen: 6',
      'kaputt-neu: 7',
    ])
  })

  it('zeigt die Gründe und den negativen Rückgang auf Englisch', () => {
    renderMitSprache(<CodeanalysePlatte daten={daten()} />, 'en')
    fireEvent.click(woche(/^CW 45:/))
    const b = bereich()
    expect(angabe(b, 'Reduction in findings')).toBe('−25 %')
    const gruende = within(b).getByRole('list', { name: 'measurements not counted' })
    expect(within(gruende).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
      'no code analysis measurement: 1',
      'verdict before the code analysis missing: 2',
      'verdict on the published state missing: 3',
      'cards removed before publishing: 4',
      'measurement incomplete: 5',
      'findings cannot be attributed: 6',
      'kaputt-neu: 7',
    ])
  })

  it('zeigt eine Woche mit Messungen ohne Zeilen mit Hinweis und ohne Dichte (AK 2)', () => {
    renderMitSprache(<CodeanalysePlatte daten={daten()} />)
    const k = woche('KW 43: keine Dichte (0 Zeilen)')
    expect(k).toHaveTextContent('keine Dichte (0 Zeilen)')
    expect(within(k).getByTestId('grundlinie')).toBeInTheDocument()
    expect(within(k).queryByTestId(/^codeanalyse-saeule-/)).not.toBeInTheDocument()
    fireEvent.click(k)
    const summe = within(bereich()).getByRole('row', { name: /^Summe/ })
    expect(summe).toHaveTextContent(/keine Dichte \(0 Zeilen\).*Funde 2.*Zeilen 0.*keine Dichte \(0 Zeilen\).*Funde 1.*Zeilen 0/)
    expect(summe.textContent).not.toMatch(/\d,\d/)
  })

  it('zeigt die Woche mit Messungen ohne Zeilen auf Englisch', () => {
    renderMitSprache(<CodeanalysePlatte daten={daten()} />, 'en')
    expect(woche('CW 43: no density (0 lines)')).toHaveTextContent('no density (0 lines)')
  })

  it('zeigt in einer Woche ohne zählende Messung weder Karten noch Messungen als Zahl (AK 6)', () => {
    renderMitSprache(<CodeanalysePlatte daten={daten()} />)
    const k = woche('KW 48: keine zählende Messung')
    expect(within(k).getByTestId('grundlinie')).toBeInTheDocument()
    expect(k).not.toHaveTextContent('keine Dichte')
    fireEvent.click(k)
    const b = bereich()
    expect(b).toHaveTextContent('KW 48: keine zählende Messung')
    expect(within(b).queryByRole('table')).not.toBeInTheDocument()
    expect(within(b).queryByText('Karten')).not.toBeInTheDocument()
    expect(within(b).queryByText('Messungen')).not.toBeInTheDocument()
    expect(b.textContent).not.toMatch(/\b0\b/)
    const gruende = within(b).getByRole('list', { name: 'nicht zählende Messungen' })
    expect(within(gruende).getByRole('listitem')).toHaveTextContent('Urteil über den veröffentlichten Stand fehlt: 1')
  })

  it('zeigt eine leere Woche ohne Liste der Gründe', () => {
    renderMitSprache(<CodeanalysePlatte daten={daten()} />)
    fireEvent.click(woche('KW 37: keine zählende Messung'))
    expect(within(bereich()).queryByRole('list')).not.toBeInTheDocument()
  })

  it('zeigt je Woche mit Dichte zwei Säulen und erklärt sie in einer Legende', () => {
    renderMitSprache(<CodeanalysePlatte daten={daten()} />)
    const k = woche(/^KW 47:/)
    expect(within(k).getByTestId('codeanalyse-saeule-vorher-100')).toBeInTheDocument()
    expect(within(k).getByTestId('codeanalyse-saeule-endzustand-25')).toBeInTheDocument()
    expect(within(woche(/^KW 45:/)).getByTestId('codeanalyse-saeule-vorher-67')).toBeInTheDocument()
    expect(screen.getByTestId('codeanalyse-legende')).toHaveTextContent(/vor der Codeanalyse.*Endzustand/)
    expect(screen.getByTestId('wochen-leiste')).toHaveTextContent(/^37.*48$/)
  })

  it('zeigt eine Säule mit Dichte null als Grundlinie neben der anderen', () => {
    const w = wochen()
    w[JUENGSTE] = { ...w[JUENGSTE], final: zustand({ changedLines: 400, findingsDensity: 0 }) }
    renderMitSprache(<CodeanalysePlatte daten={daten({ weeks: w })} />)
    const k = woche(/^KW 47:/)
    expect(within(k).getByTestId('codeanalyse-saeule-vorher-100')).toBeInTheDocument()
    expect(within(k).queryByTestId(/^codeanalyse-saeule-endzustand/)).not.toBeInTheDocument()
    expect(within(k).getByTestId('grundlinie')).toBeInTheDocument()
  })

  it.each([
    ['ohne Zeilen im Endzustand', 'final', 'KW 47: vor der Codeanalyse 30,0, Endzustand keine Dichte (0 Zeilen) Funde je 1.000 Zeilen'],
    ['ohne Zeilen vor der Codeanalyse', 'before', 'KW 47: vor der Codeanalyse keine Dichte (0 Zeilen), Endzustand 7,5 Funde je 1.000 Zeilen'],
  ] as const)('nennt eine Woche %s mit dem Hinweis an der fehlenden Dichte', (_fall, zustandOhneZeilen, name) => {
    const w = wochen()
    w[JUENGSTE] = { ...w[JUENGSTE], [zustandOhneZeilen]: zustand({ bugs: 1, findings: 1 }) }
    renderMitSprache(<CodeanalysePlatte daten={daten({ weeks: w })} />)
    expect(screen.getAllByRole('button')[JUENGSTE]).toHaveAccessibleName(name)
  })

  it('lässt den Rückgang weg, wenn es vorher keinen Fund gab (AK 4)', () => {
    const w = wochen()
    w[JUENGSTE] = { ...w[JUENGSTE], reductionPercent: null }
    renderMitSprache(<CodeanalysePlatte daten={daten({ weeks: w })} />)
    expect(within(kopf()).queryByText('Rückgang der Funde')).not.toBeInTheDocument()
    expect(within(kopf()).getByRole('row', { name: /^Summe/ })).toHaveTextContent(/Funde 12.*Funde 3/)
  })

  describe('Zugänglichkeit', () => {
    it('macht jede Woche als Schaltfläche mit Namen erreichbar', () => {
      renderMitSprache(<CodeanalysePlatte daten={daten()} />)
      const knoepfe = within(screen.getByRole('group')).getAllByRole('button')
      expect(knoepfe).toHaveLength(12)
      expect(knoepfe[0]).toHaveAccessibleName('KW 37: keine zählende Messung')
      expect(knoepfe[JUENGSTE]).toHaveAccessibleName('KW 47: vor der Codeanalyse 30,0, Endzustand 7,5 Funde je 1.000 Zeilen')
      expect(knoepfe[OHNE_ZEILEN]).toHaveAccessibleName('KW 43: keine Dichte (0 Zeilen)')
      expect(screen.getByRole('group')).toHaveAccessibleName(/^Befunddichte je Kalenderwoche — KW 37: keine zählende Messung; /)
    })

    it('nennt die Wochen auf Englisch', () => {
      renderMitSprache(<CodeanalysePlatte daten={daten()} />, 'en')
      expect(woche(/^CW 47:/)).toHaveAccessibleName('CW 47: before the code analysis 30.0, final state 7.5 findings per 1,000 lines')
    })

    it('ordnet den Bereich der gewählten Woche ihr zu', () => {
      renderMitSprache(<CodeanalysePlatte daten={daten()} />)
      const b = bereich()
      for (const k of screen.getAllByRole('button')) {
        expect(k).toHaveAttribute('aria-controls', b.id)
      }
      const k = woche(/^KW 45:/)
      fireEvent.click(k)
      expect(screen.getByRole('region', { name: k.getAttribute('aria-label')! })).toBe(b)
    })
  })
})

/** Der Wert zu einem Begriff in der Angabenliste unter einer Woche. */
function angabe(behaelter: HTMLElement, begriff: string): string | null {
  const dt = within(behaelter).getByText(begriff, { selector: 'dt' })
  return dt.nextElementSibling?.textContent ?? null
}
