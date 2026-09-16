/**
 * Statusfarben je Spalte, aus dem Spaltennamen abgeleitet. Bewusste Ausnahme von
 * "Farben nur über das Theme": Board-Status ist eine feste, kleine, semantische Menge —
 * ein zentraler Ort statt verstreuter Hex-Werte. Entsättigt auf die Marken-Familie
 * (CLAUDE-design.md): weiche, helle Tints als Pill-Flächen, dunkler Text derselben Familie, ein
 * ruhiger Akzent-Punkt.
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
 * Werte beider Erscheinungsbilder. Hell ist unveränderter Bestand. Dunkel kehrt die Rollen um:
 * eine dunkle, im Farbton getönte Pill-Fläche, heller Text derselben Familie und ein aufgehellter
 * Punkt, der auf den dunklen Flächen des Leitstands 3:1 hält (`theme.test.ts`).
 */
export const STATUS_FARBWERTE: Readonly<Record<'light' | 'dark', Readonly<Record<StatusSet, StatusColorSet>>>> = {
  light: {
    done: { bg: '#E4F3EC', text: '#14624A', dot: '#2E9E7A' },
    review: { bg: '#F7EAE4', text: '#8A3F28', dot: '#C46B4E' },
    progress: { bg: '#FAF3E3', text: '#7A5B12', dot: '#C99A2E' },
    ready: { bg: '#E1F0F2', text: '#1E5F68', dot: '#2F8C97' },
    backlog: { bg: '#EDF5F6', text: '#1E5F68', dot: '#5BABB5' },
    neutral: { bg: '#F1F5F6', text: '#5F7A7F', dot: '#8FA6AB' },
    archived: { bg: '#F0F2F2', text: '#5F7A7F', dot: '#9FB0B4' },
  },
  dark: {
    done: { bg: '#173A30', text: '#8FE0BF', dot: '#3FBF92' },
    review: { bg: '#3F2A22', text: '#F2B8A2', dot: '#E08A6C' },
    progress: { bg: '#3A3120', text: '#F0D38F', dot: '#DDB04A' },
    ready: { bg: '#173A3F', text: '#9ADCE4', dot: '#4FB3BF' },
    backlog: { bg: '#1E363B', text: '#A9D8DE', dot: '#7CC3CC' },
    neutral: { bg: '#22343A', text: '#B4C6CA', dot: '#8FA6AB' },
    archived: { bg: '#262F31', text: '#AAB7BA', dot: '#7F9195' },
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
