import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import { useState } from 'react'
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom'
import { authApi } from '../api/auth'
import { useAuth } from '../auth/AuthContext'
import { AuthCard } from '../components/AuthCard'
import { useT } from '../i18n/SpracheContext'
import { codeFehlermeldung } from '../lib/codeFehlermeldung'

/**
 * Anmeldung per Einmalcode in zwei Schritten (Plan #1801): erst die E-Mail-Adresse, danach die
 * neutrale Antwort und das Feld für den sechsstelligen Code aus der Mail. Kein Passwort.
 * Nach der Registrierung kommt der Code sofort (E3): `state.codeFuer` trägt die Adresse, und die
 * Seite beginnt im Code-Schritt, ohne selbst einen Code anzufordern.
 */
export function LoginPage() {
  const t = useT()
  const { loginWithCode } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const codeFuer = (location.state as { codeFuer?: string } | null)?.codeFuer
  const [email, setEmail] = useState(codeFuer ?? '')
  const [code, setCode] = useState('')
  const [codeAngefordert, setCodeAngefordert] = useState(codeFuer != null && codeFuer !== '')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const fehlerAnzeigen = (e: unknown) => setError(codeFehlermeldung(e, t))

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
