import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, test } from 'vitest'
import type { EpicForecast, EpicForecastEntry } from '../../api/dashboard'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { TabletQuerProvider } from '../../lib/tabletQuer'
import { setzeMedien } from '../../test/setup'
import { VorhabenPlatte } from './VorhabenPlatte'

/** Die Platte „Vorhaben“ mit Prognosezeile (Issue #1829, Plan #1819 A8, A9, E10, E11, E12). */

const vorhaben = (teil: Partial<EpicForecastEntry> = {}): EpicForecastEntry => ({
  id: 42,
  number: 1782,
  title: 'Leitstand-Prognose',
  state: 'FORECAST',
  total: 12,
  finished: 0,
  open: 12,
  p50: '2026-10-21',
  p85: '2026-10-24',
  earliest: false,
  pendingDocuments: [],
  otherEpicsSharing: 0,
  ...teil,
})

const prognose = (epics: EpicForecastEntry[], throughputDays = 20): EpicForecast => ({ throughputDays, epics })

const zeige = (daten: EpicForecast) => renderMitSprache(<VorhabenPlatte prognose={daten} />)

const platte = () => screen.getByRole('region', { name: 'Vorhaben' })

describe('VorhabenPlatte', () => {
  test('Szenario: Prognose für ein laufendes Vorhaben', () => {
    zeige(prognose([vorhaben()]))
    const zeile = within(platte()).getByTestId('prognose-1782')
    expect(zeile).toHaveTextContent('voraussichtlich fertig: 50 % bis 21.10.2026 · 85 % bis 24.10.2026 · 12 offene Pakete')
    expect(within(platte()).getByText('0/12')).toBeInTheDocument()
  })

  test('Szenario: Umgesetzte Pakete zählen beim nächsten Laden', () => {
    const { rerender } = zeige(prognose([vorhaben()]))
    expect(within(platte()).getByTestId('prognose-1782')).toHaveTextContent('12 offene Pakete')
    rerender(<VorhabenPlatte prognose={prognose([vorhaben({ finished: 3, open: 9 })])} />)
    expect(within(platte()).getByTestId('prognose-1782')).toHaveTextContent('9 offene Pakete')
    expect(within(platte()).getByText('3/12')).toBeInTheDocument()
    expect(within(platte()).getByTestId('fuellung-25')).toBeInTheDocument()
  })

  test('Szenario: Zu wenig Durchsatz', () => {
    zeige(
      prognose(
        [
          vorhaben({ state: 'INSUFFICIENT_THROUGHPUT', p50: null, p85: null }),
          vorhaben({ id: 43, number: 1790, title: 'Zweites', state: 'INSUFFICIENT_THROUGHPUT', p50: null, p85: null, open: 4, total: 4 }),
        ],
        3,
      ),
    )
    for (const nummer of [1782, 1790]) {
      const zeile = within(platte()).getByTestId(`prognose-${nummer}`)
      expect(zeile).toHaveTextContent('Zu wenig Durchsatz für eine Prognose')
      expect(zeile.textContent).not.toMatch(/\d{2}\.\d{2}\.\d{4}/)
      expect(zeile).not.toHaveTextContent('offene Pakete')
    }
  })

  test('Szenario: Vorhaben wächst noch', async () => {
    zeige(
      prognose([
        vorhaben({ open: 5, total: 5, earliest: true, pendingDocuments: [{ number: 1819, title: '[Plan] Leitstand-Prognose' }] }),
      ]),
    )
    const zeile = within(platte()).getByTestId('prognose-1782')
    expect(zeile).toHaveTextContent('voraussichtlich fertig: 50 % frühestens 21.10.2026 · 85 % frühestens 24.10.2026 · 5 offene Pakete')
    await userEvent.hover(within(zeile).getByTestId('prognose-hinweis-1782'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Weitere Pakete folgen aus #1819 [Plan] Leitstand-Prognose.')
  })

  test('Szenario: Mehrere Vorhaben gleichzeitig', async () => {
    zeige(
      prognose([
        vorhaben({ otherEpicsSharing: 2 }),
        vorhaben({ id: 43, number: 1790, title: 'Zweites', otherEpicsSharing: 2 }),
        vorhaben({ id: 44, number: 1791, title: 'Drittes', otherEpicsSharing: 2 }),
      ]),
    )
    await userEvent.hover(within(platte()).getByTestId('prognose-hinweis-1790'))
    const hinweis = await screen.findByRole('tooltip')
    expect(hinweis).toHaveTextContent('Gilt, wenn das Board nur an diesem Vorhaben arbeitet.')
    expect(hinweis).toHaveTextContent('2 weitere Vorhaben teilen sich den Durchsatz.')
  })

  it('erklärt Datum, Wahrscheinlichkeit, offene Pakete und Zeitraum und hängt den Hinweis als Beschreibung an', async () => {
    zeige(prognose([vorhaben()]))
    const ziel = within(platte()).getByTestId('prognose-hinweis-1782')
    expect(ziel).toHaveAttribute('tabindex', '0')
    await userEvent.tab()
    expect(ziel).toHaveFocus()
    const hinweis = await screen.findByRole('tooltip')
    expect(hinweis).toHaveTextContent(
      'Mit 50 % Wahrscheinlichkeit sind bis 21.10.2026 alle 12 offenen Pakete umgesetzt, mit 85 % bis 24.10.2026, ' +
        'gerechnet aus dem Durchsatz der letzten 30 Tage, jeweils von 12 bis 12 Uhr.',
    )
    expect(hinweis).not.toHaveTextContent('weitere Vorhaben')
    await waitFor(() => expect(ziel).toHaveAttribute('aria-describedby'))
    expect(ziel).not.toHaveAttribute('aria-label')
  })

  it('zeigt bei einem Vorhaben ohne Pakete eine leere Schiene, „0/0“ und keinen Hinweis (E11)', () => {
    zeige(prognose([vorhaben({ state: 'NO_PACKAGES', total: 0, finished: 0, open: 0, p50: null, p85: null })]))
    expect(within(platte()).getByTestId('fuellung-0')).toBeInTheDocument()
    expect(within(platte()).getByText('0/0')).toBeInTheDocument()
    expect(within(platte()).getByTestId('prognose-1782')).toHaveTextContent('noch keine Pakete geschnitten')
    expect(within(platte()).queryByTestId('prognose-hinweis-1782')).not.toBeInTheDocument()
  })

  it('zeigt ein umgesetztes Vorhaben voll, ohne Daten und ohne Paketzahl (AK 12, 18)', () => {
    zeige(prognose([vorhaben({ state: 'ALL_DONE', finished: 12, open: 0, p50: null, p85: null })]))
    expect(within(platte()).getByTestId('fuellung-100')).toBeInTheDocument()
    expect(within(platte()).getByText('12/12')).toBeInTheDocument()
    expect(within(platte()).getByTestId('prognose-1782')).toHaveTextContent(/^alle Pakete umgesetzt$/)
  })

  it('setzt Titel, Schiene und Zählung in die erste Zeile, die Prognose darunter (E10)', () => {
    zeige(prognose([vorhaben({ finished: 7, total: 9, open: 2 })]))
    const [eintrag] = within(platte()).getAllByRole('listitem')
    expect(eintrag.textContent).toBe(
      'Leitstand-Prognose7/9voraussichtlich fertig: 50 % bis 21.10.2026 · 85 % bis 24.10.2026 · 2 offene Pakete',
    )
    expect(within(eintrag).getByTestId('fuellung-78')).toBeInTheDocument()
  })

  it('sagt ohne Vorhaben, dass es keins gibt', () => {
    zeige(prognose([]))
    expect(platte()).toHaveTextContent('Kein offenes Vorhaben.')
    expect(within(platte()).queryByRole('listitem')).not.toBeInTheDocument()
  })

  it('spricht Englisch', () => {
    renderMitSprache(<VorhabenPlatte prognose={prognose([vorhaben({ open: 1 })])} />, 'en')
    expect(screen.getByTestId('prognose-1782')).toHaveTextContent('1 open package')
  })
})

describe('VorhabenPlatte auf dem Tablet im Querformat', () => {
  it('öffnet den Hinweis per Antippen und schließt ihn wieder', async () => {
    setzeMedien({ tabletQuer: true })
    renderMitSprache(
      <TabletQuerProvider>
        <VorhabenPlatte prognose={prognose([vorhaben({ otherEpicsSharing: 1 })])} />
      </TabletQuerProvider>,
    )
    const ziel = screen.getByTestId('prognose-hinweis-1782')
    fireEvent.click(ziel)
    expect(screen.getByRole('tooltip')).toHaveTextContent('1 weitere Vorhaben teilen sich den Durchsatz.')
    fireEvent.click(ziel)
    await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument())
  })
})
