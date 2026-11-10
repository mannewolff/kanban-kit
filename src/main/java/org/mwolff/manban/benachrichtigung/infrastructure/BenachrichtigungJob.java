package org.mwolff.manban.benachrichtigung.infrastructure;

import java.util.function.IntSupplier;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungErkennung;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungVersand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Der Takt der Benachrichtigungen (Issue #2081, Plan #2070 E2, E10): erkennt Anlässe aus Läufen und
 * Karten und plant danach die fälligen Mails ein.
 *
 * <p><b>Nur mit Mailversand</b> (E10). Bei {@code manban.mail.enabled=false} fragt der Job nichts
 * ab und legt keine Anlässe an: Sonst gälte als gemeldet, was nie zugestellt wurde.
 *
 * <p><b>Je Schritt eine Transaktion.</b> Erkennung und Versand sind eigene Bean-Aufrufe; scheitert
 * einer, rollt nur er zurück, die übrigen laufen weiter, und der nächste Takt versucht ihn erneut.
 *
 * <p>{@code fixedDelay} statt {@code fixedRate} wie beim Outbox-Worker: Der Abstand zählt ab dem
 * Ende des vorigen Laufs, ein langsamer Takt staut keine Folgeläufe auf.
 *
 * <p>Öffentlich, weil {@code BenachrichtigungSzenarienIT} den Takt von Hand auslöst.
 */
// PMD.AvoidCatchingGenericException: Der Job muss jeden Fehler eines Schritts überleben, sonst
// bliebe etwa der Versand aus, solange die Erkennung an einem Lauf scheitert. Der Fehler wird
// protokolliert, und der nächste Takt versucht den Schritt erneut.
@SuppressWarnings("PMD.AvoidCatchingGenericException")
@Component
public class BenachrichtigungJob {

  private static final Logger log = LoggerFactory.getLogger(BenachrichtigungJob.class);

  private final BenachrichtigungErkennung erkennung;
  private final BenachrichtigungVersand versand;
  private final boolean mailEnabled;

  BenachrichtigungJob(
      BenachrichtigungErkennung erkennung,
      BenachrichtigungVersand versand,
      @Value("${manban.mail.enabled:false}") boolean mailEnabled) {
    this.erkennung = erkennung;
    this.versand = versand;
    this.mailEnabled = mailEnabled;
  }

  /** Ein Takt: Läufe, Karten, Versand. */
  @Scheduled(
      fixedDelayString = "${manban.benachrichtigung.takt:PT1M}",
      initialDelayString = "${manban.benachrichtigung.takt:PT1M}")
  public void run() {
    if (!mailEnabled) {
      return;
    }
    int anlaesse =
        schritt("Läufe", erkennung::erkenneLaeufe) + schritt("Karten", erkennung::erkenneKarten);
    int geplant = schritt("Versand", versand::planeEin);
    if (anlaesse > 0 || geplant > 0) {
      log.info("Benachrichtigung: {} Anlässe erkannt, {} Mails eingeplant", anlaesse, geplant);
    }
  }

  private static int schritt(String name, IntSupplier schritt) {
    try {
      return schritt.getAsInt();
    } catch (RuntimeException e) {
      log.warn(
          "Benachrichtigung: Schritt {} gescheitert, nächster Takt versucht es erneut", name, e);
      return 0;
    }
  }
}
