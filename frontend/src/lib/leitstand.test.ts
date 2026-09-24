import { describe, expect, it } from 'vitest'
import type { NightRunItemView, NightRunServerMode, NightRunView, Verdict } from '../api/nightRuns'
import type { NightRunErrorClass, NightRunState } from './nightRunLog'
import { GRUND_UNBEKANNT, serverBefund } from '../test/befund'
import { NIGHT_RUN_VERDICT_TEXT } from './nightRunHandoff'
import {
  abbruchgruende,
  auskunftOhneArbeit,
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
  laeuftNoch,
  laufband,
  laufDauer,
  laufDauerGeteilt,
  laufMelder,
  laufNotiz,
  melderAusBefund,
  MELDER_JE_ZUSTAND,
  modusName,
  nachProjekt,
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
  stages: [],
  ...extra,
})

const lauf = (extra: Partial<NightRunView> = {}): NightRunView => {
  const basis: NightRunView = {
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
  noWorkReason: null,
  abortReason: null,
  budget: null,
  items: [],
    outcome: { verdict: 'SUCCEEDED', decisiveItem: null, noWorkReason: null, abortReason: null },
    ...extra,
  }
  // Der Befund kommt aus dem Szenario, nicht aus der Vorgabe: Ein Lauf mit rotem Paket traegt sonst
  // einen Befund, der etwas anderes sagt als seine eigenen Pakete (Issue #1081).
  return { ...basis, outcome: extra.outcome ?? serverBefund(basis) }
}

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
    const band = laufband(lauf({ mode: 'REVIEW', items: [paket(1, 'GREEN')], usage: { costUsd: 12.4, inputTokens: null, outputTokens: null, cachedInputTokens: null, modelDurationMs: null, turns: null } }))
    expect(band.titel).toBe('Prüfung abgeschlossen — 1 Vorgang')
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
    // Dasselbe Wort wie auf der Läufe-Seite (Issue #1128).
    expect(modusName('REVIEW')).toBe('Prüfung')
    expect(vorgaenge(1)).toBe('1 Vorgang')
    expect(vorgaenge(9)).toBe('9 Vorgänge')
  })

  /**
   * Die Tabelle ist ein `Record` über `NightRunServerMode` — ein neuer Modus ohne Eintrag bricht
   * `tsc`. Dieser Test hält fest, dass jeder Modus auch tatsächlich ein Wort bekommt, statt einen
   * leeren String (Issue #1016).
   */
  it('benennt jede Betriebsart, die der Server kennt — auch die interaktive Sitzung', () => {
    const alle: Record<NightRunServerMode, true> = {
      IMPLEMENTATION: true,
      REVIEW: true,
      CHAIN: true,
      INTERACTIVE: true,
    }
    for (const modus of Object.keys(alle) as NightRunServerMode[]) {
      expect(modusName(modus)).not.toBe('')
    }
    expect(modusName('INTERACTIVE')).toBe('Sitzung')
  })
})

describe('leitstand Letzter Run', () => {
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

describe('leitstand Lauf ohne Arbeit (#1069)', () => {
  const GRUND = 'Kein Eintrag trug das Label kit:nightrun'
  /** Ein Lauf, der alle Pakete zurueckstellte — er meldet keinen Grund und ist „mit Vorbehalt". */
  const zurueckgestellt = (noWorkReason: string | null = GRUND_UNBEKANNT) =>
    lauf({ noWorkReason, items: [paket(1, 'GREY', { errorClass: 'DEPENDENCY_UNMET' })] })

  /**
   * Die Auskunft haengt allein am Ausgang (Plan #1181 E10): `NO_WORK` heisst „nichts zu tun", und
   * der Wortlaut des Grundes entscheidet nichts mehr. Einen Zweig ohne Befund gibt es nicht — der
   * einzige Lauf ohne Befund ist der im Browser geparste, und der traegt nie einen Grund.
   */
  it('auskunftOhneArbeit nennt den Grund beim Lauf ohne Arbeit, den gemeldeten wie den Rueckfall', () => {
    expect(auskunftOhneArbeit(lauf({ noWorkReason: GRUND }))).toBe(GRUND)
    expect(auskunftOhneArbeit(lauf({ noWorkReason: GRUND_UNBEKANNT }))).toBe(GRUND_UNBEKANNT)
  })

  it('auskunftOhneArbeit schweigt bei jedem anderen Ausgang', () => {
    // Zurueckgestellt trotz Rueckfalltext am Datensatz: der Fall, den E11 festhaelt.
    expect(auskunftOhneArbeit(zurueckgestellt())).toBeNull()
    expect(auskunftOhneArbeit(lauf({ items: [paket(1, 'RED', { errorClass: 'CHECKS_RED' })] }))).toBeNull()
    expect(auskunftOhneArbeit(lauf())).toBeNull()
    expect(auskunftOhneArbeit(lauf({ complete: false, items: [] }))).toBeNull()
    // Der eben im Browser geparste Lauf der Nachtlauf-Seite: kein Befund, also keine Auskunft.
    expect(auskunftOhneArbeit({})).toBeNull()
  })

  // Seit #1121 grau statt zinnober: Der Lauf fand nichts zu tun — und das ist kein Mangel. Seit
  // #1185 gilt das fuer jeden Grund, den Rueckfall des Servers eingeschlossen.
  it('laufMelder meldet den Lauf ohne Arbeit grau, gemeldeter Grund wie Rueckfall', () => {
    expect(laufband(lauf({ noWorkReason: GRUND })).melder).toBe('grau')
    expect(laufband(lauf({ noWorkReason: GRUND_UNBEKANNT })).melder).toBe('grau')
  })

  // "Laeuft noch" schlaegt "ohne Arbeit": Ein laufender Lauf hat noch nichts zu melden.
  it('laufMelder bleibt stahl am laufenden Lauf, auch mit Grund', () => {
    expect(laufMelder(lauf({ complete: false, noWorkReason: GRUND, items: [] }))).toBe('stahl')
  })

  it('laufMelder bleibt gruen am abgeschlossenen Lauf ohne Grund', () => {
    expect(laufMelder(lauf({ noWorkReason: null, items: [] }))).toBe('gruen')
  })

  // E10: `laufMelder` hat nur noch ein Argument — der Grund geht ihn nichts mehr an.
  it('laufMelder rechnet ohne Befund allein an den Paketen', () => {
    expect(laufMelder({ complete: true, items: [] })).toBe('gruen')
    expect(laufMelder({ complete: false, items: [] })).toBe('stahl')
    expect(laufMelder({ complete: true, items: [{ state: 'RED' }] })).toBe('zinnob')
    expect(laufMelder({ complete: true, items: [{ state: 'YELLOW' }] })).toBe('bernst')
  })

  it('laufband traegt den Grund als Titel, meldet grau und laesst Dauer und Kosten stehen', () => {
    const ohne = laufband(lauf({ noWorkReason: GRUND, usage: { costUsd: 8.03, inputTokens: null, outputTokens: null, cachedInputTokens: null, modelDurationMs: null, turns: null } }))

    expect(ohne.titel).toBe(GRUND)
    expect(ohne.melder).toBe('grau')
    expect(ohne.minuten).toBe(252)
    expect(ohne.kosten).toBe('8,03')
    expect(ohne.laeuft).toBe(false)
  })

  it('laufband bleibt ohne Grund bei seinem bisherigen Titel', () => {
    expect(laufband(lauf({ noWorkReason: null })).titel).toBe('Kette abgeschlossen — 0 Vorgänge')
  })

  /**
   * E11: Der Rueckfalltext steht am Datensatz, der Lauf ist aber „mit Vorbehalt" — Laufband und
   * Notiz zeigen dann den regulaeren Text ohne Grund. Sie lesen die Auskunft und nicht mehr
   * `lauf.noWorkReason`; sonst behauptete die Anzeige „nichts zu tun" an einem Lauf, der ein Paket
   * zurueckstellte.
   */
  it('laesst dem Lauf mit zurueckgestelltem Paket den regulaeren Text, trotz Rueckfalltext am Datensatz', () => {
    const band = laufband(zurueckgestellt())

    expect(band.titel).toBe('Kette abgeschlossen — 1 Vorgang')
    expect(band.titel).not.toContain(GRUND_UNBEKANNT)
    expect(band.melder).toBe('grau')
    expect(laufNotiz(zurueckgestellt())).not.toContain(GRUND_UNBEKANNT)
    expect(laufNotiz(zurueckgestellt())).toBe(laufNotiz(zurueckgestellt(null)))
  })

  it('laufNotiz haengt den Grund an und laesst ihn ohne Grund weg', () => {
    expect(laufNotiz(lauf({ noWorkReason: GRUND }))).toContain(GRUND)
    expect(laufNotiz(lauf({ noWorkReason: GRUND_UNBEKANNT }))).toContain(GRUND_UNBEKANNT)
    expect(laufNotiz(lauf({ noWorkReason: null }))).not.toContain('·  ')
    expect(laufNotiz(lauf({ noWorkReason: null }))).toBe(laufNotiz(lauf()))
  })
})

describe('leitstand Der Browser liest den Massstab (#1081)', () => {
  const befund = (
    verdict: Verdict,
    decisiveItem: { cardNumber: number; state: NightRunState; errorClass: NightRunErrorClass | null } | null = null,
    noWorkReason: string | null = null,
  ) => ({ verdict, decisiveItem, noWorkReason, abortReason: null })

  // Der Befund gewinnt gegen die Pakete: Sonst waere er nur Zierde, und die zweite Rechnung
  // entschiede weiter.
  it('laufMelder nimmt den Melder aus dem Befund, auch gegen die eigenen Pakete', () => {
    const lauf = { complete: true, items: [paket(1, 'GREEN')], outcome: befund('FAILED', { cardNumber: 9, state: 'RED' as const, errorClass: 'HARD_ABORT' as const }) }

    expect(laufMelder(lauf)).toBe('zinnob')
  })

  it('laufMelder liest gelb aus dem Befund als bernst, nicht als zinnob', () => {
    // Der Server fasst rot und gelb zu FAILED zusammen; die Anzeige unterscheidet sie seit jeher.
    const lauf = { complete: true, items: [], outcome: befund('FAILED', { cardNumber: 9, state: 'YELLOW' as const, errorClass: 'CHECKS_RED' as const }) }

    expect(laufMelder(lauf)).toBe('bernst')
  })

  it('laufMelder meldet den wartenden Lauf aus dem Befund grau', () => {
    const lauf = { complete: true, items: [], outcome: befund('WAITING', { cardNumber: 9, state: 'GREY' as const, errorClass: 'DEPENDENCY_UNMET' as const }) }

    expect(laufMelder(lauf)).toBe('grau')
  })

  it('laufMelder meldet den laufenden Lauf aus dem Befund stahl', () => {
    expect(laufMelder({ complete: true, items: [paket(1, 'RED', { errorClass: 'HARD_ABORT' })], outcome: befund('RUNNING') })).toBe('stahl')
  })

  // Seit #1185 entscheidet allein der Ausgang und nicht der Wortlaut des Grundes: Jeder Lauf ohne
  // Arbeit ist NO_WORK und grau, der Rueckfall des Servers eingeschlossen.
  it('laufMelder meldet den Lauf ohne Arbeit aus dem Befund grau, auch mit dem Rueckfalltext', () => {
    expect(laufMelder({ complete: true, items: [], outcome: befund('NO_WORK', null, 'Ready ist leer — nichts zu tun.') })).toBe('grau')
    expect(laufMelder({ complete: true, items: [], outcome: befund('NO_WORK', null, GRUND_UNBEKANNT) })).toBe('grau')
  })

  it('laufMelder meldet den gelungenen Lauf aus dem Befund gruen', () => {
    expect(laufMelder({ complete: true, items: [], outcome: befund('SUCCEEDED') })).toBe('gruen')
  })

  // Ohne Befund bleibt die lokale Rechnung: der eben geparste Lauf der Nachtlauf-Seite. Einen
  // Grund traegt er nie (Plan #1181 E10) — die Zeile dazu ist mit dem Parameter entfallen.
  it('laufMelder rechnet ohne Befund weiter lokal — rot, gelb, laufend', () => {
    expect(laufMelder({ complete: true, items: [paket(1, 'RED', { errorClass: 'HARD_ABORT' })] })).toBe('zinnob')
    expect(laufMelder({ complete: true, items: [paket(1, 'YELLOW', { errorClass: 'CHECKS_RED' })] })).toBe('bernst')
    expect(laufMelder({ complete: false, items: [] })).toBe('stahl')
  })

  // Neu gegenueber #1069: Ein zurueckgestelltes Paket ist eine Stoerung und faellt nicht auf gruen.
  it('laufMelder erkennt ohne Befund das zurueckgestellte Paket', () => {
    expect(laufMelder({ complete: true, items: [paket(1, 'GREEN'), paket(2, 'GREY', { errorClass: 'DEPENDENCY_UNMET' })] })).toBe('grau')
    expect(laufMelder({ complete: true, items: [paket(1, 'GREY', { errorClass: 'AWAITING_DECISION' })] })).toBe('grau')
  })

  // Grau ohne Fehlerklasse ist ein uebergangenes Paket — der Lauf hat es nicht angefasst.
  it('laufMelder laesst grau ohne Fehlerklasse gruen', () => {
    expect(laufMelder({ complete: true, items: [paket(1, 'GREEN'), paket(2, 'GREY')] })).toBe('gruen')
  })

  // Dieselbe Rangfolge wie im Server (#1078): rot vor gelb vor grau-mit-Fehlerklasse.
  it('laufMelder haelt ohne Befund die Rangfolge des Servers', () => {
    const items = [paket(1, 'GREY', { errorClass: 'DEPENDENCY_UNMET' }), paket(2, 'YELLOW', { errorClass: 'CHECKS_RED' })]

    expect(laufMelder({ complete: true, items })).toBe('bernst')
  })
})

describe('leitstand Der verstummte Lauf (#1092)', () => {
  // Der Befund kommt aus dem Testhelfer und nicht von Hand: Er baut dieselbe Regel nach wie der
  // Server (Issue #1091) — ein unfertiger Lauf ohne Lebenszeichen ueber die Frist ist FAILED, ohne
  // massgebliches Paket und ohne Grund.
  const verstummt = serverBefund({ complete: false, verstummt: true, items: [] })

  it('serverBefund gibt dem verstummten Lauf FAILED ohne Paket und ohne Grund', () => {
    expect(verstummt).toEqual({ verdict: 'FAILED', decisiveItem: null, noWorkReason: null, abortReason: null })
  })

  // Das Test-Double des Servers zieht die Rangfolge aus #1185 mit: Das massgebliche Paket geht dem
  // Grund vor, und ohne massgebliches Paket ist jeder Grund NO_WORK — ohne Blick auf den Wortlaut.
  // Liefe es auseinander, behaupteten die Fixtures etwas anderes als der Server.
  it('serverBefund gibt jedem Grund ohne massgebliches Paket NO_WORK und laesst ein zurueckgestelltes Paket WAITING', () => {
    const gemeldet = 'Ready ist leer — nichts zu tun.'

    expect(serverBefund({ complete: true, noWorkReason: gemeldet, items: [] })).toEqual({
      verdict: 'NO_WORK',
      decisiveItem: null,
      noWorkReason: gemeldet,
      abortReason: null,
    })
    expect(serverBefund({ complete: true, noWorkReason: GRUND_UNBEKANNT, items: [] })).toEqual({
      verdict: 'NO_WORK',
      decisiveItem: null,
      noWorkReason: GRUND_UNBEKANNT,
      abortReason: null,
    })
    // Der Kern der neuen Rangfolge: Wer alle Pakete zurueckstellte, meldet keinen Grund — und der
    // Rueckfall des Servers machte ihn bis #1185 rot statt „mit Vorbehalt".
    expect(
      serverBefund({
        complete: true,
        noWorkReason: GRUND_UNBEKANNT,
        items: [paket(1, 'GREY', { errorClass: 'DEPENDENCY_UNMET' })],
      }),
    ).toEqual({
      verdict: 'WAITING',
      decisiveItem: { cardNumber: 1, state: 'GREY', errorClass: 'DEPENDENCY_UNMET' },
      noWorkReason: null,
      abortReason: null,
    })
  })

  // Issue #1123: Das Test-Double zieht auch die Laufart mit. Die Ketten-Einheit steht immer zuerst
  // und hat ihren Abbruch geerbt — massgeblich ist das Paket, an dem die Kette riss.
  it('serverBefund waehlt in einer Kette das letzte gleichrangige Paket', () => {
    const items = [paket(993, 'RED', { errorClass: 'HARD_ABORT' }), paket(1112, 'RED', { errorClass: 'HARD_ABORT' })]

    expect(serverBefund({ complete: true, mode: 'CHAIN', items }).decisiveItem?.cardNumber).toBe(1112)
    expect(serverBefund({ complete: true, mode: 'IMPLEMENTATION', items }).decisiveItem?.cardNumber).toBe(993)
  })

  // Der Kern des Pakets: Bisher fiel ein Befund ohne massgebliches Paket auf gruen durch — und der
  // verstummte Lauf hat keines.
  it('laufMelder meldet den verstummten Lauf zinnob statt gruen', () => {
    expect(laufMelder({ complete: false, items: [], outcome: verstummt })).toBe('zinnob')
  })

  it('laufMelder laesst gruen dem gelungenen Lauf und stahl dem laufenden', () => {
    expect(laufMelder({ complete: true, items: [], outcome: { abortReason: null, verdict: 'SUCCEEDED', decisiveItem: null, noWorkReason: null } })).toBe('gruen')
    expect(laufMelder({ complete: false, items: [], outcome: { abortReason: null, verdict: 'RUNNING', decisiveItem: null, noWorkReason: null } })).toBe('stahl')
  })

  // Die Zuordnung Zustand → Melder bleibt unangetastet: Der Umbau betrifft nur den Rueckfall ohne
  // massgebliches Paket, nicht die Farbe eines Pakets.
  it('haelt die Zuordnung MELDER_JE_ZUSTAND unveraendert', () => {
    expect(MELDER_JE_ZUSTAND).toEqual({ GREEN: 'gruen', YELLOW: 'bernst', RED: 'zinnob', GREY: 'grau' })
  })

  it('laufband nimmt dem verstummten Lauf das Laufen und nennt den Beginn statt „seit"', () => {
    const band = laufband(lauf({ complete: false, items: [], outcome: verstummt }))

    expect(band.laeuft).toBe(false)
    expect(band.zeitpunkt).toMatch(/^Beginn \d\d\.\d\d\., \d\d:\d\d$/)
    expect(band.melder).toBe('zinnob')
  })

  it('laufband laesst den laufenden Lauf unveraendert laufen', () => {
    const band = laufband(lauf({ complete: false, items: [] }))

    expect(band.laeuft).toBe(true)
    expect(band.zeitpunkt).toMatch(/^seit \d\d:\d\d$/)
    expect(band.melder).toBe('stahl')
  })

  // Ohne Befund bleibt `complete` der Massstab — der eben im Browser geparste Lauf der
  // Nachtlauf-Seite hat keinen (Plan #1072 E28).
  it('laeuftNoch faellt ohne Befund auf complete zurueck', () => {
    expect(laeuftNoch({ complete: false })).toBe(true)
    expect(laeuftNoch({ complete: true })).toBe(false)
  })
})

/**
 * Der Melder aus dem Befund ist seit #1096 oeffentlich: Beide Zeilen des Plattform-Leitstands
 * beziehen ihn daraus. Diese Tests rufen ihn ueber den Export auf, nicht ueber `laufMelder` —
 * sonst bliebe der Export ungenutzt und liefe beim naechsten Umbau still weg.
 */
describe('melderAusBefund — der Melder eines Laufs aus seinem Befund (#1096)', () => {
  it('meldet den laufenden Lauf stahl', () => {
    expect(melderAusBefund({ abortReason: null, verdict: 'RUNNING', decisiveItem: null, noWorkReason: null })).toBe('stahl')
  })

  // Seit #1185 auch der Rueckfall des Servers grau: Am Wortlaut haengt keine Aussage ueber den
  // Ausgang mehr, und was hinter ihm an echtem Problem stand, sagen jetzt die Pakete.
  it('meldet den Lauf ohne Arbeit mit unbekanntem Grund grau', () => {
    expect(melderAusBefund({ abortReason: null, verdict: 'NO_WORK', decisiveItem: null, noWorkReason: GRUND_UNBEKANNT })).toBe('grau')
  })

  it('meldet den Lauf, der nichts zu tun fand, grau', () => {
    expect(
      melderAusBefund({ abortReason: null, verdict: 'NO_WORK', decisiveItem: null, noWorkReason: 'Ready ist leer — nichts zu tun.' }),
    ).toBe('grau')
  })

  /**
   * AK 6 aus #1121 ueber **alle** fuenf Ausgaenge: Ein Lauf ohne Arbeit mit gemeldetem Grund ist
   * `NO_WORK`, und der ist nirgends rot. Die Tabelle geht ueber die Schluessel der Wortliste und
   * nicht ueber eine eigene Aufzaehlung — ein sechster Ausgang faellt hier auf, statt still
   * mitzulaufen.
   */
  it('ordnet jedem der fuenf Ausgaenge seinen Melder zu, und nur FAILED ist rot', () => {
    const je = Object.fromEntries(
      (Object.keys(NIGHT_RUN_VERDICT_TEXT) as Verdict[]).map((verdict) => [
        verdict,
        melderAusBefund({
          verdict,
          decisiveItem: null,
          noWorkReason: verdict === 'NO_WORK' ? 'Keine Kette zu fahren — nichts zu tun.' : null,
          abortReason: null,
        }),
      ]),
    )

    expect(je).toEqual({
      SUCCEEDED: 'gruen',
      FAILED: 'zinnob',
      WAITING: 'zinnob',
      RUNNING: 'stahl',
      NO_WORK: 'grau',
    })
  })

  it('nimmt den Melder aus dem Zustand des massgeblichen Pakets', () => {
    const je = (state: NightRunState, errorClass: NightRunErrorClass | null) =>
      melderAusBefund({ abortReason: null, verdict: 'FAILED', decisiveItem: { cardNumber: 9, state, errorClass }, noWorkReason: null })

    expect(je('RED', 'HARD_ABORT')).toBe('zinnob')
    expect(je('YELLOW', 'CHECKS_RED')).toBe('bernst')
    expect(je('GREY', 'DEPENDENCY_UNMET')).toBe('grau')
  })

  it('laesst gruen allein dem gelungenen Lauf, der verstummte bleibt zinnob', () => {
    expect(melderAusBefund({ abortReason: null, verdict: 'SUCCEEDED', decisiveItem: null, noWorkReason: null })).toBe('gruen')
    expect(melderAusBefund({ abortReason: null, verdict: 'FAILED', decisiveItem: null, noWorkReason: null })).toBe('zinnob')
  })
})

/**
 * Der selbst gemeldete Abbruch (Issue #1144, Plan #1139). Ein Lauf, der abbrach, ist nie gelungen —
 * auch nicht nach drei gruenen Paketen und auch nicht, wenn sein massgebliches Paket sonst grau
 * oder bernstein leuchtete.
 */
describe('Der abgebrochene Lauf im Browser (#1144)', () => {
  const ABBRUCH = 'Harter Stopp (dirty-tree): der Working Tree traegt uncommittete Reste'

  it('meldet den abgebrochenen Lauf zinnob, auch bei grauem oder gelbem massgeblichem Paket', () => {
    const je = (state: NightRunState, errorClass: NightRunErrorClass) =>
      melderAusBefund({
        verdict: 'FAILED',
        decisiveItem: { cardNumber: 1112, state, errorClass },
        noWorkReason: null,
        abortReason: ABBRUCH,
      })

    expect(je('GREY', 'DEPENDENCY_UNMET')).toBe('zinnob')
    expect(je('YELLOW', 'CHECKS_RED')).toBe('zinnob')
    expect(je('RED', 'HARD_ABORT')).toBe('zinnob')
  })

  it('meldet den abgebrochenen Lauf ohne massgebliches Paket zinnob', () => {
    expect(
      melderAusBefund({ verdict: 'FAILED', decisiveItem: null, noWorkReason: null, abortReason: ABBRUCH }),
    ).toBe('zinnob')
  })

  // AK 5: Der Abbruchgrund verdraengt den Rueckfalltext — das Laufband zeigt nie beides.
  it('traegt den Abbruchgrund als Titel des Laufbands und nie „Grund unbekannt"', () => {
    const band = laufband(lauf({ abortReason: ABBRUCH, noWorkReason: GRUND_UNBEKANNT }))

    expect(band.titel).toBe(ABBRUCH)
    expect(band.titel).not.toContain(GRUND_UNBEKANNT)
    expect(band.melder).toBe('zinnob')
  })

  it('stellt den Abbruchgrund vor die Auskunft ueber die Zahl der Vorgaenge', () => {
    const band = laufband(lauf({ abortReason: ABBRUCH, items: [paket(1, 'GREEN'), paket(2, 'GREEN')] }))

    expect(band.titel).toBe(ABBRUCH)
    expect(band.titel).not.toContain('Vorgänge')
  })

  it('haengt den Abbruchgrund an die Notiz, wo sonst der Grund ohne Arbeit haengt', () => {
    expect(laufNotiz(lauf({ abortReason: ABBRUCH }))).toBe(`${laufNotiz(lauf())} · ${ABBRUCH}`)
  })

  // E6: Beide Felder stehen nie zugleich — traegt der Lauf doch beide, gilt der Abbruchgrund.
  it('nennt in der Notiz den Abbruchgrund und nicht den Grund ohne Arbeit', () => {
    const notiz = laufNotiz(lauf({ abortReason: ABBRUCH, noWorkReason: GRUND_UNBEKANNT }))

    expect(notiz).toContain(ABBRUCH)
    expect(notiz).not.toContain(GRUND_UNBEKANNT)
  })

  it('serverBefund stellt den Abbruch vor den Lauf ohne Arbeit und behaelt das massgebliche Paket', () => {
    const items = [paket(993, 'GREEN'), paket(1112, 'YELLOW', { errorClass: 'CHECKS_RED' })]
    const abgebrochen = serverBefund({ complete: true, abortReason: ABBRUCH, noWorkReason: GRUND_UNBEKANNT, items })

    expect(abgebrochen.verdict).toBe('FAILED')
    expect(abgebrochen.abortReason).toBe(ABBRUCH)
    expect(abgebrochen.noWorkReason).toBeNull()
    expect(abgebrochen.decisiveItem?.cardNumber).toBe(1112)
  })

  it('laesst den nicht abgebrochenen Lauf ohne Abbruchgrund', () => {
    expect(serverBefund({ complete: true, items: [] }).abortReason).toBeNull()
    expect(laufband(lauf({ abortReason: null })).titel).toBe('Kette abgeschlossen — 0 Vorgänge')
    expect(laufNotiz(lauf({ abortReason: null }))).toBe(laufNotiz(lauf()))
  })
})

describe('nachProjekt', () => {
  const eintrag = (projectId: number, projectName: string, marke: string) => ({ projectId, projectName, marke })

  it('gruppiert nach Projekt und behaelt die Einfuegereihenfolge der Gruppen', () => {
    const gruppen = nachProjekt([
      eintrag(7, 'Kanban-Kit', 'a'),
      eintrag(3, 'Leitstand', 'b'),
      eintrag(7, 'Kanban-Kit', 'c'),
    ])

    expect(gruppen.map((g) => g.projectId)).toEqual([7, 3])
    expect(gruppen[0].projectName).toBe('Kanban-Kit')
    expect(gruppen[0].eintraege.map((e) => e.marke)).toEqual(['a', 'c'])
    expect(gruppen[1].eintraege.map((e) => e.marke)).toEqual(['b'])
  })

  it('liefert fuer die leere Liste keine Gruppe', () => {
    expect(nachProjekt([])).toEqual([])
  })

  it('haelt zwei Projekte gleichen Namens mit verschiedener projectId auseinander', () => {
    const gruppen = nachProjekt([eintrag(1, 'Doppel', 'a'), eintrag(2, 'Doppel', 'b')])

    expect(gruppen).toHaveLength(2)
    expect(gruppen.map((g) => g.projectId)).toEqual([1, 2])
    expect(gruppen.every((g) => g.projectName === 'Doppel')).toBe(true)
  })
})
