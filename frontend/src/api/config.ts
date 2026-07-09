import { apiFetch } from './client'

export interface AppConfig {
  doneRetentionDays: number
}

export const configApi = {
  get: () => apiFetch<AppConfig>('/api/config'),
}
