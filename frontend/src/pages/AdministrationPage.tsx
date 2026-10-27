import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import FormControlLabel from '@mui/material/FormControlLabel'
import IconButton from '@mui/material/IconButton'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Radio from '@mui/material/Radio'
import RadioGroup from '@mui/material/RadioGroup'
import Stack from '@mui/material/Stack'
import Switch from '@mui/material/Switch'
import TextField from '@mui/material/TextField'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import { useColorScheme } from '@mui/material/styles'
import { useEffect, useState, type ReactNode } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { accessTokensApi, type AccessToken, type CreatedAccessToken } from '../api/accessTokens'
import { authApi, type ColorScheme } from '../api/auth'
import { boardsApi, type Board } from '../api/boards'
import { ApiError, apiErrorMessage } from '../api/client'
import { configApi } from '../api/config'
import { OPENAPI_JSON_PFAD, OPENAPI_YAML_PFAD } from '../api/openapi'
import { languageApi } from '../api/language'
import { projectsApi, type Project } from '../api/projects'
import { useAuth } from '../auth/AuthContext'
import { useSnackbar } from '../components/SnackbarProvider'
import { StandardLabelsSection } from '../components/StandardLabelsSection'
import { VorschauGesperrt } from '../components/VorschauGesperrt'
import { VorschauPersoenlichHinweis } from '../components/VorschauPersoenlichHinweis'
import { useSprache, useT } from '../i18n/SpracheContext'
import { istSprache, woerterbuch, type Sprache } from '../i18n/woerterbuch'
import { useEditMode } from '../lib/EditModeContext'
import { farbmodusAus } from '../lib/farbmodus'
import { canEditCards, isPlatformAdmin } from '../lib/roles'

/**
 * Administrations-/Einstellungsseite (für alle angemeldeten Nutzer): Editiermodus-Schalter,
 * Erscheinungsbild und die Verwaltung persönlicher API-Tokens.
 */
export function AdministrationPage() {
  const { editMode, setEditMode } = useEditMode()
  const t = useT().ADMINISTRATION

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        {t.TITEL}
      </Typography>
      <Stack spacing={2} sx={{ maxWidth: 720 }}>
        <LegalSection />

        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={1}>
            <Typography variant="h6" component="h2">
              {t.EDITIERMODUS}
            </Typography>
            <FormControlLabel
              control={
                <Switch
                  checked={editMode}
                  onChange={(e) => setEditMode(e.target.checked)}
                  slotProps={{ input: { 'aria-label': t.EDITIERMODUS_ARIA } }}
                />
              }
              label={t.BEARBEITEN_AKTIVIEREN}
            />
            <Typography variant="body2" color="text.secondary">
              {t.EDITIERMODUS_HINWEIS}
            </Typography>
          </Stack>
        </Paper>

        <Persoenlich titel={t.ERSCHEINUNGSBILD}>
          <AppearanceSection />
        </Persoenlich>

        <LanguageSection />

        <DoneRetentionSection />

        <StandardLabelsSection />

        <Persoenlich titel={t.API_TOKENS}>
          <ApiTokensSection />
        </Persoenlich>

        <ApiUebersichtSection />
      </Stack>
    </Box>
  )
}

/**
 * Copyright- und Lizenzhinweis, sichtbar für jeden angemeldeten Nutzer. Das Jahr steht fest und
 * wird nicht aus der Systemzeit abgeleitet — ein mitlaufendes Jahr brächte urheberrechtlich nichts
 * und wäre eine untestbare Zeitabhängigkeit.
 */
function LegalSection() {
  const t = useT()
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={1}>
        <Typography variant="h6" component="h2">
          {t.ADMINISTRATION.RECHTLICHES}
        </Typography>
        <Typography variant="body2">{t.ADMINISTRATION.COPYRIGHT}</Typography>
        <Typography variant="body2" color="text.secondary">
          {t.ADMINISTRATION.LIZENZ(t.APP_NAME)}
        </Typography>
      </Stack>
    </Paper>
  )
}

/**
 * Feste, zweisprachige Überschrift (Issue #1646): Wer kein Deutsch liest, findet die Einstellung
 * auch auf der deutschen Oberfläche. Steht darum nicht im Wörterbuch.
 */
const SPRACHE_UEBERSCHRIFT = 'Sprache / Language'

/** Jede Sprache unter ihrem eigenen Namen — fest, in jeder Einstellung gleich. */
const SPRACHNAMEN: ReadonlyArray<{ code: Sprache; name: string }> = [
  { code: 'de', name: 'Deutsch' },
  { code: 'en', name: 'English' },
]

/**
 * Sprache der Installation (Plan #1638, E3): sichtbar für jeden Angemeldeten, bedienbar nur für den
 * Plattform-Admin. Nach dem Speichern setzt der Abschnitt die Sprache im Kontext, sodass der Admin
 * die Umstellung sofort sieht; alle anderen sehen sie beim nächsten Laden.
 */
function LanguageSection() {
  const { user } = useAuth()
  const admin = isPlatformAdmin(user)
  const { sprache, setzeSprache } = useSprache()
  const t = useT()
  const [auswahl, setAuswahl] = useState<Sprache>(sprache)
  const notify = useSnackbar()

  const save = async () => {
    try {
      const gesetzt = await languageApi.setzen(auswahl)
      setzeSprache(gesetzt)
      notify(woerterbuch(gesetzt).SPRACHE.GESPEICHERT, 'success')
    } catch (e) {
      notify(apiErrorMessage(e, t.SPRACHE.FEHLER), 'error')
    }
  }

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={1.5}>
        <Typography variant="h6" component="h2" id="sprache-ueberschrift">
          {SPRACHE_UEBERSCHRIFT}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t.SPRACHE.HINWEIS}
        </Typography>
        <RadioGroup
          row
          aria-labelledby="sprache-ueberschrift"
          value={auswahl}
          onChange={(e) => {
            if (istSprache(e.target.value)) setAuswahl(e.target.value)
          }}
        >
          {SPRACHNAMEN.map(({ code, name }) => (
            <FormControlLabel
              key={code}
              value={code}
              control={<Radio />}
              label={name}
              lang={code}
              disabled={!admin}
            />
          ))}
        </RadioGroup>
        {admin ? (
          <Box>
            <VorschauGesperrt>
              <Button variant="contained" aria-label={t.SPRACHE.SPEICHERN_ARIA} onClick={() => void save()}>
                {t.SPRACHE.SPEICHERN}
              </Button>
            </VorschauGesperrt>
          </Box>
        ) : (
          <Typography variant="body2" color="text.secondary">
            {t.SPRACHE.NUR_ADMIN}
          </Typography>
        )}
      </Stack>
    </Paper>
  )
}

/**
 * Rahmen eines persönlichen Abschnitts: In der „Ansicht als XY“ steht unter der Überschrift statt
 * des Inhalts nur der Hinweis, dass der Bereich in der Vorschau nicht gezeigt wird (AK 5, E5). Der
 * Inhalt wird dann gar nicht erst eingehängt und ruft darum auch nichts ab.
 */
function Persoenlich({ titel, children }: Readonly<{ titel: string; children: ReactNode }>) {
  const { vorschau } = useAuth()
  if (!vorschau) {
    return children
  }
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={1}>
        <Typography variant="h6" component="h2">
          {titel}
        </Typography>
        <VorschauPersoenlichHinweis />
      </Stack>
    </Paper>
  )
}

/**
 * Erscheinungsbild System / Hell / Dunkel (#1911), gespeichert am Konto. Die Wahl wirkt sofort;
 * scheitert das Speichern, springen Anzeige und Erscheinungsbild auf den gespeicherten Stand zurück.
 */
function AppearanceSection() {
  const { user, refresh } = useAuth()
  const { setMode } = useColorScheme()
  const t = useT().ADMINISTRATION
  const notify = useSnackbar()
  const gespeichert: ColorScheme = user?.colorScheme ?? 'SYSTEM'
  const [wahl, setWahl] = useState<ColorScheme>(gespeichert)

  const waehlen = async (neu: ColorScheme) => {
    setWahl(neu)
    setMode(farbmodusAus(neu))
    try {
      await authApi.setColorScheme(neu)
      await refresh()
    } catch (e) {
      setWahl(gespeichert)
      setMode(farbmodusAus(gespeichert))
      notify(apiErrorMessage(e, t.ERSCHEINUNGSBILD_FEHLER), 'error')
    }
  }

  const stufen: ReadonlyArray<{ wert: ColorScheme; name: string }> = [
    { wert: 'SYSTEM', name: t.ERSCHEINUNGSBILD_SYSTEM },
    { wert: 'LIGHT', name: t.ERSCHEINUNGSBILD_HELL },
    { wert: 'DARK', name: t.ERSCHEINUNGSBILD_DUNKEL },
  ]

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={1}>
        <Typography variant="h6" component="h2" id="erscheinungsbild-ueberschrift">
          {t.ERSCHEINUNGSBILD}
        </Typography>
        <ToggleButtonGroup
          exclusive
          size="small"
          value={wahl}
          aria-labelledby="erscheinungsbild-ueberschrift"
          onChange={(_, neu: ColorScheme | null) => {
            // Ein Klick auf die schon gewählte Stufe liefert null — die Wahl bleibt dann stehen.
            if (neu !== null && neu !== wahl) void waehlen(neu)
          }}
        >
          {stufen.map(({ wert, name }) => (
            <ToggleButton key={wert} value={wert}>
              {name}
            </ToggleButton>
          ))}
        </ToggleButtonGroup>
        <Typography variant="body2" color="text.secondary">
          {t.ERSCHEINUNGSBILD_HINWEIS}
        </Typography>
      </Stack>
    </Paper>
  )
}

/**
 * Globale Done-Aufbewahrung (nur Plattform-Admin): aktuellen effektiven Wert anzeigen und ändern.
 * {@code 0} = kein Auto-Archiv. Rendert für Nicht-Admins nichts.
 */
function DoneRetentionSection() {
  const { user } = useAuth()
  const admin = isPlatformAdmin(user)
  const [days, setDays] = useState('')
  const [effective, setEffective] = useState<number | null>(null)
  const notify = useSnackbar()
  const tg = useT()
  const t = tg.ADMINISTRATION

  useEffect(() => {
    if (!admin) {
      return
    }
    void configApi.getDoneRetention().then((r) => {
      setEffective(r.effective)
      setDays(String(r.effective))
    })
  }, [admin])

  if (!admin) {
    return null
  }

  const save = async () => {
    const n = Number(days)
    if (!Number.isInteger(n) || n < 0) {
      notify(t.DONE_UNGUELTIG, 'warning')
      return
    }
    try {
      const r = await configApi.setDoneRetention(n)
      setEffective(r.effective)
      setDays(String(r.effective))
      notify(t.GESPEICHERT, 'success')
    } catch (e) {
      notify(e instanceof ApiError ? e.message : tg.ALLGEMEIN.SPEICHERN_FEHLGESCHLAGEN, 'error')
    }
  }

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={1.5}>
        <Typography variant="h6" component="h2">
          {t.DONE_AUFBEWAHRUNG}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t.DONE_HINWEIS} <strong>{t.DONE_NULL}</strong> {t.DONE_GLOBAL}
        </Typography>
        {effective !== null && (
          <Typography variant="body2" color="text.secondary">
            {t.AKTUELL_WIRKSAM}
            {effective === 0 ? t.KEIN_AUTO_ARCHIV : t.TAGE_WERT(effective)}
          </Typography>
        )}
        <Stack direction="row" spacing={1} alignItems="flex-start">
          <TextField
            type="number"
            size="small"
            label={t.TAGE}
            value={days}
            onChange={(e) => setDays(e.target.value)}
            slotProps={{ htmlInput: { min: 0, 'aria-label': t.DONE_TAGE_ARIA } }}
            sx={{ width: 140 }}
          />
          <VorschauGesperrt>
            <Button variant="contained" onClick={() => void save()} sx={{ mt: 0.5 }}>
              {tg.ALLGEMEIN.SPEICHERN}
            </Button>
          </VorschauGesperrt>
        </Stack>
      </Stack>
    </Paper>
  )
}

/** Verwaltung persönlicher API-Tokens (Liste, Erzeugen mit Board-Bindung, Widerrufen). */
function ApiTokensSection() {
  const { user } = useAuth()
  const platformAdmin = isPlatformAdmin(user)
  const notify = useSnackbar()
  const ta = useT().ADMINISTRATION

  const [tokens, setTokens] = useState<AccessToken[]>([])
  const [projects, setProjects] = useState<Project[]>([])
  const [created, setCreated] = useState<CreatedAccessToken | null>(null)
  const [copied, setCopied] = useState(false)
  const [dialogOpen, setDialogOpen] = useState(false)

  const reloadTokens = () => {
    void accessTokensApi.list().then(setTokens)
  }

  useEffect(() => {
    reloadTokens()
    // Nur Projekte anbieten, in denen der Nutzer Karten anlegen darf — ein board-gebundenes Token
    // erlaubt genau das (via kanbancompat). Das Backend erzwingt dasselbe (TICKET_CREATE).
    void projectsApi
      .list()
      .then((ps) => setProjects(ps.filter((p) => canEditCards(p.role, platformAdmin))))
  }, [platformAdmin])

  // Wird nur mit gebundener (nicht-null) projectId aufgerufen (siehe binding), daher `id: number`.
  const projectName = (id: number): string => projects.find((p) => p.id === id)?.name ?? `#${id}`

  const binding = (t: AccessToken): string =>
    t.projectId != null && t.boardId != null ? ta.BINDUNG(projectName(t.projectId), t.boardId) : ta.UNGEBUNDEN

  const copyPlaintext = async (plaintext: string) => {
    await navigator.clipboard.writeText(plaintext)
    setCopied(true)
  }

  const revoke = async (id: number) => {
    if (!window.confirm(ta.WIDERRUFEN_FRAGE)) {
      return
    }
    try {
      await accessTokensApi.revoke(id)
      reloadTokens()
    } catch (e) {
      notify(apiErrorMessage(e, ta.WIDERRUFEN_FEHLGESCHLAGEN), 'error')
    }
  }

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={1.5}>
        <Stack direction="row" alignItems="center" spacing={1}>
          <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>
            {ta.API_TOKENS}
          </Typography>
          <Button
            variant="outlined"
            size="small"
            disabled={projects.length === 0}
            onClick={() => {
              setCreated(null)
              setDialogOpen(true)
            }}
          >
            {ta.TOKEN_ERZEUGEN}
          </Button>
        </Stack>

        <Typography variant="body2" color="text.secondary">
          {ta.TOKEN_HINWEIS} <strong>{ta.NUR_EINMAL}</strong> {ta.ANGEZEIGT}
        </Typography>
        {projects.length === 0 && (
          <Typography variant="body2" color="text.secondary">
            {ta.KEIN_PROJEKT}
          </Typography>
        )}

        {created && (
          <Alert
            severity="success"
            action={
              <Button
                color="inherit"
                size="small"
                aria-label={ta.TOKEN_KOPIEREN}
                startIcon={<ContentCopyIcon fontSize="small" />}
                onClick={() => void copyPlaintext(created.plaintext)}
              >
                {copied ? ta.KOPIERT : ta.KOPIEREN}
              </Button>
            }
          >
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              {ta.TOKEN_ERZEUGT(created.name)}
            </Typography>
            <Box
              component="code"
              aria-label={ta.TOKEN_KLARTEXT}
              sx={{ display: 'block', mt: 0.5, fontFamily: 'monospace', wordBreak: 'break-all' }}
            >
              {created.plaintext}
            </Box>
          </Alert>
        )}

        {tokens.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            {ta.KEINE_TOKENS}
          </Typography>
        ) : (
          <Stack spacing={0.75}>
            {tokens.map((t) => (
              <Box
                key={t.id}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1.5,
                  border: '1px solid',
                  borderColor: 'divider',
                  borderRadius: (t) => `${t.shape.borderRadius}px`,
                  px: 1.5,
                  py: 1,
                  opacity: t.revoked ? 0.5 : 1,
                }}
              >
                <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                  <Typography variant="body2" sx={{ fontWeight: 500 }}>
                    {t.name}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                    {binding(t)}
                  </Typography>
                </Box>
                <Chip
                  size="small"
                  label={t.revoked ? ta.WIDERRUFEN : ta.AKTIV}
                  color={t.revoked ? 'default' : 'success'}
                  variant={t.revoked ? 'outlined' : 'filled'}
                />
                {!t.revoked && (
                  <Tooltip title={ta.WIDERRUFEN}>
                    <IconButton
                      size="small"
                      aria-label={ta.TOKEN_WIDERRUFEN_ARIA(t.name)}
                      onClick={() => void revoke(t.id)}
                    >
                      <DeleteOutlineIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
              </Box>
            ))}
          </Stack>
        )}
      </Stack>

      <CreateTokenDialog
        open={dialogOpen}
        projects={projects}
        onClose={() => setDialogOpen(false)}
        onCreated={(res) => {
          setCreated(res)
          setCopied(false)
          setDialogOpen(false)
          reloadTokens()
        }}
      />
    </Paper>
  )
}

/**
 * Einstieg in die Übersicht der Schnittstelle (Issue #1410, Plan #1400 E3) samt Downloads der
 * Beschreibung. Sichtbar für jeden Angemeldeten — die Übersicht zeigt die gesamte API.
 */
function ApiUebersichtSection() {
  const t = useT()
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={1.5}>
        <Typography variant="h6" component="h2">
          {t.ADMINISTRATION.API_SCHNITTSTELLE}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t.ADMINISTRATION.API_HINWEIS}
        </Typography>
        <Stack direction="row" spacing={2} alignItems="center" sx={{ flexWrap: 'wrap' }}>
          <Button
            variant="outlined"
            size="small"
            component={RouterLink}
            to="/administration/api"
            aria-label={t.ADMINISTRATION.UEBERSICHT_OEFFNEN_ARIA}
          >
            {t.ADMINISTRATION.UEBERSICHT_OEFFNEN}
          </Button>
          <Link href={OPENAPI_JSON_PFAD} download aria-label={t.OPENAPI.JSON_ARIA}>
            {t.OPENAPI.JSON}
          </Link>
          <Link href={OPENAPI_YAML_PFAD} download aria-label={t.OPENAPI.YAML_ARIA}>
            {t.OPENAPI.YAML}
          </Link>
        </Stack>
      </Stack>
    </Paper>
  )
}

/** Anlege-Dialog: Name + Projekt→Board-Kaskade; erzeugt ein board-gebundenes Token. */
function CreateTokenDialog({
  open,
  projects,
  onClose,
  onCreated,
}: Readonly<{
  open: boolean
  projects: Project[]
  onClose: () => void
  onCreated: (created: CreatedAccessToken) => void
}>) {
  const [name, setName] = useState('')
  const [projectId, setProjectId] = useState<number | ''>('')
  const [boards, setBoards] = useState<Board[]>([])
  const [boardId, setBoardId] = useState<number | ''>('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const tg = useT()
  const t = tg.ADMINISTRATION

  useEffect(() => {
    if (!open) return
    setName('')
    setProjectId('')
    setBoards([])
    setBoardId('')
    setError(null)
    setSaving(false)
  }, [open])

  useEffect(() => {
    if (projectId === '') {
      setBoards([])
      return
    }
    void boardsApi.list(projectId).then(setBoards)
  }, [projectId])

  const canSubmit = name.trim().length > 0 && projectId !== '' && boardId !== '' && !saving

  const submit = async () => {
    setSaving(true)
    setError(null)
    try {
      // projectId/boardId sind hier Zahlen (der Erzeugen-Button ist bei !canSubmit disabled);
      // Number(...) verengt number|'' ohne toten Guard-Zweig.
      const res = await accessTokensApi.create(name.trim(), Number(projectId), Number(boardId))
      onCreated(res)
    } catch (e) {
      setError(apiErrorMessage(e, t.ERZEUGEN_FEHLGESCHLAGEN))
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{t.NEUES_TOKEN}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField
            label={t.NAME}
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            fullWidth
            autoFocus
            slotProps={{ htmlInput: { maxLength: 120, 'aria-label': t.NAME } }}
          />
          <TextField
            select
            label={t.PROJEKT}
            value={projectId === '' ? '' : String(projectId)}
            onChange={(e) => {
              setProjectId(e.target.value === '' ? '' : Number(e.target.value))
              setBoardId('')
            }}
            slotProps={{
              htmlInput: { 'aria-label': t.PROJEKT },
              select: { native: true },
              inputLabel: { shrink: true },
            }}
            fullWidth
          >
            <option value="">{t.PROJEKT_WAEHLEN}</option>
            {projects.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </TextField>
          <TextField
            select
            label={t.BOARD}
            value={boardId === '' ? '' : String(boardId)}
            onChange={(e) => setBoardId(e.target.value === '' ? '' : Number(e.target.value))}
            disabled={projectId === ''}
            slotProps={{
              htmlInput: { 'aria-label': t.BOARD },
              select: { native: true },
              inputLabel: { shrink: true },
            }}
            fullWidth
          >
            <option value="">{t.BOARD_WAEHLEN}</option>
            {boards.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </TextField>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{tg.ALLGEMEIN.ABBRECHEN}</Button>
        <Button variant="contained" onClick={() => void submit()} disabled={!canSubmit}>
          {t.ERZEUGEN}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
