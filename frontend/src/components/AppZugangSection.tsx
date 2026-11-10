import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import { useCallback, useEffect, useState } from 'react'
import {
  accessTokensApi,
  type AppZugangMitToken,
  type AppZugangZeile,
  type CreatedAccessToken,
} from '../api/accessTokens'
import { apiErrorMessage } from '../api/client'
import { formatierer } from '../i18n/format'
import { useSprache, useT } from '../i18n/SpracheContext'
import { useSnackbar } from './SnackbarProvider'

/** Eine angefragte Änderung: was die Rückfrage zeigt und was sie nach dem Ja ausführt. */
interface Aktion {
  titel: string
  frage: string
  bestaetigen: string
  /** Liefert beim Anlegen und Erneuern den Klartext, beim Widerrufen `null`. */
  ausfuehren: () => Promise<CreatedAccessToken | null>
}

/**
 * Verwaltung des Zugangs der Desktop-App (Plan #2100): je Board, in dessen Projekt der Angemeldete
 * Admin oder Owner ist, eine Zeile. Der Klartext steht nur im Zustand dieses Abschnitts, bis er
 * geschlossen wird; die Liste liefert ihn nie.
 */
export function AppZugangSection() {
  const ta = useT().ADMINISTRATION
  const t = ta.APP_ZUGANG
  const { sprache } = useSprache()
  const notify = useSnackbar()

  const [zeilen, setZeilen] = useState<AppZugangZeile[] | null>(null)
  const [aktion, setAktion] = useState<Aktion | null>(null)
  const [erzeugt, setErzeugt] = useState<CreatedAccessToken | null>(null)
  const [kopiert, setKopiert] = useState(false)

  const ladenFehlgeschlagen = t.LADEN_FEHLGESCHLAGEN
  const laden = useCallback(() => {
    accessTokensApi
      .listApp()
      .then(setZeilen)
      .catch((e: unknown) => notify(apiErrorMessage(e, ladenFehlgeschlagen), 'error'))
  }, [notify, ladenFehlgeschlagen])

  useEffect(laden, [laden])

  const ort = (z: AppZugangZeile) => t.ORT(z.projectName, z.boardName)

  const anlegen = (z: AppZugangZeile) =>
    setAktion({
      titel: t.ANLEGEN_TITEL,
      frage: t.ANLEGEN_FRAGE(ort(z)),
      bestaetigen: t.ANLEGEN,
      ausfuehren: () => accessTokensApi.createApp(z.projectId, z.boardId),
    })

  const erneuern = (z: AppZugangMitToken) =>
    setAktion({
      titel: t.ERNEUERN_TITEL,
      frage: t.ERNEUERN_FRAGE(ort(z)),
      bestaetigen: t.ERNEUERN,
      ausfuehren: () => accessTokensApi.renewApp(z.tokenId),
    })

  const widerrufen = (z: AppZugangMitToken) =>
    setAktion({
      titel: t.WIDERRUFEN_TITEL,
      frage: t.WIDERRUFEN_FRAGE(ort(z)),
      bestaetigen: t.WIDERRUFEN,
      ausfuehren: () => accessTokensApi.revokeApp(z.tokenId).then(() => null),
    })

  const erledigt = (ergebnis: CreatedAccessToken | null) => {
    if (ergebnis !== null) {
      setErzeugt(ergebnis)
      setKopiert(false)
    }
    setAktion(null)
    laden()
  }

  const kopieren = async (klartext: string) => {
    await navigator.clipboard.writeText(klartext)
    setKopiert(true)
  }

  const datum = formatierer(sprache).datum

  const angaben = (z: AppZugangMitToken) =>
    [
      t.INHABER(z.inhaberName),
      t.ANGELEGT_AM(datum(z.createdAt)),
      z.lastUsedAt === null ? t.NIE_BENUTZT : t.ZULETZT_BENUTZT(datum(z.lastUsedAt)),
    ].join(t.TRENNER)

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={1.5}>
        <Typography variant="h6" component="h2">
          {t.TITEL}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t.HINWEIS}
        </Typography>

        {erzeugt && (
          <Alert
            severity="success"
            action={
              <Stack direction="row" spacing={0.5}>
                <Button
                  color="inherit"
                  size="small"
                  aria-label={ta.TOKEN_KOPIEREN}
                  startIcon={<ContentCopyIcon fontSize="small" />}
                  onClick={() => void kopieren(erzeugt.plaintext)}
                >
                  {kopiert ? ta.KOPIERT : ta.KOPIEREN}
                </Button>
                <Button
                  color="inherit"
                  size="small"
                  aria-label={t.KLARTEXT_SCHLIESSEN}
                  onClick={() => setErzeugt(null)}
                >
                  {t.SCHLIESSEN}
                </Button>
              </Stack>
            }
          >
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              {ta.TOKEN_ERZEUGT(erzeugt.name)}
            </Typography>
            <Box
              component="code"
              aria-label={ta.TOKEN_KLARTEXT}
              sx={{ display: 'block', mt: 0.5, fontFamily: 'monospace', wordBreak: 'break-all' }}
            >
              {erzeugt.plaintext}
            </Box>
          </Alert>
        )}

        {zeilen?.length === 0 && (
          <Typography variant="body2" color="text.secondary">
            {t.KEINE_ZEILEN}
          </Typography>
        )}

        {zeilen !== null && zeilen.length > 0 && (
          <Stack spacing={0.75}>
            {zeilen.map((z) => (
              <Box
                key={z.boardId}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  flexWrap: 'wrap',
                  gap: 1.5,
                  border: 1,
                  borderColor: 'divider',
                  borderRadius: (theme) => `${theme.shape.borderRadius}px`,
                  px: 1.5,
                  py: 1,
                }}
              >
                <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                  <Typography variant="body2" sx={{ fontWeight: 500 }}>
                    {ort(z)}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                    {z.tokenId === null ? t.KEIN_ZUGANG : angaben(z)}
                  </Typography>
                </Box>
                {z.tokenId === null ? (
                  <Button
                    variant="outlined"
                    size="small"
                    aria-label={t.ANLEGEN_ARIA(ort(z))}
                    onClick={() => anlegen(z)}
                  >
                    {t.ANLEGEN}
                  </Button>
                ) : (
                  <Stack direction="row" spacing={1}>
                    {z.eigener && (
                      <Button
                        variant="outlined"
                        size="small"
                        aria-label={t.ERNEUERN_ARIA(ort(z))}
                        onClick={() => erneuern(z)}
                      >
                        {t.ERNEUERN}
                      </Button>
                    )}
                    <Button
                      variant="outlined"
                      size="small"
                      aria-label={t.WIDERRUFEN_ARIA(ort(z))}
                      onClick={() => widerrufen(z)}
                    >
                      {t.WIDERRUFEN}
                    </Button>
                  </Stack>
                )}
              </Box>
            ))}
          </Stack>
        )}
      </Stack>

      {aktion && (
        <Rueckfrage
          aktion={aktion}
          onAbbrechen={() => setAktion(null)}
          onErledigt={erledigt}
          onFehlgeschlagen={laden}
        />
      )}
    </Paper>
  )
}

/**
 * Rückfrage vor jeder Änderung. Eine Abweisung bleibt im Dialog stehen, etwa die 409-Meldung mit dem
 * Inhaber; die Zeilen werden trotzdem neu geladen, weil sie dann veraltet sind.
 */
function Rueckfrage({
  aktion,
  onAbbrechen,
  onErledigt,
  onFehlgeschlagen,
}: Readonly<{
  aktion: Aktion
  onAbbrechen: () => void
  onErledigt: (ergebnis: CreatedAccessToken | null) => void
  onFehlgeschlagen: () => void
}>) {
  const tg = useT()
  const t = tg.ADMINISTRATION.APP_ZUGANG
  const [fehler, setFehler] = useState<string | null>(null)
  const [laeuft, setLaeuft] = useState(false)

  const bestaetigen = async () => {
    setLaeuft(true)
    setFehler(null)
    try {
      onErledigt(await aktion.ausfuehren())
    } catch (e) {
      setFehler(apiErrorMessage(e, t.FEHLGESCHLAGEN))
      setLaeuft(false)
      onFehlgeschlagen()
    }
  }

  return (
    <Dialog open onClose={onAbbrechen} fullWidth maxWidth="sm">
      <DialogTitle>{aktion.titel}</DialogTitle>
      <DialogContent>
        <Stack spacing={2}>
          {fehler && <Alert severity="error">{fehler}</Alert>}
          <DialogContentText>{aktion.frage}</DialogContentText>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onAbbrechen}>{tg.ALLGEMEIN.ABBRECHEN}</Button>
        <Button variant="contained" disabled={laeuft} onClick={() => void bestaetigen()}>
          {aktion.bestaetigen}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
