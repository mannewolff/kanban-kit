import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import { useCallback, useEffect, useState, type ComponentProps, type ComponentType } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import SwaggerUI from 'swagger-ui-react'
import 'swagger-ui-react/swagger-ui.css'
import { apiErrorMessage } from '../api/client'
import { OPENAPI_JSON_PFAD, OPENAPI_YAML_PFAD, openapiApi } from '../api/openapi'
import { useAuth } from '../auth/AuthContext'
import { AusprobierBestaetigung } from '../components/AusprobierBestaetigung'
import { ABGEBROCHEN, HINWEIS_AENDERND, istAendernd, markiere } from '../lib/apiAusprobieren'
import { isPlatformAdmin } from '../lib/roles'

/**
 * Blendet den „Authorize“-Knopf für Nicht-Admins aus (Plan #1400 E5): Ausprobieren ist für sie
 * aus, also gibt es auch nichts anzumelden. Die Sicherheitsschemata bleiben in der Beschreibung lesbar.
 */
const OHNE_AUTHORIZE = { wrapComponents: { authorizeBtn: () => () => null } }

/** Keine HTTP-Methode ist ausführbar — „Try it out“ entfällt (Plan #1400 E5, Erweiterung #1366). */
const KEINE_AUSFUEHRUNG: [] = []

/** Plattform-Admins dürfen jede Methode ausprobieren (Plan #1437, E11). */
const ALLE_METHODEN: ('get' | 'put' | 'post' | 'delete' | 'options' | 'head' | 'patch' | 'trace')[] = [
  'get',
  'put',
  'post',
  'delete',
  'options',
  'head',
  'patch',
  'trace',
]

type ExecuteProps = { method: string }

/**
 * Setzt den Hinweis unmittelbar vor den Knopf „Ausführen“ eines ändernden Aufrufs (Plan #1437,
 * „Bestätigung im Interceptor, Hinweis am Knopf“). Der Authorize-Dialog bleibt sichtbar (E7/E8).
 */
const AUSPROBIEREN = {
  wrapComponents: {
    execute: (Original: ComponentType<ExecuteProps>) =>
      function MitHinweis(props: ExecuteProps) {
        return (
          <>
            {istAendernd(props.method) && (
              <Alert severity="warning" sx={{ my: 1 }}>
                {HINWEIS_AENDERND}
              </Alert>
            )}
            <Original {...props} />
          </>
        )
      },
  },
}

/** Abfang-Haken der Swagger UI; seine Anfrage ist dort nur lose typisiert. */
type Interceptor = NonNullable<ComponentProps<typeof SwaggerUI>['requestInterceptor']>

/** Die Felder der ausgehenden Anfrage, die die Seite liest. */
type SwaggerAnfrage = { url: string; method: string; headers?: Record<string, string> }

/** Offene Rückfrage vor einem ändernden Aufruf samt den Ausgängen ihres Promise. */
interface Rueckfrage {
  methode: string
  pfad: string
  absenden: () => void
  abbrechen: () => void
}

/** Pfad samt Abfrage, wie er im Dialog steht — Swagger UI liefert die URL teils absolut. */
function pfadVon(url: string): string {
  const u = new URL(url, globalThis.location.origin)
  return u.pathname + u.search
}

/**
 * Übersicht der Schnittstelle (Issue #1410): Swagger UI über der Spezifikation aus
 * {@link openapiApi.lade}, dazu die Downloads und der Weg zurück in die Administration.
 * Plattform-Admins probieren Aufrufe aus (Issue #1441); für alle anderen bleibt sie rein lesend.
 */
export function ApiUebersichtPage() {
  const [spec, setSpec] = useState<object | null>(null)
  const [fehler, setFehler] = useState<string | null>(null)
  const [rueckfrage, setRueckfrage] = useState<Rueckfrage | null>(null)
  const { user } = useAuth()
  const admin = isPlatformAdmin(user)

  // Jede Anfrage trägt das Kennzeichen, das der Server prüft (#1438). Ein ändernder Aufruf geht
  // erst nach „Absenden“ ab; „Abbrechen“ verwirft ihn, und Swagger UI zeigt ABGEBROCHEN an.
  const requestInterceptor = useCallback<Interceptor>((roh) => {
    const anfrage = roh as SwaggerAnfrage
    const markiert = markiere(anfrage)
    if (!istAendernd(anfrage.method)) return markiert
    return new Promise<typeof markiert>((resolve, reject) => {
      setRueckfrage({
        methode: anfrage.method,
        pfad: pfadVon(anfrage.url),
        absenden: () => {
          setRueckfrage(null)
          resolve(markiert)
        },
        abbrechen: () => {
          setRueckfrage(null)
          reject(new Error(ABGEBROCHEN))
        },
      })
    })
  }, [])

  useEffect(() => {
    openapiApi.lade().then(setSpec, (e: unknown) =>
      setFehler(apiErrorMessage(e, 'Die API-Beschreibung konnte nicht geladen werden.')),
    )
  }, [])

  return (
    <Box>
      <Stack direction="row" alignItems="center" spacing={2} sx={{ mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="h4" component="h1" sx={{ flexGrow: 1 }}>
          API-Schnittstelle
        </Typography>
        <Button component={RouterLink} to="/administration" startIcon={<ArrowBackIcon />}>
          Zurück zur Administration
        </Button>
      </Stack>
      <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
        <Link href={OPENAPI_JSON_PFAD} download aria-label="OpenAPI als JSON herunterladen">
          OpenAPI (JSON)
        </Link>
        <Link href={OPENAPI_YAML_PFAD} download aria-label="OpenAPI als YAML herunterladen">
          OpenAPI (YAML)
        </Link>
      </Stack>
      {fehler !== null && <Alert severity="error">{fehler}</Alert>}
      {fehler === null && spec === null && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress aria-label="API-Beschreibung wird geladen" />
        </Box>
      )}
      {spec !== null && admin && (
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          Aufrufe mit Anmeldung laufen mit Ihrer Sitzung; ein Projekt-Token geben Sie unter ‚Authorize‘
          bei projektToken an.
        </Typography>
      )}
      {spec !== null && (
        // Swagger UI bringt sein Standard-CSS für eine helle Fläche mit (Plan #1400 E10); die
        // Fläche bleibt darum auch im dunklen Modus hell, sonst stünde dunkle Schrift auf dunklem Grund.
        <Paper sx={{ p: 1, bgcolor: 'common.white', color: 'common.black', colorScheme: 'light' }}>
          {admin ? (
            <SwaggerUI
              spec={spec}
              supportedSubmitMethods={ALLE_METHODEN}
              persistAuthorization={false}
              requestInterceptor={requestInterceptor}
              docExpansion="list"
              plugins={[AUSPROBIEREN]}
            />
          ) : (
            <SwaggerUI
              spec={spec}
              supportedSubmitMethods={KEINE_AUSFUEHRUNG}
              docExpansion="list"
              plugins={[OHNE_AUTHORIZE]}
            />
          )}
        </Paper>
      )}
      {rueckfrage !== null && (
        <AusprobierBestaetigung
          offen
          methode={rueckfrage.methode}
          pfad={rueckfrage.pfad}
          onAbbrechen={rueckfrage.abbrechen}
          onAbsenden={rueckfrage.absenden}
        />
      )}
    </Box>
  )
}
