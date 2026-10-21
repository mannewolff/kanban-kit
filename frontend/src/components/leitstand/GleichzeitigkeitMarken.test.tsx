import { ThemeProvider } from '@mui/material/styles'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { NightRunConcurrencyView } from '../../api/nightRuns'
import { de } from '../../i18n/de'
import { gleichzeitigkeitsMarken } from '../../lib/gleichzeitigkeit'
import { cssRegel } from '../../test/cssRegel'
import { MELDER, theme } from '../../theme'
import { GleichzeitigkeitMarken } from './GleichzeitigkeitMarken'

const zeige = (concurrency: NightRunConcurrencyView | null) =>
  render(
    <ThemeProvider theme={theme}>
      <div data-testid="zeile">
        <GleichzeitigkeitMarken marken={gleichzeitigkeitsMarken(concurrency, de)} />
      </div>
    </ThemeProvider>,
  )

describe('GleichzeitigkeitMarken (Issue #1879, Plan #1876 E13)', () => {
  it('zeigt beide Marken als Wort in Stahl, wo beide Werte wahr sind', () => {
    zeige({ concurrent: true, checkWaitMs: 1_000, deferredAtMerge: true })

    expect(screen.getByTestId('zeile')).toHaveTextContent('gleichzeitigÜbernahme verschoben')
    expect(cssRegel(screen.getByText('gleichzeitig'))).toContain(`color: ${MELDER.stahl}`)
    expect(cssRegel(screen.getByText('Übernahme verschoben'))).toContain(`color: ${MELDER.stahl}`)
  })

  it('zeigt nur die Marke, deren Wert wahr ist', () => {
    zeige({ concurrent: true, checkWaitMs: null, deferredAtMerge: false })

    expect(screen.getByTestId('zeile')).toHaveTextContent(/^gleichzeitig$/)
  })

  it.each([
    ['falsch', { concurrent: false, checkWaitMs: 0, deferredAtMerge: false }],
    ['nicht gemeldet', { concurrent: null, checkWaitMs: null, deferredAtMerge: null }],
    ['ohne Angabe', null],
  ])('zeigt nichts bei %s', (_fall, concurrency) => {
    zeige(concurrency)

    expect(screen.getByTestId('zeile')).toBeEmptyDOMElement()
  })
})
