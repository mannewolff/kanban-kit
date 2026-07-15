import { describe, expect, it } from 'vitest'
import { dueInputToIso, formatDueDate, isOverdue } from './dueDate'

const NOW = Date.parse('2026-07-14T12:00:00Z')

describe('isOverdue', () => {
  it('ist true bei Datum in der Vergangenheit und nicht Done', () => {
    expect(isOverdue('2026-07-01T00:00:00Z', false, NOW)).toBe(true)
  })
  it('ist false bei Done-Spalte trotz Vergangenheit', () => {
    expect(isOverdue('2026-07-01T00:00:00Z', true, NOW)).toBe(false)
  })
  it('ist false ohne Datum', () => {
    expect(isOverdue(null, false, NOW)).toBe(false)
  })
  it('ist false bei Datum in der Zukunft', () => {
    expect(isOverdue('2026-08-01T00:00:00Z', false, NOW)).toBe(false)
  })
})

describe('formatDueDate', () => {
  it('formatiert als deutsches Datum', () => {
    expect(formatDueDate('2026-08-01T00:00:00Z')).toBe('1.8.2026')
  })
})

describe('dueInputToIso', () => {
  it('wandelt YYYY-MM-DD in Mitternacht-UTC-ISO', () => {
    expect(dueInputToIso('2026-08-01')).toBe('2026-08-01T00:00:00Z')
  })
  it('liefert null bei leerer Eingabe', () => {
    expect(dueInputToIso('')).toBeNull()
  })
})
