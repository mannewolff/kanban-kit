import { describe, expect, it } from 'vitest'
import type {
  CardRefView,
  ChainProgressView,
  KettenStand,
  KettenStation,
  NightChainStationView,
  NightRunProgressView,
  PackageProgressView,
  PackageState,
  StageProgressView,
} from '../api/nightRuns'
import type { NightRunState } from './nightRunLog'
import type { NightRunBudgetView, NightRunOutcomeView } from '../api/nightRuns'
import { woerterbuch } from '../i18n/woerterbuch'
import {
  kettenAnsage,
  kettenAnzeige,
  kettenkosten,
  laeufeZusammenfuehren,
  rasterzaehlung,
  rasterzustand,
  stufenzeit,
  vorwegZaehlung,
  wegleiste,
  zustandssatz,
} from './laufFortschritt'

const karte = (number: number): CardRefView => ({ number, title: `Karte ${number}`, boardId: 3 })

const paket = (number: number, zustand: PackageState): PackageProgressView => ({
  karte: karte(number),
  zustand,
})

const fortschritt = (
  pakete: PackageProgressView[],
  unbekannt: CardRefView[] = [],
): NightRunProgressView => ({
  zuordnung: 'OK',
  ketten: [],
  pakete,
  unbekannt,
  unbekanntOhneAusweis: false,
  offeneFragen: [],
})

const item = (cardNumber: number, state: NightRunState) => ({ cardNumber, state })

describe('vorwegZaehlung', () => {
  it('zählt ohne Fortschritt allein die gemeldeten Items, rot als bearbeitet', () => {
    const items = [
      item(1, 'GREEN'),
      item(2, 'YELLOW'),
      item(3, 'RED'),
      item(4, 'GREY'),
      item(5, 'GREY'),
    ]
    expect(vorwegZaehlung(items, null, true)).toEqual({
      bearbeitet: 3,
      uebergangen: 2,
      gruen: 1,
      gelb: 1,
      rot: 1,
    })
  })

  it('zählt ein fertiges, noch nicht gemeldetes Paket als bearbeitet und grün', () => {
    expect(vorwegZaehlung([], fortschritt([paket(10, 'FERTIG')]), true)).toEqual({
      bearbeitet: 1,
      uebergangen: 0,
      gruen: 1,
      gelb: 0,
      rot: 0,
    })
  })

  it('zählt ein zurückgestelltes, noch nicht gemeldetes Paket als bearbeitet und rot', () => {
    expect(vorwegZaehlung([], fortschritt([paket(11, 'ZURUECKGESTELLT')]), true)).toEqual({
      bearbeitet: 1,
      uebergangen: 0,
      gruen: 0,
      gelb: 0,
      rot: 1,
    })
  })

  it('zählt Pakete in Umsetzung, gezogene und angelegte nirgends', () => {
    const f = fortschritt([paket(12, 'IN_UMSETZUNG'), paket(13, 'GEZOGEN'), paket(14, 'ANGELEGT')])
    expect(vorwegZaehlung([], f, true)).toEqual({
      bearbeitet: 0,
      uebergangen: 0,
      gruen: 0,
      gelb: 0,
      rot: 0,
    })
  })

  it('zählt ein schon gemeldetes Paket nicht doppelt und lässt seine Meldung stehen', () => {
    const items = [item(20, 'YELLOW'), item(21, 'GREY')]
    const f = fortschritt([paket(20, 'FERTIG'), paket(21, 'ZURUECKGESTELLT')])
    expect(vorwegZaehlung(items, f, true)).toEqual({
      bearbeitet: 1,
      uebergangen: 1,
      gruen: 0,
      gelb: 1,
      rot: 0,
    })
  })

  it('zählt eine Karte unter „unbekannt" nicht', () => {
    const f = fortschritt([paket(30, 'FERTIG'), paket(31, 'ZURUECKGESTELLT')], [karte(30), karte(31)])
    expect(vorwegZaehlung([], f, true)).toEqual({
      bearbeitet: 0,
      uebergangen: 0,
      gruen: 0,
      gelb: 0,
      rot: 0,
    })
  })

  it('zählt dieselbe Kartennummer im Fortschritt nur einmal', () => {
    const f = fortschritt([paket(40, 'FERTIG'), paket(40, 'FERTIG')])
    expect(vorwegZaehlung([], f, true).gruen).toBe(1)
  })

  it('nimmt beim abgeschlossenen Lauf allein die gemeldeten Zahlen', () => {
    const f = fortschritt([paket(50, 'FERTIG'), paket(51, 'ZURUECKGESTELLT')])
    expect(vorwegZaehlung([item(52, 'GREEN')], f, false)).toEqual({
      bearbeitet: 1,
      uebergangen: 0,
      gruen: 1,
      gelb: 0,
      rot: 0,
    })
  })
})

const stufe = (
  s: StageProgressView['stufe'],
  zustand: StageProgressView['zustand'],
  seit: string | null = null,
): StageProgressView => ({
  stufe: s,
  zustand,
  seit,
})

const kette = (teil: Partial<ChainProgressView>): ChainProgressView => ({
  anforderung: karte(1364),
  plan: karte(1372),
  pakete: [],
  stufen: [],
  aktuelleStufe: null,
  endeErreicht: false,
  ...teil,
})

describe('wegleiste', () => {
  it('zeigt Variante A mit erreichtem Ende nach der Abdeckung', () => {
    const k = kette({
      stufen: [
        stufe('PLAN', 'ERREICHT'),
        stufe('REVIEW', 'ERREICHT'),
        stufe('PAKETE', 'ERREICHT'),
        stufe('ABDECKUNG', 'ERREICHT'),
      ],
      aktuelleStufe: null,
      endeErreicht: true,
    })
    expect(wegleiste(k, 'KETTE')).toEqual({
      stufen: [
        { stufe: 'PLAN', name: 'Plan', zustand: 'erreicht', stand: 'ERREICHT', aktuell: false },
        { stufe: 'REVIEW', name: 'Prüfung', zustand: 'erreicht', stand: 'ERREICHT', aktuell: false },
        { stufe: 'PAKETE', name: 'Arbeitspakete', zustand: 'erreicht', stand: 'ERREICHT', aktuell: false },
        { stufe: 'ABDECKUNG', name: 'Abdeckung', zustand: 'erreicht', stand: 'ERREICHT', aktuell: false },
      ],
      ende: 'Ende des Wegs erreicht',
    })
  })

  it('zeigt Variante B mit Umsetzung und markiert die aktuelle Stufe', () => {
    const k = kette({
      stufen: [
        stufe('PLAN', 'ERREICHT'),
        stufe('REVIEW', 'ERREICHT'),
        stufe('PAKETE', 'LAEUFT'),
        stufe('ABDECKUNG', 'OFFEN'),
        stufe('UMSETZUNG', 'OFFEN'),
      ],
      aktuelleStufe: 'PAKETE',
    })
    expect(wegleiste(k, 'KETTE')).toEqual({
      stufen: [
        { stufe: 'PLAN', name: 'Plan', zustand: 'erreicht', stand: 'ERREICHT', aktuell: false },
        { stufe: 'REVIEW', name: 'Prüfung', zustand: 'erreicht', stand: 'ERREICHT', aktuell: false },
        { stufe: 'PAKETE', name: 'Arbeitspakete', zustand: 'läuft', stand: 'LAEUFT', aktuell: true },
        { stufe: 'ABDECKUNG', name: 'Abdeckung', zustand: 'offen', stand: 'OFFEN', aktuell: false },
        { stufe: 'UMSETZUNG', name: 'Umsetzung', zustand: 'offen', stand: 'OFFEN', aktuell: false },
      ],
      ende: null,
    })
  })

  it('zeigt bei der Umsetzungsnacht nur die Umsetzung als laufende Stufe', () => {
    expect(wegleiste(null, 'UMSETZUNGSNACHT')).toEqual({
      stufen: [{ stufe: 'UMSETZUNG', name: 'Umsetzung', zustand: 'läuft', stand: 'LAEUFT', aktuell: true }],
      ende: null,
    })
  })

  it('zeigt bei der Umsetzungsnacht nur die Umsetzung, auch wenn eine Kette mitkommt', () => {
    const k = kette({ stufen: [stufe('PLAN', 'LAEUFT')], aktuelleStufe: 'PLAN' })
    expect(wegleiste(k, 'UMSETZUNGSNACHT').stufen.map((s) => s.stufe)).toEqual(['UMSETZUNG'])
  })

  it('liefert für eine Kette ohne bekannte Kette einen leeren Weg', () => {
    expect(wegleiste(null, 'KETTE')).toEqual({ stufen: [], ende: null })
  })
})

interface Lauf {
  startedAt: string
  gespeichert: boolean
  name: string
}

const lauf = (startedAt: string, gespeichert: boolean, name = startedAt): Lauf => ({
  startedAt,
  gespeichert,
  name,
})

describe('laeufeZusammenfuehren', () => {
  it('ersetzt servergeführte Läufe durch den neuen Server-Stand', () => {
    const bisher = [lauf('2026-10-03T22:00:00Z', true, 'alt')]
    const vomServer = [lauf('2026-10-03T22:00:00Z', true, 'neu')]
    expect(laeufeZusammenfuehren(bisher, vomServer)).toEqual([
      lauf('2026-10-03T22:00:00Z', true, 'neu'),
    ])
  })

  it('behält browser-eigene Läufe ohne servergeführtes Gegenstück, absteigend nach Start', () => {
    const nachtplan = lauf('2026-10-02T22:00:00Z', false, 'nachtplan')
    const eingelesen = lauf('2026-10-04T22:00:00Z', false, 'eingelesen')
    const bisher = [eingelesen, lauf('2026-10-03T22:00:00Z', true, 'alt'), nachtplan]
    const vomServer = [lauf('2026-10-03T22:00:00Z', true, 'neu'), lauf('2026-10-01T22:00:00Z', true)]
    expect(laeufeZusammenfuehren(bisher, vomServer).map((l) => l.name)).toEqual([
      'eingelesen',
      'neu',
      'nachtplan',
      '2026-10-01T22:00:00Z',
    ])
  })

  it('ersetzt einen browser-eigenen Lauf, sobald der Server ihn führt', () => {
    const bisher = [lauf('2026-10-04T22:00:00Z', false, 'eingelesen')]
    const vomServer = [lauf('2026-10-04T22:00:00Z', true, 'servergefuehrt')]
    expect(laeufeZusammenfuehren(bisher, vomServer)).toEqual(vomServer)
  })

  it('lässt einen servergeführten Lauf fallen, den der Server nicht mehr liefert', () => {
    const bisher = [lauf('2026-09-01T22:00:00Z', true)]
    expect(laeufeZusammenfuehren(bisher, [])).toEqual([])
  })
})

describe('kettenAnzeige (Issue #1453, Plan #1447 E5, E6)', () => {
  const station = (
    s: KettenStation,
    zustand: NightChainStationView['zustand'],
    text: string,
    grund: string | null = null,
  ): NightChainStationView => ({ station: s, zustand, text, grund })

  const stand = (stationen: NightChainStationView[], teil: Partial<KettenStand> = {}): KettenStand => ({
    ziel: 'UMSETZUNG',
    pruefer: null,
    zielErreicht: false,
    grenze: null,
    stationen,
    uebernommen: true,
    planReviewVorhanden: false,
    lauf: '2026-10-05T01:00:00Z',
    ...teil,
  })

  it('bildet alle sieben Zustände auf Symbol und Text ab — der Text kommt fertig vom Server', () => {
    const anzeige = kettenAnzeige(
      stand([
        station('PLAN', 'VOR_DEM_LAUF_ERBRACHT', 'vor dem Lauf erbracht'),
        station('REVIEW', 'ERLEDIGT', 'erledigt'),
        station('PAKETE', 'LAEUFT', 'läuft'),
        station('ABDECKUNG', 'WARTET', 'wartet', 'wartet: Frage an den Menschen'),
        station('UMSETZUNG', 'ABGEBROCHEN', 'abgebrochen', 'abgebrochen: Zeitgrenze'),
        station('VORBEREITUNG', 'NICHT_VORGESEHEN', 'nicht vorgesehen'),
      ]),
    )
    expect(anzeige.map((a) => [a.station, a.name, a.symbol, a.text, a.grund])).toEqual([
      ['PLAN', 'Plan', 'erbracht', 'vor dem Lauf erbracht', null],
      ['REVIEW', 'Prüfung', 'erledigt', 'erledigt', null],
      ['PAKETE', 'Arbeitspakete', 'laeuft', 'läuft', null],
      ['ABDECKUNG', 'Abdeckung', 'wartet', 'wartet', 'wartet: Frage an den Menschen'],
      ['UMSETZUNG', 'Umsetzung', 'abgebrochen', 'abgebrochen', 'abgebrochen: Zeitgrenze'],
      ['VORBEREITUNG', 'Veröffentlichung vorbereitet', 'nicht-vorgesehen', 'nicht vorgesehen', null],
    ])
    const steht = kettenAnzeige(stand([station('UMSETZUNG', 'STEHT_AUS', 'steht aus')]))[0]
    expect([steht.symbol, steht.text]).toEqual(['steht-aus', 'steht aus'])
  })

  it('übernimmt „läuft (2 Prüfer)“ wörtlich', () => {
    const [review] = kettenAnzeige(stand([station('REVIEW', 'LAEUFT', 'läuft (2 Prüfer)')], { pruefer: 2 }))
    expect(review.text).toBe('läuft (2 Prüfer)')
    expect(review.symbol).toBe('laeuft')
  })

  it('zeigt an der erreichten Zielstation „Ziel erreicht“ mit eigenem Symbol', () => {
    const anzeige = kettenAnzeige(
      stand(
        [station('PAKETE', 'ERLEDIGT', 'erledigt'), station('UMSETZUNG', 'ERLEDIGT', 'Ziel erreicht')],
        { zielErreicht: true },
      ),
    )
    expect(anzeige.map((a) => [a.symbol, a.text])).toEqual([
      ['erledigt', 'erledigt'],
      ['ziel-erreicht', 'Ziel erreicht'],
    ])
  })

  it('zeigt ein erledigtes Ziel ohne „fertig bis“ nicht als erreicht', () => {
    const [umsetzung] = kettenAnzeige(stand([station('UMSETZUNG', 'ERLEDIGT', 'erledigt')]))
    expect(umsetzung.symbol).toBe('erledigt')
  })

  it('setzt an der Projektgrenze den Grund in den Text: „Projektgrenze: <Grund>“', () => {
    const grund = 'wartet: Übergang pakete→umsetzung im Projekt nicht freigegeben — weiter mit kit:night'
    const [umsetzung] = kettenAnzeige(
      stand([station('UMSETZUNG', 'WARTET', 'Projektgrenze', grund)], {
        grenze: { stufe: 'ABDECKUNG', grund },
      }),
    )
    expect(umsetzung.symbol).toBe('projektgrenze')
    expect(umsetzung.text).toBe(`Projektgrenze: ${grund}`)
    expect(umsetzung.grund).toBeNull()
  })

  it('zeigt die Projektgrenze ohne gemeldeten Grund als bloßes „Projektgrenze“', () => {
    const [umsetzung] = kettenAnzeige(stand([station('UMSETZUNG', 'WARTET', 'Projektgrenze')]))
    expect([umsetzung.symbol, umsetzung.text, umsetzung.grund]).toEqual(['projektgrenze', 'Projektgrenze', null])
  })

  it('markiert das gewählte Ziel, ohne Ziel „Arbeitspakete“, und die aktuelle Station', () => {
    const stationen = [
      station('PAKETE', 'ERLEDIGT', 'erledigt'),
      station('ABDECKUNG', 'LAEUFT', 'läuft'),
      station('UMSETZUNG', 'STEHT_AUS', 'steht aus'),
    ]
    expect(kettenAnzeige(stand(stationen)).map((a) => [a.ziel, a.aktuell])).toEqual([
      [false, false],
      [false, true],
      [true, false],
    ])
    expect(kettenAnzeige(stand(stationen, { ziel: null })).map((a) => a.ziel)).toEqual([true, false, false])
  })

  it('zählt wartend und abgebrochen als aktuell, alles andere nicht', () => {
    const aktuell = (zustand: NightChainStationView['zustand']) =>
      kettenAnzeige(stand([station('PAKETE', zustand, 'x')]))[0].aktuell
    expect(aktuell('WARTET')).toBe(true)
    expect(aktuell('ABGEBROCHEN')).toBe(true)
    expect(aktuell('STEHT_AUS')).toBe(false)
    expect(aktuell('NICHT_VORGESEHEN')).toBe(false)
    expect(aktuell('VOR_DEM_LAUF_ERBRACHT')).toBe(false)
  })

  it('sagt den ganzen Stand in einem Satz an, mit Grund und Ziel', () => {
    const anzeige = kettenAnzeige(
      stand([
        station('REVIEW', 'ERLEDIGT', 'erledigt'),
        station('PAKETE', 'WARTET', 'wartet', 'wartet: Frage'),
        station('UMSETZUNG', 'STEHT_AUS', 'steht aus'),
      ]),
    )
    expect(kettenAnsage(anzeige)).toBe(
      'Nacht-Kette: Prüfung erledigt; Arbeitspakete wartet (wartet: Frage); Umsetzung steht aus, Ziel',
    )
  })
})

describe('laufFortschritt auf Englisch', () => {
  it('benennt die Wegleiste auf Englisch', () => {
    const k = kette({
      stufen: [stufe('PLAN', 'ERREICHT'), stufe('REVIEW', 'LAEUFT'), stufe('PAKETE', 'OFFEN')],
      aktuelleStufe: 'REVIEW',
      endeErreicht: true,
    })
    expect(wegleiste(k, 'KETTE', 'en')).toEqual({
      stufen: [
        { stufe: 'PLAN', name: 'Plan', zustand: 'reached', stand: 'ERREICHT', aktuell: false },
        { stufe: 'REVIEW', name: 'Review', zustand: 'running', stand: 'LAEUFT', aktuell: true },
        { stufe: 'PAKETE', name: 'Work packages', zustand: 'open', stand: 'OFFEN', aktuell: false },
      ],
      ende: 'End of the path reached',
    })
    expect(wegleiste(null, 'UMSETZUNGSNACHT', 'en').stufen).toEqual([
      { stufe: 'UMSETZUNG', name: 'Implementation', zustand: 'running', stand: 'LAEUFT', aktuell: true },
    ])
  })

  it('benennt Stationen und Ansage der Kette auf Englisch', () => {
    const anzeige = kettenAnzeige(
      {
        ziel: 'VORBEREITUNG',
        pruefer: null,
        zielErreicht: false,
        grenze: null,
        stationen: [
          { station: 'ABDECKUNG', zustand: 'ERLEDIGT', text: 'done', grund: null },
          { station: 'VORBEREITUNG', zustand: 'WARTET', text: 'waiting', grund: 'question' },
        ],
        uebernommen: true,
        planReviewVorhanden: false,
        lauf: '2026-10-05T01:00:00Z',
      },
      'en',
    )
    expect(anzeige.map((a) => a.name)).toEqual(['Coverage', 'Release prepared'])
    expect(kettenAnsage(anzeige, 'en')).toBe('Night chain: Coverage done; Release prepared waiting (question), target')
  })
})

const lage = woerterbuch('de').NACHTLAUF_LAGE

const mitKetten = (ketten: ChainProgressView[], pakete: PackageProgressView[] = []): NightRunProgressView => ({
  ...fortschritt(pakete),
  ketten,
})

const befund = (verdict: NightRunOutcomeView['verdict']): NightRunOutcomeView => ({
  verdict,
  decisiveItem: null,
  noWorkReason: null,
  abortReason: null,
})

const keineZahlen = { gruen: 0, gelb: 0, rot: 0 }

const umsetzendeKette = (pakete: PackageProgressView[]) =>
  kette({
    pakete,
    stufen: [
      stufe('PLAN', 'ERREICHT'),
      stufe('REVIEW', 'ERREICHT'),
      stufe('PAKETE', 'ERREICHT'),
      stufe('ABDECKUNG', 'ERREICHT'),
      stufe('UMSETZUNG', 'LAEUFT', '2026-10-21T01:00:00Z'),
    ],
    aktuelleStufe: 'UMSETZUNG',
  })

describe('zustandssatz (Plan #1882 E4)', () => {
  it('nennt an einer Kette in Umsetzung das Paket und k von n fertig', () => {
    const k = umsetzendeKette([
      paket(1846, 'IN_UMSETZUNG'),
      paket(1843, 'FERTIG'),
      paket(1844, 'FERTIG'),
      paket(1847, 'GEZOGEN'),
    ])
    expect(zustandssatz(mitKetten([k]), 'KETTE', true, null, keineZahlen, lage)).toBe(
      'Setzt Paket #1846 um — 2 von 4 fertig',
    )
  })

  it('nennt mehrere Pakete in Umsetzung zugleich', () => {
    const k = umsetzendeKette([
      paket(1846, 'IN_UMSETZUNG'),
      paket(1847, 'IN_UMSETZUNG'),
      paket(1843, 'FERTIG'),
    ])
    expect(zustandssatz(mitKetten([k]), 'KETTE', true, null, keineZahlen, lage)).toBe(
      'Setzt Pakete #1846, #1847 um — 1 von 3 fertig',
    )
  })

  it('nennt in der Umsetzungsnacht die fertigen Pakete ohne Nenner', () => {
    const f = fortschritt([paket(20, 'FERTIG'), paket(21, 'IN_UMSETZUNG'), paket(22, 'GEZOGEN')])
    expect(zustandssatz(f, 'UMSETZUNGSNACHT', true, null, keineZahlen, lage)).toBe('Setzt Paket #21 um — 1 fertig')
  })

  it('nennt in der Umsetzungsnacht mehrere Pakete in Umsetzung', () => {
    const f = fortschritt([paket(21, 'IN_UMSETZUNG'), paket(22, 'IN_UMSETZUNG')])
    expect(zustandssatz(f, 'UMSETZUNGSNACHT', true, null, keineZahlen, lage)).toBe('Setzt Pakete #21, #22 um — 0 fertig')
  })

  it('nennt in der Umsetzungsnacht ohne Paket in Umsetzung nur die fertigen', () => {
    const f = fortschritt([paket(20, 'FERTIG'), paket(22, 'GEZOGEN')])
    expect(zustandssatz(f, 'UMSETZUNGSNACHT', true, null, keineZahlen, lage)).toBe('Umsetzung — 1 fertig')
  })

  it('nennt an einer Planstufe die Stufe und den Plan', () => {
    const k = kette({
      stufen: [stufe('PLAN', 'ERREICHT'), stufe('REVIEW', 'LAEUFT'), stufe('PAKETE', 'OFFEN')],
      aktuelleStufe: 'REVIEW',
    })
    expect(zustandssatz(mitKetten([k]), 'KETTE', true, null, keineZahlen, lage)).toBe('Prüfung zu Plan #1372')
  })

  it('nennt die Anforderung, solange die Kette noch keinen Plan hat', () => {
    const k = kette({ plan: null, stufen: [stufe('PLAN', 'LAEUFT')], aktuelleStufe: 'PLAN' })
    expect(zustandssatz(mitKetten([k]), 'KETTE', true, null, keineZahlen, lage)).toBe('Plan zu Anforderung #1364')
  })

  it('nennt an einer Kette in Umsetzung ohne Paket in Umsetzung die Stufe', () => {
    const k = umsetzendeKette([paket(1843, 'FERTIG'), paket(1847, 'GEZOGEN')])
    expect(zustandssatz(mitKetten([k]), 'KETTE', true, null, keineZahlen, lage)).toBe('Umsetzung zu Plan #1372')
  })

  it('stellt bei mehreren Ketten die mit laufender Stufe voran und zählt die übrigen', () => {
    const fertig = kette({
      anforderung: karte(1814),
      stufen: [stufe('PLAN', 'ERREICHT'), stufe('ABDECKUNG', 'ERREICHT')],
      aktuelleStufe: null,
      endeErreicht: true,
    })
    const laufend = kette({
      anforderung: karte(1816),
      plan: karte(1866),
      stufen: [stufe('PLAN', 'ERREICHT'), stufe('REVIEW', 'LAEUFT')],
      aktuelleStufe: 'REVIEW',
    })
    expect(zustandssatz(mitKetten([fertig, laufend]), 'KETTE', true, null, keineZahlen, lage)).toBe(
      'Prüfung zu Plan #1866 + 1 weitere Kette',
    )
    expect(zustandssatz(mitKetten([fertig, laufend, fertig]), 'KETTE', true, null, keineZahlen, lage)).toBe(
      'Prüfung zu Plan #1866 + 2 weitere Ketten',
    )
  })

  it('nennt eine Kette am Ende des Wegs, wenn keine Stufe mehr läuft', () => {
    const fertig = kette({
      anforderung: karte(1814),
      stufen: [stufe('PLAN', 'ERREICHT')],
      aktuelleStufe: null,
      endeErreicht: true,
    })
    expect(zustandssatz(mitKetten([fertig]), 'KETTE', true, null, keineZahlen, lage)).toBe(
      'Kette #1814 am Ende des Wegs',
    )
  })

  it('nennt am abgeschlossenen Run das Ausgangswort des Befunds mit den Paketzahlen', () => {
    expect(
      zustandssatz(null, 'KETTE', false, befund('FAILED'), { gruen: 3, gelb: 1, rot: 2 }, lage),
    ).toBe('nicht gelungen — 3 grün · 1 gelb · 2 rot')
    expect(zustandssatz(null, 'KETTE', false, null, keineZahlen, lage)).toBeNull()
  })

  it('bildet ohne Fortschritt, bei unbekannter Zuordnung oder ohne Kette keinen Satz', () => {
    expect(zustandssatz(null, 'KETTE', true, null, keineZahlen, lage)).toBeNull()
    expect(
      zustandssatz({ ...fortschritt([paket(1, 'IN_UMSETZUNG')]), zuordnung: 'UNBEKANNT' }, 'UMSETZUNGSNACHT', true, null, keineZahlen, lage),
    ).toBeNull()
    expect(zustandssatz(fortschritt([]), 'KETTE', true, null, keineZahlen, lage)).toBeNull()
  })

  it('bildet die Sätze auf Englisch', () => {
    const en = woerterbuch('en').NACHTLAUF_LAGE
    const k = umsetzendeKette([paket(1846, 'IN_UMSETZUNG'), paket(1843, 'FERTIG')])
    expect(zustandssatz(mitKetten([k]), 'KETTE', true, null, keineZahlen, en)).toBe(
      'Implementing package #1846 — 1 of 2 done',
    )
    expect(zustandssatz(null, 'KETTE', false, befund('SUCCEEDED'), { gruen: 1, gelb: 0, rot: 0 }, en)).toBe(
      'succeeded — 1 green · 0 yellow · 0 red',
    )
    const zwei = umsetzendeKette([paket(1, 'IN_UMSETZUNG'), paket(2, 'IN_UMSETZUNG')])
    expect(zustandssatz(mitKetten([zwei]), 'KETTE', true, null, keineZahlen, en)).toBe(
      'Implementing packages #1, #2 — 0 of 2 done',
    )
    expect(zustandssatz(fortschritt([paket(3, 'IN_UMSETZUNG')]), 'UMSETZUNGSNACHT', true, null, keineZahlen, en)).toBe(
      'Implementing package #3 — 0 done',
    )
    expect(
      zustandssatz(fortschritt([paket(3, 'IN_UMSETZUNG'), paket(4, 'IN_UMSETZUNG')]), 'UMSETZUNGSNACHT', true, null, keineZahlen, en),
    ).toBe('Implementing packages #3, #4 — 0 done')
    expect(zustandssatz(fortschritt([]), 'UMSETZUNGSNACHT', true, null, keineZahlen, en)).toBe('Implementation — 0 done')
    const planstufe = kette({ stufen: [stufe('REVIEW', 'LAEUFT')], aktuelleStufe: 'REVIEW' })
    const ohnePlan = kette({ plan: null, stufen: [stufe('PLAN', 'LAEUFT')], aktuelleStufe: 'PLAN' })
    const ende = kette({ anforderung: karte(9), stufen: [], aktuelleStufe: null, endeErreicht: true })
    expect(zustandssatz(mitKetten([planstufe, ende]), 'KETTE', true, null, keineZahlen, en)).toBe(
      'Review for plan #1372 + 1 more chain',
    )
    expect(zustandssatz(mitKetten([ohnePlan, ende, ende]), 'KETTE', true, null, keineZahlen, en)).toBe(
      'Plan for requirement #1364 + 2 more chains',
    )
    expect(zustandssatz(mitKetten([ende]), 'KETTE', true, null, keineZahlen, en)).toBe('Chain #9 at the end of the path')
  })
})

const budget = (teil: Partial<NightRunBudgetView> = {}): NightRunBudgetView => ({
  planMin: 30,
  reviewMin: 60,
  paketeMin: 20,
  abdeckungMin: 10,
  kostenUsd: 20,
  origin: 'CONFIGURED',
  defaultFields: [],
  ...teil,
})

const JETZT = Date.parse('2026-10-21T02:00:00Z')

describe('stufenzeit (Plan #1882 E7)', () => {
  it('rechnet an der laufenden Stufe die verstrichene Zeit gegen die Vorgabe', () => {
    expect(stufenzeit(stufe('REVIEW', 'LAEUFT', '2026-10-21T01:42:00Z'), budget(), JETZT)).toEqual({
      verstrichenMs: 18 * 60_000,
      vorgabeMs: 60 * 60_000,
    })
  })

  it('nimmt für jede Planstufe ihr eigenes Feld der Vorgabe', () => {
    const seit = '2026-10-21T01:00:00Z'
    expect(stufenzeit(stufe('PLAN', 'LAEUFT', seit), budget(), JETZT)?.vorgabeMs).toBe(30 * 60_000)
    expect(stufenzeit(stufe('PAKETE', 'LAEUFT', seit), budget(), JETZT)?.vorgabeMs).toBe(20 * 60_000)
    expect(stufenzeit(stufe('ABDECKUNG', 'LAEUFT', seit), budget(), JETZT)?.vorgabeMs).toBe(10 * 60_000)
  })

  it('liefert an der Umsetzung, ohne Budget oder ohne Feld keine Vorgabe', () => {
    const seit = '2026-10-21T01:00:00Z'
    expect(stufenzeit(stufe('UMSETZUNG', 'LAEUFT', seit), budget(), JETZT)).toEqual({
      verstrichenMs: 60 * 60_000,
      vorgabeMs: null,
    })
    expect(stufenzeit(stufe('PLAN', 'LAEUFT', seit), null, JETZT)?.vorgabeMs).toBeNull()
    expect(stufenzeit(stufe('PLAN', 'LAEUFT', seit), budget({ planMin: null }), JETZT)?.vorgabeMs).toBeNull()
  })

  it('liefert nichts ohne Beginn oder an einer Stufe, die nicht läuft', () => {
    expect(stufenzeit(stufe('PLAN', 'LAEUFT'), budget(), JETZT)).toBeNull()
    expect(stufenzeit(stufe('PLAN', 'ERREICHT', '2026-10-21T01:00:00Z'), budget(), JETZT)).toBeNull()
    expect(stufenzeit(stufe('PLAN', 'OFFEN', '2026-10-21T01:00:00Z'), budget(), JETZT)).toBeNull()
  })

  it('zeigt einen Beginn in der Zukunft als null verstrichen statt negativ', () => {
    expect(stufenzeit(stufe('PLAN', 'LAEUFT', '2026-10-21T02:01:00Z'), budget(), JETZT)?.verstrichenMs).toBe(0)
  })
})

describe('rasterzustand (Plan #1882 E6)', () => {
  it('bildet jeden Paketzustand auf Form und Farbe ab', () => {
    expect(rasterzustand('FERTIG')).toEqual({ form: 'gefuellt', farbe: 'gruen', querstrich: false })
    expect(rasterzustand('IN_UMSETZUNG')).toEqual({ form: 'schraffiert', farbe: 'stahl', querstrich: false })
    expect(rasterzustand('GEZOGEN')).toEqual({ form: 'umriss', farbe: 'stahl', querstrich: false })
    expect(rasterzustand('ANGELEGT')).toEqual({ form: 'umriss', farbe: 'text-schwach', querstrich: false })
    expect(rasterzustand('ZURUECKGESTELLT')).toEqual({ form: 'gefuellt', farbe: 'zinnob', querstrich: true })
  })
})

describe('rasterzaehlung (Issue #1886)', () => {
  it('zählt je vorkommendem Zustand in fester Reihenfolge: fertig, in Umsetzung, gezogen, angelegt, zurückgestellt', () => {
    expect(
      rasterzaehlung([
        paket(1, 'ZURUECKGESTELLT'),
        paket(2, 'ANGELEGT'),
        paket(3, 'GEZOGEN'),
        paket(4, 'IN_UMSETZUNG'),
        paket(5, 'FERTIG'),
        paket(6, 'ANGELEGT'),
      ]),
    ).toEqual([
      { zustand: 'FERTIG', anzahl: 1 },
      { zustand: 'IN_UMSETZUNG', anzahl: 1 },
      { zustand: 'GEZOGEN', anzahl: 1 },
      { zustand: 'ANGELEGT', anzahl: 2 },
      { zustand: 'ZURUECKGESTELLT', anzahl: 1 },
    ])
  })

  it('lässt Zustände ohne Paket weg', () => {
    expect(rasterzaehlung([paket(1, 'ANGELEGT'), paket(2, 'ANGELEGT')])).toEqual([{ zustand: 'ANGELEGT', anzahl: 2 }])
    expect(rasterzaehlung([])).toEqual([])
  })
})

describe('kettenkosten (Plan #1882 E8)', () => {
  const wegA = [stufe('PLAN', 'ERREICHT'), stufe('ABDECKUNG', 'ERREICHT')]
  const wegB = [...wegA, stufe('UMSETZUNG', 'LAEUFT')]
  const items = [
    { cardNumber: 1364, verbrauch: { kostenUsd: 5 } },
    { cardNumber: 1365 },
    { cardNumber: 1366, verbrauch: { kostenUsd: null } },
  ]

  it('stellt an einer A-Kette den Betrag gegen das Budget', () => {
    expect(kettenkosten(karte(1364), wegA, items, budget())).toEqual({ betragUsd: 5, anteil: 0.25, planung: false })
  })

  it('lässt den Anteil über 100 % stehen und ohne Budgetbetrag weg', () => {
    expect(kettenkosten(karte(1364), wegA, items, budget({ kostenUsd: 4 }))?.anteil).toBe(1.25)
    expect(kettenkosten(karte(1364), wegA, items, budget({ kostenUsd: null }))?.anteil).toBeNull()
    expect(kettenkosten(karte(1364), wegA, items, budget({ kostenUsd: 0 }))?.anteil).toBeNull()
    expect(kettenkosten(karte(1364), wegA, items, null)).toEqual({ betragUsd: 5, anteil: null, planung: false })
  })

  it('nennt an einer B-Kette den Betrag als Planung ohne Anteil', () => {
    expect(kettenkosten(karte(1364), wegB, items, budget())).toEqual({ betragUsd: 5, anteil: null, planung: true })
  })

  it('liefert nichts, solange der Kettenvorgang nicht gemeldet ist', () => {
    expect(kettenkosten(karte(1400), wegA, items, budget())).toBeNull()
    expect(kettenkosten(karte(1365), wegA, items, budget())).toBeNull()
    expect(kettenkosten(karte(1366), wegA, items, budget())).toBeNull()
  })
})
