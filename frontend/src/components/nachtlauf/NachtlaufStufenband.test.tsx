import { ThemeProvider } from '@mui/material/styles'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { NACHTLAUF_FARBEN, nachtlaufTheme } from '../../nachtlaufDesign'
import { NachtlaufStufenband, type Bandabschnitt } from './NachtlaufStufenband'

/**
 * Das Stufenband eines Ketten-Vorgangs (#916, AK 5 und AK 8). Die Rechnung steht in der Seite;
 * geprüft wird hier, was aus den fertigen Abschnitten wird.
 */

const abschnitt = (felder: Partial<Bandabschnitt> = {}): Bandabschnitt => ({
  schluessel: 'plan',
  label: 'Plan',
  anteil: 20,
  fuellung: 39,
  erreicht: true,
  zahlen: '7,8 / 20 min',
  kosten: null,
  vermerk: null,
  farbe: NACHTLAUF_FARBEN.gut,
  ...felder,
})

const zeige = (abschnitte: readonly Bandabschnitt[], ansage = 'Stufenband: Plan 7,8 / 20 min') =>
  render(
    <ThemeProvider theme={nachtlaufTheme}>
      <NachtlaufStufenband
        abschnitte={abschnitte}
        ansage={ansage}
        testId="stufenband-791"
        abschnittTestId="stufe-791"
      />
    </ThemeProvider>,
  )

describe('NachtlaufStufenband', () => {
  it('gibt jedem Schritt die Breite seiner Zeitvorgabe', () => {
    zeige([
      abschnitt({ schluessel: 'plan', label: 'Plan', anteil: 20 }),
      abschnitt({ schluessel: 'review', label: 'Review', anteil: 15 }),
      abschnitt({ schluessel: 'abdeckung', label: 'Abdeckung', anteil: 10 }),
    ])

    expect(screen.getByTestId('stufe-791-plan')).toHaveAttribute('data-anteil', '20')
    expect(screen.getByTestId('stufe-791-review')).toHaveAttribute('data-anteil', '15')
    expect(screen.getByTestId('stufe-791-abdeckung')).toHaveAttribute('data-anteil', '10')
  })

  it('zeigt je Schritt Benennung und Zeiten', () => {
    zeige([abschnitt()])

    expect(screen.getByTestId('stufe-791-plan')).toHaveTextContent('Plan')
    expect(screen.getByTestId('stufe-791-plan')).toHaveTextContent('7,8 / 20 min')
  })

  it('unterscheidet einen nie erreichten Schritt von einem erreichten, leeren (AK 8)', () => {
    zeige([
      abschnitt({ schluessel: 'pakete', label: 'Pakete', erreicht: false, zahlen: '– / 15 min', vermerk: 'nicht erreicht' }),
      abschnitt({ schluessel: 'abdeckung', label: 'Abdeckung', erreicht: true, fuellung: 0, zahlen: '0,0 / 10 min' }),
    ])

    const nie = screen.getByTestId('stufe-791-pakete')
    const leer = screen.getByTestId('stufe-791-abdeckung')

    // Erste Unterscheidung: das Wort. Die Schraffur allein trüge die Aussage nicht.
    expect(nie).toHaveTextContent('nicht erreicht')
    expect(leer).not.toHaveTextContent('nicht erreicht')
    // Zweite Unterscheidung: das Merkmal am Element, aus dem die Schraffur folgt.
    expect(nie).toHaveAttribute('data-erreicht', 'nein')
    expect(leer).toHaveAttribute('data-erreicht', 'ja')
  })

  it('trägt den Vermerk des Abschnitts, an dem der Vorgang endete (AK 7)', () => {
    zeige([abschnitt({ schluessel: 'review', label: 'Review', vermerk: 'am Zeitbudget beendet' })])

    expect(screen.getByTestId('stufe-791-review')).toHaveTextContent('am Zeitbudget beendet')
  })

  it('führt die Ansage für Vorlesewerkzeuge am Band, nicht an jedem Abschnitt', () => {
    // `role="img"` fasst das Band zusammen — sonst läse ein Vorlesewerkzeug jede Zahl einzeln
    // ein zweites Mal vor.
    zeige([abschnitt()], 'Stufenband: Plan 7,8 / 20 min · Review 11,8 / 15 min')

    const band = screen.getByRole('img', {
      name: 'Stufenband: Plan 7,8 / 20 min · Review 11,8 / 15 min',
    })
    expect(band).toHaveAttribute('data-testid', 'stufenband-791')
  })

  it('füllt einen Abschnitt in der Breite seines Verbrauchs', () => {
    zeige([abschnitt({ fuellung: 79 })])

    expect(screen.getByTestId('stufe-791-plan')).toHaveAttribute('data-fuellung', '79')
  })
})

describe('NachtlaufStufenband — Kosten je Schritt (#1106)', () => {
  it('zeigt die Kosten eines Schritts als eigene Zeile', () => {
    zeige([abschnitt({ kosten: '3,20 $' })])
    expect(screen.getByTestId('stufe-791-plan-kosten')).toHaveTextContent('3,20 $')
  })

  it('zeigt ohne gemeldete Kosten keine Zeile — und nie eine 0', () => {
    zeige([abschnitt({ kosten: null })])
    expect(screen.queryByTestId('stufe-791-plan-kosten')).toBeNull()
  })
})
