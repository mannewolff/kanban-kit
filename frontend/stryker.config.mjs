import { aufgenommene, mutateFuer, stufenplanLesen } from './mutationsbereich.mjs'

// Stryker-Konfiguration des Frontends (Issue #1276). `mutate` wird nicht gepflegt, sondern aus
// dem Stufenplan `mutationsstufen.json` abgeleitet — derselben Quelle, aus der der Treiber
// `scripts/mutationspruefung.mjs` seinen Prüfbereich und `mutationTestUmfang.ts` den Testumfang
// holen (#1073, #1275). Der Plan wird synchron gelesen: Stryker 8 akzeptiert als Export nur ein
// Objekt, keine Funktion.
//
// Der Darstellungs-Ignorer (#1277) nimmt Stilmutanten schon beim Instrumentieren heraus; sie
// erscheinen als `Ignored` und zählen nicht in der Quote. Der Platzhalter `@stryker-mutator/*`
// lädt den Vitest-Runner mit, sobald eine eigene Plugin-Liste den Default ersetzt.
const plan = stufenplanLesen()

export default {
  testRunner: 'vitest',
  plugins: ['@stryker-mutator/*', './stryker/darstellungIgnorer.js'],
  ignorers: ['darstellung'],
  vitest: { configFile: 'vitest.mutation.config.ts' },
  mutate: mutateFuer(
    aufgenommene(plan).map((ausschnitt) => ausschnitt.name),
    plan,
  ),
  reporters: ['json', 'html', 'clear-text', 'progress'],
  htmlReporter: { fileName: '../.claude/stryker/mutation.html' },
  jsonReporter: { fileName: '../.claude/stryker/mutation.json' },
  concurrency: 4,
  thresholds: { high: 100, low: 90, break: null },
}
