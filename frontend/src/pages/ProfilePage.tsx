import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import FormControl from '@mui/material/FormControl'
import FormControlLabel from '@mui/material/FormControlLabel'
import FormGroup from '@mui/material/FormGroup'
import FormHelperText from '@mui/material/FormHelperText'
import FormLabel from '@mui/material/FormLabel'
import Stack from '@mui/material/Stack'
import Switch from '@mui/material/Switch'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { authApi } from '../api/auth'
import {
  benachrichtigungenApi,
  type BenachrichtigungsProjekt,
  type MeineBenachrichtigungen,
  type Ruhezeit,
} from '../api/benachrichtigungen'
import { ApiError, apiErrorMessage } from '../api/client'
import { leserZone } from '../api/nightRunUsage'
import { useAuth } from '../auth/AuthContext'
import { useSnackbar } from '../components/SnackbarProvider'
import { VorschauEintraege } from '../components/VorschauEintraege'
import { VorschauPersoenlichHinweis } from '../components/VorschauPersoenlichHinweis'
import { useT } from '../i18n/SpracheContext'

/**
 * Profil des angemeldeten Benutzers: Anzeigenamen ändern und sehen, wann welcher Admin die App als
 * dieses Konto angesehen hat (AK 8). In der „Ansicht als XY“ steht statt beidem nur der Hinweis,
 * dass das Profil von XY in der Vorschau nicht gezeigt wird (AK 5, Plan #1914, E5).
 */
export function ProfilePage() {
  const { vorschau } = useAuth()
  const t = useT()
  if (vorschau) {
    return (
      <Box>
        <Typography variant="h4" gutterBottom>
          {t.PROFIL.TITEL}
        </Typography>
        <VorschauPersoenlichHinweis />
      </Box>
    )
  }
  return <EigenesProfil />
}

function EigenesProfil() {
  const { user, refresh } = useAuth()
  const [displayName, setDisplayName] = useState(user?.displayName ?? '')
  const [busy, setBusy] = useState(false)
  const notify = useSnackbar()
  const t = useT()

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    try {
      await authApi.updateProfile(displayName)
      await refresh()
      notify(t.PROFIL.GESPEICHERT, 'success')
    } catch (e) {
      notify(e instanceof ApiError ? e.message : t.ALLGEMEIN.SPEICHERN_FEHLGESCHLAGEN, 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        {t.PROFIL.TITEL}
      </Typography>
      <Box component="form" onSubmit={handleSubmit} sx={{ maxWidth: 480 }}>
        <Stack spacing={2}>
          <TextField label={t.AUTH.EMAIL} value={user?.email ?? ''} disabled fullWidth />
          <TextField
            label={t.AUTH.ANZEIGENAME}
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            required
            fullWidth
            slotProps={{ htmlInput: { maxLength: 120, 'aria-label': t.AUTH.ANZEIGENAME } }}
          />
          <Button type="submit" variant="contained" disabled={busy || displayName.trim().length === 0}>
            {t.ALLGEMEIN.SPEICHERN}
          </Button>
        </Stack>
      </Box>
      <MailBenachrichtigungen />
      <Box sx={{ maxWidth: 480, mt: 4 }}>
        <Typography variant="h6" component="h2" gutterBottom>
          {t.VORSCHAU.PROTOKOLL_TITEL}
        </Typography>
        <VorschauEintraege laden={authApi.eigenesVorschauProtokoll} />
      </Box>
    </Box>
  )
}

type MailZustand = { art: 'laedt' } | { art: 'fertig'; einstellungen: MeineBenachrichtigungen } | { art: 'fehler' }

/**
 * Die eigenen Mail-Einstellungen (Issue #2082, Plan #2070 E15): Schalter je Projekt und Ruhezeit.
 * Hängt in {@link EigenesProfil}, in der Vorschau wird sie darum weder gezeigt noch geladen.
 */
function MailBenachrichtigungen() {
  const t = useT().PROFIL
  const [zustand, setZustand] = useState<MailZustand>({ art: 'laedt' })

  useEffect(() => {
    let aktiv = true
    benachrichtigungenApi.einstellungen().then(
      (einstellungen) => aktiv && setZustand({ art: 'fertig', einstellungen }),
      () => aktiv && setZustand({ art: 'fehler' }),
    )
    return () => {
      aktiv = false
    }
  }, [])

  return (
    <Box sx={{ maxWidth: 480, mt: 4 }}>
      <Typography variant="h6" component="h2" gutterBottom>
        {t.MAIL_TITEL}
      </Typography>
      {zustand.art === 'fehler' && <Alert severity="error">{t.MAIL_LADEN_FEHLGESCHLAGEN}</Alert>}
      {zustand.art === 'fertig' && (
        <Stack spacing={3}>
          <MailProjekte anfang={zustand.einstellungen.projekte} />
          <RuhezeitFormular gespeichert={zustand.einstellungen.ruhezeit} />
        </Stack>
      )}
    </Box>
  )
}

/** Der Schalter der Person je Projekt; scheitert das Schalten, springt er zurück. */
function MailProjekte({ anfang }: Readonly<{ anfang: BenachrichtigungsProjekt[] }>) {
  const t = useT()
  const notify = useSnackbar()
  const [projekte, setProjekte] = useState(anfang)

  const setzePerson = (projectId: number, personAn: boolean) =>
    setProjekte((liste) => liste.map((p) => (p.projectId === projectId ? { ...p, personAn } : p)))

  const schalte = async (projectId: number, an: boolean) => {
    setzePerson(projectId, an)
    try {
      await benachrichtigungenApi.schaltePerson(projectId, an)
    } catch (e) {
      setzePerson(projectId, !an)
      notify(apiErrorMessage(e, t.ALLGEMEIN.SPEICHERN_FEHLGESCHLAGEN), 'error')
    }
  }

  if (projekte.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        {t.PROFIL.MAIL_KEINE_PROJEKTE}
      </Typography>
    )
  }

  return (
    <FormControl component="fieldset">
      <FormLabel component="legend">{t.PROFIL.MAIL_PROJEKTE}</FormLabel>
      <FormGroup>
        {projekte.map((p) => {
          const hinweisId = `mail-projekt-aus-${p.projectId}`
          return (
            <Box key={p.projectId}>
              <FormControlLabel
                control={
                  <Switch
                    slotProps={{
                      input: { role: 'switch', 'aria-describedby': p.projektAn ? undefined : hinweisId },
                    }}
                    checked={p.personAn}
                    onChange={(e) => void schalte(p.projectId, e.target.checked)}
                  />
                }
                label={p.projectName}
              />
              {!p.projektAn && <FormHelperText id={hinweisId}>{t.PROFIL.MAIL_PROJEKT_AUS}</FormHelperText>}
            </Box>
          )
        })}
      </FormGroup>
    </FormControl>
  )
}

/** Beginn, Ende und Zeitzone der Ruhezeit; ohne gespeicherte Ruhezeit ist die Zone die des Browsers. */
function RuhezeitFormular({ gespeichert }: Readonly<{ gespeichert: Ruhezeit | null }>) {
  const t = useT()
  const notify = useSnackbar()
  const [vorhanden, setVorhanden] = useState(gespeichert !== null)
  const [beginn, setBeginn] = useState(gespeichert?.beginn ?? '')
  const [ende, setEnde] = useState(gespeichert?.ende ?? '')
  const [zeitzone, setZeitzone] = useState(gespeichert?.zeitzone ?? leserZone())
  const [busy, setBusy] = useState(false)

  const zone = zeitzone.trim()
  const vollstaendig = beginn !== '' && ende !== '' && zone !== ''

  /** Führt eine Änderung aus; `true`, wenn sie gelang. Ein Fehler steht als Meldung am Rand. */
  const fuehreAus = async (aenderung: () => Promise<void>) => {
    setBusy(true)
    let gelungen = true
    try {
      await aenderung()
    } catch (e) {
      gelungen = false
      notify(apiErrorMessage(e, t.ALLGEMEIN.SPEICHERN_FEHLGESCHLAGEN), 'error')
    }
    setBusy(false)
    return gelungen
  }

  const speichern = async (event: React.FormEvent) => {
    event.preventDefault()
    if (await fuehreAus(() => benachrichtigungenApi.setzeRuhezeit({ beginn, ende, zeitzone: zone }))) {
      setVorhanden(true)
      notify(t.PROFIL.RUHEZEIT_GESPEICHERT, 'success')
    }
  }

  const loeschen = async () => {
    if (await fuehreAus(benachrichtigungenApi.loescheRuhezeit)) {
      setVorhanden(false)
      setBeginn('')
      setEnde('')
      setZeitzone(leserZone())
      notify(t.PROFIL.RUHEZEIT_GELOESCHT, 'success')
    }
  }

  return (
    <Box component="form" onSubmit={speichern}>
      <Stack spacing={2}>
        <Box>
          <Typography variant="subtitle1" component="h3">
            {t.PROFIL.RUHEZEIT_TITEL}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t.PROFIL.RUHEZEIT_HINWEIS}
          </Typography>
        </Box>
        <Stack direction="row" spacing={2}>
          <TextField
            label={t.PROFIL.RUHEZEIT_BEGINN}
            type="time"
            value={beginn}
            onChange={(e) => setBeginn(e.target.value)}
            required
            fullWidth
            slotProps={{ inputLabel: { shrink: true }, htmlInput: { 'aria-label': t.PROFIL.RUHEZEIT_BEGINN } }}
          />
          <TextField
            label={t.PROFIL.RUHEZEIT_ENDE}
            type="time"
            value={ende}
            onChange={(e) => setEnde(e.target.value)}
            required
            fullWidth
            slotProps={{ inputLabel: { shrink: true }, htmlInput: { 'aria-label': t.PROFIL.RUHEZEIT_ENDE } }}
          />
        </Stack>
        <TextField
          label={t.PROFIL.RUHEZEIT_ZONE}
          value={zeitzone}
          onChange={(e) => setZeitzone(e.target.value)}
          required
          fullWidth
          slotProps={{ htmlInput: { 'aria-label': t.PROFIL.RUHEZEIT_ZONE } }}
        />
        <Stack direction="row" spacing={2}>
          <Button type="submit" variant="contained" disabled={busy || !vollstaendig}>
            {t.PROFIL.RUHEZEIT_SPEICHERN}
          </Button>
          {vorhanden && (
            <Button color="error" disabled={busy} onClick={() => void loeschen()}>
              {t.PROFIL.RUHEZEIT_LOESCHEN}
            </Button>
          )}
        </Stack>
      </Stack>
    </Box>
  )
}
