import { ThemeProvider } from '@mui/material/styles'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { NightRunConcurrencyView } from '../../api/nightRuns'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { cssRegel } from '../../test/cssRegel'
import { TEXT_SCHWACH, theme } from '../../theme'
import { GleichzeitigkeitAngaben } from './GleichzeitigkeitAngaben'

/** Die Angaben eines Pakets, das neben anderen lief und 7 Minuten auf seine Prüfung wartete. */
const VOLL: NightRunConcurrencyView = { concurrent: true, checkWaitMs: 7 * 60_000, deferredAtMerge: false }

const zeige = (concurrency: NightRunConcurrencyView) =>
  render(
    <ThemeProvider theme={theme}>
      <GleichzeitigkeitAngaben concurrency={concurrency} />
    </ThemeProvider>,
  )

const begriffe = () => screen.getAllByRole('term').map((b) => b.textContent)
const werte = () => screen.getAllByRole('definition').map((w) => w.textContent)

describe('GleichzeitigkeitAngaben (Issue #1879, Plan #1876 E14, E16)', () => {
  it('zeigt die drei Begriffe mit ihren Werten', () => {
    zeige(VOLL)

    expect(begriffe()).toEqual(['Gleichzeitig', 'Wartezeit auf die Prüfung', 'Übernahme verschoben'])
    expect(werte()).toEqual(['ja', '7 min 0 s', 'nein'])
  })

  it('zeigt „nein“ und „ja“ bei den umgekehrten Wahrheitswerten und eine gemeldete 0 als Wert', () => {
    zeige({ concurrent: false, checkWaitMs: 0, deferredAtMerge: true })

    expect(werte()).toEqual(['nein', '0 min 0 s', 'ja'])
    expect(cssRegel(screen.getByText('0 min 0 s'))).toContain('color: var(--mb-palette-text-primary)')
  })

  it('zeigt eine fehlende Angabe als „nicht gemeldet“ in TEXT_SCHWACH', () => {
    zeige({ ...VOLL, checkWaitMs: null })

    expect(werte()).toEqual(['ja', 'nicht gemeldet', 'nein'])
    expect(cssRegel(screen.getByText('nicht gemeldet'))).toContain(`color: ${TEXT_SCHWACH}`)
    expect(cssRegel(screen.getByText('ja'))).toContain('color: var(--mb-palette-text-primary)')
  })

  it('zeigt ohne jeden Wert dreimal „nicht gemeldet“', () => {
    zeige({ concurrent: null, checkWaitMs: null, deferredAtMerge: null })

    expect(werte()).toEqual(['nicht gemeldet', 'nicht gemeldet', 'nicht gemeldet'])
  })

  it('trägt keine eigene Überschrift', () => {
    zeige(VOLL)

    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
  })
})

describe('GleichzeitigkeitAngaben auf Englisch', () => {
  it('nennt Begriffe, Werte und „not reported“ auf Englisch', () => {
    renderMitSprache(
      <ThemeProvider theme={theme}>
        <GleichzeitigkeitAngaben concurrency={{ concurrent: true, checkWaitMs: null, deferredAtMerge: false }} />
      </ThemeProvider>,
      'en',
    )

    expect(begriffe()).toEqual(['Concurrent', 'Wait for a check slot', 'Merge deferred'])
    expect(werte()).toEqual(['yes', 'not reported', 'no'])
  })
})
