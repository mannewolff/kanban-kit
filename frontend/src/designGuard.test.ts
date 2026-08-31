import { describe, expect, it } from 'vitest'
import {
  deadAllowlistEntries,
  scanSource,
  unmatchedViolations,
  type DesignAllowance,
} from './lib/designGuard'

/**
 * Bestandswaechter der Struktur-Leitplanke (#648). Die Heuristik selbst und ihre Kanten stehen in
 * `lib/designGuard.ts` samt eigener Testdatei — hier wird nur der echte Quelltext dagegen gehalten.
 *
 * Die Ausnahmeliste ist ein **Ratchet**: Sie duldet einen bekannten Verstoss, bis das benannte
 * Paket ihn aufloest. Ein Eintrag ohne realen Verstoss macht den Test ebenso rot wie ein Verstoss
 * ohne Eintrag — so kann ein Paket seine Stelle nicht auflosen, ohne sie auszutragen.
 *
 * Sie ist seit Paket 6 (#653) **leer**. Dass die Mechanik trotzdem stimmt, sichern die
 * Unit-Tests in `lib/designGuard.test.ts`; hier waere es mangels Eintraegen nicht pruefbar.
 */
const ALLOWLIST: ReadonlyArray<DesignAllowance> = []

/**
 * Die Orte, an denen Farbwerte per Auftrag stehen: das Theme als Token-Quelle und die beiden
 * semantischen Paletten. Sie sind vom Scan ausgenommen, weil die Regel „Farben kommen aus dem
 * Theme" sich nicht gegen ihre eigene Quelle richten kann. Bewusst eine kurze, benannte Liste —
 * kein enger Glob, der halbe Verzeichnisse stillschweigend auslaesst.
 */
const COLOR_SOURCES = ['theme.ts', 'lib/statusColors.ts', 'lib/epicMeta.ts']

const SOURCES: Record<string, string> = import.meta.glob('./**/*.{ts,tsx}', {
  query: '?raw',
  import: 'default',
  eager: true,
})

const violations = Object.entries(SOURCES)
  .map(([path, source]) => ({ file: path.replace('./', ''), source }))
  .filter(({ file }) => !/\.test\.tsx?$/.test(file) && !COLOR_SOURCES.includes(file))
  .flatMap(({ file, source }) => scanSource(file, source))

describe('Design-Leitplanke im Bestand', () => {
  it('sammelt ueberhaupt Quelltext ein', () => {
    // Ohne diese Zusicherung waeren die beiden folgenden Tests auch bei leerem Glob gruen.
    expect(Object.keys(SOURCES).length).toBeGreaterThan(50)
  })

  it('haelt den Bestand frei von Verstoessen ausserhalb der Ausnahmeliste', () => {
    expect(unmatchedViolations(violations, ALLOWLIST)).toEqual([])
  })

  it('fuehrt keine Ausnahme, die kein Verstoss mehr deckt', () => {
    expect(deadAllowlistEntries(violations, ALLOWLIST)).toEqual([])
  })
})
