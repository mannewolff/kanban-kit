import { ThemeProvider } from '@mui/material/styles'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { theme } from '../../theme'
import { NachtlaufVorgangszeile } from './NachtlaufVorgangszeile'

/**
 * Die Dauer einer Vorgangszeile (#1245): Sie stand nackt als `mm:ss` neben einer Uhrzeit im Kopf
 * und las sich wie eine. Jetzt trägt sie ihre Einheit sichtbar und ihren ausgeschriebenen Text für
 * Maus und Vorlesewerkzeug.
 */

const zeige = (dauer: number | null) =>
  render(
    <ThemeProvider theme={theme}>
      <ul>
        <NachtlaufVorgangszeile
          nummer={92}
          titel="Paket 92"
          wurzel={null}
          melder="gruen"
          zustandswort="Erfolg"
          klasse={null}
          auszug={null}
          haeufigkeit={null}
          vorhaben={null}
          dauer={dauer}
          kosten="2,30 $"
          commit={null}
          offen={false}
          onUmschalten={() => {}}
          onOeffnen={() => {}}
        />
      </ul>
    </ThemeProvider>,
  )

describe('NachtlaufVorgangszeile — Dauer', () => {
  it('zeigt die Dauer mit Einheit und schreibt sie beschriftet aus', () => {
    zeige(776_000)

    const dauer = screen.getByTestId('dauer-92')
    expect(dauer).toHaveTextContent('12:56 min')
    expect(dauer).toHaveAttribute('aria-label', 'Dauer 12 min 56 s')
    expect(dauer).toHaveAttribute('title', 'Dauer 12 min 56 s')
  })

  it('setzt ab einer Stunde die Einheit „h"', () => {
    zeige(3_730_000)

    const dauer = screen.getByTestId('dauer-92')
    expect(dauer).toHaveTextContent('1:02:10 h')
    expect(dauer).toHaveAttribute('aria-label', 'Dauer 1 h 2 min 10 s')
  })

  it('zeigt ohne Messung „—" ohne Einheit und sagt, dass nicht gemessen wurde', () => {
    zeige(null)

    const dauer = screen.getByTestId('dauer-92')
    expect(dauer).toHaveTextContent('—')
    expect(dauer.textContent).not.toMatch(/min|h/)
    expect(dauer).toHaveAttribute('aria-label', 'Dauer nicht gemessen')
  })
})
