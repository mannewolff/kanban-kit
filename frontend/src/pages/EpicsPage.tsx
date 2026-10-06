import AddIcon from '@mui/icons-material/Add'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import FormControlLabel from '@mui/material/FormControlLabel'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import Switch from '@mui/material/Switch'
import Typography from '@mui/material/Typography'
import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { boardsApi, type Board } from '../api/boards'
import { apiErrorMessage } from '../api/client'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { useSnackbar } from '../components/SnackbarProvider'
import { cardsApi, type Card } from '../api/cards'
import { epicsApi, type Epic } from '../api/epics'
import { labelsApi, type Label } from '../api/labels'
import { membersApi, type Member } from '../api/members'
import { CardDetailModal } from '../components/CardDetailModal'
import { EpicVisibilityList } from '../components/EpicVisibilityList'
import { NewCardModal } from '../components/NewCardModal'
import { leseAusgeblendet, schreibeAusgeblendet } from '../lib/boardHiddenEpics'
import { epicToCard } from '../lib/epicToCard'
import { selectableEpics, sortEpics, visibleEpics } from '../lib/epicTiles'
import { useBoardRole } from '../lib/useBoardRole'
import { useProjectName } from '../lib/useProjectName'
import { useRefetchOnFocus } from '../lib/useRefetchOnFocus'

/**
 * Der Text der Löschen-Rückfrage. „aktive" ist bewusst gewählt: `rootNumbers` lässt archivierte
 * Karten aus (`EpicMembership`), während der Server beim Löschen auch deren Zuordnung löst. Die Zahl kann also kleiner sein als die Zahl der tatsächlich
 * gelösten Zuordnungen — der Satz behauptet deshalb nur etwas über die aktiven Karten.
 *
 * Der Hinweis auf die Unumkehrbarkeit steht da, weil ein gelöschtes Vorhaben in keinem
 * Papierkorb-Dialog auftaucht: Der Server filtert die Papierkorb-Liste auf gewöhnliche Karten.
 */
function rueckfrageText(epic: Epic): string {
  const anzahl = epic.rootNumbers.length
  const ende = 'Das lässt sich nicht rückgängig machen.'
  if (anzahl === 0) {
    return `„${epic.title}" wird gelöscht. Keine aktive Karte ist direkt zugeordnet. ${ende}`
  }
  if (anzahl === 1) {
    return `„${epic.title}" wird gelöscht. 1 direkt zugeordnete aktive Karte bleibt erhalten und zeigt danach „(kein Vorhaben)". ${ende}`
  }
  return `„${epic.title}" wird gelöscht. ${anzahl} direkt zugeordnete aktive Karten bleiben erhalten und zeigen danach „(kein Vorhaben)". ${ende}`
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
  // Bis der erste Ladeversuch (alle vier Requests) abgeschlossen ist — erfolgreich oder
  // fehlgeschlagen —, zeigt die Seite weder die (anfangs leere) Liste noch "Noch keine
  // Vorhaben.": Beides waere von einem echten leeren Board nicht zu unterscheiden (Issue #783).
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [selected, setSelected] = useState<Card | null>(null)
  const [creating, setCreating] = useState(false)
  // Ausgeblendete Vorhaben (Plan #620, Wirkung auf der Seite aus Plan #703, E1). Derselbe
  // Zustand, den `BoardView` liest — Schlüssel und Wertformat kommen deshalb aus
  // `lib/boardHiddenEpics`. Reine Darstellung: kein Archivieren, keine Position, nichts an der
  // Karte, deshalb liegt der Wert nur lokal.
  const [hiddenEpics, setHiddenEpics] = useState<ReadonlySet<number>>(() => leseAusgeblendet(id))
  // Der Zeige-Modus gehört zur Sitzung, nicht zum Board: Er wird nicht gespeichert und startet
  // auf jedem Board aus.
  const [zeigeAusgeblendete, setZeigeAusgeblendete] = useState(false)
  // Eben ausgeblendete Vorhaben (Plan #1488, E5): Wer bei ausgeschaltetem „Ausgeblendete zeigen"
  // ausblendet, sieht die Zeile bis zum nächsten Laden ausgegraut stehen und kann sich am selben
  // Schalter umentscheiden. Nicht gespeichert — ein neues Laden der Seite beginnt leer.
  const [frischAusgeblendet, setFrischAusgeblendet] = useState<ReadonlySet<number>>(new Set())
  const [menu, setMenu] = useState<{ epic: Epic; anchor: HTMLElement } | null>(null)
  // Das Vorhaben, für das die Löschen-Rückfrage offensteht; `null` = keine Rückfrage.
  const [deleteConfirm, setDeleteConfirm] = useState<Epic | null>(null)
  const notify = useSnackbar()

  // Die Route `/boards/:boardId/vorhaben` hält die Komponente bei einem reinen Parameterwechsel
  // gemountet — der `useState`-Initializer läuft dann nicht erneut (Plan #703, E11). Ohne dieses
  // Nachlesen filterte das neue Board mit dem Stand des vorigen. Der Umschalter geht dabei aus:
  // Sonst startete das neue Board in einem Zeige-Modus, in den dort niemand geschaltet hat.
  useEffect(() => {
    setHiddenEpics(leseAusgeblendet(id))
    setZeigeAusgeblendete(false)
    setFrischAusgeblendet(new Set())
  }, [id])

  // Fortgeschrieben wird über `schreibeAusgeblendet` — dieselbe Funktion, die `BoardPage` nutzt
  // (Plan #846, E8). Sie löscht den Schlüssel im Leerfall, statt ein aussageloses `[]` zu
  // hinterlassen; mit der Liste ist das Leeren der Menge vom Sonderfall zum Regelfall geworden.
  // Ohne funktionierendes localStorage wirkt das Umlegen trotzdem — nur das Merken über den
  // Seitenwechsel hinaus fällt aus.
  const setzeAusgeblendet = (epicId: number, ausblenden: boolean) => {
    const next = new Set(hiddenEpics)
    if (ausblenden) {
      next.add(epicId)
    } else {
      next.delete(epicId)
    }
    setHiddenEpics(next)
    schreibeAusgeblendet(id, next)
    // Nur im Normalmodus „eben ausgeblendet": Im Zeige-Modus steht die Zeile ohnehin da und soll
    // nach dem Ausschalten fehlen wie jede andere ausgeblendete (E5).
    if (ausblenden && !zeigeAusgeblendete) {
      setFrischAusgeblendet(new Set(frischAusgeblendet).add(epicId))
    }
  }

  // Was die Liste ohne Zeige-Modus auslässt: die Ausgeblendeten ohne die eben ausgeblendeten (E8).
  const ohneFrischAusgeblendete: ReadonlySet<number> = new Set(
    [...hiddenEpics].filter((epicId) => !frischAusgeblendet.has(epicId)),
  )

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

  // Jeder der vier Ladeaufrufe traegt sein eigenes `.catch` (statt eines gemeinsamen ueber
  // `Promise.all`): Ein Fehlschlag eines einzelnen Aufrufs darf die anderen drei nicht verwerfen,
  // und `loadError` markiert den Erstladeversuch als gescheitert, sobald einer von ihnen scheitert
  // (Issue #783 — zuvor unhandled promise rejection, `epics` blieb dauerhaft bei `[]`).
  const load = useCallback(() => {
    if (!validId) {
      return
    }
    setLoadError(false)
    const boardDone = boardsApi.get(id).then(setBoard).catch(() => setLoadError(true))
    const epicsDone = epicsApi.list(id).then(setEpics).catch(() => setLoadError(true))
    const cardsDone = cardsApi.list(id).then(setCards).catch(() => setLoadError(true))
    // `cardsApi.list` liefert nur `labels: number[]` (IDs) — ohne die Definitionen gibt es weder
    // Namen noch `countOnEpicTile`.
    const labelsDone = labelsApi.list(id).then(setLabels).catch(() => setLoadError(true))
    void Promise.all([boardDone, epicsDone, cardsDone, labelsDone]).then(() => setLoading(false))
  }, [id, validId])

  useEffect(() => {
    setLoading(true)
    load()
  }, [load])

  // Heilt einen fehlgeschlagenen oder leeren Erstladeversuch beim naechsten Fokuswechsel
  // selbststaendig, ohne dass ein manueller Reload noetig ist (analog `BoardPage.tsx`).
  // Live-Updates per SSE (`useBoardEvents`) bleiben bewusst aussen vor: Das gemeldete Symptom war
  // ein gescheiterter Erstladeversuch, kein veralteter Stand durch fremde Aenderungen — SSE waere
  // ein eigenstaendiges Feature ueber den Rahmen dieses Issues hinaus.
  useRefetchOnFocus(load)

  /**
   * Löscht ein Vorhaben, nachdem die Rückfrage bestätigt wurde.
   *
   * Die Rückfrage schließt **vor** dem Aufruf: Bliebe sie offen, schickte ein zweiter Klick ein
   * zweites DELETE, das nach dem erfolgreichen ersten mit 404 scheiterte und einen Fehler meldete,
   * den es nicht gab (Muster aus `BoardView.confirmDelete`).
   *
   * Bewusst nicht optimistisch: Die Zeile verschwindet erst mit dem Nachladen, nicht schon beim
   * Klick. Anders als beim Karten-Löschen im Board ist das ein seltener, durch die Rückfrage
   * abgesicherter Vorgang — ein Rollback-Pfad lohnt den Zusatzcode nicht. `load()` statt
   * `reload()`, weil nur `load()` je Teilaufruf ein `.catch` trägt (Issue #783).
   */
  /** Schließt die Rückfrage, ohne etwas zu tun — für „Abbrechen", Escape und Backdrop-Klick. */
  const schliesseRueckfrage = () => setDeleteConfirm(null)

  const confirmDeleteEpic = async (epic: Epic) => {
    setDeleteConfirm(null)
    try {
      await epicsApi.remove(epic.id)
      load()
    } catch (e) {
      notify(apiErrorMessage(e, 'Löschen fehlgeschlagen.'), 'error')
    }
  }

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
  // eine Zahl, hinter der in der Liste weniger ausgeschaltete Zeilen stehen, wäre ein sichtbarer
  // Widerspruch.
  const ausgeblendeteAnzahl = epics.filter((epic) => hiddenEpics.has(epic.id)).length

  if (!validId) {
    return <Alert severity="error">Ungültige Board-ID.</Alert>
  }

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
        <CircularProgress />
      </Box>
    )
  }

  if (loadError) {
    return <Alert severity="error">Vorhaben konnten nicht geladen werden.</Alert>
  }

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Breadcrumbs
          items={[
            { label: 'Projekte', to: '/projects' },
            // `board` ist ab hier immer gesetzt: Dieser Zweig wird nur erreicht, wenn `load()`
            // erfolgreich war (weder `loading` noch `loadError`, siehe die Returns oben) — die
            // Zusicherung dient nur der Typverengung, die TypeScript ueber die fruehen Returns
            // hinweg nicht selbst zieht (Issue #783 machte `board` hier erstmals verlaesslich).
            ...(projectName ? [{ label: projectName, to: `/projects/${board!.projectId}` }] : []),
            { label: board!.name, to: `/boards/${id}` },
            { label: 'Vorhaben' },
          ]}
        />
        {canEdit && (
          <Button variant="contained" size="small" startIcon={<AddIcon />} onClick={() => setCreating(true)}>
            Neues Vorhaben
          </Button>
        )}
      </Stack>

      {/* Immer sichtbar, sobald das Board ein Vorhaben hat (Plan #846, E9; Plan #1488, E10): Der
          Umschalter holt die Ausgeblendeten in die Liste. Ausgeblendet wird am Schalter jeder
          Zeile; die Zahl am Umschalter sagt auch bei „0", dass nichts verborgen ist. Nur ein Board
          ganz ohne Vorhaben hätte nichts zu schalten. Beschriftung und Zahl bleiben wörtlich
          (PO-Entscheidung, 2026-09-14). */}
      {epics.length > 0 && (
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

      {/* Die Liste ist die einzige Ansicht (Plan #1488): Bei „Ausgeblendete zeigen" an bekommt sie
          `sortEpics` ungefiltert — jedes Vorhaben an der Stelle, die es ohne Ausfiltern hätte (E8).
          Sonst fehlen die Ausgeblendeten, ausser denen, die eben in dieser Sitzung ausgeblendet
          wurden: Sie bleiben ausgegraut stehen, bis die Seite neu lädt (E5). `onToggle` geht ohne
          Umkehrung an `setzeAusgeblendet`: beide Seiten lesen `true` als „ausblenden" (Plan #846, E4). */}
      {epics.length > 0 && (
        <EpicVisibilityList
          epics={
            zeigeAusgeblendete
              ? sortEpics(epics, cards, labels)
              : visibleEpics(sortEpics(epics, cards, labels), ohneFrischAusgeblendete)
          }
          hidden={hiddenEpics}
          cards={cards}
          labels={labels}
          titelZuNummer={titelZuNummer}
          onToggle={setzeAusgeblendet}
          onOpen={(epic) => setSelected(epicToCard(epic, id))}
          onOpenCard={oeffneKarte}
          // Nur wer bearbeiten darf, bekommt das Menü an der Zeile — es trägt nur noch
          // „Löschen" (Plan #1488, E7).
          onMenu={canEdit ? (epic, anchor) => setMenu({ epic, anchor }) : undefined}
        />
      )}
      {epics.length === 0 && <Typography color="text.secondary">Noch keine Vorhaben.</Typography>}

      <Menu anchorEl={menu?.anchor ?? null} open={menu != null} onClose={() => setMenu(null)}>
        {/* Anders als das Ausblenden verändert Löschen den Server — deshalb hängt das Menü am
            Rechte-Check (`onMenu` nur bei `canEdit`). Der Server prüft ohnehin; das Gate erspart
            einem Nur-Leser nur die 403-Antwort auf eine Möglichkeit, die ihm gar nicht offensteht. */}
        {menu && (
          <MenuItem
            onClick={() => {
              const gewaehlt = menu.epic
              setMenu(null)
              setDeleteConfirm(gewaehlt)
            }}
          >
            Löschen
          </MenuItem>
        )}
      </Menu>

      {/* Bedingt gerendert statt über `open`: Ohne offene Rückfrage gibt es kein Vorhaben, über
          das der Text etwas aussagen könnte — so bleibt `deleteConfirm` im Inneren nicht-null,
          ohne dass ein unerreichbarer Null-Zweig entsteht. */}
      {deleteConfirm !== null && (
        // Escape, Backdrop-Klick und „Abbrechen" teilen sich einen Handler: Das Schließen ohne
        // Wirkung ist ein Vorgang, nicht drei — und eine zweite Fassung davon könnte abweichen.
        <Dialog open onClose={schliesseRueckfrage}>
          <DialogTitle>Vorhaben löschen?</DialogTitle>
          <DialogContent>
            <DialogContentText>{rueckfrageText(deleteConfirm)}</DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button onClick={schliesseRueckfrage}>Abbrechen</Button>
            <Button color="error" onClick={() => void confirmDeleteEpic(deleteConfirm)}>
              Löschen
            </Button>
          </DialogActions>
        </Dialog>
      )}

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
          // Volle Liste für Titel und Fortschritt, gefilterter Vorrat für die Auswahl (Plan #717,
          // A2) — derselbe Vertrag wie am Board. Der Umschalter „Ausgeblendete zeigen" dieser Seite
          // zieht ausdrücklich nicht mit (A1): Er steuert die Liste, nicht die Zuordnung.
          epics={epics}
          selectableEpics={selectableEpics(epics, hiddenEpics)}
          onClose={() => setSelected(null)}
          onChanged={reload}
        />
      )}
    </Box>
  )
}
