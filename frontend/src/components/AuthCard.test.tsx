import { render, screen } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import { describe, expect, it } from 'vitest'
import { AuthCard } from './AuthCard'
import { cssRegel } from '../test/cssRegel'
import { EPIC_EDGE_WIDTH, theme } from '../theme'

describe('AuthCard', () => {
  const karte = () =>
    screen.getByText((_content, element) => element?.classList.contains('MuiPaper-root') === true)

  it('zeigt Titel und Inhalt', () => {
    render(
      <ThemeProvider theme={theme}>
        <AuthCard title="Anmelden"><p>Formular</p></AuthCard>
      </ThemeProvider>,
    )

    expect(screen.getByRole('heading', { name: 'Anmelden', level: 2 })).toBeInTheDocument()
    expect(screen.getByText('Formular')).toBeInTheDocument()
  })

  it('hebt über die linke Teal-Kante hervor statt über eine Elevation', () => {
    render(
      <ThemeProvider theme={theme}>
        <AuthCard title="Anmelden"><p>Formular</p></AuthCard>
      </ThemeProvider>,
    )

    // Seit #951 trägt das Theme CSS-Variablen. jsdom verwirft die Kurzschreibweise
    // `border: 1px solid var(…)` des Paper-Overrides und mit ihr die Randbreite im berechneten
    // Stil — die Farbe steht dort noch, die Breite wird an der erzeugten Regel geprüft.
    expect(karte()).toHaveStyle({ borderLeftColor: 'var(--mb-palette-primary-main)' })
    expect(cssRegel(karte())).toContain(`border-left: ${EPIC_EDGE_WIDTH}px solid`)
    // MuiPaper-elevation2 wäre der Schatten, den #653 herausnimmt.
    expect(karte().className).not.toMatch(/MuiPaper-elevation[1-9]/)
  })
})
