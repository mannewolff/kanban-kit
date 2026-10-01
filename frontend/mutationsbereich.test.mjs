import { describe, expect, it } from 'vitest'
import { aufgenommene, kandidat, mutateFuer, stufenplanLesen } from './mutationsbereich.mjs'

const plan = {
  ausnahmen: ['src/theme.ts', 'src/test/**'],
  ausschnitte: [
    { name: 'a', muster: ['src/a/**/*.ts'], aufgenommen: '2026-09-28', reihenfolge: 1 },
    { name: 'b', muster: ['src/b.tsx', 'src/c.tsx'], aufgenommen: false, reihenfolge: 3 },
    { name: 'c', muster: ['src/d.tsx'], aufgenommen: false, reihenfolge: 2 },
  ],
}

describe('stufenplanLesen: der Stufenplan von der Platte', () => {
  it('liest `mutationsstufen.json` mit Ausnahmen und Ausschnitten', () => {
    const gelesen = stufenplanLesen()

    expect(Array.isArray(gelesen.ausnahmen)).toBe(true)
    expect(gelesen.ausschnitte.length).toBeGreaterThan(0)
  })
})

describe('aufgenommene: was schon im Pruefbereich steht', () => {
  it('nennt genau die Ausschnitte mit einem Datum in `aufgenommen`', () => {
    expect(aufgenommene(plan).map((ausschnitt) => ausschnitt.name)).toEqual(['a'])
  })

  it('liefert die leere Liste, wenn keiner aufgenommen ist', () => {
    expect(aufgenommene({ ...plan, ausschnitte: [plan.ausschnitte[1]] })).toEqual([])
  })
})

describe('kandidat: der naechste aufzunehmende Ausschnitt', () => {
  it('waehlt die kleinste `reihenfolge` unter den nicht aufgenommenen', () => {
    expect(kandidat(plan)?.name).toBe('c')
  })

  it('ist `undefined`, wenn alle Ausschnitte aufgenommen sind', () => {
    const alleDrin = plan.ausschnitte.map((ausschnitt) => ({
      ...ausschnitt,
      aufgenommen: '2026-09-28',
    }))

    expect(kandidat({ ...plan, ausschnitte: alleDrin })).toBeUndefined()
  })
})

describe('mutateFuer: Muster, Ausschluesse und Ausnahmen in einer Liste', () => {
  it('reiht die Muster der genannten Ausschnitte in der Reihenfolge des Plans', () => {
    expect(mutateFuer(['b', 'a'], plan).slice(0, 3)).toEqual([
      'src/a/**/*.ts',
      'src/b.tsx',
      'src/c.tsx',
    ])
  })

  it('haengt die Testausschluesse und jede Ausnahme mit `!` davor an', () => {
    expect(mutateFuer(['a'], plan)).toEqual([
      'src/a/**/*.ts',
      '!src/**/*.test.ts',
      '!src/**/*.test.tsx',
      '!src/theme.ts',
      '!src/test/**',
    ])
  })

  it('uebergeht Namen, die kein Ausschnitt traegt', () => {
    expect(mutateFuer(['gibtesnicht'], plan)).not.toContain('src/a/**/*.ts')
  })

  it('nennt zum wirklichen Stufenplan die beiden Bestandsausschnitte', () => {
    const gelesen = stufenplanLesen()
    const liste = mutateFuer(
      aufgenommene(gelesen).map((ausschnitt) => ausschnitt.name),
      gelesen,
    )

    expect(liste).toContain('src/lib/**/*.{ts,tsx}')
    expect(liste).toContain('src/api/**/*.ts')
    expect(liste).toContain('!src/**/*.test.ts')
    expect(liste).toContain('!src/**/*.test.tsx')
    for (const ausnahme of gelesen.ausnahmen) expect(liste).toContain(`!${ausnahme}`)
  })
})
