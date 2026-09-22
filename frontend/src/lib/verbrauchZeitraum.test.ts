import { describe, expect, it } from 'vitest'
import type { VerbrauchAngaben, VerbrauchKennzahlen } from '../api/nightRunUsage'
import {
  KEIN_LAUF_TEXT,
  NICHT_ERFASST_TEXT,
  TEILWEISE_ERFASST_TEXT,
  erfassungsstand,
  kartenText,
  laeufeText,
  nachtKurz,
  sitzungenText,
  vergleichMitVorzeitraum,
  vorzeitraumName,
  zeitraumBeschriftung,
  zyklusBeschriftung,
  zyklusDavor,
  zyklusDesStarts,
  zeitraumFall,
  zeitraumHinweis,
  zwischenspeicherAnteil,
} from './verbrauchZeitraum'

/** Textrechnung der Verbrauchs-Auswertung (Issue #940, Plan #933). */

/** `Intl` setzt vor Einheiten ein geschütztes Leerzeichen; verglichen wird der Wortlaut. */
const lesbar = (text: string | null) => text?.replaceAll(' ', ' ') ?? null

const nichts: VerbrauchAngaben = {
  costUsd: null,
  inputTokens: null,
  outputTokens: null,
  cachedInputTokens: null,
  cachedInputSharePercent: null,
}

const kosten = (costUsd: number | null): VerbrauchAngaben => ({ ...nichts, costUsd })

const kennzahlen = (werte: Partial<VerbrauchKennzahlen>): VerbrauchKennzahlen => ({
  type: 'MONTH',
  firstDay: '2026-08-01',
  lastDay: '2026-08-31',
  from: '2026-08-01T10:00:00Z',
  to: '2026-09-01T10:00:00Z',
  coverage: 'COMPLETE',
  noRuns: false,
  runCount: 3,
  nightRunCount: 3,
  interactiveRunCount: 0,
  durationMs: 1000,
  cardCount: 2,
  usage: { total: kosten(4), cardShare: kosten(3), remainder: kosten(1) },
  // Die Textrechnung urteilt über die Gesamtsumme — sie dient Leitstand und Nachtlauf-Seite
  // gleichermaßen. Der Gattungs-Split steht daneben und bleibt hier ohne Einfluss (Issue #1016).
  usageByKind: {
    night: { total: kosten(4), cardShare: kosten(3), remainder: kosten(1) },
    interactive: { total: nichts, cardShare: nichts, remainder: nichts },
  },
  interactiveUsageSince: null,
  ...werte,
})

describe('zeitraumBeschriftung', () => {
  it('benennt eine Nacht mit ihrem Beginn und ihrem Folgetag', () => {
    expect(
      zeitraumBeschriftung(kennzahlen({ type: 'DAY', firstDay: '2026-09-15', lastDay: '2026-09-15' })),
    ).toBe('Zyklus vom 15.09.2026 auf den 16.09.2026')
  })

  it('benennt die Nacht ueber den Monatswechsel richtig', () => {
    expect(
      zeitraumBeschriftung(kennzahlen({ type: 'DAY', firstDay: '2026-08-31', lastDay: '2026-08-31' })),
    ).toBe('Zyklus vom 31.08.2026 auf den 01.09.2026')
  })

  it('benennt eine Woche mit ihren Naechten von Montag bis Sonntag', () => {
    expect(
      zeitraumBeschriftung(
        kennzahlen({ type: 'WEEK', firstDay: '2026-09-07', lastDay: '2026-09-13' }),
      ),
    ).toBe('Woche vom 07.09.2026 bis 13.09.2026')
  })

  it('benennt einen Monat mit Name und Jahr', () => {
    expect(zeitraumBeschriftung(kennzahlen({ type: 'MONTH', firstDay: '2026-08-01' }))).toBe(
      'August 2026',
    )
  })
})

describe('vorzeitraumName', () => {
  it('nennt je Art den Vorzeitraum beim Namen', () => {
    expect(vorzeitraumName(kennzahlen({ type: 'DAY' }))).toBe('Vorzyklus')
    expect(vorzeitraumName(kennzahlen({ type: 'WEEK' }))).toBe('Vorwoche')
    expect(vorzeitraumName(kennzahlen({ type: 'MONTH' }))).toBe('Vormonat')
  })
})

describe('laeufeText, sitzungenText und kartenText', () => {
  it('setzt den Einzahl- und den Mehrzahlfall', () => {
    expect(laeufeText(1)).toBe('1 Lauf')
    expect(laeufeText(0)).toBe('0 Läufe')
    expect(laeufeText(7)).toBe('7 Läufe')
    expect(sitzungenText(1)).toBe('1 Sitzung')
    expect(sitzungenText(0)).toBe('0 Sitzungen')
    expect(sitzungenText(4)).toBe('4 Sitzungen')
    expect(kartenText(1)).toBe('1 Karte')
    expect(kartenText(0)).toBe('0 Karten')
    expect(kartenText(54)).toBe('54 Karten')
  })
})

describe('nachtKurz', () => {
  it('nennt Wochentag, Beginn und Folgetag ohne Jahr', () => {
    expect(nachtKurz('2026-09-08')).toBe('Di 08.09. → 09.09.')
  })

  it('zaehlt ueber den Monatswechsel richtig weiter', () => {
    expect(nachtKurz('2026-08-31')).toBe('Mo 31.08. → 01.09.')
  })
})

describe('vergleichMitVorzeitraum', () => {
  it('nennt teurer mit dem Unterschied, ohne dass der Leser rechnet', () => {
    const vergleich = vergleichMitVorzeitraum(kosten(6.5), kosten(4))

    expect(vergleich.richtung).toBe('teurer')
    expect(lesbar(vergleich.text)).toBe('2,50 $ teurer als im Vorzeitraum')
  })

  it('nennt billiger mit dem Unterschied', () => {
    const vergleich = vergleichMitVorzeitraum(kosten(1), kosten(4))

    expect(vergleich.richtung).toBe('billiger')
    expect(lesbar(vergleich.text)).toBe('3,00 $ billiger als im Vorzeitraum')
  })

  it('unterscheidet unveraendert davon', () => {
    const vergleich = vergleichMitVorzeitraum(kosten(4), kosten(4))

    expect(vergleich.richtung).toBe('unveraendert')
    expect(vergleich.text).toBe('genauso teuer wie im Vorzeitraum')
  })

  /** 0,1 + 0,2 ist in Gleitkomma nicht 0,3 — auf den Mikrodollar gleich gilt als unveraendert. */
  it('haelt Rundungsreste aus der Gleitkommarechnung fuer unveraendert', () => {
    expect(vergleichMitVorzeitraum(kosten(0.1 + 0.2), kosten(0.3)).richtung).toBe('unveraendert')
  })

  it('nennt nicht vergleichbar, wenn eine der beiden Angaben fehlt — in beiden Richtungen', () => {
    for (const [jetzt, vorher] of [
      [kosten(null), kosten(4)],
      [kosten(4), kosten(null)],
      [kosten(null), kosten(null)],
    ]) {
      const vergleich = vergleichMitVorzeitraum(jetzt, vorher)
      expect(vergleich.richtung).toBe('nicht-vergleichbar')
      expect(vergleich.text).toBe('nicht vergleichbar — eine der beiden Kostenangaben fehlt')
    }
  })

  it('vergleicht eine gemessene Null als Wert', () => {
    expect(vergleichMitVorzeitraum(kosten(0), kosten(2)).richtung).toBe('billiger')
  })
})

describe('zwischenspeicherAnteil', () => {
  it('schreibt den Prozentwert mit einer Nachkommastelle', () => {
    expect(
      lesbar(zwischenspeicherAnteil({ ...nichts, inputTokens: 200, cachedInputSharePercent: 25 })),
    ).toBe('25,0 %')
  })

  it('ist ohne Eingabemenge nicht bestimmt', () => {
    expect(
      zwischenspeicherAnteil({ ...nichts, cachedInputTokens: 50, cachedInputSharePercent: null }),
    ).toBe('nicht bestimmt')
  })

  it('nennt einen gemessenen Anteil von null Prozent als Wert', () => {
    expect(
      lesbar(zwischenspeicherAnteil({ ...nichts, inputTokens: 200, cachedInputSharePercent: 0 })),
    ).toBe('0,0 %')
  })
})

describe('zeitraumFall und zeitraumHinweis', () => {
  const leer = { noRuns: true, runCount: 0, nightRunCount: 0, interactiveRunCount: 0 } as const
  const faelle = {
    keinLauf: kennzahlen({ ...leer, usage: { total: nichts, cardShare: nichts, remainder: nichts } }),
    vorAufbewahrung: kennzahlen({
      coverage: 'BEFORE_RETENTION',
      ...leer,
      usage: { total: nichts, cardShare: nichts, remainder: nichts },
    }),
    teilweise: kennzahlen({ coverage: 'PARTIAL' }),
    nichtGemessen: kennzahlen({ usage: { total: nichts, cardShare: nichts, remainder: nichts } }),
  }

  it('unterscheidet die vier Faelle', () => {
    expect(zeitraumFall(faelle.keinLauf)).toBe('kein-lauf')
    expect(zeitraumFall(faelle.vorAufbewahrung)).toBe('vor-aufbewahrung')
    expect(zeitraumFall(faelle.teilweise)).toBe('teilweise')
    expect(zeitraumFall(faelle.nichtGemessen)).toBe('nicht-gemessen')
  })

  it('liefert vier verschiedene Texte', () => {
    const texte = Object.values(faelle).map((f) => zeitraumHinweis(f))

    expect(new Set(texte).size).toBe(4)
    expect(texte).not.toContain(null)
  })

  it('sagt beim Zeitraum ohne Lauf und ohne Sitzung den Satz aus #926 AK 9', () => {
    expect(zeitraumHinweis(faelle.keinLauf)).toBe(KEIN_LAUF_TEXT)
    expect(KEIN_LAUF_TEXT).toBe(
      'In diesem Zeitraum hat weder ein Lauf noch eine Sitzung stattgefunden.',
    )
  })

  /**
   * Ein Tag ohne Nachtlauf, aber mit Sitzungen, ist nicht leer (#984 AK 1) — die Zahlen der
   * Sitzungen stehen dort, wo bisher „kein Lauf" stand.
   */
  it('haelt einen Zeitraum ohne Nachtlauf, aber mit Sitzungen, fuer voll besetzt', () => {
    const nurSitzungen = kennzahlen({
      noRuns: false,
      runCount: 2,
      nightRunCount: 0,
      interactiveRunCount: 2,
      usageByKind: {
        night: { total: nichts, cardShare: nichts, remainder: nichts },
        interactive: { total: kosten(4), cardShare: kosten(3), remainder: kosten(1) },
      },
    })

    expect(zeitraumFall(nurSitzungen)).toBe('vollstaendig')
    expect(zeitraumHinweis(nurSitzungen)).not.toBe(KEIN_LAUF_TEXT)
  })

  /** Der Text des Kartenblatts bleibt dem Kartenblatt (#984 AK 6). */
  it('nennt in keinem Hinweis das Wort aus dem Kartenblatt', () => {
    for (const fall of Object.values(faelle)) {
      expect(zeitraumHinweis(fall)).not.toContain('nicht gemessen')
    }
  })

  it('sagt ganz vor dem aeltesten aufbewahrten Lauf NICHT den Satz aus AK 9', () => {
    expect(zeitraumHinweis(faelle.vorAufbewahrung)).not.toBe(KEIN_LAUF_TEXT)
    expect(zeitraumHinweis(faelle.vorAufbewahrung)).toContain('ältesten aufbewahrten Lauf')
  })

  it('nennt eine Teilabdeckung neben den Zahlen, auch ohne Lauf in der aufbewahrten Zeit', () => {
    expect(zeitraumHinweis(faelle.teilweise)).toContain('nur teilweise')
    expect(
      zeitraumFall(kennzahlen({ coverage: 'PARTIAL', noRuns: true, runCount: 0 })),
    ).toBe('teilweise')
  })

  it('hat ohne Besonderheit keinen Hinweis', () => {
    expect(zeitraumFall(kennzahlen({}))).toBe('vollstaendig')
    expect(zeitraumHinweis(kennzahlen({}))).toBeNull()
  })
})

/**
 * Der Erfassungsbeginn der interaktiven Sitzungen (#984 AK 6). Er entscheidet, ob eine fehlende
 * Zahl „noch nicht erfasst" heißt oder eine gemessene Null ist — 0 ist nie die Antwort auf
 * „nicht erfasst".
 */
describe('erfassungsstand', () => {
  /** August 2026; der Erfassungsbeginn liegt mitten darin. */
  const august = (interactiveUsageSince: string | null) =>
    kennzahlen({
      from: '2026-08-01T10:00:00Z',
      to: '2026-09-01T10:00:00Z',
      interactiveUsageSince,
    })

  it('nennt einen Zeitraum ganz vor dem Beginn nicht erfasst', () => {
    expect(erfassungsstand(august('2026-09-05T08:00:00Z'))).toBe('nicht-erfasst')
  })

  /** `to` ist ausschliesslich: Beginnt die Erfassung genau am Ende, liegt der Zeitraum davor. */
  it('zaehlt einen Beginn genau am Ende des Zeitraums noch als davor', () => {
    expect(erfassungsstand(august('2026-09-01T10:00:00Z'))).toBe('nicht-erfasst')
  })

  it('nennt einen Zeitraum, der den Beginn schneidet, teilweise erfasst', () => {
    expect(erfassungsstand(august('2026-08-14T07:00:00Z'))).toBe('teilweise-erfasst')
  })

  it('nennt einen Zeitraum ganz nach dem Beginn erfasst — auch wenn er genau dort anfaengt', () => {
    expect(erfassungsstand(august('2026-07-01T00:00:00Z'))).toBe('erfasst')
    expect(erfassungsstand(august('2026-08-01T10:00:00Z'))).toBe('erfasst')
  })

  /** Ohne je gemeldete Sitzung gibt es keinen Beginn — und damit keine gemessene Null. */
  it('nennt ein Projekt ohne gemeldete Sitzung nicht erfasst', () => {
    expect(erfassungsstand(august(null))).toBe('nicht-erfasst')
  })

  it('schreibt die beiden Aussagen aus, ohne 0 zu sagen', () => {
    expect(NICHT_ERFASST_TEXT).toBe('nicht erfasst')
    expect(TEILWEISE_ERFASST_TEXT).toBe('teilweise erfasst')
  })
})

describe('zeitraumHinweis mit Erfassungsluecke', () => {
  const august = (extra: Partial<VerbrauchKennzahlen>) =>
    kennzahlen({ from: '2026-08-01T10:00:00Z', to: '2026-09-01T10:00:00Z', ...extra })

  it('erklaert einen Zeitraum ganz vor dem Erfassungsbeginn mit dessen Datum', () => {
    const hinweis = zeitraumHinweis(august({ interactiveUsageSince: '2026-09-05T08:00:00Z' }))

    expect(hinweis).toContain('05.09.2026')
    expect(hinweis).toContain('ganz davor')
  })

  it('erklaert einen angeschnittenen Erfassungsbeginn eigens', () => {
    const hinweis = zeitraumHinweis(august({ interactiveUsageSince: '2026-08-14T07:00:00Z' }))

    expect(hinweis).toContain('14.08.2026')
    expect(hinweis).toContain('beginnt davor')
  })

  /** Der Erfassungs-Hinweis erklaert die groessere Luecke und steht deshalb vorn. */
  it('stellt den Erfassungs-Hinweis vor den Abdeckungs-Hinweis', () => {
    const hinweis = zeitraumHinweis(
      august({ coverage: 'PARTIAL', interactiveUsageSince: '2026-08-14T07:00:00Z' }),
    )

    expect(hinweis).not.toBeNull()
    expect(hinweis!.indexOf('Interaktive Sitzungen')).toBeLessThan(
      hinweis!.indexOf('nur teilweise abgedeckt'),
    )
  })

  /**
   * Ohne je gemeldete Sitzung gibt es keinen Zeitraum-Befund: Das ist ein Zustand des Projekts,
   * und die Nachtlauf-Seite (Issue #1016) bekaeme sonst einen Hinweis zu etwas, das sie nicht zeigt.
   */
  it('schweigt, solange das Projekt keine Sitzung gemeldet hat', () => {
    expect(zeitraumHinweis(august({ interactiveUsageSince: null }))).toBeNull()
  })

  it('schweigt, wenn der Zeitraum ganz nach dem Erfassungsbeginn liegt', () => {
    expect(zeitraumHinweis(august({ interactiveUsageSince: '2026-07-01T00:00:00Z' }))).toBeNull()
  })
})

describe('zyklusDesStarts (Issue #1127)', () => {
  const BERLIN = 'Europe/Berlin'

  it('ordnet einen Start ab 12:00 dem Zyklus dieses Tages zu', () => {
    expect(zyklusDesStarts('2026-09-14T20:05:00Z', BERLIN)).toBe('2026-09-14') // 22:05
    expect(zyklusDesStarts('2026-09-15T10:00:00Z', BERLIN)).toBe('2026-09-15') // 12:00
  })

  it('ordnet einen Start vor 12:00 dem Zyklus des Vortags zu', () => {
    expect(zyklusDesStarts('2026-09-15T09:59:00Z', BERLIN)).toBe('2026-09-14') // 11:59
    expect(zyklusDesStarts('2026-09-14T22:30:00Z', BERLIN)).toBe('2026-09-14') // 00:30, kurz nach Mitternacht
    expect(zyklusDesStarts('2026-09-15T01:10:00Z', BERLIN)).toBe('2026-09-14') // 03:10
  })

  it('geht über den Monatswechsel zurück', () => {
    expect(zyklusDesStarts('2026-10-01T01:00:00Z', BERLIN)).toBe('2026-09-30')
  })

  it('rechnet in der Zone des Lesers, wenn keine genannt ist', () => {
    // Die Tests laufen in Europe/Berlin (vite.config.ts, test.env.TZ).
    expect(zyklusDesStarts('2026-09-15T09:59:00Z')).toBe('2026-09-14')
  })
})

describe('zyklusBeschriftung (Issue #1127)', () => {
  it('nennt Beginn und Folgetag', () => {
    expect(zyklusBeschriftung('2026-09-14')).toBe('Zyklus vom 14.09.2026 auf den 15.09.2026')
  })
})

describe('zyklusDavor (Issue #1134)', () => {
  it('nennt den Zyklus einen Tag früher, auch über den Monatswechsel', () => {
    expect(zyklusDavor('2026-09-22')).toBe('2026-09-21')
    expect(zyklusDavor('2026-10-01')).toBe('2026-09-30')
  })
})
