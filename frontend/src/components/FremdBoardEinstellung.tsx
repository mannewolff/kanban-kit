import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useEffect, useRef, useState } from 'react'
import { apiErrorMessage } from '../api/client'
import {
  fremdBoardApi,
  STANDARD_STATUSLABELS,
  type FremdBoardAnsicht,
  type FremdBoardStatuslabels,
} from '../api/fremdboard'
import { projectsApi, type Project } from '../api/projects'
import { useAuth } from '../auth/AuthContext'
import { formatierer } from '../i18n/format'
import { useSprache, useT } from '../i18n/SpracheContext'
import { canManageProject, isPlatformAdmin } from '../lib/roles'
import { useSnackbar } from './SnackbarProvider'

/** Was im Formular steht. Der Zugang lebt nur hier, bis gespeichert ist. */
interface Formular {
  basisUrl: string
  projektPfad: string
  zugang: string
  kitKonto: string
  statuslabels: FremdBoardStatuslabels
}

/** Das Formular aus dem Stand des Servers — der Zugang immer leer, denn der Server liefert ihn nie. */
function formularAus(ansicht: FremdBoardAnsicht): Formular {
  return {
    basisUrl: ansicht.basisUrl ?? '',
    projektPfad: ansicht.projektPfad ?? '',
    zugang: '',
    kitKonto: ansicht.kitKonto ?? '',
    statuslabels: { ...(ansicht.statuslabels ?? STANDARD_STATUSLABELS) },
  }
}

const LABELFELDER: readonly (keyof FremdBoardStatuslabels)[] = [
  'backlog',
  'ready',
  'inProgress',
  'inReview',
]


/**
 * Einstellung „Board-Quelle“ (Issue #2169, Plan #2152): Der Owner legt fest, dass das Board eines
 * Projekts in GitLab liegt. Sichtbar nur, wenn der Angemeldete in mindestens einem Projekt Owner
 * ist (oder Plattform-Admin); angeboten werden nur diese Projekte. Der Zugang wird nur eingegeben,
 * nie angezeigt: Nach dem Speichern ist das Feld leer, und der Server liefert ihn nicht. In der
 * „Ansicht als XY“ bleibt die Einstellung ganz weg, und es wird nichts geladen.
 */
export function FremdBoardEinstellung() {
  const t = useT().ADMINISTRATION.FREMDBOARD
  const { user, vorschau } = useAuth()
  const notify = useSnackbar()
  const platformAdmin = isPlatformAdmin(user)

  const [projekte, setProjekte] = useState<Project[] | null>(null)
  const [projektId, setProjektId] = useState<number | ''>('')

  const projekteFehlgeschlagen = t.PROJEKTE_FEHLGESCHLAGEN
  useEffect(() => {
    if (vorschau) return
    projectsApi
      .list()
      .then((ps) => setProjekte(ps.filter((p) => canManageProject(p.role, platformAdmin))))
      .catch((e: unknown) => notify(apiErrorMessage(e, projekteFehlgeschlagen), 'error'))
  }, [vorschau, platformAdmin, notify, projekteFehlgeschlagen])

  if (vorschau || projekte === null || projekte.length === 0) {
    return null
  }

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={1.5}>
        <Typography variant="h6" component="h2">
          {t.TITEL}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t.HINWEIS}
        </Typography>

        <TextField
          select
          label={t.PROJEKT}
          value={projektId === '' ? '' : String(projektId)}
          onChange={(e) => setProjektId(e.target.value === '' ? '' : Number(e.target.value))}
          slotProps={{
            htmlInput: { 'aria-label': t.PROJEKT },
            select: { native: true },
            inputLabel: { shrink: true },
          }}
          fullWidth
        >
          <option value="">{t.PROJEKT_WAEHLEN}</option>
          {projekte.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </TextField>

        {/* Je Projekt ein frisches Formular: Eine späte Antwort des vorigen Projekts landet nirgends. */}
        {projektId !== '' && <Verbindung key={projektId} projektId={projektId} />}
      </Stack>
    </Paper>
  )
}

/** Verbindung eines Projekts: lädt sie, zeigt ihren Stand und speichert die Angaben. */
function Verbindung({ projektId }: Readonly<{ projektId: number }>) {
  const t = useT().ADMINISTRATION.FREMDBOARD
  const { sprache } = useSprache()
  const notify = useSnackbar()

  const [ansicht, setAnsicht] = useState<FremdBoardAnsicht | null>(null)
  const [formular, setFormular] = useState<Formular | null>(null)
  const [fehler, setFehler] = useState<string | null>(null)
  const [speichert, setSpeichert] = useState(false)
  const zugangFeld = useRef<HTMLInputElement>(null)

  const ladenFehlgeschlagen = t.LADEN_FEHLGESCHLAGEN
  useEffect(() => {
    fremdBoardApi
      .lies(projektId)
      .then((a) => {
        setAnsicht(a)
        setFormular(formularAus(a))
      })
      .catch((e: unknown) => setFehler(apiErrorMessage(e, ladenFehlgeschlagen)))
  }, [projektId, ladenFehlgeschlagen])

  const fehlerAnzeige = fehler && <Alert severity="error">{fehler}</Alert>
  if (ansicht === null || formular === null) {
    return fehlerAnzeige
  }

  const { datum, datumUhrzeit } = formatierer(sprache)
  const angebunden = ansicht.angebunden
  const zugangPflicht = !angebunden || !ansicht.zugangLesbar

  const aendere = (teil: Partial<Formular>) => setFormular({ ...formular, ...teil })
  const aendereLabel = (feld: keyof FremdBoardStatuslabels, wert: string) =>
    setFormular({ ...formular, statuslabels: { ...formular.statuslabels, [feld]: wert } })

  const vollstaendig =
    formular.basisUrl.trim() !== '' &&
    formular.projektPfad.trim() !== '' &&
    (!zugangPflicht || formular.zugang.trim() !== '') &&
    LABELFELDER.every((feld) => formular.statuslabels[feld].trim() !== '')

  const speichern = async () => {
    setSpeichert(true)
    setFehler(null)
    try {
      const neu = await fremdBoardApi.speichere(projektId, {
        art: 'GITLAB',
        basisUrl: formular.basisUrl.trim(),
        projektPfad: formular.projektPfad.trim(),
        zugang: formular.zugang.trim() === '' ? null : formular.zugang,
        kitKonto: formular.kitKonto.trim() === '' ? null : formular.kitKonto.trim(),
        statuslabels: formular.statuslabels,
      })
      setAnsicht(neu)
      setFormular(formularAus(neu))
      notify(t.GESPEICHERT, 'success')
    } catch (e) {
      setFehler(apiErrorMessage(e, t.SPEICHERN_FEHLGESCHLAGEN))
    } finally {
      setSpeichert(false)
    }
  }

  const labelName: Record<keyof FremdBoardStatuslabels, string> = {
    backlog: t.LABEL_BACKLOG,
    ready: t.LABEL_READY,
    inProgress: t.LABEL_IN_PROGRESS,
    inReview: t.LABEL_IN_REVIEW,
  }

  return (
    <Stack spacing={1.5}>
      {fehlerAnzeige}

      {!ansicht.schluesselGesetzt && <Alert severity="warning">{t.KEIN_SCHLUESSEL}</Alert>}

      {ansicht.angebundenAm !== null && (
        <Alert severity="info">
          <Stack spacing={0.5}>
            <span>{t.ANGEBUNDEN(datum(ansicht.angebundenAm))}</span>
            <span>
              {ansicht.letzterAbgleichAm === null
                ? t.NOCH_KEIN_ABGLEICH
                : t.LETZTER_ABGLEICH(datumUhrzeit(ansicht.letzterAbgleichAm))}
            </span>
          </Stack>
        </Alert>
      )}

      {ansicht.letzterFehler !== null && (
        <Alert severity="warning">{t.ABGLEICH_FEHLER(ansicht.letzterFehler)}</Alert>
      )}

      {angebunden && !ansicht.zugangLesbar && (
        <Alert
          severity="warning"
          action={
            <Button color="inherit" size="small" onClick={() => zugangFeld.current?.focus()}>
              {t.ZUGANG_NEU}
            </Button>
          }
        >
          {t.ZUGANG_NICHT_LESBAR}
        </Alert>
      )}

      <Typography variant="body2">{t.SYSTEM}</Typography>

      <TextField
        label={t.ADRESSE}
        value={formular.basisUrl}
        onChange={(e) => aendere({ basisUrl: e.target.value })}
        disabled={angebunden}
        required={!angebunden}
        helperText={t.ADRESSE_HILFE}
        slotProps={{ htmlInput: { maxLength: 500, inputMode: 'url' } }}
        fullWidth
      />
      <TextField
        label={t.PROJEKTPFAD}
        value={formular.projektPfad}
        onChange={(e) => aendere({ projektPfad: e.target.value })}
        disabled={angebunden}
        required={!angebunden}
        helperText={t.PROJEKTPFAD_HILFE}
        slotProps={{ htmlInput: { maxLength: 500 } }}
        fullWidth
      />
      <TextField
        label={t.ZUGANG}
        type="password"
        value={formular.zugang}
        onChange={(e) => aendere({ zugang: e.target.value })}
        required={zugangPflicht}
        helperText={angebunden ? t.ZUGANG_HILFE_BESTEHEND : t.ZUGANG_HILFE_NEU}
        inputRef={zugangFeld}
        autoComplete="new-password"
        slotProps={{ htmlInput: { maxLength: 1000 } }}
        fullWidth
      />
      <TextField
        label={t.KIT_KONTO}
        value={formular.kitKonto}
        onChange={(e) => aendere({ kitKonto: e.target.value })}
        helperText={t.KIT_KONTO_HILFE}
        slotProps={{ htmlInput: { maxLength: 255 } }}
        fullWidth
      />

      <Typography variant="subtitle2" component="h3">
        {t.STATUSLABELS}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        {t.STATUSLABELS_HINWEIS}
      </Typography>
      {LABELFELDER.map((feld) => (
        <TextField
          key={feld}
          label={labelName[feld]}
          value={formular.statuslabels[feld]}
          onChange={(e) => aendereLabel(feld, e.target.value)}
          required
          size="small"
          slotProps={{ htmlInput: { maxLength: 255 } }}
          fullWidth
        />
      ))}

      <Stack direction="row" sx={{ justifyContent: 'flex-end' }}>
        <Button
          variant="contained"
          disabled={!vollstaendig || speichert}
          onClick={() => void speichern()}
        >
          {angebunden ? t.SPEICHERN : t.ANBINDEN}
        </Button>
      </Stack>
    </Stack>
  )
}
