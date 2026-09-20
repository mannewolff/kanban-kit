import MoreVertIcon from '@mui/icons-material/MoreVert'
import Avatar from '@mui/material/Avatar'
import AvatarGroup from '@mui/material/AvatarGroup'
import Box from '@mui/material/Box'
import Checkbox from '@mui/material/Checkbox'
import Chip from '@mui/material/Chip'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import type { Card } from '../../api/cards'
import type { Epic } from '../../api/epics'
import type { Label } from '../../api/labels'
import type { Member } from '../../api/members'
import { cleanupCountdownLabel, cleanupDaysRemaining } from '../../lib/cleanupCountdown'
import { formatDueDate, isOverdue } from '../../lib/dueDate'
import { MELDER, NUTZER_MAL_SX, TEXT_SCHWACH, ZAHL } from '../../theme'
import { type Dichte, karteDichteSx, karteSx } from '../boardSurfaceSx'
import { EpicBadge } from '../EpicBadge'
import { labelChipSx } from '../labelChipSx'

/** Initialen (max. 2 Zeichen) aus einem Anzeigenamen für Assignee-Avatare. */
function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter((p) => p.length > 0)
  // Leerer/nur-Whitespace-Name hat kein Wort -> Platzhalter. Danach ist parts garantiert nicht
  // leer, sodass parts[0] und das letzte Element ohne Optional-Chaining/Default zugreifbar sind.
  // Das Non-null-`!` bei .at(-1) vermeidet den unerreichbaren Optional-Zweig (100 % Branch) und
  // hält zugleich die von Sonar (S7755) bevorzugte .at()-Form.
  if (parts.length === 0) return '?'
  const first = parts[0].charAt(0)
  const last = parts.length > 1 ? parts.at(-1)!.charAt(0) : ''
  return (first + last).toUpperCase()
}

/**
 * Farbige Label-Chips einer Karte. Als eigene Komponente ausgelagert, damit die
 * `find`-Suche nicht innerhalb der tief verschachtelten Spalten-/Karten-`map` steht.
 */
function CardLabels({ labelIds, boardLabels, cardTitle }: Readonly<{ labelIds: number[]; boardLabels: Label[]; cardTitle: string }>) {
  if (labelIds.length === 0) return null
  return (
    <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', mb: 0.5 }} aria-label={`Labels ${cardTitle}`}>
      {labelIds.map((labelId) => {
        const l = boardLabels.find((b) => b.id === labelId)
        return (
          <Chip
            key={labelId}
            size="small"
            label={l?.name ?? `#${labelId}`}
            sx={{ ...labelChipSx(l?.color), height: 18, '& .MuiChip-label': { px: 0.75, fontSize: '0.65rem' } }}
          />
        )
      })}
    </Stack>
  )
}

/**
 * Zuständigen-Avatare einer Karte. Analog zu {@link CardLabels} ausgelagert, um die
 * `find`-Suche aus der verschachtelten Karten-`map` zu holen.
 */
function CardAssignees({ assigneeIds, members, cardTitle }: Readonly<{ assigneeIds: number[]; members: Member[]; cardTitle: string }>) {
  if (assigneeIds.length === 0) return null
  return (
    <Stack direction="row" justifyContent="flex-end" sx={{ ml: 'auto', flex: 'none' }}>
      {/* Kürzel wie im Entwurf (`.kuerzel`, Z. 845–853): kleine dunkle Male. */}
      <AvatarGroup
        max={4}
        aria-label={`Zuständige ${cardTitle}`}
        sx={{
          '& .MuiAvatar-root': {
            ...NUTZER_MAL_SX,
            width: 19,
            height: 19,
            fontSize: 9,
            fontWeight: 600,
            letterSpacing: '.02em',
            border: 'none',
            ml: '-4px',
          },
        }}
      >
        {assigneeIds.map((uid) => {
          const name = members.find((m) => m.userId === uid)?.displayName ?? `#${uid}`
          return (
            <Avatar key={uid} title={name}>
              {initials(name)}
            </Avatar>
          )
        })}
      </AvatarGroup>
    </Stack>
  )
}

/**
 * Fuß der Karte: Schild des Vorhabens, Archiv-Countdown auf Done und die Frist. Eigene Komponente,
 * weil die Zeile erst entsteht, wenn es etwas zu zeigen gibt — die Bedingung dafür wog in der
 * Karten-Schleife (Plan #1042, P11) mit ihren drei Zweigen schwer.
 */
function KartenFuss({
  card,
  epic,
  doneAt,
  retentionDays,
  overdue,
  onEpicOpen,
}: Readonly<{
  card: Card
  epic: Epic | undefined
  doneAt: string | null
  retentionDays: number
  overdue: boolean
  onEpicOpen?: (epic: Epic) => void
}>) {
  // Verbleibende Tage bis zur Aufräumung, oder `null`, wenn kein Countdown läuft. Als Wert statt
  // als Bedingung: Er trägt die Nullprüfung für `doneAt` gleich mit, und `0` bleibt ein Countdown.
  const restTage = doneAt != null && retentionDays > 0 ? cleanupDaysRemaining(doneAt, retentionDays) : null
  if (!epic && card.dueDate == null && restTage == null) return null
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
      {epic && (
        <EpicBadge epicId={epic.id} title={epic.title} shortcode={epic.shortcode}
          onOpen={onEpicOpen ? () => onEpicOpen(epic) : undefined} />
      )}
      {restTage != null && (
        <Box component="span" sx={{ ...ZAHL, ml: 'auto', fontSize: 10.5, color: TEXT_SCHWACH }}>
          {cleanupCountdownLabel(restTage)}
        </Box>
      )}
      {card.dueDate != null && (
        // Frist wie im Entwurf (`.frist`, `.frist-eng`, Z. 792–793): Plex Mono,
        // überfällig zinnoberrot.
        <Box
          component="span"
          aria-label={`Fällig ${card.title}`}
          data-ueberfaellig={overdue ? 'ja' : undefined}
          sx={{ ...ZAHL, ml: 'auto', fontSize: 10.5, fontWeight: overdue ? 600 : 400, color: overdue ? MELDER.zinnob : TEXT_SCHWACH }}
        >
          {`fällig ${formatDueDate(card.dueDate)}`}
        </Box>
      )}
    </Box>
  )
}

interface Props {
  card: Card
  /** Das Vorhaben, dessen Kürzel die Karte trägt — `undefined`, wenn sie in keinem steht. */
  epic: Epic | undefined
  /** Liegt die Karte in einer Done-Spalte? Trägt Archiv-Countdown und Überfälligkeit. */
  done: boolean
  dichte: Dichte
  selectionMode: boolean
  selected: boolean
  canEdit: boolean
  /** Diese Karte wird gerade gezogen — an ihrer Stelle bleibt die Vertiefung. */
  bewegt: boolean
  members: Member[]
  boardLabels: Label[]
  retentionDays: number
  onDragStart: (e: React.DragEvent) => void
  onDragEnd: () => void
  /** Klick im Auswahlmodus: Karte an- oder abwählen. */
  onSelect: () => void
  /** Klick außerhalb des Auswahlmodus: Karte öffnen. */
  onOpen: () => void
  /** Das ⋮-Menü öffnen; der Aufrufer bekommt die Taste als Anker. */
  onMenu: (anchor: HTMLElement) => void
  onEpicOpen?: (epic: Epic) => void
}

/**
 * Eine Karte des Boards als Platte (Entwurf `.karte`, Z. 751–778): Kopf mit Nummer und
 * Zuständigen, Titel, Labels, Fuß mit Schild und Frist. Kompakt bleiben Kopf und Titel.
 *
 * Eigene Datei seit Plan #1042 (P11): In der Spalten-Schleife von `BoardView` trug der
 * Karten-Callback die kognitive Komplexität 18.
 */
export function BoardKarte({
  card,
  epic,
  done,
  dichte,
  selectionMode,
  selected,
  canEdit,
  bewegt,
  members,
  boardLabels,
  retentionDays,
  onDragStart,
  onDragEnd,
  onSelect,
  onOpen,
  onMenu,
  onEpicOpen,
}: Readonly<Props>) {
  const doneAt = done ? card.movedToDoneAt : null
  const overdue = isOverdue(card.dueDate, done)
  // Bearbeitbar und nicht im Auswahlmodus: Dann trägt die Karte ihren Alltag — sie lässt sich
  // ziehen (Cursor `grab`) und zeigt das ⋮-Menü. Im Auswahlmodus sammelt der Klick nur ein.
  const alltag = canEdit && !selectionMode
  const normal = dichte === 'normal'
  return (
    <Paper
      component="article"
      data-testid={`card-${card.id}`}
      data-dichte={dichte}
      draggable={alltag}
      onDragStart={onDragStart}
      onDragEnd={onDragEnd}
      data-zieh-zustand={bewegt ? 'bewegt' : undefined}
      onClick={selectionMode ? onSelect : onOpen}
      elevation={0}
      sx={{
        display: 'flex',
        flexDirection: 'column',
        ...karteDichteSx(dichte),
        ...karteSx({ gewaehlt: selected, bewegt }),
        cursor: alltag ? 'grab' : 'pointer',
        '&:active': { cursor: alltag ? 'grabbing' : 'pointer' },
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '7px', minHeight: 20 }}>
        {selectionMode && (
          <Checkbox
            size="small"
            checked={selected}
            onChange={onSelect}
            onClick={(e) => e.stopPropagation()}
            slotProps={{ input: { 'aria-label': `Karte ${card.title} auswählen` } }}
            sx={{ p: 0 }}
          />
        )}
        <Box component="span" sx={{ ...ZAHL, fontSize: 11, color: TEXT_SCHWACH }}>{`#${card.number}`}</Box>
        {normal && <CardAssignees assigneeIds={card.assignees} members={members} cardTitle={card.title} />}
        {alltag && (
          <IconButton
            size="small"
            aria-label={`Menü ${card.title}`}
            onClick={(e) => {
              e.stopPropagation()
              onMenu(e.currentTarget)
            }}
            sx={{ ml: card.assignees.length > 0 && normal ? 0 : 'auto', mr: '-6px', my: '-4px', p: '2px', color: TEXT_SCHWACH }}
          >
            <MoreVertIcon fontSize="small" />
          </IconButton>
        )}
      </Box>
      <Typography component="h4" sx={{ m: 0, fontSize: 12.5, fontWeight: 500, lineHeight: 1.35 }}>
        {card.title}
      </Typography>
      {normal && <CardLabels labelIds={card.labels} boardLabels={boardLabels} cardTitle={card.title} />}
      <KartenFuss card={card} epic={epic} doneAt={doneAt} retentionDays={retentionDays} overdue={overdue} onEpicOpen={onEpicOpen} />
    </Paper>
  )
}
