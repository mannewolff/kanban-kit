import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useState } from 'react'
import { apiErrorMessage } from '../../api/client'
import { commentsApi, type Comment, type CommentsApi } from '../../api/comments'
import { useSprache, useT } from '../../i18n/SpracheContext'
import { beitragsText, gespraechAus, kiAntwortetGleich, kopfFuerNeuenBeitrag } from '../../lib/gespraech'

/**
 * Das Gespräch mit der KI an einer Karte (Issue #2219, Kriterium 2 des Fachplans #2202): die
 * Beiträge mit Kopf „Gespräch“ in Zeitfolge, darunter das Eingabefeld; an einer Karte ohne Gespräch
 * nur „Gespräch beginnen“. Ein Beitrag geht als gewöhnlicher Kommentar mit Kopf an die Karte — der
 * Kopf folgt dem ersten Beitrag, ein neues Gespräch der Sprache der Oberfläche (Plan #2206 E9). Ob
 * ein Beitrag von der KI stammt, sagt allein `vonKi` (E6).
 *
 * @param comments die Kommentare der Karte, in beliebiger Ordnung; gewöhnliche bleiben außen vor
 * @param gesperrt Beginnen und Senden gesperrt — in der „Ansicht als …“ (Plan #1914, E15)
 * @param onGesendet der vom Server angelegte Kommentar, damit der Aufrufer ihn nachträgt
 */
export function GespraechVerlauf({
  cardId,
  comments,
  gesperrt = false,
  api = commentsApi,
  onGesendet,
}: Readonly<{
  cardId: number
  comments: readonly Comment[]
  gesperrt?: boolean
  api?: Pick<CommentsApi, 'create'>
  onGesendet?: (kommentar: Comment) => void
}>) {
  const alleT = useT()
  const t = alleT.GESPRAECH
  const { sprache } = useSprache()
  // Eigene Beiträge, bis der Aufrufer sie in `comments` nachgetragen hat — ohne Doppel.
  const [eigene, setEigene] = useState<Comment[]>([])
  const [begonnen, setBegonnen] = useState(false)
  const [text, setText] = useState('')
  const [sendet, setSendet] = useState(false)
  const [fehler, setFehler] = useState<string | null>(null)

  const bekannt = new Set(comments.map((c) => c.id))
  const beitraege = gespraechAus([...comments, ...eigene.filter((c) => !bekannt.has(c.id))])
  const eingabe = beitraege.length > 0 || begonnen

  const sende = async () => {
    const inhalt = text.trim()
    setSendet(true)
    setFehler(null)
    try {
      const neu = await api.create(cardId, beitragsText(kopfFuerNeuenBeitrag(beitraege, sprache), inhalt))
      setEigene((alt) => [...alt, neu])
      setText('')
      onGesendet?.(neu)
    } catch (e) {
      setFehler(apiErrorMessage(e, alleT.FEHLER_UNBEKANNT))
    }
    setSendet(false)
  }

  return (
    <Box data-testid="gespraech-verlauf">
      <Typography variant="subtitle2" gutterBottom>
        {t.TITEL}
      </Typography>
      <Stack spacing={1}>
        {beitraege.map((b) => (
          <Box key={b.id}>
            <Typography data-testid="gespraech-absender" variant="body2" fontWeight={600}>
              {b.vonKi ? t.KI : b.autorName}
            </Typography>
            <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
              {b.text}
            </Typography>
          </Box>
        ))}
        {kiAntwortetGleich(beitraege) && (
          <Typography role="status" variant="body2" color="text.secondary">
            {t.ANTWORTET_GLEICH}
          </Typography>
        )}
        {eingabe ? (
          <Stack spacing={1}>
            <TextField
              label={t.BEITRAG}
              size="small"
              multiline
              minRows={2}
              autoFocus={begonnen}
              disabled={gesperrt}
              value={text}
              onChange={(ev) => setText(ev.target.value)}
            />
            <Box>
              <Button
                size="small"
                variant="contained"
                disabled={gesperrt || sendet || text.trim() === ''}
                onClick={() => void sende()}
              >
                {t.SENDEN}
              </Button>
            </Box>
          </Stack>
        ) : (
          <Box>
            <Button size="small" disabled={gesperrt} onClick={() => setBegonnen(true)}>
              {t.BEGINNEN}
            </Button>
          </Box>
        )}
        {fehler !== null && (
          <Typography role="alert" variant="body2" color="error">
            {t.FEHLER(fehler)}
          </Typography>
        )}
      </Stack>
    </Box>
  )
}
