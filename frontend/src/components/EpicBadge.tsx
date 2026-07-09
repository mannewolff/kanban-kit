import Box from '@mui/material/Box'
import { epicColor, epicShortcode } from '../lib/epicMeta'

interface Props {
  epicId: number
  title: string
  shortcode: string | null
}

/** Kleines farbiges Kürzel-Badge eines Epics, angezeigt auf zugeordneten Karten. */
export function EpicBadge({ epicId, title, shortcode }: Props) {
  const label = epicShortcode(title, shortcode)
  return (
    <Box
      component="span"
      title={title}
      sx={{
        display: 'inline-block',
        bgcolor: epicColor(epicId),
        color: '#fff',
        fontSize: 10,
        fontWeight: 700,
        letterSpacing: '.03em',
        px: 0.75,
        py: 0.125,
        borderRadius: 0.75,
      }}
    >
      {label}
    </Box>
  )
}
