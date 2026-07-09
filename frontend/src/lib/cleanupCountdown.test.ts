import { describe, expect, it } from 'vitest'
import { cleanupCountdownLabel, cleanupDaysRemaining } from './cleanupCountdown'

const DAY = 86_400_000
const now = Date.parse('2026-07-09T12:00:00Z')

describe('cleanupDaysRemaining', () => {
  it('rechnet die verbleibenden Tage bis zur Archivierung', () => {
    const moved = new Date(now - 2 * DAY).toISOString()
    expect(cleanupDaysRemaining(moved, 30, now)).toBe(28)
  })

  it('gibt nie negative Werte zurück', () => {
    const moved = new Date(now - 40 * DAY).toISOString()
    expect(cleanupDaysRemaining(moved, 30, now)).toBe(0)
  })
})

describe('cleanupCountdownLabel', () => {
  it('formatiert heute/morgen/Tage', () => {
    expect(cleanupCountdownLabel(0)).toBe('wird heute archiviert')
    expect(cleanupCountdownLabel(1)).toBe('wird morgen archiviert')
    expect(cleanupCountdownLabel(5)).toBe('wird in 5 Tagen archiviert')
  })
})
