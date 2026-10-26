/**
 * Höhe eines Bereichs, der von seiner Oberkante bis zum unteren Fensterrand reicht (Issue #1901):
 * der Rest des Fensters unter `oberkante`, nie weniger als `minimum`. Alle Werte in Pixeln, die
 * Oberkante gemessen vom Anfang des Dokuments.
 */
export function restHoehe(oberkante: number, fensterHoehe: number, minimum: number): number {
  return Math.max(fensterHoehe - oberkante, minimum)
}
