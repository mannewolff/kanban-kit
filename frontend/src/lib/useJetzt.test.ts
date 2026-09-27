import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useJetzt } from './useJetzt'

const START = Date.parse('2026-09-27T13:06:00.000Z')

const verborgen = (wert: 'visible' | 'hidden') => {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => wert })
}

describe('useJetzt (Issue #1244)', () => {
  afterEach(() => {
    vi.useRealTimers()
    Reflect.deleteProperty(document, 'visibilityState')
  })

  it('liefert zunächst die aktuelle Zeit', () => {
    vi.useFakeTimers()
    vi.setSystemTime(START)

    const { result } = renderHook(() => useJetzt(10_000))

    expect(result.current).toBe(START)
  })

  it('frischt die Zeit im übergebenen Takt auf', () => {
    vi.useFakeTimers()
    vi.setSystemTime(START)
    const { result } = renderHook(() => useJetzt(10_000))

    act(() => {
      vi.advanceTimersByTime(10_000)
    })

    expect(result.current).toBe(START + 10_000)
  })

  it('rechnet vor Ablauf des Takts nicht nach', () => {
    vi.useFakeTimers()
    vi.setSystemTime(START)
    const { result } = renderHook(() => useJetzt(10_000))

    act(() => {
      vi.advanceTimersByTime(9_999)
    })

    expect(result.current).toBe(START)
  })

  it('rechnet im verborgenen Fenster nicht nach', () => {
    vi.useFakeTimers()
    vi.setSystemTime(START)
    const { result } = renderHook(() => useJetzt(10_000))
    verborgen('hidden')

    act(() => {
      vi.advanceTimersByTime(10_000)
    })

    expect(result.current).toBe(START)
  })

  it('rechnet im sichtbaren Fenster nach', () => {
    vi.useFakeTimers()
    vi.setSystemTime(START)
    const { result } = renderHook(() => useJetzt(10_000))
    verborgen('visible')

    act(() => {
      vi.advanceTimersByTime(10_000)
    })

    expect(result.current).toBe(START + 10_000)
  })

  it('legt ohne Takt kein Intervall an und hält die Zeit fest', () => {
    vi.useFakeTimers()
    vi.setSystemTime(START)

    const { result } = renderHook(() => useJetzt(null))

    expect(vi.getTimerCount()).toBe(0)
    act(() => {
      vi.setSystemTime(START + 60_000)
      vi.advanceTimersByTime(60_000)
    })
    expect(result.current).toBe(START)
  })

  it('räumt das Intervall beim Unmount', () => {
    vi.useFakeTimers()
    const { unmount } = renderHook(() => useJetzt(10_000))
    expect(vi.getTimerCount()).toBe(1)

    unmount()

    expect(vi.getTimerCount()).toBe(0)
  })

  it('legt bei geändertem Takt genau ein Intervall an', () => {
    vi.useFakeTimers()
    vi.setSystemTime(START)
    const { result, rerender } = renderHook(({ takt }) => useJetzt(takt), {
      initialProps: { takt: 10_000 as number | null },
    })

    rerender({ takt: 30_000 })

    expect(vi.getTimerCount()).toBe(1)
    act(() => {
      vi.advanceTimersByTime(10_000)
    })
    expect(result.current).toBe(START)
    act(() => {
      vi.advanceTimersByTime(20_000)
    })
    expect(result.current).toBe(START + 30_000)
  })
})
