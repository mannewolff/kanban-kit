import { renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { projectsApi } from '../api/projects'
import { useProjectName } from './useProjectName'

vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
const mockedList = projectsApi.list as unknown as ReturnType<typeof vi.fn>

describe('useProjectName', () => {
  it('liefert den Namen des passenden Projekts', async () => {
    mockedList.mockResolvedValue([{ id: 5, name: 'Team', role: 'OWNER', createdAt: '' }])
    const { result } = renderHook(() => useProjectName(5))
    await waitFor(() => expect(result.current).toBe('Team'))
  })

  it('liefert null für ein unbekanntes Projekt', async () => {
    mockedList.mockResolvedValue([{ id: 5, name: 'Team', role: 'OWNER', createdAt: '' }])
    const { result } = renderHook(() => useProjectName(99))
    await waitFor(() => expect(mockedList).toHaveBeenCalled())
    expect(result.current).toBeNull()
  })

  it('ruft keine API auf, wenn keine Projekt-ID vorliegt', () => {
    mockedList.mockClear()
    renderHook(() => useProjectName(null))
    expect(mockedList).not.toHaveBeenCalled()
  })
})
