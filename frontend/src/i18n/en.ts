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

  AUTH: {
    EMAIL: 'Email',
    PASSWORT: 'Password',
    PASSWORT_ANZEIGEN: 'Show password',
    PASSWORT_VERBERGEN: 'Hide password',
    MINDESTENS_8_ZEICHEN: 'At least 8 characters',
    ZU_VIELE_VERSUCHE: 'Too many attempts. Please try again later.',
    ZUR_ANMELDUNG: 'Go to sign-in',
    ZURUECK_ZUR_ANMELDUNG: 'Back to sign-in',
    ANMELDEN: 'Sign in',
    ANMELDEDATEN_UNGUELTIG: 'Invalid credentials.',
    ANMELDUNG_FEHLGESCHLAGEN: 'Sign-in failed. Please try again later.',
    REGISTRIEREN: 'Sign up',
    PASSWORT_VERGESSEN: 'Forgot password?',
    ANZEIGENAME: 'Display name',
    KONTO_ERSTELLEN: 'Create account',
    EMAIL_BEREITS_REGISTRIERT: 'This email address is already registered.',
    REGISTRIERUNG_FEHLGESCHLAGEN: 'Sign-up failed. Please check your input (password at least 8 characters).',
    BESTAETIGUNG_TITEL: 'Email confirmation',
    BESTAETIGUNG_HINWEIS: 'We have sent you an email to confirm your address. Please open the link in it.',
    BESTAETIGUNG_LAEUFT: 'Confirming email …',
    BESTAETIGUNG_ERFOLG: 'Email confirmed. You can sign in now.',
    BESTAETIGUNG_UNGUELTIG: 'The confirmation link is invalid or has expired.',
    VERGESSEN_TITEL: 'Reset password',
    VERGESSEN_GESENDET: 'If an account with this email exists, a reset link has been sent.',
    LINK_ANFORDERN: 'Request link',
    NEUES_PASSWORT_TITEL: 'Set new password',
    NEUES_PASSWORT: 'New password',
    PASSWORT_SETZEN: 'Set password',
    PASSWORT_GESETZT: 'Password set. You can sign in now.',
    LINK_UNGUELTIG: 'The link is invalid or has expired.',
    KEIN_TOKEN: 'No token found in the URL.',
    EINLADUNG_TITEL: 'Accept invitation',
    EINLADUNG_UNGUELTIG: 'The invitation is invalid or has expired.',
    EINLADUNG_BEIGETRETEN: (rolle) => `You have joined the project as ${rolle}.`,
    ZU_DEN_PROJEKTEN: 'Go to projects',
    BOOTSTRAP_TITEL: 'Admin bootstrap',
    BOOTSTRAP_HINWEIS: 'On a fresh instance you can make yourself the first platform admin with the configured one-time token.',
    BOOTSTRAP_TOKEN: 'Bootstrap token',
    BOOTSTRAP_ADMIN_WERDEN: 'Become admin',
    BOOTSTRAP_ADMIN_EXISTIERT: 'An admin already exists — bootstrap is no longer possible.',
    BOOTSTRAP_TOKEN_UNGUELTIG: 'Invalid or unconfigured token.',
  },
}
