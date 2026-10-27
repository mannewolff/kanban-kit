import Alert from '@mui/material/Alert'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import type { VorschauEintrag } from '../api/auth'
import { formatierer } from '../i18n/format'
import { useSprache, useT } from '../i18n/SpracheContext'

interface VorschauEintraegeProps {
  /** Lädt die Einträge eines Kontos — in der Nutzerliste für XY, im Profil für das eigene Konto. */
  laden: () => Promise<VorschauEintrag[]>
}

type Zustand = { art: 'laedt' } | { art: 'fertig'; eintraege: VorschauEintrag[] } | { art: 'fehler' }

/**
 * Die Einträge, wann welcher Admin die App als ein Konto angesehen hat (AK 8, Plan #1914, E12):
 * Admin, Start, Ende und Endgrund. Ein gelöschter Admin steht weiter mit Namen und dem Vermerk
 * „Konto gelöscht“. Lädt einmal beim Einhängen.
 */
export function VorschauEintraege({ laden }: Readonly<VorschauEintraegeProps>) {
  const t = useT()
  const { sprache } = useSprache()
  const [zustand, setZustand] = useState<Zustand>({ art: 'laedt' })

  useEffect(() => {
    let aktiv = true
    laden().then(
      (eintraege) => aktiv && setZustand({ art: 'fertig', eintraege }),
      () => aktiv && setZustand({ art: 'fehler' }),
    )
    return () => {
      aktiv = false
    }
  }, [laden])

  if (zustand.art === 'fehler') {
    return <Alert severity="error">{t.VORSCHAU.PROTOKOLL_LADEN_FEHLGESCHLAGEN}</Alert>
  }
  if (zustand.art === 'laedt') {
    return null
  }
  if (zustand.eintraege.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        {t.VORSCHAU.PROTOKOLL_LEER}
      </Typography>
    )
  }

  const zeit = formatierer(sprache).datumUhrzeit
  return (
    <Stack component="ul" spacing={0.5} sx={{ listStyle: 'none', m: 0, p: 0 }}>
      {zustand.eintraege.map((e) => (
        <Typography key={`${e.gestartetAm}-${e.adminName}`} component="li" variant="body2">
          {e.adminKontoGeloescht ? `${e.adminName} (${t.VORSCHAU.KONTO_GELOESCHT})` : e.adminName}
          {' · '}
          {t.VORSCHAU.START(zeit(e.gestartetAm))}
          {' · '}
          {e.beendetAm === null || e.endgrund === null
            ? t.VORSCHAU.LAEUFT
            : `${t.VORSCHAU.ENDE(zeit(e.beendetAm))} (${t.VORSCHAU.ENDGRUND[e.endgrund]})`}
        </Typography>
      ))}
    </Stack>
  )
}
