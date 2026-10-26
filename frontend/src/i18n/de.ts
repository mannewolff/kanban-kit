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
/** Die Namen der Stufen einer Kette — Wegleiste und Zustandssatz teilen sie (Plan #1882 E4). */
const STUFENNAMEN = {
  PLAN: 'Plan',
  REVIEW: 'Prüfung',
  PAKETE: 'Arbeitspakete',
  ABDECKUNG: 'Abdeckung',
  UMSETZUNG: 'Umsetzung',
} as const

/** Die Ausgangswörter eines Befunds — Übergabe und Zustandssatz teilen sie (Plan #1882 E4). */
const AUSGANGSWORT = {
  SUCCEEDED: 'gelungen',
  FAILED: 'nicht gelungen',
  WAITING: 'mit Vorbehalt',
  RUNNING: 'läuft',
  NO_WORK: 'nichts zu tun',
  CLOSED: 'von Hand beendet',
  NOT_STARTED: 'nicht angelaufen',
} as const

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
    PASSWORT_ANZEIGEN: 'Passwort anzeigen',
    PASSWORT_VERBERGEN: 'Passwort verbergen',
    ZU_VIELE_VERSUCHE: 'Zu viele Versuche. Bitte später erneut versuchen.',
    ZURUECK_ZUR_ANMELDUNG: 'Zurück zur Anmeldung',
    ANMELDEN: 'Anmelden',
    CODE_ANFORDERN: 'Code anfordern',
    CODE_UNTERWEGS: 'Falls es ein Konto mit dieser Adresse gibt, ist ein Code unterwegs.',
    CODE: 'Code aus der E-Mail',
    CODE_HINWEIS: 'Sechs Ziffern, 10 Minuten gültig',
    NEUEN_CODE_ANFORDERN: 'Neuen Code anfordern',
    CODE_UNGUELTIG: 'Code ungültig oder abgelaufen — fordere einen neuen an.',
    ANMELDUNG_FEHLGESCHLAGEN: 'Anmeldung fehlgeschlagen. Bitte später erneut versuchen.',
    LINK_LAEUFT: 'Anmeldung läuft …',
    LINK_OHNE_TOKEN: 'Der Link enthält kein Token. Fordere auf der Anmeldung einen neuen Code an.',
    REGISTRIEREN: 'Registrieren',
    ANZEIGENAME: 'Anzeigename',
    KONTO_ERSTELLEN: 'Konto erstellen',
    EMAIL_BEREITS_REGISTRIERT: 'Diese E-Mail-Adresse ist bereits registriert.',
    REGISTRIERUNG_FEHLGESCHLAGEN: 'Registrierung fehlgeschlagen. Bitte Eingaben prüfen.',
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

  /** Rahmen und Navigation (Issue #1650): Schiene, Kopf, Kontomenü, Board-Umschalter, Editiermodus, Tabelle. */
  RAHMEN: {
    NAV_LEITSTAND: 'Leitstand',
    NAV_BOARD: 'Board',
    NAV_LISTE: 'Liste',
    NAV_VORHABEN: 'Vorhaben',
    NAV_NACHTLAUF: 'Runner',
    NAV_PROJEKT: 'Projekt',
    NAV_PROJEKT_NAME: (name: string) => `Projekt ${name}`,
    NAV_UEBERSICHT: 'Übersicht',
    NAV_PROJEKTE: 'Projekte',
    NAV_BOARDS: 'Boards',
    NAV_VERWALTUNG: 'Verwaltung',
    NAV_MITGLIEDER: 'Mitglieder',
    NAV_ROLLEN: 'Rollen & Rechte',
    NAV_PLATTFORM_LEITSTAND: 'Plattform-Leitstand',
    NAV_ADMIN: 'Admin',
    NAV_ADMINISTRATION: 'Administration',
    NAV_DOKUMENTATION: 'Dokumentation',
    HAUPTNAVIGATION: 'Hauptnavigation',
    MENUE_AUSKLAPPEN: 'Menü ausklappen',
    MENUE_EINKLAPPEN: 'Menü einklappen',
    ZUM_INHALT: 'Zum Inhalt springen',
    NAVIGATION_OEFFNEN: 'Navigation öffnen',
    PFAD: 'Pfad',
    BOARD_WECHSELN: 'Board wechseln',
    BOARD_WECHSELN_TASTE: 'Board wechseln (Taste b)',
    BOARD_TASTE: 'Board',
    KONTO_VON: (name: string) => `Konto von ${name}`,
    PROFIL_BEARBEITEN: 'Profil bearbeiten',
    ABMELDEN: 'Abmelden',
    ABMELDEN_FEHLGESCHLAGEN: 'Abmelden fehlgeschlagen.',
    ZULETZT_BESUCHTE_BOARDS: 'Zuletzt besuchte Boards',
    BOARD_NICHT_VERFUEGBAR: 'Board ist nicht mehr verfügbar.',
    BOARD_OEFFNEN_FEHLGESCHLAGEN: 'Das Board konnte nicht geöffnet werden.',
    EDITIERMODUS_BEREICH: 'Editiermodus-Hinweis',
    EDITIERMODUS_HINWEIS: 'Achtung, Du befindest Dich im Editiermodus',
    EDITIERMODUS_VERLASSEN: 'Editiermodus verlassen',
    SPALTEN_UMSCHALTEN: 'Spalten ein-/ausblenden',
    SPALTE_UMSCHALTEN: (spalte: string) => `Spalte ${spalte} ein-/ausblenden`,
    BREITE_ZIEHEN: 'Breite ziehen',
    BREITE_AENDERN: (spalte: string) => `Breite von Spalte ${spalte} ändern`,
  },

  /** Gemeinsame Knöpfe und Meldungen der Verwaltungsseiten (Issue #1651). */
  ALLGEMEIN: {
    ABBRECHEN: 'Abbrechen',
    SPEICHERN: 'Speichern',
    ANLEGEN: 'Anlegen',
    LOESCHEN: 'Löschen',
    ENTFERNEN: 'Entfernen',
    WIEDERHERSTELLEN: 'Wiederherstellen',
    SCHLIESSEN: 'Schließen',
    ROLLE: 'Rolle',
    SPEICHERN_FEHLGESCHLAGEN: 'Speichern fehlgeschlagen.',
    UNGUELTIGE_PROJEKT_ID: 'Ungültige Projekt-ID.',
  },

  /** Projektliste (Issue #1651). */
  PROJEKTE: {
    NEUES_PROJEKT: 'Neues Projekt',
    OWNER_EMAIL: 'Owner (E-Mail)',
    LEER_ADMIN: 'Noch keine Projekte. Lege oben eines an.',
    LEER: 'Noch keine Projekte. Ein Administrator legt Projekte an.',
    UMBENENNEN_ARIA: (name: string) => `Projekt ${name} umbenennen`,
    LOESCHEN_ARIA: (name: string) => `Projekt ${name} löschen`,
    LOESCHEN_TITEL: 'Projekt löschen?',
    LOESCHEN_TEXT: (name: string) =>
      `Das Projekt „${name}“ und alle zugehörigen Boards, Vorhaben und Tickets werden unwiderruflich gelöscht.`,
    UMBENENNEN_TITEL: 'Projekt umbenennen',
    PROJEKTNAME: 'Projektname',
    NEUER_PROJEKTNAME: 'Neuer Projektname',
    NAECHSTE_NUMMER: 'Nächste Kartennummer',
    NAECHSTE_NUMMER_HILFE: 'Nummer, die die nächste neue Karte bekommt.',
    TEILNAHME: 'Teilnahme am Plattform-Leitstand',
    TEILNAHME_FEHLER: 'Teilnahme konnte nicht geschaltet werden.',
    UMBENENNEN_FEHLER: 'Umbenennen fehlgeschlagen.',
    NUMMER_ZU_KLEIN: 'Nächste Nummer muss größer als die höchste vergebene Nummer sein.',
    GESPEICHERT: 'Projekt gespeichert.',
    OWNER_UNBEKANNT: 'Kein Nutzer mit dieser Owner-E-Mail gefunden.',
    NUR_ADMINS: 'Nur Administratoren dürfen Projekte anlegen.',
    ANLEGEN_FEHLER: 'Anlegen fehlgeschlagen.',
    LOESCHEN_FEHLER: 'Löschen fehlgeschlagen.',
    GELOESCHT_LISTE_FEHLER: 'Projekt gelöscht, Liste konnte nicht aktualisiert werden.',
  },

  /** Boards eines Projekts samt Archiv (Issue #1651). */
  BOARDS: {
    NEUES_BOARD: 'Neues Board',
    LEER: 'Noch keine Boards.',
    SPALTEN: (anzahl: number) => `${anzahl} Spalten`,
    ARCHIV: 'Archiv',
    ARCHIVIEREN: 'Archivieren',
    ARCHIVIEREN_ARIA: (name: string) => `Board ${name} archivieren`,
    ARCHIVIEREN_TITEL: 'Board archivieren?',
    ARCHIVIEREN_TEXT: (name: string) =>
      `Das Board „${name}“ wird archiviert. Es verschwindet aus der Liste, bleibt aber mit allen Vorhaben und Tickets erhalten und lässt sich im Archiv wiederherstellen.`,
    ENDGUELTIG_LOESCHEN: 'Endgültig löschen',
    ENDGUELTIG_ARIA: (name: string) => `Board ${name} endgültig löschen`,
    ENDGUELTIG_TITEL: 'Board endgültig löschen?',
    ENDGUELTIG_TEXT: (name: string) =>
      `Das Board „${name}“ und alle zugehörigen Vorhaben und Tickets werden unwiderruflich gelöscht. Gib zur Bestätigung den Board-Namen ein.`,
    BOARD_NAME: 'Board-Name',
    BOARD_NAME_BESTAETIGUNG: 'Board-Name zur Bestätigung',
    ANLEGEN_FEHLER: 'Board anlegen fehlgeschlagen.',
    ANGELEGT_LISTE_FEHLER: 'Board angelegt, Liste konnte nicht aktualisiert werden.',
    ARCHIVIEREN_FEHLER: 'Archivieren fehlgeschlagen.',
    WIEDERHERSTELLEN_FEHLER: 'Wiederherstellen fehlgeschlagen.',
    ENDGUELTIG_FEHLER: 'Endgültig löschen fehlgeschlagen.',
  },

  /** Listenansicht eines Boards (Issue #1651). */
  LISTE: {
    SPALTE: {
      number: 'Nr',
      status: 'Status',
      epic: 'Vorhaben',
      title: 'Titel',
      excerpt: 'Beschreibung',
    },
    SPALTE_ARIA: (spalte: string) => `Spalte ${spalte}`,
    SORTIERT_ARIA: (beschriftung: string, aufsteigend: boolean) =>
      `${beschriftung}, ${aufsteigend ? 'aufsteigend' : 'absteigend'} sortiert`,
    LABELS: 'Labels',
    LABELS_ZAHL: (anzahl: number) => `Labels · ${anzahl}`,
    LABEL_FILTER: 'Label-Filter',
    LABEL_FILTER_NAME: (name: string) => `Label-Filter ${name}`,
    OHNE_VORHABEN: 'Ohne Vorhaben',
    KARTEN: (anzahl: number) => (anzahl === 1 ? '1 Karte' : `${anzahl} Karten`),
    FORTSCHRITT: (fertig: number, gesamt: number) => `${fertig} von ${gesamt} fertig`,
    ARCHIV: 'Archiv',
    FAELLIG: (datum: string) => `fällig ${datum}`,
    FAELLIG_ARIA: (titel: string) => `Fällig ${titel}`,
    UNGUELTIGE_BOARD_ID: 'Ungültige Board-ID.',
    HINWEIS_SORTIERT: (spalte: string, umordnen: boolean) =>
      `Sortiert nach ${spalte}.${umordnen ? ' Zum Ändern der Kartenreihenfolge die Sortierung aufheben.' : ''}`,
    HINWEIS_GRUPPIERUNG: 'Kartenreihenfolge ändern: dazu die Gruppierung aufheben.',
    HINWEIS_FILTER: 'Kartenreihenfolge ändern: dazu genau einen Status-Filter wählen und den Archiv-Filter abwählen.',
    STATUS_FILTER: 'Status-Filter',
    FILTER: (name: string) => `Filter ${name}`,
    UEBERFAELLIG: 'Überfällig',
    ZUSTAENDIG: 'Zuständig',
    ZUSTAENDIG_ALLE: 'Zuständig: alle',
    ZUSTAENDIG_NAME: (name: string) => `Zuständig: ${name}`,
    GRUPPIEREN: 'Gruppieren',
    GRUPPE_VORHABEN: 'Vorhaben',
    GRUPPE_KEINE: 'keine',
    KEINE_KARTEN: 'Keine Karten',
    KARTEN_ALS_LISTE: 'Karten als Liste',
    BREITER_ZIEHEN: 'Beschreibung-Spalte breiter ziehen',
    DETAIL_OEFFNEN: (titel: string) => `Detail öffnen: ${titel}`,
    REIHENFOLGE_AENDERN: 'Reihenfolge ändern',
    KARTE_WIEDERHERSTELLEN: (titel: string) => `Karte ${titel} wiederherstellen`,
    WIEDERHERSTELLEN_FEHLER: 'Karte wiederherstellen fehlgeschlagen.',
    VERSCHIEBEN_FEHLER: 'Verschieben fehlgeschlagen.',
  },

  /** Mitglieder eines Projekts (Issue #1651). */
  MITGLIEDER: {
    SPALTE_NAME: 'Name',
    SPALTE_EMAIL: 'E-Mail',
    SPALTE_AKTIONEN: 'Aktionen',
    EINLADEN_FORMULAR: 'Nutzer einladen',
    EMAIL_EINLADEN: 'E-Mail einladen',
    EINLADUNGSROLLE: 'Einladungsrolle',
    EINLADEN: 'Einladen',
    ANZEIGENAME_VON: (email: string) => `Anzeigename von ${email}`,
    NAMEN_SPEICHERN: 'Namen speichern',
    BEARBEITEN_ABBRECHEN: 'Bearbeiten abbrechen',
    NAME_AENDERN_HINWEIS: 'Anzeigename ändern (global, projektübergreifend)',
    NAMEN_BEARBEITEN: (name: string) => `Namen von ${name} bearbeiten`,
    ROLLE_VON: (name: string) => `Rolle von ${name}`,
    ZUM_EIGENTUEMER: 'Zum Eigentümer machen',
    ZUM_EIGENTUEMER_ARIA: (name: string) => `${name} zum Eigentümer machen`,
    ENTFERNEN_ARIA: (name: string) => `${name} entfernen`,
    TRANSFER_TITEL: 'Zum Eigentümer machen?',
    TRANSFER_TEXT: (name: string) =>
      `„${name}“ wird Eigentümer dieses Projekts. Du verlierst dabei deine Owner-Rechte und wirst zum Admin herabgestuft.`,
    UEBERTRAGEN: 'Übertragen',
    HINZUGEFUEGT: 'Nutzer wurde hinzugefügt.',
    EINLADUNG_VERSCHICKT: 'Einladung verschickt.',
    NICHT_FREIGEGEBEN: 'Nutzer ist noch nicht vom Admin freigegeben.',
    EINLADUNG_FEHLER: 'Einladung fehlgeschlagen.',
    LETZTER_OWNER_HERABSTUFEN: 'Der letzte Owner kann nicht herabgestuft werden.',
    ROLLE_FEHLER: 'Rollenänderung fehlgeschlagen.',
    NAME_FEHLER: 'Namensänderung fehlgeschlagen.',
    LETZTER_OWNER_ENTFERNEN: 'Der letzte Owner kann nicht entfernt werden.',
    ENTFERNEN_FEHLER: 'Entfernen fehlgeschlagen.',
    TRANSFER_FEHLER: 'Eigentümer-Wechsel fehlgeschlagen.',
    TRANSFER_LISTE_FEHLER: 'Eigentümer gewechselt, Mitgliederliste konnte nicht aktualisiert werden.',
  },

  /**
   * Rollen und Rechte (Issue #1651). Die Schlüssel von RESSOURCE und OPERATION sind die der
   * Rechte-Matrix des Servers; EPIC zeigt „Vorhaben“, NIGHT den Begriff der Oberfläche.
   */
  ROLLEN: {
    PROJEKT_ROLLEN: 'Projekt-Rollen',
    RESSOURCE: {
      BOARD: 'Board',
      EPIC: 'Vorhaben',
      TICKET: 'Ticket',
      COMMENT: 'Kommentar',
      ATTACHMENT: 'Anhang',
      CARD: 'Karte',
      MEMBER: 'Mitglieder',
      PROJECT: 'Projekt',
      NIGHT: 'Läufe',
    },
    OPERATION: {
      CREATE: 'C',
      READ: 'R',
      UPDATE: 'U',
      DELETE: 'D',
      MOVE: 'Move',
      MOVE_PROJECT: 'Projektwechsel',
      INVITE: 'Einladen',
      REMOVE: 'Entfernen',
      EDIT: 'Bearbeiten',
      RUN_READ: 'Lesen',
      RUN_SUBMIT: 'Einliefern',
    },
    FUSSNOTE: {
      LAEUFE:
        'Ein Plattform-Admin liest die Auswertung eines Projekts auch ohne OWNER-Rolle — aber nur, wenn das Projekt am Plattform-Leitstand teilnimmt. Einliefern darf er immer.',
      PROJEKTWECHSEL: 'Die Rolle OWNER muss im Quell- und im Zielprojekt vorliegen.',
    },
    RECHT_FUER: (recht: string, rolle: string) => `${recht} für ${rolle}`,
    HAKEN_FEST:
      'Die Haken der eingebauten Rollen sind fest. Zusätzliche, frei konfigurierbare Rollen folgen später. Die Teilnahme am Plattform-Leitstand schalten OWNER und Projekt-ADMIN — sie ist kein Recht der Matrix und bewusst nicht Sache des Plattform-Admins.',
    PLATTFORM_ROLLEN: 'Plattform-Rollen',
    USER_TEXT: 'sieht und bearbeitet nur eigene Projekte bzw. Projekte, in denen er Mitglied ist.',
    ADMIN_TEXT_VOR: 'Super-User: Vollzugriff auf',
    ADMIN_TEXT_BETONT: 'alle',
    ADMIN_TEXT_NACH: 'Projekte und Nutzerverwaltung (andere zu Admin ernennen).',
    NUR_ADMIN_TITEL: 'Nur mit der Plattform-Rolle ADMIN',
    NUR_ADMIN_HINWEIS:
      'Keine Projekt-Rolle gewährt das — auch OWNER nicht. Deshalb stehen die beiden Punkte hier und nicht als Zeile der Matrix oben.',
    LEITSTAND_ANSEHEN: 'Plattform-Leitstand ansehen',
    LEITSTAND_ANSEHEN_TEXT: 'die Übersicht über die Läufe aller teilnehmenden Projekte.',
    PROJEKT_ANLEGEN: 'Projekt anlegen und löschen',
    PROJEKT_ANLEGEN_TEXT: 'beim Anlegen bestimmt der Plattform-Admin den Owner.',
  },

  /** Board-Ansicht (Issue #1652): Seite, Werkzeugleiste, Spalten, Karten-Kacheln, Menü und Dialoge. */
  BOARD: {
    ENTFERNT: 'Dieses Board wurde archiviert oder gelöscht.',
    NICHT_GEFUNDEN: 'Board nicht gefunden.',
    UMBENENNEN: 'Board umbenennen',
    UMBENENNEN_FEHLER: 'Umbenennen fehlgeschlagen.',
    NEUER_NAME: 'Neuer Board-Name',
    LABELS: 'Labels',
    PAPIERKORB: 'Papierkorb',
    LABELS_GESPERRT_VORHABEN: 'Vorhaben tragen keine Labels',
    LABELS_GESPERRT_KEINE: 'Das Board hat keine Labels',
    SORTIERT: (spalte: string, aufsteigend: boolean) =>
      `Spalte ${spalte} ${aufsteigend ? 'aufsteigend' : 'absteigend'} sortiert`,
    SPALTEN_UMSORTIEREN_FEHLER: 'Spalten umsortieren fehlgeschlagen.',
    SORTIEREN_FEHLER: 'Sortieren fehlgeschlagen.',
    SPALTE_GELOESCHT: 'Spalte gelöscht.',
    SPALTE_NICHT_LEER: 'Spalte enthält noch Karten und kann nicht gelöscht werden.',
    LOESCHEN_FEHLER: 'Löschen fehlgeschlagen.',
    SPALTE_ANGELEGT: 'Spalte angelegt.',
    SPALTE_GESPEICHERT: 'Spalte gespeichert.',
    SPALTE_SPEICHERN_FEHLER: 'Spalte speichern fehlgeschlagen.',
    VERSCHIEBEN_FEHLER: 'Verschieben fehlgeschlagen.',
    DATEI_UNLESBAR: 'Die Datei konnte nicht gelesen werden.',
    KARTEN_ANGELEGT: (anzahl: number) => `${anzahl} ${anzahl === 1 ? 'Karte' : 'Karten'} angelegt.`,
    ARCHIVIEREN_FEHLER: 'Archivieren fehlgeschlagen.',
    KARTE_LADEN_FEHLER: 'Karte konnte nicht geladen werden.',
    PAPIERKORB_FEHLER: 'In den Papierkorb verschieben fehlgeschlagen.',
    LABELS_SETZEN_FEHLER: 'Labels setzen fehlgeschlagen.',
    // Vorhaben-Massenzuordnung (Plan #1865, E8–E10). Die Sperrgründe stehen klein, weil sie im
    // Namen der Taste hinter „Vorhaben zuordnen — " stehen und bei beiden mit „; " verbunden werden.
    ZUORDNEN_GESPERRT_VORHABEN: 'ein Vorhaben lässt sich keinem Vorhaben zuordnen',
    ZUORDNEN_GESPERRT_KEINE: 'das Board hat kein wählbares Vorhaben',
    ZUORDNEN_TITEL: 'Vorhaben zuordnen?',
    ZUORDNEN_TEXT: (anzahl: number, titel: string) =>
      anzahl === 1
        ? `Die ausgewählte Karte wird dem Vorhaben „${titel}“ zugeordnet.`
        : `${anzahl} Karten werden dem Vorhaben „${titel}“ zugeordnet.`,
    ZUORDNEN_UEBERSCHREIBT: (anzahl: number) =>
      anzahl === 1
        ? 'Davon gehört 1 Karte bereits zu einem anderen Vorhaben; diese Zuordnung wird ersetzt.'
        : `Davon gehören ${anzahl} Karten bereits zu einem anderen Vorhaben; diese Zuordnung wird ersetzt.`,
    ZUORDNEN_BESTAETIGEN: 'Zuordnen',
    ZUORDNEN_ERFOLG: (anzahl: number, titel: string) =>
      `${anzahl} ${anzahl === 1 ? 'Karte' : 'Karten'} dem Vorhaben „${titel}“ zugeordnet.`,
    // Ohne geänderte Karte wurde nichts verdeckt; dann entfällt der Weg zurück.
    ZUORDNEN_ERFOLG_AUSGEBLENDET: (anzahl: number, titel: string) => {
      const satz = `${anzahl} ${anzahl === 1 ? 'Karte' : 'Karten'} dem ausgeblendeten Vorhaben „${titel}“ zugeordnet.`
      if (anzahl === 0) return satz
      return `${satz} ${anzahl === 1 ? 'Sie ist' : 'Sie sind'} auf diesem Board jetzt verdeckt — „Ausgeblendete zeigen“ blendet sie wieder ein.`
    },
    ZUORDNEN_MEHR_UEBERSCHRIEBEN: (anzahl: number) =>
      `Davon ${anzahl === 1 ? 'hatte' : 'hatten'} ${anzahl} bisher ein anderes Vorhaben.`,
    ZUORDNEN_UEBERGANGEN: (anzahl: number) =>
      anzahl === 1
        ? '1 ausgewählte Karte gab es nicht mehr auf diesem Board; sie blieb außen vor.'
        : `${anzahl} ausgewählte Karten gab es nicht mehr auf diesem Board; sie blieben außen vor.`,
    ZUORDNEN_HOECHSTENS: (max: number, gewaehlt: number) => `Höchstens ${max} Karten in einem Zug — gewählt sind ${gewaehlt}`,
    ZUORDNEN_FEHLER: 'Vorhaben zuordnen fehlgeschlagen.',
    KARTEN_FILTERN: 'Karten filtern',
    ALLE_KARTEN: 'Alle Karten',
    MEINE: 'Meine',
    UEBERFAELLIG: 'Überfällig',
    VORHABEN_FILTER: 'Vorhaben-Filter',
    VORHABEN_ALLE: 'Vorhaben: alle',
    VORHABEN: (kuerzel: string, titel: string) => `Vorhaben: ${kuerzel} – ${titel}`,
    DICHTE: 'Dichte',
    DICHTE_NORMAL: 'normal',
    DICHTE_KOMPAKT: 'kompakt',
    AUSWAHL_BEENDEN: 'Auswahl beenden',
    AUSWAEHLEN: 'Auswählen',
    SPEZIFIKATION_EINLESEN: 'Spezifikation einlesen',
    MARKDOWN_WAEHLEN: 'Markdown-Datei auswählen',
    NEU_ANLEGEN: 'Neu anlegen',
    WAAGERECHT_ROLLEN: 'Weitere Spalten: waagerecht rollen',
    SPALTE: 'Spalte',
    NEUE_SPALTE: 'Neue Spalte',
    SPALTE_BEARBEITEN: 'Spalte bearbeiten',
    NAME: 'Name',
    SPALTENNAME: 'Spaltenname',
    WIP_LIMIT_OPTIONAL: 'WIP-Limit (optional)',
    WIP_LIMIT: 'WIP-Limit',
    SPALTE_LOESCHEN_TITEL: 'Spalte löschen?',
    SPALTE_LOESCHEN_TEXT: (name: string) => `Die Spalte „${name}“ wird gelöscht.`,
    BEARBEITEN: 'Bearbeiten',
    DUPLIZIEREN: 'Duplizieren',
    ARCHIVIEREN: 'Archivieren',
    VERSCHIEBEN_DIALOG: 'Verschieben…',
    NACH_LINKS: 'Nach links verschieben',
    NACH_RECHTS: 'Nach rechts verschieben',
    ARCHIVIEREN_TITEL: 'Karten archivieren?',
    ARCHIVIEREN_ANZAHL: (anzahl: number) =>
      anzahl === 1 ? 'Die ausgewählte Karte wird archiviert.' : `${anzahl} Karten werden archiviert.`,
    ARCHIVIEREN_HINWEIS: 'Sie verschwinden aus dem Board, bleiben aber erhalten und lassen sich einzeln wiederherstellen.',
    PAPIERKORB_TITEL: 'In den Papierkorb verschieben?',
    PAPIERKORB_TEXT: (anzahl: number) =>
      anzahl === 1
        ? 'Die Karte wird in den Papierkorb verschoben. Von dort lässt sie sich wiederherstellen oder endgültig löschen.'
        : `${anzahl} Karten werden in den Papierkorb verschoben. Von dort lassen sie sich wiederherstellen oder endgültig löschen.`,
    IN_DEN_PAPIERKORB: 'In den Papierkorb',
    AUSLASTUNG: (spalte: string) => `Auslastung ${spalte}`,
    AUSLASTUNG_WERT: (belegt: number, grenze: number) => `${belegt} von ${grenze}`,
    KARTE_LABELS: (titel: string) => `Labels ${titel}`,
    ZUSTAENDIGE: (titel: string) => `Zuständige ${titel}`,
    STATUS: (name: string) => `Status ${name}`,
    KARTE_AUSWAEHLEN: (titel: string) => `Karte ${titel} auswählen`,
    MENUE: (titel: string) => `Menü ${titel}`,
    AUSWAHL_AUFHEBEN: (spalte: string) => `Auswahl in ${spalte} aufheben`,
    ALLE_AUSWAEHLEN: (spalte: string) => `Alle Karten in ${spalte} auswählen`,
    NACH_NUMMER_SORTIEREN: (spalte: string, aufsteigend: boolean) =>
      `Spalte ${spalte} nach Nummer ${aufsteigend ? 'aufsteigend' : 'absteigend'} sortieren`,
    NACHTLAUF_MARKIEREN: (label: string) => `${label} an alle Karten in Ready setzen`,
    NACHTLAUF_ALLE_MARKIERT: (label: string) => `Alle Karten in Ready tragen ${label} schon`,
    NACHTLAUF_KEINE_KARTE: (label: string) => `In Ready steht keine Karte, die ${label} bekommen kann`,
    AUSGEBLENDET: (anzahl: number) => `${anzahl} ausgeblendet`,
    AUSGEBLENDET_EINBLENDEN: (anzahl: number) =>
      `${anzahl} ${anzahl === 1 ? 'Karte' : 'Karten'} ausgeblendet, einblenden`,
    SPALTE_BEARBEITEN_ARIA: (spalte: string) => `Spalte ${spalte} bearbeiten`,
    SPALTE_LOESCHEN: 'Spalte löschen',
    SPALTE_LOESCHEN_ARIA: (spalte: string) => `Spalte ${spalte} löschen`,
  },

  /** Leiste der Massenaktionen (Issue #1652); die Schlüssel von ZUSTAND sind die Zustände eines Labels in der Auswahl. */
  MASSENAKTION: {
    REGION: 'Massenaktionen',
    AUSGEWAEHLT: (anzahl: number) => `${anzahl} ausgewählt`,
    LABELS: 'Labels',
    LABELS_GESPERRT: (grund: string) => `Labels — ${grund}`,
    ZUSTAND: {
      alle: 'alle gewählten Karten',
      einige: 'einige gewählte Karten',
      keine: 'keine gewählte Karte',
    },
    VORHABEN_ZUORDNEN: 'Vorhaben zuordnen',
    VORHABEN_GESPERRT: (grund: string) => `Vorhaben zuordnen — ${grund}`,
    AUSGEBLENDET: '(ausgeblendet)',
    VERSCHIEBEN: 'Verschieben',
    ARCHIVIEREN: 'Archivieren',
    IN_DEN_PAPIERKORB: 'In den Papierkorb',
  },

  /** Verwaltung der Board-Labels (Issue #1652). */
  LABELVERWALTUNG: {
    TITEL: 'Labels verwalten',
    LEER: 'Noch keine Labels.',
    NEUES_LABEL: 'Neues Label',
    NEUE_FARBE: 'Neue Label-Farbe',
    LABEL_ARIA: (name: string) => `Label ${name}`,
    FARBE_ARIA: (name: string) => `Farbe ${name}`,
    LOESCHEN_ARIA: (name: string) => `Label ${name} löschen`,
    /** Info-Symbol neben dem Namen eines Systemlabels, das dessen Erklärung trägt (Issue #1673). */
    ERKLAERUNG_ARIA: (name: string) => `Erklärung zu ${name}`,
    ZAEHLEN: 'auf der Vorhaben-Kachel zählen',
    ZAEHLEN_ARIA: (name: string) => `${name}: auf der Vorhaben-Kachel zählen`,
    ANLEGEN_FEHLER: 'Label konnte nicht angelegt werden.',
    SPEICHERN_FEHLER: 'Label konnte nicht gespeichert werden.',
    LOESCHEN_FEHLER: 'Label konnte nicht gelöscht werden.',
  },

  /** Papierkorb eines Boards (Issue #1652). */
  PAPIERKORB: {
    TITEL: 'Papierkorb',
    LEER: 'Der Papierkorb ist leer.',
    WIEDERHERSTELLEN_ARIA: (titel: string) => `${titel} wiederherstellen`,
    ENDGUELTIG: 'Endgültig löschen',
    ENDGUELTIG_ARIA: (titel: string) => `${titel} endgültig löschen`,
    WIEDERHERSTELLEN_FEHLER: 'Wiederherstellen fehlgeschlagen.',
    ENDGUELTIG_FEHLER: 'Endgültiges Löschen fehlgeschlagen.',
  },

  /** Import einer Markdown-Spezifikation (Issue #1652). */
  SPEZIFIKATION: {
    TITEL: 'Spezifikation einlesen',
    EBENE: 'Ebene',
    EBENE_ARIA: 'Trennende Überschriftenebene',
    TRENNT: (ebene: number) => `H${ebene} trennt die Karten`,
    SPALTE: 'Spalte',
    ZIELSPALTE: 'Zielspalte',
    NUR_IM_BROWSER:
      'Die Datei wird nur im Browser gelesen — sie wird weder hochgeladen noch verändert oder gelöscht. An den Server gehen ausschließlich die unten gezeigten Karten.',
    DATEI_LEER: 'Die Datei ist leer — es gibt nichts einzulesen.',
    KEINE_UEBERSCHRIFT: (ebene: number, andere: number) =>
      `Keine Überschrift der Ebene H${ebene} gefunden — es wird nichts angelegt. Du kannst oben auf H${andere} umschalten.`,
    ZU_VIELE: (anzahl: number, hoechstens: number) =>
      `${anzahl} Abschnitte ausgewählt — es lassen sich höchstens ${hoechstens} auf einmal anlegen. Bitte einzelne Abschnitte abwählen.`,
    AUSGEWAEHLT: (anzahl: number, gesamt: number) => `${anzahl} von ${gesamt} Abschnitten ausgewählt.`,
    ANLEGEN: (anzahl: number) => `${anzahl} ${anzahl === 1 ? 'Karte' : 'Karten'} anlegen`,
    ABSCHNITT_ARIA: (titel: string) => `Abschnitt „${titel}“ einlesen`,
    TITEL_GEKUERZT: 'Titel gekürzt',
    BESCHREIBUNG_GEKUERZT: 'Beschreibung gekürzt',
    KEINE_BESCHREIBUNG: '(keine Beschreibung)',
    ANLEGEN_FEHLER: 'Die Karten konnten nicht angelegt werden. Bitte erneut versuchen.',
  },

  /** Eigenes Profil (Issue #1651); E-Mail und Anzeigename stehen unter AUTH. */
  PROFIL: {
    TITEL: 'Profil',
    GESPEICHERT: 'Anzeigename gespeichert.',
  },

  /** Ablehnung von `readTextFile` (Issue #1653). */
  DATEI_NICHT_LESBAR: 'Datei konnte nicht gelesen werden.',

  /** Vorschau eines Anhangs (Issue #1653). */
  ANHANG: {
    VORSCHAU_ARIA: (datei: string) => `Vorschau ${datei}`,
    HERUNTERLADEN: 'Herunterladen',
  },

  /** Karten-Detail (Issue #1653); den Fortschritt eines Vorhabens nennt LISTE.FORTSCHRITT. */
  KARTE: {
    BLOCK_BESCHREIBUNG: 'Beschreibung und Details',
    BLOCK_ZUORDNUNG: 'Zuordnung',
    BLOCK_VERLAUF: 'Verlauf',
    AUFGABE_ARIA: (nummer: number) => `Aufgabe ${nummer}`,
    BEARBEITET: (zeitpunkt: string) => `bearbeitet ${zeitpunkt}`,
    ZUSTAENDIGE: 'Zuständige',
    NIEMAND_ZUGEWIESEN: 'Niemand zugewiesen.',
    LABELS: 'Labels',
    KEINE_LABELS: 'Keine Labels.',
    ANHAENGE: 'Anhänge',
    GROESSE_KB: (kb: number) => `${kb} KB`,
    ANHANG_LOESCHEN_ARIA: (datei: string) => `Anhang ${datei} löschen`,
    KEINE_ANHAENGE: 'Keine Anhänge.',
    DATEI_ANHAENGEN: 'Datei anhängen',
    KOMMENTARE: 'Kommentare',
    KOMMENTAR_BEARBEITEN: 'Kommentar bearbeiten',
    KOMMENTAR_LOESCHEN: 'Kommentar löschen',
    KEINE_KOMMENTARE: 'Noch keine Kommentare.',
    KOMMENTAR_SCHREIBEN: 'Kommentar schreiben',
    SENDEN: 'Senden',
    AKTIVITAET: 'Aktivität',
    KEINE_AKTIVITAET: 'Keine Aktivität.',
    SYSTEM: 'System',
    TOKEN: 'Token',
    TOKEN_ARIA: (name: string | null) => `Über Access-Token: ${name ?? 'unbenannt'}`,
    AGENT_HINWEIS: 'Angabe des Clients, nicht verifiziert',
    AGENT_ARIA: (agent: string) => `Modell-Angabe des Clients, nicht verifiziert: ${agent}`,
    VORHABEN: 'Vorhaben',
    STATUS: 'Status',
    READY_FRAGE: 'Status auf Ready setzen?',
    READY_SETZEN: 'Status auf Ready setzen',
    ABHAENGIGKEITEN: 'Abhängigkeiten',
    ABHAENGIG_VON: 'Abhängig von:',
    KARTE_OEFFNEN_ARIA: (nummer: number) => `Karte #${nummer} öffnen`,
    BESCHREIBUNG: 'Beschreibung',
    BESCHREIBUNG_LAEDT: 'Beschreibung wird geladen…',
    BESCHREIBUNG_FEHLER: 'Beschreibung konnte nicht geladen werden.',
    KEINE_BESCHREIBUNG: 'Keine Beschreibung.',
    HERKUNFT: 'Herkunft',
    HERVORGEGANGEN_AUS: (nummer: number) => `Hervorgegangen aus: #${nummer}`,
    FAELLIGKEIT: 'Fälligkeitsdatum',
    FAELLIG_AM: (datum: string) => `Fällig am ${datum}`,
    UEBERFAELLIG: ' — überfällig',
    VORGANG_EROEFFNEN: 'Vorgang eröffnen',
    VORGANG_EROEFFNET: (titel: string) => `Vorgang eröffnet: ${titel}`,
    VORGANG_FEHLER: 'Vorgang eröffnen fehlgeschlagen.',
    VORHABEN_NAME: 'Name des Vorhabens',
    KUERZEL_OPTIONAL: 'Kürzel (optional)',
    EROEFFNEN: 'Eröffnen',
    ZUSTAENDIGE_FEHLER: 'Zuständige speichern fehlgeschlagen.',
    STATUS_FEHLER: 'Status setzen fehlgeschlagen.',
    WIEDERHERSTELLEN_FEHLER: 'Wiederherstellen fehlgeschlagen.',
    LABELS_FEHLER: 'Labels speichern fehlgeschlagen.',
    BAUM_FEHLER: 'Der Herkunftsbaum konnte nicht geladen werden.',
    ABHAENGIGKEITEN_UNGUELTIG: 'Nur positive Nummern, kommagetrennt (z. B. 12, 34).',
    HERKUNFT_UNGUELTIG: 'Nur eine positive Kartennummer, oder leer.',
    GESPEICHERT: 'Karte gespeichert.',
    AUFGABE_FEHLER: 'Aufgabe speichern fehlgeschlagen.',
    KOMMENTAR_ANLEGEN_FEHLER: 'Kommentar anlegen fehlgeschlagen.',
    KOMMENTAR_LOESCHEN_FEHLER: 'Kommentar löschen fehlgeschlagen.',
    KOMMENTAR_SPEICHERN_FEHLER: 'Kommentar speichern fehlgeschlagen.',
    UPLOAD_FEHLER: 'Upload fehlgeschlagen (evtl. Anhangslimit erreicht).',
    VORSCHAU_NACH_UPLOAD_FEHLER: 'Anhang hochgeladen, Vorschau konnte nicht geladen werden.',
    ANHANG_LOESCHEN_FEHLER: 'Anhang löschen fehlgeschlagen.',
    VORSCHAU_FEHLER: 'Vorschau konnte nicht geladen werden.',
    ZURUECK: 'Zurück zur vorherigen Karte',
    ABNAHME: 'Abnahme durchklicken',
    BEARBEITEN: 'Bearbeiten',
    FORTSCHRITT: 'Fortschritt',
    NICHT_GEFUNDEN: (nummer: number) => `Karte #${nummer} nicht gefunden — gelöscht oder kein Zugriff.`,
  },

  /** Anlegen einer Karte oder eines Vorhabens (Issue #1654); die Vorlage nennt die Abschnitte des Kits. */
  NEUE_KARTE: {
    VORLAGE: '## Kontext\n\n## Aufgabe\n\n## Akzeptanzkriterium\n\n## Abhängigkeiten\n',
    TITEL_VORHABEN: 'Neues Vorhaben',
    TITEL_KARTE: (spalte: string) => `Neue Karte in „${spalte}“`,
    ANLEGEN_FEHLER: 'Anlegen fehlgeschlagen.',
    TYP: 'Typ',
    TYP_KARTE: 'Karte',
    TYP_VORHABEN: 'Vorhaben',
    KUERZEL: 'Kürzel',
    KUERZEL_HINWEIS: 'Leer lassen, um es aus dem Titel abzuleiten.',
    TITEL: 'Titel',
  },

  /** Gemeinsame Felder des Anlege- und Bearbeiten-Formulars einer Karte (Issue #1661). */
  KARTENFELDER: {
    VORHABEN: 'Vorhaben',
    KEIN_VORHABEN: '(kein Vorhaben)',
    VORHABEN_NUR_LESEN: 'Vorhaben hier nicht auswählbar (ausgeblendet oder Liste nicht verfügbar) — die Zuordnung bleibt unverändert.',
    ABHAENGIG: 'Abhängig von (Nummern, kommagetrennt)',
    ABHAENGIG_KURZ: 'Abhängig von',
    ABHAENGIG_BEISPIEL: 'z. B. 12, 34',
    HERKUNFT: 'Herkunft (Kartennummer)',
    HERKUNFT_KURZ: 'Herkunft',
    HERKUNFT_HINWEIS: 'Nummer der Karte, aus der diese hervorging — leer = keine',
    FAELLIG: 'Fällig am',
    TITEL: 'Titel',
    BESCHREIBUNG: 'Markdown-Beschreibung',
    KUERZEL: 'Kürzel',
  },

  /** Verschieben einer oder mehrerer Karten (Issue #1654). */
  VERSCHIEBEN: {
    TITEL: (anzahl: number) => (anzahl === 1 ? 'Karte verschieben' : 'Karten verschieben'),
    SUBJEKT: (anzahl: number) => (anzahl === 1 ? 'Die Karte wird' : `Die ${anzahl} Karten werden`),
    EIGENES_BOARD: (subjekt: string) =>
      `${subjekt} in die gewählte Spalte dieses Boards verschoben. Nummer, Vorhaben-Zuordnung, Abhängigkeiten, Kommentare und Anhänge bleiben erhalten.`,
    FREMDES_BOARD: (subjekt: string) =>
      `${subjekt} in das gewählte Board verschoben. Dabei gehen Vorhaben-Zuordnung und Abhängigkeiten verloren; Kommentare und Anhänge bleiben erhalten.`,
    PROJEKT: 'Projekt',
    ZIELPROJEKT: 'Zielprojekt',
    BOARD: 'Board',
    ZIELBOARD: 'Zielboard',
    ZIELSPALTE: 'Zielspalte',
    WAEHLEN: '(wählen)',
    VERSCHIEBEN: 'Verschieben',
  },

  /** Herkunftsbaum eines Vorhabens (Issue #1654); HAKEN nennt den Inhalt der Hakenspalte. */
  BAUM: {
    LEER: 'Diesem Vorhaben sind noch keine Karten zugeordnet.',
    HERKUNFT: 'Herkunft',
    VORHABEN: 'Vorhaben',
    BLOCKIERT: 'blockiert',
    KETTE_UNTERBROCHEN: 'Herkunftskette unterbrochen',
    HERKUNFT_EXTERN: (nummer: number | null) => `Herkunft extern: #${nummer}`,
    EXTERN: (nummer: number) => `⇠ extern #${nummer}`,
    SPRINGEN_ARIA: (nummer: number) => `Zur Karte #${nummer} springen`,
    HAKEN: {
      erledigt: 'erledigt',
      inReview: 'in review',
      abgearbeitet: 'abgearbeitet',
      laden: 'Kit-Namen werden geladen',
      fehler: 'Kit-Namen nicht ladbar',
    },
  },

  /** Abnahme der Szenarien: Dialog und Ergebnis-Kommentar (Issue #1654). */
  ABNAHME: {
    KOMMENTAR_KOPF: '## Abnahme der Szenarien',
    ERGEBNIS_ALLE: (anzahl: number) => `Ergebnis: alle ${anzahl} Szenarien okay.`,
    ERGEBNIS: (teile: string) => `Ergebnis: ${teile}.`,
    OKAY_VON: (okay: number, anzahl: number) => `${okay} von ${anzahl} okay`,
    NICHT_OKAY_LISTE: (liste: string) => `nicht okay: ${liste}`,
    OFFEN_ANZAHL: (anzahl: number) => `offen: ${anzahl}`,
    NOCH_OFFEN: (anzahl: number) => `noch offen: ${anzahl}`,
    OFFEN: 'offen',
    OKAY: 'okay',
    NICHT_OKAY: 'nicht okay',
    ALLE_OKAY: 'Alle Szenarien okay',
    TITEL: (titel: string) => `Abnahme: ${titel}`,
    VERMERK_ARIA: (nummer: number) => `Vermerk zu Szenario ${nummer}`,
    NOTIZ: 'Notiz (optional)',
    SCHREIBEN: 'Ergebnis an den Fachplan schreiben',
    SCHREIBEN_FEHLER: 'Das Ergebnis konnte nicht geschrieben werden.',
    VERWERFEN_TITEL: 'Vermerke verwerfen?',
    VERWERFEN_TEXT: 'Die Vermerke dieser Abnahme gehen verloren.',
    VERWERFEN: 'Verwerfen',
  },

  /** Kartensuche der Kopfzeile (Issue #1654). */
  SUCHE: {
    BEARBEITUNGSDATEN_FEHLER: 'Die Bearbeitungsdaten konnten nicht vollständig geladen werden — die Karte bleibt lesend.',
    KEINE_KARTE: (nummer: number) => `Keine Karte mit der Nummer ${nummer} gefunden.`,
    FEHLER: 'Die Suche ist fehlgeschlagen.',
    NICHT_MEHR_AUFFINDBAR: 'Die Karte ist nicht mehr auffindbar.',
    STAND_FEHLER: 'Der aktuelle Stand konnte nicht geladen werden.',
    NUMMER_EINGEBEN: 'Bitte eine Kartennummer eingeben, z. B. 345 oder #345.',
    KARTE_SUCHEN: 'Karte suchen',
    PLATZHALTER: 'Karte #Nummer',
    NUMMER_SUCHEN: 'Kartennummer suchen',
    LAEUFT: 'Suche läuft',
    TREFFER_WAEHLEN: 'Treffer auswählen',
  },

  /** Kürzel-Schild eines Vorhabens (Issue #1654). */
  VORHABEN_SCHILD: {
    ARIA: (kuerzel: string) => `Vorhaben ${kuerzel}`,
    OEFFNEN_ARIA: (kuerzel: string) => `Vorhaben ${kuerzel} öffnen`,
  },
  /** Seite „Vorhaben“ (Issue #1655). */
  VORHABEN_SEITE: {
    RUECKFRAGE_ENDE: 'Das lässt sich nicht rückgängig machen.',
    RUECKFRAGE_KEINE: (titel: string) => `„${titel}" wird gelöscht. Keine aktive Karte ist direkt zugeordnet.`,
    RUECKFRAGE_EINE: (titel: string) =>
      `„${titel}" wird gelöscht. 1 direkt zugeordnete aktive Karte bleibt erhalten und zeigt danach „(kein Vorhaben)".`,
    RUECKFRAGE_VIELE: (titel: string, anzahl: number) =>
      `„${titel}" wird gelöscht. ${anzahl} direkt zugeordnete aktive Karten bleiben erhalten und zeigen danach „(kein Vorhaben)".`,
    NOCH_NICHT_GELADEN: 'noch nicht geladen',
    LOESCHEN_FEHLER: 'Löschen fehlgeschlagen.',
    UNGUELTIGE_ID: 'Ungültige Board-ID.',
    LADEN_FEHLER: 'Vorhaben konnten nicht geladen werden.',
    PROJEKTE: 'Projekte',
    TITEL: 'Vorhaben',
    NEU: 'Neues Vorhaben',
    AUSGEBLENDETE_ZEIGEN: (anzahl: number) => `Ausgeblendete zeigen (${anzahl})`,
    KEINE: 'Noch keine Vorhaben.',
    LOESCHEN: 'Löschen',
    LOESCHEN_TITEL: 'Vorhaben löschen?',
    ABBRECHEN: 'Abbrechen',
  },

  /** Sichtbarkeitsliste der Vorhaben (Issue #1655). */
  VORHABEN_LISTE: {
    KIT_NAMEN_LADEN: 'Kit-Namen werden geladen',
    KIT_NAMEN_FEHLT: 'Kit-Namen nicht ladbar',
    ANFORDERUNG: 'Anforderung',
    ANFORDERUNGEN: 'Anforderungen',
    PLAN: 'Plan',
    PLAENE: 'Pläne',
    ARBEITSPAKET: 'Arbeitspaket',
    ARBEITSPAKETE: 'Arbeitspakete',
    AUSGEBLENDET: 'Ausgeblendet',
    FERTIG: (done: number, total: number) => `${done} von ${total} fertig`,
    MENUE_ARIA: (titel: string) => `Menü ${titel}`,
    EINBLENDEN_ARIA: (kuerzel: string) => `Vorhaben ${kuerzel} einblenden`,
    AUSBLENDEN_ARIA: (kuerzel: string) => `Vorhaben ${kuerzel} ausblenden`,
    KEINE_ANFORDERUNG: 'Keine Anforderung hinterlegt.',
    ANFORDERUNG_LABEL: 'Anforderung:',
    KEINE_KARTEN: 'Noch keine Karten zugeordnet.',
    FORTSCHRITT_ARIA: (titel: string) => `Fortschritt ${titel}`,
  },

  /** Rechnung des Leitstands, `lib/leitstand.ts` (Issue #1656). */
  LEITSTAND_RECHNUNG: {
    MODUS: {
      CHAIN: 'Kette',
      IMPLEMENTATION: 'Umsetzung',
      REVIEW: 'Prüfung',
      INTERACTIVE: 'Sitzung',
    },
    VORGAENGE: (anzahl: number) => (anzahl === 1 ? '1 Vorgang' : `${anzahl} Vorgänge`),
    PAKETE: (anzahl: number) => (anzahl === 1 ? '1 Paket' : `${anzahl} Pakete`),
    LAUF_ABGESCHLOSSEN: (modus: string, vorgaenge: string) => `${modus} abgeschlossen — ${vorgaenge}`,
    LAUF_LAEUFT: (modus: string) => `${modus} läuft`,
    LAUF_LAEUFT_VORGANG: (modus: string, bearbeitet: number, gesamt: number) =>
      `${modus} läuft — Vorgang ${bearbeitet} von ${gesamt}`,
    SEIT: (uhrzeit: string) => `seit ${uhrzeit}`,
    BEGINN: (zeitpunkt: string) => `Beginn ${zeitpunkt}`,
    KOSTEN: (betrag: string) => `${betrag} $`,
    DAUER_NICHT_GEMESSEN: 'Dauer nicht gemessen',
    DAUER_STUNDEN: (h: number, m: number, s: number) => `Dauer ${h} h ${m} min ${s} s`,
    DAUER_MINUTEN: (m: number, s: number) => `Dauer ${m} min ${s} s`,
    LAUFDAUER_STUNDEN: (h: number, m: number) => `${h} h ${m} min`,
    LAUFDAUER_MINUTEN: (m: number) => `${m} min`,
    KARTE: 'Karte',
    KARTEN: 'Karten',
    KARTEN_ANZAHL: (anzahl: number) => (anzahl === 1 ? '1 Karte' : `${anzahl} Karten`),
    WOCHEN: (anzahl: number) => `${anzahl} Wochen`,
    ZUR_VORWOCHE: (pfeil: string, anzahl: number) => `${pfeil} ${anzahl} zur Vorwoche`,
    TAGE: 'Tage',
    STUNDEN: 'Stunden',
    MINUTEN: 'Minuten',
    MIO: 'Mio',
    TSD: 'Tsd',
    TOKEN: 'Token',
  },

  /** Rechnung der Platten „Fluss“ und „Alter in Arbeit“, `lib/fluss.ts` (Plan #1765). */
  LEITSTAND_FLUSS: {
    BAND: {
      done: 'Done',
      inReview: 'In Review',
      inProgress: 'In Progress',
      ready: 'Ready',
      backlog: 'Backlog',
    },
    TAG_ZEILE: (band: string, anzahl: number) => `${band}: ${anzahl}`,
    KEINE_DATEN: 'keine Daten',
    SEIT: (datum: string) => `seit ${datum}`,
    LETZTE_WOCHEN: 'letzte 12 Wochen',
    UNTER_EINER_STUNDE: 'unter 1 Std',
    STUNDEN: (anzahl: number) => `${anzahl} Std`,
    TAGE: (anzahl: number) => (anzahl === 1 ? '1 Tag' : `${anzahl} Tage`),
    MINDESTENS: (alter: string) => `mindestens ${alter}`,
    /** Die Platte „Fluss“ (Issue #1776). */
    TITEL: 'Fluss',
    NOTIZ: (kopf: string) => `Karten je Zustand am Tagesende · ${kopf}`,
    DONE_HINWEIS: 'Done zählt die seit Beginn des Zeitraums fertig gewordenen Karten.',
    HINWEIS: 'Einen Tag überfahren oder per Tab ansteuern, um seine Zahlen zu sehen.',
    LEGENDE: 'Legende',
    TAGE_GRUPPE: 'Tage im Fluss',
    LEER: 'Für diesen Zeitraum liegen keine Tage vor.',
    TAG_ARIA: (datum: string, zahlen: string) => `${datum}: ${zahlen}`,
    ARIA: (tage: number, datum: string, zahlen: string) =>
      `Fluss über ${tage} Tage, gestapelt von unten Done, In Review, In Progress, Ready, Backlog. Letzter Tag ${datum}: ${zahlen}`,
    /** Die Platte „Alter in Arbeit“ (Issue #1777). */
    ALTER_TITEL: 'Alter in Arbeit',
    ALTER_NOTIZ: 'Bernstein: länger als 85 % der Aufenthalte der letzten 12 Wochen in diesem Zustand',
    ALTER_LEER: (spalte: string) => `Keine Karte in ${spalte}`,
    ALTER_MELDER: 'ungewöhnlich lange',
    ALTER_KARTE_OEFFNEN: (nummer: number, titel: string) => `Karte #${nummer} öffnen: ${titel}`,
  },

  /** Die Platte „Auslieferung“ mit den DORA-Kennzahlen, `lib/auslieferung.ts` (Plan #1831). */
  LEITSTAND_AUSLIEFERUNG: {
    TITEL: 'Auslieferung',
    LETZTE_WOCHEN: 'letzte 12 Wochen',
    SEIT: (datum: string) => `seit ${datum}`,
    LEER: 'noch keine Auslieferung',
    KEINE_GRUNDLAGE: 'keine Grundlage',
    KEIN_AUSFALL: 'kein Ausfall',
    AUSFALL_LAEUFT: (dauer: string) => `Ausfall läuft seit ${dauer}`,
    UNTER_EINER_STUNDE: 'unter 1 Stunde',
    STUNDEN_ANZAHL: (anzahl: number) => (anzahl === 1 ? '1 Stunde' : `${anzahl} Stunden`),
    TAGE_ANZAHL: (anzahl: number) => (anzahl === 1 ? '1 Tag' : `${anzahl} Tage`),
    STUNDEN: 'Stunden',
    TAGE: 'Tage',
    FREQUENZ_FENSTER: (anzahl: number) => (anzahl === 1 ? 'Auslieferung in 12 Wochen' : 'Auslieferungen in 12 Wochen'),
    FREQUENZ_SEIT: (anzahl: number, datum: string) =>
      anzahl === 1 ? `Auslieferung seit ${datum}` : `Auslieferungen seit ${datum}`,
    FREQUENZ: 'Deployment-Frequenz',
    LEAD_TIME: 'Lead Time for Changes',
    CHANGE_FAILURE_RATE: 'Change Failure Rate',
    TIME_TO_RESTORE: 'Time to Restore',
    FREQUENZ_HINWEIS: 'Zählt die erfolgreichen Auslieferungen im Fenster.',
    LEAD_TIME_HINWEIS: 'Median je ausgelieferter Änderung, vom Commit bis zur erfolgreichen Auslieferung.',
    CHANGE_FAILURE_RATE_HINWEIS:
      'Anteil gescheiterter an allen gemeldeten Auslieferungen. Es zählen nur gemeldete Fehlschläge, nicht Fehler, die erst nach einer erfolgreichen Auslieferung auffallen.',
    TIME_TO_RESTORE_HINWEIS:
      'Median der abgeschlossenen Ausfälle, vom ersten gescheiterten Versuch bis zur nächsten erfolgreichen Auslieferung.',
    ERFOLGREICH: 'erfolgreich',
    GESCHEITERT: 'gescheitert',
    WOCHE_ARIA: (kw: number, erfolg: number, fehlschlag: number) =>
      `KW ${kw}: ${erfolg} erfolgreich, ${fehlschlag} gescheitert`,
    WOCHEN_ARIA: (wochen: string) => `Auslieferungsversuche je Woche — ${wochen}`,
  },

  /** Textrechnung der Verbrauchs-Auswertung, `lib/verbrauchZeitraum.ts` (Issue #1656). */
  VERBRAUCH_ZEITRAUM: {
    KEIN_LAUF: 'In diesem Zeitraum hat weder ein Run noch eine Sitzung stattgefunden.',
    NICHT_ERFASST: 'nicht erfasst',
    TEILWEISE_ERFASST: 'teilweise erfasst',
    SCHICHT: (spanne: string) => `Schicht ${spanne}`,
    SPANNE: (von: string, bis: string) => `vom ${von} auf den ${bis}`,
    WOCHE: (von: string, bis: string) => `Woche vom ${von} bis ${bis}`,
    LAEUFE: (anzahl: number) => (anzahl === 1 ? '1 Run' : `${anzahl} Runs`),
    SITZUNGEN: (anzahl: number) => (anzahl === 1 ? '1 Sitzung' : `${anzahl} Sitzungen`),
    KARTEN: (anzahl: number) => (anzahl === 1 ? '1 Karte' : `${anzahl} Karten`),
    NICHT_VERGLEICHBAR: 'nicht vergleichbar — eine der beiden Kostenangaben fehlt',
    UNVERAENDERT: 'genauso teuer wie im Vorzeitraum',
    TEURER: (betrag: string) => `${betrag} teurer als im Vorzeitraum`,
    BILLIGER: (betrag: string) => `${betrag} billiger als im Vorzeitraum`,
    VOR_AUFBEWAHRUNG:
      'Dieser Zeitraum liegt vor dem ältesten aufbewahrten Run — seine Runs sind nicht mehr gespeichert.',
    TEILWEISE:
      'Dieser Zeitraum ist nur teilweise abgedeckt: Er beginnt vor dem ältesten aufbewahrten Run, die Zahlen sind unvollständig.',
    NICHT_GEMESSEN: 'In diesem Zeitraum liefen Runs, ihr Verbrauch liegt aber nicht vor.',
    ERFASSUNG_DAVOR: (seit: string) =>
      `Interaktive Sitzungen werden erst seit dem ${seit} erfasst — dieser Zeitraum liegt ganz davor.`,
    ERFASSUNG_BEGINNT_DAVOR: (seit: string) =>
      `Interaktive Sitzungen werden erst seit dem ${seit} erfasst — dieser Zeitraum beginnt davor.`,
  },

  /** Fortschritt und Stufenleiste eines Laufs, `lib/laufFortschritt.ts` (Issue #1656). */
  LAUF_FORTSCHRITT: {
    ENDE_DES_WEGS: 'Ende des Wegs erreicht',
    STUFE: STUFENNAMEN,
    ZUSTAND: {
      ERREICHT: 'erreicht',
      LAEUFT: 'läuft',
      OFFEN: 'offen',
    },
    VORBEREITUNG: 'Veröffentlichung vorbereitet',
    ZIEL: ', Ziel',
    ANSAGE: (teile: string) => `Nacht-Kette: ${teile}`,
  },

  /** Stand eines laufenden Runs, `lib/aktuellerStand.ts` (Issue #1656). */
  AKTUELLER_STAND: {
    GEMELDET: (anzahl: number) => `${anzahl} gemeldet`,
    LEER: 'Gerade arbeitet kein Run — nichts gemeldet.',
    RUN_ARIA: (nummer: number, projekt: string) => `Run #${nummer} von ${projekt}`,
    GESTARTET: (uhrzeit: string, zustand: string) => ` · gestartet ${uhrzeit} · ${zustand}`,
    SEIT: ' seit ',
    BEENDET_ARIA: (nummer: number) => `Run #${nummer} als beendet kennzeichnen`,
    BEENDET: 'Als beendet kennzeichnen',
    KARTE_OEFFNEN_ARIA: (nummer: number, titel: string) => `Karte #${nummer} öffnen: ${titel}`,
    RUN_ANZEIGEN_ARIA: (titel: string, lauf: number) => `${titel} — Run #${lauf} anzeigen`,
    KARTE_FEHLT: (nummer: number) => `Karte #${nummer} nicht gefunden`,
  },

  /** Angaben eines festgefahrenen Pakets (Issue #1656). */
  FESTGEFAHREN: {
    TITEL: 'Festgefahren',
    PRUEFUNG: 'Prüfung',
    FEHLER: 'Fehler',
    VERSUCHE: 'Versuche',
    LAUFZEIT: 'Laufzeit bis Abbruch',
    ZEITGRENZE: 'Zeitgrenze der Sitzung',
    GESPART: 'geschätzte gesparte Zeit',
  },

  /**
   * Angaben einer gleichzeitigen Nacht, `lib/gleichzeitigkeit.ts` (Issue #1878, Plan #1876 E16).
   * „nicht gemeldet" steht in `NACHTLAUF_UEBERGABE.NICHT_GEMELDET`, nicht hier.
   */
  GLEICHZEITIGKEIT: {
    GLEICHZEITIG: 'Gleichzeitig',
    WARTEZEIT: 'Wartezeit auf die Prüfung',
    VERSCHOBEN: 'Übernahme verschoben',
    JA: 'ja',
    NEIN: 'nein',
    MARKE_GLEICHZEITIG: 'gleichzeitig',
    MARKE_VERSCHOBEN: 'Übernahme verschoben',
    WARTEZEIT_STUNDEN: (h: number, m: number, s: number) => `${h} h ${m} min ${s} s`,
    WARTEZEIT_MINUTEN: (m: number, s: number) => `${m} min ${s} s`,
  },

  /** Bausteine des Leitstands (Issue #1656). */
  LEITSTAND_BAUSTEINE: {
    KEINE_DATENBASIS: 'keine Datenbasis',
    NICHT_GEMESSEN: 'nicht gemessen',
    NACHTPLAN: 'Nachtplan',
  },

  /** Verbrauch mit Zeitraum-Wahl (Issue #1656). */
  LEITSTAND_VERBRAUCH: {
    ZEITRAUM: {
      DAY: 'Schicht',
      WEEK: 'Woche',
      MONTH: 'Monat',
      TOTAL: 'Gesamt',
    },
    TITEL: 'Verbrauch',
    ZEITRAUM_ARIA: 'Zeitraum',
    LAEDT: 'Der Verbrauch wird geladen …',
    FEHLER: 'Der Verbrauch konnte nicht geladen werden.',
    GESAMT: 'Gesamt',
    OHNE_EINTRAG: 'ohne aufbewahrten Eintrag',
    AB: (datum: string) => `ab ${datum}`,
    SITZUNGEN_NICHT_ERFASST: (text: string) => `Sitzungen ${text}`,
    SITZUNGEN_AB: (datum: string) => `Sitzungen ab ${datum}`,
    JE_VORGANG: (wert: string) => `${wert} je Vorgang`,
    ZEITRAUM_GRUPPE_ARIA: 'Verbrauch des Zeitraums',
    EINGABE: 'Eingabe-Token',
    AUFTEILUNG_ARIA: (gelesen: number, frisch: number) =>
      `Aufteilung der Eingabe: ${gelesen} Prozent aus dem Cache gelesen, ${frisch} Prozent frisch`,
    CACHE_GELESEN: 'Cache gelesen',
    FRISCH: 'frisch',
    AUSGABE: 'Ausgabe-Token',
    KOSTEN: 'Kosten',
    OHNE_KARTE: 'ohne Karte',
    BETRAG: (betrag: string) => `${betrag} $`,
    SCHICHTEN: (anzahl: number) => (anzahl === 1 ? '1 Schicht' : `${anzahl} Schichten`),
    WIRD_GELADEN: 'wird geladen',
    NICHT_GELADEN: 'nicht geladen',
    IMPLEMENTIERUNGSZEIT: 'Implementierungszeit',
    BREMSE: 'Bremse',
    BREMSUNGEN: (anzahl: number) => (anzahl === 1 ? 'Bremsung' : 'Bremsungen'),
    GESPART: ' gespart (geschätzt)',
    OHNE_ZEITWERT: (anzahl: number) => `davon ${anzahl} ohne Zeitwert`,
    AUS_RUNS: 'aus Runs',
    AUS_SITZUNGEN: 'aus interaktiven Sitzungen',
  },

  /** Seite „Leitstand“ eines Boards (Issue #1656). */
  LEITSTAND: {
    UNGUELTIGE_ID: 'Ungültige Board-ID.',
    TITEL: 'Leitstand',
    TITEL_BOARD: (name: string) => `Leitstand ${name}`,
    KENNZAHLEN: 'Kennzahlen',
    DURCHSATZ_WOCHE: 'Durchsatz · Woche',
    DURCHLAUFZEIT: 'Durchlaufzeit',
    IMPLEMENTIERUNGSZEIT: 'Implementierungszeit',
    KENNZAHLEN_LAEDT: 'Kennzahlen werden geladen …',
    KENNZAHLEN_FEHLER: 'Kennzahlen konnten nicht geladen werden.',
    JUENGSTER_RUN: 'Jüngster Run',
    ZEIT: 'Zeit',
    KOSTEN: 'Kosten',
    RUN_GRUEN: 'Run · grün',
    ANTEIL_ARIA: (gruen: number, gelb: number, rot: number) => `${gruen} grün, ${gelb} gelb, ${rot} rot`,
    GRUEN: (anzahl: number) => `${anzahl} grün`,
    GELB: (anzahl: number) => `${anzahl} gelb`,
    ROT: (anzahl: number) => `${anzahl} rot`,
    LETZTE_PAKETE: (anzahl: number) => (anzahl === 1 ? 'letztes Paket' : `letzte ${anzahl} Pakete`),
    EINGELIEFERT: (modus: string, zeitpunkt: string) =>
      `Eingeliefert von der ${modus} um ${zeitpunkt} — automatisch, ohne Handgriff im Browser.`,
    HOCHGELADEN: (zeitpunkt: string) => `Im Browser hochgeladen um ${zeitpunkt}.`,
    TOKEN: (name: string) => `Token ${name}`,
    UNGEDEUTET: (vorgaenge: string, anzahl: number) => `${vorgaenge} · ${anzahl} ungedeutete Zeilen`,
    KARTE_FEHLT: (nummer: number) => `Karte #${nummer} nicht gefunden — gelöscht oder kein Zugriff.`,
    KARTE_FEHLER: 'Karte konnte nicht geladen werden.',
    LETZTER_RUN: (modus: string) => `Letzter Run · ${modus}`,
    ALLE: 'Alle',
    NUR_ABBRUECHE: 'Nur Abbrüche',
    KEIN_ABBRUCH_RUN: 'Kein Abbruch in diesem Run.',
    KEIN_PAKET: 'Der Run hat noch kein Arbeitspaket gemeldet.',
    KARTE_OEFFNEN_ARIA: (nummer: number, titel: string) => `Karte #${nummer} öffnen: ${titel}`,
    /** Beschriftung mit den Marken einer gleichzeitigen Nacht (Issue #1880, Plan #1876 E11); ohne Marke wie `KARTE_OEFFNEN_ARIA`. */
    KARTE_OEFFNEN_MIT_MARKEN_ARIA: (nummer: number, titel: string, marken: readonly string[]) =>
      marken.length === 0 ? `Karte #${nummer} öffnen: ${titel}` : `Karte #${nummer} öffnen: ${titel} — ${marken.join(', ')}`,
    DURCHSATZ: 'Durchsatz',
    DURCHSATZ_NOTIZ: (wochen: number) => `abgeschlossene Karten je Woche · ${wochen} Wochen`,
    KARTEN: (anzahl: number) => `${anzahl} Karten`,
    DURCHSATZ_ARIA: (wochen: number, werte: string, zuletzt: number | undefined) =>
      `Durchsatz der letzten ${wochen} Wochen: ${werte} Karten, zuletzt ${zuletzt}`,
    DURCHSATZ_LEER: 'Noch keine abgeschlossene Karte in den letzten Wochen.',
    KEINE_MESSUNG: 'keine Messung',
    IMPLEMENTIERUNG_VERLAUF_TITEL: 'Implementierungszeit (Minuten)',
    IMPLEMENTIERUNG_NOTIZ: (wochen: number) => `Mittelwert je Woche · ${wochen} Wochen`,
    IMPLEMENTIERUNG_ARIA: (wochen: number, werte: string, zuletzt: string | undefined) =>
      `Implementierungszeit der letzten ${wochen} Wochen: ${werte}, zuletzt ${zuletzt}`,
    IMPLEMENTIERUNG_LEER: 'Noch keine gemessene Implementierungszeit in den letzten Wochen.',
    LUECKE: (woche: number) => `KW ${woche}: keine gemessene Karte`,
    ABBRUCHGRUENDE: 'Abbruchgründe',
    RUNS: (anzahl: number) => (anzahl === 1 ? '1 Run' : `${anzahl} Runs`),
    KEIN_ABBRUCH: 'Kein Abbruch in den aufbewahrten Runs.',
    NACHARBEIT: 'Nacharbeit',
    NACHARBEIT_LETZTE_WOCHEN: (wochen: number) => `letzte ${wochen} Wochen`,
    NACHARBEIT_SEIT: (datum: string) => `seit ${datum}`,
    NACHARBEIT_PAKETE: 'Pakete zurückgezogen',
    NACHARBEIT_DOKUMENTE: 'Dokumente angehalten',
    NACHARBEIT_QUOTE: (betroffen: number, bearbeitet: number) => `${betroffen} von ${bearbeitet}`,
    NACHARBEIT_QUOTE_ARIA: (zeile: string, quote: string) => `${zeile}: ${quote} — Karten zeigen`,
    NACHARBEIT_KEINE_RUNS: 'Im Bezugszeitraum gab es keinen Run.',
    NACHARBEIT_KEIN_PAKET: 'Die Runs haben kein Paket bearbeitet.',
    NACHARBEIT_KEIN_DOKUMENT: 'Die Runs haben kein Dokument bearbeitet.',
    NACHARBEIT_ZURUECKGEZOGEN: (anzahl: number) => `${anzahl}× zurückgezogen`,
    NACHARBEIT_ANGEHALTEN: (anzahl: number) => `${anzahl}× angehalten`,
    NACHARBEIT_GRUPPE_PAKETE: 'Pakete',
    NACHARBEIT_GRUPPE_DOKUMENTE: 'Dokumente',
    NACHARBEIT_HINWEIS_QUOTE: (gruppe: string, quote: string) => `${gruppe} ${quote}`,
    NACHARBEIT_HINWEIS_OHNE_KARTE: (gruppe: string) => `${gruppe} keine bearbeitete Karte`,
    NACHARBEIT_HINWEIS_KEINE_DATEN: (gruppe: string) => `${gruppe} keine Daten`,
    NACHARBEIT_KEINE_DATEN: 'keine Daten',
    NACHARBEIT_WOCHE_ARIA: (woche: number, hinweis: string) => `KW ${woche}: ${hinweis}`,
    NACHARBEIT_VERLAUF_ARIA: (wochen: number) => `Verlauf der Nacharbeit über ${wochen} Wochen`,
    VORHABEN: 'Vorhaben',
    OFFEN: 'offen',
    KEIN_VORHABEN: 'Kein offenes Vorhaben.',
    PROGNOSE_ZEILE: (p50: string, p85: string, fruehestens: boolean) => {
      const vor = fruehestens ? 'frühestens' : 'bis'
      return `voraussichtlich fertig: 50 % ${vor} ${p50} · 85 % ${vor} ${p85}`
    },
    PROGNOSE_ZU_WENIG_DURCHSATZ: 'Zu wenig Durchsatz für eine Prognose',
    PROGNOSE_ALLE_UMGESETZT: 'alle Pakete umgesetzt',
    PROGNOSE_ALLE_UMGESETZT_WEITERE: (dokumente: string) => `alle Pakete umgesetzt, weitere folgen aus ${dokumente}`,
    PROGNOSE_KEINE_PAKETE: 'noch keine Pakete geschnitten',
    PROGNOSE_OFFENE_PAKETE: (anzahl: number) => (anzahl === 1 ? '1 offenes Paket' : `${anzahl} offene Pakete`),
    PROGNOSE_ERKLAERUNG: (p50: string, p85: string, offen: number, fruehestens: boolean) => {
      const vor = fruehestens ? 'frühestens' : 'bis'
      const pakete = offen === 1 ? 'das offene Paket' : `alle ${offen} offenen Pakete`
      return (
        `Mit 50 % Wahrscheinlichkeit sind ${vor} ${p50} ${pakete} umgesetzt, mit 85 % ${vor} ${p85}, ` +
        'gerechnet aus dem Durchsatz der letzten 30 Tage, jeweils von 12 bis 12 Uhr.'
      )
    },
    PROGNOSE_NUR_DIESES: 'Gilt, wenn das Board nur an diesem Vorhaben arbeitet.',
    PROGNOSE_WEITERE_VORHABEN: (anzahl: number) => `${anzahl} weitere Vorhaben teilen sich den Durchsatz.`,
    PROGNOSE_AUSSTEHEND: (dokumente: string) => `Weitere Pakete folgen aus ${dokumente}.`,
  },

  /** Seite „Plattform-Leitstand“ (Issue #1657). */
  PLATTFORM_LEITSTAND: {
    KEIN_ADMIN: 'Kein Admin-Zugriff.',
    LADEN_FEHLGESCHLAGEN: 'Laden fehlgeschlagen.',
    KENNZEICHNEN_FEHLER: (lauf: number) => `Run #${lauf} konnte nicht als beendet gekennzeichnet werden.`,
    KARTE_FEHLER: (nummer: number) => `Karte #${nummer} konnte nicht geladen werden.`,
    AKTUELLER_STATUS: 'Aktueller Status',
    BEENDETE_RUNS: 'Beendete Runs',
    DIESE_SCHICHT: 'Begonnen in dieser Schicht',
    VORIGE_SCHICHT: 'Begonnen in der vorigen Schicht',
    KEIN_RUN_DIESER: 'Kein Run dieser Schicht ist beendet.',
    KEIN_RUN_VORIGER: 'Kein Run der vorigen Schicht ist beendet.',
    WEITERE_AUSGEBLENDET: (anzahl: number) =>
      anzahl === 1 ? '1 weiterer Run ausgeblendet' : `${anzahl} weitere Runs ausgeblendet`,
    STOERUNGEN: 'Störungen',
    NACHFRAGE_TITEL: (lauf: number) => `Run #${lauf} als beendet kennzeichnen?`,
    NACHFRAGE_TEXT: (projekt: string) =>
      `Der Run von ${projekt} steht danach unter „Beendete Runs" mit dem Vermerk „von Hand beendet". Seinen Prozess berührt das nicht.`,
    KENNZEICHNEN: 'Kennzeichnen',
    ANZAHL_ALLE: 'alle',
    ANZAHL_ARIA: (wert: string) => `Beendete Runs: ${wert}`,
    RUN: (lauf: number) => `Run #${lauf}`,
    RUN_ARIA: (lauf: number, projekt: string) => `Run #${lauf} von ${projekt}`,
    AUFKLAPPEN: (titel: string) => `${titel} aufklappen`,
    ZUKLAPPEN: (titel: string) => `${titel} zuklappen`,
    RUNS: (anzahl: number) => (anzahl === 1 ? '1 Run' : `${anzahl} Runs`),
    AUSGEBLENDET: (runs: string) => `${runs} ausgeblendet`,
    ZUR_STOERUNG_ARIA: (lauf: number) => `Zur Störung von Run #${lauf}`,
    STOERUNG: 'Störung',
    KEINE_STOERUNG: 'Keine offene Störung.',
    STOERUNGEN_ANZAHL: (anzahl: number) => (anzahl === 1 ? '1 Störung' : `${anzahl} Störungen`),
    KARTE_GRUND: (nummer: number, zustand: string) => `Karte #${nummer}: ${zustand}`,
    LOESCHEN_ARIA: (projekt: string, lauf: number) => `Störung von ${projekt}, Run #${lauf} löschen`,
    STOERUNG_LOESCHEN: 'Störung löschen',
  },

  /** Formatierer der Nachtlauf-Anzeigen, `lib/nachtlaufFormat.ts` (Issue #1658). */
  NACHTLAUF_FORMAT: {
    NICHT_ANGEGEBEN: 'nicht angegeben',
    NICHT_GEMESSEN: 'nicht gemessen',
    TOKEN: (menge: string) => `${menge} Token`,
  },

  /**
   * Zustände, Fehlerklassen, Ausgänge und Übernahmetext eines Laufs, `lib/nightRunHandoff.ts`
   * (Issue #1658). Auszug und Rohprotokoll im Übernahmetext sind Daten und bleiben, wie sie sind.
   */
  NACHTLAUF_UEBERGABE: {
    NICHT_GEMELDET: 'nicht gemeldet',
    ZUSTAND: {
      GREEN: 'Erfolg',
      YELLOW: 'Erfolg, Prüfung rot',
      RED: 'gescheitert',
      GREY: 'nicht bearbeitet',
    },
    FEHLERKLASSE: {
      CHECKS_RED: 'Prüfungen rot',
      CHECKS_NOT_STARTED: 'Prüfungen nicht gelaufen',
      DEPENDENCY_UNMET: 'Abhängigkeit offen',
      UNEXPECTED_STATE: 'Unerwarteter Zustand',
      HARD_ABORT: 'Harter Abbruch',
      AWAITING_DECISION: 'Wartet auf Entscheidung',
      REVIEWER_FAILED: 'Prüf-Session gescheitert',
      TIME_BUDGET_EXCEEDED: 'Zeitbudget erschöpft',
      STUCK: 'Festgefahren',
    },
    AUSGANG: AUSGANGSWORT,
    ZEITBUDGET_MIT_ERGEBNIS: 'Am Zeitbudget beendet, Ergebnis liegt vor',
    ZEITBUDGET_OHNE_ERGEBNIS: 'Am Zeitbudget beendet, ohne Ergebnis',
    FESTGEFAHREN: 'festgefahren',
    KOPF: (karte: string) => `Befund des Runs zu Karte ${karte}`,
    ZUSTAND_ZEILE: (wert: string) => `Zustand: ${wert}`,
    FEHLERKLASSE_ZEILE: (wert: string) => `Fehlerklasse: ${wert}`,
    PRUEFUNG: (wert: string) => `Prüfung: ${wert}`,
    FEHLER: (wert: string) => `Fehler: ${wert}`,
    VERSUCHE: (wert: string) => `Versuche: ${wert}`,
    LAUFZEIT: (wert: string) => `Laufzeit bis Abbruch: ${wert}`,
    ZEITGRENZE: (wert: string) => `Zeitgrenze der Sitzung: ${wert}`,
    GESPART: (wert: string) => `geschätzte gesparte Zeit: ${wert}`,
    AUSZUG: (wert: string) => `Auszug: ${wert}`,
    ROHPROTOKOLL: 'Rohprotokoll:',
  },

  /** Stufenleiste der Nacht-Kette an der Karte, `KettenStufenleiste` (Issue #1658). */
  NACHT_KETTE: {
    TITEL: 'Nacht-Kette',
    KIT_NAMEN_LADEN: 'Kit-Namen werden geladen',
    ZUSTAND: {
      ERBRACHT: 'vor dem Lauf erbracht',
      ZIEL: 'Ziel',
      VORGESEHEN: 'vorgesehen',
      NICHT_VORGESEHEN: 'nicht vorgesehen',
    },
    LABEL_FEHLT: (name: string) =>
      `Am Board fehlt das Label „${name}“. Die Leiste legt es nicht an — ein Mensch legt es in den Board-Labels an.`,
    DURCHZIEHEN: (name: string) =>
      `Die Karte trägt ${name} und läuft damit mindestens bis zur Umsetzung; ein kürzeres Ziel bliebe wirkungslos.`,
    PLAN_KARTE: 'Die Karte ist schon ein Plan: Plan und Prüfung sind vor dem Lauf erbracht.',
    FACHLICH_OHNE_REVIEW: (name: string) => `Die fachliche Prüfung ist nicht abgeschlossen: Die Karte trägt kein ${name}.`,
    PLAN_OHNE_REVIEW: (zeile: string) => `Die Planprüfung ist nicht abgeschlossen: Im Plan fehlt die Zeile „${zeile}“.`,
    OFFENE_FRAGE: (name: string) => `An der Karte wartet eine offene Frage auf einen Menschen (${name}).`,
    GO: 'Mit diesem Ziel gibst du das GO für alle Arbeitspakete dieser Karte.',
    BESTAETIGUNG: 'Damit gibst du das GO für alle Arbeitspakete dieser Karte. Kette starten?',
    VOLLE_KETTE: 'Kein Ziel-Label gesetzt — die Kette läuft bis zur vorbereiteten Veröffentlichung.',
    WARTET: 'Kette gestartet — sie wartet auf die Übernahme durch einen Runner.',
    UEBERNOMMEN: 'Ein Runner hat die Kette übernommen — die Leiste ist nur noch Anzeige.',
    PLAN_REVIEW_DA: (zeile: string) =>
      `Der Plan dieser Anforderung trägt schon „${zeile}“ — die Prüferzahl lässt sich nicht mehr wählen.`,
    LADEFEHLER: (meldung: string) =>
      `Der Stand der Kette ließ sich nicht laden: ${meldung}. Die Leiste zeigt den Stand vor dem Lauf.`,
    KIT_NAMEN_FEHLEN: 'Kit-Namen nicht ladbar',
    ZIEL_DER_KETTE: 'Ziel der Kette',
    NICHT_WAEHLBAR: 'nicht wählbar',
    PLANPRUEFUNG: 'Planprüfung',
    PLANPRUEFUNG_ANGABE: 'Planprüfung:',
    VORGABE: 'Vorgabe des Projekts',
    OHNE_WAHL: 'ohne Wahl gilt die Vorgabe des Projekts',
    PRUEFER: (anzahl: number) => `${anzahl} Prüfer`,
    STAND_DER_KETTE: 'Stand der Kette',
    START_ZURUECKNEHMEN: 'Start zurücknehmen',
    STARTEN: 'Kette starten',
    STARTEN_FRAGE: 'Kette starten?',
  },

  /** Übersicht „Heute Nacht“ auf der Runner-Seite, `HeuteNachtUebersicht` (Issue #1658). */
  HEUTE_NACHT: {
    LEER: 'Für die nächste Nacht ist keine Karte freigegeben.',
    TITEL: 'Heute Nacht',
    LADEFEHLER: (meldung: string) => `Die Übersicht ließ sich nicht laden: ${meldung}`,
    BOARD: (name: string) => `Board: ${name}`,
    ZIEL: (station: string) => `Ziel: ${station}`,
    PRUEFER: (anzahl: number) => `Planprüfung: ${anzahl} Prüfer`,
    STUFENSTAND: (nummer: number) => `Stufenstand von #${nummer}`,
  },

  /** Der Bereich „Runner jetzt“ auf der Runner-Seite, `RunnerJetzt` (Issue #1729, Plan #1724). */
  RUNNER_JETZT: {
    TITEL: 'Runner jetzt',
    LADEFEHLER: (meldung: string) => `Der Stand des Runners ließ sich nicht laden: ${meldung}`,
    ART: {
      CHAIN: 'Kette',
      IMPLEMENTATION: 'Umsetzungsnacht',
      REVIEW: 'Prüflauf',
      INTERACTIVE: 'Interaktive Sitzung',
    },
    LAEUFT: 'läuft',
    /** Das Token, unter dem der Lauf gemeldet wurde (Issue #1742). */
    TOKEN: (name: string) => `Token ${name}`,
    LETZTE_MELDUNG: (zeit: string) => `letzte Meldung ${zeit}`,
    /** Der Satz für „verstummt“ — auch der Kartendialog nutzt ihn (Fachplan #1721, E12). */
    VERSTUMMT: (zeit: string, stufe: string) =>
      `Runner verstummt seit ${zeit} (letzte Stufe: ${stufe}), vermutlich abgebrochen; die Umsetzung gilt womöglich noch als belegt.`,
    NICHT_GEMELDET: 'nicht gemeldet',
    STUFE: {
      PLAN: 'Plan',
      REVIEW: 'Prüfung',
      PAKETE: 'Arbeitspakete',
      ABDECKUNG: 'Abdeckung',
      UMSETZUNG: 'Umsetzung',
      VORBEREITUNG: 'Vorbereitung',
    },
    STUFEN: (lauf: string) => `Stufen: ${lauf}`,
    KARTE_STUFE: (karte: number, stufe: string) => `#${karte} ${stufe}`,
    BEGINN: (zeit: string) => `Beginn ${zeit}`,
    FRIST: (zeit: string) => `Frist ${zeit}`,
    KEINE_FRIST: 'keine Frist vorgesehen',
    SCHRITT: {
      SESSION: 'Sitzung arbeitet',
      WAITING_FOR_IMPLEMENTATION: 'wartet auf freie Umsetzung',
      RELEASE_CHECKS: 'Prüfungen der Veröffentlichung laufen',
    },
    SCHRITT_UNBEKANNT: (code: string) => `Teilschritt: ${code}`,
    HAELT: 'hält die Umsetzung',
    KEIN_RUNNER: 'kein Runner aktiv',
    NIE: 'kein Runner aktiv – noch kein Lauf gestartet',
    LETZTER_LAUF: (art: string, ende: string, ergebnis: string) => `Letzter Lauf: ${art}, beendet ${ende} – ${ergebnis}`,
    OHNE_PAKET: (grund: string) => `ohne Paket beendet – ${grund}`,
  },

  /** Die Platte „Jetzt im Werk“ auf der Runner-Seite, `JetztImWerk` (Issue #1887, Plan #1882 E2). */
  JETZT_IM_WERK: {
    TITEL: (laufend: number) =>
      laufend === 1 ? 'Jetzt im Werk · 1 Run' : `Jetzt im Werk · ${laufend} Runs gleichzeitig`,
    JETZT: (zeit: string) => `jetzt ${zeit}`,
    /** Der Balken: Nummer und Stufe, ohne Nummer die Startzeit. */
    BALKEN_NUMMER: (nummer: number) => `#${nummer}`,
    BALKEN_STUFE: (balken: string, stufe: string) => `${balken} · ${stufe}`,
    /** Die Vorleseliste: „Run #691, seit 10:32, läuft, Umsetzung“. */
    RUN: (nummer: number | undefined) => (nummer === undefined ? 'Run' : `Run #${nummer}`),
    LAEUFT: (run: string, seit: string) => `${run}, seit ${seit}, läuft`,
    MIT_STUFE: (satz: string, stufe: string) => `${satz}, ${stufe}`,
    FERTIG: (run: string, von: string, bis: string) => `${run}, ${von} bis ${bis}, fertig`,
    LEGENDE_LAEUFT: 'läuft',
    LEGENDE_BEENDET: 'beendet, in der Farbe seines Ausgangs',
    LEGENDE_JETZT: 'jetzt',
  },

  /** Der Abschnitt „Wartegrund“ im Kartendialog, `Wartegrund` (Issue #1730, Plan #1724). Den Satz für „verstummt“ liefert `RUNNER_JETZT.VERSTUMMT`. */
  WARTEGRUND: {
    TITEL: 'Wartegrund',
    LADEFEHLER: (meldung: string) => `Der Wartegrund ließ sich nicht laden: ${meldung}`,
    STARTBEREIT: 'startbereit – kein Hinderungsgrund festgestellt',
    KEINE_UMSETZUNGSNACHT: 'keine Umsetzungsnacht aktiv',
    BELEGT_KETTE: (karte: number, stufe: string) => `Umsetzung belegt durch einen anderen Lauf: Kette #${karte}, Stufe ${stufe}`,
    BELEGT_LAUF: (art: string, karte: number) => `Umsetzung belegt durch einen anderen Lauf: ${art} #${karte}`,
    KEIN_ARBEITSPAKET: {
      REQUIREMENT: 'kein Arbeitspaket, sondern eine fachliche Anforderung',
      IDEA: 'kein Arbeitspaket, sondern eine Idee',
      PLAN: 'kein Arbeitspaket, sondern ein Plan',
      HUMAN: 'kein Arbeitspaket, sondern ein Schritt für einen Menschen',
    },
    KEIN_ARBEITSPAKET_SONST: 'kein Arbeitspaket, sondern ein Dokument oder ein Schritt für einen Menschen',
    NO_LABEL: 'nicht für die Nacht freigegeben (kit:nightrun fehlt)',
    DEPENDENCY: (karten: string) => `wartet auf eine andere Karte (${karten})`,
    CLARIFY: 'wartet auf Klärung (kit:klaeren)',
    PROTECTED_LABEL: 'berührt eine geschützte Datei (kit:geschuetzt)',
    PROTECTED_FILE: (pfade: string) => `berührt eine geschützte Datei (${pfade})`,
    REVIEW_MISSING: 'die nötige Prüfung (Review) fehlt',
    PAKETE_DAVOR: (anzahl: number) => `Pakete davor: ${anzahl}`,
    NICHT_BEWERTET: (zeit: string) => `noch nicht vom Runner bewertet (letzte Bewertung ${zeit})`,
    NIE_BEWERTET: 'noch nicht vom Runner bewertet (noch keine Bewertung)',
    SEIT_BEWERTUNG_GEAENDERT: 'seit der Bewertung geändert',
  },

  /** Anläufe einer Karte im Karten-Detail, `KartenAnlaeufe` (Issue #1658). */
  KARTEN_ANLAEUFE: {
    LAUF_ART: {
      IMPLEMENTATION: 'Umsetzung',
      REVIEW: 'Prüfung',
      CHAIN: 'Kette',
      INTERACTIVE: 'Interaktive Sitzung',
    },
    GATTUNG: {
      NIGHT: 'Run',
      INTERACTIVE: 'Interaktive Sitzung',
    },
    SUMME_KOSTEN: 'Kosten (Runs)',
    SUMME_EINGABE: 'Eingabe (Runs)',
    SUMME_AUSGABE: 'Ausgabe (Runs)',
    SUMME_ZWISCHENSPEICHER: 'Zwischenspeicher (Runs)',
    NICHT_GELAUFEN: 'nicht gelaufen',
    NICHT_GEMESSEN: 'nicht gemessen',
    GRUNDLAGE: (summe: string, mit: number, alle: number) => `${summe} (aus ${mit} von ${alle} Anläufen)`,
    TITEL: 'Anläufe dieser Karte',
    LADEFEHLER: 'Die Anläufe konnten nicht geladen werden.',
    DAUER: (art: string) => `Dauer ${art}`,
    WIEDERAUFNAHMEN: (anzahl: number) => `Wiederaufnahmen: ${anzahl}`,
    DAUER_NICHT_GEMESSEN: 'Dauer nicht gemessen',
    KOSTEN: (betrag: string) => `Kosten ${betrag}`,
  },

  /** Morgenkachel „Veröffentlichung vorbereitet“, `MorgenkachelVeroeffentlichung` (Issue #1658). */
  MORGENKACHEL: {
    VEROEFFENTLICHEN: 'Der Stand kann von Hand veröffentlicht werden.',
    ERGEBNIS: {
      GREEN: 'grün',
      GREEN_PENDING: 'grün, Prüfung offen',
      RED: 'rot',
      NOT_PREPARED: 'nicht vorbereitet',
    },
    TITEL: 'Veröffentlichung vorbereitet',
    GEMELDET: (zeit: string) => `gemeldet um ${zeit}`,
    STAND: 'Stand:',
    NICHT_VORBEREITET: 'In diesem Lauf wurde keine Veröffentlichung vorbereitet.',
    OFFENE_PRUEFUNGEN: 'Offene Prüfungen',
    ROTE_PRUEFUNG: 'Fehlgeschlagene Prüfung:',
    BETROFFENE_KARTEN: 'Betroffene Karten',
    ENTHALTENE_PAKETE: 'Enthaltene Arbeitspakete',
    KEINE_PAKETE: 'Enthaltene Arbeitspakete: keine',
  },

  /** Befund eines Abbruchs mit Kopierknopf, `NachtlaufBefund` (Issue #1658). */
  NACHTLAUF_BEFUND: {
    ARIA: (nummer: number) => `Übernahmetext zu Karte #${nummer}`,
    KOPIEREN_ARIA: (nummer: number) => `Übernahmetext zu Karte #${nummer} kopieren`,
    KOPIEREN: 'Kopieren',
  },

  /** Fortschritt eines laufenden Laufs, `NachtlaufFortschritt` (Issue #1658). */
  NACHTLAUF_FORTSCHRITT: {
    PAKETZUSTAND: {
      ANGELEGT: 'angelegt',
      GEZOGEN: 'gezogen',
      IN_UMSETZUNG: 'in Umsetzung',
      FERTIG: 'fertig',
      ZURUECKGESTELLT: 'zurückgestellt',
    },
    ZUORDNUNG_UNBEKANNT:
      'Welche Karten zu diesem Lauf gehören, lässt sich nicht feststellen — der Fortschritt bleibt unbekannt.',
    UNBEKANNT: 'unbekannt',
    OHNE_AUSWEIS: 'Dieser Lauf hat sich nicht ausgewiesen (älterer Runner).',
    WEG_UMSETZUNGSNACHT: 'Weg der Umsetzungsnacht',
    PAKETE_UMSETZUNGSNACHT: 'Pakete der Umsetzungsnacht',
    KEIN_PLAN: 'noch kein Plan',
    WEG_KETTE: (nummer: number) => `Weg der Kette #${nummer}`,
    PAKETE_KETTE: (nummer: number) => `Pakete der Kette #${nummer}`,
    KEINE_PAKETE: 'noch keine Pakete',
    FRAGE: 'Offene Frage an den Menschen an Karte',
    /** Zugänglicher Name und Tooltip eines Pakets im Raster (Plan #1882 E6). */
    PAKET_NAME: (nummer: number, zustand: string, titel: string) => `Paket #${nummer}, ${zustand}: ${titel}`,
    /** Die Zählzeile unter dem Raster, ein Teil je vorkommendem Zustand. */
    RASTER_ANZAHL: (anzahl: number, zustand: string) => `${anzahl} ${zustand}`,
    /** Zugänglicher Name und Tooltip eines Stufenpunkts — „Plan: erreicht". */
    STUFE_NAME: (stufe: string, zustand: string) => `${stufe}: ${zustand}`,
    ENDE_DES_WEGS: 'Ende des Wegs',
    ZEIT_SCHIENE: (verstrichen: number, vorgabe: number) => `${verstrichen} von ${vorgabe} Minuten verstrichen`,
    ZEIT: (verstrichen: number, vorgabe: number) => `${verstrichen} / ${vorgabe} min`,
    ZEIT_UEBER: (ueber: number) => ` · ${ueber} min über der Vorgabe`,
    SEIT: (wert: string, einheit: string) => `seit ${wert} ${einheit}`,
    KOSTEN: 'Kosten der Kette',
    KOSTEN_PLANUNG: 'Planung',
    KOSTEN_GEGEN: (budget: string) => `/ ${budget} $`,
    DOLLAR: '$',
    ART: {
      ANFORDERUNG: 'Anforderung',
      PLAN: 'Plan',
      PAKET: 'Paket',
      FRAGE: 'Frage',
    },
  },

  /** Kartenchips eines Vorgangs, `NachtlaufKartenchips` (Issue #1658). */
  NACHTLAUF_KARTENCHIPS: {
    FORT: (nummer: string) => `#${nummer} nicht mehr vorhanden`,
  },

  /** Lage eines Runs, `NachtlaufLage` (Issue #1658, Plan #1882 E5). */
  NACHTLAUF_INSTRUMENTE: {
    LAGE: 'Lage des Runs',
    BETRAG: (wert: string) => `${wert} $`,
    KOSTEN: 'Kosten',
    AUFTEILUNG: (planung: string, umsetzung: string) => `Planung ${planung} · Umsetzung ${umsetzung}`,
    EINGABE: 'Eingabe',
    AUSGABE: 'Ausgabe',
    CACHE_QUOTE: 'Cache-Quote',
    ANGABE: (titel: string, wert: string) => `${titel} ${wert}`,
    QUOTE: (prozent: number) => `${prozent} %`,
    NICHT_BERECHENBAR: 'nicht berechenbar',
    LAEUFT_SEIT: (wert: string, einheit: string) => `läuft seit ${wert} ${einheit}`,
    PAKETE: 'Pakete',
    GRUEN: 'grün',
    GELB: 'gelb',
    ROT: 'rot',
  },

  /** Zustandssatz der Lage eines Runs, `zustandssatz` in `lib/laufFortschritt.ts` (Plan #1882 E4). */
  NACHTLAUF_LAGE: {
    STUFE: STUFENNAMEN,
    AUSGANG: AUSGANGSWORT,
    SETZT_UM: (nummern: string, mehrere: boolean, fertig: number, gesamt: number) =>
      `Setzt ${mehrere ? 'Pakete' : 'Paket'} ${nummern} um — ${fertig} von ${gesamt} fertig`,
    SETZT_UM_NACHT: (nummern: string, mehrere: boolean, fertig: number) =>
      `Setzt ${mehrere ? 'Pakete' : 'Paket'} ${nummern} um — ${fertig} fertig`,
    UMSETZUNG_NACHT: (fertig: number) => `Umsetzung — ${fertig} fertig`,
    ZU_PLAN: (stufe: string, nummer: number) => `${stufe} zu Plan #${nummer}`,
    ZU_ANFORDERUNG: (stufe: string, nummer: number) => `${stufe} zu Anforderung #${nummer}`,
    ENDE_DES_WEGS: (nummer: number) => `Kette #${nummer} am Ende des Wegs`,
    WEITERE_KETTEN: (anzahl: number) => (anzahl === 1 ? ' + 1 weitere Kette' : ` + ${anzahl} weitere Ketten`),
    ABGESCHLOSSEN: (ausgang: string, gruen: number, gelb: number, rot: number) =>
      `${ausgang} — ${gruen} grün · ${gelb} gelb · ${rot} rot`,
  },

  /** Aufklappbare Platte eines Laufs, `NachtlaufLaufPlatte` (Issue #1658). */
  NACHTLAUF_PLATTE: {
    AUFKLAPPEN: (titel: string) => `${titel} aufklappen`,
    ZUKLAPPEN: (titel: string) => `${titel} zuklappen`,
    ABBRUCHGRUND: 'Abbruchgrund',
  },

  /**
   * Seite „Runner“, die Auswertung der Nachtläufe, `NightRunPage` (Issue #1659). Auszüge,
   * Abbruchgründe, Überspringgründe und ungedeutete Zeilen sind Daten und bleiben, wie sie sind.
   */
  NACHTLAUF_SEITE: {
    GRUNDSATZ: {
      'kein-json': 'Nicht auswertbar',
      'unbekannte-fassung': 'Fassung nicht unterstützt',
      'nicht-unterstuetzt': 'Art des Runs oder Vokabular nicht unterstützt',
    },
    NICHT_GEDEUTET: (wort: string) => `nicht gedeutet: ${wort}`,
    HERKUNFT_NICHT_ANGEGEBEN: 'Herkunft nicht angegeben',
    ERZEUGT_VON: (version: string) => `erzeugt von ${version}`,
    UNVOLLSTAENDIG: 'Run noch nicht abgeschlossen — nicht gespeichert',
    KARTE_NICHT_GEFUNDEN: (nummer: number) => `Karte #${nummer} nicht gefunden`,
    OHNE: 'ohne',
    KIT_NAMEN_LADEN: 'Kit-Namen werden geladen',
    KIT_NAMEN_FEHLEN: 'Kit-Namen nicht ladbar',
    NOCH_NICHT_ERREICHT: 'noch nicht erreicht',
    ABGERISSEN: ' — abgerissen',
    PAKET_AUS_PLAN: 'Paket aus Plan',
    VORHABEN: 'Vorhaben',
    VORHABEN_NICHT_AUFLOESBAR: 'nicht auflösbar',
    HAEUFIGKEIT_FEHLT: (klasse: string) => `${klasse}: Häufigkeit nicht abrufbar`,
    ZUM_ERSTEN_MAL: (klasse: string) => `${klasse}: zum ersten Mal`,
    HAEUFIGKEIT: (klasse: string, anzahl: number, aufbewahrt: number) =>
      `${klasse}: ${anzahl} von ${aufbewahrt} aufbewahrten Runs`,
    KETTEN_STUFE: {
      plan: 'Plan',
      review: 'Prüfung',
      pakete: 'Pakete',
      abdeckung: 'Abdeckung',
    },
    ABSCHLUSS_REGULAER: 'regulär beendet',
    ABSCHLUSS_HARTER_STOPP: 'vorzeitig beendet (harter Stopp)',
    NOCH_NICHT_ABGESCHLOSSEN: 'noch nicht abgeschlossen',
    ABSCHLUSS_NICHT_DEUTBAR: 'Abschluss nicht deutbar',
    EIN_ARBEITSSCHRITT_OHNE_KOSTEN: 'ein Arbeitsschritt ohne Kostenmeldung',
    ARBEITSSCHRITTE_OHNE_KOSTEN: (anzahl: number) => `${anzahl} Arbeitsschritte ohne Kostenmeldung`,
    EIN_VORGANG_OHNE_KOSTEN: 'ein Vorgang ohne Kostenmeldung',
    VORGAENGE_OHNE_KOSTEN: (anzahl: number) => `${anzahl} Vorgänge ohne Kostenmeldung`,
    KEINE_KARTEN: 'keine Karten',
    ABBRUCH: (grund: string, entstanden: string) => `${grund} — bis dahin entstanden: ${entstanden}`,
    NICHT_ANGEGEBEN: 'nicht angegeben',
    AUS_VORGAENGEN: (wert: string, gemeldet: number, alle: number) => `${wert} (aus ${gemeldet} von ${alle} Vorgängen)`,
    BUDGETFELD: {
      PLAN: 'Zeitvorgabe Plan',
      REVIEW: 'Zeitvorgabe Prüfung',
      PAKETE: 'Zeitvorgabe Pakete',
      ABDECKUNG: 'Zeitvorgabe Abdeckung',
      KOSTEN: 'Kostenbudget',
    },
    EINGESTELLT: 'eingestellt',
    AUS_VOREINSTELLUNGEN: 'aus Voreinstellungen',
    AUS_VOREINSTELLUNGEN_MIT: (namen: string) => `aus Voreinstellungen: ${namen}`,
    MINUTEN: (wert: string) => `${wert} min`,
    MODELLARBEIT_ANTEIL: (prozent: number) => `Modellarbeit ${prozent} % der Dauer, der Rest außerhalb`,
    MODELLARBEIT_GLEICHZEITIG: 'Modellarbeit: mehrere Arbeiten liefen gleichzeitig',
    MODELLARBEIT_OHNE_DAUER: 'Modellarbeit: ohne messbare Dauer kein Anteil',
    MODELLZEIT_FEHLT: 'Modellzeit nicht gemeldet',
    DAUER_NICHT_GEMELDET: 'Dauer nicht gemeldet',
    KOSTEN_NICHT_GEMELDET: 'Kosten nicht gemeldet',
    ZUEGE_NICHT_GEMELDET: 'Züge nicht gemeldet',
    ZUEGE: (anzahl: number) => `${anzahl} Züge`,
    EINGABE: (menge: string) => `Eingabe ${menge}`,
    AUSGABE: (menge: string) => `Ausgabe ${menge}`,
    KOSTEN_UNBEKANNT: 'Kosten unbekannt',
    GERECHNET: 'gerechnet, nicht im Protokoll',
    GERECHNET_UNVOLLSTAENDIG: (fehlend: string) => `gerechnet, unvollständig — ${fehlend}`,
    ZUEGE_UNBEKANNT: 'Züge unbekannt',
    VON: (teil: number, ganzes: number) => `${teil} von ${ganzes}`,
    VORGAENGE_ERLEDIGT: 'Vorgänge erledigt',
    DOKUMENTE_ENTSTANDEN: 'Dokumente entstanden',
    KARTEN_BEARBEITET: 'Karten bearbeitet',
    LABEL: (name: string) => `Label ${name}`,
    AM_ZEITBUDGET: 'am Zeitbudget beendet',
    HIER_ABGEBROCHEN: 'hier abgebrochen',
    NICHT_ERREICHT: 'nicht erreicht',
    ZAHLEN_MIT_VORGABE: (verbrauch: string, vorgabe: string) => `${verbrauch} / ${vorgabe} min`,
    ZAHLEN_OHNE_VORGABE: (verbrauch: string) => `${verbrauch} min · ohne Vorgabe`,
    STUFENBAND: (schritte: string) => `Stufenband: ${schritte}`,
    KEIN_PLAN: 'kein Plan',
    KEINE_PAKETE: 'keine Pakete',
    ANTEIL_ANSAGE: (nummer: number, zustand: string, dauer: string) => `Karte #${nummer}: ${zustand}, ${dauer}`,
    ANTEIL_DER_SCHICHT: (anteil: number) => `${anteil} % der Schicht`,
    OHNE_GENANNTEN_GRUND: 'ohne genannten Grund',
    UEBERSPRUNGEN: (anzahl: number, grund: string) => `${anzahl} übersprungen: ${grund}`,
    LIEGENGEBLIEBEN: (anzahl: number) => `${anzahl} liegengeblieben`,
    OHNE_BEKANNTEN_AUSGANG: (anzahl: number) => `${anzahl} ohne bekannten Ausgang`,
    KARTEN_GESICHTET: (anzahl: number) => `${anzahl} Karten gesichtet`,
    BEARBEITET: (anzahl: number) => `${anzahl} bearbeitet`,
    UEBERGANGEN: (anzahl: number) => `${anzahl} übergangen`,
    FUSS: {
      ZEITVORGABEN: 'Zeitvorgaben je Kette',
      KOSTENBUDGET: 'Kostenbudget je Kette',
      HOECHSTE_KOSTEN: 'Höchste Kosten eines Vorgangs',
      HERKUNFT_BUDGETS: 'Herkunft der Budgets',
      EINLIEFERUNG: 'Einlieferung',
      ERGEBNIS: 'Ergebnis der Schicht',
      TEUERSTER: 'Teuerster Vorgang',
      KETTEN_DURCHGELAUFEN: 'Ketten durchgelaufen',
      KARTEN_ENTSTANDEN: 'Karten entstanden',
      LAUFZEIT_STUFEN: 'Laufzeit über alle Stufen',
      KOSTEN_SCHICHT: 'Kosten der Schicht',
      ZUR_KOSTENSUMME: 'Zur Kostensumme',
      LAUFZEIT_VORGAENGE: 'Laufzeit über alle Vorgänge',
      ZUEGE_MODELL: 'Züge des Modells',
      KENNZAHLEN: 'Kennzahlen',
    },
    ERGEBNISSTAND: 'Ergebnisstand',
    TEUERSTER: (nummer: number, betrag: string) => `#${nummer} mit ${betrag}`,
    UNVOLLSTAENDIG_GEMELDET: 'unvollständig gemeldet',
    MASCHINELL_EINGELIEFERT: (zeitpunkt: string) => `maschinell eingeliefert am ${zeitpunkt}`,
    TOKEN: (name: string) => ` (Token: ${name})`,
    HOCHGELADEN: (zeitpunkt: string) => `hochgeladen am ${zeitpunkt}`,
    ZULETZT_GEMELDET: (zeitpunkt: string) => `zuletzt gemeldet am ${zeitpunkt}`,
    HERKUNFT_UNBEKANNT: 'Herkunft unbekannt',
    NEU_ANGELEGT: 'neu angelegt',
    LAG_SCHON_VOR: 'lag schon vor',
    UNGEDEUTETE_ZEILEN: (anzahl: number) => `Ungedeutete Zeilen: ${anzahl}`,
    RUN: 'Run',
    RUN_NUMMER: (nummer: number) => `Run #${nummer}`,
    GRUND: 'Grund: ',
    AUSZUG: 'Auszug: ',
    ENTSTANDEN: 'Entstanden',
    NICHT_GEDEUTETE_ZEILEN: 'Nicht gedeutete Zeilen (Auszug)',
    FORTSCHRITT_NICHT_ABRUFBAR: 'Fortschritt nicht abrufbar',
    KARTE_NICHT_ABRUFBAR: 'Karte nicht abrufbar.',
    DATEI_NICHT_LESBAR: 'Die Datei konnte nicht gelesen werden.',
    EINLIEFERN_FEHLGESCHLAGEN: 'Einliefern fehlgeschlagen.',
    LISTE_NICHT_AKTUALISIERT: 'Eingeliefert, Liste konnte nicht aktualisiert werden.',
    PROTOKOLL_EINLESEN: 'Protokoll einlesen',
    PROTOKOLLDATEI_WAEHLEN: 'Protokolldatei auswählen',
    KEINE_AUSWERTUNG: 'Noch keine Auswertung vorhanden.',
    KEIN_RUN: 'In den letzten zwei Schichten gab es keinen Run.',
    NUR_ZWEI_SCHICHTEN: 'Nur die letzten zwei Schichten zeigen',
    AELTERE_RUNS: (anzahl: number) => `Ältere Runs anzeigen (${anzahl})`,
  },
  /** Administration (Issue #1660): Rechtliches, Editiermodus, Done-Aufbewahrung, API-Tokens und Einstieg in die API-Übersicht. */
  ADMINISTRATION: {
    TITEL: 'Administration',
    RECHTLICHES: 'Rechtliches',
    COPYRIGHT: '© 2026 Manfred Wolff',
    LIZENZ: (appName: string) =>
      `${appName} steht unter der MIT-Lizenz. Nutzung, Änderung und Weitergabe sind gestattet, sofern Copyright-Vermerk und Lizenztext erhalten bleiben. Die Software wird ohne Gewähr bereitgestellt.`,
    EDITIERMODUS: 'Editiermodus',
    EDITIERMODUS_ARIA: 'Editiermodus aktivieren',
    BEARBEITEN_AKTIVIEREN: 'Bearbeiten aktivieren',
    EDITIERMODUS_HINWEIS:
      'Ist der Editiermodus aktiv, erscheinen die Bearbeiten-Symbole (etwa zum Umbenennen von Projekten, Boards und Spalten), sofern du die nötigen Rechte hast. Der Modus ist beim Start immer aus und wird nicht über einen Neustart hinweg gemerkt.',
    ERSCHEINUNGSBILD: 'Erscheinungsbild',
    ERSCHEINUNGSBILD_SYSTEM: 'System',
    ERSCHEINUNGSBILD_HELL: 'Hell',
    ERSCHEINUNGSBILD_DUNKEL: 'Dunkel',
    ERSCHEINUNGSBILD_HINWEIS:
      '„System“ folgt dem Farbschema deines Browsers, der das Betriebssystem übersteuern kann. Die Wahl wird an deinem Konto gespeichert und gilt auf jedem Gerät nach der Anmeldung.',
    ERSCHEINUNGSBILD_FEHLER: 'Das Erscheinungsbild konnte nicht gespeichert werden.',
    DONE_AUFBEWAHRUNG: 'Done-Aufbewahrung',
    DONE_UNGUELTIG: 'Bitte eine ganze Zahl ≥ 0 angeben (0 = kein Auto-Archiv).',
    GESPEICHERT: 'Gespeichert.',
    DONE_HINWEIS: 'Erledigte Karten werden nach dieser Anzahl Tage automatisch archiviert.',
    DONE_NULL: '0 = kein Auto-Archiv.',
    DONE_GLOBAL: 'Der Wert gilt global.',
    AKTUELL_WIRKSAM: 'Aktuell wirksam: ',
    KEIN_AUTO_ARCHIV: 'kein Auto-Archiv',
    TAGE_WERT: (tage: number) => `${tage} Tage`,
    TAGE: 'Tage',
    DONE_TAGE_ARIA: 'Done-Aufbewahrung in Tagen',
    API_TOKENS: 'API-Tokens',
    TOKEN_ERZEUGEN: 'Token erzeugen',
    BINDUNG: (projekt: string, boardId: number) => `Projekt „${projekt}“ · Board ${boardId}`,
    UNGEBUNDEN: 'ungebunden',
    WIDERRUFEN_FRAGE: 'Dieses Token widerrufen? Clients, die es nutzen, verlieren den Zugriff.',
    WIDERRUFEN_FEHLGESCHLAGEN: 'Widerrufen fehlgeschlagen.',
    TOKEN_HINWEIS:
      'Ein board-gebundenes Token treibt ein Board über die API (z. B. aus einer CLI oder CI-Pipeline), ohne Login — gebunden an genau ein Board. Der Klartext wird beim Erzeugen',
    NUR_EINMAL: 'nur einmal',
    ANGEZEIGT: 'angezeigt.',
    KEIN_PROJEKT:
      'Du bist in keinem Projekt, in dem du Karten anlegen darfst — daher kannst du kein board-gebundenes Token erzeugen.',
    TOKEN_KOPIEREN: 'Token kopieren',
    KOPIERT: 'Kopiert',
    KOPIEREN: 'Kopieren',
    TOKEN_ERZEUGT: (name: string) => `Token „${name}“ erzeugt — wird nur einmal angezeigt:`,
    TOKEN_KLARTEXT: 'Token-Klartext',
    KEINE_TOKENS: 'Noch keine Tokens.',
    WIDERRUFEN: 'Widerrufen',
    AKTIV: 'Aktiv',
    TOKEN_WIDERRUFEN_ARIA: (name: string) => `Token ${name} widerrufen`,
    API_SCHNITTSTELLE: 'API-Schnittstelle',
    API_HINWEIS:
      'Alle Aufrufe des Leitstands mit Zweck, Eingaben, Antworten und Stabilitätsangabe — zum Nachschlagen, ohne Ausprobieren. Die Beschreibung lässt sich als OpenAPI 3 herunterladen.',
    UEBERSICHT_OEFFNEN_ARIA: 'API-Übersicht öffnen',
    UEBERSICHT_OEFFNEN: 'Übersicht öffnen',
    NEUES_TOKEN: 'Neues API-Token',
    NAME: 'Name',
    PROJEKT: 'Projekt',
    BOARD: 'Board',
    PROJEKT_WAEHLEN: '(Projekt wählen)',
    BOARD_WAEHLEN: '(Board wählen)',
    ERZEUGEN: 'Erzeugen',
    ERZEUGEN_FEHLGESCHLAGEN:
      'Token konnte nicht erzeugt werden. Für ein board-gebundenes Token brauchst du das Recht, auf diesem Board Karten anzulegen.',
  },

  /** Downloads der OpenAPI-Beschreibung, in Administration und API-Übersicht (Issue #1660). */
  OPENAPI: {
    JSON: 'OpenAPI (JSON)',
    YAML: 'OpenAPI (YAML)',
    JSON_ARIA: 'OpenAPI als JSON herunterladen',
    YAML_ARIA: 'OpenAPI als YAML herunterladen',
  },

  /** Admin-Bereich mit der Nutzerverwaltung (Issue #1660); die Plattform-Rollen USER/ADMIN sind Schlüssel. */
  ADMIN_SEITE: {
    TITEL: 'Admin — Nutzerverwaltung',
    KEIN_ZUGRIFF: 'Kein Admin-Zugriff.',
    LADEN_FEHLGESCHLAGEN: 'Laden fehlgeschlagen.',
    ROLLE_FEHLGESCHLAGEN: 'Rollenänderung fehlgeschlagen.',
    FREIGABE_FEHLGESCHLAGEN: 'Freigabe fehlgeschlagen.',
    NAME_FEHLGESCHLAGEN: 'Namensänderung fehlgeschlagen.',
    ENTSPERREN_FEHLGESCHLAGEN: 'Entsperren fehlgeschlagen.',
    SPERREN_FEHLGESCHLAGEN: 'Sperren fehlgeschlagen.',
    ANZEIGENAME_ARIA: (email: string) => `Anzeigename von ${email}`,
    NAMEN_SPEICHERN: 'Namen speichern',
    BEARBEITEN_ABBRECHEN: 'Bearbeiten abbrechen',
    NAMEN_BEARBEITEN_ARIA: (name: string) => `Namen von ${name} bearbeiten`,
    FREIGEBEN_ARIA: (name: string) => `${name} freigeben`,
    FREIGEBEN: 'Freigeben',
    ROLLE_UMSCHALTEN_ARIA: (name: string) => `Rolle von ${name} umschalten`,
    ZU_ROLLE: (rolle: string) => `Zu ${rolle}`,
    ENTSPERREN_ARIA: (name: string) => `${name} entsperren`,
    SPERREN_ARIA: (name: string) => `${name} sperren`,
    ENTSPERREN: 'Entsperren',
    SPERREN: 'Sperren',
    NAME: 'Name',
    EMAIL: 'E-Mail',
    VERIFIZIERT: 'Verifiziert',
    JA: 'ja',
    NEIN: 'nein',
    FREIGABE: 'Freigabe',
    FREIGEGEBEN: 'Freigegeben',
    WARTET_AUF_FREIGABE: 'Wartet auf Freigabe',
    STATUS: 'Status',
    GESPERRT: 'Gesperrt',
    AKTIV: 'Aktiv',
    AKTION: 'Aktion',
  },

  /** Statuskachel der Sicherung im Admin-Bereich (Issue #1660); das Ziel ist ein Datenwert. */
  SICHERUNG: {
    TITEL: 'Sicherung',
    ZUSTAND: {
      OK: 'Läuft',
      VERALTET: 'Veraltet',
      FEHLGESCHLAGEN: 'Fehlgeschlagen',
      ABGESCHALTET: 'Abgeschaltet',
    },
    BEREICH: (zustand: string) => `Sicherung — ${zustand}`,
    LADEN_FEHLGESCHLAGEN: 'Stand der Sicherung konnte nicht geladen werden.',
    LAEDT: 'Stand der Sicherung wird geladen',
    LETZTER_LAUF: (zeitpunkt: string) => `Letzter gelungener Lauf: ${zeitpunkt}`,
    NOCH_KEINER: 'noch keiner',
    ZIEL: (ziel: string) => `Ziel: ${ziel}`,
    NICHT_EINGERICHTET: 'Keine Sicherung eingerichtet — es wird nichts gesichert, und es ist nichts zurückzuholen.',
    EINRICHTEN: 'Sicherung einrichten',
    MAIL_AUS:
      'Der Mailversand ist abgeschaltet: Ein Alarm über eine ausbleibende Sicherung landet nur im Protokoll. Diese Ansicht ist der verlässliche Weg.',
  },

  /** Abschnitt „Abweisungen wegen Last“ im Admin-Bereich (Issue #1660). */
  ABWEISUNGEN: {
    TITEL: 'Abweisungen wegen Last',
    LAEDT: 'Abweisungen werden geladen',
    FEHLER: 'Abweisungen wegen Last konnten nicht geladen werden.',
    LEER: 'Keine Abweisungen wegen Last in den letzten 90 Tagen.',
    PERSON: 'Person',
    STUNDE: 'Stunde',
    ANZAHL: 'Anzahl',
  },

  /** Info-Symbol neben einem Element mit eigener Aktion, das auf dem Tablet quer die Erklärung öffnet (Plan #1707, E7). */
  ERKLAERUNG_INFO: {
    ZEIGEN: (name: string) => `Erklärung zu ${name}`,
  },

  /** Standard-Labels der Installation in der Administration (Issue #1660); Label- und Gruppennamen sind Daten. */
  STANDARD_LABELS: {
    TITEL: 'Standard-Labels',
    HINWEIS:
      'Legt die Labels, die Kit, Laufsteuerung und Stufenleiste erwarten, auf allen Boards aller Projekte an. Fehlende kommen dazu, vorhandene bleiben, wie sie sind — der Knopf lässt sich beliebig oft drücken.',
    KNOPF: 'Standard-Labels für alle Boards',
    ERGEBNIS: (boards: number, angelegt: number, uebersprungen: number) =>
      `Auf ${boards} Boards: ${angelegt} Labels neu angelegt, ${uebersprungen} schon vorhanden.`,
    DIALOG_HINWEIS:
      'Diese Labels werden auf allen nicht archivierten Boards aller Projekte angelegt. Vorhandene Labels bleiben unverändert und werden übersprungen.',
    LADEN_FEHLGESCHLAGEN: 'Die Standard-Labels konnten nicht geladen werden.',
    ANLEGEN_FEHLGESCHLAGEN: 'Die Standard-Labels konnten nicht angelegt werden.',
  },

  /**
   * Erklärungen der Systemlabels (Issue #1671, Plan #1668 E1, E2): je Sache des Namen-Registers
   * Bedeutung, wer setzt und wann es verschwindet, `handlung` nur, wo der Mensch etwas tun soll;
   * je Schutzregel des Servers ein Satz (E3), bei `KEINER` keiner. Quelle: Kit-Dokumentation 5.0
   * und `docs/nutzung.md`, im Zweifel weggelassen statt geraten (E12).
   */
  LABEL_ERKLAERUNG: {
    SACHEN: {
      DURCHZIEHEN: {
        bedeutung:
          'Die Nacht-Kette setzt die Arbeitspakete dieser Karte nach der Abdeckung selbst um. Das Label ist das GO für alle ihre Pakete.',
        setzt: 'Ein Mensch, an der fachlichen Anforderung oder am Plan.',
        verschwindet: 'Nur, wenn ein Mensch es abnimmt; danach endet die Kette wieder nach der Abdeckung.',
      },
      GESCHUETZT: {
        bedeutung:
          'Das Arbeitspaket ändert eine geschützte Datei, die nur ein Mensch schreiben darf. Es wurde darum nicht umgesetzt und liegt im Backlog.',
        setzt: 'Die Umsetzung des Kits, wenn das Paket eine geschützte Datei nennt oder ein Schreibzugriff darauf abgewiesen wird.',
        verschwindet:
          'Nur, wenn ein Mensch es abnimmt, nachdem er die Änderung an der Datei selbst vorgenommen hat. Erst dann gilt das Paket als freigegeben.',
        handlung: 'Nimm die Änderung an der genannten Datei selbst vor und nimm danach das Label ab.',
      },
      KLAEREN: {
        bedeutung:
          'An der Karte wartet eine offene Frage auf einen Menschen. Umsetzung, Prüflauf und Nacht-Kette arbeiten an ihr nicht weiter.',
        setzt: 'Eine Sitzung des Kits, der Prüflauf oder die Nacht-Kette, wenn sie an einer Frage anhalten, die nur ein Mensch beantworten kann.',
        verschwindet: 'Nur, wenn ein Mensch es abnimmt; keine Maschine nimmt es ab.',
        handlung: 'Beantworte die Frage im Kommentar und nimm das Label ab.',
      },
      GEKLAERT: {
        bedeutung:
          'An dieser fachlichen Anforderung ist keine Frage an den PO mehr offen. Es ist eine Hilfe für den Menschen, kein Kriterium der Nacht-Kette.',
        setzt: '/issue-review oder /groom, sobald unter „Offene Fragen an den PO“ „Keine“ steht.',
        verschwindet: 'Nur, wenn ein Mensch es abnimmt; das Kit nimmt es nie ab.',
      },
      NIGHT: {
        bedeutung:
          'Gibt die Karte einmal für die Nacht-Kette frei: Der nächste Runner übernimmt sie. Jedes Setzen erlaubt genau eine Kette.',
        setzt: 'Ein Mensch, etwa mit „Kette starten“ in der Stufenleiste der Karte.',
        verschwindet:
          'Der Runner nimmt es ab, sobald er die Karte übernimmt. Vorher kann ein Mensch den Start mit „Start zurücknehmen“ widerrufen.',
      },
      NIGHTRUN: {
        bedeutung:
          'Gibt ein Arbeitspaket in Ready für den Nacht-Runner frei. Pakete ohne dieses Label lässt der Runner liegen.',
        setzt: 'Ein Mensch.',
        verschwindet: 'Der Runner nimmt es ab, sobald es verbraucht ist; ein Mensch kann es jederzeit abnehmen.',
      },
      PRUEFEN: {
        bedeutung:
          'Meldet eine fachliche Anforderung für den Prüflauf an: Modelle, die sie nicht geschrieben haben, prüfen sie mit /issue-review.',
        setzt: 'Ein Mensch, an einer Karte mit dem Titel-Präfix [Requirement].',
        verschwindet: 'Der Prüflauf nimmt es unmittelbar vor der Prüfung der Karte ab; jedes Setzen erlaubt genau eine Prüfung.',
      },
      NIGHTPLAN: {
        bedeutung:
          'Ein altes Steuerlabel der Nacht-Kette, das es seit Stufe 2 des Kits nicht mehr gibt. Es hat keine Wirkung; die Kette startet mit kit:night.',
        setzt: 'Niemand mehr; es stammt aus einer früheren Fassung des Kits.',
        verschwindet: 'Nur, wenn ein Mensch es abnimmt. Die Nacht-Kette weist in ihrem Protokoll darauf hin.',
        handlung: 'Nimm das Label ab. Soll die Kette laufen, setze kit:night.',
      },
      NIGHTREVIEW: {
        bedeutung:
          'Ein altes Steuerlabel der Nacht-Kette, das es seit Stufe 2 des Kits nicht mehr gibt. Es hat keine Wirkung; die Kette startet mit kit:night.',
        setzt: 'Niemand mehr; es stammt aus einer früheren Fassung des Kits.',
        verschwindet: 'Nur, wenn ein Mensch es abnimmt. Die Nacht-Kette weist in ihrem Protokoll darauf hin.',
        handlung: 'Nimm das Label ab. Soll die Kette laufen, setze kit:night.',
      },
      NIGHTISSUES: {
        bedeutung:
          'Ein altes Steuerlabel der Nacht-Kette, das es seit Stufe 2 des Kits nicht mehr gibt. Es hat keine Wirkung; die Kette startet mit kit:night.',
        setzt: 'Niemand mehr; es stammt aus einer früheren Fassung des Kits.',
        verschwindet: 'Nur, wenn ein Mensch es abnimmt. Die Nacht-Kette weist in ihrem Protokoll darauf hin.',
        handlung: 'Nimm das Label ab. Soll die Kette laufen, setze kit:night.',
      },
      WARTET_EREIGNIS: {
        bedeutung: 'Der Fachplan wartet auf ein äußeres Ereignis. Die Nacht-Kette baut nichts aus ihm.',
        setzt: 'Das Kit.',
        verschwindet: 'Nur, wenn ein Mensch es abnimmt; damit hebt er das Warten auf.',
        handlung: 'Nimm das Label ab, sobald das Ereignis eingetreten ist.',
      },
      LAUF_ABGEBROCHEN: {
        bedeutung:
          'Ein Lauf des Nacht-Runners ist an dieser Karte abgebrochen; der Kommentar „Laufstand“ nennt den Stand. Wiederholen genügt.',
        setzt: 'Der Nacht-Runner.',
        verschwindet:
          'Der Runner tauscht es aus, wenn sich der Laufstand der Karte ändert; eine Karte trägt höchstens eines der drei Lauf-Labels.',
        handlung: 'Wiederhole den Lauf mit derselben Geste, bei einer Kette mit kit:night an der Karte.',
      },
      LAUF_LAEUFT: {
        bedeutung: 'Ein Lauf des Nacht-Runners bearbeitet diese Karte gerade; der Kommentar „Laufstand“ zeigt den Stand.',
        setzt: 'Der Nacht-Runner.',
        verschwindet:
          'Der Runner tauscht es aus, wenn sich der Laufstand der Karte ändert; eine Karte trägt höchstens eines der drei Lauf-Labels.',
      },
      LAUF_WARTET: {
        bedeutung: 'Der Lauf an dieser Karte hält an und wartet. Worauf, steht wörtlich im Kommentar „Laufstand“.',
        setzt: 'Der Nacht-Runner.',
        verschwindet:
          'Der Runner tauscht es aus, wenn sich der Laufstand der Karte ändert; eine Karte trägt höchstens eines der drei Lauf-Labels.',
        handlung: 'Lies im Laufstand, worauf der Lauf wartet, und erledige das; weiter geht es mit kit:night an der Karte.',
      },
      REVIEW_OFFEN: {
        bedeutung:
          'Kennzeichen des früheren Prüf-Laufs, der eine Karte damit zur Prüfung anmeldete. Das Kit 5.0 kennt diesen Lauf nicht mehr; zur Prüfung meldet heute kit:check an.',
        setzt: 'Niemand mehr; es stammt aus einer früheren Fassung des Kits.',
        verschwindet: 'Nur, wenn ein Mensch es abnimmt.',
      },
      REVIEW_FERTIG: {
        bedeutung:
          'Die Karte wurde mit /issue-review geprüft. Das ist eine Spur, keine Freigabe; die Nacht-Kette verlangt sie aber an fachlicher Anforderung und Plan.',
        setzt: '/issue-review nach einer abgeschlossenen Prüfung, auch im Prüflauf und in der Nacht-Kette.',
        verschwindet: 'Es bleibt als Spur der Prüfung stehen; ein Mensch kann es abnehmen.',
      },
      ZIEL_PLAN: {
        bedeutung: 'Ziel der Nacht-Kette an dieser Karte: Sie endet nach Plan und Planprüfung.',
        setzt: 'Ein Mensch, über die Stufenleiste der Karte.',
        verschwindet:
          'Der Runner nimmt es zusammen mit kit:night ab, wenn er die Karte übernimmt. Vorher ersetzt ein anderes Ziel in der Stufenleiste es.',
      },
      ZIEL_PAKETE: {
        bedeutung:
          'Ziel der Nacht-Kette an dieser Karte: Sie endet nach den Arbeitspaketen und der Abdeckungsprüfung. Ohne gewähltes Ziel gilt dieses.',
        setzt: 'Ein Mensch, über die Stufenleiste der Karte.',
        verschwindet:
          'Der Runner nimmt es zusammen mit kit:night ab, wenn er die Karte übernimmt. Vorher ersetzt ein anderes Ziel in der Stufenleiste es.',
      },
      ZIEL_UMSETZUNG: {
        bedeutung:
          'Ziel der Nacht-Kette an dieser Karte: Sie setzt die Arbeitspakete auch um. Das Ziel ist zugleich das GO für alle Pakete dieser Karte.',
        setzt: 'Ein Mensch, über die Stufenleiste der Karte, mit ausdrücklicher Bestätigung.',
        verschwindet:
          'Der Runner nimmt es zusammen mit kit:night ab, wenn er die Karte übernimmt. Vorher ersetzt ein anderes Ziel in der Stufenleiste es.',
      },
      ZIEL_PUSH_VORBEREITET: {
        bedeutung:
          'Ziel der Nacht-Kette an dieser Karte: Sie setzt die Pakete um und bereitet die Veröffentlichung vor; veröffentlicht wird von Hand. Das Ziel ist zugleich das GO für alle Pakete dieser Karte.',
        setzt: 'Ein Mensch, über die Stufenleiste der Karte, mit ausdrücklicher Bestätigung.',
        verschwindet:
          'Der Runner nimmt es zusammen mit kit:night ab, wenn er die Karte übernimmt. Vorher ersetzt ein anderes Ziel in der Stufenleiste es.',
      },
      PLANREVIEW_1: {
        bedeutung: 'Ein Modell prüft den Plan, den die Nacht-Kette aus dieser fachlichen Anforderung macht.',
        setzt: 'Ein Mensch, über den Schalter „Planprüfung“ der Stufenleiste. Ohne Wahl gilt die Vorgabe des Projekts.',
        verschwindet: 'Der Runner nimmt es zusammen mit kit:night ab, wenn er die Karte übernimmt.',
      },
      PLANREVIEW_2: {
        bedeutung: 'Zwei Modelle prüfen den Plan, den die Nacht-Kette aus dieser fachlichen Anforderung macht.',
        setzt: 'Ein Mensch, über den Schalter „Planprüfung“ der Stufenleiste. Ohne Wahl gilt die Vorgabe des Projekts.',
        verschwindet: 'Der Runner nimmt es zusammen mit kit:night ab, wenn er die Karte übernimmt.',
      },
    },
    SCHUTZ: {
      NUR_MENSCH_SETZT: 'Setzen geht nur angemeldet im Leitstand, nicht über ein Token.',
      NUR_MENSCH_NIMMT_AB: 'Abnehmen geht nur angemeldet im Leitstand, nicht über ein Token.',
    },
  },

  /** Seitenrahmen der API-Übersicht und Ausprobieren (Issue #1660, E2); Swagger UI und das OpenAPI-Dokument bleiben, wie sie sind. */
  API_UEBERSICHT: {
    TITEL: 'API-Schnittstelle',
    ZURUECK: 'Zurück zur Administration',
    LADEN_FEHLGESCHLAGEN: 'Die API-Beschreibung konnte nicht geladen werden.',
    LAEDT: 'API-Beschreibung wird geladen',
    ERKLAERUNG:
      'Aufrufe mit Anmeldung laufen mit Ihrer Sitzung; ein Projekt-Token geben Sie unter ‚Authorize‘ bei projektToken an.',
    ABGEBROCHEN: 'Abgebrochen, nichts gesendet',
    HINWEIS_AENDERND: 'Dieser Aufruf verändert echte Daten dieser Umgebung.',
    BESTAETIGUNG_TITEL: 'Echte Daten ändern?',
    ABSENDEN: 'Absenden',
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
