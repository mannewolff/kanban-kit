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
import type { BoardColumn } from '../api/boards'
import { apiErrorMessage } from '../api/client'
import { useT } from '../i18n/SpracheContext'
import {
  MAX_CARDS_PER_IMPORT,
  splitSpecIntoSections,
  type HeadingLevel,
  type SpecSection,
} from '../lib/specImport'
import { dialogTitleSx } from './dialogChromeSx'

/** Eine anzulegende Karte, wie sie der Stapel-Endpunkt erwartet (#1200). */
export interface SpecCard {
  title: string
  description: string | null
}

interface Props {
  open: boolean
  /** Name der gewählten Datei — nur zur Anzeige, hochgeladen wird sie nicht. */
  fileName: string
  /** Inhalt der Datei, im Browser gelesen. */
  markdown: string
  /** Spalten des Boards, in das eingelesen wird — in ihrer Reihenfolge auf dem Board. */
  columns: readonly BoardColumn[]
  /** Gewählte Zielspalte; der Aufrufer belegt sie mit der ersten Spalte des Boards vor. */
  columnId: number
  onColumnChange: (columnId: number) => void
  onClose: () => void
  /**
   * Legt die ausgewählten Karten in der gewählten Spalte an; wirft bei Fehlschlag (der Dialog bleibt
   * dann offen). Die Spalte reist mit, damit der Aufrufer sie nicht aus einem zweiten Zustand lesen
   * muss — angelegt wird genau dort, wo der Dialog sie beim Druck auf den Knopf zeigte.
   */
  onImport: (cards: SpecCard[], columnId: number) => Promise<void>
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
 * Vorschau und Bestätigung des Spezifikations-Imports (Issue #493, #1201): zeigt, welche Karten aus
 * der gewählten Markdown-Datei entstünden, bevor irgendetwas angelegt wird.
 *
 * Die Vorschau ist Sicherheitsmerkmal, nicht Komfort — eine falsch geratene Trennebene erzeugte
 * sonst Dutzende unbrauchbarer Karten, die einzeln wegzuräumen wären. Deshalb: Ebene umschaltbar,
 * jeder Abschnitt einzeln abwählbar (Vorspann, Inhaltsverzeichnis), Kürzungen und die Obergrenze
 * sichtbar, bevor der Knopf gedrückt wird.
 *
 * Die Datei selbst bleibt im Browser: {@link Props.markdown} ist bereits gelesener Text, an den
 * Server gehen ausschließlich die fertigen Karten.
 */
export function SpecImportDialog({
  open,
  fileName,
  markdown,
  columns,
  columnId,
  onColumnChange,
  onClose,
  onImport,
}: Readonly<Props>) {
  const t = useT()
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
  const tooMany = selected.length > MAX_CARDS_PER_IMPORT

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
        columnId,
      )
      onClose()
    } catch (e) {
      setError(apiErrorMessage(e, t.SPEZIFIKATION.ANLEGEN_FEHLER))
    } finally {
      setBusy(false)
    }
  }

  // Die jeweils andere Ebene — sie wird in der Leermeldung als Ausweg angeboten.
  const otherLevel: HeadingLevel = level === 2 ? 1 : 2
  const emptyMessage =
    markdown.trim() === ''
      ? t.SPEZIFIKATION.DATEI_LEER
      : t.SPEZIFIKATION.KEINE_UEBERSCHRIFT(level, otherLevel)

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth aria-labelledby="spec-import-title">
      <DialogTitle id="spec-import-title" sx={dialogTitleSx}>{t.SPEZIFIKATION.TITEL}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2}>
          <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap">
            <Typography variant="body2" fontWeight={600}>
              {fileName}
            </Typography>
            <TextField
              select
              size="small"
              label={t.SPEZIFIKATION.EBENE}
              value={String(level)}
              onChange={(e) => handleLevelChange(e.target.value)}
              slotProps={{
                htmlInput: { 'aria-label': t.SPEZIFIKATION.EBENE_ARIA },
                select: { native: true },
              }}
            >
              <option value="1">{t.SPEZIFIKATION.TRENNT(1)}</option>
              <option value="2">{t.SPEZIFIKATION.TRENNT(2)}</option>
            </TextField>
            {/* Zielspalte nach dem Muster aus `TransferCardDialog`: nativer Select, Beschriftung am
                Feld. Ohne Leer-Option — es wird stets in genau eine Spalte eingelesen. */}
            <TextField
              select
              size="small"
              label={t.SPEZIFIKATION.SPALTE}
              value={String(columnId)}
              onChange={(e) => onColumnChange(Number(e.target.value))}
              slotProps={{
                htmlInput: { 'aria-label': t.SPEZIFIKATION.ZIELSPALTE },
                select: { native: true },
              }}
            >
              {columns.map((column) => (
                <option key={column.id} value={column.id}>
                  {column.name}
                </option>
              ))}
            </TextField>
          </Stack>

          <Typography variant="caption" color="text.secondary">
            {t.SPEZIFIKATION.NUR_IM_BROWSER}
          </Typography>

          {error !== null && <Alert severity="error">{error}</Alert>}

          {sections.length === 0 ? (
            <Alert severity="info">{emptyMessage}</Alert>
          ) : (
            <>
              {tooMany && (
                <Alert severity="warning">
                  {t.SPEZIFIKATION.ZU_VIELE(selected.length, MAX_CARDS_PER_IMPORT)}
                </Alert>
              )}
              <Typography variant="body2">
                {t.SPEZIFIKATION.AUSGEWAEHLT(selected.length, sections.length)}
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
        <Button onClick={onClose}>{t.ALLGEMEIN.ABBRECHEN}</Button>
        {sections.length > 0 && (
          <Button
            variant="contained"
            onClick={() => void handleImport()}
            disabled={selected.length === 0 || tooMany || busy}
          >
            {t.SPEZIFIKATION.ANLEGEN(selected.length)}
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
  const t = useT()
  return (
    <Stack direction="row" spacing={1} alignItems="flex-start" sx={{ py: 0.25 }}>
      <Checkbox
        size="small"
        checked={checked}
        onChange={onToggle}
        slotProps={{ input: { 'aria-label': t.SPEZIFIKATION.ABSCHNITT_ARIA(section.title) } }}
      />
      <Box sx={{ minWidth: 0, pt: 0.5 }}>
        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
          <Typography variant="body2" fontWeight={600} sx={{ wordBreak: 'break-word' }}>
            {section.title}
          </Typography>
          {section.titleTruncated && <Chip size="small" color="warning" label={t.SPEZIFIKATION.TITEL_GEKUERZT} />}
          {section.descriptionTruncated && (
            <Chip size="small" color="warning" label={t.SPEZIFIKATION.BESCHREIBUNG_GEKUERZT} />
          )}
        </Stack>
        <Typography variant="caption" color="text.secondary" sx={{ wordBreak: 'break-word' }}>
          {section.description === '' ? t.SPEZIFIKATION.KEINE_BESCHREIBUNG : previewText(section.description)}
        </Typography>
      </Box>
    </Stack>
  )
}
