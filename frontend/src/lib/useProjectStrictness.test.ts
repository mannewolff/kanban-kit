import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { projectsApi, type Project } from '../api/projects'
import { useProjectStrictness } from './useProjectStrictness'

vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
const mockedList = projectsApi.list as unknown as ReturnType<typeof vi.fn>

function projekt(id: number, strictness: Project['strictness']): Project {
  return { id, name: `P${id}`, role: 'OWNER', createdAt: '', strictness }
}

/** Eine Antwort, die der Test selbst auflöst — für Wechsel und Abbau vor dem Laden. */
function verzoegert() {
  let aufloesen: (ps: Project[]) => void = () => undefined
  const antwort = new Promise<Project[]>((resolve) => {
    aufloesen = resolve
  })
  return { antwort, aufloesen }
}

/** Lässt die bereits aufgelöste Antwort samt ihrer `then`-Kette durchlaufen. */
function antwortVerarbeitet() {
  return act(() => new Promise<void>((resolve) => setTimeout(resolve, 0)))
}

describe('useProjectStrictness', () => {
  beforeEach(() => {
    mockedList.mockReset()
  })

  it('liefert null, solange die Projektliste noch lädt', () => {
    mockedList.mockReturnValue(new Promise(() => undefined))
    const { result } = renderHook(() => useProjectStrictness(5))
    expect(result.current).toBeNull()
  })

  it('liefert die Strenge des passenden Projekts, nicht die eines anderen in der Liste', async () => {
    mockedList.mockResolvedValue([projekt(3, 'light'), projekt(5, 'strict'), projekt(7, 'normal')])
    const { result } = renderHook(() => useProjectStrictness(5))
    await waitFor(() => expect(result.current).toBe('strict'))
  })

  it('liefert null für ein Projekt ohne gemeldete Strenge', async () => {
    mockedList.mockResolvedValue([projekt(5, undefined)])
    const { result } = renderHook(() => useProjectStrictness(5))
    await waitFor(() => expect(mockedList).toHaveBeenCalled())
    await antwortVerarbeitet()
    expect(result.current).toBeNull()
  })

  it('liefert null für ein unbekanntes Projekt', async () => {
    mockedList.mockResolvedValue([projekt(5, 'strict')])
    const { result } = renderHook(() => useProjectStrictness(99))
    await waitFor(() => expect(mockedList).toHaveBeenCalled())
    await antwortVerarbeitet()
    expect(result.current).toBeNull()
  })

  it('lässt die Marke weg, wenn das Laden scheitert', async () => {
    mockedList.mockRejectedValue(new Error('kaputt'))
    const { result } = renderHook(() => useProjectStrictness(5))
    await waitFor(() => expect(mockedList).toHaveBeenCalled())
    await antwortVerarbeitet()
    expect(result.current).toBeNull()
  })

  it('ruft keine API auf, wenn keine Projekt-ID vorliegt', () => {
    const { result } = renderHook(() => useProjectStrictness(null))
    expect(mockedList).not.toHaveBeenCalled()
    expect(result.current).toBeNull()
  })

  it('lädt beim Wechsel der Projekt-ID die Strenge des neuen Projekts', async () => {
    mockedList.mockResolvedValue([projekt(5, 'strict'), projekt(6, 'light')])
    const { result, rerender } = renderHook(({ id }) => useProjectStrictness(id), {
      initialProps: { id: 5 as number | null },
    })
    await waitFor(() => expect(result.current).toBe('strict'))
    rerender({ id: 6 })
    await waitFor(() => expect(result.current).toBe('light'))
  })

  it('setzt die Strenge zurück, wenn die Projekt-ID wegfällt', async () => {
    mockedList.mockResolvedValue([projekt(5, 'strict')])
    const { result, rerender } = renderHook(({ id }) => useProjectStrictness(id), {
      initialProps: { id: 5 as number | null },
    })
    await waitFor(() => expect(result.current).toBe('strict'))
    rerender({ id: null })
    expect(result.current).toBeNull()
  })

  it('übernimmt keine verspätete Antwort für eine nicht mehr gültige Projekt-ID', async () => {
    const alt = verzoegert()
    mockedList.mockReturnValueOnce(alt.antwort)
    const { result, rerender } = renderHook(({ id }) => useProjectStrictness(id), {
      initialProps: { id: 5 as number | null },
    })
    rerender({ id: null })
    await act(async () => {
      alt.aufloesen([projekt(5, 'strict')])
      await alt.antwort
    })
    expect(result.current).toBeNull()
  })
})
