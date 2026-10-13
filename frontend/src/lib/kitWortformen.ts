/**
 * Die Wortformen des Kits, die der Leitstand liest — deutsch und englisch, an genau einem Ort
 * (Issue #1592, Plan #1582, A5).
 *
 * <p>Der Leitstand muss die englischen Formen verstehen, bevor das Kit sie zum ersten Mal schreibt
 * (Plan #1582, E1). Die englischen Formen sind darum eine Vorlage fuer Kit-Plan 1420 und keine
 * Abschrift; weicht das Kit ab, gleicht ein `[Task]` diese Datei an. Ausgenommen sind die Anker
 * des Laufstands: Sie folgen dem Namen-Register des Kits (`MARKER`, Issue #1599).
 *
 * <p>Titel-Praefixe und Labelnamen stehen hier nicht — sie kommen vom Server (A4). Die einzige
 * Ausnahme ist `kit:klaeren|kit:clarify` in der Protokollzeile des Ueberspringens: Der
 * Textprotokoll-Parser ist rein und deutet eine hochgeladene Datei ohne Serverzugriff (A5).
 *
 * <p><b>Beide Formen einer Regel tragen dieselben Gruppen in derselben Reihenfolge</b> — die
 * Deutung in `nightRunLog.ts` liest sie, ohne die Sprache zu kennen.
 */

/** Eine Regel in beiden Sprachen; eine sprachneutrale Zeile traegt zweimal dasselbe Muster. */
export interface Wortform {
  readonly de: RegExp
  readonly en: RegExp
}

/** Wendet eine Regel an: zuerst die deutsche, dann die englische Form. */
export function passe(form: Wortform, text: string): RegExpExecArray | null {
  return form.de.exec(text) ?? form.en.exec(text)
}

/** Minutenangabe des Runners, mit oder ohne Nachkommastelle (`nach 8.8 min`). */
const MIN = String.raw`(\d+(?:\.\d+)?)`

/** Die Labelnamen der offenen Entscheidung — Ausnahme von A4, siehe Dateikopf. */
const KLAEREN = 'kit:klaeren|kit:clarify'

/**
 * Abschnitt Protokoll: die Zeilen des Textprotokolls von `night.mjs`, ohne Zeitstempel-Praefix.
 * Laufstand-Anker, Budgetfelder und Ergebnisdatei-Gruende stehen in eigenen Abschnitten weiter unten.
 */
export const PROTOKOLL = {
  // --- Rahmen eines Laufs
  /** Ein Lauf beginnt — Gruppe 1 ist der Modus. */
  start: { de: /^Nacht-Runner startet \(Modus (\w+)/, en: /^Night runner starting \(mode (\w+)/ },
  /** Abschluss eines Laufs; Gruppe 1 ist gesetzt, wenn es ein Probelauf war. */
  abschluss: {
    de: /^(?:Nacht-Runner beendet|Nacht-Review beendet|(Dry-Run beendet))/,
    en: /^(?:Night runner finished|Night review finished|(Dry run finished))/,
  },
  /** Die Pruefstufe in der Abschlusszeile — Gruppe 1. */
  stufe: { de: /beendet \(Stufe ([^)]+)\)/, en: /finished \(stage ([^)]+)\)/ },
  /** Der zweite Schreiber: `fail()` schreibt ohne Praefix und beendet den Lauf. */
  fehler: { de: /^Fehler: /, en: /^Error: / },

  // --- Implementierungs-Lauf, Ausgaenge je Arbeitspaket
  /** Gruppen: Minuten, Commit, Kartennummer. */
  erfolg: {
    de: new RegExp(String.raw`^ {2}Erfolg nach ${MIN} min, Commit ([0-9a-f]{7,40}), Issue #(\d+) in In review\.`),
    en: new RegExp(String.raw`^ {2}Success after ${MIN} min, commit ([0-9a-f]{7,40}), issue #(\d+) in In review\.`),
  },
  /** Gruppen: Commit, Kartennummer. */
  salvageErfolg: {
    de: /^ {2}Salvage erfolgreich, Commit ([0-9a-f]{7,40}), Issue #(\d+) in In review\./,
    en: /^ {2}Salvage succeeded, commit ([0-9a-f]{7,40}), issue #(\d+) in In review\./,
  },
  /** Gruppen: Minuten, Kartennummer. */
  fehlschlagDirty: {
    de: new RegExp(String.raw`^ {2}FEHLSCHLAG nach ${MIN} min: Issue #(\d+) nicht in In review UND Working Tree dirty`),
    en: new RegExp(String.raw`^ {2}FAILURE after ${MIN} min: issue #(\d+) not in In review AND working tree dirty`),
  },
  /** Gruppen: Minuten, Kartennummer. */
  fehlschlagNichts: {
    de: new RegExp(String.raw`^ {2}Fehlschlag nach ${MIN} min: Issue #(\d+) — die Session hat nichts hinterlassen`),
    en: new RegExp(String.raw`^ {2}Failure after ${MIN} min: issue #(\d+) — the session left nothing behind`),
  },
  /** Gruppen: Minuten, Kartennummer. */
  fehlschlagSauber: {
    de: new RegExp(String.raw`^ {2}Fehlschlag nach ${MIN} min: Issue #(\d+) nicht in In review, Tree sauber`),
    en: new RegExp(String.raw`^ {2}Failure after ${MIN} min: issue #(\d+) not in In review, tree clean`),
  },
  /** Gruppen: Minuten, Kartennummer. */
  infrastruktur: {
    de: new RegExp(String.raw`^ {2}INFRASTRUKTUR-FEHLSCHLAG nach ${MIN} min .*Issue #(\d+) bleibt unangetastet\.`),
    en: new RegExp(String.raw`^ {2}INFRASTRUCTURE FAILURE after ${MIN} min .*issue #(\d+) remains untouched\.`),
  },
  /** Gruppe: Kartennummer. */
  harterStopp: {
    de: /^ {2}HARTER STOPP: (?:erfolgreiche Runde zu|die Review-Session zu) Issue #(\d+)/,
    en: /^ {2}HARD STOP: (?:successful round for|the review session for) issue #(\d+)/,
  },
  /** Gruppe: Kartennummer. */
  salvageGescheitert: {
    de: /^ {2}SALVAGE-VERSUCH gescheitert — harter Stopp\. Issue #(\d+)/,
    en: /^ {2}SALVAGE ATTEMPT failed — hard stop\. Issue #(\d+)/,
  },

  // --- Pruef-Lauf, Ausgaenge je Arbeitspaket; Gruppen: Minuten, Kartennummer
  geprueft: {
    de: new RegExp(String.raw`^ {2}Erfolg nach ${MIN} min: Issue #(\d+) geprueft (?:ohne|mit) Befund`),
    en: new RegExp(String.raw`^ {2}Success after ${MIN} min: issue #(\d+) checked (?:without|with) findings`),
  },
  syntheseOhneBeleg: {
    de: new RegExp(String.raw`^ {2}Nach ${MIN} min: Issue #(\d+) — Synthese ohne Beleg: `),
    en: new RegExp(String.raw`^ {2}After ${MIN} min: issue #(\d+) — Synthesis without evidence: `),
  },
  schaerfungFehlt: {
    de: new RegExp(String.raw`^ {2}Nach ${MIN} min: Issue #(\d+) — Befunde vorhanden, aber kein Body-Vorschlag`),
    en: new RegExp(String.raw`^ {2}After ${MIN} min: issue #(\d+) — Findings present, but no body proposal`),
  },

  // --- Uebergangene Arbeitspakete; Gruppen der Kandidatenliste: Kartennummer, Titel
  /** Uebersprungen wegen einer offenen Entscheidung — unter beiden Labelnamen. */
  uebersprungenKlaeren: {
    de: new RegExp(String.raw`^ {2}#(\d+) (.*?) -> uebersprungen \((?:${KLAEREN})`),
    en: new RegExp(String.raw`^ {2}#(\d+) (.*?) -> skipped \((?:${KLAEREN})`),
  },
  /** Uebersprungen aus jedem anderen Grund — die offene Entscheidung ausdruecklich ausgenommen. */
  uebersprungenListe: {
    de: new RegExp(String.raw`^ {2}#(\d+) (.*?) -> uebersprungen \((?!${KLAEREN})`),
    en: new RegExp(String.raw`^ {2}#(\d+) (.*?) -> skipped \((?!${KLAEREN})`),
  },
  ueberMax: {
    de: /^ {2}#(\d+) (.*?) -> ueber --max \d+, (?:bleibt|bliebe) liegen/,
    en: /^ {2}#(\d+) (.*?) -> over --max \d+, (?:remains|would remain) pending/,
  },
  /** Gruppe: Kartennummer. */
  zurueckgestellt: { de: /^#(\d+) zurueckgestellt: Abhaengigkeit/, en: /^#(\d+) deferred: dependency/ },
  /** Gruppe: Kartennummer. */
  uebersprungenGate: { de: /^#(\d+) uebersprungen: /, en: /^#(\d+) skipped: / },

  // --- Sessionbeginn; Gruppen: Kartennummer, Titel
  session: {
    // Stryker disable next-line Regex: gleichwertig — `$` hinter `(.*)` unterscheidet nur bei U+2028/U+2029; Anfang und Ziffern halten eigene Tests
    de: /^(?:Review-)?Session \d+\/\d+: Issue #(\d+) — (.*)$/,
    // Stryker disable next-line Regex: gleichwertig — wie die deutsche Form
    en: /^(?:Review )?session \d+\/\d+: issue #(\d+) — (.*)$/,
  },

  // --- Pruefblock
  /** Beginn des Pruefblocks. */
  pruefblock: { de: /^Pruefungen der Sessions:$/, en: /^Checks of the sessions:$/ },
  /** Gruppen: Kartennummer, gelaufene Pruefungen. */
  pruefGelaufen: {
    de: /^ {2}Issue #(\d+): gelaufen: (.*?) \| ausgelassen:/,
    en: /^ {2}Issue #(\d+): ran: (.*?) \| skipped:/,
  },
  /** Gruppe: Kartennummer. */
  pruefUngeprueft: { de: /^ {2}Issue #(\d+): ungeprueft — /, en: /^ {2}Issue #(\d+): unchecked — / },
  /** Gruppe: Kartennummer. */
  pruefLeer: { de: /^ {2}Issue #(\d+): leeres Paket — /, en: /^ {2}Issue #(\d+): empty package — / },
  /** Eine rote Pruefung in der Liste der gelaufenen. */
  rot: { de: / -> rot /, en: / -> red / },

  // --- Stumme Zeilen: gedeutet, aber ohne Zustand
  freigegeben: {
    de: /^#\d+ bewusst ohne Pruefung freigegeben/,
    en: /^#\d+ deliberately released without checks/,
  },
  vorflug: {
    de: /^ {2}Vorflug-Session (?:startet|nicht auswertbar)/,
    en: /^ {2}Preflight session (?:starting|not evaluable)/,
  },
  reviewer: { de: /^ {2}Reviewer .*: /, en: /^ {2}Reviewer .*: / },
  keinReviewer: { de: /^ {2}Kein Reviewer konfiguriert: /, en: /^ {2}No reviewer configured: / },
  tracker: { de: /^ {2}Tracker \(.*\): /, en: /^ {2}Tracker \(.*\): / },
  formatFix: {
    de: /^ {2}buildChecks rot — einmaliger Format-Fix/,
    en: /^ {2}buildChecks red — one-time format fix/,
  },
  formatFixAngewendet: { de: /^ {2}FORMAT-FIX angewendet/, en: /^ {2}FORMAT FIX applied/ },
  salvageGestartet: { de: /^ {2}SALVAGE-VERSUCH gestartet/, en: /^ {2}SALVAGE ATTEMPT started/ },
  salvageNichtMoeglich: { de: /^ {2}Salvage nicht moeglich: /, en: /^ {2}Salvage not possible: / },
  hinweisZusammenfassung: {
    de: /^ {2}Hinweis: die vorherige Pruef-Zusammenfassung/,
    en: /^ {2}Note: the previous check summary/,
  },
  cliMeldung: { de: /^ {2}CLI-Meldung: /, en: /^ {2}CLI message: / },
  vorhandeneLabels: {
    de: /^ {2}I[nm] (?:Ready|Backlog) vorhandene Labels: /,
    en: /^ {2}Labels present in (?:Ready|Backlog): /,
  },
  tippfehler: {
    de: /^ {2}Tippfehler im --(?:review-)?label-Wert\?/,
    en: /^ {2}Typo in the --(?:review-)?label value\?/,
  },
  warnung: { de: /^WARNUNG: /, en: /^WARNING: / },
  readyLeer: { de: /^Ready ist leer — nichts zu tun\./, en: /^Ready is empty — nothing to do\./ },
  keineReviewKandidaten: { de: /^Keine Review-Kandidaten im Backlog/, en: /^No review candidates in Backlog/ },
  morgenRitual: { de: /^Morgen-Ritual: /, en: /^Morning ritual: / },
  keineRunde: {
    de: /^Pruefungen: keine Implementierungs-Runde gelaufen\.$/,
    en: /^Checks: no implementation round ran\.$/,
  },
  summe: { de: /^ {2}Summe: \d+ Session\(s\)/, en: /^ {2}Total: \d+ session\(s\)/ },
} as const satisfies Record<string, Wortform>

/** Ein fester Text in beiden Sprachen: zuerst die deutsche, dann die englische Form. */
export type Wortpaar = readonly [de: string, en: string]

/**
 * Abschnitt Ergebnisdatei: die Freitext-Gruende, an denen `nightRunErgebnisstand.ts` Zustand und
 * Fehlerklasse festmacht (Issue #1593). Die maschinellen Kennwerte (`ausgang`, `art`,
 * Stufenschluessel, Budgetfeldnamen) bleiben deutsch und haben kein Gegenstueck (Plan #1582, E4).
 */
export const ERGEBNISGRUND = {
  abhaengigkeit: ['Abhaengigkeit', 'dependency'],
  klaeren: ['kit:klaeren', 'kit:clarify'],
  ohneInReview: ['Session ohne In-review-Ergebnis', 'Session without In-review result'],
  /** Praefix eines Zeitbudget-Abbruchs, wie `ketteSession` ihn schreibt. */
  zeitbudget: ['Zeitbudget ', 'Time budget '],
} as const satisfies Record<string, Wortpaar>

/**
 * Abschnitt Laufstand: die Anker des Laufstand-Kommentars (Plan #1582, E12), wie das
 * Namen-Register des Kits sie in `MARKER` fuehrt (Issue #1599).
 */
export const LAUFSTAND = {
  kopf: ['## Laufstand', '## Run status'],
} as const satisfies Record<string, Wortpaar>

/** Der Pruefvermerk eines Plans — das Kit schreibt ihn in beiden Sprachen gleich (Issue #1599). */
export const PLAN_REVIEW = 'Plan-Review:'

/** Abschnitt Budgetfelder: die Namen fuer die Anzeige der `defaultFields` (Plan #1582, E3). */
export const BUDGETFELDER = {
  paketeMin: ['paketeMin', 'packagesMin'],
  abdeckungMin: ['abdeckungMin', 'coverageMin'],
} as const satisfies Record<string, Wortpaar>

/**
 * Abschnitt Szenarien: die Form der Szenarien im Fachplan, die der Abnahme-Dialog liest
 * (Issue #1627, Plan #1626, E1). Wie bei den englischen Protokollzeilen eine Vorlage fuer das Format
 * aus mannewolff/claude-workflow-kit#1586; weicht das Kit ab, gleicht ein `[Task]` diese Datei an.
 */

/** Die Ueberschrift des Abschnitts — eine eigene `##`-Zeile, ohne Zusatz. */
export const SZENARIEN_ABSCHNITT: Wortform = { de: /^##[ \t]+Szenarien[ \t]*$/, en: /^##[ \t]+Scenarios[ \t]*$/ }

/** Optionales Listenzeichen vor einem Schritt (`-`, `*`, `+` oder `1.`). */
const LISTENPUNKT = String.raw`^(?:(?:[-*+]|\d+\.)[ \t]+)?`

/** Ein Schritt mit optional fettem Schluesselwort — Gruppen: Fettdruck, Schluesselwort, Rest. */
const schritt = (woerter: string) => new RegExp(String.raw`${LISTENPUNKT}(\*\*)?(${woerter})\1(?:[ \t]+(.*?))?[ \t]*$`)

/** Eine Schrittzeile eines Szenarios; die Zeile kommt ohne fuehrende Leerzeichen. */
export const SZENARIO_SCHRITT: Wortform = {
  de: schritt('Angenommen|Wenn|Dann|Und|Aber'),
  en: schritt('Given|When|Then|And|But'),
}
