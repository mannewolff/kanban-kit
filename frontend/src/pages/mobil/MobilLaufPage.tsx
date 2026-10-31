import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useEffect, useId, useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { mobilApi, type MobileMeldung, type MobilerLaufDetail } from '../../api/mobil'
import { formatierer } from '../../i18n/format'
import { useSprache, useT } from '../../i18n/SpracheContext'
import type { Woerterbuch } from '../../i18n/de'
import type { Sprache } from '../../i18n/woerterbuch'
import { mobilerZustandsText } from '../../lib/nightRunHandoff'

type Stand = { art: 'laedt' } | { art: 'da'; detail: MobilerLaufDetail } | { art: 'fehlt' } | { art: 'fehler' }

/** Der Satz zur letzten Meldung; die Meldung kommt als Daten (Plan #1989 E19). */
function meldungsText(meldung: MobileMeldung | null, t: Woerterbuch['MOBIL'], sprache: Sprache): string {
  const zeit = (wert: string | null) => (wert == null ? '' : formatierer(sprache).datumUhrzeit(wert))
  if (meldung == null) return t.LAEUFE_KEINE_MELDUNG
  switch (meldung.art) {
    case 'ABBRUCHGRUND':
      return meldung.text ?? t.LAEUFE_KEINE_MELDUNG
    case 'PAKET':
      return meldung.cardNumber == null
        ? (meldung.text ?? t.LAEUFE_KEINE_MELDUNG)
        : t.LAEUFE_MELDUNG_PAKET(meldung.cardNumber, meldung.text ?? t.LAEUFE_KEINE_MELDUNG)
    case 'STILLE':
      return t.LAEUFE_MELDUNG_STILLE(zeit(meldung.seit))
    case 'STUFE':
      return meldung.cardNumber == null
        ? t.LAEUFE_MELDUNG_STUFE(meldung.stage ?? '', zeit(meldung.seit))
        : t.LAEUFE_MELDUNG_STUFE_KARTE(meldung.stage ?? '', meldung.cardNumber, zeit(meldung.seit))
  }
}

/** Eine Angabe des Laufs; der Wert trägt die Beschriftung als Namen. */
function Angabe({ titel, wert }: Readonly<{ titel: string; wert: string }>) {
  const id = useId()
  return (
    <Box>
      <Typography component="dt" id={id} variant="body2" color="text.secondary">
        {titel}
      </Typography>
      <Typography component="dd" aria-labelledby={id} sx={{ m: 0, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
        {wert}
      </Typography>
    </Box>
  )
}

/**
 * Ein Lauf auf dem Handy (Issue #1995, Plan #1989 E19): Zustand, letzte Meldung und Dauer, auch
 * wenn er inzwischen beendet ist. Dahin führt der Tipp aus der Liste und später der aus der
 * Nachricht. Nur lesen — kein Knopf zum Schließen oder Quittieren. Einen Lauf, den es nicht gibt
 * oder den das Konto nicht sehen darf, meldet der Server mit 404.
 */
export function MobilLaufPage() {
  const t = useT().MOBIL
  const { sprache } = useSprache()
  const { laufId } = useParams()
  const kennung = Number(laufId)
  const gueltig = Number.isSafeInteger(kennung) && kennung > 0
  const [stand, setStand] = useState<Stand>({ art: 'laedt' })

  useEffect(() => {
    if (!gueltig) return
    let aktiv = true
    setStand({ art: 'laedt' })
    mobilApi.lauf(kennung).then(
      (detail) => {
        if (aktiv) setStand({ art: 'da', detail })
      },
      (fehler: unknown) => {
        if (aktiv) setStand({ art: fehler instanceof ApiError && fehler.status === 404 ? 'fehlt' : 'fehler' })
      },
    )
    return () => {
      aktiv = false
    }
  }, [kennung, gueltig])

  const sicht: Stand = gueltig ? stand : { art: 'fehlt' }

  return (
    <Stack spacing={2}>
      <Link component={RouterLink} to="/mobil/laeufe" variant="body2">
        {t.LAEUFE_ZURUECK}
      </Link>
      {sicht.art === 'laedt' && <CircularProgress sx={{ alignSelf: 'center' }} />}
      {sicht.art === 'fehlt' && <Alert severity="info">{t.LAEUFE_NICHT_GEFUNDEN}</Alert>}
      {sicht.art === 'fehler' && <Alert severity="error">{t.LAEUFE_LAUF_FEHLGESCHLAGEN}</Alert>}
      {sicht.art === 'da' && (
        <>
          <Typography variant="h6" component="h1">
            {t.LAEUFE_ORT(sicht.detail.lauf.projectName, sicht.detail.lauf.boardName ?? t.LAEUFE_ALLE_BOARDS)}
          </Typography>
          <Stack component="dl" spacing={1.5} sx={{ m: 0 }}>
            <Angabe titel={t.LAEUFE_ZUSTAND} wert={mobilerZustandsText(sicht.detail.lauf.zustand, sprache)} />
            <Angabe titel={t.LAEUFE_MELDUNG} wert={meldungsText(sicht.detail.meldung, t, sprache)} />
            <Angabe
              titel={t.LAEUFE_DAUER_TITEL}
              wert={formatierer(sprache).dauer(sicht.detail.lauf.durationMs / 1000)}
            />
          </Stack>
        </>
      )}
    </Stack>
  )
}
