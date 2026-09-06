import { describe, expect, it } from 'vitest'
import type { BoardColumn } from '../api/boards'
import {
  canonicalColumnKey,
  isDoneColumn,
  neighbourColumns,
  otherCanonicalColumns,
} from './columnMeta'

describe('isDoneColumn', () => {
  it.each([
    ['Done', true],
    ['done', true],
    ['Erledigt/DONE', true],
    ['In Progress', false],
    ['', false],
  ])('erkennt %s als done=%s', (name, expected) => {
    expect(isDoneColumn(name)).toBe(expected)
  })
})

describe('neighbourColumns', () => {
  const col = (id: number, position: number): BoardColumn => ({
    id,
    name: `Spalte ${id}`,
    position,
    wipLimit: null,
  })
  const columns = [col(10, 0), col(20, 1), col(30, 2)]

  it('liefert für eine mittlere Spalte beide Nachbarn', () => {
    expect(neighbourColumns(columns, 20)).toEqual({ left: columns[0], right: columns[2] })
  })

  it('liefert für die erste Spalte keinen linken Nachbarn', () => {
    expect(neighbourColumns(columns, 10)).toEqual({ left: null, right: columns[1] })
  })

  it('liefert für die letzte Spalte keinen rechten Nachbarn', () => {
    expect(neighbourColumns(columns, 30)).toEqual({ left: columns[1], right: null })
  })

  it('liefert bei einer einzigen Spalte gar keinen Nachbarn', () => {
    expect(neighbourColumns([columns[0]], 10)).toEqual({ left: null, right: null })
  })

  it('liefert für eine unbekannte Spalte keinen Nachbarn', () => {
    expect(neighbourColumns(columns, 999)).toEqual({ left: null, right: null })
  })
})

describe('canonicalColumnKey', () => {
  it.each([
    ['Backlog', 'BACKLOG'],
    ['Ready', 'READY'],
    ['In Progress', 'IN_PROGRESS'],
    ['In Review', 'IN_REVIEW'],
    ['Done', 'DONE'],
    ['  done  ', 'DONE'],
    ['In-Progress', 'IN_PROGRESS'],
  ])('bildet %s auf %s ab', (name, expected) => {
    expect(canonicalColumnKey(name)).toBe(expected)
  })

  it.each([[''], ['Unbekannt'], ['Todo'], ['123'], ['QA']])(
    'liefert für %s undefined',
    (name) => {
      expect(canonicalColumnKey(name)).toBeUndefined()
    },
  )
})

describe('otherCanonicalColumns', () => {
  const col = (id: number, name: string, position: number): BoardColumn => ({
    id,
    name,
    position,
    wipLimit: null,
  })

  it('schließt die aktuelle Spalte aus', () => {
    const columns = [col(1, 'Backlog', 0), col(2, 'Ready', 1), col(3, 'Done', 2)]
    expect(otherCanonicalColumns(columns, 2).map((c) => c.id)).toEqual([1, 3])
  })

  it('schließt nicht-kanonische Spalten aus', () => {
    const columns = [col(1, 'Backlog', 0), col(2, 'QA', 1), col(3, 'Done', 2)]
    expect(otherCanonicalColumns(columns, 1).map((c) => c.name)).toEqual(['Done'])
  })

  it('sortiert unabhängig von der Eingabereihenfolge nach position', () => {
    const columns = [col(3, 'Done', 2), col(1, 'Backlog', 0), col(2, 'Ready', 1)]
    expect(otherCanonicalColumns(columns, 99).map((c) => c.name)).toEqual([
      'Backlog',
      'Ready',
      'Done',
    ])
  })

  it('liefert auf einem Board ohne In Review die übrigen kanonischen Spalten in Positionsreihenfolge', () => {
    const columns = [
      col(1, 'Backlog', 0),
      col(2, 'Ready', 1),
      col(3, 'In Progress', 2),
      col(4, 'Done', 3),
    ]
    expect(otherCanonicalColumns(columns, 1).map((c) => c.name)).toEqual([
      'Ready',
      'In Progress',
      'Done',
    ])
  })

  it('liefert mehrere Spalten mit demselben kanonischen Zustand alle', () => {
    const columns = [col(1, 'Backlog', 0), col(2, 'Done', 1), col(3, 'done!', 2)]
    expect(otherCanonicalColumns(columns, 1).map((c) => c.id)).toEqual([2, 3])
  })

  it('liefert bei unbekannter currentColumnId alle kanonischen Spalten', () => {
    const columns = [col(1, 'Backlog', 0), col(2, 'Ready', 1)]
    expect(otherCanonicalColumns(columns, 999).map((c) => c.id)).toEqual([1, 2])
  })

  it('liefert bei nicht-kanonischer aktueller Spalte alle kanonischen Spalten', () => {
    const columns = [col(1, 'QA', 0), col(2, 'Backlog', 1), col(3, 'Ready', 2)]
    expect(otherCanonicalColumns(columns, 1).map((c) => c.id)).toEqual([2, 3])
  })

  it('lässt das übergebene columns-Array unverändert', () => {
    const columns = [col(3, 'Done', 2), col(1, 'Backlog', 0), col(2, 'Ready', 1)]
    otherCanonicalColumns(columns, 1)
    expect(columns.map((c) => c.id)).toEqual([3, 1, 2])
  })
})
