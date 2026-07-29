import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Checkbox from '@mui/material/Checkbox'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useEffect, useMemo, useState } from 'react'
import {
  MAX_IDEAS_PER_IMPORT,
  splitSpecIntoSections,
  type HeadingLevel,
  type SpecSection,
} from '../lib/specImport'

/** Eine anzulegende Idee, wie sie der Batch-Endpoint erwartet (#492). */
export interface SpecIdea {
  title: string
  description: string | null
}

interface Props {
  open: boolean
  /** Name der gewählten Datei — nur zur Anzeige, hochgeladen wird sie nicht. */
  fileName: string
  /** Inhalt der Datei, im Browser gelesen. */
  markdown: string
  onClose: () => void
  /** Legt die ausgewählten Ideen an; wirft bei Fehlschlag (der Dialog bleibt dann offen). */
  onImport: (ideas: SpecIdea[]) => Promise<void>
}

/** Zeichen der einzeiligen Beschreibungs-Vorschau je Abschnitt. */
const PREVIEW_LENGTH = 160

/** Voreingestellte Trennebene: In exportierten Spezifikationen trägt H1 meist den Dokumenttitel. */
const DEFAULT_LEVEL: HeadingLevel = 2

/** Beschreibung als eine Zeile für die Vorschau — Umbrüche zu Leerzeichen, hinten gekürzt. */
function previewText(description: string): string {
  const oneLine = description.replace(/\s+/g, ' ')
  return oneLine.length > PREVIEW_LENGTH ? `${oneLine.slice(0, PREVIEW_LENGTH)}…` : oneLine
}

/**
 * Vorschau und Bestätigung des Spezifikations-Imports (Issue #493): zeigt, welche Ideen aus der
 * gewählten Markdown-Datei entstünden, bevor irgendetwas angelegt wird.
 *
 * Die Vorschau ist Sicherheitsmerkmal, nicht Komfort — eine falsch geratene Trennebene erzeugte
 * sonst Dutzende unbrauchbarer Karten, die einzeln wegzuräumen wären. Deshalb: Ebene umschaltbar,
 * jeder Abschnitt einzeln abwählbar (Vorspann, Inhaltsverzeichnis), Kürzungen und die Obergrenze
 * sichtbar, bevor der Knopf gedrückt wird.
 *
 * Die Datei selbst bleibt im Browser: {@link Props.markdown} ist bereits gelesener Text, an den
 * Server gehen ausschließlich die fertigen Karten.
 */
export function SpecImportDialog({ open, fileName, markdown, onClose, onImport }: Readonly<Props>) {
  const [level, setLevel] = useState<HeadingLevel>(DEFAULT_LEVEL)
  const [deselected, setDeselected] = useState<ReadonlySet<number>>(new Set())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Neue Datei bzw. erneutes Öffnen: Ebene, Abwahl und Fehlermeldung zurücksetzen, damit nichts
  // aus einem vorherigen Dokument nachwirkt (der Dialog bleibt zwischen den Läufen gemountet).
  useEffect(() => {
    if (!open) return
    setLevel(DEFAULT_LEVEL)
    setDeselected(new Set())
    setBusy(false)
    setError(null)
  }, [open, markdown])

  const sections = useMemo(() => splitSpecIntoSections(markdown, level), [markdown, level])
  const selected = sections.filter((_, index) => !deselected.has(index))
  const tooMany = selected.length > MAX_IDEAS_PER_IMPORT

  // Die Abwahl hängt an der Position im Dokument; nach einem Ebenenwechsel zeigen dieselben
  // Positionen auf andere Abschnitte, deshalb wird sie mit zurückgesetzt.
  const handleLevelChange = (value: string) => {
    setLevel(value === '1' ? 1 : 2)
    setDeselected(new Set())
  }

  const toggle = (index: number) => {
    setDeselected((current) => {
      const next = new Set(current)
      if (next.has(index)) {
        next.delete(index)
      } else {
        next.add(index)
      }
      return next
    })
  }

  const handleImport = async () => {
    setBusy(true)
    setError(null)
    try {
      await onImport(
        selected.map((section) => ({
          title: section.title,
          // Ein Abschnitt ohne Text unter der Überschrift bekommt keine leere Beschreibung,
          // sondern gar keine — dasselbe, was das Anlegen von Hand liefert.
          description: section.description === '' ? null : section.description,
        })),
      )
      onClose()
    } catch {
      setError('Die Ideen konnten nicht angelegt werden. Bitte erneut versuchen.')
    } finally {
      setBusy(false)
    }
  }

  const emptyMessage =
    markdown.trim() === ''
      ? 'Die Datei ist leer — es gibt nichts einzulesen.'
      : `Keine Überschrift der Ebene H${level} gefunden — es wird nichts angelegt. Du kannst oben auf H${level === 2 ? 1 : 2} umschalten.`

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth aria-labelledby="spec-import-title">
      <DialogTitle id="spec-import-title">Spezifikation einlesen</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2}>
          <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap">
            <Typography variant="body2" fontWeight={600}>
              {fileName}
            </Typography>
            <TextField
              select
              size="small"
              label="Ebene"
              value={String(level)}
              onChange={(e) => handleLevelChange(e.target.value)}
              slotProps={{
                htmlInput: { 'aria-label': 'Trennende Überschriftenebene' },
                select: { native: true },
              }}
            >
              <option value="1">H1 trennt die Karten</option>
              <option value="2">H2 trennt die Karten</option>
            </TextField>
          </Stack>

          <Typography variant="caption" color="text.secondary">
            Die Datei wird nur im Browser gelesen — sie wird weder hochgeladen noch verändert oder
            gelöscht. An den Server gehen ausschließlich die unten gezeigten Ideen.
          </Typography>

          {error !== null && <Alert severity="error">{error}</Alert>}

          {sections.length === 0 ? (
            <Alert severity="info">{emptyMessage}</Alert>
          ) : (
            <>
              {tooMany && (
                <Alert severity="warning">
                  {`${selected.length} Abschnitte ausgewählt — es lassen sich höchstens ${MAX_IDEAS_PER_IMPORT} auf einmal anlegen. Bitte einzelne Abschnitte abwählen.`}
                </Alert>
              )}
              <Typography variant="body2">
                {`${selected.length} von ${sections.length} Abschnitten ausgewählt.`}
              </Typography>
              <Box>
                {sections.map((section, index) => (
                  <SectionRow
                    key={`${index}-${section.title}`}
                    section={section}
                    checked={!deselected.has(index)}
                    onToggle={() => toggle(index)}
                  />
                ))}
              </Box>
            </>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Abbrechen</Button>
        {sections.length > 0 && (
          <Button
            variant="contained"
            onClick={() => void handleImport()}
            disabled={selected.length === 0 || tooMany || busy}
          >
            {`${selected.length} ${selected.length === 1 ? 'Idee' : 'Ideen'} anlegen`}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  )
}

/** Eine Zeile der Vorschau: Auswahlkästchen, Titel mit Kürzungs-Hinweisen, gekürzte Beschreibung. */
function SectionRow({
  section,
  checked,
  onToggle,
}: Readonly<{ section: SpecSection; checked: boolean; onToggle: () => void }>) {
  return (
    <Stack direction="row" spacing={1} alignItems="flex-start" sx={{ py: 0.25 }}>
      <Checkbox
        size="small"
        checked={checked}
        onChange={onToggle}
        slotProps={{ input: { 'aria-label': `Abschnitt „${section.title}“ einlesen` } }}
      />
      <Box sx={{ minWidth: 0, pt: 0.5 }}>
        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
          <Typography variant="body2" fontWeight={600} sx={{ wordBreak: 'break-word' }}>
            {section.title}
          </Typography>
          {section.titleTruncated && <Chip size="small" color="warning" label="Titel gekürzt" />}
          {section.descriptionTruncated && (
            <Chip size="small" color="warning" label="Beschreibung gekürzt" />
          )}
        </Stack>
        <Typography variant="caption" color="text.secondary" sx={{ wordBreak: 'break-word' }}>
          {section.description === '' ? '(keine Beschreibung)' : previewText(section.description)}
        </Typography>
      </Box>
    </Stack>
  )
}
