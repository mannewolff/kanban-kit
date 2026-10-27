import { useCallback, useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { SCHATTEN_HOCH, theme } from '../theme'
import {
  ablageIndex,
  HALTEZEIT_MS,
  randGeschwindigkeit,
  STAND_BEREIT,
  ziehSchritt,
  type ZiehEreignis,
  type ZiehZustand,
} from './fingerZiehen'

/** Wohin die gezogene Karte fiele: Spalte und Stelle darin, ohne die gezogene Karte gezählt. */
export interface FingerZiel {
  readonly spalteId: string
  readonly index: number
}

export interface FingerZiehenOptionen {
  /** Nur im Tablet-Modus wahr; der Aufrufer reicht `useTabletQuer()` durch. */
  readonly aktiv: boolean
  /** Wird nach dem Loslassen über einer Spalte gerufen, mit der Kennung des gegriffenen Elements. */
  readonly onAblage: (id: string, ziel: FingerZiel) => void
}

export interface FingerZiehen {
  /** Handler für das gegriffene Element; ohne `aktiv` leer. */
  readonly griff: (id: string) => { onPointerDown?: (e: ReactPointerEvent<HTMLElement>) => void }
  /** Stand des Zugs, für `data-zieh-zustand`. */
  readonly zustand: ZiehZustand
  /** Kennung des Elements, das gerade gegriffen ist oder wartet. */
  readonly gezogenId: string | null
  /** Ablagestelle unter dem Finger, solange gezogen wird. */
  readonly ziel: FingerZiel | null
}

const BOARD_FLAECHE = '[data-testid="board-surface"]'

/** Die fest positionierte, angehobene Kopie, die dem Finger folgt (E5). */
function schwebendeKopie(element: HTMLElement, rechteck: DOMRect): HTMLElement {
  const kopie = element.cloneNode(true) as HTMLElement
  const reduziert = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  kopie.setAttribute('aria-hidden', 'true')
  Object.assign(kopie.style, {
    position: 'fixed',
    margin: '0',
    width: `${rechteck.width}px`,
    height: `${rechteck.height}px`,
    boxShadow: SCHATTEN_HOCH,
    transform: reduziert ? 'none' : 'scale(1.02)',
    pointerEvents: 'none',
    zIndex: String(theme.zIndex.tooltip),
  })
  return kopie
}

/** Spalte und Stelle unter dem Punkt; die gezogene Karte selbst zählt nicht mit. */
function zielUnter(x: number, y: number, gezogenId: string): FingerZiel | null {
  const spalte = document.elementFromPoint(x, y)?.closest<HTMLElement>('[data-spalte-id]')
  if (!spalte) {
    return null
  }
  const karten = Array.from(spalte.querySelectorAll<HTMLElement>('[data-karte-id]'))
    .filter((karte) => karte.dataset.karteId !== gezogenId)
    .map((karte) => karte.getBoundingClientRect())
  return { spalteId: String(spalte.dataset.spalteId), index: ablageIndex(y, karten) }
}

/**
 * Ziehen per Finger im Tablet-Modus (Plan #1707, E3–E5): Pointer-Ereignisse mit
 * `pointerType === 'touch'`, neben dem unveränderten HTML5-Pfad der Maus. Nach `HALTEZEIT_MS`
 * Ruhe hebt sich eine Kopie und folgt dem Finger; ein nicht-passiver `touchmove`-Listener hält
 * dabei die Seite still. Am Rand der Board-Fläche rollt die Fläche auf beiden Achsen mit, gemessen
 * an ihren eigenen Kanten; das Dokument rollt nicht, denn seit Issue #1901 rollt allein die Fläche.
 * Ohne `aktiv` registriert der Hook nichts, ebenso in der „Ansicht als XY“: Dort ist Ziehen und
 * Ablegen aus (Plan #1914, E15).
 */
export function useFingerZiehen({ aktiv, onAblage }: FingerZiehenOptionen): FingerZiehen {
  const { vorschau } = useAuth()
  const greifbar = aktiv && !vorschau
  const [zustand, setZustand] = useState<ZiehZustand>('bereit')
  const [gezogenId, setGezogenId] = useState<string | null>(null)
  const [ziel, setZiel] = useState<FingerZiel | null>(null)
  const ablageRef = useRef(onAblage)
  useEffect(() => {
    ablageRef.current = onAblage
  })
  /** Bricht den laufenden Zug ab; `null`, solange keiner läuft. */
  const abbrechenRef = useRef<(() => void) | null>(null)

  // Endet der Tablet-Modus oder hängt die Komponente aus, endet auch ein laufender Zug.
  useEffect(() => () => abbrechenRef.current?.(), [greifbar])

  // Stryker disable ArrayDeclaration: gleichwertig — eine feste Abhängigkeit hält die Funktion ebenso stabil wie keine; ein next-line-Kommentar im Funktionskörper erreicht die Liste nicht
  const starteZug = useCallback((id: string, element: HTMLElement, pointerId: number, startX: number, startY: number) => {
    let stand = ziehSchritt(STAND_BEREIT, { typ: 'runter', x: startX, y: startY, zeit: Date.now() })
    let x = startX
    let y = startY
    let aktuellesZiel: FingerZiel | null = null
    let bild = 0
    const rechteck = element.getBoundingClientRect()
    const versatzX = startX - rechteck.left
    const versatzY = startY - rechteck.top
    const kopie = schwebendeKopie(element, rechteck)

    const zielAktualisieren = () => {
      const neu = zielUnter(x, y, id)
      if (neu?.spalteId !== aktuellesZiel?.spalteId || neu?.index !== aktuellesZiel?.index) {
        aktuellesZiel = neu
        setZiel(neu)
      }
    }

    const randScroll = () => {
      const flaeche = document.querySelector<HTMLElement>(BOARD_FLAECHE)
      if (flaeche) {
        const rahmen = flaeche.getBoundingClientRect()
        flaeche.scrollTop += randGeschwindigkeit(y, rahmen.top, rahmen.bottom)
        flaeche.scrollLeft += randGeschwindigkeit(x, rahmen.left, rahmen.right)
      }
      zielAktualisieren()
      bild = window.requestAnimationFrame(randScroll)
    }

    const beenden = (ende: ZiehZustand) => {
      window.clearTimeout(uhr)
      window.cancelAnimationFrame(bild)
      kopie.remove()
      window.removeEventListener('pointermove', beiBewegung)
      window.removeEventListener('pointerup', beiLoslassen)
      window.removeEventListener('pointercancel', beiAbbruch)
      document.removeEventListener('touchmove', beiTouchmove)
      abbrechenRef.current = null
      setZustand(ende)
      setGezogenId(null)
      setZiel(null)
    }

    const schritt = (ereignis: ZiehEreignis) => {
      const vorher = stand.zustand
      stand = ziehSchritt(stand, ereignis)
      if (stand.zustand === 'zieht') {
        if (vorher === 'wartet') {
          document.body.append(kopie)
          bild = window.requestAnimationFrame(randScroll)
          setZustand('zieht')
        }
        kopie.style.left = `${x - versatzX}px`
        kopie.style.top = `${y - versatzY}px`
        zielAktualisieren()
      } else if (stand.zustand === 'abgelegt') {
        // Ohne Spalte unter dem Finger gibt es keine Ablagestelle: Der Zug gilt als abgebrochen.
        const abgelegtIn = aktuellesZiel
        beenden(abgelegtIn ? 'abgelegt' : 'abgebrochen')
        if (abgelegtIn) {
          ablageRef.current(id, abgelegtIn)
        }
      } else if (stand.zustand === 'abgebrochen') {
        beenden('abgebrochen')
      }
    }

    const nurDieserFinger = (handler: (e: PointerEvent) => void) => (e: PointerEvent) => {
      if (e.pointerId === pointerId) {
        handler(e)
      }
    }
    const beiBewegung = nurDieserFinger((e) => {
      x = e.clientX
      y = e.clientY
      schritt({ typ: 'bewegt', x, y, zeit: Date.now() })
    })
    const beiLoslassen = nurDieserFinger(() => schritt({ typ: 'hoch', zeit: Date.now() }))
    const beiAbbruch = nurDieserFinger(() => schritt({ typ: 'abbruch' }))
    // Nur im Zustand „zieht“ hält der Listener die Seite still; beim Warten scrollt der Browser.
    const beiTouchmove = (e: TouchEvent) => {
      if (stand.zustand === 'zieht') {
        e.preventDefault()
      }
    }

    window.addEventListener('pointermove', beiBewegung)
    window.addEventListener('pointerup', beiLoslassen)
    window.addEventListener('pointercancel', beiAbbruch)
    document.addEventListener('touchmove', beiTouchmove, { passive: false })
    const uhr = window.setTimeout(() => schritt({ typ: 'uhr', zeit: Date.now() }), HALTEZEIT_MS)
    abbrechenRef.current = () => schritt({ typ: 'abbruch' })
    setZustand('wartet')
    setGezogenId(id)
  }, [])
  // Stryker restore ArrayDeclaration

  const griff = useCallback(
    (id: string) =>
      greifbar
        ? {
            onPointerDown: (e: ReactPointerEvent<HTMLElement>) => {
              if (e.pointerType === 'touch' && !abbrechenRef.current) {
                starteZug(id, e.currentTarget, e.pointerId, e.clientX, e.clientY)
              }
            },
          }
        : {},
    [greifbar, starteZug],
  )

  return { griff, zustand, gezogenId, ziel }
}
