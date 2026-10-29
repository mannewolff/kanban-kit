import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { useEffect, useRef, useState, type KeyboardEvent, type MouseEvent } from 'react'
import { type DerivationNode } from '../api/cards'
import { artAusTitel, useKitNamen, type KitNamen, type KitNamenZustand } from '../api/kitNamen'
import { useT } from '../i18n/SpracheContext'
import { kuerzeTitel } from '../lib/kuerzeTitel'
import { MELDER, NUR_LESER_SX } from '../theme'
import { labelChipSx } from './labelChipSx'

interface Props {
  /**
   * Die Zeilen in Präorder, bereits geladen. Seit Issue #644 lädt die Komponente nicht mehr
   * selbst: Sie hängt im Vorhaben-Dialog, der die Karte ohnehin kennt und die Lade-, Fehler- und
   * Leerzustände für alle seine Bereiche gemeinsam führt. Geladen wird hier nur noch das
   * Namen-Register für die Hakenspalte (`useKitNamen`, Plan #1582, E10).
   */
  rows: readonly DerivationNode[]
  /** Enter auf einer Zeile. Es gibt keine Karten-Route — der Aufrufer öffnet die Karte. */
  onOpenCard: (number: number) => void
  /**
   * Nummer der Karte, deren Detail den Baum zeigt (Plan #1980, E8). Ihre Zeile trägt
   * `aria-current` und ist hervorgehoben; Klick und Enter öffnen sie nicht noch einmal. Ohne Angabe
   * — im Baum des Vorhabens — ist keine Zeile die aktuelle.
   */
  aktuelleNummer?: number
}

/** Ab dieser Länge wird ein Titel im Baum gekürzt (Plan #1980, E9). */
const TITEL_MAX = 60

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

/**
 * Die direkten Kinder einer Zeile in Präorder: alles zwischen der Zeile und der nächsten Zeile
 * gleicher oder geringerer Tiefe, davon genau die Zeilen einer Ebene tiefer. Die Nachfahren eines
 * Kindes liegen dazwischen — die Suche darf dort nicht abbrechen, sonst bliebe jedes Kind nach dem
 * ersten ungezählt.
 */
function directChildren(rows: readonly DerivationNode[], index: number): DerivationNode[] {
  const kinder: DerivationNode[] = []
  for (let j = index + 1; j < rows.length && rows[j].depth > rows[index].depth; j++) {
    if (rows[j].depth === rows[index].depth + 1) {
      kinder.push(rows[j])
    }
  }
  return kinder
}

/** Was die Hakenspalte einer Zeile aussagt — oder `null`, wenn sie leer bleibt. */
export type HakenArt = 'erledigt' | 'inReview' | 'abgearbeitet' | null

/**
 * Die Regel der Hakenspalte (Issue #985) als reine Funktion — ohne Rendering prüfbar.
 *
 * <p>Erledigt ist eine Karte, die auf Done steht. **In review** ist eine Karte mit dem Status In
 * review — implementiert, aber noch nicht abgenommen (Issue #1608); Done hat Vorrang. **Abgearbeitet** ist ein Planungsdokument, aus
 * dem der nächste Schritt entstanden ist: ein `[Fachlich]` mit mindestens einem `[Plan]` darunter,
 * ein `[Plan]` mit mindestens einem Arbeitspaket darunter. Gezählt werden nur **direkte** Kinder —
 * Plan und Arbeitspakete hängen direkt an ihrem Vorgänger, ein Dokument zwei Ebenen tiefer gehört
 * zu einer anderen Zeile.
 *
 * <p>Die Art einer Zeile lesen die Titel-Präfixe des Namen-Registers (Plan #1582, E2): Ein
 * `[Requirement]` gilt wie ein `[Fachlich]`.
 */
export function hakenArt(rows: readonly DerivationNode[], index: number, kitNamen: KitNamen): HakenArt {
  const zeile = rows[index]
  if (zeile.done) {
    return 'erledigt'
  }
  if (zeile.inReview) {
    return 'inReview'
  }
  const art = artAusTitel(kitNamen, zeile.title)
  if (art === 'FACHLICH') {
    const plaene = directChildren(rows, index).filter((kind) => artAusTitel(kitNamen, kind.title) === 'PLAN')
    return plaene.length > 0 ? 'abgearbeitet' : null
  }
  if (art === 'PLAN') {
    return directChildren(rows, index).length > 0 ? 'abgearbeitet' : null
  }
  return null
}

/**
 * Was die Hakenspalte zeigt: der Haken, oder — solange das Namen-Register lädt oder wenn es fehlt —
 * der Hinweis darauf an genau der Stelle, die ein Präfix braucht (Plan #1582, E10). Eine erledigte
 * Karte und eine in review brauchen keins: Ihren Haken setzt der Status, nicht der Titel.
 */
type Hakenspalte = HakenArt | 'laden' | 'fehler'

function hakenspalte(rows: readonly DerivationNode[], index: number, kitNamen: KitNamenZustand): Hakenspalte {
  if (rows[index].done) {
    return 'erledigt'
  }
  if (rows[index].inReview) {
    return 'inReview'
  }
  return kitNamen.status === 'fertig' ? hakenArt(rows, index, kitNamen.daten) : kitNamen.status
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
export function DerivationTree({ rows, onOpenCard, aktuelleNummer }: Readonly<Props>) {
  const [collapsed, setCollapsed] = useState<ReadonlySet<number>>(() => new Set())
  const [focused, setFocused] = useState<number | undefined>(undefined)
  // Getrennt vom Fokus: Das Ziel eines Sprungs kann beim Ausloesen noch eingeklappt und damit gar
  // nicht gerendert sein. Fokus und Scrollen muessen deshalb einen Rendervorgang spaeter laufen.
  const [sprungZiel, setSprungZiel] = useState<number | undefined>(undefined)
  const [hervorgehoben, setHervorgehoben] = useState<number | undefined>(undefined)
  const zeilenRefs = useRef(new Map<number, HTMLElement>())
  const kitNamen = useKitNamen()
  const t = useT()

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
      <Typography color="text.secondary">{t.BAUM.LEER}</Typography>
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

  const oeffne = (nummer: number) => {
    if (nummer !== aktuelleNummer) {
      onOpenCard(nummer)
    }
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
        fokussiere(nummerBei(sichtbareIndizes.at(-1)))
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
        oeffne(zeile.number)
        break
      default:
        return
    }
    event.preventDefault()
  }

  return (
    <Box role="tree" aria-label={t.BAUM.HERKUNFT}>
      {sichtbareIndizes.map((index) => {
        const zeile = rows[index]
        const kinderDa = hasChildren(rows, index)
        const aktuell = zeile.number === aktuelleNummer
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
            aria-current={aktuell ? 'true' : undefined}
            tabIndex={zeile.number === effektivFokus ? 0 : -1}
            data-jump-target={zeile.number === hervorgehoben ? 'true' : undefined}
            // Der Roving-Tabindex folgt dem tatsaechlichen Fokus. Ohne diese Kopplung liefen die
            // beiden auseinander, sobald der Fokus von aussen kommt — dann traege die aktive Zeile
            // den Tab-Stopp, und die Sprungmarke einer anderen Zeile waere unerreichbar.
            onFocus={() => setFocused(zeile.number)}
            onKeyDown={(event) => beiTaste(event, index)}
            onClick={() => oeffne(zeile.number)}
            sx={{
              display: 'flex',
              alignItems: 'baseline',
              gap: 1,
              py: 0.5,
              pr: 1,
              // Fest, ohne Tiefe: Die Hakenspalte steht über alle Ebenen bündig, eingerückt wird
              // erst dahinter (Issue #985).
              pl: 1,
              borderRadius: 1,
              cursor: aktuell ? 'default' : 'pointer',
              opacity: zeile.blocked ? 0.6 : 1,
              bgcolor: aktuell || zeile.number === hervorgehoben ? 'action.selected' : undefined,
              '&:focus-visible': { outline: '2px solid', outlineColor: 'primary.main' },
            }}
          >
            <Haken art={hakenspalte(rows, index, kitNamen)} />
            <Typography
              variant="body2"
              component="span"
              sx={{ color: 'text.secondary', ml: zeile.depth * 2.5 }}
            >
              #{zeile.number}
            </Typography>
            {zeile.labels.map((label) => (
              <LabelMarke key={`l${label.name}`} name={label.name} farbe={label.color} />
            ))}
            <Titel titel={zeile.title} aktuell={aktuell} />
            {zeile.type === 'EPIC' && <Marke text={t.BAUM.VORHABEN} />}
            {zeile.blocked && <Marke text={t.BAUM.BLOCKIERT} />}
            {zeile.broken && <Marke text={t.BAUM.KETTE_UNTERBROCHEN} />}
            {zeile.externalOrigin && <Marke text={t.BAUM.HERKUNFT_EXTERN(zeile.derivedFrom)} />}
            {zeile.dependencies.map((nummer) => (
              <Sprungmarke
                key={`i${nummer}`}
                nummer={nummer}
                aktiv={zeile.number === effektivFokus}
                onSpringen={springe}
              />
            ))}
            {zeile.externalDependencies.map((nummer) => (
              <Marke key={`e${nummer}`} text={t.BAUM.EXTERN(nummer)} />
            ))}
          </Box>
        )
      })}
    </Box>
  )
}

/**
 * Titel einer Zeile, nach {@link TITEL_MAX} Zeichen gekürzt (Plan #1980, E9). Gekürzt steht der
 * volle Titel zweimal daneben: als `title` fürs Darüberfahren am Desktop und als nur vorgelesener
 * Text, während der sichtbare Stummel vor Screenreadern verborgen ist. Ein ungekürzter Titel braucht
 * beides nicht.
 *
 * <p>Die aktuelle Zeile steht zusätzlich fett — die Hervorhebung hängt nicht an der Fläche allein
 * (CLAUDE-design.md: Farbe ist nie alleiniger Informationsträger).
 */
function Titel({ titel, aktuell }: Readonly<{ titel: string; aktuell: boolean }>) {
  const kurz = kuerzeTitel(titel, TITEL_MAX)
  const gekuerzt = kurz !== titel
  return (
    <Typography
      variant="body2"
      component="span"
      title={gekuerzt ? titel : undefined}
      sx={{ flexGrow: 1, fontWeight: aktuell ? 600 : undefined }}
    >
      {gekuerzt ? (
        <>
          <span aria-hidden="true">{kurz}</span>
          <Box component="span" sx={NUR_LESER_SX}>
            {titel}
          </Box>
        </>
      ) : (
        titel
      )}
    </Typography>
  )
}

/**
 * Die Hakenspalte einer Zeile (Issue #985): ganz links, feste Breite, damit die Zeilen über alle
 * Tiefen bündig stehen — ohne Haken bleibt sie leer statt zu fehlen.
 *
 * <p>`role="img"` mit `aria-label` statt eines blossen Zeichens: Farbe ist nie alleiniger
 * Informationsträger (CLAUDE-design.md), und ein „✓" allein sagt nicht, ob eine Karte erledigt
 * oder ein Planungsdokument abgearbeitet ist. Wie {@link Marke} ohne `tabIndex` — der Baum ist ein
 * Roving Tabindex mit genau einem Tab-Stopp.
 */
function Haken({ art }: Readonly<{ art: Hakenspalte }>) {
  const t = useT()
  const zeichen = HAKEN_ZEICHEN[art ?? 'leer']
  const name = art === null ? undefined : t.BAUM.HAKEN[art]
  return (
    <Typography
      variant="body2"
      component="span"
      role={art === null ? undefined : 'img'}
      aria-label={name}
      // Die leere Spalte traegt keine Rolle und keinen Namen — sie haelt nur Platz. Damit die
      // Buendigkeit trotzdem pruefbar bleibt, ist sie ueber diese Marke greifbar.
      data-testid="hakenspalte"
      sx={{
        width: 14,
        flexShrink: 0,
        textAlign: 'center',
        color: hakenFarbe(art),
        fontWeight: 600,
      }}
    >
      {zeichen}
    </Typography>
  )
}

/**
 * Farbe des Hakens: Bernstein für „in review" — unterwegs, noch nicht abgenommen (Issue #1608) —,
 * Grün für erledigt und abgearbeitet, gedämpft für Lade- und Fehlerhinweis.
 */
function hakenFarbe(art: Hakenspalte): string {
  if (art === 'inReview') {
    return MELDER.bernst
  }
  return art === 'laden' || art === 'fehler' ? 'text.secondary' : MELDER.gruen
}

/**
 * Sichtbares Zeichen je Inhalt der Hakenspalte; den zugänglichen Namen nennt das Wörterbuch
 * (`BAUM.HAKEN`). Lade- und Fehlerhinweis tragen dieselben Worte wie die Stufenleiste (Plan #1582,
 * E10); sichtbar bleibt ein Zeichen, weil die Spalte nur 14 px breit ist — der Name trägt die Aussage.
 */
const HAKEN_ZEICHEN: Record<NonNullable<Hakenspalte> | 'leer', string> = {
  erledigt: '✓',
  inReview: '✓',
  abgearbeitet: '✓',
  laden: '…',
  fehler: '!',
  leer: '',
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
  const t = useT()
  return (
    <Box
      component="button"
      type="button"
      tabIndex={aktiv ? 0 : -1}
      aria-label={t.BAUM.SPRINGEN_ARIA(nummer)}
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
