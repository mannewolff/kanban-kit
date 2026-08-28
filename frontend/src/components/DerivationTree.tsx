import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import Typography from '@mui/material/Typography'
import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { cardsApi, type DerivationNode } from '../api/cards'
import { ApiError } from '../api/client'

interface Props {
  boardId: number
  /** Enter auf einer Zeile. Es gibt keine Karten-Route — die Seite öffnet das Detail-Modal. */
  onOpenCard: (number: number) => void
}

/**
 * Index des Elternteils in der Präorder-Liste, oder `-1` für eine Wurzel. In Präorder steht der
 * Elternteil vor dem Kind, und es ist die nächste Zeile darüber mit kleinerer Tiefe.
 */
function parentIndex(rows: readonly DerivationNode[], index: number): number {
  for (let j = index - 1; j >= 0; j--) {
    if (rows[j].depth < rows[index].depth) {
      return j
    }
  }
  return -1
}

/** In Präorder folgt das erste Kind unmittelbar — und trägt genau eine Ebene mehr. */
function hasChildren(rows: readonly DerivationNode[], index: number): boolean {
  return rows[index + 1]?.depth === rows[index].depth + 1
}

/** Sichtbar ist eine Zeile, solange kein Vorfahre zugeklappt ist. */
function isVisible(
  rows: readonly DerivationNode[],
  index: number,
  collapsed: ReadonlySet<number>,
): boolean {
  for (let p = parentIndex(rows, index); p >= 0; p = parentIndex(rows, p)) {
    if (collapsed.has(rows[p].number)) {
      return false
    }
  }
  return true
}

/**
 * Herkunftsbaum eines Boards (Issue #611) auf Basis der flachen Präorder-Liste aus Issue #609.
 *
 * <p>Reines Lesen: Die Zugehörigkeit berechnet der Server, diese Ansicht stellt sie nur dar. Wer
 * umhängen will, ändert die Herkunft an der Karte.
 *
 * <p>Die Reihenfolge des Servers wird unverändert übernommen — die topologische Ordnung ist dort
 * berechnet und getestet. Hier wird nur nach `depth` eingerückt.
 *
 * <p>Bedienung nach dem APG-Muster „Tree View": ein einzelner Tab-Stopp (Roving Tabindex), innen
 * mit den Pfeiltasten. Accessibility steht in CLAUDE.md über Optik und Wartbarkeit, deshalb hat
 * jede farbliche Unterscheidung eine textliche Entsprechung.
 */
export function DerivationTree({ boardId, onOpenCard }: Readonly<Props>) {
  const [rows, setRows] = useState<DerivationNode[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [collapsed, setCollapsed] = useState<ReadonlySet<number>>(() => new Set())
  const [focused, setFocused] = useState<number | undefined>(undefined)
  const zeilenRefs = useRef(new Map<number, HTMLElement>())

  useEffect(() => {
    let aktiv = true
    setRows(null)
    setError(null)
    // Der Fokus wird mit den Daten zurückgesetzt: Er zeigt auf eine Kartennummer, und nach einem
    // Boardwechsel gibt es die womöglich nicht mehr.
    setFocused(undefined)
    void cardsApi
      .derivationTree(boardId)
      .then(
        (daten) => ({ daten, fehler: null as string | null }),
        (grund: unknown) => ({
          daten: null,
          fehler:
            grund instanceof ApiError
              ? grund.message
              : 'Der Herkunftsbaum konnte nicht geladen werden.',
        }),
      )
      .then((ergebnis) => {
        if (!aktiv) {
          return
        }
        setRows(ergebnis.daten)
        setError(ergebnis.fehler)
      })
    return () => {
      aktiv = false
    }
  }, [boardId])

  if (error !== null) {
    return <Alert severity="error">{error}</Alert>
  }
  if (rows === null) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}>
        <CircularProgress size={24} />
      </Box>
    )
  }
  if (rows.length === 0) {
    // Der Normalfall bis zur Nachpflege über Issue #608 — kein Fehler, deshalb auch keine Meldung.
    return (
      <Typography color="text.secondary">
        Für dieses Board ist keine Herkunft hinterlegt. Sie entsteht, sobald Karten einen Vorfahren
        tragen.
      </Typography>
    )
  }

  const sichtbareIndizes = rows
    .map((_, i) => i)
    .filter((i) => isVisible(rows, i, collapsed))
  const effektivFokus = focused ?? rows[sichtbareIndizes[0]].number

  const nummerBei = (i: number | undefined) =>
    i === undefined || i < 0 ? undefined : rows[i].number

  /**
   * Setzt Roving-Tabindex und DOM-Fokus zugleich. `undefined` heißt „dort ist nichts" — am Rand des
   * Baums, an einer Wurzel ohne Elternteil, an einem Blatt ohne Kind. Dann geschieht nichts.
   */
  const fokussiere = (nummer: number | undefined) => {
    const el = nummer === undefined ? undefined : zeilenRefs.current.get(nummer)
    if (el === undefined) {
      return
    }
    setFocused(nummer)
    el.focus()
  }

  const umschalten = (nummer: number, zuklappen: boolean) =>
    setCollapsed((vorher) => {
      const naechste = new Set(vorher)
      if (zuklappen) {
        naechste.add(nummer)
      } else {
        naechste.delete(nummer)
      }
      return naechste
    })

  const beiTaste = (event: KeyboardEvent<HTMLDivElement>, index: number) => {
    const zeile = rows[index]
    const kinderDa = hasChildren(rows, index)
    const offen = !collapsed.has(zeile.number)
    const pos = sichtbareIndizes.indexOf(index)
    switch (event.key) {
      case 'ArrowDown':
        fokussiere(nummerBei(sichtbareIndizes[pos + 1]))
        break
      case 'ArrowUp':
        fokussiere(nummerBei(sichtbareIndizes[pos - 1]))
        break
      case 'Home':
        fokussiere(nummerBei(sichtbareIndizes[0]))
        break
      case 'End':
        fokussiere(nummerBei(sichtbareIndizes[sichtbareIndizes.length - 1]))
        break
      case 'ArrowRight':
        // APG: erst aufklappen, beim zweiten Druck zum ersten Kind — das in Präorder unmittelbar
        // folgt und deshalb die nächste sichtbare Zeile ist.
        if (kinderDa && !offen) {
          umschalten(zeile.number, false)
        } else {
          fokussiere(kinderDa ? nummerBei(sichtbareIndizes[pos + 1]) : undefined)
        }
        break
      case 'ArrowLeft':
        // APG: erst zuklappen, sonst zum Elternteil.
        if (kinderDa && offen) {
          umschalten(zeile.number, true)
        } else {
          fokussiere(nummerBei(parentIndex(rows, index)))
        }
        break
      case 'Enter':
        onOpenCard(zeile.number)
        break
      default:
        return
    }
    event.preventDefault()
  }

  return (
    <Box role="tree" aria-label="Herkunft">
      {sichtbareIndizes.map((index) => {
        const zeile = rows[index]
        const kinderDa = hasChildren(rows, index)
        return (
          <Box
            key={zeile.number}
            ref={(el: HTMLElement | null) => {
              if (el) {
                zeilenRefs.current.set(zeile.number, el)
              } else {
                zeilenRefs.current.delete(zeile.number)
              }
            }}
            role="treeitem"
            aria-level={zeile.depth + 1}
            aria-expanded={kinderDa ? !collapsed.has(zeile.number) : undefined}
            tabIndex={zeile.number === effektivFokus ? 0 : -1}
            onKeyDown={(event) => beiTaste(event, index)}
            sx={{
              display: 'flex',
              alignItems: 'baseline',
              gap: 1,
              py: 0.5,
              pr: 1,
              pl: zeile.depth * 2.5 + 1,
              borderRadius: 1,
              cursor: 'default',
              opacity: zeile.blocked ? 0.6 : 1,
              '&:focus-visible': { outline: '2px solid', outlineColor: 'primary.main' },
            }}
          >
            <Typography variant="body2" component="span" sx={{ color: 'text.secondary' }}>
              #{zeile.number}
            </Typography>
            <Typography variant="body2" component="span" sx={{ flexGrow: 1 }}>
              {zeile.title}
            </Typography>
            {zeile.type === 'EPIC' && <Marke text="Vorhaben" />}
            {zeile.done && <Marke text="erledigt" />}
            {zeile.blocked && <Marke text="blockiert" />}
            {zeile.broken && <Marke text="Herkunftskette unterbrochen" />}
            {zeile.externalOrigin && <Marke text={`Herkunft extern: #${zeile.derivedFrom}`} />}
            {zeile.dependencies.map((nummer) => (
              <Marke key={`i${nummer}`} text={`⇠ #${nummer}`} />
            ))}
            {zeile.externalDependencies.map((nummer) => (
              <Marke key={`e${nummer}`} text={`⇠ extern #${nummer}`} />
            ))}
          </Box>
        )
      })}
    </Box>
  )
}

/**
 * Nicht bedienbare Text-Marke. Ausdrücklich ohne Rolle und ohne `tabIndex`: Die Interaktion an der
 * Abhängigkeitsmarke liefert Issue #612 — hier wäre sie ein zweiter, konkurrierender Tab-Stopp im
 * Roving-Tabindex des Baums.
 */
function Marke({ text }: Readonly<{ text: string }>) {
  return (
    <Typography
      variant="caption"
      component="span"
      sx={{
        px: 0.75,
        borderRadius: 10,
        border: 1,
        borderColor: 'divider',
        color: 'text.secondary',
        whiteSpace: 'nowrap',
      }}
    >
      {text}
    </Typography>
  )
}
