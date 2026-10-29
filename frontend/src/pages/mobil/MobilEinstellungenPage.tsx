import Alert from '@mui/material/Alert'
import CircularProgress from '@mui/material/CircularProgress'
import FormControl from '@mui/material/FormControl'
import FormControlLabel from '@mui/material/FormControlLabel'
import FormHelperText from '@mui/material/FormHelperText'
import FormLabel from '@mui/material/FormLabel'
import Link from '@mui/material/Link'
import Radio from '@mui/material/Radio'
import RadioGroup from '@mui/material/RadioGroup'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { boardsApi } from '../../api/boards'
import { projectsApi } from '../../api/projects'
import { useAuth } from '../../auth/AuthContext'
import { useT } from '../../i18n/SpracheContext'
import { leseMobilBoard, schreibeMobilBoard } from '../../lib/mobilBoard'
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

/**
 * Einstellungen der mobilen Oberfläche: das Standard-Board dieses Geräts (Plan #1989, E5). Die Wahl
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
    </Stack>
  )
}
