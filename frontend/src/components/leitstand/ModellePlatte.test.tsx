import { ThemeProvider } from '@mui/material/styles'
import { fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, test } from 'vitest'
import type { NightRunModelRow, NightRunModels, ProgressStage } from '../../api/nightRuns'
import { de } from '../../i18n/de'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { TabletQuerProvider } from '../../lib/tabletQuer'
import { setzeMedien } from '../../test/setup'
import { theme } from '../../theme'
import { ModellePlatte } from './ModellePlatte'

/** Die Platte „Modelle“ (Issue #1949, Plan #1905 E10–E14). */

const STUFEN: ProgressStage[] = ['PLAN', 'REVIEW', 'PAKETE', 'ABDECKUNG', 'UMSETZUNG']

const zeile = (teil: Partial<NightRunModelRow> = {}): NightRunModelRow => ({
  model: 'claude-opus-5-5',
  effort: null,
  attempts: 5,
  succeeded: 4,
  enoughData: true,
  costMedianUsd: 1.25,
  costFrom: 5,
  modelTimeMedianMs: 30_000,
  modelTimeFrom: 5,
  ...teil,
})

const daten = (je: Partial<Record<ProgressStage, NightRunModelRow[]>> = {}, teil: Partial<NightRunModels> = {}): NightRunModels => ({
  dataSince: '2026-08-01T22:00:00Z',
  lastTwelveWeeks: true,
  withoutModel: 0,
  attemptsInPeriod: Object.values(je).flat().reduce((s, z) => s + z.attempts, 0),
  stages: STUFEN.map((stage) => ({ stage, rows: je[stage] ?? [] })),
  ...teil,
})

const zeige = (d: NightRunModels, sprache: 'de' | 'en' = 'de') =>
  renderMitSprache(
    <ThemeProvider theme={theme}>
      <TabletQuerProvider>
        <ModellePlatte daten={d} />
      </TabletQuerProvider>
    </ThemeProvider>,
    sprache,
  )

const stufe = (name: string) => screen.getByRole('table', { name })

test('Szenario: Zu wenige Vorgänge', () => {
  // Angenommen ein Modell hat in der Stufe Prüfung erst drei Vorgänge
  zeige(daten({ REVIEW: [zeile({ attempts: 3, succeeded: 3, enoughData: false, costFrom: 3, modelTimeFrom: 3 })] }))

  // Wenn der Betreiber den Leitstand öffnet — dann steht in seiner Zeile „zu wenig Daten“ statt einer Quote
  const reihe = within(stufe('Prüfung')).getByRole('row', { name: /claude-opus-5-5/ })
  const zellen = within(reihe).getAllByRole('cell')
  expect(zellen.at(-1)).toHaveTextContent('zu wenig Daten')
  expect(reihe).not.toHaveTextContent('erfolgreich')

  // Und die Zahl der Vorgänge, die Kosten und die Modellzeit stehen trotzdem da
  expect(zellen[0]).toHaveTextContent('3')
  expect(zellen[1]).toHaveTextContent('1,25 $')
  expect(zellen[2]).toHaveTextContent('30 s')
})

describe('ModellePlatte', () => {
  it('zeigt alle fünf Stufen in der Reihenfolge der Kette, eine leere mit ihrem Satz', () => {
    zeige(daten({ UMSETZUNG: [zeile()] }))

    const platte = screen.getByRole('region', { name: 'Modelle' })
    const ueberschriften = within(platte).getAllByRole('heading', { level: 3 }).map((h) => h.textContent)
    expect(ueberschriften).toEqual(['Plan', 'Prüfung', 'Arbeitspakete', 'Abdeckung', 'Umsetzung'])
    expect(screen.getAllByText('Kein Vorgang mit gemeldetem Modell')).toHaveLength(4)
    expect(screen.getAllByRole('table')).toHaveLength(1)
    expect(within(stufe('Umsetzung')).getByRole('row', { name: /claude-opus-5-5/ })).toHaveTextContent('4 von 5 erfolgreich')
  })

  it('zeigt ganz ohne Vorgang einen Satz statt der Tabellen (AK 9)', () => {
    zeige(daten())

    expect(screen.getByText('Im Bezugszeitraum gab es keinen Vorgang mit gemeldetem Modell.')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { level: 3 })).not.toBeInTheDocument()
  })

  it('nennt im Kopf Zeitraum und Vorgänge ohne Modellangabe', () => {
    zeige(daten({ PLAN: [zeile()] }, { dataSince: '2026-08-01T12:00:00Z', lastTwelveWeeks: false, withoutModel: 3 }))

    expect(screen.getByRole('region', { name: 'Modelle' })).toHaveTextContent('seit 1.8.2026 · 3 Vorgänge ohne Modellangabe nicht gezählt')
  })

  it('trägt Spaltenköpfe als th mit scope und den Zeilennamen als Zeilenkopf', () => {
    zeige(daten({ PLAN: [zeile({ effort: 'medium' })] }))

    const tabelle = stufe('Plan')
    const koepfe = within(tabelle).getAllByRole('columnheader')
    expect(koepfe.map((k) => k.textContent)).toEqual(['Modell', 'Vorgänge', 'Kosten je Vorgang', 'Modellzeit je Vorgang', 'erfolgreich'])
    for (const kopf of koepfe) {
      expect(kopf.tagName).toBe('TH')
      expect(kopf).toHaveAttribute('scope', 'col')
    }
    const zeilenkopf = within(tabelle).getByRole('rowheader')
    expect(zeilenkopf.tagName).toBe('TH')
    expect(zeilenkopf).toHaveAttribute('scope', 'row')
    expect(zeilenkopf).toHaveTextContent('claude-opus-5-5 · mittel')
  })

  it('ordnet nicht um und nennt Teilmengen und fehlende Messungen', () => {
    zeige(
      daten({
        UMSETZUNG: [
          zeile({ model: 'gpt-astra', attempts: 12, succeeded: 9, costFrom: 9 }),
          zeile({ model: 'claude-opus-5-5', effort: 'turbo', costMedianUsd: null, costFrom: 0, modelTimeMedianMs: null, modelTimeFrom: 0 }),
        ],
      }),
    )

    const namen = within(stufe('Umsetzung')).getAllByRole('rowheader').map((z) => z.textContent)
    expect(namen).toEqual(['gpt-astra', 'claude-opus-5-5 · turbo'])
    const reihen = within(stufe('Umsetzung')).getAllByRole('row').slice(1)
    expect(reihen[0]).toHaveTextContent('1,25 $ (aus 9 von 12)')
    expect(reihen[0]).toHaveTextContent('9 von 12 erfolgreich')
    expect(within(reihen[1]).getAllByText('nicht gemessen')).toHaveLength(2)
  })

  it('hebt keine Zeile hervor (AK 5)', () => {
    zeige(daten({ REVIEW: [zeile({ model: 'a', succeeded: 5 }), zeile({ model: 'b', succeeded: 1 })] }))

    const reihen = within(stufe('Prüfung')).getAllByRole('row').slice(1)
    const stil = reihen.map((r) => {
      const s = getComputedStyle(r)
      return [r.className, s.backgroundColor, s.fontWeight, s.color]
    })
    expect(stil[0]).toEqual(stil[1])
    for (const r of reihen) {
      expect(r).not.toHaveAttribute('aria-current')
      expect(r).not.toHaveAttribute('aria-selected')
    }
  })

  it('erklärt am Kopf „erfolgreich“, was es in der Stufe heißt — per Fokus erreichbar', async () => {
    zeige(daten({ UMSETZUNG: [zeile()] }))

    const kopf = within(stufe('Umsetzung')).getByRole('columnheader', { name: 'erfolgreich' })
    const ausloeser = within(kopf).getByText('erfolgreich')
    expect(ausloeser).toHaveAttribute('tabindex', '0')
    await userEvent.tab()
    expect(ausloeser).toHaveFocus()
    expect(await screen.findByRole('tooltip')).toHaveTextContent(de.LEITSTAND.MODELLE_ERFOLG_ERKLAERUNG.UMSETZUNG)
  })

  it('öffnet die Erklärung auf dem Tablet quer per Antippen', async () => {
    setzeMedien({ tabletQuer: true })
    zeige(daten({ REVIEW: [zeile()] }))

    fireEvent.click(within(within(stufe('Prüfung')).getByRole('columnheader', { name: 'erfolgreich' })).getByText('erfolgreich'))

    expect(await screen.findByRole('tooltip')).toHaveTextContent(de.LEITSTAND.MODELLE_ERFOLG_ERKLAERUNG.REVIEW)
  })

  it('steht auf Englisch mit übersetzten Texten und dem Modellnamen wie gemeldet', () => {
    zeige(daten({ PAKETE: [zeile({ effort: 'xhigh', attempts: 2, enoughData: false })] }), 'en')

    expect(screen.getByRole('region', { name: 'Models' })).toHaveTextContent('last 12 weeks')
    const tabelle = screen.getByRole('table', { name: 'Work packages' })
    expect(within(tabelle).getByRole('rowheader')).toHaveTextContent('claude-opus-5-5 · extra high')
    expect(tabelle).toHaveTextContent('too little data')
    expect(screen.getAllByText('No attempt with a reported model')).toHaveLength(4)
  })

  it('setzt jede Tabelle in einen waagrecht scrollbaren Rahmen', () => {
    zeige(daten({ PLAN: [zeile()] }))

    // eslint-disable-next-line testing-library/no-node-access -- der Rahmen hat keine Rolle; geprüft wird sein Überlauf
    const rahmen = stufe('Plan').parentElement as HTMLElement
    expect(getComputedStyle(rahmen).overflowX).toBe('auto')
  })
})
