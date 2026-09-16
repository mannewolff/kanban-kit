import { ThemeProvider } from '@mui/material/styles'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { VerbrauchAngaben, VerbrauchVorhaben } from '../../api/nightRunUsage'
import { nachtlaufTheme } from '../../nachtlaufDesign'
import { NachtlaufVerbrauchVorhaben } from './NachtlaufVerbrauchVorhaben'

/** Die Vorhaben-Aufstellung der Verbrauchs-Auswertung (Issue #942, #926 AK 10–12). */

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
    <ThemeProvider theme={nachtlaufTheme}>
      <NachtlaufVerbrauchVorhaben epics={epics} withoutEpic={withoutEpic} epicsOverlap={overlap} />
    </ThemeProvider>,
  )

const lesbar = (element: HTMLElement) => element.textContent?.replaceAll(' ', ' ') ?? ''

describe('NachtlaufVerbrauchVorhaben', () => {
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

  it('fuehrt „ohne Vorhaben" als eigenen Posten', () => {
    zeige([vorhaben(1, 'PLANEN', 'Planen', 3)])

    const posten = screen.getByTestId('verbrauch-ohne-vorhaben')
    expect(posten).toHaveTextContent('Ohne Vorhaben')
    expect(lesbar(posten)).toContain('1,25 $')
    expect(posten).toHaveTextContent('2 Karten')
  })

  it('laesst „ohne Vorhaben" auch ohne Karten nicht weg', () => {
    zeige([], false, { ...ohne, cardCount: 0, usage: nichts })

    expect(screen.getByTestId('verbrauch-ohne-vorhaben')).toHaveTextContent('0 Karten')
    expect(screen.getByText('In diesem Zeitraum ist keine Karte einem Vorhaben zugeordnet.')).toBeInTheDocument()
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
