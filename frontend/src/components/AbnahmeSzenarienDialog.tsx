import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Typography from '@mui/material/Typography'
import { useState } from 'react'
import { apiErrorMessage } from '../api/client'
import {
  abnahmeKommentar,
  abnahmeStand,
  type AbnahmeStand,
  type Szenario,
  type Vermerk,
} from '../lib/abnahmeSzenarien'
import { isTooLong, tooLongMessage } from '../lib/textLimits'
import { dialogTitleSx } from './dialogChromeSx'

const TITEL_ID = 'abnahme-szenarien-titel'
const RUECKFRAGE_TITEL_ID = 'abnahme-verwerfen-titel'
const RUECKFRAGE_HINWEIS_ID = 'abnahme-verwerfen-hinweis'

/** Höchstlänge der Notiz zu „nicht okay“ (Plan #1626, E9). */
const NOTIZ_MAX = 500

interface Props {
  open: boolean
  /** Titel der Karte, deren Szenarien abgenommen werden. */
  titel: string
  szenarien: Szenario[]
  onClose: () => void
  /** Schreibt den fertigen Ergebnis-Kommentar; ein Fehler bleibt im Dialog stehen (E6). */
  onAbschicken: (text: string) => Promise<void>
}

/** Die Zusammenfassung unter der Liste (AK 3): okay, nicht okay mit Nummer, noch offen. */
function zusammenfassung(anzahl: number, stand: AbnahmeStand): string {
  if (stand.okay === anzahl) return 'Alle Szenarien okay'
  const teile = [`${stand.okay} von ${anzahl} okay`]
  if (stand.nichtOkay > 0) {
    const liste = stand.nichtOkaySzenarien.map(({ nummer, name }) => `${nummer}. ${name}`).join(', ')
    teile.push(`nicht okay: ${liste}`)
  }
  if (stand.offen > 0) teile.push(`noch offen: ${stand.offen}`)
  return teile.join(' — ')
}

/** Ein Schritt mit hervorgehobenem Schlüsselwort — das erste Wort, so liefert es `szenarienAus`. */
function Schritt({ text }: Readonly<{ text: string }>) {
  const trenner = text.indexOf(' ')
  if (trenner < 0) return <strong>{text}</strong>
  return (
    <>
      <strong>{text.slice(0, trenner)}</strong>
      {text.slice(trenner)}
    </>
  )
}

/**
 * Die Abnahme der Szenarien eines Fachplans von Hand (Plan #1626, E3): je Szenario „okay“ oder
 * „nicht okay“ mit optionaler Notiz, darunter der Stand, am Ende das Ergebnis als Kommentartext an
 * den Aufrufer. Die Vermerke leben nur hier (E7) und hängen an der Position des Szenarios (E12).
 */
export function AbnahmeSzenarienDialog({ open, titel, szenarien, onClose, onAbschicken }: Readonly<Props>) {
  const [vermerke, setVermerke] = useState<(Vermerk | undefined)[]>([])
  const [sendet, setSendet] = useState(false)
  const [fehler, setFehler] = useState<string | null>(null)
  const [rueckfrage, setRueckfrage] = useState(false)

  const stand = abnahmeStand(szenarien, vermerke)
  const text = abnahmeKommentar(szenarien, vermerke)
  const zuLang = isTooLong(text)
  const vollstaendig = stand.offen === 0

  const setzeVermerk = (index: number, aenderung: Partial<Vermerk>) => {
    setVermerke((alt) => {
      const neu = [...alt]
      neu[index] = { urteil: 'okay', notiz: '', ...alt[index], ...aenderung }
      return neu
    })
  }

  const schliessen = () => {
    setVermerke([])
    setFehler(null)
    setRueckfrage(false)
    onClose()
  }

  const schliessenAnfragen = () => {
    if (sendet) return
    if (vermerke.some((vermerk) => vermerk !== undefined)) setRueckfrage(true)
    else schliessen()
  }

  const abschicken = async () => {
    setSendet(true)
    setFehler(null)
    try {
      await onAbschicken(text)
      schliessen()
    } catch (e) {
      setFehler(apiErrorMessage(e, 'Das Ergebnis konnte nicht geschrieben werden.'))
    } finally {
      setSendet(false)
    }
  }

  return (
    <>
      <Dialog open={open} onClose={schliessenAnfragen} maxWidth="md" fullWidth aria-labelledby={TITEL_ID}>
        <DialogTitle id={TITEL_ID} sx={dialogTitleSx}>
          Abnahme: {titel}
        </DialogTitle>
        <DialogContent>
          <Stack spacing={3} sx={{ mt: 2 }}>
            {szenarien.map((szenario, index) => {
              const nummer = index + 1
              const kopfId = `abnahme-szenario-${nummer}`
              const vermerk = vermerke[index]
              return (
                <Box component="section" key={kopfId} aria-labelledby={kopfId}>
                  <Typography id={kopfId} component="h3" variant="subtitle1" sx={{ fontWeight: 'fontWeightBold' }}>
                    {nummer}. {szenario.name}
                  </Typography>
                  <Box component="ol" sx={{ mt: 1, mb: 1, pl: 3 }}>
                    {szenario.schritte.map((schritt, schrittIndex) => (
                      <li key={schrittIndex}>
                        <Schritt text={schritt} />
                      </li>
                    ))}
                  </Box>
                  <ToggleButtonGroup
                    exclusive
                    size="small"
                    value={vermerk?.urteil ?? null}
                    onChange={(_, urteil: Vermerk['urteil'] | null) => {
                      if (urteil) setzeVermerk(index, { urteil })
                    }}
                    aria-label={`Vermerk zu Szenario ${nummer}`}
                  >
                    <ToggleButton value="okay" color="success">
                      okay
                    </ToggleButton>
                    <ToggleButton value="nicht okay" color="error">
                      nicht okay
                    </ToggleButton>
                  </ToggleButtonGroup>
                  {vermerk?.urteil === 'nicht okay' && (
                    <TextField
                      label="Notiz (optional)"
                      size="small"
                      fullWidth
                      value={vermerk.notiz}
                      onChange={(e) => setzeVermerk(index, { notiz: e.target.value })}
                      slotProps={{ htmlInput: { maxLength: NOTIZ_MAX } }}
                      sx={{ mt: 1 }}
                    />
                  )}
                </Box>
              )
            })}
            <Typography role="status" sx={{ fontWeight: 'fontWeightBold' }}>
              {zusammenfassung(szenarien.length, stand)}
            </Typography>
            {zuLang && (
              <Typography variant="body2" color="error">
                {tooLongMessage(text.length)}
              </Typography>
            )}
            {fehler && <Alert severity="error">{fehler}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={schliessenAnfragen} disabled={sendet}>
            Schließen
          </Button>
          <Button variant="contained" onClick={() => void abschicken()} disabled={!vollstaendig || zuLang || sendet}>
            Ergebnis an den Fachplan schreiben
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        open={rueckfrage}
        onClose={() => setRueckfrage(false)}
        maxWidth="xs"
        fullWidth
        aria-labelledby={RUECKFRAGE_TITEL_ID}
        aria-describedby={RUECKFRAGE_HINWEIS_ID}
      >
        <DialogTitle id={RUECKFRAGE_TITEL_ID} sx={dialogTitleSx}>
          Vermerke verwerfen?
        </DialogTitle>
        <DialogContent>
          <DialogContentText id={RUECKFRAGE_HINWEIS_ID} sx={{ mt: 2 }}>
            Die Vermerke dieser Abnahme gehen verloren.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRueckfrage(false)} autoFocus>
            Abbrechen
          </Button>
          <Button variant="contained" color="error" onClick={schliessen}>
            Verwerfen
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
