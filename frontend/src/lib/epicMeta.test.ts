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
    expect(epicColor(7)).toMatch(/^var\(--mb-palette-epic-[0-3]-hue\)$/)
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
    expect(epicTint(7)).toMatch(/^var\(--mb-palette-epic-[0-3]-tint\)$/)
  })
})

describe('EPIC_FARBWERTE', () => {
  it('führt in beiden Erscheinungsbildern die vier Schild-Töne des Entwurfs mit Farbton und Tint', () => {
    for (const plaetze of [EPIC_FARBWERTE.light, EPIC_FARBWERTE.dark]) {
      expect(plaetze).toHaveLength(4)
      for (const platz of plaetze) {
        expect(platz.hue).toMatch(/^#[0-9A-F]{6}$/)
        expect(platz.tint).toMatch(/^rgba\(\d+,\d+,\d+,0\.\d+\)$/)
      }
    }
  })

  it('folgt der Reihenfolge des Entwurfs: Kupfer, Stahl, Grün, Bernstein (Z. 787–790)', () => {
    // Hell nachgedunkelt, damit das Kürzel auf seiner Tönung 4,5:1 hält (theme.test.ts rechnet nach).
    expect(EPIC_FARBWERTE.light.map((p) => p.hue)).toEqual(['#935327', '#2A63B3', '#25713E', '#835C10'])
    expect(EPIC_FARBWERTE.dark.map((p) => p.hue)).toEqual(['#D08A52', '#629BF1', '#46C46F', '#E0AE49'])
  })

  it('bildet den Tint wie das Schild des Entwurfs: der Farbton zu 13 %', () => {
    expect(EPIC_FARBWERTE.light[0].tint).toBe('rgba(147,83,39,0.13)')
    expect(EPIC_FARBWERTE.dark[0].tint).toBe('rgba(208,138,82,0.13)')
  })
})
