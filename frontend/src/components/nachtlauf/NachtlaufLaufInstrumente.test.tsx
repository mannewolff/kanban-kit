import { describe, expect, it, vi, afterEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import { NachtlaufLaufInstrumente } from './NachtlaufLaufInstrumente'

const PAKETE = { gruen: 1, gelb: 0, rot: 0 }

const zeichnen = (laeuftSeit?: string) =>
  render(
    <NachtlaufLaufInstrumente
      verbrauch={undefined}
      dauerMs={59 * 60_000}
      pakete={PAKETE}
      laeuftSeit={laeuftSeit}
    />,
  )

describe('NachtlaufLaufInstrumente — mitlaufende Zeit (Issue #1244)', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('zeigt neben der gemeldeten Dauer, wie lange der Lauf tatsächlich schon läuft', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-27T14:20:00Z'))
    zeichnen('2026-09-27T13:06:00Z')

    expect(screen.getByTestId('instrument-dauer-wert')).toHaveTextContent('59 min')
    expect(screen.getByTestId('instrument-dauer-zusatz')).toHaveTextContent('läuft seit 1:14 h')
  })

  it('frischt die Zeile alle 10 Sekunden auf, ohne dass etwas nachgeladen wird', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-27T13:36:25Z'))
    zeichnen('2026-09-27T13:06:00Z')
    expect(screen.getByTestId('instrument-dauer-zusatz')).toHaveTextContent('läuft seit 30 min')

    act(() => {
      vi.advanceTimersByTime(10_000)
    })

    expect(screen.getByTestId('instrument-dauer-zusatz')).toHaveTextContent('läuft seit 31 min')
  })

  it('rechnet im verborgenen Fenster nicht nach', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-27T13:36:00Z'))
    zeichnen('2026-09-27T13:06:00Z')
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' })
    try {
      act(() => {
        vi.setSystemTime(new Date('2026-09-27T14:20:00Z'))
        vi.advanceTimersByTime(10_000)
      })
      expect(screen.getByTestId('instrument-dauer-zusatz')).toHaveTextContent('läuft seit 30 min')
    } finally {
      Reflect.deleteProperty(document, 'visibilityState')
    }
  })

  it('zeigt an einem beendeten Lauf keine mitlaufende Zeit und legt keinen Takt an', () => {
    vi.useFakeTimers()
    zeichnen()

    expect(screen.queryByTestId('instrument-dauer-zusatz')).toBeNull()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('räumt den Takt beim Unmount', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-27T14:20:00Z'))
    const { unmount } = zeichnen('2026-09-27T13:06:00Z')
    expect(vi.getTimerCount()).toBe(1)

    unmount()

    expect(vi.getTimerCount()).toBe(0)
  })
})
