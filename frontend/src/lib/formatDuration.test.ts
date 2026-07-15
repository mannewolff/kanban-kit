import { describe, expect, it } from 'vitest'
import { formatDuration } from './formatDuration'

describe('formatDuration', () => {
  it('gibt „n. v." für null zurück', () => {
    expect(formatDuration(null)).toBe('n. v.')
  })

  it('zeigt Sekunden unter einer Minute', () => {
    expect(formatDuration(45)).toBe('45 s')
  })

  it('zeigt Minuten unter einer Stunde', () => {
    expect(formatDuration(150)).toBe('2 Min')
  })

  it('zeigt Stunden und Minuten unter einem Tag', () => {
    expect(formatDuration(3 * 3600 + 25 * 60)).toBe('3 Std 25 Min')
  })

  it('zeigt Tage und Stunden ab einem Tag', () => {
    expect(formatDuration(2 * 86_400 + 5 * 3600)).toBe('2 T 5 Std')
  })

  it('rundet Sekunden-Bruchteile', () => {
    expect(formatDuration(44.6)).toBe('45 s')
  })
})
