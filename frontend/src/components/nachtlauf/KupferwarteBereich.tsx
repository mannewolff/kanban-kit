import Box from '@mui/material/Box'
import { ThemeProvider } from '@mui/material/styles'
import type { ReactNode } from 'react'
import { ERSCHEINUNGSBILD_SX, theme } from '../../theme'

/**
 * Ein Bereich, der Theme und Variablen der Kupferwarte für seinen Teilbaum zurückstellt und seinen
 * Grund selbst malt (#987).
 *
 * <p>Entstanden für die Nachtlauf-Seite, deren Laufblöcke so die helle Ausnahme verließen. Seit
 * #1912 folgt jene Seite vollständig Kupferwarte und braucht ihn nicht mehr; Verwender ist der
 * Plattform-Leitstand.
 *
 * <p><b>Warum es zwei Dinge braucht.</b> Theme ist React-Kontext, Variablen sind CSS-Vererbung: Der
 * Bereich stellt beides zurück — das Theme über einen eigenen Provider, die Variablen über
 * {@link ERSCHEINUNGSBILD_SX}.
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
