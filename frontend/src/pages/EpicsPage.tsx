import AddIcon from '@mui/icons-material/Add'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import FormControlLabel from '@mui/material/FormControlLabel'
import LinearProgress from '@mui/material/LinearProgress'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Switch from '@mui/material/Switch'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { boardsApi, type Board } from '../api/boards'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { cardsApi, type Card } from '../api/cards'
import { epicsApi, type Epic } from '../api/epics'
import { labelsApi, type Label } from '../api/labels'
import { CardDetailModal } from '../components/CardDetailModal'
import { EpicBadge } from '../components/EpicBadge'
import { labelChipSx } from '../components/labelChipSx'
import { NewCardModal } from '../components/NewCardModal'
import { hiddenEpicsStorageKey } from '../lib/boardHiddenEpics'
import { aggregateMarks, countKinds, sortEpics } from '../lib/epicTiles'
import { useBoardRole } from '../lib/useBoardRole'
import { useProjectName } from '../lib/useProjectName'
import { CARD_LIFT, CARD_SHADOW, CARD_SHADOW_HOVER, PANEL_RADIUS } from '../theme'

function epicToCard(epic: Epic, boardId: number): Card {
  return {
    id: epic.id, boardId, columnId: 0, number: epic.number, title: epic.title,
    description: epic.description, positionInColumn: 0, archived: false, ideaStored: false, movedToDoneAt: null,
    dependencies: [], type: 'EPIC', parentId: null, shortcode: epic.shortcode, assignees: [], dueDate: null, labels: [],
    // Vorhaben tragen keine Herkunft (Issue #607): der Anlege-Endpunkt lehnt sie fuer EPIC ab.
    derivedFrom: null,
  }
}

/**
 * Eine Art in der Zusammensetzung, mit Singular- und Pluralform. Die Anzahl steht als Text neben
 * der Bezeichnung, nicht als blosse Zahl — sonst waere "1 2 5" auf der Kachel nicht lesbar.
 */
function Art({ anzahl, eins, viele }: Readonly<{ anzahl: number; eins: string; viele: string }>) {
  return (
    <Typography variant="caption" color="text.secondary">
      {`${anzahl} ${anzahl === 1 ? eins : viele}`}
    </Typography>
  )
}

export function EpicsPage() {
  const { boardId } = useParams()
  const id = Number.parseInt(boardId ?? '', 10)
  const validId = Number.isInteger(id) && id > 0
  const [board, setBoard] = useState<Board | null>(null)
  const [epics, setEpics] = useState<Epic[]>([])
  const [cards, setCards] = useState<Card[]>([])
  const [labels, setLabels] = useState<Label[]>([])
  const [selected, setSelected] = useState<Card | null>(null)
  const [creating, setCreating] = useState(false)
  // Auf dem Board ausgeblendete Vorhaben (Plan #620). Derselbe Zustand, den `BoardView` liest —
  // Schlüssel und Wertformat kommen deshalb aus `lib/boardHiddenEpics`. Reine Darstellung: kein
  // Archivieren, keine Position, nichts an der Karte, deshalb liegt der Wert nur lokal.
  const [hiddenEpics, setHiddenEpics] = useState<ReadonlySet<number>>(() => {
    try {
      const raw = localStorage.getItem(hiddenEpicsStorageKey(id))
      return raw ? new Set<number>(JSON.parse(raw) as number[]) : new Set<number>()
    } catch {
      return new Set<number>()
    }
  })

  // Ohne funktionierendes localStorage wirkt das Umlegen trotzdem — nur das Merken über den
  // Seitenwechsel hinaus fällt aus (E8).
  const setzeAusgeblendet = (epicId: number, ausblenden: boolean) => {
    const next = new Set(hiddenEpics)
    if (ausblenden) {
      next.add(epicId)
    } else {
      next.delete(epicId)
    }
    setHiddenEpics(next)
    try {
      localStorage.setItem(hiddenEpicsStorageKey(id), JSON.stringify([...next]))
    } catch {
      // localStorage nicht verfügbar
    }
  }

  const reload = () => {
    void epicsApi.list(id).then(setEpics)
    void cardsApi.list(id).then(setCards)
    void labelsApi.list(id).then(setLabels)
  }

  /**
   * Titel zur Kartennummer. Der Server liefert nur Nummern (Issue #633); die Titel stehen in der
   * ohnehin geladenen Kartenliste. Der Rückfall greift, solange beide Abrufe noch nicht beide
   * beantwortet sind — die Nummer allein bleibt dann sichtbar und die Zeile springt nicht.
   */
  const titelZuNummer = (nummer: number) =>
    cards.find((c) => c.number === nummer)?.title ?? 'noch nicht geladen'

  useEffect(() => {
    if (!validId) {
      return
    }
    let active = true
    void boardsApi.get(id).then((b) => {
      if (active) setBoard(b)
    })
    void epicsApi.list(id).then((es) => {
      if (active) setEpics(es)
    })
    void cardsApi.list(id).then((cs) => {
      if (active) setCards(cs)
    })
    // `cardsApi.list` liefert nur `labels: number[]` (IDs) — ohne die Definitionen gibt es weder
    // Namen noch `countOnEpicTile`.
    void labelsApi.list(id).then((ls) => {
      if (active) setLabels(ls)
    })
    return () => {
      active = false
    }
  }, [id, validId])

  /**
   * Öffnet die Karte zu einer Nummer aus dem Herkunftsbaum. Erst gegen `epics`, dann gegen `cards`:
   * `cardsApi.list` filtert serverseitig auf `type == CARD`, ein Vorhaben steht dort also nicht.
   * Eine Nummer, die in keiner geladenen Liste vorkommt — etwa eine frisch per Ingest entstandene
   * Karte —, öffnet nichts. Das ist kein Fehler, nur ein Stand, den diese Seite nicht kennt.
   */
  const oeffneKarte = (nummer: number) => {
    const vorhaben = epics.find((e) => e.number === nummer)
    if (vorhaben) {
      setSelected(epicToCard(vorhaben, id))
      return
    }
    const karte = cards.find((c) => c.number === nummer)
    if (karte) {
      setSelected(karte)
    }
  }

  const { canEdit, canModerate } = useBoardRole(board)
  const projectName = useProjectName(board?.projectId ?? null)

  if (!validId) {
    return <Alert severity="error">Ungültige Board-ID.</Alert>
  }

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Breadcrumbs
          items={[
            { label: 'Projekte', to: '/' },
            ...(board && projectName ? [{ label: projectName, to: `/projects/${board.projectId}` }] : []),
            ...(board ? [{ label: board.name, to: `/boards/${id}` }] : []),
            { label: 'Vorhaben' },
          ]}
        />
        {canEdit && (
          <Button variant="contained" size="small" startIcon={<AddIcon />} onClick={() => setCreating(true)}>
            Neues Vorhaben
          </Button>
        )}
      </Stack>

      {/* Kachelraster statt gestapelter Zeilen: Ein Vorhaben ist ein Gegenstand, den man
          überblickt, keine Tabellenzeile. Die Kacheln sind quadratisch (`aspectRatio: '1'`) und
          brechen um, die Seite wird bei vielen Vorhaben länger — beides Nutzerentscheidung
          (#656). `minmax(min(240px, 100%), 1fr)` klemmt die Spalte: Mit `240px` allein liefe das
          Raster auf schmalen Fenstern über den Rand hinaus. */}
      <Box
        data-testid="vorhaben-raster"
        sx={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(min(240px, 100%), 1fr))',
          gap: 2,
        }}
      >
        {sortEpics(epics, cards, labels).map((epic) => {
          const pct = epic.total > 0 ? (epic.done / epic.total) * 100 : 0
          const arten = countKinds(epic, cards)
          const marken = aggregateMarks(epic, cards, labels)
          // Leer heisst wie in #662: keine Mitglieder UND keine Anforderung. Ein Vorhaben mit
          // Anforderung, aber ohne Karten ist eroeffnet, nicht leer.
          const leer = epic.total === 0 && epic.requirementCardNumber === null
          return (
            <Paper
              key={epic.id}
              data-testid={`vorhaben-kachel-${epic.id}`}
              variant="outlined"
              onClick={() => setSelected(epicToCard(epic, id))}
              sx={{
                p: 2,
                cursor: 'pointer',
                minWidth: 0,
                aspectRatio: '1',
                display: 'flex',
                flexDirection: 'column',
                borderRadius: `${PANEL_RADIUS}px`,
                boxShadow: CARD_SHADOW,
                transition: 'box-shadow .2s ease, transform .2s ease',
                '&:hover': { boxShadow: CARD_SHADOW_HOVER, transform: `translateY(${CARD_LIFT}px)` },
                '@media (prefers-reduced-motion: reduce)': {
                  transition: 'none',
                  '&:hover': { boxShadow: CARD_SHADOW_HOVER, transform: 'none' },
                },
              }}
            >
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
                <EpicBadge epicId={epic.id} title={epic.title} shortcode={epic.shortcode} />
                <Typography variant="subtitle1" sx={{ fontWeight: 600, flexGrow: 1 }}>
                  {epic.title}
                </Typography>
                {/* Immer neutral, auch bei sortenreinen Vorhaben: `total` zaehlt ALLE
                    Mitglieder, und sobald dieselbe Kachel Anforderungen und Plaene ausweist, waere
                    "n Arbeitspakete fertig" schlicht falsch. Eine nur bedingte Umbenennung waere
                    kein Fortschritt — sie liesse die Bestandstests gruen. */}
                <Typography variant="caption" color="text.secondary">
                  {epic.done} von {epic.total} fertig
                </Typography>
              </Stack>
              {/* Woraus das Vorhaben entstanden ist. Traegt es keine Anforderung, steht hier
                  nichts — kein Platzhalter und keine Ersatzanzeige aus den Wurzeln (Plan #637, E6).
                  `component="button"` rendert ein echtes <button>: per Tab erreichbar und per Enter
                  ausloesbar. Ein onClick auf einer Anzeigekomponente kaeme durch alle Gates —
                  jsx-a11y prueft nur DOM-Elemente in Kleinschreibung, keine MUI-Komponenten — und
                  waere per Tastatur trotzdem unerreichbar. */}
              {epic.requirementCardNumber !== null && (
                <Stack direction="row" spacing={0.5} alignItems="baseline" sx={{ mb: 1 }}>
                  <Typography variant="caption" color="text.secondary">
                    Anforderung:
                  </Typography>
                  <Link
                    component="button"
                    type="button"
                    variant="caption"
                    underline="hover"
                    textAlign="left"
                    onClick={(e) => {
                      // Ohne stopPropagation oeffnete derselbe Klick zusaetzlich das
                      // Vorhaben-Detail — der Kachel-Klick liegt eine Ebene darueber.
                      e.stopPropagation()
                      oeffneKarte(epic.requirementCardNumber as number)
                    }}
                  >
                    {`#${epic.requirementCardNumber} · ${titelZuNummer(epic.requirementCardNumber)}`}
                  </Link>
                </Stack>
              )}
              {/* Traegt ein Vorhaben keine Anforderung, steht das ausdruecklich da. Das revidiert
                  bewusst Plan #637 (E6), der keinen Platzhalter wollte: Dort zaehlte Sparsamkeit,
                  hier Unterscheidbarkeit — eine leere Stelle waere nicht von einem Ladefehler zu
                  unterscheiden. */}
              {epic.requirementCardNumber === null && (
                <Typography variant="caption" color="text.secondary" sx={{ mb: 1 }}>
                  Keine Anforderung hinterlegt.
                </Typography>
              )}

              {/* Woraus das Vorhaben besteht und was daran liegen geblieben ist (#656) — die
                  Rechnung dazu steht in `lib/epicTiles.ts` (#662). Eine Art mit null Karten wird
                  nicht genannt: "0 Plaene" ist keine Aussage, nur Rauschen. */}
              {leer ? (
                <Typography variant="caption" color="text.secondary">
                  Noch keine Karten zugeordnet.
                </Typography>
              ) : (
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ mb: 1 }}>
                  {arten.requirements > 0 && <Art anzahl={arten.requirements} eins="Anforderung" viele="Anforderungen" />}
                  {arten.plans > 0 && <Art anzahl={arten.plans} eins="Plan" viele="Pläne" />}
                  {arten.workItems > 0 && <Art anzahl={arten.workItems} eins="Arbeitspaket" viele="Arbeitspakete" />}
                </Stack>
              )}

              {marken.length > 0 && (
                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" sx={{ mb: 1 }}>
                  {marken.map((marke) => (
                    <Typography
                      key={marke.name}
                      variant="caption"
                      component="span"
                      sx={{ ...labelChipSx(marke.color), px: 0.75, borderRadius: 10, whiteSpace: 'nowrap' }}
                    >
                      {`${marke.name} ${marke.count}`}
                    </Typography>
                  ))}
                </Stack>
              )}
              {/* `mt: auto` schiebt den Balken an den Fuß der Kachel. In der quadratischen Fläche
                  bleibt Luft zwischen Kopf und Fuß; ohne das klebte der Balken am Titel und die
                  untere Hälfte wäre leer. */}
              <LinearProgress
                variant="determinate"
                value={pct}
                aria-label={`Fortschritt ${epic.title}`}
                sx={{ height: 8, borderRadius: 1, mt: 'auto' }}
              />
              {/* Der Schalter sitzt hier, weil die Kachel der Ort ist, an dem man ein Vorhaben in
                  der Hand hat (Plan #620, E9). Der Rückweg liegt am Board an der Spaltenmarke, ein
                  zweites Setz-Element dort entfällt bewusst. `Switch` ist ein echtes
                  <input type="checkbox">: per Tab erreichbar und mit der Leertaste auslösbar. Ein
                  onClick auf einer Anzeigekomponente käme durch alle Gates — jsx-a11y prüft nur
                  DOM-Elemente in Kleinschreibung, keine MUI-Komponenten — und wäre per Tastatur
                  trotzdem unerreichbar. */}
              <FormControlLabel
                sx={{ mt: 1, mr: 0 }}
                // Ohne stopPropagation öffnete derselbe Klick zusätzlich das Vorhaben-Detail — der
                // Kachel-Klick liegt eine Ebene darüber (E11).
                onClick={(e) => e.stopPropagation()}
                control={
                  <Switch
                    size="small"
                    checked={hiddenEpics.has(epic.id)}
                    onChange={(e) => setzeAusgeblendet(epic.id, e.target.checked)}
                  />
                }
                label={
                  <Typography variant="caption" color="text.secondary">
                    Auf dem Board ausblenden
                  </Typography>
                }
              />
            </Paper>
          )
        })}
      </Box>
      {epics.length === 0 && <Typography color="text.secondary">Noch keine Vorhaben.</Typography>}

      <NewCardModal
        open={creating}
        epicOnly
        columnName=""
        epics={[]}
        onClose={() => setCreating(false)}
        onSubmit={async (input) => {
          await epicsApi.create(id, input.title, input.description, input.shortcode)
          reload()
        }}
      />

      {selected && (
        <CardDetailModal
          card={selected}
          canEdit={canEdit}
          canModerateComments={canModerate}
          onClose={() => setSelected(null)}
          onChanged={reload}
        />
      )}
    </Box>
  )
}
