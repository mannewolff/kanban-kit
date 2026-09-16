import { describe, expect, it } from 'vitest'
import { EPIC_FARBWERTE, epicColor, epicShortcode, epicTint } from './epicMeta'

describe('epicShortcode', () => {
  it('nutzt ein explizit gesetztes Kürzel', () => {
    expect(epicShortcode('Irgendein Titel', 'AUTH')).toBe('AUTH')
  })

  it('leitet Initialen aus dem Titel ab (max. 3)', () => {
    expect(epicShortcode('Zehn Tage Workshop IT')).toBe('ZTW')
    expect(epicShortcode('   ', null)).toBe('VORH')
  })
})

describe('epicColor', () => {
  it('ist stabil pro Epic-ID', () => {
    expect(epicColor(7)).toBe(epicColor(7))
  })

  // Seit #952 ein Variablen-Verweis: Die Farbe schaltet mit dem Erscheinungsbild, die Signatur
  // bleibt dieselbe. Welcher Wert dahinter liegt, prüft `theme.test.ts`.
  it('liefert einen Verweis auf den Farbton eines Palettenplatzes', () => {
    expect(epicColor(7)).toMatch(/^var\(--mb-palette-epic-[0-7]-hue\)$/)
  })

  it('verteilt verschiedene Vorhaben auf verschiedene Plätze', () => {
    const plaetze = new Set(Array.from({ length: 40 }, (_, i) => epicColor(i)))
    expect(plaetze.size).toBe(EPIC_FARBWERTE.light.length)
  })
})

describe('epicTint', () => {
  // Bis #952 rechnete `EpicBadge` die getönte Fläche als `${hue}22` — Hexwert plus Alpha-Suffix.
  // Auf einem Variablen-Verweis ergibt das ungültiges CSS, und die Fläche verschwindet still.
  it('liefert für dieselbe ID den Tint-Verweis desselben Palettenplatzes', () => {
    for (const id of [1, 7, 42, 1234]) {
      expect(epicTint(id)).toBe(epicColor(id).replace('-hue)', '-tint)'))
    }
  })

  it('hängt kein Alpha-Suffix an einen Farbwert', () => {
    expect(epicTint(7)).toMatch(/^var\(--mb-palette-epic-[0-7]-tint\)$/)
  })
})

describe('EPIC_FARBWERTE', () => {
  it('führt in beiden Erscheinungsbildern acht Plätze mit Farbton und Tint', () => {
    for (const plaetze of [EPIC_FARBWERTE.light, EPIC_FARBWERTE.dark]) {
      expect(plaetze).toHaveLength(8)
      for (const platz of plaetze) {
        expect(platz.hue).toMatch(/^#[0-9A-F]{6}$/)
        expect(platz.tint).toMatch(/^rgba\(\d+,\d+,\d+,0\.\d+\)$/)
      }
    }
  })

  it('hält die hellen Farbtöne des Bestands unverändert', () => {
    expect(EPIC_FARBWERTE.light.map((p) => p.hue)).toEqual([
      '#534AB7', '#1D9E75', '#D4537E', '#185FA5',
      '#BA7517', '#993C1D', '#0F6E56', '#0C447C',
    ])
  })

  it('bildet den hellen Tint wie bisher: der Farbton mit Deckkraft 0x22', () => {
    // `#534AB722` hieß Deckkraft 34/255. Als rgba bleibt das Aussehen im hellen Erscheinungsbild gleich.
    expect(EPIC_FARBWERTE.light[0].tint).toBe('rgba(83,74,183,0.133)')
  })
})
