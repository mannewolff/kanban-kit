import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { useParams } from 'react-router-dom'

/** Platzhalter für die Board-Ansicht. Die Karten-/Spaltenansicht kommt mit U3. */
export function BoardPage() {
  const { boardId } = useParams()
  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Board #{boardId}
      </Typography>
      <Typography color="text.secondary">Die Karten- und Spaltenansicht folgt mit U3.</Typography>
    </Box>
  )
}
