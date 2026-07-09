import Box from '@mui/material/Box'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'

const ROLES = ['VIEWER', 'MEMBER', 'ADMIN', 'OWNER'] as const
type Role = (typeof ROLES)[number]

// Konsistent zur Seed-Matrix (V1__baseline.sql): welche Projekt-Rolle darf was.
const CAPABILITIES: { label: string; allowed: Role[] }[] = [
  { label: 'Boards & Karten lesen', allowed: ['VIEWER', 'MEMBER', 'ADMIN', 'OWNER'] },
  { label: 'Karten anlegen / verschieben / löschen', allowed: ['MEMBER', 'ADMIN', 'OWNER'] },
  { label: 'Kommentare schreiben', allowed: ['MEMBER', 'ADMIN', 'OWNER'] },
  { label: 'Anhänge hochladen', allowed: ['MEMBER', 'ADMIN', 'OWNER'] },
  { label: 'Spalten bearbeiten', allowed: ['ADMIN', 'OWNER'] },
  { label: 'Boards anlegen / löschen', allowed: ['ADMIN', 'OWNER'] },
  { label: 'Mitglieder einladen / entfernen', allowed: ['ADMIN', 'OWNER'] },
  { label: 'Projekt umbenennen / löschen', allowed: ['OWNER'] },
]

export function RolesPage() {
  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Rollen & Rechte
      </Typography>

      <Typography variant="subtitle1" sx={{ mt: 1, mb: 1, fontWeight: 600 }}>
        Projekt-Rollen
      </Typography>
      <Table size="small" sx={{ maxWidth: 720 }}>
        <TableHead>
          <TableRow>
            <TableCell>Recht</TableCell>
            {ROLES.map((r) => (
              <TableCell key={r} align="center">{r}</TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {CAPABILITIES.map((cap) => (
            <TableRow key={cap.label}>
              <TableCell>{cap.label}</TableCell>
              {ROLES.map((r) => (
                <TableCell key={r} align="center" aria-label={`${cap.label} für ${r}`}>
                  {cap.allowed.includes(r) ? '✓' : '–'}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <Typography variant="subtitle1" sx={{ mt: 3, mb: 1, fontWeight: 600 }}>
        Plattform-Rollen
      </Typography>
      <Typography variant="body2" component="div">
        <b>USER</b> — sieht und bearbeitet nur eigene Projekte bzw. Projekte, in denen er Mitglied ist.
      </Typography>
      <Typography variant="body2" component="div" sx={{ mt: 0.5 }}>
        <b>ADMIN</b> — Super-User: Vollzugriff auf <i>alle</i> Projekte und Nutzerverwaltung (andere zu Admin ernennen).
      </Typography>
    </Box>
  )
}
