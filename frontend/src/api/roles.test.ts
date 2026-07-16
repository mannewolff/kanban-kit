import { afterEach, describe, expect, it, vi } from 'vitest'
import { rolesApi } from './roles'

function spyFetch(body = '{}') {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    text: () => Promise.resolve(body),
  } as Response)
}

afterEach(() => vi.restoreAllMocks())

describe('rolesApi', () => {
  it('matrix ruft GET /api/roles/matrix und liefert die geparste Antwort', async () => {
    const matrix = { roles: ['OWNER', 'MEMBER'], permissions: [], grants: {} }
    spyFetch(JSON.stringify(matrix))
    const result = await rolesApi.matrix()
    expect(result).toEqual(matrix)
  })
})
