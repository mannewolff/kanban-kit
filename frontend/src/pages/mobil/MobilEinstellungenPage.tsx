import Alert from '@mui/material/Alert'
import CircularProgress from '@mui/material/CircularProgress'
import FormControl from '@mui/material/FormControl'
import FormControlLabel from '@mui/material/FormControlLabel'
import FormGroup from '@mui/material/FormGroup'
import FormHelperText from '@mui/material/FormHelperText'
import FormLabel from '@mui/material/FormLabel'
import Link from '@mui/material/Link'
import Radio from '@mui/material/Radio'
import RadioGroup from '@mui/material/RadioGroup'
import Stack from '@mui/material/Stack'
import Switch from '@mui/material/Switch'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { boardsApi } from '../../api/boards'
import { ApiError } from '../../api/client'
import { projectsApi } from '../../api/projects'
import { pushApi, type PushBoard } from '../../api/push'
import { useAuth } from '../../auth/AuthContext'
import { useT } from '../../i18n/SpracheContext'
import { leseMobilBoard, schreibeMobilBoard } from '../../lib/mobilBoard'
import { abmelden, bestehendesAbo, einschalten, kannPush, vomStartbildschirm } from '../../lib/pushAbo'
import { canEditCards, isPlatformAdmin } from '../../lib/roles'

interface BoardWahl {
  boardId: number
  projekt: string
  board: string
}

/**
 * Die Boards, auf denen das Konto Karten anlegen darf (Plan #1989, E5) — dieselbe Auswahl wie bei
 * der Anlage von Zugangsschlüsseln in der Administration. Neue Rechte entstehen nicht.
 */
async function ladeBoards(platformAdmin: boolean): Promise<BoardWahl[]> {
  const projekte = (await projectsApi.list()).filter((p) => canEditCards(p.role, platformAdmin))
  const jeProjekt = await Promise.all(
    projekte.map(async (p) =>
      (await boardsApi.list(p.id)).map((b) => ({ boardId: b.id, projekt: p.name, board: b.name })),
    ),
  )
  return jeProjekt.flat()
}

/** Wie es um Benachrichtigungen auf diesem Gerät steht. */
type PushBereit = { art: 'bereit'; publicKey: string; abo: PushSubscription | null }
type PushStand =
  | { art: 'pruefe' }
  | { art: 'kein-push'; homeBildschirm: boolean }
  | { art: 'nicht-eingerichtet' }
  | { art: 'fehler' }
  | PushBereit

/**
 * Ohne Push zeigt die Seite einen Hinweis statt des Schalters (Plan #1989, E18): Nicht vom
 * Startbildschirm geöffnet führt der Weg über „Zum Home-Bildschirm“ — auf dem iPhone gibt es Push nur
 * dort —, vom Startbildschirm aus kann das Gerät es nicht.
 */
function anfangsStand(): PushStand {
  return kannPush() ? { art: 'pruefe' } : { art: 'kein-push', homeBildschirm: !vomStartbildschirm() }
}

/**
 * Benachrichtigungen auf diesem Gerät ein- und ausschalten, dazu ein Schalter je Board (Plan #1989,
 * E13, E14, E18; AK 11–14 der Quelle #1961). In der Vorschau sind die Schalter gesperrt: Der Server
 * weist dort jedes Schreiben ab.
 */
function Benachrichtigungen() {
  const t = useT().MOBIL
  const { vorschau } = useAuth()
  const gesperrt = vorschau !== null
  const [stand, setStand] = useState<PushStand>(anfangsStand)
  const [boards, setBoards] = useState<PushBoard[] | null>(null)
  const [boardsFehler, setBoardsFehler] = useState(false)
  const [meldung, setMeldung] = useState<string | null>(null)
  const [beschaeftigt, setBeschaeftigt] = useState(false)

  useEffect(() => {
    if (!kannPush()) return
    let aktiv = true
    const lade = async () => {
      try {
        const { publicKey } = await pushApi.schluessel()
        if (!aktiv) return
        const abo = await bestehendesAbo()
        if (aktiv) setStand({ art: 'bereit', publicKey, abo })
      } catch (error) {
        if (!aktiv) return
        setStand(error instanceof ApiError && error.status === 404 ? { art: 'nicht-eingerichtet' } : { art: 'fehler' })
      }
    }
    void lade()
    return () => {
      aktiv = false
    }
  }, [])

  const abo = stand.art === 'bereit' ? stand.abo : null
  const endpoint = abo?.endpoint ?? null

  useEffect(() => {
    setBoards(null)
    setBoardsFehler(false)
    if (endpoint == null) return
    let aktiv = true
    pushApi.boards(endpoint).then(
      (b) => {
        if (aktiv) setBoards(b)
      },
      () => {
        if (aktiv) setBoardsFehler(true)
      },
    )
    return () => {
      aktiv = false
    }
  }, [endpoint])

  if (stand.art === 'pruefe') return null

  const umschalten = async (bereit: PushBereit, an: boolean) => {
    setMeldung(null)
    setBeschaeftigt(true)
    try {
      if (an) {
        const neu = await einschalten(bereit.publicKey)
        if (neu) setStand({ ...bereit, abo: neu })
        else setMeldung(t.PUSH_VERWEIGERT)
      } else {
        await abmelden()
        setStand({ ...bereit, abo: null })
      }
    } catch {
      setMeldung(t.PUSH_FEHLGESCHLAGEN)
    } finally {
      setBeschaeftigt(false)
    }
  }

  const setzeBoard = (boardId: number, an: boolean) =>
    setBoards((liste) => liste?.map((b) => (b.boardId === boardId ? { ...b, an } : b)) ?? liste)

  const schalteBoard = async (endpoint: string, boardId: number, an: boolean) => {
    setMeldung(null)
    setzeBoard(boardId, an)
    try {
      await pushApi.boardSchalten(endpoint, boardId, an)
    } catch {
      setzeBoard(boardId, !an)
      setMeldung(t.PUSH_FEHLGESCHLAGEN)
    }
  }

  return (
    <Stack spacing={1}>
      <Typography variant="subtitle1" component="h2">
        {t.PUSH_UEBERSCHRIFT}
      </Typography>
      {stand.art === 'kein-push' && (
        <Alert severity="info">{stand.homeBildschirm ? t.PUSH_HOME_BILDSCHIRM : t.PUSH_KEIN_GERAET}</Alert>
      )}
      {stand.art === 'nicht-eingerichtet' && <Alert severity="info">{t.PUSH_NICHT_EINGERICHTET}</Alert>}
      {stand.art === 'fehler' && <Alert severity="error">{t.PUSH_LADEN_FEHLGESCHLAGEN}</Alert>}
      {stand.art === 'bereit' && (
        <>
          <FormControl>
            <FormControlLabel
              control={
                <Switch
                  slotProps={{ input: { role: 'switch' } }}
                  checked={stand.abo !== null}
                  disabled={gesperrt || beschaeftigt}
                  onChange={(e) => void umschalten(stand, e.target.checked)}
                />
              }
              label={t.PUSH_SCHALTER}
            />
            <FormHelperText>{t.PUSH_HINWEIS}</FormHelperText>
          </FormControl>
          {gesperrt && <Alert severity="info">{t.PUSH_VORSCHAU}</Alert>}
          {meldung && <Alert severity="error">{meldung}</Alert>}
          {boardsFehler && <Alert severity="error">{t.PUSH_LADEN_FEHLGESCHLAGEN}</Alert>}
          {boards?.length === 0 && <Alert severity="info">{t.PUSH_KEINE_BOARDS}</Alert>}
          {abo !== null && boards != null && boards.length > 0 && (
            <FormControl component="fieldset">
              <FormLabel component="legend">{t.PUSH_BOARDS}</FormLabel>
              <FormGroup>
                {boards.map((b) => (
                  <FormControlLabel
                    key={b.boardId}
                    control={
                      <Switch
                        slotProps={{ input: { role: 'switch' } }}
                        checked={b.an}
                        disabled={gesperrt}
                        onChange={(e) => void schalteBoard(abo.endpoint, b.boardId, e.target.checked)}
                      />
                    }
                    label={t.BOARD_EINTRAG(b.projectName, b.boardName)}
                  />
                ))}
              </FormGroup>
            </FormControl>
          )}
          <Typography variant="body2" color="text.secondary">
            {t.PUSH_ENDE}
          </Typography>
        </>
      )}
    </Stack>
  )
}

/**
 * Einstellungen der mobilen Oberfläche: das Standard-Board dieses Geräts (Plan #1989, E5) und die
 * Benachrichtigungen (E13, E14, E18). Die Wahl des Boards
 * gilt je Konto auf diesem Gerät und wird sofort gemerkt. Gibt es das gemerkte Board nicht mehr
 * (gelöscht, archiviert oder ohne Anlegerecht), sagt die Seite das und verlangt eine neue Wahl.
 */
export function MobilEinstellungenPage() {
  const t = useT().MOBIL
  const { user } = useAuth()
  const userId = user?.userId ?? null
  const platformAdmin = isPlatformAdmin(user)
  const [wahlen, setWahlen] = useState<BoardWahl[] | null>(null)
  const [ladeFehler, setLadeFehler] = useState(false)
  const [gewaehlt, setGewaehlt] = useState<number | null>(() => (userId == null ? null : leseMobilBoard(userId)))
  const [gespeichert, setGespeichert] = useState(false)

  useEffect(() => {
    let aktiv = true
    ladeBoards(platformAdmin).then(
      (w) => {
        if (aktiv) setWahlen(w)
      },
      () => {
        if (aktiv) setLadeFehler(true)
      },
    )
    return () => {
      aktiv = false
    }
  }, [platformAdmin])

  const waehle = (boardId: number) => {
    if (userId == null) return
    schreibeMobilBoard(userId, boardId)
    setGewaehlt(boardId)
    setGespeichert(true)
  }

  const verschwunden = wahlen != null && gewaehlt != null && !wahlen.some((w) => w.boardId === gewaehlt)

  return (
    <Stack spacing={2}>
      <Typography variant="h6" component="h1">
        {t.EINSTELLUNGEN_UEBERSCHRIFT}
      </Typography>
      {ladeFehler && <Alert severity="error">{t.LADEN_FEHLGESCHLAGEN}</Alert>}
      {wahlen == null && !ladeFehler && <CircularProgress sx={{ alignSelf: 'center' }} />}
      {wahlen?.length === 0 && <Alert severity="info">{t.KEIN_BOARD}</Alert>}
      {wahlen != null && wahlen.length > 0 && (
        <FormControl>
          <FormLabel id="mobil-standard-board">{t.STANDARD_BOARD}</FormLabel>
          {verschwunden && (
            <Alert severity="warning" sx={{ my: 1 }}>
              {t.BOARD_VERSCHWUNDEN}
            </Alert>
          )}
          <RadioGroup
            aria-labelledby="mobil-standard-board"
            value={verschwunden || gewaehlt == null ? '' : String(gewaehlt)}
            onChange={(e) => waehle(Number(e.target.value))}
          >
            {wahlen.map((w) => (
              <FormControlLabel
                key={w.boardId}
                value={String(w.boardId)}
                control={<Radio />}
                label={t.BOARD_EINTRAG(w.projekt, w.board)}
              />
            ))}
          </RadioGroup>
          <FormHelperText>{t.STANDARD_BOARD_HINWEIS}</FormHelperText>
        </FormControl>
      )}
      {gespeichert && (
        <Alert severity="success">
          <span>{t.GESPEICHERT}</span>{' '}
          <Link component={RouterLink} to="/mobil">
            {t.ZUR_IDEE}
          </Link>
        </Alert>
      )}
      <Benachrichtigungen />
    </Stack>
  )
}
