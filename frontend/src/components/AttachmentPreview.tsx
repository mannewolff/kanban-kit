import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Link from '@mui/material/Link'
import { useT } from '../i18n/SpracheContext'

interface Props {
  filename: string
  contentType: string
  /** Blob-Object-URL des Anhangs (clientseitig, kein Inline-Render vom Server). */
  url: string
  downloadHref: string
  onClose: () => void
}

/** Lightbox für Anhänge: Bilder inline, PDF im Frame, Download als Fallback. */
export function AttachmentPreview({ filename, contentType, url, downloadHref, onClose }: Readonly<Props>) {
  const t = useT()
  const isImage = contentType.startsWith('image/')
  return (
    <Dialog open onClose={onClose} maxWidth="lg" fullWidth aria-label={t.ANHANG.VORSCHAU_ARIA(filename)}>
      <DialogTitle>{filename}</DialogTitle>
      <DialogContent dividers>
        {isImage ? (
          <Box component="img" src={url} alt={filename} sx={{ maxWidth: '100%', display: 'block', mx: 'auto' }} />
        ) : (
          <Box component="iframe" title={filename} src={url} sx={{ width: '100%', height: '75vh', border: 0 }} />
        )}
      </DialogContent>
      <DialogActions>
        <Link href={downloadHref}>{t.ANHANG.HERUNTERLADEN}</Link>
        <Button onClick={onClose}>{t.ALLGEMEIN.SCHLIESSEN}</Button>
      </DialogActions>
    </Dialog>
  )
}
