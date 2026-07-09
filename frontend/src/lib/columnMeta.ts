/** Ob eine Spalte fachlich "Done" ist (aus dem Namen). */
export const isDoneColumn = (name: string): boolean => name.toLowerCase().includes('done')
