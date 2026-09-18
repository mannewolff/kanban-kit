import { ThemeProvider } from '@mui/material/styles'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type {
  NightRunUsageApi,
  VerbrauchAngaben,
  VerbrauchAufteilung,
  VerbrauchKennzahlen,
  VerbrauchZeitraum,
  VerbrauchZeitraumArt,
} from '../../api/nightRunUsage'
import { KEIN_LAUF_TEXT } from '../../lib/verbrauchZeitraum'
import { theme } from '../../theme'
import { NachtlaufVerbrauchZeitraum } from './NachtlaufVerbrauchZeitraum'

/**
 * Die Zeitraum-Sicht der Verbrauchs-Auswertung (Issue #942, #926 AK 5–9), seit #987 in der Gestalt
 * der Kupferwarte nach `docs/mockup-nachtlauf-verbrauch.html`.
 */

const nichts: VerbrauchAngaben = {
  costUsd: null,
  inputTokens: null,
  outputTokens: null,
  cachedInputTokens: null,
  cachedInputSharePercent: null,
}

const kosten = (costUsd: number | null): VerbrauchAngaben => ({ ...nichts, costUsd })

const aufteilung = (
  total: VerbrauchAngaben,
  cardShare: VerbrauchAngaben = nichts,
  remainder: VerbrauchAngaben = nichts,
): VerbrauchAufteilung => ({ total, cardShare, remainder })

const LEER = aufteilung(nichts)

/**
 * Der Verbrauch einer Antwort nach Gattung (Issue #1016): `nachtlauf` ist der Anteil, den die
 * Nachtlauf-Seite zeigt, `sitzungen` der interaktive. `gesamt` ist standardmäßig der
 * Nachtlauf-Anteil — das trifft zu, solange keine Sitzung im Spiel ist; wo eine ist, nennt der Test
 * die Gesamtsumme ausdrücklich, statt sie hier zu rechnen.
 */
const verbrauch = (
  nachtlauf: VerbrauchAufteilung,
  sitzungen: VerbrauchAufteilung = LEER,
  gesamt: VerbrauchAufteilung = nachtlauf,
): Pick<VerbrauchKennzahlen, 'usage' | 'usageByKind'> => ({
  usage: gesamt,
  usageByKind: { night: nachtlauf, interactive: sitzungen },
})

const TAGE: Record<VerbrauchZeitraumArt, [string, string]> = {
  DAY: ['2026-09-15', '2026-09-15'],
  WEEK: ['2026-09-07', '2026-09-13'],
  MONTH: ['2026-08-01', '2026-08-31'],
}

const kennzahlen = (
  type: VerbrauchZeitraumArt,
  werte: Partial<VerbrauchKennzahlen> = {},
): VerbrauchKennzahlen => ({
  type,
  firstDay: TAGE[type][0],
  lastDay: TAGE[type][1],
  from: '2026-09-07T10:00:00Z',
  to: '2026-09-14T10:00:00Z',
  coverage: 'COMPLETE',
  noRuns: false,
  runCount: 4,
  nightRunCount: 4,
  interactiveRunCount: 0,
  durationMs: 1000,
  cardCount: 3,
  interactiveUsageSince: null,
  ...verbrauch(aufteilung(kosten(6.5), kosten(5), kosten(1.5))),
  ...werte,
})

const zeitraum = (
  type: VerbrauchZeitraumArt,
  current: Partial<VerbrauchKennzahlen> = {},
): VerbrauchZeitraum => ({
  current: kennzahlen(type, current),
  previous: kennzahlen(type, {
    firstDay: '2026-08-31',
    lastDay: '2026-09-06',
    runCount: 2,
    cardCount: 1,
    ...verbrauch(aufteilung(kosten(4), kosten(3), kosten(1))),
  }),
  nights: [
    { night: '2026-09-08', runCount: 2, cardCount: 1, ...verbrauch(aufteilung(kosten(3))), aborted: false },
    { night: '2026-09-09', runCount: 1, cardCount: 1, ...verbrauch(LEER), aborted: true },
  ],
  epics: [
    { epicId: 1, shortcode: 'PLANEN', title: 'Planen', cardCount: 2, usage: kosten(4) },
  ],
  withoutEpic: { epicId: null, shortcode: null, title: null, cardCount: 1, usage: kosten(1) },
  epicsOverlap: false,
})

const apiMit = (antwort: (type: VerbrauchZeitraumArt) => Promise<VerbrauchZeitraum>) => ({
  // Über die Signatur der API getippt: Die Tests lesen den Rückschritt aus den aufgezeichneten
  // Aufrufen, und die kennt der Mock nur mit allen drei Parametern.
  period: vi.fn<NightRunUsageApi['period']>((_, type) => antwort(type)),
})

const zeige = (api: ReturnType<typeof apiMit>, onNacht = vi.fn()) => {
  render(
    <ThemeProvider theme={theme}>
      <NachtlaufVerbrauchZeitraum projectId={5} api={api} onNachtWaehlen={onNacht} />
    </ThemeProvider>,
  )
  return onNacht
}

/** `Intl` setzt vor der Einheit ein geschütztes Leerzeichen; verglichen wird der Wortlaut. */
const lesbar = (element: HTMLElement) => element.textContent?.replaceAll(' ', ' ') ?? ''

const beschriftung = () => screen.findByTestId('verbrauch-zeitraum-beschriftung')

const kachel = (platte: HTMLElement, etikett: string) =>
  within(platte).getByTestId(`verbrauch-kachel-${etikett}`)

describe('NachtlaufVerbrauchZeitraum — Kopfzeile und Navigation', () => {
  it('fuehrt die Abschnittsueberschrift „Verbrauch" mit dem gewaehlten Zeitraum daneben', async () => {
    const api = apiMit((type) => Promise.resolve(zeitraum(type)))

    zeige(api)

    expect(screen.getByRole('heading', { level: 2, name: 'Verbrauch' })).toBeInTheDocument()
    expect(await beschriftung()).toHaveTextContent('Woche vom 07.09.2026 bis 13.09.2026')
    expect(api.period).toHaveBeenCalledWith(5, 'WEEK', 0)
    expect(screen.getByRole('button', { name: 'Woche' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('ruft Tag, Woche und Monat nacheinander ab, ohne den Bereich zu verlassen', async () => {
    const api = apiMit((type) => Promise.resolve(zeitraum(type)))
    zeige(api)
    expect(await beschriftung()).toHaveTextContent('Woche vom 07.09.2026 bis 13.09.2026')

    fireEvent.click(screen.getByRole('button', { name: 'Tag' }))
    await waitFor(async () =>
      expect(await beschriftung()).toHaveTextContent('Nacht vom 15.09.2026 auf den 16.09.2026'),
    )

    fireEvent.click(screen.getByRole('button', { name: 'Monat' }))
    await waitFor(async () => expect(await beschriftung()).toHaveTextContent('August 2026'))

    fireEvent.click(screen.getByRole('button', { name: 'Woche' }))
    await waitFor(async () =>
      expect(await beschriftung()).toHaveTextContent('Woche vom 07.09.2026 bis 13.09.2026'),
    )
    expect(api.period.mock.calls.map((c) => [c[1], c[2]])).toEqual([
      ['WEEK', 0],
      ['DAY', 0],
      ['MONTH', 0],
      ['WEEK', 0],
    ])
  })

  it('bleibt bei einem Klick auf die schon gewaehlte Art stehen', async () => {
    const api = apiMit((type) => Promise.resolve(zeitraum(type)))
    zeige(api)
    await beschriftung()

    fireEvent.click(screen.getByRole('button', { name: 'Woche' }))

    expect(api.period).toHaveBeenCalledTimes(1)
  })

  it('schreitet zurueck und wieder vor; spaeter als der letzte abgeschlossene geht nicht', async () => {
    const api = apiMit((type) => Promise.resolve(zeitraum(type)))
    zeige(api)
    await beschriftung()
    expect(screen.getByRole('button', { name: 'Späterer Zeitraum' })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: 'Früherer Zeitraum' }))
    await waitFor(() => expect(api.period).toHaveBeenLastCalledWith(5, 'WEEK', 1))
    expect(screen.getByRole('button', { name: 'Späterer Zeitraum' })).toBeEnabled()

    fireEvent.click(screen.getByRole('button', { name: 'Späterer Zeitraum' }))
    await waitFor(() => expect(api.period).toHaveBeenLastCalledWith(5, 'WEEK', 0))
  })

  it('beginnt beim Wechsel der Art wieder beim zuletzt abgeschlossenen Zeitraum', async () => {
    const api = apiMit((type) => Promise.resolve(zeitraum(type)))
    zeige(api)
    await beschriftung()
    fireEvent.click(screen.getByRole('button', { name: 'Früherer Zeitraum' }))
    await waitFor(() => expect(api.period).toHaveBeenLastCalledWith(5, 'WEEK', 1))

    fireEvent.click(screen.getByRole('button', { name: 'Monat' }))

    await waitFor(() => expect(api.period).toHaveBeenLastCalledWith(5, 'MONTH', 0))
  })

  it('sagt es, wenn der Zeitraum nicht geladen werden kann', async () => {
    zeige(apiMit(() => Promise.reject(new Error('kaputt'))))

    expect(await screen.findByText('Der Zeitraum konnte nicht geladen werden.')).toBeInTheDocument()
  })

  it('verwirft eine Antwort, die nach dem Wechsel der Art eintrifft', async () => {
    let wocheLiefern: (z: VerbrauchZeitraum) => void = () => undefined
    let wocheScheitern: (e: Error) => void = () => undefined
    let aufrufe = 0
    const api = apiMit((type) => {
      aufrufe += 1
      if (aufrufe === 1) {
        return new Promise<VerbrauchZeitraum>((resolve) => {
          wocheLiefern = resolve
        })
      }
      if (aufrufe === 2) {
        return new Promise<VerbrauchZeitraum>((_, reject) => {
          wocheScheitern = reject
        })
      }
      return Promise.resolve(zeitraum(type))
    })
    zeige(api)

    fireEvent.click(screen.getByRole('button', { name: 'Tag' }))
    fireEvent.click(screen.getByRole('button', { name: 'Monat' }))
    expect(await beschriftung()).toHaveTextContent('August 2026')

    wocheLiefern(zeitraum('WEEK'))
    wocheScheitern(new Error('zu spaet'))
    await Promise.resolve()

    expect(await beschriftung()).toHaveTextContent('August 2026')
    expect(screen.queryByText('Der Zeitraum konnte nicht geladen werden.')).not.toBeInTheDocument()
  })
})

describe('NachtlaufVerbrauchZeitraum — Platten und Kacheln', () => {
  it('stellt Zeitraum und Vorzeitraum als zwei Platten mit je vier Kacheln nebeneinander', async () => {
    zeige(apiMit((type) => Promise.resolve(zeitraum(type))))

    const aktuell = await screen.findByTestId('verbrauch-zeitraum-aktuell')
    const vorher = screen.getByTestId('verbrauch-zeitraum-vorher')
    expect(within(aktuell).getByRole('heading', { name: 'Dieser Zeitraum' })).toBeInTheDocument()
    expect(within(vorher).getByRole('heading', { name: 'Vorwoche' })).toBeInTheDocument()
    expect(vorher).toHaveTextContent('Woche vom 31.08.2026 bis 06.09.2026')
    expect(within(aktuell).getAllByTestId(/^verbrauch-kachel-/)).toHaveLength(4)
    expect(within(vorher).getAllByTestId(/^verbrauch-kachel-/)).toHaveLength(4)
    expect(lesbar(kachel(aktuell, 'Gesamtsumme'))).toContain('6,50')
    expect(lesbar(kachel(vorher, 'Gesamtsumme'))).toContain('4,00')
  })

  it('ordnet jede Kachel aus vorhandenen Daten ein', async () => {
    zeige(apiMit((type) => Promise.resolve(zeitraum(type))))

    const aktuell = await screen.findByTestId('verbrauch-zeitraum-aktuell')
    // 6,50 $ auf 4 Läufe, 5,00 $ von 6,50 $ sind 77 %, 3 Karten in 4 Läufen.
    expect(lesbar(kachel(aktuell, 'Gesamtsumme'))).toContain('1,63 $ je Lauf')
    expect(kachel(aktuell, 'Karten zugeordnet')).toHaveTextContent('77 % der Summe')
    expect(kachel(aktuell, 'Rest')).toHaveTextContent('keiner Karte zuzuordnen')
    expect(kachel(aktuell, 'Läufe')).toHaveTextContent('3 Karten')
    expect(lesbar(kachel(aktuell, 'Läufe'))).toContain('4')
  })

  it('laesst die Einordnung weg, wo der Wert dafuer fehlt — und schreibt nie 0', async () => {
    zeige(apiMit((type) => Promise.resolve(zeitraum(type, verbrauch(LEER)))))

    const aktuell = await screen.findByTestId('verbrauch-zeitraum-aktuell')
    expect(kachel(aktuell, 'Gesamtsumme')).toHaveTextContent('nicht gemessen')
    expect(kachel(aktuell, 'Gesamtsumme')).not.toHaveTextContent('je Lauf')
    expect(kachel(aktuell, 'Karten zugeordnet')).not.toHaveTextContent('der Summe')
    expect(kachel(aktuell, 'Gesamtsumme').textContent).not.toMatch(/\d/)
  })

  it('nennt im Kopf der Platte die Spanne und die Zahl der Laeufe', async () => {
    zeige(apiMit((type) => Promise.resolve(zeitraum(type))))

    expect(await screen.findByTestId('verbrauch-zeitraum-aktuell')).toHaveTextContent('4 Läufe')
    expect(screen.getByTestId('verbrauch-zeitraum-vorher')).toHaveTextContent('2 Läufe')
  })
})

describe('NachtlaufVerbrauchZeitraum — Vergleich mit dem Vorzeitraum', () => {
  it('zeigt teurer als zinnoberne Marke mit ▲ und behaelt den ganzen Satz fuer Vorlesewerkzeuge', async () => {
    zeige(apiMit((type) => Promise.resolve(zeitraum(type))))

    const vergleich = await screen.findByTestId('verbrauch-zeitraum-vergleich')
    expect(lesbar(within(vergleich).getByTestId('delta-schlecht'))).toBe('▲ 2,50 $ zur Vorwoche')
    expect(lesbar(vergleich)).toContain('2,50 $ teurer als im Vorzeitraum')
  })

  it('zeigt billiger als gruene Marke mit ▼', async () => {
    zeige(
      apiMit((type) =>
        Promise.resolve(zeitraum(type, verbrauch(aufteilung(kosten(3), kosten(2), kosten(1))))),
      ),
    )

    const vergleich = await screen.findByTestId('verbrauch-zeitraum-vergleich')
    expect(lesbar(within(vergleich).getByTestId('delta-gut'))).toBe('▼ 1,00 $ zur Vorwoche')
  })

  it('setzt keine Marke, wo nicht vergleichbar ist', async () => {
    zeige(apiMit((type) => Promise.resolve(zeitraum(type, verbrauch(LEER)))))

    const vergleich = await screen.findByTestId('verbrauch-zeitraum-vergleich')
    expect(vergleich).toHaveTextContent('nicht vergleichbar')
    expect(within(vergleich).queryByTestId(/^delta-/)).not.toBeInTheDocument()
  })
})

describe('NachtlaufVerbrauchZeitraum — Hinweise', () => {
  it('zeigt bei einem Zeitraum ohne Laeufe den Satz aus AK 9 und keine Zahlen', async () => {
    zeige(
      apiMit((type) =>
        Promise.resolve(
          zeitraum(type, { noRuns: true, runCount: 0, nightRunCount: 0, cardCount: 0, ...verbrauch(LEER) }),
        ),
      ),
    )

    expect(await screen.findByText(KEIN_LAUF_TEXT)).toBeInTheDocument()
    expect(screen.queryByTestId(/^verbrauch-kachel-/)).not.toBeInTheDocument()
    expect(screen.queryByTestId('verbrauch-zeitraum-vorher')).not.toBeInTheDocument()
    expect(screen.queryByTestId('verbrauch-vorhaben')).not.toBeInTheDocument()
  })

  it('zeigt ganz vor dem aeltesten aufbewahrten Lauf den eigenen Satz, nicht den aus AK 9', async () => {
    zeige(
      apiMit((type) =>
        Promise.resolve(
          zeitraum(type, { coverage: 'BEFORE_RETENTION', noRuns: true, runCount: 0, nightRunCount: 0, ...verbrauch(LEER) }),
        ),
      ),
    )

    const hinweis = await screen.findByTestId('verbrauch-zeitraum-hinweis')
    expect(hinweis).toHaveTextContent('vor dem ältesten aufbewahrten Lauf')
    expect(screen.queryByText(KEIN_LAUF_TEXT)).not.toBeInTheDocument()
    expect(screen.queryByTestId(/^verbrauch-kachel-/)).not.toBeInTheDocument()
  })

  it('zeigt bei Teilabdeckung die Zahlen und den Hinweis im Kopf der Platte', async () => {
    zeige(apiMit((type) => Promise.resolve(zeitraum(type, { coverage: 'PARTIAL' }))))

    const aktuell = await screen.findByTestId('verbrauch-zeitraum-aktuell')
    expect(within(aktuell).getByTestId('verbrauch-zeitraum-hinweis')).toHaveTextContent(
      'nur teilweise abgedeckt',
    )
    expect(within(aktuell).getAllByTestId(/^verbrauch-kachel-/)).toHaveLength(4)
  })

  /**
   * Der Hinweis des Zeitraums sagt, dass der Verbrauch nicht vorliegt — „nicht gemessen" bleibt
   * dem Kartenblatt vorbehalten, wo es einen bekannten Lauf ohne Zahl meint (Issue #1017).
   */
  it('erklaert Angaben ohne Verbrauch mit einem eigenen Hinweis', async () => {
    zeige(apiMit((type) => Promise.resolve(zeitraum(type, verbrauch(LEER)))))

    expect(await screen.findByTestId('verbrauch-zeitraum-hinweis')).toHaveTextContent(
      'ihr Verbrauch liegt aber nicht vor',
    )
    expect(screen.getByTestId('verbrauch-zeitraum-vergleich')).toHaveTextContent('nicht vergleichbar')
  })

  it('nennt den Hinweis des Vorzeitraums an seiner Platte', async () => {
    const mitAltemVorzeitraum = (type: VerbrauchZeitraumArt): VerbrauchZeitraum => ({
      ...zeitraum(type),
      previous: kennzahlen(type, { coverage: 'BEFORE_RETENTION', noRuns: true, runCount: 0, nightRunCount: 0 }),
    })
    zeige(apiMit((type) => Promise.resolve(mitAltemVorzeitraum(type))))

    const vorher = await screen.findByTestId('verbrauch-zeitraum-vorher')
    expect(vorher).toHaveTextContent('vor dem ältesten aufbewahrten Lauf')
    expect(screen.getByTestId('verbrauch-zeitraum-aktuell')).not.toHaveTextContent('aufbewahrten')
  })
})

describe('NachtlaufVerbrauchZeitraum — Naechte und Vorhaben', () => {
  it('macht jede Nacht des Zeitraums als Zeile erreichbar', async () => {
    const onNacht = zeige(apiMit((type) => Promise.resolve(zeitraum(type))))

    const knopf = await screen.findByRole('button', { name: /Nacht vom 09\.09\.2026/ })
    expect(knopf).toHaveAccessibleName(/abgebrochen/)
    fireEvent.click(knopf)

    expect(onNacht).toHaveBeenCalledWith('2026-09-09')
    const erste = screen.getByRole('button', { name: /Nacht vom 08\.09\.2026/ })
    expect(lesbar(erste)).toContain('3,00 $')
    expect(erste).toHaveTextContent('Di 08.09. → 09.09.')
    expect(erste).toHaveTextContent('2 Läufe')
  })

  it('zeichnet den Balken einer Nacht im Verhaeltnis zur teuersten und nie fuer eine ungemessene', async () => {
    zeige(apiMit((type) => Promise.resolve(zeitraum(type))))

    const erste = await screen.findByRole('button', { name: /Nacht vom 08\.09\.2026/ })
    expect(within(erste).getByTestId('fuellung-100')).toBeInTheDocument()
    const zweite = screen.getByRole('button', { name: /Nacht vom 09\.09\.2026/ })
    expect(within(zweite).queryByTestId(/^fuellung-/)).not.toBeInTheDocument()
  })

  it('zeigt keine Naechte-Platte, wenn der Zeitraum keine Nacht traegt', async () => {
    zeige(apiMit((type) => Promise.resolve({ ...zeitraum(type), nights: [] })))

    await screen.findByTestId('verbrauch-zeitraum-aktuell')
    expect(screen.queryByRole('heading', { name: 'Nächte' })).not.toBeInTheDocument()
  })

  it('zeigt die Vorhaben-Aufstellung des Zeitraums neben den Naechten', async () => {
    zeige(apiMit((type) => Promise.resolve(zeitraum(type))))

    expect(await screen.findByTestId('verbrauch-vorhaben')).toHaveTextContent('PLANEN · Planen')
  })
})

/**
 * Die Festlegung auf den Nachtlauf-Anteil (Issue #1016, Plan #1007): Die Seite bleibt in ihrer
 * Aussage auf Nachtläufe beschränkt, auch wenn dieselben Endpunkte seit #1013 die interaktiven
 * Sitzungen mitführen.
 */
describe('NachtlaufVerbrauchZeitraum — Nachtlauf-Anteil', () => {
  /** Nachtläufe wie im Standard-Fixture, dazu Sitzungen für 10,00 $ — Gesamtsumme 16,50 $. */
  const mitSitzungen = (type: VerbrauchZeitraumArt): VerbrauchZeitraum =>
    zeitraum(
      type,
      verbrauch(
        aufteilung(kosten(6.5), kosten(5), kosten(1.5)),
        aufteilung(kosten(10), kosten(8), kosten(2)),
        aufteilung(kosten(16.5), kosten(13), kosten(3.5)),
      ),
    )

  it('zeigt in den Kacheln den Nachtlauf-Anteil und nicht die Gesamtsumme', async () => {
    zeige(apiMit((type) => Promise.resolve(mitSitzungen(type))))

    const aktuell = await screen.findByTestId('verbrauch-zeitraum-aktuell')
    expect(lesbar(kachel(aktuell, 'Gesamtsumme'))).toContain('6,50')
    expect(lesbar(kachel(aktuell, 'Gesamtsumme'))).not.toContain('16,50')
    expect(lesbar(kachel(aktuell, 'Rest'))).toContain('1,50')
    expect(kachel(aktuell, 'Karten zugeordnet')).toHaveTextContent('77 % der Summe')
  })

  it('vergleicht Nachtlauf-Anteil mit Nachtlauf-Anteil des Vorzeitraums', async () => {
    zeige(apiMit((type) => Promise.resolve(mitSitzungen(type))))

    const vergleich = await screen.findByTestId('verbrauch-zeitraum-vergleich')
    expect(lesbar(within(vergleich).getByTestId('delta-schlecht'))).toBe('▲ 2,50 $ zur Vorwoche')
  })

  it('zeigt in den Naechte-Zeilen den Nachtlauf-Anteil der Nacht', async () => {
    zeige(
      apiMit((type) =>
        Promise.resolve({
          ...zeitraum(type),
          nights: [
            {
              night: '2026-09-08',
              runCount: 2,
              cardCount: 1,
              ...verbrauch(aufteilung(kosten(3)), aufteilung(kosten(7)), aufteilung(kosten(10))),
              aborted: false,
            },
          ],
        }),
      ),
    )

    const zeile = await screen.findByRole('button', { name: /Nacht vom 08\.09\.2026/ })
    expect(lesbar(zeile)).toContain('3,00 $')
    expect(lesbar(zeile)).not.toContain('10,00 $')
  })

  /** „Nicht gemessen" bleibt „nicht gemessen" — auch wenn die Gesamtsumme einen Wert trägt. */
  it('schreibt einen ungemessenen Nachtlauf-Anteil nie als 0 und nie als Gesamtsumme', async () => {
    zeige(
      apiMit((type) =>
        Promise.resolve(
          zeitraum(type, verbrauch(LEER, aufteilung(kosten(10)), aufteilung(kosten(10)))),
        ),
      ),
    )

    const aktuell = await screen.findByTestId('verbrauch-zeitraum-aktuell')
    expect(kachel(aktuell, 'Gesamtsumme')).toHaveTextContent('nicht gemessen')
    expect(kachel(aktuell, 'Gesamtsumme').textContent).not.toMatch(/\d/)
    expect(kachel(aktuell, 'Rest')).toHaveTextContent('nicht gemessen')
    expect(screen.getByTestId('verbrauch-zeitraum-vergleich')).toHaveTextContent(
      'nicht vergleichbar',
    )
  })
})
