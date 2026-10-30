import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { StrengeMarke } from './StrengeMarke'

describe('StrengeMarke — die Strenge-Stufe des Kits (#2014)', () => {
  it.each([
    ['light', 'Leicht'],
    ['normal', 'Normal'],
    ['strict', 'Streng'],
  ] as const)('zeigt %s als „%s"', (stufe, name) => {
    renderMitSprache(<StrengeMarke stufe={stufe} />)

    const marke = screen.getByTestId('strenge-marke')
    expect(marke).toHaveTextContent(name)
    expect(marke).toHaveAccessibleName(`Strenge, die das Projekt im Kit gewählt hat: ${name}`)
  })

  it('zeigt beim Darüberfahren den Hinweis', async () => {
    renderMitSprache(<StrengeMarke stufe="normal" />)

    await userEvent.hover(screen.getByTestId('strenge-marke'))

    expect(await screen.findByRole('tooltip')).toHaveTextContent(
      'Strenge, die das Projekt im Kit gewählt hat',
    )
  })

  it('zeigt auf Englisch die englischen Namen', () => {
    renderMitSprache(<StrengeMarke stufe="strict" />, 'en')

    expect(screen.getByTestId('strenge-marke')).toHaveTextContent('Strict')
  })

  it.each([null, undefined])('rendert bei %s nichts', (stufe) => {
    const { container } = renderMitSprache(<StrengeMarke stufe={stufe} />)

    expect(screen.queryByTestId('strenge-marke')).not.toBeInTheDocument()
    expect(container).toBeEmptyDOMElement()
  })
})
