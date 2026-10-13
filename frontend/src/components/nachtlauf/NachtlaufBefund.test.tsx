import { ThemeProvider } from '@mui/material/styles'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { theme } from '../../theme'
import { NachtlaufBefund } from './NachtlaufBefund'

/** Der Befund eines Abbruchs mit seinem Kopierknopf (#988), seit Issue #1658 in beiden Sprachen. */

const BEFUND = 'Befund des Runs zu Karte #700 Paket A\nAuszug: HARTER STOPP'

describe('NachtlaufBefund', () => {
  it('zeigt den Text neben dem Knopf und kopiert per Klick', async () => {
    const onKopieren = vi.fn()
    render(
      <ThemeProvider theme={theme}>
        <NachtlaufBefund cardNumber={700} text={BEFUND} onKopieren={onKopieren} />
      </ThemeProvider>,
    )

    expect(screen.getByLabelText('Übernahmetext zu Karte #700')).toHaveTextContent('Auszug: HARTER STOPP')
    await userEvent.click(screen.getByRole('button', { name: 'Übernahmetext zu Karte #700 kopieren' }))
    expect(onKopieren).toHaveBeenCalledTimes(1)
  })

  it('beschriftet Kasten und Knopf auf Englisch, der Text bleibt, wie er ist', () => {
    renderMitSprache(
      <ThemeProvider theme={theme}>
        <NachtlaufBefund cardNumber={700} text={BEFUND} onKopieren={vi.fn()} />
      </ThemeProvider>,
      'en',
    )

    expect(screen.getByLabelText('Handoff text for card #700')).toHaveTextContent('Auszug: HARTER STOPP')
    expect(screen.getByRole('button', { name: 'Copy handoff text for card #700' })).toHaveTextContent('Copy')
  })
})
