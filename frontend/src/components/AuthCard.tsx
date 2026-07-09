import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'

/** Zentrierte Karte für Auth-Screens. */
export function AuthCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', p: 2 }}>
      <Paper elevation={2} sx={{ p: 4, width: '100%', maxWidth: 420 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          manban
        </Typography>
        <Typography variant="h6" component="h2" sx={{ mb: 2 }} color="text.secondary">
          {title}
        </Typography>
        {children}
      </Paper>
    </Box>
  )
}
