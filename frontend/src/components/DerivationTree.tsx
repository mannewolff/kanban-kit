import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { useEffect, useRef, useState, type KeyboardEvent, type MouseEvent } from 'react'
import { type DerivationNode } from '../api/cards'
import { labelChipSx } from './labelChipSx'

interface Props {
  /**
   * Die Zeilen in Präorder, bereits geladen. Seit Issue #644 lädt die Komponente nicht mehr
   * selbst: Sie hängt im Vorhaben-Dialog, der die Karte ohnehin kennt und die Lade-, Fehler- und
   * Leerzustände für alle seine Bereiche gemeinsam führt. Reine Darstellung heisst auch: ohne
   * Netzwerk testbar.
   */
  rows: readonly DerivationNode[]
  /** Enter auf einer Zeile. Es gibt keine Karten-Route — der Aufrufer öffnet die Karte. */
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
 * Herkunftsbaum (Issue #611) auf Basis der flachen Präorder-Liste aus Issue #609 — seit Issue
 * #644 der Baum eines Vorhabens, dargestellt im Detail-Dialog.
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
export function DerivationTree({ rows, onOpenCard }: Readonly<Props>) {
  const [collapsed, setCollapsed] = useState<ReadonlySet<number>>(() => new Set())
  const [focused, setFocused] = useState<number | undefined>(undefined)
  // Getrennt vom Fokus: Das Ziel eines Sprungs kann beim Ausloesen noch eingeklappt und damit gar
  // nicht gerendert sein. Fokus und Scrollen muessen deshalb einen Rendervorgang spaeter laufen.
  const [sprungZiel, setSprungZiel] = useState<number | undefined>(undefined)
  const [hervorgehoben, setHervorgehoben] = useState<number | undefined>(undefined)
  const zeilenRefs = useRef(new Map<number, HTMLElement>())

  useEffect(() => {
    const el = sprungZiel === undefined ? undefined : zeilenRefs.current.get(sprungZiel)
    if (el === undefined) {
      return
    }
    el.scrollIntoView({ block: 'nearest' })
    el.focus()
    // Zuruecksetzen, damit ein zweiter Sprung auf dasselbe Ziel wieder wirkt.
    setSprungZiel(undefined)
  }, [sprungZiel])

  if (rows.length === 0) {
    // Vorhabenbezogen, nicht boardbezogen: Diese Ansicht haengt im Dialog EINES Vorhabens, ein
    // Satz ueber das Board waere hier die falsche Aussage. Kein Fehler — ein Vorhaben ohne
    // zugeordnete Karten ist ein gueltiger Zustand.
    return (
      <Typography color="text.secondary">
        Diesem Vorhaben sind noch keine Karten zugeordnet.
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
    // Die Sprung-Hervorhebung ist voruebergehend und erlischt beim naechsten Fokuswechsel. Ein
    // Zeitablauf waere die Alternative gewesen, braucht aber eine willkuerliche Dauer.
    setHervorgehoben(undefined)
    el.focus()
  }

  /**
   * Sprung von einer Abhängigkeitsmarke zur Zielzeile (Issue #612). Ein Ziel ausserhalb des Baums
   * ist der Normalfall und kein Fehler: `dependencies` enthält board-interne Nummern, aufgelöst
   * gegen alle Karten des Boards — eine Karte ohne Herkunftsbezug steht dort aber nicht.
   */
  const springe = (nummer: number) => {
    const index = rows.findIndex((zeile) => zeile.number === nummer)
    if (index < 0) {
      return
    }
    // Den ganzen Pfad aufklappen, sonst spraenge die Ansicht ins Leere.
    setCollapsed((vorher) => {
      const naechste = new Set(vorher)
      for (let p = parentIndex(rows, index); p >= 0; p = parentIndex(rows, p)) {
        naechste.delete(rows[p].number)
      }
      return naechste
    })
    setFocused(nummer)
    setHervorgehoben(nummer)
    setSprungZiel(nummer)
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
            data-jump-target={zeile.number === hervorgehoben ? 'true' : undefined}
            // Der Roving-Tabindex folgt dem tatsaechlichen Fokus. Ohne diese Kopplung liefen die
            // beiden auseinander, sobald der Fokus von aussen kommt — dann traege die aktive Zeile
            // den Tab-Stopp, und die Sprungmarke einer anderen Zeile waere unerreichbar.
            onFocus={() => setFocused(zeile.number)}
            onKeyDown={(event) => beiTaste(event, index)}
            onClick={() => onOpenCard(zeile.number)}
            sx={{
              display: 'flex',
              alignItems: 'baseline',
              gap: 1,
              py: 0.5,
              pr: 1,
              pl: zeile.depth * 2.5 + 1,
              borderRadius: 1,
              cursor: 'pointer',
              opacity: zeile.blocked ? 0.6 : 1,
              bgcolor: zeile.number === hervorgehoben ? 'action.selected' : undefined,
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
              <Sprungmarke
                key={`i${nummer}`}
                nummer={nummer}
                aktiv={zeile.number === effektivFokus}
                onSpringen={springe}
              />
            ))}
            {zeile.externalDependencies.map((nummer) => (
              <Marke key={`e${nummer}`} text={`⇠ extern #${nummer}`} />
            ))}
            {zeile.labels.map((label) => (
              <LabelMarke key={`l${label.name}`} name={label.name} farbe={label.color} />
            ))}
          </Box>
        )
      })}
    </Box>
  )
}

/**
 * Bedienbare Marke einer board-internen Abhängigkeit (Issue #612): springt zur Zielzeile.
 *
 * <p>Der Tab-Stopp folgt dem Roving Tabindex des Baums — erreichbar ist nur die Marke der gerade
 * aktiven Zeile. Ohne diese Kopplung wäre jede Marke jeder Zeile ein eigener Tab-Stopp, und der
 * Baum verlöre die Eigenschaft, als Ganzes ein einziger Tab-Stopp zu sein.
 *
 * <p>`stopPropagation` auf jedem Tastendruck: Sonst behandelte die Zeile denselben Anschlag noch
 * einmal — Enter auf der Marke spränge und öffnete zugleich die Karte. Dasselbe gilt fuer den
 * Klick (Issue #739): Enter/Space auf einem `<button>` loesen zusaetzlich zum Tastendruck ein
 * synthetisches `click`-Ereignis aus, seit die Zeile selbst einen `onClick` traegt — ohne
 * `stopPropagation` hier oeffnete das zugleich die eigene Karte der Zeile.
 */
function Sprungmarke({
  nummer,
  aktiv,
  onSpringen,
}: Readonly<{ nummer: number; aktiv: boolean; onSpringen: (nummer: number) => void }>) {
  return (
    <Box
      component="button"
      type="button"
      tabIndex={aktiv ? 0 : -1}
      aria-label={`Zur Karte #${nummer} springen`}
      onClick={(event: MouseEvent<HTMLElement>) => {
        event.stopPropagation()
        onSpringen(nummer)
      }}
      onKeyDown={(event: KeyboardEvent<HTMLElement>) => event.stopPropagation()}
      sx={{
        px: 0.75,
        borderRadius: 10,
        border: 1,
        borderColor: 'divider',
        bgcolor: 'transparent',
        color: 'text.secondary',
        font: 'inherit',
        fontSize: '0.75rem',
        whiteSpace: 'nowrap',
        cursor: 'pointer',
        '&:hover': { borderColor: 'text.secondary' },
      }}
    >
      ⇠ #{nummer}
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

/**
 * Label-Marke einer Zeile (Issue #661): Name als Text, die Labelfarbe als Chip-Flaeche.
 *
 * <p>Wie {@link Marke} ausdruecklich ohne Rolle und ohne `tabIndex` — der Baum ist ein Roving
 * Tabindex mit genau einem Tab-Stopp und vertraegt kein zweites Fokusmodell daneben.
 *
 * <p>Flaeche und Textfarbe kommen aus `labelChipSx`, derselben Quelle wie an den vier
 * Chip-Stellen: Labelfarben sind nutzerdefiniert und duerfen ein Theme-Token sein, an dem
 * `getContrastText` wirft (#649).
 */
function LabelMarke({ name, farbe }: Readonly<{ name: string; farbe: string }>) {
  return (
    <Typography
      variant="caption"
      component="span"
      sx={{
        ...labelChipSx(farbe),
        px: 0.75,
        borderRadius: 10,
        whiteSpace: 'nowrap',
      }}
    >
      {name}
    </Typography>
  )
}
