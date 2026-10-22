import { ThemeProvider } from '@mui/material/styles'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MELDER, theme } from '../../theme'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { NachtlaufFuss, type Fussangabe } from './NachtlaufFuss'

/**
 * Die Fußzeile eines Laufs (#918). Welche Angaben sie führt, entscheidet die Seite.
 *
 * <p>Seit #988 folgt sie Kupferwarte (`CLAUDE-design.md`): Die Farben kommen als Variablen aus dem
 * Theme, deshalb wird auf den **Verweis** verglichen und nicht auf einen aufgelösten Farbwert.
 */

const zeige = (angaben: readonly Fussangabe[]) =>
  render(
    <ThemeProvider theme={theme}>
      <NachtlaufFuss angaben={angaben} testId="uebersicht-fuss" />
    </ThemeProvider>,
  )

describe('NachtlaufFuss', () => {
  it('zeigt jede Angabe als Paar aus Benennung und Wert', () => {
    zeige([
      { label: 'Zeitvorgaben je Kette', wert: 'Plan 20 · Prüfung 15 min' },
      { label: 'Kostenbudget je Kette', wert: '50,00 $' },
    ])

    const fuss = within(screen.getByTestId('uebersicht-fuss'))
    expect(fuss.getByText('Zeitvorgaben je Kette')).toBeInTheDocument()
    expect(fuss.getByText('Plan 20 · Prüfung 15 min')).toBeInTheDocument()
    expect(fuss.getByText('50,00 $')).toBeInTheDocument()
  })

  it('führt die Angaben als Beschreibungsliste, nicht als lose Textfolge', () => {
    // Ein Vorlesewerkzeug liest so Paare statt einer Reihe unverbundener Wörter.
    zeige([{ label: 'Kostenbudget je Kette', wert: '50,00 $' }])

    const fuss = screen.getByTestId('uebersicht-fuss')
    expect(fuss.tagName).toBe('DL')
    expect(within(fuss).getByText('Kostenbudget je Kette').tagName).toBe('DT')
    expect(within(fuss).getByText('50,00 $').tagName).toBe('DD')
  })

  it('gibt einem Vorbehalt den Bernstein-Melder', () => {
    zeige([
      {
        label: 'Herkunft der Budgets',
        wert: 'Vorgabewerte, night.kette fehlt in der Config',
        vorbehalt: true,
      },
    ])

    const wert = screen.getByText('Vorgabewerte, night.kette fehlt in der Config')
    expect(getComputedStyle(wert).color).toBe(MELDER.bernst)
  })

  it('lässt eine Angabe ohne Vorbehalt in der gewöhnlichen Textfarbe', () => {
    // „nicht angegeben" ist keine Warnung, sondern eine Auskunft — sie darf nicht rufen.
    zeige([{ label: 'Herkunft der Budgets', wert: 'nicht angegeben' }])

    const wert = screen.getByText('nicht angegeben')
    expect(getComputedStyle(wert).color).not.toBe(MELDER.bernst)
    expect(getComputedStyle(wert).color).toContain('text-secondary')
  })
})

describe('NachtlaufFuss — Zeile „Einlieferung“ (Plan #1882 E10)', () => {
  const EINLIEFERUNG = [
    'Herkunft unbekannt',
    'maschinell eingeliefert am 21.10.2026, 10:32:03 (Token: ki-workflow-kit)',
    'zuletzt gemeldet am 21.10.2026, 11:26:55',
  ]

  it('führt die Einlieferungsangaben als eine Angabe „Einlieferung“ hinter den übrigen', () => {
    render(
      <ThemeProvider theme={theme}>
        <NachtlaufFuss
          angaben={[{ label: 'Kostenbudget je Kette', wert: '50,00 $' }]}
          einlieferung={EINLIEFERUNG}
          testId="uebersicht-fuss"
        />
      </ThemeProvider>,
    )

    const fuss = screen.getByTestId('uebersicht-fuss')
    const zeile = within(fuss).getByTestId('fuss-einlieferung')
    expect(within(zeile).getByText('Einlieferung').tagName).toBe('DT')
    expect(within(zeile).getByText(EINLIEFERUNG.join(' · ')).tagName).toBe('DD')
    expect(within(fuss).getAllByRole('term').at(-1)).toHaveTextContent('Einlieferung')
  })

  it('lässt die Zeile ohne Einlieferungsangaben weg', () => {
    zeige([{ label: 'Kostenbudget je Kette', wert: '50,00 $' }])

    expect(screen.queryByTestId('fuss-einlieferung')).toBeNull()
    expect(screen.queryByText('Einlieferung')).toBeNull()
  })

  it('benennt die Zeile auf Englisch', () => {
    renderMitSprache(
      <ThemeProvider theme={theme}>
        <NachtlaufFuss angaben={[]} einlieferung={['Origin unknown']} testId="uebersicht-fuss" />
      </ThemeProvider>,
      'en',
    )

    expect(within(screen.getByTestId('fuss-einlieferung')).getByText('Delivery').tagName).toBe('DT')
  })
})

describe('NachtlaufFuss auf Englisch (Issue #1658)', () => {
  it('zeigt die Angaben der Seite auf Englisch als Paare', () => {
    renderMitSprache(
      <ThemeProvider theme={theme}>
        <NachtlaufFuss angaben={[{ label: 'Cost budget per chain', wert: '$50.00' }]} testId="uebersicht-fuss" />
      </ThemeProvider>,
      'en',
    )

    const fuss = within(screen.getByTestId('uebersicht-fuss'))
    expect(fuss.getByText('Cost budget per chain').tagName).toBe('DT')
    expect(fuss.getByText('$50.00').tagName).toBe('DD')
  })
})
