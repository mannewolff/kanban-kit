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
