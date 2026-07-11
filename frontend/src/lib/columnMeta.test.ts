import { describe, expect, it } from 'vitest'
import { isDoneColumn } from './columnMeta'

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
