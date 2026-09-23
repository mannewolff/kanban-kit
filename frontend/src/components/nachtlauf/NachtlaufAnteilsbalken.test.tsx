import { ThemeProvider } from '@mui/material/styles'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { NACHTLAUF_FARBEN, nachtlaufTheme } from '../../nachtlaufDesign'
import { NachtlaufAnteilsbalken } from './NachtlaufAnteilsbalken'

/**
 * Der Anteilsbalken eines Umsetzungs-Vorgangs (#917, AK 6). Gerechnet wird in der Seite; hier
 * steht, was aus dem fertigen Anteil wird.
 */

const zeige = (felder: Partial<Parameters<typeof NachtlaufAnteilsbalken>[0]> = {}) =>
  render(
    <ThemeProvider theme={nachtlaufTheme}>
      <NachtlaufAnteilsbalken
        anteil={15}
        beschriftung="12 Min · 15 % der Schicht"
        ansage="Karte #869: Erfolg, 12 Min, 15 % der Schicht"
        farbe={NACHTLAUF_FARBEN.gut}
        testId="anteil-869"
        fuellungTestId="anteil-869-balken"
        {...felder}
      />
    </ThemeProvider>,
  )

describe('NachtlaufAnteilsbalken', () => {
  it('führt den Anteil als Datenfeld, nicht nur als Stilwert', () => {
    // In jsdom rechnet kein Browser ein Layout aus; ein Test auf `width` prüfte die Zeichenkette,
    // die man selbst geschrieben hat.
    zeige({ anteil: 9 })
    expect(screen.getByTestId('anteil-869')).toHaveAttribute('data-anteil', '9')
  })

  it('nennt Dauer und Anteil in Worten neben dem Balken', () => {
    zeige()
    expect(screen.getByTestId('anteil-869')).toHaveTextContent('12 Min · 15 % der Schicht')
  })

  it('trägt eine Ansage für Vorlesewerkzeuge mit Nummer, Zustand und Dauer', () => {
    // Die Aussage hängt nie allein an der Farbe des Balkens.
    zeige()
    expect(
      screen.getByRole('img', { name: 'Karte #869: Erfolg, 12 Min, 15 % der Schicht' }),
    ).toBeInTheDocument()
  })

  it('nimmt den Ton, den die Seite ihm gibt', () => {
    // Der Ton kommt aus der Abbildung `NightRunState → Ton` (E10) und nicht aus der Komponente.
    zeige({ farbe: NACHTLAUF_FARBEN.leer })

    const fuellung = screen.getByTestId('anteil-869-balken')
    expect(getComputedStyle(fuellung).backgroundColor).toBe(alsRgb(NACHTLAUF_FARBEN.leer))
  })
})

/** `getComputedStyle` gibt Farben als `rgb(…)` zurück, die Tokens stehen als `#rrggbb`. */
function alsRgb(hex: string): string {
  const kanal = (position: number) => Number.parseInt(hex.slice(position, position + 2), 16)
  return `rgb(${kanal(1)}, ${kanal(3)}, ${kanal(5)})`
}
