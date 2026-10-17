import type { EpicForecastEntry, EpicForecastPendingDocument } from '../api/dashboard'
import { formatierer } from '../i18n/format'
import { woerterbuch, type Sprache } from '../i18n/woerterbuch'

/**
 * Reine Textlogik der Prognosezeile in der Platte „Vorhaben“ (Issue #1828, Plan #1819 A7): Die
 * Wortwahl hängt an der Sprache des Lesers, der Server liefert nur Lage, Zählung und Tage.
 */

/**
 * Ein Tag `JJJJ-MM-TT` als lokale Mitternacht (E13). `new Date('JJJJ-MM-TT')` wäre Mitternacht UTC
 * und zeigte westlich von UTC den Vortag.
 */
export function lokalesDatum(tag: string): Date {
  const [jahr, monat, tagImMonat] = tag.split('-').map(Number)
  return new Date(jahr, monat - 1, tagImMonat)
}

/** Ein Tag `JJJJ-MM-TT` im Datumsformat der Sprache. */
export function datumText(sprache: Sprache, tag: string): string {
  return formatierer(sprache).datum(lokalesDatum(tag))
}

/** „1 offenes Paket“ bzw. „‹n› offene Pakete“ (E9). */
export function paketZahl(sprache: Sprache, offen: number): string {
  return woerterbuch(sprache).LEITSTAND.PROGNOSE_OFFENE_PAKETE(offen)
}

/** Die ausstehenden Dokumente aufsteigend nach Nummer als „#‹Nummer› ‹Titel›“, mit Komma getrennt (E7). */
export function dokumentListe(dokumente: readonly EpicForecastPendingDocument[]): string {
  return [...dokumente]
    .sort((a, b) => a.number - b.number)
    .map((d) => `#${d.number} ${d.title}`)
    .join(', ')
}

/** Die beiden Daten, sofern das Vorhaben eine Prognose trägt; sonst `null`. */
function daten(sprache: Sprache, v: EpicForecastEntry): { p50: string; p85: string } | null {
  if (v.state !== 'FORECAST' || v.p50 === null || v.p85 === null) return null
  return { p50: datumText(sprache, v.p50), p85: datumText(sprache, v.p85) }
}

/** Die Zeile unter dem Vorhaben, je Lage (AK 1, 5, 6, 11, 12). */
export function zeilenText(sprache: Sprache, v: EpicForecastEntry): string {
  const t = woerterbuch(sprache).LEITSTAND
  if (v.state === 'NO_PACKAGES') return t.PROGNOSE_KEINE_PAKETE
  if (v.state === 'ALL_DONE') {
    return v.pendingDocuments.length === 0
      ? t.PROGNOSE_ALLE_UMGESETZT
      : t.PROGNOSE_ALLE_UMGESETZT_WEITERE(dokumentListe(v.pendingDocuments))
  }
  const d = daten(sprache, v)
  return d === null ? t.PROGNOSE_ZU_WENIG_DURCHSATZ : t.PROGNOSE_ZEILE(d.p50, d.p85, v.earliest)
}

/**
 * Der Hinweis beim Überfahren der Prognose (AK 6, 7, 8): Erklärung, Annahme, weitere Vorhaben und
 * ausstehende Dokumente. Ohne Daten gibt es keinen Hinweis.
 */
export function hinweisText(sprache: Sprache, v: EpicForecastEntry): string | null {
  const d = daten(sprache, v)
  if (d === null) return null
  const t = woerterbuch(sprache).LEITSTAND
  const saetze = [t.PROGNOSE_ERKLAERUNG(d.p50, d.p85, v.open, v.earliest), t.PROGNOSE_NUR_DIESES]
  if (v.otherEpicsSharing > 0) saetze.push(t.PROGNOSE_WEITERE_VORHABEN(v.otherEpicsSharing))
  if (v.pendingDocuments.length > 0) saetze.push(t.PROGNOSE_AUSSTEHEND(dokumentListe(v.pendingDocuments)))
  return saetze.join(' ')
}
