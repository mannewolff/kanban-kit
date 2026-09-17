import { ThemeProvider, useTheme } from '@mui/material/styles'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { NACHTLAUF_WURZEL_SX, nachtlaufTheme } from '../../nachtlaufDesign'
import { theme } from '../../theme'
import { KupferwarteBereich } from './KupferwarteBereich'

/**
 * Ein Bereich, der die Nachtlauf-Ausnahme verlässt (#987). Geprüft wird, dass er beides zurückholt:
 * das Theme der Kupferwarte für seinen Teilbaum und die Variablen, die der Wurzelknoten der
 * Ausnahme auf feste Hellwerte gesetzt hat.
 */

/** Liest das Theme, das an ihrer Stelle im Baum gilt. */
function Sonde() {
  const aktiv = useTheme()
  return (
    <span data-testid="sonde" data-kupfer={aktiv.palette.primary.main} data-variablen={String('vars' in aktiv)} />
  )
}

/** Wie auf der Seite: Ausnahme-Theme und heller Wurzelknoten liegen über dem Bereich. */
const zeigeInDerAusnahme = () =>
  render(
    <ThemeProvider theme={nachtlaufTheme}>
      <div style={NACHTLAUF_WURZEL_SX as Record<string, string>}>
        <KupferwarteBereich>
          <Sonde />
        </KupferwarteBereich>
      </div>
    </ThemeProvider>,
  )

describe('KupferwarteBereich', () => {
  it('gibt seinem Inhalt das Theme der Kupferwarte zurueck, nicht das der Ausnahme', () => {
    zeigeInDerAusnahme()

    const sonde = screen.getByTestId('sonde')
    expect(sonde.dataset.kupfer).toBe(theme.colorSchemes.light!.palette.primary.main)
    expect(sonde.dataset.kupfer).not.toBe(nachtlaufTheme.palette.primary.main)
    expect(sonde.dataset.variablen).toBe('true')
  })

  it('schreibt die Variablen beider Erscheinungsbilder an seinen eigenen Knoten', () => {
    zeigeInDerAusnahme()

    // Der Wurzelknoten der Ausnahme setzt sie auf feste Hellwerte; geerbt würde die Seite auch im
    // dunklen System hell bleiben. Der Bereich setzt sie deshalb selbst.
    const bereich = screen.getByTestId('kupferwarte-bereich')
    expect(getComputedStyle(bereich).getPropertyValue('--mb-palette-background-default')).toBe(
      theme.colorSchemes.light!.palette.background.default,
    )
  })

  it('umschliesst seinen Inhalt', () => {
    render(
      <KupferwarteBereich>
        <span data-testid="inhalt" />
      </KupferwarteBereich>,
    )

    expect(screen.getByTestId('kupferwarte-bereich')).toContainElement(screen.getByTestId('inhalt'))
  })
})
