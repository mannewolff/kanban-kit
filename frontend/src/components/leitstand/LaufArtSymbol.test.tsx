import { ThemeProvider } from '@mui/material/styles'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { theme } from '../../theme'
import { LaufArtSymbol, laufArtName, type LaufArt } from './LaufArtSymbol'

const ARTEN: ReadonlyArray<readonly [LaufArt, string]> = [
  ['IMPLEMENTATION', 'Umsetzung'],
  ['CHAIN', 'Kette'],
  ['REVIEW', 'Prüfung'],
  ['INTERACTIVE', 'Sitzung'],
  ['NIGHTPLAN', 'Nachtplan'],
]

const zeige = (art: LaufArt) =>
  render(
    <ThemeProvider theme={theme}>
      <LaufArtSymbol art={art} />
    </ThemeProvider>,
  )

describe('LaufArtSymbol — die Art eines Laufs als Symbol (#1141)', () => {
  it.each(ARTEN)('zeichnet %s als Symbol mit dem Wort als zugänglichem Namen', (art, wort) => {
    zeige(art)

    const symbol = screen.getByTestId(`art-${art}`)
    expect(symbol).toHaveAttribute('role', 'img')
    expect(symbol).toHaveAccessibleName(wort)
  })

  it('gibt jeder Art dieselbe feste Breite, damit die Zeile nicht flattert', () => {
    const breiten = ARTEN.map(([art]) => {
      const { unmount } = zeige(art)
      const breite = screen.getByTestId(`art-${art}`).getAttribute('data-breite')
      unmount()
      return breite
    })

    expect(breiten).toHaveLength(ARTEN.length)
    expect(new Set(breiten).size).toBe(1)
    expect(breiten[0]).toBe('16')
  })

  it('nennt das Wort jeder Art aus einem einzigen Wörterbuch', () => {
    for (const [art, wort] of ARTEN) {
      expect(laufArtName(art)).toBe(wort)
    }
  })
})
