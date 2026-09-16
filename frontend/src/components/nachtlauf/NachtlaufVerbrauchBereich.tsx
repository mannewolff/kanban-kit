import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import {
  nightRunUsageApi,
  type NightRunUsageApi,
  type VerbrauchNacht,
} from '../../api/nightRunUsage'
import { NACHTLAUF_FARBEN, NACHTLAUF_SCHRIFTEN } from '../../nachtlaufDesign'
import { NachtlaufVerbrauchNacht } from './NachtlaufVerbrauchNacht'

type Zustand = { art: 'laden' } | { art: 'fehler' } | { art: 'nacht'; nacht: VerbrauchNacht }

/**
 * Der Verbrauchs-Bereich der Nachtlauf-Seite (Issue #941, Plan #933 E13): ein eigener Bereich
 * **innerhalb** der bestehenden Seite und ihres Theme-Teilbaums, keine eigene Route.
 *
 * <p>Gezeigt wird die zuletzt abgeschlossene Nacht. Ihr Datum rechnet nicht der Browser, sondern
 * der Server über den Tageszeitraum mit Rückschritt 0 — die Tagesgrenze 12:00 (Issue #969) steht
 * damit an genau einer Stelle. Der Zeitraum-Teil (#942) setzt an genau diesem Abruf an.
 *
 * <p>Ein Fehler bleibt im Bereich und wandert nicht in die Meldungszeile der Seite: Die übrige
 * Auswertung funktioniert auch ohne ihn.
 */
export function NachtlaufVerbrauchBereich({
  projectId,
  api = nightRunUsageApi,
}: Readonly<{ projectId: number; api?: Pick<NightRunUsageApi, 'night' | 'period'> }>) {
  const [zustand, setZustand] = useState<Zustand>({ art: 'laden' })

  useEffect(() => {
    let aktiv = true
    const laden = async () => {
      const tag = await api.period(projectId, 'DAY', 0)
      if (!aktiv) return
      const nacht = await api.night(projectId, tag.current.firstDay)
      if (aktiv) setZustand({ art: 'nacht', nacht })
    }
    laden().catch(() => {
      if (aktiv) setZustand({ art: 'fehler' })
    })
    return () => {
      aktiv = false
    }
  }, [api, projectId])

  return (
    <Box
      component="section"
      aria-labelledby="verbrauch-ueberschrift"
      data-testid="verbrauch-bereich"
      sx={{ mb: 4 }}
    >
      <Typography
        id="verbrauch-ueberschrift"
        component="h2"
        sx={{
          fontFamily: NACHTLAUF_SCHRIFTEN.display,
          fontWeight: 600,
          fontSize: 22,
          color: NACHTLAUF_FARBEN.ink,
          mb: 1,
        }}
      >
        Verbrauch
      </Typography>
      {zustand.art === 'laden' && <Typography sx={HINWEIS}>Der Verbrauch wird geladen …</Typography>}
      {zustand.art === 'fehler' && (
        <Typography sx={HINWEIS}>Der Verbrauch konnte nicht geladen werden.</Typography>
      )}
      {zustand.art === 'nacht' && <NachtlaufVerbrauchNacht nacht={zustand.nacht} />}
    </Box>
  )
}

const HINWEIS = {
  fontFamily: NACHTLAUF_SCHRIFTEN.body,
  fontSize: 13,
  color: NACHTLAUF_FARBEN.ink3,
} as const
