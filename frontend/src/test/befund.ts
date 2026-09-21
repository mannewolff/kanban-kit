import type { NightRunItemView, NightRunOutcomeView } from '../api/nightRuns'

/**
 * Der Rueckfalltext, den der Server selbst setzt, wenn ein Lauf ohne Arbeit keinen Grund meldet
 * (`NightRunOutcome.GRUND_UNBEKANNT`). Er ist der **einzige** Grund, der seit Issue #1121 noch
 * `FAILED` ergibt; jeder gemeldete Grund ist `NO_WORK`.
 *
 * Er steht hier und nicht im Produktionscode: Der Browser liest den Ausgang und muss den Text nie
 * wiedererkennen — nur dieses Test-Double des Servers muss es, weil es dessen Regel nachbildet.
 */
export const GRUND_UNBEKANNT = 'Nichts abgearbeitet — Grund unbekannt'

/**
 * Der Befund, den der Server zu einem Lauf schicken wuerde (Issue #1081).
 *
 * **Warum das hier steht und nicht im Produktionscode:** Seit Issue #1078 rechnet der Browser den
 * Massstab nicht mehr — er liest ihn. Fixtures, die einen Lauf beschreiben, muessen ihn aber
 * mitliefern, sonst behaupten sie etwas anderes als ihre eigenen Pakete. Diese Funktion ist genau
 * dafuer da: ein **Test-Double des Servers**, damit ein Szenario „Lauf mit rotem Paket" nicht von
 * Hand einen dazu passenden Befund tragen muss und beides auseinanderlaufen kann.
 *
 * Sie bildet `NightRunOutcome.of` nach — dieselbe Reihenfolge: „verstummt" schlaegt alles, dann
 * „laeuft noch", dann der Lauf ohne Arbeit, dann rot vor gelb vor grau-mit-Fehlerklasse. Weicht sie
 * einmal ab, faellt das an den Tests auf, die den Server ueber MockMvc pruefen (`NightRunIT`): Dort
 * steht der echte Vertrag.
 *
 * <p>Der Lauf ohne Arbeit zerfaellt seit Issue #1121 in zwei Faelle: ein **gemeldeter** Grund ergibt
 * `NO_WORK`, allein der Rueckfall {@link GRUND_UNBEKANNT} bleibt `FAILED`.
 *
 * <p>Die Stille kommt als **Angabe** herein und nicht als Zeitrechnung aus `startedAt`, `updatedAt`
 * und einer Frist (Issue #1091): Ein Szenario sagt hier, ob der Lauf verstummt ist; die Frist selbst
 * gehoert dem Server, und sie hier nachzurechnen hiesse, eine zweite Uhr in die Fixtures zu holen.
 */
export function serverBefund(lauf: {
  complete: boolean
  noWorkReason?: string | null
  items: readonly NightRunItemView[]
  /** Ob der Lauf ueber die Stillefrist hinaus kein Lebenszeichen gab (Issue #1091). */
  verstummt?: boolean
}): NightRunOutcomeView {
  // Ein verstummter Lauf ist nicht gelungen — ohne massgebliches Paket und ohne Grund, denn er hat
  // sein Ergebnis nie gemeldet.
  if (!lauf.complete && lauf.verstummt === true) {
    return { verdict: 'FAILED', decisiveItem: null, noWorkReason: null }
  }
  if (!lauf.complete) {
    return { verdict: 'RUNNING', decisiveItem: null, noWorkReason: null }
  }
  if (lauf.noWorkReason != null && lauf.noWorkReason !== '') {
    return {
      verdict: lauf.noWorkReason === GRUND_UNBEKANNT ? 'FAILED' : 'NO_WORK',
      decisiveItem: null,
      noWorkReason: lauf.noWorkReason,
    }
  }
  const massgeblich =
    lauf.items.find((i) => i.state === 'RED') ??
    lauf.items.find((i) => i.state === 'YELLOW') ??
    lauf.items.find((i) => i.state === 'GREY' && i.errorClass != null)
  if (massgeblich == null) {
    return { verdict: 'SUCCEEDED', decisiveItem: null, noWorkReason: null }
  }
  return {
    verdict: massgeblich.state === 'GREY' ? 'WAITING' : 'FAILED',
    decisiveItem: {
      cardNumber: massgeblich.cardNumber,
      state: massgeblich.state,
      errorClass: massgeblich.errorClass,
    },
    noWorkReason: null,
  }
}
