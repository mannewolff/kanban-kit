import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useState } from 'react'
import { attachmentsApi as defaultAttachmentsApi, type AttachmentsApi } from '../../api/attachments'
import type { Board } from '../../api/boards'
import { cardsApi as defaultCardsApi } from '../../api/cards'
import { apiErrorMessage } from '../../api/client'
import { useT } from '../../i18n/SpracheContext'
import { IDEE_TEXT_GRENZE, ideeTitel, ideeZuLang, ideenSpalte } from '../../lib/ideeTitel'
import { useAnhangHochladen } from '../../lib/useAnhangHochladen'
import { VorschauGesperrt } from '../VorschauGesperrt'

/**
 * Eine Idee mit Anhängen in einem Schritt einstellen (Issue #2220, Plan #2206 E13, Kriterium 7 des
 * Fachplans #2202): Text und Dateien, die Karte fällt als `[Idea] <Text>` in die Ideenspalte des
 * gewählten Boards, danach gehen die Dateien einzeln an sie.
 *
 * Scheitert eine Datei, bleibt die Karte stehen: Der Dialog nennt die Datei und bietet nur noch
 * „Schließen“ — ein zweites Einstellen legte die Idee doppelt an. Scheitert schon das Anlegen,
 * bleiben Text und Dateien für einen neuen Versuch stehen.
 */
export function IdeeEinstellenDialog({
  boards,
  onClose,
  onEingestellt,
  cardsApi = defaultCardsApi,
  attachmentsApi = defaultAttachmentsApi,
}: Readonly<{
  boards: readonly Board[]
  onClose: () => void
  /** Die Karte steht — auch dann, wenn danach eine Datei nicht ankam. */
  onEingestellt: () => void
  cardsApi?: Pick<typeof defaultCardsApi, 'create'>
  attachmentsApi?: Pick<AttachmentsApi, 'upload' | 'fetchBlob'>
}>) {
  const t = useT().IDEE_EINSTELLEN
  const allgemein = useT().ALLGEMEIN
  const { hochladen } = useAnhangHochladen(attachmentsApi)
  const [text, setText] = useState('')
  const [boardId, setBoardId] = useState<number | undefined>(boards[0]?.id)
  const [dateien, setDateien] = useState<File[]>([])
  const [sendet, setSendet] = useState(false)
  const [fehler, setFehler] = useState<string | null>(null)
  const [nichtAngehaengt, setNichtAngehaengt] = useState<string[] | null>(null)

  const zuLang = ideeZuLang(text)
  const gesperrt = text.trim() === '' || zuLang || sendet

  const einstellen = async () => {
    const spalte = ideenSpalte(boards.find((b) => b.id === boardId)?.columns ?? [])
    if (boardId === undefined || spalte === undefined) {
      setFehler(t.OHNE_SPALTE)
      return
    }
    setSendet(true)
    setFehler(null)
    let karteId: number
    try {
      karteId = (await cardsApi.create(boardId, spalte.id, ideeTitel(text))).id
    } catch (error_: unknown) {
      setFehler(apiErrorMessage(error_, t.ANLEGEN_FEHLER))
      setSendet(false)
      return
    }
    const gescheitert: string[] = []
    for (const datei of dateien) {
      // eslint-disable-next-line no-await-in-loop -- nacheinander: Die Anhangsgrenze des Servers trifft dann die letzten Dateien, nicht zufällige
      if ((await hochladen(karteId, datei, { vorschau: false })) == null) gescheitert.push(datei.name)
    }
    onEingestellt()
    if (gescheitert.length === 0) {
      onClose()
      return
    }
    setNichtAngehaengt(gescheitert)
    setSendet(false)
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" aria-labelledby="idee-einstellen-titel">
      <DialogTitle id="idee-einstellen-titel">{t.TITEL}</DialogTitle>
      <DialogContent>
        {nichtAngehaengt == null ? (
          <Stack spacing={2} sx={{ pt: 1 }}>
            {boards.length > 1 && (
              <TextField
                select
                id="idee-einstellen-board"
                label={t.BOARD}
                value={boardId}
                onChange={(e) => setBoardId(Number(e.target.value))}
                slotProps={{ select: { native: true } }}
              >
                {boards.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </TextField>
            )}
            <TextField
              label={t.TEXT}
              value={text}
              onChange={(e) => setText(e.target.value)}
              autoFocus
              multiline
              minRows={3}
              error={zuLang}
              helperText={zuLang ? t.ZU_LANG(text.trim().length, IDEE_TEXT_GRENZE) : t.HINWEIS}
            />
            <Stack spacing={0.5}>
              {dateien.map((datei, index) => (
                <Stack key={`${datei.name}-${index}`} direction="row" spacing={1} alignItems="center">
                  <Typography variant="body2">{datei.name}</Typography>
                  <IconButton
                    size="small"
                    aria-label={t.DATEI_ENTFERNEN_ARIA(datei.name)}
                    onClick={() => setDateien((d) => d.filter((_, i) => i !== index))}
                  >
                    ✕
                  </IconButton>
                </Stack>
              ))}
              <Button component="label" size="small" sx={{ alignSelf: 'flex-start' }}>
                {t.DATEIEN}
                <input
                  hidden
                  multiple
                  type="file"
                  aria-label={t.DATEIEN}
                  onChange={(e) => {
                    const neu = Array.from(e.target.files ?? [])
                    setDateien((d) => [...d, ...neu])
                    e.target.value = ''
                  }}
                />
              </Button>
            </Stack>
            {fehler && <Alert severity="error">{fehler}</Alert>}
          </Stack>
        ) : (
          <Alert severity="error">{t.DATEIEN_FEHLER(nichtAngehaengt.join(', '))}</Alert>
        )}
      </DialogContent>
      <DialogActions>
        {nichtAngehaengt == null ? (
          <>
            <Button onClick={onClose}>{allgemein.ABBRECHEN}</Button>
            <VorschauGesperrt>
              <Button variant="contained" disabled={gesperrt} onClick={() => void einstellen()}>
                {t.EINSTELLEN}
              </Button>
            </VorschauGesperrt>
          </>
        ) : (
          <Button onClick={onClose}>{allgemein.SCHLIESSEN}</Button>
        )}
      </DialogActions>
    </Dialog>
  )
}
