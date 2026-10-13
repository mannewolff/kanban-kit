import Alert from '@mui/material/Alert'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import { authApi } from '../api/auth'
import { AuthCard } from '../components/AuthCard'
import { useT } from '../i18n/SpracheContext'

type State = 'notice' | 'verifying' | 'success' | 'error'

/** Zeigt entweder den Bestätigungs-Hinweis oder löst ein Token aus der URL ein. */
export function VerifyNoticePage() {
  const t = useT()
  const [params] = useSearchParams()
  const token = params.get('token')
  const [state, setState] = useState<State>(token ? 'verifying' : 'notice')

  useEffect(() => {
    if (!token) {
      return
    }
    authApi
      .verify(token)
      .then(() => setState('success'))
      .catch(() => setState('error'))
  }, [token])

  return (
    <AuthCard title={t.AUTH.BESTAETIGUNG_TITEL}>
      <Stack spacing={2}>
        {state === 'notice' && (
          <Typography>
            {t.AUTH.BESTAETIGUNG_HINWEIS}
          </Typography>
        )}
        {state === 'verifying' && <Typography>{t.AUTH.BESTAETIGUNG_LAEUFT}</Typography>}
        {state === 'success' && <Alert severity="success">{t.AUTH.BESTAETIGUNG_ERFOLG}</Alert>}
        {state === 'error' && <Alert severity="error">{t.AUTH.BESTAETIGUNG_UNGUELTIG}</Alert>}
        <Link component={RouterLink} to="/login">{t.AUTH.ZUR_ANMELDUNG}</Link>
      </Stack>
    </AuthCard>
  )
}
