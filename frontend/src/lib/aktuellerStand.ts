import { NIGHT_RUN_STATE_TEXT } from './nightRunHandoff'
import { nachProjekt, type Projektgruppe } from './leitstand'
import type { NightRunState } from './nightRunLog'

/**
 * Die Rechnung der Sektion „Aktueller Status" des Plattform-Leitstands (#1172, Plan #1167):
 * der Stand eines laufenden Runs als Zahl (AK 8) und die Gruppierung seiner gemeldeten
 * Arbeitspakete nach Projekt (AK 6). Ohne React und ohne Netzzugriff — dasselbe Muster wie
 * `nightRunHandoff.ts` und `leitstand.ts` (Plan #1167, E6).
 *
 * <p>Die Formen sind absichtlich **strukturell** und nicht die Sichten aus `api/`: Gezählt wird
 * ein Zustand, gruppiert wird nach Projekt und Lauf. So bleibt das Modul ohne Kopplung an die
 * Antwort testbar — dieselbe Grenze, die {@link paketZaehlung} in `leitstand.ts` zieht.
 */

/** Ein gemeldetes Paket, so weit die Rechnung es braucht. */
export interface StandPaket {
  state: NightRunState
}

/** Ein laufender Run, so weit die Gruppierung ihn braucht. */
export interface StandLauf {
  nightRunId: number
  projectId: number
  projectName: string
}

/** Die gemeldeten Pakete **eines** Laufs, wie die Antwort sie neben `laufende` führt (E1). */
export interface StandLaufPakete<P extends StandPaket> {
  nightRunId: number
  pakete: readonly P[]
}

/** Ein Lauf im Projektblock: seine Pakete und sein Stand als fertiger Text. */
export interface Laufeintrag<P extends StandPaket> {
  nightRunId: number
  pakete: readonly P[]
  stand: string
}

/**
 * Die Reihenfolge der Kübel aus AK 8 — Erfolg · Erfolg, Prüfung rot · gescheitert ·
 * nicht bearbeitet. Ein Array und kein `Object.keys`: Die Ordnung ist die Aussage.
 */
const ZUSTAENDE: readonly NightRunState[] = ['GREEN', 'YELLOW', 'RED', 'GREY']

/**
 * Der Stand eines Laufs, etwa `"5 gemeldet · 3 Erfolg, 1 gescheitert, 1 nicht bearbeitet"`.
 *
 * <p>Gezählt wird **alles Gemeldete** einschließlich der grauen Pakete: Hier steht nicht der
 * Anteil gelungener Arbeit, sondern was der Lauf bisher berichtet hat. Genannt werden nur
 * Zustände mit mindestens einem Paket; **kein Nenner** — wie viele Pakete noch kommen, weiß
 * der laufende Run selbst nicht. Bei null Paketen bleibt es bei `"0 gemeldet"` (E8).
 *
 * <p><b>Gezählt wird nach {@link NightRunState} und nicht nach dem überschriebenen
 * Zustandstext</b> (Plan #1167, E4): `nightRunZustandsText` ersetzt das Wort je Fehlerklasse —
 * nach ihm gruppiert entstünden beliebig viele Kübel und Kopfzeilen-Einträge wie „Am
 * Zeitbudget beendet, ohne Ergebnis: 1". Das Wort der **Zeile** ist eine andere Sache und
 * steht in der Komponente.
 */
export function standText(pakete: readonly StandPaket[]): string {
  if (pakete.length === 0) {
    return '0 gemeldet'
  }
  const kuebel = ZUSTAENDE.map((state) => ({
    state,
    zahl: pakete.filter((p) => p.state === state).length,
  })).filter((k) => k.zahl > 0)
  const genannt = kuebel.map((k) => `${k.zahl} ${NIGHT_RUN_STATE_TEXT[k.state]}`).join(', ')
  return `${pakete.length} gemeldet · ${genannt}`
}

/**
 * Die laufenden Runs nach Projekt gruppiert, je Lauf ein Eintrag mit seinen gemeldeten Paketen
 * und seinem Stand.
 *
 * <p>Gebaut über {@link nachProjekt} — dieselbe Funktion, die der Bereich *Störungen* nutzt
 * (AK 6, E5); eine zweite Gruppierung daneben liefe beim nächsten Feinschliff auseinander.
 * Projektname und Ordnung kommen aus `laufende`, damit es die Zuordnung Projekt → Lauf nur
 * einmal gibt; die Reihenfolge der Antwort bleibt unverändert.
 *
 * <p>Ein Lauf ohne Eintrag in `gemeldetePakete` bekommt eine **leere** Paketliste und damit
 * den Stand `"0 gemeldet"` (E8): Er steht in der Liste oben, ein fehlender Kopf sähe aus wie
 * ein vergessener Run.
 */
export function laufgruppen<P extends StandPaket>(
  laufende: readonly StandLauf[],
  gemeldetePakete: readonly StandLaufPakete<P>[],
): Projektgruppe<Laufeintrag<P>>[] {
  const jeLauf = new Map(gemeldetePakete.map((eintrag) => [eintrag.nightRunId, eintrag.pakete]))
  return nachProjekt(
    laufende.map((lauf) => {
      const pakete = jeLauf.get(lauf.nightRunId) ?? []
      return {
        projectId: lauf.projectId,
        projectName: lauf.projectName,
        nightRunId: lauf.nightRunId,
        pakete,
        stand: standText(pakete),
      }
    }),
  ).map((gruppe) => ({
    projectId: gruppe.projectId,
    projectName: gruppe.projectName,
    eintraege: gruppe.eintraege.map(({ nightRunId, pakete, stand }) => ({
      nightRunId,
      pakete,
      stand,
    })),
  }))
}
