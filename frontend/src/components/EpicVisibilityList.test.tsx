import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Card } from '../api/cards'
import type { Epic } from '../api/epics'
import type { Label } from '../api/labels'
import { kontrast } from '../lib/kontrast'
import { NUT, TEXT_SCHWACH, theme } from '../theme'
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

function karte(number: number, title: string, labels: number[] = []): Card {
  return {
    id: number * 10, boardId: 1, columnId: 10, number, title, description: null, excerpt: null,
    positionInColumn: 0, archived: false, movedToDoneAt: null, dependencies: [], type: 'CARD',
    parentId: null, shortcode: null, assignees: [], dueDate: null, labels, derivedFrom: null,
    status: null, canSetStatus: false,
  }
}

function label(id: number, name: string, countOnEpicTile = true): Label {
  return { id, boardId: 1, name, color: '#C8393E', countOnEpicTile }
}

function zeichne(overrides: Partial<Parameters<typeof EpicVisibilityList>[0]> = {}) {
  const onToggle = vi.fn()
  const onOpen = vi.fn()
  const onOpenCard = vi.fn()
  render(
    <EpicVisibilityList
      epics={VORHABEN}
      hidden={AUSGEBLENDET}
      cards={[]}
      labels={[]}
      titelZuNummer={(nummer) => `Karte ${nummer}`}
      onToggle={onToggle}
      onOpen={onOpen}
      onOpenCard={onOpenCard}
      {...overrides}
    />,
  )
  return { onToggle, onOpen, onOpenCard }
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

  // --- Angaben der Kachel in der Zeile (Issue #1489) ---------------------------------------

  it('nennt die Anforderung mit Nummer und Titel und öffnet beim Klick nur die Karte', async () => {
    const user = userEvent.setup()
    const mitAnforderung = { ...AUSBLENDEN, requirementCardNumber: 42 }
    const { onOpen, onOpenCard } = zeichne({ epics: [mitAnforderung] })

    await user.click(screen.getByRole('button', { name: '#42 · Karte 42' }))

    expect(onOpenCard.mock.calls).toEqual([[42]])
    expect(onOpen).not.toHaveBeenCalled()
  })

  it('sagt ausdrücklich, wenn ein Vorhaben keine Anforderung trägt', () => {
    zeichne({ epics: [AUSBLENDEN] })

    expect(screen.getByText('Keine Anforderung hinterlegt.')).toBeInTheDocument()
  })

  it('zeigt die Zusammensetzung nach Art in Einzahl und Mehrzahl, ohne Nullwerte', () => {
    const vorhaben = { ...AUSBLENDEN, total: 4, memberNumbers: [1, 2, 3, 4] }
    zeichne({
      epics: [vorhaben],
      cards: [karte(1, '[Fachlich] Anforderung'), karte(2, '[Plan] Weg A'), karte(3, '[Plan] Weg B'), karte(4, 'Paket')],
    })

    expect(screen.getByText('1 Anforderung')).toBeInTheDocument()
    expect(screen.getByText('2 Pläne')).toBeInTheDocument()
    expect(screen.getByText('1 Arbeitspaket')).toBeInTheDocument()
    expect(screen.queryByText(/^0 (Anforderung|Pläne|Arbeitspakete)/)).not.toBeInTheDocument()
  })

  it('sagt bei einem leeren Vorhaben, dass ihm noch keine Karten zugeordnet sind', () => {
    zeichne({ epics: [AUSBLENDEN] })

    expect(screen.getByText('Noch keine Karten zugeordnet.')).toBeInTheDocument()
  })

  it('zeigt je gezähltem Label eine Marke mit Anzahl, ungezählte nicht', () => {
    const vorhaben = { ...AUSBLENDEN, total: 2, memberNumbers: [1, 2] }
    zeichne({
      epics: [vorhaben],
      cards: [karte(1, 'Paket A', [7, 8]), karte(2, 'Paket B', [7])],
      labels: [label(7, 'blockiert'), label(8, 'intern', false)],
    })

    expect(screen.getByText('blockiert 2')).toBeInTheDocument()
    expect(screen.queryByText(/^intern/)).not.toBeInTheDocument()
  })

  it('zeigt den Fortschritt als Text und als benannten Balken', () => {
    zeichne({ epics: [{ ...AUSBLENDEN, done: 3, total: 4 }] })

    expect(screen.getByText('3 von 4 fertig')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Fortschritt Ausblenden der Vorhaben' })).toHaveAttribute(
      'aria-valuenow',
      '75',
    )
  })

  it('zeigt den vollen Titel als Tooltip beim Überfahren', async () => {
    const user = userEvent.setup()
    zeichne({ epics: [AUSBLENDEN] })

    await user.hover(screen.getByRole('button', { name: 'Ausblenden der Vorhaben' }))

    expect(await screen.findByRole('tooltip')).toHaveTextContent('Ausblenden der Vorhaben')
  })

  it('zeigt den vollen Titel als Tooltip beim Tastaturfokus', async () => {
    const user = userEvent.setup()
    zeichne({ epics: [AUSBLENDEN] })

    await user.tab()

    expect(screen.getByRole('button', { name: 'Ausblenden der Vorhaben' })).toHaveFocus()
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Ausblenden der Vorhaben')
  })

  it('kürzt den Titel einzeilig, statt die Zeile umzubrechen', () => {
    zeichne({ epics: [AUSBLENDEN] })

    expect(screen.getByRole('button', { name: 'Ausblenden der Vorhaben' })).toHaveStyle({
      whiteSpace: 'nowrap',
      textOverflow: 'ellipsis',
      overflow: 'hidden',
    })
  })

  it('öffnet das Vorhaben mit Enter auf dem Titelknopf', async () => {
    const user = userEvent.setup()
    const { onOpen } = zeichne({ epics: [AUSBLENDEN] })

    await user.tab()
    expect(screen.getByRole('button', { name: 'Ausblenden der Vorhaben' })).toHaveFocus()
    await user.keyboard('{Enter}')

    expect(onOpen.mock.calls).toEqual([[AUSBLENDEN]])
  })

  it('öffnet das Vorhaben beim Klick auf die freie Zeilenfläche', async () => {
    const user = userEvent.setup()
    const { onOpen } = zeichne({ epics: [AUSBLENDEN] })

    await user.click(screen.getByText('Keine Anforderung hinterlegt.'))

    expect(onOpen.mock.calls).toEqual([[AUSBLENDEN]])
  })

  it('öffnet über den Menü-Knopf nur das Menü, nicht das Vorhaben', async () => {
    const user = userEvent.setup()
    const onMenu = vi.fn()
    const { onOpen } = zeichne({ epics: [AUSBLENDEN], onMenu })

    const knopf = screen.getByRole('button', { name: 'Menü Ausblenden der Vorhaben' })
    await user.click(knopf)

    expect(onMenu).toHaveBeenCalledWith(AUSBLENDEN, knopf)
    expect(onOpen).not.toHaveBeenCalled()
  })

  it('zeigt ohne onMenu keinen Menü-Knopf', () => {
    zeichne({ epics: [AUSBLENDEN] })

    expect(screen.queryByRole('button', { name: /^Menü / })).not.toBeInTheDocument()
  })

  it('stellt den Schalter ans Ende der ersten Zeile, hinter den Menü-Knopf', async () => {
    const user = userEvent.setup()
    zeichne({ epics: [{ ...AUSBLENDEN, requirementCardNumber: 42 }], onMenu: vi.fn() })

    await user.tab()
    expect(screen.getByRole('button', { name: 'Ausblenden der Vorhaben' })).toHaveFocus()
    await user.tab()
    expect(screen.getByRole('button', { name: 'Menü Ausblenden der Vorhaben' })).toHaveFocus()
    await user.tab()
    expect(screen.getByRole('checkbox', { name: 'Vorhaben AUS ausblenden' })).toHaveFocus()
    // Die Anforderung steht in der zweiten Zeile und folgt deshalb erst nach dem Schalter: Die
    // Tab-Reihenfolge ist die sichtbare Reihenfolge.
    await user.tab()
    expect(screen.getByRole('button', { name: '#42 · Karte 42' })).toHaveFocus()
  })

  it('schachtelt kein Bedienelement in ein anderes', () => {
    zeichne({ epics: VORHABEN.map((e) => ({ ...e, requirementCardNumber: 42 })), onMenu: vi.fn() })

    const knoepfe = within(screen.getByTestId('vorhaben-liste')).getAllByRole('button')

    expect(knoepfe).toHaveLength(9)
    for (const knopf of knoepfe) {
      expect(within(knopf).queryAllByRole('button')).toHaveLength(0)
      expect(within(knopf).queryAllByRole('link')).toHaveLength(0)
      expect(within(knopf).queryAllByRole('checkbox')).toHaveLength(0)
    }
  })

  it('setzt eine ausgeblendete Zeile auf die eingelassene Fläche in schwacher Schrift, ohne Deckkraft', () => {
    zeichne()

    const zeile = screen.getByTestId(`vorhaben-zeile-${BOARD.id}`)
    const sichtbar = screen.getByTestId(`vorhaben-zeile-${AUSBLENDEN.id}`)

    expect(zeile).toHaveStyle({ backgroundColor: NUT, color: TEXT_SCHWACH })
    expect(sichtbar).not.toHaveStyle({ backgroundColor: NUT })
    expect(getComputedStyle(zeile).opacity).not.toMatch(/^0\./)
  })

  it.each([
    ['hell', theme.colorSchemes.light!.palette],
    ['dunkel', theme.colorSchemes.dark!.palette],
  ])('%s: schwache Schrift auf der eingelassenen Fläche hält 4,5:1', (_, palette) => {
    expect(kontrast(palette.warte.nute, palette.warte.textSchwach)).toBeGreaterThanOrEqual(4.5)
  })
})
