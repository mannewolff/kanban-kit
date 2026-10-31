import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useEffect, useState, type FormEvent } from 'react'
import { Navigate } from 'react-router-dom'
import { boardsApi, type Board } from '../../api/boards'
import { cardsApi } from '../../api/cards'
import { ApiError } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { useT } from '../../i18n/SpracheContext'
import { IDEE_TEXT_GRENZE, ideeTitel, ideeZuLang, ideenSpalte } from '../../lib/ideeTitel'
import { leseMobilBoard } from '../../lib/mobilBoard'

type Meldung = 'angelegt' | 'fehler' | null

/** Antworten, an denen das gemerkte Board nicht (mehr) erreichbar ist: gelöscht oder Recht entzogen. */
const istVerschwunden = (e: unknown): boolean => e instanceof ApiError && (e.status === 404 || e.status === 403)

/**
 * Ideen festhalten auf dem Handy (Plan #1989, E2, E6, E7): ein Textfeld, ein Knopf, und die Idee
 * fällt als `[Idea] <Text>` ins Backlog des Standard-Boards dieses Geräts.
 *
 * Das Board wird beim Öffnen geladen, damit die Seite sagt, wohin die Idee geht. Scheitert das —
 * etwa ohne Netz —, holt das Abschicken es nach; ein verschwundenes Board führt zur neuen Wahl.
 * Scheitert das Anlegen, bleibt der Text stehen: Es gibt kein Arbeiten ohne Netz, aber auch keinen
 * verlorenen Gedanken.
 */
export function MobilIdeePage() {
  const t = useT().MOBIL
  const { user } = useAuth()
  const boardId = user == null ? null : leseMobilBoard(user.userId)
  const [board, setBoard] = useState<Board | null>(null)
  const [verschwunden, setVerschwunden] = useState(false)
  const [text, setText] = useState('')
  const [sendet, setSendet] = useState(false)
  const [meldung, setMeldung] = useState<Meldung>(null)

  useEffect(() => {
    if (boardId == null) return
    let aktiv = true
    boardsApi.get(boardId).then(
      (b) => {
        if (aktiv) setBoard(b)
      },
      (e: unknown) => {
        if (aktiv) setVerschwunden(istVerschwunden(e))
      },
    )
    return () => {
      aktiv = false
    }
  }, [boardId])

  if (boardId == null || verschwunden) {
    return <Navigate to="/mobil/einstellungen" replace />
  }

  const zuLang = ideeZuLang(text)
  const gesperrt = text.trim() === '' || zuLang || sendet

  const festhalten = async (event: FormEvent) => {
    // Abgeschickt wird nur über den Knopf, und der ist gesperrt, solange `gesperrt` gilt: Das
    // mehrzeilige Feld kennt kein Absenden per Eingabetaste.
    event.preventDefault()
    setSendet(true)
    setMeldung(null)
    try {
      const ziel = board ?? (await boardsApi.get(boardId))
      setBoard(ziel)
      const spalte = ideenSpalte(ziel.columns)
      if (spalte == null) {
        setMeldung('fehler')
        return
      }
      await cardsApi.create(boardId, spalte.id, ideeTitel(text))
      setText('')
      setMeldung('angelegt')
    } catch (e) {
      if (istVerschwunden(e)) {
        setVerschwunden(true)
        return
      }
      setMeldung('fehler')
    } finally {
      setSendet(false)
    }
  }

  return (
    <Stack component="form" spacing={2} onSubmit={(e) => void festhalten(e)} noValidate>
      <Typography variant="h6" component="h1">
        {t.IDEE_UEBERSCHRIFT}
      </Typography>
      {board != null && <Typography color="text.secondary">{t.ZIEL(board.name)}</Typography>}
      <TextField
        label={t.IDEE_FELD}
        value={text}
        onChange={(e) => {
          setText(e.target.value)
          setMeldung(null)
        }}
        autoFocus
        multiline
        minRows={4}
        error={zuLang}
        helperText={zuLang ? t.ZU_LANG(text.trim().length, IDEE_TEXT_GRENZE) : t.IDEE_HINWEIS}
      />
      <Button type="submit" variant="contained" size="large" disabled={gesperrt}>
        {t.FESTHALTEN}
      </Button>
      {meldung === 'angelegt' && <Alert severity="success">{t.ANGELEGT}</Alert>}
      {meldung === 'fehler' && <Alert severity="error">{t.NICHT_ANGELEGT}</Alert>}
    </Stack>
  )
}
