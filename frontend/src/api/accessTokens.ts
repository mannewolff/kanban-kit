import { apiFetch } from './client'

/** Persönliches API-Token (ohne Hash/Klartext). Spiegelt AccessTokenView im Backend. */
export interface AccessToken {
  id: number
  name: string
  projectId: number | null
  boardId: number | null
  createdAt: string
  lastUsedAt: string | null
  revoked: boolean
}

/** Antwort beim Anlegen: der Klartext wird GENAU EINMAL zurückgegeben. */
export interface CreatedAccessToken {
  id: number
  name: string
  plaintext: string
}

export const accessTokensApi = {
  list: () => apiFetch<AccessToken[]>('/api/access-tokens'),
  create: (name: string, projectId?: number | null, boardId?: number | null) =>
    apiFetch<CreatedAccessToken>('/api/access-tokens', {
      method: 'POST',
      body: JSON.stringify({ name, projectId, boardId }),
    }),
  revoke: (id: number) => apiFetch<void>(`/api/access-tokens/${id}`, { method: 'DELETE' }),
}

export type AccessTokensApi = typeof accessTokensApi
