import List from '@mui/material/List'
import ListItem from '@mui/material/ListItem'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemText from '@mui/material/ListItemText'
import Switch from '@mui/material/Switch'
import Typography from '@mui/material/Typography'
import type { Epic } from '../api/epics'
import { epicShortcode } from '../lib/epicMeta'
import { EpicBadge } from './EpicBadge'

interface Props {
  /** Bereits sortiert — die Liste übernimmt die Reihenfolge, sie stellt keine eigene her. */
  epics: readonly Epic[]
  hidden: ReadonlySet<number>
  /**
   * Gleichsinnig mit `EpicsPage.setzeAusgeblendet`: `true` heißt ausblenden. Zwei gegenläufige
   * Booleans über eine Komponentengrenze hinweg wären die Stelle, an der still verwechselt wird
   * (Plan #846, E4).
   */
  onToggle: (epicId: number, ausblenden: boolean) => void
  onOpen: (epic: Epic) => void
}

/**
 * Kompakte Liste aller Vorhaben eines Boards mit je einem Schalter „eingeblendet / ausgeblendet".
 * Reine Präsentation: kein eigener Zustand, kein API-Zugriff, kein Rechte-Check — Ausblenden ist
 * eine persönliche Ansichtseinstellung, und einem Nur-Leser das Aufräumen seiner eigenen Ansicht
 * zu verbieten wäre keine Schutzwirkung (Plan #846, E12).
 */
export function EpicVisibilityList({ epics, hidden, onToggle, onOpen }: Readonly<Props>) {
  return (
    <List data-testid="vorhaben-liste">
      {epics.map((epic) => {
        const istAusgeblendet = hidden.has(epic.id)
        const kuerzel = epicShortcode(epic.title, epic.shortcode)
        return (
          <ListItem
            key={epic.id}
            data-testid={`vorhaben-zeile-${epic.id}`}
            disablePadding
            // Der Schalter liegt als `secondaryAction` im DOM **neben** dem Bedienelement der
            // Zeile, nicht darin: kein verschachteltes Bedienelement und kein `stopPropagation`
            // nötig, um das Öffnen beim Schalten zu unterdrücken (Plan #846, E2).
            secondaryAction={
              <Switch
                size="small"
                checked={!istAusgeblendet}
                onChange={(e) => onToggle(epic.id, !e.target.checked)}
                // Der Name nennt die anstehende Aktion, den Zustand trägt der Schalter selbst:
                // Bei einem nativen Kontrollkästchen leitet die Vorlesehilfe `aria-checked` aus
                // `checked` ab, ein zusätzliches Attribut wäre nur eine zweite Wahrheit.
                slotProps={{
                  input: {
                    'aria-label': `Vorhaben ${kuerzel} ${istAusgeblendet ? 'einblenden' : 'ausblenden'}`,
                  },
                }}
              />
            }
          >
            <ListItemButton onClick={() => onOpen(epic)}>
              {/* Ohne `onOpen`: reine Anzeige. Ein Knopf im Knopf wäre ein verschachteltes
                  Bedienelement, und die Zeile führt ohnehin zum selben Vorhaben (Plan #846, E3). */}
              <EpicBadge
                epicId={epic.id}
                title={epic.title}
                shortcode={epic.shortcode}
                sx={{ mr: 1 }}
              />
              <ListItemText primary={epic.title} />
              {/* Der Zustand steht als Text da, nicht nur als Schalterstellung: So ist er auch
                  beim Überfliegen der Zeile lesbar (Plan #846, E5). */}
              {istAusgeblendet && (
                <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic', ml: 1 }}>
                  Ausgeblendet
                </Typography>
              )}
            </ListItemButton>
          </ListItem>
        )
      })}
    </List>
  )
}
