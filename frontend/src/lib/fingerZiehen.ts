/**
 * Gerätefreie Logik für das Ziehen per Finger im Tablet-Modus (Plan #1707, E3–E5). Reine Funktionen
 * ohne DOM: Der Hook `useFingerZiehen` verdrahtet sie mit Zeigerereignissen, Uhr und Seite.
 */

/** So lange muss der Finger ruhen, bis die Karte gegriffen ist (E4: „etwa eine halbe Sekunde“). */
export const HALTEZEIT_MS = 500
/** Bis zu diesem Abstand gilt der Finger als ruhend; darüber wischt er, und der Browser scrollt. */
export const RUHE_TOLERANZ_PX = 8
/** Breite des Randbands, in dem beim Ziehen der Inhalt mitscrollt (E5). */
export const RAND_BAND_PX = 48
/** Scrollweg je Bild, wenn der Finger am äußersten Rand des Bands steht. */
export const RAND_SCROLL_MAX_PX = 16

/** `bereit → wartet → zieht → abgelegt | abgebrochen`; aus einem Endzustand beginnt der nächste Zug. */
export type ZiehZustand = 'bereit' | 'wartet' | 'zieht' | 'abgelegt' | 'abgebrochen'

/** Warum ein Zug abbrach: gewischt (Browser scrollt), angetippt (Karte öffnet) oder `pointercancel`. */
export type AbbruchGrund = 'gewischt' | 'angetippt' | 'abgebrochen'

export interface ZiehStand {
  readonly zustand: ZiehZustand
  readonly startX: number
  readonly startY: number
  readonly startZeit: number
  readonly grund: AbbruchGrund | null
}

export type ZiehEreignis =
  | { readonly typ: 'runter'; readonly x: number; readonly y: number; readonly zeit: number }
  | { readonly typ: 'bewegt'; readonly x: number; readonly y: number; readonly zeit: number }
  | { readonly typ: 'hoch'; readonly zeit: number }
  | { readonly typ: 'uhr'; readonly zeit: number }
  | { readonly typ: 'abbruch' }

export const STAND_BEREIT: ZiehStand = { zustand: 'bereit', startX: 0, startY: 0, startZeit: 0, grund: null }

function laeuft(stand: ZiehStand): boolean {
  return stand.zustand === 'wartet' || stand.zustand === 'zieht'
}

function abgebrochen(stand: ZiehStand, grund: AbbruchGrund): ZiehStand {
  return { ...stand, zustand: 'abgebrochen', grund }
}

function imWarten(stand: ZiehStand, ereignis: Exclude<ZiehEreignis, { typ: 'runter' }>): ZiehStand {
  switch (ereignis.typ) {
    case 'abbruch':
      return abgebrochen(stand, 'abgebrochen')
    case 'hoch':
      return abgebrochen(stand, 'angetippt')
    case 'bewegt':
      // Ist die Haltezeit um, gilt der Griff — auch wenn die Uhr erst mit der Bewegung kommt.
      if (haltezeitUm(stand, ereignis.zeit)) {
        return gegriffen(stand)
      }
      if (Math.hypot(ereignis.x - stand.startX, ereignis.y - stand.startY) > RUHE_TOLERANZ_PX) {
        return abgebrochen(stand, 'gewischt')
      }
      return stand
    case 'uhr':
      return haltezeitUm(stand, ereignis.zeit) ? gegriffen(stand) : stand
  }
}

function haltezeitUm(stand: ZiehStand, zeit: number): boolean {
  return zeit - stand.startZeit >= HALTEZEIT_MS
}

function gegriffen(stand: ZiehStand): ZiehStand {
  return { ...stand, zustand: 'zieht' }
}

function imZiehen(stand: ZiehStand, ereignis: Exclude<ZiehEreignis, { typ: 'runter' }>): ZiehStand {
  if (ereignis.typ === 'hoch') {
    return { ...stand, zustand: 'abgelegt' }
  }
  if (ereignis.typ === 'abbruch') {
    return abgebrochen(stand, 'abgebrochen')
  }
  return stand
}

/**
 * Ein Schritt des Automaten. Unveränderter Stand kommt als dieselbe Referenz zurück, damit der
 * Aufrufer ohne Vergleich der Felder erkennt, dass nichts geschah.
 */
export function ziehSchritt(stand: ZiehStand, ereignis: ZiehEreignis): ZiehStand {
  if (ereignis.typ === 'runter') {
    // Ein zweiter Finger während eines laufenden Zugs zählt nicht.
    return laeuft(stand)
      ? stand
      : { zustand: 'wartet', startX: ereignis.x, startY: ereignis.y, startZeit: ereignis.zeit, grund: null }
  }
  if (stand.zustand === 'wartet') {
    return imWarten(stand, ereignis)
  }
  if (stand.zustand === 'zieht') {
    return imZiehen(stand, ereignis)
  }
  return stand
}

/** Senkrechte Lage einer Karte, wie sie `getBoundingClientRect` liefert. */
export interface KartenRechteck {
  readonly top: number
  readonly height: number
}

/**
 * Stelle, an der eine gezogene Karte in eine Spalte fällt: vor der ersten Karte, deren Mitte unter
 * dem Finger liegt, sonst ans Ende. Die Rechtecke stehen in Spaltenreihenfolge; eine leere Spalte
 * nimmt die Karte an Stelle 0.
 */
export function ablageIndex(y: number, karten: readonly KartenRechteck[]): number {
  const index = karten.findIndex((karte) => y < karte.top + karte.height / 2)
  return index === -1 ? karten.length : index
}

/**
 * Scrollgeschwindigkeit auf einer Achse: negativ im Band am Anfang des Bereichs, positiv im Band am
 * Ende, linear zur Eindringtiefe bis `RAND_SCROLL_MAX_PX`, 0 dazwischen. Jenseits des Rands bleibt
 * es bei der Höchstgeschwindigkeit.
 */
export function randGeschwindigkeit(position: number, anfang: number, ende: number): number {
  const tiefeAnfang = anfang + RAND_BAND_PX - position
  if (tiefeAnfang > 0) {
    return -(Math.min(tiefeAnfang, RAND_BAND_PX) / RAND_BAND_PX) * RAND_SCROLL_MAX_PX
  }
  // Ohne Vergleich: Eine Grenze `> 0` wäre gleichwertig zu `>= 0`, denn Tiefe 0 ergibt ohnehin 0.
  const tiefeEnde = Math.max(position - (ende - RAND_BAND_PX), 0)
  return (Math.min(tiefeEnde, RAND_BAND_PX) / RAND_BAND_PX) * RAND_SCROLL_MAX_PX
}
