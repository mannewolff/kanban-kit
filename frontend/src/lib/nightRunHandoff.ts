import type { Verdict } from '../api/nightRuns'
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
  /**
   * Der Sitzungsstrom des Arbeitspakets, Zeile fuer Zeile wie der Parser ihn geschnitten hat
   * (`lib/nightRunLog.ts`). Optional, weil ihn nur die gerade geparste Sitzung hat: Ein vom
   * Server geladener Lauf traegt ihn nicht (Plan #744, A5).
   */
  rawLines?: readonly string[]
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
  TIME_BUDGET_EXCEEDED: 'Zeitbudget erschöpft',
}

/**
 * Das Wort je Ausgang eines Laufs (Issue #1096, Kriterien 10 und 11 der fachlichen Quelle #1086).
 *
 * <p>Der Ausgang muss **ohne Farbwahrnehmung** zu erkennen sein; dafuer braucht jeder Melder ein
 * Wort neben sich. `RUNNING` steht mit in der Tabelle, weil auch die laufende Zeile eine
 * Textangabe braucht (Kriterium 3) — Puls und Stahl allein sagen es nicht.
 *
 * <p><b>`Record<Verdict, string>` und nicht `Partial`</b> — aus demselben Grund wie bei
 * {@link NIGHT_RUN_ERROR_CLASS_TEXT}: Kaeme ein fuenfter Ausgang hinzu, braeche der Build, statt
 * dass er stumm als leerer Text erschiene.
 *
 * <p>Die Woerter leben hier und nicht im Server (Plan #1088): Der Befund kommt als Daten, nicht als
 * fertiger Satz — so steht es schon im Klassenkommentar von `NightRunOutcome`.
 *
 * <p>`WAITING` heisst „mit Vorbehalt": der Lauf, dessen massgebliches Paket grau mit Fehlerklasse
 * ist — zurueckgestellt oder auf einen Menschen wartend. Nicht zu verwechseln mit
 * `NightRunState.YELLOW`, das auf `FAILED` abbildet.
 */
export const NIGHT_RUN_VERDICT_TEXT: Record<Verdict, string> = {
  SUCCEEDED: 'gelungen',
  FAILED: 'nicht gelungen',
  WAITING: 'mit Vorbehalt',
  RUNNING: 'läuft',
}

/**
 * Wo die Fehlerklasse den Zustand genauer sagt als die Ampel allein (Issue #856).
 *
 * <p>`NIGHT_RUN_STATE_TEXT.YELLOW` lautet „Erfolg, Prüfung rot" — an einem am Zeitbudget beendeten
 * Vorgang waere das eine Falschaussage: Die Pruefung war nicht rot, sie kam gar nicht dran. Diese
 * Tabelle ueberschreibt den Zustandstext deshalb je Fehlerklasse.
 *
 * <p><b>Beide Ebenen sind `Partial`</b> — anders als {@link NIGHT_RUN_ERROR_CLASS_TEXT}, das jede
 * Klasse fuehren muss: Ein Eintrag hier ist die Ausnahme, und der Regelfall ist der Rueckfall auf
 * {@link NIGHT_RUN_STATE_TEXT}. Eine neue Fehlerklasse braucht hier nichts; sie faellt zurueck.
 *
 * <p>`TIME_BUDGET_EXCEEDED` traegt nur Gelb und Rot: Das sind die beiden Zustaende, die
 * `deuteKettenAusgang` (`lib/nightRunErgebnisstand.ts`) zu dieser Klasse ueberhaupt vergibt — gelb,
 * wenn bis zum Abbruch ein Dokument entstand, rot ohne.
 */
export const NIGHT_RUN_STATE_TEXT_BY_ERROR_CLASS: Partial<
  Record<NightRunErrorClass, Partial<Record<NightRunState, string>>>
> = {
  TIME_BUDGET_EXCEEDED: {
    YELLOW: 'Am Zeitbudget beendet, Ergebnis liegt vor',
    RED: 'Am Zeitbudget beendet, ohne Ergebnis',
  },
}

/**
 * Der Zustandstext neben der Ampel und im Uebernahmetext — die eine Quelle fuer beide.
 *
 * <p>Zuerst {@link NIGHT_RUN_STATE_TEXT_BY_ERROR_CLASS}, sonst {@link NIGHT_RUN_STATE_TEXT}. Der
 * Rueckfall ist wertgleich mit dem frueheren direkten Zugriff: Fuer jede Kombination ausser den
 * beiden neuen aendert sich nichts (AK 9 aus Issue #842).
 */
export function nightRunZustandsText(
  state: NightRunState,
  errorClass: NightRunErrorClass | undefined,
): string {
  const jeKlasse = errorClass === undefined ? undefined : NIGHT_RUN_STATE_TEXT_BY_ERROR_CLASS[errorClass]
  return jeKlasse?.[state] ?? NIGHT_RUN_STATE_TEXT[state]
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
 *
 * <p>Das Rohprotokoll haengt als letzter Abschnitt an — **woertlich**, ohne Trim und ohne
 * Filterung (Plan #744, A6). Der Auszug nennt die eine begruendende Zeile, das Rohprotokoll den
 * ganzen Strom; wer ihn in der Entwicklungsumgebung liest, braucht ihn so, wie er lief. Gebaut
 * wird er nur hier: Eine zweite Textbaustelle formatierte frueher oder spaeter anders als das,
 * was tatsaechlich kopiert wird.
 */
export function buildHandoffText(item: NightRunHandoffItem): string | null {
  if (item.state !== 'YELLOW' && item.state !== 'RED') {
    return null
  }
  const karte = item.title === '' ? `#${item.cardNumber}` : `#${item.cardNumber} ${item.title}`
  const zeilen = [
    `Lauf-Befund zu Karte ${karte}`,
    `Zustand: ${nightRunZustandsText(item.state, item.errorClass)}`,
  ]

  if (item.errorClass !== undefined) {
    zeilen.push(`Fehlerklasse: ${NIGHT_RUN_ERROR_CLASS_TEXT[item.errorClass]}`)
  }
  // Truthiness statt `!== undefined`: Ein leerer Auszug ergaebe eine Zeile, die nichts sagt.
  if (item.excerpt) {
    zeilen.push(`Auszug: ${item.excerpt}`)
  }
  if (item.rawLines !== undefined && item.rawLines.length > 0) {
    zeilen.push('Rohprotokoll:', ...item.rawLines)
  }
  return zeilen.join('\n')
}
