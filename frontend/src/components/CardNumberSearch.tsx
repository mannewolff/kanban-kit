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
import { useKeyboardShortcut } from '../lib/useKeyboardShortcut'
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
 */
function locationLabel(hit: CardSearchHit): string {
  if (hit.boardName === null) {
    return `${hit.projectName} / Ideen`
  }
  const board = hit.boardArchived ? `${hit.boardName} (archiviert)` : hit.boardName
  return `${hit.projectName} / ${board} / ${hit.columnName}`
}

/**
 * Suchfeld der Kopfzeile: Kartennummer mit oder ohne `#` eingeben, Karte öffnen (#490). Die Nummer
 * ist projektweit eindeutig, nicht global — eine Eingabe kann deshalb mehrere Karten in
 * verschiedenen Projekten treffen. Dann wird ausgewählt statt geraten.
 *
 * Die Karte öffnet sich nur lesend: Aus der Suche heraus ist die Rolle im Zielprojekt nicht
 * bekannt, und eine Rolle zu unterstellen wäre schlechter als der Verzicht auf den Editiermodus.
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
  const [choices, setChoices] = useState<CardSearchHit[] | null>(null)
  const [selected, setSelected] = useState<CardSearchHit | null>(null)
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
        setSelected(hits[0])
        return
      }
      setChoices(hits)
    } catch {
      notify('Die Suche ist fehlgeschlagen.', 'error')
    } finally {
      setBusy(false)
    }
  }

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
          color="inherit"
          aria-label="Karte suchen"
          onClick={() => setExpanded(true)}
          sx={{ display: expanded ? 'none' : { xs: 'inline-flex', sm: 'none' } }}
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
                    <CircularProgress size={14} color="inherit" aria-label="Suche läuft" />
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
        {(choices ?? []).map((hit) => (
          <MenuItem
            key={hit.card.id}
            onClick={() => {
              setChoices(null)
              setSelected(hit)
            }}
          >
            <ListItemText primary={hit.card.title} secondary={locationLabel(hit)} />
          </MenuItem>
        ))}
      </Menu>

      {selected && (
        // Ohne sichtbaren Platzhalter: Der Nachschlag des Chunks dauert Millisekunden, und ein
        // zweiter Ladeindikator direkt nach dem des Suchfelds wäre mehr Flackern als Information.
        <Suspense fallback={null}>
          <CardDetailModal
            card={selected.card}
            canEdit={false}
            projectId={selected.projectId}
            columnName={selected.columnName ?? undefined}
            onClose={() => setSelected(null)}
          />
        </Suspense>
      )}
    </>
  )
}
