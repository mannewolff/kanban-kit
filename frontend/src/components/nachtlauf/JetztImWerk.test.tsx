import { ThemeProvider } from '@mui/material/styles'
import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { RunnerJetzt as RunnerJetztDaten, RunnerLauf, RunnerStufe } from '../../api/nightRuns'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { uhrzeit } from '../../lib/leitstand'
import type { Zeitleistenlauf } from '../../lib/laufZeitleiste'
import { MELDER, theme } from '../../theme'
import { JetztImWerk } from './JetztImWerk'

/**
 * Die Platte „Jetzt im Werk“ (Issue #1887, Plan #1882 E2; Vorlage `docs/entwurf-lauf-ansicht.html`
 * Z. 452–475): je Run ein Balken auf einer Zeitachse mit Nummer und laufender Stufe, beendete Runs
 * in ihrem Melder, dazu eine Vorleseliste. Die Rechnung selbst prüft `laufZeitleiste.test.ts`.
 *
 * <p>jsdom löst `var()` nicht auf; Farben werden deshalb gegen den **Verweis** verglichen.
 */

const JETZT = Date.parse('2026-10-21T11:35:00Z')

const lauf = (teil: Partial<Zeitleistenlauf> & Pick<Zeitleistenlauf, 'startedAt'>): Zeitleistenlauf => ({
  nummer: undefined,
  durationMs: 0,
  laeuft: true,
  melder: 'stahl',
  ...teil,
})

const stufe = (stage: string, startedAt: string): RunnerStufe => ({
  cardNumber: 1887,
  stage,
  startedAt,
  deadline: null,
  step: 'SESSION',
  holdsImplementation: false,
})

const runnerLauf = (runId: number, stages: RunnerStufe[]): RunnerLauf => ({
  runId,
  mode: 'CHAIN',
  startedAt: '2026-10-21T10:32:00Z',
  tokenName: 'runner',
  lastReportAt: '2026-10-21T11:30:00Z',
  state: 'LAEUFT',
  stages,
  lastStage: null,
})

const runner = (runs: RunnerLauf[]): RunnerJetztDaten => ({ state: 'AKTIV', runs, lastRun: null })

function zeige(laeufe: Zeitleistenlauf[], daten: RunnerJetztDaten | null = null) {
  return renderMitSprache(
    <ThemeProvider theme={theme}>
      <JetztImWerk laeufe={laeufe} runner={daten} jetzt={JETZT} />
    </ThemeProvider>,
  )
}

const vorleseliste = () =>
  within(screen.getByRole('list')).getAllByRole('listitem').map((eintrag) => eintrag.textContent)

describe('JetztImWerk', () => {
  it('zeigt zwei gleichzeitig laufende Runs mit ihrer jüngsten Stufe', () => {
    zeige(
      [
        lauf({ nummer: 692, startedAt: '2026-10-21T10:34:00Z' }),
        lauf({ nummer: 691, startedAt: '2026-10-21T10:32:00Z' }),
      ],
      runner([
        runnerLauf(691, [stufe('UMSETZUNG', '2026-10-21T10:50:00Z'), stufe('PLAN', '2026-10-21T10:33:00Z')]),
        runnerLauf(692, [stufe('PLAN', '2026-10-21T10:35:00Z'), stufe('REVIEW', '2026-10-21T11:00:00Z')]),
      ]),
    )

    expect(screen.getByRole('heading', { name: 'Jetzt im Werk · 2 Runs gleichzeitig' })).toBeInTheDocument()
    expect(screen.getByText(`jetzt ${uhrzeit('2026-10-21T11:35:00Z')}`)).toBeInTheDocument()
    expect(screen.getByTestId('werk-balken-0')).toHaveTextContent('#691 · Umsetzung')
    expect(screen.getByTestId('werk-balken-1')).toHaveTextContent('#692 · Prüfung')
    expect(vorleseliste()).toEqual([
      `Run #691, seit ${uhrzeit('2026-10-21T10:32:00Z')}, läuft, Umsetzung`,
      `Run #692, seit ${uhrzeit('2026-10-21T10:34:00Z')}, läuft, Prüfung`,
    ])
  })

  it('zeichnet laufende Runs schraffiert in Stahl und spannt die Achse vom Fensteranfang bis jetzt', () => {
    zeige([lauf({ nummer: 691, startedAt: '2026-10-21T10:05:00Z' })])

    const balken = screen.getByTestId('werk-balken-0')
    expect(getComputedStyle(balken).background).toContain('repeating-linear-gradient')
    expect(getComputedStyle(balken).background).toContain(MELDER.stahl)
    // Der Run begann vor 90 Minuten — das Fenster beginnt mit ihm, der Balken reicht bis jetzt.
    expect(balken).toHaveStyle({ left: '0%', width: '100%' })
    const marken = within(screen.getByTestId('werk-marken'))
      .getAllByText(/\d/)
      .map((m) => m.textContent)
    expect(marken).toEqual(
      ['10:05:00', '10:27:30', '10:50:00', '11:12:30', '11:35:00'].map((z) => uhrzeit(`2026-10-21T${z}Z`)),
    )
  })

  it('zeigt einen im Fenster beendeten Run in seinem Melder, ohne Stufe', () => {
    zeige(
      [
        lauf({ nummer: 691, startedAt: '2026-10-21T10:40:00Z' }),
        lauf({ nummer: 690, startedAt: '2026-10-21T10:35:00Z', laeuft: false, durationMs: 15 * 60_000, melder: 'gruen' }),
      ],
      runner([runnerLauf(690, [stufe('PLAN', '2026-10-21T10:36:00Z')])]),
    )

    expect(screen.getByRole('heading', { name: 'Jetzt im Werk · 1 Run' })).toBeInTheDocument()
    const beendet = screen.getByTestId('werk-balken-0')
    expect(beendet).toHaveTextContent(/^#690$/)
    expect(getComputedStyle(beendet).background).toBe(MELDER.gruen)
    expect(beendet).toHaveStyle({ left: '0%', width: '25%' })
    expect(vorleseliste()).toContain(
      `Run #690, ${uhrzeit('2026-10-21T10:35:00Z')} bis ${uhrzeit('2026-10-21T10:50:00Z')}, fertig`,
    )
  })

  it('beschriftet einen Run ohne gemeldete Stufe nur mit seiner Nummer', () => {
    zeige([lauf({ nummer: 693, startedAt: '2026-10-21T11:00:00Z' })], runner([runnerLauf(693, [])]))

    expect(screen.getByTestId('werk-balken-0')).toHaveTextContent(/^#693$/)
    expect(vorleseliste()).toEqual([`Run #693, seit ${uhrzeit('2026-10-21T11:00:00Z')}, läuft`])
  })

  it('nennt an einem Run ohne Nummer seinen Start', () => {
    zeige([lauf({ startedAt: '2026-10-21T11:00:00Z' })])

    expect(screen.getByTestId('werk-balken-0')).toHaveTextContent(new RegExp(`^${uhrzeit('2026-10-21T11:00:00Z')}$`))
    expect(vorleseliste()).toEqual([`Run, seit ${uhrzeit('2026-10-21T11:00:00Z')}, läuft`])
  })

  it('verweist mit Balken und Vorleseliste auf die Platte des Runs', () => {
    zeige([lauf({ nummer: 691, startedAt: '2026-10-21T10:32:00Z' })])

    expect(screen.getByTestId('werk-balken-0')).toHaveAttribute('href', '#lauf-2026-10-21T10:32:00Z')
    // Die Achse ist nur Gestalt: Ihre Balken stehen nicht in der Tabulator-Reihenfolge, die Liste trägt den Link.
    expect(screen.getByTestId('werk-balken-0')).toHaveAttribute('tabindex', '-1')
    expect(screen.getByRole('link', { name: `Run #691, seit ${uhrzeit('2026-10-21T10:32:00Z')}, läuft` })).toHaveAttribute(
      'href',
      '#lauf-2026-10-21T10:32:00Z',
    )
  })

  it('erscheint nicht, solange kein Run läuft', () => {
    const { container } = zeige([lauf({ nummer: 690, startedAt: '2026-10-21T11:00:00Z', laeuft: false, melder: 'gruen' })])

    expect(container).toBeEmptyDOMElement()
  })

  it('spricht Englisch', () => {
    renderMitSprache(
      <ThemeProvider theme={theme}>
        <JetztImWerk
          laeufe={[lauf({ nummer: 691, startedAt: '2026-10-21T10:32:00Z' }), lauf({ nummer: 692, startedAt: '2026-10-21T10:34:00Z' })]}
          runner={runner([runnerLauf(691, [stufe('UMSETZUNG', '2026-10-21T10:50:00Z')])])}
          jetzt={JETZT}
        />
      </ThemeProvider>,
      'en',
    )

    expect(screen.getByRole('heading', { name: 'Right now · 2 runs at once' })).toBeInTheDocument()
    expect(vorleseliste()[0]).toBe(`Run #691, since ${uhrzeit('2026-10-21T10:32:00Z', 'en')}, running, Implementation`)
  })

  it('nennt auf Englisch einen Run ohne Nummer', () => {
    renderMitSprache(
      <ThemeProvider theme={theme}>
        <JetztImWerk laeufe={[lauf({ startedAt: '2026-10-21T11:00:00Z' })]} runner={null} jetzt={JETZT} />
      </ThemeProvider>,
      'en',
    )

    expect(vorleseliste()).toEqual([`Run, since ${uhrzeit('2026-10-21T11:00:00Z', 'en')}, running`])
  })

  it('nennt auf Englisch einen einzelnen Run und einen beendeten', () => {
    renderMitSprache(
      <ThemeProvider theme={theme}>
        <JetztImWerk
          laeufe={[
            lauf({ nummer: 691, startedAt: '2026-10-21T10:40:00Z' }),
            lauf({ nummer: 690, startedAt: '2026-10-21T10:35:00Z', laeuft: false, durationMs: 15 * 60_000, melder: 'gruen' }),
          ]}
          runner={null}
          jetzt={JETZT}
        />
      </ThemeProvider>,
      'en',
    )

    expect(screen.getByRole('heading', { name: 'Right now · 1 run' })).toBeInTheDocument()
    expect(vorleseliste()[0]).toBe(
      `Run #690, ${uhrzeit('2026-10-21T10:35:00Z', 'en')} to ${uhrzeit('2026-10-21T10:50:00Z', 'en')}, finished`,
    )
  })
})
