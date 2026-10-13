import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '../api/auth'
import { ResetPasswordPage } from './ResetPasswordPage'
import { renderMitSprache } from '../i18n/renderMitSprache'

vi.mock('../api/auth', () => ({
  authApi: { reset: vi.fn() },
}))

const mockedApi = authApi as unknown as {
  reset: ReturnType<typeof vi.fn>
}

function renderReset(search = '?token=tok-123') {
  return render(
    <MemoryRouter initialEntries={[`/reset${search}`]}>
      <ResetPasswordPage />
    </MemoryRouter>,
  )
}

describe('ResetPasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('zeigt eine Warnung, wenn kein Token in der URL steht', () => {
    renderReset('')
    expect(screen.getByText('Kein Token in der URL gefunden.')).toBeInTheDocument()
  })

  it('ruft reset mit Token und Passwort auf und zeigt die Erfolgs-Ansicht', async () => {
    mockedApi.reset.mockResolvedValue(undefined)
    renderReset()
    await userEvent.type(screen.getByLabelText(/Neues Passwort/), 'geheim123')
    await userEvent.click(screen.getByRole('button', { name: 'Passwort setzen' }))
    expect(mockedApi.reset).toHaveBeenCalledWith('tok-123', 'geheim123')
    expect(await screen.findByText(/Passwort gesetzt/)).toBeInTheDocument()
  })

  it('zeigt eine Fehlermeldung bei ungültigem/abgelaufenem Link', async () => {
    mockedApi.reset.mockRejectedValue(new Error('invalid token'))
    renderReset()
    await userEvent.type(screen.getByLabelText(/Neues Passwort/), 'geheim123')
    await userEvent.click(screen.getByRole('button', { name: 'Passwort setzen' }))
    expect(await screen.findByText('Der Link ist ungültig oder abgelaufen.')).toBeInTheDocument()
  })
})

describe('ResetPasswordPage auf Englisch', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const aufEnglisch = (search = '?token=t') =>
    renderMitSprache(
      <MemoryRouter initialEntries={[`/reset${search}`]}>
        <ResetPasswordPage />
      </MemoryRouter>,
      'en',
    )

  it('zeigt die Warnung ohne Token auf Englisch', () => {
    aufEnglisch('')
    expect(screen.getByRole('heading', { name: 'Set new password', level: 2 })).toBeInTheDocument()
    expect(screen.getByText('No token found in the URL.')).toBeInTheDocument()
    expect(screen.getByText('At least 8 characters')).toBeInTheDocument()
  })

  it('zeigt Erfolg auf Englisch', async () => {
    mockedApi.reset.mockResolvedValue(undefined)
    aufEnglisch()
    await userEvent.type(screen.getByLabelText(/New password/), 'geheim123')
    await userEvent.click(screen.getByRole('button', { name: 'Set password' }))
    expect(await screen.findByText('Password set. You can sign in now.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to sign-in' })).toBeInTheDocument()
  })

  it('zeigt den Fehler auf Englisch', async () => {
    mockedApi.reset.mockRejectedValue(new Error('x'))
    aufEnglisch()
    await userEvent.type(screen.getByLabelText(/New password/), 'geheim123')
    await userEvent.click(screen.getByRole('button', { name: 'Set password' }))
    expect(await screen.findByText('The link is invalid or has expired.')).toBeInTheDocument()
  })
})
