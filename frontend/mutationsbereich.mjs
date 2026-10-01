import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * Die eine Quelle des Frontend-Pruefbereichs (Issue #1275, Plan #1270, E2/E6).
 *
 * `frontend/mutationsstufen.json` nennt die Ausschnitte, aus denen die `mutate`-Liste des
 * Mutationslaufs entsteht. Wer sie an zwei Stellen pflegt, bekommt das Ergebnis aus #1073:
 * einen Bericht, der vollstaendig aussieht und es nicht ist. Konfiguration und Treiber lesen
 * deshalb beide hier.
 *
 * Reines JavaScript, damit `stryker.config.mjs` es ohne Uebersetzungsschritt einbinden kann;
 * synchron, damit sie ein Objekt und keine Funktion exportiert (Stryker 8.7.1 verlangt das).
 */

const PLAN_PFAD = join(dirname(fileURLToPath(import.meta.url)), 'mutationsstufen.json')

/** Der Stufenplan, frisch von der Platte gelesen. */
export function stufenplanLesen() {
  return JSON.parse(readFileSync(PLAN_PFAD, 'utf8'))
}

/** Die Ausschnitte, die bereits im Pruefbereich stehen — `aufgenommen` traegt ein Datum. */
export function aufgenommene(plan) {
  return plan.ausschnitte.filter((ausschnitt) => typeof ausschnitt.aufgenommen === 'string')
}

/**
 * Der naechste Ausschnitt, der aufgenommen werden soll: der mit der kleinsten `reihenfolge`
 * unter den noch nicht aufgenommenen. `undefined`, wenn keiner mehr offen ist. Die Reihenfolge
 * ist damit Daten und keine Absprache (E14).
 */
export function kandidat(plan) {
  return plan.ausschnitte
    .filter((ausschnitt) => typeof ausschnitt.aufgenommen !== 'string')
    .reduce(
      (bester, ausschnitt) =>
        bester === undefined || ausschnitt.reihenfolge < bester.reihenfolge ? ausschnitt : bester,
      undefined,
    )
}

/**
 * Die `mutate`-Liste zu den genannten Ausschnitten: ihre Muster, dann die Ausschluesse der
 * Testdateien, dann jede Ausnahme aus dem Stufenplan mit `!` davor. Die Negationen stehen hier
 * und nicht in der Konfiguration, weil `--mutate` die Liste der Konfiguration ersetzt (E6) —
 * ohne sie mutierte der Vollauf die Testdateien mit.
 */
export function mutateFuer(namen, plan) {
  const gewaehlt = plan.ausschnitte.filter((ausschnitt) => namen.includes(ausschnitt.name))
  return [
    ...gewaehlt.flatMap((ausschnitt) => ausschnitt.muster),
    '!src/**/*.test.ts',
    '!src/**/*.test.tsx',
    ...plan.ausnahmen.map((ausnahme) => `!${ausnahme}`),
  ]
}
