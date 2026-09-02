import AddIcon from '@mui/icons-material/Add'
import MoreVertIcon from '@mui/icons-material/MoreVert'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import FormControlLabel from '@mui/material/FormControlLabel'
import IconButton from '@mui/material/IconButton'
import LinearProgress from '@mui/material/LinearProgress'
import Link from '@mui/material/Link'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
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
import { membersApi, type Member } from '../api/members'
import { CardDetailModal } from '../components/CardDetailModal'
import { EpicBadge } from '../components/EpicBadge'
import { labelChipSx } from '../components/labelChipSx'
import { NewCardModal } from '../components/NewCardModal'
import { hiddenEpicsStorageKey } from '../lib/boardHiddenEpics'
import { epicToCard } from '../lib/epicToCard'
import { aggregateMarks, countKinds, sortEpics, visibleEpics } from '../lib/epicTiles'
import { useBoardRole } from '../lib/useBoardRole'
import { useProjectName } from '../lib/useProjectName'
import { CARD_LIFT, CARD_SHADOW, CARD_SHADOW_HOVER, PANEL_RADIUS } from '../theme'

/**
 * Zeilenhöhe eines Marken-Chips auf der Vorhaben-Kachel.
 *
 * Fest gesetzt und nicht aus dem Theme abgeleitet, weil daraus die Obergrenze des Marken-Bereichs
 * gerechnet wird (zwei Zeilen). Hinge die Zeilenhöhe an der Schriftgröße des Themes, wäre die
 * Kachelhöhe von einer Theme-Änderung abhängig, ohne dass das hier sichtbar wäre.
 */
const MARKE_ZEILENHOEHE = '1.5rem'

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

/**
 * Der gespeicherte Stand der ausgeblendeten Vorhaben eines Boards. Steht als eigene Funktion da,
 * weil er an zwei Stellen gebraucht wird — beim ersten Mount und nach jedem Board-Wechsel (Plan
 * #703, E11). Zwei Abschriften desselben Lesevorgangs wären zwei Gelegenheiten, das Wertformat
 * auseinanderlaufen zu lassen.
 *
 * Ein defektes oder gesperrtes `localStorage` liefert „nichts ausgeblendet" statt die Seite
 * scheitern zu lassen: Die Ausblendung ist reine Darstellung, ihr Verlust kostet keine Daten.
 */
function leseAusgeblendet(boardId: number): ReadonlySet<number> {
  try {
    const raw = localStorage.getItem(hiddenEpicsStorageKey(boardId))
    return raw ? new Set<number>(JSON.parse(raw) as number[]) : new Set<number>()
  } catch {
    return new Set<number>()
  }
}

export function EpicsPage() {
  const { boardId } = useParams()
  const id = Number.parseInt(boardId ?? '', 10)
  const validId = Number.isInteger(id) && id > 0
  const [board, setBoard] = useState<Board | null>(null)
  const [epics, setEpics] = useState<Epic[]>([])
  const [cards, setCards] = useState<Card[]>([])
  const [labels, setLabels] = useState<Label[]>([])
  const [members, setMembers] = useState<Member[]>([])
  const [selected, setSelected] = useState<Card | null>(null)
  const [creating, setCreating] = useState(false)
  // Ausgeblendete Vorhaben (Plan #620, Wirkung im Kachelraster aus Plan #703, E1). Derselbe
  // Zustand, den `BoardView` liest — Schlüssel und Wertformat kommen deshalb aus
  // `lib/boardHiddenEpics`. Reine Darstellung: kein Archivieren, keine Position, nichts an der
  // Karte, deshalb liegt der Wert nur lokal.
  const [hiddenEpics, setHiddenEpics] = useState<ReadonlySet<number>>(() => leseAusgeblendet(id))
  // Der Zeige-Modus gehört zur Sitzung, nicht zum Board: Er wird nicht gespeichert und startet
  // auf jedem Board aus.
  const [zeigeAusgeblendete, setZeigeAusgeblendete] = useState(false)
  const [menu, setMenu] = useState<{ epic: Epic; anchor: HTMLElement } | null>(null)

  // Die Route `/boards/:boardId/vorhaben` hält die Komponente bei einem reinen Parameterwechsel
  // gemountet — der `useState`-Initializer läuft dann nicht erneut (Plan #703, E11). Ohne dieses
  // Nachlesen filterte das neue Board mit dem Stand des vorigen. Der Umschalter geht dabei aus:
  // Sonst startete das neue Board in einem Zeige-Modus, in den dort niemand geschaltet hat.
  useEffect(() => {
    setHiddenEpics(leseAusgeblendet(id))
    setZeigeAusgeblendete(false)
  }, [id])

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

  // Projektmitglieder für die Zuständigen an der geöffneten Karte, sobald das Projekt bekannt ist —
  // dasselbe Muster wie auf dem Board. Ein Fehlschlag lässt die Liste leer, statt die Seite
  // scheitern zu lassen: Die Vorhaben-Übersicht selbst braucht die Mitglieder nicht.
  const projectId = board?.projectId
  useEffect(() => {
    if (projectId == null) {
      return
    }
    void membersApi.list(projectId).then(setMembers).catch(() => setMembers([]))
  }, [projectId])

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

  // Gezählt wird die Schnittmenge mit den Vorhaben dieses Boards, nicht die Größe des
  // gespeicherten Satzes: Eine ID überlebt dort das Löschen ihres Vorhabens (Issue #704), und
  // eine Zahl, hinter der im Zeige-Modus weniger Kacheln stehen, wäre ein sichtbarer Widerspruch.
  const ausgeblendeteAnzahl = epics.filter((epic) => hiddenEpics.has(epic.id)).length

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

      {/* Ist nichts ausgeblendet, erscheint der Umschalter gar nicht — er böte nichts zu zeigen
          (Plan #703, E3). Er verschwindet auch wieder, sobald das letzte Vorhaben im Zeige-Modus
          eingeblendet wird; der Zeige-Modus ist damit gegenstandslos. */}
      {ausgeblendeteAnzahl > 0 && (
        <FormControlLabel
          sx={{ mb: 2 }}
          control={
            <Switch
              size="small"
              checked={zeigeAusgeblendete}
              onChange={(e) => setZeigeAusgeblendete(e.target.checked)}
            />
          }
          label={
            <Typography variant="caption" color="text.secondary">
              {`Ausgeblendete zeigen (${ausgeblendeteAnzahl})`}
            </Typography>
          }
        />
      )}

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
        {visibleEpics(sortEpics(epics, cards, labels), hiddenEpics, zeigeAusgeblendete).map((epic) => {
          const pct = epic.total > 0 ? (epic.done / epic.total) * 100 : 0
          const arten = countKinds(epic, cards)
          const marken = aggregateMarks(epic, cards, labels)
          // Leer heisst wie in #662: keine Mitglieder UND keine Anforderung. Ein Vorhaben mit
          // Anforderung, aber ohne Karten ist eroeffnet, nicht leer.
          const leer = epic.total === 0 && epic.requirementCardNumber === null
          // Nur im Zeige-Modus wahr — sonst stünde die Kachel gar nicht im Raster.
          const istAusgeblendet = hiddenEpics.has(epic.id)
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
                // `aspectRatio` allein hält die Höhe nicht: Ein Flex-Container hat `min-height:
                // auto`, sein Inhalt dehnt ihn also über das Quadrat hinaus — genau daher kamen
                // die unterschiedlich hohen Kacheln. `hidden` zieht die Grenze, die drei
                // Begrenzungen darunter (Titel, Anforderung, Mittelteil) sorgen dafür, dass sie
                // nichts Sinntragendes abschneidet.
                overflow: 'hidden',
                // Die Dämpfung hängt an derselben Bedingung wie der Text „Ausgeblendet" unten:
                // Sie unterstützt ihn, sie ersetzt ihn nicht.
                opacity: istAusgeblendet ? 0.55 : 1,
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
                {/* Höchstens zwei Zeilen. Einzeilig wie sonst im Bestand (`noWrap`) wäre hier zu
                    wenig — Vorhaben-Titel sind ganze Sätze und wären fast immer beschnitten.
                    `minWidth: 0` erlaubt dem Flex-Kind zu schrumpfen; ohne das greift die
                    Kürzung nicht, weil der Text seine eigene Mindestbreite erzwingt. */}
                <Typography
                  variant="subtitle1"
                  sx={{
                    fontWeight: 600,
                    flexGrow: 1,
                    minWidth: 0,
                    display: '-webkit-box',
                    WebkitBoxOrient: 'vertical',
                    WebkitLineClamp: 2,
                    overflow: 'hidden',
                  }}
                >
                  {epic.title}
                </Typography>
                {/* Immer neutral, auch bei sortenreinen Vorhaben: `total` zaehlt ALLE
                    Mitglieder, und sobald dieselbe Kachel Anforderungen und Plaene ausweist, waere
                    "n Arbeitspakete fertig" schlicht falsch. Eine nur bedingte Umbenennung waere
                    kein Fortschritt — sie liesse die Bestandstests gruen. */}
                <Typography variant="caption" color="text.secondary">
                  {epic.done} von {epic.total} fertig
                </Typography>
                {/* Der Zustand steht als Text da, nicht nur als blassere Fläche: Eine reine
                    Opacity-Änderung wäre für Screenreader gar nicht wahrnehmbar (Accessibility,
                    Priorität 4 in CLAUDE.md) und im Test nur über einen geratenen Stilwert
                    greifbar. */}
                {istAusgeblendet && (
                  <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic' }}>
                    Ausgeblendet
                  </Typography>
                )}
                {/* Kein Rechte-Check (Plan #703, E8): Ausblenden verändert nichts am Server, und
                    einem Nur-Leser zu verbieten, seine eigene Ansicht aufzuräumen, wäre keine
                    Schutzwirkung. */}
                <IconButton
                  size="small"
                  aria-label={`Menü ${epic.title}`}
                  onClick={(e) => {
                    // Ohne stopPropagation öffnete derselbe Klick zusätzlich das Vorhaben-Detail —
                    // der Kachel-Klick liegt eine Ebene darüber (E11 aus Plan #620).
                    e.stopPropagation()
                    setMenu({ epic, anchor: e.currentTarget })
                  }}
                  sx={{ mt: -0.5, mr: -0.5 }}
                >
                  <MoreVertIcon fontSize="small" />
                </IconButton>
              </Stack>
              {/* Der veränderliche Teil der Kachel in einem eigenen, schrumpffähigen Kasten:
                  `flex: 1` füllt den Raum zwischen Kopf und Fortschrittsbalken, `minHeight: 0`
                  erlaubt das Unterschreiten der Inhaltshöhe (ohne das griffe `overflow` nicht),
                  und was dann noch nicht passt, wird abgeschnitten statt die Kachel zu dehnen.
                  Damit ist die gleiche Höhe aller Kacheln garantiert und nicht bloß wahrscheinlich
                  — die Begrenzungen an Titel, Anforderung und Marken sorgen dafür, dass der Schnitt
                  in der Praxis gar nicht erst nötig wird. */}
              <Box sx={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
              {/* Woraus das Vorhaben entstanden ist. Traegt es keine Anforderung, steht hier
                  nichts — kein Platzhalter und keine Ersatzanzeige aus den Wurzeln (Plan #637, E6).
                  `component="button"` rendert ein echtes <button>: per Tab erreichbar und per Enter
                  ausloesbar. Ein onClick auf einer Anzeigekomponente kaeme durch alle Gates —
                  jsx-a11y prueft nur DOM-Elemente in Kleinschreibung, keine MUI-Komponenten — und
                  waere per Tastatur trotzdem unerreichbar. */}
              {epic.requirementCardNumber !== null && (
                <Stack direction="row" spacing={0.5} alignItems="baseline" sx={{ mb: 1, minWidth: 0 }}>
                  <Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0 }}>
                    Anforderung:
                  </Typography>
                  {/* Eine Zeile mit Auslassungspunkten — das Muster aus `BoardListPage`. Der
                      Kartentitel hängt hier ungekürzt dran und war der zweite Grund, aus dem
                      Kacheln unterschiedlich hoch wurden. */}
                  <Link
                    component="button"
                    type="button"
                    variant="caption"
                    underline="hover"
                    textAlign="left"
                    sx={{
                      minWidth: 0,
                      display: 'block',
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                    }}
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

              {/* Höchstens zwei Zeilen Marken. Ein Vorhaben mit vielen verschiedenen Labels trieb
                  die Kachel sonst beliebig in die Höhe — `flexWrap` kennt keine Obergrenze. Die
                  Zeilenhöhe steht am Chip fest, damit die Rechnung nicht von der Schriftgröße des
                  Themes abhängt; der Zuschlag ist der Zeilenabstand (`spacing={0.5}` = 4px). */}
              {marken.length > 0 && (
                <Stack
                  direction="row"
                  spacing={0.5}
                  useFlexGap
                  flexWrap="wrap"
                  sx={{ mb: 1, maxHeight: `calc(2 * ${MARKE_ZEILENHOEHE} + 4px)`, overflow: 'hidden' }}
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
              </Box>
              {/* Der Balken steht am Fuß der Kachel, weil der Kasten darüber den freien Raum füllt
                  (`flex: 1`). Früher tat das ein `mt: 'auto'` am Balken selbst — das schob ihn zwar
                  ebenso nach unten, ließ den Inhalt darüber aber ungebremst wachsen. */}
              <LinearProgress
                variant="determinate"
                value={pct}
                aria-label={`Fortschritt ${epic.title}`}
                sx={{ height: 8, borderRadius: 1 }}
              />
            </Paper>
          )
        })}
      </Box>
      {epics.length === 0 && <Typography color="text.secondary">Noch keine Vorhaben.</Typography>}

      {/* Ein Menü statt eines Schalters an der Kachel (Plan #703, E2): Ein Schalter, der die
          Kachel verschwinden lässt, auf der er sitzt, ist nach dem Umlegen selbst weg — und damit
          unbedienbar. Keine Sicherheitsabfrage (E4): Beim Ausblenden geht nichts verloren, der
          Vorgang ist mit einem Klick umkehrbar. */}
      <Menu anchorEl={menu?.anchor ?? null} open={menu != null} onClose={() => setMenu(null)}>
        {menu && (
          <MenuItem
            onClick={() => {
              const gewaehlt = menu.epic
              setMenu(null)
              setzeAusgeblendet(gewaehlt.id, !hiddenEpics.has(gewaehlt.id))
            }}
          >
            {hiddenEpics.has(menu.epic.id) ? 'Einblenden' : 'Ausblenden'}
          </MenuItem>
        )}
      </Menu>

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
          // Ohne `projectId` baut der Dialog keinen Sprung-Handler: Die Zeilen des Herkunftsbaums
          // und die `#N`-Verweise blieben ohne Ziel (Issue #687).
          projectId={projectId}
          members={members}
          boardLabels={labels}
          epics={epics}
          onClose={() => setSelected(null)}
          onChanged={reload}
        />
      )}
    </Box>
  )
}
