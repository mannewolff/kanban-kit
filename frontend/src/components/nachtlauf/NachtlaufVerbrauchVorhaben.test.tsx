import { ThemeProvider } from '@mui/material/styles'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { VerbrauchAngaben, VerbrauchVorhaben } from '../../api/nightRunUsage'
import { theme } from '../../theme'
import { NachtlaufVerbrauchVorhaben } from './NachtlaufVerbrauchVorhaben'

/**
 * Die Vorhaben-Aufstellung der Verbrauchs-Auswertung (Issue #942, #926 AK 10–12), seit #987 als
 * Balkenliste in der Platte „Vorhaben" nach `docs/mockup-nachtlauf-verbrauch.html`.
 */

const nichts: VerbrauchAngaben = {
  costUsd: null,
  inputTokens: null,
  outputTokens: null,
  cachedInputTokens: null,
  cachedInputSharePercent: null,
}

const vorhaben = (
  epicId: number,
  shortcode: string | null,
  title: string,
  costUsd: number | null,
  cardCount = 1,
): VerbrauchVorhaben => ({ epicId, shortcode, title, cardCount, usage: { ...nichts, costUsd } })

const ohne: VerbrauchVorhaben = {
  epicId: null,
  shortcode: null,
  title: null,
  cardCount: 2,
  usage: { ...nichts, costUsd: 1.25 },
}

const zeige = (epics: VerbrauchVorhaben[], overlap = false, withoutEpic = ohne) =>
  render(
    <ThemeProvider theme={theme}>
      <NachtlaufVerbrauchVorhaben epics={epics} withoutEpic={withoutEpic} epicsOverlap={overlap} />
    </ThemeProvider>,
  )

/** `Intl` setzt vor der Einheit ein geschütztes Leerzeichen; verglichen wird der Wortlaut. */
const lesbar = (element: HTMLElement) => element.textContent?.replaceAll(' ', ' ') ?? ''

describe('NachtlaufVerbrauchVorhaben', () => {
  it('steht als Platte „Vorhaben" mit den Kosten des Zeitraums', () => {
    zeige([vorhaben(1, 'PLANEN', 'Planen', 3)])

    const platte = screen.getByTestId('verbrauch-vorhaben')
    expect(within(platte).getByRole('heading', { name: 'Vorhaben' })).toBeInTheDocument()
    expect(platte).toHaveTextContent('Kosten im Zeitraum')
  })

  it('fuehrt die Vorhaben in der Reihenfolge des Servers — absteigend nach Kosten', () => {
    zeige([
      vorhaben(2, 'BACKUP', 'Backup', 5),
      vorhaben(1, 'PLANEN', 'Planen', 3, 2),
      vorhaben(3, null, 'Ohne Kürzel', null),
    ])

    const zeilen = within(screen.getByTestId('verbrauch-vorhaben-liste')).getAllByRole('listitem')
    expect(zeilen.map((z) => lesbar(z))).toEqual([
      expect.stringContaining('BACKUP · Backup'),
      expect.stringContaining('PLANEN · Planen'),
      expect.stringContaining('Ohne Kürzel'),
    ])
    expect(lesbar(zeilen[0])).toContain('5,00 $')
    expect(lesbar(zeilen[1])).toContain('2 Karten')
    expect(lesbar(zeilen[2])).toContain('1 Karte')
    expect(lesbar(zeilen[2])).toContain('nicht gemessen')
  })

  it('zeichnet je Vorhaben einen Balken im Verhaeltnis zum teuersten', () => {
    zeige([vorhaben(2, 'BACKUP', 'Backup', 5), vorhaben(1, 'PLANEN', 'Planen', 2)])

    const zeilen = within(screen.getByTestId('verbrauch-vorhaben-liste')).getAllByRole('listitem')
    expect(within(zeilen[0]).getByTestId('fuellung-100')).toBeInTheDocument()
    expect(within(zeilen[1]).getByTestId('fuellung-40')).toBeInTheDocument()
  })

  it('zeichnet ohne gemessene Kosten keinen Balken, statt 0 zu behaupten', () => {
    zeige([vorhaben(3, null, 'Ohne Kürzel', null)])

    const zeile = within(screen.getByTestId('verbrauch-vorhaben-liste')).getAllByRole('listitem')[0]
    expect(within(zeile).queryByTestId(/^fuellung-/)).not.toBeInTheDocument()
  })

  it('fuehrt „ohne Vorhaben" als eigenen Posten am Ende', () => {
    zeige([vorhaben(1, 'PLANEN', 'Planen', 3)])

    const posten = screen.getByTestId('verbrauch-ohne-vorhaben')
    expect(posten).toHaveTextContent('Ohne Vorhaben')
    expect(lesbar(posten)).toContain('1,25 $')
    expect(posten).toHaveTextContent('2 Karten')
  })

  it('laesst „ohne Vorhaben" auch ohne Karten nicht weg', () => {
    zeige([], false, { ...ohne, cardCount: 0, usage: nichts })

    expect(screen.getByTestId('verbrauch-ohne-vorhaben')).toHaveTextContent('0 Karten')
    expect(
      screen.getByText('In diesem Zeitraum ist keine Karte einem Vorhaben zugeordnet.'),
    ).toBeInTheDocument()
    expect(screen.queryByTestId('verbrauch-vorhaben-liste')).not.toBeInTheDocument()
  })

  it('sagt, dass die Aufstellung nur den kartenbezogenen Anteil zeigt', () => {
    zeige([vorhaben(1, 'PLANEN', 'Planen', 3)])

    expect(screen.getByTestId('verbrauch-vorhaben-anteil-hinweis')).toHaveTextContent(
      'nur den Anteil, der einzelnen Karten zugeordnet ist',
    )
  })

  it('sagt immer, dass sich die Vorhaben-Summen ueberschneiden koennen', () => {
    zeige([vorhaben(1, 'PLANEN', 'Planen', 3)], false)

    const hinweis = screen.getByTestId('verbrauch-vorhaben-ueberschneidung')
    expect(hinweis).toHaveTextContent('können sich überschneiden')
    expect(hinweis).not.toHaveTextContent('In diesem Zeitraum ist das der Fall.')
  })

  it('sagt es ausdruecklich, wenn sich die Summen in diesem Zeitraum ueberschneiden', () => {
    zeige([vorhaben(1, 'PLANEN', 'Planen', 3), vorhaben(2, 'BACKUP', 'Backup', 3)], true)

    expect(screen.getByTestId('verbrauch-vorhaben-anteil-hinweis')).toBeInTheDocument()
    expect(screen.getByTestId('verbrauch-vorhaben-ueberschneidung')).toHaveTextContent(
      'In diesem Zeitraum ist das der Fall.',
    )
  })
})
