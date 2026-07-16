import Box from '@mui/material/Box'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import { Link as RouterLink } from 'react-router-dom'

export interface Crumb {
  label: string
  /** Zielpfad; nur Vorsegmente (nicht das letzte) werden als Link gerendert. */
  to?: string
}

interface Props {
  items: Crumb[]
}

/**
 * Vollständiger Breadcrumb-Pfad in Überschrift-Optik: alle Segmente durch „/" getrennt, Vorsegmente
 * mit `to` als Links, nur das letzte Segment fett und als aktuelle Seite ausgezeichnet.
 */
export function Breadcrumbs({ items }: Readonly<Props>) {
  return (
    <Typography variant="h5" component="h1" sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'baseline' }}>
      {items.map((item, index) => {
        const isLast = index === items.length - 1
        // Stabiler key (nicht der Index): so bleibt ein Segment beim Nachladen eines vorgelagerten
        // Segments (z. B. des Projektnamens) dasselbe DOM-Element und wird nicht neu gemountet.
        return (
          <Box
            component="span"
            key={`${item.to ?? ''}|${item.label}`}
            sx={{ display: 'inline-flex', alignItems: 'baseline' }}
          >
            {index > 0 && (
              <Box component="span" aria-hidden sx={{ color: 'text.secondary', fontWeight: 400, mx: 0.75 }}>
                /
              </Box>
            )}
            {item.to && !isLast ? (
              <Link
                component={RouterLink}
                to={item.to}
                underline="hover"
                sx={{ color: 'text.secondary', fontWeight: 400 }}
              >
                {item.label}
              </Link>
            ) : (
              <Box
                component="span"
                aria-current={isLast ? 'page' : undefined}
                sx={{
                  fontWeight: isLast ? 600 : 400,
                  color: isLast ? 'text.primary' : 'text.secondary',
                }}
              >
                {item.label}
              </Box>
            )}
          </Box>
        )
      })}
    </Typography>
  )
}
