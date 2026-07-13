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
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: ['src/**'],
      exclude: [
        // Begründete Ausschlüsse (analog CLAUDE-java.md §5.2, einzeln):
        'src/main.tsx', // React-Bootstrap ohne Logik (Root-Mount)
        'src/vite-env.d.ts', // Typ-Deklaration
        'src/test/**', // Test-Setup
        'src/App.tsx', // reines Routen-Wiring (lazy-Imports); Verhalten über Page-Tests gedeckt
        'src/theme.ts', // Design-Token-Objekt ohne Logik
      ],
      // Ehrliche Ist-Schwellen (Stand 2026-07-11) als Gate gegen Rückschritt.
      // Zielpfad: schrittweise anheben, wenn Lücken (v. a. Auth-Seiten, BoardPage-Interaktionen)
      // geschlossen werden — analog zum PIT-Vorgehen im Backend (94 → 100).
      thresholds: {
        lines: 87,
        branches: 87,
        functions: 58,
        statements: 87,
      },
    },
  },
})
