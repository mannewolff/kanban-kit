import type { FlowDay } from '../api/dashboard'
import { formatierer } from '../i18n/format'
import { woerterbuch, type Sprache } from '../i18n/woerterbuch'

/**
 * Die Rechnung der Platten „Fluss“ und „Alter in Arbeit“ (Plan #1765, E9, E16, E17): Stapel, SVG-Pfade
 * und Texte aus den Antworten von `/dashboard/flow` und `/dashboard/aging`. Ohne React, ohne
 * Netzzugriff und ohne Farben — die vergibt die Platte aus dem Theme.
 */

/** Ein Band der Platte „Fluss“, benannt wie die Zahl in `FlowDay`. */
export type FlussBand = 'done' | 'inReview' | 'inProgress' | 'ready' | 'backlog'

/** Die Bänder in Stapelreihenfolge, von unten nach oben (Quelle #1751, Geklärter Stand 4). */
export const FLUSS_BAENDER: readonly FlussBand[] = ['done', 'inReview', 'inProgress', 'ready', 'backlog']

/** Unter- und Oberkante eines Bandes an einem Tag, in Karten. */
export interface BandStapel {
  unten: number
  oben: number
}

/** Der Stapel eines bekannten Tages; ein unbekannter Tag ist `null`. */
export type FlussStapelTag = Readonly<Record<FlussBand, BandStapel>> | null

/**
 * Legt die Bänder je Tag von unten übereinander. Ein Tag mit `known = false` bleibt `null` — eine
 * Lücke, nie ein Stapel aus Nullen (Quelle #1751, AK 18).
 */
export function flussStapel(tage: readonly FlowDay[]): FlussStapelTag[] {
  return tage.map((tag) => {
    if (!tag.known) {
      return null
    }
    let unten = 0
    const stapel = {} as Record<FlussBand, BandStapel>
    for (const band of FLUSS_BAENDER) {
      const oben = unten + (tag[band] ?? 0)
      stapel[band] = { unten, oben }
      unten = oben
    }
    return stapel
  })
}

const koordinate = (wert: number) => String(Math.round(wert * 100) / 100)

/**
 * Ein SVG-Pfad je Band über die Tage, in einer Fläche `breite` × `hoehe`; der höchste Stapel reicht
 * bis oben. Jeder Lauf bekannter Tage ist ein eigener geschlossener Teilpfad — oben vorwärts, unten
 * zurück —, ein unbekannter Tag unterbricht ihn. Ein einzeln stehender bekannter Tag wird zur Säule
 * um seinen Punkt, damit er nicht zur unsichtbaren Linie schrumpft.
 */
export function flussPfade(tage: readonly FlowDay[], breite: number, hoehe: number): Record<FlussBand, string> {
  const stapel = flussStapel(tage)
  const hoechster = Math.max(1, ...stapel.map((s) => s?.backlog.oben ?? 0))
  const schritt = tage.length > 1 ? breite / (tage.length - 1) : breite
  const x = (i: number) => (tage.length > 1 ? i * schritt : breite / 2)
  const y = (wert: number) => hoehe - (wert / hoechster) * hoehe

  const laeufe: number[][] = []
  stapel.forEach((s, i) => {
    if (s === null) {
      return
    }
    const letzter = laeufe.at(-1)
    if (letzter?.at(-1) === i - 1) {
      letzter.push(i)
    } else {
      laeufe.push([i])
    }
  })

  const pfad = (band: FlussBand) =>
    laeufe
      .map((lauf) => {
        const werte = lauf.map((i) => ({ x: x(i), stapel: (stapel[i] as NonNullable<FlussStapelTag>)[band] }))
        const punkte =
          werte.length === 1
            ? [
                { ...werte[0], x: Math.max(0, werte[0].x - schritt / 2) },
                { ...werte[0], x: Math.min(breite, werte[0].x + schritt / 2) },
              ]
            : werte
        const oben = punkte.map((p) => `${koordinate(p.x)},${koordinate(y(p.stapel.oben))}`)
        const unten = punkte.map((p) => `${koordinate(p.x)},${koordinate(y(p.stapel.unten))}`).reverse()
        return [`M${[...oben, ...unten].join(' L')}`, 'Z'].join(' ')
      })
      .join(' ')

  return Object.fromEntries(FLUSS_BAENDER.map((band) => [band, pfad(band)])) as Record<FlussBand, string>
}

/**
 * Die Zahlen eines Tages fürs Überfahren, je Band eine Zeile in der Reihenfolge von oben, wie die
 * Bänder im Bild liegen; ein unbekannter Tag sagt „keine Daten“.
 */
export function flussTagZeilen(tag: FlowDay, sprache: Sprache): string[] {
  const t = woerterbuch(sprache).LEITSTAND_FLUSS
  if (!tag.known) {
    return [t.KEINE_DATEN]
  }
  return [...FLUSS_BAENDER].reverse().map((band) => t.TAG_ZEILE(t.BAND[band], tag[band] ?? 0))
}

/**
 * Der Kopftext der Platte: „seit ‹Datum›“, wenn die sicheren Daten später als vor zwölf Wochen
 * beginnen, sonst „letzte 12 Wochen“. `dataSince` ist ein Kalendertag ohne Zeit und wird als lokaler
 * Tag gelesen, damit er in keiner Zone auf den Vortag rutscht.
 */
export function flussKopf(dataSince: string | null, sprache: Sprache): string {
  const t = woerterbuch(sprache).LEITSTAND_FLUSS
  if (dataSince === null) {
    return t.LETZTE_WOCHEN
  }
  const [jahr, monat, tag] = dataSince.split('-').map(Number)
  return t.SEIT(formatierer(sprache).datum(new Date(jahr, monat - 1, tag)))
}

const STUNDE = 3600
const TAG = 24 * STUNDE

/**
 * Das Alter einer Karte in ihrem Zustand: unter einem Tag in vollen Stunden, sonst in vollen Tagen.
 * Bei unbekanntem Eintritt ist es ein Mindestwert, „mindestens ‹n› Tage“ (Quelle #1751, AK 18).
 */
export function alterText(ageSeconds: number, ageIsMinimum: boolean, sprache: Sprache): string {
  const t = woerterbuch(sprache).LEITSTAND_FLUSS
  let alter: string
  if (ageSeconds >= TAG) {
    alter = t.TAGE(Math.floor(ageSeconds / TAG))
  } else if (ageSeconds >= STUNDE) {
    alter = t.STUNDEN(Math.floor(ageSeconds / STUNDE))
  } else {
    alter = t.UNTER_EINER_STUNDE
  }
  return ageIsMinimum ? t.MINDESTENS(alter) : alter
}
