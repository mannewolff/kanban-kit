import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from 'vitest'
import { accessTokensApi } from '../api/accessTokens'
import { authApi } from '../api/auth'
import { boardsApi } from '../api/boards'
import { ApiError } from '../api/client'
import { configApi } from '../api/config'
import { projectsApi } from '../api/projects'
import { SnackbarProvider } from '../components/SnackbarProvider'
import { SpracheProvider } from '../i18n/SpracheContext'
import { EditModeProvider, useEditMode } from '../lib/EditModeContext'
import { AdministrationPage } from './AdministrationPage'

// Rolle je Test umschaltbar: Default USER (Nicht-Admin, blendet Admin-Abschnitte aus).
const authState = vi.hoisted(() => ({
  user: { platformRole: 'USER', memberships: [] } as {
    platformRole: string
    memberships: []
    colorScheme?: 'SYSTEM' | 'LIGHT' | 'DARK'
  },
  vorschau: null as null | Record<string, string>,
  refresh: vi.fn(),
}))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => authState }))
vi.mock('../api/auth', () => ({ authApi: { setColorScheme: vi.fn() } }))
const farbe = vi.hoisted(() => ({ setMode: vi.fn() }))
vi.mock('@mui/material/styles', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@mui/material/styles')>()),
  useColorScheme: () => ({ setMode: farbe.setMode }),
}))
vi.mock('../api/accessTokens', () => ({
  accessTokensApi: { list: vi.fn(), create: vi.fn(), revoke: vi.fn() },
}))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../api/boards', () => ({ boardsApi: { list: vi.fn() } }))
vi.mock('../api/config', () => ({
  configApi: { getDoneRetention: vi.fn(), setDoneRetention: vi.fn() },
}))

const mConfig = configApi as unknown as {
  getDoneRetention: ReturnType<typeof vi.fn>
  setDoneRetention: ReturnType<typeof vi.fn>
}

const mTokens = accessTokensApi as unknown as {
  list: ReturnType<typeof vi.fn>
  create: ReturnType<typeof vi.fn>
  revoke: ReturnType<typeof vi.fn>
}
const mAuth = authApi as unknown as { setColorScheme: ReturnType<typeof vi.fn> }
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
    <SpracheProvider anfangs="de">
      <MemoryRouter>
        <SnackbarProvider>
          <EditModeProvider>
            <AdministrationPage />
            <ModeProbe />
          </EditModeProvider>
        </SnackbarProvider>
      </MemoryRouter>
    </SpracheProvider>,
  )
}

/** Prüft den Fehler-Toast: Der Text steht dort, und der Alert trägt die Severity `error`. */
async function erwarteFehlerToast(text: string) {
  expect(await screen.findByText(text)).toBeInTheDocument()
  expect(await screen.findByRole('alert', { hidden: true })).toHaveClass('MuiAlert-filledError')
}

// Spiegelt den Editiermodus, damit der Test den Context-Effekt des Schalters beobachten kann.
function ModeProbe() {
  const { editMode } = useEditMode()
  return <span data-testid="mode">{String(editMode)}</span>
}

describe('AdministrationPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authState.user = { platformRole: 'USER', memberships: [] }
    authState.vorschau = null
    authState.refresh.mockResolvedValue(undefined)
    mAuth.setColorScheme.mockResolvedValue({})
    mTokens.list.mockResolvedValue([])
    mTokens.create.mockResolvedValue({ id: 1, name: 'board-cli', plaintext: 'tk_geheim' })
    mTokens.revoke.mockResolvedValue(undefined)
    mProjects.list.mockResolvedValue([])
    mBoards.list.mockResolvedValue([])
    mConfig.getDoneRetention.mockResolvedValue({ effective: 30, override: null })
    mConfig.setDoneRetention.mockResolvedValue({ effective: 30, override: 30 })
    Object.assign(navigator, { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  it('zeigt den Rechtliches-Abschnitt mit Copyright und MIT-Hinweis vor dem Editiermodus', () => {
    renderPage()

    expect(screen.getByRole('heading', { name: 'Rechtliches', level: 2 })).toBeInTheDocument()
    expect(screen.getByText('© 2026 Manfred Wolff')).toBeInTheDocument()
    expect(screen.getByText(/MIT-Lizenz/)).toBeInTheDocument()
    expect(screen.getByText(/ohne Gewähr/)).toBeInTheDocument()

    const headings = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)
    expect(headings.indexOf('Rechtliches')).toBeLessThan(headings.indexOf('Editiermodus'))
  })

  it('zeigt die Sektion API-Schnittstelle auch einem Nicht-Admin, mit Übersicht und Downloads', () => {
    renderPage()

    expect(screen.getByRole('heading', { name: 'API-Schnittstelle', level: 2 })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'API-Übersicht öffnen' })).toHaveAttribute(
      'href',
      '/administration/api',
    )
    const json = screen.getByRole('link', { name: 'OpenAPI als JSON herunterladen' })
    expect(json).toHaveAttribute('href', '/api/openapi')
    expect(json).toHaveAttribute('download')
    const yaml = screen.getByRole('link', { name: 'OpenAPI als YAML herunterladen' })
    expect(yaml).toHaveAttribute('href', '/api/openapi.yaml')
    expect(yaml).toHaveAttribute('download')
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

  it('zeigt die Server-Meldung, wenn das Widerrufen abgelehnt wird', async () => {
    mTokens.list.mockResolvedValue([boundToken])
    // Server-Ablehnung mit einer für den Nutzer formulierten Meldung in `detail` (RFC 9457).
    mTokens.revoke.mockRejectedValue(
      new ApiError(409, 'Conflict', undefined, 'Das Token wurde bereits widerrufen.'),
    )
    renderPage()

    fireEvent.click(await screen.findByLabelText('Token board-cli widerrufen'))

    await erwarteFehlerToast('Das Token wurde bereits widerrufen.')
  })

  it('fällt beim Widerrufen ohne Server-Meldung auf den eigenen Text zurück', async () => {
    mTokens.list.mockResolvedValue([boundToken])
    mTokens.revoke.mockRejectedValue(new TypeError('Failed to fetch'))
    renderPage()

    fireEvent.click(await screen.findByLabelText('Token board-cli widerrufen'))

    await erwarteFehlerToast('Widerrufen fehlgeschlagen.')
  })

  it('deaktiviert „Token erzeugen", wenn kein berechtigtes Projekt existiert', async () => {
    mProjects.list.mockResolvedValue([viewerProject])
    renderPage()

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Token erzeugen' })).toBeDisabled(),
    )
  })

  it('zeigt die Server-Meldung, wenn das Anlegen eines Tokens abgelehnt wird', async () => {
    mProjects.list.mockResolvedValue([memberProject])
    mBoards.list.mockResolvedValue([boardA])
    mTokens.create.mockRejectedValue(
      new ApiError(403, 'Forbidden', undefined, 'Auf Board A darfst du keine Karten anlegen.'),
    )
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Token erzeugen' }))
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'x' } })
    fireEvent.change(screen.getByLabelText('Projekt'), { target: { value: '3' } })
    await screen.findByRole('option', { name: 'Board A' })
    fireEvent.change(screen.getByLabelText('Board'), { target: { value: '7' } })
    fireEvent.click(screen.getByRole('button', { name: 'Erzeugen' }))

    // Der Darstellungsort bleibt der Inline-Alert im Dialog; nur der Text kommt vom Server.
    expect(await screen.findByText('Auf Board A darfst du keine Karten anlegen.')).toBeInTheDocument()
  })

  it('zeigt ohne Server-Meldung den eigenen Text zum fehlgeschlagenen Anlegen', async () => {
    mProjects.list.mockResolvedValue([memberProject])
    mBoards.list.mockResolvedValue([boardA])
    mTokens.create.mockRejectedValue(new TypeError('Failed to fetch'))
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

  describe('Standard-Labels (Issue #1486)', () => {
    it('zeigt den Knopf nur einem Plattform-Admin', async () => {
      renderPage() // Default USER
      expect(
        screen.queryByRole('button', { name: 'Standard-Labels für alle Boards' }),
      ).not.toBeInTheDocument()

      authState.user = { platformRole: 'ADMIN', memberships: [] }
      mConfig.getDoneRetention.mockResolvedValue({ effective: 30, override: null })
      renderPage()
      expect(
        await screen.findByRole('button', { name: 'Standard-Labels für alle Boards' }),
      ).toBeInTheDocument()
    })
  })

  describe('Done-Aufbewahrung', () => {
    const asAdmin = () => {
      authState.user = { platformRole: 'ADMIN', memberships: [] }
    }

    it('blendet den Abschnitt für Nicht-Admins aus', () => {
      renderPage() // Default USER
      expect(screen.queryByText('Done-Aufbewahrung')).not.toBeInTheDocument()
      expect(mConfig.getDoneRetention).not.toHaveBeenCalled()
    })

    it('lädt und zeigt den aktuellen effektiven Wert (Admin)', async () => {
      asAdmin()
      mConfig.getDoneRetention.mockResolvedValue({ effective: 30, override: null })
      renderPage()

      expect(await screen.findByText('Done-Aufbewahrung')).toBeInTheDocument()
      const line = await screen.findByText(/Aktuell wirksam:/)
      expect(line).toHaveTextContent('30 Tage')
      expect(screen.getByLabelText('Done-Aufbewahrung in Tagen')).toHaveValue(30)
    })

    it('speichert 0 (kein Auto-Archiv) und meldet Erfolg', async () => {
      asAdmin()
      mConfig.getDoneRetention.mockResolvedValue({ effective: 30, override: null })
      mConfig.setDoneRetention.mockResolvedValue({ effective: 0, override: 0 })
      renderPage()

      const field = await screen.findByLabelText('Done-Aufbewahrung in Tagen')
      fireEvent.change(field, { target: { value: '0' } })
      fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

      await waitFor(() => expect(mConfig.setDoneRetention).toHaveBeenCalledWith(0))
      expect(await screen.findByText('Gespeichert.')).toBeInTheDocument()
      expect(screen.getByText(/Aktuell wirksam:/)).toHaveTextContent('kein Auto-Archiv')
    })

    it('lehnt negative Werte ab, ohne zu speichern', async () => {
      asAdmin()
      renderPage()

      const field = await screen.findByLabelText('Done-Aufbewahrung in Tagen')
      fireEvent.change(field, { target: { value: '-1' } })
      fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

      expect(await screen.findByText(/ganze Zahl/)).toBeInTheDocument()
      expect(mConfig.setDoneRetention).not.toHaveBeenCalled()
    })

    it('lehnt nicht-ganzzahlige Werte ab, ohne zu speichern', async () => {
      asAdmin()
      renderPage()

      const field = await screen.findByLabelText('Done-Aufbewahrung in Tagen')
      fireEvent.change(field, { target: { value: '1.5' } })
      fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

      expect(await screen.findByText(/ganze Zahl/)).toBeInTheDocument()
      expect(mConfig.setDoneRetention).not.toHaveBeenCalled()
    })

    it('zeigt die Server-Fehlermeldung, wenn das Speichern scheitert (ApiError)', async () => {
      asAdmin()
      mConfig.setDoneRetention.mockRejectedValue(new ApiError(409, 'Konflikt beim Speichern'))
      renderPage()

      const field = await screen.findByLabelText('Done-Aufbewahrung in Tagen')
      fireEvent.change(field, { target: { value: '7' } })
      fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

      expect(await screen.findByText('Konflikt beim Speichern')).toBeInTheDocument()
    })

    it('zeigt eine generische Fehlermeldung bei nicht-ApiError-Fehlern', async () => {
      asAdmin()
      mConfig.setDoneRetention.mockRejectedValue(new Error('boom'))
      renderPage()

      const field = await screen.findByLabelText('Done-Aufbewahrung in Tagen')
      fireEvent.change(field, { target: { value: '7' } })
      fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

      expect(await screen.findByText('Speichern fehlgeschlagen.')).toBeInTheDocument()
    })
  })

  describe('Sprache / Language (Issue #1646)', () => {
    let fetchSpy: MockInstance<typeof fetch> | undefined

    /** `PUT /api/admin/language` antwortet mit dem gesetzten Sprachcode (oder einem Fehler). */
    function putAntwortet(antwort: { ok: true; language: string } | { ok: false; status: number; body: string }) {
      const spy = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
        ok: antwort.ok,
        status: antwort.ok ? 200 : antwort.status,
        statusText: antwort.ok ? 'OK' : 'Error',
        text: () => Promise.resolve(antwort.ok ? JSON.stringify({ language: antwort.language }) : antwort.body),
      } as Response)
      fetchSpy = spy
      return spy
    }

    afterEach(() => fetchSpy?.mockRestore())

    it('zeigt einem Nicht-Admin den Abschnitt zweisprachig, aber nur lesbar', () => {
      renderPage() // Default USER

      expect(screen.getByRole('heading', { name: 'Sprache / Language', level: 2 })).toBeInTheDocument()
      const gruppe = screen.getByRole('radiogroup', { name: 'Sprache / Language' })
      expect(gruppe).toBeInTheDocument()
      expect(screen.getByRole('radio', { name: 'Deutsch' })).toBeChecked()
      expect(screen.getByRole('radio', { name: 'Deutsch' })).toBeDisabled()
      expect(screen.getByRole('radio', { name: 'English' })).toBeDisabled()
      expect(screen.queryByRole('button', { name: 'Sprache speichern' })).not.toBeInTheDocument()
      expect(screen.getByText('Nur ein Plattform-Admin kann die Sprache ändern.')).toBeInTheDocument()
    })

    it('lässt den Plattform-Admin die Sprache wählen', () => {
      authState.user = { platformRole: 'ADMIN', memberships: [] }
      renderPage()

      expect(screen.getByRole('heading', { name: 'Sprache / Language', level: 2 })).toBeInTheDocument()
      expect(screen.getByRole('radio', { name: 'Deutsch' })).toBeEnabled()
      expect(screen.getByRole('radio', { name: 'English' })).toBeEnabled()
      expect(screen.getByRole('button', { name: 'Sprache speichern' })).toBeInTheDocument()
      expect(screen.queryByText('Nur ein Plattform-Admin kann die Sprache ändern.')).not.toBeInTheDocument()
      expect(screen.getByText(/beim nächsten Laden der Seite/)).toBeInTheDocument()
    })

    it('speichert über PUT /api/admin/language und schaltet sofort auf Englisch', async () => {
      authState.user = { platformRole: 'ADMIN', memberships: [] }
      const put = putAntwortet({ ok: true, language: 'en' })
      renderPage()

      await userEvent.click(screen.getByRole('radio', { name: 'English' }))
      await userEvent.click(screen.getByRole('button', { name: 'Sprache speichern' }))

      await waitFor(() => expect(put).toHaveBeenCalledTimes(1))
      const [pfad, init] = put.mock.calls[0] as [string, RequestInit]
      expect(pfad).toBe('/api/admin/language')
      expect(init.method).toBe('PUT')
      expect(JSON.parse(String(init.body))).toEqual({ language: 'en' })

      expect(await screen.findByText('Language saved.')).toBeInTheDocument()
      expect(screen.getByText(/the next time they load the page/)).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Save language' })).toBeInTheDocument()
      expect(screen.getByRole('heading', { name: 'Sprache / Language', level: 2 })).toBeInTheDocument()
      expect(screen.getByRole('radio', { name: 'English' })).toBeChecked()
    })

    it('zeigt die Server-Meldung, wenn das Speichern abgelehnt wird', async () => {
      authState.user = { platformRole: 'ADMIN', memberships: [] }
      putAntwortet({ ok: false, status: 400, body: JSON.stringify({ detail: 'Unbekannte Sprache' }) })
      renderPage()

      await userEvent.click(screen.getByRole('radio', { name: 'English' }))
      await userEvent.click(screen.getByRole('button', { name: 'Sprache speichern' }))

      await erwarteFehlerToast('Unbekannte Sprache')
      expect(screen.getByRole('button', { name: 'Sprache speichern' })).toBeInTheDocument()
    })

    it('fällt ohne Server-Meldung auf den eigenen Fehlertext zurück', async () => {
      authState.user = { platformRole: 'ADMIN', memberships: [] }
      putAntwortet({ ok: false, status: 502, body: '<html>Bad Gateway</html>' })
      renderPage()

      await userEvent.click(screen.getByRole('button', { name: 'Sprache speichern' }))

      await erwarteFehlerToast('Die Sprache konnte nicht gespeichert werden.')
    })
  })
})

describe('AdministrationPage auf Englisch', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authState.user = { platformRole: 'USER', memberships: [] }
    mTokens.list.mockResolvedValue([])
    mTokens.create.mockResolvedValue({ id: 1, name: 'board-cli', plaintext: 'tk_geheim' })
    mTokens.revoke.mockResolvedValue(undefined)
    mProjects.list.mockResolvedValue([])
    mBoards.list.mockResolvedValue([])
    mConfig.getDoneRetention.mockResolvedValue({ effective: 30, override: null })
    mConfig.setDoneRetention.mockResolvedValue({ effective: 30, override: 30 })
    Object.assign(navigator, { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  function renderEnglisch() {
    return render(
      <SpracheProvider anfangs="en">
        <MemoryRouter>
          <SnackbarProvider>
            <EditModeProvider>
              <AdministrationPage />
            </EditModeProvider>
          </SnackbarProvider>
        </MemoryRouter>
      </SpracheProvider>,
    )
  }

  it('zeigt die Abschnitte für jeden Angemeldeten auf Englisch', () => {
    renderEnglisch()

    expect(screen.getByRole('heading', { name: 'Administration', level: 4 })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Legal', level: 2 })).toBeInTheDocument()
    expect(screen.getByText('© 2026 Manfred Wolff')).toBeInTheDocument()
    expect(screen.getByText(/AI Control Room is licensed under the MIT License/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Edit mode', level: 2 })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Enable edit mode' })).not.toBeChecked()
    expect(screen.getByText('Enable editing')).toBeInTheDocument()
    expect(screen.getByText(/the edit icons appear/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'API tokens', level: 2 })).toBeInTheDocument()
    expect(screen.getByText(/drives a board through the API/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'API', level: 2 })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open API overview' })).toHaveTextContent('Open overview')
    expect(screen.getByRole('link', { name: 'Download OpenAPI as JSON' })).toHaveAttribute('href', '/api/openapi')
    expect(screen.getByRole('link', { name: 'Download OpenAPI as YAML' })).toHaveAttribute('href', '/api/openapi.yaml')
    // Admin-Abschnitte fehlen für Nicht-Admins auch auf Englisch.
    expect(screen.queryByRole('heading', { name: 'Done retention' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Standard labels' })).not.toBeInTheDocument()
  })

  it('zeigt Tokens, Leerzustand und Zustände auf Englisch', async () => {
    mProjects.list.mockResolvedValue([memberProject])
    mTokens.list.mockResolvedValue([
      boundToken,
      { ...boundToken, id: 2, name: 'frei', projectId: null, boardId: null },
      { ...boundToken, id: 3, name: 'alt', revoked: true },
    ])
    renderEnglisch()

    expect(await screen.findAllByText('Project “Dogfood” · Board 7')).toHaveLength(2)
    expect(screen.getByText('unbound')).toBeInTheDocument()
    expect(screen.getByText('Revoked')).toBeInTheDocument()
    expect(screen.getAllByText('Active')).toHaveLength(2)
    expect(screen.getByLabelText('Revoke token board-cli')).toBeInTheDocument()
  })

  it('nennt ohne Tokens und ohne berechtigtes Projekt den Grund auf Englisch', async () => {
    renderEnglisch()

    expect(await screen.findByText('No tokens yet.')).toBeInTheDocument()
    expect(screen.getByText(/You are not in any project where you may create cards/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create token' })).toBeDisabled()
  })

  it('erzeugt ein Token über den englischen Dialog und zeigt den Klartext', async () => {
    mProjects.list.mockResolvedValue([memberProject])
    mBoards.list.mockResolvedValue([boardA])
    renderEnglisch()

    fireEvent.click(await screen.findByRole('button', { name: 'Create token' }))
    expect(await screen.findByRole('dialog', { name: 'New API token' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: '(Choose project)' })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'board-cli' } })
    fireEvent.change(screen.getByLabelText('Project'), { target: { value: '3' } })
    await screen.findByRole('option', { name: 'Board A' })
    expect(screen.getByRole('option', { name: '(Choose board)' })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Board'), { target: { value: '7' } })
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Create' }))

    await waitFor(() => expect(screen.queryByText('New API token')).not.toBeInTheDocument())
    expect(await screen.findByText('Token “board-cli” created — shown only once:')).toBeInTheDocument()
    expect(screen.getByLabelText('Token plaintext')).toHaveTextContent('tk_geheim')
    const kopieren = screen.getByRole('button', { name: 'Copy token' })
    expect(kopieren).toHaveTextContent('Copy')
    fireEvent.click(kopieren)
    await waitFor(() => expect(kopieren).toHaveTextContent('Copied'))
  })

  it('meldet Fehler beim Anlegen und Widerrufen auf Englisch und fragt auf Englisch nach', async () => {
    mProjects.list.mockResolvedValue([memberProject])
    mBoards.list.mockResolvedValue([boardA])
    mTokens.list.mockResolvedValue([boundToken])
    mTokens.create.mockRejectedValue(new TypeError('Failed to fetch'))
    mTokens.revoke.mockRejectedValue(new TypeError('Failed to fetch'))
    renderEnglisch()

    fireEvent.click(await screen.findByLabelText('Revoke token board-cli'))
    expect(window.confirm).toHaveBeenCalledWith('Revoke this token? Clients using it will lose access.')
    expect(await screen.findByText('Revoking failed.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Create token' }))
    await screen.findByRole('dialog')
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'x' } })
    fireEvent.change(screen.getByLabelText('Project'), { target: { value: '3' } })
    await screen.findByRole('option', { name: 'Board A' })
    fireEvent.change(screen.getByLabelText('Board'), { target: { value: '7' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create' }))

    expect(await screen.findByText(/The token could not be created/)).toBeInTheDocument()
  })

  it('zeigt dem Plattform-Admin Done-Aufbewahrung und Standard-Labels auf Englisch', async () => {
    authState.user = { platformRole: 'ADMIN', memberships: [] }
    renderEnglisch()

    expect(screen.getByRole('heading', { name: 'Done retention', level: 2 })).toBeInTheDocument()
    expect(await screen.findByText('Currently in effect: 30 days')).toBeInTheDocument()
    expect(screen.getByText(/archived automatically after this number of days/)).toBeInTheDocument()
    expect(screen.getByText('0 = no auto-archive.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Standard labels', level: 2 })).toBeInTheDocument()

    const feld = screen.getByLabelText('Done retention in days')
    fireEvent.change(feld, { target: { value: '-1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Please enter a whole number ≥ 0 (0 = no auto-archive).')).toBeInTheDocument()
  })

  it('meldet Speichern und Speicherfehler der Done-Aufbewahrung auf Englisch', async () => {
    authState.user = { platformRole: 'ADMIN', memberships: [] }
    mConfig.getDoneRetention.mockResolvedValue({ effective: 0, override: 0 })
    mConfig.setDoneRetention.mockResolvedValueOnce({ effective: 1, override: 1 })
    mConfig.setDoneRetention.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    renderEnglisch()

    expect(await screen.findByText('Currently in effect: no auto-archive')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Done retention in days'), { target: { value: '1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Saved.')).toBeInTheDocument()
    expect(await screen.findByText('Currently in effect: 1 day')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Saving failed.')).toBeInTheDocument()
  })
})

describe('AdministrationPage — Erscheinungsbild (#1911)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authState.user = { platformRole: 'USER', memberships: [], colorScheme: 'LIGHT' }
    authState.vorschau = null
    authState.refresh.mockResolvedValue(undefined)
    mAuth.setColorScheme.mockResolvedValue({})
    mTokens.list.mockResolvedValue([])
    mProjects.list.mockResolvedValue([])
    mBoards.list.mockResolvedValue([])
    mConfig.getDoneRetention.mockResolvedValue({ effective: 30, override: null })
  })

  const gruppe = () => screen.getByRole('group', { name: 'Erscheinungsbild' })
  const stufe = (name: string) => within(gruppe()).getByRole('button', { name })

  it('zeigt drei Stufen in einer benannten Gruppe, die Kontowahl vorausgewählt', () => {
    renderPage()

    expect(within(gruppe()).getAllByRole('button').map((knopf) => knopf.textContent)).toEqual([
      'System',
      'Hell',
      'Dunkel',
    ])
    expect(stufe('Hell')).toHaveAttribute('aria-pressed', 'true')
    expect(stufe('System')).toHaveAttribute('aria-pressed', 'false')
    expect(stufe('Dunkel')).toHaveAttribute('aria-pressed', 'false')
  })

  it('wählt „System“ vor, wenn das Konto keine Wahl trägt', () => {
    authState.user = { platformRole: 'USER', memberships: [] }
    renderPage()

    expect(stufe('System')).toHaveAttribute('aria-pressed', 'true')
  })

  it('setzt beim Klick sofort den Modus, speichert die Wahl und lädt die Selbstauskunft neu', async () => {
    renderPage()

    await userEvent.click(stufe('Dunkel'))

    expect(farbe.setMode).toHaveBeenCalledWith('dark')
    expect(mAuth.setColorScheme).toHaveBeenCalledWith('DARK')
    await waitFor(() => expect(authState.refresh).toHaveBeenCalled())
    expect(stufe('Dunkel')).toHaveAttribute('aria-pressed', 'true')
    expect(stufe('Hell')).toHaveAttribute('aria-pressed', 'false')
  })

  it('lässt sich per Tastatur bedienen', async () => {
    renderPage()

    stufe('System').focus()
    await userEvent.keyboard('{Enter}')

    expect(mAuth.setColorScheme).toHaveBeenCalledWith('SYSTEM')
    expect(farbe.setMode).toHaveBeenCalledWith('system')
  })

  it('speichert nichts, wenn die schon gewählte Stufe erneut geklickt wird', async () => {
    renderPage()

    await userEvent.click(stufe('Hell'))

    expect(mAuth.setColorScheme).not.toHaveBeenCalled()
    expect(farbe.setMode).not.toHaveBeenCalled()
    expect(stufe('Hell')).toHaveAttribute('aria-pressed', 'true')
  })

  it('stellt bei einem Fehler den gespeicherten Stand wieder her und meldet ihn', async () => {
    mAuth.setColorScheme.mockRejectedValue(new Error('netz weg'))
    renderPage()

    await userEvent.click(stufe('Dunkel'))

    await erwarteFehlerToast('Das Erscheinungsbild konnte nicht gespeichert werden.')
    expect(farbe.setMode).toHaveBeenNthCalledWith(1, 'dark')
    expect(farbe.setMode).toHaveBeenLastCalledWith('light')
    expect(stufe('Hell')).toHaveAttribute('aria-pressed', 'true')
    expect(stufe('Dunkel')).toHaveAttribute('aria-pressed', 'false')
    expect(authState.refresh).not.toHaveBeenCalled()
  })
})

describe('AdministrationPage in der Ansicht als XY', () => {
  const vorschau = { kennung: 'vk-1', zielName: 'Grace', adminName: 'Ada', laeuftAbAm: '2026-10-27T10:30:00Z' }

  beforeEach(() => {
    vi.clearAllMocks()
    authState.user = { platformRole: 'USER', memberships: [], colorScheme: 'DARK' }
    authState.vorschau = vorschau
    mTokens.list.mockResolvedValue([boundToken])
    mProjects.list.mockResolvedValue([memberProject])
    mBoards.list.mockResolvedValue([])
    mConfig.getDoneRetention.mockResolvedValue({ effective: 30, override: null })
  })

  it('Szenario: Persönliches von XY bleibt verborgen', () => {
    renderPage()

    // Beide Abschnitte behalten ihre Überschrift und tragen statt des Inhalts den Hinweis.
    expect(screen.getByRole('heading', { name: 'API-Tokens', level: 2 })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Erscheinungsbild', level: 2 })).toBeInTheDocument()
    const hinweise = screen.getAllByRole('note')
    expect(hinweise).toHaveLength(2)
    for (const hinweis of hinweise) {
      expect(hinweis).toHaveTextContent('Dieser Bereich wird in der Vorschau nicht gezeigt.')
    }
    // Keine Tokens, keine Farbwahl — und die Tokens von XY werden gar nicht erst abgerufen (E5).
    expect(mTokens.list).not.toHaveBeenCalled()
    expect(mProjects.list).not.toHaveBeenCalled()
    expect(screen.queryByText('board-cli')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Token erzeugen' })).not.toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'Erscheinungsbild' })).not.toBeInTheDocument()
  })

  it('sperrt Aufbewahrung, Sprache und Standard-Labels mit dem Hinweis „Vorschau“', async () => {
    // XY ist nie Plattform-Admin; die Sperre gilt trotzdem, falls die Abschnitte erscheinen.
    authState.user = { platformRole: 'ADMIN', memberships: [] }
    renderPage()
    await screen.findByText(/Aktuell wirksam/)

    const gesperrt = screen.getAllByRole('button', { name: 'Vorschau' })
    expect(gesperrt).toHaveLength(3)
    for (const knopf of gesperrt) expect(knopf).toBeDisabled()
    // Sprache, Aufbewahrung und Standard-Labels in der Reihenfolge der Seite.
    expect(gesperrt.map((knopf) => knopf.textContent)).toEqual([
      'Speichern',
      'Speichern',
      'Standard-Labels für alle Boards',
    ])
  })

  it('lässt außerhalb der Vorschau Tokens und Farbwahl stehen', async () => {
    authState.vorschau = null
    renderPage()

    expect(await screen.findByText('board-cli')).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Erscheinungsbild' })).toBeInTheDocument()
    expect(screen.queryByRole('note')).not.toBeInTheDocument()
  })
})
