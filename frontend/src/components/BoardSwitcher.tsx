import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Dialog from '@mui/material/Dialog'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Typography from '@mui/material/Typography'
import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { boardsApi } from '../api/boards'
import { ApiError } from '../api/client'
import type { BoardHistoryEntry } from '../lib/useBoardHistory'

/** Das Ziel gibt es nicht mehr oder es ist nicht mehr freigegeben — der Eintrag verschwindet. */
const UNAVAILABLE = 'Board ist nicht mehr verfügbar.'
/** Vorübergehender Fehlschlag: Der Verlauf bleibt, nur dieser Versuch ist gescheitert. */
const OPEN_FAILED = 'Das Board konnte nicht geöffnet werden.'

const TITLE_ID = 'board-switcher-title'

interface Props {
  open: boolean
  /** Zuletzt besuchte Boards, absteigend nach letzter Benutzung. */
  entries: readonly BoardHistoryEntry[]
  /** Board der aktuellen Route, sonst `null` — bestimmt die Vorauswahl. */
  currentBoardId: number | null
  onClose: () => void
  /** Ziel ist nachweislich verschwunden (403/404): aus dem Verlauf nehmen. */
  onRemoveEntry: (boardId: number) => void
  onNotify: (message: string, severity: 'info' | 'error') => void
}

/**
 * Overlay zum Wechseln des Boards nach dem Vorbild der Anwendungsumschaltung: Das aktuelle Board
 * steht vorne, vorausgewählt ist der zuletzt *andere* Eintrag, Enter springt (#584).
 *
 * Drei Entscheidungen prägen die Umsetzung:
 *
 * 1. **Die Tastaturführung ist lokal, nicht global.** Das Overlay rendert als Dialog, und
 *    `useKeyboardShortcut` blockiert bei jedem offenen Dialog — ein zweites `b` erreichte den
 *    globalen Hook also nie. Die Tasten hängen deshalb am Eintrag, der den Fokus hält.
 * 2. **Geprüft wird vor der Navigation, nicht danach.** Auf den Fehler der Zielroute zu reagieren
 *    trägt nicht: `BoardPage` navigiert bei 404 weg, `BoardListPage` und `EpicsPage` haben gar
 *    keine Fehlerbehandlung. Erst wenn `boardsApi.get` das Ziel bestätigt, wird gesprungen.
 * 3. **Nur 403/404 räumen den Verlauf.** Die Statusunterscheidung ist nicht kosmetisch: Ohne sie
 *    löschte ein WLAN-Aussetzer gültige Einträge. Alles andere meldet nur diesen Versuch.
 *
 * Die Komponente hält keine Daten: Verlauf, aktuelle Board-ID und das Entfernen toter Einträge
 * kommen von außen (#587); lokal sind allein Auswahl, laufende Prüfung und Hinweis.
 */
export function BoardSwitcher({
  open,
  entries,
  currentBoardId,
  onClose,
  onRemoveEntry,
  onNotify,
}: Readonly<Props>) {
  const navigate = useNavigate()

  // Bei leerem Verlauf und beim einzigen Eintrag, der das aktuelle Board ist, gibt es nichts zu
  // wechseln — dann bleibt das Overlay auch bei `open` unsichtbar.
  const visible = open && entries.some((entry) => entry.id !== currentBoardId)
  // Steht das aktuelle Board vorne, ist der zweite Eintrag gemeint; sonst der erste.
  const preselect = entries[0]?.id === currentBoardId ? 1 : 0

  const [shown, setShown] = useState(visible)
  const [selected, setSelected] = useState(preselect)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // Laufende Zielprüfung. Jeder Start und jedes Schließen erhöht den Zähler; eine Antwort mit
  // veralteter Kennung wird verworfen — sonst spränge ein geschlossenes Overlay noch nachträglich.
  const run = useRef(0)
  // Der ausgewählte Eintrag als Zustand statt als Ref: Der Dialog hängt seinen Inhalt erst in
  // einem späteren Durchlauf ein (Portal), ein Effekt auf den Auswahlindex liefe dort ins Leere.
  // Über den Knoten selbst zieht der Fokus genau dann nach, wenn der Eintrag im DOM steht.
  const [selectedNode, setSelectedNode] = useState<HTMLDivElement | null>(null)

  if (shown !== visible) {
    // Sichtbarkeitswechsel während des Renderns auflösen (Muster wie `useBoardHistory`): Jede
    // Sitzung beginnt mit frischer Vorauswahl, ohne Hinweis und ohne geerbte Prüfung.
    setShown(visible)
    setSelected(preselect)
    setPending(false)
    setError(null)
  }

  // Zweite Hälfte desselben Wechsels, aber im Effekt: Eine laufende Prüfung der vorigen Sitzung
  // verfällt, damit ihre Antwort weder springt noch den Verlauf anfasst. (Die Kennung ist eine
  // Ref — in der Renderphase wäre sie nicht anzufassen.)
  useEffect(() => {
    run.current += 1
  }, [visible])

  // Der Verlauf kann schrumpfen, während das Overlay offen ist (ein totes Ziel fliegt raus); der
  // gemerkte Index zeigte sonst ins Leere.
  const clamp = (value: number) => Math.min(value, entries.length - 1)
  const index = clamp(selected)

  // Auswahl und Fokus wandern gemeinsam: Beim Öffnen liegt der Fokus auf der Vorauswahl, jede
  // Pfeiltaste zieht ihn mit.
  useEffect(() => {
    selectedNode?.focus()
  }, [selectedNode])

  const close = () => {
    run.current += 1
    setPending(false)
    onClose()
  }

  const choose = (entry: BoardHistoryEntry) => {
    // Kein zweiter Sprung während einer offenen Prüfung: Er brächte zwei Navigationen aus einem
    // Overlay heraus, das längst geschlossen sein sollte.
    if (pending) {
      return
    }
    setPending(true)
    setError(null)
    const started = ++run.current
    void boardsApi
      .get(entry.id)
      .then(() => {
        if (run.current === started) {
          navigate(`/boards/${entry.id}`)
          close()
        }
      })
      .catch((failure: unknown) => {
        if (run.current !== started) {
          return
        }
        // Nach jedem Fehlschlag ist die Auswahl wieder frei — ein zweiter Versuch muss möglich sein.
        setPending(false)
        if (failure instanceof ApiError && (failure.status === 403 || failure.status === 404)) {
          setError(UNAVAILABLE)
          onRemoveEntry(entry.id)
          return
        }
        onNotify(OPEN_FAILED, 'error')
      })
  }

  // Zyklisch: Der letzte Eintrag ist mit dem ersten verbunden.
  const step = (delta: number) =>
    setSelected((current) => (clamp(current) + delta + entries.length) % entries.length)

  const onKeyDown = (event: KeyboardEvent) => {
    // Strg/Alt/Meta gehören dem Browser bzw. dem Betriebssystem — wie im globalen Kürzel-Hook.
    if (event.ctrlKey || event.altKey || event.metaKey) {
      return
    }
    if (event.key === 'b' || event.key === 'ArrowDown') {
      event.preventDefault()
      step(1)
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      step(-1)
    } else if (event.key === 'Enter') {
      event.preventDefault()
      choose(entries[index])
    }
  }

  if (!visible) {
    return null
  }

  return (
    <Dialog
      open
      onClose={close}
      aria-labelledby={TITLE_ID}
      maxWidth="xs"
      fullWidth
      // Der Fokus gehört auf die Vorauswahl, nicht auf den Dialograhmen: Nur so wandert er mit den
      // Pfeiltasten weiter, und nur so erreichen die Tasten den Eintrag. Der Fokus-Käfig des
      // Dialogs bleibt aktiv, allein das automatische Fokussieren beim Öffnen entfällt.
      disableAutoFocus
    >
      <DialogTitle id={TITLE_ID}>Board wechseln</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" role="alert" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <Box role="listbox" aria-label="Zuletzt besuchte Boards" aria-busy={pending}>
          {entries.map((entry, position) => (
            <Box
              key={entry.id}
              role="option"
              aria-selected={position === index}
              // Rollender Tabindex: Nur der ausgewählte Eintrag liegt in der Tabreihenfolge, die
              // Auswahl selbst wandert mit den Pfeiltasten.
              tabIndex={position === index ? 0 : -1}
              ref={position === index ? setSelectedNode : null}
              onKeyDown={onKeyDown}
              onClick={() => choose(entry)}
              sx={{
                px: 2,
                py: 1,
                borderRadius: 1,
                cursor: 'pointer',
                bgcolor: position === index ? 'action.selected' : 'transparent',
                '&:hover': { bgcolor: 'action.hover' },
              }}
            >
              <Typography variant="body1">{entry.name}</Typography>
              {/* Der Projektname unterscheidet gleichnamige Boards zweier Projekte. */}
              <Typography variant="body2" color="text.secondary">
                {entry.projectName}
              </Typography>
            </Box>
          ))}
        </Box>
      </DialogContent>
    </Dialog>
  )
}
