import { ThemeProvider } from '@mui/material/styles'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type {
  VerbrauchAngaben,
  VerbrauchAufteilung,
  VerbrauchKarte,
  VerbrauchNacht,
} from '../../api/nightRunUsage'
import { nachtlaufTheme } from '../../nachtlaufDesign'
import { NachtlaufVerbrauchNacht } from './NachtlaufVerbrauchNacht'

/** Die Nachtansicht der Verbrauchs-Auswertung (Issue #941, #926 AK 1–4). */

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
      angaben({ costUsd: 10, inputTokens: 1000, outputTokens: 100, cachedInputTokens: 250, cachedInputSharePercent: 25 }),
      angaben({ costUsd: 6 }),
      angaben({ costUsd: 4 }),
    ),
  ),
  aborted: false,
  cards: [
    karte(721, 2, 3_600_000, angaben({ costUsd: 4.5 })),
    karte(722, 1, null, angaben({ costUsd: 1.5 })),
  ],
  ...werte,
})

const zeige = (daten: VerbrauchNacht) =>
  render(
    <ThemeProvider theme={nachtlaufTheme}>
      <NachtlaufVerbrauchNacht nacht={daten} />
    </ThemeProvider>,
  )

/** `Intl` setzt vor das Währungszeichen ein geschütztes Leerzeichen. */
const lesbar = (element: HTMLElement) => element.textContent?.replaceAll(' ', ' ') ?? ''

describe('NachtlaufVerbrauchNacht', () => {
  it('zeigt Gesamtsumme, kartenbezogenen Anteil und Rest als drei getrennt beschriftete Zahlen', () => {
    zeige(nacht({}))

    const gesamt = screen.getByTestId('nachtlauf-kennzahl-Gesamtsumme')
    const karten = screen.getByTestId('nachtlauf-kennzahl-Einzelnen Karten zugeordnet')
    const rest = screen.getByTestId('nachtlauf-kennzahl-Keiner Karte zuzuordnen')
    expect(lesbar(gesamt)).toContain('10,00 $')
    expect(lesbar(karten)).toContain('6,00 $')
    expect(lesbar(rest)).toContain('4,00 $')
  })

  it('zeigt eine Nacht aus zwei Laeufen als eine Nacht', () => {
    zeige(nacht({ runCount: 2 }))

    expect(screen.getAllByRole('heading', { level: 3 })).toHaveLength(1)
    expect(screen.getByRole('heading', { level: 3 })).toHaveTextContent(
      'Nacht vom 15.09.2026 auf den 16.09.2026',
    )
    expect(screen.getByTestId('verbrauch-nacht-laeufe')).toHaveTextContent('2 Läufe')
  })

  it('zeigt Karten, Dauer, die vier Verbrauchsangaben und den Zwischenspeicher-Anteil', () => {
    zeige(nacht({}))

    expect(screen.getByTestId('nachtlauf-kennzahl-Bearbeitete Karten')).toHaveTextContent('2')
    expect(screen.getByTestId('nachtlauf-kennzahl-Dauer')).toHaveTextContent('1 Std 30 Min')
    expect(screen.getByTestId('nachtlauf-kennzahl-Eingabe')).toHaveTextContent('1.000 Token')
    expect(screen.getByTestId('nachtlauf-kennzahl-Ausgabe')).toHaveTextContent('100 Token')
    expect(screen.getByTestId('nachtlauf-kennzahl-Zwischenspeicher')).toHaveTextContent('250 Token')
    expect(lesbar(screen.getByTestId('nachtlauf-kennzahl-Anteil aus dem Zwischenspeicher'))).toContain(
      '25,0 %',
    )
  })

  it('zeigt die Kartenzeilen mit ihren Summen ueber die Anlaeufe dieser Nacht', () => {
    zeige(nacht({}))

    const zeilen = within(screen.getByTestId('verbrauch-nacht-karten')).getAllByRole('listitem')
    expect(zeilen).toHaveLength(2)
    expect(lesbar(zeilen[0])).toContain('#721')
    expect(lesbar(zeilen[0])).toContain('2 Anläufe')
    expect(lesbar(zeilen[0])).toContain('4,50 $')
    expect(lesbar(zeilen[1])).toContain('1 Anlauf')
    expect(lesbar(zeilen[1])).toContain('Dauer nicht gemessen')
  })

  it('zeigt je Karte ihren Anteil an den Kosten der Nacht als Balken', () => {
    zeige(nacht({}))

    expect(screen.getByTestId('verbrauch-karte-721-anteil')).toHaveAttribute('data-anteil', '45')
    expect(screen.getByTestId('verbrauch-karte-721-anteil')).toHaveAccessibleName(
      'Karte #721: 45 % der Kosten der Nacht',
    )
  })

  it('zeigt keinen Balken, wo Kosten der Karte oder der Nacht fehlen oder die Nacht nichts kostete', () => {
    zeige(nacht({ cards: [karte(1, 1, 1, nichts)] }))
    expect(screen.queryByTestId('verbrauch-karte-1-anteil')).not.toBeInTheDocument()
  })

  it('zeigt keinen Balken, wenn die Nacht selbst keine Kosten traegt', () => {
    zeige(
      nacht({
        ...verbrauch(aufteilung(angaben({ costUsd: 0 }))),
        cards: [karte(1, 1, 1, angaben({ costUsd: 0 }))],
      }),
    )
    expect(screen.queryByTestId('verbrauch-karte-1-anteil')).not.toBeInTheDocument()
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

    for (const label of ['Gesamtsumme', 'Eingabe', 'Ausgabe', 'Zwischenspeicher']) {
      const kennzahl = screen.getByTestId(`nachtlauf-kennzahl-${label}`)
      expect(kennzahl).toHaveTextContent('nicht gemessen')
      expect(kennzahl.textContent).not.toMatch(/\b0\b/)
    }
    expect(screen.getByTestId('nachtlauf-kennzahl-Anteil aus dem Zwischenspeicher')).toHaveTextContent(
      'nicht bestimmt',
    )
    expect(within(screen.getByTestId('verbrauch-nacht-karten')).getByRole('listitem').textContent).not.toMatch(
      /\b0\b/,
    )
  })

  it('sagt es, wenn in der Nacht kein Lauf stattfand', () => {
    zeige(nacht({ runCount: 0, cardCount: 0, cards: [] }))

    expect(screen.getByText('In dieser Nacht hat kein Lauf stattgefunden.')).toBeInTheDocument()
    expect(screen.queryByTestId('verbrauch-nacht-summen')).not.toBeInTheDocument()
    expect(screen.queryByTestId('verbrauch-nacht-kennzahlen')).not.toBeInTheDocument()
  })

  it('nennt einen einzelnen Lauf in der Einzahl', () => {
    zeige(nacht({ runCount: 1 }))

    expect(screen.getByTestId('verbrauch-nacht-laeufe')).toHaveTextContent('1 Lauf')
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

    expect(lesbar(screen.getByTestId('nachtlauf-kennzahl-Gesamtsumme'))).toContain('10,00 $')
    expect(lesbar(screen.getByTestId('nachtlauf-kennzahl-Gesamtsumme'))).not.toContain('15,00')
    expect(lesbar(screen.getByTestId('nachtlauf-kennzahl-Einzelnen Karten zugeordnet'))).toContain(
      '6,00 $',
    )
    expect(lesbar(screen.getByTestId('nachtlauf-kennzahl-Keiner Karte zuzuordnen'))).toContain(
      '4,00 $',
    )
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

    expect(screen.getByTestId('nachtlauf-kennzahl-Eingabe')).toHaveTextContent('1.000 Token')
    expect(screen.getByTestId('nachtlauf-kennzahl-Ausgabe')).toHaveTextContent('100 Token')
    expect(screen.getByTestId('nachtlauf-kennzahl-Zwischenspeicher')).toHaveTextContent('250 Token')
    expect(
      lesbar(screen.getByTestId('nachtlauf-kennzahl-Anteil aus dem Zwischenspeicher')),
    ).toContain('25,0 %')
  })

  it('zeigt je Kartenzeile den Nachtlauf-Anteil und bezieht den Balken darauf', () => {
    zeige(
      nacht({
        ...verbrauch(
          aufteilung(angaben({ costUsd: 10 })),
          aufteilung(angaben({ costUsd: 10 })),
          aufteilung(angaben({ costUsd: 20 })),
        ),
        cards: [
          karte(721, 2, 3_600_000, angaben({ costUsd: 4.5 }), angaben({ costUsd: 5.5 }), angaben({ costUsd: 10 })),
        ],
      }),
    )

    const zeile = within(screen.getByTestId('verbrauch-nacht-karten')).getByRole('listitem')
    expect(lesbar(zeile)).toContain('4,50 $')
    expect(lesbar(zeile)).not.toContain('10,00 $')
    // 4,50 $ von 10,00 $ Nachtlauf-Anteil — nicht 10,00 $ von 20,00 $ Gesamtsumme.
    expect(screen.getByTestId('verbrauch-karte-721-anteil')).toHaveAttribute('data-anteil', '45')
  })

  /** „Nicht gemessen" bleibt „nicht gemessen" — auch wenn die Gesamtsumme einen Wert trägt. */
  it('schreibt einen ungemessenen Nachtlauf-Anteil nie als 0 und nie als Gesamtsumme', () => {
    zeige(
      nacht({
        ...verbrauch(LEER, aufteilung(angaben({ costUsd: 8 })), aufteilung(angaben({ costUsd: 8 }))),
        cards: [karte(721, 1, 60_000, nichts, angaben({ costUsd: 8 }), angaben({ costUsd: 8 }))],
      }),
    )

    for (const label of ['Gesamtsumme', 'Eingabe', 'Ausgabe', 'Zwischenspeicher']) {
      const kennzahl = screen.getByTestId(`nachtlauf-kennzahl-${label}`)
      expect(kennzahl).toHaveTextContent('nicht gemessen')
      expect(kennzahl.textContent).not.toMatch(/\d/)
    }
    const zeile = within(screen.getByTestId('verbrauch-nacht-karten')).getByRole('listitem')
    expect(lesbar(zeile)).toContain('Kosten nicht gemessen')
    expect(lesbar(zeile)).not.toContain('8,00 $')
    expect(screen.queryByTestId('verbrauch-karte-721-anteil')).not.toBeInTheDocument()
  })
})
