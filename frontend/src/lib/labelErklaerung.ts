import { sacheVon, type KitNamen } from '../api/kitNamen'
import type { Woerterbuch } from '../i18n/de'

/** Ein Teil der Erklärung eines Systemlabels (Plan #1668, E1, E3). */
export interface ErklaerungsTeil {
  art: 'bedeutung' | 'setzt' | 'verschwindet' | 'schutz' | 'handlung'
  text: string
}

/** Ein Eintrag unter `LABEL_ERKLAERUNG.SACHEN`; `handlung` nur, wo der Mensch etwas tun soll. */
interface Eintrag {
  readonly bedeutung: string
  readonly setzt: string
  readonly verschwindet: string
  readonly handlung?: string
}

/**
 * Die Erklärung eines Systemlabels (Issue #1671): Bedeutung, wer setzt, wann es verschwindet,
 * Schutzsatz und Handlung — in dieser Reihenfolge, Schutzsatz und Handlung nur, wo es sie gibt.
 *
 * Schlüssel der Texte ist die Sache, nicht der Name (E2): `kit:klaeren` erklärt sich wie
 * `kit:clarify`. Der Schutzsatz folgt aus der Schutzregel des Servers, nicht aus dem Labeltext
 * (E3). Für ein Label, das kein Systemlabel ist, und für eine Sache ohne Text `null`.
 */
export function labelErklaerung(daten: KitNamen, name: string, t: Woerterbuch): ErklaerungsTeil[] | null {
  const sache = sacheVon(daten, name)
  const kitSache = daten.sachen.find((s) => s.sache === sache)
  const sachen: Readonly<Record<string, Eintrag>> = t.LABEL_ERKLAERUNG.SACHEN
  if (kitSache === undefined || !Object.hasOwn(sachen, kitSache.sache)) return null
  const eintrag = sachen[kitSache.sache]
  const teile: ErklaerungsTeil[] = [
    { art: 'bedeutung', text: eintrag.bedeutung },
    { art: 'setzt', text: eintrag.setzt },
    { art: 'verschwindet', text: eintrag.verschwindet },
  ]
  if (kitSache.schutz !== 'KEINER') {
    teile.push({ art: 'schutz', text: t.LABEL_ERKLAERUNG.SCHUTZ[kitSache.schutz] })
  }
  if (eintrag.handlung !== undefined) teile.push({ art: 'handlung', text: eintrag.handlung })
  return teile
}
