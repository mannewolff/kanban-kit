import { useEffect, useState } from 'react'
import { boardsApi, type Board } from '../api/boards'
import { ApiError, istVorschauVerborgen } from '../api/client'
import { dashboardApi, type AgingWip, type BoardDashboardKpis, type BoardFlow, type DeliveryView, type EpicForecast } from '../api/dashboard'
import { epicsApi, type Epic } from '../api/epics'
import { nightRunsApi, type NightRunErrorClassCounts, type NightRunModels, type NightRunRework, type NightRunView } from '../api/nightRuns'
import { juengsterLauf } from './leitstand'

/**
 * Der Ladezustand eines Abrufs des Leitstands; `ohneRecht` ist die stille Form von „403“,
 * `vorschauVerborgen` die Abweisung `403 vorschau-verborgen` der „Ansicht als XY“, an deren Stelle
 * ein Hinweis steht (Plan #1914, E16).
 */
export type Laden<T> =
  | { art: 'laedt' }
  | { art: 'ohneRecht' }
  | { art: 'vorschauVerborgen' }
  | { art: 'fehler' }
  | { art: 'da'; wert: T }

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
function fehlerZustand(err: unknown): Laden<never> {
  if (istVorschauVerborgen(err)) return { art: 'vorschauVerborgen' }
  return err instanceof ApiError && err.status === 403 ? { art: 'ohneRecht' } : { art: 'fehler' }
}

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
        if (aktiv) setZustand(fehlerZustand(err))
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
  /** Die Platte „Fluss“ (Issue #1778); wie die Kennzahlen für alle, die das Board sehen dürfen. */
  fluss: Laden<BoardFlow>
  /** Die Platte „Alter in Arbeit“ (Issue #1778). */
  alter: Laden<AgingWip>
  /** Die Prognose der Platte „Vorhaben“ (Issue #1828); lädt wie der Fluss sofort zum Board. */
  prognose: Laden<EpicForecast>
  /** Die Platte „Auslieferung“ (Issue #1836) mit den Daten des ganzen Projekts; lädt sofort zum Board. */
  auslieferung: Laden<DeliveryView>
  epics: Laden<Epic[]>
  klassen: Laden<NightRunErrorClassCounts>
  /** Die Nacharbeit der Runs auf diesem Board (Issue #1760); ohne Run-Recht `ohneRecht`. */
  nacharbeit: Laden<NightRunRework>
  /** Der Modellvergleich der Runs auf diesem Board (Issue #1949); ohne Run-Recht `ohneRecht`. */
  modelle: Laden<NightRunModels>
  /** Das Projekt des Boards; `null`, solange das Board nicht geladen ist. */
  projectId: number | null
  /** Die aufbewahrten Läufe; `null`, solange sie fehlen — auch mangels Recht. */
  liste: NightRunView[] | null
  juengster: NightRunView | null
  /** Ob die Läufe in der „Ansicht als XY“ verborgen sind (`403 vorschau-verborgen`, Plan #1914 E16). */
  laeufeVerborgen: boolean
  /** Die Vorhaben als Liste; ohne geladene Vorhaben leer statt fehlend. */
  epicListe: readonly Epic[]
}

/**
 * Lädt die Daten des Leitstands zu einem Board und leitet die Sichtwerte ab (Issue #1053).
 *
 * Die Läufe, die Fehlerklassen und damit der Verbrauch hängen am Projekt des Boards und beginnen
 * erst, wenn das Board da ist; Nacharbeit, Modelle, Fluss, Alter, Prognose und Auslieferung hängen am Board selbst und laden sofort. Wer die
 * Läufe nicht sehen darf, bekommt vom Server 403 — das bleibt als `ohneRecht` still, und die Seite
 * lässt die Lauf-Bereiche weg. In der „Ansicht als XY“ antwortet er dort, wo XY sähe, was der Admin
 * nicht sehen darf, mit `403 vorschau-verborgen`; daraus wird `vorschauVerborgen`, und die Seite
 * zeigt den Hinweis statt der Lauf-Bereiche (Plan #1914, E16).
 */
export function useLeitstandDaten(boardId: string | undefined): LeitstandDaten {
  // Stryker disable next-line StringLiteral: gleichwertig — Number.parseInt ergibt für '' wie für jeden anderen Nicht-Zahl-Text NaN
  const id = Number.parseInt(boardId ?? '', 10)
  const gueltigeId = Number.isInteger(id) && id > 0 ? id : null

  const board = useLadenWenn(gueltigeId, (bid) => boardsApi.get(bid), [gueltigeId])
  const kpis = useLadenWenn(gueltigeId, (bid) => dashboardApi.get(bid), [gueltigeId])
  const fluss = useLadenWenn(gueltigeId, (bid) => dashboardApi.flow(bid), [gueltigeId])
  const alter = useLadenWenn(gueltigeId, (bid) => dashboardApi.aging(bid), [gueltigeId])
  const prognose = useLadenWenn(gueltigeId, (bid) => dashboardApi.epicForecast(bid), [gueltigeId])
  const auslieferung = useLadenWenn(gueltigeId, (bid) => dashboardApi.delivery(bid), [gueltigeId])
  const epics = useLadenWenn(gueltigeId, (bid) => epicsApi.list(bid), [gueltigeId])
  const nacharbeit = useLadenWenn(gueltigeId, (bid) => nightRunsApi.rework(bid), [gueltigeId])
  const modelle = useLadenWenn(gueltigeId, (bid) => nightRunsApi.models(bid), [gueltigeId])
  const projectId = board.art === 'da' ? board.wert.projectId : null
  const laeufe = useLadenWenn(projectId, (pid) => nightRunsApi.list(pid), [projectId])
  const klassen = useLadenWenn(projectId, (pid) => nightRunsApi.errorClassCounts(pid), [projectId])

  const liste = laeufe.art === 'da' ? laeufe.wert : null
  const juengster = liste === null ? null : juengsterLauf(liste)
  const epicListe = epics.art === 'da' ? epics.wert : []

  const laeufeVerborgen = laeufe.art === 'vorschauVerborgen'

  return { validId: gueltigeId !== null, board, kpis, fluss, alter, prognose, auslieferung, epics, klassen, nacharbeit, modelle, projectId, liste, juengster, laeufeVerborgen, epicListe }
}
