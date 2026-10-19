import type { Woerterbuch } from '../i18n/de'
import { woerterbuch, type Sprache } from '../i18n/woerterbuch'
import { SZENARIEN_ABSCHNITT, SZENARIO_SCHRITT, passe } from './kitWortformen'

/**
 * Die Szenarien eines Fachplans fuer die Abnahme von Hand (Issue #1627, Plan #1626): lesen, den
 * Stand der Vermerke zaehlen und den Ergebnis-Kommentar bauen. Die einzige Stelle, die das
 * Szenario-Format kennt; die Wortformen selbst stehen in `kitWortformen.ts` (E1).
 */

/** Ein Szenario mit seinen Schritten in der Reihenfolge des Fachplans. */
export interface Szenario {
  name: string
  schritte: string[]
}

/** Der Vermerk des Menschen zu einem Szenario; die Notiz ist freiwillig (E9). */
export interface Vermerk {
  urteil: 'okay' | 'nicht okay'
  notiz: string
}

/** Ein nicht okayes Szenario mit seiner Nummer (1-basiert) — gleiche Namen sind moeglich (E12). */
export interface NichtOkaySzenario {
  nummer: number
  name: string
}

export interface AbnahmeStand {
  okay: number
  nichtOkay: number
  offen: number
  nichtOkaySzenarien: NichtOkaySzenario[]
}

const ZAUN = /^(?:```|~~~)/
const ABSCHNITT_ENDE = /^#{1,2}[ \t]/
// Die Zeile ist schon getrimmt: Der Name endet ohne Leerraum, ein Anker am Ende waere wirkungslos.
const SZENARIO_KOPF = /^###[ \t]+(.+)/

/** Die Zeilen des Szenarien-Abschnitts ausserhalb von Codebloecken, getrimmt und ohne Leerzeilen. */
function abschnittsZeilen(body: string): string[] {
  // Stryker disable next-line ArrayDeclaration: gleichwertig — eine Zeile vor dem ersten Szenario-Kopf verwirft szenarienAus ohnehin
  const zeilen: string[] = []
  let imAbschnitt = false
  let imCode = false
  for (const roh of body.split(/\r?\n/)) {
    const zeile = roh.trim()
    if (ZAUN.test(zeile)) {
      imCode = !imCode
    } else if (imCode || zeile === '') {
      // Codeblock und Leerzeile tragen keinen Schritt.
    } else if (!imAbschnitt) {
      imAbschnitt = passe(SZENARIEN_ABSCHNITT, zeile) !== null
    } else if (ABSCHNITT_ENDE.test(zeile)) {
      break
    } else {
      zeilen.push(zeile)
    }
  }
  return zeilen
}

/** Nimmt eine Zeile unter einem Szenario auf: als Schritt oder als Fortsetzung des vorigen. */
function nimmAuf(schritte: string[], zeile: string): void {
  const treffer = passe(SZENARIO_SCHRITT, zeile)
  if (treffer) {
    schritte.push(treffer[3] ? `${treffer[2]} ${treffer[3]}` : treffer[2])
  } else if (schritte.length > 0) {
    schritte[schritte.length - 1] += ` ${zeile}`
  }
}

/**
 * Liest die Szenarien aus dem Abschnitt `## Szenarien` bzw. `## Scenarios`; er endet an der
 * naechsten `##`-Ueberschrift. Codebloecke werden uebergangen, Szenarien ohne Schritte fallen weg.
 */
export function szenarienAus(body: string): Szenario[] {
  const szenarien: Szenario[] = []
  for (const zeile of abschnittsZeilen(body)) {
    const kopf = SZENARIO_KOPF.exec(zeile)
    if (kopf) {
      szenarien.push({ name: kopf[1], schritte: [] })
    } else if (szenarien.length > 0) {
      nimmAuf(szenarien[szenarien.length - 1].schritte, zeile)
    }
  }
  return szenarien.filter((szenario) => szenario.schritte.length > 0)
}

/** Zaehlt die Vermerke; ein Szenario ohne Vermerk ist offen (Index = Position, E12). */
export function abnahmeStand(szenarien: Szenario[], vermerke: (Vermerk | undefined)[]): AbnahmeStand {
  const stand: AbnahmeStand = { okay: 0, nichtOkay: 0, offen: 0, nichtOkaySzenarien: [] }
  szenarien.forEach((szenario, index) => {
    const urteil = vermerke[index]?.urteil
    if (urteil === 'okay') {
      stand.okay++
    } else if (urteil === 'nicht okay') {
      stand.nichtOkay++
      stand.nichtOkaySzenarien.push({ nummer: index + 1, name: szenario.name })
    } else {
      stand.offen++
    }
  })
  return stand
}

/** Maskiert die Markdown-Zeichen, damit die Anzeige dem Text entspricht (E10). */
const maskiert = (text: string) => text.replace(/[\\*_`[\]|]/g, '\\$&')

/** Macht eine Notiz einzeilig (E9); ohne Regex, weil `\s*\n\s*` superlinear zuruecksetzt (Issue #1634). */
const einzeilig = (text: string) =>
  text
    .split('\n')
    .map((zeile) => zeile.trim())
    .filter((zeile) => zeile !== '')
    .join(' ')

function ergebnisZeile(anzahl: number, stand: AbnahmeStand, t: Woerterbuch): string {
  if (stand.okay === anzahl) return t.ABNAHME.ERGEBNIS_ALLE(anzahl)
  const teile = [t.ABNAHME.OKAY_VON(stand.okay, anzahl)]
  if (stand.nichtOkay > 0) {
    const liste = stand.nichtOkaySzenarien.map(({ nummer, name }) => `${nummer}. ${maskiert(name)}`).join(', ')
    teile.push(t.ABNAHME.NICHT_OKAY_LISTE(liste))
  }
  if (stand.offen > 0) teile.push(t.ABNAHME.OFFEN_ANZAHL(stand.offen))
  return t.ABNAHME.ERGEBNIS(teile.join(', '))
}

/** Das angezeigte Urteil; der Vermerk selbst traegt den sprachneutralen Wert. */
export function urteilText(urteil: Vermerk['urteil'], t: Woerterbuch): string {
  return urteil === 'okay' ? t.ABNAHME.OKAY : t.ABNAHME.NICHT_OKAY
}

function listenZeile(nummer: number, szenario: Szenario, vermerk: Vermerk | undefined, t: Woerterbuch): string {
  const kopf = `${nummer}. **${maskiert(szenario.name)}** — `
  if (!vermerk) return `${kopf}${t.ABNAHME.OFFEN}`
  const notiz = vermerk.urteil === 'nicht okay' ? einzeilig(vermerk.notiz) : ''
  const urteil = urteilText(vermerk.urteil, t)
  return notiz ? `${kopf}${urteil}: ${maskiert(notiz)}` : `${kopf}${urteil}`
}

/** Baut den Ergebnis-Kommentar fuer den Fachplan (E10) in der Sprache der Oberflaeche, Vorgabe Deutsch. */
export function abnahmeKommentar(
  szenarien: Szenario[],
  vermerke: (Vermerk | undefined)[],
  sprache: Sprache = 'de',
): string {
  const t = woerterbuch(sprache)
  const liste = szenarien.map((szenario, index) => listenZeile(index + 1, szenario, vermerke[index], t))
  return [t.ABNAHME.KOMMENTAR_KOPF, '', ergebnisZeile(szenarien.length, abnahmeStand(szenarien, vermerke), t), '', ...liste].join(
    '\n',
  )
}
