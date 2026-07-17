import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '../api/auth'
import { VerifyNoticePage } from './VerifyNoticePage'

vi.mock('../api/auth', () => ({
  authApi: { verify: vi.fn() },
}))

const mockedApi = authApi as unknown as {
  verify: ReturnType<typeof vi.fn>
}

function renderVerify(search = '') {
  return render(
    <MemoryRouter initialEntries={[`/verify${search}`]}>
      <VerifyNoticePage />
    </MemoryRouter>,
  )
}

describe('VerifyNoticePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('zeigt ohne Token den Bestätigungs-Hinweis', () => {
    renderVerify()
    expect(screen.getByText(/Wir haben dir eine E-Mail zur Bestätigung geschickt/)).toBeInTheDocument()
    expect(mockedApi.verify).not.toHaveBeenCalled()
  })

  it('löst mit Token die Verifikation aus und zeigt bei Erfolg die Erfolgsmeldung', async () => {
    mockedApi.verify.mockResolvedValue(undefined)
    renderVerify('?token=tok-123')
    expect(screen.getByText('E-Mail wird bestätigt …')).toBeInTheDocument()
    expect(mockedApi.verify).toHaveBeenCalledWith('tok-123')
    expect(await screen.findByText(/E-Mail bestätigt/)).toBeInTheDocument()
  })

  it('zeigt bei fehlgeschlagener Verifikation eine Fehlermeldung', async () => {
    mockedApi.verify.mockRejectedValue(new Error('expired'))
    renderVerify('?token=tok-123')
    expect(await screen.findByText('Der Bestätigungslink ist ungültig oder abgelaufen.')).toBeInTheDocument()
  })
})
