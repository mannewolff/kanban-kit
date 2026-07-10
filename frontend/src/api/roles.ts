import { apiFetch } from './client'

export interface PermissionDef {
  key: string
  resource: string
  operation: string
}

export interface RoleMatrix {
  roles: string[]
  permissions: PermissionDef[]
  /** role -> Liste der gewährten Permission-Keys */
  grants: Record<string, string[]>
}

export const rolesApi = {
  matrix: () => apiFetch<RoleMatrix>('/api/roles/matrix'),
}

export type RolesApi = typeof rolesApi
