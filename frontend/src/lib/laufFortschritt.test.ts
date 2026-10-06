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
import {
  ENDE_DES_WEGS,
  kettenAnsage,
  kettenAnzeige,
  laeufeZusammenfuehren,
  vorwegZaehlung,
  wegleiste,
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

const stufe = (s: StageProgressView['stufe'], zustand: StageProgressView['zustand']) => ({
  stufe: s,
  zustand,
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
        { stufe: 'PLAN', name: 'Plan', zustand: 'erreicht', aktuell: false },
        { stufe: 'REVIEW', name: 'Prüfung', zustand: 'erreicht', aktuell: false },
        { stufe: 'PAKETE', name: 'Arbeitspakete', zustand: 'erreicht', aktuell: false },
        { stufe: 'ABDECKUNG', name: 'Abdeckung', zustand: 'erreicht', aktuell: false },
      ],
      ende: ENDE_DES_WEGS,
    })
    expect(ENDE_DES_WEGS).toBe('Ende des Wegs erreicht')
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
        { stufe: 'PLAN', name: 'Plan', zustand: 'erreicht', aktuell: false },
        { stufe: 'REVIEW', name: 'Prüfung', zustand: 'erreicht', aktuell: false },
        { stufe: 'PAKETE', name: 'Arbeitspakete', zustand: 'läuft', aktuell: true },
        { stufe: 'ABDECKUNG', name: 'Abdeckung', zustand: 'offen', aktuell: false },
        { stufe: 'UMSETZUNG', name: 'Umsetzung', zustand: 'offen', aktuell: false },
      ],
      ende: null,
    })
  })

  it('zeigt bei der Umsetzungsnacht nur die Umsetzung als laufende Stufe', () => {
    expect(wegleiste(null, 'UMSETZUNGSNACHT')).toEqual({
      stufen: [{ stufe: 'UMSETZUNG', name: 'Umsetzung', zustand: 'läuft', aktuell: true }],
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
