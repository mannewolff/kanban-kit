import { describe, expect, it } from 'vitest'
import * as statusColorsModule from './statusColors'
import { ARCHIVED_STATUS_COLOR, STATUS_FARBWERTE, statusColors } from './statusColors'

describe('statusColors', () => {
  // Seit #952 liefert das Modul Variablen-Verweise statt Hexwerten: Die Signatur bleibt
  // „Zeichenkette rein, Zeichenkette raus", und das Erscheinungsbild entscheidet die Media-Query.
  // Die Werte dahinter prüft `theme.test.ts` je Erscheinungsbild.
  it('leitet je Spaltennamen den Verweis auf das passende Farbset ab', () => {
    expect(statusColors('Backlog').dot).toBe('var(--mb-palette-status-backlog-dot)')
    expect(statusColors('Ready').dot).toBe('var(--mb-palette-status-ready-dot)')
    expect(statusColors('In Progress').dot).toBe('var(--mb-palette-status-progress-dot)')
    expect(statusColors('In Review').dot).toBe('var(--mb-palette-status-review-dot)')
    expect(statusColors('Done').dot).toBe('var(--mb-palette-status-done-dot)')
  })

  it('liefert Fläche, Text und Punkt desselben Sets', () => {
    expect(statusColors('Done')).toEqual({
      bg: 'var(--mb-palette-status-done-bg)',
      text: 'var(--mb-palette-status-done-text)',
      dot: 'var(--mb-palette-status-done-dot)',
    })
  })

  it('fällt für unbekannte Spalten auf Neutral zurück', () => {
    expect(statusColors('Irgendwas').dot).toBe('var(--mb-palette-status-neutral-dot)')
  })

  it('führt archivierte Karten in ihrem eigenen Set', () => {
    expect(ARCHIVED_STATUS_COLOR.dot).toBe('var(--mb-palette-status-archived-dot)')
  })

  it('setzt die Punkte hell auf die Melder des Entwurfs', () => {
    expect(STATUS_FARBWERTE.light.done.dot).toBe('#2E8B4C')
    expect(STATUS_FARBWERTE.light.review.dot).toBe('#A17113')
    expect(STATUS_FARBWERTE.light.progress.dot).toBe('#2F6FC9')
    for (const set of ['ready', 'backlog', 'neutral', 'archived'] as const) {
      expect(STATUS_FARBWERTE.light[set].dot).toBe('#757B86')
    }
  })

  it('führt in beiden Erscheinungsbildern dieselben Sets mit Hexwerten', () => {
    expect(Object.keys(STATUS_FARBWERTE.dark)).toEqual(Object.keys(STATUS_FARBWERTE.light))
    for (const erscheinungsbild of [STATUS_FARBWERTE.light, STATUS_FARBWERTE.dark]) {
      for (const set of Object.values(erscheinungsbild)) {
        expect(Object.values(set).every((wert) => /^#[0-9A-F]{6}$/.test(wert))).toBe(true)
      }
    }
  })

  // Regressionsschutz gegen die Rückkehr der zweiten Farbquelle (#648, E1/E2): Das Modul trägt
  // ausschließlich Status-Farben. Jedes weitere Token gehört ins Theme, sonst laufen zwei
  // Farbquellen auseinander, ohne dass ein Test es merkt.
  it('exportiert keine Nicht-Status-Tokens', () => {
    expect(Object.keys(statusColorsModule).sort()).toEqual(['ARCHIVED_STATUS_COLOR', 'STATUS_FARBWERTE', 'statusColors'])
  })
})
