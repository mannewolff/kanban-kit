import { describe, expect, it, vi, afterEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { NachtlaufLage, type Laufverbrauch } from './NachtlaufLage'

const PAKETE = { gruen: 4, gelb: 0, rot: 1 }

const VERBRAUCH: Laufverbrauch = {
  kostenUsd: 14.55,
  eingabe: 24_090_000,
  ausgabe: 195_000,
  zwischenspeicher: 23_367_300,
}

const zeichnen = (
  angaben: Partial<{ satz: string | null; laeuftSeit: string; verbrauch: Laufverbrauch | undefined }> = {},
) =>
  render(
    <NachtlaufLage
      satz={'satz' in angaben ? (angaben.satz ?? null) : 'Setzt Paket #1846 um — 4 von 11 fertig'}
      verbrauch={'verbrauch' in angaben ? angaben.verbrauch : VERBRAUCH}
      pakete={PAKETE}
      laeuftSeit={angaben.laeuftSeit}
    />,
  )

describe('NachtlaufLage — die Lage des Runs (Plan #1882 E3, E5)', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('zeigt den Zustandssatz', () => {
    zeichnen()

    expect(screen.getByTestId('lage-satz')).toHaveTextContent('Setzt Paket #1846 um — 4 von 11 fertig')
  })

  it('zeigt die Kosten als eine große Zahl mit kleiner Einheit, ohne Tabellenziffern', () => {
    zeichnen()

    const kosten = screen.getByTestId('lage-kosten')
    expect(kosten).toHaveTextContent('14,55$')
    expect(kosten).toHaveAccessibleName('Kosten')
    expect(getComputedStyle(kosten).fontVariantNumeric).not.toBe('tabular-nums')
  })

  it('nennt ungemessene Kosten, statt eine Null zu behaupten', () => {
    zeichnen({ verbrauch: undefined })

    expect(screen.getByTestId('lage-kosten')).toHaveTextContent('nicht gemessen')
  })

  it('zeigt die Pakete nach Zustand', () => {
    zeichnen()

    const pakete = screen.getByTestId('lage-pakete')
    expect(pakete).toHaveTextContent('4 grün 0 gelb 1 rot')
    expect(pakete).toHaveAccessibleName('Pakete')
  })

  it('führt Eingabe, Ausgabe und Cache-Quote als leise Zeile', () => {
    zeichnen()

    expect(screen.getByTestId('lage-verbrauch')).toHaveTextContent(
      'Eingabe 24,09 Mio · Ausgabe 195 Tsd · Cache-Quote 97 %',
    )
  })

  it('hängt die Kostenaufteilung eines Kettenlaufs an die leise Zeile', () => {
    render(
      <NachtlaufLage
        satz={null}
        verbrauch={VERBRAUCH}
        pakete={PAKETE}
        aufteilung={{ planungUsd: 17.64, umsetzungUsd: undefined }}
      />,
    )

    expect(screen.getByTestId('lage-verbrauch')).toHaveTextContent(
      'Cache-Quote 97 % · Planung 17,64 $ · Umsetzung nicht gemeldet',
    )
  })

  it('sagt in der leisen Zeile, was nicht gemessen oder nicht berechenbar ist', () => {
    zeichnen({ verbrauch: { kostenUsd: 1, eingabe: undefined, ausgabe: 148, zwischenspeicher: 10 } })

    expect(screen.getByTestId('lage-verbrauch')).toHaveTextContent(
      'Eingabe nicht gemessen · Ausgabe 148 Token · Cache-Quote nicht berechenbar',
    )
  })

  it('lässt ohne Satz keinen leeren Platz', () => {
    zeichnen({ satz: null })

    expect(screen.queryByTestId('lage-satz')).toBeNull()
    // Auch der Behälter von Satz und „läuft seit“ entfällt — Pakete und Kosten rücken nach vorn.
    expect(screen.queryByTestId('lage-zustand')).toBeNull()
    expect(screen.getByRole('region', { name: 'Lage des Runs' })).toHaveTextContent(/^4 grün/)
  })

  it('zeigt am laufenden Run, wie lange er schon läuft (Issue #1244)', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-27T14:20:00Z'))
    zeichnen({ laeuftSeit: '2026-09-27T13:06:00Z' })

    expect(screen.getByTestId('lage-laeuft-seit')).toHaveTextContent('läuft seit 1:14 h')
  })

  it('zeigt „läuft seit“ auch am laufenden Run ohne Satz', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-27T14:20:00Z'))
    zeichnen({ satz: null, laeuftSeit: '2026-09-27T13:06:00Z' })

    expect(screen.getByTestId('lage-laeuft-seit')).toHaveTextContent('läuft seit 1:14 h')
  })

  it('frischt „läuft seit“ alle 10 Sekunden auf, ohne dass etwas nachgeladen wird', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-27T13:36:25Z'))
    zeichnen({ laeuftSeit: '2026-09-27T13:06:00Z' })
    expect(screen.getByTestId('lage-laeuft-seit')).toHaveTextContent('läuft seit 30 min')

    act(() => {
      vi.advanceTimersByTime(10_000)
    })

    expect(screen.getByTestId('lage-laeuft-seit')).toHaveTextContent('läuft seit 31 min')
  })

  it('rechnet im verborgenen Fenster nicht nach', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-27T13:36:00Z'))
    zeichnen({ laeuftSeit: '2026-09-27T13:06:00Z' })
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' })
    try {
      act(() => {
        vi.setSystemTime(new Date('2026-09-27T14:20:00Z'))
        vi.advanceTimersByTime(10_000)
      })
      expect(screen.getByTestId('lage-laeuft-seit')).toHaveTextContent('läuft seit 30 min')
    } finally {
      Reflect.deleteProperty(document, 'visibilityState')
    }
  })

  it('zeigt am abgeschlossenen Run kein „läuft seit“ und legt keinen Takt an', () => {
    vi.useFakeTimers()
    zeichnen()

    expect(screen.queryByTestId('lage-laeuft-seit')).toBeNull()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('räumt den Takt beim Unmount', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-27T14:20:00Z'))
    const { unmount } = zeichnen({ laeuftSeit: '2026-09-27T13:06:00Z' })
    expect(vi.getTimerCount()).toBe(1)

    unmount()

    expect(vi.getTimerCount()).toBe(0)
  })
})

describe('NachtlaufLage auf Englisch (Issue #1658)', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('beschriftet die Lage auf Englisch und schreibt Zahlen in US-Schreibweise', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-27T14:20:00Z'))
    renderMitSprache(
      <NachtlaufLage
        satz="Implements package #1846 — 4 of 11 done"
        verbrauch={{ kostenUsd: 1234.5, eingabe: 2_500_000, ausgabe: undefined, zwischenspeicher: undefined }}
        pakete={{ gruen: 3, gelb: 1, rot: 2 }}
        aufteilung={{ planungUsd: 1.5, umsetzungUsd: undefined }}
        laeuftSeit="2026-09-27T13:06:00Z"
      />,
      'en',
    )

    expect(screen.getByTestId('lage-kosten')).toHaveTextContent('1,234.50$')
    expect(screen.getByTestId('lage-kosten')).toHaveAccessibleName('Cost')
    expect(screen.getByTestId('lage-verbrauch')).toHaveTextContent(
      'Input 2.50 M · Output not measured · Cache rate not computable · Planning $1.50 · Implementation not reported',
    )
    expect(screen.getByTestId('lage-laeuft-seit')).toHaveTextContent('running for 1:14 h')
    expect(screen.getByTestId('lage-pakete')).toHaveTextContent('3 green 1 yellow 2 red')
  })

  it('nennt eine berechenbare Cache-Quote auf Englisch', () => {
    renderMitSprache(<NachtlaufLage satz={null} verbrauch={VERBRAUCH} pakete={PAKETE} />, 'en')

    expect(screen.getByTestId('lage-verbrauch')).toHaveTextContent('Cache rate 97 %')
  })
})
