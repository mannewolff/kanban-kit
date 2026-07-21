import { apiFetch } from './client'

export interface AppConfig {
  doneRetentionDays: number
}

/** Globale Done-Aufbewahrung (Plattform-Admin). `override` = null, wenn nur der Env-Default gilt. */
export interface DoneRetention {
  effective: number
  override: number | null
}

export const configApi = {
  get: () => apiFetch<AppConfig>('/api/config'),
  getDoneRetention: () => apiFetch<DoneRetention>('/api/admin/done-retention'),
  setDoneRetention: (days: number) =>
    apiFetch<DoneRetention>('/api/admin/done-retention', {
      method: 'PUT',
      body: JSON.stringify({ days }),
    }),
}
