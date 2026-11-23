import { act, renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Attachment } from '../api/attachments'
import { useAnhangHochladen } from './useAnhangHochladen'

const pdf: Attachment = { id: 8, cardId: 100, filename: 'neu.pdf', contentType: 'application/pdf', size: 100, createdAt: '' }
const bild: Attachment = { id: 9, cardId: 100, filename: 'foto.png', contentType: 'image/png', size: 100, createdAt: '' }

function apiMit(upload: ReturnType<typeof vi.fn>, fetchBlob: ReturnType<typeof vi.fn> = vi.fn()) {
  return { upload, fetchBlob }
}

describe('useAnhangHochladen', () => {
  it('lädt eine Datei an die Karte und liefert den Anhang ohne Vorschau, wenn es kein Bild ist', async () => {
    const api = apiMit(vi.fn().mockResolvedValue(pdf))
    const { result } = renderHook(() => useAnhangHochladen(api))
    const datei = new File(['x'], 'neu.pdf', { type: 'application/pdf' })

    let ergebnis: Awaited<ReturnType<typeof result.current.hochladen>> = null
    await act(async () => {
      ergebnis = await result.current.hochladen(100, datei)
    })

    expect(api.upload).toHaveBeenCalledWith(100, datei)
    expect(api.fetchBlob).not.toHaveBeenCalled()
    expect(ergebnis).toEqual({ anhang: pdf, vorschauUrl: null })
    expect(result.current.fehler).toBeNull()
  })

  it('holt bei einem Bild die Vorschau gleich mit', async () => {
    // jsdom kennt createObjectURL nicht.
    URL.createObjectURL = vi.fn(() => 'blob:vorschau')
    const api = apiMit(vi.fn().mockResolvedValue(bild), vi.fn().mockResolvedValue(new Blob(['x'])))
    const { result } = renderHook(() => useAnhangHochladen(api))

    let ergebnis: Awaited<ReturnType<typeof result.current.hochladen>> = null
    await act(async () => {
      ergebnis = await result.current.hochladen(100, new File(['x'], 'foto.png', { type: 'image/png' }))
    })

    expect(api.fetchBlob).toHaveBeenCalledWith(9)
    expect(ergebnis).toEqual({ anhang: bild, vorschauUrl: 'blob:vorschau' })
  })

  it('holt ohne Vorschau-Wunsch auch bei einem Bild keine Vorschau', async () => {
    const api = apiMit(vi.fn().mockResolvedValue(bild))
    const { result } = renderHook(() => useAnhangHochladen(api))

    let ergebnis: Awaited<ReturnType<typeof result.current.hochladen>> = null
    await act(async () => {
      ergebnis = await result.current.hochladen(100, new File(['x'], 'foto.png'), { vorschau: false })
    })

    expect(api.fetchBlob).not.toHaveBeenCalled()
    expect(ergebnis).toEqual({ anhang: bild, vorschauUrl: null })
  })

  it('meldet einen gescheiterten Upload und liefert nichts', async () => {
    const api = apiMit(vi.fn().mockRejectedValue(new Error('boom')))
    const { result } = renderHook(() => useAnhangHochladen(api))

    let ergebnis: Awaited<ReturnType<typeof result.current.hochladen>> = null
    await act(async () => {
      ergebnis = await result.current.hochladen(100, new File(['x'], 'neu.pdf'))
    })

    expect(ergebnis).toBeNull()
    expect(result.current.fehler).toBe('Upload fehlgeschlagen (evtl. Anhangslimit erreicht).')
  })

  it('meldet eine gescheiterte Vorschau getrennt und liefert den Anhang trotzdem', async () => {
    const api = apiMit(vi.fn().mockResolvedValue(bild), vi.fn().mockRejectedValue(new Error('boom')))
    const { result } = renderHook(() => useAnhangHochladen(api))

    let ergebnis: Awaited<ReturnType<typeof result.current.hochladen>> = null
    await act(async () => {
      ergebnis = await result.current.hochladen(100, new File(['x'], 'foto.png'))
    })

    expect(ergebnis).toEqual({ anhang: bild, vorschauUrl: null })
    expect(result.current.fehler).toBe('Anhang hochgeladen, Vorschau konnte nicht geladen werden.')
  })

  it('räumt einen alten Fehler beim nächsten Hochladen ab', async () => {
    const upload = vi.fn().mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce(pdf)
    const { result } = renderHook(() => useAnhangHochladen(apiMit(upload)))

    await act(async () => {
      await result.current.hochladen(100, new File(['x'], 'neu.pdf'))
    })
    expect(result.current.fehler).not.toBeNull()
    await act(async () => {
      await result.current.hochladen(100, new File(['x'], 'neu.pdf'))
    })
    expect(result.current.fehler).toBeNull()
  })

  it('lässt den Fehler von außen setzen', () => {
    const { result } = renderHook(() => useAnhangHochladen(apiMit(vi.fn())))
    act(() => result.current.setFehler('anders'))
    expect(result.current.fehler).toBe('anders')
  })
})
