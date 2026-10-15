import { ThemeProvider } from '@mui/material/styles'
import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { NightRunRework, NightRunReworkLine, NightRunReworkWeek, NightRunReworkWeekValue } from '../../api/nightRuns'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { kalenderwoche } from '../../lib/leitstand'
import { cssRegel } from '../../test/cssRegel'
import { KUPFER, MELDER, theme } from '../../theme'
import { NacharbeitPlatte } from './NacharbeitPlatte'

/** Die Platte „Nacharbeit“ (Issue #1759, Plan #1752 E6, E8, E9, E13, E14). */

/** Zwölf rollende Fenster, das älteste zuerst; Beginn jeweils 06:00 UTC am Donnerstag. */
const WOCHENBEGINNE = Array.from({ length: 12 }, (_, i) =>
  new Date(Date.UTC(2026, 6, 23 + 7 * i, 6)).toISOString(),
)

const KEINE_DATEN: NightRunReworkWeekValue = { state: 'KEINE_DATEN', affected: 0, processed: 0 }
const KEINE_KARTE: NightRunReworkWeekValue = { state: 'KEINE_KARTE', affected: 0, processed: 0 }
const quote = (affected: number, processed: number): NightRunReworkWeekValue => ({ state: 'QUOTE', affected, processed })

const zeile = (teil: Partial<NightRunReworkLine> = {}): NightRunReworkLine => ({
  dataSince: '2026-07-01T22:00:00Z',
  affected: 0,
  processed: 0,
  cards: [],
  ...teil,
})

const wochen = (
  je: (i: number) => Pick<NightRunReworkWeek, 'packages' | 'documents'> = () => ({ packages: KEINE_KARTE, documents: KEINE_KARTE }),
): NightRunReworkWeek[] => WOCHENBEGINNE.map((weekStart, i) => ({ weekStart, ...je(i) }))

const daten = (teil: Partial<NightRunRework> = {}): NightRunRework => ({
  dataSince: '2026-07-01T22:00:00Z',
  runsInPeriod: true,
  packages: zeile({
    affected: 2,
    processed: 14,
    cards: [
      { number: 1712, title: 'Endpunkt bereitstellen', count: 2 },
      { number: 1715, title: 'Dienst', count: 1 },
    ],
  }),
  documents: zeile({ affected: 1, processed: 6, cards: [{ number: 1700, title: 'Plan Nacharbeit', count: 1 }] }),
  weeks: wochen((i) => (i === 11 ? { packages: quote(1, 14), documents: quote(0, 6) } : { packages: KEINE_KARTE, documents: KEINE_KARTE })),
  ...teil,
})

const zeige = (d: NightRunRework, onOeffnen: (nummer: number) => void = vi.fn(), sprache: 'de' | 'en' = 'de') =>
  renderMitSprache(
    <ThemeProvider theme={theme}>
      <NacharbeitPlatte daten={d} onOeffnen={onOeffnen} />
    </ThemeProvider>,
    sprache,
  )

const platte = () => screen.getByRole('region', { name: 'Nacharbeit' })

describe('NacharbeitPlatte', () => {
  it('zeigt Titel, Bezugszeitraum und beide Quoten als „x von y“', () => {
    zeige(daten())
    expect(within(platte()).getByRole('heading', { name: 'Nacharbeit' })).toBeInTheDocument()
    expect(platte()).toHaveTextContent('letzte 12 Wochen')
    expect(screen.getByTestId('nacharbeit-zeile-pakete')).toHaveTextContent('Pakete zurückgezogen')
    expect(screen.getByTestId('nacharbeit-zeile-pakete')).toHaveTextContent('2 von 14')
    expect(screen.getByTestId('nacharbeit-zeile-dokumente')).toHaveTextContent('Dokumente angehalten')
    expect(screen.getByTestId('nacharbeit-zeile-dokumente')).toHaveTextContent('1 von 6')
  })

  it('spricht Englisch, wenn die Oberfläche Englisch ist', () => {
    zeige(daten(), vi.fn(), 'en')
    expect(screen.getByRole('region', { name: 'Rework' })).toHaveTextContent('last 12 weeks')
    expect(screen.getByTestId('nacharbeit-zeile-pakete')).toHaveTextContent('Packages withdrawn')
    expect(screen.getByTestId('nacharbeit-zeile-pakete')).toHaveTextContent('2 of 14')
    fireEvent.click(screen.getByRole('button', { name: 'Packages withdrawn: 2 of 14 — show cards' }))
    expect(screen.getByTestId('nacharbeit-karten-pakete')).toHaveTextContent('#1712 · 2× withdrawn')
    fireEvent.click(screen.getByRole('button', { name: 'Documents halted: 1 of 6 — show cards' }))
    expect(screen.getByTestId('nacharbeit-karten-dokumente')).toHaveTextContent('#1700 · 1× halted')
  })

  it('klappt die Kartenliste mit der Quote auf und wieder zu', () => {
    zeige(daten())
    const taste = screen.getByRole('button', { name: 'Pakete zurückgezogen: 2 von 14 — Karten zeigen' })
    expect(taste).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByTestId('nacharbeit-karten-pakete')).not.toBeInTheDocument()

    fireEvent.click(taste)
    expect(taste).toHaveAttribute('aria-expanded', 'true')
    const liste = screen.getByTestId('nacharbeit-karten-pakete')
    expect(taste).toHaveAttribute('aria-controls', liste.id)
    const eintraege = within(liste).getAllByRole('listitem')
    expect(eintraege).toHaveLength(2)
    expect(eintraege[0]).toHaveTextContent('#1712 · 2× zurückgezogen')
    expect(eintraege[0]).toHaveTextContent('Endpunkt bereitstellen')
    expect(eintraege[1]).toHaveTextContent('#1715 · 1× zurückgezogen')

    fireEvent.click(taste)
    expect(taste).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByTestId('nacharbeit-karten-pakete')).not.toBeInTheDocument()
  })

  it('klappt jede Zeile für sich auf und nennt bei Dokumenten „angehalten“', () => {
    zeige(daten())
    fireEvent.click(screen.getByRole('button', { name: 'Dokumente angehalten: 1 von 6 — Karten zeigen' }))
    expect(screen.queryByTestId('nacharbeit-karten-pakete')).not.toBeInTheDocument()
    expect(screen.getByTestId('nacharbeit-karten-dokumente')).toHaveTextContent('#1700 · 1× angehalten')
  })

  it('ruft beim Klick auf eine Nummer onOeffnen mit der Nummer', () => {
    const onOeffnen = vi.fn()
    zeige(daten(), onOeffnen)
    fireEvent.click(screen.getByRole('button', { name: 'Pakete zurückgezogen: 2 von 14 — Karten zeigen' }))
    fireEvent.click(screen.getByRole('button', { name: 'Karte #1715 öffnen: Dienst' }))
    expect(onOeffnen).toHaveBeenCalledTimes(1)
    expect(onOeffnen).toHaveBeenCalledWith(1715)
  })

  it('zeigt eine Quote ohne betroffene Karte als Text, nicht als Taste', () => {
    zeige(daten({ packages: zeile({ affected: 0, processed: 14 }) }))
    expect(screen.getByTestId('nacharbeit-zeile-pakete')).toHaveTextContent('0 von 14')
    expect(within(screen.getByTestId('nacharbeit-zeile-pakete')).queryByRole('button')).not.toBeInTheDocument()
  })

  it('Szenario: Keine Runs im Zeitraum', () => {
    zeige(daten({ runsInPeriod: false, packages: zeile(), documents: zeile(), weeks: wochen() }))
    expect(screen.getByTestId('nacharbeit-keine-runs')).toHaveTextContent('Im Bezugszeitraum gab es keinen Run.')
    expect(platte()).not.toHaveTextContent('0 von 0')
    expect(screen.queryByTestId('nacharbeit-zeile-pakete')).not.toBeInTheDocument()
    expect(screen.queryByTestId('nacharbeit-verlauf')).not.toBeInTheDocument()
  })

  it('Szenario: Runs nur mit Paketen', () => {
    zeige(
      daten({
        packages: zeile({ affected: 2, processed: 14, cards: [{ number: 1712, title: 'Endpunkt', count: 2 }] }),
        documents: zeile(),
        weeks: wochen(() => ({ packages: quote(2, 14), documents: KEINE_KARTE })),
      }),
    )
    expect(screen.getByTestId('nacharbeit-zeile-pakete')).toHaveTextContent('2 von 14')
    const dokumente = screen.getByTestId('nacharbeit-zeile-dokumente')
    expect(dokumente).toHaveTextContent('Die Runs haben kein Dokument bearbeitet.')
    expect(within(dokumente).queryByRole('button')).not.toBeInTheDocument()
    expect(platte()).not.toHaveTextContent('0 von 0')
  })

  it('setzt bei keinem bearbeiteten Paket den Satz in die Paket-Zeile', () => {
    zeige(daten({ packages: zeile() }))
    expect(screen.getByTestId('nacharbeit-zeile-pakete')).toHaveTextContent('Die Runs haben kein Paket bearbeitet.')
    expect(screen.getByTestId('nacharbeit-zeile-dokumente')).toHaveTextContent('1 von 6')
  })

  it('Szenario: Ältere Wochen ohne Daten', () => {
    const beginn = '2026-09-10T22:00:00Z'
    zeige(
      daten({
        dataSince: beginn,
        packages: zeile({ dataSince: beginn, affected: 1, processed: 3, cards: [{ number: 1712, title: 'Endpunkt', count: 1 }] }),
        documents: zeile({ dataSince: beginn, affected: 0, processed: 2 }),
        weeks: wochen((i) => (i < 7 ? { packages: KEINE_DATEN, documents: KEINE_DATEN } : { packages: quote(1, 3), documents: quote(0, 2) })),
      }),
    )
    for (let i = 0; i < 7; i++) {
      const woche = screen.getByTestId(`nacharbeit-woche-${i}`)
      expect(woche).toHaveAttribute('title', 'keine Daten')
      expect(within(woche).queryByTestId(/^nacharbeit-balken-/)).not.toBeInTheDocument()
      expect(within(woche).queryByTestId('grundlinie')).not.toBeInTheDocument()
      expect(within(woche).getAllByTestId(/^nacharbeit-leer-/)).toHaveLength(2)
    }
    expect(screen.getByTestId('nacharbeit-woche-7')).toHaveAttribute('title', 'Pakete 1 von 3 · Dokumente 0 von 2')
    expect(platte()).toHaveTextContent('seit 11.9.2026')
    expect(platte()).not.toHaveTextContent('letzte 12 Wochen')
  })

  it('nennt den späteren Beginn der Dokument-Zeile zusätzlich (E8)', () => {
    zeige(daten({ documents: zeile({ dataSince: '2026-09-10T22:00:00Z', affected: 1, processed: 6, cards: [] }) }))
    expect(platte()).toHaveTextContent('letzte 12 Wochen')
    expect(screen.getByTestId('nacharbeit-zeile-dokumente')).toHaveTextContent('seit 11.9.2026')
    expect(screen.getByTestId('nacharbeit-zeile-pakete')).not.toHaveTextContent('seit')
  })

  it('zeigt die drei Wochenzustände sichtbar verschieden', () => {
    zeige(
      daten({
        weeks: wochen((i) => {
          if (i === 0) return { packages: KEINE_DATEN, documents: KEINE_DATEN }
          if (i === 1) return { packages: KEINE_KARTE, documents: KEINE_KARTE }
          return { packages: quote(0, 6), documents: quote(3, 6) }
        }),
      }),
    )
    // Keine Daten: leerer Platz, weder Balken noch Grundlinie.
    expect(within(screen.getByTestId('nacharbeit-woche-0')).getByTestId('nacharbeit-leer-pakete-0')).toBeEmptyDOMElement()
    // Keine bearbeitete Karte: gestrichelte Grundlinie, kein Balken.
    const ohneKarte = screen.getByTestId('nacharbeit-woche-1')
    expect(within(ohneKarte).getAllByTestId('grundlinie')).toHaveLength(2)
    expect(within(ohneKarte).queryByTestId(/^nacharbeit-balken-/)).not.toBeInTheDocument()
    // 0 %: ein 2 px hoher Stummel in der Gruppenfarbe.
    const stummel = screen.getByTestId('nacharbeit-balken-pakete-2')
    expect(stummel).toHaveAttribute('data-prozent', '0')
    expect(cssRegel(stummel)).toContain('height: 0%')
    expect(cssRegel(stummel)).toContain('min-height: 2px')
    expect(cssRegel(stummel)).toContain(KUPFER)
    // 50 %: halbe Höhe in Stahl.
    const halb = screen.getByTestId('nacharbeit-balken-dokumente-2')
    expect(halb).toHaveAttribute('data-prozent', '50')
    expect(cssRegel(halb)).toContain('height: 50%')
    expect(cssRegel(halb)).toContain(MELDER.stahl)
  })

  it('zeigt die Zahlen hinter den Balken beim Überfahren und je Woche als aria-label', () => {
    zeige(daten())
    const letzte = screen.getByTestId('nacharbeit-woche-11')
    expect(letzte).toHaveAttribute('title', 'Pakete 1 von 14 · Dokumente 0 von 6')
    expect(letzte).toHaveAttribute('role', 'img')
    expect(letzte).toHaveAttribute(
      'aria-label',
      `KW ${kalenderwoche(WOCHENBEGINNE[11])}: Pakete 1 von 14 · Dokumente 0 von 6`,
    )
    expect(screen.getByTestId('nacharbeit-woche-0')).toHaveAttribute(
      'aria-label',
      `KW ${kalenderwoche(WOCHENBEGINNE[0])}: Pakete keine bearbeitete Karte · Dokumente keine bearbeitete Karte`,
    )
    expect(screen.getByRole('group', { name: 'Verlauf der Nacharbeit über 12 Wochen' })).toBeInTheDocument()
  })

  it('beschriftet die Kalenderwochen aus den eigenen weeks[].weekStart (E6)', () => {
    const eigene = WOCHENBEGINNE.map((w) => new Date(new Date(w).getTime() + 3 * 7 * 86_400_000).toISOString())
    zeige(daten({ weeks: daten().weeks.map((w, i) => ({ ...w, weekStart: eigene[i] })) }))
    const leiste = screen.getByTestId('wochen-leiste')
    expect(leiste).toHaveTextContent(eigene.map((w) => String(kalenderwoche(w))).join(''))
    expect(screen.getByTestId('nacharbeit-woche-0').getAttribute('aria-label')).toMatch(
      new RegExp(`^KW ${kalenderwoche(eigene[0])}:`),
    )
  })
})
