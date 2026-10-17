import { screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DeliveryView, DeliveryWeek } from '../../api/dashboard'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { AuslieferungPlatte } from './AuslieferungPlatte'

/** Die Platte „Auslieferung“ (Issue #1836, Plan #1831 E9, E10, E11, E15). */

const STUNDE = 3600
const JETZT = Date.parse('2026-10-17T12:00:00Z')

/** Zwölf rollende Wochenfenster bis jetzt, alle ohne Versuch. */
const leereWochen = (): DeliveryWeek[] =>
  Array.from({ length: 12 }, (_, i) => ({
    weekStart: new Date(JETZT - (12 - i) * 7 * 24 * STUNDE * 1000).toISOString(),
    successCount: 0,
    failureCount: 0,
  }))

const auslieferung = (teile: Partial<DeliveryView> = {}): DeliveryView => ({
  hasReports: true,
  dataSince: null,
  deploymentCount: 9,
  leadTimeMedianSeconds: 5 * STUNDE,
  changeFailurePercent: 25,
  timeToRestoreMedianSeconds: 2 * STUNDE,
  outageSince: null,
  weeks: leereWochen(),
  ...teile,
})

const kachel = (name: string) => screen.getByRole('article', { name })

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] })
  vi.setSystemTime(JETZT)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('AuslieferungPlatte', () => {
  it('Szenario: Ausfall läuft noch', () => {
    renderMitSprache(
      <AuslieferungPlatte
        daten={auslieferung({
          outageSince: new Date(JETZT - 3 * STUNDE * 1000).toISOString(),
          timeToRestoreMedianSeconds: null,
        })}
      />,
    )
    const ttr = kachel('Time to Restore')
    expect(within(ttr).getByTestId('auslieferung-ausfall')).toHaveTextContent('Ausfall läuft seit 3 Stunden')
    expect(within(ttr).getByTestId('kachel-wert')).toHaveTextContent('—')
    expect(ttr).toHaveTextContent('kein Ausfall')
  })

  it('Szenario: Projekt ohne bisherige Auslieferung', () => {
    renderMitSprache(<AuslieferungPlatte daten={auslieferung({ hasReports: false, deploymentCount: 0, leadTimeMedianSeconds: null, changeFailurePercent: null, timeToRestoreMedianSeconds: null })} />)
    const platte = screen.getByRole('region', { name: 'Auslieferung' })
    expect(within(platte).getByTestId('auslieferung-leer')).toHaveTextContent('noch keine Auslieferung')
    expect(within(platte).queryAllByRole('article')).toHaveLength(0)
    expect(within(platte).queryByTestId('wochen-leiste')).not.toBeInTheDocument()
    expect(platte.textContent).not.toMatch(/\d/)
  })

  it('zeigt vier Kacheln nebeneinander, jede mit ihrem Hinweis (AK 1, AK 7)', () => {
    renderMitSprache(<AuslieferungPlatte daten={auslieferung()} />)
    const kacheln = screen.getAllByRole('article')
    expect(kacheln.map((k) => k.getAttribute('aria-label'))).toEqual([
      'Deployment-Frequenz',
      'Lead Time for Changes',
      'Change Failure Rate',
      'Time to Restore',
    ])
    for (const k of kacheln) {
      expect(within(k).getAllByTestId('auslieferung-hinweis')).toHaveLength(1)
    }
    expect(within(kachel('Change Failure Rate')).getByTestId('auslieferung-hinweis')).toHaveTextContent(
      'Es zählen nur gemeldete Fehlschläge, nicht Fehler, die erst nach einer erfolgreichen Auslieferung auffallen.',
    )
    expect(within(kachel('Deployment-Frequenz')).getByTestId('auslieferung-hinweis')).toHaveTextContent('erfolgreichen Auslieferungen')
  })

  it('zeigt die Werte mit ihren Einheiten', () => {
    renderMitSprache(<AuslieferungPlatte daten={auslieferung()} />)
    expect(within(kachel('Deployment-Frequenz')).getByTestId('kachel-wert')).toHaveTextContent('9')
    expect(kachel('Deployment-Frequenz')).toHaveTextContent('Auslieferungen in 12 Wochen')
    expect(within(kachel('Lead Time for Changes')).getByTestId('kachel-wert')).toHaveTextContent('5,0')
    expect(kachel('Lead Time for Changes')).toHaveTextContent('Stunden')
    expect(within(kachel('Change Failure Rate')).getByTestId('kachel-wert')).toHaveTextContent('25')
    expect(kachel('Change Failure Rate')).toHaveTextContent('%')
    expect(within(kachel('Time to Restore')).getByTestId('kachel-wert')).toHaveTextContent('2,0')
    expect(screen.queryByTestId('auslieferung-ausfall')).not.toBeInTheDocument()
  })

  it('nennt im Kopf das Fenster und bei späterem Datenbeginn „seit ‹Datum›“ (AK 2)', () => {
    const { unmount } = renderMitSprache(<AuslieferungPlatte daten={auslieferung()} />)
    expect(screen.getByRole('region', { name: 'Auslieferung' })).toHaveTextContent('letzte 12 Wochen')
    unmount()
    renderMitSprache(<AuslieferungPlatte daten={auslieferung({ dataSince: '2026-09-26T08:00:00Z' })} />)
    expect(screen.getByRole('region', { name: 'Auslieferung' })).toHaveTextContent('seit 26.9.2026')
    expect(kachel('Deployment-Frequenz')).toHaveTextContent('Auslieferungen seit 26.9.2026')
  })

  it('zeigt „keine Grundlage“ bei fehlender Lead Time und Change Failure Rate, „kein Ausfall“ bei der Time to Restore (E9)', () => {
    renderMitSprache(
      <AuslieferungPlatte daten={auslieferung({ leadTimeMedianSeconds: null, changeFailurePercent: null, timeToRestoreMedianSeconds: null })} />,
    )
    for (const name of ['Lead Time for Changes', 'Change Failure Rate']) {
      expect(within(kachel(name)).getByTestId('kachel-wert')).toHaveTextContent('—')
      expect(kachel(name)).toHaveTextContent('keine Grundlage')
    }
    expect(kachel('Time to Restore')).toHaveTextContent('kein Ausfall')
    expect(kachel('Time to Restore')).not.toHaveTextContent('keine Grundlage')
  })

  it('zeigt eine Frequenz 0 als echte 0', () => {
    renderMitSprache(<AuslieferungPlatte daten={auslieferung({ deploymentCount: 0 })} />)
    expect(within(kachel('Deployment-Frequenz')).getByTestId('kachel-wert')).toHaveTextContent(/^0$/)
    expect(kachel('Deployment-Frequenz')).toHaveTextContent('Auslieferungen in 12 Wochen')
  })

  it('zeigt eine Change Failure Rate 0 als echte 0', () => {
    renderMitSprache(<AuslieferungPlatte daten={auslieferung({ changeFailurePercent: 0 })} />)
    expect(within(kachel('Change Failure Rate')).getByTestId('kachel-wert')).toHaveTextContent(/^0$/)
  })

  it('zeigt die Wochenleiste über zwölf Wochen mit abgesetzten Fehlschlägen und Grundlinien (AK 14)', () => {
    const wochen = leereWochen()
    wochen[10] = { ...wochen[10], successCount: 3, failureCount: 1 }
    wochen[11] = { ...wochen[11], successCount: 2, failureCount: 0 }
    renderMitSprache(<AuslieferungPlatte daten={auslieferung({ weeks: wochen })} />)

    const werk = screen.getByRole('img', { name: /^Auslieferungsversuche je Woche/ })
    expect(within(werk).getAllByTestId(/^auslieferung-woche-/)).toHaveLength(12)
    expect(within(werk).getAllByTestId('grundlinie')).toHaveLength(10)
    expect(within(screen.getByTestId('wochen-leiste')).getAllByText(/^\d+$/)).toHaveLength(12)

    const starke = screen.getByTestId('auslieferung-woche-10')
    expect(within(starke).getByTestId('auslieferung-erfolg-75')).toBeInTheDocument()
    expect(within(starke).getByTestId('auslieferung-fehlschlag-25')).toBeInTheDocument()
    // Abgesetzt: der Fehlschlag steht über dem Erfolg, getrennt von ihm.
    const fehl = within(starke).getByTestId('auslieferung-fehlschlag-25')
    const erfolg = within(starke).getByTestId('auslieferung-erfolg-75')
    expect(fehl.compareDocumentPosition(erfolg) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()

    const letzte = screen.getByTestId('auslieferung-woche-11')
    expect(within(letzte).getByTestId('auslieferung-erfolg-50')).toBeInTheDocument()
    expect(within(letzte).queryByTestId(/^auslieferung-fehlschlag-/)).not.toBeInTheDocument()

    expect(werk.getAttribute('aria-label')).toContain('3 erfolgreich, 1 gescheitert')
    expect(screen.getByTestId('auslieferung-legende')).toHaveTextContent('erfolgreich')
    expect(screen.getByTestId('auslieferung-legende')).toHaveTextContent('gescheitert')
  })

  it('spricht Englisch', () => {
    renderMitSprache(
      <AuslieferungPlatte daten={auslieferung({ outageSince: new Date(JETZT - 26 * STUNDE * 1000).toISOString() })} />,
      'en',
    )
    expect(screen.getByRole('region', { name: 'Delivery' })).toHaveTextContent('last 12 weeks')
    expect(screen.getByTestId('auslieferung-ausfall')).toHaveTextContent('Outage ongoing for 1 day')
    expect(kachel('Deployment frequency')).toHaveTextContent('deliveries in 12 weeks')
  })
})
