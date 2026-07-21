import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Board } from '../api/boards'
import { projectsApi } from '../api/projects'
import { useBoardRole } from './useBoardRole'

const authUser = vi.hoisted(() => ({ value: null as unknown }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ user: authUser.value }) }))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
const mList = projectsApi.list as unknown as ReturnType<typeof vi.fn>

const board: Board = { id: 1, projectId: 5, name: 'B', createdAt: '', columns: [] }

describe('useBoardRole', () => {
  beforeEach(() => {
    mList.mockReset()
    mList.mockResolvedValue([])
    authUser.value = { platformRole: 'USER', memberships: [] }
  })

  it('nutzt die eigene Mitgliedschaft, ohne die Projektliste zu laden', () => {
    authUser.value = { platformRole: 'USER', memberships: [{ projectId: 5, role: 'MEMBER' }] }
    const { result } = renderHook(() => useBoardRole(board))
    expect(result.current.effectiveRole).toBe('MEMBER')
    expect(result.current.canEdit).toBe(true)
    expect(result.current.canModerate).toBe(false)
    expect(mList).not.toHaveBeenCalled()
  })

  it('lädt die Rolle nach, wenn keine Mitgliedschaft bekannt ist (ADMIN → volle Rechte)', async () => {
    mList.mockResolvedValue([{ id: 5, name: 'B', role: 'ADMIN', createdAt: '' }])
    const { result } = renderHook(() => useBoardRole(board))
    await waitFor(() => expect(result.current.canModerate).toBe(true))
    expect(result.current.effectiveRole).toBe('ADMIN')
    expect(result.current.canEdit).toBe(true)
  })

  it('fällt auf VIEWER zurück, wenn das Projekt nicht gefunden wird', async () => {
    mList.mockResolvedValue([])
    const { result } = renderHook(() => useBoardRole(board))
    await waitFor(() => expect(mList).toHaveBeenCalled())
    expect(result.current.effectiveRole).toBe('VIEWER')
    expect(result.current.canEdit).toBe(false)
  })

  it('lädt nichts ohne Board und liefert VIEWER', () => {
    const { result } = renderHook(() => useBoardRole(null))
    expect(mList).not.toHaveBeenCalled()
    expect(result.current.effectiveRole).toBe('VIEWER')
    expect(result.current.canEdit).toBe(false)
  })

  it('gibt einem Plattform-Admin volle Rechte, unabhängig von der Projektrolle', () => {
    authUser.value = { platformRole: 'ADMIN', memberships: [] }
    const { result } = renderHook(() => useBoardRole(null))
    expect(result.current.canEdit).toBe(true)
    expect(result.current.canModerate).toBe(true)
  })
})
