package org.mwolff.manban.nightrun.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.Meldegrund.Zustand;

/**
 * Wie ein Nachtlauf auf dem Handy erscheint: Zustand, Dauer und letzte Meldung (Issue #1994, Plan
 * #1989 E19, fachliche Quelle #1961 AK 9).
 *
 * <p>Den Zustand trägt {@link Meldegrund}; hier kommt nur hinzu, was ohne Grund gilt — ein offener
 * Lauf {@link Zustand#LAEUFT}, jeder andere {@link Zustand#BEENDET}. Für die Stille gibt es auch
 * hier keine zweite Regel, gerechnet wird mit {@link NightRunOutcome#verstummt}.
 *
 * @param zustand wie der Lauf erscheint
 * @param dauer beim offenen Lauf bis jetzt, beim verstummten bis zu seinem letzten Lebenszeichen,
 *     sonst die gemeldete
 * @param meldung die letzte Meldung; {@code null}, wenn der Lauf keine hat
 */
public record MobilerLauf(Zustand zustand, Duration dauer, @Nullable LetzteMeldung meldung) {

  /** Woher die letzte Meldung eines Laufs stammt. */
  public enum MeldungsArt {
    /** Der Grund, mit dem der Lauf abbrach. */
    ABBRUCHGRUND,
    /** Der Auszug des Pakets, das den Grund trägt. */
    PAKET,
    /** Der Lauf meldet sich seit {@code seit} nicht mehr. */
    STILLE,
    /** Die Stufe, an der der Lauf zuletzt begann. */
    STUFE
  }

  /**
   * Die letzte Meldung als Daten — den Satz bildet die Oberfläche in der Sprache der Installation,
   * wie beim Befund ({@link NightRunOutcome}).
   *
   * @param art woher die Meldung stammt
   * @param text Abbruchgrund oder Auszug; {@code null} bei Stille und Stufe
   * @param cardNumber Karte des Pakets oder der Stufe; sonst {@code null}
   * @param stage Name der Stufe; nur bei {@link MeldungsArt#STUFE}
   * @param seit letztes Lebenszeichen bei Stille, Beginn der Stufe bei Stufe; sonst {@code null}
   */
  public record LetzteMeldung(
      MeldungsArt art,
      @Nullable String text,
      @Nullable Integer cardNumber,
      @Nullable String stage,
      @Nullable Instant seit) {}

  /**
   * Bildet die Sicht auf einen Lauf.
   *
   * @param run der Lauf
   * @param items seine Pakete, in Laufreihenfolge
   * @param jetzt der Zeitpunkt, gegen den das Lebenszeichen gemessen wird
   * @param stilleFrist die Stille, die ein unfertiger Lauf sich erlauben darf
   */
  public static MobilerLauf von(
      NightRun run, List<NightRunItem> items, Instant jetzt, Duration stilleFrist) {
    Optional<Meldegrund> grund = Meldegrund.von(run, items, jetzt, stilleFrist);
    boolean offen = !run.complete() && run.closedAt() == null;
    boolean verstummt =
        offen && NightRunOutcome.verstummt(run.startedAt(), run.updatedAt(), jetzt, stilleFrist);
    Zustand zustand =
        grund.map(Meldegrund::zustand).orElse(offen ? Zustand.LAEUFT : Zustand.BEENDET);
    Duration dauer =
        offen
            ? Duration.between(run.startedAt(), verstummt ? lebenszeichen(run) : jetzt)
            : Duration.ofMillis(run.durationMs());
    return new MobilerLauf(zustand, dauer, meldung(run, grund, verstummt));
  }

  /**
   * Die letzte Meldung (E19): der Abbruchgrund, sonst der Auszug des Pakets, das den Grund trägt,
   * sonst beim verstummten Lauf die Stille, sonst die zuletzt begonnene Stufe.
   */
  private static @Nullable LetzteMeldung meldung(
      NightRun run, Optional<Meldegrund> grund, boolean verstummt) {
    if (run.abortReason() != null) {
      return new LetzteMeldung(MeldungsArt.ABBRUCHGRUND, run.abortReason(), null, null, null);
    }
    NightRunItem paket = grund.map(Meldegrund::paket).orElse(null);
    if (paket != null && hatText(paket.excerpt())) {
      return new LetzteMeldung(MeldungsArt.PAKET, paket.excerpt(), paket.cardNumber(), null, null);
    }
    if (verstummt) {
      return new LetzteMeldung(MeldungsArt.STILLE, null, null, null, lebenszeichen(run));
    }
    return run.current().stream()
        .max(Comparator.comparing(RunnerStufe::startedAt))
        .map(
            stufe ->
                new LetzteMeldung(
                    MeldungsArt.STUFE, null, stufe.cardNumber(), stufe.stage(), stufe.startedAt()))
        .orElse(null);
  }

  private static boolean hatText(@Nullable String text) {
    return text != null && !text.isBlank();
  }

  /** Gemessen wie in {@link NightRunOutcome#verstummt}: ohne Fortschreibung zählt der Start. */
  private static Instant lebenszeichen(NightRun run) {
    Instant updatedAt = run.updatedAt();
    return updatedAt == null ? run.startedAt() : updatedAt;
  }
}
