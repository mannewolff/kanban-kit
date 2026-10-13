import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import type { AdminApi, OverloadRejection } from '../api/admin'
import { formatierer } from '../i18n/format'
import { useSprache, useT } from '../i18n/SpracheContext'
import type { Sprache } from '../i18n/woerterbuch'
import { DataTable, type DataTableColumn } from './DataTable'

/** Die Stunde minutengenau: `22.09.2026, 10:00` bzw. `Sep 22, 2026, 10:00 AM`. */
const STUNDE: Readonly<Record<Sprache, Intl.DateTimeFormatOptions>> = {
  de: { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' },
  en: { dateStyle: 'medium', timeStyle: 'short' },
}

type Zustand =
  | { art: 'laedt' }
  | { art: 'fehler' }
  | { art: 'geladen'; zeilen: OverloadRejection[] }

interface Props {
  api: Pick<AdminApi, 'listOverloadRejections'>
}


/**
 * Abschnitt der Admin-Seite: wann und bei wem die Durchsatzbremse wegen Last abgewiesen hat, je
 * Person und Stunde, die jüngste Stunde zuerst (Issue #1003, Plan #995 E20).
 */
export function OverloadRejectionsSection({ api }: Readonly<Props>) {
  const [zustand, setZustand] = useState<Zustand>({ art: 'laedt' })
  const t = useT().ABWEISUNGEN
  const { sprache } = useSprache()
  const stunde = new Intl.DateTimeFormat(formatierer(sprache).gebietsschema, STUNDE[sprache])
  const spalten: DataTableColumn<OverloadRejection>[] = [
    { key: 'person', header: t.PERSON, render: (r) => r.displayName, defaultWidth: 200, minWidth: 120, resizable: true },
    { key: 'stunde', header: t.STUNDE, render: (r) => stunde.format(new Date(r.hour)), defaultWidth: 180, resizable: true },
    { key: 'anzahl', header: t.ANZAHL, render: (r) => r.rejections, align: 'right', defaultWidth: 100 },
  ]

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
        {t.TITEL}
      </Typography>
      {zustand.art === 'laedt' && <CircularProgress size={24} aria-label={t.LAEDT} />}
      {zustand.art === 'fehler' && <Alert severity="error">{t.FEHLER}</Alert>}
      {zustand.art === 'geladen' && zustand.zeilen.length === 0 && (
        <Typography color="text.secondary">{t.LEER}</Typography>
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
