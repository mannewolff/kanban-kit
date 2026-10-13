import { describe, expect, it, test } from 'vitest'
import { de } from '../i18n/de'
import { en } from '../i18n/en'
import { KIT_NAMEN } from '../test/kitNamen'
import { labelErklaerung } from './labelErklaerung'

/** Die Arten der Teile in der Reihenfolge, in der die Erklärung sie liefert. */
const arten = (name: string) => labelErklaerung(KIT_NAMEN, name, de)?.map((teil) => teil.art)

test('Szenario: Altes deutsches Label', () => {
  // Angenommen eine alte Karte trägt das Label `kit:klaeren`; dann erscheint die Erklärung von `kit:clarify`.
  const alt = labelErklaerung(KIT_NAMEN, 'kit:klaeren', de)
  expect(alt).not.toBeNull()
  expect(alt).toEqual(labelErklaerung(KIT_NAMEN, 'kit:clarify', de))
})

describe('labelErklaerung — Teile je Label', () => {
  it('liefert für kit:clarify Bedeutung, Setzer, Verschwinden, Schutzsatz und Handlung in dieser Reihenfolge', () => {
    const teile = labelErklaerung(KIT_NAMEN, 'kit:clarify', de)
    const klaeren = de.LABEL_ERKLAERUNG.SACHEN.KLAEREN
    expect(teile).toEqual([
      { art: 'bedeutung', text: klaeren.bedeutung },
      { art: 'setzt', text: klaeren.setzt },
      { art: 'verschwindet', text: klaeren.verschwindet },
      { art: 'schutz', text: de.LABEL_ERKLAERUNG.SCHUTZ.NUR_MENSCH_NIMMT_AB },
      { art: 'handlung', text: klaeren.handlung },
    ])
  })

  it('liefert für kit:night den Schutzsatz zum Setzen', () => {
    const schutz = labelErklaerung(KIT_NAMEN, 'kit:night', de)?.find((teil) => teil.art === 'schutz')
    expect(schutz?.text).toBe(de.LABEL_ERKLAERUNG.SCHUTZ.NUR_MENSCH_SETZT)
  })

  it('liefert für run:running weder Schutzsatz noch Handlung', () => {
    expect(arten('run:running')).toEqual(['bedeutung', 'setzt', 'verschwindet'])
  })

  it('liefert für ein selbst angelegtes Label null', () => {
    expect(labelErklaerung(KIT_NAMEN, 'Aktuell', de)).toBeNull()
  })

  it('liefert für eine Sache ohne Wörterbucheintrag null', () => {
    const daten = { ...KIT_NAMEN, sachen: [{ sache: 'UNBEKANNT', namen: ['kit:neu'], schutz: 'KEINER' as const }] }
    expect(labelErklaerung(daten, 'kit:neu', de)).toBeNull()
  })

  it('nimmt keinen geerbten Objektschlüssel als Sache', () => {
    const daten = { ...KIT_NAMEN, sachen: [{ sache: 'constructor', namen: ['kit:x'], schutz: 'KEINER' as const }] }
    expect(labelErklaerung(daten, 'kit:x', de)).toBeNull()
  })

  it('liefert die Texte in der übergebenen Sprache', () => {
    expect(labelErklaerung(KIT_NAMEN, 'kit:clarify', en)?.[0]).toEqual({
      art: 'bedeutung',
      text: en.LABEL_ERKLAERUNG.SACHEN.KLAEREN.bedeutung,
    })
  })
})

describe('LABEL_ERKLAERUNG — Vollständigkeit', () => {
  for (const [sprache, t] of [
    ['de', de],
    ['en', en],
  ] as const) {
    it(`hat in ${sprache} für jede Sache des Registers Bedeutung, Setzer und Verschwinden`, () => {
      const sachen: Readonly<Record<string, { bedeutung: string; setzt: string; verschwindet: string } | undefined>> =
        t.LABEL_ERKLAERUNG.SACHEN
      for (const { sache } of KIT_NAMEN.sachen) {
        const eintrag = sachen[sache]
        expect(eintrag, sache).toBeDefined()
        expect(eintrag?.bedeutung.trim(), sache).not.toBe('')
        expect(eintrag?.setzt.trim(), sache).not.toBe('')
        expect(eintrag?.verschwindet.trim(), sache).not.toBe('')
      }
    })
  }
})
