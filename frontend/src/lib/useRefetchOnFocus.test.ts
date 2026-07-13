import { act, renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { useRefetchOnFocus } from './useRefetchOnFocus'

describe('useRefetchOnFocus', () => {
  it('ruft den Callback bei Fensterfokus und beim Sichtbarwerden', () => {
    const cb = vi.fn()
    renderHook(() => useRefetchOnFocus(cb))

    act(() => window.dispatchEvent(new Event('focus')))
    expect(cb).toHaveBeenCalledTimes(1)

    // jsdom meldet document.visibilityState standardmäßig als 'visible'.
    act(() => document.dispatchEvent(new Event('visibilitychange')))
    expect(cb).toHaveBeenCalledTimes(2)
  })

  it('nutzt stets die aktuelle Callback-Referenz', () => {
    const first = vi.fn()
    const second = vi.fn()
    const { rerender } = renderHook(({ cb }) => useRefetchOnFocus(cb), {
      initialProps: { cb: first },
    })
    rerender({ cb: second })

    act(() => window.dispatchEvent(new Event('focus')))
    expect(first).not.toHaveBeenCalled()
    expect(second).toHaveBeenCalledTimes(1)
  })

  it('meldet die Listener beim Unmount ab', () => {
    const cb = vi.fn()
    const { unmount } = renderHook(() => useRefetchOnFocus(cb))
    unmount()
    act(() => window.dispatchEvent(new Event('focus')))
    expect(cb).not.toHaveBeenCalled()
  })
})
