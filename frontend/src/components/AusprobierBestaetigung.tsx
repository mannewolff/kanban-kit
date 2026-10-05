import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import { HINWEIS_AENDERND } from '../lib/apiAusprobieren'
import { SCHRIFT_MONO } from '../theme'
import { dialogTitleSx } from './dialogChromeSx'

const TITEL_ID = 'ausprobier-bestaetigung-titel'
const HINWEIS_ID = 'ausprobier-bestaetigung-hinweis'

interface Props {
  offen: boolean
  /** HTTP-Methode des Aufrufs, wie sie abgehen soll. */
  methode: string
  /** Pfad des Aufrufs, wie er abgehen soll. */
  pfad: string
  onAbbrechen: () => void
  onAbsenden: () => void
}

/**
 * Rueckfrage vor einem aendernden ausprobierten Aufruf (Plan #1437, E10). Der Fokus liegt beim
 * Oeffnen auf „Abbrechen": Wer Enter drueckt, ohne zu lesen, sendet nichts ab. Schliessen ueber
 * Escape oder den Hintergrund gilt als Abbruch.
 */
export function AusprobierBestaetigung({ offen, methode, pfad, onAbbrechen, onAbsenden }: Readonly<Props>) {
  return (
    <Dialog
      open={offen}
      onClose={onAbbrechen}
      maxWidth="xs"
      fullWidth
      aria-labelledby={TITEL_ID}
      aria-describedby={HINWEIS_ID}
    >
      <DialogTitle id={TITEL_ID} sx={dialogTitleSx}>
        Echte Daten ändern?
      </DialogTitle>
      <DialogContent>
        <DialogContentText id={HINWEIS_ID} sx={{ mt: 2, mb: 2 }}>
          {HINWEIS_AENDERND}
        </DialogContentText>
        <Box component="code" sx={{ fontFamily: SCHRIFT_MONO, wordBreak: 'break-all' }}>
          <Box component="span" sx={{ fontWeight: 'fontWeightBold', mr: 1 }}>
            {methode}
          </Box>
          <span>{pfad}</span>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onAbbrechen} autoFocus>
          Abbrechen
        </Button>
        <Button variant="contained" onClick={onAbsenden}>
          Absenden
        </Button>
      </DialogActions>
    </Dialog>
  )
}
