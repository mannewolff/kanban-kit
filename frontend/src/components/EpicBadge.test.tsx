import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { epicColor, epicTint } from '../lib/epicMeta'
import { EpicBadge } from './EpicBadge'

describe('EpicBadge', () => {
  // Bis #952 stand hier `${hue}22` — Hexwert plus Alpha-Suffix. Seit die Vorhaben-Farben
  // Variablen-Verweise sind, ergäbe das `var(--mb-palette-epic-N-hue)22`: ungültiges CSS, und die
  // getönte Fläche verschwände still. Die Fläche nutzt deshalb einen eigenen Tint-Wert.
  it.each([
    ['als reine Anzeige', undefined, 'Vorhaben AUT'],
    ['als Knopf', vi.fn(), 'Vorhaben AUT öffnen'],
  ])('tönt die Fläche %s über den eigenen Tint-Wert, nicht über ein Alpha-Suffix', (_, onOpen, name) => {
    render(<EpicBadge epicId={9} title="Authentifizierung" shortcode="AUT" onOpen={onOpen} />)

    const badge = screen.getByLabelText(name)
    expect(badge).toHaveStyle({ backgroundColor: epicTint(9) })
    expect(epicTint(9)).toMatch(/^var\(--mb-palette-epic-\d-tint\)$/)
    // Punkt und Kürzel tragen den Farbton, die Fläche den Tint — beide aus demselben Palettenplatz.
    expect(screen.getByText('AUT')).toHaveStyle({ color: epicColor(9) })
  })

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

  it.each(['{Enter}', '{ }'])('hält den auslösenden Tastendruck %s von der Ebene ringsum fern', async (taste) => {
    const onOpen = vi.fn()
    const aussen = vi.fn()
    const user = userEvent.setup()
    render(
      <div role="presentation" onKeyDown={aussen}>
        <EpicBadge epicId={9} title="Authentifizierung" shortcode="AUT" onOpen={onOpen} />
      </div>,
    )

    await user.tab()
    await user.keyboard(taste)

    expect(aussen).not.toHaveBeenCalled()
  })

  it('lässt andere Tasten zur Ebene ringsum durch', async () => {
    const aussen = vi.fn()
    const user = userEvent.setup()
    render(
      <div role="presentation" onKeyDown={aussen}>
        <EpicBadge epicId={9} title="Authentifizierung" shortcode="AUT" onOpen={vi.fn()} />
      </div>,
    )

    await user.tab()
    await user.keyboard('{Escape}')

    expect(aussen).toHaveBeenCalledTimes(1)
  })
})
