import { describe, expect, it } from 'vitest'
import { epicColor, epicShortcode } from './epicMeta'

describe('epicShortcode', () => {
  it('nutzt ein explizit gesetztes Kürzel', () => {
    expect(epicShortcode('Irgendein Titel', 'AUTH')).toBe('AUTH')
  })

  it('leitet Initialen aus dem Titel ab (max. 3)', () => {
    expect(epicShortcode('Zehn Tage Workshop IT')).toBe('ZTW')
    expect(epicShortcode('   ', null)).toBe('EPIC')
  })
})

describe('epicColor', () => {
  it('ist stabil pro Epic-ID', () => {
    expect(epicColor(7)).toBe(epicColor(7))
  })
})
