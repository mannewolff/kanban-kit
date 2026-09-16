package org.mwolff.manban.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.automation.AutomationStatus;
import org.mwolff.manban.common.automation.AutomationStatusContributor;
import org.slf4j.LoggerFactory;

/**
 * Das Ausgabeformat des Startprotokolls (Issue #907, fachlich #841).
 *
 * <p>Geprüft wird mit Fake-Contributoren, nicht mit den echten Modulen: Das Format ist der
 * Gegenstand dieses Pakets, die Beiträge folgen in eigenen. So bleibt der Test unabhängig davon,
 * wie viele Automatiken es am Ende gibt.
 *
 * <p>Die abgeschaltete Automatik hat einen eigenen Fall, weil sie der Kern der Anforderung ist:
 * Verschwände sie aus dem Protokoll, wäre „fehlt" nicht von „läuft nicht" zu unterscheiden.
 */
class AutomationStartupLoggerTest {

  private final ListAppender<ILoggingEvent> logWatcher = new ListAppender<>();

  @BeforeEach
  void watchLog() {
    logWatcher.start();
    ((Logger) LoggerFactory.getLogger(AutomationStartupLogger.class)).addAppender(logWatcher);
  }

  /** Abmelden, sonst sammelte der Appender über Testklassen hinweg weiter. */
  @AfterEach
  void unwatchLog() {
    ((Logger) LoggerFactory.getLogger(AutomationStartupLogger.class)).detachAppender(logWatcher);
    logWatcher.stop();
  }

  private static AutomationStatusContributor meldet(AutomationStatus... stati) {
    return () -> List.of(stati);
  }

  private List<String> zeilenNachLauf(AutomationStatusContributor... contributors) {
    new AutomationStartupLogger(List.of(contributors)).protokolliereAutomatiken();
    return logWatcher.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  @Test
  void jedeGemeldeteAutomatikBekommtGenauEineZeile() {
    List<String> zeilen =
        zeilenNachLauf(
            meldet(
                new AutomationStatus("Done-Archivierung", true, "MANBAN_CLEANUP_ENABLED", ""),
                new AutomationStatus("Papierkorb", true, "MANBAN_CLEANUP_ENABLED", "")),
            meldet(new AutomationStatus("Outbox-Zustellung", true, "MANBAN_OUTBOX_ENABLED", "")));

    assertThat(zeilen).hasSize(3);
  }

  @Test
  void dieZeilenStehenNachBezeichnungSortiert_ueberPaketGrenzenHinweg() {
    List<String> zeilen =
        zeilenNachLauf(
            meldet(new AutomationStatus("Zustellung", true, "MANBAN_OUTBOX_ENABLED", "")),
            meldet(new AutomationStatus("Archivierung", true, "MANBAN_CLEANUP_ENABLED", "")));

    assertThat(zeilen.get(0)).contains("Archivierung");
    assertThat(zeilen.get(1)).contains("Zustellung");
  }

  @Test
  void dieZeilenStehenAufStufeInfo() {
    zeilenNachLauf(
        meldet(new AutomationStatus("Archivierung", true, "MANBAN_CLEANUP_ENABLED", "")));

    assertThat(logWatcher.list)
        .singleElement()
        .extracting(ILoggingEvent::getLevel)
        .isEqualTo(Level.INFO);
  }

  @Test
  void eineAbgeschalteteAutomatikErscheintAlsAbgeschaltet_stattZuFehlen() {
    List<String> zeilen =
        zeilenNachLauf(
            meldet(new AutomationStatus("Papierkorb", false, "MANBAN_CLEANUP_ENABLED", "")));

    assertThat(zeilen)
        .singleElement()
        .asString()
        .contains("abgeschaltet")
        .doesNotContain("eingeschaltet");
  }

  @Test
  void detailUndSchalterStehenInDerZeile() {
    List<String> zeilen =
        zeilenNachLauf(
            meldet(
                new AutomationStatus(
                    "Done-Archivierung",
                    true,
                    "MANBAN_CLEANUP_ENABLED",
                    "Frist 30 Tage (Konfiguration)")));

    assertThat(zeilen)
        .singleElement()
        .isEqualTo(
            "Automatik \"Done-Archivierung\": eingeschaltet — Frist 30 Tage (Konfiguration);"
                + " Schalter MANBAN_CLEANUP_ENABLED");
  }

  @Test
  void ohneDetailBleibtDieZeileWohlgeformt() {
    List<String> zeilen =
        zeilenNachLauf(
            meldet(new AutomationStatus("Papierkorb", true, "MANBAN_CLEANUP_ENABLED", "")));

    assertThat(zeilen)
        .singleElement()
        .isEqualTo("Automatik \"Papierkorb\": eingeschaltet; Schalter MANBAN_CLEANUP_ENABLED");
  }

  @Test
  void ohneContributorEntstehtKeineZeileUndKeinFehler() {
    assertThat(zeilenNachLauf()).isEmpty();
  }
}
