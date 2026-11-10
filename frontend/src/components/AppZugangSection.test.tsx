import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { accessTokensApi, type AppZugangZeile } from '../api/accessTokens'
import { ApiError } from '../api/client'
import { renderMitSprache } from '../i18n/renderMitSprache'
import { AppZugangSection } from './AppZugangSection'
import { SnackbarProvider } from './SnackbarProvider'

vi.mock('../api/accessTokens', () => ({
  accessTokensApi: { listApp: vi.fn(), createApp: vi.fn(), renewApp: vi.fn(), revokeApp: vi.fn() },
}))

const api = vi.mocked(accessTokensApi)

const ohneZugang: AppZugangZeile = {
  tokenId: null,
  projectId: 3,
  projectName: 'Dogfood',
  boardId: 7,
  boardName: 'Entwicklung',
  inhaberName: null,
  eigener: false,
  createdAt: null,
  lastUsedAt: null,
}
const eigenerZugang: AppZugangZeile = {
  ...ohneZugang,
  tokenId: 12,
  inhaberName: 'Ada Admin',
  eigener: true,
  createdAt: '2026-03-02T12:00:00Z',
  lastUsedAt: '2026-04-05T12:00:00Z',
}
const fremderZugang: AppZugangZeile = {
  ...eigenerZugang,
  tokenId: 13,
  inhaberName: 'Grace Owner',
  eigener: false,
  lastUsedAt: null,
}

const anlegenName = 'Zugang für Dogfood · Entwicklung anlegen'
const erneuernName = 'Zugang für Dogfood · Entwicklung erneuern'
const widerrufenName = 'Zugang für Dogfood · Entwicklung widerrufen'

function renderSection(sprache: 'de' | 'en' = 'de') {
  return renderMitSprache(
    <SnackbarProvider>
      <AppZugangSection />
    </SnackbarProvider>,
    sprache,
  )
}

/** Klickt den Knopf der Zeile an und bestätigt die Rückfrage mit `bestaetigen`. */
async function bestaetige(knopf: string, bestaetigen: string) {
  await userEvent.click(await screen.findByRole('button', { name: knopf }))
  const dialog = await screen.findByRole('dialog')
  await userEvent.click(within(dialog).getByRole('button', { name: bestaetigen }))
}

describe('AppZugangSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.listApp.mockResolvedValue([])
    Object.assign(navigator, { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } })
  })

  it('zeigt bei einer leeren Liste den Hinweis', async () => {
    renderSection()

    expect(await screen.findByText(/in keinem Projekt Admin oder Owner/)).toBeInTheDocument()
  })

  it('zeigt vor der Antwort weder Zeilen noch den Hinweis der leeren Liste', () => {
    api.listApp.mockReturnValue(new Promise(() => {}))
    renderSection()

    expect(screen.getByRole('heading', { name: 'Zugang der App', level: 2 })).toBeInTheDocument()
    expect(screen.queryByText(/in keinem Projekt Admin oder Owner/)).not.toBeInTheDocument()
  })

  it('meldet einen Fehler beim Laden der Zeilen', async () => {
    api.listApp.mockRejectedValue(new ApiError(500, 'x', undefined, 'Laden kaputt'))
    renderSection()

    expect(await screen.findByText('Laden kaputt')).toBeInTheDocument()
  })

  it('Zeile ohne Zugang bietet nur Anlegen', async () => {
    api.listApp.mockResolvedValue([ohneZugang])
    renderSection()

    expect(await screen.findByRole('button', { name: anlegenName })).toBeInTheDocument()
    expect(screen.getByText('Dogfood · Entwicklung')).toBeInTheDocument()
    expect(screen.getByText('Kein Zugang')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: erneuernName })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: widerrufenName })).not.toBeInTheDocument()
  })

  it('zeigt Inhaber, Anlage und letzte Nutzung, Erneuern und Widerrufen beim eigenen Zugang', async () => {
    api.listApp.mockResolvedValue([eigenerZugang])
    renderSection()

    expect(await screen.findByRole('button', { name: erneuernName })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: widerrufenName })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: anlegenName })).not.toBeInTheDocument()
    expect(
      screen.getByText('Inhaber: Ada Admin · angelegt am 2.3.2026 · zuletzt benutzt am 5.4.2026'),
    ).toBeInTheDocument()
  })

  it('bietet beim fremden Zugang Widerrufen, aber kein Erneuern', async () => {
    api.listApp.mockResolvedValue([fremderZugang])
    renderSection()

    expect(await screen.findByRole('button', { name: widerrufenName })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: erneuernName })).not.toBeInTheDocument()
    expect(
      screen.getByText('Inhaber: Grace Owner · angelegt am 2.3.2026 · noch nie benutzt'),
    ).toBeInTheDocument()
  })

  it('nennt nirgends ein Ablaufdatum', async () => {
    api.listApp.mockResolvedValue([eigenerZugang])
    renderSection()

    await screen.findByRole('button', { name: erneuernName })
    expect(screen.queryByText(/läuft ab|gültig bis/i)).not.toBeInTheDocument()
  })

  it('legt nach der Rückfrage ohne Namensfeld an und zeigt den Klartext, bis er geschlossen wird', async () => {
    api.listApp.mockResolvedValueOnce([ohneZugang]).mockResolvedValue([eigenerZugang])
    api.createApp.mockResolvedValue({ id: 12, name: 'Desktop-App', plaintext: 'tk_einmal' })
    renderSection()

    await userEvent.click(await screen.findByRole('button', { name: anlegenName }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).queryByRole('textbox')).not.toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(api.createApp).toHaveBeenCalledWith(3, 7)
    expect(screen.getByLabelText('Token-Klartext')).toHaveTextContent('tk_einmal')
    expect(await screen.findByRole('button', { name: erneuernName })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Klartext schließen' }))
    expect(screen.queryByText('tk_einmal')).not.toBeInTheDocument()
  })

  it('kopiert den Klartext und meldet das am Knopf', async () => {
    api.listApp.mockResolvedValue([ohneZugang])
    api.createApp.mockResolvedValue({ id: 12, name: 'Desktop-App', plaintext: 'tk_einmal' })
    const schreiben = vi.fn().mockResolvedValue(undefined)
    renderSection()
    await bestaetige(anlegenName, 'Anlegen')
    // user-event ersetzt die Zwischenablage beim ersten Klick, darum erst danach den Mock setzen.
    Object.assign(navigator, { clipboard: { writeText: schreiben } })

    await userEvent.click(await screen.findByRole('button', { name: 'Token kopieren' }))

    expect(schreiben).toHaveBeenCalledWith('tk_einmal')
    expect(screen.getByRole('button', { name: 'Token kopieren' })).toHaveTextContent('Kopiert')
  })

  it('erneuert nach der Rückfrage und zeigt den neuen Klartext', async () => {
    api.listApp.mockResolvedValue([eigenerZugang])
    api.renewApp.mockResolvedValue({ id: 14, name: 'Desktop-App', plaintext: 'tk_neu' })
    renderSection()

    await bestaetige(erneuernName, 'Erneuern')

    await waitFor(() => expect(api.renewApp).toHaveBeenCalledWith(12))
    expect(await screen.findByLabelText('Token-Klartext')).toHaveTextContent('tk_neu')
    expect(api.listApp).toHaveBeenCalledTimes(2)
    await userEvent.click(screen.getByRole('button', { name: 'Klartext schließen' }))
    expect(screen.queryByText('tk_neu')).not.toBeInTheDocument()
  })

  it('widerruft auch einen fremden Zugang nach der Rückfrage, ohne Klartext', async () => {
    api.listApp.mockResolvedValueOnce([fremderZugang]).mockResolvedValue([ohneZugang])
    api.revokeApp.mockResolvedValue(undefined)
    renderSection()

    await bestaetige(widerrufenName, 'Widerrufen')

    await waitFor(() => expect(api.revokeApp).toHaveBeenCalledWith(13))
    expect(await screen.findByRole('button', { name: anlegenName })).toBeInTheDocument()
    expect(screen.queryByLabelText('Token-Klartext')).not.toBeInTheDocument()
  })

  it('tut beim Abbrechen der Rückfrage nichts', async () => {
    api.listApp.mockResolvedValue([eigenerZugang])
    renderSection()

    await bestaetige(widerrufenName, 'Abbrechen')

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(api.revokeApp).not.toHaveBeenCalled()
  })

  it('zeigt die 409-Meldung mit dem Inhaber im Dialog und lädt die Zeilen neu', async () => {
    api.listApp.mockResolvedValue([ohneZugang])
    api.createApp.mockRejectedValue(
      new ApiError(409, 'x', undefined, 'Für dieses Board besteht schon ein Zugang der App. Inhaber: Grace Owner'),
    )
    renderSection()

    await bestaetige(anlegenName, 'Anlegen')

    const dialog = await screen.findByRole('dialog')
    expect(
      await within(dialog).findByText('Für dieses Board besteht schon ein Zugang der App. Inhaber: Grace Owner'),
    ).toBeInTheDocument()
    expect(screen.queryByLabelText('Token-Klartext')).not.toBeInTheDocument()
    await waitFor(() => expect(api.listApp).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(within(dialog).getByRole('button', { name: 'Anlegen' })).toBeEnabled())
  })

  it('zeigt ohne Server-Meldung den allgemeinen Fehlertext', async () => {
    api.listApp.mockResolvedValue([eigenerZugang])
    api.revokeApp.mockRejectedValue(new Error('Netz weg'))
    renderSection()

    await bestaetige(widerrufenName, 'Widerrufen')

    expect(await screen.findByText('Das hat nicht geklappt.')).toBeInTheDocument()
  })

  it('zeigt die Texte auf Englisch', async () => {
    api.listApp.mockResolvedValue([{ ...ohneZugang, boardId: 8, boardName: 'Zweit' }, eigenerZugang])
    renderSection('en')

    expect(await screen.findByRole('heading', { name: 'App access', level: 2 })).toBeInTheDocument()
    for (const [knopf, frage] of [
      ['Create access for Dogfood · Zweit', 'Create app access for Dogfood · Zweit? You will be its owner.'],
      [
        'Renew access for Dogfood · Entwicklung',
        'Renew app access for Dogfood · Entwicklung? The current access stops working immediately.',
      ],
      [
        'Revoke access for Dogfood · Entwicklung',
        'Revoke app access for Dogfood · Entwicklung? This is final. A new access can be created afterwards.',
      ],
    ]) {
      await userEvent.click(await screen.findByRole('button', { name: knopf }))
      const dialog = await screen.findByRole('dialog')
      expect(within(dialog).getByText(frage)).toBeInTheDocument()
      await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }))
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    }
    expect(
      screen.getByText('Owner: Ada Admin · created on Mar 2, 2026 · last used on Apr 5, 2026'),
    ).toBeInTheDocument()
  })
})
