import jsxA11y from 'eslint-plugin-jsx-a11y'
import react from 'eslint-plugin-react'
import reactHooks from 'eslint-plugin-react-hooks'
import tseslint from 'typescript-eslint'

// ESLint-Gate (CLAUDE-react.md): typescript-eslint + react + react-hooks + jsx-a11y,
// flat config. Regel-Deaktivierungen nur einzeln und mit Begründung direkt an der Regel.
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
    },
  },
)
