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
import { AMPEL } from '../theme'

const ZEITPUNKT = new Intl.DateTimeFormat('de-DE', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

/**
 * Zustandstext und Ampellampe je Urteil. Der Text steht sichtbar neben dem Punkt **und** im
 * zugänglichen Namen des Bereichs: Der Zustand hängt nie allein an der Farbe.
 */
const ZUSTAND: Readonly<Record<BackupVerdict, { text: string; lampe: keyof typeof AMPEL }>> = {
  OK: { text: 'Läuft', lampe: 'green' },
  VERALTET: { text: 'Veraltet', lampe: 'yellow' },
  FEHLGESCHLAGEN: { text: 'Fehlgeschlagen', lampe: 'red' },
  ABGESCHALTET: { text: 'Abgeschaltet', lampe: 'grey' },
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
function bereichsname(zustand: Zustand): string {
  return zustand.art === 'geladen' ? `Sicherung — ${ZUSTAND[zustand.stand.verdict].text}` : 'Sicherung'
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
            meldung: apiErrorMessage(e, 'Stand der Sicherung konnte nicht geladen werden.'),
          }),
      )
    return () => {
      aktiv = false
    }
  }, [api])

  return (
    <Box component="section" aria-label={bereichsname(zustand)} sx={{ mb: 4 }}>
      <Typography variant="h6" component="h2" gutterBottom>
        Sicherung
      </Typography>
      {zustand.art === 'laedt' && (
        <CircularProgress size={24} aria-label="Stand der Sicherung wird geladen" />
      )}
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
                bgcolor: AMPEL[ZUSTAND[zustand.stand.verdict].lampe],
              }}
            />
            <Typography>{ZUSTAND[zustand.stand.verdict].text}</Typography>
          </Box>
          <Typography variant="body2" color="text.secondary">
            {`Letzter gelungener Lauf: ${zeitpunktText(zustand.stand.kinds)}`}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {`Ziel: ${zustand.stand.targetLabel}`}
          </Typography>
          {!zustand.stand.enabled && (
            <Alert severity="warning">
              Keine Sicherung eingerichtet — es wird nichts gesichert, und es ist nichts
              zurückzuholen.{' '}
              <Link href="/docs/backup" target="_blank" rel="noopener noreferrer">
                Sicherung einrichten
              </Link>
            </Alert>
          )}
          {zustand.stand.enabled && !zustand.stand.alertMailEnabled && (
            <Alert severity="info">
              Der Mailversand ist abgeschaltet: Ein Alarm über eine ausbleibende Sicherung landet nur
              im Protokoll. Diese Ansicht ist der verlässliche Weg.
            </Alert>
          )}
        </Stack>
      )}
    </Box>
  )
}

function zeitpunktText(kinds: BackupKindStatus[]): string {
  const zeitpunkt = letzterErfolg(kinds)
  return zeitpunkt === null ? 'noch keiner' : ZEITPUNKT.format(zeitpunkt)
}
