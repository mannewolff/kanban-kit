import { describe, expect, it } from 'vitest'
import { kontrast, leuchtdichte } from './kontrast'

describe('leuchtdichte', () => {
  it('liegt für Schwarz bei 0 und für Weiß bei 1', () => {
    expect(leuchtdichte('#000000')).toBe(0)
    expect(leuchtdichte('#FFFFFF')).toBe(1)
  })

  it('nimmt Groß- und Kleinschreibung gleich an', () => {
    expect(leuchtdichte('#2f8c97')).toBe(leuchtdichte('#2F8C97'))
  })

  it('liest die Kurzform #abc als #aabbcc', () => {
    expect(leuchtdichte('#abc')).toBe(leuchtdichte('#AABBCC'))
  })

  it.each(['', '2F8C97', '#2F8C9', '#2F8C977', '#GGGGGG', '#abcd', 'rgb(0,0,0)', 'var(--x)'])(
    'weist den unbrauchbaren Wert %j ab, statt still zu rechnen',
    (wert) => {
      expect(() => leuchtdichte(wert)).toThrow(/Hexwert/)
    },
  )
})

describe('kontrast', () => {
  it('ergibt für Schwarz gegen Weiß 21:1', () => {
    expect(kontrast('#000000', '#FFFFFF')).toBe(21)
  })

  it('ergibt für eine Farbe gegen sich selbst 1:1', () => {
    expect(kontrast('#2F8C97', '#2F8C97')).toBe(1)
  })

  it('hängt nicht von der Reihenfolge der Argumente ab', () => {
    expect(kontrast('#243539', '#5BABB5')).toBe(kontrast('#5BABB5', '#243539'))
  })

  it('weist einen unbrauchbaren Wert auf jeder Seite ab', () => {
    expect(() => kontrast('#zzz', '#FFFFFF')).toThrow(/Hexwert/)
    expect(() => kontrast('#FFFFFF', 'weiss')).toThrow(/Hexwert/)
  })

  // Grenzfälle aus dem Bestand: je ein Paar knapp über und knapp unter der Schwelle, damit ein
  // Rundungs- oder Formelfehler an genau der Stelle auffällt, an der er ein Urteil kippen würde.
  describe('Schwelle 4,5:1 für Fließtext', () => {
    it('hält sie mit der gedämpften Statusschrift auf Weiß (statusColors, rund 4,59)', () => {
      expect(kontrast('#5F7A7F', '#FFFFFF')).toBeGreaterThanOrEqual(4.5)
      expect(kontrast('#5F7A7F', '#FFFFFF')).toBeLessThan(4.6)
    })

    it('verfehlt sie mit dem dritten Nachtlauf-Grau auf dem dunklen Grund (nachtlaufDesign, rund 4,49)', () => {
      expect(kontrast('#767D89', '#0F1319')).toBeLessThan(4.5)
      expect(kontrast('#767D89', '#0F1319')).toBeGreaterThan(4.4)
    })
  })

  describe('Schwelle 3:1 für große Schrift und Flächen', () => {
    it('hält sie mit dem Status-Grün auf Eis (rund 3,02)', () => {
      expect(kontrast('#2E9E7A', '#EDF5F6')).toBeGreaterThanOrEqual(3)
      expect(kontrast('#2E9E7A', '#EDF5F6')).toBeLessThan(3.1)
    })

    it('verfehlt sie mit demselben Grün auf dem hellen Nachtlauf-Grau (rund 2,90)', () => {
      expect(kontrast('#2E9E7A', '#EDEFF3')).toBeLessThan(3)
      expect(kontrast('#2E9E7A', '#EDEFF3')).toBeGreaterThan(2.8)
    })
  })
})
