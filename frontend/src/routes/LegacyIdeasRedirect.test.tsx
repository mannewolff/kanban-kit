import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { LegacyIdeasRedirect } from './LegacyIdeasRedirect'

/** Zielseite, die Pfad, Query und Fragment sichtbar macht. */
function Target() {
  const { pathname, search, hash } = useLocation()
  return <div data-testid="ziel">{`${pathname}${search}${hash}`}</div>
}

function renderAt(entry: string) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/projects/:projectId/ideas" element={<LegacyIdeasRedirect />} />
        <Route path="/projects/:projectId" element={<Target />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('LegacyIdeasRedirect', () => {
  it('leitet /projects/7/ideas auf die Projektseite um', () => {
    renderAt('/projects/7/ideas')

    expect(screen.getByTestId('ziel')).toHaveTextContent('/projects/7')
  })

  it('nimmt Query und Fragment mit', () => {
    renderAt('/projects/7/ideas?filter=x#abschnitt')

    expect(screen.getByTestId('ziel')).toHaveTextContent('/projects/7?filter=x#abschnitt')
  })
})
