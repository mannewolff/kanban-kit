import { describe, expect, it } from 'vitest'
import { farbmodusAus } from './farbmodus'

describe('farbmodusAus (#1911)', () => {
  it('bildet jede Wahl am Konto auf den Modus von MUI ab', () => {
    expect(farbmodusAus('SYSTEM')).toBe('system')
    expect(farbmodusAus('LIGHT')).toBe('light')
    expect(farbmodusAus('DARK')).toBe('dark')
  })

  it('folgt ohne Konto dem System', () => {
    expect(farbmodusAus(undefined)).toBe('system')
  })
})
