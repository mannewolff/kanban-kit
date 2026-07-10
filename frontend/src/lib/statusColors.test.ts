import { describe, expect, it } from 'vitest'
import { statusColors } from './statusColors'

describe('statusColors', () => {
  it('leitet je Spaltennamen das passende (entsättigte) Farbset ab', () => {
    expect(statusColors('Backlog').dot).toBe('#5BABB5')
    expect(statusColors('Ready').dot).toBe('#2F8C97')
    expect(statusColors('In Progress').dot).toBe('#C99A2E')
    expect(statusColors('In Review').dot).toBe('#C46B4E')
    expect(statusColors('Done').dot).toBe('#2E9E7A')
  })

  it('fällt für unbekannte Spalten auf Neutral zurück', () => {
    expect(statusColors('Irgendwas').dot).toBe('#8FA6AB')
  })
})
