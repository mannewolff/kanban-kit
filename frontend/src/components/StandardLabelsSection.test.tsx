import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { standardLabelsApi } from '../api/standardLabels'
import { StandardLabelsSection } from './StandardLabelsSection'
import { renderMitSprache } from '../i18n/renderMitSprache'

const authState = vi.hoisted(() => ({
  user: { platformRole: 'ADMIN', memberships: [] } as { platformRole: string; memberships: [] },
}))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => authState }))
vi.mock('../api/standardLabels', () => ({
  standardLabelsApi: { satz: vi.fn(), anlegen: vi.fn() },
}))

const mApi = standardLabelsApi as unknown as {
  satz: ReturnType<typeof vi.fn>
  anlegen: ReturnType<typeof vi.fn>
}

const SATZ = [
  { name: 'kit:night', gruppe: 'Kit', farbe: '#6a1b9a' },
  { name: 'kit:clarify', gruppe: 'Kit', farbe: '#6a1b9a' },
  { name: 'run:waiting', gruppe: 'Lauf', farbe: '#1565c0' },
  { name: 'goal:implementation', gruppe: 'Stufenleiste', farbe: '#ef6c00' },
]

const KNOPF = { name: 'Standard-Labels für alle Boards' }

async function dialogOeffnen() {
  await userEvent.click(screen.getByRole('button', KNOPF))
  return screen.findByRole('dialog')
}

describe('StandardLabelsSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authState.user = { platformRole: 'ADMIN', memberships: [] }
    mApi.satz.mockResolvedValue(SATZ)
    mApi.anlegen.mockResolvedValue({ boards: 3, angelegt: 10, uebersprungen: 2 })
  })

  it('zeigt einem Nicht-Admin keinen Abschnitt', () => {
    authState.user = { platformRole: 'USER', memberships: [] }
    const { container } = render(<StandardLabelsSection />)

    expect(container).toBeEmptyDOMElement()
    expect(mApi.satz).not.toHaveBeenCalled()
  })

  it('zeigt dem Admin den Knopf und lädt den Satz erst beim Öffnen', () => {
    render(<StandardLabelsSection />)

    expect(screen.getByRole('heading', { name: 'Standard-Labels' })).toBeInTheDocument()
    expect(screen.getByRole('button', KNOPF)).toBeInTheDocument()
    expect(mApi.satz).not.toHaveBeenCalled()
  })

  it('der Dialog zeigt jedes Label des Satzes nach Gruppen und erklärt die Wirkung', async () => {
    render(<StandardLabelsSection />)

    const dialog = await dialogOeffnen()

    for (const label of SATZ) {
      expect(await within(dialog).findByText(label.name)).toBeInTheDocument()
    }
    for (const gruppe of ['Kit', 'Lauf', 'Stufenleiste']) {
      expect(within(dialog).getByRole('heading', { name: gruppe })).toBeInTheDocument()
    }
    expect(within(dialog).getByText(/allen nicht archivierten Boards aller Projekte/))
      .toBeInTheDocument()
    expect(within(dialog).getByText(/Vorhandene Labels bleiben unverändert/)).toBeInTheDocument()
    expect(mApi.anlegen).not.toHaveBeenCalled()
  })

  it('erst das Bestätigen legt an und zeigt die Rückmeldung', async () => {
    render(<StandardLabelsSection />)
    const dialog = await dialogOeffnen()
    await within(dialog).findByText('kit:night')

    await userEvent.click(within(dialog).getByRole('button', { name: 'Anlegen' }))

    expect(mApi.anlegen).toHaveBeenCalledTimes(1)
    expect(
      await screen.findByText('Auf 3 Boards: 10 Labels neu angelegt, 2 schon vorhanden.'),
    ).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('Abbrechen schließt den Dialog, ohne anzulegen', async () => {
    render(<StandardLabelsSection />)
    const dialog = await dialogOeffnen()
    await within(dialog).findByText('kit:night')

    await userEvent.click(within(dialog).getByRole('button', { name: 'Abbrechen' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(mApi.anlegen).not.toHaveBeenCalled()
  })

  it('Escape schließt den Dialog, ohne anzulegen', async () => {
    render(<StandardLabelsSection />)
    const dialog = await dialogOeffnen()
    await within(dialog).findByText('kit:night')

    await userEvent.keyboard('{Escape}')

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(mApi.anlegen).not.toHaveBeenCalled()
  })

  it('ein Fehler beim Anlegen steht im Dialog, der offen bleibt', async () => {
    mApi.anlegen.mockRejectedValue(new ApiError(403, 'Forbidden', undefined, 'Der Aufrufer ist kein Plattform-Admin.'))
    render(<StandardLabelsSection />)
    const dialog = await dialogOeffnen()
    await within(dialog).findByText('kit:night')

    await userEvent.click(within(dialog).getByRole('button', { name: 'Anlegen' }))

    expect(
      await within(dialog).findByText('Der Aufrufer ist kein Plattform-Admin.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Anlegen' })).toBeEnabled()
  })

  it('ohne Meldung des Servers steht der eigene Fehlertext im Dialog', async () => {
    mApi.anlegen.mockRejectedValue(new Error('Netz weg'))
    render(<StandardLabelsSection />)
    const dialog = await dialogOeffnen()
    await within(dialog).findByText('kit:night')

    await userEvent.click(within(dialog).getByRole('button', { name: 'Anlegen' }))

    expect(
      await within(dialog).findByText('Die Standard-Labels konnten nicht angelegt werden.'),
    ).toBeInTheDocument()
  })

  it('ein Fehler beim Laden des Satzes steht im Dialog, Anlegen bleibt gesperrt', async () => {
    mApi.satz.mockRejectedValue(new Error('Netz weg'))
    render(<StandardLabelsSection />)

    const dialog = await dialogOeffnen()

    expect(
      await within(dialog).findByText('Die Standard-Labels konnten nicht geladen werden.'),
    ).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Anlegen' })).toBeDisabled()
  })
})

describe('StandardLabelsSection auf Englisch', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authState.user = { platformRole: 'ADMIN', memberships: [] }
    mApi.satz.mockResolvedValue(SATZ)
    mApi.anlegen.mockResolvedValue({ boards: 3, angelegt: 10, uebersprungen: 2 })
  })

  it('zeigt Abschnitt, Dialog und Ergebnis auf Englisch, Label- und Gruppennamen unverändert', async () => {
    renderMitSprache(<StandardLabelsSection />, 'en')

    expect(screen.getByRole('heading', { name: 'Standard labels' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Standard labels for all boards' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/non-archived boards of all projects/)).toBeInTheDocument()
    expect(await within(dialog).findByRole('heading', { name: 'Stufenleiste' })).toBeInTheDocument()
    expect(within(dialog).getByText('kit:night')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toBeInTheDocument()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }))

    expect(await screen.findByText('On 3 boards: 10 labels created, 2 already present.')).toBeInTheDocument()
  })

  it('meldet Ladefehler und Anlegefehler auf Englisch', async () => {
    mApi.satz.mockRejectedValueOnce(new Error('503'))
    renderMitSprache(<StandardLabelsSection />, 'en')

    await userEvent.click(screen.getByRole('button', { name: 'Standard labels for all boards' }))
    const dialog = await screen.findByRole('dialog')
    expect(await within(dialog).findByText('The standard labels could not be loaded.')).toBeInTheDocument()
  })

  it('fällt beim Anlegen auf Englisch ohne Server-Meldung auf den englischen Text zurück', async () => {
    mApi.anlegen.mockRejectedValue(new TypeError('Failed to fetch'))
    renderMitSprache(<StandardLabelsSection />, 'en')

    await userEvent.click(screen.getByRole('button', { name: 'Standard labels for all boards' }))
    const dialog = await screen.findByRole('dialog')
    await within(dialog).findByText('kit:night')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }))

    expect(await within(dialog).findByText('The standard labels could not be created.')).toBeInTheDocument()
  })
})
