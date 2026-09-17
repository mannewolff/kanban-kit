import { describe, expect, it } from 'vitest'
import { variablenAufloesen } from './variablenAufloesen'

const WERTE = { '--mb-a': '#AAAAAA', '--mb-b': 'rgba(1,2,3,0.5)' }

describe('variablenAufloesen', () => {
  it('ersetzt einen Verweis durch den hinterlegten Wert', () => {
    expect(variablenAufloesen('var(--mb-a)', WERTE)).toBe('#AAAAAA')
  })

  it('ersetzt ihn auch mit Rückfallwert und bevorzugt den hinterlegten Wert', () => {
    expect(variablenAufloesen('var(--mb-a, #000000)', WERTE)).toBe('#AAAAAA')
  })

  it('ersetzt Verweise mitten in einer Zeichenkette, auch mehrere', () => {
    expect(variablenAufloesen('1px solid var(--mb-a), 0 0 0 var(--mb-b)', WERTE)).toBe(
      '1px solid #AAAAAA, 0 0 0 rgba(1,2,3,0.5)',
    )
  })

  it('liest einen Rückfallwert mit eigenen Klammern vollständig', () => {
    // MUI hängt an jede Variable ihren hellen Wert an; Schatten tragen darin `rgba(…)`.
    expect(variablenAufloesen('var(--mb-x, 0 1px rgba(4,5,6,0.1)) x', WERTE)).toBe('0 1px rgba(4,5,6,0.1) x')
  })

  it('nimmt ohne hinterlegten Wert den Rückfallwert', () => {
    expect(variablenAufloesen('var(--mb-x, #123456)', WERTE)).toBe('#123456')
  })

  it('lässt einen Verweis ohne hinterlegten Wert und ohne Rückfall stehen', () => {
    expect(variablenAufloesen('var(--mb-x)', WERTE)).toBe('var(--mb-x)')
  })

  it('lässt Zeichenketten ohne Verweis unverändert', () => {
    expect(variablenAufloesen('2px solid red', WERTE)).toBe('2px solid red')
  })

  it('löst verschachtelte Objekte und Listen auf, ohne das Original zu verändern', () => {
    const original = { a: { b: 'var(--mb-a)', c: [1, 'var(--mb-b)'] }, d: 4, e: null, f: true }
    const aufgeloest = variablenAufloesen(original, WERTE)

    expect(aufgeloest).toEqual({ a: { b: '#AAAAAA', c: [1, 'rgba(1,2,3,0.5)'] }, d: 4, e: null, f: true })
    expect(original.a.b).toBe('var(--mb-a)')
  })

  it('lässt Funktionen unverändert durch', () => {
    const funktion = () => 'var(--mb-a)'
    expect(variablenAufloesen({ root: funktion }, WERTE).root).toBe(funktion)
  })

  it('verträgt undefined', () => {
    expect(variablenAufloesen(undefined, WERTE)).toBeUndefined()
  })
})
