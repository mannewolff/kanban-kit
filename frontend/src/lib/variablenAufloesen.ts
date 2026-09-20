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

/**
 * Ersetzt jeden `var(…)`-Verweis einer Zeichenkette; Klammern im Rückfallwert werden mitgezählt.
 *
 * Fehlt die schließende Klammer, bleibt der unverarbeitete Rest unverändert stehen (#977). Ohne den
 * Abbruch am Textende zählte die Klammertiefe nie herunter und die Suche lief endlos — die Funktion
 * läuft beim Modulimport, ein Tippfehler im Theme fröre damit die Seite ein.
 */
function aufloesenIn(text: string, werte: Readonly<Record<string, string>>): string {
  let ergebnis = ''
  let rest = text
  let beginn = rest.indexOf('var(')
  while (beginn !== -1) {
    const ende = verweisEnde(rest, beginn)
    if (ende === -1) break
    ergebnis += rest.slice(0, beginn)
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

/**
 * Index hinter der schließenden Klammer des `var(`-Verweises, der bei `beginn` steht; Klammern im
 * Rückfallwert zählen mit. Fehlt die schließende Klammer, ist das Ergebnis `-1`.
 */
function verweisEnde(text: string, beginn: number): number {
  let tiefe = 1
  for (let ende = beginn + 4; ende < text.length; ende++) {
    if (text[ende] === '(') tiefe++
    if (text[ende] === ')') tiefe--
    if (tiefe === 0) return ende + 1
  }
  return -1
}
