import { ThemeProvider } from '@mui/material/styles'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { NACHTLAUF_FARBEN, nachtlaufTheme } from '../../nachtlaufDesign'
import { NachtlaufKennzahlen, type NachtlaufKennzahl } from './NachtlaufKennzahlen'

/**
 * Die Kennzahlenreihe der Nacht (#915, AK 3) — die größte Zahlendarstellung der Seite, zwischen
 * zwei Haarlinien. Sie stellt dar und rechnet nicht: Die Werte kommen fertig von der Seite, wo sie
 * schon vor diesem Paket gebildet wurden (Nicht-Ziel 3, keine neuen Kennzahlen).
 */

const KETTE: readonly NachtlaufKennzahl[] = [
  { wert: '2 von 3', label: 'Ketten durchgelaufen', hinweis: null },
  { wert: '4', label: 'Karten entstanden', hinweis: null },
  { wert: '1:16 h', label: 'Laufzeit über alle Stufen', hinweis: null },
  { wert: '25,98 $', label: 'Kosten der Nacht', hinweis: 'eine Session ohne Kostenmeldung' },
]

const zeige = (eigenschaften: Parameters<typeof NachtlaufKennzahlen>[0]) =>
  render(
    <ThemeProvider theme={nachtlaufTheme}>
      <NachtlaufKennzahlen {...eigenschaften} />
    </ThemeProvider>,
  )

describe('NachtlaufKennzahlen', () => {
  it('zeigt jede Kennzahl mit Wert und Benennung', () => {
    zeige({ kennzahlen: KETTE })

    const reihe = screen.getByTestId('nachtlauf-kennzahlen')
    for (const kennzahl of KETTE) {
      expect(within(reihe).getByText(kennzahl.wert)).toBeInTheDocument()
      expect(within(reihe).getByText(kennzahl.label)).toBeInTheDocument()
    }
  })

  it('stellt den Vorbehalt fehlender Kostenmeldungen unter den Wert (AK 9, Fall 1)', () => {
    zeige({ kennzahlen: KETTE })

    const vermerk = screen.getByText('eine Session ohne Kostenmeldung')
    expect(vermerk).toBeInTheDocument()
    // Der Vorbehalt trägt den Warnton des Entwurfs — er ist eine Einschränkung des Werts
    // darüber, keine weitere Beschriftung.
    expect(getComputedStyle(vermerk).color).toBe(alsRgb(NACHTLAUF_FARBEN.budget))
  })

  it('zeigt an einer Kennzahl ohne Vorbehalt keine leere Zeile', () => {
    zeige({ kennzahlen: [{ wert: '369', label: 'Züge des Modells', hinweis: null }] })

    const kennzahl = screen.getByTestId('nachtlauf-kennzahl-Züge des Modells')
    expect(within(kennzahl).getAllByText(/./)).toHaveLength(2)
  })

  it('setzt die Werte in die Schrift und Größe des Entwurfs', () => {
    zeige({ kennzahlen: KETTE })

    const stil = getComputedStyle(screen.getByText('2 von 3'))
    expect(stil.fontFamily).toContain('Chivo')
    expect(stil.fontSize).toBe('24px')
  })

  it('zeigt statt Kosten und Zügen den Hinweis des Laufs, wenn er einen trägt', () => {
    // Plan #864, E8: Ohne die ausführliche Ausgabe fordert der Runner den Kennzahlen-Strom gar
    // nicht an. „Kosten unbekannt" behauptete dann ein Fehlen, wo nur nichts angefordert wurde.
    zeige({
      kennzahlen: KETTE.slice(0, 2),
      hinweis: 'Kennzahlen nicht angefordert',
    })

    expect(screen.getByTestId('nachtlauf-kennzahlen-hinweis')).toHaveTextContent(
      'Kennzahlen nicht angefordert',
    )
  })

  it('zeigt ohne Hinweis keine Hinweiszeile', () => {
    zeige({ kennzahlen: KETTE })
    expect(screen.queryByTestId('nachtlauf-kennzahlen-hinweis')).not.toBeInTheDocument()
  })
})

/** `getComputedStyle` gibt Farben als `rgb(…)` zurück, die Tokens stehen als `#rrggbb`. */
function alsRgb(hex: string): string {
  const kanal = (position: number) => Number.parseInt(hex.slice(position, position + 2), 16)
  return `rgb(${kanal(1)}, ${kanal(3)}, ${kanal(5)})`
}
