package org.mwolff.manban.backup.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.application.AdminAccessDeniedException;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.backup.domain.BackupKind;
import org.mwolff.manban.backup.domain.BackupOutcome;
import org.mwolff.manban.backup.domain.BackupRun;
import org.mwolff.manban.backup.domain.BackupVerdict;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Beantwortet dem Plattform-Admin die Frage, ob die Sicherung lebt (Issue #826).
 *
 * <p>Die Sicherung selbst läuft als eigener Container außerhalb der Anwendung (Plan #825 E1) und
 * protokolliert jeden Lauf in {@code backup_run} (E2). Dieser Dienst liest ausschließlich: je Art
 * den jüngsten Lauf, dazu den jüngsten gelungenen, und daraus ein Gesamturteil.
 */
@Service
public class BackupStatusService {

  private final BackupRunRepository runs;
  private final BackupProperties properties;
  private final PlatformAdminChecker platformAdminChecker;
  private final Clock clock;
  private final boolean alertMailEnabled;

  public BackupStatusService(
      BackupRunRepository runs,
      BackupProperties properties,
      PlatformAdminChecker platformAdminChecker,
      Clock clock,
      @Value("${manban.mail.enabled:false}") boolean alertMailEnabled) {
    this.runs = runs;
    this.properties = properties;
    this.platformAdminChecker = platformAdminChecker;
    this.clock = clock;
    this.alertMailEnabled = alertMailEnabled;
  }

  /**
   * Der Stand der Sicherung. Nur für Plattform-Admins.
   *
   * @throws AdminAccessDeniedException wenn der Aufrufer kein Plattform-Admin ist (403)
   */
  @Transactional(readOnly = true)
  public BackupStatus status(long actorUserId) {
    if (!platformAdminChecker.isPlatformAdmin(actorUserId)) {
      throw new AdminAccessDeniedException();
    }
    return stand();
  }

  /**
   * Derselbe Stand für den Betrieb — <strong>ohne Rechteprüfung</strong> (Issue #828).
   *
   * <p>Der Sicherungs-Wachhund fragt nicht im Namen eines Menschen, sondern für sich selbst: Es
   * gibt keinen Aufrufer, dessen Rechte zu prüfen wären. Die Antwort verlässt das System auch nicht
   * über HTTP, sondern nur als Alarm an die Plattform-Admins — also an genau den Kreis, der {@link
   * #status(long)} ohnehin offensteht. Ein erfundener Systemnutzer, den {@link
   * org.mwolff.manban.auth.application.PlatformAdminChecker} bejahen müsste, wäre eine zweite
   * Wahrheit darüber, wer Admin ist.
   */
  @Transactional(readOnly = true)
  public BackupStatus operationalStatus() {
    return stand();
  }

  private BackupStatus stand() {
    Instant jetzt = clock.instant();
    ZonedDateTime bezug = jetzt.atZone(clock.getZone());
    List<KindStatus> arten =
        Arrays.stream(BackupKind.values()).map(art -> standDerArt(art, jetzt, bezug)).toList();
    return new BackupStatus(
        urteil(arten), properties.enabled(), alertMailEnabled, properties.targetLabel(), arten);
  }

  private KindStatus standDerArt(BackupKind art, Instant jetzt, ZonedDateTime bezug) {
    Duration warnfrist = properties.warnAfter(art, bezug);
    Instant letzterErfolg = runs.latestSuccessful(art).map(BackupRun::startedAt).orElse(null);
    boolean veraltet =
        letzterErfolg == null || Duration.between(letzterErfolg, jetzt).compareTo(warnfrist) > 0;
    return runs.latest(art)
        .map(
            lauf ->
                new KindStatus(
                    art,
                    lauf.startedAt(),
                    lauf.finishedAt(),
                    lauf.outcome(),
                    lauf.detail(),
                    lauf.bytes(),
                    letzterErfolg,
                    Duration.between(lauf.startedAt(), jetzt).toSeconds(),
                    warnfrist.toSeconds(),
                    veraltet))
        .orElseGet(
            () ->
                new KindStatus(
                    art,
                    null,
                    null,
                    null,
                    null,
                    null,
                    letzterErfolg,
                    null,
                    warnfrist.toSeconds(),
                    veraltet));
  }

  /**
   * Das Gesamturteil in seiner Rangfolge: Erst sagt die Ansicht, ob überhaupt gesichert wird, dann
   * ob etwas kaputt ist, dann ob etwas zu lange schweigt. Ein Fehlschlag geht dem Schweigen vor —
   * er nennt bereits den Grund, während „veraltet" nur die Folge beschreibt.
   */
  private BackupVerdict urteil(List<KindStatus> arten) {
    if (!properties.enabled()) {
      return BackupVerdict.ABGESCHALTET;
    }
    if (arten.stream().anyMatch(art -> art.lastOutcome() == BackupOutcome.FEHLSCHLAG)) {
      return BackupVerdict.FEHLGESCHLAGEN;
    }
    if (arten.stream().anyMatch(KindStatus::stale)) {
      return BackupVerdict.VERALTET;
    }
    return BackupVerdict.OK;
  }

  /**
   * Der Stand der Sicherung insgesamt.
   *
   * @param alertMailEnabled ob der Alarm tatsächlich verschickt wird ({@code manban.mail.enabled}).
   *     Ist er aus — der ausgelieferte Zustand —, protokolliert der Versand nur (Plan #825 E11);
   *     dann ist die Admin-Ansicht der einzige verlässliche Weg und sagt das (Issue #833).
   * @param targetLabel sprechender Name des Ablageorts außer Haus
   * @param kinds je Art ein Eintrag, in der Reihenfolge von {@link BackupKind}
   */
  public record BackupStatus(
      BackupVerdict verdict,
      boolean enabled,
      boolean alertMailEnabled,
      String targetLabel,
      List<KindStatus> kinds) {}

  /**
   * Der Stand einer Art.
   *
   * @param lastStartedAt Beginn des jüngsten Laufs; {@code null}, wenn es keinen gibt
   * @param lastFinishedAt Ende des jüngsten Laufs
   * @param lastOutcome Ausgang des jüngsten Laufs; {@code null}, wenn es keinen gibt
   * @param detail Grund, falls der jüngste Lauf scheiterte
   * @param bytes Umfang des jüngsten Laufs
   * @param lastSuccessAt Beginn des jüngsten <em>gelungenen</em> Laufs — daran hängt {@code stale}
   * @param ageSeconds Alter des jüngsten Laufs in Sekunden; {@code null}, wenn es keinen gibt
   * @param warnAfterSeconds die aus dem Rhythmus abgeleitete Warnfrist dieser Art
   * @param stale ob seit dem jüngsten gelungenen Lauf mehr als die Warnfrist vergangen ist; ohne
   *     jeden gelungenen Lauf ebenfalls {@code true} — nichts zu haben ist nicht besser als
   *     Veraltetes zu haben
   */
  public record KindStatus(
      BackupKind kind,
      @Nullable Instant lastStartedAt,
      @Nullable Instant lastFinishedAt,
      @Nullable BackupOutcome lastOutcome,
      @Nullable String detail,
      @Nullable Long bytes,
      @Nullable Instant lastSuccessAt,
      @Nullable Long ageSeconds,
      long warnAfterSeconds,
      boolean stale) {}
}
