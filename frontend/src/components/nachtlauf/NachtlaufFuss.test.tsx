import { ThemeProvider } from '@mui/material/styles'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { NACHTLAUF_FARBEN, nachtlaufTheme } from '../../nachtlaufDesign'
import { NachtlaufFuss, type Fussangabe } from './NachtlaufFuss'

/** Die Fußzeile eines Laufs (#918). Welche Angaben sie führt, entscheidet die Seite. */

const zeige = (angaben: readonly Fussangabe[]) =>
  render(
    <ThemeProvider theme={nachtlaufTheme}>
      <NachtlaufFuss angaben={angaben} testId="uebersicht-fuss" />
    </ThemeProvider>,
  )

/** `getComputedStyle` gibt Farben als `rgb(…)` zurück, die Tokens stehen als `#rrggbb`. */
function alsRgb(hex: string): string {
  const kanal = (position: number) => Number.parseInt(hex.slice(position, position + 2), 16)
  return `rgb(${kanal(1)}, ${kanal(3)}, ${kanal(5)})`
}

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

  it('gibt einem Vorbehalt die Warnfarbe des Entwurfs', () => {
    zeige([
      {
        label: 'Herkunft der Budgets',
        wert: 'Vorgabewerte, night.kette fehlt in der Config',
        vorbehalt: true,
      },
    ])

    const wert = screen.getByText('Vorgabewerte, night.kette fehlt in der Config')
    expect(getComputedStyle(wert).color).toBe(alsRgb(NACHTLAUF_FARBEN.budget))
  })

  it('lässt eine Angabe ohne Vorbehalt in der gewöhnlichen Textfarbe', () => {
    // „nicht angegeben" ist keine Warnung, sondern eine Auskunft — sie darf nicht rufen.
    zeige([{ label: 'Herkunft der Budgets', wert: 'nicht angegeben' }])

    const wert = screen.getByText('nicht angegeben')
    expect(getComputedStyle(wert).color).toBe(alsRgb(NACHTLAUF_FARBEN.ink2))
  })
})
