import { describe, expect, it } from 'vitest'
import {
  ablageIndex,
  HALTEZEIT_MS,
  RAND_BAND_PX,
  RAND_SCROLL_MAX_PX,
  randGeschwindigkeit,
  RUHE_TOLERANZ_PX,
  STAND_BEREIT,
  ziehSchritt,
  type ZiehStand,
} from './fingerZiehen'

const wartet: ZiehStand = { zustand: 'wartet', startX: 100, startY: 200, startZeit: 1000, grund: null }
const zieht: ZiehStand = { ...wartet, zustand: 'zieht' }

describe('fingerZiehen — Konstanten', () => {
  it('nennt Haltezeit, Ruhetoleranz und Randband aus dem Plan', () => {
    expect(HALTEZEIT_MS).toBe(500)
    expect(RUHE_TOLERANZ_PX).toBe(8)
    expect(RAND_BAND_PX).toBe(48)
    expect(RAND_SCROLL_MAX_PX).toBeGreaterThan(0)
  })
})

describe('ziehSchritt — Zustandsautomat', () => {
  it('beginnt bereit und wartet nach dem Aufsetzen des Fingers', () => {
    expect(STAND_BEREIT).toEqual({ zustand: 'bereit', startX: 0, startY: 0, startZeit: 0, grund: null })
    expect(ziehSchritt(STAND_BEREIT, { typ: 'runter', x: 100, y: 200, zeit: 1000 })).toEqual(wartet)
  })

  it('beginnt nach einem beendeten Zug einen neuen', () => {
    const abgelegt: ZiehStand = { ...zieht, zustand: 'abgelegt' }
    const abgebrochen: ZiehStand = { ...wartet, zustand: 'abgebrochen', grund: 'gewischt' }
    const runter = { typ: 'runter', x: 100, y: 200, zeit: 1000 } as const
    expect(ziehSchritt(abgelegt, runter)).toEqual(wartet)
    expect(ziehSchritt(abgebrochen, runter)).toEqual(wartet)
  })

  it('übergeht einen zweiten Finger während Warten und Ziehen', () => {
    const runter = { typ: 'runter', x: 1, y: 1, zeit: 1200 } as const
    expect(ziehSchritt(wartet, runter)).toBe(wartet)
    expect(ziehSchritt(zieht, runter)).toBe(zieht)
  })

  it('zieht nach genau 500 ms Ruhe, nicht nach 499 ms', () => {
    expect(ziehSchritt(wartet, { typ: 'uhr', zeit: 1499 })).toBe(wartet)
    expect(ziehSchritt(wartet, { typ: 'uhr', zeit: 1500 })).toEqual(zieht)
  })

  it('zieht auch, wenn die erste Bewegung nach der Haltezeit kommt', () => {
    expect(ziehSchritt(wartet, { typ: 'bewegt', x: 130, y: 200, zeit: 1500 })).toEqual(zieht)
  })

  it('bleibt bei 8 px Bewegung wartend und bricht bei 9 px ab', () => {
    expect(ziehSchritt(wartet, { typ: 'bewegt', x: 108, y: 200, zeit: 1100 })).toBe(wartet)
    expect(ziehSchritt(wartet, { typ: 'bewegt', x: 100, y: 192, zeit: 1100 })).toBe(wartet)
    expect(ziehSchritt(wartet, { typ: 'bewegt', x: 109, y: 200, zeit: 1100 })).toEqual({
      ...wartet,
      zustand: 'abgebrochen',
      grund: 'gewischt',
    })
    expect(ziehSchritt(wartet, { typ: 'bewegt', x: 100, y: 191, zeit: 1100 })).toEqual({
      ...wartet,
      zustand: 'abgebrochen',
      grund: 'gewischt',
    })
  })

  it('misst die Bewegung als Abstand, nicht je Achse', () => {
    // 6 px und 6 px ergeben rund 8,5 px — über der Toleranz.
    expect(ziehSchritt(wartet, { typ: 'bewegt', x: 106, y: 206, zeit: 1100 }).zustand).toBe('abgebrochen')
  })

  it('wertet Loslassen vor der Haltezeit als Antippen', () => {
    expect(ziehSchritt(wartet, { typ: 'hoch', zeit: 1499 })).toEqual({
      ...wartet,
      zustand: 'abgebrochen',
      grund: 'angetippt',
    })
  })

  it('legt beim Loslassen während des Ziehens ab', () => {
    expect(ziehSchritt(zieht, { typ: 'hoch', zeit: 3000 })).toEqual({ ...zieht, zustand: 'abgelegt' })
  })

  it('bricht bei pointercancel im Warten und im Ziehen ab', () => {
    expect(ziehSchritt(wartet, { typ: 'abbruch' })).toEqual({ ...wartet, zustand: 'abgebrochen', grund: 'abgebrochen' })
    expect(ziehSchritt(zieht, { typ: 'abbruch' })).toEqual({ ...zieht, zustand: 'abgebrochen', grund: 'abgebrochen' })
  })

  it('bleibt beim Bewegen und bei der Uhr während des Ziehens', () => {
    expect(ziehSchritt(zieht, { typ: 'bewegt', x: 900, y: 900, zeit: 4000 })).toBe(zieht)
    expect(ziehSchritt(zieht, { typ: 'uhr', zeit: 4000 })).toBe(zieht)
  })

  it('übergeht alles außer dem Aufsetzen, solange kein Zug läuft', () => {
    const abgelegt: ZiehStand = { ...zieht, zustand: 'abgelegt' }
    for (const stand of [STAND_BEREIT, abgelegt]) {
      expect(ziehSchritt(stand, { typ: 'bewegt', x: 1, y: 1, zeit: 1 })).toBe(stand)
      expect(ziehSchritt(stand, { typ: 'hoch', zeit: 1 })).toBe(stand)
      expect(ziehSchritt(stand, { typ: 'uhr', zeit: 99999 })).toBe(stand)
      expect(ziehSchritt(stand, { typ: 'abbruch' })).toBe(stand)
    }
  })
})

describe('ablageIndex', () => {
  const karten = [
    { top: 0, height: 100 },
    { top: 110, height: 100 },
  ]

  it('liefert 0 in einer leeren Spalte', () => {
    expect(ablageIndex(500, [])).toBe(0)
  })

  it('legt oberhalb der Kartenmitte davor, ab der Mitte danach ab', () => {
    expect(ablageIndex(-20, karten)).toBe(0)
    expect(ablageIndex(49, karten)).toBe(0)
    expect(ablageIndex(50, karten)).toBe(1)
    expect(ablageIndex(159, karten)).toBe(1)
    expect(ablageIndex(160, karten)).toBe(2)
    expect(ablageIndex(900, karten)).toBe(2)
  })
})

describe('randGeschwindigkeit', () => {
  it('ist 0 außerhalb des Randbands und an seiner Grenze', () => {
    expect(randGeschwindigkeit(500, 0, 1000)).toBe(0)
    expect(randGeschwindigkeit(48, 0, 1000)).toBe(0)
    expect(randGeschwindigkeit(952, 0, 1000)).toBe(0)
  })

  it('wächst linear mit der Eindringtiefe, am Anfang rückwärts', () => {
    expect(randGeschwindigkeit(24, 0, 1000)).toBe(-RAND_SCROLL_MAX_PX / 2)
    expect(randGeschwindigkeit(0, 0, 1000)).toBe(-RAND_SCROLL_MAX_PX)
    expect(randGeschwindigkeit(976, 0, 1000)).toBe(RAND_SCROLL_MAX_PX / 2)
    expect(randGeschwindigkeit(1000, 0, 1000)).toBe(RAND_SCROLL_MAX_PX)
  })

  it('rechnet relativ zum übergebenen Bereich', () => {
    expect(randGeschwindigkeit(224, 200, 800)).toBe(-RAND_SCROLL_MAX_PX / 2)
    expect(randGeschwindigkeit(776, 200, 800)).toBe(RAND_SCROLL_MAX_PX / 2)
  })

  it('begrenzt auf die Höchstgeschwindigkeit, wenn der Finger über den Rand hinausgeht', () => {
    expect(randGeschwindigkeit(-30, 0, 1000)).toBe(-RAND_SCROLL_MAX_PX)
    expect(randGeschwindigkeit(1030, 0, 1000)).toBe(RAND_SCROLL_MAX_PX)
  })
})
