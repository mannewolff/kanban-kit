import { describe, expect, it } from 'vitest'
import { formatierer } from './format'

// Mittags in Berlin — so bleibt der Tag in jeder Zone der Tests derselbe.
const OKT_13 = '2026-10-13T12:30:00Z'

describe('formatierer – Deutsch', () => {
  const f = formatierer('de')

  it('nennt das Gebietsschema de-DE', () => {
    expect(f.gebietsschema).toBe('de-DE')
  })

  it('schreibt ein Datum als 13.10.2026', () => {
    expect(f.datum(OKT_13)).toBe('13.10.2026')
  })

  it('nimmt Datum, Zeichenkette und Millisekunden gleich', () => {
    expect(f.datum(new Date(OKT_13))).toBe('13.10.2026')
    expect(f.datum(Date.parse(OKT_13))).toBe('13.10.2026')
  })

  it('schreibt Datum mit Uhrzeit im 24-Stunden-Format', () => {
    expect(f.datumUhrzeit(OKT_13)).toBe('13.10.2026, 14:30:00')
  })

  it('schreibt Dezimalzahlen mit Komma und Tausender mit Punkt', () => {
    expect(f.zahl(1234.5)).toBe('1.234,5')
  })

  it('reicht Optionen an die Zahl durch', () => {
    expect(f.zahl(2, { minimumFractionDigits: 1 })).toBe('2,0')
  })

  it('schreibt Geld mit Komma und Währungszeichen hinten, Vorgabe USD', () => {
    expect(f.geld(1.5)).toBe('1,50 $')
    expect(f.geld(1.5, 'EUR')).toBe('1,50 €')
  })

  it.each([
    [null, 'n. v.'],
    [42.4, '42 s'],
    [59.6, '1 Min'],
    [125, '2 Min'],
    [3 * 3600 + 5 * 60, '3 Std 5 Min'],
    [2 * 86_400 + 4 * 3600, '2 T 4 Std'],
  ])('schreibt die Dauer %s als %s', (sekunden, erwartet) => {
    expect(f.dauer(sekunden)).toBe(erwartet)
  })
})

describe('formatierer – Englisch', () => {
  const f = formatierer('en')

  it('nennt das Gebietsschema en-US', () => {
    expect(f.gebietsschema).toBe('en-US')
  })

  it('schreibt ein Datum als Oct 13, 2026', () => {
    expect(f.datum(OKT_13)).toBe('Oct 13, 2026')
  })

  it('schreibt Datum mit Uhrzeit mit AM/PM', () => {
    // Vor „PM“ steht je nach ICU-Fassung ein schmales oder ein normales Leerzeichen.
    expect(f.datumUhrzeit(OKT_13)).toMatch(/^Oct 13, 2026, 2:30:00\sPM$/)
    expect(f.datumUhrzeit('2026-10-13T07:05:00Z')).toMatch(/^Oct 13, 2026, 9:05:00\sAM$/)
  })

  it('schreibt Dezimalzahlen mit Punkt und Tausender mit Komma', () => {
    expect(f.zahl(1234.5)).toBe('1,234.5')
  })

  it('schreibt Geld mit Dollarzeichen vorn', () => {
    expect(f.geld(1.5)).toBe('$1.50')
  })

  it.each([
    [null, 'n/a'],
    [42, '42 s'],
    [125, '2 min'],
    [3 * 3600 + 5 * 60, '3 h 5 min'],
    [2 * 86_400 + 4 * 3600, '2 d 4 h'],
  ])('schreibt die Dauer %s als %s', (sekunden, erwartet) => {
    expect(f.dauer(sekunden)).toBe(erwartet)
  })
})
