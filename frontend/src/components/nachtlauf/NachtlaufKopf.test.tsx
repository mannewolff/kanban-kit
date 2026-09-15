import { ThemeProvider } from '@mui/material/styles'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { nachtlaufTheme } from '../../nachtlaufDesign'
import { NachtlaufKopf } from './NachtlaufKopf'

/**
 * Der Kopf der Nacht (#915, AK 3): Vorzeile, Überschrift, Metazeile. Er stellt dar und rechnet
 * nicht — Modell, Label und Abschluss kommen fertig von der Seite, wo sie schon vor diesem Paket
 * gebildet wurden.
 */

const zeige = (eigenschaften: Parameters<typeof NachtlaufKopf>[0]) =>
  render(
    <ThemeProvider theme={nachtlaufTheme}>
      <NachtlaufKopf {...eigenschaften} />
    </ThemeProvider>,
  )

/** 14.09.2026, 13:12 Uhr Ortszeit — derselbe Lauf, den der Entwurf zeigt. */
const START = new Date(2026, 8, 14, 13, 12, 0).toISOString()

describe('NachtlaufKopf', () => {
  it('benennt die Lauf-Art in der Vorzeile', () => {
    zeige({ startedAt: START, mode: 'CHAIN', angaben: ['claude-opus-5', 'Ergebnisstand'] })
    expect(screen.getByTestId('nachtlauf-vorzeile')).toHaveTextContent('Nachtlauf · Kette')
  })

  it('benennt den Umsetzungs-Lauf in der Vorzeile', () => {
    zeige({
      startedAt: START,
      mode: 'IMPLEMENTATION',
      angaben: ['claude-opus-5', 'Ergebnisstand'],
    })
    expect(screen.getByTestId('nachtlauf-vorzeile')).toHaveTextContent('Nachtlauf · Umsetzung')
  })

  it('nennt in der Überschrift den Tag der Nacht', () => {
    zeige({ startedAt: START, mode: 'CHAIN', angaben: ['claude-opus-5', 'Ergebnisstand'] })
    expect(screen.getByTestId('nachtlauf-ueberschrift')).toHaveTextContent('Nacht vom 14. September')
  })

  it('führt in der Metazeile Laufkennung, die Angaben des Stands und die Herkunft', () => {
    zeige({
      startedAt: START,
      mode: 'CHAIN',
      angaben: ['claude-opus-5 · Label kit:night · regulär beendet', 'Ergebnisstand'],
    })
    // Die Laufkennung ist der Dateiname des Ergebnisstands — daran erkennt man den Lauf wieder.
    expect(screen.getByTestId('nachtlauf-meta')).toHaveTextContent(
      '2026-09-14-131200 · claude-opus-5 · Label kit:night · regulär beendet · Ergebnisstand',
    )
  })

  it('nennt auch eine unbekannte Herkunft (AK 9, Fall 3)', () => {
    zeige({
      startedAt: START,
      mode: 'IMPLEMENTATION',
      angaben: ['regulär beendet', 'Herkunft unbekannt'],
    })
    expect(screen.getByTestId('nachtlauf-meta')).toHaveTextContent('Herkunft unbekannt')
  })

  it('lässt eine leere Angabe des Stands weg, statt einen leeren Abschnitt zu zeigen', () => {
    zeige({ startedAt: START, mode: 'CHAIN', angaben: ['', 'Herkunft unbekannt'] })
    expect(screen.getByTestId('nachtlauf-meta')).toHaveTextContent(
      '2026-09-14-131200 · Herkunft unbekannt',
    )
  })

  it('setzt die Überschrift in die Schrift und Größe des Entwurfs', () => {
    zeige({ startedAt: START, mode: 'CHAIN', angaben: ['Ergebnisstand'] })
    const stil = getComputedStyle(screen.getByTestId('nachtlauf-ueberschrift'))
    expect(stil.fontFamily).toContain('Chivo')
    expect(stil.fontSize).toBe('clamp(28px, 5vw, 40px)')
  })

  it('rendert keine Überschriftenebene, weil der Kopf im Aufklappknopf steht', () => {
    // Ein `<h1>` ist Fluss-Inhalt und in einem `<button>` nicht erlaubt; der Kopf steht aber im
    // `AccordionSummary` des Laufs. Die Größe trägt die Gestaltung, nicht das Element.
    zeige({ startedAt: START, mode: 'CHAIN', angaben: ['Ergebnisstand'] })
    expect(screen.queryAllByRole('heading')).toHaveLength(0)
  })
})
