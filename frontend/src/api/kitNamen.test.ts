import { renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { KitNamen } from './kitNamen'

/** Ausschnitt der Antwort von `GET /api/kit-namen` (Issue #1588). */
const DATEN: KitNamen = {
  sachen: [
    { sache: 'KLAEREN', namen: ['kit:klaeren', 'kit:clarify'], schutz: 'NUR_MENSCH_NIMMT_AB' },
    { sache: 'NIGHT', namen: ['kit:night'], schutz: 'NUR_MENSCH_SETZT' },
    { sache: 'ZIEL_PAKETE', namen: ['ziel:pakete', 'goal:packages'], schutz: 'KEINER' },
  ],
  titelPraefixe: [
    { art: 'FACHLICH', namen: ['[Fachlich]', '[Requirement]'] },
    { art: 'IDEE', namen: ['[Idee]', '[Idea]'] },
    { art: 'MENSCH', namen: ['[Mensch]', '[Human]'] },
    { art: 'PLAN', namen: ['[Plan]'] },
    { art: 'TASK', namen: ['[Task]'] },
  ],
  namensraum: ['kit:', 'lauf:', 'ziel:', 'review:', 'planreview:', 'run:', 'goal:'],
}

function antwort(body: unknown): Response {
  return {
    ok: true,
    status: 200,
    statusText: 'OK',
    text: () => Promise.resolve(JSON.stringify(body)),
  } as Response
}

/** Frisches Modul je Test, damit der Sitzungs-Cache leer beginnt. */
async function modul() {
  return import('./kitNamen')
}

beforeEach(() => vi.resetModules())
afterEach(() => vi.restoreAllMocks())

describe('ladeKitNamen', () => {
  it('ruft /api/kit-namen je Sitzung genau einmal auf, auch bei mehreren Aufrufen', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(antwort(DATEN))
    const { ladeKitNamen } = await modul()
    const [a, b] = await Promise.all([ladeKitNamen(), ladeKitNamen()])
    const c = await ladeKitNamen()
    expect(a).toEqual(DATEN)
    expect(b).toEqual(DATEN)
    expect(c).toEqual(DATEN)
    expect(spy).toHaveBeenCalledTimes(1)
    expect(spy.mock.calls[0][0]).toBe('/api/kit-namen')
  })

  it('reicht die Schutzregel je Sache durch (Issue #1670)', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(antwort(DATEN))
    const { ladeKitNamen } = await modul()
    const daten = await ladeKitNamen()
    expect(daten.sachen.map((s) => [s.sache, s.schutz])).toEqual([
      ['KLAEREN', 'NUR_MENSCH_NIMMT_AB'],
      ['NIGHT', 'NUR_MENSCH_SETZT'],
      ['ZIEL_PAKETE', 'KEINER'],
    ])
  })

  it('lädt nach einem Fehler beim nächsten Aufruf erneut', async () => {
    const spy = vi
      .spyOn(globalThis, 'fetch')
      .mockRejectedValueOnce(new Error('netz weg'))
      .mockResolvedValue(antwort(DATEN))
    const { ladeKitNamen } = await modul()
    await expect(ladeKitNamen()).rejects.toThrow('netz weg')
    await expect(ladeKitNamen()).resolves.toEqual(DATEN)
    expect(spy).toHaveBeenCalledTimes(2)
  })
})

describe('useKitNamen', () => {
  it('liefert nacheinander Laden, dann Daten', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(antwort(DATEN))
    const { useKitNamen } = await modul()
    const { result } = renderHook(() => useKitNamen())
    expect(result.current).toEqual({ status: 'laden' })
    await waitFor(() => expect(result.current).toEqual({ status: 'fertig', daten: DATEN }))
  })

  it('liefert bei einem Fehler den Fehler', async () => {
    const fehler = new Error('kaputt')
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(fehler)
    const { useKitNamen } = await modul()
    const { result } = renderHook(() => useKitNamen())
    expect(result.current).toEqual({ status: 'laden' })
    await waitFor(() => expect(result.current).toEqual({ status: 'fehler', fehler }))
  })

  it('setzt nach dem Abbau keinen Zustand mehr', async () => {
    let freigeben: (r: Response) => void = () => {}
    vi.spyOn(globalThis, 'fetch').mockReturnValue(
      new Promise<Response>((resolve) => {
        freigeben = resolve
      }),
    )
    const { useKitNamen, ladeKitNamen } = await modul()
    const { result, unmount } = renderHook(() => useKitNamen())
    unmount()
    freigeben(antwort(DATEN))
    await ladeKitNamen()
    expect(result.current).toEqual({ status: 'laden' })
  })

  it('setzt nach dem Abbau auch keinen Fehler mehr', async () => {
    let scheitern: (e: Error) => void = () => {}
    vi.spyOn(globalThis, 'fetch').mockReturnValue(
      new Promise<Response>((_, reject) => {
        scheitern = reject
      }),
    )
    const { useKitNamen } = await modul()
    const { result, unmount } = renderHook(() => useKitNamen())
    unmount()
    scheitern(new Error('zu spät'))
    await new Promise((r) => setTimeout(r, 0))
    expect(result.current).toEqual({ status: 'laden' })
  })
})

/**
 * Nach dem Abbau verwirft React ein `setState` still, `result.current` bleibt also auch ohne
 * Schutz bei `laden`. Diese Tests beobachten darum den Setter selbst (Issue #1601).
 */
describe('useKitNamen — Aufrufe des Zustands-Setters', () => {
  async function modulMitSetterSpion() {
    const setter = vi.fn()
    vi.doMock('react', async () => {
      const react = await vi.importActual<typeof import('react')>('react')
      return {
        ...react,
        useState: <T>(start: T) => {
          const [wert, setzen] = react.useState(start)
          return [
            wert,
            (neu: T) => {
              setter(neu)
              setzen(neu)
            },
          ] as const
        },
      }
    })
    return { setter, ...(await modul()) }
  }

  afterEach(() => vi.doUnmock('react'))

  it('ruft den Setter nach dem Abbau mit Daten nicht mehr auf', async () => {
    let freigeben: (r: Response) => void = () => {}
    vi.spyOn(globalThis, 'fetch').mockReturnValue(
      new Promise<Response>((resolve) => {
        freigeben = resolve
      }),
    )
    const { setter, useKitNamen, ladeKitNamen } = await modulMitSetterSpion()
    const { unmount } = renderHook(() => useKitNamen())
    unmount()
    freigeben(antwort(DATEN))
    await ladeKitNamen()
    await new Promise((r) => setTimeout(r, 0))
    expect(setter).not.toHaveBeenCalled()
  })

  it('ruft den Setter nach dem Abbau mit einem Fehler nicht mehr auf', async () => {
    let scheitern: (e: Error) => void = () => {}
    vi.spyOn(globalThis, 'fetch').mockReturnValue(
      new Promise<Response>((_, reject) => {
        scheitern = reject
      }),
    )
    const { setter, useKitNamen } = await modulMitSetterSpion()
    const { unmount } = renderHook(() => useKitNamen())
    unmount()
    scheitern(new Error('zu spät'))
    await new Promise((r) => setTimeout(r, 0))
    expect(setter).not.toHaveBeenCalled()
  })
})

describe('namenDerSache', () => {
  it('liefert alle Namen der Sache, der deutsche zuerst', async () => {
    const { namenDerSache } = await modul()
    expect(namenDerSache(DATEN, 'KLAEREN')).toEqual(['kit:klaeren', 'kit:clarify'])
  })

  it('liefert für eine unbekannte Sache eine leere Liste', async () => {
    const { namenDerSache } = await modul()
    expect(namenDerSache(DATEN, 'GIBTS_NICHT')).toEqual([])
  })
})

describe('sacheVon', () => {
  it('findet die Sache zu einem englischen Namen', async () => {
    const { sacheVon } = await modul()
    expect(sacheVon(DATEN, 'goal:packages')).toBe('ZIEL_PAKETE')
  })

  it('findet die Sache zu einem deutschen Namen mit umgebendem Leerraum', async () => {
    const { sacheVon } = await modul()
    expect(sacheVon(DATEN, '  kit:klaeren ')).toBe('KLAEREN')
  })

  it('liefert für einen unbekannten Namen undefined', async () => {
    const { sacheVon } = await modul()
    expect(sacheVon(DATEN, 'kit:unbekannt')).toBeUndefined()
  })

  it('unterscheidet die Schreibung wie das Backend', async () => {
    const { sacheVon } = await modul()
    expect(sacheVon(DATEN, 'KIT:KLAEREN')).toBeUndefined()
  })
})

describe('boardName', () => {
  it('nimmt den Namen, den das Board trägt', async () => {
    const { boardName } = await modul()
    expect(boardName(DATEN, 'ZIEL_PAKETE', ['bug', 'goal:packages'])).toBe('goal:packages')
    expect(boardName(DATEN, 'ZIEL_PAKETE', ['ziel:pakete'])).toBe('ziel:pakete')
  })

  it('nimmt den deutschen, wenn das Board beide trägt', async () => {
    const { boardName } = await modul()
    expect(boardName(DATEN, 'ZIEL_PAKETE', ['goal:packages', 'ziel:pakete'])).toBe('ziel:pakete')
  })

  it('nimmt den deutschen, wenn das Board keinen trägt', async () => {
    const { boardName } = await modul()
    expect(boardName(DATEN, 'ZIEL_PAKETE', ['bug'])).toBe('ziel:pakete')
  })

  it('liefert für eine unbekannte Sache undefined', async () => {
    const { boardName } = await modul()
    expect(boardName(DATEN, 'GIBTS_NICHT', ['bug'])).toBeUndefined()
  })
})

describe('artAusTitel', () => {
  it.each([
    ['[Requirement] Englisch schalten', 'FACHLICH'],
    ['[Fachlich] Deutsch', 'FACHLICH'],
    ['[Idea] etwas', 'IDEE'],
    ['[Human] Schritt', 'MENSCH'],
    ['[Plan] Umbau', 'PLAN'],
    ['[Task] Kleinkram', 'TASK'],
    ['   [human] mit Leerraum', 'MENSCH'],
    ['\t[IDEE] groß', 'IDEE'],
  ])('%s → %s', async (titel, art) => {
    const { artAusTitel } = await modul()
    expect(artAusTitel(DATEN, titel)).toBe(art)
  })

  it.each([
    ['Arbeitspaket ohne Präfix'],
    ['Vorne Text [Idee] hinten'],
    ['[Idee'],
    [''],
  ])('%s → undefined', async (titel) => {
    const { artAusTitel } = await modul()
    expect(artAusTitel(DATEN, titel)).toBeUndefined()
  })

  it('behandelt Sonderzeichen im Präfix wörtlich', async () => {
    const { artAusTitel } = await modul()
    const daten: KitNamen = { ...DATEN, titelPraefixe: [{ art: 'X', namen: ['[a.b]'] }] }
    expect(artAusTitel(daten, '[a.b] passt')).toBe('X')
    expect(artAusTitel(daten, '[axb] passt nicht')).toBeUndefined()
  })
})
