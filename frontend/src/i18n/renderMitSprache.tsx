import { render, type RenderOptions, type RenderResult } from '@testing-library/react'
import type { ReactElement } from 'react'
import { SpracheProvider } from './SpracheContext'
import type { Sprache } from './woerterbuch'

/**
 * Rendert für einen Test in einer festen Sprache, Vorgabe Deutsch (Plan #1638, A10). Ohne Laden:
 * Der Provider bekommt die Sprache als Anfangswert und rendert sofort.
 */
export function renderMitSprache(
  ui: ReactElement,
  sprache: Sprache = 'de',
  optionen?: Omit<RenderOptions, 'wrapper'>,
): RenderResult {
  return render(ui, {
    ...optionen,
    wrapper: ({ children }) => <SpracheProvider anfangs={sprache}>{children}</SpracheProvider>,
  })
}
