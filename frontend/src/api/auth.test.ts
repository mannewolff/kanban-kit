import { afterEach, describe, expect, it, vi } from 'vitest'
import { authApi } from './auth'
import { ApiError } from './client'

function mockJsonResponse(body: unknown) {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    text: () => Promise.resolve(JSON.stringify(body)),
  } as Response)
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('authApi.me – Laufzeit-Validierung der Me-Antwort', () => {
  it('gibt Me bei valider Antwort zurück', async () => {
    mockJsonResponse({
      userId: 1,
      email: 'a@x.de',
      displayName: 'Ada',
      platformRole: 'USER',
      memberships: [],
    })

    const me = await authApi.me()

    expect(me.userId).toBe(1)
  })

  it('wirft ApiError bei typverletzter Server-Antwort', async () => {
    mockJsonResponse({ userId: 'nope', email: 'a@x.de' })

    await expect(authApi.me()).rejects.toBeInstanceOf(ApiError)
  })
})
