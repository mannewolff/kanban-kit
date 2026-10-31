import Alert from '@mui/material/Alert'
import CircularProgress from '@mui/material/CircularProgress'
import List from '@mui/material/List'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemText from '@mui/material/ListItemText'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { mobilApi, type MobilerLauf } from '../../api/mobil'
import { formatierer } from '../../i18n/format'
import { useSprache, useT } from '../../i18n/SpracheContext'
import { mobilerZustandsText } from '../../lib/nightRunHandoff'

/**
 * Die Läufe auf dem Handy (Issue #1995, Plan #1989 E19): die laufenden, hängenden und gescheiterten
 * Nachtläufe der laufenden und der vorigen Nacht, die das Konto im Leitstand sehen darf. Welche das
 * sind, entscheidet der Server (E9). Nur lesen; ein Tipp öffnet den Lauf.
 */
export function MobilLaeufePage() {
  const t = useT().MOBIL
  const { sprache } = useSprache()
  const [laeufe, setLaeufe] = useState<MobilerLauf[] | null>(null)
  const [ladeFehler, setLadeFehler] = useState(false)

  useEffect(() => {
    let aktiv = true
    mobilApi.laeufe().then(
      (l) => {
        if (aktiv) setLaeufe(l)
      },
      () => {
        if (aktiv) setLadeFehler(true)
      },
    )
    return () => {
      aktiv = false
    }
  }, [])

  const format = formatierer(sprache)

  return (
    <Stack spacing={2}>
      <Typography variant="h6" component="h1">
        {t.LAEUFE_UEBERSCHRIFT}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        {t.LAEUFE_HINWEIS}
      </Typography>
      {ladeFehler && <Alert severity="error">{t.LAEUFE_FEHLGESCHLAGEN}</Alert>}
      {laeufe == null && !ladeFehler && <CircularProgress sx={{ alignSelf: 'center' }} />}
      {laeufe?.length === 0 && (
        <Typography color="text.secondary">{t.LAEUFE_LEER}</Typography>
      )}
      {laeufe != null && laeufe.length > 0 && (
        <List disablePadding>
          {laeufe.map((lauf) => (
            <li key={lauf.laufId}>
              <ListItemButton component={RouterLink} to={`/mobil/laeufe/${lauf.laufId}`} divider>
                <ListItemText
                  primary={t.LAEUFE_ORT(lauf.projectName, lauf.boardName ?? t.LAEUFE_ALLE_BOARDS)}
                  secondary={`${t.LAEUFE_BEGINN(format.datumUhrzeit(lauf.startedAt))} · ${t.LAEUFE_DAUER(
                    format.dauer(lauf.durationMs / 1000),
                  )}`}
                />
                <Typography component="span" variant="body2" sx={{ fontWeight: 600, ml: 2 }}>
                  {mobilerZustandsText(lauf.zustand, sprache)}
                </Typography>
              </ListItemButton>
            </li>
          ))}
        </List>
      )}
    </Stack>
  )
}
