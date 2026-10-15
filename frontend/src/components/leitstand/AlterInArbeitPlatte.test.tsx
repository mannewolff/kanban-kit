import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { AgingCard, AgingState, AgingWip } from '../../api/dashboard'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { cssRegel } from '../../test/cssRegel'
import { RAND } from '../../theme'
import { AlterInArbeitPlatte } from './AlterInArbeitPlatte'

/** Die Platte „Alter in Arbeit“ (Issue #1777, Plan #1765 E15, E16, E18, E19). */

const STUNDE = 3600
const TAG = 24 * STUNDE

const karte = (number: number, ageSeconds: number, zusatz: Partial<AgingCard> = {}): AgingCard => ({
  id: number * 10,
  number,
  title: `Paket ${number}`,
  ageSeconds,
  ageIsMinimum: false,
  overThreshold: false,
  ...zusatz,
})

const zustand = (cards: AgingCard[], thresholdSeconds: number | null = 3 * TAG, comparisonCount = 20): AgingState => ({
  comparisonCount,
  thresholdSeconds,
  cards,
})

const leer = (): AgingState => zustand([])

const alter = (teile: Partial<AgingWip> = {}): AgingWip => ({
  ready: leer(),
  inProgress: leer(),
  inReview: leer(),
  ...teile,
})

const spalte = (name: string) => screen.getByTestId(`alter-spalte-${name}`)
const zeilen = (name: string) => within(spalte(name)).queryAllByRole('listitem')

/** Die Regel des Elements ab einer Mindestbreite, die Emotion als eigene `@media`-Regel ablegt. */
const breitRegel = (element: Element, px: number): string => {
  const klasse = [...element.classList].find((c) => c.startsWith('css-'))
  const regel = [...document.styleSheets]
    .flatMap((blatt) => [...blatt.cssRules])
    .find((r) => new RegExp(`^@media \\(min-width: ?${px}px\\)`).test(r.cssText) && r.cssText.includes(`.${klasse} `))
  return regel?.cssText ?? ''
}

const zeige = (daten: AgingWip, onOeffnen: (nummer: number) => void = () => {}) =>
  renderMitSprache(<AlterInArbeitPlatte daten={daten} onOeffnen={onOeffnen} />)

describe('AlterInArbeitPlatte', () => {
  it('Szenario: Eine Karte liegt auffällig lange', () => {
    zeige(
      alter({
        inReview: zustand([
          karte(42, 8 * TAG, { overThreshold: true }),
          karte(43, 2 * TAG),
          karte(44, 5 * STUNDE),
        ]),
      }),
    )

    const [oben, ...rest] = zeilen('inReview')
    expect(within(oben).getByText('#42')).toBeInTheDocument()
    expect(within(oben).getByText('8 Tage')).toBeInTheDocument()
    expect(within(oben).getByTestId('led-bernst')).toBeInTheDocument()
    for (const zeile of rest) {
      expect(within(zeile).queryByTestId('led-bernst')).not.toBeInTheDocument()
    }
  })

  it('Szenario: Unbekannter Eintritt', () => {
    zeige(alter({ inReview: zustand([karte(50, 20 * TAG, { ageIsMinimum: true, overThreshold: true })]) }))

    const [zeile] = zeilen('inReview')
    expect(within(zeile).getByText('mindestens 20 Tage')).toBeInTheDocument()
    expect(within(zeile).getByTestId('led-bernst')).toBeInTheDocument()
  })

  it('Szenario: Zu wenig Vergleichskarten', () => {
    zeige(alter({ inProgress: zustand([karte(7, 9 * TAG), karte(8, 1 * TAG)], null, 3) }))

    const liste = zeilen('inProgress')
    expect(liste).toHaveLength(2)
    expect(within(liste[0]).getByText('9 Tage')).toBeInTheDocument()
    expect(within(liste[1]).getByText('1 Tag')).toBeInTheDocument()
    expect(within(spalte('inProgress')).queryByTestId('led-bernst')).not.toBeInTheDocument()
  })

  it('zeigt die Spalten Ready, In Progress, In Review in dieser Reihenfolge, mit Überschrift', () => {
    zeige(alter())

    const spalten = screen.getAllByTestId(/^alter-spalte-/).map((s) => s.dataset.testid)
    expect(spalten).toEqual(['alter-spalte-ready', 'alter-spalte-inProgress', 'alter-spalte-inReview'])
    expect(within(spalte('ready')).getByRole('heading', { name: 'Ready' })).toBeInTheDocument()
    expect(within(spalte('inProgress')).getByRole('heading', { name: 'In Progress' })).toBeInTheDocument()
    expect(within(spalte('inReview')).getByRole('heading', { name: 'In Review' })).toBeInTheDocument()
  })

  it('trägt Titel und Notiz zur Schwelle im Kopf', () => {
    zeige(alter())

    expect(screen.getByRole('region', { name: 'Alter in Arbeit' })).toBeInTheDocument()
    expect(screen.getByText(/85 %/)).toBeInTheDocument()
  })

  it('listet die Karten je Spalte in der gelieferten Reihenfolge mit Nummer, Titel und Alter', () => {
    zeige(alter({ ready: zustand([karte(3, 4 * TAG), karte(1, 3 * STUNDE), karte(2, 600)]) }))

    const liste = zeilen('ready')
    expect(liste.map((z) => within(z).getByRole('button').textContent)).toEqual(['#3', '#1', '#2'])
    expect(within(liste[0]).getByText('Paket 3')).toBeInTheDocument()
    expect(within(liste[0]).getByText('4 Tage')).toBeInTheDocument()
    expect(within(liste[1]).getByText('3 Std')).toBeInTheDocument()
    expect(within(liste[2]).getByText('unter 1 Std')).toBeInTheDocument()
  })

  it('setzt den Melder genau bei overThreshold, auch ohne Schwelle nie sonst', () => {
    zeige(
      alter({
        ready: zustand([karte(1, 10 * TAG), karte(2, 9 * TAG, { overThreshold: true })]),
        inProgress: zustand([karte(3, 30 * TAG)], null, 2),
      }),
    )

    const ready = zeilen('ready')
    expect(within(ready[0]).queryByTestId('led-bernst')).not.toBeInTheDocument()
    expect(within(ready[1]).getByTestId('led-bernst')).toBeInTheDocument()
    expect(within(spalte('inProgress')).queryByTestId('led-bernst')).not.toBeInTheDocument()
    expect(screen.getAllByTestId('led-bernst')).toHaveLength(1)
  })

  it('nennt den Melder Vorlesewerkzeugen als Text — die Farbe trägt ihn nicht allein', () => {
    zeige(alter({ inReview: zustand([karte(42, 8 * TAG, { overThreshold: true }), karte(43, TAG)]) }))

    const [oben, unten] = zeilen('inReview')
    const hinweis = within(oben).getByText('ungewöhnlich lange')
    expect(cssRegel(hinweis)).toContain('clip: rect(0 0 0 0)')
    expect(within(unten).queryByText('ungewöhnlich lange')).not.toBeInTheDocument()
  })

  it('lässt eine leere Spalte stehen und sagt „Keine Karte in ‹Spalte›“', () => {
    zeige(alter({ inProgress: zustand([karte(1, TAG)]) }))

    expect(within(spalte('ready')).getByText('Keine Karte in Ready')).toBeInTheDocument()
    expect(within(spalte('inReview')).getByText('Keine Karte in In Review')).toBeInTheDocument()
    expect(within(spalte('inProgress')).queryByText(/Keine Karte/)).not.toBeInTheDocument()
    expect(within(spalte('ready')).queryByRole('list')).not.toBeInTheDocument()
    expect(screen.getByTestId('alter-leer-ready')).toHaveTextContent('Keine Karte in Ready')
    expect(screen.getByTestId('alter-leer-inReview')).toHaveTextContent('Keine Karte in In Review')
  })

  it('trennt die Spalten schmal oben, ab md links — vor der ersten Spalte nicht', () => {
    zeige(alter())

    const erste = spalte('ready')
    expect(cssRegel(erste)).not.toContain('border')
    expect(breitRegel(erste, 0)).not.toContain('border')
    expect(breitRegel(erste, 900)).not.toContain('border')
    for (const name of ['inProgress', 'inReview']) {
      expect(cssRegel(spalte(name))).toContain(`border-color: ${RAND}`)
      expect(breitRegel(spalte(name), 0)).toMatch(/border-top: 1px solid/)
      expect(breitRegel(spalte(name), 900)).toMatch(/border-top: 0/)
      expect(breitRegel(spalte(name), 900)).toMatch(/border-left: 1px solid/)
    }
  })

  it('öffnet die Karte über die Taste der Kartennummer', async () => {
    const onOeffnen = vi.fn()
    zeige(alter({ inReview: zustand([karte(42, 8 * TAG)]) }), onOeffnen)

    await userEvent.click(screen.getByRole('button', { name: 'Karte #42 öffnen: Paket 42' }))

    expect(onOeffnen).toHaveBeenCalledWith(42)
  })

  it('stellt die Spalten ab md nebeneinander, schmal untereinander', () => {
    zeige(alter())

    const raster = screen.getByTestId('alter-spalten')
    expect(breitRegel(raster, 0)).toMatch(/grid-template-columns: minmax\(0, ?1fr\)/)
    expect(breitRegel(raster, 900)).toMatch(/grid-template-columns: repeat\(3, ?minmax\(0, ?1fr\)\)/)
  })

  it('spricht Englisch', () => {
    renderMitSprache(
      <AlterInArbeitPlatte
        daten={alter({ inReview: zustand([karte(5, 20 * TAG, { ageIsMinimum: true, overThreshold: true })]) })}
        onOeffnen={() => {}}
      />,
      'en',
    )

    expect(screen.getByRole('region', { name: 'Work item age' })).toBeInTheDocument()
    expect(screen.getByText('No card in Ready')).toBeInTheDocument()
    expect(screen.getByText('at least 20 days')).toBeInTheDocument()
    expect(screen.getByText('unusually long')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Open card #5: Paket 5' })).toBeInTheDocument()
  })
})
