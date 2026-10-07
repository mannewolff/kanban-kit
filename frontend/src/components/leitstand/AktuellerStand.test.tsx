import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DisruptionView, LaufPaketeView, PaketView } from '../../api/plattformLeitstand'
import { AktuellerStand, karteSchluessel } from './AktuellerStand'

const laufend = (extra: Partial<DisruptionView> = {}): DisruptionView => ({
  nightRunId: 8,
  projectId: 9,
  projectName: 'Mein Projekt',
  mode: 'CHAIN',
  startedAt: '2026-09-21T01:10:00Z',
  outcome: { abortReason: null, verdict: 'RUNNING', decisiveItem: null, noWorkReason: null },
  ...extra,
})

const paket = (extra: Partial<PaketView> = {}): PaketView => ({
  cardNumber: 721,
  title: 'Erstes Paket',
  state: 'GREEN',
  errorClass: null,
  cardExists: true,
  durationMs: null,
  ...extra,
})

const pakete = (nightRunId: number, liste: PaketView[]): LaufPaketeView => ({
  nightRunId,
  pakete: liste,
})

/**
 * Der Kopf eines laufenden Runs in „Aktueller Status" (Issue #1193).
 *
 * Seit die zweite Platte über dieselbe Liste `laufende` entfallen ist, steht alles, was sie als
 * einzige trug, hier: der **Verweis** auf den Lauf, das **Zustandswort** und die **Startzeit**.
 * Geprüft wird deshalb genau
 * das — dass der Kopf diese drei Angaben führt, auch an einem Lauf, der noch nichts gemeldet hat.
 *
 * <p>Das Wort ist keine Zierde: Nach Kriterium 3 der Quelle #1064 darf der Zustand weder allein an
 * einer Farbe noch allein an der Bewegung hängen. Wer `prefers-reduced-motion` gesetzt hat — das
 * Theme hält den Puls dann an — und Farben nicht unterscheidet, läse sonst nirgends, dass dieser
 * Run arbeitet.
 *
 * <p>Die Sektion als ganze ist über `PlattformLeitstandPage.test.tsx` geprüft; hier steht nur der
 * Kopf, weil seine Angaben an der Komponente hängen und nicht an der Seite.
 */
describe('AktuellerStand — Kopf eines laufenden Runs (#1193)', () => {
  // Start 01:10:00Z, jetzt 02:33:22Z — der Lauf ist 01:23:22 alt (#1246). Nur Uhr und Intervall
  // werden vorgetäuscht; userEvent braucht den echten setTimeout.
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] })
    vi.setSystemTime(new Date('2026-09-21T02:33:22Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  const zeige = (
    laufende: DisruptionView[],
    gemeldetePakete: LaufPaketeView[],
    onAlsBeendetKennzeichnen: (lauf: DisruptionView) => void = () => {},
  ) =>
    render(
      <AktuellerStand
        laufende={laufende}
        gemeldetePakete={gemeldetePakete}
        verschwunden={new Set()}
        onKarteOeffnen={() => {}}
        onAlsBeendetKennzeichnen={onAlsBeendetKennzeichnen}
      />,
      { wrapper: MemoryRouter },
    )

  /** Der Weg zur Auswertung genau dieses Laufs — gestaltet und benannt wie der Verweis der Zeilen. */
  it('führt die Kennung als Verweis in die Lauf-Ansicht', () => {
    zeige([laufend()], [pakete(8, [paket()])])

    const kopf = screen.getByTestId('stand-kopf-8')
    const verweis = within(kopf).getByRole('link', { name: 'Run #8 von Mein Projekt' })
    expect(verweis).toHaveTextContent('Run #8')
    expect(verweis).toHaveAttribute('href', '/projects/9/nachtlauf?lauf=8')
  })

  /** Kriterium 3: der Zustand als Wort, dazu die Startzeit im Format des Laufbands. */
  it('nennt Zustandswort, Startzeit und Stand', () => {
    zeige([laufend()], [pakete(8, [paket()])])

    expect(screen.getByTestId('stand-kopf-8')).toHaveTextContent(
      'Run #8 · gestartet 03:10 · läuft seit 01:23:22 · 1 gemeldet · 1 Erfolg',
    )
  })

  /**
   * Ein Lauf, der noch nichts gemeldet hat, steht mit „0 gemeldet" da — und trägt Verweis, Wort
   * und Zeit genauso. Genau ihn zeigte vorher die entfallene zweite Platte vollständig — hier
   * stand er nur mit „0 gemeldet" da.
   */
  it('zeigt Verweis, Wort und Zeit auch an einem Lauf ohne gemeldetes Paket', () => {
    zeige([laufend({ nightRunId: 6 })], [pakete(6, [])])

    const kopf = screen.getByTestId('stand-kopf-6')
    expect(within(kopf).getByRole('link', { name: 'Run #6 von Mein Projekt' })).toHaveAttribute(
      'href',
      '/projects/9/nachtlauf?lauf=6',
    )
    expect(kopf).toHaveTextContent('Run #6 · gestartet 03:10 · läuft seit 01:23:22 · 0 gemeldet')
    expect(within(screen.getByTestId('stand-lauf-6')).queryAllByTestId(/^stand-paket-/)).toHaveLength(
      0,
    )
  })

  /**
   * Der Name der Paketliste ist der Kopf — er enthält damit Kennung, Zustand, Zeit und Stand.
   * Ohne ihn sagte ein Vorlesewerkzeug nur „Liste mit einem Eintrag".
   */
  it('benennt die Paketliste mit Kennung, Zustand, Zeit und Stand', () => {
    zeige([laufend()], [pakete(8, [paket()])])

    expect(screen.getByRole('list')).toHaveAccessibleName(
      /^Run #8 von Mein Projekt · gestartet 03:10 · läuft · 1 gemeldet · 1 Erfolg$/,
    )
  })

  /** Der Melder pulsiert weiter am laufenden Run — Farbe und Bewegung kommen aus dem Befund. */
  it('lässt den Melder des laufenden Runs weiter pulsieren', () => {
    zeige([laufend()], [pakete(8, [paket()])])

    const led = within(screen.getByTestId('stand-kopf-8')).getByTestId('led-stahl')
    expect(led).toHaveAttribute('data-puls', 'an')
  })

  /**
   * Issue #1197: Die Schaltfläche, mit der ein Plattform-Admin einen hängenden Run wegräumt.
   *
   * Ihr Name nennt die Run-Nummer: In einer Sektion mit mehreren laufenden Runs wären zwei
   * gleichlautende Tasten für ein Vorlesewerkzeug nicht zu unterscheiden.
   */
  it('gibt jedem laufenden Run eine Taste, die seine Nummer nennt', async () => {
    const gemeldet: number[] = []
    zeige(
      [laufend(), laufend({ nightRunId: 6 })],
      [pakete(8, [paket()]), pakete(6, [])],
      (lauf) => gemeldet.push(lauf.nightRunId),
    )

    await userEvent.click(
      screen.getByRole('button', { name: 'Run #6 als beendet kennzeichnen' }),
    )

    expect(gemeldet).toEqual([6])
    expect(
      screen.getByRole('button', { name: 'Run #8 als beendet kennzeichnen' }),
    ).toBeInTheDocument()
    })

  /**
   * Issue #1246: Die Laufzeit steht sekundengenau im Kopf und zählt ohne Server-Abruf hoch — damit
   * das Zeitverhalten aller laufenden Runs auf einen Blick zu sehen ist.
   */
  it('zählt die Laufzeit eines laufenden Runs jede Sekunde weiter', () => {
    zeige([laufend()], [pakete(8, [paket()])])

    expect(screen.getByTestId('stand-laufzeit-8')).toHaveTextContent('01:23:22')

    act(() => {
      vi.advanceTimersByTime(1000)
    })

    expect(screen.getByTestId('stand-laufzeit-8')).toHaveTextContent('01:23:23')
    expect(screen.getByTestId('stand-kopf-8')).toHaveTextContent(
      'Run #8 · gestartet 03:10 · läuft seit 01:23:23 · 1 gemeldet · 1 Erfolg',
    )
  })

  /**
   * Eine hochzählende Uhr an einem Lauf, der nicht mehr arbeitet, behauptete Arbeit — ein solcher
   * Eintrag nennt Startzeit und Zustandswort, aber keine Laufzeit, und legt keinen Takt an.
   */
  it('zeigt an einem nicht laufenden Run keine Laufzeit und legt keinen Takt an', () => {
    zeige(
      [
        laufend({
          outcome: { abortReason: null, verdict: 'CLOSED', decisiveItem: null, noWorkReason: null },
        }),
      ],
      [pakete(8, [paket()])],
    )

    expect(screen.getByTestId('stand-kopf-8')).toHaveTextContent(
      'Run #8 · gestartet 03:10 · von Hand beendet · 1 gemeldet · 1 Erfolg',
    )
    expect(screen.queryByTestId('stand-laufzeit-8')).not.toBeInTheDocument()
    expect(vi.getTimerCount()).toBe(0)
  })

  /**
   * Der Lauf, der nicht anlief (Issue #1502, Plan #1498 E11): Der Kopf trägt Wort und braune LED
   * aus dem Befund — den Grund nicht, der steht unter „Beendete Runs" derselben Seite.
   */
  it('zeigt am nicht angelaufenen Run das Wort „nicht angelaufen" mit brauner LED', () => {
    zeige(
      [
        laufend({
          outcome: {
            abortReason: 'Working Tree ist nicht sauber.',
            verdict: 'NOT_STARTED',
            decisiveItem: null,
            noWorkReason: null,
          },
        }),
      ],
      [pakete(8, [])],
    )

    const kopf = screen.getByTestId('stand-kopf-8')
    expect(kopf).toHaveTextContent('Run #8 · gestartet 03:10 · nicht angelaufen · 0 gemeldet')
    expect(within(kopf).getByTestId('led-braun')).toBeInTheDocument()
  })

  /** Der Takt endet mit der Anzeige — ein verlassener Leitstand tickt nicht weiter. */
  it('räumt den Takt beim Verlassen ab', () => {
    const { unmount } = zeige([laufend()], [pakete(8, [paket()])])
    expect(vi.getTimerCount()).toBe(1)

    unmount()

    expect(vi.getTimerCount()).toBe(0)
  })

  /**
   * Issue #1247: Jede Paketzeile unter einem laufenden Run nennt die Dauer des Pakets — im Format
   * der übrigen Paketdauern, ohne Messung als Strich.
   */
  it('zeigt die Dauer jedes gemeldeten Pakets', () => {
    zeige(
      [laufend()],
      [
        pakete(8, [
          paket({ cardNumber: 721, durationMs: 776_000 }),
          paket({ cardNumber: 722, durationMs: 3_730_000 }),
          paket({ cardNumber: 723, durationMs: null }),
        ]),
      ],
    )

    expect(screen.getByTestId('stand-dauer-8-721')).toHaveTextContent('12:56 min')
    expect(screen.getByTestId('stand-dauer-8-721')).toHaveAccessibleName('Dauer 12 min 56 s')
    expect(screen.getByTestId('stand-dauer-8-722')).toHaveTextContent('1:02:10 h')
    expect(screen.getByTestId('stand-dauer-8-723')).toHaveTextContent('—')
  })
})

/**
 * Leerzustand, Projektblock und Paketzeile (Issue #1289): Die Angaben einer Zeile — Kartenverweis,
 * Lauf-Verweis, fehlende Karte, Zustandswort — hängen an der Komponente. Sie waren bisher nur über
 * Seiten-Tests gedeckt, die die Mutationsprüfung des Ausschnitts nicht erreicht.
 */
describe('AktuellerStand — Leerzustand, Projektblock und Paketzeile', () => {
  const zeige = (
    laufende: DisruptionView[] | null,
    gemeldetePakete: LaufPaketeView[],
    verschwunden: ReadonlySet<string> = new Set(),
    onKarteOeffnen: (projectId: number, nummer: number) => void = () => {},
  ) =>
    render(
      <AktuellerStand
        laufende={laufende}
        gemeldetePakete={gemeldetePakete}
        verschwunden={verschwunden}
        onKarteOeffnen={onKarteOeffnen}
        onAlsBeendetKennzeichnen={() => {}}
      />,
      { wrapper: MemoryRouter },
    )

  it('zeigt nichts, solange die erste Antwort fehlt', () => {
    const { container } = zeige(null, [])
    expect(container).toBeEmptyDOMElement()
  })

  it('sagt ohne laufenden Run, dass nichts arbeitet und nichts gemeldet ist', () => {
    zeige([], [])
    expect(screen.getByTestId('kein-aktueller-stand')).toHaveTextContent(
      'Gerade arbeitet kein Run — nichts gemeldet.',
    )
  })

  it('fasst die Läufe eines Projekts unter einem Projektkopf zusammen', () => {
    zeige([laufend()], [pakete(8, [paket()])])
    const gruppe = screen.getByTestId('stand-gruppe-9')
    expect(within(gruppe).getByTestId('stand-gruppe-kopf-9')).toHaveTextContent('Mein Projekt')
    expect(within(gruppe).getByTestId('stand-paket-8-721')).toBeInTheDocument()
  })

  it('öffnet die Karte über ihren Nummernverweis', async () => {
    const geoeffnet: Array<[number, number]> = []
    zeige([laufend()], [pakete(8, [paket()])], new Set(), (projekt, nummer) =>
      geoeffnet.push([projekt, nummer]),
    )

    const verweis = screen.getByRole('button', { name: 'Karte #721 öffnen: Erstes Paket' })
    expect(verweis).toHaveTextContent('#721')
    await userEvent.click(verweis)

    expect(geoeffnet).toEqual([[9, 721]])
    expect(screen.queryByText('Karte #721 nicht gefunden')).not.toBeInTheDocument()
  })

  it('führt vom Titel des Pakets in die Auswertung seines Laufs', () => {
    zeige([laufend()], [pakete(8, [paket()])])
    expect(screen.getByRole('link', { name: 'Erstes Paket — Run #8 anzeigen' })).toHaveAttribute(
      'href',
      '/projects/9/nachtlauf?lauf=8',
    )
  })

  it('zeigt eine gelöschte Karte als bloße Nummer mit Hinweis', () => {
    zeige([laufend()], [pakete(8, [paket({ cardExists: false })])])

    const zeile = screen.getByTestId('stand-paket-8-721')
    expect(within(zeile).queryByRole('button')).not.toBeInTheDocument()
    expect(within(zeile).getByText('#721')).toBeInTheDocument()
    expect(zeile).toHaveTextContent('Karte #721 nicht gefunden')
  })

  it('behandelt eine beim Abruf verschwundene Karte desselben Projekts als fehlend', () => {
    zeige([laufend()], [pakete(8, [paket()])], new Set([karteSchluessel(9, 721)]))

    const zeile = screen.getByTestId('stand-paket-8-721')
    expect(within(zeile).queryByRole('button')).not.toBeInTheDocument()
    expect(zeile).toHaveTextContent('Karte #721 nicht gefunden')
  })

  it('lässt dieselbe Nummer aus einem anderen Projekt unberührt', () => {
    zeige([laufend()], [pakete(8, [paket()])], new Set([karteSchluessel(10, 721)]))

    expect(screen.getByRole('button', { name: 'Karte #721 öffnen: Erstes Paket' })).toBeInTheDocument()
    expect(screen.queryByText('Karte #721 nicht gefunden')).not.toBeInTheDocument()
  })

  it('verbindet Projekt und Nummer zum Schlüssel einer verschwundenen Karte', () => {
    expect(karteSchluessel(9, 721)).toBe('9#721')
  })

  it('nennt das Zustandswort nach der Fehlerklasse des Pakets', () => {
    zeige(
      [laufend()],
      [pakete(8, [paket({ state: 'RED', errorClass: 'TIME_BUDGET_EXCEEDED' })])],
    )
    expect(screen.getByTestId('stand-paket-8-721')).toHaveTextContent('Am Zeitbudget beendet, ohne Ergebnis')
  })
})
