import { useState } from 'react'
import Button from '@mui/material/Button'
import ListItemIcon from '@mui/material/ListItemIcon'
import ListItemText from '@mui/material/ListItemText'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import ArchiveOutlinedIcon from '@mui/icons-material/ArchiveOutlined'
import CheckBoxOutlineBlankOutlinedIcon from '@mui/icons-material/CheckBoxOutlineBlankOutlined'
import CheckBoxOutlinedIcon from '@mui/icons-material/CheckBoxOutlined'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import DriveFileMoveOutlinedIcon from '@mui/icons-material/DriveFileMoveOutlined'
import IndeterminateCheckBoxOutlinedIcon from '@mui/icons-material/IndeterminateCheckBoxOutlined'
import LabelOutlinedIcon from '@mui/icons-material/LabelOutlined'
import type { LabelAction } from '../api/cards'
import type { Label } from '../api/labels'
import { useT } from '../i18n/SpracheContext'
import { LabelErklaerung } from './LabelErklaerung'
import { SURFACE_HOVER_SHADOW } from '../theme'

/** Wie weit ein Label in der Auswahl vertreten ist. */
export type LabelZustand = 'alle' | 'einige' | 'keine'

export interface LabelOption {
  label: Label
  zustand: LabelZustand
}

const ZUSTAND_SYMBOL: Record<LabelZustand, typeof CheckBoxOutlinedIcon> = {
  alle: CheckBoxOutlinedIcon,
  einige: IndeterminateCheckBoxOutlinedIcon,
  keine: CheckBoxOutlineBlankOutlinedIcon,
}

interface Props {
  /** Anzahl der aktuell ausgewählten Karten. */
  count: number
  /** Ob „Verschieben" angeboten wird (nur wenn der Nutzer board-übergreifend verschieben darf). */
  canMove: boolean
  /** Alle Labels des Boards mit ihrem Zustand in der Auswahl. */
  labelOptions: LabelOption[]
  /** Grund, warum „Labels" gesperrt ist; `null` heißt bedienbar. */
  labelsDisabledReason: string | null
  /** Klick auf einen Label-Eintrag: ohne vollen Haken `ADD`, auf einem vollen Haken `REMOVE`. */
  onToggleLabel: (labelId: number, action: LabelAction) => void
  onArchive: () => void
  onMove: () => void
  onDelete: () => void
  onCancel: () => void
}

/**
 * Fixierte Aktionsleiste für die Mehrfachauswahl von Karten. Erscheint, sobald mindestens eine
 * Karte ausgewählt ist, und bietet Labels setzen, Archivieren, optional Verschieben sowie Abbrechen.
 */
export function BulkActionBar({
  count,
  canMove,
  labelOptions,
  labelsDisabledReason,
  onToggleLabel,
  onArchive,
  onMove,
  onDelete,
  onCancel,
}: Readonly<Props>) {
  const t = useT()
  const [labelAnchor, setLabelAnchor] = useState<HTMLElement | null>(null)
  const labelsGesperrt = labelsDisabledReason !== null

  const labelTaste = (
    <Button
      size="small"
      startIcon={<LabelOutlinedIcon />}
      disabled={labelsGesperrt}
      // Der Grund steht im Namen der Taste, nicht nur im Tooltip: Ein gesperrter Button ist für
      // Tastatur und Screenreader nicht erreichbar, der Tooltip käme dort also nie an.
      aria-label={labelsGesperrt ? t.MASSENAKTION.LABELS_GESPERRT(labelsDisabledReason) : undefined}
      onClick={(e) => setLabelAnchor(e.currentTarget)}
    >
      {t.MASSENAKTION.LABELS}
    </Button>
  )

  return (
    <Paper
      elevation={0}
      role="region"
      aria-label={t.MASSENAKTION.REGION}
      sx={{
        // E3, Auslegung „schwebende Ebene": Die Aktionsleiste liegt ueber beliebigem Inhalt, ihre
        // Abhebung ist Funktion und nicht Dekoration. Marken-Schatten statt MUI-Standardschatten.
        boxShadow: SURFACE_HOVER_SHADOW,
        position: 'fixed',
        bottom: 24,
        left: '50%',
        transform: 'translateX(-50%)',
        px: 2,
        py: 1,
        display: 'flex',
        alignItems: 'center',
        gap: 2,
        borderRadius: 1,
        zIndex: (theme) => theme.zIndex.appBar,
      }}
    >
      <Typography variant="body2" sx={{ fontWeight: 600 }}>
        {t.MASSENAKTION.AUSGEWAEHLT(count)}
      </Typography>
      {labelsGesperrt ? (
        // Ein deaktivierter Button meldet keine Mausereignisse; ohne den span-Wrapper bliebe der
        // Hinweis, warum er gesperrt ist, auch am Zeigegerät unsichtbar.
        <Tooltip title={labelsDisabledReason}>
          <span>{labelTaste}</span>
        </Tooltip>
      ) : (
        labelTaste
      )}
      <Menu anchorEl={labelAnchor} open={labelAnchor !== null} onClose={() => setLabelAnchor(null)}>
        {labelOptions.map(({ label, zustand }) => {
          const ZustandSymbol = ZUSTAND_SYMBOL[zustand]
          // Die Erklärung eines Systemlabels hängt rechts am Menüpunkt; sein Fokus bleibt beim Menü
          // (Plan #1668, E6), sodass sie bei den Pfeiltasten erscheint wie beim Darüberfahren.
          return (
            <LabelErklaerung key={label.id} name={label.name} placement="right" eigenerFokus>
              <MenuItem
                // Der Zustand steht im zugänglichen Namen, nicht nur im Häkchen-Symbol: Ein Vollhaken
                // und ein Strich sind rein visuelle Unterschiede, die ohne Bildschirm verschwänden —
                // und genau an ihnen hängt, was ein Klick tut.
                aria-label={`${label.name} — ${t.MASSENAKTION.ZUSTAND[zustand]}`}
                // Das Menü bleibt nach dem Klick offen: Mehrere Labels in einem Zug zu setzen ist der
                // Regelfall, ein Schließen zwänge zum erneuten Öffnen je Label.
                onClick={() => onToggleLabel(label.id, zustand === 'alle' ? 'REMOVE' : 'ADD')}
              >
                <ListItemIcon>
                  <ZustandSymbol fontSize="small" />
                </ListItemIcon>
                <ListItemText primary={label.name} />
              </MenuItem>
            </LabelErklaerung>
          )
        })}
      </Menu>
      {canMove && (
        <Button size="small" startIcon={<DriveFileMoveOutlinedIcon />} onClick={onMove}>
          {t.MASSENAKTION.VERSCHIEBEN}
        </Button>
      )}
      <Button size="small" startIcon={<ArchiveOutlinedIcon />} onClick={onArchive}>
        {t.MASSENAKTION.ARCHIVIEREN}
      </Button>
      <Button size="small" color="error" startIcon={<DeleteOutlineIcon />} onClick={onDelete}>
        {t.MASSENAKTION.IN_DEN_PAPIERKORB}
      </Button>
      <Button size="small" onClick={onCancel}>
        {t.ALLGEMEIN.ABBRECHEN}
      </Button>
    </Paper>
  )
}
