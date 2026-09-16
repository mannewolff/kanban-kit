/**
 * Statusfarben je Spalte, aus dem Spaltennamen abgeleitet. Bewusste Ausnahme von
 * "Farben nur über das Theme": Board-Status ist eine feste, kleine, semantische Menge —
 * ein zentraler Ort statt verstreuter Hex-Werte. Die Töne sind die Melder des Leitstand-Entwurfs
 * (CLAUDE-design.md): getönte Schild-Fläche, Melder als Schrift, Melder als Punkt.
 *
 * Das Modul trägt ausschließlich Status-Farben (#648, E2). Jedes andere Token gehört ins Theme —
 * zwei Farbquellen laufen auseinander, ohne dass ein Test es merkt.
 *
 * **Zwei Erscheinungsbilder (#952, Plan #932 E10/E11).** Die Werte beider Erscheinungsbilder stehen
 * hier in {@link STATUS_FARBWERTE}; `theme.ts` legt sie als CSS-Variablen an. Die Funktionen liefern
 * Verweise auf diese Variablen, keine Werte — die Signatur bleibt „Zeichenkette rein, Zeichenkette
 * raus", und welches Erscheinungsbild gilt, entscheidet die Media-Query statt eines Parameters, der
 * React-Kontext in ein Lib-Modul zöge. Das Modul importiert das Theme bewusst nicht: `theme.ts`
 * liest die Werte von hier, ein Rückimport wäre ein Zyklus. Dass die Verweise auf die Variablen
 * zeigen, die MUI erzeugt, prüft `theme.test.ts`.
 */
export interface StatusColorSet {
  /** Weiche Tint-Fläche (Pills/Badges). */
  bg: string
  /** Textfarbe auf {@link bg}. */
  text: string
  /** Akzentfarbe des Status: Oberkante von Spalte und Karte, Punkt in Listenansichten. */
  dot: string
}

type StatusSet = 'done' | 'review' | 'progress' | 'ready' | 'backlog' | 'neutral' | 'archived'

/**
 * Werte beider Erscheinungsbilder, abgebildet auf die Melder des Leitstand-Entwurfs (#978,
 * `docs/entwurf-leitstand.html`): Done grün, In review bernstein, In Arbeit stahl, alles Wartende
 * grau — so, wie die Spalten und die Liste des Entwurfs ihre LED setzen.
 *
 * - `dot` ist der Melder als Füllung (LED, Kante): 3:1 auf jeder Fläche der Warte, die Nut
 *   eingeschlossen. Hell sind Grün, Bernstein und Grau dafür nachgedunkelt (siehe `theme.ts`).
 * - `bg` ist der Melder zu 13 % über der Platte, wie das Schild des Entwurfs (Z. 780–786).
 * - `text` ist der Melder als Schrift: 4,5:1 auf `bg`, Platte und Grund. Hell nachgedunkelt, dunkel
 *   hält der Entwurfston unverändert — außer Grau, das aufgehellt ist.
 */
const HELL_GRAU: StatusColorSet = { bg: '#EEEFF2', text: '#636972', dot: '#757B86' }
const DUNKEL_GRAU: StatusColorSet = { bg: '#22272E', text: '#878E97', dot: '#6E7681' }

export const STATUS_FARBWERTE: Readonly<Record<'light' | 'dark', Readonly<Record<StatusSet, StatusColorSet>>>> = {
  light: {
    done: { bg: '#E2EFE7', text: '#277741', dot: '#2E8B4C' },
    review: { bg: '#F3ECE0', text: '#8A6110', dot: '#A17113' },
    progress: { bg: '#E2EBF7', text: '#2C68BD', dot: '#2F6FC9' },
    ready: HELL_GRAU,
    backlog: HELL_GRAU,
    neutral: HELL_GRAU,
    archived: HELL_GRAU,
  },
  dark: {
    done: { bg: '#1D312C', text: '#46C46F', dot: '#46C46F' },
    review: { bg: '#312E27', text: '#E0AE49', dot: '#E0AE49' },
    progress: { bg: '#202B3D', text: '#5B96F0', dot: '#5B96F0' },
    ready: DUNKEL_GRAU,
    backlog: DUNKEL_GRAU,
    neutral: DUNKEL_GRAU,
    archived: DUNKEL_GRAU,
  },
}

/** Verweise auf die Variablen eines Sets, wie `theme.ts` sie über `palette.status` anlegt. */
const verweise = (set: StatusSet): StatusColorSet => ({
  bg: `var(--mb-palette-status-${set}-bg)`,
  text: `var(--mb-palette-status-${set}-text)`,
  dot: `var(--mb-palette-status-${set}-dot)`,
})

// Reihenfolge nach Namensmuster; erster Treffer gewinnt (keine Überschneidungen).
const MUSTER: ReadonlyArray<StatusSet> = ['done', 'review', 'progress', 'ready', 'backlog']

/** Volles Farbset einer Spalte (Tint-Fläche, Text, Punkt). */
export function statusColors(name: string): StatusColorSet {
  const n = name.toLowerCase()
  for (const set of MUSTER) {
    if (n.includes(set)) {
      return verweise(set)
    }
  }
  return verweise('neutral')
}

/** Farbe für archivierte Karten (Badges/Listen). */
export const ARCHIVED_STATUS_COLOR: StatusColorSet = verweise('archived')
