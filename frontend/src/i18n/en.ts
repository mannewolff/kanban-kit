import type { Woerterbuch } from './de'

/**
 * Das englische Wörterbuch der Oberfläche (Plan #1638, A1). Typisiert gegen `de.ts`: Fehlt hier
 * ein Schlüssel oder steht einer zu viel, scheitert `tsc`.
 *
 * Glossar (E1) — die englischen Namen folgen dem englischen Kit:
 * - „KI-Leitstand“ → „AI Control Room“
 * - „Leitstand“ → „Control Room“
 * - „Plattform-Leitstand“ → „Platform Control Room“
 * - „Vorhaben“ → „Epics“
 * - „Nachtlauf“ → „Night run“
 * - „Karte“ → „Card“
 * - Rollen VIEWER, MEMBER, ADMIN, OWNER bleiben unverändert.
 *
 * Kartenarten (A11): FACHLICH „Requirement“, IDEE „Idea“, MENSCH „Human“, PLAN „Plan“, TASK „Task“.
 */
export const en: Woerterbuch = {
  APP_NAME: 'AI Control Room',

  FEHLER_UNBEKANNT: 'unknown error',

  KARTENART: {
    FACHLICH: 'Requirement',
    IDEE: 'Idea',
    MENSCH: 'Human',
    PLAN: 'Plan',
    TASK: 'Task',
  },

  DAUER_NICHT_VERFUEGBAR: 'n/a',
  DAUER_SEKUNDEN: (sekunden) => `${sekunden} s`,
  DAUER_MINUTEN: (minuten) => `${minuten} min`,
  DAUER_STUNDEN: (stunden, minuten) => `${stunden} h ${minuten} min`,
  DAUER_TAGE: (tage, stunden) => `${tage} d ${stunden} h`,

  STATUS: {
    BACKLOG: 'Backlog',
    READY: 'Ready',
    IN_PROGRESS: 'In progress',
    IN_REVIEW: 'In review',
    DONE: 'Done',
  },

  TEXT_ZU_LANG: (laenge, grenze) => `${laenge} / ${grenze} characters`,

  ARCHIVIERUNG: {
    HEUTE: 'archived today',
    MORGEN: 'archived tomorrow',
    IN_TAGEN: (tage) => `archived in ${tage} days`,
  },

  SPRACHE: {
    HINWEIS:
      'The language applies to all users and to the pages before sign-in. Users who are signed in see the change the next time they load the page.',
    NUR_ADMIN: 'Only a platform admin can change the language.',
    SPEICHERN: 'Save',
    SPEICHERN_ARIA: 'Save language',
    GESPEICHERT: 'Language saved.',
    FEHLER: 'The language could not be saved.',
  },

  DATUMSFELD: {
    MUSTER: 'MM/DD/YYYY',
    UNGUELTIG: 'Not a valid date — use MM/DD/YYYY',
  },
}
