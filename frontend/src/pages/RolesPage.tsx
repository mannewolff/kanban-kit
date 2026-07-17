import Box from '@mui/material/Box'
import Checkbox from '@mui/material/Checkbox'
import Paper from '@mui/material/Paper'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { rolesApi as defaultRolesApi, type PermissionDef, type RoleMatrix, type RolesApi } from '../api/roles'

const RESOURCE_LABEL: Record<string, string> = {
  BOARD: 'Board',
  EPIC: 'Epic',
  TICKET: 'Ticket',
  COMMENT: 'Kommentar',
  ATTACHMENT: 'Anhang',
  CARD: 'Karte',
  MEMBER: 'Mitglieder',
  PROJECT: 'Projekt',
}

const OPERATION_LABEL: Record<string, string> = {
  CREATE: 'C',
  READ: 'R',
  UPDATE: 'U',
  DELETE: 'D',
  MOVE: 'Move',
  INVITE: 'Einladen',
  REMOVE: 'Entfernen',
  EDIT: 'Bearbeiten',
}

/** Gruppiert die Permissions in der gelieferten Reihenfolge nach Ressource. */
function groupByResource(permissions: PermissionDef[]): { resource: string; perms: PermissionDef[] }[] {
  const groups: { resource: string; perms: PermissionDef[] }[] = []
  for (const p of permissions) {
    const last = groups.at(-1)
    if (last?.resource === p.resource) {
      last.perms.push(p)
    } else {
      groups.push({ resource: p.resource, perms: [p] })
    }
  }
  return groups
}

/**
 * Rechte-Matrix als Checkbox-Grid: Spalten = einzelne Rechte (nach Ressource gruppiert),
 * Zeilen = Rollen. Für die eingebauten Rollen sind die Haken fest (disabled); die Daten
 * kommen aus GET /api/roles/matrix (eine Quelle der Wahrheit). Zusätzliche, konfigurierbare
 * Rollen (mit editierbaren Haken) sind einer späteren Version vorbehalten.
 */
export function RolesPage({ api = defaultRolesApi }: { api?: RolesApi } = {}) {
  const [matrix, setMatrix] = useState<RoleMatrix | null>(null)

  useEffect(() => {
    void api.matrix().then(setMatrix)
  }, [api])

  const groups = matrix ? groupByResource(matrix.permissions) : []

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Rollen & Rechte
      </Typography>

      <Typography variant="subtitle1" sx={{ mt: 1, mb: 1, fontWeight: 600 }}>
        Projekt-Rollen
      </Typography>

      {matrix && (
        <TableContainer component={Paper} variant="outlined" sx={{ maxWidth: '100%', overflowX: 'auto' }}>
          <Table size="small" sx={{ width: 'auto' }}>
            <TableHead>
              <TableRow>
                <TableCell rowSpan={2} sx={{ fontWeight: 600 }}>Rolle</TableCell>
                {groups.map((g) => (
                  <TableCell
                    key={g.resource}
                    align="center"
                    colSpan={g.perms.length}
                    sx={{ fontWeight: 600, borderLeft: 1, borderColor: 'divider' }}
                  >
                    {RESOURCE_LABEL[g.resource] ?? g.resource}
                  </TableCell>
                ))}
              </TableRow>
              <TableRow>
                {matrix.permissions.map((p) => (
                  <TableCell key={p.key} align="center" sx={{ px: 0.5 }}>
                    {OPERATION_LABEL[p.operation] ?? p.operation}
                  </TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {matrix.roles.map((role) => (
                <TableRow key={role}>
                  <TableCell sx={{ fontWeight: 600 }}>{role}</TableCell>
                  {matrix.permissions.map((p) => (
                    <TableCell key={p.key} align="center" padding="none">
                      <Checkbox
                        size="small"
                        disabled
                        checked={matrix.grants[role]?.includes(p.key) ?? false}
                        slotProps={{ input: { 'aria-label': `${p.key} für ${role}` } }}
                      />
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
        Die Haken der eingebauten Rollen sind fest. Zusätzliche, frei konfigurierbare Rollen folgen später.
      </Typography>

      <Typography variant="subtitle1" sx={{ mt: 3, mb: 1, fontWeight: 600 }}>
        Plattform-Rollen
      </Typography>
      <Typography variant="body2" component="div">
        <b>USER</b> — sieht und bearbeitet nur eigene Projekte bzw. Projekte, in denen er Mitglied ist.
      </Typography>
      <Typography variant="body2" component="div" sx={{ mt: 0.5 }}>
        <b>ADMIN</b> — Super-User: Vollzugriff auf <i>alle</i> Projekte, legt Projekte an/löscht sie und
        verwaltet Nutzer (andere zu Admin ernennen).
      </Typography>
    </Box>
  )
}
