import type { NightRunItemView, NightRunOutcomeView } from '../api/nightRuns'

/**
 * Der Befund, den der Server zu einem Lauf schicken wuerde (Issue #1081).
 *
 * **Warum das hier steht und nicht im Produktionscode:** Seit Issue #1078 rechnet der Browser den
 * Massstab nicht mehr — er liest ihn. Fixtures, die einen Lauf beschreiben, muessen ihn aber
 * mitliefern, sonst behaupten sie etwas anderes als ihre eigenen Pakete. Diese Funktion ist genau
 * dafuer da: ein **Test-Double des Servers**, damit ein Szenario „Lauf mit rotem Paket" nicht von
 * Hand einen dazu passenden Befund tragen muss und beides auseinanderlaufen kann.
 *
 * Sie bildet `NightRunOutcome.of` nach — dieselbe Reihenfolge: „laeuft noch" schlaegt alles, dann
 * der Lauf ohne Arbeit, dann rot vor gelb vor grau-mit-Fehlerklasse. Weicht sie einmal ab, faellt
 * das an den Tests auf, die den Server ueber MockMvc pruefen (`NightRunIT`): Dort steht der echte
 * Vertrag.
 */
export function serverBefund(lauf: {
  complete: boolean
  noWorkReason?: string | null
  items: readonly NightRunItemView[]
}): NightRunOutcomeView {
  if (!lauf.complete) {
    return { verdict: 'RUNNING', decisiveItem: null, noWorkReason: null }
  }
  if (lauf.noWorkReason != null && lauf.noWorkReason !== '') {
    return { verdict: 'FAILED', decisiveItem: null, noWorkReason: lauf.noWorkReason }
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
