package org.mwolff.manban.backup.domain;

/**
 * Die Arten von Sicherungslaufen, die der Sicherungs-Container protokolliert (Issue #826).
 *
 * <p>Die Reihenfolge der Deklaration ist die Reihenfolge, in der die Admin-Ansicht sie zeigt: erst
 * die Datenbank (Basissicherung und laufendes WAL-Archiv), dann die Anhänge, zuletzt die Kopie
 * außer Haus.
 */
public enum BackupKind {

  /** Vollständige Basissicherung der Datenbank im Takt von {@code manban.backup.base-cron}. */
  BASIS,

  /** Laufendes Archiv des Transaktionsprotokolls — die punktgenaue Rückholung hängt daran. */
  WAL,

  /** Spiegel der Datei-Anhänge im Takt von {@code manban.backup.mirror-interval}. */
  SPIEGEL,

  /** Verschlüsselte Kopie außer Haus. */
  OFFSITE
}
