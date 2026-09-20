import { useEffect, useState } from 'react'
import { boardsApi, type Board } from '../api/boards'
import { ApiError } from '../api/client'
import { dashboardApi, type BoardDashboardKpis } from '../api/dashboard'
import { epicsApi, type Epic } from '../api/epics'
import { nightRunsApi, type NightRunErrorClassCounts, type NightRunView } from '../api/nightRuns'
import { juengsterLauf } from './leitstand'

/** Der Ladezustand eines Abrufs des Leitstands; `ohneRecht` ist die stille Form von „403". */
export type Laden<T> = { art: 'laedt' } | { art: 'ohneRecht' } | { art: 'fehler' } | { art: 'da'; wert: T }

const LAEDT = { art: 'laedt' } as const

/**
 * Lädt einen Abruf in einen {@link Laden}-Zustand, sobald sein Schlüssel bekannt ist; 403 wird zu
 * „ohne Recht". Solange der Schlüssel `null` ist, bleibt der Zustand „lädt" und es wird nichts
 * abgerufen — so hängt der Abruf der Läufe am Projekt, das erst mit dem Board hereinkommt.
 *
 * Der Schlüssel steht **vor** dem Abruf und nicht als Bedingung daneben: Die eine Prüfung auf
 * `null` verengt damit zugleich den Typ, den der Abruf bekommt. Die frühere Form — ein
 * Abruf-Argument, das je nach Bedingung die Funktion oder `null` war — brauchte dafür einen
 * Ternär je Abruf, und genau diese sechs Ternäre trieben die kognitive Komplexität der Seite auf
 * 63 (Issue #1053): SonarCloud rechnet jeden Zweig, der eine Funktion trägt, als weitere
 * Verschachtelungsstufe für alles, was danach kommt.
 */
function useLadenWenn<S, T>(
  schluessel: S | null,
  abruf: (schluessel: S) => Promise<T>,
  abhaengig: readonly unknown[],
): Laden<T> {
  const [zustand, setZustand] = useState<Laden<T>>(LAEDT)
  useEffect(() => {
    if (schluessel === null) {
      return
    }
    let aktiv = true
    setZustand(LAEDT)
    abruf(schluessel).then(
      (wert) => {
        if (aktiv) setZustand({ art: 'da', wert })
      },
      (err: unknown) => {
        if (aktiv) setZustand(err instanceof ApiError && err.status === 403 ? { art: 'ohneRecht' } : { art: 'fehler' })
      },
    )
    return () => {
      aktiv = false
    }
    // Der Abruf ist je Render eine neue Funktion; maßgeblich sind die Werte, von denen er abhängt.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, abhaengig)
  return zustand
}

/** Alles, was der Leitstand (#979) für ein Board lädt und daraus ableitet. */
export interface LeitstandDaten {
  /** Ob der Routen-Parameter eine Board-ID war; ohne sie wird nichts abgerufen. */
  validId: boolean
  board: Laden<Board>
  kpis: Laden<BoardDashboardKpis>
  epics: Laden<Epic[]>
  klassen: Laden<NightRunErrorClassCounts>
  /** Das Projekt des Boards; `null`, solange das Board nicht geladen ist. */
  projectId: number | null
  /** Die aufbewahrten Läufe; `null`, solange sie fehlen — auch mangels Recht. */
  liste: NightRunView[] | null
  juengster: NightRunView | null
  /** Die Vorhaben als Liste; ohne geladene Vorhaben leer statt fehlend. */
  epicListe: readonly Epic[]
}

/**
 * Lädt die Daten des Leitstands zu einem Board und leitet die Sichtwerte ab (Issue #1053).
 *
 * Die Läufe, die Fehlerklassen und damit der Verbrauch hängen am Projekt des Boards und beginnen
 * erst, wenn das Board da ist. Wer die Läufe nicht sehen darf, bekommt vom Server 403 — das bleibt
 * als `ohneRecht` still, und die Seite lässt die Lauf-Bereiche weg.
 */
export function useLeitstandDaten(boardId: string | undefined): LeitstandDaten {
  const id = Number.parseInt(boardId ?? '', 10)
  const gueltigeId = Number.isInteger(id) && id > 0 ? id : null

  const board = useLadenWenn(gueltigeId, (bid) => boardsApi.get(bid), [gueltigeId])
  const kpis = useLadenWenn(gueltigeId, (bid) => dashboardApi.get(bid), [gueltigeId])
  const epics = useLadenWenn(gueltigeId, (bid) => epicsApi.list(bid), [gueltigeId])
  const projectId = board.art === 'da' ? board.wert.projectId : null
  const laeufe = useLadenWenn(projectId, (pid) => nightRunsApi.list(pid), [projectId])
  const klassen = useLadenWenn(projectId, (pid) => nightRunsApi.errorClassCounts(pid), [projectId])

  const liste = laeufe.art === 'da' ? laeufe.wert : null
  const juengster = liste === null ? null : juengsterLauf(liste)
  const epicListe = epics.art === 'da' ? epics.wert : []

  return { validId: gueltigeId !== null, board, kpis, epics, klassen, projectId, liste, juengster, epicListe }
}
