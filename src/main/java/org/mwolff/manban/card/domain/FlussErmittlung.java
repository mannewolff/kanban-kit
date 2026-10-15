package org.mwolff.manban.card.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Ermittelt die Tage der Platte „Fluss“ aus den Aufenthalten eines Boards (Plan #1765, E7, E9,
 * E11): je Kalendertag in der Zone des Lesers die fünf Bänder, ob der Stand sicher bestimmbar ist,
 * und den Datenbeginn. Rein, ohne Datenbank.
 *
 * <p>Ein Tag zeigt den Stand an seinem Ende, heute den Stand jetzt. Done zählt nur Aufenthalte,
 * deren bekannter Eintritt ab Beginn des ersten Tages liegt — unmittelbar vor dem Zeitraum steht
 * Done auf null (AK 5). Ein Tag ist unbekannt, wenn sein Ende in einer unbekannten Spanne des
 * Boards liegt: einem Aufenthalt ohne Zustand (E7), der Zeit vor einem Aufenthalt mit unbekanntem
 * Eintritt (E9) oder, solange der Nachtrag nicht gelaufen ist, der Zeit vor dem Erfassungsbeginn.
 */
public final class FlussErmittlung {

  /** Zwölf Wochen in Kalendertagen — die Länge von „Durchsatz“ (E9). */
  public static final int TAGE = 84;

  /**
   * Ein Tag der Platte; ist der Stand unbekannt, trägt er keine Zahlen.
   *
   * @param known ob der Stand am Tagesende sicher bestimmbar ist
   * @param done die ab Beginn des Zeitraums fertig gewordenen Karten
   */
  public record Tag(
      LocalDate datum,
      boolean known,
      @Nullable Integer backlog,
      @Nullable Integer ready,
      @Nullable Integer inProgress,
      @Nullable Integer inReview,
      @Nullable Integer done) {

    static Tag unbekannt(LocalDate datum) {
      return new Tag(datum, false, null, null, null, null, null);
    }

    static Tag bekannt(LocalDate datum, Map<CardStatus, Integer> baender) {
      return new Tag(
          datum,
          true,
          baender.get(CardStatus.BACKLOG),
          baender.get(CardStatus.READY),
          baender.get(CardStatus.IN_PROGRESS),
          baender.get(CardStatus.IN_REVIEW),
          baender.get(CardStatus.DONE));
    }
  }

  /**
   * Die Tage des Zeitraums, der älteste zuerst.
   *
   * @param dataSince der erste Tag mit Daten, wenn er nach Beginn des Zeitraums liegt; sonst {@code
   *     null}
   */
  public record Fluss(List<Tag> tage, @Nullable LocalDate dataSince) {}

  /** Eine halboffene Zeitspanne; {@code bis == null} heißt „dauert an“. */
  private record Spanne(Instant von, @Nullable Instant bis) {

    /** Ob die Spanne den Stand unmittelbar vor dem Stichzeitpunkt trägt. */
    boolean traegt(Instant stich) {
      return von.isBefore(stich) && (bis == null || !bis.isBefore(stich));
    }
  }

  private FlussErmittlung() {}

  /**
   * Die Tage bis einschließlich heute.
   *
   * @param tage Anzahl der Kalendertage, {@link #TAGE}
   * @param zone Zone des Lesers; in ihr beginnen und enden die Tage
   * @param boardId Board des Leitstands — nur seine Aufenthalte zählen (E11)
   * @param aufenthalte Aufenthalte der Karten, auch auf anderen Boards
   * @param nachgetragen ob der Nachtrag aus dem Spaltenverlauf gelaufen ist
   * @param erfassungsbeginn seit wann Zustandswechsel erfasst werden
   */
  public static Fluss ermittle(
      int tage,
      ZoneId zone,
      Instant jetzt,
      long boardId,
      List<FlussStay> aufenthalte,
      boolean nachgetragen,
      Instant erfassungsbeginn) {
    LocalDate heute = LocalDate.ofInstant(jetzt, zone);
    LocalDate erster = heute.minusDays(tage - 1L);
    Instant zeitraumBeginn = erster.atStartOfDay(zone).toInstant();
    List<FlussStay> amBoard = aufenthalte.stream().filter(a -> a.boardId() == boardId).toList();
    List<Spanne> unbekannt =
        unbekannteSpannen(amBoard, aufenthalte, nachgetragen, erfassungsbeginn);

    List<Tag> ergebnis = new ArrayList<>(tage);
    for (int i = 0; i < tage; i++) {
      LocalDate datum = erster.plusDays(i);
      Instant stich =
          datum.equals(heute) ? jetzt : datum.plusDays(1).atStartOfDay(zone).toInstant();
      boolean known = unbekannt.stream().noneMatch(spanne -> spanne.traegt(stich));
      ergebnis.add(
          known
              ? Tag.bekannt(datum, baender(amBoard, stich, zeitraumBeginn))
              : Tag.unbekannt(datum));
    }
    LocalDate dataSince =
        ergebnis.getFirst().known()
            ? null
            : ergebnis.stream().filter(Tag::known).map(Tag::datum).findFirst().orElse(null);
    return new Fluss(List.copyOf(ergebnis), dataSince);
  }

  /**
   * Die Spannen ohne sicher bestimmbaren Stand auf dem Board. Vor einem Aufenthalt mit unbekanntem
   * Eintritt ist die Karte unbekannt bis zurück zum Ende ihres vorigen Aufenthalts, ohne einen
   * solchen unbegrenzt (E9).
   */
  private static List<Spanne> unbekannteSpannen(
      List<FlussStay> amBoard,
      List<FlussStay> aufenthalte,
      boolean nachgetragen,
      Instant erfassungsbeginn) {
    Stream<Spanne> ohneZustand =
        amBoard.stream()
            .filter(a -> a.status() == null)
            .map(a -> new Spanne(a.enteredAt(), a.leftAt()));
    Stream<Spanne> vorUnbekanntemEintritt =
        amBoard.stream()
            .filter(a -> !a.enteredKnown())
            .map(a -> new Spanne(vorigesEnde(a, aufenthalte), a.enteredAt()));
    Stream<Spanne> vorDerErfassung =
        nachgetragen ? Stream.empty() : Stream.of(new Spanne(Instant.MIN, erfassungsbeginn));
    return Stream.of(ohneZustand, vorUnbekanntemEintritt, vorDerErfassung).flatMap(s -> s).toList();
  }

  private static Instant vorigesEnde(FlussStay aufenthalt, List<FlussStay> aufenthalte) {
    return aufenthalte.stream()
        .filter(a -> a.cardId() == aufenthalt.cardId())
        .map(FlussStay::leftAt)
        .flatMap(Stream::ofNullable)
        .filter(ende -> !ende.isAfter(aufenthalt.enteredAt()))
        .max(Instant::compareTo)
        .orElse(Instant.MIN);
  }

  private static Map<CardStatus, Integer> baender(
      List<FlussStay> amBoard, Instant stich, Instant zeitraumBeginn) {
    Map<CardStatus, Integer> baender = new EnumMap<>(CardStatus.class);
    for (CardStatus status : CardStatus.values()) {
      baender.put(status, 0);
    }
    // Ein Aufenthalt ohne Zustand, der den Stich trägt, macht den Tag unbekannt (E7) — hier bleibt
    // er ohne Band.
    amBoard.stream()
        .filter(a -> new Spanne(a.enteredAt(), a.leftAt()).traegt(stich))
        .filter(a -> a.status() != CardStatus.DONE || zaehltAlsFertig(a, zeitraumBeginn))
        .map(FlussStay::status)
        .flatMap(Stream::ofNullable)
        .forEach(status -> baender.merge(status, 1, Integer::sum));
    return baender;
  }

  /**
   * Ein Done-Aufenthalt zählt nur mit bekanntem Eintritt ab Beginn des Zeitraums — eine Saat ohne
   * {@code moved_to_done_at} ist kein Abschluss von heute (AK 18).
   */
  private static boolean zaehltAlsFertig(FlussStay aufenthalt, Instant zeitraumBeginn) {
    return aufenthalt.enteredKnown() && !aufenthalt.enteredAt().isBefore(zeitraumBeginn);
  }
}
