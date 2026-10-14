import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { de } from '../i18n/de'
import { epicColor, epicTint } from '../lib/epicMeta'
import { TabletQuerProvider } from '../lib/tabletQuer'
import { setzeMedien } from '../test/setup'
import { EpicBadge } from './EpicBadge'
import { renderMitSprache } from '../i18n/renderMitSprache'

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

describe('EpicBadge auf dem Tablet im Querformat (Plan #1707, E7)', () => {
  it('öffnet am Kennzeichen ohne Aktion die Erklärung per Antippen, nicht die Ebene ringsum', async () => {
    setzeMedien({ tabletQuer: true })
    const aussen = vi.fn()
    render(
      <TabletQuerProvider>
        <div role="presentation" onClick={aussen}>
          <EpicBadge epicId={9} title="Authentifizierung" shortcode="AUT" />
        </div>
      </TabletQuerProvider>,
    )
    const badge = screen.getByLabelText('Vorhaben AUT')
    expect(badge).not.toHaveAttribute('title')

    fireEvent.click(badge)
    expect(screen.getByRole('tooltip')).toHaveTextContent('Authentifizierung')
    expect(aussen).not.toHaveBeenCalled()

    fireEvent.click(badge)
    await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument())
  })

  it('setzt an den Vorhaben-Knopf kein Info-Symbol; Antippen zeigt das Vorhaben wie heute', () => {
    setzeMedien({ tabletQuer: true })
    const onOpen = vi.fn()
    render(
      <TabletQuerProvider>
        <EpicBadge epicId={9} title="Authentifizierung" shortcode="AUT" onOpen={onOpen} />
      </TabletQuerProvider>,
    )

    expect(screen.getAllByRole('button')).toHaveLength(1)
    expect(screen.queryByRole('button', { name: de.ERKLAERUNG_INFO.ZEIGEN('Authentifizierung') })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Vorhaben AUT öffnen' }))

    expect(onOpen).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Vorhaben AUT öffnen' })).toHaveAttribute('title', 'Authentifizierung')
  })

  it('trägt am Desktop das native title wie heute und öffnet per Klick nichts', () => {
    render(
      <TabletQuerProvider>
        <EpicBadge epicId={9} title="Authentifizierung" shortcode="AUT" />
      </TabletQuerProvider>,
    )
    const badge = screen.getByLabelText('Vorhaben AUT')

    expect(badge).toHaveAttribute('title', 'Authentifizierung')
    fireEvent.click(badge)
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
  })
})

describe('EpicBadge auf Englisch', () => {
  it('benennt Anzeige und Bedienelement auf Englisch', () => {
    const { unmount } = renderMitSprache(<EpicBadge epicId={3} title="Login" shortcode="LOG" />, 'en')
    expect(screen.getByLabelText('Epic LOG')).toBeInTheDocument()
    unmount()

    renderMitSprache(<EpicBadge epicId={3} title="Login" shortcode="LOG" onOpen={vi.fn()} />, 'en')
    expect(screen.getByRole('button', { name: 'Open epic LOG' })).toBeInTheDocument()
  })
})
