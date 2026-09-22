package org.mwolff.manban.backup.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.backup.domain.BackupKind;

/**
 * Die abgeleitete Warnfrist (Issue #826, Plan #825 E13).
 *
 * <p>Geprüft wird vor allem, dass die Frist <em>aus dem eingestellten Rhythmus</em> entsteht und
 * nicht daneben konfiguriert wird: Eine getrennte Schraube könnte vom tatsächlichen Takt abweichen
 * und die Zusage aus E5 stillschweigend aushebeln.
 */
class BackupPropertiesTest {

  /** Ein Mittwoch, 12:00 UTC — weit genug vor dem nächsten 03:00-Lauf. */
  private static final ZonedDateTime BEZUG = ZonedDateTime.parse("2026-09-23T12:00:00Z");

  private static BackupProperties eingestellt(String cron, Duration spiegeltakt) {
    return new BackupProperties(true, cron, spiegeltakt, "Nextcloud");
  }

  @Test
  void basisUndOffsiteWarnenNachZweiCronTakten() {
    BackupProperties properties = eingestellt("0 0 3 * * *", Duration.ofMinutes(5));

    assertThat(properties.warnAfter(BackupKind.BASIS, BEZUG)).isEqualTo(Duration.ofDays(2));
    assertThat(properties.warnAfter(BackupKind.OFFSITE, BEZUG)).isEqualTo(Duration.ofDays(2));
  }

  @Test
  void einEngererCronTaktZiehtDieWarnfristMit() {
    BackupProperties properties = eingestellt("0 0 */6 * * *", Duration.ofMinutes(5));

    assertThat(properties.warnAfter(BackupKind.BASIS, BEZUG)).isEqualTo(Duration.ofHours(12));
  }

  @Test
  void spiegelUndWalWarnenNachZweiSpiegeltakten() {
    BackupProperties properties = eingestellt("0 0 3 * * *", Duration.ofMinutes(5));

    assertThat(properties.warnAfter(BackupKind.SPIEGEL, BEZUG)).isEqualTo(Duration.ofMinutes(10));
    assertThat(properties.warnAfter(BackupKind.WAL, BEZUG)).isEqualTo(Duration.ofMinutes(10));
  }

  @Test
  void fehlendeAngabenErgebenDieVorgaben() {
    BackupProperties properties = new BackupProperties(false, null, null, null);

    assertThat(properties.enabled()).isFalse();
    assertThat(properties.baseCron()).isEqualTo("0 0 3 * * *");
    assertThat(properties.mirrorInterval()).isEqualTo(Duration.ofMinutes(5));
    assertThat(properties.targetLabel()).isEqualTo("nicht benannt");
  }

  @Test
  void leereUndNichtPositiveAngabenErgebenEbenfallsDieVorgaben() {
    BackupProperties properties = new BackupProperties(true, "  ", Duration.ZERO, "");

    assertThat(properties.baseCron()).isEqualTo("0 0 3 * * *");
    assertThat(properties.mirrorInterval()).isEqualTo(Duration.ofMinutes(5));
    assertThat(properties.targetLabel()).isEqualTo("nicht benannt");
  }

  @Test
  void gesetzteAngabenBleibenUnveraendert() {
    BackupProperties properties = eingestellt("0 30 4 * * *", Duration.ofMinutes(7));

    assertThat(properties.enabled()).isTrue();
    assertThat(properties.baseCron()).isEqualTo("0 30 4 * * *");
    assertThat(properties.mirrorInterval()).isEqualTo(Duration.ofMinutes(7));
    assertThat(properties.targetLabel()).isEqualTo("Nextcloud");
  }
}
