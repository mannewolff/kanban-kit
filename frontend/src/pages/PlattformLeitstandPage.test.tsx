import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cardsApi, type CardByNumber } from '../api/cards'
import { ApiError } from '../api/client'
import type {
  DisruptionView,
  LaufPaketeView,
  LeitstandView,
  PaketView,
} from '../api/plattformLeitstand'
import { plattformLeitstandApi } from '../api/plattformLeitstand'
import { KURZ_GRUND_MAX } from '../lib/nightRunHandoff'
import { cssRegel } from '../test/cssRegel'
import PlattformLeitstandPage from './PlattformLeitstandPage'

vi.mock('../api/plattformLeitstand', async () => {
  const echt = await vi.importActual<typeof import('../api/plattformLeitstand')>(
    '../api/plattformLeitstand',
  )
  return { ...echt, plattformLeitstandApi: { leitstand: vi.fn(), quittieren: vi.fn() } }
})

// Nur `byNumber` wird ersetzt (Issue #1174): Der Kartenabruf beim Klick auf eine Nummer ist der
// einzige Weg, auf dem diese Seite die Karten-API benutzt.
vi.mock('../api/cards', async () => {
  const echt = await vi.importActual<typeof import('../api/cards')>('../api/cards')
  return { ...echt, cardsApi: { ...echt.cardsApi, byNumber: vi.fn() } }
})

// Der Kartendialog ist eigenständig getestet (Muster aus `LeitstandPage.test.tsx`); hier zählt, mit
// welcher Karte und mit welchem Schreibrecht er geöffnet wird.
vi.mock('../components/CardDetailModal', () => ({
  CardDetailModal: ({
    card,
    canEdit,
    onClose,
  }: Readonly<{ card: CardByNumber; canEdit: boolean; onClose: () => void }>) => (
    <div data-testid="karten-detail" data-bearbeitbar={String(canEdit)}>
      {card.title}
      <button type="button" onClick={onClose}>
        Detail schließen
      </button>
    </div>
  ),
}))

const api = vi.mocked(plattformLeitstandApi)
const karteNachNummer = vi.mocked(cardsApi.byNumber)

/** Ein Speicher je Test, unabhängig davon, ob die Node-Fassung einen nativen mitbringt. */
function fakeStorage(): Storage {
  const map = new Map<string, string>()
  return {
    getItem: (k) => map.get(k) ?? null,
    setItem: (k, v) => void map.set(k, String(v)),
    removeItem: (k) => void map.delete(k),
    clear: () => map.clear(),
    key: (i) => [...map.keys()][i] ?? null,
    get length() {
      return map.size
    },
  }
}

/** Ein Speicher, dessen Zugriffe werfen — wie in einem privaten oder gesperrten Kontext. */
function throwingStorage(): Storage {
  const boom = () => {
    throw new Error('storage disabled')
  }
  return {
    getItem: boom,
    setItem: boom,
    removeItem: boom,
    clear: boom,
    key: boom,
    get length(): number {
      return 0
    },
  }
}

/**
 * Der Plattform-Leitstand (Issue #1083, um die beiden Lauf-Bereiche erweitert in #1098; fachliche
 * Quellen #1064 und #1086).
 *
 * Geprüft wird die **Aussage** der Zeile, nicht ihre Gestalt: Projekt, Zeitpunkt, anklickbare
 * Kennung, Ausgang als Wort und die Taste. Wie die Seite aussieht, nimmt ein Mensch gegen
 * `docs/entwurf-leitstand.html` ab — der Entwurf führt für diese Ansicht kein eigenes Mockup.
 */
describe('PlattformLeitstandPage (#1083)', () => {
  const stoerung = (extra: Partial<DisruptionView> = {}): DisruptionView => ({
    nightRunId: 5,
    projectId: 9,
    projectName: 'Mein Projekt',
    mode: 'IMPLEMENTATION',
    startedAt: '2026-09-19T21:10:00Z',
    outcome: {
      abortReason: null,
      verdict: 'FAILED',
      decisiveItem: { cardNumber: 721, state: 'RED', errorClass: 'CHECKS_RED' },
      noWorkReason: null,
    },
    ...extra,
  })

  /** Die Antwort des Servers; nicht genannte Listen sind leer. */
  const sicht = (teil: Partial<LeitstandView> = {}): LeitstandView => ({
    laufende: [],
    durchgefuehrte: [],
    durchgefuehrteVoriger: [],
    stoerungen: [],
    gemeldetePakete: [],
    ...teil,
  })

  const zeigeSeite = () => render(<PlattformLeitstandPage />, { wrapper: MemoryRouter })

  beforeEach(() => {
    vi.clearAllMocks()
    api.quittieren.mockResolvedValue(undefined)
    // Der gemerkte Klappzustand (#1152) läuft über einen eigenen Speicher je Test statt über das
    // native `localStorage`: Unter Node 26 ist das nativ vorhandene deaktiviert (siehe
    // `test/setup.ts`), und ein Test, der lokal an dieser Stelle grün ist und in CI rot, prüft
    // die Umgebung statt die Seite.
    vi.stubGlobal('localStorage', fakeStorage())
  })

  /**
   * Kriterium 18 und AK 1 der Quelle #1153: die vier Bereiche untereinander, in dieser Ordnung.
   * „Aktueller Status" (#1173) steht zwischen den laufenden und den beendeten Runs — was gerade
   * gemeldet wird, gehört neben das, was gerade arbeitet.
   */
  it('stellt die vier Bereiche in der Ordnung Aktive Runs, Aktueller Status, Beendete Runs, Störungen', async () => {
    api.leitstand.mockResolvedValue(sicht({ stoerungen: [stoerung()] }))

    zeigeSeite()

    await screen.findByTestId('stoerung-5')
    expect(screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)).toEqual([
      'Aktive Runs',
      'Aktueller Status',
      'Beendete Runs',
      'Störungen',
    ])
  })

  /**
   * Der Bereich „Aktive Läufe" (Kriterien 1–4, 11).
   *
   * Der Puls hält bei `prefers-reduced-motion` von selbst an — die globale Regel im Theme greift
   * für jede Animation; hier steht deshalb nur, **dass** die LED pulst.
   */
  describe('Aktive Runs (#1098, benannt in #1102)', () => {
    const laufend = (extra: Partial<DisruptionView> = {}): DisruptionView => ({
      nightRunId: 8,
      projectId: 9,
      projectName: 'Mein Projekt',
      mode: 'CHAIN',
      startedAt: '2026-09-21T01:10:00Z',
      outcome: { abortReason: null, verdict: 'RUNNING', decisiveItem: null, noWorkReason: null },
      ...extra,
    })

    /**
     * Kriterium 3: Der Zustand steht als **Wort** da. Farbe und Bewegung allein trügen die Aussage
     * sonst allein — wer keine Farben unterscheidet oder Bewegung abgeschaltet hat, läse nichts.
     */
    it('nennt Projekt, „läuft seit HH:MM" und die Kennung, mit pulsierendem Stahl-Melder', async () => {
      api.leitstand.mockResolvedValue(sicht({ laufende: [laufend()] }))

      zeigeSeite()

      const zeile = await screen.findByTestId('laufend-8')
      expect(within(zeile).getByText('Mein Projekt')).toBeInTheDocument()
      expect(within(zeile).getByText('läuft seit 03:10')).toBeInTheDocument()
      const led = within(zeile).getByTestId('led-stahl')
      expect(led).toHaveAttribute('data-puls', 'an')
      // Issue #1136: Ein laufender Lauf zeigt den Wechselblinker — zwei Lampen.
      expect(within(led).getAllByTestId('blinker-lampe')).toHaveLength(2)
      expect(within(zeile).getByRole('link', { name: 'Run #8 von Mein Projekt' })).toHaveAttribute(
        'href',
        '/projects/9/nachtlauf?lauf=8',
      )
    })

    /** Kriterium 9: auch mehrere Läufe desselben Projekts, in der Reihenfolge der Antwort. */
    it('hält die Reihenfolge der Antwort ein', async () => {
      api.leitstand.mockResolvedValue(
        sicht({
          laufende: [
            laufend({ nightRunId: 9, projectName: 'Jung' }),
            laufend({ nightRunId: 8, projectName: 'Alt' }),
          ],
        }),
      )

      zeigeSeite()

      const zeilen = await screen.findAllByTestId(/^laufend-/)
      expect(zeilen.map((z) => z.getAttribute('data-testid'))).toEqual(['laufend-9', 'laufend-8'])
    })

    /** Kriterium 4: eine leere Fläche wäre von einer kaputten Anzeige nicht zu unterscheiden. */
    it('sagt ausdrücklich, wenn gerade nirgends ein Lauf läuft', async () => {
      api.leitstand.mockResolvedValue(sicht())

      zeigeSeite()

      expect(await screen.findByTestId('keine-laufenden')).toHaveTextContent(
        'Gerade läuft kein Run.',
      )
    })
  })

  /** Der Bereich „Beendete Runs" (Kriterien 9–14). */
  describe('Beendete Runs (#1098, benannt in #1102)', () => {
    const durchgefuehrt = (extra: Partial<DisruptionView> = {}): DisruptionView => ({
      nightRunId: 5,
      projectId: 9,
      projectName: 'Mein Projekt',
      mode: 'IMPLEMENTATION',
      startedAt: '2026-09-19T21:10:00Z',
      outcome: { abortReason: null, verdict: 'SUCCEEDED', decisiveItem: null, noWorkReason: null },
      ...extra,
    })

    it('nennt Projekt, Startzeitpunkt und Kennung', async () => {
      api.leitstand.mockResolvedValue(sicht({ durchgefuehrte: [durchgefuehrt()] }))

      zeigeSeite()

      const zeile = await screen.findByTestId('durchgefuehrt-5')
      expect(within(zeile).getByText('Mein Projekt')).toBeInTheDocument()
      expect(within(zeile).getByText('19.09., 23:10')).toBeInTheDocument()
      expect(within(zeile).getByRole('link', { name: 'Run #5 von Mein Projekt' })).toBeInTheDocument()
    })

    /**
     * Kriterien 10 und 11: die drei Ausgänge als Wort, nicht als Farbe. Der Melder pulst hier
     * nicht — ein beendeter Lauf arbeitet nicht mehr (Kriterium 5).
     */
    it.each([
      ['SUCCEEDED', 'gelungen', 'led-gruen'],
      ['FAILED', 'nicht gelungen', 'led-zinnob'],
      ['WAITING', 'mit Vorbehalt', 'led-grau'],
    ] as const)('zeigt %s als „%s" mit ruhendem Melder', async (verdict, wort, led) => {
      api.leitstand.mockResolvedValue(
        sicht({
          durchgefuehrte: [
            durchgefuehrt({
              outcome: {
                verdict,
                decisiveItem:
                  verdict === 'SUCCEEDED'
                    ? null
                    : {
                        cardNumber: 721,
                        state: verdict === 'FAILED' ? 'RED' : 'GREY',
                        errorClass: verdict === 'FAILED' ? 'CHECKS_RED' : 'AWAITING_DECISION',
                      },
                noWorkReason: null,
                abortReason: null,
              },
            }),
          ],
        }),
      )

      zeigeSeite()

      const zeile = await screen.findByTestId('durchgefuehrt-5')
      expect(within(zeile).getByText(wort)).toBeInTheDocument()
      expect(within(zeile).getByTestId(led)).toHaveAttribute('data-puls', 'aus')
    })

    /**
     * Issue #1121: Ein Lauf, der anlief und nichts Freigegebenes fand, steht hier grau mit dem Wort
     * „nichts zu tun" — nicht rot. Wer ein Projekt nachts bewusst ruhen lässt, räumte sonst jeden
     * Morgen eine Meldung weg.
     */
    it('zeigt NO_WORK als „nichts zu tun" mit grauem, ruhendem Melder und der Auskunft', async () => {
      api.leitstand.mockResolvedValue(
        sicht({
          durchgefuehrte: [
            durchgefuehrt({
              outcome: {
                abortReason: null,
                verdict: 'NO_WORK',
                decisiveItem: null,
                noWorkReason: 'Ready ist leer — nichts zu tun.',
              },
            }),
          ],
        }),
      )

      zeigeSeite()

      const zeile = await screen.findByTestId('durchgefuehrt-5')
      expect(zeile).toHaveTextContent('nichts zu tun — Ready ist leer — nichts zu tun.')
      expect(within(zeile).getByTestId('led-grau')).toHaveAttribute('data-puls', 'aus')
    })

    /**
     * Issue #1189, Kriterium 2 der Quelle #1175: Seit der neuen Leseregel (#1185) ist der Lauf ohne
     * Arbeit keine Störung mehr — damit fällt die einzige Stelle weg, an der sein Grund bisher
     * stand. Er muss hier stehen, auch wenn der Runner keinen meldete und der Server auf seinen
     * Rückfalltext zurückfiel: Grau und „nichts zu tun" allein sagten nicht, warum.
     */
    it('nennt beim Lauf ohne Arbeit auch den Rückfalltext, ohne Störzeile', async () => {
      api.leitstand.mockResolvedValue(
        sicht({
          durchgefuehrte: [
            durchgefuehrt({
              outcome: {
                abortReason: null,
                verdict: 'NO_WORK',
                decisiveItem: null,
                noWorkReason: 'Nichts abgearbeitet — Grund unbekannt',
              },
            }),
          ],
        }),
      )

      zeigeSeite()

      const zeile = await screen.findByTestId('durchgefuehrt-5')
      expect(zeile).toHaveTextContent('nichts zu tun — Nichts abgearbeitet — Grund unbekannt')
      expect(screen.queryByTestId('stoerung-5')).not.toBeInTheDocument()
    })

    /** E9: dieselbe Kürzung wie beim Abbruchgrund — die Auskunft teilt sich die Zeile mit dem Rest. */
    it('kürzt eine überlange Auskunft auf eine Zeile', async () => {
      const lang = `${'A'.repeat(KURZ_GRUND_MAX)}B`

      api.leitstand.mockResolvedValue(
        sicht({
          durchgefuehrte: [
            durchgefuehrt({
              outcome: {
                abortReason: null,
                verdict: 'NO_WORK',
                decisiveItem: null,
                noWorkReason: lang,
              },
            }),
          ],
        }),
      )

      zeigeSeite()

      const zeile = await screen.findByTestId('durchgefuehrt-5')
      expect(zeile).toHaveTextContent(
        `nichts zu tun — ${'A'.repeat(KURZ_GRUND_MAX - 1)}…`,
      )
      expect(zeile).not.toHaveTextContent(lang)
    })

    /**
     * E11: Ein Lauf, der alle Pakete zurückstellte, trägt den Rückfalltext am Datensatz, ist aber
     * „mit Vorbehalt" — er hat nicht nichts gefunden. Die Auskunft gehört allein dem Ausgang
     * `NO_WORK`, sonst nennte die Zeile einen Grund, der nicht ihrer ist.
     */
    it('nennt beim Lauf mit zurückgestellten Paketen keine Auskunft „ohne Arbeit"', async () => {
      api.leitstand.mockResolvedValue(
        sicht({
          durchgefuehrte: [
            durchgefuehrt({
              outcome: {
                abortReason: null,
                verdict: 'WAITING',
                decisiveItem: { cardNumber: 721, state: 'GREY', errorClass: 'AWAITING_DECISION' },
                noWorkReason: 'Nichts abgearbeitet — Grund unbekannt',
              },
            }),
          ],
        }),
      )

      zeigeSeite()

      const zeile = await screen.findByTestId('durchgefuehrt-5')
      expect(zeile).toHaveTextContent('mit Vorbehalt')
      expect(zeile).not.toHaveTextContent('Nichts abgearbeitet')
    })

    /** Kriterium 12, erste Hälfte: von **jedem** Eintrag — auch vom gelungenen. */
    it('führt von jedem Eintrag zur Auswertung, auch vom gelungenen', async () => {
      api.leitstand.mockResolvedValue(
        sicht({
          durchgefuehrte: [
            durchgefuehrt({ nightRunId: 5, projectId: 9, projectName: 'Gelungen' }),
            durchgefuehrt({
              nightRunId: 6,
              projectId: 10,
              projectName: 'Gescheitert',
              outcome: {
                abortReason: null,
                verdict: 'FAILED',
                decisiveItem: { cardNumber: 721, state: 'RED', errorClass: 'CHECKS_RED' },
                noWorkReason: null,
              },
            }),
          ],
        }),
      )

      zeigeSeite()

      await screen.findByTestId('durchgefuehrt-5')
      expect(screen.getByRole('link', { name: 'Run #5 von Gelungen' })).toHaveAttribute(
        'href',
        '/projects/9/nachtlauf?lauf=5',
      )
      expect(screen.getByRole('link', { name: 'Run #6 von Gescheitert' })).toHaveAttribute(
        'href',
        '/projects/10/nachtlauf?lauf=6',
      )
    })

    /**
     * Kriterium 12, zweite Hälfte: Gibt es zu dem Lauf eine Störung, führt ein zweiter Weg dorthin.
     * Die Mitgliedschaft entscheidet der Browser aus **einer** Antwort — ein Serverfeld wäre eine
     * zweite Quelle für dieselbe Aussage.
     */
    it('verweist bei einem Lauf mit Störung zusätzlich auf dessen Störzeile', async () => {
      api.leitstand.mockResolvedValue(
        sicht({
          durchgefuehrte: [
            durchgefuehrt({
              nightRunId: 5,
              outcome: {
                abortReason: null,
                verdict: 'FAILED',
                decisiveItem: { cardNumber: 721, state: 'RED', errorClass: 'CHECKS_RED' },
                noWorkReason: null,
              },
            }),
            durchgefuehrt({ nightRunId: 6, projectName: 'Zweites Projekt' }),
          ],
          stoerungen: [stoerung({ nightRunId: 5 })],
        }),
      )

      zeigeSeite()

      const mit = await screen.findByTestId('durchgefuehrt-5')
      expect(within(mit).getByRole('link', { name: 'Zur Störung von Run #5' })).toHaveAttribute(
        'href',
        '#stoerung-5',
      )
      expect(screen.getByTestId('stoerung-5')).toHaveAttribute('id', 'stoerung-5')
      const ohne = screen.getByTestId('durchgefuehrt-6')
      expect(
        within(ohne).queryByRole('link', { name: 'Zur Störung von Run #6' }),
      ).not.toBeInTheDocument()
      expect(within(ohne).getAllByRole('link')).toHaveLength(1)
    })

    /** Kriterium 13: Das Quittieren nimmt den Weg zur Störung, nicht den Ausgang des Laufs. */
    it('nimmt nach dem Quittieren nur den Verweis zur Störung weg', async () => {
      api.leitstand.mockResolvedValue(
        sicht({
          durchgefuehrte: [
            durchgefuehrt({
              nightRunId: 5,
              outcome: {
                abortReason: null,
                verdict: 'FAILED',
                decisiveItem: { cardNumber: 721, state: 'RED', errorClass: 'CHECKS_RED' },
                noWorkReason: null,
              },
            }),
          ],
          stoerungen: [stoerung({ nightRunId: 5 })],
        }),
      )

      zeigeSeite()
      await userEvent.click(
        await screen.findByRole('button', { name: 'Störung von Mein Projekt, Run #5 löschen' }),
      )

      await waitFor(() => expect(screen.queryByTestId('stoerung-5')).not.toBeInTheDocument())
      const zeile = screen.getByTestId('durchgefuehrt-5')
      expect(within(zeile).queryByRole('link', { name: 'Zur Störung von Run #5' })).not.toBeInTheDocument()
      expect(within(zeile).getByText('nicht gelungen')).toBeInTheDocument()
      expect(within(zeile).getByRole('link', { name: 'Run #5 von Mein Projekt' })).toBeInTheDocument()
      expect(api.leitstand).toHaveBeenCalledTimes(1)
    })

    /** Kriterium 14: ausdrücklicher Satz statt leerer Fläche. */
    it('sagt ausdrücklich, wenn in dieser Nacht noch kein Run beendet ist', async () => {
      api.leitstand.mockResolvedValue(sicht())

      zeigeSeite()

      expect(await screen.findByTestId('keine-durchgefuehrten')).toHaveTextContent(
        'Kein Run dieser Schicht ist beendet.',
      )
    })
  })

  /**
   * Die Sektion „Aktueller Status" (Issue #1173, fachliche Quelle #1153, AK 1–8 und 10–12).
   *
   * Geprüft wird die **Aussage** der Zeile: Nummer, Titel und das Zustandswort — nie die Farbe
   * allein. Die Wörter kommen aus `lib/nightRunHandoff.ts`, derselben Quelle wie in der
   * Lauf-Ansicht; der Stand aus `lib/aktuellerStand.ts` (#1172).
   */
  describe('Aktueller Status (#1173)', () => {
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
      ...extra,
    })

    /** Die Pakete eines Laufs, wie die Antwort sie neben `laufende` führt. */
    const pakete = (nightRunId: number, liste: PaketView[]): LaufPaketeView => ({
      nightRunId,
      pakete: liste,
    })

    /** AK 3–5: Nummer, Titel und Zustandswort — je gemeldetem Paket eine Zeile. */
    it('nennt je Paket Nummer, Titel und Zustandswort', async () => {
      api.leitstand.mockResolvedValue(
        sicht({
          laufende: [laufend()],
          gemeldetePakete: [pakete(8, [paket({ cardNumber: 721, title: 'Erstes Paket' })])],
        }),
      )

      zeigeSeite()

      const zeile = await screen.findByTestId('stand-paket-8-721')
      expect(within(zeile).getByText('#721')).toBeInTheDocument()
      expect(within(zeile).getByText('Erstes Paket')).toBeInTheDocument()
      expect(within(zeile).getByText('Erfolg')).toBeInTheDocument()
    })

    /**
     * AK 5: Alle vier Zustände tragen ihr eigenes Wort neben dem Melder — Farbe ist nie der
     * einzige Träger der Aussage.
     */
    it.each([
      ['GREEN', null, 'Erfolg', 'led-gruen'],
      ['YELLOW', 'CHECKS_RED', 'Erfolg, Prüfung rot', 'led-bernst'],
      ['RED', 'CHECKS_RED', 'gescheitert', 'led-zinnob'],
      ['GREY', 'AWAITING_DECISION', 'nicht bearbeitet', 'led-grau'],
    ] as const)('zeigt %s als „%s" mit eigenem Melder', async (state, errorClass, wort, led) => {
      api.leitstand.mockResolvedValue(
        sicht({
          laufende: [laufend()],
          gemeldetePakete: [pakete(8, [paket({ state, errorClass })])],
        }),
      )

      zeigeSeite()

      const zeile = await screen.findByTestId('stand-paket-8-721')
      expect(within(zeile).getByText(wort)).toBeInTheDocument()
      expect(within(zeile).getByTestId(led)).toBeInTheDocument()
    })

    /**
     * AK 4: Das Wort kommt aus `nightRunZustandsText` und stimmt deshalb mit dem der Lauf-Ansicht
     * überein — „Erfolg, Prüfung rot" wäre an einem am Zeitbudget beendeten Paket eine
     * Falschaussage: Die Prüfung war nicht rot, sie kam gar nicht dran.
     */
    it('nennt ein am Zeitbudget beendetes Paket wie die Lauf-Ansicht', async () => {
      api.leitstand.mockResolvedValue(
        sicht({
          laufende: [laufend()],
          gemeldetePakete: [
            pakete(8, [paket({ state: 'YELLOW', errorClass: 'TIME_BUDGET_EXCEEDED' })]),
          ],
        }),
      )

      zeigeSeite()

      const zeile = await screen.findByTestId('stand-paket-8-721')
      expect(within(zeile).getByText('Am Zeitbudget beendet, Ergebnis liegt vor')).toBeInTheDocument()
    })

    /** AK 6: nach Projekt gruppiert, je Lauf ein Kopf mit Kennung und Stand. */
    it('gruppiert nach Projekt und gibt jedem Lauf einen Kopf mit seinem Stand', async () => {
      api.leitstand.mockResolvedValue(
        sicht({
          laufende: [
            laufend({ nightRunId: 8, projectId: 9, projectName: 'Alpha' }),
            laufend({ nightRunId: 7, projectId: 10, projectName: 'Beta' }),
            laufend({ nightRunId: 6, projectId: 9, projectName: 'Alpha' }),
          ],
          gemeldetePakete: [
            pakete(8, [paket({ cardNumber: 721 }), paket({ cardNumber: 722, state: 'RED', errorClass: 'CHECKS_RED' })]),
            pakete(7, [paket({ cardNumber: 800 })]),
            pakete(6, []),
          ],
        }),
      )

      zeigeSeite()

      const gruppen = await screen.findAllByTestId(/^stand-gruppe-\d+$/)
      expect(gruppen.map((g) => g.getAttribute('data-testid'))).toEqual([
        'stand-gruppe-9',
        'stand-gruppe-10',
      ])
      expect(within(gruppen[0]).getByText('Alpha')).toBeInTheDocument()
      expect(
        within(gruppen[0])
          .getAllByTestId(/^stand-lauf-\d+$/)
          .map((l) => l.getAttribute('data-testid')),
      ).toEqual(['stand-lauf-8', 'stand-lauf-6'])
      expect(screen.getByTestId('stand-kopf-8')).toHaveTextContent(
        'Run #8 · 2 gemeldet · 1 Erfolg, 1 gescheitert',
      )
      // Ein laufender Run ohne Paket steht mit „0 gemeldet" und ohne Zeile darunter.
      expect(screen.getByTestId('stand-kopf-6')).toHaveTextContent('Run #6 · 0 gemeldet')
      expect(within(screen.getByTestId('stand-lauf-6')).queryAllByTestId(/^stand-paket-/)).toHaveLength(0)
    })

    /** AK 7: keine Kappung — ein langer Lauf zeigt jede seiner Zeilen. */
    it('zeigt auch vierzig Pakete ohne Kappung', async () => {
      api.leitstand.mockResolvedValue(
        sicht({
          laufende: [laufend()],
          gemeldetePakete: [
            pakete(
              8,
              Array.from({ length: 40 }, (_, i) => paket({ cardNumber: 700 + i, title: `Paket ${i}` })),
            ),
          ],
        }),
      )

      zeigeSeite()

      await screen.findByTestId('stand-paket-8-700')
      expect(screen.getAllByTestId(/^stand-paket-/)).toHaveLength(40)
      expect(screen.queryByText(/ausgeblendet/)).toBeNull()
    })

    /**
     * AK 8: Gezählt wird alles Gemeldete einschließlich der grauen Pakete, und **ohne Nenner** —
     * wie viele noch kommen, weiß der laufende Run selbst nicht.
     */
    it('zählt graue Pakete im Stand mit und nennt keinen Nenner', async () => {
      api.leitstand.mockResolvedValue(
        sicht({
          laufende: [laufend()],
          gemeldetePakete: [
            pakete(8, [
              paket({ cardNumber: 721 }),
              paket({ cardNumber: 722, state: 'GREY', errorClass: 'DEPENDENCY_UNMET' }),
            ]),
          ],
        }),
      )

      zeigeSeite()

      const kopf = await screen.findByTestId('stand-kopf-8')
      expect(kopf).toHaveTextContent('Run #8 · 2 gemeldet · 1 Erfolg, 1 nicht bearbeitet')
      expect(kopf.textContent).not.toMatch(/\bvon\b|\/\s*\d/)
    })

    /**
     * Ohne Namen sagte ein Vorlesewerkzeug nur „Liste mit elf Einträgen" und ließe offen, zu
     * welchem Lauf sie gehört. Der Name ist der Kopf — dieselbe Lösung wie bei den Störgruppen.
     */
    it('benennt die Liste eines Laufs mit dessen Kopf', async () => {
      api.leitstand.mockResolvedValue(
        sicht({
          laufende: [laufend()],
          gemeldetePakete: [pakete(8, [paket()])],
        }),
      )

      zeigeSeite()

      const liste = await screen.findByRole('list', { name: 'Run #8 · 1 gemeldet · 1 Erfolg' })
      expect(within(liste).getByTestId('stand-paket-8-721')).toBeInTheDocument()
    })

    /** AK 12: ein eigener Satz, nicht derselbe wie in „Aktive Runs" (E9). */
    it('sagt ohne laufenden Run einen eigenen Satz', async () => {
      api.leitstand.mockResolvedValue(sicht())

      zeigeSeite()

      expect(await screen.findByTestId('kein-aktueller-stand')).toHaveTextContent(
        'Gerade arbeitet kein Run — nichts gemeldet.',
      )
      expect(screen.getByTestId('keine-laufenden')).toHaveTextContent('Gerade läuft kein Run.')
    })

    /**
     * AK 10 und E11: Die Sektion erbt den Takt der Seite — ein neu gemeldetes Paket steht nach
     * 30 Sekunden da, und es gibt **keinen** zweiten Abruf allein für sie.
     */
    it('zeigt nach dem Takt ein neu gemeldetes Paket, ohne eigenen Abruf', async () => {
      vi.useFakeTimers()
      try {
        api.leitstand
          .mockResolvedValueOnce(
            sicht({ laufende: [laufend()], gemeldetePakete: [pakete(8, [paket()])] }),
          )
          .mockResolvedValueOnce(
            sicht({
              laufende: [laufend()],
              gemeldetePakete: [
                pakete(8, [paket(), paket({ cardNumber: 722, title: 'Zweites Paket' })]),
              ],
            }),
          )

        zeigeSeite()
        await act(async () => {
          await Promise.resolve()
        })
        expect(screen.queryByTestId('stand-paket-8-722')).toBeNull()

        await act(async () => {
          vi.advanceTimersByTime(30_000)
        })

        expect(screen.getByTestId('stand-paket-8-722')).toHaveTextContent('Zweites Paket')
        expect(api.leitstand).toHaveBeenCalledTimes(2)
      } finally {
        vi.useRealTimers()
      }
    })

    /**
     * Die beiden Wege aus einer Paketzeile (Issue #1174, AK 9 der Quelle #1153): Die **Nummer**
     * öffnet die Karte über dem Leitstand, der **Rest der Zeile** führt in die Lauf-Ansicht.
     *
     * Geprüft wird beides als eigenes Bedienelement — eines im anderen wäre ungültiges HTML und für
     * die Tastatur nicht auflösbar (E10). Der Kartendialog selbst ist eigenständig getestet; hier
     * zählt, mit welcher Karte, mit welchem Recht und **wo** er erscheint.
     */
    describe('Wege aus der Paketzeile (#1174)', () => {
      const karte = (nummer: number, title: string): CardByNumber => ({
        id: 1000 + nummer,
        number: nummer,
        title,
        description: null,
        type: 'CARD',
        dependencies: [],
        assignees: [],
        labels: [],
        parentId: null,
        shortcode: null,
        dueDate: null,
        archived: false,
        ideaStored: false,
        derivedFrom: null,
        boardId: 1,
        columnId: 5,
      })

      /** Eine Sicht mit genau einem laufenden Run und dessen Paketen. */
      const mitPaketen = (liste: PaketView[]) =>
        sicht({ laufende: [laufend()], gemeldetePakete: [pakete(8, liste)] })

      const nummerTaste = (zeile: HTMLElement, nummer: number, titel: string) =>
        within(zeile).getByRole('button', { name: `Karte #${nummer} öffnen: ${titel}` })

      const laufVerweis = (zeile: HTMLElement, titel: string) =>
        within(zeile).getByRole('link', { name: `${titel} — Run #8 anzeigen` })

      /** AK 9: Die Nummer öffnet die Karte über dem Leitstand — ohne die Seite zu verlassen. */
      it('öffnet über der Seite den nicht bearbeitbaren Kartendialog, wenn die Nummer geklickt wird', async () => {
        api.leitstand.mockResolvedValue(mitPaketen([paket()]))
        karteNachNummer.mockResolvedValue(karte(721, 'Erstes Paket'))

        zeigeSeite()

        const zeile = await screen.findByTestId('stand-paket-8-721')
        await userEvent.click(nummerTaste(zeile, 721, 'Erstes Paket'))

        expect(karteNachNummer).toHaveBeenCalledWith(9, 721)
        const dialog = await screen.findByTestId('karten-detail')
        expect(dialog).toHaveTextContent('Erstes Paket')
        // E12: `canEdit={false}` — der Leitstand zeigt die Karte, er bearbeitet sie nicht.
        expect(dialog).toHaveAttribute('data-bearbeitbar', 'false')
        // Der Dialog liegt **außerhalb** des Kupferwarte-Teilbaums: Dessen eigener ThemeProvider und
        // seine hellen CSS-Variablen gelten sonst auch im Dialog.
        expect(screen.getByTestId('kupferwarte-bereich')).not.toContainElement(dialog)
        // Die Sektion bleibt stehen — der Dialog liegt darüber, er ersetzt sie nicht.
        expect(screen.getByTestId('stand-paket-8-721')).toBeInTheDocument()
      })

      /** Der Dialog liegt **über** dem Leitstand: Geschlossen steht die Seite unverändert da. */
      it('lässt den Dialog wieder schließen und die Seite stehen', async () => {
        api.leitstand.mockResolvedValue(mitPaketen([paket()]))
        karteNachNummer.mockResolvedValue(karte(721, 'Erstes Paket'))

        zeigeSeite()

        const zeile = await screen.findByTestId('stand-paket-8-721')
        await userEvent.click(nummerTaste(zeile, 721, 'Erstes Paket'))
        await userEvent.click(await screen.findByRole('button', { name: 'Detail schließen' }))

        expect(screen.queryByTestId('karten-detail')).toBeNull()
        expect(
          nummerTaste(screen.getByTestId('stand-paket-8-721'), 721, 'Erstes Paket'),
        ).toBeInTheDocument()
      })

      /** AK 9: Der Rest der Zeile führt in die Lauf-Ansicht genau dieses Laufs. */
      it('führt von der Zeile daneben in die Lauf-Ansicht', async () => {
        api.leitstand.mockResolvedValue(mitPaketen([paket()]))

        zeigeSeite()

        const zeile = await screen.findByTestId('stand-paket-8-721')
        expect(laufVerweis(zeile, 'Erstes Paket')).toHaveAttribute(
          'href',
          '/projects/9/nachtlauf?lauf=8',
        )
      })

      /**
       * E10: zwei Geschwister, keine Schachtelung — und für die Tastatur genau zwei Halte in der
       * Reihenfolge Nummer, dann Zeile.
       */
      it('hält je Zeile zwei getrennte Halte in der Reihenfolge Nummer, dann Zeile', async () => {
        api.leitstand.mockResolvedValue(mitPaketen([paket()]))

        zeigeSeite()

        const zeile = await screen.findByTestId('stand-paket-8-721')
        const nummer = nummerTaste(zeile, 721, 'Erstes Paket')
        const verweis = laufVerweis(zeile, 'Erstes Paket')
        expect(nummer).not.toContainElement(verweis)
        expect(verweis).not.toContainElement(nummer)

        // Gegangen wird der ganze Weg von vorn: erst die Kennung des aktiven Runs, dann die beiden
        // Halte der Paketzeile, dann die Tasten der nächsten Platte — genau zwei Halte in der Zeile.
        await userEvent.tab()
        expect(within(screen.getByTestId('laufend-8')).getByRole('link')).toHaveFocus()
        await userEvent.tab()
        expect(nummer).toHaveFocus()
        await userEvent.tab()
        expect(verweis).toHaveFocus()
        await userEvent.tab()
        expect(screen.getByRole('button', { name: 'Beendete Runs: 10' })).toHaveFocus()
      })

      /**
       * AK 9: Gibt es die Karte nicht mehr, ist die Nummer **kein** Bedienelement — der Zustand gilt
       * vor dem Klick, nicht erst nach einem erfolglosen. Titel und Ausgang bleiben stehen.
       */
      it('macht die Nummer bei fehlender Karte zu reinem Text und sagt es', async () => {
        api.leitstand.mockResolvedValue(mitPaketen([paket({ cardExists: false })]))

        zeigeSeite()

        const zeile = await screen.findByTestId('stand-paket-8-721')
        expect(within(zeile).queryByRole('button')).toBeNull()
        expect(zeile).toHaveTextContent('Karte #721 nicht gefunden')
        expect(within(zeile).getByText('#721')).toBeInTheDocument()
        expect(within(zeile).getByText('Erstes Paket')).toBeInTheDocument()
        expect(within(zeile).getByText('Erfolg')).toBeInTheDocument()
        // Der Weg in die Lauf-Ansicht hängt nicht an der Karte.
        expect(laufVerweis(zeile, 'Erstes Paket')).toBeInTheDocument()
      })

      /**
       * E14: Ein 404 heißt „zwischen zwei Abrufen verschwunden" — genau diese Zeile sagt es danach,
       * ihre Nummer ist kein Bedienelement mehr, und die Sektion bleibt vollständig.
       */
      it('setzt nach einem 404 genau diese Zeile auf nicht gefunden', async () => {
        api.leitstand.mockResolvedValue(
          mitPaketen([paket(), paket({ cardNumber: 722, title: 'Zweites Paket' })]),
        )
        karteNachNummer.mockRejectedValue(new ApiError(404, 'Not Found'))

        zeigeSeite()

        const zeile = await screen.findByTestId('stand-paket-8-721')
        await userEvent.click(nummerTaste(zeile, 721, 'Erstes Paket'))

        await waitFor(() =>
          expect(screen.getByTestId('stand-paket-8-721')).toHaveTextContent(
            'Karte #721 nicht gefunden',
          ),
        )
        expect(within(screen.getByTestId('stand-paket-8-721')).queryByRole('button')).toBeNull()
        expect(screen.queryByTestId('karten-detail')).toBeNull()
        expect(screen.queryByRole('alert')).toBeNull()
        // Nur die geklickte Zeile: Die andere behält ihren Weg zur Karte.
        const andere = screen.getByTestId('stand-paket-8-722')
        expect(nummerTaste(andere, 722, 'Zweites Paket')).toBeInTheDocument()
        expect(andere).not.toHaveTextContent('nicht gefunden')
      })

      /**
       * E14: Jeder andere Fehler ist keine Aussage über die Karte, sondern über den Abruf — er
       * erscheint als `Alert` unter der Platte. Die Sektion bleibt vollständig, und der
       * `fehler`-Zustand der Seite bleibt unberührt: Alle vier Bereiche stehen weiter da.
       */
      it('zeigt bei einem anderen Fehler einen Alert unter der Platte', async () => {
        api.leitstand.mockResolvedValue(mitPaketen([paket()]))
        karteNachNummer.mockRejectedValue(new ApiError(500, 'Boom'))

        zeigeSeite()

        const zeile = await screen.findByTestId('stand-paket-8-721')
        await userEvent.click(nummerTaste(zeile, 721, 'Erstes Paket'))

        expect(await screen.findByRole('alert')).toHaveTextContent(
          'Karte #721 konnte nicht geladen werden.',
        )
        expect(screen.getByTestId('stand-paket-8-721')).not.toHaveTextContent('nicht gefunden')
        expect(nummerTaste(screen.getByTestId('stand-paket-8-721'), 721, 'Erstes Paket')).toBeInTheDocument()
        expect(screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)).toEqual([
          'Aktive Runs',
          'Aktueller Status',
          'Beendete Runs',
          'Störungen',
        ])
      })

      /** E3: Die Karten werden **nicht** vorab geladen — der Abruf steht am Klick. */
      it('ruft die Karte erst beim Klick ab', async () => {
        api.leitstand.mockResolvedValue(
          mitPaketen([paket(), paket({ cardNumber: 722, title: 'Zweites Paket' })]),
        )

        zeigeSeite()

        await screen.findByTestId('stand-paket-8-722')
        expect(karteNachNummer).not.toHaveBeenCalled()
      })
    })
  })

  /** AK 13 (#1064): die jüngste Störung zuoberst — die Reihenfolge der Antwort bleibt erhalten. */
  it('zeigt die Störungen in der Reihenfolge der Antwort, jüngste zuoberst', async () => {
    api.leitstand.mockResolvedValue(
      sicht({
        stoerungen: [
          stoerung({ nightRunId: 7, projectId: 7, startedAt: '2026-09-19T21:10:00Z', projectName: 'Jung' }),
          stoerung({ nightRunId: 6, projectId: 6, startedAt: '2026-09-18T21:10:00Z', projectName: 'Mittel' }),
          stoerung({ nightRunId: 5, projectId: 5, startedAt: '2026-09-17T21:10:00Z', projectName: 'Alt' }),
        ],
      }),
    )

    zeigeSeite()

    const zeilen = await screen.findAllByTestId(/^stoerung-/)
    expect(zeilen.map((z) => z.getAttribute('data-testid'))).toEqual([
      'stoerung-7',
      'stoerung-6',
      'stoerung-5',
    ])
  })

  /**
   * AK 6: Datum und Uhrzeit, Kennung und Grund. Das Projekt steht seit #1087 in der
   * Gruppenüberschrift statt in jeder Zeile — dieselbe Angabe, nur einmal.
   */
  it('nennt in einer Zeile Zeitpunkt, Kennung und Grund, das Projekt in der Überschrift', async () => {
    api.leitstand.mockResolvedValue(sicht({ stoerungen: [stoerung()] }))

    zeigeSeite()

    const zeile = await screen.findByTestId('stoerung-5')
    expect(within(zeile).getByText('19.09., 23:10')).toBeInTheDocument()
    expect(within(zeile).getByRole('link', { name: 'Run #5' })).toBeInTheDocument()
    expect(within(zeile).getByText(/^Karte #721:/)).toBeInTheDocument()
    expect(within(screen.getByTestId('stoergruppe-kopf-9')).getByText('Mein Projekt')).toBeInTheDocument()
  })

  /**
   * AK 6: derselbe Text wie in der Nachtlauf-Auswertung, gebildet mit derselben Funktion — aber
   * ohne die Dauer, die dort je Paket dabeisteht. Die Störzeile führt den Zeitpunkt schon.
   */
  it('bildet den Grund als „Karte #n: Zustand" ohne Dauer', async () => {
    api.leitstand.mockResolvedValue(sicht({ stoerungen: [stoerung()] }))

    zeigeSeite()

    const zeile = await screen.findByTestId('stoerung-5')
    const grund = within(zeile).getByText(/^Karte #721:/)
    expect(grund).toHaveTextContent('Karte #721: gescheitert')
    expect(grund.textContent).not.toMatch(/\d+\s*(ms|s|min)/)
  })

  /**
   * Ein Befund ohne massgebliches Paket und ohne Grund kann der Server nicht liefern — eine
   * Stoerung ist entweder ein Paket oder ein Lauf ohne Arbeit. Der Zweig steht trotzdem, weil der
   * Typ beides als `null` zulaesst; er zeigt dann nichts statt „undefined".
   */
  it('zeigt bei einem Befund ohne Paket und ohne Grund keinen Text', async () => {
    api.leitstand.mockResolvedValue(
      sicht({
        stoerungen: [
          stoerung({ outcome: { abortReason: null, verdict: 'FAILED', decisiveItem: null, noWorkReason: null } }),
        ],
      }),
    )

    zeigeSeite()

    const zeile = await screen.findByTestId('stoerung-5')
    expect(within(zeile).queryByText(/^Karte #/)).not.toBeInTheDocument()
  })

  /**
   * Der Typ laesst eine Fehlerklasse `null` zu — dann nennt der Grundtext nur den Zustand. Der Fall
   * ist selten (die Datenbank verlangt zu jedem nicht-gruenen Zustand eine Klasse), aber der Zweig
   * steht, und ein Test sagt, was er dann zeigt.
   */
  it('nennt ohne Fehlerklasse nur den Zustand des Pakets', async () => {
    api.leitstand.mockResolvedValue(
      sicht({
        stoerungen: [
          stoerung({
            outcome: {
              abortReason: null,
              verdict: 'FAILED',
              decisiveItem: { cardNumber: 721, state: 'RED', errorClass: null },
              noWorkReason: null,
            },
          }),
        ],
      }),
    )

    zeigeSeite()

    const zeile = await screen.findByTestId('stoerung-5')
    expect(within(zeile).getByText(/^Karte #721:/)).toBeInTheDocument()
  })

  /** AK 7: Der Klick führt zur Auswertung genau dieses Laufs im betroffenen Projekt. */
  it('verweist mit der Kennung auf den Lauf im Projekt', async () => {
    api.leitstand.mockResolvedValue(sicht({ stoerungen: [stoerung()] }))

    zeigeSeite()

    const verweis = await screen.findByRole('link', { name: 'Run #5' })
    expect(verweis).toHaveAttribute('href', '/projects/9/nachtlauf?lauf=5')
  })

  /** AK 8: kein Rückfragen-Dialog, kein Rückgängig — die Zeile ist sofort weg. */
  it('räumt die Zeile ohne Rückfrage weg und ruft den Endpunkt', async () => {
    api.leitstand.mockResolvedValue(
      sicht({
        stoerungen: [stoerung(), stoerung({ nightRunId: 6, projectId: 10, projectName: 'Anderes' })],
      }),
    )

    zeigeSeite()
    const taste = await screen.findByRole('button', {
      name: 'Störung von Mein Projekt, Run #5 löschen',
    })
    await userEvent.click(taste)

    expect(api.quittieren).toHaveBeenCalledWith(5)
    await waitFor(() => expect(screen.queryByTestId('stoerung-5')).not.toBeInTheDocument())
    expect(screen.getByTestId('stoerung-6')).toBeInTheDocument()
    expect(api.leitstand).toHaveBeenCalledTimes(1)
  })

  /** In einer Liste gleichlautender Tasten braucht jede ihre eigene Ansage. */
  it('gibt jeder Taste ein unterscheidbares aria-label', async () => {
    api.leitstand.mockResolvedValue(
      sicht({
        stoerungen: [stoerung(), stoerung({ nightRunId: 6, projectId: 10, projectName: 'Anderes' })],
      }),
    )

    zeigeSeite()

    await screen.findByTestId('stoerung-5')
    const namen = screen.getAllByRole('button').map((b) => b.getAttribute('aria-label'))
    expect(new Set(namen).size).toBe(namen.length)
  })

  /**
   * Der selbst gemeldete Abbruch auf der Übersichtsseite (Issue #1146, AK 4 und AK 8).
   *
   * Geprüft wird beides zugleich: dass der Grund an **beiden** Stellen derselben Seite steht — in
   * der Störzeile und hinter dem Ausgangswort der durchgeführten Zeile — und dass beide Zeilen
   * denselben Melder tragen. Ein Lauf, der abbrach, ist nie gelungen, auch wenn sein maßgebliches
   * Paket nur zurückgestellt oder gelb ist (E13).
   */
  describe('Selbst gemeldeter Abbruch (#1146)', () => {
    /** Mehrzeilig: `kurzGrund` nimmt die erste nicht leere Zeile — hier wird das sichtbar. */
    const GRUND = 'Harter Stopp (dirty-tree)\nnähere Angaben stehen in der Auswertung'
    const KURZ = 'Harter Stopp (dirty-tree)'

    const abgebrochen = (extra: Partial<DisruptionView['outcome']> = {}): DisruptionView =>
      stoerung({
        outcome: {
          abortReason: GRUND,
          verdict: 'FAILED',
          decisiveItem: { cardNumber: 721, state: 'RED', errorClass: 'CHECKS_RED' },
          noWorkReason: null,
          ...extra,
        },
      })

    /** AK 4, erste Hälfte: Die Störzeile nennt den Grund — gekürzt auf eine Zeile. */
    it('zeigt den gekürzten Abbruchgrund in der Störzeile', async () => {
      api.leitstand.mockResolvedValue(sicht({ stoerungen: [abgebrochen()] }))

      zeigeSeite()

      const zeile = await screen.findByTestId('stoerung-5')
      expect(within(zeile).getByText(KURZ)).toBeInTheDocument()
      expect(within(zeile).queryByText(/nähere Angaben/)).not.toBeInTheDocument()
    })

    /** AK 4, zweite Hälfte: Derselbe Grund steht hinter dem Ausgangswort der durchgeführten Zeile. */
    it('zeigt denselben gekürzten Grund hinter dem Ausgangswort der durchgeführten Zeile', async () => {
      api.leitstand.mockResolvedValue(
        sicht({ durchgefuehrte: [abgebrochen()], stoerungen: [abgebrochen()] }),
      )

      zeigeSeite()

      const zeile = await screen.findByTestId('durchgefuehrt-5')
      expect(zeile).toHaveTextContent(`nicht gelungen — ${KURZ}`)
      expect(zeile).not.toHaveTextContent('nähere Angaben')
    })

    /** Ohne Abbruchgrund bleibt die durchgeführte Zeile beim bloßen Ausgangswort. */
    it('lässt die durchgeführte Zeile ohne Abbruchgrund unverändert', async () => {
      api.leitstand.mockResolvedValue(sicht({ durchgefuehrte: [stoerung()] }))

      zeigeSeite()

      const zeile = await screen.findByTestId('durchgefuehrt-5')
      expect(zeile).toHaveTextContent('nicht gelungen')
      expect(zeile).not.toHaveTextContent('—')
    })

    /**
     * E13 und AK 8: derselbe Ausgang überall. Ein Abbruch nach einem zurückgestellten (grau) oder
     * gelben Paket färbte die Störzeile früher grau bzw. bernstein — neben dem Abbruchgrund als
     * Text und neben einer zinnoberroten durchgeführten Zeile derselben Seite.
     */
    it.each([
      ['GREY', 'AWAITING_DECISION'],
      ['YELLOW', 'CHECKS_RED'],
    ] as const)(
      'färbt bei Abbruch mit %s maßgeblichem Paket beide Zeilen zinnober',
      async (state, errorClass) => {
        const lauf = abgebrochen({ decisiveItem: { cardNumber: 721, state, errorClass } })
        api.leitstand.mockResolvedValue(sicht({ durchgefuehrte: [lauf], stoerungen: [lauf] }))

        zeigeSeite()

        const stoerzeile = await screen.findByTestId('stoerung-5')
        expect(within(stoerzeile).getByTestId('led-zinnob')).toBeInTheDocument()
        const durchgefuehrt = screen.getByTestId('durchgefuehrt-5')
        expect(within(durchgefuehrt).getByTestId('led-zinnob')).toBeInTheDocument()
      },
    )

    /** Der Abbruchgrund steht vor dem Grund des maßgeblichen Pakets. */
    it('nimmt den Abbruchgrund vor dem Paketgrund', async () => {
      api.leitstand.mockResolvedValue(sicht({ stoerungen: [abgebrochen()] }))

      zeigeSeite()

      const zeile = await screen.findByTestId('stoerung-5')
      expect(within(zeile).getByText(KURZ)).toBeInTheDocument()
      expect(within(zeile).queryByText(/^Karte #721:/)).not.toBeInTheDocument()
    })
  })

  /** Issue #1135: „Beendete Runs" ist zweigeteilt in diese und die vorige Schicht. */
  describe('Dieser und voriger Zyklus (#1135)', () => {
    beforeEach(() => {
      vi.useFakeTimers({ toFake: ['Date'] })
      vi.setSystemTime(new Date('2026-09-22T11:00:00Z')) // 22.09. 13:00 in Berlin
      // Seit #1152 startet die vorige Schicht zugeklappt. Diese Prüfungen gelten weiter dem
      // Inhalt und seiner Reihenfolge — deshalb klappen sie ihn über den gemerkten Zustand auf
      // statt ihn erst wegzuklicken. Geleert wird der Speicher global in `test/setup.ts`.
      localStorage.setItem('leitstand-voriger-zyklus-offen', 'true')
    })
    afterEach(() => vi.useRealTimers())

    it('zeigt beide Abschnitte mit Überschrift, Spanne und ihren Zeilen', async () => {
      api.leitstand.mockResolvedValue(
        sicht({
          durchgefuehrte: [stoerung({ nightRunId: 7, outcome: { abortReason: null, verdict: 'SUCCEEDED', decisiveItem: null, noWorkReason: null } })],
          durchgefuehrteVoriger: [stoerung({ nightRunId: 6, outcome: { abortReason: null, verdict: 'SUCCEEDED', decisiveItem: null, noWorkReason: null } })],
        }),
      )
      zeigeSeite()

      const dieser = await screen.findByRole('region', { name: 'Begonnen in dieser Schicht' })
      expect(dieser).toHaveTextContent('vom 22.09.2026 auf den 23.09.2026')
      expect(within(dieser).getByTestId('durchgefuehrt-7')).toBeInTheDocument()
      const voriger = screen.getByRole('region', { name: 'Begonnen in der vorigen Schicht' })
      expect(voriger).toHaveTextContent('vom 21.09.2026 auf den 22.09.2026')
      expect(within(voriger).getByTestId('durchgefuehrt-6')).toBeInTheDocument()
      // Die Reihenfolge: dieser Zyklus zuerst.
      expect(dieser.compareDocumentPosition(voriger) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    })

    it('sagt es, wenn in diesem Zyklus noch nichts beendet ist', async () => {
      api.leitstand.mockResolvedValue(sicht({ durchgefuehrteVoriger: [stoerung({ nightRunId: 6 })] }))
      zeigeSeite()

      expect(await screen.findByTestId('keine-durchgefuehrten')).toHaveTextContent(
        'Kein Run dieser Schicht ist beendet.',
      )
      expect(screen.queryByTestId('keine-durchgefuehrten-voriger')).toBeNull()
    })

    it('sagt es, wenn im vorigen Zyklus nichts beendet wurde', async () => {
      api.leitstand.mockResolvedValue(sicht({ durchgefuehrte: [stoerung({ nightRunId: 7 })] }))
      zeigeSeite()

      expect(await screen.findByTestId('keine-durchgefuehrten-voriger')).toHaveTextContent(
        'Kein Run der vorigen Schicht ist beendet.',
      )
    })

    it('markiert eine Zeile mit offener Störung auch im vorigen Zyklus', async () => {
      api.leitstand.mockResolvedValue(
        sicht({ durchgefuehrteVoriger: [stoerung({ nightRunId: 6 })], stoerungen: [stoerung({ nightRunId: 6 })] }),
      )
      zeigeSeite()

      const voriger = await screen.findByRole('region', { name: 'Begonnen in der vorigen Schicht' })
      expect(within(voriger).getByRole('link', { name: 'Zur Störung von Run #6' })).toHaveAttribute('href', '#stoerung-6')
    })
  })

  /**
   * Issue #1152: Die vorige Schicht ist ein- und ausklappbar und startet zugeklappt — gelesen wird
   * an der Stelle fast immer nur die laufende Schicht, und ein Dutzend alter Zeilen schiebt die
   * Störungen aus dem Bild.
   *
   * Ohne falsche Uhr: Geprüft wird das Klappen, und weder Überschrift noch Kennung einer Zeile
   * hängen am Datum.
   */
  describe('Vorige Schicht klappbar (#1152)', () => {
    const zweiVorige = () =>
      sicht({
        durchgefuehrte: [stoerung({ nightRunId: 7 })],
        durchgefuehrteVoriger: [stoerung({ nightRunId: 6 }), stoerung({ nightRunId: 5 })],
      })

    const schalter = () =>
      screen.getByRole('button', {
        name: /^Begonnen in der vorigen Schicht (auf|zu)klappen$/,
      })

    // Der Spion auf `getSelection` hielte sonst bis ans Dateiende.
    afterEach(() => vi.restoreAllMocks())

    it('startet ohne gemerkten Zustand zugeklappt', async () => {
      api.leitstand.mockResolvedValue(zweiVorige())
      zeigeSeite()

      await screen.findByTestId('durchgefuehrt-7')
      expect(screen.getByRole('region', { name: 'Begonnen in der vorigen Schicht' })).toBeInTheDocument()
      expect(schalter()).toHaveAttribute('aria-expanded', 'false')
      expect(screen.queryByTestId('durchgefuehrt-6')).toBeNull()
      expect(screen.queryByTestId('keine-durchgefuehrten-voriger')).toBeNull()
    })

    it('klappt auf einen Klick des Pfeils auf und auf den nächsten wieder zu', async () => {
      api.leitstand.mockResolvedValue(zweiVorige())
      zeigeSeite()

      await screen.findByTestId('durchgefuehrt-7')
      await userEvent.click(schalter())

      expect(schalter()).toHaveAttribute('aria-expanded', 'true')
      const voriger = screen.getByRole('region', { name: 'Begonnen in der vorigen Schicht' })
      expect(within(voriger).getByTestId('durchgefuehrt-6')).toBeInTheDocument()
      expect(within(voriger).getByTestId('durchgefuehrt-5')).toBeInTheDocument()

      await userEvent.click(schalter())
      expect(schalter()).toHaveAttribute('aria-expanded', 'false')
      expect(screen.queryByTestId('durchgefuehrt-6')).toBeNull()
    })

    it('schaltet auch über die Kopfzeile neben dem Pfeil', async () => {
      api.leitstand.mockResolvedValue(zweiVorige())
      zeigeSeite()

      await screen.findByTestId('durchgefuehrt-7')
      await userEvent.click(screen.getByTestId('zyklus-voriger-kopf'))

      expect(screen.getByTestId('durchgefuehrt-6')).toBeInTheDocument()
    })

    it('merkt den aufgeklappten Zustand über einen Neuaufbau der Seite', async () => {
      api.leitstand.mockResolvedValue(zweiVorige())
      const { unmount } = render(<PlattformLeitstandPage />, { wrapper: MemoryRouter })

      await screen.findByTestId('durchgefuehrt-7')
      await userEvent.click(schalter())
      await screen.findByTestId('durchgefuehrt-6')
      unmount()

      zeigeSeite()
      expect(await screen.findByTestId('durchgefuehrt-6')).toBeInTheDocument()
      expect(schalter()).toHaveAttribute('aria-expanded', 'true')
    })

    it('startet mit gemerktem Zustand aufgeklappt', async () => {
      localStorage.setItem('leitstand-voriger-zyklus-offen', 'true')
      api.leitstand.mockResolvedValue(zweiVorige())
      zeigeSeite()

      expect(await screen.findByTestId('durchgefuehrt-6')).toBeInTheDocument()
    })

    /** „Diese Schicht" bleibt, was sie war: ohne Pfeil und immer sichtbar. */
    it('lässt diese Schicht ohne Schalter und immer sichtbar', async () => {
      api.leitstand.mockResolvedValue(zweiVorige())
      zeigeSeite()

      const dieser = await screen.findByRole('region', { name: 'Begonnen in dieser Schicht' })
      expect(within(dieser).getByTestId('durchgefuehrt-7')).toBeInTheDocument()
      expect(within(dieser).queryByRole('button')).toBeNull()
    })

    /** Zugeklappt sagt die Zeile sonst nicht, ob sich das Aufklappen lohnt. */
    it('nennt die Anzahl der Runs der vorigen Schicht in beiden Zuständen', async () => {
      api.leitstand.mockResolvedValue(zweiVorige())
      zeigeSeite()

      expect(await screen.findByTestId('zyklus-voriger-anzahl')).toHaveTextContent('2 Runs')
      await userEvent.click(schalter())
      expect(screen.getByTestId('zyklus-voriger-anzahl')).toHaveTextContent('2 Runs')
    })

    it('zählt einen einzelnen Run im Singular', async () => {
      api.leitstand.mockResolvedValue(sicht({ durchgefuehrteVoriger: [stoerung({ nightRunId: 6 })] }))
      zeigeSeite()

      // Wörtlich: „1 Runs" enthielte „1 Run" und käme sonst durch.
      expect((await screen.findByTestId('zyklus-voriger-anzahl')).textContent).toBe('1 Run')
    })

    /** Eine „0" vor der ersten Antwort wäre eine Behauptung über Daten, die noch niemand kennt. */
    it('nennt vor der ersten Antwort keine Anzahl', () => {
      api.leitstand.mockReturnValue(new Promise(() => {}))
      zeigeSeite()

      expect(screen.getByRole('region', { name: 'Begonnen in der vorigen Schicht' })).toBeInTheDocument()
      expect(screen.queryByTestId('zyklus-voriger-anzahl')).toBeNull()
    })

    /** Wer die Spanne markiert, um sie zu kopieren, will den Abschnitt nicht aufklappen. */
    it('schaltet nicht, wenn im Kopf Text ausgewählt ist', async () => {
      api.leitstand.mockResolvedValue(zweiVorige())
      zeigeSeite()
      await screen.findByTestId('durchgefuehrt-7')
      vi.spyOn(window, 'getSelection').mockReturnValue({
        toString: () => 'vom 21.09.2026',
      } as unknown as Selection)

      await userEvent.click(screen.getByTestId('zyklus-voriger-kopf'))

      expect(screen.queryByTestId('durchgefuehrt-6')).toBeNull()
    })

    /** `getSelection()` darf `null` liefern — „keine Auswahl" ist kein Grund, stumm zu bleiben. */
    it('schaltet, wo der Browser gar keine Auswahl führt', async () => {
      api.leitstand.mockResolvedValue(zweiVorige())
      zeigeSeite()
      await screen.findByTestId('durchgefuehrt-7')
      vi.spyOn(window, 'getSelection').mockReturnValue(null)

      await userEvent.click(screen.getByTestId('zyklus-voriger-kopf'))

      expect(screen.getByTestId('durchgefuehrt-6')).toBeInTheDocument()
    })

    /** Ein gesperrter Speicher kostet die Erinnerung, nicht die Seite. */
    it('startet zugeklappt und bleibt bedienbar, wenn der Speicher wirft', async () => {
      vi.stubGlobal('localStorage', throwingStorage())
      api.leitstand.mockResolvedValue(zweiVorige())
      zeigeSeite()

      await screen.findByTestId('durchgefuehrt-7')
      expect(screen.queryByTestId('durchgefuehrt-6')).toBeNull()
      await userEvent.click(schalter())
      expect(screen.getByTestId('durchgefuehrt-6')).toBeInTheDocument()
    })
  })

  /**
   * Issue #1140: Wie viele beendete Runs man sieht, stellt der Mensch ein — 10, 20 oder alle.
   *
   * Gezählt wird über **beide** Abschnitte zusammen: Die Einstellung beantwortet „wie viele Runs
   * sehe ich", und je Abschnitt zeigte „10" bis zu zwanzig Zeilen. Die vorige Schicht startet seit
   * #1152 zugeklappt; diese Prüfungen gelten dem Begrenzen und klappen sie deshalb über den
   * gemerkten Zustand auf.
   */
  describe('Anzahl beendeter Runs einstellbar (#1140)', () => {
    /** `anzahl` Zeilen mit fortlaufenden Kennungen ab `ab`. */
    const zeilen = (anzahl: number, ab: number): DisruptionView[] =>
      Array.from({ length: anzahl }, (_, i) => stoerung({ nightRunId: ab + i }))

    const taste = (name: string) => screen.getByRole('button', { name })

    beforeEach(() => {
      localStorage.setItem('leitstand-voriger-zyklus-offen', 'true')
    })

    it('zeigt vorgegeben 10 Zeilen und nennt die ausgeblendeten', async () => {
      api.leitstand.mockResolvedValue(sicht({ durchgefuehrteVoriger: zeilen(25, 100) }))
      zeigeSeite()

      await screen.findByTestId('durchgefuehrt-100')
      expect(screen.getAllByTestId(/^durchgefuehrt-/)).toHaveLength(10)
      expect(screen.getByTestId('durchgefuehrt-109')).toBeInTheDocument()
      expect(screen.queryByTestId('durchgefuehrt-110')).toBeNull()
      expect(screen.getByTestId('ausgeblendet-hinweis')).toHaveTextContent(
        '15 weitere Runs ausgeblendet',
      )
      expect(taste('Beendete Runs: 10')).toHaveAttribute('aria-pressed', 'true')
      expect(taste('Beendete Runs: alle')).toHaveAttribute('aria-pressed', 'false')
    })

    it('zeigt nach Klick auf 20 zwanzig und nach Klick auf alle jede Zeile', async () => {
      api.leitstand.mockResolvedValue(sicht({ durchgefuehrteVoriger: zeilen(25, 100) }))
      zeigeSeite()

      await screen.findByTestId('durchgefuehrt-100')
      await userEvent.click(taste('Beendete Runs: 20'))

      expect(screen.getAllByTestId(/^durchgefuehrt-/)).toHaveLength(20)
      expect(screen.getByTestId('ausgeblendet-hinweis')).toHaveTextContent(
        '5 weitere Runs ausgeblendet',
      )
      expect(taste('Beendete Runs: 20')).toHaveAttribute('aria-pressed', 'true')

      await userEvent.click(taste('Beendete Runs: alle'))

      expect(screen.getAllByTestId(/^durchgefuehrt-/)).toHaveLength(25)
      expect(screen.queryByTestId('ausgeblendet-hinweis')).toBeNull()
    })

    /** Ein einzelner ausgeblendeter Run steht im Singular da. */
    it('zählt einen einzelnen ausgeblendeten Run im Singular', async () => {
      api.leitstand.mockResolvedValue(sicht({ durchgefuehrteVoriger: zeilen(11, 100) }))
      zeigeSeite()

      await screen.findByTestId('durchgefuehrt-100')
      expect(screen.getByTestId('ausgeblendet-hinweis').textContent).toBe(
        '1 weiterer Run ausgeblendet',
      )
    })

    it('zählt beide Abschnitte zusammen', async () => {
      api.leitstand.mockResolvedValue(
        sicht({ durchgefuehrte: zeilen(4, 200), durchgefuehrteVoriger: zeilen(12, 100) }),
      )
      zeigeSeite()

      await screen.findByTestId('durchgefuehrt-200')
      const dieser = screen.getByRole('region', { name: 'Begonnen in dieser Schicht' })
      const voriger = screen.getByRole('region', { name: 'Begonnen in der vorigen Schicht' })
      expect(within(dieser).getAllByTestId(/^durchgefuehrt-/)).toHaveLength(4)
      expect(within(voriger).getAllByTestId(/^durchgefuehrt-/)).toHaveLength(6)
      expect(within(voriger).getByTestId('durchgefuehrt-105')).toBeInTheDocument()
      expect(screen.queryByTestId('durchgefuehrt-106')).toBeNull()
      expect(screen.getByTestId('ausgeblendet-hinweis')).toHaveTextContent(
        '6 weitere Runs ausgeblendet',
      )
    })

    /**
     * Ein Abschnitt, dessen Zeilen alle ausgeblendet sind, hat sehr wohl welche — der Leersatz
     * „Kein Run … ist beendet." wäre dort schlicht falsch.
     */
    it('sagt am ganz verdrängten Abschnitt „ausgeblendet" statt des Leersatzes', async () => {
      api.leitstand.mockResolvedValue(
        sicht({ durchgefuehrte: zeilen(12, 200), durchgefuehrteVoriger: zeilen(3, 100) }),
      )
      zeigeSeite()

      await screen.findByTestId('durchgefuehrt-200')
      const dieser = screen.getByRole('region', { name: 'Begonnen in dieser Schicht' })
      expect(within(dieser).getAllByTestId(/^durchgefuehrt-/)).toHaveLength(10)
      const voriger = screen.getByRole('region', { name: 'Begonnen in der vorigen Schicht' })
      expect(within(voriger).getByTestId('zyklus-voriger-ausgeblendet')).toHaveTextContent(
        '3 Runs ausgeblendet',
      )
      expect(screen.queryByTestId('keine-durchgefuehrten-voriger')).toBeNull()
    })

    /** Ein Abschnitt ohne Runs behält seinen Leersatz — dort ist nichts verdrängt, dort ist nichts. */
    it('behält den Leersatz eines Abschnitts ohne Runs', async () => {
      api.leitstand.mockResolvedValue(sicht({ durchgefuehrte: zeilen(12, 200) }))
      zeigeSeite()

      await screen.findByTestId('durchgefuehrt-200')
      expect(screen.getByTestId('keine-durchgefuehrten-voriger')).toHaveTextContent(
        'Kein Run der vorigen Schicht ist beendet.',
      )
      expect(screen.queryByTestId('zyklus-voriger-ausgeblendet')).toBeNull()
    })

    it('merkt die Wahl über einen Neuaufbau der Seite', async () => {
      api.leitstand.mockResolvedValue(sicht({ durchgefuehrteVoriger: zeilen(25, 100) }))
      const { unmount } = render(<PlattformLeitstandPage />, { wrapper: MemoryRouter })

      await screen.findByTestId('durchgefuehrt-100')
      await userEvent.click(taste('Beendete Runs: 20'))
      unmount()

      zeigeSeite()
      await screen.findByTestId('durchgefuehrt-100')
      expect(screen.getAllByTestId(/^durchgefuehrt-/)).toHaveLength(20)
      expect(taste('Beendete Runs: 20')).toHaveAttribute('aria-pressed', 'true')
    })

    /** Ein unbekannter gemerkter Wert ist kein Wert — dann gilt die Vorgabe. */
    it('fällt bei einem unbekannten gemerkten Wert auf 10 zurück', async () => {
      localStorage.setItem('manban.plattformLeitstand.anzahl', 'siebzehn')
      api.leitstand.mockResolvedValue(sicht({ durchgefuehrteVoriger: zeilen(25, 100) }))
      zeigeSeite()

      await screen.findByTestId('durchgefuehrt-100')
      expect(screen.getAllByTestId(/^durchgefuehrt-/)).toHaveLength(10)
    })

    /** Ein gesperrter Speicher kostet die Erinnerung, nicht die Seite. */
    it('bleibt mit der Vorgabe bedienbar, wenn der Speicher wirft', async () => {
      vi.stubGlobal('localStorage', throwingStorage())
      api.leitstand.mockResolvedValue(sicht({ durchgefuehrte: zeilen(25, 200) }))
      zeigeSeite()

      await screen.findByTestId('durchgefuehrt-200')
      expect(screen.getAllByTestId(/^durchgefuehrt-/)).toHaveLength(10)
      await userEvent.click(taste('Beendete Runs: alle'))
      expect(screen.getAllByTestId(/^durchgefuehrt-/)).toHaveLength(25)
    })

    /** Aktive Runs dürfen nie verschwinden, und eine offene Störung erst recht nicht. */
    it('lässt aktive Runs und Störungen unbegrenzt', async () => {
      const offene = zeilen(12, 300)
      api.leitstand.mockResolvedValue(sicht({ laufende: zeilen(12, 400), stoerungen: offene }))
      zeigeSeite()

      await screen.findByTestId('laufend-400')
      expect(screen.getAllByTestId(/^laufend-/)).toHaveLength(12)
      expect(screen.getAllByTestId(/^stoerung-/)).toHaveLength(12)
    })
  })

  /**
   * Issue #1141: Jede der drei Listen zeigt an jeder Zeile die Art des Laufs — als Symbol
   * unmittelbar nach dem Lämpchen, nicht mehr als Textmarke (#1128).
   */
  it('zeigt in allen drei Listen die Art des Laufs als Symbol nach dem Lämpchen', async () => {
    api.leitstand.mockResolvedValue(
      sicht({
        laufende: [{ ...stoerung({ nightRunId: 8 }), mode: 'CHAIN', outcome: { abortReason: null, verdict: 'RUNNING', decisiveItem: null, noWorkReason: null } }],
        durchgefuehrte: [stoerung({ nightRunId: 5 })],
        stoerungen: [stoerung({ nightRunId: 5 })],
      }),
    )
    zeigeSeite()

    await screen.findByTestId('laufend-8')

    /**
     * Lämpchen und Symbol einer Zeile in Dokumentreihenfolge — das Symbol ist das zweite und steht
     * damit unmittelbar hinter dem Lämpchen.
     */
    const symbolNachLed = (zeile: string) => {
      const [led, symbol] = within(screen.getByTestId(zeile)).getAllByTestId(/^(led-|art-)/)
      expect(led).toHaveAttribute('data-testid', expect.stringMatching(/^led-/))
      return symbol
    }
    expect(symbolNachLed('laufend-8')).toHaveAccessibleName('Kette')
    expect(symbolNachLed('durchgefuehrt-5')).toHaveAccessibleName('Umsetzung')
    expect(symbolNachLed('stoerung-5')).toHaveAccessibleName('Umsetzung')

    // Die Textmarken aus #1128 gibt es nicht mehr.
    expect(screen.queryByTestId('art-laufend-8')).toBeNull()
    expect(screen.queryByTestId('art-durchgefuehrt-5')).toBeNull()
    expect(screen.queryByTestId('art-stoerung-5')).toBeNull()
  })

  /** Und jeder Verweis der Seite ebenso — auch über die drei Bereiche hinweg. */
  it('gibt jedem Verweis eine unterscheidbare Beschriftung', async () => {
    api.leitstand.mockResolvedValue(
      sicht({
        laufende: [
          {
            nightRunId: 8,
            projectId: 9,
            projectName: 'Mein Projekt',
            mode: 'CHAIN',
            startedAt: '2026-09-21T01:10:00Z',
            outcome: { abortReason: null, verdict: 'RUNNING', decisiveItem: null, noWorkReason: null },
          },
        ],
        durchgefuehrte: [stoerung({ nightRunId: 5 })],
        stoerungen: [stoerung({ nightRunId: 5 })],
      }),
    )

    zeigeSeite()

    await screen.findByTestId('durchgefuehrt-5')
    const namen = screen.getAllByRole('link').map((a) => a.textContent + '|' + (a.getAttribute('aria-label') ?? ''))
    expect(new Set(namen).size).toBe(namen.length)
  })

  /**
   * AK 14: Eine leere Fläche wäre von einer kaputten Anzeige nicht zu unterscheiden — und eine
   * Gruppenliste ohne Gruppen (#1087, AK 8) genauso wenig.
   */
  it('sagt ausdrücklich, wenn keine Störung offen ist', async () => {
    api.leitstand.mockResolvedValue(sicht())

    zeigeSeite()

    expect(await screen.findByTestId('keine-stoerungen')).toHaveTextContent('Keine offene Störung.')
    expect(screen.queryAllByTestId(/^stoergruppe-/)).toHaveLength(0)
  })

  /**
   * #1122: Gruppenkopf und Störzeile halten links und rechts denselben Abstand zum Rand wie der
   * Leersatz — sonst klebt der Projektname am linken Rahmen und „Störung löschen" am rechten.
   *
   * Der Vergleichswert wird aus `keine-stoerungen` **gelesen** statt zweimal hingeschrieben: So
   * bleibt die Aussage „derselbe Einzug wie im Leerfall" auch dann wahr, wenn der Wert je wandert.
   * Beide Zustände zusammen gibt es nicht — ist eine Störung offen, verschwindet der Leersatz —,
   * deshalb zwei Darstellungen nacheinander.
   */
  it('rückt Gruppenkopf und Störzeile so weit ein wie den Leersatz', async () => {
    api.leitstand.mockResolvedValue(sicht())
    const view = zeigeSeite()
    const leersatz = getComputedStyle(await screen.findByTestId('keine-stoerungen'))
    const einzug = { paddingLeft: leersatz.paddingLeft, paddingRight: leersatz.paddingRight }
    expect(einzug).toEqual({ paddingLeft: '16px', paddingRight: '16px' })
    view.unmount()

    api.leitstand.mockResolvedValue(sicht({ stoerungen: [stoerung()] }))
    zeigeSeite()

    const kopf = await screen.findByTestId('stoergruppe-kopf-9')
    expect(kopf).toHaveStyle(einzug)
    expect(screen.getByTestId('stoerung-5')).toHaveStyle(einzug)
    // Der Einzug sitzt am Kopf selbst und nicht an einem Rahmen darum — die Trennlinie läuft
    // deshalb weiter über die volle Breite der Platte. Gelesen aus der erzeugten Regel, weil jsdom
    // die Kurzschreibweise mit CSS-Variable im berechneten Stil verwirft (siehe `cssRegel`).
    expect(cssRegel(kopf)).toMatch(/border-bottom: 1px solid/)
  })

  /** AK 3: dasselbe wie auf jeder anderen Admin-Seite — ein Fehlertext statt Inhalt. */
  it('zeigt bei 403 einen Fehlertext statt Inhalt', async () => {
    api.leitstand.mockRejectedValue(new ApiError(403, 'Forbidden'))

    zeigeSeite()

    expect(await screen.findByText('Kein Admin-Zugriff.')).toBeInTheDocument()
    expect(screen.queryByTestId('keine-stoerungen')).not.toBeInTheDocument()
    expect(screen.queryByTestId('keine-laufenden')).not.toBeInTheDocument()
    expect(screen.queryByTestId('keine-durchgefuehrten')).not.toBeInTheDocument()
  })

  it('unterscheidet den Ladefehler vom fehlenden Recht', async () => {
    api.leitstand.mockRejectedValue(new ApiError(500, 'Boom'))

    zeigeSeite()

    expect(await screen.findByText('Laden fehlgeschlagen.')).toBeInTheDocument()
  })

  /**
   * Der Leitstand frischt sich selbst auf (Kriterium 19, Issue #1099).
   *
   * Geprüft wird mit künstlicher Zeit, deshalb ohne `findBy*`: Ein `await act(async () => {})`
   * lässt die angelaufene Antwort durch, ein `advanceTimersByTime` rückt den Takt weiter. So steht
   * in jeder Zusicherung genau der Stand, den die Seite nach *dieser* Zahl von Abrufen zeigt.
   *
   * Die beiden Übergänge ohne äußeres Ereignis — Ablauf der Stillefrist und Nachtwechsel um 12:00 —
   * leistet schon der Abruf selbst: Der Server rechnet den Ausgang bei jedem Abruf neu (#1091).
   * Der letzte Test hier zeigt genau das an der wandernden Zeile.
   */
  describe('Auffrischen (#1099)', () => {
    const laufend = (extra: Partial<DisruptionView> = {}): DisruptionView => ({
      nightRunId: 8,
      projectId: 9,
      projectName: 'Mein Projekt',
      mode: 'CHAIN',
      startedAt: '2026-09-21T01:10:00Z',
      outcome: { abortReason: null, verdict: 'RUNNING', decisiveItem: null, noWorkReason: null },
      ...extra,
    })

    /** Lässt die angelaufene Antwort durch, ohne die Uhr zu bewegen. */
    const antwortDurchlassen = async () => {
      await act(async () => {
        await Promise.resolve()
      })
    }

    /** Rückt die künstliche Uhr und lässt die dadurch ausgelösten Antworten durch. */
    const zeitVergehtLassen = async (ms: number) => {
      await act(async () => {
        vi.advanceTimersByTime(ms)
      })
    }

    /**
     * Setzt `document.visibilityState`. jsdom führt den Wert als Getter auf `Document.prototype`;
     * eine eigene Eigenschaft auf `document` überdeckt ihn und lässt sich wieder abräumen.
     */
    const sichtbarkeit = (wert: DocumentVisibilityState) => {
      Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => wert })
    }

    beforeEach(() => {
      vi.useFakeTimers()
    })

    afterEach(() => {
      vi.useRealTimers()
      Reflect.deleteProperty(document, 'visibilityState')
    })

    it('ruft nach 30 Sekunden erneut ab und nach 60 ein zweites Mal', async () => {
      api.leitstand.mockResolvedValue(sicht())

      zeigeSeite()
      await antwortDurchlassen()
      expect(api.leitstand).toHaveBeenCalledTimes(1)

      await zeitVergehtLassen(30_000)
      expect(api.leitstand).toHaveBeenCalledTimes(2)

      await zeitVergehtLassen(30_000)
      expect(api.leitstand).toHaveBeenCalledTimes(3)
    })

    /** Ein verborgenes Fenster braucht keine Abrufe — beim Wiedersehen holt der Fokus-Zuhörer nach. */
    it('ruft bei verborgenem Fenster nicht ab und holt beim Wiedersichtbarwerden sofort nach', async () => {
      api.leitstand.mockResolvedValue(sicht())

      zeigeSeite()
      await antwortDurchlassen()
      expect(api.leitstand).toHaveBeenCalledTimes(1)

      sichtbarkeit('hidden')
      await zeitVergehtLassen(90_000)
      expect(api.leitstand).toHaveBeenCalledTimes(1)

      sichtbarkeit('visible')
      await act(async () => {
        document.dispatchEvent(new Event('visibilitychange'))
      })
      expect(api.leitstand).toHaveBeenCalledTimes(2)
    })

    it('räumt den Zeitgeber beim Verlassen der Seite ab', async () => {
      api.leitstand.mockResolvedValue(sicht())

      const { unmount } = zeigeSeite()
      await antwortDurchlassen()
      unmount()

      await zeitVergehtLassen(90_000)
      expect(api.leitstand).toHaveBeenCalledTimes(1)
    })

    /**
     * Ein gescheitertes Auffrischen löscht nichts: Ein einzelner Netzaussetzer um 03:00 wischte
     * sonst den ganzen Leitstand weg — genau das Bild, das die Kriterien 4 und 14 vermeiden.
     */
    it('lässt nach einem gescheiterten Folgeabruf den letzten Stand unverändert stehen', async () => {
      api.leitstand
        .mockResolvedValueOnce(sicht({ laufende: [laufend()] }))
        .mockRejectedValueOnce(new ApiError(500, 'Boom'))

      zeigeSeite()
      await antwortDurchlassen()
      expect(screen.getByTestId('laufend-8')).toBeInTheDocument()

      await zeitVergehtLassen(30_000)

      expect(api.leitstand).toHaveBeenCalledTimes(2)
      expect(screen.getByTestId('laufend-8')).toHaveTextContent('läuft seit 03:10')
      expect(screen.queryByText('Laden fehlgeschlagen.')).not.toBeInTheDocument()
    })

    it('zeigt den Fehlertext, wenn schon das Erstladen scheitert', async () => {
      api.leitstand.mockRejectedValue(new ApiError(500, 'Boom'))

      zeigeSeite()
      await antwortDurchlassen()

      expect(screen.getByText('Laden fehlgeschlagen.')).toBeInTheDocument()
      expect(screen.queryByTestId('keine-laufenden')).not.toBeInTheDocument()
    })

    /** Eine Rolle bildet sich nicht von selbst zurück — wem sie entzogen wurde, sieht nichts mehr. */
    it('zeigt bei 403 im Folgeabruf den Fehlertext, auch wenn zuvor Daten da waren', async () => {
      api.leitstand
        .mockResolvedValueOnce(sicht({ laufende: [laufend()] }))
        .mockRejectedValueOnce(new ApiError(403, 'Forbidden'))

      zeigeSeite()
      await antwortDurchlassen()
      expect(screen.getByTestId('laufend-8')).toBeInTheDocument()

      await zeitVergehtLassen(30_000)

      expect(screen.getByText('Kein Admin-Zugriff.')).toBeInTheDocument()
      expect(screen.queryByTestId('laufend-8')).not.toBeInTheDocument()
    })

    /**
     * Der Übergang ohne äußeres Ereignis: Niemand klickt, niemand lädt neu — der Server rechnet
     * den Ausgang beim zweiten Abruf neu, und die Zeile wandert von selbst nach unten.
     */
    it('lässt einen beendeten Lauf ohne Zutun aus dem oberen in den unteren Bereich wandern', async () => {
      const beendet = laufend({
        outcome: {
          abortReason: null,
          verdict: 'FAILED',
          decisiveItem: { cardNumber: 721, state: 'RED', errorClass: 'CHECKS_RED' },
          noWorkReason: null,
        },
      })
      api.leitstand
        .mockResolvedValueOnce(sicht({ laufende: [laufend()] }))
        .mockResolvedValueOnce(sicht({ durchgefuehrte: [beendet] }))

      zeigeSeite()
      await antwortDurchlassen()
      expect(screen.getByTestId('laufend-8')).toBeInTheDocument()
      expect(screen.queryByTestId('durchgefuehrt-8')).not.toBeInTheDocument()

      await zeitVergehtLassen(30_000)

      expect(screen.queryByTestId('laufend-8')).not.toBeInTheDocument()
      expect(screen.getByTestId('durchgefuehrt-8')).toHaveTextContent('nicht gelungen')
    })
  })

  /**
   * Gruppierung nach Projekt (#1087).
   *
   * Wer mehrere Projekte betreibt, liest in einer flachen Liste abwechselnd Namen statt Befunde.
   * Geprüft wird deshalb beides: dass die Zeilen eines Projekts beieinanderstehen, **und** dass
   * das Gruppieren die Reihenfolge der Server-Antwort nirgends anfasst.
   */
  describe('Gruppierung nach Projekt (#1087)', () => {
    /** Die Antwort des Servers, verschränkt: Alpha, Beta, Alpha. */
    const verschraenkt = () =>
      sicht({
        stoerungen: [
          stoerung({ nightRunId: 9, projectId: 1, projectName: 'Alpha', startedAt: '2026-09-19T21:10:00Z' }),
          stoerung({ nightRunId: 8, projectId: 2, projectName: 'Beta', startedAt: '2026-09-18T21:10:00Z' }),
          stoerung({ nightRunId: 7, projectId: 1, projectName: 'Alpha', startedAt: '2026-09-17T21:10:00Z' }),
        ],
      })

    const zeilenIn = (element: HTMLElement) =>
      within(element)
        .getAllByTestId(/^stoerung-/)
        .map((z) => z.getAttribute('data-testid'))

    /** AK 1: zwei Gruppen aus drei verschränkten Zeilen, und keine Zeile außerhalb ihrer Gruppe. */
    it('fasst die Zeilen eines Projekts zu einer Gruppe zusammen', async () => {
      api.leitstand.mockResolvedValue(verschraenkt())

      zeigeSeite()

      const gruppen = await screen.findAllByTestId(/^stoergruppe-\d+$/)
      expect(gruppen.map((g) => g.getAttribute('data-testid'))).toEqual([
        'stoergruppe-1',
        'stoergruppe-2',
      ])
      expect(zeilenIn(gruppen[0])).toEqual(['stoerung-9', 'stoerung-7'])
      expect(zeilenIn(gruppen[1])).toEqual(['stoerung-8'])
      // Keine Zeile steht außerhalb ihrer Gruppe: alle drei sind in den Gruppen aufgegangen.
      expect(screen.getAllByTestId(/^stoerung-/)).toHaveLength(3)
    })

    /** AK 2: die jüngste Störung bestimmt die Gruppe zuoberst, nicht die Zahl ihrer Zeilen. */
    it('stellt die Gruppe der jüngsten Störung zuoberst, auch wenn sie die kleinere ist', async () => {
      api.leitstand.mockResolvedValue(
        sicht({
          stoerungen: [
            stoerung({ nightRunId: 9, projectId: 2, projectName: 'Beta', startedAt: '2026-09-19T21:10:00Z' }),
            stoerung({ nightRunId: 8, projectId: 1, projectName: 'Alpha', startedAt: '2026-09-18T21:10:00Z' }),
            stoerung({ nightRunId: 7, projectId: 1, projectName: 'Alpha', startedAt: '2026-09-17T21:10:00Z' }),
          ],
        }),
      )

      zeigeSeite()

      const gruppen = await screen.findAllByTestId(/^stoergruppe-\d+$/)
      expect(gruppen.map((g) => g.getAttribute('data-testid'))).toEqual([
        'stoergruppe-2',
        'stoergruppe-1',
      ])
    })

    /**
     * AK 3: Innerhalb der Gruppe gilt die Reihenfolge der Antwort — hier bewusst gegen den
     * Zeitpunkt gestellt. Ein zweites Sortieren im Browser fiele genau hier auf.
     */
    it('lässt die Reihenfolge der Server-Antwort innerhalb einer Gruppe unangetastet', async () => {
      api.leitstand.mockResolvedValue(
        sicht({
          stoerungen: [
            stoerung({ nightRunId: 3, projectId: 1, projectName: 'Alpha', startedAt: '2026-09-17T21:10:00Z' }),
            stoerung({ nightRunId: 4, projectId: 1, projectName: 'Alpha', startedAt: '2026-09-19T21:10:00Z' }),
          ],
        }),
      )

      zeigeSeite()

      const gruppe = await screen.findByTestId('stoergruppe-1')
      expect(zeilenIn(gruppe)).toEqual(['stoerung-3', 'stoerung-4'])
    })

    /** AK 4: Projektname und Zahl der offenen Störungen, Einzahl wie Mehrzahl. */
    it('nennt in der Überschrift den Projektnamen und die Zahl der offenen Störungen', async () => {
      api.leitstand.mockResolvedValue(verschraenkt())

      zeigeSeite()

      const alpha = within(await screen.findByTestId('stoergruppe-kopf-1'))
      expect(alpha.getByText('Alpha')).toBeInTheDocument()
      expect(alpha.getByText('2 Störungen')).toBeInTheDocument()
      const beta = within(screen.getByTestId('stoergruppe-kopf-2'))
      expect(beta.getByText('Beta')).toBeInTheDocument()
      expect(beta.getByText('1 Störung')).toBeInTheDocument()
    })

    /**
     * AK 5: Der Name steht nur noch in der Überschrift. Das `aria-label` behält ihn — ohne ihn
     * wären zwei Tasten verschiedener Projekte für ein Vorlesewerkzeug nicht zu unterscheiden.
     */
    it('lässt den Projektnamen aus der Zeile weg, behält ihn aber im aria-label der Taste', async () => {
      api.leitstand.mockResolvedValue(sicht({ stoerungen: [stoerung()] }))

      zeigeSeite()

      const zeile = await screen.findByTestId('stoerung-5')
      expect(within(zeile).queryByText('Mein Projekt')).not.toBeInTheDocument()
      expect(
        within(zeile).getByRole('button', { name: 'Störung von Mein Projekt, Run #5 löschen' }),
      ).toBeInTheDocument()
    })

    /**
     * AK 6: Die Gruppe ist auch ohne Sicht eine Gruppe — eine eigene, benannte Liste je Projekt
     * statt einer durchlaufenden Liste mit Zwischenüberschriften.
     */
    it('macht jede Gruppe als Liste unter ihrem Projektnamen auffindbar', async () => {
      api.leitstand.mockResolvedValue(verschraenkt())

      zeigeSeite()

      expect(zeilenIn(await screen.findByRole('list', { name: 'Alpha' }))).toEqual([
        'stoerung-9',
        'stoerung-7',
      ])
      expect(zeilenIn(screen.getByRole('list', { name: 'Beta' }))).toEqual(['stoerung-8'])
    })

    /** AK 7: Mit der letzten Störung eines Projekts geht seine Überschrift mit. */
    it('nimmt mit der letzten Störung eines Projekts auch dessen Gruppe weg', async () => {
      api.leitstand.mockResolvedValue(verschraenkt())

      zeigeSeite()

      await userEvent.click(
        await screen.findByRole('button', { name: 'Störung von Beta, Run #8 löschen' }),
      )

      await waitFor(() => expect(screen.queryByTestId('stoergruppe-2')).not.toBeInTheDocument())
      expect(screen.queryByText('Beta')).not.toBeInTheDocument()
      expect(zeilenIn(screen.getByTestId('stoergruppe-1'))).toEqual(['stoerung-9', 'stoerung-7'])
      expect(screen.getByText('2 Störungen')).toBeInTheDocument()
    })

    /** AK 7: Quittiert man eine von zweien, bleibt die Gruppe — mit der neuen Zahl. */
    it('behält die Gruppe, solange eine Störung des Projekts offen bleibt', async () => {
      api.leitstand.mockResolvedValue(verschraenkt())

      zeigeSeite()

      await userEvent.click(
        await screen.findByRole('button', { name: 'Störung von Alpha, Run #9 löschen' }),
      )

      await waitFor(() => expect(screen.queryByTestId('stoerung-9')).not.toBeInTheDocument())
      expect(zeilenIn(screen.getByTestId('stoergruppe-1'))).toEqual(['stoerung-7'])
      expect(within(screen.getByTestId('stoergruppe-kopf-1')).getByText('1 Störung')).toBeInTheDocument()
    })
  })
})
