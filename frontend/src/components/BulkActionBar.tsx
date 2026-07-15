import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import ArchiveOutlinedIcon from '@mui/icons-material/ArchiveOutlined'
import DriveFileMoveOutlinedIcon from '@mui/icons-material/DriveFileMoveOutlined'

interface Props {
  /** Anzahl der aktuell ausgewählten Karten. */
  count: number
  /** Ob „Verschieben" angeboten wird (nur wenn der Nutzer board-übergreifend verschieben darf). */
  canMove: boolean
  onArchive: () => void
  onMove: () => void
  onCancel: () => void
}

/**
 * Fixierte Aktionsleiste für die Mehrfachauswahl von Karten. Erscheint, sobald mindestens eine
 * Karte ausgewählt ist, und bietet Archivieren, optional Verschieben sowie Abbrechen.
 */
export function BulkActionBar({ count, canMove, onArchive, onMove, onCancel }: Props) {
  return (
    <Paper
      elevation={6}
      role="region"
      aria-label="Massenaktionen"
      sx={{
        position: 'fixed',
        bottom: 24,
        left: '50%',
        transform: 'translateX(-50%)',
        px: 2,
        py: 1,
        display: 'flex',
        alignItems: 'center',
        gap: 2,
        borderRadius: 2,
        zIndex: (theme) => theme.zIndex.appBar,
      }}
    >
      <Typography variant="body2" sx={{ fontWeight: 600 }}>
        {count} ausgewählt
      </Typography>
      {canMove && (
        <Button size="small" startIcon={<DriveFileMoveOutlinedIcon />} onClick={onMove}>
          Verschieben
        </Button>
      )}
      <Button size="small" color="error" startIcon={<ArchiveOutlinedIcon />} onClick={onArchive}>
        Archivieren
      </Button>
      <Button size="small" onClick={onCancel}>
        Abbrechen
      </Button>
    </Paper>
  )
}
