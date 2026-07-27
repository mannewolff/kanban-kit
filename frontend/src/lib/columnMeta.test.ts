import { describe, expect, it } from 'vitest'
import type { BoardColumn } from '../api/boards'
import { isDoneColumn, neighbourColumns } from './columnMeta'

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
