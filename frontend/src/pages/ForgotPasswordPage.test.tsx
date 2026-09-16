import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '../api/auth'
import { ApiError } from '../api/client'
import { ForgotPasswordPage } from './ForgotPasswordPage'

vi.mock('../api/auth', () => ({
  authApi: { forgot: vi.fn() },
}))

const mockedApi = authApi as unknown as {
  forgot: ReturnType<typeof vi.fn>
}

function renderForgot() {
  return render(
    <MemoryRouter>
      <ForgotPasswordPage />
    </MemoryRouter>,
  )
}

async function submitForgot() {
  renderForgot()
  await userEvent.type(screen.getByLabelText(/E-Mail/), 'a@b.de')
  await userEvent.click(screen.getByRole('button', { name: 'Link anfordern' }))
}

const successMessage = /Falls ein Konto mit dieser E-Mail existiert/

describe('ForgotPasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('ruft forgot mit der E-Mail auf und zeigt bei Erfolg denselben Hinweis', async () => {
    mockedApi.forgot.mockResolvedValue(undefined)
    await submitForgot()
    expect(mockedApi.forgot).toHaveBeenCalledWith('a@b.de')
    expect(await screen.findByText(successMessage)).toBeInTheDocument()
  })

  it('zeigt denselben Hinweis auch, wenn forgot fehlschlägt (kein Leak, ob die E-Mail existiert)', async () => {
    mockedApi.forgot.mockRejectedValue(new Error('boom'))
    await submitForgot()
    expect(await screen.findByText(successMessage)).toBeInTheDocument()
  })

  it('zeigt bei 429 die Abweisung der Zaehlbremse statt des neutralen Hinweises', async () => {
    mockedApi.forgot.mockRejectedValue(
      new ApiError(429, 'Too Many Requests', undefined, 'Zu viele Versuche. Bitte in 15 Minuten erneut versuchen.'),
    )
    await submitForgot()
    expect(await screen.findByText('Zu viele Versuche. Bitte in 15 Minuten erneut versuchen.')).toBeInTheDocument()
    expect(screen.queryByText(successMessage)).not.toBeInTheDocument()
  })

  it('bleibt bei 500 beim neutralen Hinweis (die Aenderung weicht AK 5 nicht auf)', async () => {
    mockedApi.forgot.mockRejectedValue(new ApiError(500, 'Internal Server Error', undefined, 'Datenbank weg'))
    await submitForgot()
    expect(await screen.findByText(successMessage)).toBeInTheDocument()
    expect(screen.queryByText('Datenbank weg')).not.toBeInTheDocument()
  })
})
