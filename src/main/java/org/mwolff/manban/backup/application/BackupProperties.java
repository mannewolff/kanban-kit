package org.mwolff.manban.backup.application;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;
import org.mwolff.manban.backup.domain.BackupKind;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

/**
 * Konfiguration der Sicherung (Issue #826).
 *
 * <p><b>Die Warnschwelle ist kein eigenes Feld</b> (Plan #825 E13). Rhythmus und Aufbewahrung
 * bleiben einstellbar, damit eine kleine Instanz nicht denselben Takt fahren muss; eine daneben
 * konfigurierbare Warnschwelle könnte aber vom tatsächlichen Takt abweichen und die Zusage über den
 * Rückholzeitpunkt (E5) stillschweigend aushebeln. {@link #warnAfter(BackupKind, ZonedDateTime)}
 * leitet sie deshalb aus dem eingestellten Rhythmus ab.
 *
 * @param enabled ob überhaupt gesichert wird. Der Schalter wird vom Backup-Overlay gesetzt und
 *     nicht von Hand (E6): Ausgeliefert ist er aus, und die Admin-Ansicht sagt das unübersehbar.
 * @param baseCron Takt der Basissicherung; fehlend oder leer ergibt täglich 03:00
 * @param mirrorInterval Takt des Anhang-Spiegels; fehlend oder nicht positiv ergibt 5 Minuten.
 *     Nicht positiv wäre kein Takt, sondern sein Gegenteil — jeder Spiegel gälte im selben
 *     Augenblick als überfällig.
 * @param targetLabel sprechender Name des Ablageorts außer Haus, wie ihn die Admin-Ansicht zeigt;
 *     fehlend oder leer ergibt {@code nicht benannt}
 */
@ConfigurationProperties(prefix = "manban.backup")
public record BackupProperties(
    boolean enabled, String baseCron, Duration mirrorInterval, String targetLabel) {

  private static final String VORGABE_CRON = "0 0 3 * * *";
  private static final Duration VORGABE_SPIEGELTAKT = Duration.ofMinutes(5);
  private static final String VORGABE_ZIEL = "nicht benannt";

  /**
   * Wie viele Takte ohne gelungenen Lauf vergehen dürfen, bevor die Sicherung als veraltet gilt.
   *
   * <p>Zwei: Ein Lauf darf sich verspäten, ohne dass die Ampel umspringt — sind zwei Takte
   * vergangen, ist mindestens ein Lauf ganz ausgefallen.
   */
  private static final int ERLAUBTE_TAKTE = 2;

  public BackupProperties {
    if (baseCron == null || baseCron.isBlank()) {
      baseCron = VORGABE_CRON;
    }
    if (mirrorInterval == null || !mirrorInterval.isPositive()) {
      mirrorInterval = VORGABE_SPIEGELTAKT;
    }
    if (targetLabel == null || targetLabel.isBlank()) {
      targetLabel = VORGABE_ZIEL;
    }
  }

  /**
   * Die aus dem Rhythmus abgeleitete Warnfrist dieser Art.
   *
   * <p>Basissicherung und Kopie außer Haus entstehen gemeinsam im Cron-Takt; Anhang-Spiegel und
   * WAL-Archiv fallen im deutlich engeren Spiegeltakt an.
   *
   * @param bezug Zeitpunkt, ab dem der Cron-Takt gemessen wird — ein Ausdruck wie „täglich 03:00"
   *     hat keinen Takt an sich, sondern nur zwischen zwei aufeinanderfolgenden Auslösungen
   */
  public Duration warnAfter(BackupKind kind, ZonedDateTime bezug) {
    return takt(kind, bezug).multipliedBy(ERLAUBTE_TAKTE);
  }

  /** Der Abstand zwischen zwei Läufen dieser Art. */
  private Duration takt(BackupKind kind, ZonedDateTime bezug) {
    return switch (kind) {
      case BASIS, OFFSITE -> cronTakt(bezug);
      case WAL, SPIEGEL -> mirrorInterval;
    };
  }

  /**
   * Der Abstand zwischen den beiden nächsten Auslösungen von {@link #baseCron()}.
   *
   * <p>{@code requireNonNull} statt eines Zweigs: {@link CronExpression#next} liefert nur dann
   * {@code null}, wenn der Ausdruck nie wieder auslöst (etwa der 31. Februar). Einen solchen
   * Ausdruck einzustellen hieße, die Sicherung abzuschalten, ohne es zu sagen — dann ist ein lauter
   * Fehler die ehrlichere Antwort als eine erfundene Frist.
   */
  private Duration cronTakt(ZonedDateTime bezug) {
    CronExpression takt = CronExpression.parse(baseCron);
    ZonedDateTime erste = Objects.requireNonNull(takt.next(bezug), baseCron);
    ZonedDateTime zweite = Objects.requireNonNull(takt.next(erste), baseCron);
    return Duration.between(erste, zweite);
  }
}
