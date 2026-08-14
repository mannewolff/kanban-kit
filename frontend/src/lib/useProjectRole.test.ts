import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Project } from '../api/projects'
import { projectsApi } from '../api/projects'
import { useProjectRole } from './useProjectRole'

const authUser = vi.hoisted(() => ({ value: null as unknown }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ user: authUser.value }) }))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
const mList = projectsApi.list as unknown as ReturnType<typeof vi.fn>

const project = (id: number, role: string): Project => ({ id, name: `P${id}`, role, createdAt: '' })

describe('useProjectRole', () => {
  beforeEach(() => {
    mList.mockReset()
    mList.mockResolvedValue([])
    authUser.value = { platformRole: 'USER', memberships: [] }
  })

  it('nutzt die Mitgliedschaft aus dem Auth-Context, ohne die Projektliste zu laden', () => {
    authUser.value = { platformRole: 'USER', memberships: [{ projectId: 5, role: 'MEMBER' }] }
    const { result } = renderHook(() => useProjectRole(5))
    expect(result.current.effectiveRole).toBe('MEMBER')
    expect(result.current.canEdit).toBe(true)
    expect(result.current.canModerate).toBe(false)
    expect(mList).not.toHaveBeenCalled()
  })

  it('lädt die Rolle nach, wenn keine Mitgliedschaft bekannt ist', async () => {
    mList.mockResolvedValue([project(5, 'ADMIN')])
    const { result } = renderHook(() => useProjectRole(5))
    await waitFor(() => expect(result.current.effectiveRole).toBe('ADMIN'))
    expect(result.current.canEdit).toBe(true)
    expect(result.current.canModerate).toBe(true)
  })

  it('fällt auf VIEWER zurück, wenn das Projekt nicht in der Liste steht', async () => {
    mList.mockResolvedValue([project(999, 'OWNER')])
    const { result } = renderHook(() => useProjectRole(5))
    await waitFor(() => expect(mList).toHaveBeenCalled())
    expect(result.current.effectiveRole).toBe('VIEWER')
    expect(result.current.canEdit).toBe(false)
  })

  it('ergibt bei einem Ladefehler VIEWER, ohne zu werfen', async () => {
    mList.mockRejectedValue(new Error('Netzwerkfehler'))
    const { result } = renderHook(() => useProjectRole(5))
    await waitFor(() => expect(mList).toHaveBeenCalled())
    expect(result.current.effectiveRole).toBe('VIEWER')
    expect(result.current.canEdit).toBe(false)
  })

  it('fällt beim Projektwechsel sofort auf VIEWER zurück, bis die neue Rolle feststeht', async () => {
    mList.mockResolvedValueOnce([project(5, 'OWNER')])
    const { result, rerender } = renderHook(({ id }: { id: number | null }) => useProjectRole(id), {
      initialProps: { id: 5 as number | null },
    })
    await waitFor(() => expect(result.current.effectiveRole).toBe('OWNER'))

    // Antwort für das neue Projekt steht noch aus: die alte Rolle darf nicht weiterwirken.
    mList.mockImplementation(() => new Promise<Project[]>(() => {}))
    rerender({ id: 6 })
    expect(result.current.effectiveRole).toBe('VIEWER')
    expect(result.current.canEdit).toBe(false)
  })

  it('ignoriert die verspätet eintreffende Antwort des vorigen Projekts', async () => {
    let resolveFirst: (projects: Project[]) => void = () => {}
    mList.mockImplementationOnce(() => new Promise<Project[]>((resolve) => { resolveFirst = resolve }))
    mList.mockResolvedValue([project(6, 'VIEWER')])
    const { result, rerender } = renderHook(({ id }: { id: number | null }) => useProjectRole(id), {
      initialProps: { id: 5 as number | null },
    })
    rerender({ id: 6 })
    await waitFor(() => expect(mList).toHaveBeenCalledTimes(2))

    await act(async () => { resolveFirst([project(5, 'OWNER')]) })

    expect(result.current.effectiveRole).toBe('VIEWER')
    expect(result.current.canEdit).toBe(false)
  })

  it('liefert ohne Projekt VIEWER, ohne die Projektliste zu laden', () => {
    const { result } = renderHook(() => useProjectRole(null))
    expect(mList).not.toHaveBeenCalled()
    expect(result.current.effectiveRole).toBe('VIEWER')
    expect(result.current.canEdit).toBe(false)
    expect(result.current.canModerate).toBe(false)
  })

  it('gibt einem Plattform-Admin ohne Mitgliedschaft volle Rechte', () => {
    authUser.value = { platformRole: 'ADMIN', memberships: [] }
    const { result } = renderHook(() => useProjectRole(null))
    expect(result.current.canEdit).toBe(true)
    expect(result.current.canModerate).toBe(true)
  })
})
