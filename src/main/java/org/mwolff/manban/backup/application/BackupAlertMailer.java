package org.mwolff.manban.backup.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.backup.domain.BackupVerdict;

/**
 * Ausgehender Port für den Alarm über eine ausbleibende oder gescheiterte Sicherung (Issue #828).
 *
 * <p>Der Alarm geht über die transaktionale Outbox hinaus, nicht über einen eigenen Versandweg: Sie
 * bringt Idempotenz und Wiederholung bereits mit, und ein zweiter Weg wäre ein zweiter Ort mit
 * denselben Fehlern.
 */
@FunctionalInterface
public interface BackupAlertMailer {

  /**
   * Plant den Alarm an einen Plattform-Admin ein.
   *
   * @param adminEmail Empfänger
   * @param failure Art des Ausfalls. Der Wachhund übergibt nur {@link BackupVerdict#VERALTET} und
   *     {@link BackupVerdict#FEHLGESCHLAGEN} — über eine gesunde oder gar nicht eingerichtete
   *     Sicherung gibt es nichts zu alarmieren.
   * @param lastSuccessAt Beginn des letzten gelungenen Laufs der betroffenen Arten; {@code null},
   *     wenn noch nie einer gelang. An diesem Wert hängt die Unterdrückung der Wiederholung —
   *     solange er sich nicht ändert, ist es derselbe Ausfall.
   */
  void sendBackupAlert(String adminEmail, BackupVerdict failure, @Nullable Instant lastSuccessAt);
}
