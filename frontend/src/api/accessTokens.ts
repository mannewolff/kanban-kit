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

/** Eine Zeile der Verwaltung ohne Zugang: nur das Board ist bekannt. */
interface AppZugangOhneToken {
  tokenId: null
  inhaberName: null
  eigener: false
  createdAt: null
  lastUsedAt: null
}

/** Eine Zeile der Verwaltung mit wirksamem Zugang. `eigener`: Der Aufrufer darf erneuern. */
export interface AppZugangMitToken {
  tokenId: number
  projectId: number
  projectName: string
  boardId: number
  boardName: string
  inhaberName: string
  eigener: boolean
  createdAt: string
  lastUsedAt: string | null
}

/** Je Board eine Zeile, ohne Klartext. Spiegelt AppZugangZeile im Backend. */
export type AppZugangZeile =
  | (AppZugangOhneToken & Pick<AppZugangMitToken, 'projectId' | 'projectName' | 'boardId' | 'boardName'>)
  | AppZugangMitToken

export const accessTokensApi = {
  list: () => apiFetch<AccessToken[]>('/api/access-tokens'),
  create: (name: string, projectId?: number | null, boardId?: number | null) =>
    apiFetch<CreatedAccessToken>('/api/access-tokens', {
      method: 'POST',
      body: JSON.stringify({ name, projectId, boardId }),
    }),
  revoke: (id: number) => apiFetch<void>(`/api/access-tokens/${id}`, { method: 'DELETE' }),
  listApp: () => apiFetch<AppZugangZeile[]>('/api/access-tokens/app'),
  createApp: (projectId: number, boardId: number) =>
    apiFetch<CreatedAccessToken>('/api/access-tokens/app', {
      method: 'POST',
      body: JSON.stringify({ projectId, boardId }),
    }),
  renewApp: (id: number) =>
    apiFetch<CreatedAccessToken>(`/api/access-tokens/app/${id}/erneuern`, { method: 'POST' }),
  revokeApp: (id: number) => apiFetch<void>(`/api/access-tokens/app/${id}`, { method: 'DELETE' }),
}

export type AccessTokensApi = typeof accessTokensApi
