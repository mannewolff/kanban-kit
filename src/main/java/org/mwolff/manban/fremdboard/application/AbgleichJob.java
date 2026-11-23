package org.mwolff.manban.fremdboard.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.fremdboard.domain.FremdAbrufException;
import org.mwolff.manban.fremdboard.domain.Verbindung;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Der regelmäßige Abgleich mit den fremden Boards (Issue #2168, Plan #2152 A7, A8, A9): holt
 * Änderungen von Hand ab, erkennt gelöschte Karten und behält bei einem Ausfall den letzten Stand.
 *
 * <p><b>Takt und Abstände</b> stehen unter {@code manban.fremdboard.*}: Je Takt (Vorgabe 60 s)
 * kommen die Projekte dran, deren letzter Abgleich mindestens den Mindestabstand (Vorgabe 2 min)
 * zurückliegt; nach dem Abstand des Vollabgleichs (Vorgabe 60 min) ist der nächste ein voller. Mit
 * den Vorgaben erscheint eine Änderung von Hand nach höchstens vier Minuten.
 *
 * <p><b>Anspruch.</b> Mehrere Knoten dürfen den Job zugleich fahren: Je Projekt übernimmt ihn nur
 * der Knoten, dessen bedingtes UPDATE auf {@code letzter_abgleich_am} gelingt ({@link
 * VerbindungRepository#beanspruchen}). Die Sperre gilt nur für diesen Anspruch; gelesen wird danach
 * ohne offene Transaktion, damit keine Datenbankverbindung über die Abrufe offen bleibt.
 *
 * <p><b>Ab wann.</b> Inkrementell gilt als geändert, was nach dem letzten gelungenen Abgleich
 * geändert wurde, voll, was nach dem letzten Vollabgleich geändert wurde — beides mit {@link
 * #UEBERLAPPUNG}, damit ein Uhrenversatz zwischen den Systemen keine Änderung verschluckt. Was
 * doppelt gelesen wird, zählt nicht doppelt: Jeder Wechsel trägt seine Kennung im {@link
 * EreignisRegister}. Vor dem ersten Abgleich ist der Anbindungszeitpunkt der Anfang, und der erste
 * Abgleich ist ein voller — er spiegelt auch die Karten, die sich seit der Anbindung nicht geändert
 * haben.
 *
 * <p><b>Fehler</b> (A9): Scheitert ein Abgleich, steht der Grund in {@code letzter_fehler}, und
 * {@code letzter_abgleich_am} kehrt auf den letzten gelungenen zurück. {@code stand_am} setzt nur
 * ein gelungener Abgleich, auf den Zeitpunkt seines Anspruchs — aus ihm zeigt der Board-Hinweis,
 * von wann der Stand ist (Issue #2222). Gelöscht wird dann nichts; der nächste Takt versucht es
 * erneut. Ein Fehler in einem Projekt hält die übrigen nicht auf.
 */
// PMD.AvoidCatchingGenericException: Der Job muss jeden Fehler eines Projekts überleben, sonst
// bliebe bei einem kaputten Projekt jeder Abgleich aus. Der Fehler wird vermerkt und protokolliert,
// und der nächste Takt versucht es erneut — wie im BenachrichtigungJob.
@SuppressWarnings("PMD.AvoidCatchingGenericException")
@Component
public class AbgleichJob {

  /** So weit vor dem letzten Abgleich beginnt der nächste. */
  static final Duration UEBERLAPPUNG = Duration.ofMinutes(1);

  /** Fehler, der nicht vom fremden System kommt; Einzelheiten stehen im Protokoll. */
  static final String FEHLER_INTERN = "Abgleich fehlgeschlagen (interner Fehler, siehe Protokoll)";

  /** Fehler des fremden Systems ohne eigenen Grund. */
  static final String FEHLER_OHNE_GRUND = "Das fremde System hat nicht geantwortet";

  /** Länge der Spalte {@code letzter_fehler}. */
  private static final int FEHLER_LAENGE = 1000;

  private static final Logger log = LoggerFactory.getLogger(AbgleichJob.class);

  private final VerbindungRepository verbindungen;
  private final AbgleichService abgleich;
  private final FremdboardProperties properties;
  private final Clock clock;

  public AbgleichJob(
      VerbindungRepository verbindungen,
      AbgleichService abgleich,
      FremdboardProperties properties,
      Clock clock) {
    this.verbindungen = verbindungen;
    this.abgleich = abgleich;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Ein Takt: gleicht jedes fällige Projekt ab. {@code fixedDelay} wie beim Benachrichtigungs-Job:
   * Der Abstand zählt ab dem Ende des vorigen Laufs, ein langsames fremdes System staut nichts auf.
   */
  @Scheduled(
      fixedDelayString = "${manban.fremdboard.takt:PT60S}",
      initialDelayString = "${manban.fremdboard.takt:PT60S}")
  public void run() {
    Instant jetzt = clock.instant();
    Instant grenze = jetzt.minus(properties.mindestabstand());
    for (long projectId : verbindungen.faellige(grenze)) {
      try {
        projektAbgleichen(projectId, jetzt, grenze);
      } catch (RuntimeException e) {
        log.warn("Fremdes Board: Abgleich von Projekt {} gescheitert", projectId, e);
      }
    }
  }

  private void projektAbgleichen(long projectId, Instant jetzt, Instant grenze) {
    Optional<Verbindung> gelesen = verbindungen.finde(projectId);
    if (gelesen.isEmpty()) {
      return;
    }
    Verbindung verbindung = gelesen.get();
    Instant bisher = verbindung.letzterAbgleichAm();
    if ((bisher != null && bisher.isAfter(grenze))
        || !verbindungen.beanspruchen(projectId, bisher, jetzt)) {
      return;
    }
    Instant letzterVoll = verbindung.letzterVollabgleichAm();
    boolean voll =
        letzterVoll == null || !jetzt.isBefore(letzterVoll.plus(properties.vollabgleich()));
    try {
      if (voll) {
        abgleich.vollAbgleichen(verbindung, seit(verbindung, letzterVoll));
      } else {
        abgleich.inkrementellAbgleichen(verbindung, seit(verbindung, bisher));
      }
    } catch (FremdAbrufException e) {
      verbindungen.abgleichGescheitert(projectId, jetzt, bisher, grund(e.getMessage()));
      return;
    } catch (RuntimeException e) {
      log.warn("Fremdes Board: Abgleich von Projekt {} gescheitert", projectId, e);
      verbindungen.abgleichGescheitert(projectId, jetzt, bisher, FEHLER_INTERN);
      return;
    }
    verbindungen.abgleichGelungen(projectId, jetzt, voll ? jetzt : null);
  }

  private static Instant seit(Verbindung verbindung, @Nullable Instant zuletzt) {
    return zuletzt == null ? verbindung.angebundenAm() : zuletzt.minus(UEBERLAPPUNG);
  }

  private static String grund(@Nullable String meldung) {
    if (meldung == null || meldung.isBlank()) {
      return FEHLER_OHNE_GRUND;
    }
    return meldung.substring(0, Math.min(meldung.length(), FEHLER_LAENGE));
  }
}
