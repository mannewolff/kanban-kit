import sonarjs from 'eslint-plugin-sonarjs'

import base from './eslint.config.js'

// Messkonfiguration für Plan #1042 (P0, E2): bildet die Komplexitäts- und Stil-Regeln
// von SonarCloud lokal nach, damit die Folgepakete ihre Akzeptanzkriterien ohne Push
// und Scan prüfen können. Sie misst, sie sperrt nicht — das normale `npm run lint`
// (eslint.config.js) bleibt unverändert. Das letzte Paket des Plans übernimmt diese
// Einträge wörtlich ins Pflicht-Gate und lässt diese Datei entfallen.
//
// Die Regeln rechnen nicht in jedem Punkt wie SonarCloud; wo sie strenger sind, steht
// die Abweichung als Option an der Regel.
export default [
  ...base,
  {
    files: ['src/**/*.{ts,tsx}'],
    plugins: { sonarjs },
    rules: {
      // Sonar S3776 — deckungsgleich mit SonarCloud, gleiche Schwelle 15.
      'sonarjs/cognitive-complexity': ['error', 15],
      // Sonar S4624 — verschachtelte Template-Literale.
      'sonarjs/no-nested-template-literals': 'error',
      // Sonar S3358 — verschachtelte Ternäre (Kernregel, kein sonarjs nötig).
      'no-nested-ternary': 'error',
      // Sonar S6749 — Fragment ohne Wirkung.
      'react/jsx-no-useless-fragment': 'error',
      // Sonar S6606 — `||` statt `??`. Strenger als SonarCloud: Ohne die beiden Optionen
      // meldet die Regel auch String-Defaults und gemischte Logik-Ausdrücke, die Sonar
      // nicht beanstandet und deren Umbau das Verhalten änderte — `AppShell.tsx:460`
      // (`….join('') || '?'`: mit `??` würde ein leerer Anzeigename zu `''` statt `'?'`)
      // und `BoardView.tsx:1151`.
      '@typescript-eslint/prefer-nullish-coalescing': [
        'error',
        { ignorePrimitives: { string: true }, ignoreMixedLogicalExpressions: true },
      ],
    },
  },
]
