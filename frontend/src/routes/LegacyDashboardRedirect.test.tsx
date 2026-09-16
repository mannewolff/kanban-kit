import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { LegacyDashboardRedirect } from './LegacyDashboardRedirect'

/** Zielseite, die Pfad, Query und Fragment sichtbar macht. */
function Target() {
  const { pathname, search, hash } = useLocation()
  return <div data-testid="ziel">{`${pathname}${search}${hash}`}</div>
}

describe('LegacyDashboardRedirect (#979)', () => {
  it('leitet /dashboard auf den Leitstand um und erhält dabei Query und Fragment', () => {
    render(
      <MemoryRouter initialEntries={['/boards/7/dashboard?x=1#oben']}>
        <Routes>
          <Route path="/boards/:boardId/dashboard" element={<LegacyDashboardRedirect />} />
          <Route path="/boards/:boardId/leitstand" element={<Target />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByTestId('ziel')).toHaveTextContent('/boards/7/leitstand?x=1#oben')
  })
})
