package org.mwolff.manban.backup.infrastructure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.PlatformAdminDirectory;
import org.mwolff.manban.backup.application.BackupAlertMailer;
import org.mwolff.manban.backup.application.BackupStatusService;
import org.mwolff.manban.backup.application.BackupStatusService.BackupStatus;
import org.mwolff.manban.backup.application.BackupStatusService.KindStatus;
import org.mwolff.manban.backup.domain.BackupKind;
import org.mwolff.manban.backup.domain.BackupOutcome;
import org.mwolff.manban.backup.domain.BackupVerdict;

/**
 * Der Wachhund über der Sicherung (Issue #828).
 *
 * <p>Geprüft wird die eine Entscheidung, die er trifft: <em>ob</em> alarmiert wird und mit
 * <em>welchem</em> Zeitpunkt. Am Zeitpunkt hängt der Idempotenzschlüssel und damit die Zusage
 * „genau ein Alarm je Zustandswechsel" — deshalb ist er kein Beiwerk der Mail, sondern der Kern.
 */
class BackupWatchdogJobTest {

  private static final Instant VOR_EINEM_TAG = Instant.parse("2026-09-22T03:00:00Z");
  private static final Instant VOR_DREI_TAGEN = Instant.parse("2026-09-20T03:00:00Z");
  private static final Instant VOR_FUENF_TAGEN = Instant.parse("2026-09-18T03:00:00Z");
  private static final Instant GERADE_EBEN = Instant.parse("2026-09-23T11:59:00Z");

  private final BackupStatusService status = mock(BackupStatusService.class);
  private final PlatformAdminDirectory admins = mock(PlatformAdminDirectory.class);
  private final BackupAlertMailer mailer = mock(BackupAlertMailer.class);
  private final BackupWatchdogJob job = new BackupWatchdogJob(status, admins, mailer);

  /** Eine Art mit genau den Feldern, an denen der Wachhund hängt; der Rest bleibt leer. */
  private static KindStatus art(
      BackupKind kind,
      @Nullable Instant letzterErfolg,
      @Nullable BackupOutcome letzterAusgang,
      boolean veraltet) {
    return new KindStatus(
        kind, null, null, letzterAusgang, null, null, letzterErfolg, null, 600L, veraltet);
  }

  private void meldet(BackupVerdict urteil, KindStatus... arten) {
    when(status.operationalStatus())
        .thenReturn(new BackupStatus(urteil, true, "Nextcloud", List.of(arten)));
  }

  private void plattformAdmins(String... emails) {
    when(admins.adminEmails()).thenReturn(List.of(emails));
  }

  @Test
  void okAlarmiertNicht() {
    meldet(BackupVerdict.OK, art(BackupKind.BASIS, GERADE_EBEN, BackupOutcome.ERFOLG, false));

    job.run();

    verifyNoInteractions(admins, mailer);
  }

  @Test
  void abgeschaltetAlarmiertNicht() {
    meldet(BackupVerdict.ABGESCHALTET, art(BackupKind.BASIS, null, null, true));

    job.run();

    verifyNoInteractions(admins, mailer);
  }

  @Test
  void veraltetAlarmiertJedenAdminMitDemAeltestenErfolgDerBetroffenenArten() {
    // Given — drei Arten schweigen, eine läuft. Der Alarm nennt die schwächste Stelle, nicht die
    // jüngste: Sonst verschöbe ein gesunder Lauf den Schlüssel und der Alarm ginge erneut hinaus.
    // Die Reihenfolge ist Absicht — der älteste steht in der Mitte, nicht am Rand.
    meldet(
        BackupVerdict.VERALTET,
        art(BackupKind.BASIS, VOR_DREI_TAGEN, BackupOutcome.ERFOLG, true),
        art(BackupKind.OFFSITE, VOR_FUENF_TAGEN, BackupOutcome.ERFOLG, true),
        art(BackupKind.SPIEGEL, VOR_EINEM_TAG, BackupOutcome.ERFOLG, true),
        art(BackupKind.WAL, GERADE_EBEN, BackupOutcome.ERFOLG, false));
    plattformAdmins("a@example.org", "b@example.org");

    // When
    job.run();

    // Then
    verify(mailer).sendBackupAlert("a@example.org", BackupVerdict.VERALTET, VOR_FUENF_TAGEN);
    verify(mailer).sendBackupAlert("b@example.org", BackupVerdict.VERALTET, VOR_FUENF_TAGEN);
  }

  @Test
  void einFehlschlagMachtEineArtBetroffenAuchOhneVeralteteFrist() {
    // Given — der jüngste Lauf scheiterte, der Erfolg davor liegt noch innerhalb der Frist.
    meldet(
        BackupVerdict.FEHLGESCHLAGEN,
        art(BackupKind.OFFSITE, VOR_DREI_TAGEN, BackupOutcome.FEHLSCHLAG, false),
        art(BackupKind.WAL, GERADE_EBEN, BackupOutcome.ERFOLG, false));
    plattformAdmins("a@example.org");

    // When
    job.run();

    // Then
    verify(mailer).sendBackupAlert("a@example.org", BackupVerdict.FEHLGESCHLAGEN, VOR_DREI_TAGEN);
  }

  @Test
  void ohneJemalsGelungenenLaufAlarmiertErOhneZeitpunkt() {
    // Given — eine betroffene Art ohne jeden Erfolg macht die ganze Aussage „nie", auch wenn eine
    // andere betroffene Art schon einmal gelungen ist.
    meldet(
        BackupVerdict.VERALTET,
        art(BackupKind.BASIS, VOR_DREI_TAGEN, BackupOutcome.ERFOLG, true),
        art(BackupKind.OFFSITE, null, null, true));
    plattformAdmins("a@example.org");

    // When
    job.run();

    // Then
    verify(mailer).sendBackupAlert("a@example.org", BackupVerdict.VERALTET, null);
  }

  @Test
  void ohnePlattformAdminAlarmiertErNiemanden() {
    meldet(BackupVerdict.VERALTET, art(BackupKind.BASIS, VOR_DREI_TAGEN, null, true));
    plattformAdmins();

    job.run();

    verifyNoInteractions(mailer);
  }
}
