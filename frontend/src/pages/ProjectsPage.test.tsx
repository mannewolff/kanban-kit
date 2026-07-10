import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { projectsApi } from '../api/projects'
import { ProjectsPage } from './ProjectsPage'

vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn(), create: vi.fn(), remove: vi.fn() } }))

let mockUser: { platformRole: string } | null = { platformRole: 'USER' }
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ user: mockUser }) }))

const mocked = projectsApi as unknown as {
  list: ReturnType<typeof vi.fn>
  create: ReturnType<typeof vi.fn>
  remove: ReturnType<typeof vi.fn>
}

describe('ProjectsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUser = { platformRole: 'USER' }
  })

  it('zeigt Nicht-Admins weder Anlegen noch Löschen', async () => {
    mocked.list.mockResolvedValue([{ id: 1, name: 'Meins', role: 'OWNER', createdAt: '2026-01-01T00:00:00Z' }])
    render(<MemoryRouter><ProjectsPage /></MemoryRouter>)

    expect(await screen.findByText('Meins')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Anlegen' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Projekt Meins löschen')).not.toBeInTheDocument()
  })

  it('zeigt Admins Anlegen mit Owner-Feld und Löschen', async () => {
    mockUser = { platformRole: 'ADMIN' }
    mocked.list.mockResolvedValue([{ id: 1, name: 'Meins', role: 'OWNER', createdAt: '2026-01-01T00:00:00Z' }])
    render(<MemoryRouter><ProjectsPage /></MemoryRouter>)

    expect(await screen.findByText('Meins')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Anlegen' })).toBeInTheDocument()
    expect(screen.getByLabelText('Owner (E-Mail)')).toBeInTheDocument()
    expect(screen.getByLabelText('Projekt Meins löschen')).toBeInTheDocument()
  })

  it('löscht erst nach Bestätigung (Abbrechen löscht nicht)', async () => {
    mockUser = { platformRole: 'ADMIN' }
    mocked.list.mockResolvedValue([{ id: 1, name: 'Meins', role: 'OWNER', createdAt: '2026-01-01T00:00:00Z' }])
    mocked.remove.mockResolvedValue(undefined)
    render(<MemoryRouter><ProjectsPage /></MemoryRouter>)

    fireEvent.click(await screen.findByLabelText('Projekt Meins löschen'))
    expect(await screen.findByText('Projekt löschen?')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    await waitFor(() => expect(screen.queryByText('Projekt löschen?')).not.toBeInTheDocument())
    expect(mocked.remove).not.toHaveBeenCalled()

    fireEvent.click(screen.getByLabelText('Projekt Meins löschen'))
    fireEvent.click(await screen.findByRole('button', { name: 'Löschen' }))
    await waitFor(() => expect(mocked.remove).toHaveBeenCalledWith(1))
  })

  it('routet beim Erst-Aufruf mit genau einem Projekt direkt zur Boardauswahl', async () => {
    mocked.list.mockResolvedValue([{ id: 5, name: 'Solo', role: 'OWNER', createdAt: '' }])
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<ProjectsPage />} />
          <Route path="/projects/:id" element={<div>Boardauswahl</div>} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText('Boardauswahl')).toBeInTheDocument()
  })
})
