import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import { EPIC_EDGE_WIDTH } from '../theme'

/** Zentrierte Karte für Auth-Screens. */
export function AuthCard({ title, children }: Readonly<{ title: string; children: React.ReactNode }>) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', p: 2 }}>
      <Paper
        variant="outlined"
        sx={{
          p: 4,
          width: '100%',
          maxWidth: 420,
          // Betonung an der Kante statt ueber einen Schatten (#653, E3/E7).
          borderLeft: `${EPIC_EDGE_WIDTH}px solid`,
          borderLeftColor: 'primary.main',
        }}
      >
        <Typography variant="h4" component="h1" gutterBottom>
          kanban-kit
        </Typography>
        <Typography variant="h6" component="h2" sx={{ mb: 2 }} color="text.secondary">
          {title}
        </Typography>
        {children}
      </Paper>
    </Box>
  )
}
