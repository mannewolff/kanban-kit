package org.mwolff.manban.backup.infrastructure;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.application.PlatformAdminDirectory;
import org.mwolff.manban.backup.application.BackupAlertMailer;
import org.mwolff.manban.backup.application.BackupStatusService;
import org.mwolff.manban.backup.application.BackupStatusService.BackupStatus;
import org.mwolff.manban.backup.application.BackupStatusService.KindStatus;
import org.mwolff.manban.backup.domain.BackupOutcome;
import org.mwolff.manban.backup.domain.BackupVerdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stellt fest, ob die Sicherung schweigt oder scheitert, und alarmiert die Plattform-Admins (Issue
 * #828, AK10 der fachlichen Quelle #823).
 *
 * <p>Läuft im selben Rhythmus wie die übrigen geplanten Jobs ({@code manban.cleanup.cron}) — mit
 * derselben Begründung wie dort: kein zweiter Ort, an dem dasselbe eingestellt werden müsste. Ohne
 * eingeschaltete Sicherung existiert die Bean nicht; wer nichts eingerichtet hat, bekommt keine
 * Post über eine Sicherung, die er nie wollte (Plan #825 E6).
 *
 * <p><strong>Kein eigener Zustandsspeicher</strong> für „genau ein Alarm je Zustandswechsel": Das
 * leistet der Idempotenzschlüssel der Outbox, der am Zeitpunkt des letzten gelungenen Laufs hängt.
 * Ein Zustand im Arbeitsspeicher alarmierte nach jedem Neustart erneut, eine zusätzliche Tabelle
 * wäre ein zweiter Ort für dieselbe Aussage.
 *
 * <p><strong>Keine eigene Uhr.</strong> Ob ein Lauf zu alt ist, entscheidet der {@link
 * BackupStatusService} — er trägt die {@code Clock}-Bean bereits und beantwortet dieselbe Frage für
 * die Admin-Ansicht. Eine zweite Uhr hier wäre eine zweite Zeitquelle für dasselbe Urteil: Ampel
 * und Alarm könnten auseinanderlaufen, ohne dass einer von beiden falsch rechnet.
 *
 * <p>{@code @Transactional}, weil die Outbox die Vormerkung nur innerhalb einer laufenden
 * Transaktion annimmt ({@code Propagation.MANDATORY}). Der Aufruf kommt vom Scheduler über den
 * Bean-Proxy, es ist also keine Selbst-Invokation.
 */
@Component
@ConditionalOnProperty(name = "manban.backup.enabled", havingValue = "true")
class BackupWatchdogJob {

  private static final Logger log = LoggerFactory.getLogger(BackupWatchdogJob.class);

  private final BackupStatusService status;
  private final PlatformAdminDirectory admins;
  private final BackupAlertMailer mailer;

  BackupWatchdogJob(
      BackupStatusService status, PlatformAdminDirectory admins, BackupAlertMailer mailer) {
    this.status = status;
    this.admins = admins;
    this.mailer = mailer;
  }

  @Scheduled(cron = "${manban.cleanup.cron:0 0 * * * *}")
  @Transactional
  void run() {
    BackupStatus stand = status.operationalStatus();
    if (stand.verdict() == BackupVerdict.OK || stand.verdict() == BackupVerdict.ABGESCHALTET) {
      return;
    }
    List<String> empfaenger = admins.adminEmails();
    if (empfaenger.isEmpty()) {
      // Laut, aber ohne Abbruch: Eine Instanz ohne Plattform-Admin ist ein eigener Missstand, und
      // eine geworfene Ausnahme machte daraus einen scheinbaren Fehler des Wachhunds.
      log.warn(
          "Sicherung meldet {}, aber es gibt keinen Plattform-Admin als Empfänger",
          stand.verdict());
      return;
    }
    Instant letzterErfolg = letzterErfolgDerBetroffenen(stand.kinds());
    for (String admin : empfaenger) {
      mailer.sendBackupAlert(admin, stand.verdict(), letzterErfolg);
    }
    log.warn(
        "Sicherung meldet {} — Alarm an {} Plattform-Admin(s) eingeplant",
        stand.verdict(),
        empfaenger.size());
  }

  /**
   * Der älteste gelungene Lauf unter den betroffenen Arten; {@code null}, sobald eine davon nie
   * gelang.
   *
   * <p><strong>Warum die betroffenen und warum der älteste:</strong> Der Alarm soll die schwächste
   * Stelle der Kette benennen, nicht die jüngste Meldung. Nähme man den jüngsten Erfolg über alle
   * Arten, verschöbe ein gesundes WAL-Archiv den Zeitpunkt alle fünf Minuten — und mit ihm den
   * Idempotenzschlüssel: Dieselbe seit Tagen ausbleibende Basissicherung erzeugte dann alle fünf
   * Minuten einen neuen Alarm. Nähme man alle Arten statt nur der betroffenen, schöbe ihn jeder
   * gesunde Lauf weiter, ohne dass sich am Ausfall etwas geändert hätte.
   */
  private static @Nullable Instant letzterErfolgDerBetroffenen(List<KindStatus> arten) {
    Instant aeltester = null;
    for (KindStatus art : arten) {
      if (!betroffen(art)) {
        continue;
      }
      Instant erfolg = art.lastSuccessAt();
      if (erfolg == null) {
        return null;
      }
      if (aeltester == null || erfolg.isBefore(aeltester)) {
        aeltester = erfolg;
      }
    }
    return aeltester;
  }

  /**
   * Betroffen ist, was zu lange schweigt oder zuletzt gescheitert ist — genau die beiden Gründe,
   * aus denen {@link BackupStatusService} sein Urteil bildet.
   */
  private static boolean betroffen(KindStatus art) {
    return art.stale() || art.lastOutcome() == BackupOutcome.FEHLSCHLAG;
  }
}
