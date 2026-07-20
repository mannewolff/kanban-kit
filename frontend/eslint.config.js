import jsxA11y from 'eslint-plugin-jsx-a11y'
import react from 'eslint-plugin-react'
import reactHooks from 'eslint-plugin-react-hooks'
import testingLibrary from 'eslint-plugin-testing-library'
import tseslint from 'typescript-eslint'

// ESLint-Gate (CLAUDE-react.md): typescript-eslint + react + react-hooks + jsx-a11y,
// flat config. Regel-Deaktivierungen nur einzeln und mit Begründung direkt an der Regel.
//
// Typed Linting ist aktiv (parserOptions.projectService), damit typbasierte Regeln wie
// @typescript-eslint/no-deprecated greifen — Deprecation-Nutzungen (z. B. abgekündigte
// MUI-Props) fallen so als harter Lint-Fehler im Gate auf statt spät bei Sonar.
export default tseslint.config(
  { ignores: ['dist/**', 'node_modules/**'] },
  {
    files: ['src/**/*.{ts,tsx}'],
    extends: [
      tseslint.configs.recommended,
      react.configs.flat.recommended,
      react.configs.flat['jsx-runtime'],
      reactHooks.configs.flat.recommended,
      jsxA11y.flatConfigs.recommended,
    ],
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    settings: {
      react: { version: 'detect' },
    },
    rules: {
      // Deaktiviert (begründet): Das etablierte Datenfetch-Muster dieses Projekts setzt im
      // Effect synchron Reset-State und lädt dann mit Cancellation-Flag (siehe BoardPage,
      // AppShell, #67). Die "richtige" Auflösung wäre eine Server-State-Library (TanStack
      // Query) — laut CLAUDE-react.md Versionsstrategie ein eigener Plan, kein Nebenbei-Umbau.
      'react-hooks/set-state-in-effect': 'off',
      // Deaktiviert (begründet): autoFocus auf Inputs in Modal-Dialogen ist ARIA-konform
      // (Fokus gehört beim Öffnen in den Dialog); ein pauschales Verbot wäre ein
      // A11y-Rückschritt. Betroffene Stellen: CardDetailModal, NewCardModal.
      'jsx-a11y/no-autofocus': 'off',
      // Aktiviert (typed): Nutzung @deprecated-markierter APIs (z. B. abgekündigte MUI-Props
      // wie inputProps) als harter Lint-Fehler im Pflicht-Gate — fängt genau die Klasse, die
      // zuvor erst spät als Sonar-Findings auffiel ("Leitplanke im Gate statt Doku").
      '@typescript-eslint/no-deprecated': 'error',
    },
  },
  {
    // testing-library/recommended (flat/react) nur an Test-Dateien — das Plugin gehört an die
    // Tests, nicht an den src-Produktivcode. Fängt idiomatische Test-Anti-Muster wie
    // `waitFor(() => expect(getByX()))` (prefer-find-by) direkt im Gate ab.
    files: ['src/**/*.test.{ts,tsx}'],
    extends: [testingLibrary.configs['flat/react']],
  },
)
