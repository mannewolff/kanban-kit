import { describe, expect, it } from 'vitest'
import { MAX_TEXT_LENGTH, isTooLong, tooLongMessage } from './textLimits'

describe('textLimits', () => {
  it('meldet erst oberhalb der Grenze zu lang', () => {
    expect(isTooLong('a'.repeat(MAX_TEXT_LENGTH))).toBe(false)
    expect(isTooLong('a'.repeat(MAX_TEXT_LENGTH + 1))).toBe(true)
  })

  it('zählt ein Nicht-BMP-Zeichen als zwei Einheiten — wie @Size im Backend', () => {
    // Die Zählweise ist Vertrag: Java und JavaScript zählen UTF-16-Codeeinheiten, PostgreSQL nicht.
    expect(isTooLong('😀'.repeat(MAX_TEXT_LENGTH / 2))).toBe(false)
    expect(isTooLong('😀'.repeat(MAX_TEXT_LENGTH / 2) + '😀')).toBe(true)
  })

  it('formatiert den Fehlertext als Ist/Grenze mit Tausenderpunkten', () => {
    expect(tooLongMessage(60_000)).toBe('60.000 / 50.000 Zeichen')
  })
})
