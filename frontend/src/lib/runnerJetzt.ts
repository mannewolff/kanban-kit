import type { Woerterbuch } from '../i18n/de'

/** Die Texte des Bereichs „Runner jetzt“ in der gewählten Sprache. */
export type RunnerJetztTexte = Woerterbuch['RUNNER_JETZT']

/**
 * Der Name einer Stufe, wie der Runner sie meldet (`PLAN` … `VORBEREITUNG`); einen unbekannten
 * Code zeigt die Anzeige wörtlich (Plan #1724 E5).
 */
export function stufenname(t: RunnerJetztTexte, stage: string): string {
  return stage in t.STUFE ? t.STUFE[stage as keyof RunnerJetztTexte['STUFE']] : stage
}

/**
 * Der Satz für „verstummt“ (Fachplan #1721): Zeit der letzten Meldung und die zuletzt begonnene
 * Stufe, ohne gemeldete Stufe „nicht gemeldet“. Eine Stelle für die Runner-Seite und den
 * Kartendialog, damit der Wortlaut an beiden derselbe ist (Issue #1729, #1730).
 */
export function verstummtSatz(t: RunnerJetztTexte, zeit: string, stage: string | null): string {
  return t.VERSTUMMT(zeit, stage === null ? t.NICHT_GEMELDET : stufenname(t, stage))
}
