/**
 * Formatiert eine Dauer in Sekunden menschenlesbar auf Deutsch. `null` (keine Datenbasis) wird zu
 * „n. v.". Grobkörnig: ab einem Tag „T + Std", ab einer Stunde „Std + Min", darunter „Min" bzw. „s".
 */
export function formatDuration(seconds: number | null): string {
  if (seconds == null) {
    return 'n. v.'
  }
  const total = Math.round(seconds)
  if (total < 60) {
    return `${total} s`
  }
  const minutes = Math.floor(total / 60)
  if (minutes < 60) {
    return `${minutes} Min`
  }
  const hours = Math.floor(minutes / 60)
  if (hours < 24) {
    return `${hours} Std ${minutes % 60} Min`
  }
  const days = Math.floor(hours / 24)
  return `${days} T ${hours % 24} Std`
}
