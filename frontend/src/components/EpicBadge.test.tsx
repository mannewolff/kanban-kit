import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { EpicBadge } from './EpicBadge'

describe('EpicBadge', () => {
  it('bleibt ohne onOpen reine Anzeige', () => {
    render(<EpicBadge epicId={9} title="Authentifizierung" shortcode="AUT" />)

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Vorhaben AUT')).toBeInTheDocument()
  })

  it('rendert mit onOpen einen Knopf mit sprechendem Namen', () => {
    render(<EpicBadge epicId={9} title="Authentifizierung" shortcode="AUT" onOpen={vi.fn()} />)

    expect(screen.getByRole('button', { name: 'Vorhaben AUT öffnen' })).toBeInTheDocument()
  })

  it('löst beim Klick nur onOpen aus, nicht den Handler ringsum', async () => {
    const onOpen = vi.fn()
    const aussen = vi.fn()
    const user = userEvent.setup()
    render(
      // role="presentation" statt eines nackten onClick-div: Der Wrapper steht hier nur als
      // Klickfänger der umgebenden Ebene (Kachel, Listenzeile), nicht als Bedienelement.
      <div role="presentation" onClick={aussen}>
        <EpicBadge epicId={9} title="Authentifizierung" shortcode="AUT" onOpen={onOpen} />
      </div>,
    )

    await user.click(screen.getByRole('button', { name: 'Vorhaben AUT öffnen' }))

    expect(onOpen).toHaveBeenCalledTimes(1)
    expect(aussen).not.toHaveBeenCalled()
  })

  it('löst onOpen per Enter auf dem fokussierten Knopf aus', async () => {
    const onOpen = vi.fn()
    const user = userEvent.setup()
    render(<EpicBadge epicId={9} title="Authentifizierung" shortcode="AUT" onOpen={onOpen} />)

    await user.tab()
    expect(screen.getByRole('button', { name: 'Vorhaben AUT öffnen' })).toHaveFocus()
    await user.keyboard('{Enter}')

    expect(onOpen).toHaveBeenCalledTimes(1)
  })
})
