import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import { useEffect, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import SwaggerUI from 'swagger-ui-react'
import 'swagger-ui-react/swagger-ui.css'
import { apiErrorMessage } from '../api/client'
import { OPENAPI_JSON_PFAD, OPENAPI_YAML_PFAD, openapiApi } from '../api/openapi'

/**
 * Blendet den „Authorize“-Knopf aus (Plan #1400 E5): Ausprobieren ist aus, also gibt es auch
 * nichts anzumelden. Die Sicherheitsschemata bleiben in der Beschreibung lesbar.
 */
const OHNE_AUTHORIZE = { wrapComponents: { authorizeBtn: () => () => null } }

/** Keine HTTP-Methode ist ausführbar — „Try it out“ entfällt (Plan #1400 E5, Erweiterung #1366). */
const KEINE_AUSFUEHRUNG: [] = []

/**
 * Übersicht der Schnittstelle (Issue #1410): Swagger UI über der Spezifikation aus
 * {@link openapiApi.lade}, dazu die Downloads und der Weg zurück in die Administration.
 */
export function ApiUebersichtPage() {
  const [spec, setSpec] = useState<object | null>(null)
  const [fehler, setFehler] = useState<string | null>(null)

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
      {spec !== null && (
        // Swagger UI bringt sein Standard-CSS für eine helle Fläche mit (Plan #1400 E10); die
        // Fläche bleibt darum auch im dunklen Modus hell, sonst stünde dunkle Schrift auf dunklem Grund.
        <Paper sx={{ p: 1, bgcolor: 'common.white', color: 'common.black', colorScheme: 'light' }}>
          <SwaggerUI
            spec={spec}
            supportedSubmitMethods={KEINE_AUSFUEHRUNG}
            docExpansion="list"
            plugins={[OHNE_AUTHORIZE]}
          />
        </Paper>
      )}
    </Box>
  )
}
