import AddIcon from '@mui/icons-material/Add'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import LinearProgress from '@mui/material/LinearProgress'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { boardsApi, type Board } from '../api/boards'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { cardsApi, type Card } from '../api/cards'
import { epicsApi, type Epic } from '../api/epics'
import { CardDetailModal } from '../components/CardDetailModal'
import { EpicBadge } from '../components/EpicBadge'
import { NewCardModal } from '../components/NewCardModal'
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

export function EpicsPage() {
  const { boardId } = useParams()
  const id = Number.parseInt(boardId ?? '', 10)
  const validId = Number.isInteger(id) && id > 0
  const [board, setBoard] = useState<Board | null>(null)
  const [epics, setEpics] = useState<Epic[]>([])
  const [cards, setCards] = useState<Card[]>([])
  const [selected, setSelected] = useState<Card | null>(null)
  const [creating, setCreating] = useState(false)

  const reload = () => {
    void epicsApi.list(id).then(setEpics)
    void cardsApi.list(id).then(setCards)
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
        {epics.map((epic) => {
          const pct = epic.total > 0 ? (epic.done / epic.total) * 100 : 0
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
                <Typography variant="caption" color="text.secondary">
                  {epic.done}/{epic.total} Arbeitspakete fertig
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
              {/* `mt: auto` schiebt den Balken an den Fuß der Kachel. In der quadratischen Fläche
                  bleibt Luft zwischen Kopf und Fuß; ohne das klebte der Balken am Titel und die
                  untere Hälfte wäre leer. */}
              <LinearProgress
                variant="determinate"
                value={pct}
                aria-label={`Fortschritt ${epic.title}`}
                sx={{ height: 8, borderRadius: 1, mt: 'auto' }}
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
