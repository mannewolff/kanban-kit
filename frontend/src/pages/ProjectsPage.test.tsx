import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { projectsApi } from '../api/projects'
import { ProjectsPage } from './ProjectsPage'

vi.mock('../api/projects', () => ({
  projectsApi: { list: vi.fn(), create: vi.fn(), remove: vi.fn(), rename: vi.fn() },
}))

let mockUser: { platformRole: string } | null = { platformRole: 'USER' }
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ user: mockUser }) }))
// Editiermodus gemockt: Bestandstests mit editMode=true (Bleistifte sichtbar); der Editiermodus-
// aus-Test schaltet editMode.value=false.
const editMode = vi.hoisted(() => ({ value: true }))
vi.mock('../lib/EditModeContext', () => ({
  useEditMode: () => ({ editMode: editMode.value, setEditMode: vi.fn(), toggleEditMode: vi.fn() }),
}))

const mocked = projectsApi as unknown as {
  list: ReturnType<typeof vi.fn>
  create: ReturnType<typeof vi.fn>
  remove: ReturnType<typeof vi.fn>
  rename: ReturnType<typeof vi.fn>
}

describe('ProjectsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUser = { platformRole: 'USER' }
    editMode.value = true
  })

  it('blendet bei ausgeschaltetem Editiermodus Umbenennen und Löschen aus', async () => {
    mockUser = { platformRole: 'ADMIN' }
    editMode.value = false
    mocked.list.mockResolvedValue([{ id: 1, name: 'Meins', role: 'OWNER', createdAt: '2026-01-01T00:00:00Z' }])
    render(<MemoryRouter><ProjectsPage /></MemoryRouter>)

    expect(await screen.findByText('Meins')).toBeInTheDocument()
    expect(screen.queryByLabelText('Projekt Meins umbenennen')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Projekt Meins löschen')).not.toBeInTheDocument()
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

  it('gibt beim manuellen Anwählen eines Projekts autoRoute weiter, damit bei einem Board durchgeroutet wird', async () => {
    mocked.list.mockResolvedValue([
      { id: 1, name: 'Eins', role: 'OWNER', createdAt: '' },
      { id: 2, name: 'Zwei', role: 'OWNER', createdAt: '' },
    ])
    function Target() {
      const { state } = useLocation()
      return <div>autoRoute={String((state as { autoRoute?: boolean } | null)?.autoRoute)}</div>
    }
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<ProjectsPage />} />
          <Route path="/projects/:id" element={<Target />} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.click(await screen.findByText('Zwei'))
    expect(await screen.findByText('autoRoute=true')).toBeInTheDocument()
  })

  it('benennt ein Projekt als OWNER um', async () => {
    mocked.list.mockResolvedValue([{ id: 1, name: 'Meins', role: 'OWNER', createdAt: '' }])
    mocked.rename.mockResolvedValue({ id: 1, name: 'Neu', role: 'OWNER', createdAt: '' })
    render(<MemoryRouter><ProjectsPage /></MemoryRouter>)

    fireEvent.click(await screen.findByLabelText('Projekt Meins umbenennen'))
    fireEvent.change(screen.getByLabelText('Neuer Projektname'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(mocked.rename).toHaveBeenCalledWith(1, 'Neu'))
  })

  it('zeigt Nicht-OWNER kein Umbenennen', async () => {
    mocked.list.mockResolvedValue([{ id: 1, name: 'Fremd', role: 'MEMBER', createdAt: '' }])
    render(<MemoryRouter><ProjectsPage /></MemoryRouter>)

    expect(await screen.findByText('Fremd')).toBeInTheDocument()
    expect(screen.queryByLabelText('Projekt Fremd umbenennen')).not.toBeInTheDocument()
  })

  it('zeigt eine Fehlermeldung, wenn das Umbenennen fehlschlägt', async () => {
    mocked.list.mockResolvedValue([{ id: 1, name: 'Meins', role: 'OWNER', createdAt: '' }])
    mocked.rename.mockRejectedValue(new Error('boom'))
    render(<MemoryRouter><ProjectsPage /></MemoryRouter>)

    fireEvent.click(await screen.findByLabelText('Projekt Meins umbenennen'))
    fireEvent.change(screen.getByLabelText('Neuer Projektname'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(await screen.findByText('Umbenennen fehlgeschlagen.')).toBeInTheDocument()
  })

  it('legt kein Projekt ohne Name oder Owner-E-Mail an', async () => {
    mockUser = { platformRole: 'ADMIN' }
    mocked.list.mockResolvedValue([])
    render(<MemoryRouter><ProjectsPage /></MemoryRouter>)
    await screen.findByRole('button', { name: 'Anlegen' })

    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(mocked.create).not.toHaveBeenCalled()
  })

  it('zeigt beim Anlegen eine spezifische Fehlermeldung bei unbekannter Owner-E-Mail (400)', async () => {
    mockUser = { platformRole: 'ADMIN' }
    mocked.list.mockResolvedValue([])
    mocked.create.mockRejectedValue(new ApiError(400, 'unbekannt'))
    render(<MemoryRouter><ProjectsPage /></MemoryRouter>)
    await screen.findByRole('button', { name: 'Anlegen' })

    fireEvent.change(screen.getByLabelText('Neues Projekt'), { target: { value: 'Neu' } })
    fireEvent.change(screen.getByLabelText('Owner (E-Mail)'), { target: { value: 'x@x.de' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(
      await screen.findByText('Kein Nutzer mit dieser Owner-E-Mail gefunden.'),
    ).toBeInTheDocument()
  })

  it('zeigt beim Anlegen eine spezifische Fehlermeldung ohne Admin-Recht (403)', async () => {
    mockUser = { platformRole: 'ADMIN' }
    mocked.list.mockResolvedValue([])
    mocked.create.mockRejectedValue(new ApiError(403, 'verboten'))
    render(<MemoryRouter><ProjectsPage /></MemoryRouter>)
    await screen.findByRole('button', { name: 'Anlegen' })

    fireEvent.change(screen.getByLabelText('Neues Projekt'), { target: { value: 'Neu' } })
    fireEvent.change(screen.getByLabelText('Owner (E-Mail)'), { target: { value: 'x@x.de' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(
      await screen.findByText('Nur Administratoren dürfen Projekte anlegen.'),
    ).toBeInTheDocument()
  })

  it('zeigt beim Anlegen eine generische Fehlermeldung bei anderem Fehler', async () => {
    mockUser = { platformRole: 'ADMIN' }
    mocked.list.mockResolvedValue([])
    mocked.create.mockRejectedValue(new Error('boom'))
    render(<MemoryRouter><ProjectsPage /></MemoryRouter>)
    await screen.findByRole('button', { name: 'Anlegen' })

    fireEvent.change(screen.getByLabelText('Neues Projekt'), { target: { value: 'Neu' } })
    fireEvent.change(screen.getByLabelText('Owner (E-Mail)'), { target: { value: 'x@x.de' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(await screen.findByText('Anlegen fehlgeschlagen.')).toBeInTheDocument()
  })

  it('legt ein Projekt erfolgreich an', async () => {
    mockUser = { platformRole: 'ADMIN' }
    mocked.list.mockResolvedValue([])
    mocked.create.mockResolvedValue({ id: 5, name: 'Neu', role: 'OWNER', createdAt: '' })
    render(<MemoryRouter><ProjectsPage /></MemoryRouter>)
    await screen.findByRole('button', { name: 'Anlegen' })

    fireEvent.change(screen.getByLabelText('Neues Projekt'), { target: { value: 'Neu' } })
    fireEvent.change(screen.getByLabelText('Owner (E-Mail)'), { target: { value: 'x@x.de' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(mocked.create).toHaveBeenCalledWith('Neu', 'x@x.de'))
  })
})
