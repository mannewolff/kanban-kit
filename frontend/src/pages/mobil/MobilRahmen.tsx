import LightbulbOutlined from '@mui/icons-material/LightbulbOutlined'
import ListAltOutlined from '@mui/icons-material/ListAltOutlined'
import SettingsOutlined from '@mui/icons-material/SettingsOutlined'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'
import { Suspense, useEffect, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { useT } from '../../i18n/SpracheContext'
import { auffrischen } from '../../lib/pushAbo'
import { ANZEIGE, GRUND, KOPFFARBE, PLATTE, RAND } from '../../theme'

/** Legt ein Element für den Kopf mit den genannten Attributen an. */
function kopfElement(tag: 'link' | 'meta', attribute: Readonly<Record<string, string>>): HTMLElement {
  const element = document.createElement(tag)
  for (const [name, wert] of Object.entries(attribute)) {
    element.setAttribute(name, wert)
  }
  return element
}

/** Ein Eintrag der Leiste unten; `NavLink` setzt am aktiven `aria-current="page"`. */
const LEISTEN_EINTRAG_SX = {
  flex: 1,
  flexDirection: 'column',
  gap: 0.25,
  py: 1,
  borderRadius: 0,
  color: 'text.secondary',
  '&[aria-current="page"]': { color: 'primary.main' },
} as const

/**
 * Der schmale Rahmen der mobilen Oberfläche unter `/mobil` (Plan #1989, E3): oben Name und
 * Abmelden, darunter die Seite, unten die Leiste „Idee“, „Läufe“ und „Einstellungen“. Keine Seitenleiste,
 * kein Board — und nichts, womit sich Karten verschieben, bearbeiten, archivieren oder löschen
 * ließen (AK 8 der Quelle #1961).
 *
 * Die Angaben für den Startbildschirm hängt der Rahmen selbst in den Kopf und nimmt sie beim
 * Aushängen wieder ab (E4): Stünden sie in `index.html`, böte der Browser den ganzen Leitstand zur
 * Installation an, und der große Bildschirm änderte sich.
 */
export function MobilRahmen() {
  const t = useT()
  const { logout, vorschau } = useAuth()
  const navigate = useNavigate()
  const [abmeldenFehler, setAbmeldenFehler] = useState(false)

  // Das Abo für Benachrichtigungen gilt bis zum Ende der Sitzung; jedes Öffnen verlängert es auf die
  // laufende (Plan #1989, E14). Scheitert das, bleibt das alte Abo bis zu seinem Ende bestehen.
  useEffect(() => {
    auffrischen(vorschau).catch(() => undefined)
  }, [vorschau])

  useEffect(() => {
    const elemente = [
      kopfElement('link', { rel: 'manifest', href: '/mobil-manifest.json' }),
      kopfElement('meta', { name: 'theme-color', content: KOPFFARBE }),
      kopfElement('meta', { name: 'apple-mobile-web-app-capable', content: 'yes' }),
    ]
    elemente.forEach((element) => document.head.appendChild(element))
    return () => elemente.forEach((element) => element.remove())
  }, [])

  // Wie in der AppShell: Scheitert das Abmelden, bleibt man angemeldet auf der Seite.
  const abmelden = async () => {
    setAbmeldenFehler(false)
    try {
      await logout()
      navigate('/login')
    } catch {
      setAbmeldenFehler(true)
    }
  }

  return (
    <Box sx={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column', bgcolor: GRUND }}>
      <Box
        component="header"
        sx={{
          display: 'flex',
          alignItems: 'center',
          px: 2,
          pt: 'max(8px, env(safe-area-inset-top))',
          pb: 1,
          bgcolor: PLATTE,
          borderBottom: 1,
          borderColor: RAND,
        }}
      >
        <Typography component="p" sx={{ ...ANZEIGE, fontWeight: 600, flexGrow: 1 }}>
          {t.APP_NAME}
        </Typography>
        <Button size="small" onClick={() => void abmelden()}>
          {t.MOBIL.ABMELDEN}
        </Button>
      </Box>
      {abmeldenFehler && (
        <Alert severity="error" sx={{ m: 2, mb: 0 }}>
          {t.MOBIL.ABMELDEN_FEHLGESCHLAGEN}
        </Alert>
      )}
      <Box component="main" sx={{ flexGrow: 1, width: '100%', maxWidth: 560, mx: 'auto', p: 2 }}>
        <Suspense fallback={null}>
          <Outlet />
        </Suspense>
      </Box>
      <Box
        component="nav"
        aria-label={t.MOBIL.NAVIGATION}
        sx={{
          position: 'sticky',
          bottom: 0,
          display: 'flex',
          pb: 'env(safe-area-inset-bottom)',
          bgcolor: PLATTE,
          borderTop: 1,
          borderColor: RAND,
        }}
      >
        <Button component={NavLink} to="/mobil" end sx={LEISTEN_EINTRAG_SX}>
          <LightbulbOutlined aria-hidden />
          {t.MOBIL.NAV_IDEE}
        </Button>
        <Button component={NavLink} to="/mobil/laeufe" sx={LEISTEN_EINTRAG_SX}>
          <ListAltOutlined aria-hidden />
          {t.MOBIL.NAV_LAEUFE}
        </Button>
        <Button component={NavLink} to="/mobil/einstellungen" sx={LEISTEN_EINTRAG_SX}>
          <SettingsOutlined aria-hidden />
          {t.MOBIL.NAV_EINSTELLUNGEN}
        </Button>
      </Box>
    </Box>
  )
}
