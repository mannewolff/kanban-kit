import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { anbieterApi, type AnbieterLage, type WartendeKarte } from '../../api/anbieter'
import { auslieferungApi, type AuslieferungZuletzt } from '../../api/auslieferung'
import { useT } from '../../i18n/SpracheContext'
import { useRefetchOnFocus } from '../../lib/useRefetchOnFocus'
import { TABELLENZIFFERN } from '../../theme'

/**
 * Takt, in dem die Seite die Lage neu liest: deutlich unter der Abholfrist des Servers (Vorgabe
 * 6 Minuten, Plan #2206 E12), damit der Hinweis bald nach dem nächsten Lesen verschwindet.
 */
export const LAGE_TAKT_MS = 60_000

/**
 * Oben auf der Startseite des Projekts: was auf den Kunden wartet, die jüngste Vorschau und
 * Veröffentlichung und „gerade nicht erreichbar“ (Issue #2218, Plan #2206 E12, E14; Fachplan #2202
 * Kriterien 3, 4, 10). Was zu zeigen ist, entscheidet der Server; ohne etwas zu zeigen bleibt der
 * Abschnitt weg, und was sich nicht laden lässt, fehlt still.
 */
export function WartetAufDich({ projectId }: Readonly<{ projectId: number }>) {
  const t = useT()
  const [karten, setKarten] = useState<WartendeKarte[]>([])
  const [lage, setLage] = useState<AnbieterLage | null>(null)
  const [auslieferung, setAuslieferung] = useState<AuslieferungZuletzt | null>(null)

  useEffect(() => {
    let aktiv = true
    anbieterApi.wartetAufDich(projectId).then(
      (k) => {
        if (aktiv) setKarten(k)
      },
      () => {},
    )
    auslieferungApi.zuletzt(projectId).then(
      (a) => {
        if (aktiv) setAuslieferung(a)
      },
      () => {},
    )
    return () => {
      aktiv = false
    }
  }, [projectId])

  const leseLage = () => {
    anbieterApi.lage(projectId).then(setLage, () => {})
  }
  useEffect(() => {
    leseLage()
    const takt = setInterval(leseLage, LAGE_TAKT_MS)
    return () => clearInterval(takt)
    // leseLage hängt allein an projectId.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId])
  useRefetchOnFocus(leseLage)

  const vorschau = auslieferung?.vorschau?.url ?? null
  const veroeffentlicht = auslieferung?.veroeffentlichung?.url ?? null
  const nichtErreichbar = lage?.nichtErreichbar === true
  if (karten.length === 0 && vorschau === null && veroeffentlicht === null && !nichtErreichbar) {
    return null
  }

  return (
    <Paper component="section" variant="outlined" aria-label={t.ANBIETER.ABSCHNITT} sx={{ px: 2, py: 1.5, mb: 3 }}>
      <Stack spacing={1.5}>
        {nichtErreichbar && <Alert severity="warning">{t.ANBIETER.NICHT_ERREICHBAR}</Alert>}
        {karten.length > 0 && (
          <Box>
            <Typography component="h2" variant="subtitle1" sx={{ fontWeight: 600, mb: 0.5 }}>
              {t.ANBIETER.WARTET_AUF_DICH}
            </Typography>
            <Stack component="ul" spacing={0.5} sx={{ listStyle: 'none', m: 0, p: 0 }}>
              {karten.map((k) => (
                <Box component="li" key={k.cardId} sx={{ display: 'flex', alignItems: 'baseline', gap: 1 }}>
                  <Link
                    component={RouterLink}
                    to={`/boards/${k.boardId}?karte=${k.number}`}
                  >
                    <Box component="span" sx={TABELLENZIFFERN}>#{k.number}</Box> {k.title}
                  </Link>
                  <Typography component="span" variant="caption" color="text.secondary">
                    {k.grund === 'FREIGABE' ? t.ANBIETER.GRUND_FREIGABE : t.ANBIETER.GRUND_RUECKFRAGE}
                  </Typography>
                </Box>
              ))}
            </Stack>
          </Box>
        )}
        {vorschau !== null && (
          <Link href={vorschau} target="_blank" rel="noopener noreferrer" variant="body2">
            {t.ANBIETER.VORSCHAU_ANSEHEN}
          </Link>
        )}
        {veroeffentlicht !== null && (
          <Typography variant="body2">
            {t.ANBIETER.VEROEFFENTLICHT_UNTER}{' '}
            <Link href={veroeffentlicht} target="_blank" rel="noopener noreferrer">
              {veroeffentlicht}
            </Link>
          </Typography>
        )}
      </Stack>
    </Paper>
  )
}
