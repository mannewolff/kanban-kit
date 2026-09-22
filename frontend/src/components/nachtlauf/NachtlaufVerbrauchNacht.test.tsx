import { ThemeProvider } from '@mui/material/styles'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type {
  VerbrauchAngaben,
  VerbrauchAufteilung,
  VerbrauchKarte,
  VerbrauchNacht,
  VerbrauchStufe,
} from '../../api/nightRunUsage'
import type { NightRunStage } from '../../api/nightRuns'
import { nachtlaufTheme } from '../../nachtlaufDesign'
import { NachtlaufVerbrauchNacht } from './NachtlaufVerbrauchNacht'

/** Die Nachtansicht der Verbrauchs-Auswertung (Issue #941, #926 AK 1–4; Kacheln seit Task #1108). */

const nichts: VerbrauchAngaben = {
  costUsd: null,
  inputTokens: null,
  outputTokens: null,
  cachedInputTokens: null,
  cachedInputSharePercent: null,
}

const angaben = (werte: Partial<VerbrauchAngaben>): VerbrauchAngaben => ({ ...nichts, ...werte })

const aufteilung = (
  total: VerbrauchAngaben,
  cardShare: VerbrauchAngaben = nichts,
  remainder: VerbrauchAngaben = nichts,
): VerbrauchAufteilung => ({ total, cardShare, remainder })

const LEER = aufteilung(nichts)

/**
 * Der Verbrauch einer Nacht nach Gattung (Issue #1016): `nachtlauf` ist der Anteil, den die
 * Nachtansicht zeigt. `gesamt` ist standardmäßig der Nachtlauf-Anteil — wo ein Sitzungs-Anteil im
 * Spiel ist, nennt der Test die Gesamtsumme ausdrücklich, statt sie hier zu rechnen.
 */
const verbrauch = (
  nachtlauf: VerbrauchAufteilung,
  sitzungen: VerbrauchAufteilung = LEER,
  gesamt: VerbrauchAufteilung = nachtlauf,
): Pick<VerbrauchNacht, 'usage' | 'usageByKind'> => ({
  usage: gesamt,
  usageByKind: { night: nachtlauf, interactive: sitzungen },
})

/** Eine Kartenzeile; `nachtlauf` ist der Anteil, den die Nachtansicht zeigt. */
const karte = (
  cardNumber: number,
  attemptCount: number,
  durationMs: number | null,
  nachtlauf: VerbrauchAngaben,
  sitzungen: VerbrauchAngaben = nichts,
  gesamt: VerbrauchAngaben = nachtlauf,
): VerbrauchKarte => ({
  cardNumber,
  attemptCount,
  durationMs,
  usage: gesamt,
  usageByKind: { night: nachtlauf, interactive: sitzungen },
})

const nacht = (werte: Partial<VerbrauchNacht>): VerbrauchNacht => ({
  night: '2026-09-15',
  runCount: 2,
  durationMs: 5_400_000,
  cardCount: 2,
  ...verbrauch(
    aufteilung(
      angaben({
        costUsd: 10,
        inputTokens: 70_450_000,
        outputTokens: 415_000,
        cachedInputTokens: 52_000_000,
        cachedInputSharePercent: 73.8,
      }),
      angaben({ costUsd: 6 }),
      angaben({ costUsd: 4 }),
    ),
  ),
  aborted: false,
  cards: [
    karte(721, 2, 3_600_000, angaben({ costUsd: 4.5 })),
    karte(722, 1, null, angaben({ costUsd: 1.5 })),
  ],
  stages: [],
  ...werte,
})

/** Eine Stufe der Kette in der Aufstellung der Nacht (Issue #1117). */
const stufe = (stage: NightRunStage, costUsd: number, itemCount = 1): VerbrauchStufe => ({
  stage,
  itemCount,
  durationMs: 60_000,
  usage: angaben({ costUsd }),
})

const zeige = (daten: VerbrauchNacht) =>
  render(
    <ThemeProvider theme={nachtlaufTheme}>
      <NachtlaufVerbrauchNacht nacht={daten} />
    </ThemeProvider>,
  )

/** `Intl` setzt vor das Währungszeichen ein geschütztes Leerzeichen. */
const lesbar = (element: HTMLElement) => element.textContent?.replaceAll(' ', ' ') ?? ''

const kachel = (etikett: string) => screen.getByTestId(`verbrauch-kachel-${etikett}`)

/** Der große Wert der Kachel — ohne die Einordnung darunter, die dieselbe Zahl führen kann. */
const wertVon = (kachel: HTMLElement) => within(kachel).getByTestId('kachel-wert').textContent

/** Die kleine Einheit steht unmittelbar hinter dem Wert. */
const einheitVon = (kachel: HTMLElement) =>
  within(kachel).getByTestId('kachel-wert').nextElementSibling?.textContent

describe('NachtlaufVerbrauchNacht', () => {
  /**
   * Die Nacht zeigt dieselben Kacheln wie Leitstand und Zeitraumansicht (Task #1108): zwei Raster
   * aus je vier Kacheln statt der beiden flachen Kennzahlenreihen von vorher.
   */
  it('zeigt acht Kacheln in zwei Rastern', () => {
    zeige(nacht({}))

    const summen = screen.getByTestId('verbrauch-nacht-summen')
    const kennzahlen = screen.getByTestId('verbrauch-nacht-kennzahlen')
    expect(within(summen).getAllByTestId(/^verbrauch-kachel-/)).toHaveLength(4)
    expect(within(kennzahlen).getAllByTestId(/^verbrauch-kachel-/)).toHaveLength(4)
    for (const etikett of ['Gesamtsumme', 'Karten zugeordnet', 'Rest', 'Läufe']) {
      expect(within(summen).getByTestId(`verbrauch-kachel-${etikett}`)).toBeInTheDocument()
    }
    for (const etikett of ['Dauer', 'Eingabe-Token', 'Ausgabe-Token', 'Zwischenspeicher']) {
      expect(within(kennzahlen).getByTestId(`verbrauch-kachel-${etikett}`)).toBeInTheDocument()
    }
    expect(screen.queryByTestId(/^nachtlauf-kennzahl-/)).not.toBeInTheDocument()
  })

  it('zeigt Gesamtsumme, kartenbezogenen Anteil und Rest als drei getrennt beschriftete Zahlen', () => {
    zeige(nacht({}))

    expect(wertVon(kachel('Gesamtsumme'))).toBe('10,00')
    expect(wertVon(kachel('Karten zugeordnet'))).toBe('6,00')
    expect(wertVon(kachel('Rest'))).toBe('4,00')
    expect(einheitVon(kachel('Gesamtsumme'))).toBe('$')
  })

  /** Dieselben Einordnungen wie in der Zeitraumansicht (Task #1108, AK 2). */
  it('ordnet jede Kachel wie die Zeitraumansicht ein', () => {
    zeige(nacht({}))

    expect(lesbar(kachel('Gesamtsumme'))).toContain('5,00 $ je Lauf')
    expect(kachel('Karten zugeordnet')).toHaveTextContent('60 % der Summe')
    expect(kachel('Rest')).toHaveTextContent('keiner Karte zuzuordnen')
    expect(kachel('Läufe')).toHaveTextContent('2 Karten')
    expect(lesbar(kachel('Zwischenspeicher'))).toContain('73,8 % aus dem Zwischenspeicher')
  })

  it('zeigt eine Nacht aus zwei Laeufen als eine Nacht', () => {
    zeige(nacht({ runCount: 2 }))

    expect(screen.getAllByRole('heading', { level: 3 })).toHaveLength(1)
    expect(screen.getByRole('heading', { level: 3 })).toHaveTextContent(
      'Zyklus vom 15.09.2026 auf den 16.09.2026',
    )
    expect(screen.getByTestId('verbrauch-nacht-laeufe')).toHaveTextContent('2 Läufe')
  })

  /** Tokenmengen in der Einheit des Leitstands (Task #1108, AK 3), nicht als ausgeschriebene Zahl. */
  it('zeigt Dauer und Tokenmengen in der Einheit des Leitstands', () => {
    zeige(nacht({}))

    expect(wertVon(kachel('Dauer'))).toBe('1:30')
    expect(einheitVon(kachel('Dauer'))).toBe('h')
    expect(wertVon(kachel('Eingabe-Token'))).toBe('70,45')
    expect(einheitVon(kachel('Eingabe-Token'))).toBe('Mio')
    expect(wertVon(kachel('Ausgabe-Token'))).toBe('415')
    expect(einheitVon(kachel('Ausgabe-Token'))).toBe('Tsd')
    expect(wertVon(kachel('Zwischenspeicher'))).toBe('52,00')
    expect(einheitVon(kachel('Zwischenspeicher'))).toBe('Mio')
  })

  it('zeigt eine Dauer unter einer Stunde in Minuten', () => {
    zeige(nacht({ durationMs: 900_000 }))

    expect(wertVon(kachel('Dauer'))).toBe('15')
    expect(einheitVon(kachel('Dauer'))).toBe('min')
  })

  /**
   * Die Kartenzeilen sind entfallen (Task #1105): Was eine einzelne Karte gekostet hat, steht auf
   * derselben Seite schon beim konkreten Lauf.
   */
  it('zeigt keine Zeile je Karte und keinen Anteilsbalken, auch wenn die Antwort Karten fuehrt', () => {
    zeige(nacht({}))

    expect(screen.queryByTestId('verbrauch-nacht-karten')).not.toBeInTheDocument()
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument()
    expect(screen.queryByTestId('verbrauch-karte-721-anteil')).not.toBeInTheDocument()
    expect(lesbar(screen.getByTestId('verbrauch-nacht'))).not.toContain('der Kosten des Zyklus')
    expect(lesbar(screen.getByTestId('verbrauch-nacht'))).not.toContain('#721')
  })

  it('zeigt bei einer abgebrochenen Nacht den Hinweis neben den Zahlen', () => {
    zeige(nacht({ aborted: true }))

    expect(screen.getByTestId('verbrauch-nacht-abbruch')).toHaveTextContent(
      'Der Lauf wurde abgebrochen — die Werte sind unvollständig.',
    )
  })

  it('zeigt bei einer regulaer beendeten Nacht keinen Abbruch-Hinweis', () => {
    zeige(nacht({ aborted: false }))

    expect(screen.queryByTestId('verbrauch-nacht-abbruch')).not.toBeInTheDocument()
  })

  it('zeigt eine fehlende Angabe als „nicht gemessen" und nie als 0', () => {
    zeige(
      nacht({
        cardCount: 3,
        ...verbrauch(LEER),
        cards: [karte(721, 2, 60_000, nichts)],
      }),
    )

    for (const etikett of ['Gesamtsumme', 'Eingabe-Token', 'Ausgabe-Token', 'Zwischenspeicher']) {
      expect(kachel(etikett)).toHaveTextContent('nicht gemessen')
      expect(wertVon(kachel(etikett))).not.toMatch(/\d/)
    }
    expect(lesbar(kachel('Zwischenspeicher'))).not.toContain('aus dem Zwischenspeicher')
  })

  it('sagt es, wenn in der Nacht kein Lauf stattfand', () => {
    zeige(nacht({ runCount: 0, cardCount: 0, cards: [] }))

    expect(screen.getByText('In diesem Zyklus hat kein Lauf stattgefunden.')).toBeInTheDocument()
    expect(screen.queryByTestId('verbrauch-nacht-summen')).not.toBeInTheDocument()
    expect(screen.queryByTestId('verbrauch-nacht-kennzahlen')).not.toBeInTheDocument()
  })

  it('nennt einen einzelnen Lauf in der Einzahl', () => {
    zeige(nacht({ runCount: 1 }))

    expect(screen.getByTestId('verbrauch-nacht-laeufe')).toHaveTextContent('1 Lauf')
    expect(einheitVon(kachel('Läufe'))).toBe('Lauf')
  })
})

/**
 * Die Festlegung auf den Nachtlauf-Anteil (Issue #1016, Plan #1007): Die Nachtansicht bleibt in
 * ihrer Aussage auf Nachtläufe beschränkt, auch wenn derselbe Abruf seit #1013 die interaktiven
 * Sitzungen mitführt.
 */
describe('NachtlaufVerbrauchNacht — Nachtlauf-Anteil', () => {
  it('zeigt in den Summen den Nachtlauf-Anteil und nicht die Gesamtsumme', () => {
    zeige(
      nacht(
        verbrauch(
          aufteilung(angaben({ costUsd: 10 }), angaben({ costUsd: 6 }), angaben({ costUsd: 4 })),
          aufteilung(angaben({ costUsd: 5 }), angaben({ costUsd: 3 }), angaben({ costUsd: 2 })),
          aufteilung(angaben({ costUsd: 15 }), angaben({ costUsd: 9 }), angaben({ costUsd: 6 })),
        ),
      ),
    )

    expect(wertVon(kachel('Gesamtsumme'))).toBe('10,00')
    expect(wertVon(kachel('Karten zugeordnet'))).toBe('6,00')
    expect(wertVon(kachel('Rest'))).toBe('4,00')
    expect(lesbar(screen.getByTestId('verbrauch-nacht'))).not.toContain('15,00')
  })

  it('zeigt Mengen und Zwischenspeicher-Anteil des Nachtlauf-Anteils', () => {
    zeige(
      nacht(
        verbrauch(
          aufteilung(
            angaben({ inputTokens: 1000, outputTokens: 100, cachedInputTokens: 250, cachedInputSharePercent: 25 }),
          ),
          aufteilung(angaben({ inputTokens: 9000, outputTokens: 900, cachedInputTokens: 4500 })),
          aufteilung(
            angaben({ inputTokens: 10_000, outputTokens: 1000, cachedInputTokens: 4750, cachedInputSharePercent: 47.5 }),
          ),
        ),
      ),
    )

    expect(wertVon(kachel('Eingabe-Token'))).toBe('1')
    expect(einheitVon(kachel('Eingabe-Token'))).toBe('Tsd')
    expect(wertVon(kachel('Ausgabe-Token'))).toBe('100')
    expect(einheitVon(kachel('Ausgabe-Token'))).toBe('Token')
    expect(wertVon(kachel('Zwischenspeicher'))).toBe('250')
    expect(lesbar(kachel('Zwischenspeicher'))).toContain('25,0 % aus dem Zwischenspeicher')
  })

  /** „Nicht gemessen" bleibt „nicht gemessen" — auch wenn die Gesamtsumme einen Wert trägt. */
  it('schreibt einen ungemessenen Nachtlauf-Anteil nie als 0 und nie als Gesamtsumme', () => {
    zeige(
      nacht({
        ...verbrauch(LEER, aufteilung(angaben({ costUsd: 8 })), aufteilung(angaben({ costUsd: 8 }))),
        cards: [karte(721, 1, 60_000, nichts, angaben({ costUsd: 8 }), angaben({ costUsd: 8 }))],
      }),
    )

    for (const etikett of ['Gesamtsumme', 'Eingabe-Token', 'Ausgabe-Token', 'Zwischenspeicher']) {
      expect(kachel(etikett)).toHaveTextContent('nicht gemessen')
      expect(wertVon(kachel(etikett))).not.toMatch(/\d/)
    }
    expect(lesbar(screen.getByTestId('verbrauch-nacht'))).not.toContain('8,00 $')
  })

  /** Die Aufstellung je Stufe der Kette hängt in der Nacht (Issue #1117, #993 AK 8). */
  it('zeigt die Aufstellung je Stufe aus der Antwort der Nacht', () => {
    zeige(nacht({ stages: [stufe('PLAN', 5, 2), stufe('ABDECKUNG', 2)] }))

    const platte = screen.getByTestId('verbrauch-stufen')
    const zeilen = within(platte).getAllByRole('listitem')
    expect(zeilen.map((z) => lesbar(z))).toEqual([
      expect.stringContaining('Plan'),
      expect.stringContaining('Abdeckung'),
    ])
    // `Intl` setzt vor dem Währungszeichen ein geschütztes Leerzeichen; welches, hängt an der
    // ICU-Fassung — geprüft wird deshalb mit `\s` statt mit einem festen Zeichen im Literal.
    expect(lesbar(zeilen[0])).toMatch(/5,00\s\$/)
    expect(lesbar(zeilen[0])).toContain('2 Vorgänge')
  })

  it('zeigt die Aufstellung nicht, wenn die Nacht keine Stufen fuehrt', () => {
    zeige(nacht({}))

    expect(screen.queryByTestId('verbrauch-stufen')).not.toBeInTheDocument()
  })

  it('zeigt die Aufstellung nicht in einer Nacht ohne Lauf', () => {
    zeige(nacht({ runCount: 0, stages: [stufe('PLAN', 5)] }))

    expect(screen.queryByTestId('verbrauch-stufen')).not.toBeInTheDocument()
  })
})
