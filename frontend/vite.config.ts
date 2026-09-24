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
    // Die Zone der Tests ist festgenagelt und nicht die der Maschine. Ein Intl.DateTimeFormat
    // ohne `timeZone` folgt sonst der Zone des Rechners, und jede Erwartung auf einen
    // formatierten Zeitpunkt haengt daran: `19.09., 23:10` hier, `19.09., 21:10` auf dem
    // UTC-Runner der CI. Genau daran scheiterte v2.1.3 in der CI, waehrend der lokale
    // Pflichtlauf gruen war — der Fehler gehoert zu keiner Aenderung und ist lokal unsichtbar.
    // Europe/Berlin und nicht UTC, weil das Produkt Ortszeit zeigt (`leserZone()` in
    // api/nightRunUsage.ts, Plan E4: „die letzte Nacht" ist die des Lesers). In UTC getestet
    // fiele ein Fehler am Sommer-/Winterzeitwechsel nie auf.
    env: { TZ: 'Europe/Berlin' },
    // Das Vitest-Default von 5000 ms haelt keiner Last stand. Unter der vollen Suite mit
    // Coverage-Messung und parallelen Workern, erst recht neben einer fremden Testsuite auf
    // derselben Maschine, rissen am 2026-09-24 von Lauf zu Lauf andere Tests die Grenze
    // (CardDetailModal-lastige Seiten, aber auch Login und Signup) — isoliert brauchen
    // dieselben Tests hoechstens 1,3 s. Ein roter Pflichtcheck, der zu keiner Aenderung
    // gehoert, haelt den Nachtlauf hart an (Issue #1190). Ein echter Haenger endet nie und
    // wird auch mit 20 s gefangen; Zeitlimits je Test scheiden aus, weil die betroffenen
    // Tests wechseln (Issue #1196).
    testTimeout: 20000,
    hookTimeout: 20000,
    globals: true,
    setupFiles: './src/test/setup.ts',
    css: false,
    // Stryker kopiert das gesamte `frontend/` in einen Sandkasten unter `.stryker-tmp/`. Bricht
    // ein Mutationslauf ab, bleibt diese Kopie liegen — samt aller Testdateien. Vitests
    // Default-`exclude` kennt sie nicht, also sammelt der naechste Testlauf jede Datei doppelt
    // ein: einmal aus `src/`, einmal eingefroren aus dem Sandkasten. Die Kopie enthaelt nur
    // `frontend/`, nicht die Repo-Wurzel, weshalb jeder Test, der eine Datei ueber `..` sucht
    // (z. B. `designQuelle.test.ts` auf `CLAUDE-design.md`), dort ins Leere greift und rot wird.
    // Der Ordner ist gitignored, taucht also in `git status` nicht auf — der rote Check gehoert
    // dann zu keiner Aenderung und laesst sich von der ausloesenden Sitzung nicht beheben.
    // Die Default-Liste wird durch eine eigene ersetzt, deshalb stehen die Vitest-Defaults hier
    // ausgeschrieben.
    exclude: ['**/node_modules/**', '**/dist/**', '**/.stryker-tmp/**'],
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
        'src/nachtlaufDesign.ts', // Design-Token-Objekt ohne Logik (Ausnahme Nachtlauf-Auswertung)
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
