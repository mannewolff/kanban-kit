import { describe, expect, it } from 'vitest'
import {
  gemesseneAusschnitte,
  mutationTestInclude,
  musterWurzel,
  stufenplan,
  testMusterFuer,
  type Stufenplan,
} from '../../mutationTestUmfang'

// Leitplanke zu Issue #1073, Befund 1: die Stryker-Konfiguration hat einen Umfang zugesagt, den sie
// nicht eingelöst hat — `mutate` nannte `src/api/**/*.ts`, der Testrunner wurde über
// `vitest.dir: "src/lib"` aber nur nach `src/lib` geschickt. Der erzeugte Bericht sah trotzdem
// vollständig aus, enthielt aber keine einzige Datei aus `src/api`. Das ist schlimmer als ein
// offen fehlender Umfang, und es fällt von selbst niemandem auf. Der Testumfang wird deshalb aus
// derselben Quelle abgeleitet wie der Mutationsumfang — seit #1275 aus `mutationsstufen.json` —,
// und diese Ableitung steht hier im Pflicht-Gate, nicht als Bitte in einer CLAUDE-*.md.

const probeplan: Stufenplan = {
  ausnahmen: [],
  ausschnitte: [
    {
      name: 'drin',
      muster: ['src/lib/**/*.ts'],
      aufgenommen: '2026-09-28',
      phase: 0,
      reihenfolge: 1,
      begruendung: 'Probe',
    },
    {
      name: 'naechster',
      muster: ['src/components/Foo.tsx'],
      aufgenommen: false,
      phase: 1,
      reihenfolge: 2,
      begruendung: 'Probe',
    },
    {
      name: 'spaeter',
      muster: ['src/pages/Bar.tsx'],
      aufgenommen: false,
      phase: 2,
      reihenfolge: 3,
      begruendung: 'Probe',
    },
  ],
}

describe('musterWurzel: das feste Verzeichnis eines Glob-Musters', () => {
  it('schneidet ab dem ersten Segment mit Wildcard ab', () => {
    expect(musterWurzel('src/api/**/*.ts')).toBe('src/api')
    expect(musterWurzel('src/lib/*.ts')).toBe('src/lib')
  })

  it('lässt ein Muster ohne Wildcard unverändert', () => {
    expect(musterWurzel('src/lib/boardOps.ts')).toBe('src/lib/boardOps.ts')
  })

  it('liefert für ein Muster ohne festes Präfix die leere Wurzel', () => {
    expect(musterWurzel('**/*.ts')).toBe('')
  })

  it('verwirft auch feste Segmente hinter einer Wildcard — sie sind kein Präfix', () => {
    expect(musterWurzel('src/**/api/*.ts')).toBe('src')
  })
})

describe('gemesseneAusschnitte: die aufgenommenen plus der Kandidat', () => {
  it('nimmt genau einen offenen Ausschnitt dazu — den mit der kleinsten Reihenfolge', () => {
    expect(gemesseneAusschnitte(probeplan).map((ausschnitt) => ausschnitt.name)).toEqual([
      'drin',
      'naechster',
    ])
  })

  it('bleibt bei den aufgenommenen, wenn kein Ausschnitt mehr offen ist', () => {
    const alleDrin = probeplan.ausschnitte.map((ausschnitt) => ({
      ...ausschnitt,
      aufgenommen: '2026-09-28',
    }))

    expect(gemesseneAusschnitte({ ...probeplan, ausschnitte: alleDrin })).toHaveLength(3)
  })
})

describe('testMusterFuer: woher ein Ausschnitt seine Tests nimmt', () => {
  it('nimmt `testMuster`, wo der Ausschnitt es führt', () => {
    expect(
      testMusterFuer({ ...probeplan.ausschnitte[1], testMuster: ['src/components/Eigen.test.tsx'] }),
    ).toEqual(['src/components/Eigen.test.tsx'])
  })

  it('nimmt bei einem Muster mit Wildcard die Tests unter der Musterwurzel', () => {
    expect(testMusterFuer(probeplan.ausschnitte[0])).toEqual(['src/lib/**/*.test.{ts,tsx}'])
  })

  it('nimmt bei einem Muster ohne Wildcard den Konventionstest daneben', () => {
    expect(testMusterFuer(probeplan.ausschnitte[1])).toEqual(['src/components/Foo.test.tsx'])
  })

  it('macht aus einem Muster ohne festes Präfix kein absolutes Glob', () => {
    expect(testMusterFuer({ ...probeplan.ausschnitte[0], muster: ['**/*.ts'] })).toEqual([
      '**/*.test.{ts,tsx}',
    ])
  })
})

describe('mutationTestInclude: der zugesagte Umfang wird eingelöst', () => {
  const gemessen = gemesseneAusschnitte(stufenplan)

  it('misst überhaupt Ausschnitte', () => {
    expect(gemessen.length).toBeGreaterThan(0)
  })

  it.each(gemessen.map((ausschnitt) => [ausschnitt.name, ausschnitt] as const))(
    'der Mutationslauf fährt die Tests zu %s',
    (name, ausschnitt) => {
      for (const muster of testMusterFuer(ausschnitt)) {
        expect(
          mutationTestInclude(),
          `Zum Ausschnitt "${name}" fährt der Mutationslauf die Tests "${muster}" nicht — ` +
            'seine mutierten Quellen bleiben ungeprüft, obwohl der Bericht vollständig aussieht.',
        ).toContain(muster)
      }
    },
  )

  it('meldet jedes Testmuster nur einmal', () => {
    const doppelt: Stufenplan = {
      ausnahmen: [],
      ausschnitte: [probeplan.ausschnitte[0], { ...probeplan.ausschnitte[0], name: 'zwilling' }],
    }

    expect(mutationTestInclude(doppelt)).toEqual(['src/lib/**/*.test.{ts,tsx}'])
  })

  it('fährt zum Kandidaten dessen Tests — ohne sie wäre seine Quote null', () => {
    expect(mutationTestInclude(probeplan)).toContain('src/components/Foo.test.tsx')
    expect(mutationTestInclude(probeplan)).not.toContain('src/pages/Bar.test.tsx')
  })
})
