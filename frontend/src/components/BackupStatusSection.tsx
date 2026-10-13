import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import {
  backupApi,
  type BackupApi,
  type BackupKindStatus,
  type BackupStatus,
  type BackupVerdict,
} from '../api/backup'
import { apiErrorMessage } from '../api/client'
import { formatierer } from '../i18n/format'
import { useSprache, useT } from '../i18n/SpracheContext'
import type { Woerterbuch } from '../i18n/de'
import { woerterbuch, type Sprache } from '../i18n/woerterbuch'
import { AMPEL } from '../theme'

/** Der Zeitpunkt minutengenau: `23.09.2026, 05:00` bzw. `Sep 23, 2026, 5:00 AM`. */
const ZEITPUNKT: Readonly<Record<Sprache, Intl.DateTimeFormatOptions>> = {
  de: { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' },
  en: { dateStyle: 'medium', timeStyle: 'short' },
}

/**
 * Ampellampe je Urteil; der Zustandstext steht im Wörterbuch (`SICHERUNG.ZUSTAND`). Er steht
 * sichtbar neben dem Punkt **und** im zugänglichen Namen des Bereichs: Der Zustand hängt nie allein
 * an der Farbe.
 */
const LAMPE: Readonly<Record<BackupVerdict, keyof typeof AMPEL>> = {
  OK: 'green',
  VERALTET: 'yellow',
  FEHLGESCHLAGEN: 'red',
  ABGESCHALTET: 'grey',
}

type Zustand =
  | { art: 'laedt' }
  | { art: 'fehler'; meldung: string }
  | { art: 'geladen'; stand: BackupStatus }

interface Props {
  api?: Pick<BackupApi, 'getStatus'>
}

/**
 * Der jüngste gelungene Lauf über alle Arten hinweg — „die letzte Sicherung" der fachlichen Quelle
 * (#823 AK10). Verglichen wird über {@link Date.parse} und nicht über die Zeichenketten: Instants
 * kommen mal mit, mal ohne Sekundenbruchteil, und lexikalisch sortierte `…:00Z` vor `…:00.5Z`.
 */
function letzterErfolg(kinds: BackupKindStatus[]): Date | null {
  const zeiten = kinds
    .map((k) => k.lastSuccessAt)
    .filter((z): z is string => z !== null)
    .map((z) => Date.parse(z))
  return zeiten.length === 0 ? null : new Date(Math.max(...zeiten))
}

/** Der Name des Bereichs trägt den Zustand mit — sonst bliebe er dem Screenreader die Ampel schuldig. */
function bereichsname(zustand: Zustand, t: Woerterbuch['SICHERUNG']): string {
  return zustand.art === 'geladen' ? t.BEREICH(t.ZUSTAND[zustand.stand.verdict]) : t.TITEL
}

/**
 * Statuskachel der Sicherung für den Plattform-Admin (Issue #833, Plan #825 E6/E11).
 *
 * <p>Eigenständig und mit eigener `api`-Prop, nicht in die Seite hineingeschrieben: Die Admin-Seite
 * bekommt ihre `AdminApi` als Ganzes injiziert, ein zweiter Abruf in derselben Komponente bräche
 * deren Tests.
 *
 * <p>Die Kachel ist auch dann wichtig, wenn nichts eingerichtet ist: Ausgeliefert wird ohne
 * Sicherung, aber sichtbar ohne (E6). Und ist der Mailversand aus, verpufft der Alarm im Protokoll
 * (E11) — dann ist diese Ansicht der einzige verlässliche Weg, und sie sagt das.
 */
export function BackupStatusSection({ api = backupApi }: Readonly<Props>) {
  const [zustand, setZustand] = useState<Zustand>({ art: 'laedt' })
  const t = useT().SICHERUNG
  const { sprache } = useSprache()
  const ladefehler = t.LADEN_FEHLGESCHLAGEN

  useEffect(() => {
    let aktiv = true
    api
      .getStatus()
      .then((stand) => aktiv && setZustand({ art: 'geladen', stand }))
      .catch(
        (e: unknown) =>
          aktiv &&
          setZustand({
            art: 'fehler',
            meldung: apiErrorMessage(e, ladefehler),
          }),
      )
    return () => {
      aktiv = false
    }
  }, [api, ladefehler])

  return (
    <Box component="section" aria-label={bereichsname(zustand, t)} sx={{ mb: 4 }}>
      <Typography variant="h6" component="h2" gutterBottom>
        {t.TITEL}
      </Typography>
      {zustand.art === 'laedt' && <CircularProgress size={24} aria-label={t.LAEDT} />}
      {zustand.art === 'fehler' && <Alert severity="error">{zustand.meldung}</Alert>}
      {zustand.art === 'geladen' && (
        <Stack spacing={1.5} alignItems="flex-start">
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Box
              aria-hidden
              data-testid="sicherung-ampel"
              sx={{
                width: 12,
                height: 12,
                borderRadius: '50%',
                flexShrink: 0,
                bgcolor: AMPEL[LAMPE[zustand.stand.verdict]],
              }}
            />
            <Typography>{t.ZUSTAND[zustand.stand.verdict]}</Typography>
          </Box>
          <Typography variant="body2" color="text.secondary">
            {t.LETZTER_LAUF(zeitpunktText(zustand.stand.kinds, sprache))}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t.ZIEL(zustand.stand.targetLabel)}
          </Typography>
          {!zustand.stand.enabled && (
            <Alert severity="warning">
              {t.NICHT_EINGERICHTET}{' '}
              <Link href="/docs/backup" target="_blank" rel="noopener noreferrer">
                {t.EINRICHTEN}
              </Link>
            </Alert>
          )}
          {zustand.stand.enabled && !zustand.stand.alertMailEnabled && (
            <Alert severity="info">
              {t.MAIL_AUS}
            </Alert>
          )}
        </Stack>
      )}
    </Box>
  )
}

function zeitpunktText(kinds: BackupKindStatus[], sprache: Sprache): string {
  const zeitpunkt = letzterErfolg(kinds)
  return zeitpunkt === null
    ? woerterbuch(sprache).SICHERUNG.NOCH_KEINER
    : new Intl.DateTimeFormat(formatierer(sprache).gebietsschema, ZEITPUNKT[sprache]).format(zeitpunkt)
}
