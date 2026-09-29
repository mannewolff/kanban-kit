import stufenplanQuelle from './mutationsstufen.json'

/**
 * Der Testumfang des Mutationslaufs, abgeleitet aus dem Stufenplan `mutationsstufen.json`
 * (#1275; zuvor aus `mutate` der Stryker-Konfiguration, #1073).
 *
 * Vorher standen Mutationsumfang und Testumfang getrennt in derselben Datei — `mutate` nannte
 * `src/api/**` + `src/lib/**`, der Runner wurde über `vitest.dir` aber nur nach `src/lib`
 * geschickt. Der erzeugte Bericht sah vollständig aus und enthielt keine einzige Datei aus
 * `src/api`. Zwei Wahrheiten über denselben Umfang laufen still auseinander; hier gibt es nur
 * noch eine, und der Testumfang folgt ihr per Konstruktion.
 *
 * Gefahren werden die Tests der **aufgenommenen Ausschnitte und des Kandidaten** (E6): Der
 * Vollauf mutiert den Kandidaten mit, um seine Quote gegen die 82-%-Marke zu halten; ohne seine
 * Tests endete jeder seiner Mutanten als `NoCoverage` und die Marke wäre nie erreichbar.
 *
 * Warum nicht einfach der ganze `src`-Baum: Stryker kopiert nur `frontend/` in seinen Sandkasten
 * unter `.stryker-tmp/`. `src/designQuelle.test.ts` liest `../../CLAUDE-design.md` aus der
 * Repo-Wurzel und greift dort ins Leere — der Dry-Run bricht ab, bevor ein Mutant gemessen wird.
 * Der Test tötet ohnehin keinen Mutanten unter den Ausschnitten; er gehört nicht in diesen Lauf,
 * sondern in den Pflichtlauf, wo die Datei da ist.
 */

/** Ein Ausschnitt des Stufenplans — eine benannte Menge von Quelldateien mit ihrer Position. */
export type Ausschnitt = {
  readonly name: string
  readonly muster: readonly string[]
  readonly testMuster?: readonly string[]
  readonly aufgenommen: string | false
  readonly gemeinsameSchwelle?: boolean
  readonly phase: number
  readonly reihenfolge: number
  readonly begruendung: string
}

/** Der Stufenplan: die Ausnahmen (E4) und die Ausschnitte in ihrer Reihenfolge. */
export type Stufenplan = {
  readonly ausnahmen: readonly string[]
  readonly ausschnitte: readonly Ausschnitt[]
}

export const stufenplan = stufenplanQuelle as unknown as Stufenplan

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

/**
 * Die Testmuster eines Ausschnitts: `testMuster`, falls er es führt (E5); sonst je Muster die
 * Tests unter der Musterwurzel, und für ein Muster ohne Wildcard der Konventionstest daneben
 * (`X.tsx` → `X.test.tsx`, wie `konventionsTests` in `scripts/mutationspruefung.mjs`).
 */
export const testMusterFuer = (ausschnitt: Ausschnitt): string[] =>
  ausschnitt.testMuster
    ? [...ausschnitt.testMuster]
    : ausschnitt.muster.map((muster) => {
        if (!muster.includes('*')) return muster.replace(/\.(tsx?)$/, '.test.$1')
        const wurzel = musterWurzel(muster)
        return wurzel === '' ? '**/*.test.{ts,tsx}' : `${wurzel}/**/*.test.{ts,tsx}`
      })

/** Die Ausschnitte, die der Mutationslauf mutiert: die aufgenommenen und der Kandidat (E6). */
export const gemesseneAusschnitte = (plan: Stufenplan = stufenplan): Ausschnitt[] => {
  const aufgenommen = plan.ausschnitte.filter(
    (ausschnitt) => typeof ausschnitt.aufgenommen === 'string',
  )
  const offen = plan.ausschnitte
    .filter((ausschnitt) => typeof ausschnitt.aufgenommen !== 'string')
    .sort((einer, anderer) => einer.reihenfolge - anderer.reihenfolge)
  return offen.length === 0 ? aufgenommen : [...aufgenommen, offen[0]]
}

/** Vitest-`include` für den Mutationslauf: zu jedem gemessenen Ausschnitt seine Tests. */
export const mutationTestInclude = (plan: Stufenplan = stufenplan): string[] => [
  ...new Set(gemesseneAusschnitte(plan).flatMap(testMusterFuer)),
]
