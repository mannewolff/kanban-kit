import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from '../../api/boards'
import { projectsApi } from '../../api/projects'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { leseMobilBoard, schreibeMobilBoard } from '../../lib/mobilBoard'
import { MobilEinstellungenPage } from './MobilEinstellungenPage'

const authState = vi.hoisted(() => ({
  user: { userId: 7, platformRole: 'USER' } as { userId: number; platformRole: string } | null,
}))
vi.mock('../../auth/AuthContext', () => ({ useAuth: () => authState }))
vi.mock('../../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../../api/boards', () => ({ boardsApi: { list: vi.fn() } }))
vi.mock('../../lib/mobilBoard', () => ({ leseMobilBoard: vi.fn(), schreibeMobilBoard: vi.fn() }))

const projects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }
const boards = boardsApi as unknown as { list: ReturnType<typeof vi.fn> }
const gemerkt = vi.mocked(leseMobilBoard)
const merke = vi.mocked(schreibeMobilBoard)

const board = (id: number, projectId: number, name: string) => ({ id, projectId, name, createdAt: '', columns: [] })

function seite(sprache: 'de' | 'en' = 'de') {
  return renderMitSprache(
    <MemoryRouter>
      <MobilEinstellungenPage />
    </MemoryRouter>,
    sprache,
  )
}

describe('MobilEinstellungenPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authState.user = { userId: 7, platformRole: 'USER' }
    gemerkt.mockReturnValue(null)
    projects.list.mockResolvedValue([
      { id: 1, name: 'Alpha', role: 'MEMBER', createdAt: '' },
      { id: 2, name: 'Beta', role: 'VIEWER', createdAt: '' },
    ])
    boards.list.mockImplementation((projectId: number) =>
      Promise.resolve(projectId === 1 ? [board(10, 1, 'Produkt'), board(11, 1, 'Technik')] : [board(20, 2, 'Lesen')]),
    )
  })

  it('bietet nur Boards der Projekte an, in denen Karten angelegt werden dürfen', async () => {
    seite()
    const gruppe = await screen.findByRole('radiogroup', { name: 'Standard-Board' })
    expect(await within(gruppe).findByRole('radio', { name: 'Alpha · Produkt' })).toBeInTheDocument()
    expect(within(gruppe).getByRole('radio', { name: 'Alpha · Technik' })).toBeInTheDocument()
    expect(within(gruppe).queryByRole('radio', { name: 'Beta · Lesen' })).not.toBeInTheDocument()
    expect(boards.list).toHaveBeenCalledWith(1)
    expect(boards.list).not.toHaveBeenCalledWith(2)
  })

  it('bietet einem Plattform-Admin auch Boards an, in denen er nur liest', async () => {
    authState.user = { userId: 7, platformRole: 'ADMIN' }
    seite()
    expect(await screen.findByRole('radio', { name: 'Beta · Lesen' })).toBeInTheDocument()
  })

  it('merkt sich die Wahl auf diesem Gerät für das Konto', async () => {
    seite()
    await userEvent.click(await screen.findByRole('radio', { name: 'Alpha · Technik' }))
    expect(merke).toHaveBeenCalledWith(7, 11)
    expect(screen.getByRole('radio', { name: 'Alpha · Technik' })).toBeChecked()
    expect(screen.getByText('Gespeichert.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Zur Idee' })).toHaveAttribute('href', '/mobil')
  })

  it('zeigt die gemerkte Wahl', async () => {
    gemerkt.mockReturnValue(10)
    seite()
    expect(await screen.findByRole('radio', { name: 'Alpha · Produkt' })).toBeChecked()
    expect(screen.queryByText(/gibt es nicht mehr/)).not.toBeInTheDocument()
  })

  it('verlangt eine neue Wahl, wenn es das gemerkte Board nicht mehr gibt', async () => {
    gemerkt.mockReturnValue(99)
    seite()
    expect(await screen.findByText('Das gewählte Board gibt es nicht mehr. Bitte wähle ein neues.')).toBeInTheDocument()
    expect(screen.getAllByRole('radio').every((r) => !(r as HTMLInputElement).checked)).toBe(true)
    await userEvent.click(screen.getByRole('radio', { name: 'Alpha · Produkt' }))
    expect(merke).toHaveBeenCalledWith(7, 10)
    expect(screen.queryByText(/gibt es nicht mehr/)).not.toBeInTheDocument()
  })

  it('sagt es, wenn es kein Projekt mit Anlegerecht gibt', async () => {
    projects.list.mockResolvedValue([{ id: 2, name: 'Beta', role: 'VIEWER', createdAt: '' }])
    seite()
    expect(await screen.findByText('Du bist in keinem Projekt, in dem du Karten anlegen darfst.')).toBeInTheDocument()
  })

  it('meldet, wenn die Boards nicht geladen werden können', async () => {
    projects.list.mockRejectedValue(new TypeError('Failed to fetch'))
    seite()
    expect(await screen.findByText('Die Boards konnten nicht geladen werden.')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('progressbar')).not.toBeInTheDocument())
  })

  it('merkt ohne Anmeldung nichts', async () => {
    authState.user = null
    seite()
    await userEvent.click(await screen.findByRole('radio', { name: 'Alpha · Produkt' }))
    expect(gemerkt).not.toHaveBeenCalled()
    expect(merke).not.toHaveBeenCalled()
    expect(screen.queryByText('Gespeichert.')).not.toBeInTheDocument()
  })

  it('übernimmt nach dem Verlassen der Seite weder Boards noch Fehler', async () => {
    let liefere: (p: unknown[]) => void = () => {}
    let verwerfe: (e: unknown) => void = () => {}
    projects.list
      .mockReturnValueOnce(new Promise<unknown[]>((r) => (liefere = r)))
      .mockReturnValueOnce(new Promise<unknown[]>((_, f) => (verwerfe = f)))
    seite().unmount()
    liefere([])
    seite().unmount()
    verwerfe(new TypeError('Failed to fetch'))
    await Promise.resolve()
    expect(projects.list).toHaveBeenCalledTimes(2)
  })

  it('spricht Englisch, wenn die Installation Englisch spricht', async () => {
    seite('en')
    expect(await screen.findByRole('radio', { name: 'Alpha · Produkt' })).toBeInTheDocument()
    expect(screen.getByRole('radiogroup', { name: 'Default board' })).toBeInTheDocument()
  })
})
