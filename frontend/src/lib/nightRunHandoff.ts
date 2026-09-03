import type { NightRunErrorClass, NightRunState } from './nightRunLog'

/**
 * Der Uebernahmetext eines Arbeitspakets (Issue #727, Plan #718).
 *
 * <p>Die Auswertung sagt, wo hinzusehen ist; die Ursache wird in der Entwicklungsumgebung gesucht.
 * Dieses Modul baut die Bruecke — einen fertigen Text zum Uebernehmen. Der Leitstand bekommt
 * bewusst **keinen eingebauten KI-Assistenten** (Nicht-Ziel aus #715): Er formuliert die Frage,
 * beantwortet wird sie anderswo.
 *
 * <p><b>Der Text wird nicht gespeichert.</b> Er entsteht zur Anzeigezeit als reine Funktion aus dem
 * angezeigten Arbeitspaket — ohne React, ohne Netzwerk, ohne Datum und ohne Zufall. Genau deshalb
 * ist er ohne Oberflaeche testbar; dasselbe Muster wie `listSort.ts` und `nightRunLog.ts`.
 *
 * <p><b>Die Beschriftungen liegen hier</b> und nicht in `NightRunPage.tsx`: Die Signatur nimmt nur
 * das Arbeitspaket entgegen, die Funktion muss die Texte also selbst kennen. Zwei Quellen fuer
 * dieselbe Beschriftung liefen auseinander, und dann sagte der Uebernahmetext etwas anderes als die
 * Anzeige daneben. Die Seite importiert sie von hier.
 */

/**
 * Das Arbeitspaket in der Anzeigeform, so weit der Uebernahmetext es braucht. `NightRunPage` haelt
 * genau diesen Ausschnitt in der Hand — frisch geparste und aufbewahrte Laeufe sehen dort gleich
 * aus (#725), der Text kennt den Unterschied also gar nicht erst.
 */
export interface NightRunHandoffItem {
  /** Projektweite Kartennummer des Arbeitspakets. */
  cardNumber: number
  /** Titel zum Zeitpunkt des Laufs; leer, wenn die Protokollzeile ihn nicht trug. */
  title: string
  state: NightRunState
  errorClass: NightRunErrorClass | undefined
  /** Die Protokollzeile, die den Zustand begruendet; fehlt, wenn der Lauf keine nannte. */
  excerpt: string | undefined
}

/** Die Beschriftung je Zustand — Text traegt die Aussage, nicht nur die Farbe. */
export const NIGHT_RUN_STATE_TEXT: Record<NightRunState, string> = {
  GREEN: 'Erfolg',
  YELLOW: 'Erfolg, Prüfung rot',
  RED: 'gescheitert',
  GREY: 'nicht bearbeitet',
}

/**
 * Die Beschriftung je Fehlerklasse. Der Typ ist `Record<NightRunErrorClass, string>` und **nicht**
 * `Partial`: Die Liste der Klassen ist abgeschlossen und lebt in `nightRunLog.ts` (Plan #718, A13);
 * kaeme dort eine hinzu, braeche hier der Build, statt dass eine Klasse stumm ohne Beschriftung
 * erschiene. Deshalb zaehlt diese Datei die Klassen auch nirgends selbst auf.
 */
export const NIGHT_RUN_ERROR_CLASS_TEXT: Record<NightRunErrorClass, string> = {
  CHECKS_RED: 'Prüfungen rot',
  CHECKS_NOT_STARTED: 'Prüfungen nicht gelaufen',
  DEPENDENCY_UNMET: 'Abhängigkeit offen',
  UNEXPECTED_STATE: 'Unerwarteter Zustand',
  HARD_ABORT: 'Harter Abbruch',
  AWAITING_DECISION: 'Wartet auf Entscheidung',
  REVIEWER_FAILED: 'Prüf-Session gescheitert',
}

/**
 * Der Text zu einem Arbeitspaket — `null`, wenn keiner entsteht.
 *
 * <p>Nur ein **gelbes oder rotes** Arbeitspaket bekommt einen: Zu einem gruenen gibt es nichts zu
 * uebernehmen, und ein graues traegt zwar eine Fehlerklasse (offene Abhaengigkeit), ist aber kein
 * Befund.
 *
 * <p>Fehlende Angaben lassen ihre Zeile weg, statt sie leer oder mit `undefined` zu schreiben: Ein
 * Text, der `Auszug: undefined` in eine fremde Sitzung traegt, behauptet dort etwas Falsches.
 */
export function buildHandoffText(item: NightRunHandoffItem): string | null {
  if (item.state !== 'YELLOW' && item.state !== 'RED') {
    return null
  }
  const karte = item.title === '' ? `#${item.cardNumber}` : `#${item.cardNumber} ${item.title}`
  const zeilen = [`Nachtlauf-Befund zu Karte ${karte}`, `Zustand: ${NIGHT_RUN_STATE_TEXT[item.state]}`]

  if (item.errorClass !== undefined) {
    zeilen.push(`Fehlerklasse: ${NIGHT_RUN_ERROR_CLASS_TEXT[item.errorClass]}`)
  }
  // Truthiness statt `!== undefined`: Ein leerer Auszug ergaebe eine Zeile, die nichts sagt.
  if (item.excerpt) {
    zeilen.push(`Auszug: ${item.excerpt}`)
  }
  return zeilen.join('\n')
}
