package org.mwolff.manban.nightrun.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Warum ein Nachtlauf hängt oder gescheitert ist — der Grund, aus dem die mobile Oberfläche ihn
 * zeigt und der Meldejob eine Nachricht schickt (Issue #1994, Plan #1989 E11, fachliche Quelle
 * #1961 AK 10 und 11).
 *
 * <p><strong>Warum neben {@link NightRunOutcome}.</strong> Der Befund zeigt einen laufenden Lauf
 * nie rot: Er hat noch nichts zu melden. Die Nachricht aufs Handy soll aber kommen, sobald das
 * erste Paket wartet, festgefahren ist oder scheitert, auch wenn der Lauf weiterarbeitet. Der
 * Befund bleibt die Regel des Leitstands; hier zählen die Pakete schon während des Laufs. Für die
 * Stille gibt es trotzdem nur eine Regel, {@link NightRunOutcome#verstummt}.
 *
 * <p><strong>Kein Grund</strong> sind ein Ende an der eigenen Grenze (Budget einer Kettenstufe,
 * Obergrenze an Paketen, #1074 AK 11), ein Lauf ohne Arbeit und ein Lauf, der nicht anlief: Keiner
 * von ihnen trägt ein wartendes, festgefahrenes oder gescheitertes Paket. Ebenso wenig ein Lauf,
 * den ein Plattform-Admin von Hand beendet hat, und jede interaktive Sitzung.
 *
 * @param grund was mit dem Lauf ist
 * @param paket das Paket, das den Grund trägt; {@code null} beim verstummten Lauf
 */
public record Meldegrund(Grund grund, @Nullable NightRunItem paket) {

  /** Wie die mobile Oberfläche einen Lauf zeigt. */
  public enum Zustand {
    /** Arbeitet noch, ohne Grund zur Meldung. */
    LAEUFT,
    /** Ein Paket wartet auf jemanden oder ist an einer Prüfung festgefahren. */
    HAENGT,
    /** Ein Paket ist gescheitert oder hat sein Zeitbudget erschöpft, oder der Lauf verstummte. */
    GESCHEITERT,
    /** Beendet ohne Grund zur Meldung. */
    BEENDET
  }

  /** Die Gründe, der schwerste zuerst — die Reihenfolge entscheidet, wenn mehrere zutreffen. */
  public enum Grund {
    /** Ein Paket ist rot, außer am Zeitbudget und festgefahren — auch ein harter Abbruch. */
    GESCHEITERT(Zustand.GESCHEITERT),
    /** Ein Paket endete gelb oder rot an seinem Zeitbudget. */
    ZEITBUDGET(Zustand.GESCHEITERT),
    /** Der unfertige Lauf meldet sich länger als die Stillefrist nicht. */
    VERSTUMMT(Zustand.GESCHEITERT),
    /** Ein Paket ist an einer Prüfung festgefahren. */
    FESTGEFAHREN(Zustand.HAENGT),
    /** Ein Paket ist grau mit Fehlerklasse: zurückgestellt oder wartet auf einen Menschen. */
    WARTET(Zustand.HAENGT);

    private final Zustand anzeige;

    Grund(Zustand anzeige) {
      this.anzeige = anzeige;
    }

    /** Der Zustand, unter dem ein Lauf mit diesem Grund erscheint. */
    public Zustand zustand() {
      return anzeige;
    }
  }

  /** Der Zustand, unter dem der Lauf erscheint. */
  public Zustand zustand() {
    return grund.zustand();
  }

  /**
   * Der schwerste Grund des Laufs, oder keiner.
   *
   * <p>Die Zeit kommt als Parameter wie im Befund: Unter {@code ..domain..} gibt es keine
   * Spring-Abhängigkeit (Plan #1088 E2).
   *
   * @param run der Lauf
   * @param items seine Pakete, in Laufreihenfolge
   * @param jetzt der Zeitpunkt, gegen den das Lebenszeichen gemessen wird
   * @param stilleFrist die Stille, die ein unfertiger Lauf sich erlauben darf
   */
  public static Optional<Meldegrund> von(
      NightRun run, List<NightRunItem> items, Instant jetzt, Duration stilleFrist) {
    boolean offen = !run.complete();
    if (run.kind() != NightRunKind.NIGHT || (offen && run.closedAt() != null)) {
      return Optional.empty();
    }
    Stream<Meldegrund> stille =
        offen && NightRunOutcome.verstummt(run.startedAt(), run.updatedAt(), jetzt, stilleFrist)
            ? Stream.of(new Meldegrund(Grund.VERSTUMMT, null))
            : Stream.empty();
    // min nimmt bei Gleichstand das erste — die Laufreihenfolge, in der Kette ihre Umkehrung.
    return Stream.concat(ausPaketen(run.mode(), items), stille)
        .min(Comparator.comparing(Meldegrund::grund));
  }

  /**
   * Die Gründe der Pakete, in der Reihenfolge, in der gleichrangige um den Vorrang streiten: in
   * einer Kette umgekehrt, wie im Befund (Issue #1123) — gerissen ist sie am zuletzt gemeldeten.
   */
  private static Stream<Meldegrund> ausPaketen(NightRunMode mode, List<NightRunItem> items) {
    List<NightRunItem> reihenfolge = mode == NightRunMode.CHAIN ? items.reversed() : items;
    return reihenfolge.stream()
        .flatMap(item -> Stream.ofNullable(grundVon(item)).map(g -> new Meldegrund(g, item)));
  }

  /**
   * Der Grund eines Pakets. Grün meldet nie; grau ohne Fehlerklasse ist übergangen und kein Mangel
   * des Laufs; gelb meldet nur am Zeitbudget.
   */
  private static @Nullable Grund grundVon(NightRunItem item) {
    NightRunState state = item.state();
    NightRunErrorClass klasse = item.errorClass();
    if (state == NightRunState.GREEN) {
      return null;
    }
    if (klasse == NightRunErrorClass.STUCK) {
      return Grund.FESTGEFAHREN;
    }
    if (state == NightRunState.GREY) {
      return klasse == null ? null : Grund.WARTET;
    }
    if (klasse == NightRunErrorClass.TIME_BUDGET_EXCEEDED) {
      return Grund.ZEITBUDGET;
    }
    return state == NightRunState.RED ? Grund.GESCHEITERT : null;
  }
}
