/**
 * Löst CSS-Variablen-Verweise `var(--name)` und `var(--name, Rückfall)` in einem beliebig
 * verschachtelten Wert zu festen Werten auf: zuerst aus `werte`, sonst aus dem Rückfallwert. Ein
 * Verweis ohne beides bleibt stehen. Das Original bleibt unverändert, Funktionen laufen durch.
 *
 * **Wozu (#954):** Die Nachtlauf-Auswertung übernimmt die Komponenten-Vorgaben des Leitstands.
 * Seit #951 tragen diese Variablen, die an `:root` hängen und per Media-Query ins Dunkle schalten —
 * auch in Menüs und Popovern, die in einem Portal außerhalb der Seite landen. Die Ausnahme bleibt
 * nur hell, wenn die übernommenen Vorgaben feste Hellwerte tragen.
 */
export function variablenAufloesen<T>(wert: T, werte: Readonly<Record<string, string>>): T {
  if (typeof wert === 'string') {
    return aufloesenIn(wert, werte) as T
  }
  if (Array.isArray(wert)) {
    return wert.map((eintrag: unknown) => variablenAufloesen(eintrag, werte)) as T
  }
  if (wert !== null && typeof wert === 'object') {
    return Object.fromEntries(
      Object.entries(wert).map(([schluessel, eintrag]) => [schluessel, variablenAufloesen(eintrag, werte)]),
    ) as T
  }
  return wert
}

/** Ersetzt jeden `var(…)`-Verweis einer Zeichenkette; Klammern im Rückfallwert werden mitgezählt. */
function aufloesenIn(text: string, werte: Readonly<Record<string, string>>): string {
  let ergebnis = ''
  let rest = text
  let beginn = rest.indexOf('var(')
  while (beginn !== -1) {
    ergebnis += rest.slice(0, beginn)
    let tiefe = 1
    let ende = beginn + 4
    for (; tiefe > 0; ende++) {
      if (rest[ende] === '(') tiefe++
      if (rest[ende] === ')') tiefe--
    }
    const inhalt = rest.slice(beginn + 4, ende - 1)
    const komma = inhalt.indexOf(',')
    const name = (komma === -1 ? inhalt : inhalt.slice(0, komma)).trim()
    const rueckfall = komma === -1 ? undefined : inhalt.slice(komma + 1).trim()
    ergebnis += werte[name] ?? rueckfall ?? rest.slice(beginn, ende)
    rest = rest.slice(ende)
    beginn = rest.indexOf('var(')
  }
  return ergebnis + rest
}
