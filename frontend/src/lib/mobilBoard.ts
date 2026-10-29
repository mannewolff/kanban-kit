/**
 * Das Standard-Board der mobilen Oberfläche, je Gerät und je Konto (Plan #1989, E5). Es steht im
 * `localStorage` und nicht am Konto: Ein anderes Handy hat seine eigene Wahl.
 *
 * Ein gesperrter oder defekter Speicher lässt die Seite nicht scheitern — dann gibt es eben keine
 * gemerkte Wahl, und die Oberfläche fragt neu.
 */

/** Der Schlüssel der Wahl eines Kontos; das Wertformat ist die Board-ID als Dezimalzahl. */
export function mobilBoardSchluessel(userId: number): string {
  return `manban.mobilBoard.${userId}`
}

/** Das gemerkte Board des Kontos; `null` ohne Wahl, bei unbrauchbarem Eintrag oder ohne Speicher. */
export function leseMobilBoard(userId: number): number | null {
  try {
    const wert = Number(localStorage.getItem(mobilBoardSchluessel(userId)))
    return Number.isInteger(wert) && wert > 0 ? wert : null
  } catch {
    return null
  }
}

/** Merkt sich das Board des Kontos auf diesem Gerät. */
export function schreibeMobilBoard(userId: number, boardId: number): void {
  try {
    localStorage.setItem(mobilBoardSchluessel(userId), String(boardId))
  } catch {
    // localStorage nicht verfügbar
  }
}

/** Vergisst das Board des Kontos, etwa wenn es das Board nicht mehr gibt. */
export function vergissMobilBoard(userId: number): void {
  try {
    localStorage.removeItem(mobilBoardSchluessel(userId))
  } catch {
    // localStorage nicht verfügbar
  }
}
