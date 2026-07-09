import { describe, expect, it } from 'vitest'
import { statusColors } from './statusColors'

describe('statusColors', () => {
  it('leitet je Spaltennamen das passende Farbset ab', () => {
    expect(statusColors('Ready').dot).toBe('#0075ca')
    expect(statusColors('In Progress').dot).toBe('#e4b400')
    expect(statusColors('In Review').dot).toBe('#d93f0b')
    expect(statusColors('Done').dot).toBe('#0e8a16')
  })

  it('fällt für unbekannte/Backlog-Spalten auf Neutral zurück', () => {
    expect(statusColors('Backlog').dot).toBe('#6b7280')
    expect(statusColors('Irgendwas').dot).toBe('#6b7280')
  })
})
