import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import IconButton from '@mui/material/IconButton'
import InputAdornment from '@mui/material/InputAdornment'
import ListItemText from '@mui/material/ListItemText'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import TextField from '@mui/material/TextField'
import Tooltip from '@mui/material/Tooltip'
import SearchIcon from '@mui/icons-material/Search'
import { alpha } from '@mui/material/styles'
import { Suspense, lazy, useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { cardsApi, type CardSearchHit } from '../api/cards'
import { epicsApi, type Epic } from '../api/epics'
import { labelsApi, type Label as BoardLabel } from '../api/labels'
import { membersApi, type Member } from '../api/members'
import { cardLocationLabel, type CardLocation } from '../lib/cardLocation'
import { useKeyboardShortcut } from '../lib/useKeyboardShortcut'
import { useProjectRole } from '../lib/useProjectRole'
import { useSnackbar } from './SnackbarProvider'

/**
 * Das Detail-Modal wiegt mit seinem Markdown-Renderer rund 200 kB und ist bisher ausschließlich
 * über lazy geladene Seiten erreichbar. Die Kopfzeile hängt dagegen in der App-Shell — ein
 * statischer Import zöge den ganzen Baum in den Start-Chunk (Performance-Budget, CLAUDE-react.md).
 * Deshalb hier derselbe Lazy-Schnitt wie bei den Routen in `App.tsx`; geladen wird erst beim
 * ersten Treffer.
 */
const CardDetailModal = lazy(() =>
  import('./CardDetailModal').then((m) => ({ default: m.CardDetailModal })),
)

/**
 * Kartennummer aus einer freien Eingabe. Toleriert das führende Lattenkreuz und Leerzeichen, sonst
 * nur Ziffern. Höchstens neun Stellen: die Nummer ist im Backend ein 32-Bit-`int`, eine längere
 * Eingabe wäre dort ein Parserfehler statt einer Antwort — das fangen wir vor der Anfrage ab.
 */
function parseCardNumber(raw: string): number | null {
  const digits = raw.replace(/\s+/g, '').replace(/^#/, '')
  return /^\d{1,9}$/.test(digits) ? Number.parseInt(digits, 10) : null
}

/**
 * Ortsangabe eines Treffers, die die Projekte unterscheidbar macht. Eine board-lose Pool-Idee hat
 * weder Board noch Spalte und wird als „Ideen" des Projekts benannt; board-gebundene Karten haben
 * laut Backend stets beides (seit V18 lässt die Datenbank nichts dazwischen zu). Ein archiviertes
 * Board wird als solches gekennzeichnet — die Karte bleibt auffindbar, der Ort soll nicht so
 * aussehen wie ein aktives Board.
 *
 * Dieselbe Angabe trägt der Pfad im geöffneten Detail-Modal, deshalb eine gemeinsame Form
 * (`CardLocation`) statt zweier Formulierungen derselben Sonderfälle.
 */
function hitLocation(hit: CardSearchHit): CardLocation {
  return {
    projectId: hit.projectId,
    projectName: hit.projectName,
    // Board-ID und -Name sind im Vertrag der Suche stets gemeinsam gesetzt oder gemeinsam `null`;
    // die Prüfung auf beide dient allein der Typverengung.
    board:
      hit.boardId === null || hit.boardName === null
        ? null
        : {
            id: hit.boardId,
            name: hit.boardName,
            archived: hit.boardArchived,
            columnName: hit.columnName,
          },
  }
}

/** Der geöffnete Treffer samt der Nummer, mit der er gefunden wurde. */
interface Opened {
  hit: CardSearchHit
  /**
   * Eigens gehalten, weil das Suchfeld beim Treffer geleert wird und `card.number` nullable ist
   * (Legacy-Pool-Ideen) — ohne sie ließe sich der Treffer nach einer Änderung nicht neu holen.
   */
  number: number
}

/**
 * Bearbeitungsdaten des geöffneten Treffers. An `cardId` gebunden, damit die Daten eines vorigen
 * Treffers nie für den aktuellen gelten. Jede Liste steht für sich: `null` heißt „liegt nicht vor" —
 * noch am Laden, fehlgeschlagen oder (ohne aktives Board) gar nicht abgefragt.
 */
interface EditContext {
  cardId: number
  members: Member[] | null
  epics: Epic[] | null
  boardLabels: BoardLabel[] | null
  /** Noch offene Abfragen; erst bei 0 steht der Bearbeitungskontext fest. */
  pending: number
  failed: boolean
}

/**
 * Suchfeld der Kopfzeile: Kartennummer mit oder ohne `#` eingeben, Karte öffnen (#490). Die Nummer
 * ist projektweit eindeutig, nicht global — eine Eingabe kann deshalb mehrere Karten in
 * verschiedenen Projekten treffen. Dann wird ausgewählt statt geraten.
 *
 * Die Karte öffnet sich bearbeitbar, sobald die Rolle im Zielprojekt feststeht (`useProjectRole`)
 * **und** die Bearbeitungsdaten geladen sind (#586). Bis dahin — und nach jedem Ladefehler — bleibt
 * sie lesend: Least Privilege, und ein leerer Optionsvorrat wäre gefährlicher als kein Editiermodus.
 * Epics und Board-Labels gibt es nur von einem aktiven Board (das Backend verweigert sie für ein
 * archiviertes, obwohl dessen Karten bearbeitbar bleiben, #462); die Mitglieder hängen dagegen am
 * Projekt und werden deshalb immer geladen — auch für eine board-lose Pool-Idee. Fehlt ein
 * Optionsvorrat, bleiben Epic-Auswahl und Label-Sektion lesend, während der Rest bearbeitbar ist.
 *
 * Ein leeres Ergebnis heißt „nicht gefunden", nicht „existiert nicht": Die Suche läuft nur über die
 * eigenen Projekte, und Karten im Papierkorb bleiben unsichtbar, obwohl ihre Nummer belegt ist.
 *
 * Ankündigung des Ergebnisses: Ein Treffer zieht den Fokus in den Dialog, mehrere Treffer in den
 * ersten Eintrag der Auswahl, und alles andere meldet der Toast (`role="alert"`).
 *
 * Das Tastenkürzel `/` fokussiert das Feld. Es greift bewusst nicht bei offenem Dialog — so ist der
 * Hook (`useKeyboardShortcut`) gebaut. Aus einem offenen Kartendetail heraus ist die Suche also nur
 * mit der Maus bzw. nach dem Schließen erreichbar; `Strg`/`Cmd`-Kürzel scheiden aus, weil der Hook
 * Modifikatoren dem Browser überlässt.
 */
export function CardNumberSearch() {
  const notify = useSnackbar()
  const [query, setQuery] = useState('')
  // Auf schmalen Breiten ist das Feld zu einem Icon eingeklappt (wie der Benutzername in der
  // Kopfzeile); ab `sm` ist es unabhängig davon immer sichtbar.
  const [expanded, setExpanded] = useState(false)
  const [busy, setBusy] = useState(false)
  const [choices, setChoices] = useState<Opened[] | null>(null)
  const [selected, setSelected] = useState<Opened | null>(null)
  const [edit, setEdit] = useState<EditContext | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  // Anker der Trefferauswahl als Zustand statt als Ref: Das Menü öffnet sich nicht aus einem
  // Klick heraus, sondern aus der Antwort — der Anker wird also beim Rendern gebraucht.
  const [anchor, setAnchor] = useState<HTMLElement | null>(null)

  // Ausklappen und Fokussieren getrennt: Beim Ausklappen ist das Feld im selben Durchlauf noch
  // ausgeblendet, `focus()` liefe dort ins Leere.
  useEffect(() => {
    if (expanded) {
      inputRef.current?.focus()
    }
  }, [expanded])

  useKeyboardShortcut('/', true, () => {
    setExpanded(true)
    inputRef.current?.focus()
  })

  const openedCardId = selected?.hit.card.id ?? null
  const openedProjectId = selected?.hit.projectId ?? null
  // Board-gebundene Vorräte nur von einem aktiven Board: Für ein archiviertes Board scheitern
  // Epics und Labels im Backend an `BoardService.requireProjectId`, während die Karte selbst
  // bearbeitbar bleibt (#462). Eine board-lose Pool-Idee hat ohnehin kein Board.
  const openedBoardId = selected && !selected.hit.boardArchived ? selected.hit.boardId : null

  const role = useProjectRole(openedProjectId)

  // Bearbeitungsdaten erst beim Öffnen laden, nie beim Tippen — und je Abfrage unabhängig, damit
  // ein einzelner Fehlschlag die übrigen Daten nicht verwirft. Die Abhängigkeiten sind bewusst die
  // Kennungen und nicht der Treffer selbst: Ein Neuladen nach dem Speichern liefert dieselbe Karte
  // in einem neuen Objekt und dürfte den Bearbeitungskontext nicht wegwerfen.
  useEffect(() => {
    if (openedCardId === null || openedProjectId === null) {
      setEdit(null)
      return
    }
    const cardId = openedCardId
    const boardId = openedBoardId
    let active = true
    let reported = false
    setEdit({
      cardId,
      members: null,
      epics: null,
      boardLabels: null,
      pending: boardId === null ? 1 : 3,
      failed: false,
    })

    const settle = (patch: Partial<EditContext>) =>
      setEdit((current) => current && { ...current, ...patch, pending: current.pending - 1 })
    // Ein Hinweis je Treffer, nicht je fehlgeschlagener Abfrage.
    const fail = () => {
      if (!reported) {
        reported = true
        notify(
          'Die Bearbeitungsdaten konnten nicht vollständig geladen werden — die Karte bleibt lesend.',
          'error',
        )
      }
      settle({ failed: true })
    }

    void membersApi
      .list(openedProjectId)
      .then((members) => active && settle({ members }))
      .catch(() => active && fail())
    if (boardId !== null) {
      void epicsApi
        .list(boardId)
        .then((epics) => active && settle({ epics }))
        .catch(() => active && fail())
      void labelsApi
        .list(boardId)
        .then((boardLabels) => active && settle({ boardLabels }))
        .catch(() => active && fail())
    }
    // Verspätete Antworten eines vorigen Treffers bleiben unbeachtet.
    return () => {
      active = false
    }
  }, [openedCardId, openedProjectId, openedBoardId, notify])

  const run = async (number: number) => {
    setBusy(true)
    try {
      const hits = await cardsApi.searchByNumber(number)
      if (hits.length === 0) {
        notify(`Keine Karte mit der Nummer ${number} gefunden.`, 'warning')
        return
      }
      setQuery('')
      if (hits.length === 1) {
        setSelected({ hit: hits[0], number })
        return
      }
      setChoices(hits.map((hit) => ({ hit, number })))
    } catch {
      notify('Die Suche ist fehlgeschlagen.', 'error')
    } finally {
      setBusy(false)
    }
  }

  /**
   * Nach einer Änderung im Dialog den Treffer neu holen und über die unveränderliche `card.id`
   * auflösen — nicht über die Nummer: Dieselbe Nummer liegt in mehreren Projekten, und die
   * Auflösung träfe sonst die falsche Karte. Scheitert das Nachladen selbst, bleibt der zuletzt
   * bekannte Stand offen; die gerade gespeicherte Änderung soll nicht mit dem Dialog verschwinden.
   */
  const refresh = async (opened: Opened) => {
    try {
      const hits = await cardsApi.searchByNumber(opened.number)
      const again = hits.find((h) => h.card.id === opened.hit.card.id)
      if (again === undefined) {
        notify('Die Karte ist nicht mehr auffindbar.', 'warning')
        setSelected(null)
        return
      }
      setSelected({ hit: again, number: opened.number })
    } catch {
      notify('Der aktuelle Stand konnte nicht geladen werden.', 'error')
    }
  }

  // Nur der Kontext der aktuell geöffneten Karte zählt: Beim Trefferwechsel ist er im selben
  // Render leer, ohne auf den Ladeeffekt zu warten.
  const context = edit !== null && edit.cardId === openedCardId ? edit : null
  const settled = context !== null && context.pending === 0 && !context.failed

  const submit = (event: FormEvent) => {
    event.preventDefault()
    // Kein zweiter Lauf während einer offenen Anfrage: Er brächte nur einen springenden
    // Ladezustand und eine Doppelanfrage.
    if (busy || query.trim() === '') {
      return
    }
    const number = parseCardNumber(query)
    if (number === null) {
      notify('Bitte eine Kartennummer eingeben, z. B. 345 oder #345.', 'warning')
      return
    }
    void run(number)
  }

  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      setQuery('')
      setExpanded(false)
      // Ohne Blur bliebe der Fokus auf schmalen Breiten in einem ausgeblendeten Feld hängen.
      inputRef.current?.blur()
    }
  }

  return (
    <>
      <Tooltip title="Karte suchen">
        <IconButton
          aria-label="Karte suchen"
          onClick={() => setExpanded(true)}
          sx={{ color: 'text.primary', display: expanded ? 'none' : { xs: 'inline-flex', sm: 'none' } }}
        >
          <SearchIcon />
        </IconButton>
      </Tooltip>
      <Box
        component="form"
        role="search"
        ref={setAnchor}
        onSubmit={submit}
        sx={{ display: expanded ? 'flex' : { xs: 'none', sm: 'flex' }, alignItems: 'center' }}
      >
        <TextField
          size="small"
          inputRef={inputRef}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder="#345"
          slotProps={{
            // Kein `inputMode="numeric"`: Es widerspräche der zugesagten Toleranz gegenüber `#345`,
            // weil die Ziffern-Tastatur mobiler Geräte das Lattenkreuz nicht anbietet.
            htmlInput: { 'aria-label': 'Kartennummer suchen', maxLength: 12 },
            input: {
              endAdornment: (
                <InputAdornment position="end">
                  {busy ? (
                    <CircularProgress size={14} sx={{ color: 'text.primary' }} aria-label="Suche läuft" />
                  ) : (
                    <SearchIcon fontSize="small" aria-hidden />
                  )}
                </InputAdornment>
              ),
            },
          }}
          // Die Kopfleiste steht auf `primary`; ein unveränderter Rand/Text wäre darauf kaum
          // lesbar. Alle Farben aus den Marken-Tokens des Themes abgeleitet, keine Sonderfarben.
          sx={{
            width: { xs: 130, sm: 150, md: 190 },
            '& .MuiOutlinedInput-root': {
              bgcolor: 'primary.dark',
              color: 'primary.contrastText',
            },
            '& .MuiOutlinedInput-notchedOutline': {
              borderColor: (t) => alpha(t.palette.primary.contrastText, 0.5),
            },
            '& .MuiOutlinedInput-root:hover .MuiOutlinedInput-notchedOutline': {
              borderColor: 'primary.contrastText',
            },
            '& .MuiOutlinedInput-root.Mui-focused .MuiOutlinedInput-notchedOutline': {
              borderColor: 'primary.contrastText',
            },
            '& .MuiInputBase-input::placeholder': { color: 'primary.contrastText', opacity: 0.85 },
          }}
        />
      </Box>

      <Menu
        anchorEl={anchor}
        open={choices !== null}
        onClose={() => setChoices(null)}
        slotProps={{ list: { 'aria-label': 'Treffer auswählen' } }}
      >
        {choices?.map((choice) => (
          <MenuItem
            key={choice.hit.card.id}
            onClick={() => {
              setSelected(choice)
              setChoices(null)
            }}
          >
            <ListItemText
              primary={choice.hit.card.title}
              secondary={cardLocationLabel(hitLocation(choice.hit))}
            />
          </MenuItem>
        ))}
      </Menu>

      {selected && (
        // Ohne sichtbaren Platzhalter: Der Nachschlag des Chunks dauert Millisekunden, und ein
        // zweiter Ladeindikator direkt nach dem des Suchfelds wäre mehr Flackern als Information.
        <Suspense fallback={null}>
          <CardDetailModal
            card={selected.hit.card}
            canEdit={settled && role.canEdit}
            canModerateComments={settled && role.canModerate}
            // Ohne geladenen Optionsvorrat bleiben diese beiden Felder lesend, der Rest nicht.
            canEditEpic={context?.epics != null}
            canEditLabels={context?.boardLabels != null}
            members={context?.members ?? []}
            epics={context?.epics ?? []}
            boardLabels={context?.boardLabels ?? []}
            projectId={selected.hit.projectId}
            columnName={selected.hit.columnName ?? undefined}
            // Aus der Suche heraus fehlt jeder Ortsbezug — hier ist der Pfad der einzige Hinweis,
            // wo die Karte liegt (#491).
            location={hitLocation(selected.hit)}
            onChanged={() => void refresh(selected)}
            onClose={() => setSelected(null)}
          />
        </Suspense>
      )}
    </>
  )
}
