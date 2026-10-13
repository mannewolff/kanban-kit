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
import type { Woerterbuch } from '../i18n/de'
import { useT } from '../i18n/SpracheContext'

/**
 * Fussnoten zu Rechten, deren Regel eine Zusatzbedingung traegt, die kein Haken ausdruecken kann
 * (Issue #1165). Die Marke steht am Spaltenkopf, der Text unter der Tabelle; er kommt aus dem
 * Woerterbuch (`ROLLEN.FUSSNOTE`).
 */
const FUSSNOTEN: { marke: string; keys: string[]; text: keyof Woerterbuch['ROLLEN']['FUSSNOTE'] }[] = [
  { marke: '¹', keys: ['NIGHT_RUN_READ', 'NIGHT_RUN_SUBMIT'], text: 'LAEUFE' },
  { marke: '²', keys: ['CARD_MOVE_PROJECT'], text: 'PROJEKTWECHSEL' },
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
  const t = useT()
  const [matrix, setMatrix] = useState<RoleMatrix | null>(null)

  useEffect(() => {
    void api.matrix().then(setMatrix)
  }, [api])

  const groups = matrix ? groupByResource(matrix.permissions) : []
  // Anzeige Vorhaben; der gespeicherte Schluessel bleibt EPIC_* (V4-Seed, siehe RoleMatrixService) —
  // umbenannt wird nur, was der Nutzer sieht. NIGHT_RUN_* wird zu Ressource NIGHT + Operation RUN_*
  // zerlegt; unbekannte Schluessel zeigen sich roh.
  const ressourcen: Readonly<Record<string, string>> = t.ROLLEN.RESSOURCE
  const operationen: Readonly<Record<string, string>> = t.ROLLEN.OPERATION

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        {t.RAHMEN.NAV_ROLLEN}
      </Typography>

      <Typography variant="subtitle1" sx={{ mt: 1, mb: 1, fontWeight: 600 }}>
        {t.ROLLEN.PROJEKT_ROLLEN}
      </Typography>

      {matrix && (
        <TableContainer component={Paper} variant="outlined" sx={{ maxWidth: '100%', overflowX: 'auto' }}>
          <Table size="small" sx={{ width: 'auto' }}>
            <TableHead>
              <TableRow>
                <TableCell rowSpan={2} sx={{ fontWeight: 600 }}>{t.ALLGEMEIN.ROLLE}</TableCell>
                {groups.map((g) => (
                  <TableCell
                    key={g.resource}
                    align="center"
                    colSpan={g.perms.length}
                    sx={{ fontWeight: 600, borderLeft: 1, borderColor: 'divider' }}
                  >
                    {ressourcen[g.resource] ?? g.resource}
                  </TableCell>
                ))}
              </TableRow>
              <TableRow>
                {matrix.permissions.map((p) => (
                  <TableCell key={p.key} align="center" sx={{ px: 0.5 }}>
                    {operationen[p.operation] ?? p.operation}
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
                        slotProps={{ input: { 'aria-label': t.ROLLEN.RECHT_FUER(p.key, role) } }}
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
          <sup>{f.marke}</sup> {t.ROLLEN.FUSSNOTE[f.text]}
        </Typography>
      ))}

      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
        {t.ROLLEN.HAKEN_FEST}
      </Typography>

      <Typography variant="subtitle1" sx={{ mt: 3, mb: 1, fontWeight: 600 }}>
        {t.ROLLEN.PLATTFORM_ROLLEN}
      </Typography>
      <Typography variant="body2" component="div">
        <b>USER</b> — {t.ROLLEN.USER_TEXT}
      </Typography>
      <Typography variant="body2" component="div" sx={{ mt: 0.5 }}>
        <b>ADMIN</b> — {t.ROLLEN.ADMIN_TEXT_VOR} <i>{t.ROLLEN.ADMIN_TEXT_BETONT}</i> {t.ROLLEN.ADMIN_TEXT_NACH}
      </Typography>

      <Typography variant="subtitle2" sx={{ mt: 2, mb: 0.5, fontWeight: 600 }}>
        {t.ROLLEN.NUR_ADMIN_TITEL}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
        {t.ROLLEN.NUR_ADMIN_HINWEIS}
      </Typography>
      <Typography variant="body2" component="div">
        <b>{t.ROLLEN.LEITSTAND_ANSEHEN}</b> — {t.ROLLEN.LEITSTAND_ANSEHEN_TEXT}
      </Typography>
      <Typography variant="body2" component="div" sx={{ mt: 0.5 }}>
        <b>{t.ROLLEN.PROJEKT_ANLEGEN}</b> — {t.ROLLEN.PROJEKT_ANLEGEN_TEXT}
      </Typography>
    </Box>
  )
}
