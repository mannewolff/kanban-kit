import strykerConfig from './stryker.config.json'

/**
 * Der Testumfang des Mutationslaufs, abgeleitet aus `mutate` in `stryker.config.json` (#1073).
 *
 * Vorher standen Mutationsumfang und Testumfang getrennt in derselben Datei — `mutate` nannte
 * `src/api/**` + `src/lib/**`, der Runner wurde über `vitest.dir` aber nur nach `src/lib`
 * geschickt. Der erzeugte Bericht sah vollständig aus und enthielt keine einzige Datei aus
 * `src/api`. Zwei Wahrheiten über denselben Umfang laufen still auseinander; hier gibt es nur
 * noch eine, und der Testumfang folgt ihr per Konstruktion.
 *
 * Warum nicht einfach der ganze `src`-Baum: Stryker kopiert nur `frontend/` in seinen Sandkasten
 * unter `.stryker-tmp/`. `src/designQuelle.test.ts` liest `../../CLAUDE-design.md` aus der
 * Repo-Wurzel und greift dort ins Leere — der Dry-Run bricht ab, bevor ein Mutant gemessen wird.
 * Der Test tötet ohnehin keinen Mutanten unter `src/lib` oder `src/api`; er gehört nicht in
 * diesen Lauf, sondern in den Pflichtlauf, wo die Datei da ist.
 */

/** Das feste Wurzelverzeichnis eines Glob-Musters — alles vor dem ersten Segment mit Wildcard. */
export const musterWurzel = (muster: string): string =>
  muster
    .split('/')
    .reduce<string[]>(
      (feste, segment) =>
        feste.includes('*') || segment.includes('*') ? [...feste, '*'] : [...feste, segment],
      [],
    )
    .filter((segment) => segment !== '*')
    .join('/')

/** Vitest-`include` für den Mutationslauf: zu jeder mutierten Wurzel die Tests darunter. */
export const mutationTestInclude = (
  mutate: readonly string[] = strykerConfig.mutate,
): string[] => [
  ...new Set(
    mutate
      .filter((muster) => !muster.startsWith('!'))
      .map(musterWurzel)
      .map((wurzel) => (wurzel === '' ? '**/*.test.{ts,tsx}' : `${wurzel}/**/*.test.{ts,tsx}`)),
  ),
]
