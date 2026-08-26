import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { LegacyEpicsRedirect } from './LegacyEpicsRedirect'

/** Zielseite, die Pfad, Query und Fragment sichtbar macht. */
function Target() {
  const { pathname, search, hash } = useLocation()
  return <div data-testid="ziel">{`${pathname}${search}${hash}`}</div>
}

function renderAt(entry: string) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/boards/:boardId/epics" element={<LegacyEpicsRedirect />} />
        <Route path="/boards/:boardId/vorhaben" element={<Target />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('LegacyEpicsRedirect', () => {
  it('leitet /epics auf /vorhaben um und erhält dabei Query und Fragment', () => {
    renderAt('/boards/1/epics?filter=x#abschnitt')

    expect(screen.getByTestId('ziel')).toHaveTextContent('/boards/1/vorhaben?filter=x#abschnitt')
  })
})
