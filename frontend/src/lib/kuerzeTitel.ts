/**
 * Kürzt einen Titel auf höchstens `max` Zeichen und hängt dann „…" an (Plan #1980, E9). Gezählt
 * werden Unicode-Codepunkte, nicht UTF-16-Einheiten: Ein Emoji zählt als ein Zeichen, und kein
 * Ersatzpaar wird an der Schnittstelle zerteilt.
 */
export function kuerzeTitel(titel: string, max: number): string {
  const zeichen = Array.from(titel)
  return zeichen.length <= max ? titel : zeichen.slice(0, max).join('') + '…'
}
