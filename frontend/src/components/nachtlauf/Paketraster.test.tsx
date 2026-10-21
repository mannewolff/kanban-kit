import { ThemeProvider } from '@mui/material/styles'
import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { PackageProgressView, PackageState } from '../../api/nightRuns'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { kontrast } from '../../lib/kontrast'
import { rasterzustand, type Rasterfarbe } from '../../lib/laufFortschritt'
import { nachtlaufTheme } from '../../nachtlaufDesign'
import { theme } from '../../theme'
import { Paketraster } from './Paketraster'

/**
 * Das Paketraster einer Kettenzeile (Issue #1886, Plan #1882 E6): je Paket ein Quadrat, dessen Form
 * den Zustand trägt, mit dem Zustand als zugänglichem Namen und Tooltip; ein Klick öffnet die Karte.
 */

const paket = (number: number, zustand: PackageState, title = `Karte ${number}`): PackageProgressView => ({
  karte: { number, title, boardId: 7 },
  zustand,
})

const ALLE: readonly PackageProgressView[] = [
  paket(201, 'ANGELEGT'),
  paket(202, 'GEZOGEN'),
  paket(203, 'IN_UMSETZUNG'),
  paket(204, 'FERTIG', 'Bezugsstand in kit/board/fassung.mjs'),
  paket(205, 'ZURUECKGESTELLT'),
]

const zeige = (pakete: readonly PackageProgressView[]) => {
  const onKarteOeffnen = vi.fn()
  render(
    <ThemeProvider theme={nachtlaufTheme}>
      <Paketraster pakete={pakete} name="Pakete der Kette #100" onKarteOeffnen={onKarteOeffnen} />
    </ThemeProvider>,
  )
  return { onKarteOeffnen }
}

const raster = () => within(screen.getByRole('list', { name: 'Pakete der Kette #100' }))

describe('Paketraster', () => {
  it('zeigt je Paketzustand ein Quadrat mit Nummer, Zustand und Titel als zugänglichem Namen', () => {
    zeige(ALLE)

    expect(raster().getAllByRole('button').map((b) => b.getAttribute('aria-label'))).toEqual([
      'Paket #201, angelegt: Karte 201',
      'Paket #202, gezogen: Karte 202',
      'Paket #203, in Umsetzung: Karte 203',
      'Paket #204, fertig: Bezugsstand in kit/board/fassung.mjs',
      'Paket #205, zurückgestellt: Karte 205',
    ])
  })

  it('trägt die Form des Zustands am Quadrat, nicht allein die Farbe', () => {
    zeige(ALLE)

    expect(raster().getAllByRole('button').map((b) => b.dataset.form)).toEqual([
      'umriss',
      'umriss',
      'schraffiert',
      'gefuellt',
      'gefuellt',
    ])
    expect(raster().getByRole('button', { name: /^Paket #205/ })).toHaveAttribute('data-querstrich', 'true')
    expect(raster().getByRole('button', { name: /^Paket #204/ })).toHaveAttribute('data-querstrich', 'false')
  })

  it('zeigt den Zustand als Tooltip bei Mausberührung', async () => {
    const user = userEvent.setup()
    zeige(ALLE)

    await user.hover(raster().getByRole('button', { name: /^Paket #203/ }))

    expect(await screen.findByRole('tooltip')).toHaveTextContent('Paket #203, in Umsetzung: Karte 203')
  })

  it('zeigt den Tooltip auch beim Tastaturfokus und trägt dort einen sichtbaren Fokusring', async () => {
    const user = userEvent.setup()
    zeige(ALLE)

    await user.tab()

    const erstes = raster().getByRole('button', { name: /^Paket #201/ })
    expect(erstes).toHaveFocus()
    expect(erstes).toHaveClass('Mui-focusVisible')
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Paket #201, angelegt: Karte 201')
  })

  it('öffnet die Karte per Klick und per Tastatur', async () => {
    const user = userEvent.setup()
    const { onKarteOeffnen } = zeige(ALLE)

    fireEvent.click(raster().getByRole('button', { name: /^Paket #204/ }))
    await user.tab()
    await user.keyboard('{Enter}')

    expect(onKarteOeffnen.mock.calls).toEqual([[204], [201]])
  })

  it('zählt die Zustände unter dem Raster in der Reihenfolge der Zustände', () => {
    zeige([paket(1, 'ANGELEGT'), paket(2, 'FERTIG'), paket(3, 'FERTIG'), paket(4, 'IN_UMSETZUNG')])

    expect(screen.getByTestId('raster-anzahl')).toHaveTextContent('2 fertig · 1 in Umsetzung · 1 angelegt')
  })

  it('benennt ein leeres Raster', () => {
    zeige([])

    expect(screen.queryByRole('list')).not.toBeInTheDocument()
    expect(screen.getByText('noch keine Pakete')).toBeInTheDocument()
  })

  it('spricht Englisch', () => {
    renderMitSprache(
      <ThemeProvider theme={nachtlaufTheme}>
        <Paketraster pakete={[paket(203, 'IN_UMSETZUNG')]} name="Packages" onKarteOeffnen={vi.fn()} />
      </ThemeProvider>,
      'en',
    )

    expect(screen.getByRole('button', { name: 'Package #203, in implementation: Karte 203' })).toBeInTheDocument()
    expect(screen.getByTestId('raster-anzahl')).toHaveTextContent('1 in implementation')
  })
})

describe('Kontrast der Formzustände (Plan #1882 E6, WCAG 1.4.11)', () => {
  const schemata = [
    ['hell', theme.colorSchemes.light!.palette],
    ['dunkel', theme.colorSchemes.dark!.palette],
  ] as const

  const ZUSTAENDE: readonly PackageState[] = ['ANGELEGT', 'GEZOGEN', 'IN_UMSETZUNG', 'FERTIG', 'ZURUECKGESTELLT']

  it.each(schemata)('%s: jede Rasterfarbe, jeder Stufenpunkt und der gestrichelte Umriss halten 3:1 auf der Platte', (_, palette) => {
    const farbe: Record<Rasterfarbe, string> = {
      gruen: palette.melder.gruen,
      stahl: palette.melder.stahl,
      zinnob: palette.melder.zinnob,
      'text-schwach': palette.warte.textSchwach,
    }
    const platte = palette.warte.platte
    for (const zustand of ZUSTAENDE) {
      expect(kontrast(farbe[rasterzustand(zustand).farbe], platte)).toBeGreaterThanOrEqual(3)
    }
    // Stufenpunkte: Haken „erreicht" in Grün, „läuft" in Kupfer, Umriss „offen" in „Text schwach".
    expect(kontrast(palette.melder.gruen, platte)).toBeGreaterThanOrEqual(3)
    expect(kontrast(palette.primary.main, platte)).toBeGreaterThanOrEqual(3)
    expect(kontrast(palette.warte.textSchwach, platte)).toBeGreaterThanOrEqual(3)
  })
})
