import { describe, expect, it } from 'vitest'
import {
  canEditCards,
  canManageBoards,
  canManageMembers,
  canManageProject,
  canModerateComments,
  canTransferOwnership,
  isPlatformAdmin,
} from './roles'

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

  it('lässt nur OWNER und Plattform-Admin über die Rolle OWNER entscheiden (Issue #1972)', () => {
    expect(canTransferOwnership('OWNER')).toBe(true)
    expect(canTransferOwnership('ADMIN')).toBe(false)
    expect(canTransferOwnership('MEMBER')).toBe(false)
    expect(canTransferOwnership('ADMIN', true)).toBe(true)
  })

  it.each([
    ['canManageBoards', canManageBoards],
    ['canManageMembers', canManageMembers],
    ['canModerateComments', canModerateComments],
  ])('%s lässt nur OWNER, ADMIN und Plattform-Admin zu', (_name, check) => {
    expect(check('OWNER')).toBe(true)
    expect(check('ADMIN')).toBe(true)
    expect(check('MEMBER')).toBe(false)
    expect(check('VIEWER')).toBe(false)
    expect(check('')).toBe(false)
    expect(check('MEMBER', true)).toBe(true)
    expect(check('MEMBER', false)).toBe(false)
  })

  it('isPlatformAdmin liest platformRole', () => {
    expect(isPlatformAdmin({ platformRole: 'ADMIN' })).toBe(true)
    expect(isPlatformAdmin({ platformRole: 'USER' })).toBe(false)
    expect(isPlatformAdmin(null)).toBe(false)
  })
})
