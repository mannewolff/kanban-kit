/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import pkg from './package.json'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Betriebsversion zur Build-Zeit injizieren (Issue #0106) — Deklaration in vite-env.d.ts.
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
  },
  server: {
    // Im Dev-Betrieb API-Aufrufe an das Spring-Backend weiterreichen.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    // Performance-Budget (CLAUDE-react.md): Warn-Grenze = dokumentiertes Chunk-Budget.
    chunkSizeWarningLimit: 600,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
    css: false,
    // Coverage-Gate (CLAUDE-react.md §Tests): v8-Provider, Build bricht bei Unterschreitung.
    // lcov zusätzlich zu text/html: wird von SonarQube importiert (sonar-project.properties).
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      include: ['src/**'],
      exclude: [
        // Testdateien selbst nicht messen (Vitest-Default; unsere Custom-`exclude`-Liste ersetzt den
        // Default, daher hier explizit): Test-Helper/Fixtures sind keine Produktlogik.
        '**/*.test.ts',
        '**/*.test.tsx',
        // Begründete Ausschlüsse (analog CLAUDE-java.md §5.2, einzeln):
        'src/main.tsx', // React-Bootstrap ohne Logik (Root-Mount)
        'src/vite-env.d.ts', // Typ-Deklaration
        'src/test/**', // Test-Setup
        'src/App.tsx', // reines Routen-Wiring (lazy-Imports); Verhalten über Page-Tests gedeckt
        'src/theme.ts', // Design-Token-Objekt ohne Logik
      ],
      // Finaler Ratchet (Stand 2026-07-20, kanban-kit#323): echte 100/100/100/100 über den
      // gesamten Produktcode (Statements/Branches/Functions/Lines). Erreicht ohne `c8 ignore`-
      // Ausnahmen — jede vormalige Lücke wurde entweder als echte Kante getestet oder als
      // beweisbar toter Guard branchfrei umstrukturiert (dokumentiert an der jeweiligen Stelle).
      // Ab hier ist jeder Rückschritt ein Fehler: neue Logik kommt nur mit passenden Tests herein.
      thresholds: {
        lines: 100,
        branches: 100,
        functions: 100,
        statements: 100,
      },
    },
  },
})
