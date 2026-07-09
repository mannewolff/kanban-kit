import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { useAuth } from '../auth/AuthContext'

/** Geschützte Startseite. Projekt-/Board-Auswahl folgt mit U2. */
export function HomePage() {
  const { user } = useAuth()

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Willkommen{user ? `, ${user.displayName}` : ''}
      </Typography>
      <Typography color="text.secondary">
        {user && user.memberships.length > 0
          ? `Du bist Mitglied in ${user.memberships.length} Projekt(en).`
          : 'Noch keine Projekte. Die Projekt- und Board-Auswahl kommt in Kürze.'}
      </Typography>
    </Box>
  )
}
