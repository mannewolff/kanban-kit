import { ThemeProvider } from '@mui/material/styles'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { CardByNumber } from '../../api/cards'
import type { NightRunState } from '../../lib/nightRunLog'
import { NACHTLAUF_FARBEN, NACHTLAUF_TON, nachtlaufTheme } from '../../nachtlaufDesign'
import { NachtlaufVorgang } from './NachtlaufVorgang'

/**
 * Der Vorgangsblock eines Ketten-Laufs (#916, AK 4, 6, 7). Geprüft wird, was aus den fertigen
 * Angaben wird — gerechnet wird weiter in der Seite.
 */

const karte = (number: number, title: string): CardByNumber =>
  ({ id: number, number, title }) as CardByNumber

type Eigenschaften = Parameters<typeof NachtlaufVorgang>[0]

const grundstellung: Eigenschaften = {
  nummer: 791,
  titel: 'Zugriff und Konten bleiben abgesichert',
  zustand: 'GREEN',
  ausgangswort: 'fertig',
  grund: null,
  band: null,
  chipgruppen: [],
  kennzahlen: '11,52 $ · 89 Züge',
  onOeffnen: vi.fn(),
}

const zeige = (felder: Partial<Eigenschaften> = {}) =>
  render(
    <ThemeProvider theme={nachtlaufTheme}>
      <NachtlaufVorgang {...grundstellung} {...felder} />
    </ThemeProvider>,
  )

/** `getComputedStyle` gibt Farben als `rgb(…)` zurück, die Tokens stehen als `#rrggbb`. */
function alsRgb(hex: string): string {
  const kanal = (position: number) => Number.parseInt(hex.slice(position, position + 2), 16)
  return `rgb(${kanal(1)}, ${kanal(3)}, ${kanal(5)})`
}

describe('NachtlaufVorgang', () => {
  it('führt Nummer, Titel und Ausgang in der Kopfzeile (AK 4)', () => {
    zeige()

    const block = screen.getByTestId('paket-791')
    expect(block).toHaveTextContent('#791')
    expect(block).toHaveTextContent('Zugriff und Konten bleiben abgesichert')
    expect(screen.getByTestId('zustand-791')).toHaveTextContent('fertig')
  })

  it('nennt den Ausgang in Worten, nicht allein in einer Farbe (AK 7)', () => {
    zeige({ zustand: 'YELLOW', ausgangswort: 'Zeitbudget' })

    // Der Punkt in der Pille ist `aria-hidden` und trägt `currentColor` — die Aussage steht im
    // Text daneben und ist auch ohne Farbwahrnehmung zu lesen.
    const pille = screen.getByTestId('zustand-791')
    expect(pille).toHaveTextContent('Zeitbudget')
    // Als eigener Textknoten und nicht bloß als Attribut: So liest ihn auch ein Vorlesewerkzeug.
    expect(within(pille).getByText('Zeitbudget')).toBeInTheDocument()
  })

  it('stellt den Grund eines an einer Grenze beendeten Vorgangs als eigenen Satz darunter (AK 7)', () => {
    zeige({
      zustand: 'YELLOW',
      ausgangswort: 'Zeitbudget',
      grund: 'Die Review-Session wurde nach 15,0 min am Limit beendet.',
    })

    expect(screen.getByTestId('abbruch-791')).toHaveTextContent(
      'Die Review-Session wurde nach 15,0 min am Limit beendet.',
    )
  })

  it('zeigt an einem regulär beendeten Vorgang keinen Grund', () => {
    zeige()
    expect(screen.queryByTestId('abbruch-791')).not.toBeInTheDocument()
  })

  it.each([
    ['GREEN', NACHTLAUF_FARBEN.gut],
    ['YELLOW', NACHTLAUF_FARBEN.budget],
    ['RED', NACHTLAUF_FARBEN.leer],
    ['GREY', NACHTLAUF_FARBEN.nie],
  ] as ReadonlyArray<readonly [NightRunState, string]>)(
    'gibt dem Zustand %s den Ton des Entwurfs',
    (zustand, ton) => {
      zeige({ zustand })

      expect(getComputedStyle(screen.getByTestId('zustand-791')).color).toBe(alsRgb(ton))
      // Dieselbe Zuordnung, die `nachtlaufDesign.ts` für das Band führt (E10).
      expect(NACHTLAUF_TON[zustand]).toBe(ton)
    },
  )

  it('führt Kosten, Züge und den Anteil der Modellarbeit in der Ergebniszeile (AK 6)', () => {
    zeige({ kennzahlen: '11,52 $ · 89 Züge · Modellarbeit 78 % der Dauer, der Rest außerhalb' })

    expect(screen.getByTestId('kennzahlen-791')).toHaveTextContent(
      'Modellarbeit 78 % der Dauer, der Rest außerhalb',
    )
  })

  it('zeigt die entstandenen Karten je Arbeitsschritt', () => {
    zeige({
      chipgruppen: [
        {
          label: 'Plan',
          leer: 'kein Plan',
          testId: 'dokumente-791-plan',
          chips: [{ id: '844', art: 'Plan', zustand: { art: 'geladen', karte: karte(844, 'Plan A') } }],
        },
        { label: 'Pakete', leer: 'keine Pakete', testId: 'dokumente-791-pakete', chips: [] },
      ],
    })

    expect(screen.getByTestId('ergebnis-791')).toHaveTextContent('Entstanden')
    expect(screen.getByTestId('dokumente-791-plan')).toHaveTextContent('#844')
    expect(screen.getByTestId('dokumente-791-pakete')).toHaveTextContent('keine Pakete')
  })

  it('lässt die Vorzeile „Entstanden" weg, wo es keine Arbeitsschritte mit Karten gibt', () => {
    zeige({ chipgruppen: [] })
    expect(screen.getByTestId('ergebnis-791')).not.toHaveTextContent('Entstanden')
  })

  it('zeigt das Stufenband, wo Arbeitsschritte vorliegen', () => {
    zeige({
      band: {
        ansage: 'Stufenband: Plan 7,8 / 20 min',
        abschnitte: [
          {
            schluessel: 'plan',
            label: 'Plan',
            anteil: 20,
            fuellung: 39,
            erreicht: true,
            zahlen: '7,8 / 20 min',
            vermerk: null,
            farbe: NACHTLAUF_FARBEN.gut,
          },
        ],
      },
    })

    expect(screen.getByTestId('stufenband-791')).toBeInTheDocument()
    expect(screen.getByTestId('stufe-791-plan')).toHaveTextContent('7,8 / 20 min')
  })

  it('zeigt ohne Arbeitsschritte kein Band', () => {
    zeige({ band: null })
    expect(screen.queryByTestId('stufenband-791')).not.toBeInTheDocument()
  })

  it('hängt an, was der Entwurf nicht vorsieht (AK 10)', () => {
    zeige({ children: <div data-testid="zusatz">Übernahmetext</div> })

    const block = screen.getByTestId('paket-791')
    expect(within(block).getByTestId('zusatz')).toBeInTheDocument()
  })
})
