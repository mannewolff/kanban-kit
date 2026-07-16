import Box from '@mui/material/Box'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import type { SxProps, Theme } from '@mui/material/styles'
import { epicColor, epicShortcode } from '../lib/epicMeta'

interface Props {
  epicId: number
  title: string
  shortcode: string | null
  sx?: SxProps<Theme>
}

/** Kürzel-Badge eines Epics: farbiger Punkt + Kürzel auf zartem Grund in der Epic-Farbe (Toolbox-Stil). */
export function EpicBadge({ epicId, title, shortcode, sx }: Readonly<Props>) {
  const hue = epicColor(epicId)
  const label = epicShortcode(title, shortcode)
  return (
    <Stack
      direction="row"
      alignItems="center"
      spacing={0.5}
      title={title}
      aria-label={`Epic ${label}`}
      sx={{ width: 'fit-content', px: 0.75, py: 0.25, borderRadius: 1, bgcolor: `${hue}22`, flexShrink: 0, ...sx }}
    >
      <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: hue, flexShrink: 0 }} />
      <Typography variant="caption" sx={{ fontWeight: 700, color: hue, lineHeight: 1 }}>
        {label}
      </Typography>
    </Stack>
  )
}
