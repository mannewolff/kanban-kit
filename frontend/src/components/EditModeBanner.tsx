import Box from '@mui/material/Box'
import Switch from '@mui/material/Switch'
import Typography from '@mui/material/Typography'
import WarningAmberIcon from '@mui/icons-material/WarningAmber'
import { useEditMode } from '../lib/EditModeContext'

/** Höhe des Editiermodus-Streifens; AppShell schiebt Header und Inhalt um diesen Wert nach unten. */
export const EDIT_MODE_BANNER_HEIGHT = 40

/**
 * Globaler Hinweisstreifen ganz oben, solange der Editiermodus aktiv ist: Amber-Signalfarbe, klarer
 * Hinweistext und ein Toggle-Switch zum sofortigen Verlassen. Rendert nichts im Ansichtsmodus.
 */
export function EditModeBanner() {
  const { editMode, setEditMode } = useEditMode()
  if (!editMode) {
    return null
  }
  return (
    <Box
      role="region"
      aria-label="Editiermodus-Hinweis"
      sx={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        height: EDIT_MODE_BANNER_HEIGHT,
        zIndex: (t) => t.zIndex.drawer + 2,
        bgcolor: 'warning.main',
        color: 'warning.contrastText',
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        px: 2,
      }}
    >
      <WarningAmberIcon fontSize="small" />
      <Typography variant="body2" sx={{ fontWeight: 600, flexGrow: 1 }}>
        Achtung, Du befindest Dich im Editiermodus
      </Typography>
      <Switch
        checked={editMode}
        onChange={(e) => setEditMode(e.target.checked)}
        color="default"
        slotProps={{ input: { 'aria-label': 'Editiermodus verlassen' } }}
      />
    </Box>
  )
}
