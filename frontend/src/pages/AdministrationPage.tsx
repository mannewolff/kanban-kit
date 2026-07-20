import Box from '@mui/material/Box'
import FormControlLabel from '@mui/material/FormControlLabel'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Switch from '@mui/material/Switch'
import Typography from '@mui/material/Typography'
import { useEditMode } from '../lib/EditModeContext'

/**
 * Administrations-/Einstellungsseite. Enthält zunächst nur den Editiermodus-Schalter; weitere
 * Verwaltungsfunktionen folgen. Für alle angemeldeten Nutzer erreichbar.
 */
export function AdministrationPage() {
  const { editMode, setEditMode } = useEditMode()

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Administration
      </Typography>
      <Paper variant="outlined" sx={{ p: 2, maxWidth: 560 }}>
        <Stack spacing={1}>
          <Typography variant="h6" component="h2">
            Editiermodus
          </Typography>
          <FormControlLabel
            control={
              <Switch
                checked={editMode}
                onChange={(e) => setEditMode(e.target.checked)}
                slotProps={{ input: { 'aria-label': 'Editiermodus aktivieren' } }}
              />
            }
            label="Bearbeiten aktivieren"
          />
          <Typography variant="body2" color="text.secondary">
            Ist der Editiermodus aktiv, erscheinen die Bearbeiten-Symbole (etwa zum Umbenennen von
            Projekten, Boards und Spalten), sofern du die nötigen Rechte hast. Der Modus ist beim
            Start immer aus und wird nicht über einen Neustart hinweg gemerkt.
          </Typography>
        </Stack>
      </Paper>
    </Box>
  )
}
