import { fireEvent, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useKeyboardShortcut } from './useKeyboardShortcut'

/** Hängt ein Element an den Body und gibt es fokussiert zurück (Aufräumen über afterEach). */
function mount<K extends keyof HTMLElementTagNameMap>(tag: K): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag)
  document.body.appendChild(el)
  return el
}

describe('useKeyboardShortcut', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('ruft den Handler bei der gebundenen Taste auf', () => {
    const onTrigger = vi.fn()
    renderHook(() => useKeyboardShortcut('+', true, onTrigger))

    fireEvent.keyDown(document.body, { key: '+' })

    expect(onTrigger).toHaveBeenCalledTimes(1)
  })

  it('ignoriert eine andere Taste', () => {
    const onTrigger = vi.fn()
    renderHook(() => useKeyboardShortcut('+', true, onTrigger))

    fireEvent.keyDown(document.body, { key: 'a' })

    expect(onTrigger).not.toHaveBeenCalled()
  })

  it.each([{ ctrlKey: true }, { altKey: true }, { metaKey: true }])(
    'ignoriert die Taste mit Modifikator %o',
    (modifier) => {
      const onTrigger = vi.fn()
      renderHook(() => useKeyboardShortcut('+', true, onTrigger))

      fireEvent.keyDown(document.body, { key: '+', ...modifier })

      expect(onTrigger).not.toHaveBeenCalled()
    },
  )

  it.each(['input', 'textarea', 'select'] as const)('greift nicht im %s', (tag) => {
    const onTrigger = vi.fn()
    renderHook(() => useKeyboardShortcut('+', true, onTrigger))

    fireEvent.keyDown(mount(tag), { key: '+' })

    expect(onTrigger).not.toHaveBeenCalled()
  })

  it('greift nicht in editierbarem Inhalt, auch nicht in dessen Kindelementen', () => {
    const onTrigger = vi.fn()
    renderHook(() => useKeyboardShortcut('+', true, onTrigger))
    const editable = mount('div')
    editable.setAttribute('contenteditable', 'true')
    const line = document.createElement('p')
    editable.appendChild(line)

    fireEvent.keyDown(line, { key: '+' })

    expect(onTrigger).not.toHaveBeenCalled()
  })

  it('greift bei einem Ziel, das kein Element ist', () => {
    const onTrigger = vi.fn()
    renderHook(() => useKeyboardShortcut('+', true, onTrigger))

    fireEvent.keyDown(document, { key: '+' })

    expect(onTrigger).toHaveBeenCalledTimes(1)
  })

  it('greift nicht, solange ein Dialog offen ist', () => {
    const onTrigger = vi.fn()
    renderHook(() => useKeyboardShortcut('+', true, onTrigger))
    mount('div').setAttribute('role', 'dialog')

    fireEvent.keyDown(document.body, { key: '+' })

    expect(onTrigger).not.toHaveBeenCalled()
  })

  it('meldet nichts an, solange das Kürzel nicht freigeschaltet ist', () => {
    const onTrigger = vi.fn()
    const { rerender } = renderHook(({ on }) => useKeyboardShortcut('+', on, onTrigger), {
      initialProps: { on: false },
    })

    fireEvent.keyDown(document.body, { key: '+' })
    expect(onTrigger).not.toHaveBeenCalled()

    rerender({ on: true })
    fireEvent.keyDown(document.body, { key: '+' })
    expect(onTrigger).toHaveBeenCalledTimes(1)
  })

  it('meldet den Listener beim Unmount wieder ab', () => {
    const onTrigger = vi.fn()
    const { unmount } = renderHook(() => useKeyboardShortcut('+', true, onTrigger))

    unmount()
    fireEvent.keyDown(document.body, { key: '+' })

    expect(onTrigger).not.toHaveBeenCalled()
  })

  it('nutzt die aktuelle Handler-Referenz ohne neu anzumelden', () => {
    const first = vi.fn()
    const second = vi.fn()
    const { rerender } = renderHook(({ cb }) => useKeyboardShortcut('+', true, cb), {
      initialProps: { cb: first },
    })

    rerender({ cb: second })
    fireEvent.keyDown(document.body, { key: '+' })

    expect(second).toHaveBeenCalledTimes(1)
    expect(first).not.toHaveBeenCalled()
  })
})
