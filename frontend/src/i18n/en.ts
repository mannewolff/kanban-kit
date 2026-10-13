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
}
