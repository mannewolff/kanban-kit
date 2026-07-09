import { describe, expect, it } from 'vitest'
import { canEditCards, canManageBoards, canManageProject, isPlatformAdmin } from './roles'

describe('roles', () => {
  it('gibt einem Plattform-Admin Vollzugriff', () => {
    expect(canEditCards('VIEWER', true)).toBe(true)
    expect(canManageProject('VIEWER', true)).toBe(true)
    expect(canManageBoards('VIEWER', true)).toBe(true)
  })

  it('richtet sich sonst nach der Projektrolle', () => {
    expect(canEditCards('VIEWER')).toBe(false)
    expect(canEditCards('MEMBER')).toBe(true)
    expect(canManageProject('OWNER')).toBe(true)
    expect(canManageProject('ADMIN')).toBe(false)
  })

  it('isPlatformAdmin liest platformRole', () => {
    expect(isPlatformAdmin({ platformRole: 'ADMIN' })).toBe(true)
    expect(isPlatformAdmin({ platformRole: 'USER' })).toBe(false)
    expect(isPlatformAdmin(null)).toBe(false)
  })
})
