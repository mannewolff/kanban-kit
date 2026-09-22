package org.mwolff.manban.auth.application;

import java.util.List;

/**
 * Ausgehender Port des {@code auth}-Moduls für modulfremde Admin-Benachrichtigungen (Issue #827).
 * Er beantwortet genau eine Frage: „An wen geht eine Nachricht, die alle Plattform-Admins angeht?"
 *
 * <p>Die Signatur trägt bewusst <strong>keinen</strong> Typ aus {@code auth.domain} nach außen —
 * weder {@code AppUser} noch {@code PlatformRole}. Ein Aufrufer wie der Sicherungs-Wachhund braucht
 * Empfänger, nicht die Benutzer-Aggregate; über Zeichenketten bleibt das Aggregat modulintern
 * (Gegenstück zu {@link UserLookup}/{@link UserSummary} für den Lesezugriff auf einzelne Nutzer).
 *
 * <p>Führt <strong>keine</strong> Rechteprüfung durch — reines Nachschlagen; wie bei {@link
 * UserLookup} bleibt die Autorisierung beim aufrufenden Modul. Ein Aufruferkreis ist hier nicht
 * begrenzt: Der Port gibt keine Handhabe auf fremde Daten, sondern die Empfängerliste einer
 * Betriebsmeldung.
 */
@FunctionalInterface
public interface PlatformAdminDirectory {

  /**
   * E-Mail-Adressen aller Plattform-Admins, <strong>stabil sortiert</strong>; leer, wenn es keinen
   * gibt.
   *
   * <p>Die Sortierung ist Teil des Vertrags, nicht Zufall der Abfrage: Aufrufer bilden aus der
   * Empfängerliste Idempotenzschlüssel (Outbox), und eine wechselnde Reihenfolge erzeugte bei
   * unveränderter Admin-Menge verschiedene Schlüssel — dieselbe Meldung ginge mehrfach hinaus.
   */
  List<String> adminEmails();
}
