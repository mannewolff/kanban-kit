import MoreVertIcon from '@mui/icons-material/MoreVert'
import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import IconButton from '@mui/material/IconButton'
import LinearProgress from '@mui/material/LinearProgress'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import Switch from '@mui/material/Switch'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import type { Card } from '../api/cards'
import type { Epic } from '../api/epics'
import type { Label } from '../api/labels'
import { epicShortcode } from '../lib/epicMeta'
import { aggregateMarks, countKinds } from '../lib/epicTiles'
import { NUT, PANEL_RADIUS, PLATTE, RAND, SCHATTEN_PLATTE, TABELLENZIFFERN, TEXT_SCHWACH } from '../theme'
import { EpicBadge } from './EpicBadge'
import { labelChipSx } from './labelChipSx'

/**
 * Zeilenhöhe eines Marken-Chips in der Vorhaben-Zeile.
 *
 * Fest gesetzt und nicht aus dem Theme abgeleitet, weil daraus die Obergrenze des Marken-Bereichs
 * gerechnet wird (zwei Zeilen). Hinge die Zeilenhöhe an der Schriftgröße des Themes, wäre die
 * Zeilenhöhe von einer Theme-Änderung abhängig, ohne dass das hier sichtbar wäre.
 */
const MARKE_ZEILENHOEHE = '1.5rem'

/**
 * Eine Art in der Zusammensetzung, mit Singular- und Pluralform. Die Anzahl steht als Text neben
 * der Bezeichnung, nicht als blosse Zahl — sonst waere "1 2 5" in der Zeile nicht lesbar.
 */
function Art({ anzahl, eins, viele }: Readonly<{ anzahl: number; eins: string; viele: string }>) {
  return (
    <Typography variant="caption" color="text.secondary">
      {`${anzahl} ${anzahl === 1 ? eins : viele}`}
    </Typography>
  )
}

interface Props {
  /** Bereits sortiert — die Liste übernimmt die Reihenfolge, sie stellt keine eigene her. */
  epics: readonly Epic[]
  hidden: ReadonlySet<number>
  /** Die Karten des Boards — Grundlage für Zusammensetzung und Marken (`lib/epicTiles`). */
  cards: Card[]
  /** Die Labels des Boards; nur die mit `countOnEpicTile` erscheinen als Marke. */
  labels: Label[]
  /** Titel zur Nummer der Anforderung; der Server liefert nur die Nummer (Issue #633). */
  titelZuNummer: (nummer: number) => string
  /**
   * Gleichsinnig mit `EpicsPage.setzeAusgeblendet`: `true` heißt ausblenden. Zwei gegenläufige
   * Booleans über eine Komponentengrenze hinweg wären die Stelle, an der still verwechselt wird
   * (Plan #846, E4).
   */
  onToggle: (epicId: number, ausblenden: boolean) => void
  onOpen: (epic: Epic) => void
  /** Öffnet die Karte zur Nummer — hier die Anforderung, aus der das Vorhaben entstanden ist. */
  onOpenCard: (nummer: number) => void
  /**
   * Öffnet das Menü am Vorhaben. Fehlt es, hat die Zeile keinen Menü-Knopf: Das Menü trägt nur
   * noch „Löschen", und ein leeres Menü wäre kein Bedienelement (Plan #1488, E7).
   */
  onMenu?: (epic: Epic, anchor: HTMLElement) => void
}

/**
 * Die Vorhaben eines Boards als Tafel, eine Zeile je Vorhaben (Plan #1488, E1/E3): oben Kürzel,
 * Titel, Fortschritt, Menü und ganz rechts der Schalter „eingeblendet / ausgeblendet"; darunter
 * Anforderung, Zusammensetzung und Marken; am Fuß der Balken.
 *
 * Reine Präsentation: kein eigener Zustand, kein API-Zugriff, kein Rechte-Check — Ausblenden ist
 * eine persönliche Ansichtseinstellung, und einem Nur-Leser das Aufräumen seiner eigenen Ansicht
 * zu verbieten wäre keine Schutzwirkung (Plan #846, E12).
 */
export function EpicVisibilityList({
  epics,
  hidden,
  cards,
  labels,
  titelZuNummer,
  onToggle,
  onOpen,
  onOpenCard,
  onMenu,
}: Readonly<Props>) {
  return (
    <Box
      component="ul"
      data-testid="vorhaben-liste"
      // Eine Tafel wie die Liste der Vorlage (Plan #1488, E3): eine Platte, die Zeilen durch
      // Haarlinien getrennt — keine eigene erhabene Platte je Vorhaben.
      sx={{
        listStyle: 'none',
        m: 0,
        p: 0,
        borderRadius: `${PANEL_RADIUS}px`,
        border: `1px solid ${RAND}`,
        bgcolor: PLATTE,
        boxShadow: SCHATTEN_PLATTE,
        overflow: 'hidden',
      }}
    >
      {epics.map((epic) => {
        const istAusgeblendet = hidden.has(epic.id)
        const kuerzel = epicShortcode(epic.title, epic.shortcode)
        const pct = epic.total > 0 ? (epic.done / epic.total) * 100 : 0
        const arten = countKinds(epic, cards)
        const marken = aggregateMarks(epic, cards, labels)
        // Leer heisst wie in #662: keine Mitglieder UND keine Anforderung. Ein Vorhaben mit
        // Anforderung, aber ohne Karten ist eroeffnet, nicht leer.
        const leer = epic.total === 0 && epic.requirementCardNumber === null
        return (
          <Box
            component="li"
            key={epic.id}
            data-testid={`vorhaben-zeile-${epic.id}`}
            // Die Fläche öffnet per Maus; für die Tastatur ist der Titel ein eigener Knopf
            // (Plan #1488, E2). Ein Knopf über die ganze Zeile trüge Anforderung, Menü und Schalter
            // als Bedienelemente in sich (Plan #846, E3).
            onClick={() => onOpen(epic)}
            sx={{
              px: 2,
              py: 1.5,
              cursor: 'pointer',
              '&:not(:last-of-type)': { borderBottom: `1px solid ${RAND}` },
              // Abgeschwächt über die eingelassene Fläche und die schwache Schrift, nicht über
              // Deckkraft (Plan #1488, E6): Die Zeile bleibt bedienbar, ihr Text muss 4,5:1
              // halten. Den Zustand trägt zusätzlich der Text „Ausgeblendet".
              ...(istAusgeblendet && { bgcolor: NUT, color: TEXT_SCHWACH }),
            }}
          >
            <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0 }}>
              <EpicBadge epicId={epic.id} title={epic.title} shortcode={epic.shortcode} />
              {/* Einzeilig mit Auslassungspunkten (Plan #1488, E4). Die Kürzung ist reines CSS — der
                  volle Titel steht im DOM, der Screenreader liest ihn ganz; der Tooltip zeigt ihn
                  beim Überfahren und beim Tastaturfokus. `minWidth: 0` lässt das Flex-Kind
                  schrumpfen, sonst greift die Kürzung nicht. */}
              <Tooltip title={epic.title}>
                <ButtonBase
                  disableRipple
                  onClick={(e) => {
                    // Sonst öffnete der Flächen-Handler der Zeile dasselbe Vorhaben ein zweites Mal.
                    e.stopPropagation()
                    onOpen(epic)
                  }}
                  sx={{
                    flexGrow: 1,
                    minWidth: 0,
                    display: 'block',
                    justifyContent: 'flex-start',
                    font: 'inherit',
                    fontWeight: 600,
                    color: 'inherit',
                    textAlign: 'left',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  {epic.title}
                </ButtonBase>
              </Tooltip>
              {/* Der Zustand steht als Text da, nicht nur als Schalterstellung: So ist er auch
                  beim Überfliegen der Zeile lesbar (Plan #846, E5). */}
              {istAusgeblendet && (
                <Typography variant="caption" sx={{ fontStyle: 'italic', flexShrink: 0, color: TEXT_SCHWACH }}>
                  Ausgeblendet
                </Typography>
              )}
              {/* Immer neutral, auch bei sortenreinen Vorhaben: `total` zaehlt ALLE Mitglieder. */}
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{ ...TABELLENZIFFERN, flexShrink: 0, whiteSpace: 'nowrap' }}
              >
                {epic.done} von {epic.total} fertig
              </Typography>
              {onMenu && (
                <IconButton
                  size="small"
                  aria-label={`Menü ${epic.title}`}
                  onClick={(e) => {
                    // Ohne stopPropagation öffnete derselbe Klick zusätzlich das Vorhaben-Detail.
                    e.stopPropagation()
                    onMenu(epic, e.currentTarget)
                  }}
                >
                  <MoreVertIcon fontSize="small" />
                </IconButton>
              )}
              <Switch
                size="small"
                checked={!istAusgeblendet}
                onClick={(e) => e.stopPropagation()}
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
            </Stack>

            {/* Zweite Zeile: Herkunft, Zusammensetzung und Marken. Auf schmalem Schirm bricht sie um
                (Plan #1488, E3). */}
            <Stack
              direction="row"
              spacing={2}
              useFlexGap
              flexWrap="wrap"
              alignItems="baseline"
              sx={{ mt: 0.5, minWidth: 0 }}
            >
              {/* `component="button"` rendert ein echtes <button>: per Tab erreichbar und per Enter
                  auslösbar. */}
              {epic.requirementCardNumber === null ? (
                <Typography variant="caption" color="text.secondary">
                  Keine Anforderung hinterlegt.
                </Typography>
              ) : (
                <Stack direction="row" spacing={0.5} alignItems="baseline" sx={{ minWidth: 0, maxWidth: '100%' }}>
                  <Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0 }}>
                    Anforderung:
                  </Typography>
                  <Link
                    component="button"
                    type="button"
                    variant="caption"
                    underline="hover"
                    textAlign="left"
                    sx={{ minWidth: 0, display: 'block', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}
                    onClick={(e) => {
                      // Ohne stopPropagation oeffnete derselbe Klick zusaetzlich das Vorhaben-Detail.
                      e.stopPropagation()
                      onOpenCard(epic.requirementCardNumber as number)
                    }}
                  >
                    {`#${epic.requirementCardNumber} · ${titelZuNummer(epic.requirementCardNumber)}`}
                  </Link>
                </Stack>
              )}

              {/* Eine Art mit null Karten wird nicht genannt: "0 Plaene" ist keine Aussage. */}
              {leer ? (
                <Typography variant="caption" color="text.secondary">
                  Noch keine Karten zugeordnet.
                </Typography>
              ) : (
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                  {arten.requirements > 0 && <Art anzahl={arten.requirements} eins="Anforderung" viele="Anforderungen" />}
                  {arten.plans > 0 && <Art anzahl={arten.plans} eins="Plan" viele="Pläne" />}
                  {arten.workItems > 0 && <Art anzahl={arten.workItems} eins="Arbeitspaket" viele="Arbeitspakete" />}
                </Stack>
              )}

              {/* Höchstens zwei Zeilen Marken; der Zuschlag ist der Zeilenabstand (4px). */}
              {marken.length > 0 && (
                <Stack
                  direction="row"
                  spacing={0.5}
                  useFlexGap
                  flexWrap="wrap"
                  sx={{ maxHeight: `calc(2 * ${MARKE_ZEILENHOEHE} + 4px)`, overflow: 'hidden' }}
                >
                  {marken.map((marke) => (
                    <Typography
                      key={marke.name}
                      variant="caption"
                      component="span"
                      sx={{
                        ...labelChipSx(marke.color),
                        px: 0.75,
                        borderRadius: 10,
                        whiteSpace: 'nowrap',
                        lineHeight: MARKE_ZEILENHOEHE,
                      }}
                    >
                      {`${marke.name} ${marke.count}`}
                    </Typography>
                  ))}
                </Stack>
              )}
            </Stack>

            <LinearProgress
              variant="determinate"
              value={pct}
              // Gedämpft wie die Zeile: Die Füllung verliert die Leitfarbe, der Wert bleibt lesbar.
              color={istAusgeblendet ? 'inherit' : 'primary'}
              aria-label={`Fortschritt ${epic.title}`}
              sx={{ mt: 1, height: 6, borderRadius: 1 }}
            />
          </Box>
        )
      })}
    </Box>
  )
}
