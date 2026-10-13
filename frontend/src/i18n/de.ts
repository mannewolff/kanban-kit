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

  /** Die fünf Prozesszustände (`columnMeta`); wie im Kit, in beiden Sprachen gleich (Kriterium 6). */
  STATUS: {
    BACKLOG: 'Backlog',
    READY: 'Ready',
    IN_PROGRESS: 'In progress',
    IN_REVIEW: 'In review',
    DONE: 'Done',
  },

  /** Fehlertext eines zu langen Freitexts (`textLimits`); die Zahlen kommen formatiert. */
  TEXT_ZU_LANG: (laenge: string, grenze: string) => `${laenge} / ${grenze} Zeichen`,

  /** Archivierungs-Hinweis einer Done-Karte (`cleanupCountdown`). */
  ARCHIVIERUNG: {
    HEUTE: 'wird heute archiviert',
    MORGEN: 'wird morgen archiviert',
    IN_TAGEN: (tage: number) => `wird in ${tage} Tagen archiviert`,
  },

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

  /** Datumsfeld zum Eingeben (`DatumFeld`, Issue #1648): Schreibweise und Meldung bei ungültiger Eingabe. */
  DATUMSFELD: {
    MUSTER: 'TT.MM.JJJJ',
    UNGUELTIG: 'Kein gültiges Datum — Schreibweise TT.MM.JJJJ',
  },

  /** Seiten vor der Anmeldung (Issue #1649): Anmeldung, Registrierung, Bestätigung, Passwort, Einladung, Bootstrap. */
  AUTH: {
    EMAIL: 'E-Mail',
    PASSWORT: 'Passwort',
    PASSWORT_ANZEIGEN: 'Passwort anzeigen',
    PASSWORT_VERBERGEN: 'Passwort verbergen',
    MINDESTENS_8_ZEICHEN: 'Mindestens 8 Zeichen',
    ZU_VIELE_VERSUCHE: 'Zu viele Versuche. Bitte später erneut versuchen.',
    ZUR_ANMELDUNG: 'Zur Anmeldung',
    ZURUECK_ZUR_ANMELDUNG: 'Zurück zur Anmeldung',
    ANMELDEN: 'Anmelden',
    ANMELDEDATEN_UNGUELTIG: 'Ungültige Anmeldedaten.',
    ANMELDUNG_FEHLGESCHLAGEN: 'Anmeldung fehlgeschlagen. Bitte später erneut versuchen.',
    REGISTRIEREN: 'Registrieren',
    PASSWORT_VERGESSEN: 'Passwort vergessen?',
    ANZEIGENAME: 'Anzeigename',
    KONTO_ERSTELLEN: 'Konto erstellen',
    EMAIL_BEREITS_REGISTRIERT: 'Diese E-Mail-Adresse ist bereits registriert.',
    REGISTRIERUNG_FEHLGESCHLAGEN: 'Registrierung fehlgeschlagen. Bitte Eingaben prüfen (Passwort mind. 8 Zeichen).',
    BESTAETIGUNG_TITEL: 'E-Mail-Bestätigung',
    BESTAETIGUNG_HINWEIS: 'Wir haben dir eine E-Mail zur Bestätigung geschickt. Bitte öffne den Link darin.',
    BESTAETIGUNG_LAEUFT: 'E-Mail wird bestätigt …',
    BESTAETIGUNG_ERFOLG: 'E-Mail bestätigt. Du kannst dich jetzt anmelden.',
    BESTAETIGUNG_UNGUELTIG: 'Der Bestätigungslink ist ungültig oder abgelaufen.',
    VERGESSEN_TITEL: 'Passwort zurücksetzen',
    VERGESSEN_GESENDET: 'Falls ein Konto mit dieser E-Mail existiert, wurde ein Link zum Zurücksetzen gesendet.',
    LINK_ANFORDERN: 'Link anfordern',
    NEUES_PASSWORT_TITEL: 'Neues Passwort setzen',
    NEUES_PASSWORT: 'Neues Passwort',
    PASSWORT_SETZEN: 'Passwort setzen',
    PASSWORT_GESETZT: 'Passwort gesetzt. Du kannst dich jetzt anmelden.',
    LINK_UNGUELTIG: 'Der Link ist ungültig oder abgelaufen.',
    KEIN_TOKEN: 'Kein Token in der URL gefunden.',
    EINLADUNG_TITEL: 'Einladung annehmen',
    EINLADUNG_UNGUELTIG: 'Die Einladung ist ungültig oder abgelaufen.',
    EINLADUNG_BEIGETRETEN: (rolle: string) => `Du bist dem Projekt als ${rolle} beigetreten.`,
    ZU_DEN_PROJEKTEN: 'Zu den Projekten',
    BOOTSTRAP_TITEL: 'Admin-Bootstrap',
    BOOTSTRAP_HINWEIS:
      'Auf einer frischen Instanz kannst du dich mit dem konfigurierten Einmal-Token zum ersten Plattform-Admin machen.',
    BOOTSTRAP_TOKEN: 'Bootstrap-Token',
    BOOTSTRAP_ADMIN_WERDEN: 'Admin werden',
    BOOTSTRAP_ADMIN_EXISTIERT: 'Es existiert bereits ein Admin — Bootstrap nicht mehr möglich.',
    BOOTSTRAP_TOKEN_UNGUELTIG: 'Ungültiger oder nicht konfigurierter Token.',
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
