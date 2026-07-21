import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { accessTokensApi } from '../api/accessTokens'
import { boardsApi } from '../api/boards'
import { projectsApi } from '../api/projects'
import { EditModeProvider, useEditMode } from '../lib/EditModeContext'
import { AdministrationPage } from './AdministrationPage'

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { platformRole: 'USER', memberships: [] } }),
}))
vi.mock('../api/accessTokens', () => ({
  accessTokensApi: { list: vi.fn(), create: vi.fn(), revoke: vi.fn() },
}))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../api/boards', () => ({ boardsApi: { list: vi.fn() } }))

const mTokens = accessTokensApi as unknown as {
  list: ReturnType<typeof vi.fn>
  create: ReturnType<typeof vi.fn>
  revoke: ReturnType<typeof vi.fn>
}
const mProjects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mBoards = boardsApi as unknown as { list: ReturnType<typeof vi.fn> }

const memberProject = { id: 3, name: 'Dogfood', role: 'MEMBER', createdAt: '' }
const viewerProject = { id: 4, name: 'NurLesen', role: 'VIEWER', createdAt: '' }
const boardA = { id: 7, name: 'Board A' }
const boundToken = {
  id: 1, name: 'board-cli', projectId: 3, boardId: 7,
  createdAt: '2026-01-01', lastUsedAt: null, revoked: false,
}

function renderPage() {
  return render(
    <EditModeProvider>
      <AdministrationPage />
      <ModeProbe />
    </EditModeProvider>,
  )
}

// Spiegelt den Editiermodus, damit der Test den Context-Effekt des Schalters beobachten kann.
function ModeProbe() {
  const { editMode } = useEditMode()
  return <span data-testid="mode">{String(editMode)}</span>
}

describe('AdministrationPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mTokens.list.mockResolvedValue([])
    mTokens.create.mockResolvedValue({ id: 1, name: 'board-cli', plaintext: 'tk_geheim' })
    mTokens.revoke.mockResolvedValue(undefined)
    mProjects.list.mockResolvedValue([])
    mBoards.list.mockResolvedValue([])
    Object.assign(navigator, { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  it('rendert den Editiermodus-Schalter, Default aus', () => {
    renderPage()
    const toggle = screen.getByRole('checkbox', { name: 'Editiermodus aktivieren' })
    expect(toggle).not.toBeChecked()
    expect(screen.getByTestId('mode')).toHaveTextContent('false')
  })

  it('schaltet den Editiermodus über den Context ein und wieder aus', async () => {
    renderPage()
    const toggle = screen.getByRole('checkbox', { name: 'Editiermodus aktivieren' })

    await userEvent.click(toggle)
    expect(toggle).toBeChecked()
    expect(screen.getByTestId('mode')).toHaveTextContent('true')

    await userEvent.click(toggle)
    expect(toggle).not.toBeChecked()
    expect(screen.getByTestId('mode')).toHaveTextContent('false')
  })

  it('listet vorhandene Tokens mit Board-Bindung', async () => {
    mTokens.list.mockResolvedValue([boundToken])
    mProjects.list.mockResolvedValue([memberProject])
    renderPage()

    expect(await screen.findByText('board-cli')).toBeInTheDocument()
    expect(screen.getByText('Projekt „Dogfood“ · Board 7')).toBeInTheDocument()
  })

  it('bietet nur Projekte mit Karten-Anlegerecht in der Auswahl (VIEWER ausgeschlossen)', async () => {
    mProjects.list.mockResolvedValue([memberProject, viewerProject])
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Token erzeugen' }))
    expect(await screen.findByRole('option', { name: 'Dogfood' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'NurLesen' })).not.toBeInTheDocument()
  })

  it('erzeugt ein board-gebundenes Token und zeigt den Klartext einmalig', async () => {
    mProjects.list.mockResolvedValue([memberProject])
    mBoards.list.mockResolvedValue([boardA])
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Token erzeugen' }))
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'board-cli' } })
    fireEvent.change(screen.getByLabelText('Projekt'), { target: { value: '3' } })
    await screen.findByRole('option', { name: 'Board A' })
    fireEvent.change(screen.getByLabelText('Board'), { target: { value: '7' } })
    fireEvent.click(screen.getByRole('button', { name: 'Erzeugen' }))

    await waitFor(() => expect(mTokens.create).toHaveBeenCalledWith('board-cli', 3, 7))
    // Der Dialog schließt nach Erfolg — warten, bis er weg ist (sonst ist der Hintergrund noch
    // aria-hidden und der Kopieren-Button per Rolle unsichtbar).
    await waitFor(() => expect(screen.queryByText('Neues API-Token')).not.toBeInTheDocument())
    // Klartext einmalig sichtbar + kopierbar.
    expect(await screen.findByLabelText('Token-Klartext')).toHaveTextContent('tk_geheim')
    fireEvent.click(screen.getByRole('button', { name: 'Token kopieren' }))
    await waitFor(() =>
      expect((navigator.clipboard.writeText as ReturnType<typeof vi.fn>)).toHaveBeenCalledWith('tk_geheim'),
    )
  })

  it('leert Projekt/Board über die (wählen)-Option und deaktiviert Erzeugen', async () => {
    mProjects.list.mockResolvedValue([memberProject])
    mBoards.list.mockResolvedValue([boardA])
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Token erzeugen' }))
    fireEvent.change(screen.getByLabelText('Projekt'), { target: { value: '3' } })
    await screen.findByRole('option', { name: 'Board A' })
    fireEvent.change(screen.getByLabelText('Board'), { target: { value: '7' } })
    fireEvent.change(screen.getByLabelText('Board'), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText('Projekt'), { target: { value: '' } })

    expect(screen.getByRole('button', { name: 'Erzeugen' })).toBeDisabled()
  })

  it('schließt den Erzeugen-Dialog über Abbrechen', async () => {
    mProjects.list.mockResolvedValue([memberProject])
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Token erzeugen' }))
    expect(screen.getByText('Neues API-Token')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    await waitFor(() => expect(screen.queryByText('Neues API-Token')).not.toBeInTheDocument())
  })

  it('zeigt die Bindung mit #id, wenn das Projekt nicht (mehr) sichtbar ist', async () => {
    mTokens.list.mockResolvedValue([{ ...boundToken, projectId: 99, boardId: 7 }])
    mProjects.list.mockResolvedValue([memberProject]) // Projekt 99 nicht dabei
    renderPage()

    expect(await screen.findByText('Projekt „#99“ · Board 7')).toBeInTheDocument()
  })

  it('widerruft ein Token über die Zeilen-Aktion (nach Bestätigung)', async () => {
    mTokens.list.mockResolvedValue([boundToken])
    renderPage()

    fireEvent.click(await screen.findByLabelText('Token board-cli widerrufen'))
    await waitFor(() => expect(mTokens.revoke).toHaveBeenCalledWith(1))
  })

  it('deaktiviert „Token erzeugen", wenn kein berechtigtes Projekt existiert', async () => {
    mProjects.list.mockResolvedValue([viewerProject])
    renderPage()

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Token erzeugen' })).toBeDisabled(),
    )
  })

  it('zeigt eine Fehlermeldung, wenn das Anlegen scheitert (z. B. 403)', async () => {
    mProjects.list.mockResolvedValue([memberProject])
    mBoards.list.mockResolvedValue([boardA])
    mTokens.create.mockRejectedValue(new Error('403'))
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Token erzeugen' }))
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'x' } })
    fireEvent.change(screen.getByLabelText('Projekt'), { target: { value: '3' } })
    await screen.findByRole('option', { name: 'Board A' })
    fireEvent.change(screen.getByLabelText('Board'), { target: { value: '7' } })
    fireEvent.click(screen.getByRole('button', { name: 'Erzeugen' }))

    expect(await screen.findByText(/Token konnte nicht erzeugt werden/)).toBeInTheDocument()
  })

  it('stellt widerrufene Tokens abgesetzt dar (kein Widerrufen-Button)', async () => {
    mTokens.list.mockResolvedValue([{ ...boundToken, revoked: true }])
    renderPage()

    expect(await screen.findByText('Widerrufen')).toBeInTheDocument()
    expect(screen.queryByLabelText('Token board-cli widerrufen')).not.toBeInTheDocument()
  })

  it('zeigt ungebundene Tokens als „ungebunden"', async () => {
    mTokens.list.mockResolvedValue([
      { ...boundToken, id: 2, name: 'frei', projectId: null, boardId: null },
    ])
    renderPage()

    expect(await screen.findByText('frei')).toBeInTheDocument()
    expect(screen.getByText('ungebunden')).toBeInTheDocument()
  })

  it('widerruft nicht, wenn die Bestätigung abgelehnt wird', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    mTokens.list.mockResolvedValue([boundToken])
    renderPage()

    fireEvent.click(await screen.findByLabelText('Token board-cli widerrufen'))
    expect(mTokens.revoke).not.toHaveBeenCalled()
  })
})
