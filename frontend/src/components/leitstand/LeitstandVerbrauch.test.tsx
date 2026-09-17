import { render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type {
  VerbrauchAngaben,
  VerbrauchAufteilung,
  VerbrauchGesamt,
  VerbrauchKennzahlen,
  VerbrauchZeitraum,
} from '../../api/nightRunUsage'
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
  it('zeigt unter der Summe den Nachtlauf- und den Sitzungs-Anteil mit ihren Beschriftungen', async () => {
    zeige()

    const kosten = await kachel('Kosten')
    expect(kosten).toHaveTextContent('12,40$')
    const nacht = within(kosten).getByTestId('anteil-nacht')
    const sitzungen = within(kosten).getByTestId('anteil-interaktiv')
    expect(lesbar(nacht.textContent)).toContain('aus Nachtläufen')
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

    expect(await screen.findByTestId('verbrauch-umfang')).toHaveTextContent('1 Lauf · 2 Sitzungen')
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
    expect(screen.queryByText(/weder ein Lauf noch eine Sitzung/)).not.toBeInTheDocument()
    expect(await screen.findByTestId('verbrauch-umfang')).toHaveTextContent('0 Läufe · 2 Sitzungen')
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
      await screen.findByText('In diesem Zeitraum hat weder ein Lauf noch eine Sitzung stattgefunden.'),
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
