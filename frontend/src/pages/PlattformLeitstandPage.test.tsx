import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import type { DisruptionView, LeitstandView } from '../api/plattformLeitstand'
import { plattformLeitstandApi } from '../api/plattformLeitstand'
import { cssRegel } from '../test/cssRegel'
import PlattformLeitstandPage from './PlattformLeitstandPage'

vi.mock('../api/plattformLeitstand', async () => {
  const echt = await vi.importActual<typeof import('../api/plattformLeitstand')>(
    '../api/plattformLeitstand',
  )
  return { ...echt, plattformLeitstandApi: { leitstand: vi.fn(), quittieren: vi.fn() } }
})

const api = vi.mocked(plattformLeitstandApi)

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
    ...teil,
  })

  const zeigeSeite = () => render(<PlattformLeitstandPage />, { wrapper: MemoryRouter })

  beforeEach(() => {
    vi.clearAllMocks()
    api.quittieren.mockResolvedValue(undefined)
  })

  /** Kriterium 18: die drei Bereiche untereinander, in dieser Ordnung. */
  it('stellt die drei Bereiche in der Ordnung Aktive Läufe, Beendete Läufe, Störungen', async () => {
    api.leitstand.mockResolvedValue(sicht({ stoerungen: [stoerung()] }))

    zeigeSeite()

    await screen.findByTestId('stoerung-5')
    expect(screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)).toEqual([
      'Aktive Läufe',
      'Beendete Läufe',
      'Störungen',
    ])
  })

  /**
   * Der Bereich „Aktive Läufe" (Kriterien 1–4, 11).
   *
   * Der Puls hält bei `prefers-reduced-motion` von selbst an — die globale Regel im Theme greift
   * für jede Animation; hier steht deshalb nur, **dass** die LED pulst.
   */
  describe('Aktive Läufe (#1098, benannt in #1102)', () => {
    const laufend = (extra: Partial<DisruptionView> = {}): DisruptionView => ({
      nightRunId: 8,
      projectId: 9,
      projectName: 'Mein Projekt',
      mode: 'CHAIN',
      startedAt: '2026-09-21T01:10:00Z',
      outcome: { verdict: 'RUNNING', decisiveItem: null, noWorkReason: null },
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
      expect(within(zeile).getByRole('link', { name: 'Lauf #8 von Mein Projekt' })).toHaveAttribute(
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
        'Gerade läuft kein Lauf.',
      )
    })
  })

  /** Der Bereich „Beendete Läufe" (Kriterien 9–14). */
  describe('Beendete Läufe (#1098, benannt in #1102)', () => {
    const durchgefuehrt = (extra: Partial<DisruptionView> = {}): DisruptionView => ({
      nightRunId: 5,
      projectId: 9,
      projectName: 'Mein Projekt',
      mode: 'IMPLEMENTATION',
      startedAt: '2026-09-19T21:10:00Z',
      outcome: { verdict: 'SUCCEEDED', decisiveItem: null, noWorkReason: null },
      ...extra,
    })

    it('nennt Projekt, Startzeitpunkt und Kennung', async () => {
      api.leitstand.mockResolvedValue(sicht({ durchgefuehrte: [durchgefuehrt()] }))

      zeigeSeite()

      const zeile = await screen.findByTestId('durchgefuehrt-5')
      expect(within(zeile).getByText('Mein Projekt')).toBeInTheDocument()
      expect(within(zeile).getByText('19.09., 23:10')).toBeInTheDocument()
      expect(within(zeile).getByRole('link', { name: 'Lauf #5 von Mein Projekt' })).toBeInTheDocument()
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
    it('zeigt NO_WORK als „nichts zu tun" mit grauem, ruhendem Melder', async () => {
      api.leitstand.mockResolvedValue(
        sicht({
          durchgefuehrte: [
            durchgefuehrt({
              outcome: {
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
      expect(within(zeile).getByText('nichts zu tun')).toBeInTheDocument()
      expect(within(zeile).getByTestId('led-grau')).toHaveAttribute('data-puls', 'aus')
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
              outcome: { verdict: 'FAILED', decisiveItem: null, noWorkReason: 'Ready war leer' },
            }),
          ],
        }),
      )

      zeigeSeite()

      await screen.findByTestId('durchgefuehrt-5')
      expect(screen.getByRole('link', { name: 'Lauf #5 von Gelungen' })).toHaveAttribute(
        'href',
        '/projects/9/nachtlauf?lauf=5',
      )
      expect(screen.getByRole('link', { name: 'Lauf #6 von Gescheitert' })).toHaveAttribute(
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
      expect(within(mit).getByRole('link', { name: 'Zur Störung von Lauf #5' })).toHaveAttribute(
        'href',
        '#stoerung-5',
      )
      expect(screen.getByTestId('stoerung-5')).toHaveAttribute('id', 'stoerung-5')
      const ohne = screen.getByTestId('durchgefuehrt-6')
      expect(
        within(ohne).queryByRole('link', { name: 'Zur Störung von Lauf #6' }),
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
        await screen.findByRole('button', { name: 'Störung von Mein Projekt, Lauf #5 löschen' }),
      )

      await waitFor(() => expect(screen.queryByTestId('stoerung-5')).not.toBeInTheDocument())
      const zeile = screen.getByTestId('durchgefuehrt-5')
      expect(within(zeile).queryByRole('link', { name: 'Zur Störung von Lauf #5' })).not.toBeInTheDocument()
      expect(within(zeile).getByText('nicht gelungen')).toBeInTheDocument()
      expect(within(zeile).getByRole('link', { name: 'Lauf #5 von Mein Projekt' })).toBeInTheDocument()
      expect(api.leitstand).toHaveBeenCalledTimes(1)
    })

    /** Kriterium 14: ausdrücklicher Satz statt leerer Fläche. */
    it('sagt ausdrücklich, wenn in dieser Nacht noch kein Lauf beendet ist', async () => {
      api.leitstand.mockResolvedValue(sicht())

      zeigeSeite()

      expect(await screen.findByTestId('keine-durchgefuehrten')).toHaveTextContent(
        'In diesem Zyklus wurde noch kein Lauf beendet.',
      )
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
    expect(within(zeile).getByRole('link', { name: 'Lauf #5' })).toBeInTheDocument()
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

  /** Ein Lauf ohne Arbeit hat kein Paket — sein Grund ist der Text selbst (#1069). */
  it('zeigt beim Lauf ohne Arbeit den Grund wörtlich', async () => {
    api.leitstand.mockResolvedValue(
      sicht({
        stoerungen: [
          stoerung({
            outcome: { verdict: 'FAILED', decisiveItem: null, noWorkReason: 'Ready war leer' },
          }),
        ],
      }),
    )

    zeigeSeite()

    const zeile = await screen.findByTestId('stoerung-5')
    expect(within(zeile).getByText('Ready war leer')).toBeInTheDocument()
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
          stoerung({ outcome: { verdict: 'FAILED', decisiveItem: null, noWorkReason: null } }),
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

    const verweis = await screen.findByRole('link', { name: 'Lauf #5' })
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
      name: 'Störung von Mein Projekt, Lauf #5 löschen',
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

  /** Issue #1135: „Beendete Läufe" ist zweigeteilt in diesen und den vorigen Zyklus. */
  describe('Dieser und voriger Zyklus (#1135)', () => {
    beforeEach(() => {
      vi.useFakeTimers({ toFake: ['Date'] })
      vi.setSystemTime(new Date('2026-09-22T11:00:00Z')) // 22.09. 13:00 in Berlin
    })
    afterEach(() => vi.useRealTimers())

    it('zeigt beide Abschnitte mit Überschrift, Spanne und ihren Zeilen', async () => {
      api.leitstand.mockResolvedValue(
        sicht({
          durchgefuehrte: [stoerung({ nightRunId: 7, outcome: { verdict: 'SUCCEEDED', decisiveItem: null, noWorkReason: null } })],
          durchgefuehrteVoriger: [stoerung({ nightRunId: 6, outcome: { verdict: 'SUCCEEDED', decisiveItem: null, noWorkReason: null } })],
        }),
      )
      zeigeSeite()

      const dieser = await screen.findByRole('region', { name: 'Dieser Zyklus' })
      expect(dieser).toHaveTextContent('vom 22.09.2026 auf den 23.09.2026')
      expect(within(dieser).getByTestId('durchgefuehrt-7')).toBeInTheDocument()
      const voriger = screen.getByRole('region', { name: 'Voriger Zyklus' })
      expect(voriger).toHaveTextContent('vom 21.09.2026 auf den 22.09.2026')
      expect(within(voriger).getByTestId('durchgefuehrt-6')).toBeInTheDocument()
      // Die Reihenfolge: dieser Zyklus zuerst.
      expect(dieser.compareDocumentPosition(voriger) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    })

    it('sagt es, wenn in diesem Zyklus noch nichts beendet ist', async () => {
      api.leitstand.mockResolvedValue(sicht({ durchgefuehrteVoriger: [stoerung({ nightRunId: 6 })] }))
      zeigeSeite()

      expect(await screen.findByTestId('keine-durchgefuehrten')).toHaveTextContent(
        'In diesem Zyklus wurde noch kein Lauf beendet.',
      )
      expect(screen.queryByTestId('keine-durchgefuehrten-voriger')).toBeNull()
    })

    it('sagt es, wenn im vorigen Zyklus nichts beendet wurde', async () => {
      api.leitstand.mockResolvedValue(sicht({ durchgefuehrte: [stoerung({ nightRunId: 7 })] }))
      zeigeSeite()

      expect(await screen.findByTestId('keine-durchgefuehrten-voriger')).toHaveTextContent(
        'Im vorigen Zyklus wurde kein Lauf beendet.',
      )
    })

    it('markiert eine Zeile mit offener Störung auch im vorigen Zyklus', async () => {
      api.leitstand.mockResolvedValue(
        sicht({ durchgefuehrteVoriger: [stoerung({ nightRunId: 6 })], stoerungen: [stoerung({ nightRunId: 6 })] }),
      )
      zeigeSeite()

      const voriger = await screen.findByRole('region', { name: 'Voriger Zyklus' })
      expect(within(voriger).getByRole('link', { name: 'Zur Störung von Lauf #6' })).toHaveAttribute('href', '#stoerung-6')
    })
  })

  /** Issue #1128: Jede der drei Listen zeigt an jeder Zeile die Art des Laufs als Marke. */
  it('zeigt in allen drei Listen die Art des Laufs', async () => {
    api.leitstand.mockResolvedValue(
      sicht({
        laufende: [{ ...stoerung({ nightRunId: 8 }), mode: 'CHAIN', outcome: { verdict: 'RUNNING', decisiveItem: null, noWorkReason: null } }],
        durchgefuehrte: [stoerung({ nightRunId: 5 })],
        stoerungen: [stoerung({ nightRunId: 5 })],
      }),
    )
    zeigeSeite()

    expect(await screen.findByTestId('art-laufend-8')).toHaveTextContent('Kette')
    expect(screen.getByTestId('art-durchgefuehrt-5')).toHaveTextContent('Umsetzung')
    expect(screen.getByTestId('art-stoerung-5')).toHaveTextContent('Umsetzung')
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
            outcome: { verdict: 'RUNNING', decisiveItem: null, noWorkReason: null },
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
      outcome: { verdict: 'RUNNING', decisiveItem: null, noWorkReason: null },
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
        within(zeile).getByRole('button', { name: 'Störung von Mein Projekt, Lauf #5 löschen' }),
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
        await screen.findByRole('button', { name: 'Störung von Beta, Lauf #8 löschen' }),
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
        await screen.findByRole('button', { name: 'Störung von Alpha, Lauf #9 löschen' }),
      )

      await waitFor(() => expect(screen.queryByTestId('stoerung-9')).not.toBeInTheDocument())
      expect(zeilenIn(screen.getByTestId('stoergruppe-1'))).toEqual(['stoerung-7'])
      expect(within(screen.getByTestId('stoergruppe-kopf-1')).getByText('1 Störung')).toBeInTheDocument()
    })
  })
})
