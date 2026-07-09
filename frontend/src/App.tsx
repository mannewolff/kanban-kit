import Box from '@mui/material/Box'
import Container from '@mui/material/Container'
import Typography from '@mui/material/Typography'

export function App() {
  return (
    <Container maxWidth="sm">
      <Box sx={{ mt: 8, textAlign: 'center' }}>
        <Typography variant="h3" component="h1" gutterBottom>
          manban
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Das Gerüst steht. Die Fachfunktionen folgen mit den nächsten Issues.
        </Typography>
      </Box>
    </Container>
  )
}
