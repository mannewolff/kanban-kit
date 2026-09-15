import { ThemeProvider } from '@mui/material/styles'
import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { CardByNumber } from '../../api/cards'
import { nachtlaufTheme } from '../../nachtlaufDesign'
import { NachtlaufKartenchips, type Kartenchip } from './NachtlaufKartenchips'

/**
 * Die entstandenen Karten eines Vorgangs als Chips (#916). Geprüft werden die drei Zustände, die
 * der Kartenkatalog unterscheidet, und die Leerauskunft.
 */

const karte = (number: number, title: string): CardByNumber =>
  ({ id: number, number, title }) as CardByNumber

const zeige = (chips: readonly Kartenchip[], onOeffnen = vi.fn()) => {
  render(
    <ThemeProvider theme={nachtlaufTheme}>
      <NachtlaufKartenchips
        chips={chips}
        leer="keine Pakete"
        testId="chips-791"
        onOeffnen={onOeffnen}
      />
    </ThemeProvider>,
  )
  return onOeffnen
}

describe('NachtlaufKartenchips', () => {
  it('zeigt eine geladene Karte als Verweis mit Arbeitsschritt und Nummer', () => {
    zeige([{ id: '844', art: 'Plan', zustand: { art: 'geladen', karte: karte(844, 'Der Plan') } }])

    const chip = screen.getByRole('button', { name: 'Plan #844 Der Plan' })
    // Sichtbar steht allein die Nummer; der Arbeitsschritt ist die kleine Vorzeile daneben.
    expect(chip).toHaveTextContent('Plan#844')
  })

  it('öffnet beim Anklicken den Kartendialog', () => {
    const ziel = karte(844, 'Der Plan')
    const onOeffnen = zeige([{ id: '844', art: 'Plan', zustand: { art: 'geladen', karte: ziel } }])

    fireEvent.click(screen.getByRole('button', { name: 'Plan #844 Der Plan' }))

    expect(onOeffnen).toHaveBeenCalledWith(ziel)
  })

  it('nennt eine nicht mehr vorhandene Karte beim Namen, statt sie wegzulassen', () => {
    zeige([{ id: '845', art: 'Paket', zustand: { art: 'fort' } }])

    expect(screen.getByTestId('chips-791')).toHaveTextContent('#845 nicht mehr vorhanden')
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('zeigt eine noch ladende Karte als reine Nummer, nicht als „nicht mehr vorhanden"', () => {
    // Eine ladende Karte als fort zu zeigen wäre eine Falschaussage, die sich Sekunden später
    // selbst widerlegt.
    zeige([{ id: '846', art: 'Plan', zustand: { art: 'laedt' } }])

    const chips = within(screen.getByTestId('chips-791'))
    expect(chips.getByText('#846')).toBeInTheDocument()
    expect(screen.getByTestId('chips-791')).not.toHaveTextContent('nicht mehr vorhanden')
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('benennt einen Arbeitsschritt ohne entstandene Karte', () => {
    zeige([])

    expect(screen.getByTestId('chips-791')).toHaveTextContent('keine Pakete')
  })

  it('zeigt mehrere Karten nebeneinander', () => {
    zeige([
      { id: '846', art: 'Plan', zustand: { art: 'geladen', karte: karte(846, 'Plan B') } },
      { id: '847', art: 'Paket', zustand: { art: 'geladen', karte: karte(847, 'Paket B') } },
      { id: '848', art: 'Paket', zustand: { art: 'geladen', karte: karte(848, 'Paket C') } },
    ])

    expect(within(screen.getByTestId('chips-791')).getAllByRole('button')).toHaveLength(3)
  })
})
