import { fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { BoardFlow, FlowDay } from '../../api/dashboard'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { statusColors } from '../../lib/statusColors'
import { cssRegel } from '../../test/cssRegel'
import { FlussPlatte } from './FlussPlatte'

/** Die Platte „Fluss“ (Issue #1776, Plan #1765 E9, E16, E17). */

/** Kalendertag `i` ab dem 2026-07-23, `YYYY-MM-DD`. */
const datum = (i: number) => new Date(Date.UTC(2026, 6, 23 + i)).toISOString().slice(0, 10)

const bekannt = (i: number, zahlen: Partial<Omit<FlowDay, 'date' | 'known'>> = {}): FlowDay => ({
  date: datum(i),
  known: true,
  backlog: 10,
  ready: 4,
  inProgress: 2,
  inReview: 0,
  done: 0,
  ...zahlen,
})

const unbekannt = (i: number): FlowDay => ({
  date: datum(i),
  known: false,
  backlog: null,
  ready: null,
  inProgress: null,
  inReview: null,
  done: null,
})

const fluss = (days: FlowDay[], dataSince: string | null = null): BoardFlow => ({ days, dataSince })

/** 84 Tage; in der letzten Woche legt der Nachtlauf jede Nacht drei Pakete nach In Review. */
const stau = (): BoardFlow =>
  fluss(Array.from({ length: 84 }, (_, i) => bekannt(i, { inReview: i < 77 ? 0 : (i - 76) * 3, done: Math.floor(i / 7) })))

const tag = (i: number) => screen.getByTestId(`fluss-tag-${i}`)
const anzeige = () => screen.getByTestId('fluss-anzeige')

describe('FlussPlatte', () => {
  it('Szenario: Stau in In Review', () => {
    renderMitSprache(<FlussPlatte daten={stau()} />)

    fireEvent.mouseEnter(tag(77))
    expect(within(anzeige()).getByText('In Review: 3')).toBeInTheDocument()

    fireEvent.mouseEnter(tag(83))
    expect(within(anzeige()).getByText('In Review: 21')).toBeInTheDocument()
    expect(within(anzeige()).getByText('Done: 11')).toBeInTheDocument()
  })

  it('zeichnet fünf Bänder und die Legende in der Reihenfolge Done, In Review, In Progress, Ready, Backlog', () => {
    renderMitSprache(<FlussPlatte daten={stau()} />)

    const baender = screen.getAllByTestId(/^fluss-band-/).map((b) => b.dataset.testid)
    expect(baender).toEqual(['fluss-band-done', 'fluss-band-inReview', 'fluss-band-inProgress', 'fluss-band-ready', 'fluss-band-backlog'])
    const legende = within(screen.getByRole('list', { name: 'Legende' })).getAllByRole('listitem')
    expect(legende.map((l) => l.textContent)).toEqual(['Done', 'In Review', 'In Progress', 'Ready', 'Backlog'])
  })

  it('färbt die Bänder aus den Statusfarben und trennt Ready und Backlog über die Füllung', () => {
    renderMitSprache(<FlussPlatte daten={stau()} />)

    const regel = (band: string) => cssRegel(screen.getByTestId(`fluss-band-${band}`))
    expect(regel('done')).toContain(`fill: ${statusColors('done').dot}`)
    expect(regel('inReview')).toContain(`fill: ${statusColors('review').dot}`)
    expect(regel('inProgress')).toContain(`fill: ${statusColors('progress').dot}`)
    expect(regel('ready')).toContain(`fill: ${statusColors('ready').dot}`)
    expect(regel('backlog')).toContain(`fill: ${statusColors('backlog').bg}`)
    expect(regel('backlog')).toContain(`stroke: ${statusColors('backlog').dot}`)
    expect(regel('ready')).not.toContain('stroke:')
    // Die Legende trägt dieselbe Füllung wie ihr Band.
    expect(cssRegel(screen.getByTestId('fluss-legende-backlog'))).toContain(statusColors('backlog').bg)
    expect(cssRegel(screen.getByTestId('fluss-legende-ready'))).toContain(statusColors('ready').dot)
  })

  it('zeigt beim Überfahren Datum und Zahl je Band, von oben gelesen, und nennt Done als seit Beginn fertig', () => {
    renderMitSprache(<FlussPlatte daten={fluss([bekannt(0), bekannt(1, { done: 5, inReview: 1 })])} />)

    expect(within(anzeige()).queryByText('Done: 5')).not.toBeInTheDocument()
    fireEvent.mouseEnter(tag(1))

    const zeilen = within(anzeige()).getAllByRole('listitem').map((z) => z.textContent)
    expect(zeilen).toEqual(['Backlog: 10', 'Ready: 4', 'In Progress: 2', 'In Review: 1', 'Done: 5'])
    expect(within(anzeige()).getByText('24.7.2026')).toBeInTheDocument()
    expect(screen.getByText('Done zählt die seit Beginn des Zeitraums fertig gewordenen Karten.')).toBeInTheDocument()

    fireEvent.mouseLeave(tag(1))
    expect(within(anzeige()).queryByText('Done: 5')).not.toBeInTheDocument()
    expect(within(anzeige()).getByText(/Einen Tag überfahren/)).toBeInTheDocument()
  })

  it('zeigt die Zahlen auch bei Tastaturfokus; jeder Tag ist per Tab erreichbar', async () => {
    const nutzer = userEvent.setup()
    renderMitSprache(<FlussPlatte daten={fluss([bekannt(0, { ready: 7 }), unbekannt(1), bekannt(2, { ready: 9 })])} />)

    await nutzer.tab()
    expect(tag(0)).toHaveFocus()
    expect(within(anzeige()).getByText('Ready: 7')).toBeInTheDocument()
    await nutzer.tab()
    expect(tag(1)).toHaveFocus()
    expect(within(anzeige()).getByText('keine Daten')).toBeInTheDocument()
    await nutzer.tab()
    expect(tag(2)).toHaveFocus()
    expect(within(anzeige()).getByText('Ready: 9')).toBeInTheDocument()

    await nutzer.tab()
    expect(within(anzeige()).queryByText('Ready: 9')).not.toBeInTheDocument()
  })

  it('zeigt die Zahlen eines Tages auch nach Antippen', () => {
    renderMitSprache(<FlussPlatte daten={fluss([bekannt(0, { ready: 7 }), bekannt(1)])} />)

    fireEvent.click(tag(0))
    expect(within(anzeige()).getByText('Ready: 7')).toBeInTheDocument()
  })

  it('nennt jeden Tag vorgelesen mit Datum und Zahlen', () => {
    renderMitSprache(<FlussPlatte daten={fluss([bekannt(0), unbekannt(1)])} />)

    expect(tag(0)).toHaveAccessibleName('23.7.2026: Backlog: 10, Ready: 4, In Progress: 2, In Review: 0, Done: 0')
    expect(tag(1)).toHaveAccessibleName('24.7.2026: keine Daten')
    expect(screen.getByRole('group', { name: 'Tage im Fluss' })).toBeInTheDocument()
  })

  it('zeigt für einen unbekannten Tag „keine Daten“ und zeichnet dort keine Fläche', () => {
    renderMitSprache(<FlussPlatte daten={fluss([bekannt(0), bekannt(1), unbekannt(2), bekannt(3), bekannt(4)])} />)

    fireEvent.mouseEnter(tag(2))
    expect(within(anzeige()).getByText('keine Daten')).toBeInTheDocument()
    expect(within(anzeige()).queryByText(/Backlog:/)).not.toBeInTheDocument()

    // Die Lücke trennt jedes Band in zwei geschlossene Teilpfade.
    const pfad = screen.getByTestId('fluss-band-done').getAttribute('d') ?? ''
    expect(pfad.match(/M/g)).toHaveLength(2)
  })

  it('nennt im Kopf „seit ‹Datum›“, wenn die sicheren Daten später beginnen, sonst den Zeitraum', () => {
    const { unmount } = renderMitSprache(<FlussPlatte daten={fluss([unbekannt(0), bekannt(1)], '2026-07-24')} />)
    expect(screen.getByRole('heading', { name: 'Fluss' })).toBeInTheDocument()
    expect(screen.getByText('Karten je Zustand am Tagesende · seit 24.7.2026')).toBeInTheDocument()
    unmount()

    renderMitSprache(<FlussPlatte daten={fluss([bekannt(0)])} />)
    expect(screen.getByText('Karten je Zustand am Tagesende · letzte 12 Wochen')).toBeInTheDocument()
  })

  it('fasst den letzten Tag im aria-label der Grafik zusammen', () => {
    renderMitSprache(<FlussPlatte daten={stau()} />)

    expect(screen.getByRole('img')).toHaveAccessibleName(
      'Fluss über 84 Tage, gestapelt von unten Done, In Review, In Progress, Ready, Backlog. Letzter Tag 14.10.2026: Backlog: 10, Ready: 4, In Progress: 2, In Review: 21, Done: 11',
    )
  })

  it('sagt „keine Daten“ in der Zusammenfassung, wenn der letzte Tag unbekannt ist', () => {
    renderMitSprache(<FlussPlatte daten={fluss([bekannt(0), unbekannt(1)])} />)

    expect(screen.getByRole('img')).toHaveAccessibleName(/Letzter Tag 24\.7\.2026: keine Daten$/)
  })

  it('zeigt ohne Tage einen Satz statt einer Grafik', () => {
    renderMitSprache(<FlussPlatte daten={fluss([])} />)

    expect(screen.getByText('Für diesen Zeitraum liegen keine Tage vor.')).toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('spricht englisch, wenn die Sprache Englisch ist', () => {
    renderMitSprache(<FlussPlatte daten={fluss([bekannt(0), unbekannt(1)])} />, 'en')

    expect(screen.getByRole('heading', { name: 'Flow' })).toBeInTheDocument()
    fireEvent.mouseEnter(tag(1))
    expect(within(anzeige()).getByText('no data')).toBeInTheDocument()
  })
})
