import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import type { AdminApi, OverloadRejection } from '../api/admin'
import { DataTable, type DataTableColumn } from './DataTable'

const STUNDE = new Intl.DateTimeFormat('de-DE', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

type Zustand =
  | { art: 'laedt' }
  | { art: 'fehler' }
  | { art: 'geladen'; zeilen: OverloadRejection[] }

interface Props {
  api: Pick<AdminApi, 'listOverloadRejections'>
}

const spalten: DataTableColumn<OverloadRejection>[] = [
  { key: 'person', header: 'Person', render: (r) => r.displayName, defaultWidth: 200, minWidth: 120, resizable: true },
  { key: 'stunde', header: 'Stunde', render: (r) => STUNDE.format(new Date(r.hour)), defaultWidth: 180, resizable: true },
  { key: 'anzahl', header: 'Anzahl', render: (r) => r.rejections, align: 'right', defaultWidth: 100 },
]

/**
 * Abschnitt der Admin-Seite: wann und bei wem die Durchsatzbremse wegen Last abgewiesen hat, je
 * Person und Stunde, die jüngste Stunde zuerst (Issue #1003, Plan #995 E20).
 */
export function OverloadRejectionsSection({ api }: Readonly<Props>) {
  const [zustand, setZustand] = useState<Zustand>({ art: 'laedt' })

  useEffect(() => {
    let aktiv = true
    api
      .listOverloadRejections()
      .then((zeilen) => aktiv && setZustand({ art: 'geladen', zeilen }))
      .catch(() => aktiv && setZustand({ art: 'fehler' }))
    return () => {
      aktiv = false
    }
  }, [api])

  return (
    <Box component="section" aria-labelledby="abweisungen-last" sx={{ mt: 4 }}>
      <Typography id="abweisungen-last" variant="h6" component="h2" gutterBottom>
        Abweisungen wegen Last
      </Typography>
      {zustand.art === 'laedt' && <CircularProgress size={24} aria-label="Abweisungen werden geladen" />}
      {zustand.art === 'fehler' && (
        <Alert severity="error">Abweisungen wegen Last konnten nicht geladen werden.</Alert>
      )}
      {zustand.art === 'geladen' && zustand.zeilen.length === 0 && (
        <Typography color="text.secondary">Keine Abweisungen wegen Last in den letzten 90 Tagen.</Typography>
      )}
      {zustand.art === 'geladen' && zustand.zeilen.length > 0 && (
        <DataTable
          columns={spalten}
          rows={zustand.zeilen}
          getRowKey={(r) => `${r.userId}-${r.hour}`}
          storageKey="admin-overload-rejections"
        />
      )}
    </Box>
  )
}
