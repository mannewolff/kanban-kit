import { describe, expect, it } from 'vitest'
import type { FlowDay } from '../api/dashboard'
import { alterText, FLUSS_BAENDER, flussKopf, flussPfade, flussStapel, flussTagZeilen } from './fluss'

const tag = (
  date: string,
  werte: { backlog: number; ready: number; inProgress: number; inReview: number; done: number } | null,
): FlowDay =>
  werte === null
    ? { date, known: false, backlog: null, ready: null, inProgress: null, inReview: null, done: null }
    : { date, known: true, ...werte }

// Vier Tage, der dritte ohne sicheren Stand: ein Lauf aus zwei Tagen, eine Lücke, ein einzelner Tag.
const TAGE: FlowDay[] = [
  tag('2026-10-12', { backlog: 1, ready: 1, inProgress: 0, inReview: 1, done: 1 }),
  tag('2026-10-13', { backlog: 2, ready: 1, inProgress: 1, inReview: 2, done: 2 }),
  tag('2026-10-14', null),
  tag('2026-10-15', { backlog: 0, ready: 0, inProgress: 0, inReview: 0, done: 4 }),
]

describe('FLUSS_BAENDER', () => {
  it('stapelt von unten Done, In Review, In Progress, Ready, Backlog', () => {
    expect(FLUSS_BAENDER).toEqual(['done', 'inReview', 'inProgress', 'ready', 'backlog'])
  })
})

describe('flussStapel', () => {
  it('legt die Bänder je bekanntem Tag von unten übereinander', () => {
    expect(flussStapel(TAGE)[0]).toEqual({
      done: { unten: 0, oben: 1 },
      inReview: { unten: 1, oben: 2 },
      inProgress: { unten: 2, oben: 2 },
      ready: { unten: 2, oben: 3 },
      backlog: { unten: 3, oben: 4 },
    })
  })

  it('liefert für einen unbekannten Tag null statt Null-Werten', () => {
    const stapel = flussStapel(TAGE)

    expect(stapel).toHaveLength(4)
    expect(stapel[2]).toBeNull()
    expect(stapel[3]?.backlog).toEqual({ unten: 4, oben: 4 })
  })

  it('zählt eine fehlende Zahl an einem bekannten Tag als 0', () => {
    const stapel = flussStapel([{ ...tag('2026-10-15', { backlog: 2, ready: 0, inProgress: 0, inReview: 0, done: 1 }), ready: null }])

    expect(stapel[0]?.ready).toEqual({ unten: 1, oben: 1 })
    expect(stapel[0]?.backlog).toEqual({ unten: 1, oben: 3 })
  })

  it('liefert für eine leere Liste keine Tage', () => {
    expect(flussStapel([])).toEqual([])
  })
})

describe('flussPfade', () => {
  // Breite 200 auf vier Tage: x = 0, 66.67, 133.33, 200; Höchstwert 8 auf Höhe 80: y = 80 − 10·v.
  const pfade = flussPfade(TAGE, 200, 80)

  it('zeichnet je Band einen geschlossenen Pfad über die Tage, oben vor, unten zurück', () => {
    expect(pfade.done).toBe('M0,70 L66.67,60 L66.67,80 L0,80 Z M166.67,40 L200,40 L200,80 L166.67,80 Z')
    expect(pfade.backlog).toBe('M0,40 L66.67,0 L66.67,20 L0,50 Z M166.67,40 L200,40 L200,40 L166.67,40 Z')
  })

  it('unterbricht die Pfade an einem unbekannten Tag, statt ihn als Null zu zeichnen', () => {
    for (const band of FLUSS_BAENDER) {
      expect(pfade[band].match(/M/g)).toHaveLength(2)
      expect(pfade[band]).not.toContain('133.33')
    }
  })

  it('zeichnet einen einzelnen bekannten Tag über die ganze Breite', () => {
    const einzeln = flussPfade([tag('2026-10-15', { backlog: 1, ready: 0, inProgress: 0, inReview: 0, done: 1 })], 100, 50)

    expect(einzeln.done).toBe('M0,25 L100,25 L100,50 L0,50 Z')
    expect(einzeln.backlog).toBe('M0,0 L100,0 L100,25 L0,25 Z')
  })

  it('zeichnet einen Tag mitten zwischen zwei Lücken als Säule um seinen Punkt', () => {
    const mitte = flussPfade(
      [tag('2026-10-13', null), tag('2026-10-14', { backlog: 0, ready: 0, inProgress: 0, inReview: 0, done: 2 }), tag('2026-10-15', null)],
      100,
      40,
    )

    expect(mitte.done).toBe('M25,0 L75,0 L75,40 L25,40 Z')
  })

  it('bleibt bei lauter Nullen auf der Grundlinie, ohne durch null zu teilen', () => {
    const null2 = flussPfade(
      [
        tag('2026-10-14', { backlog: 0, ready: 0, inProgress: 0, inReview: 0, done: 0 }),
        tag('2026-10-15', { backlog: 0, ready: 0, inProgress: 0, inReview: 0, done: 0 }),
      ],
      10,
      20,
    )

    expect(null2.done).toBe('M0,20 L10,20 L10,20 L0,20 Z')
  })

  it('liefert für eine leere Liste oder lauter unbekannte Tage leere Pfade', () => {
    const leer = { done: '', inReview: '', inProgress: '', ready: '', backlog: '' }

    expect(flussPfade([], 100, 50)).toEqual(leer)
    expect(flussPfade([tag('2026-10-15', null)], 100, 50)).toEqual(leer)
  })
})

describe('flussTagZeilen', () => {
  it('nennt die Zahl je Band in der Reihenfolge von oben', () => {
    expect(flussTagZeilen(TAGE[1], 'de')).toEqual(['Backlog: 2', 'Ready: 1', 'In Progress: 1', 'In Review: 2', 'Done: 2'])
    expect(flussTagZeilen(TAGE[0], 'en')).toEqual(['Backlog: 1', 'Ready: 1', 'In Progress: 0', 'In Review: 1', 'Done: 1'])
  })

  it('nennt eine fehlende Zahl an einem bekannten Tag als 0', () => {
    expect(flussTagZeilen({ ...TAGE[3], ready: null }, 'de')).toContain('Ready: 0')
  })

  it('nennt bei unbekanntem Tag „keine Daten“', () => {
    expect(flussTagZeilen(TAGE[2], 'de')).toEqual(['keine Daten'])
    expect(flussTagZeilen(TAGE[2], 'en')).toEqual(['no data'])
  })
})

describe('flussKopf', () => {
  it('nennt den Beginn der sicheren Daten, wenn dataSince gesetzt ist', () => {
    expect(flussKopf('2026-09-01', 'de')).toBe('seit 1.9.2026')
    expect(flussKopf('2026-09-01', 'en')).toBe('since Sep 1, 2026')
  })

  it('nennt ohne dataSince die letzten zwölf Wochen', () => {
    expect(flussKopf(null, 'de')).toBe('letzte 12 Wochen')
    expect(flussKopf(null, 'en')).toBe('last 12 weeks')
  })
})

describe('alterText', () => {
  const STUNDE = 3600
  const TAG = 24 * STUNDE

  it('zeigt unter einem Tag die vollen Stunden', () => {
    expect(alterText(STUNDE - 1, false, 'de')).toBe('unter 1 Std')
    expect(alterText(STUNDE, false, 'de')).toBe('1 Std')
    expect(alterText(TAG - 1, false, 'de')).toBe('23 Std')
    expect(alterText(5 * STUNDE, false, 'en')).toBe('5 h')
    expect(alterText(0, false, 'en')).toBe('under 1 h')
  })

  it('zeigt ab einem Tag die vollen Tage', () => {
    expect(alterText(TAG, false, 'de')).toBe('1 Tag')
    expect(alterText(3 * TAG + 5 * STUNDE, false, 'de')).toBe('3 Tage')
    expect(alterText(TAG, false, 'en')).toBe('1 day')
    expect(alterText(2 * TAG, false, 'en')).toBe('2 days')
  })

  it('sagt bei unbekanntem Eintritt „mindestens ‹n› Tage“', () => {
    expect(alterText(4 * TAG, true, 'de')).toBe('mindestens 4 Tage')
    expect(alterText(4 * TAG, true, 'en')).toBe('at least 4 days')
    expect(alterText(2 * STUNDE, true, 'de')).toBe('mindestens 2 Std')
  })
})
