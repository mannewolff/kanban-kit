import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Epic } from '../api/epics'
import { EpicVisibilityList } from './EpicVisibilityList'

function macheEpic(id: number, title: string, shortcode: string | null): Epic {
  return {
    id,
    number: id,
    title,
    description: null,
    shortcode,
    done: 0,
    total: 0,
    memberNumbers: [],
    rootNumbers: [],
    requirementCardNumber: null,
  }
}

// Bewusst nicht alphabetisch und nicht nach ID sortiert: Die Komponente soll die Reihenfolge der
// Aufruferin übernehmen, nicht eine eigene herstellen.
const AUSBLENDEN = macheEpic(5, 'Ausblenden der Vorhaben', 'AUS')
const BOARD = macheEpic(3, 'Board-Umbau', 'BRD')
const ZEIT = macheEpic(9, 'Zeitplanung im Griff', null)
const VORHABEN: readonly Epic[] = [AUSBLENDEN, BOARD, ZEIT]
// BOARD ist ausgeblendet, die beiden anderen sind eingeblendet — so trägt jeder Test beide Fälle.
const AUSGEBLENDET: ReadonlySet<number> = new Set([BOARD.id])

function zeichne(overrides: Partial<Parameters<typeof EpicVisibilityList>[0]> = {}) {
  const onToggle = vi.fn()
  const onOpen = vi.fn()
  render(
    <EpicVisibilityList
      epics={VORHABEN}
      hidden={AUSGEBLENDET}
      onToggle={onToggle}
      onOpen={onOpen}
      {...overrides}
    />,
  )
  return { onToggle, onOpen }
}

describe('EpicVisibilityList', () => {
  it('reiht die Zeilen genau so wie übergeben', () => {
    zeichne()

    const zeilen = screen.queryAllByTestId(/^vorhaben-zeile-/)

    expect(zeilen.map((z) => z.getAttribute('data-testid'))).toEqual([
      'vorhaben-zeile-5',
      'vorhaben-zeile-3',
      'vorhaben-zeile-9',
    ])
  })

  it('hakt die Schalter eingeblendeter Vorhaben an und die der ausgeblendeten nicht', () => {
    zeichne()

    const schalter = screen.getAllByRole('checkbox')

    expect(schalter.map((s) => (s as HTMLInputElement).checked)).toEqual([true, false, true])
  })

  it('meldet beim Umlegen, ob aus- oder eingeblendet werden soll', async () => {
    const user = userEvent.setup()
    const { onToggle } = zeichne()

    await user.click(screen.getByRole('checkbox', { name: 'Vorhaben AUS ausblenden' }))
    await user.click(screen.getByRole('checkbox', { name: 'Vorhaben BRD einblenden' }))

    expect(onToggle.mock.calls).toEqual([
      [AUSBLENDEN.id, true],
      [BOARD.id, false],
    ])
  })

  it('öffnet das Vorhaben beim Klick auf Kürzel oder Titel', async () => {
    const user = userEvent.setup()
    const { onOpen } = zeichne()

    await user.click(screen.getByText('AUS'))
    await user.click(screen.getByText('Board-Umbau'))

    expect(onOpen.mock.calls).toEqual([[AUSBLENDEN], [BOARD]])
  })

  it('öffnet das Vorhaben nicht, wenn nur der Schalter betätigt wird', async () => {
    const user = userEvent.setup()
    const { onToggle, onOpen } = zeichne()

    await user.click(screen.getByRole('checkbox', { name: 'Vorhaben AUS ausblenden' }))

    expect(onToggle).toHaveBeenCalledTimes(1)
    expect(onOpen).not.toHaveBeenCalled()
  })

  it('ist per Tastatur bedienbar: Zeile mit Enter, Schalter mit der Leertaste', async () => {
    const user = userEvent.setup()
    const { onToggle, onOpen } = zeichne()

    await user.tab()
    await user.keyboard('{Enter}')

    expect(onOpen.mock.calls).toEqual([[AUSBLENDEN]])

    await user.tab()
    expect(screen.getByRole('checkbox', { name: 'Vorhaben AUS ausblenden' })).toHaveFocus()
    await user.keyboard('{ }')

    expect(onToggle.mock.calls).toEqual([[AUSBLENDEN.id, true]])
  })

  it('beschriftet jeden Schalter mit der anstehenden Aktion und trägt den Zustand', () => {
    zeichne()

    // Die Optionen `name` und `checked` fragen genau das ab, was die Vorlesehilfe ansagt:
    // `aria-label` und den berechneten `aria-checked`-Zustand des Schalters.
    expect(screen.getByRole('checkbox', { name: 'Vorhaben AUS ausblenden', checked: true })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Vorhaben BRD einblenden', checked: false })).toBeInTheDocument()
    // Ohne eigenes Kürzel steht die Ableitung aus dem Titel im Namen.
    expect(screen.getByRole('checkbox', { name: 'Vorhaben ZIG ausblenden', checked: true })).toBeInTheDocument()
  })

  it('vermerkt „Ausgeblendet" nur in den Zeilen ausgeblendeter Vorhaben', () => {
    zeichne()

    const vermerke = screen.getAllByText('Ausgeblendet')

    expect(vermerke).toHaveLength(1)
    expect(screen.getByTestId(`vorhaben-zeile-${BOARD.id}`)).toContainElement(vermerke[0])
  })
})
