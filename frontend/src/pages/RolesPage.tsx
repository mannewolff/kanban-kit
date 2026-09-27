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
  // Anzeige Vorhaben; der gespeicherte Schluessel bleibt EPIC_* (V4-Seed, siehe
  // RoleMatrixService) — umbenannt wird nur, was der Nutzer sieht.
  EPIC: 'Vorhaben',
  TICKET: 'Ticket',
  COMMENT: 'Kommentar',
  ATTACHMENT: 'Anhang',
  CARD: 'Karte',
  MEMBER: 'Mitglieder',
  PROJECT: 'Projekt',
  // NIGHT_RUN_* wird zu Ressource NIGHT + Operation RUN_* zerlegt (RoleMatrixService); angezeigt
  // wird der Begriff der Oberflaeche.
  NIGHT: 'Läufe',
}

const OPERATION_LABEL: Record<string, string> = {
  CREATE: 'C',
  READ: 'R',
  UPDATE: 'U',
  DELETE: 'D',
  MOVE: 'Move',
  MOVE_PROJECT: 'Projektwechsel',
  INVITE: 'Einladen',
  REMOVE: 'Entfernen',
  EDIT: 'Bearbeiten',
  RUN_READ: 'Lesen',
  RUN_SUBMIT: 'Einliefern',
}

/**
 * Fussnoten zu Rechten, deren Regel eine Zusatzbedingung traegt, die kein Haken ausdruecken kann
 * (Issue #1165). Die Marke steht am Spaltenkopf, der Text unter der Tabelle.
 */
const FUSSNOTEN: { marke: string; keys: string[]; text: string }[] = [
  {
    marke: '¹',
    keys: ['NIGHT_RUN_READ', 'NIGHT_RUN_SUBMIT'],
    text:
      'Ein Plattform-Admin liest die Auswertung eines Projekts auch ohne OWNER-Rolle — aber ' +
      'nur, wenn das Projekt am Plattform-Leitstand teilnimmt. Einliefern darf er immer.',
  },
  {
    marke: '²',
    keys: ['CARD_MOVE_PROJECT'],
    text: 'Die Rolle OWNER muss im Quell- und im Zielprojekt vorliegen.',
  },
]

/** Marke der Fussnote zu einem Recht, oder undefined, wenn es keine traegt. */
function fussnotenMarke(key: string): string | undefined {
  return FUSSNOTEN.find((f) => f.keys.includes(key))?.marke
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
                    {fussnotenMarke(p.key) && <sup>{fussnotenMarke(p.key)}</sup>}
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

      {FUSSNOTEN.map((f) => (
        <Typography
          key={f.marke}
          variant="caption"
          color="text.secondary"
          sx={{ display: 'block', mt: 1 }}
        >
          <sup>{f.marke}</sup> {f.text}
        </Typography>
      ))}

      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
        Die Haken der eingebauten Rollen sind fest. Zusätzliche, frei konfigurierbare Rollen folgen später.
        Die Teilnahme am Plattform-Leitstand schalten OWNER und Projekt-ADMIN — sie ist kein Recht der
        Matrix und bewusst nicht Sache des Plattform-Admins.
      </Typography>

      <Typography variant="subtitle1" sx={{ mt: 3, mb: 1, fontWeight: 600 }}>
        Plattform-Rollen
      </Typography>
      <Typography variant="body2" component="div">
        <b>USER</b> — sieht und bearbeitet nur eigene Projekte bzw. Projekte, in denen er Mitglied ist.
      </Typography>
      <Typography variant="body2" component="div" sx={{ mt: 0.5 }}>
        <b>ADMIN</b> — Super-User: Vollzugriff auf <i>alle</i> Projekte und Nutzerverwaltung (andere zu
        Admin ernennen).
      </Typography>

      <Typography variant="subtitle2" sx={{ mt: 2, mb: 0.5, fontWeight: 600 }}>
        Nur mit der Plattform-Rolle ADMIN
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
        Keine Projekt-Rolle gewährt das — auch OWNER nicht. Deshalb stehen die beiden Punkte hier und
        nicht als Zeile der Matrix oben.
      </Typography>
      <Typography variant="body2" component="div">
        <b>Plattform-Leitstand ansehen</b> — die Übersicht über die Läufe aller teilnehmenden Projekte.
      </Typography>
      <Typography variant="body2" component="div" sx={{ mt: 0.5 }}>
        <b>Projekt anlegen und löschen</b> — beim Anlegen bestimmt der Plattform-Admin den Owner.
      </Typography>
    </Box>
  )
}
