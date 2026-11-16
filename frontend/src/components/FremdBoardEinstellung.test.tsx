import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { fremdBoardApi, STANDARD_STATUSLABELS, type FremdBoardAnsicht } from '../api/fremdboard'
import { projectsApi, type Project } from '../api/projects'
import { renderMitSprache } from '../i18n/renderMitSprache'
import { FremdBoardEinstellung } from './FremdBoardEinstellung'
import { SnackbarProvider } from './SnackbarProvider'

const authState = vi.hoisted(() => ({
  user: { platformRole: 'USER' } as { platformRole: string },
  vorschau: null as null | Record<string, string>,
}))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => authState }))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../api/fremdboard', async (original) => ({
  ...(await original<typeof import('../api/fremdboard')>()),
  fremdBoardApi: { lies: vi.fn(), speichere: vi.fn() },
}))

const projekte = vi.mocked(projectsApi)
const api = vi.mocked(fremdBoardApi)

const projekt = (id: number, name: string, role: string): Project => ({
  id,
  name,
  role,
  createdAt: '2026-01-01T00:00:00Z',
})

const ohne: FremdBoardAnsicht = {
  angebunden: false,
  schluesselGesetzt: true,
  art: null,
  basisUrl: null,
  projektPfad: null,
  kitKonto: null,
  statuslabels: null,
  angebundenAm: null,
  letzterAbgleichAm: null,
  letzterFehler: null,
  zugangLesbar: false,
}

const angebunden: FremdBoardAnsicht = {
  angebunden: true,
  schluesselGesetzt: true,
  art: 'GITLAB',
  basisUrl: 'https://gitlab.example.org',
  projektPfad: 'gruppe/projekt',
  kitKonto: 'kit-bot',
  statuslabels: { backlog: 'Offen', ready: 'Bereit', inProgress: 'Dran', inReview: 'Prüfen' },
  angebundenAm: '2026-11-15T10:00:00Z',
  letzterAbgleichAm: '2026-11-15T10:05:00Z',
  letzterFehler: null,
  zugangLesbar: true,
}

const GEHEIM = 'glpat-ganz-geheim'

function renderEinstellung(sprache: 'de' | 'en' = 'de') {
  return renderMitSprache(
    <SnackbarProvider>
      <FremdBoardEinstellung />
    </SnackbarProvider>,
    sprache,
  )
}

/** Wählt das Projekt und wartet, bis seine Verbindung geladen ist. */
async function waehle(name: string) {
  await userEvent.selectOptions(await screen.findByRole('combobox', { name: 'Projekt' }), name)
  await screen.findByRole('textbox', { name: 'Adresse' })
}

describe('FremdBoardEinstellung', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authState.user = { platformRole: 'USER' }
    authState.vorschau = null
    projekte.list.mockResolvedValue([projekt(4, 'Dogfood', 'OWNER'), projekt(5, 'Fremd', 'ADMIN')])
    api.lies.mockResolvedValue(ohne)
  })

  it('zeigt sich nur mit einem Projekt, in dem der Angemeldete Owner ist', async () => {
    projekte.list.mockResolvedValue([projekt(5, 'Fremd', 'ADMIN'), projekt(6, 'Lesen', 'VIEWER')])
    renderEinstellung()

    await waitFor(() => expect(projekte.list).toHaveBeenCalled())
    expect(screen.queryByRole('heading', { name: 'Board-Quelle' })).not.toBeInTheDocument()
  })

  it('bietet nur die Projekte an, in denen der Angemeldete Owner ist', async () => {
    renderEinstellung()

    const auswahl = await screen.findByRole('combobox', { name: 'Projekt' })
    expect(screen.getByRole('heading', { name: 'Board-Quelle', level: 2 })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Dogfood' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'Fremd' })).not.toBeInTheDocument()
    expect(auswahl).toHaveValue('')
  })

  it('bietet einem Plattform-Admin jedes Projekt an', async () => {
    authState.user = { platformRole: 'ADMIN' }
    projekte.list.mockResolvedValue([projekt(5, 'Fremd', 'ADMIN')])
    renderEinstellung()

    expect(await screen.findByRole('option', { name: 'Fremd' })).toBeInTheDocument()
  })

  it('meldet einen Fehler beim Laden der Projekte', async () => {
    projekte.list.mockRejectedValue(new ApiError(500, 'x', undefined, 'Projekte kaputt'))
    renderEinstellung()

    expect(await screen.findByText('Projekte kaputt')).toBeInTheDocument()
  })

  it('füllt ohne Verbindung die Vorgaben der Statuslabels und verlangt den Zugang', async () => {
    renderEinstellung()
    await waehle('Dogfood')

    expect(api.lies).toHaveBeenCalledWith(4)
    expect(screen.getByRole('textbox', { name: 'Adresse' })).toHaveValue('')
    expect(screen.getByRole('textbox', { name: 'Label für Backlog' })).toHaveValue('Backlog')
    expect(screen.getByRole('textbox', { name: 'Label für Ready' })).toHaveValue('Ready')
    expect(screen.getByRole('textbox', { name: 'Label für In progress' })).toHaveValue('In progress')
    expect(screen.getByRole('textbox', { name: 'Label für In review' })).toHaveValue('In review')
    expect(screen.getByLabelText(/^Zugang/)).toBeRequired()
    expect(screen.getByRole('button', { name: 'Anbinden' })).toBeDisabled()
  })

  it('speichert die Angaben und zeigt den Zugang danach nicht mehr', async () => {
    api.speichere.mockResolvedValue({ ...angebunden, statuslabels: { ...STANDARD_STATUSLABELS } })
    renderEinstellung()
    await waehle('Dogfood')

    await userEvent.type(screen.getByRole('textbox', { name: 'Adresse' }), 'https://gitlab.example.org')
    await userEvent.type(screen.getByRole('textbox', { name: 'Projektpfad' }), 'gruppe/projekt')
    await userEvent.type(screen.getByLabelText(/^Zugang/), GEHEIM)
    await userEvent.type(screen.getByRole('textbox', { name: 'Kit-Konto' }), 'kit-bot')
    await userEvent.click(screen.getByRole('button', { name: 'Anbinden' }))

    await waitFor(() =>
      expect(api.speichere).toHaveBeenCalledWith(4, {
        art: 'GITLAB',
        basisUrl: 'https://gitlab.example.org',
        projektPfad: 'gruppe/projekt',
        zugang: GEHEIM,
        kitKonto: 'kit-bot',
        statuslabels: { ...STANDARD_STATUSLABELS },
      }),
    )
    expect(await screen.findByText('Gespeichert.')).toBeInTheDocument()
    expect(screen.getByLabelText(/^Zugang/)).toHaveValue('')
    expect(document.body.innerHTML).not.toContain(GEHEIM)
    expect(screen.getByText(/liegt in GitLab/)).toBeInTheDocument()
  })

  it('schickt ein leeres Kit-Konto als null', async () => {
    api.speichere.mockResolvedValue(angebunden)
    renderEinstellung()
    await waehle('Dogfood')

    await userEvent.type(screen.getByRole('textbox', { name: 'Adresse' }), 'https://gitlab.com')
    await userEvent.type(screen.getByRole('textbox', { name: 'Projektpfad' }), 'g/p')
    await userEvent.type(screen.getByLabelText(/^Zugang/), GEHEIM)
    await userEvent.click(screen.getByRole('button', { name: 'Anbinden' }))

    await waitFor(() => expect(api.speichere).toHaveBeenCalled())
    expect(api.speichere.mock.calls[0][1].kitKonto).toBeNull()
  })

  it('zeigt eine Ablehnung mit dem Grund des Servers und behält die Eingaben', async () => {
    api.speichere.mockRejectedValue(
      new ApiError(409, 'x', undefined, 'Das Projekt hat schon Karten in kanban-kit: 3 auf Boards.'),
    )
    renderEinstellung()
    await waehle('Dogfood')

    await userEvent.type(screen.getByRole('textbox', { name: 'Adresse' }), 'https://gitlab.com')
    await userEvent.type(screen.getByRole('textbox', { name: 'Projektpfad' }), 'g/p')
    await userEvent.type(screen.getByLabelText(/^Zugang/), GEHEIM)
    await userEvent.click(screen.getByRole('button', { name: 'Anbinden' }))

    expect(
      await screen.findByText('Das Projekt hat schon Karten in kanban-kit: 3 auf Boards.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Adresse' })).toHaveValue('https://gitlab.com')
    expect(screen.getByRole('button', { name: 'Anbinden' })).toBeEnabled()
  })

  it('zeigt ohne Grund vom Server eine eigene Meldung', async () => {
    api.speichere.mockRejectedValue(new Error('netz'))
    renderEinstellung()
    await waehle('Dogfood')

    await userEvent.type(screen.getByRole('textbox', { name: 'Adresse' }), 'https://gitlab.com')
    await userEvent.type(screen.getByRole('textbox', { name: 'Projektpfad' }), 'g/p')
    await userEvent.type(screen.getByLabelText(/^Zugang/), GEHEIM)
    await userEvent.click(screen.getByRole('button', { name: 'Anbinden' }))

    expect(await screen.findByText('Die Board-Quelle ließ sich nicht speichern.')).toBeInTheDocument()
  })

  it('weist ohne Schlüssel auf dem Server darauf hin', async () => {
    api.lies.mockResolvedValue({ ...ohne, schluesselGesetzt: false })
    renderEinstellung()
    await waehle('Dogfood')

    expect(screen.getByText(/kein Schlüssel für Zugänge gesetzt/)).toBeInTheDocument()
  })

  it('zeigt eine bestehende Verbindung mit festen Angaben und ohne Pflicht zum Zugang', async () => {
    api.lies.mockResolvedValue(angebunden)
    renderEinstellung()
    await waehle('Dogfood')

    expect(screen.getByRole('textbox', { name: 'Adresse' })).toHaveValue('https://gitlab.example.org')
    expect(screen.getByRole('textbox', { name: 'Adresse' })).toBeDisabled()
    expect(screen.getByRole('textbox', { name: 'Projektpfad' })).toBeDisabled()
    expect(screen.getByRole('textbox', { name: 'Kit-Konto' })).toHaveValue('kit-bot')
    expect(screen.getByRole('textbox', { name: 'Label für Backlog' })).toHaveValue('Offen')
    expect(screen.getByLabelText(/^Zugang/)).not.toBeRequired()
    expect(screen.getByLabelText(/^Zugang/)).toHaveValue('')
    expect(screen.getByText(/Letzter Abgleich/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeEnabled()
  })

  it('schickt bei einer bestehenden Verbindung ohne neuen Zugang null', async () => {
    api.lies.mockResolvedValue(angebunden)
    api.speichere.mockResolvedValue(angebunden)
    renderEinstellung()
    await waehle('Dogfood')

    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(api.speichere).toHaveBeenCalled())
    expect(api.speichere.mock.calls[0][1]).toMatchObject({
      zugang: null,
      basisUrl: 'https://gitlab.example.org',
      statuslabels: angebunden.statuslabels,
    })
  })

  it('sagt, wenn noch kein Abgleich gelaufen ist', async () => {
    api.lies.mockResolvedValue({ ...angebunden, letzterAbgleichAm: null })
    renderEinstellung()
    await waehle('Dogfood')

    expect(screen.getByText('Letzter Abgleich: noch keiner')).toBeInTheDocument()
  })

  it('nennt den Fehler des letzten Abgleichs', async () => {
    api.lies.mockResolvedValue({ ...angebunden, letzterFehler: 'GitLab antwortete mit 401.' })
    renderEinstellung()
    await waehle('Dogfood')

    expect(screen.getByText(/GitLab antwortete mit 401\./)).toBeInTheDocument()
  })

  it('weist auf einen nicht lesbaren Zugang hin und führt zum neu Hinterlegen', async () => {
    api.lies.mockResolvedValue({ ...angebunden, zugangLesbar: false })
    renderEinstellung()
    await waehle('Dogfood')

    expect(screen.getByText(/Zugang lässt sich nicht lesen/)).toBeInTheDocument()
    const zugang = screen.getByLabelText(/^Zugang/)
    expect(zugang).toBeRequired()
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: 'Zugang neu hinterlegen' }))
    expect(zugang).toHaveFocus()

    await userEvent.type(zugang, GEHEIM)
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeEnabled()
  })

  it('meldet einen Fehler beim Laden der Verbindung', async () => {
    api.lies.mockRejectedValue(new ApiError(403, 'x', undefined, 'Nur für den Owner.'))
    renderEinstellung()
    await userEvent.selectOptions(await screen.findByRole('combobox', { name: 'Projekt' }), 'Dogfood')

    expect(await screen.findByText('Nur für den Owner.')).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: 'Adresse' })).not.toBeInTheDocument()
  })

  it('leert das Formular, wenn wieder kein Projekt gewählt ist', async () => {
    renderEinstellung()
    await waehle('Dogfood')

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Projekt' }), '')

    expect(screen.queryByRole('textbox', { name: 'Adresse' })).not.toBeInTheDocument()
  })

  it('sperrt das Speichern, solange ein Statuslabel leer ist', async () => {
    api.lies.mockResolvedValue(angebunden)
    renderEinstellung()
    await waehle('Dogfood')

    await userEvent.clear(screen.getByRole('textbox', { name: 'Label für Ready' }))

    expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled()
  })

  it('bleibt in der Vorschau weg und lädt nichts', () => {
    authState.vorschau = { kennung: 'vk-1' }
    renderEinstellung()

    expect(screen.queryByRole('heading', { name: 'Board-Quelle' })).not.toBeInTheDocument()
    expect(projekte.list).not.toHaveBeenCalled()
  })

  it('spricht Englisch', async () => {
    api.lies.mockResolvedValue({ ...angebunden, letzterFehler: 'GitLab answered 401.' })
    renderEinstellung('en')

    expect(await screen.findByRole('heading', { name: 'Board source', level: 2 })).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Project' }), 'Dogfood')

    expect(await screen.findByText(/lives in GitLab, connected on/)).toBeInTheDocument()
    expect(screen.getByText(/^Last sync: /)).toBeInTheDocument()
    expect(screen.getByText('The last sync failed: GitLab answered 401.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled()
  })
})
