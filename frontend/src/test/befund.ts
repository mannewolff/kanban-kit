import type { NightRunItemView, NightRunOutcomeView, NightRunServerMode } from '../api/nightRuns'

/**
 * Der Rueckfalltext, den der Server selbst setzt, wenn ein Lauf ohne Arbeit keinen Grund meldet
 * (`NightRunOutcome.GRUND_UNBEKANNT`).
 *
 * **Ein reiner Anzeigetext** seit Issue #1185: Am Wortlaut haengt keine Aussage ueber den Ausgang
 * mehr — jeder Grund ohne massgebliches Paket ist `NO_WORK`, gemeldet oder nicht. Er steht hier
 * weiterhin, weil Szenarien den Lauf ohne gemeldeten Grund nachbilden und den Text dann anzeigen.
 *
 * Er steht hier und nicht im Produktionscode: Der Browser liest den Ausgang und muss den Text nie
 * wiedererkennen — auch dieses Test-Double des Servers vergleicht ihn seit #1185 mit nichts mehr.
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
 * „laeuft noch", dann der harte Abbruch, dann rot vor gelb vor grau-mit-Fehlerklasse, **zuletzt** der
 * Lauf ohne Arbeit. Weicht sie einmal ab, faellt das an den Tests auf, die den Server ueber MockMvc
 * pruefen (`NightRunIT`): Dort steht der echte Vertrag.
 *
 * <p>Die Rangfolge stammt aus Issue #1185 und loest die von #1121 ab: Das massgebliche Paket geht
 * dem Grund vor — wer alle Pakete zurueckstellte, bleibt `WAITING` statt ueber den Rueckfall rot zu
 * werden. Ist keines massgeblich, ergibt **jeder** Grund `NO_WORK`, gemeldet oder als Rueckfall
 * {@link GRUND_UNBEKANNT}.
 *
 * <p>Die Stille kommt als **Angabe** herein und nicht als Zeitrechnung aus `startedAt`, `updatedAt`
 * und einer Frist (Issue #1091): Ein Szenario sagt hier, ob der Lauf verstummt ist; die Frist selbst
 * gehoert dem Server, und sie hier nachzurechnen hiesse, eine zweite Uhr in die Fixtures zu holen.
 *
 * <p>Die **Laufart** entscheidet seit Issue #1123 unter gleichrangigen Paketen: In einer Kette
 * (`CHAIN`) ist das letzte massgeblich, sonst das erste. Sie ist optional, weil ein Szenario ohne
 * gleichrangige Pakete sie nicht braucht; fehlt sie, gilt die Reihenfolge wie ausserhalb einer Kette.
 */
export function serverBefund(lauf: {
  complete: boolean
  noWorkReason?: string | null
  /** Grund eines harten Abbruchs (Issue #1143) — er schlaegt den Lauf ohne Arbeit und die Pakete. */
  abortReason?: string | null
  items: readonly NightRunItemView[]
  /** Ob der Lauf ueber die Stillefrist hinaus kein Lebenszeichen gab (Issue #1091). */
  verstummt?: boolean
  /** Laufart des Laufs (Issue #1123) — in einer Kette zaehlt das letzte gleichrangige Paket. */
  mode?: NightRunServerMode
}): NightRunOutcomeView {
  // Ein verstummter Lauf ist nicht gelungen — ohne massgebliches Paket und ohne Grund, denn er hat
  // sein Ergebnis nie gemeldet.
  if (!lauf.complete && lauf.verstummt === true) {
    return { verdict: 'FAILED', decisiveItem: null, noWorkReason: null, abortReason: null }
  }
  if (!lauf.complete) {
    return { verdict: 'RUNNING', decisiveItem: null, noWorkReason: null, abortReason: null }
  }
  // Einmal ausgewaehlt, zweimal gebraucht (Issue #1185): Der Abbruch-Zweig braucht dasselbe Paket
  // wie die Rangfolge darunter, und zweimal ausgewaehlt liefen die beiden auseinander.
  const massgeblich = massgeblichesPaket(lauf.mode, lauf.items)
  // Der abgebrochene Lauf (Issue #1143) steht hinter „verstummt" und „laeuft noch" und **vor** den
  // Paketen: Ein Lauf, der abbrach, ist nie gelungen — auch nicht nach drei gruenen Paketen. Das
  // massgebliche Paket bleibt daneben und kommt weiter aus den Paketen (E5).
  if (lauf.abortReason != null) {
    return {
      verdict: 'FAILED',
      decisiveItem: massgeblich,
      noWorkReason: null,
      abortReason: lauf.abortReason,
    }
  }
  // Das massgebliche Paket geht dem Grund vor (Issue #1185): Es ist die genauere Auskunft, und ein
  // Lauf, der alle Pakete zurueckstellte, meldet keinen Grund und bleibt so „mit Vorbehalt".
  if (massgeblich != null) {
    return {
      verdict: massgeblich.state === 'GREY' ? 'WAITING' : 'FAILED',
      decisiveItem: massgeblich,
      noWorkReason: null,
      abortReason: null,
    }
  }
  // Zuletzt der Lauf ohne Arbeit — **jeder** Grund ergibt `NO_WORK`, ohne Blick auf den Wortlaut.
  if (lauf.noWorkReason != null && lauf.noWorkReason !== '') {
    return {
      verdict: 'NO_WORK',
      decisiveItem: null,
      noWorkReason: lauf.noWorkReason,
      abortReason: null,
    }
  }
  return { verdict: 'SUCCEEDED', decisiveItem: null, noWorkReason: null, abortReason: null }
}

/**
 * Das Paket, das den Ausgang bestimmt — `null`, wenn keines ihn bestimmt.
 *
 * Eigene Funktion wie im Server seit Issue #1143: Der abgebrochene Lauf braucht dieselbe Auswahl,
 * aber ein anderes Urteil. Zweimal ausgeschrieben liefen die beiden Auswahlen auseinander.
 *
 * Die Ketten-Einheit steht immer zuerst und erbt ihren Abbruch von dem, was spaeter riss — in einer
 * Kette ist deshalb das letzte gleichrangige Paket massgeblich (Issue #1123).
 */
function massgeblichesPaket(
  mode: NightRunServerMode | undefined,
  items: readonly NightRunItemView[],
): NightRunOutcomeView['decisiveItem'] {
  const reihenfolge = mode === 'CHAIN' ? [...items].reverse() : items
  const massgeblich =
    reihenfolge.find((i) => i.state === 'RED') ??
    reihenfolge.find((i) => i.state === 'YELLOW') ??
    reihenfolge.find((i) => i.state === 'GREY' && i.errorClass != null)
  return massgeblich == null
    ? null
    : {
        cardNumber: massgeblich.cardNumber,
        state: massgeblich.state,
        errorClass: massgeblich.errorClass,
      }
}
