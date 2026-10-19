import type { DeliveryWeek } from '../api/dashboard'
import { formatierer } from '../i18n/format'
import { woerterbuch, type Sprache } from '../i18n/woerterbuch'

/**
 * Kopf, Formate und Leerzustände der Platte „Auslieferung“ (Issue #1836, Plan #1831 E9, E10, E15).
 * Gerechnet wird im Backend; hier wird nur formatiert.
 */

const STUNDE = 3600
const TAG = 24 * STUNDE

/**
 * Der Kopftext der Platte: „seit ‹Datum›“, wenn die erste Meldung im Fenster liegt, sonst „letzte 12
 * Wochen“. `dataSince` ist ein Zeitpunkt; das Datum steht in der Zone des Lesers (E10).
 */
export function auslieferungKopf(dataSince: string | null, sprache: Sprache): string {
  const t = woerterbuch(sprache).LEITSTAND_AUSLIEFERUNG
  return dataSince === null ? t.LETZTE_WOCHEN : t.SEIT(formatierer(sprache).datum(dataSince))
}

/** Die Einheit der Deployment-Frequenz: „Auslieferungen in 12 Wochen“ bzw. „… seit ‹Datum›“. */
export function frequenzEinheit(anzahl: number, dataSince: string | null, sprache: Sprache): string {
  const t = woerterbuch(sprache).LEITSTAND_AUSLIEFERUNG
  return dataSince === null ? t.FREQUENZ_FENSTER(anzahl) : t.FREQUENZ_SEIT(anzahl, formatierer(sprache).datum(dataSince))
}

/** Eine Dauer-Kennzahl als Wert und Einheit: unter einem Tag Stunden, sonst Tage, je mit einer Stelle. */
export function kennzahlDauer(sekunden: number, sprache: Sprache): { wert: string; einheit: string } {
  const t = woerterbuch(sprache).LEITSTAND_AUSLIEFERUNG
  const zahl1 = (wert: number) => formatierer(sprache).zahl(wert, { minimumFractionDigits: 1, maximumFractionDigits: 1 })
  return sekunden < TAG
    ? { wert: zahl1(sekunden / STUNDE), einheit: t.STUNDEN }
    : { wert: zahl1(sekunden / TAG), einheit: t.TAGE }
}

/** Die Dauer eines laufenden Ausfalls in vollen Stunden bzw. Tagen; darunter „unter 1 Stunde“. */
export function ausfallDauer(sekunden: number, sprache: Sprache): string {
  const t = woerterbuch(sprache).LEITSTAND_AUSLIEFERUNG
  if (sekunden >= TAG) {
    return t.TAGE_ANZAHL(Math.floor(sekunden / TAG))
  }
  if (sekunden >= STUNDE) {
    return t.STUNDEN_ANZAHL(Math.floor(sekunden / STUNDE))
  }
  return t.UNTER_EINER_STUNDE
}

/**
 * „Ausfall läuft seit ‹Dauer›“ vom Beginn des laufenden Ausfalls bis `jetzt` (E9, AK 6); ohne
 * laufenden Ausfall `null`. Ein Beginn hinter der eigenen Uhr zählt als 0, nicht negativ.
 */
export function ausfallText(outageSince: string | null, jetzt: number, sprache: Sprache): string | null {
  if (outageSince === null) {
    return null
  }
  const sekunden = Math.max(0, (jetzt - Date.parse(outageSince)) / 1000)
  return woerterbuch(sprache).LEITSTAND_AUSLIEFERUNG.AUSFALL_LAEUFT(ausfallDauer(sekunden, sprache))
}

/** Die Change Failure Rate als ganze Prozent in der Schreibweise der Sprache. */
export function prozentWert(prozent: number, sprache: Sprache): string {
  return formatierer(sprache).zahl(Math.round(prozent))
}

/** Die Höhen einer Woche der Wochenleiste in Prozent der stärksten Woche. */
export interface WochenHoehe {
  erfolg: number
  fehlschlag: number
}

/**
 * Die Höhen je Woche: Erfolg und Fehlschlag in Prozent der Woche mit den meisten Versuchen. Eine
 * Woche ohne Versuch ist `null` und zeigt die Grundlinie statt eines Balkens (E11).
 */
export function wochenHoehen(wochen: readonly DeliveryWeek[]): (WochenHoehe | null)[] {
  const max = Math.max(0, ...wochen.map((w) => w.successCount + w.failureCount))
  return wochen.map((w) =>
    w.successCount + w.failureCount === 0
      ? null
      : { erfolg: Math.round((w.successCount / max) * 100), fehlschlag: Math.round((w.failureCount / max) * 100) },
  )
}
