import { fireEvent, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { HerkunftsGlied } from '../../api/cards'
import { de } from '../../i18n/de'
import { en } from '../../i18n/en'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { TabletQuerProvider } from '../../lib/tabletQuer'
import { setzeMedien } from '../../test/setup'
import { KartenHerkunft } from './KartenHerkunft'

const KETTE: HerkunftsGlied[] = [
  { number: 1914, title: '[Plan] Ansicht als XY', archived: true, luecke: null },
  { number: 1912, title: null, archived: false, luecke: 'PAPIERKORB' },
  { number: 1910, title: '[Fachlich] Ansicht als XY', archived: false, luecke: null },
  { number: 1900, title: null, archived: false, luecke: 'GELOESCHT' },
]

/** Die Nummer in einer Attrappe der Kachel, deren Klick die Karte öffnet. */
function zeigen(herkunft: HerkunftsGlied[] | null, onOpen = vi.fn(), sprache: 'de' | 'en' = 'de') {
  renderMitSprache(
    <TabletQuerProvider>
      {/* eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions -- Attrappe der Kachel, nur ihr Klick zählt */}
      <div onClick={onOpen}>
        <KartenHerkunft number={1930} herkunft={herkunft} />
      </div>
    </TabletQuerProvider>,
    sprache,
  )
  return onOpen
}

const nummer = () => screen.getByText('#1930')

/** Die Zeilen des Hinweises in ihrer Reihenfolge. */
async function zeilen() {
  const tooltip = await screen.findByRole('tooltip')
  return Array.from(tooltip.querySelectorAll('p')).map((p) => p.textContent)
}

describe('KartenHerkunft', () => {
  it('zeigt beim Überfahren die Kette, nächster Vorfahr zuerst, mit Lücken und Archiv-Vermerk', async () => {
    zeigen(KETTE)

    await userEvent.hover(nummer())

    expect(await zeilen()).toEqual([
      de.BOARD.HERKUNFT_TITEL,
      `#1914 · [Plan] Ansicht als XY ${de.BOARD.HERKUNFT_ARCHIVIERT}`,
      '#1912 (im Papierkorb)',
      '#1910 · [Fachlich] Ansicht als XY',
      '#1900 (gelöscht)',
    ])
    expect(nummer()).toHaveAttribute('aria-describedby', (await screen.findByRole('tooltip')).id)
  })

  it('zeigt den Hinweis beim Tastaturfokus', async () => {
    zeigen(KETTE)

    await userEvent.tab()

    expect(nummer()).toHaveFocus()
    expect(await zeilen()).toContain('#1910 · [Fachlich] Ansicht als XY')
  })

  it('zeigt „Ohne Herkunft“ bei einer leeren Kette', async () => {
    zeigen([])

    await userEvent.hover(nummer())

    expect(await zeilen()).toEqual(['Herkunft', 'Ohne Herkunft'])
  })

  it('spricht Englisch', async () => {
    zeigen([KETTE[1], KETTE[3]], vi.fn(), 'en')

    await userEvent.hover(nummer())

    expect(await zeilen()).toEqual([en.BOARD.HERKUNFT_TITEL, '#1912 (in the trash)', '#1900 (deleted)'])
  })

  it('lässt die Nummer ohne Hinweis und unfokussierbar, wenn die Kette nicht berechnet ist', async () => {
    zeigen(null)

    await userEvent.hover(nummer())

    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
    expect(nummer()).not.toHaveAttribute('tabindex')
  })

  it('reicht einen Klick auf die Nummer an die Kachel weiter, die die Karte öffnet', async () => {
    const onOpen = zeigen(KETTE)

    await userEvent.click(nummer())

    expect(onOpen).toHaveBeenCalledTimes(1)
  })

  it('öffnet auf dem Tablet quer beim Antippen den Hinweis statt der Karte', async () => {
    setzeMedien({ tabletQuer: true })
    const onOpen = zeigen(KETTE)

    fireEvent.click(nummer())

    expect(await zeilen()).toContain('#1914 · [Plan] Ansicht als XY (archiviert)')
    expect(onOpen).not.toHaveBeenCalled()
  })
})
