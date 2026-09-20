import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { projectsApi } from '../api/projects'
import { ProjectsPage } from '../pages/ProjectsPage'
import { StartRedirect } from './StartRedirect'

const useAuthMock = vi.fn()
vi.mock('../auth/AuthContext', () => ({ useAuth: () => useAuthMock() }))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))

const projekteMock = vi.mocked(projectsApi)

/** Zielseite, die Pfad und durchgereichten State sichtbar macht. */
function Ziel() {
  const { pathname, state } = useLocation()
  return (
    <div data-testid="ziel">{`${pathname} ${JSON.stringify(state ?? null)}`}</div>
  )
}

function zeige(platformRole: string, state?: unknown) {
  useAuthMock.mockReturnValue({
    user: { userId: 1, email: 'a@b.de', displayName: 'A', platformRole, memberships: [] },
    loading: false,
  })
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/', state }]}>
      <Routes>
        <Route path="/" element={<StartRedirect />} />
        <Route path="/projects" element={<Ziel />} />
        <Route path="/plattform-leitstand" element={<Ziel />} />
      </Routes>
    </MemoryRouter>,
  )
}

/**
 * Die Weiche auf `/` (Issue #1082, AK 1).
 *
 * Warum es sie gibt: Der Plattform-Admin soll beim Anmelden sehen, ob in der Nacht etwas
 * schiefging, ohne zu suchen — und alle anderen sollen weiterhin bei ihren Projekten landen.
 */
describe('StartRedirect (#1082)', () => {
  it('führt einen Plattform-Admin auf den Plattform-Leitstand', () => {
    zeige('ADMIN')

    expect(screen.getByTestId('ziel')).toHaveTextContent('/plattform-leitstand')
  })

  it('führt jeden anderen auf die Projektliste', () => {
    zeige('USER')

    expect(screen.getByTestId('ziel')).toHaveTextContent('/projects')
  })

  /**
   * Plan #1072 E21: `ProjectsPage` routet bei genau einem Projekt durch, wenn `location.key` noch
   * `default` ist **oder** `state.autoRoute` gesetzt wurde. Hinter einer Weiterleitung ist der Key
   * nicht mehr `default` — ohne den durchgereichten State verlöre ein Nutzer mit genau einem
   * Projekt das automatische Durchrouten nach dem Anmelden.
   */
  it('reicht autoRoute an die Projektliste durch', () => {
    zeige('USER', { autoRoute: true })

    expect(screen.getByTestId('ziel')).toHaveTextContent('{"autoRoute":true}')
  })

  it('leitet auch ohne State weiter', () => {
    zeige('USER')

    expect(screen.getByTestId('ziel')).toHaveTextContent('/projects null')
  })

  /**
   * Plan #1072 E21, als Kette: Nach dem Anmelden führt `/` über die Weiche auf `/projects`, und wer
   * genau ein Projekt hat, landet weiterhin direkt darin. Der `autoRoute`-State ist das einzige,
   * was das trägt — hinter der Weiterleitung ist `location.key` nicht mehr `default`.
   */
  it('lässt einen Nicht-Admin mit genau einem Projekt in diesem Projekt landen (#1082)', async () => {
    projekteMock.list.mockResolvedValue([{ id: 5, name: 'Solo', role: 'OWNER', createdAt: '' }])
    useAuthMock.mockReturnValue({
      user: { userId: 1, email: 'a@b.de', displayName: 'A', platformRole: 'USER', memberships: [] },
      loading: false,
    })

    render(
      <MemoryRouter initialEntries={[{ pathname: '/', state: { autoRoute: true } }]}>
        <Routes>
          <Route path="/" element={<StartRedirect />} />
          <Route path="/projects" element={<ProjectsPage />} />
          <Route path="/projects/:id" element={<div>Boardauswahl</div>} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText('Boardauswahl')).toBeInTheDocument()
  })
})
