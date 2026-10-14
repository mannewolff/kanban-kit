import { act, fireEvent, renderHook } from '@testing-library/react'
import type { PointerEvent as ReactPointerEvent } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { SCHATTEN_HOCH } from '../theme'
import { HALTEZEIT_MS, RAND_SCROLL_MAX_PX } from './fingerZiehen'
import { useFingerZiehen, type FingerZiel } from './useFingerZiehen'

// jsdom kennt weder `PointerEvent` noch `elementFromPoint` (Plan #1707, E13): Die Attrappe trägt
// `pointerId` und `pointerType`, `fireEvent` greift über `window.PointerEvent` auf sie zu.
class PointerEventAttrappe extends MouseEvent {
  readonly pointerId: number
  readonly pointerType: string
  constructor(type: string, init: PointerEventInit = {}) {
    super(type, init)
    this.pointerId = init.pointerId ?? 0
    this.pointerType = init.pointerType ?? ''
  }
}

function rechteck(left: number, top: number, width: number, height: number): DOMRect {
  return { left, top, width, height, right: left + width, bottom: top + height, x: left, y: top, toJSON: () => ({}) }
}

let flaeche: HTMLElement
let ready: HTMLElement
let doing: HTMLElement
let karte1: HTMLElement
let karte2: HTMLElement
let unterFinger: Element | null

function aufbauen(): void {
  flaeche = document.createElement('div')
  flaeche.dataset.testid = 'board-surface'
  ready = document.createElement('div')
  ready.dataset.spalteId = 'ready'
  karte1 = document.createElement('div')
  karte1.dataset.karteId = '1'
  karte1.textContent = 'Eins'
  karte2 = document.createElement('div')
  karte2.dataset.karteId = '2'
  karte2.textContent = 'Zwei'
  ready.append(karte1, karte2)
  doing = document.createElement('div')
  doing.dataset.spalteId = 'doing'
  flaeche.append(ready, doing)
  document.body.append(flaeche)
  karte1.getBoundingClientRect = () => rechteck(10, 100, 200, 50)
  karte2.getBoundingClientRect = () => rechteck(10, 160, 200, 50)
  flaeche.getBoundingClientRect = () => rechteck(0, 0, 1000, 768)
}

function greifen(onPointerDown: ((e: ReactPointerEvent<HTMLElement>) => void) | undefined, init: Partial<PointerEventInit & { currentTarget: HTMLElement }> = {}) {
  act(() => {
    onPointerDown?.({
      pointerType: 'touch',
      pointerId: 1,
      clientX: 50,
      clientY: 120,
      currentTarget: karte1,
      ...init,
    } as unknown as ReactPointerEvent<HTMLElement>)
  })
}

function bewegen(clientX: number, clientY: number, pointerId = 1): void {
  fireEvent.pointerMove(window, { pointerId, pointerType: 'touch', clientX, clientY })
}

function loslassen(pointerId = 1): void {
  fireEvent.pointerUp(window, { pointerId, pointerType: 'touch' })
}

function warten(ms: number): void {
  act(() => {
    vi.advanceTimersByTime(ms)
  })
}

/** Ein `touchmove`, an dem sich ablesen lässt, ob der Hook das Scrollen verhindert hat. */
function touchmove(): boolean {
  const ereignis = new Event('touchmove', { cancelable: true, bubbles: true })
  act(() => {
    document.dispatchEvent(ereignis)
  })
  return ereignis.defaultPrevented
}

function kopie(): HTMLElement | null {
  return document.querySelector<HTMLElement>('body > [aria-hidden="true"]')
}

function anzeigen(onAblage = vi.fn<(id: string, ziel: FingerZiel) => void>(), aktiv = true) {
  const view = renderHook((props: { aktiv: boolean; onAblage: (id: string, ziel: FingerZiel) => void }) => useFingerZiehen(props), {
    initialProps: { aktiv, onAblage },
  })
  return { ...view, onAblage }
}

beforeEach(() => {
  vi.useFakeTimers({
    toFake: ['setTimeout', 'clearTimeout', 'Date', 'requestAnimationFrame', 'cancelAnimationFrame'],
  })
  vi.stubGlobal('PointerEvent', PointerEventAttrappe)
  aufbauen()
  unterFinger = null
  document.elementFromPoint = vi.fn(() => unterFinger)
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  document.body.innerHTML = ''
  document.documentElement.scrollTop = 0
})

describe('useFingerZiehen — ohne Tablet-Modus und ohne Finger', () => {
  it('liefert ohne aktiv keine Handler und bleibt bereit', () => {
    const { result } = anzeigen(undefined, false)
    expect(result.current.griff('1')).toEqual({})
    expect(result.current.zustand).toBe('bereit')
    expect(result.current.gezogenId).toBeNull()
    expect(result.current.ziel).toBeNull()
  })

  it('liefert Handler, sobald der Tablet-Modus beginnt', () => {
    const { result, rerender, onAblage } = anzeigen(undefined, false)
    expect(result.current.griff('1').onPointerDown).toBeUndefined()
    rerender({ aktiv: true, onAblage })
    greifen(result.current.griff('1').onPointerDown)
    expect(result.current.zustand).toBe('wartet')
  })

  it('reagiert nicht auf die Maus', () => {
    const { result, onAblage } = anzeigen()
    greifen(result.current.griff('1').onPointerDown, { pointerType: 'mouse' })
    warten(HALTEZEIT_MS)
    unterFinger = doing
    bewegen(400, 300)
    loslassen()
    expect(result.current.zustand).toBe('bereit')
    expect(kopie()).toBeNull()
    expect(touchmove()).toBe(false)
    expect(onAblage).not.toHaveBeenCalled()
  })
})

describe('useFingerZiehen — Greifen, Ziehen, Ablegen', () => {
  it('wartet bis 499 ms und zieht ab 500 ms mit angehobener Kopie', () => {
    const { result } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    expect(result.current.zustand).toBe('wartet')
    expect(result.current.gezogenId).toBe('1')
    warten(HALTEZEIT_MS - 1)
    expect(result.current.zustand).toBe('wartet')
    expect(kopie()).toBeNull()

    warten(1)
    expect(result.current.zustand).toBe('zieht')
    const schwebend = kopie()
    expect(schwebend?.textContent).toBe('Eins')
    expect(schwebend?.style.position).toBe('fixed')
    expect(schwebend?.style.pointerEvents).toBe('none')
    expect(schwebend?.style.transform).toBe('scale(1.02)')
    expect(schwebend?.style.boxShadow).toBe(SCHATTEN_HOCH)
    expect(schwebend?.style.width).toBe('200px')
    expect(schwebend?.style.height).toBe('50px')
    // Griffpunkt 50/120 auf der Karte bei 10/100: die Kopie liegt deckungsgleich unter dem Finger.
    expect(schwebend?.style.left).toBe('10px')
    expect(schwebend?.style.top).toBe('100px')
  })

  it('lässt die Kopie dem Finger folgen und legt in einer anderen Spalte ab', () => {
    const { result, onAblage } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    warten(HALTEZEIT_MS)
    unterFinger = doing
    bewegen(450, 300)
    expect(kopie()?.style.left).toBe('410px')
    expect(kopie()?.style.top).toBe('280px')
    expect(result.current.ziel).toEqual({ spalteId: 'doing', index: 0 })
    expect(touchmove()).toBe(true)

    loslassen()
    expect(onAblage).toHaveBeenCalledOnce()
    expect(onAblage).toHaveBeenCalledWith('1', { spalteId: 'doing', index: 0 })
    expect(result.current.zustand).toBe('abgelegt')
    expect(result.current.gezogenId).toBeNull()
    expect(result.current.ziel).toBeNull()
    expect(kopie()).toBeNull()
    expect(touchmove()).toBe(false)
  })

  it('berechnet die Stelle in der eigenen Spalte ohne die gezogene Karte', () => {
    const { result, onAblage } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    warten(HALTEZEIT_MS)
    // Ein Kind der Karte unter dem Finger: Die Spalte wird über `closest` gefunden.
    const kind = document.createElement('span')
    karte2.append(kind)
    unterFinger = kind
    bewegen(50, 184)
    expect(result.current.ziel).toEqual({ spalteId: 'ready', index: 0 })
    bewegen(50, 185)
    expect(result.current.ziel).toEqual({ spalteId: 'ready', index: 1 })
    loslassen()
    expect(onAblage).toHaveBeenCalledOnce()
    expect(onAblage).toHaveBeenCalledWith('1', { spalteId: 'ready', index: 1 })
  })

  it('bricht ab, wenn der Finger außerhalb jeder Spalte losgelassen wird', () => {
    const { result, onAblage } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    warten(HALTEZEIT_MS)
    unterFinger = doing
    bewegen(450, 300)
    unterFinger = flaeche
    bewegen(900, 300)
    expect(result.current.ziel).toBeNull()
    unterFinger = null
    bewegen(900, 310)
    loslassen()
    expect(onAblage).not.toHaveBeenCalled()
    expect(result.current.zustand).toBe('abgebrochen')
    expect(kopie()).toBeNull()
  })

  it('nimmt ohne Skalierung ab, wenn reduzierte Bewegung gewünscht ist', () => {
    const bisher = window.matchMedia
    window.matchMedia = ((query: string) => ({ matches: query === '(prefers-reduced-motion: reduce)' })) as typeof window.matchMedia
    try {
      const { result } = anzeigen()
      greifen(result.current.griff('1').onPointerDown)
      warten(HALTEZEIT_MS)
      expect(kopie()?.style.transform).toBe('none')
    } finally {
      window.matchMedia = bisher
    }
  })

  it('setzt den Außenabstand der Kopie auf null, auch wenn die Karte einen trägt', () => {
    karte1.style.margin = '8px'
    const { result } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    warten(HALTEZEIT_MS)
    expect(kopie()?.style.margin).toBe('0px')
  })

  it('wechselt die Ablagestelle in eine andere Spalte an derselben Stelle', () => {
    const { result, onAblage } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    warten(HALTEZEIT_MS)
    unterFinger = ready
    bewegen(50, 120)
    expect(result.current.ziel).toEqual({ spalteId: 'ready', index: 0 })
    unterFinger = doing
    bewegen(450, 120)
    expect(result.current.ziel).toEqual({ spalteId: 'doing', index: 0 })
    loslassen()
    expect(onAblage).toHaveBeenCalledWith('1', { spalteId: 'doing', index: 0 })
  })

  it('behält die Ablagestelle bei, solange sie sich nicht ändert', () => {
    const { result } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    warten(HALTEZEIT_MS)
    unterFinger = doing
    bewegen(450, 300)
    const erstes = result.current.ziel
    bewegen(460, 310)
    expect(result.current.ziel).toBe(erstes)
  })

  it('meldet den touchmove-Listener ausdrücklich als nicht passiv an', () => {
    const anmelden = vi.spyOn(document, 'addEventListener')
    const { result } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    expect(anmelden).toHaveBeenCalledWith('touchmove', expect.any(Function), { passive: false })
  })

  it('meldet nach dem Ablegen jeden Listener wieder ab', () => {
    const anmelden = vi.spyOn(window, 'addEventListener')
    const abmelden = vi.spyOn(window, 'removeEventListener')
    const dokumentAn = vi.spyOn(document, 'addEventListener')
    const dokumentAb = vi.spyOn(document, 'removeEventListener')
    const { result } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    warten(HALTEZEIT_MS)
    unterFinger = doing
    bewegen(450, 300)
    loslassen()
    for (const typ of ['pointermove', 'pointerup', 'pointercancel']) {
      const angemeldet = anmelden.mock.calls.find(([t]) => t === typ)?.[1]
      expect(angemeldet).toBeTypeOf('function')
      expect(abmelden).toHaveBeenCalledWith(typ, angemeldet)
    }
    const touch = dokumentAn.mock.calls.find(([t]) => t === 'touchmove')?.[1]
    expect(touch).toBeTypeOf('function')
    expect(dokumentAb).toHaveBeenCalledWith('touchmove', touch)
  })

  it('ruft den zuletzt übergebenen Ablage-Callback', () => {
    const erster = vi.fn()
    const zweiter = vi.fn()
    const { result, rerender } = anzeigen(erster)
    greifen(result.current.griff('1').onPointerDown)
    rerender({ aktiv: true, onAblage: zweiter })
    warten(HALTEZEIT_MS)
    unterFinger = doing
    bewegen(450, 300)
    loslassen()
    expect(erster).not.toHaveBeenCalled()
    expect(zweiter).toHaveBeenCalledOnce()
  })
})

describe('useFingerZiehen — Wischen, Antippen, Abbruch', () => {
  it('scrollt beim Wischen über 8 px vor 500 ms: kein Ablegen, kein preventDefault', () => {
    const { result, onAblage } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    expect(touchmove()).toBe(false)
    bewegen(50, 129)
    expect(result.current.zustand).toBe('abgebrochen')
    expect(touchmove()).toBe(false)
    warten(HALTEZEIT_MS)
    unterFinger = doing
    bewegen(450, 300)
    loslassen()
    expect(result.current.zustand).toBe('abgebrochen')
    expect(kopie()).toBeNull()
    expect(onAblage).not.toHaveBeenCalled()
  })

  it('bleibt beim Zittern innerhalb von 8 px im Warten', () => {
    const { result } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    bewegen(50, 128)
    expect(result.current.zustand).toBe('wartet')
    warten(HALTEZEIT_MS)
    expect(result.current.zustand).toBe('zieht')
  })

  it('wertet kurzes Antippen nicht als Zug', () => {
    const { result, onAblage } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    warten(HALTEZEIT_MS - 1)
    loslassen()
    warten(10)
    expect(result.current.zustand).toBe('abgebrochen')
    expect(kopie()).toBeNull()
    expect(onAblage).not.toHaveBeenCalled()
  })

  it('bricht bei pointercancel ab', () => {
    const { result, onAblage } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    warten(HALTEZEIT_MS)
    unterFinger = doing
    bewegen(450, 300)
    fireEvent.pointerCancel(window, { pointerId: 1, pointerType: 'touch' })
    expect(result.current.zustand).toBe('abgebrochen')
    expect(kopie()).toBeNull()
    expect(onAblage).not.toHaveBeenCalled()
  })

  it('übergeht fremde Finger und einen zweiten Griff während des Zugs', () => {
    const { result, onAblage } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    greifen(result.current.griff('2').onPointerDown, { pointerId: 2, currentTarget: karte2 })
    expect(result.current.gezogenId).toBe('1')
    bewegen(400, 400, 2)
    expect(result.current.zustand).toBe('wartet')
    warten(HALTEZEIT_MS)
    unterFinger = doing
    bewegen(450, 300)
    loslassen(2)
    expect(result.current.zustand).toBe('zieht')
    loslassen()
    expect(onAblage).toHaveBeenCalledOnce()
    expect(onAblage).toHaveBeenCalledWith('1', { spalteId: 'doing', index: 0 })
  })

  it('bricht den Zug ab, wenn der Tablet-Modus endet', () => {
    const { result, rerender, onAblage } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    warten(HALTEZEIT_MS)
    rerender({ aktiv: false, onAblage })
    expect(result.current.zustand).toBe('abgebrochen')
    expect(kopie()).toBeNull()
    expect(touchmove()).toBe(false)
  })

  it('räumt beim Aushängen Kopie, Uhr und Listener ab', () => {
    const { result, unmount, onAblage } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    warten(HALTEZEIT_MS)
    unmount()
    expect(kopie()).toBeNull()
    expect(touchmove()).toBe(false)
    unterFinger = doing
    bewegen(450, 300)
    loslassen()
    expect(onAblage).not.toHaveBeenCalled()
  })
})

describe('useFingerZiehen — Rand-Scroll', () => {
  it('scrollt das Dokument senkrecht am unteren und oberen Rand des Ausschnitts', () => {
    Object.defineProperty(document, 'scrollingElement', { configurable: true, get: () => document.documentElement })
    try {
      const { result } = anzeigen()
      greifen(result.current.griff('1').onPointerDown)
      warten(HALTEZEIT_MS)
      document.documentElement.scrollTop = 100
      bewegen(500, window.innerHeight)
      warten(16)
      expect(document.documentElement.scrollTop).toBe(100 + RAND_SCROLL_MAX_PX)
      bewegen(500, 0)
      warten(16)
      expect(document.documentElement.scrollTop).toBe(100)
    } finally {
      Reflect.deleteProperty(document, 'scrollingElement')
    }
  })

  it('greift auf das Wurzelelement zurück, wenn der Browser kein scrollingElement meldet', () => {
    const { result } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    warten(HALTEZEIT_MS)
    bewegen(500, window.innerHeight)
    warten(16)
    expect(document.documentElement.scrollTop).toBe(RAND_SCROLL_MAX_PX)
  })

  it('scrollt die Board-Fläche waagerecht am rechten Rand, nicht in der Mitte', () => {
    const { result } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    warten(HALTEZEIT_MS)
    bewegen(500, 400)
    warten(16)
    expect(flaeche.scrollLeft).toBe(0)
    expect(document.documentElement.scrollTop).toBe(0)
    bewegen(1000, 400)
    warten(16)
    expect(flaeche.scrollLeft).toBe(RAND_SCROLL_MAX_PX)
  })

  it('aktualisiert die Ablagestelle, während der Inhalt unter dem Finger scrollt', () => {
    const { result } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    warten(HALTEZEIT_MS)
    bewegen(1000, 400)
    expect(result.current.ziel).toBeNull()
    unterFinger = doing
    warten(16)
    expect(result.current.ziel).toEqual({ spalteId: 'doing', index: 0 })
  })

  it('scrollt nicht mehr nach dem Ablegen', () => {
    const { result } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    warten(HALTEZEIT_MS)
    bewegen(1000, window.innerHeight)
    loslassen()
    warten(100)
    expect(flaeche.scrollLeft).toBe(0)
    expect(document.documentElement.scrollTop).toBe(0)
  })

  it('scrollt ohne Board-Fläche nur senkrecht', () => {
    flaeche.removeAttribute('data-testid')
    const { result } = anzeigen()
    greifen(result.current.griff('1').onPointerDown)
    warten(HALTEZEIT_MS)
    bewegen(1000, window.innerHeight)
    warten(16)
    expect(flaeche.scrollLeft).toBe(0)
    expect(document.documentElement.scrollTop).toBe(RAND_SCROLL_MAX_PX)
  })
})
