import { mergeConfig } from 'vite'
import { mutationTestInclude } from './mutationTestUmfang'
import baseConfig from './vite.config'

// Vitest-Konfiguration nur für den Mutationslauf (`npm run test:mutation`, Issue #1073).
// Identisch zum Pflichtlauf, bis auf `include`: gefahren werden genau die Tests, die zu den in
// `stryker.config.json` mutierten Verzeichnissen gehören. Die Liste ist abgeleitet, nicht
// gepflegt — Begründung in `mutationTestUmfang.ts`.
export default mergeConfig(baseConfig, {
  test: { include: mutationTestInclude() },
})
