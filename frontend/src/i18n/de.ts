/**
 * Das deutsche Wörterbuch der Oberfläche und zugleich die Quelle der Schlüssel (Plan #1638, A1).
 *
 * `as const` hält jeden Wert als Literal fest; {@link Woerterbuch} weitet sie zu `string`, damit
 * `en.ts` dieselben Schlüssel mit eigenem Text tragen muss — `tsc` weist eine fehlende oder
 * überzählige Übersetzung im Build ab. Texte mit Werten sind Funktionen (`(n: number) => …`)
 * statt Platzhalter-Strings: So prüft der Compiler auch, dass jede Sprache dieselben Werte nimmt.
 *
 * Neue Texte kommen paketweise dazu, wenn eine Seite umgestellt wird.
 */
export const de = {
  /** Anzeigename der Anwendung: Kopfzeile, Anmeldekarte, Lizenzhinweis und Fenstertitel. */
  APP_NAME: 'KI-Leitstand',

  /** Allgemeiner Fallback, wenn ein Fehler keine anzeigbare Server-Meldung trägt (E6). */
  FEHLER_UNBEKANNT: 'unbekannter Fehler',

  /** Die angezeigten Kartenarten des Kits (A11); Schlüssel wie im Namen-Register des Servers. */
  KARTENART: {
    FACHLICH: 'Fachliche Anforderung',
    IDEE: 'Idee',
    MENSCH: 'Mensch',
    PLAN: 'Plan',
    TASK: 'Task',
  },

  /** Einheiten der Dauer für `formatierer(…).dauer` (A7). */
  DAUER_NICHT_VERFUEGBAR: 'n. v.',
  DAUER_SEKUNDEN: (sekunden: number) => `${sekunden} s`,
  DAUER_MINUTEN: (minuten: number) => `${minuten} Min`,
  DAUER_STUNDEN: (stunden: number, minuten: number) => `${stunden} Std ${minuten} Min`,
  DAUER_TAGE: (tage: number, stunden: number) => `${tage} T ${stunden} Std`,

  /** Abschnitt „Sprache / Language“ der Administration (Issue #1646); Überschrift und Sprachnamen stehen fest. */
  SPRACHE: {
    HINWEIS:
      'Die Sprache gilt für alle Nutzer und für die Seiten vor der Anmeldung. Wer gerade angemeldet ist, sieht die Umstellung beim nächsten Laden der Seite.',
    NUR_ADMIN: 'Nur ein Plattform-Admin kann die Sprache ändern.',
    SPEICHERN: 'Speichern',
    SPEICHERN_ARIA: 'Sprache speichern',
    GESPEICHERT: 'Sprache gespeichert.',
    FEHLER: 'Die Sprache konnte nicht gespeichert werden.',
  },
} as const

/** Weitet die Literale des deutschen Wörterbuchs zu `string`, rekursiv und durch Funktionen hindurch. */
type Weit<T> = T extends string
  ? string
  : T extends (...werte: infer A) => infer R
    ? (...werte: A) => Weit<R>
    : { readonly [K in keyof T]: Weit<T[K]> }

/** Die Schlüssel des Wörterbuchs. */
export type Schluessel = keyof typeof de

/** Der Typ jedes Wörterbuchs: dieselben Schlüssel wie {@link de}, Texte frei. */
export type Woerterbuch = { readonly [K in Schluessel]: Weit<(typeof de)[K]> }
