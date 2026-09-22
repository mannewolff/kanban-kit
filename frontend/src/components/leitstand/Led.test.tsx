import { ThemeProvider } from '@mui/material/styles'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { cssRegel } from '../../test/cssRegel'
import { theme } from '../../theme'
import { Led } from './LeitstandBausteine'

const zeige = (pulsiert: boolean) =>
  render(
    <ThemeProvider theme={theme}>
      <Led melder="stahl" pulsiert={pulsiert} />
    </ThemeProvider>,
  )

describe('Led — Wechselblinker eines laufenden Laufs (#1136)', () => {
  it('zeichnet einen laufenden Lauf mit zwei Lampen, einen ruhenden mit einer', () => {
    zeige(true)
    expect(screen.getAllByTestId('blinker-lampe')).toHaveLength(2)
    expect(screen.getByTestId('led-stahl')).toHaveAttribute('data-puls', 'an')
  })

  it('zeichnet ohne Lauf eine einzelne Lampe ohne Blinker', () => {
    zeige(false)
    expect(screen.queryAllByTestId('blinker-lampe')).toHaveLength(0)
    expect(screen.getByTestId('led-stahl')).toHaveAttribute('data-puls', 'aus')
  })

  it('lässt beide Lampen im selben 1-s-Takt hart umschlagen, die zweite um eine halbe Periode versetzt', () => {
    zeige(true)
    const [links, rechts] = screen.getAllByTestId('blinker-lampe').map(cssRegel)

    for (const regel of [links, rechts]) {
      expect(regel).toMatch(/animation: \S+ 1s step-end infinite/)
    }
    expect(links).toContain('animation-delay: 0s')
    expect(rechts).toContain('animation-delay: -0.5s')
  })

  it('steht ohne Bewegung still, links hell und rechts in der Melderfarbe', () => {
    zeige(true)
    const [links, rechts] = screen.getAllByTestId('blinker-lampe').map(cssRegel)

    expect(links).toMatch(/background-color: var\(--[\w-]*blinkerHell[,)]/)
    expect(rechts).toMatch(/background-color: var\(--[\w-]*melder-stahl[,)]/)
  })
})
