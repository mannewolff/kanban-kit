package org.mwolff.manban.backup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AdminAccessDeniedException;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.backup.application.BackupStatusService.BackupStatus;
import org.mwolff.manban.backup.application.BackupStatusService.KindStatus;
import org.mwolff.manban.backup.domain.BackupKind;
import org.mwolff.manban.backup.domain.BackupOutcome;
import org.mwolff.manban.backup.domain.BackupRun;
import org.mwolff.manban.backup.domain.BackupVerdict;

/**
 * Die Urteilsbildung über den Stand der Sicherung (Issue #826).
 *
 * <p>Die vier Zustände unterscheiden sich in dem, was sie dem Betreiber sagen: {@code abgeschaltet}
 * heißt „läuft nicht", {@code fehlgeschlagen} heißt „ist kaputt", {@code veraltet} heißt „schweigt
 * zu lange" (Plan #825 E6). Genau diese Trennung wird hier geprüft, samt ihrer Rangfolge.
 */
class BackupStatusServiceTest {

  private static final Instant JETZT = Instant.parse("2026-09-23T12:00:00Z");
  private static final long ADMIN = 7L;
  private static final long NIEMAND = 8L;

  /** 03:00 täglich → Takt 24 h → Warnfrist 48 h; Spiegeltakt 5 min → Warnfrist 10 min. */
  private static final String CRON = "0 0 3 * * *";

  private static final long WARNFRIST_BASIS = Duration.ofDays(2).toSeconds();
  private static final long WARNFRIST_SPIEGEL = Duration.ofMinutes(10).toSeconds();

  private BackupRunRepository runs;
  private PlatformAdminChecker admins;

  @BeforeEach
  void setUp() {
    runs = mock(BackupRunRepository.class);
    admins = mock(PlatformAdminChecker.class);
    when(admins.isPlatformAdmin(ADMIN)).thenReturn(true);
  }

  private BackupStatusService service(boolean eingeschaltet) {
    return new BackupStatusService(
        runs,
        new BackupProperties(eingeschaltet, CRON, Duration.ofMinutes(5), "Nextcloud"),
        admins,
        Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  /** Ein gelungener Lauf, der vor {@code vor} begonnen hat. */
  private static BackupRun erfolg(BackupKind art, Duration vor) {
    Instant start = JETZT.minus(vor);
    return new BackupRun(art, start, start.plusSeconds(30), BackupOutcome.ERFOLG, null, 4096L);
  }

  private static BackupRun fehlschlag(BackupKind art, Duration vor) {
    Instant start = JETZT.minus(vor);
    return new BackupRun(
        art, start, start.plusSeconds(5), BackupOutcome.FEHLSCHLAG, "rclone: refused", null);
  }

  private void letzterLauf(BackupKind art, BackupRun lauf) {
    when(runs.latest(art)).thenReturn(Optional.of(lauf));
  }

  private void letzterErfolg(BackupKind art, BackupRun lauf) {
    when(runs.latestSuccessful(art)).thenReturn(Optional.of(lauf));
  }

  /** Derselbe Lauf ist der jüngste und der jüngste gelungene. */
  private void frisch(BackupKind art, Duration vor) {
    BackupRun lauf = erfolg(art, vor);
    letzterLauf(art, lauf);
    letzterErfolg(art, lauf);
  }

  private static KindStatus art(BackupStatus status, BackupKind art) {
    return status.kinds().stream().filter(k -> k.kind() == art).findFirst().orElseThrow();
  }

  @Test
  void werKeinPlattformAdminIstSiehtNichts() {
    assertThatThrownBy(() -> service(true).status(NIEMAND))
        .isInstanceOf(AdminAccessDeniedException.class);

    verifyNoInteractions(runs);
  }

  @Test
  void abgeschaltet_wennDieSicherungNichtEingeschaltetIst() {
    BackupStatus status = service(false).status(ADMIN);

    assertThat(status.verdict()).isEqualTo(BackupVerdict.ABGESCHALTET);
    assertThat(status.enabled()).isFalse();
    assertThat(status.targetLabel()).isEqualTo("Nextcloud");
  }

  @Test
  void veraltet_wennNochNieEinLaufProtokolliertWurde() {
    BackupStatus status = service(true).status(ADMIN);

    assertThat(status.verdict()).isEqualTo(BackupVerdict.VERALTET);
    assertThat(status.enabled()).isTrue();
    assertThat(status.kinds()).hasSize(BackupKind.values().length);

    KindStatus basis = art(status, BackupKind.BASIS);
    assertThat(basis.lastStartedAt()).isNull();
    assertThat(basis.lastFinishedAt()).isNull();
    assertThat(basis.lastOutcome()).isNull();
    assertThat(basis.detail()).isNull();
    assertThat(basis.bytes()).isNull();
    assertThat(basis.lastSuccessAt()).isNull();
    assertThat(basis.ageSeconds()).isNull();
    assertThat(basis.warnAfterSeconds()).isEqualTo(WARNFRIST_BASIS);
    assertThat(basis.stale()).isTrue();
  }

  @Test
  void ok_wennJedeArtEinenLaufInnerhalbIhrerWarnfristHat() {
    frisch(BackupKind.BASIS, Duration.ofHours(2));
    frisch(BackupKind.WAL, Duration.ofMinutes(1));
    // Genau auf der Frist — die Grenze selbst ist noch nicht „zu lange her".
    frisch(BackupKind.SPIEGEL, Duration.ofMinutes(10));
    frisch(BackupKind.OFFSITE, Duration.ofHours(3));

    BackupStatus status = service(true).status(ADMIN);

    assertThat(status.verdict()).isEqualTo(BackupVerdict.OK);

    KindStatus basis = art(status, BackupKind.BASIS);
    assertThat(basis.kind()).isEqualTo(BackupKind.BASIS);
    assertThat(basis.lastStartedAt()).isEqualTo(JETZT.minus(Duration.ofHours(2)));
    assertThat(basis.lastFinishedAt()).isEqualTo(JETZT.minus(Duration.ofHours(2)).plusSeconds(30));
    assertThat(basis.lastOutcome()).isEqualTo(BackupOutcome.ERFOLG);
    assertThat(basis.detail()).isNull();
    assertThat(basis.bytes()).isEqualTo(4096L);
    assertThat(basis.lastSuccessAt()).isEqualTo(JETZT.minus(Duration.ofHours(2)));
    assertThat(basis.ageSeconds()).isEqualTo(Duration.ofHours(2).toSeconds());
    assertThat(basis.warnAfterSeconds()).isEqualTo(WARNFRIST_BASIS);
    assertThat(basis.stale()).isFalse();

    KindStatus spiegel = art(status, BackupKind.SPIEGEL);
    assertThat(spiegel.warnAfterSeconds()).isEqualTo(WARNFRIST_SPIEGEL);
    assertThat(spiegel.ageSeconds()).isEqualTo(WARNFRIST_SPIEGEL);
    assertThat(spiegel.stale()).isFalse();
  }

  @Test
  void veraltet_wennDerLetzteErfolgAelterAlsDieWarnfristIst() {
    frisch(BackupKind.BASIS, Duration.ofHours(2));
    frisch(BackupKind.WAL, Duration.ofMinutes(1));
    frisch(BackupKind.OFFSITE, Duration.ofHours(3));
    // Eine Sekunde über die Frist hinaus.
    frisch(BackupKind.SPIEGEL, Duration.ofMinutes(10).plusSeconds(1));

    BackupStatus status = service(true).status(ADMIN);

    assertThat(status.verdict()).isEqualTo(BackupVerdict.VERALTET);
    assertThat(art(status, BackupKind.SPIEGEL).stale()).isTrue();
    assertThat(art(status, BackupKind.BASIS).stale()).isFalse();
  }

  @Test
  void fehlgeschlagen_gehtDemVeraltetVor() {
    frisch(BackupKind.BASIS, Duration.ofHours(2));
    frisch(BackupKind.OFFSITE, Duration.ofHours(3));
    // Der Spiegel schweigt zu lange — für sich genommen „veraltet".
    frisch(BackupKind.SPIEGEL, Duration.ofMinutes(30));
    // Das WAL-Archiv hat zuletzt versagt, obwohl kurz davor ein Lauf gelang.
    letzterErfolg(BackupKind.WAL, erfolg(BackupKind.WAL, Duration.ofMinutes(2)));
    letzterLauf(BackupKind.WAL, fehlschlag(BackupKind.WAL, Duration.ofMinutes(1)));

    BackupStatus status = service(true).status(ADMIN);

    assertThat(status.verdict()).isEqualTo(BackupVerdict.FEHLGESCHLAGEN);

    KindStatus wal = art(status, BackupKind.WAL);
    assertThat(wal.lastOutcome()).isEqualTo(BackupOutcome.FEHLSCHLAG);
    assertThat(wal.detail()).isEqualTo("rclone: refused");
    assertThat(wal.bytes()).isNull();
    assertThat(wal.lastSuccessAt()).isEqualTo(JETZT.minus(Duration.ofMinutes(2)));
    assertThat(wal.stale()).isFalse();
    assertThat(art(status, BackupKind.SPIEGEL).stale()).isTrue();
  }

  /**
   * Der Betriebspfad (Issue #828): Der Wachhund fragt denselben Stand ab, aber ohne Aufrufer — er
   * <em>ist</em> niemand. Eine Rechteprüfung hätte hier keinen Prüfling.
   */
  @Test
  void derBetriebFragtDenselbenStandOhneRechtepruefung() {
    frisch(BackupKind.BASIS, Duration.ofHours(2));
    frisch(BackupKind.WAL, Duration.ofMinutes(1));
    frisch(BackupKind.SPIEGEL, Duration.ofMinutes(1));
    frisch(BackupKind.OFFSITE, Duration.ofHours(3));

    BackupStatus status = service(true).operationalStatus();

    assertThat(status.verdict()).isEqualTo(BackupVerdict.OK);
    assertThat(status.kinds()).hasSize(BackupKind.values().length);
    verify(admins, never()).isPlatformAdmin(anyLong());
  }

  @Test
  void abgeschaltet_gehtJedemAnderenUrteilVor() {
    letzterLauf(BackupKind.WAL, fehlschlag(BackupKind.WAL, Duration.ofMinutes(1)));

    BackupStatus status = service(false).status(ADMIN);

    assertThat(status.verdict()).isEqualTo(BackupVerdict.ABGESCHALTET);
  }
}
