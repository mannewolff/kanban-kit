import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useProjectIdeaEvents } from './useProjectIdeaEvents'

class MockEventSource {
  static instances: MockEventSource[] = []
  url: string
  closed = false
  private readonly listeners = new Map<string, Set<(e: Event) => void>>()
  constructor(url: string) {
    this.url = url
    MockEventSource.instances.push(this)
  }
  addEventListener(type: string, cb: (e: Event) => void): void {
    const set = this.listeners.get(type) ?? new Set()
    set.add(cb)
    this.listeners.set(type, set)
  }
  removeEventListener(type: string, cb: (e: Event) => void): void {
    this.listeners.get(type)?.delete(cb)
  }
  close(): void {
    this.closed = true
  }
  emit(type: string): void {
    this.listeners.get(type)?.forEach((cb) => cb(new Event(type)))
  }
}

describe('useProjectIdeaEvents', () => {
  beforeEach(() => {
    MockEventSource.instances = []
    vi.stubGlobal('EventSource', MockEventSource)
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('öffnet den SSE-Stream für den Ideen-Pool des Projekts', () => {
    renderHook(() => useProjectIdeaEvents(7, vi.fn()))
    expect(MockEventSource.instances).toHaveLength(1)
    expect(MockEventSource.instances[0].url).toBe('/api/projects/7/ideas/events')
  })

  it('ruft onChange debounced bei einem Event (mehrere Events → ein Refetch)', () => {
    const onChange = vi.fn()
    renderHook(() => useProjectIdeaEvents(7, onChange))
    const es = MockEventSource.instances[0]

    act(() => {
      es.emit('project-ideas-changed')
      es.emit('project-ideas-changed')
    })
    expect(onChange).not.toHaveBeenCalled() // erst nach Ablauf des Debounce

    act(() => vi.advanceTimersByTime(250))
    expect(onChange).toHaveBeenCalledTimes(1)
  })

  it('schließt den Stream beim Unmount und feuert danach nicht mehr', () => {
    const onChange = vi.fn()
    const { unmount } = renderHook(() => useProjectIdeaEvents(7, onChange))
    const es = MockEventSource.instances[0]

    act(() => es.emit('project-ideas-changed')) // Debounce-Timer läuft
    unmount()

    expect(es.closed).toBe(true)
    act(() => vi.advanceTimersByTime(250))
    expect(onChange).not.toHaveBeenCalled() // Timer beim Teardown verworfen
  })

  it('schließt den alten und öffnet einen neuen Stream bei Projektwechsel', () => {
    const { rerender } = renderHook(({ id }) => useProjectIdeaEvents(id, vi.fn()), {
      initialProps: { id: 7 },
    })
    const first = MockEventSource.instances[0]

    rerender({ id: 8 })

    expect(first.closed).toBe(true)
    expect(MockEventSource.instances).toHaveLength(2)
    expect(MockEventSource.instances[1].url).toBe('/api/projects/8/ideas/events')
  })

  it('öffnet keinen Stream bei ungültiger Projekt-ID', () => {
    renderHook(() => useProjectIdeaEvents(Number.NaN, vi.fn()))
    renderHook(() => useProjectIdeaEvents(0, vi.fn()))
    expect(MockEventSource.instances).toHaveLength(0)
  })

  it('nutzt die aktuelle onChange-Referenz ohne den Stream neu zu öffnen', () => {
    const first = vi.fn()
    const second = vi.fn()
    const { rerender } = renderHook(({ cb }) => useProjectIdeaEvents(7, cb), {
      initialProps: { cb: first },
    })

    rerender({ cb: second })
    expect(MockEventSource.instances).toHaveLength(1) // kein neuer Stream

    const es = MockEventSource.instances[0]
    act(() => {
      es.emit('project-ideas-changed')
      vi.advanceTimersByTime(250)
    })
    expect(second).toHaveBeenCalledTimes(1)
    expect(first).not.toHaveBeenCalled()
  })
})
