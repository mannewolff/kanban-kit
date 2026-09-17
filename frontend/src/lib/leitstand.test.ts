import { describe, expect, it } from 'vitest'
import type { NightRunItemView, NightRunView } from '../api/nightRuns'
import {
  abbruchgruende,
  balkenHoehen,
  cacheQuote,
  durchlaufKachel,
  durchsatzKachel,
  ersteZeile,
  funkenPunkte,
  gruenAnteil,
  implementierungKachel,
  istAbbruch,
  juengsterLauf,
  kalenderwoche,
  kostenText,
  kurzHash,
  laufband,
  laufDauer,
  laufDauerGeteilt,
  laufMelder,
  laufNotiz,
  modusName,
  paketDauer,
  paketZaehlung,
  tagZeit,
  tokenMenge,
  tokenText,
  vorgaenge,
} from './leitstand'

const paket = (nummer: number, state: NightRunItemView['state'], extra: Partial<NightRunItemView> = {}): NightRunItemView => ({
  id: nummer,
  cardNumber: nummer,
  title: `Paket ${nummer}`,
  state,
  errorClass: null,
  durationMs: null,
  commitHash: null,
  excerpt: null,
  usage: null,
  ...extra,
})

const lauf = (extra: Partial<NightRunView> = {}): NightRunView => ({
  id: 1,
  startedAt: '2026-09-14T21:10:00Z',
  mode: 'CHAIN',
  durationMs: 15_120_000,
  processedCount: 0,
  skippedCount: 0,
  unparsedCount: 0,
  unparsedSample: null,
  createdAt: '2026-09-15T01:22:00Z',
  origin: 'TOKEN',
  tokenName: 'kette',
  complete: true,
  updatedAt: null,
  usage: null,
  items: [],
  ...extra,
})

const woche = (tag: number, doneCount: number) => ({
  weekStart: new Date(Date.UTC(2026, 5, 1 + 7 * tag, 9)).toISOString(),
  doneCount,
})

describe('leitstand Laufband (#979)', () => {
  it('nennt einen laufenden Lauf mit Vorgang n von m, gezählt ohne graue Pakete', () => {
    const band = laufband(lauf({ complete: false, items: [paket(940, 'GREEN'), paket(941, 'GREEN'), paket(942, 'GREY')] }))
    expect(band.titel).toBe('Kette läuft — Vorgang 2 von 3')
    expect(band.melder).toBe('stahl')
    expect(band.laeuft).toBe(true)
    expect(band.vorgang).toEqual({ nummer: 942, titel: 'Paket 942' })
    expect(band.zeitpunkt).toMatch(/^seit \d\d:\d\d$/)
  })

  it('nennt einen laufenden Lauf ohne gemeldeten Vorgang schlicht als laufend', () => {
    const band = laufband(lauf({ complete: false, mode: 'IMPLEMENTATION' }))
    expect(band.titel).toBe('Umsetzung läuft')
    expect(band.vorgang).toBeNull()
  })

  it('nennt einen abgeschlossenen Lauf mit Zahl der Vorgänge, Dauer in Minuten und Kosten', () => {
    const band = laufband(lauf({ mode: 'REVIEW', items: [paket(1, 'GREEN')], usage: { costUsd: 12.4, inputTokens: null, outputTokens: null, cachedInputTokens: null } }))
    expect(band.titel).toBe('Review abgeschlossen — 1 Vorgang')
    expect(band.zeitpunkt).toMatch(/^Beginn \d\d\.\d\d\., \d\d:\d\d$/)
    expect(band.minuten).toBe(252)
    expect(band.kosten).toBe('12,40')
  })

  it('lässt nicht gemessene Kosten weg, statt 0 zu zeigen', () => {
    expect(laufband(lauf()).kosten).toBeNull()
    expect(kostenText(null)).toBeNull()
    expect(kostenText(0)).toBe('0,00 $')
  })

  it('setzt den Melder eines abgeschlossenen Laufs auf den schlechtesten Zustand', () => {
    expect(laufMelder(lauf({ items: [paket(1, 'GREEN'), paket(2, 'RED'), paket(3, 'YELLOW')] }))).toBe('zinnob')
    expect(laufMelder(lauf({ items: [paket(1, 'GREEN'), paket(3, 'YELLOW')] }))).toBe('bernst')
    expect(laufMelder(lauf({ items: [paket(1, 'GREEN'), paket(2, 'GREY')] }))).toBe('gruen')
  })

  it('liest den jüngsten Lauf nach Startzeit, gleich wo er in der Antwort steht', () => {
    const alt = lauf({ id: 1, startedAt: '2026-09-10T21:00:00Z' })
    const neu = lauf({ id: 2, startedAt: '2026-09-14T21:00:00Z' })
    expect(juengsterLauf([alt, neu])?.id).toBe(2)
    expect(juengsterLauf([neu, alt])?.id).toBe(2)
    expect(juengsterLauf([])).toBeNull()
  })

  it('benennt die Betriebsarten und die Zahl der Vorgänge', () => {
    expect(modusName('CHAIN')).toBe('Kette')
    expect(vorgaenge(1)).toBe('1 Vorgang')
    expect(vorgaenge(9)).toBe('9 Vorgänge')
  })
})

describe('leitstand Letzter Lauf', () => {
  it('formatiert Paketdauern wie der Entwurf', () => {
    expect(paketDauer(null)).toBe('—')
    expect(paketDauer(391_000)).toBe('06:31')
    expect(paketDauer(3_735_000)).toBe('1:02:15')
  })

  it('formatiert Laufdauern und die Notiz im Plattenkopf', () => {
    expect(laufDauer(720_000)).toBe('12 min')
    expect(laufDauer(15_120_000)).toBe('4 h 12 min')
    expect(laufNotiz(lauf({ items: [paket(1, 'GREEN')] }))).toMatch(/^\d\d\.\d\d\., \d\d:\d\d · 4 h 12 min · 1 Paket$/)
    expect(laufNotiz(lauf({ items: [paket(1, 'GREEN'), paket(2, 'RED')] }))).toMatch(/ · 2 Pakete$/)
  })

  it('zählt rote und gelbe Pakete als Abbruch, grüne und graue nicht', () => {
    expect(['GREEN', 'YELLOW', 'RED', 'GREY'].map((s) => istAbbruch(paket(1, s as NightRunItemView['state'])))).toEqual([
      false,
      true,
      true,
      false,
    ])
  })

  it('kürzt den Commit-Hash und liest die erste Zeile eines Auszugs', () => {
    expect(kurzHash('9489421abcdef')).toBe('9489421')
    expect(kurzHash(null)).toBeNull()
    expect(ersteZeile('\n  2 Tests rot  \nmehr')).toBe('2 Tests rot')
    expect(ersteZeile('  \n ')).toBeNull()
    expect(ersteZeile(null)).toBeNull()
  })
})

describe('leitstand Kennzahl-Kacheln', () => {
  it('zeigt den Durchsatz der letzten Woche mit Verlauf und Delta zur Vorwoche in Prozent', () => {
    const kachel = durchsatzKachel([woche(0, 4), woche(1, 10), woche(2, 12)])
    expect(kachel).toMatchObject({ wert: '12', einheit: 'Karten', basis: '3 Wochen', verlauf: [4, 10, 12] })
    expect(kachel.delta).toEqual({ text: '▲ 20 %', art: 'gut' })
  })

  it('nennt einen Rückgang als schlecht, Gleichstand als neutral und eine einzelne Karte im Singular', () => {
    expect(durchsatzKachel([woche(0, 10), woche(1, 8)]).delta).toEqual({ text: '▼ 20 %', art: 'schlecht' })
    expect(durchsatzKachel([woche(0, 3), woche(1, 3)]).delta).toEqual({ text: '± 0', art: 'neutral' })
    expect(durchsatzKachel([woche(0, 2), woche(1, 1)]).einheit).toBe('Karte')
  })

  it('rechnet ohne Vorwochenwert kein Prozent, sondern nennt die Zahl', () => {
    expect(durchsatzKachel([woche(0, 0), woche(1, 5)]).delta).toEqual({ text: '▲ 5 zur Vorwoche', art: 'gut' })
    expect(durchsatzKachel([woche(0, 5)]).delta).toEqual({ text: '▲ 5 zur Vorwoche', art: 'gut' })
  })

  it('zeigt zwölf leere Wochen als Leerwert statt einer Null', () => {
    const kachel = durchsatzKachel(Array.from({ length: 12 }, (_, i) => woche(i, 0)))
    expect(kachel).toEqual({ wert: null, einheit: 'Karten', basis: '12 Wochen', verlauf: null, delta: null })
  })

  it('zeigt Durchlaufzeit in Tagen und Implementierungszeit in Stunden mit ihrer Datenbasis', () => {
    expect(durchlaufKachel(276_480, 86)).toEqual({ wert: '3,2', einheit: 'Tage', basis: '86 Karten', verlauf: null, delta: null })
    expect(durchlaufKachel(null, 0).wert).toBeNull()
    expect(durchlaufKachel(86_400, 1).basis).toBe('1 Karte')
    expect(implementierungKachel(53_280, 61)).toMatchObject({ wert: '14,8', einheit: 'Stunden', basis: '61 Karten' })
    expect(implementierungKachel(3600, 0).wert).toBeNull()
    expect(implementierungKachel(null, 3).wert).toBeNull()
    expect(implementierungKachel(3600, 1).basis).toBe('1 Karte')
  })

  it('zeigt die Implementierungszeit unter einer Stunde in Minuten', () => {
    expect(implementierungKachel(1080, 4)).toMatchObject({ wert: '18', einheit: 'Minuten' })
    expect(implementierungKachel(3540, 4)).toMatchObject({ wert: '59', einheit: 'Minuten' })
    // Ab 60 gerundeten Minuten kippt die Einheit — „60 Minuten" stünde sonst neben „1,0 Stunden".
    expect(implementierungKachel(3570, 4)).toMatchObject({ wert: '1,0', einheit: 'Stunden' })
    expect(implementierungKachel(7200, 4)).toMatchObject({ wert: '2,0', einheit: 'Stunden' })
  })

  it('rechnet den grünen Anteil über alle aufbewahrten Läufe ohne graue Pakete', () => {
    const anteil = gruenAnteil([
      lauf({ items: [paket(1, 'GREEN'), paket(2, 'GREEN'), paket(3, 'GREY')] }),
      lauf({ items: [paket(4, 'YELLOW'), paket(5, 'RED'), paket(6, 'GREEN')] }),
    ])
    expect(anteil).toEqual({ prozent: 60, gruen: 3, gelb: 1, rot: 1, gesamt: 5 })
    expect(gruenAnteil([lauf({ items: [paket(1, 'GREY')] })]).prozent).toBeNull()
  })

  it('legt die Sparkline in den Rahmen des Entwurfs', () => {
    expect(funkenPunkte([0, 10, 5])).toEqual([
      { x: 2, y: 30 },
      { x: 62, y: 4 },
      { x: 122, y: 17 },
    ])
    expect(funkenPunkte([3, 3])).toEqual([
      { x: 2, y: 17 },
      { x: 122, y: 17 },
    ])
    expect(funkenPunkte([7])).toEqual([{ x: 2, y: 17 }])
  })
})

describe('leitstand Platten', () => {
  it('nennt die ISO-Kalenderwoche eines Wochenbeginns, auch über den Jahreswechsel', () => {
    expect(kalenderwoche('2026-06-01T09:00:00Z')).toBe(23)
    expect(kalenderwoche('2026-01-01T00:00:00Z')).toBe(1)
    expect(kalenderwoche('2027-01-03T12:00:00Z')).toBe(53)
  })

  it('setzt Balkenhöhen relativ zum höchsten Wert', () => {
    expect(balkenHoehen([6, 12, 3])).toEqual([50, 100, 25])
    expect(balkenHoehen([0, 0])).toEqual([0, 0])
  })

  it('sortiert Abbruchgründe nach Häufigkeit und färbt sie nach Art', () => {
    expect(abbruchgruende({ REVIEWER_FAILED: 2, CHECKS_RED: 11, AWAITING_DECISION: 7, HARD_ABORT: 0, DEPENDENCY_UNMET: 7 })).toEqual([
      { klasse: 'CHECKS_RED', zahl: 11, breite: 100, melder: 'zinnob' },
      { klasse: 'AWAITING_DECISION', zahl: 7, breite: 64, melder: 'bernst' },
      { klasse: 'DEPENDENCY_UNMET', zahl: 7, breite: 64, melder: 'bernst' },
      { klasse: 'REVIEWER_FAILED', zahl: 2, breite: 18, melder: 'grau' },
    ])
    expect(abbruchgruende({})).toEqual([])
  })

  it('nennt Token-Mengen in der Einheit des Entwurfs', () => {
    expect(tokenMenge(4_820_000)).toEqual({ wert: '4,82', einheit: 'Mio' })
    expect(tokenMenge(186_400)).toEqual({ wert: '186', einheit: 'Tsd' })
    expect(tokenMenge(420)).toEqual({ wert: '420', einheit: 'Token' })
    expect(tokenMenge(null)).toBeNull()
    expect(tokenText(3_660_000)).toBe('3,66 Mio')
  })
})

describe('leitstand Instrumente eines Laufs (#988)', () => {
  it('nennt den Beginn eines Laufs als Tag und Uhrzeit — dieselbe Form wie die Notiz', () => {
    // Dieselbe Rechenstelle wie `laufNotiz` und `laufband`: Zwei Formatierer für denselben
    // Zeitpunkt liefen beim nächsten Wortwechsel auseinander.
    expect(tagZeit('2026-09-14T21:10:00Z')).toBe(laufNotiz(lauf()).split(' · ')[0])
  })

  it('teilt die Laufdauer in Wert und Einheit — ab einer Stunde als h:mm', () => {
    expect(laufDauerGeteilt(15_120_000)).toEqual({ wert: '4:12', einheit: 'h' })
    expect(laufDauerGeteilt(3_600_000)).toEqual({ wert: '1:00', einheit: 'h' })
    expect(laufDauerGeteilt(2_535_000)).toEqual({ wert: '42', einheit: 'min' })
    expect(laufDauerGeteilt(0)).toEqual({ wert: '0', einheit: 'min' })
  })

  it('zählt die Pakete eines Laufs nach Zustand; graue zählen nicht mit', () => {
    const gezaehlt = paketZaehlung([
      { state: 'GREEN' },
      { state: 'GREEN' },
      { state: 'YELLOW' },
      { state: 'RED' },
      { state: 'GREY' },
    ])
    expect(gezaehlt).toEqual({ gruen: 2, gelb: 1, rot: 1, gesamt: 4 })
    expect(paketZaehlung([])).toEqual({ gruen: 0, gelb: 0, rot: 0, gesamt: 0 })
  })

  it('rechnet den Anteil aus Zwischenspeicher, bleibt aber ohne Bezugsgröße bei null', () => {
    expect(cacheQuote(760_000, 1_000_000)).toBe(76)
    expect(cacheQuote(0, 1_000_000)).toBe(0)
    // Ohne Eingabe gibt es kein Verhältnis: „0 %" behauptete eine Messung, „100 %" eine zweite.
    expect(cacheQuote(760_000, 0)).toBeNull()
    expect(cacheQuote(760_000, null)).toBeNull()
    expect(cacheQuote(null, 1_000_000)).toBeNull()
  })

  it('rechnet den Paket-Anteil des Leitstands über dieselbe Zählung', () => {
    // Gegenprobe zur Wiederverwendung: `gruenAnteil` zählt nicht mehr selbst.
    const laeufe = [lauf({ items: [paket(1, 'GREEN'), paket(2, 'RED'), paket(3, 'GREY')] })]
    const { gruen, gelb, rot, gesamt } = gruenAnteil(laeufe)
    expect({ gruen, gelb, rot, gesamt }).toEqual(
      paketZaehlung(laeufe.flatMap((l) => l.items)),
    )
  })
})
