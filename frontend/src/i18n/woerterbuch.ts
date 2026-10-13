import { de, type Woerterbuch } from './de'
import { en } from './en'

/** Die Sprachen der Oberfläche; die Codes sind die von `GET /api/language`. */
export const SPRACHEN = ['de', 'en'] as const

export type Sprache = (typeof SPRACHEN)[number]

const WOERTERBUECHER: Readonly<Record<Sprache, Woerterbuch>> = { de, en }

/** Das Wörterbuch einer Sprache. */
export function woerterbuch(sprache: Sprache): Woerterbuch {
  return WOERTERBUECHER[sprache]
}

/** Ob ein Wert einer der Sprachcodes ist. */
export function istSprache(wert: unknown): wert is Sprache {
  return SPRACHEN.some((sprache) => sprache === wert)
}
