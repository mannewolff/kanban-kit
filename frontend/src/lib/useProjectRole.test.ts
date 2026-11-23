import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Project } from '../api/projects'
import { projectsApi } from '../api/projects'
import { fremdBoardApi, type FremdBoardStand } from '../api/fremdboard'
import { useProjectRole } from './useProjectRole'

const authUser = vi.hoisted(() => ({ value: null as unknown }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ user: authUser.value }) }))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../api/fremdboard', () => ({ fremdBoardApi: { stand: vi.fn() } }))
const mList = projectsApi.list as unknown as ReturnType<typeof vi.fn>
const mStand = fremdBoardApi.stand as unknown as ReturnType<typeof vi.fn>

const KEIN_FREMDES: FremdBoardStand = { imFremdsystem: false, art: null, standAm: null, abgleichGescheitert: false }
const IN_GITLAB: FremdBoardStand = {
  imFremdsystem: true,
  art: 'GITLAB',
  standAm: '2026-11-23T10:00:00Z',
  abgleichGescheitert: false,
}

const project = (id: number, role: string): Project => ({ id, name: `P${id}`, role, createdAt: '' })

describe('useProjectRole', () => {
  beforeEach(() => {
    mList.mockReset()
    mList.mockResolvedValue([])
    mStand.mockReset()
    mStand.mockResolvedValue(KEIN_FREMDES)
    authUser.value = { platformRole: 'USER', memberships: [] }
  })

  it('nutzt die Mitgliedschaft aus dem Auth-Context, ohne die Projektliste zu laden', () => {
    authUser.value = { platformRole: 'USER', memberships: [{ projectId: 5, role: 'MEMBER' }] }
    const { result } = renderHook(() => useProjectRole(5))
    expect(result.current.effectiveRole).toBe('MEMBER')
    expect(result.current.canEdit).toBe(true)
    expect(result.current.canModerate).toBe(false)
    expect(result.current.darfStoerungenSehen).toBe(true)
    expect(mList).not.toHaveBeenCalled()
  })

  it('verbirgt einem Kunden die Störungen (Issue #2213)', () => {
    authUser.value = { platformRole: 'USER', memberships: [{ projectId: 5, role: 'CUSTOMER' }] }
    const { result } = renderHook(() => useProjectRole(5))
    expect(result.current.canEdit).toBe(true)
    expect(result.current.darfStoerungenSehen).toBe(false)
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

  it('nimmt die Mitgliedschaft des angefragten Projekts, nicht die erste (Issue #2244)', () => {
    authUser.value = {
      platformRole: 'USER',
      memberships: [
        { projectId: 4, role: 'OWNER' },
        { projectId: 5, role: 'VIEWER' },
      ],
    }
    const { result } = renderHook(() => useProjectRole(5))
    expect(result.current.effectiveRole).toBe('VIEWER')
    expect(result.current.canEdit).toBe(false)
  })

  it('übernimmt ohne Projekt keine Rolle, auch nicht aus einer Mitgliedschaft ohne Projektnummer (Issue #2244)', () => {
    authUser.value = { platformRole: 'USER', memberships: [{ projectId: null, role: 'OWNER' }] }
    const { result } = renderHook(() => useProjectRole(null))
    expect(result.current.effectiveRole).toBe('VIEWER')
    expect(result.current.canEdit).toBe(false)
  })

  it('lädt ohne angemeldeten Nutzer die Rolle nach, ohne zu werfen (Issue #2244)', async () => {
    authUser.value = null
    mList.mockResolvedValue([project(5, 'MEMBER')])
    const { result } = renderHook(() => useProjectRole(5))
    await waitFor(() => expect(result.current.effectiveRole).toBe('MEMBER'))
  })

  it('übernimmt nach der Rückkehr zum Projekt keine verspätete Antwort des ersten Aufrufs (Issue #2244)', async () => {
    let resolveFirst: (projects: Project[]) => void = () => {}
    mList.mockImplementationOnce(() => new Promise<Project[]>((resolve) => { resolveFirst = resolve }))
    mList.mockResolvedValueOnce([project(6, 'VIEWER')])
    mList.mockResolvedValueOnce([project(5, 'MEMBER')])
    const { result, rerender } = renderHook(({ id }: { id: number | null }) => useProjectRole(id), {
      initialProps: { id: 5 as number | null },
    })
    rerender({ id: 6 })
    rerender({ id: 5 })
    await waitFor(() => expect(result.current.effectiveRole).toBe('MEMBER'))

    await act(async () => { resolveFirst([project(5, 'OWNER')]) })

    expect(result.current.effectiveRole).toBe('MEMBER')
    expect(result.current.canModerate).toBe(false)
  })

  it('übernimmt nach der Rückkehr zum Projekt keinen verspäteten Ladefehler des ersten Aufrufs (Issue #2244)', async () => {
    let rejectFirst: (error: Error) => void = () => {}
    mList.mockImplementationOnce(() => new Promise<Project[]>((_, reject) => { rejectFirst = reject }))
    mList.mockResolvedValueOnce([project(6, 'VIEWER')])
    mList.mockResolvedValueOnce([project(5, 'OWNER')])
    const { result, rerender } = renderHook(({ id }: { id: number | null }) => useProjectRole(id), {
      initialProps: { id: 5 as number | null },
    })
    rerender({ id: 6 })
    rerender({ id: 5 })
    await waitFor(() => expect(result.current.effectiveRole).toBe('OWNER'))

    await act(async () => { rejectFirst(new Error('Netzwerkfehler')) })

    expect(result.current.effectiveRole).toBe('OWNER')
  })

  it('behält nach einem Ladefehler keine zuvor nachgeladene Rolle (Issue #2244)', async () => {
    mList.mockResolvedValueOnce([project(5, 'OWNER')])
    const { result, rerender } = renderHook(() => useProjectRole(5))
    await waitFor(() => expect(result.current.effectiveRole).toBe('OWNER'))

    authUser.value = { platformRole: 'USER', memberships: [{ projectId: 5, role: 'MEMBER' }] }
    rerender()
    expect(result.current.effectiveRole).toBe('MEMBER')

    mList.mockRejectedValueOnce(new Error('Netzwerkfehler'))
    authUser.value = { platformRole: 'USER', memberships: [] }
    rerender()
    await waitFor(() => expect(mList).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(result.current.effectiveRole).toBe('VIEWER'))
    expect(result.current.canEdit).toBe(false)
  })

  it('gibt einem Plattform-Admin ohne Mitgliedschaft volle Rechte', () => {
    authUser.value = { platformRole: 'ADMIN', memberships: [] }
    const { result } = renderHook(() => useProjectRole(null))
    expect(result.current.canEdit).toBe(true)
    expect(result.current.canModerate).toBe(true)
    expect(result.current.darfStoerungenSehen).toBe(true)
  })
})

describe('useProjectRole bei einem Board in GitLab (Issue #2170, Plan #2152 A9, A10)', () => {
  beforeEach(() => {
    mList.mockReset()
    mList.mockResolvedValue([])
    mStand.mockReset()
    authUser.value = { platformRole: 'USER', memberships: [{ projectId: 5, role: 'OWNER' }] }
  })

  it('nimmt das Bearbeiten weg, sobald das Board in GitLab liegt, und nennt den Stand', async () => {
    mStand.mockResolvedValue(IN_GITLAB)
    const { result } = renderHook(() => useProjectRole(5))
    await waitFor(() => expect(result.current.fremdBoard).toEqual(IN_GITLAB))
    expect(mStand).toHaveBeenCalledWith(5)
    expect(result.current.canEdit).toBe(false)
    expect(result.current.effectiveRole).toBe('OWNER')
  })

  it('nimmt auch einem Plattform-Admin das Bearbeiten weg', async () => {
    authUser.value = { platformRole: 'ADMIN', memberships: [] }
    mStand.mockResolvedValue(IN_GITLAB)
    const { result } = renderHook(() => useProjectRole(5))
    await waitFor(() => expect(result.current.canEdit).toBe(false))
  })

  it('lässt das Bearbeiten bei einem eigenen Board', async () => {
    mStand.mockResolvedValue(KEIN_FREMDES)
    const { result } = renderHook(() => useProjectRole(5))
    await waitFor(() => expect(result.current.fremdBoard).toEqual(KEIN_FREMDES))
    expect(result.current.canEdit).toBe(true)
  })

  it('lässt das Bearbeiten, wenn die Auskunft scheitert — der Server bleibt die Sperre', async () => {
    mStand.mockRejectedValue(new Error('Netzwerkfehler'))
    const { result } = renderHook(() => useProjectRole(5))
    await waitFor(() => expect(mStand).toHaveBeenCalled())
    expect(result.current.fremdBoard).toBeNull()
    expect(result.current.canEdit).toBe(true)
  })

  it('fragt ohne Projekt nicht nach', () => {
    const { result } = renderHook(() => useProjectRole(null))
    expect(mStand).not.toHaveBeenCalled()
    expect(result.current.fremdBoard).toBeNull()
  })

  it('trägt den Stand des vorigen Projekts nicht ins nächste', async () => {
    authUser.value = {
      platformRole: 'USER',
      memberships: [
        { projectId: 5, role: 'OWNER' },
        { projectId: 6, role: 'OWNER' },
      ],
    }
    mStand.mockResolvedValueOnce(IN_GITLAB)
    const { result, rerender } = renderHook(({ id }: { id: number | null }) => useProjectRole(id), {
      initialProps: { id: 5 as number | null },
    })
    await waitFor(() => expect(result.current.canEdit).toBe(false))

    mStand.mockImplementation(() => new Promise<FremdBoardStand>(() => {}))
    rerender({ id: 6 })
    expect(result.current.fremdBoard).toBeNull()
    expect(result.current.canEdit).toBe(true)
  })

  it('übernimmt keine verspätete Antwort des vorigen Projekts', async () => {
    authUser.value = {
      platformRole: 'USER',
      memberships: [
        { projectId: 5, role: 'OWNER' },
        { projectId: 6, role: 'OWNER' },
      ],
    }
    let erste: (stand: FremdBoardStand) => void = () => {}
    mStand.mockImplementationOnce(() => new Promise<FremdBoardStand>((resolve) => { erste = resolve }))
    mStand.mockResolvedValue(KEIN_FREMDES)
    const { result, rerender } = renderHook(({ id }: { id: number | null }) => useProjectRole(id), {
      initialProps: { id: 5 as number | null },
    })
    rerender({ id: 6 })
    await waitFor(() => expect(result.current.fremdBoard).toEqual(KEIN_FREMDES))

    await act(async () => { erste(IN_GITLAB) })

    expect(result.current.fremdBoard).toEqual(KEIN_FREMDES)
    expect(result.current.canEdit).toBe(true)
  })

  it('übernimmt keinen verspäteten Fehler des vorigen Aufrufs nach der Rückkehr zum Projekt', async () => {
    let ersterFehler: (error: Error) => void = () => {}
    mStand.mockImplementationOnce(() => new Promise<FremdBoardStand>((_, reject) => { ersterFehler = reject }))
    mStand.mockResolvedValueOnce(KEIN_FREMDES)
    mStand.mockResolvedValueOnce(IN_GITLAB)
    const { result, rerender } = renderHook(({ id }: { id: number | null }) => useProjectRole(id), {
      initialProps: { id: 5 as number | null },
    })
    rerender({ id: 6 })
    rerender({ id: 5 })
    await waitFor(() => expect(result.current.fremdBoard).toEqual(IN_GITLAB))

    await act(async () => { ersterFehler(new Error('Netzwerkfehler')) })

    expect(result.current.fremdBoard).toEqual(IN_GITLAB)
    expect(result.current.canEdit).toBe(false)
  })
})
