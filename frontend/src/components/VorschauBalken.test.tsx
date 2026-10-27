import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '../api/auth'
import { ApiError, setNeuLaden } from '../api/client'
import { renderMitSprache } from '../i18n/renderMitSprache'
import { SnackbarProvider } from './SnackbarProvider'
import { VorschauBalken, VORSCHAU_BALKEN_HEIGHT } from './VorschauBalken'

const auth = vi.hoisted(() => ({ vorschau: null as null | Record<string, string> }))
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ vorschau: auth.vorschau }),
}))

vi.mock('../api/auth', () => ({
  authApi: { vorschauBeenden: vi.fn() },
}))

const beenden = authApi.vorschauBeenden as unknown as ReturnType<typeof vi.fn>

const vorschau = { kennung: 'vk-1', zielName: 'Grace', adminName: 'Ada', laeuftAbAm: '2026-10-27T10:30:00Z' }

function renderBalken(oben = 0, sprache: 'de' | 'en' = 'de') {
  return renderMitSprache(
    <SnackbarProvider>
      <VorschauBalken oben={oben} />
    </SnackbarProvider>,
    sprache,
  )
}

describe('VorschauBalken', () => {
  const neuLaden = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    auth.vorschau = vorschau
    setNeuLaden(neuLaden)
  })

  afterEach(() => setNeuLaden(null))

  it('rendert nichts außerhalb der Vorschau', () => {
    auth.vorschau = null
    renderBalken()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('zeigt den festen Balken mit dem Namen von XY als Status mit zugänglichem Namen', () => {
    renderBalken()
    const balken = screen.getByRole('status', { name: 'Ansicht als Grace' })
    expect(balken).toHaveTextContent('Du siehst die App als Grace. Nur lesend.')
    expect(balken).toHaveStyle({ position: 'fixed', top: '0px', height: `${VORSCHAU_BALKEN_HEIGHT}px` })
  })

  it('setzt sich um den übergebenen Versatz nach unten', () => {
    renderBalken(40)
    expect(screen.getByRole('status', { name: 'Ansicht als Grace' })).toHaveStyle({ top: '40px' })
  })

  it('beendet mit „Beenden“ die Vorschau und lädt danach auf der Startseite neu', async () => {
    beenden.mockResolvedValue(undefined)
    renderBalken()

    await userEvent.click(screen.getByRole('button', { name: 'Beenden' }))

    expect(beenden).toHaveBeenCalledTimes(1)
    await waitFor(() => expect(neuLaden).toHaveBeenCalledTimes(1))
  })

  it('meldet ein gescheitertes Beenden mit der Server-Meldung und lädt nicht neu', async () => {
    beenden.mockRejectedValue(new ApiError(500, 'x', undefined, 'Sitzung unbekannt.'))
    renderBalken()

    await userEvent.click(screen.getByRole('button', { name: 'Beenden' }))

    expect(await screen.findByText('Sitzung unbekannt.')).toBeInTheDocument()
    expect(neuLaden).not.toHaveBeenCalled()
  })

  it('fällt ohne Server-Meldung auf den eigenen Text zurück', async () => {
    beenden.mockRejectedValue(new TypeError('Failed to fetch'))
    renderBalken()

    await userEvent.click(screen.getByRole('button', { name: 'Beenden' }))

    expect(await screen.findByText('Die Ansicht ließ sich nicht beenden.')).toBeInTheDocument()
  })

  it('zeigt Balken und Knopf auf Englisch', () => {
    renderBalken(0, 'en')
    expect(screen.getByRole('status', { name: 'View as Grace' })).toHaveTextContent(
      'You are viewing the app as Grace. Read only.',
    )
    expect(screen.getByRole('button', { name: 'End' })).toBeInTheDocument()
  })
})
