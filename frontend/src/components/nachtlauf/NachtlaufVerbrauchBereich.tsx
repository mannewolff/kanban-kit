import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import {
  nightRunUsageApi,
  type NightRunUsageApi,
  type VerbrauchNacht,
} from '../../api/nightRunUsage'
import { NACHTLAUF_FARBEN, NACHTLAUF_SCHRIFTEN } from '../../nachtlaufDesign'
import { KupferwarteBereich } from './KupferwarteBereich'
import { NachtlaufVerbrauchNacht } from './NachtlaufVerbrauchNacht'
import { NachtlaufVerbrauchZeitraum } from './NachtlaufVerbrauchZeitraum'

type Zustand = { art: 'laden' } | { art: 'fehler' } | { art: 'nacht'; nacht: VerbrauchNacht }

/**
 * Der Verbrauchs-Bereich der Nachtlauf-Seite (Issues #941, #942, Plan #933 E13): ein eigener
 * Bereich **innerhalb** der bestehenden Seite, keine eigene Route.
 *
 * <p><b>Der Bereich liegt seit #987 auf der Grenze der Nachtlauf-Ausnahme:</b> Die Zeitraum-Sicht
 * folgt Kupferwarte und steht deshalb in einem {@link KupferwarteBereich}; die Nachtansicht
 * darunter bleibt in der Ausnahme und damit im hellen Theme-Teilbaum der Seite.
 *
 * <p>Oben die Zeitraum-Sicht, darunter die Nachtansicht. Beim Öffnen zeigt sie die zuletzt
 * abgeschlossene Nacht — ihr Datum rechnet der Server über den Tageszeitraum mit Rückschritt 0, die
 * Tagesgrenze 12:00 (Issue #969) steht damit an genau einer Stelle. Wird im Zeitraum eine Nacht
 * gewählt, stellt die Nachtansicht auf deren Datum um, ohne die Seite zu verlassen (#926 AK 8).
 *
 * <p>Ein Fehler bleibt im Bereich und wandert nicht in die Meldungszeile der Seite: Die übrige
 * Auswertung funktioniert auch ohne ihn.
 *
 * <p><b>Der Bereich zeigt den Nachtlauf-Anteil</b> (Issue #1016, Plan #1007). Die Endpunkte, die er
 * liest, führen seit Issue #1013 auch die interaktiven Sitzungen; die Festlegung darauf treffen die
 * beiden Unterbausteine {@link NachtlaufVerbrauchZeitraum} und {@link NachtlaufVerbrauchNacht}, die
 * die Zahlen anzeigen. Hier wird allein das Datum der Nacht gelesen, und das trägt keine Gattung.
 */
export function NachtlaufVerbrauchBereich({
  projectId,
  api = nightRunUsageApi,
}: Readonly<{ projectId: number; api?: Pick<NightRunUsageApi, 'night' | 'period'> }>) {
  const [nachtDatum, setNachtDatum] = useState<string | null>(null)
  const [zustand, setZustand] = useState<Zustand>({ art: 'laden' })

  useEffect(() => {
    let aktiv = true
    api.period(projectId, 'DAY', 0).then(
      (tag) => {
        // Hat der Leser inzwischen selbst eine Nacht gewählt, gilt seine Wahl.
        if (aktiv) setNachtDatum((gewaehlt) => gewaehlt ?? tag.current.firstDay)
      },
      () => {
        if (aktiv) setZustand({ art: 'fehler' })
      },
    )
    return () => {
      aktiv = false
    }
  }, [api, projectId])

  useEffect(() => {
    if (nachtDatum === null) {
      return
    }
    let aktiv = true
    setZustand({ art: 'laden' })
    api.night(projectId, nachtDatum).then(
      (nacht) => {
        if (aktiv) setZustand({ art: 'nacht', nacht })
      },
      () => {
        if (aktiv) setZustand({ art: 'fehler' })
      },
    )
    return () => {
      aktiv = false
    }
  }, [api, projectId, nachtDatum])

  return (
    <Box
      component="section"
      aria-labelledby="verbrauch-ueberschrift"
      data-testid="verbrauch-bereich"
      sx={{ mb: 4 }}
    >
      {/* Die Überschrift des Bereichs steht in der Kopfzeile der Zeitraum-Sicht (Mockup). */}
      <KupferwarteBereich>
        <NachtlaufVerbrauchZeitraum projectId={projectId} api={api} onNachtWaehlen={setNachtDatum} />
      </KupferwarteBereich>

      <Box sx={{ mt: 4 }}>
        {zustand.art === 'laden' && (
          <Typography sx={HINWEIS}>Der Verbrauch wird geladen …</Typography>
        )}
        {zustand.art === 'fehler' && (
          <Typography sx={HINWEIS}>Der Verbrauch konnte nicht geladen werden.</Typography>
        )}
        {zustand.art === 'nacht' && <NachtlaufVerbrauchNacht nacht={zustand.nacht} />}
      </Box>
    </Box>
  )
}

const HINWEIS = {
  fontFamily: NACHTLAUF_SCHRIFTEN.body,
  fontSize: 13,
  color: NACHTLAUF_FARBEN.ink3,
} as const
