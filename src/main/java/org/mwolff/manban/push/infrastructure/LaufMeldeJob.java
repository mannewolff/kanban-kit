package org.mwolff.manban.push.infrastructure;

import org.mwolff.manban.nightrun.application.MeldbareLaeufe;
import org.mwolff.manban.nightrun.application.MeldbareLaeufe.MeldbarerLauf;
import org.mwolff.manban.push.application.LaufMeldeService;
import org.mwolff.manban.push.application.PushProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Der Meldejob (Issue #1998, Plan #1989 E12, E16): prüft im Takt {@code manban.push.melde-takt} die
 * Läufe der laufenden und der vorigen Nacht und meldet jeden, der erstmals einen Meldegrund hat.
 * Die Verzögerung von höchstens einem Takt gilt als „sofort“ (AK 11).
 *
 * <p><b>Nur mit Push.</b> Ohne VAPID-Schlüssel fragt der Job nichts ab: Ein Lauf, der in dieser
 * Zeit scheitert, gilt dann nicht als gemeldet — sonst bekäme nach dem Einschalten niemand eine
 * Nachricht zu ihm, obwohl nie eine geschickt wurde.
 *
 * <p><b>Je Lauf eine Transaktion.</b> {@link LaufMeldeService#melde} ist ein eigener Bean-Aufruf;
 * scheitert er an einem Lauf, rollt nur dieser zurück und wird im nächsten Takt erneut versucht.
 *
 * <p>{@code fixedDelay} statt {@code fixedRate} wie beim Outbox-Worker: Der Abstand zählt ab dem
 * Ende des vorigen Laufs, ein langsamer Takt staut keine Folgeläufe auf.
 */
// PMD.AvoidCatchingGenericException: Der Job muss jeden Fehler an einem Lauf überleben, sonst
// bliebe jeder spätere Lauf derselben Liste ungemeldet. Der Fehler wird protokolliert, und der
// nächste Takt versucht den Lauf erneut — die Meldezeile ist mit zurückgerollt.
@SuppressWarnings("PMD.AvoidCatchingGenericException")
@Component
class LaufMeldeJob {

  private static final Logger log = LoggerFactory.getLogger(LaufMeldeJob.class);

  private final MeldbareLaeufe meldbare;
  private final LaufMeldeService service;
  private final PushProperties properties;

  LaufMeldeJob(MeldbareLaeufe meldbare, LaufMeldeService service, PushProperties properties) {
    this.meldbare = meldbare;
    this.service = service;
    this.properties = properties;
  }

  @Scheduled(
      fixedDelayString = "${manban.push.melde-takt:PT1M}",
      initialDelayString = "${manban.push.melde-takt:PT1M}")
  void run() {
    int geplant = melden();
    if (geplant > 0) {
      log.info("Push: {} Nachrichten zu Läufen geplant", geplant);
    }
  }

  /** Ein Takt; liefert die Zahl der geplanten Nachrichten. */
  int melden() {
    if (!properties.aktiv()) {
      return 0;
    }
    int geplant = 0;
    for (MeldbarerLauf lauf : meldbare.laeufe()) {
      try {
        geplant += service.melde(lauf);
      } catch (RuntimeException e) {
        log.warn(
            "Push: Lauf {} nicht gemeldet, nächster Takt versucht es erneut", lauf.laufId(), e);
      }
    }
    return geplant;
  }
}
