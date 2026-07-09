import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { projectsApi } from '../api/projects'
import { ProjectsPage } from './ProjectsPage'

vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn(), create: vi.fn(), remove: vi.fn() } }))

const mocked = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }

describe('ProjectsPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('listet Projekte und zeigt Löschen nur für OWNER', async () => {
    mocked.list.mockResolvedValue([
      { id: 1, name: 'Meins', role: 'OWNER', createdAt: '2026-01-01T00:00:00Z' },
      { id: 2, name: 'Fremd', role: 'VIEWER', createdAt: '2026-01-01T00:00:00Z' },
    ])
    render(
      <MemoryRouter>
        <ProjectsPage />
      </MemoryRouter>,
    )

    expect(await screen.findByText('Meins')).toBeInTheDocument()
    expect(screen.getByText('Fremd')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Anlegen' })).toBeInTheDocument()
    // Löschen nur beim OWNER-Projekt:
    expect(screen.getByLabelText('Projekt Meins löschen')).toBeInTheDocument()
    expect(screen.queryByLabelText('Projekt Fremd löschen')).not.toBeInTheDocument()
  })
})
