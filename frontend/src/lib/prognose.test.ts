import { describe, expect, it } from 'vitest'
import type { EpicForecastEntry } from '../api/dashboard'
import { datumText, dokumentListe, hinweisText, lokalesDatum, paketZahl, zeilenText } from './prognose'

/** Reine Textlogik der Prognosezeile in der Platte „Vorhaben“ (Issue #1828, Plan #1819 A7, E7, E9, E13). */

const vorhaben = (teil: Partial<EpicForecastEntry> = {}): EpicForecastEntry => ({
  id: 42,
  number: 1782,
  title: 'Leitstand-Prognose',
  state: 'FORECAST',
  total: 14,
  finished: 2,
  open: 12,
  p50: '2026-10-21',
  p85: '2026-10-24',
  earliest: false,
  pendingDocuments: [],
  otherEpicsSharing: 0,
  ...teil,
})

const ausstehend = [
  { number: 1819, title: '[Plan] Leitstand-Prognose' },
  { number: 1790, title: '[Requirement] Prognose je Vorhaben' },
]

describe('zeilenText — Deutsch', () => {
  it('nennt bei FORECAST beide Daten mit „bis“ (AK 1)', () => {
    expect(zeilenText('de', vorhaben())).toBe('voraussichtlich fertig: 50 % bis 21.10.2026 · 85 % bis 24.10.2026')
  })

  it('schreibt bei ausstehenden Dokumenten „frühestens“ statt „bis“ (AK 6)', () => {
    expect(zeilenText('de', vorhaben({ earliest: true, pendingDocuments: ausstehend }))).toBe(
      'voraussichtlich fertig: 50 % frühestens 21.10.2026 · 85 % frühestens 24.10.2026',
    )
  })

  it('meldet bei INSUFFICIENT_THROUGHPUT zu wenig Durchsatz (AK 5)', () => {
    expect(zeilenText('de', vorhaben({ state: 'INSUFFICIENT_THROUGHPUT', p50: null, p85: null }))).toBe(
      'Zu wenig Durchsatz für eine Prognose',
    )
  })

  it('meldet bei ALL_DONE ohne ausstehendes Dokument „alle Pakete umgesetzt“ (AK 12)', () => {
    expect(zeilenText('de', vorhaben({ state: 'ALL_DONE', open: 0, p50: null, p85: null }))).toBe(
      'alle Pakete umgesetzt',
    )
  })

  it('nennt bei ALL_DONE die ausstehenden Dokumente, aus denen weitere folgen (AK 12, E7)', () => {
    expect(
      zeilenText('de', vorhaben({ state: 'ALL_DONE', open: 0, p50: null, p85: null, pendingDocuments: ausstehend })),
    ).toBe(
      'alle Pakete umgesetzt, weitere folgen aus #1790 [Requirement] Prognose je Vorhaben, #1819 [Plan] Leitstand-Prognose',
    )
  })

  it('meldet bei NO_PACKAGES „noch keine Pakete geschnitten“', () => {
    expect(zeilenText('de', vorhaben({ state: 'NO_PACKAGES', total: 0, finished: 0, open: 0, p50: null, p85: null }))).toBe(
      'noch keine Pakete geschnitten',
    )
  })
})

describe('zeilenText — Englisch', () => {
  it('nennt bei FORECAST beide Daten mit „by“', () => {
    expect(zeilenText('en', vorhaben())).toBe('expected done: 50% by Oct 21, 2026 · 85% by Oct 24, 2026')
  })

  it('schreibt bei ausstehenden Dokumenten „not before“', () => {
    expect(zeilenText('en', vorhaben({ earliest: true, pendingDocuments: ausstehend }))).toBe(
      'expected done: 50% not before Oct 21, 2026 · 85% not before Oct 24, 2026',
    )
  })

  it('meldet zu wenig Durchsatz', () => {
    expect(zeilenText('en', vorhaben({ state: 'INSUFFICIENT_THROUGHPUT', p50: null, p85: null }))).toBe(
      'Not enough throughput for a forecast',
    )
  })

  it('meldet alle Pakete umgesetzt, mit und ohne ausstehendes Dokument', () => {
    expect(zeilenText('en', vorhaben({ state: 'ALL_DONE', open: 0, p50: null, p85: null }))).toBe(
      'all packages implemented',
    )
    expect(
      zeilenText('en', vorhaben({ state: 'ALL_DONE', open: 0, p50: null, p85: null, pendingDocuments: [ausstehend[0]] })),
    ).toBe('all packages implemented, more to follow from #1819 [Plan] Leitstand-Prognose')
  })

  it('meldet noch keine Pakete', () => {
    expect(zeilenText('en', vorhaben({ state: 'NO_PACKAGES', total: 0, finished: 0, open: 0, p50: null, p85: null }))).toBe(
      'no packages cut yet',
    )
  })
})

describe('paketZahl (E9)', () => {
  it('schreibt die Einzahl bei genau einem offenen Paket', () => {
    expect(paketZahl('de', 1)).toBe('1 offenes Paket')
    expect(paketZahl('en', 1)).toBe('1 open package')
  })

  it('schreibt sonst die Mehrzahl', () => {
    expect(paketZahl('de', 12)).toBe('12 offene Pakete')
    expect(paketZahl('de', 0)).toBe('0 offene Pakete')
    expect(paketZahl('en', 12)).toBe('12 open packages')
  })
})

describe('dokumentListe (E7)', () => {
  it('nennt die Dokumente aufsteigend nach Nummer als „#‹Nummer› ‹Titel›“, mit Komma getrennt', () => {
    expect(dokumentListe(ausstehend)).toBe('#1790 [Requirement] Prognose je Vorhaben, #1819 [Plan] Leitstand-Prognose')
  })

  it('lässt die übergebene Liste unverändert', () => {
    const liste = [...ausstehend]
    dokumentListe(liste)
    expect(liste).toEqual(ausstehend)
  })
})

describe('hinweisText — Deutsch', () => {
  it('erklärt Datum, Wahrscheinlichkeit, Zahl der offenen Pakete und 30 Tage von 12 bis 12 Uhr (AK 8, AK 7)', () => {
    expect(hinweisText('de', vorhaben())).toBe(
      'Mit 50 % Wahrscheinlichkeit sind bis 21.10.2026 alle 12 offenen Pakete umgesetzt, mit 85 % bis 24.10.2026, ' +
        'gerechnet aus dem Durchsatz der letzten 30 Tage, jeweils von 12 bis 12 Uhr. ' +
        'Gilt, wenn das Board nur an diesem Vorhaben arbeitet.',
    )
  })

  it('spricht bei einem offenen Paket von dem einen Paket', () => {
    expect(hinweisText('de', vorhaben({ open: 1 }))).toContain('bis 21.10.2026 das offene Paket umgesetzt')
  })

  it('ergänzt die weiteren Vorhaben, die sich den Durchsatz teilen, nur bei mehr als null (AK 7)', () => {
    expect(hinweisText('de', vorhaben({ otherEpicsSharing: 2 }))).toBe(
      'Mit 50 % Wahrscheinlichkeit sind bis 21.10.2026 alle 12 offenen Pakete umgesetzt, mit 85 % bis 24.10.2026, ' +
        'gerechnet aus dem Durchsatz der letzten 30 Tage, jeweils von 12 bis 12 Uhr. ' +
        'Gilt, wenn das Board nur an diesem Vorhaben arbeitet. 2 weitere Vorhaben teilen sich den Durchsatz.',
    )
    expect(hinweisText('de', vorhaben({ otherEpicsSharing: 0 }))).not.toContain('teilen sich')
  })

  it('nennt die ausstehenden Dokumente bei „frühestens“ (AK 6, E7)', () => {
    expect(hinweisText('de', vorhaben({ earliest: true, pendingDocuments: ausstehend }))).toBe(
      'Mit 50 % Wahrscheinlichkeit sind frühestens 21.10.2026 alle 12 offenen Pakete umgesetzt, mit 85 % frühestens 24.10.2026, ' +
        'gerechnet aus dem Durchsatz der letzten 30 Tage, jeweils von 12 bis 12 Uhr. ' +
        'Gilt, wenn das Board nur an diesem Vorhaben arbeitet. ' +
        'Weitere Pakete folgen aus #1790 [Requirement] Prognose je Vorhaben, #1819 [Plan] Leitstand-Prognose.',
    )
  })

  it.each(['NO_PACKAGES', 'ALL_DONE', 'INSUFFICIENT_THROUGHPUT'] as const)('hat bei %s keinen Hinweis', (state) => {
    expect(hinweisText('de', vorhaben({ state, p50: null, p85: null }))).toBeNull()
  })

  it('hat ohne Daten keinen Hinweis, auch bei FORECAST', () => {
    expect(hinweisText('de', vorhaben({ p85: null }))).toBeNull()
    expect(zeilenText('de', vorhaben({ p50: null }))).toBe('Zu wenig Durchsatz für eine Prognose')
  })
})

describe('hinweisText — Englisch', () => {
  it('erklärt die Prognose, die Annahme und die weiteren Vorhaben', () => {
    expect(hinweisText('en', vorhaben({ otherEpicsSharing: 1 }))).toBe(
      'With 50% probability all 12 open packages are implemented by Oct 21, 2026, with 85% by Oct 24, 2026, ' +
        'calculated from the throughput of the last 30 days, each from noon to noon. ' +
        'Applies if the board works on this epic only. 1 other epic shares the throughput.',
    )
    expect(hinweisText('en', vorhaben({ otherEpicsSharing: 3 }))).toContain('3 other epics share the throughput.')
  })

  it('spricht bei einem offenen Paket von dem einen Paket und nennt ausstehende Dokumente', () => {
    expect(hinweisText('en', vorhaben({ open: 1, earliest: true, pendingDocuments: [ausstehend[0]] }))).toBe(
      'With 50% probability the open package is implemented not before Oct 21, 2026, with 85% not before Oct 24, 2026, ' +
        'calculated from the throughput of the last 30 days, each from noon to noon. ' +
        'Applies if the board works on this epic only. More packages follow from #1819 [Plan] Leitstand-Prognose.',
    )
  })
})

describe('lokales Datum (E13)', () => {
  it('baut aus JJJJ-MM-TT Mitternacht in der Zone des Lesers', () => {
    const d = lokalesDatum('2026-10-21')
    expect([d.getFullYear(), d.getMonth(), d.getDate(), d.getHours()]).toEqual([2026, 9, 21, 0])
  })

  it('zeigt in jeder Zone den Tag selbst und nicht den Vortag wie UTC-Mitternacht', () => {
    // Gegenprobe, zonenexplizit: `new Date('JJJJ-MM-TT')` ist UTC-Mitternacht und faellt westlich von UTC auf den Vortag.
    const utc = new Date('2026-10-21')
    expect(utc.toISOString()).toBe('2026-10-21T00:00:00.000Z')
    expect(new Intl.DateTimeFormat('en-US', { timeZone: 'America/New_York', day: 'numeric' }).format(utc)).toBe('20')
    // `lokalesDatum` dagegen ist lokale Mitternacht des Tages selbst — unabhaengig von der Zone des Prozesses.
    const d = lokalesDatum('2026-10-21')
    expect([d.getDate(), d.getHours(), d.getMinutes(), d.getSeconds(), d.getMilliseconds()]).toEqual([21, 0, 0, 0, 0])
    expect(d.getTime()).toBe(utc.getTime() + d.getTimezoneOffset() * 60_000)
    expect(datumText('de', '2026-10-21')).toBe('21.10.2026')
    expect(datumText('en', '2026-01-01')).toBe('Jan 1, 2026')
  })
})
