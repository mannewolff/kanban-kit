import jsxA11y from 'eslint-plugin-jsx-a11y'
import react from 'eslint-plugin-react'
import sonarjs from 'eslint-plugin-sonarjs'
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
    // `sonarjs` bringt kein flat-Preset mit, das hier passte: Uebernommen sind genau die fuenf
    // Regeln unten, nicht das ganze recommended-Set (Plan #1042, E2).
    plugins: { sonarjs },
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
      // Aktiviert (typed): `a && a.b`-Ketten statt `a?.b` — deckungsgleich mit Sonar S6582
      // (#445). Als Gate-Regel statt reinem Einzelfix, damit dieselbe Klasse nicht wieder
      // erst nach dem Push bei SonarCloud auffällt.
      '@typescript-eslint/prefer-optional-chain': 'error',
      // Aktiviert: Textknoten, der nur durch einen Zeilenumbruch von einem Inline-Element
      // getrennt ist — JSX verschluckt das Leerzeichen, die Absicht bleibt mehrdeutig.
      // Deckungsgleich mit Sonar S6772 (#541); als Gate-Regel statt reinem Einzelfix.
      'react/jsx-child-element-spacing': 'error',

      // --- Sonar-Regeln im Gate (Plan #1042, E2) ---------------------------------------
      // Uebernommen aus der Messkonfiguration `eslint.sonar.config.js`, die Issue #1045
      // angelegt hat und dieses Paket ersetzt. Grund derselbe wie bei den Regeln darueber:
      // „Leitplanke im Gate statt Doku, die bittet" (CLAUDE-react.md) — allein der Scan zu
      // v2.0.0 brachte 17 neue Befunde aus Nachtlaeufen, die vor dem Push niemand sah.
      //
      // Sonar S3776 — deckungsgleich mit SonarCloud, gleiche Schwelle 15.
      'sonarjs/cognitive-complexity': ['error', 15],
      // Sonar S4624 — verschachtelte Template-Literale.
      'sonarjs/no-nested-template-literals': 'error',
      // Sonar S3358 — verschachtelte Ternaere (Kernregel, kein sonarjs noetig).
      'no-nested-ternary': 'error',
      // Sonar S6749 — Fragment ohne Wirkung.
      'react/jsx-no-useless-fragment': 'error',
      // Sonar S6606 — `||` statt `??`. Strenger als SonarCloud: Ohne die beiden Optionen
      // meldet die Regel auch String-Defaults und gemischte Logik-Ausdruecke, die Sonar
      // nicht beanstandet und deren Umbau das Verhalten aenderte — `AppShell.tsx:460`
      // (`….join('') || '?'`: mit `??` wuerde ein leerer Anzeigename zu `''` statt `'?'`)
      // und `BoardView.tsx:1151`.
      '@typescript-eslint/prefer-nullish-coalescing': [
        'error',
        { ignorePrimitives: { string: true }, ignoreMixedLogicalExpressions: true },
      ],
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
