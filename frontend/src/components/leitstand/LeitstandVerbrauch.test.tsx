import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import type {
  VerbrauchAngaben,
  VerbrauchAufteilung,
  VerbrauchGesamt,
  VerbrauchKennzahlen,
  VerbrauchZeitraum,
} from '../../api/nightRunUsage'
import { kostenText, tokenText } from '../../lib/leitstand'
import { LeitstandVerbrauch } from './LeitstandVerbrauch'

/**
 * Der Verbrauch im Leitstand (Issue #1017, #984 AK 1, 3, 4, 6): beide Anteile unter der Summe, der
 * Posten „ohne Karte", die Lebenszeit-Summe und die drei Aussagen zum Erfassungsbeginn.
 */

/** `Intl` setzt vor Einheiten ein geschütztes Leerzeichen; verglichen wird der Wortlaut. */
const lesbar = (text: string | null | undefined) => text?.replaceAll(' ', ' ') ?? ''

const nichts: VerbrauchAngaben = {
  costUsd: null,
  inputTokens: null,
  outputTokens: null,
  cachedInputTokens: null,
  cachedInputSharePercent: null,
}

const angaben = (
  costUsd: number | null,
  inputTokens: number | null = null,
  outputTokens: number | null = null,
  cachedInputTokens: number | null = null,
): VerbrauchAngaben => ({
  costUsd,
  inputTokens,
  outputTokens,
  cachedInputTokens,
  cachedInputSharePercent: null,
})

const LEER: VerbrauchAufteilung = { total: nichts, cardShare: nichts, remainder: nichts }

const teilung = (
  total: VerbrauchAngaben,
  cardShare: VerbrauchAngaben,
  remainder: VerbrauchAngaben,
): VerbrauchAufteilung => ({ total, cardShare, remainder })

/** Nachtläufe 8,40 $, Sitzungen 4,00 $, zusammen 12,40 $ — die Summe ist ihre Addition (AK 5). */
const NACHT = teilung(angaben(8.4, 3_000_000, 120_000, 2_400_000), angaben(6), angaben(2.4))
const SITZUNG = teilung(angaben(4, 1_820_000, 66_000, 1_260_000), angaben(3), angaben(1))
const GESAMT = teilung(
  angaben(12.4, 4_820_000, 186_000, 3_660_000),
  angaben(9),
  angaben(3.4, 200_000, 20_000, 50_000),
)

const kennzahlen = (extra: Partial<VerbrauchKennzahlen> = {}): VerbrauchKennzahlen => ({
  type: 'DAY',
  firstDay: '2026-09-14',
  lastDay: '2026-09-14',
  from: '2026-09-14T10:00:00Z',
  to: '2026-09-15T10:00:00Z',
  coverage: 'COMPLETE',
  noRuns: false,
  runCount: 3,
  nightRunCount: 1,
  interactiveRunCount: 2,
  durationMs: 1,
  cardCount: 9,
  usage: GESAMT,
  usageByKind: { night: NACHT, interactive: SITZUNG },
  interactiveUsageSince: '2026-09-01T08:00:00Z',
  ...extra,
})

const zeitraum = (extra: Partial<VerbrauchZeitraum> = {}): VerbrauchZeitraum => ({
  current: kennzahlen(),
  previous: kennzahlen({ usage: teilung(angaben(14.1), angaben(10), angaben(4.1)) }),
  nights: [
    {
      night: '2026-09-14',
      runCount: 3,
      cardCount: 9,
      usage: GESAMT,
      usageByKind: { night: NACHT, interactive: SITZUNG },
      aborted: false,
    },
  ],
  epics: [],
  withoutEpic: { epicId: null, shortcode: null, title: null, cardCount: 0, usage: nichts },
  epicsOverlap: false,
  stages: [],
  ...extra,
})

const gesamt = (extra: Partial<VerbrauchGesamt> = {}): VerbrauchGesamt => ({
  runCount: 42,
  nightRunCount: 30,
  interactiveRunCount: 12,
  cardCount: 61,
  usage: teilung(angaben(318.5), angaben(240), angaben(78.5)),
  usageByKind: { night: teilung(angaben(200), angaben(160), angaben(40)), interactive: teilung(angaben(118.5), angaben(80), angaben(38.5)) },
  oldestRetainedRunStart: '2026-05-01T00:00:00Z',
  interactiveUsageSince: '2026-09-01T08:00:00Z',
  ...extra,
})

function zeige(
  zeitraumWert: VerbrauchZeitraum = zeitraum(),
  gesamtWert: VerbrauchGesamt | Error = gesamt(),
) {
  const api = {
    period: vi.fn().mockResolvedValue(zeitraumWert),
    total:
      gesamtWert instanceof Error
        ? vi.fn().mockRejectedValue(gesamtWert)
        : vi.fn().mockResolvedValue(gesamtWert),
  }
  render(<LeitstandVerbrauch projectId={5} api={api} />)
  return api
}

const kachel = (name: string) => screen.findByRole('article', { name })

describe('LeitstandVerbrauch — beide Anteile (AK 1)', () => {
  it('zeigt unter der Summe den Lauf- und den Sitzungs-Anteil mit ihren Beschriftungen', async () => {
    zeige()

    const kosten = await kachel('Kosten')
    expect(kosten).toHaveTextContent('12,40$')
    const nacht = within(kosten).getByTestId('anteil-nacht')
    const sitzungen = within(kosten).getByTestId('anteil-interaktiv')
    expect(lesbar(nacht.textContent)).toContain('aus Runs')
    expect(lesbar(nacht.textContent)).toContain('8,40 $')
    expect(lesbar(sitzungen.textContent)).toContain('aus interaktiven Sitzungen')
    expect(lesbar(sitzungen.textContent)).toContain('4,00 $')
  })

  /** Die angezeigte Summe ist genau die Addition der beiden Anteile (#984 AK 5). */
  it('zeigt eine Summe, die genau die Addition der beiden Anteile ist', async () => {
    zeige()

    const kosten = await kachel('Kosten')
    const nacht = Number(lesbar(within(kosten).getByTestId('anteil-nacht').textContent).replace(/[^\d,]/g, '').replace(',', '.'))
    const sitzung = Number(lesbar(within(kosten).getByTestId('anteil-interaktiv').textContent).replace(/[^\d,]/g, '').replace(',', '.'))

    expect(nacht + sitzung).toBeCloseTo(12.4, 2)
    expect(within(kosten).getByTestId('kachel-wert')).toHaveTextContent('12,40')
  })

  it('traegt die Anteile auch an den Token-Kacheln', async () => {
    zeige()

    const eingabe = await kachel('Eingabe-Token')
    expect(lesbar(within(eingabe).getByTestId('anteil-nacht').textContent)).toContain('3,00 Mio')
    expect(lesbar(within(eingabe).getByTestId('anteil-interaktiv').textContent)).toContain('1,82 Mio')
    const ausgabe = await kachel('Ausgabe-Token')
    expect(lesbar(within(ausgabe).getByTestId('anteil-nacht').textContent)).toContain('120 Tsd')
    expect(lesbar(within(ausgabe).getByTestId('anteil-interaktiv').textContent)).toContain('66 Tsd')
  })

  it('nennt in der Kopfzeile Laeufe und Sitzungen', async () => {
    zeige()

    expect(await screen.findByTestId('verbrauch-umfang')).toHaveTextContent('1 Run · 2 Sitzungen')
  })

  /** Ein Tag ohne Nachtlauf, aber mit Sitzungen, zeigt Zahlen — nicht „kein Lauf" (AK 1). */
  it('zeigt an einem Tag ohne Nachtlauf, aber mit Sitzungen, Zahlen', async () => {
    zeige(
      zeitraum({
        current: kennzahlen({
          runCount: 2,
          nightRunCount: 0,
          interactiveRunCount: 2,
          usage: SITZUNG,
          usageByKind: { night: LEER, interactive: SITZUNG },
        }),
      }),
    )

    expect(await kachel('Kosten')).toHaveTextContent('4,00$')
    expect(screen.queryByText(/weder ein Run noch eine Sitzung/)).not.toBeInTheDocument()
    expect(await screen.findByTestId('verbrauch-umfang')).toHaveTextContent('0 Runs · 2 Sitzungen')
  })

  it('zeigt einen Zeitraum ohne Lauf und ohne Sitzung weiterhin als leer', async () => {
    zeige(
      zeitraum({
        current: kennzahlen({
          noRuns: true,
          runCount: 0,
          nightRunCount: 0,
          interactiveRunCount: 0,
          cardCount: 0,
          usage: LEER,
          usageByKind: { night: LEER, interactive: LEER },
        }),
      }),
    )

    expect(
      await screen.findByText('In diesem Zeitraum hat weder ein Run noch eine Sitzung stattgefunden.'),
    ).toBeInTheDocument()
  })
})

describe('LeitstandVerbrauch — ohne Karte (AK 3)', () => {
  it('nennt den nicht zuordenbaren Anteil genau „ohne Karte" und zeigt den Wert aus remainder', async () => {
    zeige()

    const posten = await screen.findByTestId('posten-ohne-karte')
    expect(lesbar(posten.textContent)).toContain('ohne Karte')
    expect(lesbar(posten.textContent)).toContain('3,40 $')
  })

  it('laesst den Posten weg, wo der Rest nicht vorliegt — statt 0 zu behaupten', async () => {
    zeige(zeitraum({ current: kennzahlen({ usage: teilung(angaben(12.4), angaben(9), nichts) }) }))

    await kachel('Kosten')
    expect(screen.queryByTestId('posten-ohne-karte')).not.toBeInTheDocument()
  })
})

describe('LeitstandVerbrauch — Erfassungsbeginn (AK 6)', () => {
  /** Der Zeitraum liegt ganz vor dem Erfassungsbeginn. */
  it('schreibt beim interaktiven Anteil „nicht erfasst" statt 0 und laesst die Nachtlauf-Zahlen stehen', async () => {
    zeige(
      zeitraum({
        current: kennzahlen({
          interactiveUsageSince: '2026-09-20T08:00:00Z',
          usage: NACHT,
          usageByKind: { night: NACHT, interactive: LEER },
        }),
      }),
    )

    const kosten = await kachel('Kosten')
    const sitzungen = within(kosten).getByTestId('anteil-interaktiv')
    expect(lesbar(sitzungen.textContent)).toContain('nicht erfasst')
    expect(lesbar(sitzungen.textContent)).not.toContain('0,00')
    expect(lesbar(within(kosten).getByTestId('anteil-nacht').textContent)).toContain('8,40 $')
  })

  /** Der Zeitraum schneidet den Erfassungsbeginn: Zahlen sind da, aber unvollständig. */
  it('schreibt bei einem angeschnittenen Zeitraum „teilweise erfasst" neben die Zahl', async () => {
    zeige(
      zeitraum({
        current: kennzahlen({ interactiveUsageSince: '2026-09-14T20:00:00Z' }),
      }),
    )

    const kosten = await kachel('Kosten')
    const sitzungen = within(kosten).getByTestId('anteil-interaktiv')
    expect(lesbar(sitzungen.textContent)).toContain('teilweise erfasst')
    expect(lesbar(sitzungen.textContent)).toContain('4,00 $')
    expect(lesbar(within(kosten).getByTestId('anteil-nacht').textContent)).toContain('8,40 $')
  })

  /** Nach dem Erfassungsbeginn ist 0 ein Messwert und wird als solcher gezeigt. */
  it('zeigt nach dem Erfassungsbeginn die Zahl, auch wenn sie 0 ist', async () => {
    zeige(
      zeitraum({
        current: kennzahlen({
          interactiveUsageSince: '2026-01-01T00:00:00Z',
          usageByKind: { night: NACHT, interactive: teilung(angaben(0), angaben(0), angaben(0)) },
        }),
      }),
    )

    const kosten = await kachel('Kosten')
    const sitzungen = within(kosten).getByTestId('anteil-interaktiv')
    expect(lesbar(sitzungen.textContent)).toContain('0,00 $')
    expect(lesbar(sitzungen.textContent)).not.toContain('nicht erfasst')
    expect(lesbar(within(kosten).getByTestId('anteil-nacht').textContent)).toContain('8,40 $')
  })
})

describe('LeitstandVerbrauch — Lebenszeit (AK 4)', () => {
  it('zeigt die Summe ueber die ganze Laufzeit samt Abdeckung und Erfassungsbeginn', async () => {
    const api = zeige()

    const lebenszeit = await kachel('Gesamt über die Laufzeit')
    expect(lebenszeit).toHaveTextContent('318,50$')
    expect(lesbar(lebenszeit.textContent)).toContain('ab 01.05.2026')
    expect(lesbar(lebenszeit.textContent)).toContain('Sitzungen ab 01.09.2026')
    expect(api.total).toHaveBeenCalledWith(5)
  })

  it('meldet einen Ladefehler der Lebenszeit-Kachel wie die uebrigen Kacheln', async () => {
    zeige(zeitraum(), new Error('Netz'))

    const lebenszeit = await kachel('Gesamt über die Laufzeit')
    await waitFor(() =>
      expect(within(lebenszeit).getByTestId('kachel-wert')).toHaveTextContent('—'),
    )
    expect(lebenszeit).toHaveTextContent('nicht geladen')
  })

  it('sagt ohne gemeldete Sitzung, dass der interaktive Anteil noch nicht erfasst ist', async () => {
    zeige(zeitraum(), gesamt({ interactiveUsageSince: null }))

    const lebenszeit = await kachel('Gesamt über die Laufzeit')
    expect(lesbar(lebenszeit.textContent)).toContain('nicht erfasst')
  })

  /** Die Lebenszeit lädt eigenständig — der Zeitraum steht schon, solange sie unterwegs ist. */
  it('sagt, dass die Summe noch geladen wird, solange der Abruf laeuft', async () => {
    const api = {
      period: vi.fn().mockResolvedValue(zeitraum()),
      total: vi.fn().mockReturnValue(new Promise(() => undefined)),
    }
    render(<LeitstandVerbrauch projectId={5} api={api} />)

    const lebenszeit = await kachel('Gesamt über die Laufzeit')
    expect(lebenszeit).toHaveTextContent('wird geladen')
  })

  it('sagt es, wenn kein Eintrag aufbewahrt ist — statt ein Datum zu erfinden', async () => {
    zeige(zeitraum(), gesamt({ oldestRetainedRunStart: null }))

    const lebenszeit = await kachel('Gesamt über die Laufzeit')
    expect(lesbar(lebenszeit.textContent)).toContain('ohne aufbewahrten Eintrag')
  })

  /** Eine Lebenszeit ohne gemessene Kosten bleibt leer und wird nie 0. */
  it('zeigt eine Summe ohne gemessene Kosten als Leerwert', async () => {
    zeige(zeitraum(), gesamt({ usage: teilung(nichts, nichts, nichts) }))

    const lebenszeit = await kachel('Gesamt über die Laufzeit')
    await waitFor(() =>
      expect(within(lebenszeit).getByTestId('kachel-wert')).toHaveTextContent('—'),
    )
    expect(lesbar(lebenszeit.textContent)).toContain('ab 01.05.2026')
  })
})

describe('LeitstandVerbrauch — Bestand', () => {
  /** Ein Anteil ohne Messung bleibt leer; eine 0 behauptete eine Messung, die es nicht gab. */
  it('zeigt einen nicht gemessenen Anteil als Leerwert und nie als 0', async () => {
    zeige(
      zeitraum({
        current: kennzahlen({
          interactiveUsageSince: '2026-01-01T00:00:00Z',
          usageByKind: { night: LEER, interactive: LEER },
        }),
      }),
    )

    const kosten = await kachel('Kosten')
    expect(within(kosten).getByTestId('anteil-nacht')).toHaveTextContent('—')
    expect(within(kosten).getByTestId('anteil-interaktiv')).toHaveTextContent('—')
    const ausgabe = await kachel('Ausgabe-Token')
    expect(within(ausgabe).getByTestId('anteil-nacht')).toHaveTextContent('—')
  })


  /** Der Stapelbalken bleibt der Anteil aus dem Zwischenspeicher und wird nicht umgewidmet. */
  it('zeigt im Stapelbalken weiterhin den Anteil aus dem Zwischenspeicher', async () => {
    zeige()

    const eingabe = await kachel('Eingabe-Token')
    expect(within(eingabe).getByRole('img')).toHaveAccessibleName(
      'Aufteilung der Eingabe: 76 Prozent aus dem Cache gelesen, 24 Prozent frisch',
    )
    expect(lesbar(eingabe.textContent)).toContain('Cache gelesen')
    expect(lesbar(eingabe.textContent)).toContain('frisch')
  })
})

// --- Verhalten jenseits der Anteile (Issue #1281) ---------------------------------------------
// Die Mutationsprüfung des Ausschnitts `bausteine-leitstand` fährt nur die Tests unter
// `leitstand/`. Was die Seiten-Tests hier mit abdeckten, braucht deshalb eigene Nachweise:
// Laden, Fehler, Zeitraumwechsel samt verspäteter Antworten und die Rechnungen der Kacheln.

/** Ein Versprechen, das der Test selbst erfüllt oder verwirft. */
function aufgeschoben<T>() {
  let erfuellen!: (wert: T) => void
  let verwerfen!: (grund: Error) => void
  const versprechen = new Promise<T>((ja, nein) => {
    erfuellen = ja
    verwerfen = nein
  })
  return { versprechen, erfuellen, verwerfen }
}

const nachtMit = (night: string, outputTokens: number | null) => ({
  night,
  runCount: 1,
  cardCount: 1,
  usage: teilung(angaben(1, null, outputTokens), nichts, nichts),
  usageByKind: { night: LEER, interactive: LEER },
  aborted: false,
})

const zeitraumTasten = () => within(screen.getByRole('group', { name: 'Zeitraum' })).getAllByRole('button')

describe('LeitstandVerbrauch — Zeitraum, Laden und Fehler (Issue #1281)', () => {
  it('bietet Schicht, Woche und Monat an und lädt zuerst die Schicht', async () => {
    const api = zeige()

    await kachel('Kosten')
    expect(zeitraumTasten().map((taste) => taste.textContent)).toEqual(['Schicht', 'Woche', 'Monat'])
    expect(screen.getByRole('button', { name: 'Schicht' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Woche' })).toHaveAttribute('aria-pressed', 'false')
    expect(api.period).toHaveBeenCalledTimes(1)
    expect(api.period).toHaveBeenCalledWith(5, 'DAY', 0)
  })

  it('lädt beim Wechsel den gewählten Zeitraum, die Lebenszeit aber nicht erneut', async () => {
    const api = zeige()
    await kachel('Kosten')

    fireEvent.click(screen.getByRole('button', { name: 'Woche' }))
    await waitFor(() => expect(api.period).toHaveBeenLastCalledWith(5, 'WEEK', 0))
    expect(screen.getByRole('button', { name: 'Woche' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Schicht' })).toHaveAttribute('aria-pressed', 'false')

    fireEvent.click(screen.getByRole('button', { name: 'Monat' }))
    await waitFor(() => expect(api.period).toHaveBeenLastCalledWith(5, 'MONTH', 0))
    expect(api.period).toHaveBeenCalledTimes(3)
    expect(api.total).toHaveBeenCalledTimes(1)
  })

  it('holt beide Angaben neu, wenn das Projekt wechselt', async () => {
    const api = {
      period: vi.fn().mockResolvedValue(zeitraum()),
      total: vi.fn().mockResolvedValue(gesamt()),
    }
    const { rerender } = render(<LeitstandVerbrauch projectId={5} api={api} />)
    await kachel('Kosten')

    rerender(<LeitstandVerbrauch projectId={6} api={api} />)
    await waitFor(() => expect(api.total).toHaveBeenLastCalledWith(6))
    expect(api.period).toHaveBeenLastCalledWith(6, 'DAY', 0)
  })

  it('zeigt schon im ersten Bild den Ladesatz, bevor ein Effekt läuft', () => {
    const api = { period: vi.fn(), total: vi.fn() }
    expect(renderToStaticMarkup(<LeitstandVerbrauch projectId={5} api={api} />)).toContain('Der Verbrauch wird geladen …')
  })

  it('sagt, dass geladen wird, solange der Zeitraum unterwegs ist', () => {
    render(
      <LeitstandVerbrauch
        projectId={5}
        api={{ period: vi.fn().mockReturnValue(new Promise(() => undefined)), total: vi.fn().mockResolvedValue(gesamt()) }}
      />,
    )
    expect(screen.getByText('Der Verbrauch wird geladen …')).toBeInTheDocument()
    expect(screen.queryByText('Der Verbrauch konnte nicht geladen werden.')).not.toBeInTheDocument()
    expect(screen.queryByTestId('verbrauch-umfang')).not.toBeInTheDocument()
  })

  it('zeigt beim Wechsel wieder den Ladesatz statt der alten Zahlen', async () => {
    const api = {
      period: vi.fn().mockResolvedValueOnce(zeitraum()).mockReturnValue(new Promise(() => undefined)),
      total: vi.fn().mockResolvedValue(gesamt()),
    }
    render(<LeitstandVerbrauch projectId={5} api={api} />)
    await kachel('Kosten')
    expect(screen.queryByText('Der Verbrauch wird geladen …')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Woche' }))
    expect(await screen.findByText('Der Verbrauch wird geladen …')).toBeInTheDocument()
    expect(screen.queryByRole('article', { name: 'Kosten' })).not.toBeInTheDocument()
  })

  it('meldet einen Ladefehler des Zeitraums', async () => {
    render(
      <LeitstandVerbrauch
        projectId={5}
        api={{ period: vi.fn().mockRejectedValue(new Error('Netz')), total: vi.fn().mockResolvedValue(gesamt()) }}
      />,
    )
    expect(await screen.findByText('Der Verbrauch konnte nicht geladen werden.')).toBeInTheDocument()
    expect(screen.queryByText('Der Verbrauch wird geladen …')).not.toBeInTheDocument()
  })

  it('verwirft eine verspätete Antwort des vorigen Zeitraums', async () => {
    const alt = aufgeschoben<VerbrauchZeitraum>()
    const api = {
      period: vi
        .fn()
        .mockReturnValueOnce(alt.versprechen)
        .mockResolvedValue(zeitraum({ current: kennzahlen({ nightRunCount: 4 }) })),
      total: vi.fn().mockResolvedValue(gesamt()),
    }
    render(<LeitstandVerbrauch projectId={5} api={api} />)

    fireEvent.click(screen.getByRole('button', { name: 'Woche' }))
    expect(await screen.findByTestId('verbrauch-umfang')).toHaveTextContent('4 Runs')

    await act(async () => alt.erfuellen(zeitraum()))
    expect(screen.getByTestId('verbrauch-umfang')).toHaveTextContent('4 Runs')
  })

  it('verwirft auch einen verspäteten Fehler des vorigen Zeitraums', async () => {
    const alt = aufgeschoben<VerbrauchZeitraum>()
    const api = {
      period: vi.fn().mockReturnValueOnce(alt.versprechen).mockResolvedValue(zeitraum()),
      total: vi.fn().mockResolvedValue(gesamt()),
    }
    render(<LeitstandVerbrauch projectId={5} api={api} />)

    fireEvent.click(screen.getByRole('button', { name: 'Woche' }))
    await kachel('Kosten')

    await act(async () => alt.verwerfen(new Error('zu spät')))
    expect(screen.queryByText('Der Verbrauch konnte nicht geladen werden.')).not.toBeInTheDocument()
    expect(screen.getByRole('article', { name: 'Kosten' })).toBeInTheDocument()
  })

  it('verwirft eine verspätete Lebenszeit des vorigen Projekts, ob Wert oder Fehler', async () => {
    const altWert = aufgeschoben<VerbrauchGesamt>()
    const altFehler = aufgeschoben<VerbrauchGesamt>()
    const api = {
      period: vi.fn().mockResolvedValue(zeitraum()),
      total: vi
        .fn()
        .mockReturnValueOnce(altWert.versprechen)
        .mockReturnValueOnce(altFehler.versprechen)
        .mockResolvedValue(gesamt()),
    }
    const { rerender } = render(<LeitstandVerbrauch projectId={5} api={api} />)
    rerender(<LeitstandVerbrauch projectId={6} api={api} />)
    rerender(<LeitstandVerbrauch projectId={7} api={api} />)
    const lebenszeit = await kachel('Gesamt über die Laufzeit')
    await waitFor(() => expect(lebenszeit).toHaveTextContent('318,50$'))

    await act(async () => altWert.erfuellen(gesamt({ usage: teilung(angaben(999), nichts, nichts) })))
    await act(async () => altFehler.verwerfen(new Error('zu spät')))
    expect(lebenszeit).toHaveTextContent('318,50$')
    expect(lebenszeit).not.toHaveTextContent('nicht geladen')
  })
})

describe('LeitstandVerbrauch — Rechnungen der Kacheln (Issue #1281)', () => {
  it('zeigt Eingabe und Ausgabe mit Wert und Einheit und ohne Hinweiszeile', async () => {
    zeige()

    // Wert und Einheit stehen unmittelbar hinter dem Etikett; die Einheit der Anteile zählt nicht.
    expect(lesbar((await kachel('Eingabe-Token')).textContent)).toMatch(/^Eingabe-Token4,82Mio/)
    expect(lesbar((await kachel('Ausgabe-Token')).textContent)).toMatch(/^Ausgabe-Token186Tsd/)
    // Ohne Hinweis steht kein leerer Absatz über den Kacheln.
    expect(screen.getByRole('region', { name: 'Verbrauch' }).innerHTML).not.toContain('<p')
  })

  it('teilt die Eingabe in gelesene und frische Token, mit Leerzeichen vor dem Wert', async () => {
    zeige()

    const eingabe = await kachel('Eingabe-Token')
    expect(lesbar(eingabe.textContent)).toContain('Cache gelesen 3,66 Mio')
    expect(lesbar(eingabe.textContent)).toContain('frisch 1,16 Mio')
  })

  it.each([
    ['ohne gelesene Token', angaben(12.4, 4_820_000, 186_000, null)],
    ['ohne Eingabe', angaben(12.4, null, 186_000, 5)],
    ['bei null Eingabe-Token', angaben(12.4, 0, 186_000, 0)],
  ])('lässt die Aufteilung %s weg', async (_fall, summe) => {
    zeige(zeitraum({ current: kennzahlen({ usage: teilung(summe, nichts, nichts) }) }))

    const eingabe = await kachel('Eingabe-Token')
    expect(within(eingabe).queryByRole('img')).not.toBeInTheDocument()
    expect(eingabe).not.toHaveTextContent('frisch')
  })

  it('zeichnet den Ausgabeverlauf aus den gemessenen Nächten', async () => {
    zeige(zeitraum({ nights: [nachtMit('2026-09-12', 100), nachtMit('2026-09-13', null), nachtMit('2026-09-14', 300)] }))

    const ausgabe = await kachel('Ausgabe-Token')
    expect(within(ausgabe).getByTestId('funke').innerHTML).toContain('points="2,30 122,4"')
    expect(ausgabe).toHaveTextContent('3 Schichten')
  })

  it.each([
    ['eine Nacht', [nachtMit('2026-09-14', 100)]],
    ['eine gemessene Nacht', [nachtMit('2026-09-13', 100), nachtMit('2026-09-14', null)]],
  ])('zeichnet für %s keinen Verlauf', async (_fall, nights) => {
    zeige(zeitraum({ nights }))

    const ausgabe = await kachel('Ausgabe-Token')
    expect(within(ausgabe).queryByTestId('funke')).not.toBeInTheDocument()
  })

  it('schreibt eine einzelne Schicht in der Einzahl', async () => {
    zeige()

    const ausgabe = await kachel('Ausgabe-Token')
    expect(ausgabe).toHaveTextContent('1 Schicht')
    expect(ausgabe).not.toHaveTextContent('Schichten')
  })

  it('rechnet Ausgabe und Kosten je Vorgang', async () => {
    zeige()

    const ausgabe = await kachel('Ausgabe-Token')
    expect(lesbar(within(ausgabe).getByTestId('delta-neutral').textContent)).toBe(
      lesbar(`${tokenText(Math.round(186_000 / 9))} je Vorgang`),
    )
    const kosten = await kachel('Kosten')
    expect(lesbar(kosten.textContent)).toContain(lesbar(`${kostenText(12.4 / 9)} je Vorgang`))
  })

  it.each([
    ['ohne Vorgang', kennzahlen({ cardCount: 0 })],
    ['ohne gemessene Ausgabe und Kosten', kennzahlen({ usage: teilung(angaben(null, 4_820_000, null, 3_660_000), nichts, nichts) })],
  ])('rechnet %s nichts je Vorgang', async (_fall, current) => {
    zeige(zeitraum({ current }))

    const ausgabe = await kachel('Ausgabe-Token')
    expect(within(ausgabe).queryByTestId('delta-neutral')).not.toBeInTheDocument()
    const kosten = await kachel('Kosten')
    expect(kosten).not.toHaveTextContent('je Vorgang')
  })

  it('lässt ohne Vorgang den Fuß der Kosten leer — nach der Vergleichsmarke kommt nichts', async () => {
    zeige(zeitraum({ current: kennzahlen({ cardCount: 0 }) }))

    const kosten = await kachel('Kosten')
    expect(lesbar(kosten.textContent)).toMatch(/▼ 1,70 \$$/)
  })

  it('zeigt Kosten ohne Messung als Leerwert', async () => {
    zeige(zeitraum({ current: kennzahlen({ usage: teilung(nichts, nichts, nichts) }) }))

    const kosten = await kachel('Kosten')
    expect(within(kosten).getByTestId('kachel-wert')).toHaveTextContent('—')
  })

  it('markiert günstiger als im Vorzeitraum grün mit Abwärtspfeil', async () => {
    zeige()

    const kosten = await kachel('Kosten')
    const vergleich = within(kosten).getByTitle(/billiger als im Vorzeitraum/)
    expect(lesbar(within(vergleich).getByTestId('delta-gut').textContent)).toBe('▼ 1,70 $')
  })

  it('markiert teurer als im Vorzeitraum zinnober mit Aufwärtspfeil', async () => {
    zeige(zeitraum({ previous: kennzahlen({ usage: teilung(angaben(10), nichts, nichts) }) }))

    const kosten = await kachel('Kosten')
    expect(lesbar(within(kosten).getByTestId('delta-schlecht').textContent)).toBe('▲ 2,40 $')
    expect(within(kosten).queryByTestId('delta-gut')).not.toBeInTheDocument()
  })

  it.each([
    ['gleich teuer', angaben(12.4)],
    ['ohne Vergleichswert', nichts],
  ])('zeigt %s keine Marke', async (_fall, vorher) => {
    zeige(zeitraum({ previous: kennzahlen({ usage: teilung(vorher, nichts, nichts) }) }))

    const kosten = await kachel('Kosten')
    expect(within(kosten).queryByTestId(/^delta-/)).not.toBeInTheDocument()
  })

  it('hängt an den erfassten Sitzungsanteil keinen Vorbehalt', async () => {
    zeige()

    const kosten = await kachel('Kosten')
    expect(lesbar(within(kosten).getByTestId('anteil-interaktiv').textContent)).toBe('aus interaktiven Sitzungen4,00 $')
  })
})

describe('LeitstandVerbrauch — Lebenszeit im Detail (Issue #1281)', () => {
  it('nennt die Abdeckung als eine Zeile mit Mittelpunkt', async () => {
    zeige()

    const lebenszeit = await kachel('Gesamt über die Laufzeit')
    expect(lesbar(lebenszeit.textContent)).toContain('ab 01.05.2026 · Sitzungen ab 01.09.2026')
    expect(lesbar(within(lebenszeit).getByTestId('anteil-interaktiv').textContent)).toBe('aus interaktiven Sitzungen118,50 $')
  })

  it('schreibt ohne Erfassungsbeginn „Sitzungen nicht erfasst", auch im Anteil', async () => {
    zeige(zeitraum(), gesamt({ interactiveUsageSince: null }))

    const lebenszeit = await kachel('Gesamt über die Laufzeit')
    await waitFor(() => expect(lesbar(lebenszeit.textContent)).toContain('ab 01.05.2026 · Sitzungen nicht erfasst'))
    expect(lesbar(within(lebenszeit).getByTestId('anteil-interaktiv').textContent)).toBe('aus interaktiven Sitzungennicht erfasst')
  })

  it('zeigt während des Ladens weder Anteile noch Abdeckung', async () => {
    render(
      <LeitstandVerbrauch
        projectId={5}
        api={{ period: vi.fn().mockResolvedValue(zeitraum()), total: vi.fn().mockReturnValue(new Promise(() => undefined)) }}
      />,
    )

    const lebenszeit = await kachel('Gesamt über die Laufzeit')
    expect(lesbar(lebenszeit.textContent)).toBe('Gesamt über die Laufzeit—wird geladen')
    expect(within(lebenszeit).queryByTestId('anteil-nacht')).not.toBeInTheDocument()
  })
})
