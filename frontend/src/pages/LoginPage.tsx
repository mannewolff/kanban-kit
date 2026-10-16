import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import { useState } from 'react'
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom'
import { authApi } from '../api/auth'
import { ApiError, apiErrorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { AuthCard } from '../components/AuthCard'
import { useT } from '../i18n/SpracheContext'

/**
 * Anmeldung per Einmalcode in zwei Schritten (Plan #1801): erst die E-Mail-Adresse, danach die
 * neutrale Antwort und das Feld für den sechsstelligen Code aus der Mail. Kein Passwort.
 */
export function LoginPage() {
  const t = useT()
  const { loginWithCode } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [codeAngefordert, setCodeAngefordert] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const fehlerAnzeigen = (e: unknown) => {
    if (e instanceof ApiError && e.status === 429) {
      // Grenze je Adresse bzw. Bremse je Herkunft: vor dem 401-Zweig, damit niemand weiterprobiert,
      // statt zu warten. Der Server nennt den Fall in `detail`.
      setError(apiErrorMessage(e, t.AUTH.ZU_VIELE_VERSUCHE))
    } else if (e instanceof ApiError && e.status === 401) {
      // Falsch, abgelaufen, verbraucht, ersetzt oder unbekannte Adresse — bewusst ununterscheidbar (E6).
      setError(t.AUTH.CODE_UNGUELTIG)
    } else {
      // 403 (gesperrt, wartet auf Freigabe) trägt die konkrete Meldung in `detail`. Gelesen wird
      // `detail` über apiErrorMessage, nie `message`: das fällt bei unlesbarem Body auf den Roh-Body
      // zurück — bei einem Fehler eines vorgelagerten Proxys stünde sonst dessen HTML im Formular.
      setError(apiErrorMessage(e, t.AUTH.ANMELDUNG_FEHLGESCHLAGEN))
    }
  }

  const codeAnfordern = async () => {
    setError(null)
    setBusy(true)
    try {
      await authApi.requestCode(email)
      setCode('')
      setCodeAngefordert(true)
    } catch (e) {
      fehlerAnzeigen(e)
    } finally {
      setBusy(false)
    }
  }

  const handleEmailSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    void codeAnfordern()
  }

  const handleCodeSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await loginWithCode(email, code)
      // Die vor dem Anmelden aufgerufene Adresse gewinnt (Issue #1082, AK 1 Satz 2): Wer auf eine
      // bestimmte Seite wollte, landet dort und nicht auf der Startseite.
      const vorher = (location.state as { from?: string } | null)?.from
      if (vorher != null && vorher !== '') {
        navigate(vorher, { replace: true })
        return
      }
      // autoRoute-Signal, damit bei genau einem Projekt/Board direkt durchgeroutet wird
      // (nach dem Login ist die Navigation ein Push, also nicht location.key === 'default').
      navigate('/', { replace: true, state: { autoRoute: true } })
    } catch (e) {
      fehlerAnzeigen(e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthCard title={t.AUTH.ANMELDEN}>
      {codeAngefordert ? (
        <Box component="form" onSubmit={handleCodeSubmit}>
          <Stack spacing={2}>
            <Alert severity="info">{t.AUTH.CODE_UNTERWEGS}</Alert>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField label={t.AUTH.CODE} value={code} onChange={(e) => setCode(e.target.value.trim())}
              required fullWidth autoComplete="one-time-code" helperText={t.AUTH.CODE_HINWEIS}
              slotProps={{ htmlInput: { inputMode: 'numeric', pattern: '[0-9]{6}', maxLength: 6 } }} />
            <Button type="submit" variant="contained" disabled={busy} fullWidth>
              {t.AUTH.ANMELDEN}
            </Button>
            <Button onClick={() => void codeAnfordern()} disabled={busy} fullWidth>
              {t.AUTH.NEUEN_CODE_ANFORDERN}
            </Button>
          </Stack>
        </Box>
      ) : (
        <Box component="form" onSubmit={handleEmailSubmit}>
          <Stack spacing={2}>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField label={t.AUTH.EMAIL} type="email" value={email} onChange={(e) => setEmail(e.target.value)}
              required fullWidth autoComplete="email" />
            <Button type="submit" variant="contained" disabled={busy} fullWidth>
              {t.AUTH.CODE_ANFORDERN}
            </Button>
            <Link component={RouterLink} to="/signup">{t.AUTH.REGISTRIEREN}</Link>
          </Stack>
        </Box>
      )}
    </AuthCard>
  )
}
