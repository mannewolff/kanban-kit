import { ThemeProvider } from '@mui/material/styles'
import { fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  CardRefView,
  ChainProgressView,
  NightRunBudgetView,
  NightRunProgressView,
  PackageProgressView,
} from '../../api/nightRuns'
import type { FortschrittArt, Kostenvorgang } from '../../lib/laufFortschritt'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { nachtlaufTheme } from '../../nachtlaufDesign'
import { TEXT_MATT } from '../../theme'
import { NachtlaufFortschritt } from './NachtlaufFortschritt'

/**
 * Der Fortschritt eines Laufs in der aufgeklappten Kachel (Issue #1377, Plan #1372 E12; seit
 * Issue #1886 als Kettenzeilen nach Plan #1882 E6–E8): Stufenleiste und Paketraster je Kette,
 * Kartennummern als Verweise, „unbekannt" und der Hinweis auf eine offene Frage. Jeder Zustand wird
 * über seinen zugänglichen Namen gefunden.
 */

/** Achtzehn Minuten nach dem Beginn der laufenden Prüfung der Beispielkette. */
const JETZT = '2026-10-21T01:18:00Z'

const budget = (teil: Partial<NightRunBudgetView> = {}): NightRunBudgetView => ({
  planMin: 60,
  reviewMin: 60,
  paketeMin: 30,
  abdeckungMin: 15,
  kostenUsd: 50,
  origin: 'CONFIGURED',
  defaultFields: [],
  ...teil,
})

const ERREICHT_BIS_ABDECKUNG: ChainProgressView['stufen'] = [
  { stufe: 'PLAN', zustand: 'ERREICHT', seit: null },
  { stufe: 'REVIEW', zustand: 'ERREICHT', seit: null },
  { stufe: 'PAKETE', zustand: 'ERREICHT', seit: null },
  { stufe: 'ABDECKUNG', zustand: 'ERREICHT', seit: null },
]

const karte = (number: number, title = `Karte ${number}`): CardRefView => ({
  number,
  title,
  boardId: 7,
})

const paket = (number: number, zustand: PackageProgressView['zustand']): PackageProgressView => ({
  karte: karte(number),
  zustand,
})

const kette = (teil: Partial<ChainProgressView> = {}): ChainProgressView => ({
  anforderung: karte(100, 'Die Anforderung'),
  plan: karte(101, 'Der Plan'),
  pakete: [],
  stufen: [
    { stufe: 'PLAN', zustand: 'ERREICHT', seit: null },
    { stufe: 'REVIEW', zustand: 'LAEUFT', seit: '2026-10-21T01:00:00Z' },
    { stufe: 'PAKETE', zustand: 'OFFEN', seit: null },
    { stufe: 'ABDECKUNG', zustand: 'OFFEN', seit: null },
  ],
  aktuelleStufe: 'REVIEW',
  endeErreicht: false,
  ...teil,
})

const fortschritt = (teil: Partial<NightRunProgressView> = {}): NightRunProgressView => ({
  zuordnung: 'OK',
  ketten: [],
  pakete: [],
  unbekannt: [],
  unbekanntOhneAusweis: false,
  offeneFragen: [],
  ...teil,
})

const zeige = (
  f: NightRunProgressView,
  {
    art = 'KETTE',
    laeuft = true,
    budget: b = budget(),
    vorgaenge = [],
  }: {
    art?: FortschrittArt
    laeuft?: boolean
    budget?: NightRunBudgetView | null
    vorgaenge?: readonly Kostenvorgang[]
  } = {},
) => {
  const onKarteOeffnen = vi.fn()
  const { container } = render(
    <ThemeProvider theme={nachtlaufTheme}>
      <NachtlaufFortschritt
        fortschritt={f}
        art={art}
        laeuft={laeuft}
        budget={b}
        vorgaenge={vorgaenge}
        onKarteOeffnen={onKarteOeffnen}
      />
    </ThemeProvider>,
  )
  return { onKarteOeffnen, container }
}

const stufenliste = () => within(screen.getByRole('list', { name: 'Weg der Kette #100' }))

/** Die Stufenpunkte einer Liste — die Bilder mit Zustand, ohne die Zeitschiene. */
const punktbilder = (liste: HTMLElement): HTMLElement[] =>
  within(liste)
    .getAllByRole('img')
    .filter((p) => p.dataset.zustand !== undefined)

/** Die Stufenpunkte einer Liste über ihre zugänglichen Namen. */
const punkte = (liste: HTMLElement) => punktbilder(liste).map((p) => p.getAttribute('aria-label'))

const wegDerKette = (name = 'Weg der Kette #100') => screen.getByRole('list', { name })

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(JETZT))
})

afterEach(() => {
  vi.useRealTimers()
})

describe('NachtlaufFortschritt', () => {
  it('zeigt die Stufenleiste einer Kette mit jedem Zustand als zugänglichem Namen', () => {
    zeige(fortschritt({ ketten: [kette()] }))

    expect(punkte(wegDerKette())).toEqual([
      'Plan: erreicht',
      'Prüfung: läuft',
      'Arbeitspakete: offen',
      'Abdeckung: offen',
    ])
    expect(punktbilder(wegDerKette()).map((p) => p.dataset.zustand)).toEqual([
      'ERREICHT',
      'LAEUFT',
      'OFFEN',
      'OFFEN',
    ])
  })

  it('zeigt den Zustand eines Stufenpunkts als Tooltip', async () => {
    zeige(fortschritt({ ketten: [kette()] }))

    fireEvent.mouseOver(stufenliste().getByRole('img', { name: 'Arbeitspakete: offen' }))

    expect(await screen.findByRole('tooltip')).toHaveTextContent('Arbeitspakete: offen')
  })

  it('nennt jede Stufe sichtbar beim Namen (#1902)', () => {
    zeige(fortschritt({ ketten: [kette()] }))

    const stufen = stufenliste().getAllByRole('listitem')
    expect(stufen).toHaveLength(4)
    ;['Plan', 'Prüfung', 'Arbeitspakete', 'Abdeckung'].forEach((name, i) => {
      expect(within(stufen[i]).getByText(name)).toBeVisible()
    })
  })

  it('hebt den Namen der laufenden Stufe hervor, die übrigen stehen matt (#1902)', () => {
    zeige(fortschritt({ ketten: [kette()] }))

    const stufen = stufenliste().getAllByRole('listitem')
    expect(within(stufen[1]).getByText('Prüfung')).toHaveStyle({ fontWeight: 600 })
    for (const [i, name] of [[0, 'Plan'], [2, 'Arbeitspakete'], [3, 'Abdeckung']] as const) {
      const text = within(stufen[i]).getByText(name)
      expect(text).toHaveStyle({ color: TEXT_MATT, fontWeight: 400 })
      expect(text).not.toHaveStyle({ fontWeight: 600 })
    }
  })

  it('lässt die Stufenleiste umbrechen, statt einen Namen abzuschneiden (#1902)', () => {
    zeige(fortschritt({ ketten: [kette()] }))

    expect(screen.getByRole('list', { name: 'Weg der Kette #100' })).toHaveStyle({ flexWrap: 'wrap' })
    for (const name of ['Plan', 'Prüfung', 'Arbeitspakete', 'Abdeckung']) {
      const text = stufenliste().getByText(name)
      expect(text).toHaveStyle({ whiteSpace: 'nowrap' })
      expect(text).not.toHaveStyle({ overflow: 'hidden' })
      expect(text).not.toHaveStyle({ textOverflow: 'ellipsis' })
    }
  })

  it('zeigt an der laufenden Stufe die Zeit gegen ihre Vorgabe', () => {
    zeige(fortschritt({ ketten: [kette()] }))

    const laufend = stufenliste().getAllByRole('listitem')[1]
    expect(laufend).toHaveTextContent('18 / 60 min')
    expect(within(laufend).getByRole('img', { name: '18 von 60 Minuten verstrichen' })).toBeInTheDocument()
    expect(screen.getAllByText(/ min$/)).toHaveLength(1)
  })

  it('lässt die Schiene über der Vorgabe voll und nennt die Überschreitung', () => {
    vi.setSystemTime(new Date('2026-10-21T02:15:00Z'))
    zeige(fortschritt({ ketten: [kette()] }))

    const laufend = stufenliste().getAllByRole('listitem')[1]
    expect(laufend).toHaveTextContent('75 / 60 min · 15 min über der Vorgabe')
    expect(screen.getByTestId('zeit-fuellung')).toHaveStyle({ width: '100%' })
  })

  it('zeigt ohne Vorgabe keinen Zeitbalken, nur „seit <Dauer>"', () => {
    zeige(fortschritt({ ketten: [kette()] }), { budget: budget({ reviewMin: null }) })
    expect(screen.queryByRole('img', { name: /Minuten verstrichen/ })).not.toBeInTheDocument()
    expect(stufenliste().getAllByRole('listitem')[1]).toHaveTextContent('seit 18 min')
  })

  it('zeigt ohne Beginn der Stufe keine Zeit', () => {
    zeige(
      fortschritt({
        ketten: [
          kette({
            stufen: [
              { stufe: 'PLAN', zustand: 'ERREICHT', seit: null },
              { stufe: 'REVIEW', zustand: 'LAEUFT', seit: null },
            ],
          }),
        ],
      }),
    )
    expect(screen.queryByText(/min/)).not.toBeInTheDocument()
  })

  it('zeigt die Titel der Anforderung sichtbar', () => {
    zeige(fortschritt({ ketten: [kette()] }))

    expect(screen.getByTestId('fortschritt-kette-100')).toHaveTextContent('Die Anforderung')
  })

  it('markiert allein die aktuelle Stufe mit aria-current="step"', () => {
    zeige(fortschritt({ ketten: [kette()] }))

    const stufen = stufenliste().getAllByRole('listitem')
    expect(stufen.map((s) => s.getAttribute('aria-current'))).toEqual([null, 'step', null, null])
    expect(within(stufen[1]).getByRole('img', { name: 'Prüfung: läuft' })).toBeInTheDocument()
  })

  it('zeigt die Umsetzung als Stufe, wenn die Kette sie trägt', () => {
    zeige(
      fortschritt({
        ketten: [
          kette({
            stufen: [
              { stufe: 'PLAN', zustand: 'ERREICHT', seit: null },
              { stufe: 'REVIEW', zustand: 'ERREICHT', seit: null },
              { stufe: 'PAKETE', zustand: 'ERREICHT', seit: null },
              { stufe: 'ABDECKUNG', zustand: 'ERREICHT', seit: null },
              { stufe: 'UMSETZUNG', zustand: 'LAEUFT', seit: '2026-10-21T01:00:00Z' },
            ],
            aktuelleStufe: 'UMSETZUNG',
          }),
        ],
      }),
    )

    const stufen = stufenliste().getAllByRole('listitem')
    expect(within(stufen[4]).getByRole('img', { name: 'Umsetzung: läuft' })).toBeInTheDocument()
    expect(stufen[4]).toHaveAttribute('aria-current', 'step')
    // Die Umsetzung hat keine Vorgabe: nur „seit <Dauer>", keine Schiene (Plan #1882 E7).
    expect(stufen[4]).toHaveTextContent('seit 18 min')
    expect(screen.queryByRole('img', { name: /Minuten verstrichen/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('img', { name: 'Ende des Wegs erreicht' })).not.toBeInTheDocument()
  })

  it('zeigt in Variante A nach der Abdeckung „Ende des Wegs erreicht" statt einer offenen Umsetzung', () => {
    zeige(
      fortschritt({
        ketten: [
          kette({
            stufen: ERREICHT_BIS_ABDECKUNG,
            aktuelleStufe: null,
            endeErreicht: true,
          }),
        ],
      }),
    )

    const zeile = within(screen.getByTestId('fortschritt-kette-100'))
    expect(zeile.getByRole('img', { name: 'Ende des Wegs erreicht' })).toHaveTextContent('Ende des Wegs')
    expect(punkte(wegDerKette())).toEqual([
      'Plan: erreicht',
      'Prüfung: erreicht',
      'Arbeitspakete: erreicht',
      'Abdeckung: erreicht',
    ])
    expect(screen.queryByText(/Umsetzung/)).not.toBeInTheDocument()
    expect(
      stufenliste()
        .getAllByRole('listitem')
        .filter((s) => s.hasAttribute('aria-current')),
    ).toHaveLength(0)
  })

  it('nennt Anforderung, Plan und Pakete mit Nummer und Paketzustand als Text', () => {
    zeige(
      fortschritt({
        ketten: [
          kette({
            pakete: [
              paket(201, 'ANGELEGT'),
              paket(202, 'GEZOGEN'),
              paket(203, 'IN_UMSETZUNG'),
              paket(204, 'FERTIG'),
              paket(205, 'ZURUECKGESTELLT'),
            ],
          }),
        ],
      }),
    )

    expect(screen.getByRole('button', { name: 'Anforderung #100 Die Anforderung' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Plan #101 Der Plan' })).toBeVisible()
    const pakete = within(screen.getByRole('list', { name: 'Pakete der Kette #100' }))
    expect(pakete.getAllByRole('button').map((p) => p.getAttribute('aria-label'))).toEqual([
      'Paket #201, angelegt: Karte 201',
      'Paket #202, gezogen: Karte 202',
      'Paket #203, in Umsetzung: Karte 203',
      'Paket #204, fertig: Karte 204',
      'Paket #205, zurückgestellt: Karte 205',
    ])
  })

  it('benennt einen fehlenden Plan und fehlende Pakete, statt sie wegzulassen', () => {
    zeige(fortschritt({ ketten: [kette({ plan: null, pakete: [] })] }))

    const block = screen.getByTestId('fortschritt-kette-100')
    expect(block).toHaveTextContent('noch kein Plan')
    expect(block).toHaveTextContent('noch keine Pakete')
    expect(within(block).queryByRole('button', { name: /^Plan / })).not.toBeInTheDocument()
  })

  it('ruft beim Klick auf eine Nummer onKarteOeffnen mit der Nummer', () => {
    const { onKarteOeffnen } = zeige(
      fortschritt({ ketten: [kette({ pakete: [paket(203, 'IN_UMSETZUNG')] })] }),
    )

    fireEvent.click(screen.getByRole('button', { name: 'Anforderung #100 Die Anforderung' }))
    fireEvent.click(screen.getByRole('button', { name: 'Plan #101 Der Plan' }))
    fireEvent.click(screen.getByRole('button', { name: 'Paket #203, in Umsetzung: Karte 203' }))

    expect(onKarteOeffnen.mock.calls).toEqual([[100], [101], [203]])
  })

  it('stellt an einer Kette ohne Umsetzung die Kosten mit Balken gegen das Budget', () => {
    zeige(
      fortschritt({ ketten: [kette({ stufen: ERREICHT_BIS_ABDECKUNG, aktuelleStufe: null, endeErreicht: true })] }),
      { vorgaenge: [{ cardNumber: 100, verbrauch: { kostenUsd: 17.5 } }] },
    )

    const kosten = screen.getByRole('group', { name: 'Kosten der Kette' })
    expect(kosten).toHaveTextContent('17,50 / 50,00 $')
    expect(within(kosten).getByTestId('kosten-fuellung')).toHaveStyle({ width: '35%' })
  })

  it('lässt den Kostenbalken über dem Budget voll', () => {
    zeige(fortschritt({ ketten: [kette()] }), { vorgaenge: [{ cardNumber: 100, verbrauch: { kostenUsd: 75 } }] })

    expect(screen.getByTestId('kosten-fuellung')).toHaveStyle({ width: '100%' })
  })

  it('nennt an einer Kette mit Umsetzung die Planung ohne Balken', () => {
    zeige(
      fortschritt({
        ketten: [
          kette({
            stufen: [...ERREICHT_BIS_ABDECKUNG, { stufe: 'UMSETZUNG', zustand: 'LAEUFT', seit: null }],
            aktuelleStufe: 'UMSETZUNG',
          }),
        ],
      }),
      { vorgaenge: [{ cardNumber: 100, verbrauch: { kostenUsd: 7.28 } }] },
    )

    const kosten = screen.getByRole('group', { name: 'Kosten der Kette' })
    expect(kosten).toHaveTextContent('Planung 7,28 $')
    expect(within(kosten).queryByTestId('kosten-fuellung')).not.toBeInTheDocument()
  })

  it('nennt ohne Budget nur den Betrag', () => {
    zeige(fortschritt({ ketten: [kette()] }), {
      budget: null,
      vorgaenge: [{ cardNumber: 100, verbrauch: { kostenUsd: 2.5 } }],
    })

    expect(screen.getByRole('group', { name: 'Kosten der Kette' })).toHaveTextContent(/^2,50 \$$/)
    expect(screen.queryByTestId('kosten-fuellung')).not.toBeInTheDocument()
  })

  it('zeigt keine Kosten, solange der Kettenvorgang nicht gemeldet ist', () => {
    zeige(fortschritt({ ketten: [kette()] }), { vorgaenge: [{ cardNumber: 999, verbrauch: { kostenUsd: 4 } }] })

    expect(screen.queryByRole('group', { name: 'Kosten der Kette' })).not.toBeInTheDocument()
    expect(screen.getByTestId('fortschritt-kette-100')).not.toHaveTextContent('$')
  })

  it('zeigt mehrere Ketten je mit eigener Wegleiste', () => {
    zeige(
      fortschritt({
        ketten: [kette(), kette({ anforderung: karte(300, 'Zweite'), plan: null })],
      }),
    )

    expect(screen.getByRole('list', { name: 'Weg der Kette #100' })).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Weg der Kette #300' })).toBeInTheDocument()
  })

  it('zeigt in der Umsetzungsnacht nur die Umsetzung und das Raster', () => {
    zeige(
      fortschritt({ pakete: [paket(401, 'IN_UMSETZUNG'), paket(402, 'FERTIG')] }),
      { art: 'UMSETZUNGSNACHT', vorgaenge: [{ cardNumber: 401, verbrauch: { kostenUsd: 3 } }] },
    )

    const weg = within(screen.getByRole('list', { name: 'Weg der Umsetzungsnacht' }))
    const stufen = weg.getAllByRole('listitem')
    expect(punkte(wegDerKette('Weg der Umsetzungsnacht'))).toEqual(['Umsetzung: läuft'])
    expect(stufen).toHaveLength(1)
    expect(within(stufen[0]).getByText('Umsetzung')).toBeVisible()
    expect(stufen[0]).toHaveAttribute('aria-current', 'step')
    expect(screen.queryByText(/Plan|Prüfung|Abdeckung|Anforderung/)).not.toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'Kosten der Kette' })).not.toBeInTheDocument()
    const pakete = within(screen.getByRole('list', { name: 'Pakete der Umsetzungsnacht' }))
    expect(pakete.getAllByRole('button').map((p) => p.getAttribute('aria-label'))).toEqual([
      'Paket #401, in Umsetzung: Karte 401',
      'Paket #402, fertig: Karte 402',
    ])
  })

  it('benennt eine Umsetzungsnacht ohne Pakete', () => {
    zeige(fortschritt(), { art: 'UMSETZUNGSNACHT' })

    expect(screen.getByTestId('fortschritt-umsetzungsnacht')).toHaveTextContent('noch keine Pakete')
  })

  it('listet nicht zuordenbare Karten unter „unbekannt" als Verweise', () => {
    const { onKarteOeffnen } = zeige(
      fortschritt({ ketten: [kette()], unbekannt: [karte(501), karte(502)] }),
    )

    const unbekannt = within(screen.getByRole('list', { name: 'unbekannt' }))
    expect(unbekannt.getAllByRole('button').map((b) => b.textContent)).toEqual([
      'unbekannt#501',
      'unbekannt#502',
    ])
    fireEvent.click(unbekannt.getByRole('button', { name: 'unbekannt #502 Karte 502' }))
    expect(onKarteOeffnen).toHaveBeenCalledWith(502)
  })

  it('lässt den Abschnitt „unbekannt" weg, wenn jede Karte zugeordnet ist', () => {
    zeige(fortschritt({ ketten: [kette()] }))

    expect(screen.queryByRole('list', { name: 'unbekannt' })).not.toBeInTheDocument()
  })

  it('zeigt bei unbekannter Zuordnung einen Hinweis statt der Wegleiste', () => {
    zeige(fortschritt({ zuordnung: 'UNBEKANNT', ketten: [kette()], unbekannt: [karte(601)] }))

    expect(screen.getByTestId('fortschritt-zuordnung-unbekannt')).toHaveTextContent(
      'Welche Karten zu diesem Lauf gehören, lässt sich nicht feststellen',
    )
    expect(screen.queryByRole('list', { name: /^Weg der/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('list', { name: /^Pakete der/ })).not.toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'unbekannt' })).toBeInTheDocument()
  })

  it('erklärt „unbekannt" bei einem Lauf ohne Ausweis', () => {
    zeige(fortschritt({ ketten: [kette()], unbekannt: [karte(601)], unbekanntOhneAusweis: true }))

    expect(screen.getByTestId('fortschritt-unbekannt-ohne-ausweis')).toHaveTextContent(
      'Dieser Lauf hat sich nicht ausgewiesen (älterer Runner).',
    )
  })

  it('zeigt den Satz zum Ausweis nicht ohne das Flag', () => {
    zeige(fortschritt({ ketten: [kette()], unbekannt: [karte(601)] }))

    expect(screen.queryByTestId('fortschritt-unbekannt-ohne-ausweis')).not.toBeInTheDocument()
  })

  it('zeigt den Satz zum Ausweis nicht bei unbekannter Zuordnung', () => {
    zeige(
      fortschritt({
        zuordnung: 'UNBEKANNT',
        ketten: [kette()],
        unbekannt: [karte(601)],
        unbekanntOhneAusweis: true,
      }),
    )

    expect(screen.queryByTestId('fortschritt-unbekannt-ohne-ausweis')).not.toBeInTheDocument()
  })

  it('zeigt den Hinweis auf eine offene Frage mit der Karte als Status', () => {
    const { onKarteOeffnen } = zeige(
      fortschritt({ ketten: [kette()], offeneFragen: [karte(701, 'Die Frage')] }),
    )

    const status = screen.getByRole('status')
    expect(status).toHaveTextContent('Offene Frage an den Menschen')
    fireEvent.click(within(status).getByRole('button', { name: 'Frage #701 Die Frage' }))
    expect(onKarteOeffnen).toHaveBeenCalledWith(701)
  })

  it('zeigt ohne offene Frage keinen Status', () => {
    zeige(fortschritt({ ketten: [kette()] }))

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('zeigt bei abgeschlossenem Lauf nur noch den Frage-Hinweis', () => {
    zeige(
      fortschritt({
        ketten: [kette()],
        unbekannt: [karte(501)],
        offeneFragen: [karte(701, 'Die Frage')],
      }),
      { laeuft: false },
    )

    expect(screen.getByRole('status')).toHaveTextContent('#701')
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Anforderung/ })).not.toBeInTheDocument()
  })

  it('rendert bei abgeschlossenem Lauf ohne offene Frage nichts', () => {
    const { container } = zeige(fortschritt({ ketten: [kette()] }), { laeuft: false })

    expect(container).toBeEmptyDOMElement()
  })
})

describe('NachtlaufFortschritt auf Englisch (Issue #1658)', () => {
  const zeigeEnglisch = (f: NightRunProgressView, art: FortschrittArt = 'KETTE') =>
    renderMitSprache(
      <ThemeProvider theme={nachtlaufTheme}>
        <NachtlaufFortschritt
          fortschritt={f}
          art={art}
          laeuft
          budget={budget()}
          vorgaenge={[{ cardNumber: 100, verbrauch: { kostenUsd: 7.5 } }]}
          onKarteOeffnen={vi.fn()}
        />
      </ThemeProvider>,
      'en',
    )

  it('zeigt Weg, Verweise und Paketzustände einer Kette auf Englisch', () => {
    zeigeEnglisch(fortschritt({ ketten: [kette({ pakete: [paket(203, 'IN_UMSETZUNG'), paket(204, 'ZURUECKGESTELLT')] })] }))

    const weg = within(screen.getByRole('list', { name: 'Path of chain #100' }))
    expect(punkte(wegDerKette('Path of chain #100'))).toEqual(['Plan: reached', 'Review: running', 'Work packages: open', 'Coverage: open'])
    expect(weg.getByRole('img', { name: '18 of 60 minutes elapsed' })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Cost of the chain' })).toHaveTextContent('7.50 / 50.00 $')
    expect(screen.getByRole('button', { name: 'Requirement #100 Die Anforderung' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Plan #101 Der Plan' })).toBeInTheDocument()
    const pakete = screen.getByRole('list', { name: 'Packages of chain #100' })
    expect(within(pakete).getByRole('button', { name: 'Package #203, in implementation: Karte 203' })).toBeInTheDocument()
    expect(within(pakete).getByRole('button', { name: 'Package #204, deferred: Karte 204' })).toBeInTheDocument()
  })

  it('nennt Überschreitung, „seit" und die Planung auf Englisch', () => {
    vi.setSystemTime(new Date('2026-10-21T02:15:00Z'))
    zeigeEnglisch(
      fortschritt({
        ketten: [
          kette(),
          kette({
            anforderung: karte(300, 'Zweite'),
            stufen: [...ERREICHT_BIS_ABDECKUNG, { stufe: 'UMSETZUNG', zustand: 'LAEUFT', seit: '2026-10-21T01:31:00Z' }],
            aktuelleStufe: 'UMSETZUNG',
          }),
        ],
      }),
    )

    expect(screen.getByRole('list', { name: 'Path of chain #100' })).toHaveTextContent('75 / 60 min · 15 min over the limit')
    expect(screen.getByRole('list', { name: 'Path of chain #300' })).toHaveTextContent('for 44 min')
    expect(within(screen.getByTestId('fortschritt-kette-100')).getByRole('group', { name: 'Cost of the chain' })).toHaveTextContent(
      '7.50 / 50.00 $',
    )
  })

  it('nennt fehlenden Plan, fehlende Pakete, Unbekanntes und die offene Frage auf Englisch', () => {
    zeigeEnglisch(
      fortschritt({
        ketten: [kette({ plan: null, pakete: [] })],
        unbekannt: [karte(400)],
        unbekanntOhneAusweis: true,
        offeneFragen: [karte(500)],
      }),
    )

    expect(screen.getByText('no plan yet')).toBeInTheDocument()
    expect(screen.getByText('no packages yet')).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'unknown' })).toBeInTheDocument()
    expect(screen.getByTestId('fortschritt-unbekannt-ohne-ausweis')).toHaveTextContent(
      'This run did not identify itself (older runner).',
    )
    expect(screen.getByTestId('fortschritt-frage')).toHaveTextContent('Open question for a person on card')
    expect(screen.getByRole('button', { name: 'Question #500 Karte 500' })).toBeInTheDocument()
  })

  it('zeigt die Umsetzungsnacht und eine unbekannte Zuordnung auf Englisch', () => {
    zeigeEnglisch(fortschritt({ pakete: [paket(203, 'FERTIG')] }), 'UMSETZUNGSNACHT')
    expect(screen.getByRole('list', { name: 'Path of the implementation night' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Package #203, done: Karte 203' })).toBeInTheDocument()

    zeigeEnglisch(fortschritt({ zuordnung: 'UNBEKANNT' }))
    expect(screen.getByTestId('fortschritt-zuordnung-unbekannt')).toHaveTextContent(
      'Which cards belong to this run cannot be determined — the progress remains unknown.',
    )
  })
})
