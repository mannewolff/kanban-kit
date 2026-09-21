import { ThemeProvider } from '@mui/material/styles'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { VerbrauchAngaben, VerbrauchStufe } from '../../api/nightRunUsage'
import type { NightRunStage } from '../../api/nightRuns'
import { theme } from '../../theme'
import { NachtlaufVerbrauchStufen } from './NachtlaufVerbrauchStufen'

/** Die Aufstellung je Stufe der Kette (Issue #1117, #993 AK 8) nach dem Muster der Vorhaben. */

const nichts: VerbrauchAngaben = {
  costUsd: null,
  inputTokens: null,
  outputTokens: null,
  cachedInputTokens: null,
  cachedInputSharePercent: null,
}

const stufe = (
  stage: NightRunStage,
  costUsd: number | null,
  itemCount = 1,
  durationMs: number | null = 60_000,
): VerbrauchStufe => ({ stage, itemCount, durationMs, usage: { ...nichts, costUsd } })

const zeige = (stufen: VerbrauchStufe[]) =>
  render(
    <ThemeProvider theme={theme}>
      <NachtlaufVerbrauchStufen stufen={stufen} />
    </ThemeProvider>,
  )

const lesbar = (element: HTMLElement) => element.textContent ?? ''

/**
 * `Intl` setzt vor dem Währungszeichen ein geschütztes Leerzeichen — welches, hängt an der
 * ICU-Fassung. Geprüft wird deshalb mit `\s` statt mit einem festen Zeichen im Literal.
 */
const alsBetrag = (zahl: string) => new RegExp(`${zahl}\\s\\$`)

describe('NachtlaufVerbrauchStufen', () => {
  it('steht als Platte „Stufen der Kette"', () => {
    zeige([stufe('PLAN', 3)])

    const platte = screen.getByTestId('verbrauch-stufen')
    expect(within(platte).getByRole('heading', { name: 'Stufen der Kette' })).toBeInTheDocument()
    expect(platte).toHaveTextContent('Kosten je Stufe')
  })

  it('nennt die vier Stufen mit ihren Kosten und der Zahl der Vorgaenge (AK 8)', () => {
    zeige([
      stufe('PLAN', 5, 2),
      stufe('REVIEW', 3, 1),
      stufe('PAKETE', 1.25, 4),
      stufe('ABDECKUNG', 0.5, 1),
    ])

    const zeilen = within(screen.getByTestId('verbrauch-stufen-liste')).getAllByRole('listitem')
    expect(zeilen.map((z) => lesbar(z))).toEqual([
      expect.stringContaining('Plan'),
      expect.stringContaining('Prüfung'),
      expect.stringContaining('Pakete'),
      expect.stringContaining('Abdeckung'),
    ])
    expect(lesbar(zeilen[0])).toMatch(alsBetrag('5,00'))
    expect(lesbar(zeilen[0])).toContain('2 Vorgänge')
    expect(lesbar(zeilen[2])).toMatch(alsBetrag('1,25'))
    expect(lesbar(zeilen[2])).toContain('4 Vorgänge')
    expect(lesbar(zeilen[3])).toContain('1 Vorgang')
  })

  it('folgt der Reihenfolge des Servers, ohne nachzusortieren', () => {
    zeige([stufe('PAKETE', 1), stufe('PLAN', 9)])

    const zeilen = within(screen.getByTestId('verbrauch-stufen-liste')).getAllByRole('listitem')
    expect(zeilen.map((z) => lesbar(z))).toEqual([
      expect.stringContaining('Pakete'),
      expect.stringContaining('Plan'),
    ])
  })

  it('zeigt ohne gemeldete Kosten die Fehlanzeige statt 0 (E16)', () => {
    zeige([stufe('PLAN', null)])

    const zeile = within(screen.getByTestId('verbrauch-stufen-liste')).getAllByRole('listitem')[0]
    expect(lesbar(zeile)).toContain('nicht gemessen')
    expect(lesbar(zeile)).not.toMatch(alsBetrag('0,00'))
  })

  it('zeichnet ohne gemessene Kosten keinen Balken, statt 0 zu behaupten', () => {
    zeige([stufe('PLAN', null)])

    const zeile = within(screen.getByTestId('verbrauch-stufen-liste')).getAllByRole('listitem')[0]
    expect(within(zeile).queryByTestId(/^fuellung-/)).not.toBeInTheDocument()
  })

  it('zeichnet je Stufe einen Balken im Verhaeltnis zur teuersten', () => {
    zeige([stufe('PLAN', 5), stufe('REVIEW', 2)])

    const zeilen = within(screen.getByTestId('verbrauch-stufen-liste')).getAllByRole('listitem')
    expect(within(zeilen[0]).getByTestId('fuellung-100')).toBeInTheDocument()
    expect(within(zeilen[1]).getByTestId('fuellung-40')).toBeInTheDocument()
  })

  it('zeigt die Aufstellung ohne Stufen gar nicht (E6)', () => {
    const { container } = zeige([])

    expect(screen.queryByTestId('verbrauch-stufen')).not.toBeInTheDocument()
    expect(container).toBeEmptyDOMElement()
  })

  it('fuehrt nie eine Zeile „ohne Stufe" (E6)', () => {
    zeige([stufe('PLAN', 5), stufe('REVIEW', 2)])

    expect(screen.queryByText(/ohne Stufe/i)).not.toBeInTheDocument()
    expect(
      within(screen.getByTestId('verbrauch-stufen-liste')).getAllByRole('listitem'),
    ).toHaveLength(2)
  })
})
