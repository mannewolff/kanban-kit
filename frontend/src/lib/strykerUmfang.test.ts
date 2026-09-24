import { describe, expect, it } from 'vitest'
import { mutationTestInclude, musterWurzel } from '../../mutationTestUmfang'

// Leitplanke zu Issue #1073, Befund 1: `stryker.config.json` hat einen Umfang zugesagt, den es
// nicht eingelöst hat — `mutate` nannte `src/api/**/*.ts`, der Testrunner wurde über
// `vitest.dir: "src/lib"` aber nur nach `src/lib` geschickt. Der erzeugte Bericht sah trotzdem
// vollständig aus, enthielt aber keine einzige Datei aus `src/api`. Das ist schlimmer als ein
// offen fehlender Umfang, und es fällt von selbst niemandem auf. Der Testumfang wird deshalb aus
// `mutate` abgeleitet, und diese Ableitung steht hier im Pflicht-Gate — nicht als Bitte in einer
// CLAUDE-*.md.

// Rohtext statt `node:fs`: etabliertes Muster im Projekt (siehe `designQuelle.test.ts`) — Vitest
// löst `import.meta.url` nicht zu einem `file:`-URL auf.
const QUELLE: Record<string, string> = import.meta.glob('../../stryker.config.json', {
  query: '?raw',
  import: 'default',
  eager: true,
})

const config = JSON.parse(Object.values(QUELLE)[0] ?? '{"mutate":[]}') as { mutate: string[] }
const einschluesse = config.mutate.filter((muster) => !muster.startsWith('!'))

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

describe('mutationTestInclude: der zugesagte Umfang wird eingelöst', () => {
  it('nennt überhaupt Muster, die mutiert werden', () => {
    expect(einschluesse.length).toBeGreaterThan(0)
  })

  it.each(einschluesse)('der Mutationslauf fährt die Tests zu %s', (muster) => {
    const wurzel = musterWurzel(muster)

    expect(
      mutationTestInclude(),
      `Zu "${muster}" fährt der Mutationslauf keine Tests — die mutierten Quellen unter ` +
        `"${wurzel}" bleiben ungeprüft, obwohl der Bericht vollständig aussieht.`,
    ).toContain(`${wurzel}/**/*.test.{ts,tsx}`)
  })

  it('übergeht Ausschluss-Muster und meldet jede Wurzel nur einmal', () => {
    expect(mutationTestInclude(['src/lib/**/*.ts', 'src/lib/*.tsx', '!src/**/*.test.ts'])).toEqual([
      'src/lib/**/*.test.{ts,tsx}',
    ])
  })

  it('macht aus einem Muster ohne festes Präfix kein absolutes Glob', () => {
    expect(mutationTestInclude(['**/*.ts'])).toEqual(['**/*.test.{ts,tsx}'])
  })
})
