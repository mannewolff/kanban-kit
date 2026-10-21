import { describe, expect, it } from 'vitest'
import { laufSprungziel, zeitleiste, type Zeitleistenlauf } from './laufZeitleiste'

const MIN = 60_000
const JETZT = Date.parse('2026-10-21T03:00:00Z')

const lauf = (teil: Partial<Zeitleistenlauf> & Pick<Zeitleistenlauf, 'startedAt'>): Zeitleistenlauf => ({
  nummer: undefined,
  durationMs: 0,
  laeuft: true,
  melder: 'stahl',
  ...teil,
})

describe('zeitleiste (Plan #1882 E2)', () => {
  it('zeigt zwei gleichzeitige Runs vom frühesten Start bis jetzt mit ihrer Stufe', () => {
    const laeufe = [
      lauf({ nummer: 12, startedAt: '2026-10-21T01:30:00Z' }),
      lauf({ nummer: 11, startedAt: '2026-10-21T01:00:00Z' }),
    ]
    const stufen = new Map([
      [11, 'REVIEW'],
      [12, 'UMSETZUNG'],
    ])
    expect(zeitleiste(laeufe, stufen, JETZT)).toEqual({
      vonMs: JETZT - 120 * MIN,
      bisMs: JETZT,
      balken: [
        {
          nummer: 11,
          startedAt: '2026-10-21T01:00:00Z',
          anfangMs: JETZT - 120 * MIN,
          endeMs: null,
          melder: 'stahl',
          stufe: 'REVIEW',
        },
        {
          nummer: 12,
          startedAt: '2026-10-21T01:30:00Z',
          anfangMs: JETZT - 90 * MIN,
          endeMs: null,
          melder: 'stahl',
          stufe: 'UMSETZUNG',
        },
      ],
    })
  })

  it('reicht mindestens 60 Minuten zurück', () => {
    const z = zeitleiste([lauf({ nummer: 1, startedAt: '2026-10-21T02:50:00Z' })], new Map(), JETZT)
    expect(z?.vonMs).toBe(JETZT - 60 * MIN)
  })

  it('behält einen im Fenster beendeten Run mit seinem Melder und schneidet ihn am Fenster ab', () => {
    const laeufe = [
      lauf({ nummer: 5, startedAt: '2026-10-21T02:00:00Z' }),
      lauf({ nummer: 4, startedAt: '2026-10-21T01:30:00Z', laeuft: false, durationMs: 45 * MIN, melder: 'gruen' }),
      lauf({ nummer: 3, startedAt: '2026-10-21T00:00:00Z', laeuft: false, durationMs: 30 * MIN, melder: 'zinnob' }),
    ]
    const z = zeitleiste(laeufe, new Map([[4, 'PLAN']]), JETZT)
    expect(z?.balken).toEqual([
      {
        nummer: 4,
        startedAt: '2026-10-21T01:30:00Z',
        anfangMs: JETZT - 60 * MIN,
        endeMs: JETZT - 45 * MIN,
        melder: 'gruen',
        stufe: null,
      },
      {
        nummer: 5,
        startedAt: '2026-10-21T02:00:00Z',
        anfangMs: JETZT - 60 * MIN,
        endeMs: null,
        melder: 'stahl',
        stufe: null,
      },
    ])
  })

  it('führt einen Run ohne Stufe und einen ohne Nummer mit `null`-Stufe', () => {
    const z = zeitleiste(
      [lauf({ nummer: 7, startedAt: '2026-10-21T02:30:00Z' }), lauf({ startedAt: '2026-10-21T02:40:00Z' })],
      new Map([[8, 'PLAN']]),
      JETZT,
    )
    expect(z?.balken.map((b) => [b.nummer, b.stufe])).toEqual([
      [7, null],
      [undefined, null],
    ])
  })

  it('liefert nichts, solange kein Run läuft', () => {
    expect(
      zeitleiste([lauf({ nummer: 1, startedAt: '2026-10-21T02:30:00Z', laeuft: false, durationMs: MIN })], new Map(), JETZT),
    ).toBeNull()
    expect(zeitleiste([], new Map(), JETZT)).toBeNull()
  })
})

describe('laufSprungziel (Issue #1887)', () => {
  it('bildet das Sprungziel einer Platte aus ihrem Start', () => {
    expect(laufSprungziel('2026-10-21T01:00:00Z')).toBe('lauf-2026-10-21T01:00:00Z')
  })
})
