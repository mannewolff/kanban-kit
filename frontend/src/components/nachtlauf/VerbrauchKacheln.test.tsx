import { ThemeProvider } from '@mui/material/styles'
import { render, screen, within } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import type { VerbrauchAngaben, VerbrauchAufteilung } from '../../api/nightRunUsage'
import { dollar } from '../../lib/leitstand'
import { theme } from '../../theme'
import { VerbrauchKachel, VerbrauchKachelRaster, VerbrauchKostenKacheln } from './VerbrauchKacheln'

/**
 * Die gemeinsame Kachel der Verbrauchs-Auswertung (Task #1108): Nachtansicht und Zeitraumansicht
 * zeigen ihre Zahlen durch denselben Baustein.
 */

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

const zeige = (inhalt: ReactNode) => render(<ThemeProvider theme={theme}>{inhalt}</ThemeProvider>)

const kachel = (etikett: string) => screen.getByTestId(`verbrauch-kachel-${etikett}`)

/** Der große Wert der Kachel — ohne die Einordnung darunter, die dieselbe Zahl führen kann. */
const wertVon = (kachel: HTMLElement) => within(kachel).getByTestId('kachel-wert').textContent

/** Die kleine Einheit steht unmittelbar hinter dem Wert. */
const einheitVon = (kachel: HTMLElement) =>
  within(kachel).getByTestId('kachel-wert').nextElementSibling?.textContent

/** `Intl` setzt vor die Einheit ein geschütztes Leerzeichen; verglichen wird der Wortlaut. */
const lesbar = (element: HTMLElement) => element.textContent?.replaceAll(' ', ' ') ?? ''

describe('VerbrauchKachel', () => {
  it('zeigt Etikett, Wert mit Einheit und die Einordnung darunter', () => {
    zeige(<VerbrauchKachel etikett="Gesamtsumme" wert="14,29" einheit="$" basis="14,29 $ je Run" />)

    expect(kachel('Gesamtsumme')).toHaveAccessibleName('Gesamtsumme')
    expect(wertVon(kachel('Gesamtsumme'))).toBe('14,29')
    expect(einheitVon(kachel('Gesamtsumme'))).toBe('$')
    expect(lesbar(kachel('Gesamtsumme'))).toContain('14,29 $ je Run')
  })

  it('zeigt einen fehlenden Wert als „nicht gemessen" und nie als 0', () => {
    zeige(<VerbrauchKachel etikett="Eingabe-Token" wert={null} einheit="Mio" basis="" />)

    expect(einheitVon(kachel('Eingabe-Token'))).toBe('nicht gemessen')
    expect(kachel('Eingabe-Token').textContent).not.toMatch(/\d/)
  })

  /**
   * Ein Rest knapp unter null stand als „-0,00 $" (Task #1108): Das Vorzeichen behauptet einen
   * negativen Betrag, den die zwei Nachkommastellen gar nicht mehr zeigen.
   */
  it('schreibt einen auf null gerundeten Betrag ohne Vorzeichen', () => {
    zeige(<VerbrauchKachel etikett="Rest" wert={dollar(-0.001)} einheit="$" basis="" />)

    expect(wertVon(kachel('Rest'))).toBe('0,00')
  })

  it('behaelt das Vorzeichen eines Betrags, der sichtbar unter null liegt', () => {
    zeige(<VerbrauchKachel etikett="Rest" wert={dollar(-1.5)} einheit="$" basis="" />)

    expect(wertVon(kachel('Rest'))).toBe('-1,50')
  })
})

describe('VerbrauchKachelRaster', () => {
  it('traegt die uebergebene Kennung und die Kacheln darin', () => {
    zeige(
      <VerbrauchKachelRaster testId="verbrauch-nacht-summen">
        <VerbrauchKachel etikett="Rest" wert="4,00" einheit="$" basis="" />
      </VerbrauchKachelRaster>,
    )

    expect(screen.getByTestId('verbrauch-nacht-summen')).toContainElement(kachel('Rest'))
  })

  it('kommt ohne Kennung aus', () => {
    zeige(
      <VerbrauchKachelRaster>
        <VerbrauchKachel etikett="Rest" wert="4,00" einheit="$" basis="" />
      </VerbrauchKachelRaster>,
    )

    expect(kachel('Rest')).toBeInTheDocument()
  })
})

describe('VerbrauchKostenKacheln', () => {
  it('zeigt die vier Kosten-Kacheln mit ihren Einordnungen', () => {
    zeige(
      <VerbrauchKostenKacheln
        kennzahlen={{
          runCount: 2,
          cardCount: 5,
          usageByKind: {
            night: aufteilung(
              angaben({ costUsd: 10 }),
              angaben({ costUsd: 6 }),
              angaben({ costUsd: 4 }),
            ),
            interactive: aufteilung(angaben({ costUsd: 99 })),
          },
        }}
      />,
    )

    expect(wertVon(kachel('Gesamtsumme'))).toBe('10,00')
    expect(lesbar(kachel('Gesamtsumme'))).toContain('5,00 $ je Run')
    expect(kachel('Karten zugeordnet')).toHaveTextContent('60 % der Summe')
    expect(kachel('Rest')).toHaveTextContent('keiner Karte zuzuordnen')
    expect(wertVon(kachel('Runs'))).toBe('2')
    expect(einheitVon(kachel('Runs'))).toBe('Runs')
    expect(kachel('Runs')).toHaveTextContent('5 Karten')
  })

  it('laesst die Einordnung leer, wo die Zahl dafuer fehlt', () => {
    zeige(
      <VerbrauchKostenKacheln
        kennzahlen={{
          runCount: 0,
          cardCount: 0,
          usageByKind: { night: aufteilung(nichts), interactive: aufteilung(nichts) },
        }}
      />,
    )

    expect(lesbar(kachel('Gesamtsumme'))).not.toContain('je Run')
    expect(lesbar(kachel('Karten zugeordnet'))).not.toContain('der Summe')
  })

  /** Eine gemessene Null ist keine Bezugsgröße: „0 % der Summe" wäre eine Aussage über nichts. */
  it('laesst den Anteil leer, wenn die Gesamtsumme null ist', () => {
    zeige(
      <VerbrauchKostenKacheln
        kennzahlen={{
          runCount: 1,
          cardCount: 1,
          usageByKind: {
            night: aufteilung(
              angaben({ costUsd: 0 }),
              angaben({ costUsd: 0 }),
              angaben({ costUsd: 0 }),
            ),
            interactive: aufteilung(nichts),
          },
        }}
      />,
    )

    expect(lesbar(kachel('Karten zugeordnet'))).not.toContain('der Summe')
    expect(einheitVon(kachel('Runs'))).toBe('Run')
  })
})
