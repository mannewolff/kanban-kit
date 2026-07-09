/** Farbe für eine Spalte, abgeleitet aus ihrem Namen (portiert aus der Toolbox). */
export function columnColor(name: string): string {
  const n = name.toLowerCase()
  if (n.includes('done')) return '#2f6f4f'
  if (n.includes('progress')) return '#1565c0'
  if (n.includes('review')) return '#b5651d'
  return '#6b7280'
}
