import { render, screen } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import { describe, expect, it } from 'vitest'
import { AuthCard } from './AuthCard'
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

    expect(karte()).toHaveStyle({
      borderLeftWidth: `${EPIC_EDGE_WIDTH}px`,
      borderLeftColor: theme.palette.primary.main,
    })
    // MuiPaper-elevation2 wäre der Schatten, den #653 herausnimmt.
    expect(karte().className).not.toMatch(/MuiPaper-elevation[1-9]/)
  })
})
