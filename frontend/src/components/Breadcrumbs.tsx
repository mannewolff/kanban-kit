import Box from '@mui/material/Box'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import { Link as RouterLink } from 'react-router-dom'

export interface Crumb {
  label: string
  /**
   * Zielpfad; als Link gerendert, solange das Segment nicht die aktuelle Seite ist — also alle
   * Vorsegmente, und bei `currentPage={false}` zusätzlich das letzte.
   */
  to?: string
}

interface Props {
  items: Crumb[]
  /** Textgröße; Default `h5` wie in den Seitenüberschriften. */
  variant?: 'h5' | 'body2'
  /**
   * Element des Containers; Default `h1` — der Pfad *ist* die Seitenüberschrift. Bewusst eine
   * kleine Union statt `ElementType`: Ein zweites `h1` innerhalb eines Dialogs wäre ein
   * A11y-Rückschritt, also stehen nur begründete Alternativen zur Wahl. `span` ist die Wahl für
   * einen Pfad innerhalb einer Überschrift (MUI rendert `DialogTitle` als `h2`, das nur
   * Phrasing-Content aufnehmen darf).
   */
  component?: 'h1' | 'span'
  /**
   * Ob das letzte Segment die aktuell angezeigte Seite ist (Default). Bei `false` beschreibt der
   * Pfad nur einen Ort — dann ist auch das letzte Segment mit `to` ein Link und kein Segment als
   * `aria-current` ausgezeichnet (z. B. der Ortspfad im Karten-Detail).
   */
  currentPage?: boolean
}

/**
 * Vollständiger Breadcrumb-Pfad in Überschrift-Optik: alle Segmente durch „/" getrennt, Vorsegmente
 * mit `to` als Links, nur das letzte Segment fett und als aktuelle Seite ausgezeichnet.
 *
 * Über `variant`/`component`/`currentPage` dient dieselbe Komponente auch als reiner Ortspfad
 * innerhalb einer anderen Ansicht (Karten-Detail) — die Defaults bilden unverändert die
 * Seitenüberschrift ab.
 */
export function Breadcrumbs({
  items,
  variant = 'h5',
  component = 'h1',
  currentPage = true,
}: Readonly<Props>) {
  return (
    <Typography
      variant={variant}
      component={component}
      sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'baseline' }}
    >
      {items.map((item, index) => {
        const isLast = currentPage && index === items.length - 1
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
