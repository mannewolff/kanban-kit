package org.mwolff.manban.nightrun.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.NightRunOutcome.Verdict;

/**
 * Was der Runner eines Projekts jetzt tut (Issue #1727, Plan #1724 E2, E3, E8, E14).
 *
 * <p>Abgeleitet allein aus den gespeicherten Läufen, ohne I/O. Jeder nicht beendete Lauf läuft oder
 * ist verstummt; die Schweigefrist ist die Stillefrist des Befunds ({@link
 * NightRunOutcome#verstummt}, E3) — eine Frist, eine Rechnung. Beendet ist ein Lauf, der sich
 * abgeschlossen gemeldet hat oder von Hand beendet wurde ({@code closedAt}, E8).
 *
 * <p>Der letzte beendete Lauf steht auch dann da, wenn ein anderer läuft: Szenario 1 (E14) zeigt
 * die laufende Kette neben der Umsetzungsnacht, die ohne Paket endete.
 *
 * @param gesamt ob ein Lauf läuft oder verstummt ist, sonst ob es je einen gab
 * @param laeufe die laufenden und verstummten Läufe, in der Reihenfolge der Eingabe
 * @param letzterLauf der zuletzt beendete Lauf; {@code null}, wenn keiner beendet ist
 */
public record RunnerLage(
    Gesamt gesamt, List<AktiverLauf> laeufe, @Nullable LetzterLauf letzterLauf) {

  public RunnerLage {
    laeufe = List.copyOf(laeufe);
  }

  /** Der Zustand des Runners als Ganzes. */
  public enum Gesamt {

    /** Mindestens ein Lauf läuft oder ist verstummt. */
    AKTIV,

    /** Kein Lauf läuft oder ist verstummt, aber es gab schon einen. */
    KEIN_RUNNER,

    /** Im Projekt wurde noch nie ein Lauf gestartet. */
    NIE
  }

  /** Der Zustand eines nicht beendeten Laufs. */
  public enum LaufZustand {

    /** Hat sich innerhalb der Stillefrist gemeldet. */
    LAEUFT,

    /** Gilt als laufend, hat sich aber länger als die Stillefrist nicht gemeldet. */
    VERSTUMMT
  }

  /**
   * Ein laufender oder verstummter Lauf.
   *
   * @param runId der Lauf
   * @param mode Art des Laufs — Kette oder Umsetzungsnacht
   * @param startedAt Beginn des Laufs
   * @param letzteMeldung das letzte Lebenszeichen: die letzte Meldung, ohne sie der Start
   * @param zustand läuft oder verstummt
   * @param stufen die Stufen aus {@code current}, wie gemeldet
   * @param letzteStufe nur beim verstummten Lauf: die zuletzt begonnene Stufe; {@code null}, wenn
   *     der Lauf läuft oder keine Stufe gemeldet hat — die Anzeige setzt dann „nicht gemeldet“ ein
   */
  public record AktiverLauf(
      long runId,
      NightRunMode mode,
      Instant startedAt,
      Instant letzteMeldung,
      LaufZustand zustand,
      List<RunnerStufe> stufen,
      @Nullable RunnerStufe letzteStufe) {

    public AktiverLauf {
      stufen = List.copyOf(stufen);
    }
  }

  /**
   * Der zuletzt beendete Lauf.
   *
   * @param runId der Lauf
   * @param mode Art des Laufs
   * @param startedAt Beginn des Laufs
   * @param ende von Hand beendet: der Zeitpunkt der Beendigung, sonst Beginn plus Dauer
   * @param ergebnis der Ausgang über {@link NightRunOutcome#of}
   * @param noWorkReason der gemeldete Grund eines Laufs ohne Arbeit; {@code null}, wenn er keinen
   *     trägt
   */
  public record LetzterLauf(
      long runId,
      NightRunMode mode,
      Instant startedAt,
      Instant ende,
      Verdict ergebnis,
      @Nullable String noWorkReason) {}

  /**
   * Leitet die Lage aus den Läufen eines Projekts ab.
   *
   * @param laeufe die Läufe des Projekts, jüngster Start zuerst
   * @param pakete Pakete der Läufe; ausgewertet werden nur die des letzten beendeten Laufs (siehe
   *     {@link #letzterBeendeter}), fremde Pakete werden übergangen
   * @param jetzt der Zeitpunkt, gegen den die Stillefrist gemessen wird
   * @param stilleFrist die Stille, die ein unfertiger Lauf sich erlauben darf
   */
  public static RunnerLage ermittle(
      List<NightRun> laeufe, List<NightRunItem> pakete, Instant jetzt, Duration stilleFrist) {
    if (laeufe.isEmpty()) {
      return new RunnerLage(Gesamt.NIE, List.of(), null);
    }
    List<AktiverLauf> aktive =
        laeufe.stream()
            .filter(lauf -> !beendet(lauf))
            .map(lauf -> aktiv(lauf, jetzt, stilleFrist))
            .toList();
    LetzterLauf letzter =
        letzterBeendeter(laeufe)
            .map(lauf -> letzter(lauf, pakete, jetzt, stilleFrist))
            .orElse(null);
    return new RunnerLage(aktive.isEmpty() ? Gesamt.KEIN_RUNNER : Gesamt.AKTIV, aktive, letzter);
  }

  /**
   * Der zuletzt beendete Lauf — der mit dem spätesten Ende, bei gleichem Ende der später
   * gestartete.
   *
   * <p>Eigene Methode, damit der Dienst nur die Pakete dieses einen Laufs lädt: Der Ringpuffer hält
   * bis zu 190 Läufe, und jedes Paket trägt bis zu 4.000 Zeichen Auszug.
   */
  public static Optional<NightRun> letzterBeendeter(List<NightRun> laeufe) {
    return laeufe.stream()
        .filter(RunnerLage::beendet)
        .max(Comparator.comparing(RunnerLage::ende).thenComparing(NightRun::startedAt));
  }

  /** Abgeschlossen gemeldet oder von Hand beendet (E8) — wie {@link NightRunOutcome#of}. */
  private static boolean beendet(NightRun lauf) {
    return lauf.complete() || lauf.closedAt() != null;
  }

  /**
   * Das Ende eines beendeten Laufs. Von Hand beendet heißt: Der Lauf meldete sich nie ab, sein Ende
   * ist die Beendigung. Meldet er sich danach doch noch vollständig, gilt seine Meldung — dieselbe
   * Rangfolge wie im Befund.
   *
   * <p>Nur für beendete Läufe ({@link #beendet}): Wer sich nicht vollständig gemeldet hat, trägt
   * dann zwingend {@code closedAt} — eine eigene Prüfung darauf wäre nicht zu unterscheiden und
   * ließe einen äquivalenten Mutanten stehen (Issue #1734).
   */
  private static Instant ende(NightRun lauf) {
    if (lauf.complete()) {
      return lauf.startedAt().plusMillis(lauf.durationMs());
    }
    return Objects.requireNonNull(lauf.closedAt(), "ende nur für beendete Läufe");
  }

  private static AktiverLauf aktiv(NightRun lauf, Instant jetzt, Duration stilleFrist) {
    Instant updatedAt = lauf.updatedAt();
    Instant letzteMeldung = updatedAt == null ? lauf.startedAt() : updatedAt;
    boolean verstummt = NightRunOutcome.verstummt(lauf.startedAt(), updatedAt, jetzt, stilleFrist);
    return new AktiverLauf(
        lauf.requireId(),
        lauf.mode(),
        lauf.startedAt(),
        letzteMeldung,
        verstummt ? LaufZustand.VERSTUMMT : LaufZustand.LAEUFT,
        lauf.current(),
        verstummt ? zuletztBegonnen(lauf.current()) : null);
  }

  private static @Nullable RunnerStufe zuletztBegonnen(List<RunnerStufe> stufen) {
    return stufen.stream().max(Comparator.comparing(RunnerStufe::startedAt)).orElse(null);
  }

  private static LetzterLauf letzter(
      NightRun lauf, List<NightRunItem> pakete, Instant jetzt, Duration stilleFrist) {
    Long runId = lauf.requireId();
    List<NightRunItem> eigene =
        pakete.stream().filter(paket -> Objects.equals(paket.nightRunId(), runId)).toList();
    NightRunOutcome befund =
        NightRunOutcome.of(
            lauf.complete(),
            lauf.closedAt(),
            lauf.noWorkReason(),
            lauf.abortReason(),
            lauf.abortKind(),
            lauf.mode(),
            eigene,
            lauf.startedAt(),
            lauf.updatedAt(),
            jetzt,
            stilleFrist);
    return new LetzterLauf(
        runId, lauf.mode(), lauf.startedAt(), ende(lauf), befund.verdict(), lauf.noWorkReason());
  }
}
