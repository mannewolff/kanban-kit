import Box from '@mui/material/Box'
import { ThemeProvider } from '@mui/material/styles'
import type { ReactNode } from 'react'
import { ERSCHEINUNGSBILD_SX, theme } from '../../theme'

/**
 * Ein Bereich der Nachtlauf-Seite, der die Ausnahme aus `CLAUDE-design.md` verlässt und wieder
 * Kupferwarte trägt (#987).
 *
 * <p><b>Warum es zwei Dinge braucht.</b> Die Seite liegt unter einem verschachtelten
 * `ThemeProvider` mit `nachtlaufTheme` — festen Hellwerten ohne CSS-Variablen — und ihr
 * Wurzelknoten schreibt zusätzlich die hellen Variablen des Leitstands in seinen Teilbaum (#954).
 * Das eine ist React-Kontext, das andere CSS-Vererbung: Der Bereich stellt deshalb beides zurück —
 * das Theme über einen eigenen Provider, die Variablen über {@link ERSCHEINUNGSBILD_SX}.
 *
 * <p><b>Der Grund wird selbst gemalt</b>, über die volle Breite des Inhaltsbereichs. Sonst stünden
 * im dunklen Erscheinungsbild dunkle Platten auf der hellen Fläche der Ausnahme.
 */
export function KupferwarteBereich({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <ThemeProvider theme={theme}>
      <Box
        data-testid="kupferwarte-bereich"
        sx={{
          ...ERSCHEINUNGSBILD_SX,
          bgcolor: 'background.default',
          color: 'text.primary',
          // Zieht die Fläche über die Polsterung des Inhaltsbereichs der Shell (22/26/44 px, #978),
          // damit links und rechts kein Streifen der Ausnahme stehen bleibt.
          mx: { xs: '-16px', md: '-26px' },
          px: { xs: '16px', md: '26px' },
          py: '20px',
        }}
      >
        {children}
      </Box>
    </ThemeProvider>
  )
}
