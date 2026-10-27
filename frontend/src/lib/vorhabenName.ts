import type { KitArt } from '../api/kitNamen'

/** Präfix, mit dem der Namensvorschlag eines Vorhabens beginnt (Issue #1932). */
export const VORHABEN_PRAEFIX = '[Epic]'

/**
 * Namensvorschlag für „Vorgang eröffnen" (Issue #1932): Ein führendes Präfix der Art `FACHLICH`
 * (`[Fachlich]`, `[Requirement]`) wird durch `[Epic]` ersetzt — das neue Vorhaben ist kein
 * Requirement. Die Präfixe kommen vom Server (Plan #1582, E10); solange sie fehlen oder keines
 * führend steht, bleibt der Kartentitel unverändert, denn ein Titel ohne Präfix soll kein
 * erfundenes bekommen. Schreibung und führender Leerraum wie bei `artAusTitel`.
 */
export function vorhabenNameVorschlag(praefixe: readonly KitArt[] | undefined, titel: string): string {
  const fachlich = praefixe?.find((a) => a.art === 'FACHLICH')
  const rest = titel.trimStart()
  const praefix = fachlich?.namen.find(
    (n) => rest.slice(0, n.length).toLowerCase() === n.toLowerCase(),
  )
  return praefix === undefined ? titel : VORHABEN_PRAEFIX + rest.slice(praefix.length)
}
