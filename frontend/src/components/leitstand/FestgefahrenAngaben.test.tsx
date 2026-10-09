import { ThemeProvider } from '@mui/material/styles'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { NightRunStuckAngaben } from '../../lib/nightRunHandoff'
import { theme } from '../../theme'
import { FestgefahrenAngaben } from './FestgefahrenAngaben'

/** Alle Angaben eines festgefahrenen Pakets, wie das Kit sie meldet (Plan #1547, E9). */
const VOLL: NightRunStuckAngaben = {
  check: 'mvn verify',
  error: 'OpenApiIT: Vertrag weicht ab',
  attempts: 3,
  sessionLimitMs: 3_600_000,
  sessionId: 'a1b2c3d4',
}

const ZWANZIG_MIN = 20 * 60_000

function zeige(stuck: NightRunStuckAngaben | null | undefined, durationMs: number | null | undefined) {
  return render(
    <ThemeProvider theme={theme}>
      <FestgefahrenAngaben stuck={stuck} durationMs={durationMs} />
    </ThemeProvider>,
  )
}

/**
 * Der Wert zu einem Begriff der Beschreibungsliste — abgefragt über die Rollen `term` und
 * `definition`, so wie ein Vorlesewerkzeug das Paar liest.
 */
function wert(begriff: string): string | null {
  const bereich = screen.getByRole('region', { name: 'Festgefahren' })
  const begriffe = within(bereich).getAllByRole('term')
  const werte = within(bereich).getAllByRole('definition')
  const index = begriffe.findIndex((b) => b.textContent === begriff)
  expect(index, `Begriff „${begriff}“ fehlt`).toBeGreaterThanOrEqual(0)
  return werte[index].textContent
}

describe('FestgefahrenAngaben (Issue #1552, Plan #1547 E14, E17)', () => {
  it('trägt die Überschrift „Festgefahren“ als Wort, nicht nur als Farbe', () => {
    zeige(VOLL, ZWANZIG_MIN)

    expect(screen.getByRole('heading', { name: 'Festgefahren' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Festgefahren' })).toBeInTheDocument()
  })

  it('zeigt alle sechs Angaben in der Reihenfolge des Fachplans', () => {
    zeige(VOLL, ZWANZIG_MIN)

    const bereich = screen.getByRole('region', { name: 'Festgefahren' })
    expect(within(bereich).getAllByRole('term').map((b) => b.textContent)).toEqual([
      'Prüfung',
      'Fehler',
      'Versuche',
      'Laufzeit bis Abbruch',
      'Zeitgrenze der Sitzung',
      'geschätzte gesparte Zeit',
    ])
    expect(wert('Prüfung')).toBe('mvn verify')
    expect(wert('Fehler')).toBe('OpenApiIT: Vertrag weicht ab')
    expect(wert('Versuche')).toBe('3')
    expect(wert('Laufzeit bis Abbruch')).toBe('20 min')
    expect(wert('Zeitgrenze der Sitzung')).toBe('1 h 0 min')
    expect(wert('geschätzte gesparte Zeit')).toBe('40 min')
  })

  it.each([
    ['Prüfung', { ...VOLL, check: null }],
    ['Fehler', { ...VOLL, error: undefined }],
    ['Versuche', { ...VOLL, attempts: null }],
    ['Zeitgrenze der Sitzung', { ...VOLL, sessionLimitMs: null }],
  ])('schreibt „nicht gemeldet“, wo die Angabe „%s“ fehlt', (begriff, stuck) => {
    zeige(stuck, ZWANZIG_MIN)

    expect(wert(begriff)).toBe('nicht gemeldet')
  })

  it('schreibt „nicht gemeldet“ für die Laufzeit bis Abbruch, wo keine Laufzeit gemeldet ist', () => {
    zeige(VOLL, null)

    expect(wert('Laufzeit bis Abbruch')).toBe('nicht gemeldet')
  })

  it('schreibt „nicht gemeldet“ für die gesparte Zeit ohne Zeitgrenze', () => {
    zeige({ ...VOLL, sessionLimitMs: undefined }, ZWANZIG_MIN)

    expect(wert('geschätzte gesparte Zeit')).toBe('nicht gemeldet')
  })

  it('schreibt „nicht gemeldet“ für die gesparte Zeit ohne Laufzeit', () => {
    zeige(VOLL, undefined)

    expect(wert('geschätzte gesparte Zeit')).toBe('nicht gemeldet')
  })

  it('zeigt ohne jede Angabe sechsmal „nicht gemeldet“', () => {
    zeige(null, null)

    const bereich = screen.getByRole('region', { name: 'Festgefahren' })
    expect(within(bereich).getAllByRole('definition').map((w) => w.textContent)).toEqual(
      Array(6).fill('nicht gemeldet'),
    )
  })

  it('zeigt 0 min, wenn die Laufzeit über der Zeitgrenze liegt', () => {
    zeige({ ...VOLL, sessionLimitMs: 3_600_000 }, 4_000_000)

    expect(wert('geschätzte gesparte Zeit')).toBe('0 min')
  })
})
