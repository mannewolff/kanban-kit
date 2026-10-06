import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { standardLabelsApi } from '../api/standardLabels'
import { StandardLabelsSection } from './StandardLabelsSection'

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
  { name: 'kit:klaeren', gruppe: 'Kit', farbe: '#6a1b9a' },
  { name: 'lauf:wartet', gruppe: 'Lauf', farbe: '#1565c0' },
  { name: 'ziel:umsetzung', gruppe: 'Stufenleiste', farbe: '#ef6c00' },
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
