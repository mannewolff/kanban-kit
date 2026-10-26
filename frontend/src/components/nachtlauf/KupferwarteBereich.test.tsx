import { createTheme, ThemeProvider, useTheme } from '@mui/material/styles'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderMitSprache } from '../../i18n/renderMitSprache'
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

/**
 * Ein fremdes Theme ohne Variablen und ein Knoten mit festem Variablenwert liegen über dem Bereich —
 * so stand bis #1912 die Nachtlauf-Ausnahme über ihm.
 */
const fremdesTheme = createTheme({ palette: { primary: { main: '#123456' } } })
const zeigeInDerAusnahme = () =>
  render(
    <ThemeProvider theme={fremdesTheme}>
      <div style={{ '--mb-palette-background-default': '#ABCDEF' } as Record<string, string>}>
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
    expect(sonde.dataset.kupfer).not.toBe(fremdesTheme.palette.primary.main)
    expect(sonde.dataset.variablen).toBe('true')
  })

  it('schreibt die Variablen beider Erscheinungsbilder an seinen eigenen Knoten', () => {
    zeigeInDerAusnahme()

    // Ein Knoten darüber setzt sie auf einen festen Wert; geerbt bliebe er stehen. Der Bereich setzt
    // sie deshalb selbst.
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

describe('KupferwarteBereich auf Englisch (Issue #1658)', () => {
  it('führt keinen eigenen Text und gibt den Inhalt auf Englisch unverändert wieder', () => {
    renderMitSprache(
      <KupferwarteBereich>
        <span>Night run</span>
      </KupferwarteBereich>,
      'en',
    )

    expect(screen.getByTestId('kupferwarte-bereich')).toHaveTextContent(/^Night run$/)
  })
})
