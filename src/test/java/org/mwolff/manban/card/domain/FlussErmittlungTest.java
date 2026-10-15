package org.mwolff.manban.card.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.domain.FlussErmittlung.Fluss;
import org.mwolff.manban.card.domain.FlussErmittlung.Tag;
import org.mwolff.manban.card.domain.FlussStay.EndedBy;

/** Tagesermittlung der Platte „Fluss“ (Issue #1771, Plan #1765, E7, E9, E11). */
class FlussErmittlungTest {

  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
  private static final long BOARD = 1L;
  private static final long ANDERES_BOARD = 2L;

  /** Heute ist der 2026-10-26 (Montag), 13:00 Berliner Zeit; der Zeitraum beginnt am 2026-08-04. */
  private static final LocalDate HEUTE = LocalDate.of(2026, 10, 26);

  private static final Instant JETZT = lokal(HEUTE, 13, 0);
  private static final LocalDate ERSTER_TAG = HEUTE.minusDays(83);
  private static final Instant LANGE_VORHER = Instant.parse("2026-01-01T00:00:00Z");

  private static Instant lokal(LocalDate datum, int stunde, int minute) {
    return LocalDateTime.of(datum, LocalTime.of(stunde, minute)).atZone(BERLIN).toInstant();
  }

  private static FlussStay stay(
      long karte,
      long board,
      @Nullable CardStatus status,
      Instant von,
      @Nullable Instant bis,
      boolean bekannt) {
    return new FlussStay(
        null, karte, board, status, von, bis, bekannt, bis == null ? null : EndedBy.STATUS);
  }

  private static FlussStay stay(
      long karte, @Nullable CardStatus status, Instant von, @Nullable Instant bis) {
    return stay(karte, BOARD, status, von, bis, true);
  }

  private static Fluss ermittle(List<FlussStay> aufenthalte) {
    return ermittle(JETZT, aufenthalte, true, LANGE_VORHER);
  }

  private static Fluss ermittle(
      Instant jetzt, List<FlussStay> aufenthalte, boolean nachgetragen, Instant seit) {
    return FlussErmittlung.ermittle(
        FlussErmittlung.TAGE, BERLIN, jetzt, BOARD, aufenthalte, nachgetragen, seit);
  }

  private static Tag tag(Fluss fluss, LocalDate datum) {
    return fluss.tage().stream().filter(t -> t.datum().equals(datum)).findFirst().orElseThrow();
  }

  @Test
  @DisplayName("Szenario: Stau in In Review")
  void stauInInReview() {
    List<FlussStay> aufenthalte = new ArrayList<>();
    long karte = 100;
    LocalDate wochenbeginn = HEUTE.minusDays(6);
    for (int nacht = 0; nacht < 7; nacht++) {
      Instant nachtlauf = lokal(wochenbeginn.plusDays(nacht), 2, 30);
      for (int paket = 0; paket < 3; paket++) {
        aufenthalte.add(
            stay(karte, CardStatus.IN_PROGRESS, nachtlauf.minusSeconds(3600), nachtlauf));
        aufenthalte.add(stay(karte, CardStatus.IN_REVIEW, nachtlauf, null));
        karte++;
      }
    }

    Fluss fluss = ermittle(aufenthalte);

    List<Integer> inReview = fluss.tage().subList(77, 84).stream().map(Tag::inReview).toList();
    assertThat(inReview).containsExactly(3, 6, 9, 12, 15, 18, 21);
    assertThat(fluss.tage().getLast().inReview()).isGreaterThanOrEqualTo(21);
    assertThat(tag(fluss, wochenbeginn.minusDays(1)).inReview()).isZero();
  }

  @Test
  void vierundachtzigKalendertageBisEinschliesslichHeute() {
    Fluss fluss = ermittle(List.of());

    assertThat(fluss.tage()).hasSize(84);
    assertThat(fluss.tage().getFirst().datum()).isEqualTo(ERSTER_TAG);
    assertThat(fluss.tage().getLast().datum()).isEqualTo(HEUTE);
    assertThat(fluss.tage())
        .allSatisfy(
            t -> {
              assertThat(t.known()).isTrue();
              assertThat(t.backlog()).isZero();
              assertThat(t.ready()).isZero();
              assertThat(t.inProgress()).isZero();
              assertThat(t.inReview()).isZero();
              assertThat(t.done()).isZero();
            });
    assertThat(fluss.dataSince()).isNull();
  }

  @Test
  void zaehltJedenZustandInSeinemBand() {
    Instant vorher = lokal(ERSTER_TAG.minusDays(10), 12, 0);
    Fluss fluss =
        ermittle(
            List.of(
                stay(1, CardStatus.BACKLOG, vorher, null),
                stay(2, CardStatus.READY, vorher, null),
                stay(3, CardStatus.IN_PROGRESS, vorher, null),
                stay(4, CardStatus.IN_REVIEW, vorher, null),
                stay(5, CardStatus.IN_REVIEW, vorher, null)));

    Tag heute = fluss.tage().getLast();
    assertThat(heute.backlog()).isEqualTo(1);
    assertThat(heute.ready()).isEqualTo(1);
    assertThat(heute.inProgress()).isEqualTo(1);
    assertThat(heute.inReview()).isEqualTo(2);
    assertThat(heute.done()).isZero();
  }

  @Test
  void standAmTagesende() {
    LocalDate tag = HEUTE.minusDays(10);
    Instant wechsel = lokal(tag, 23, 59);
    Instant mitternacht = lokal(tag.plusDays(1), 0, 0);
    Fluss fluss =
        ermittle(
            List.of(
                stay(1, CardStatus.READY, LANGE_VORHER, wechsel),
                stay(1, CardStatus.IN_PROGRESS, wechsel, null),
                stay(2, CardStatus.READY, LANGE_VORHER, mitternacht),
                stay(2, CardStatus.IN_PROGRESS, mitternacht, null)));

    assertThat(tag(fluss, tag.minusDays(1)).ready()).isEqualTo(2);
    assertThat(tag(fluss, tag).ready()).isEqualTo(1);
    assertThat(tag(fluss, tag).inProgress()).isEqualTo(1);
    assertThat(tag(fluss, tag.plusDays(1)).ready()).isZero();
    assertThat(tag(fluss, tag.plusDays(1)).inProgress()).isEqualTo(2);
  }

  @Test
  void heuteIstDerStandJetzt() {
    Instant gleichDanach = JETZT.plusSeconds(60);
    Fluss fluss =
        ermittle(
            List.of(
                stay(1, CardStatus.READY, LANGE_VORHER, JETZT.minusSeconds(60)),
                stay(1, CardStatus.IN_PROGRESS, JETZT.minusSeconds(60), null),
                stay(2, CardStatus.READY, gleichDanach, null)));

    Tag heute = fluss.tage().getLast();
    assertThat(heute.ready()).isZero();
    assertThat(heute.inProgress()).isEqualTo(1);
  }

  @Test
  void doneBeginntVorDemZeitraumBeiNull() {
    Instant ersterTagBeginn = lokal(ERSTER_TAG, 0, 0);
    Fluss fluss =
        ermittle(
            List.of(
                stay(1, CardStatus.DONE, ersterTagBeginn.minusSeconds(1), null),
                stay(2, CardStatus.DONE, ersterTagBeginn, null),
                stay(3, CardStatus.DONE, lokal(ERSTER_TAG, 18, 0), null),
                stay(4, CardStatus.DONE, lokal(ERSTER_TAG.plusDays(1), 9, 0), null)));

    assertThat(fluss.tage().getFirst().done()).isEqualTo(2);
    assertThat(fluss.tage().get(1).done()).isEqualTo(3);
    assertThat(fluss.tage().getLast().done()).isEqualTo(3);
  }

  @Test
  void doneMitUnbekanntemEintrittZaehltNicht() {
    Instant saat = lokal(HEUTE.minusDays(20), 10, 0);
    Fluss fluss =
        ermittle(
            JETZT,
            List.of(
                stay(1, BOARD, CardStatus.DONE, saat, null, false),
                stay(2, CardStatus.DONE, saat, null)),
            true,
            LANGE_VORHER);

    assertThat(fluss.tage().getLast().done()).isEqualTo(1);
    assertThat(fluss.tage().getLast().known()).isTrue();
  }

  @Test
  void verlaesstEineKarteDoneSinktDoneUndErneutFertigZaehltSieEinmal() {
    LocalDate fertig = HEUTE.minusDays(30);
    LocalDate zurueck = HEUTE.minusDays(20);
    LocalDate wiederFertig = HEUTE.minusDays(10);
    Fluss fluss =
        ermittle(
            List.of(
                stay(1, CardStatus.IN_PROGRESS, LANGE_VORHER, lokal(fertig, 10, 0)),
                stay(1, CardStatus.DONE, lokal(fertig, 10, 0), lokal(zurueck, 10, 0)),
                stay(1, CardStatus.IN_PROGRESS, lokal(zurueck, 10, 0), lokal(wiederFertig, 10, 0)),
                stay(1, CardStatus.DONE, lokal(wiederFertig, 10, 0), null)));

    assertThat(tag(fluss, fertig.minusDays(1)).done()).isZero();
    assertThat(tag(fluss, fertig).done()).isEqualTo(1);
    assertThat(tag(fluss, zurueck.minusDays(1)).done()).isEqualTo(1);
    assertThat(tag(fluss, zurueck).done()).isZero();
    assertThat(tag(fluss, zurueck).inProgress()).isEqualTo(1);
    assertThat(tag(fluss, wiederFertig).done()).isEqualTo(1);
    assertThat(tag(fluss, wiederFertig).inProgress()).isZero();
    assertThat(fluss.tage().getLast().done()).isEqualTo(1);
  }

  @Test
  void fuenfundzwanzigStundenTagRechnetInDerZoneDesLesers() {
    // 2026-10-25: Ende der Sommerzeit in Berlin, der Tag hat 25 Stunden.
    LocalDate umstellung = LocalDate.of(2026, 10, 25);
    Instant spaetAmTag = Instant.parse("2026-10-25T22:30:00Z"); // 23:30 MEZ am 25.
    Instant nachMitternacht = Instant.parse("2026-10-25T23:30:00Z"); // 00:30 MEZ am 26.
    Fluss fluss =
        ermittle(
            List.of(
                stay(1, CardStatus.READY, spaetAmTag, null),
                stay(2, CardStatus.IN_REVIEW, nachMitternacht, null)));

    assertThat(tag(fluss, umstellung).ready()).isEqualTo(1);
    assertThat(tag(fluss, umstellung).inReview()).isZero();
    assertThat(tag(fluss, umstellung.plusDays(1)).inReview()).isEqualTo(1);
  }

  @Test
  void dreiundzwanzigStundenTagRechnetInDerZoneDesLesers() {
    // 2026-03-29: Beginn der Sommerzeit in Berlin, der Tag hat 23 Stunden.
    LocalDate umstellung = LocalDate.of(2026, 3, 29);
    Instant jetzt = lokal(LocalDate.of(2026, 4, 10), 12, 0);
    Instant amTagesbeginn = Instant.parse("2026-03-28T23:30:00Z"); // 00:30 MEZ am 29.
    Instant nachMitternacht = Instant.parse("2026-03-29T22:30:00Z"); // 00:30 MESZ am 30.
    Fluss fluss =
        ermittle(
            jetzt,
            List.of(
                stay(1, CardStatus.READY, amTagesbeginn, null),
                stay(2, CardStatus.IN_REVIEW, nachMitternacht, null)),
            true,
            LANGE_VORHER);

    assertThat(tag(fluss, umstellung.minusDays(1)).ready()).isZero();
    assertThat(tag(fluss, umstellung).ready()).isEqualTo(1);
    assertThat(tag(fluss, umstellung).inReview()).isZero();
    assertThat(tag(fluss, umstellung.plusDays(1)).inReview()).isEqualTo(1);
  }

  @Test
  void unbekannteSpanneAmTagesendeMachtDenTagLeer() {
    LocalDate leer = HEUTE.minusDays(40);
    Fluss fluss =
        ermittle(
            List.of(
                stay(1, CardStatus.READY, LANGE_VORHER, lokal(leer, 20, 0)),
                stay(1, null, lokal(leer, 20, 0), lokal(leer.plusDays(1), 8, 0)),
                stay(1, BOARD, CardStatus.READY, lokal(leer.plusDays(1), 8, 0), null, false),
                stay(2, CardStatus.IN_PROGRESS, LANGE_VORHER, null)));

    Tag tag = tag(fluss, leer);
    assertThat(tag.known()).isFalse();
    assertThat(tag.backlog()).isNull();
    assertThat(tag.ready()).isNull();
    assertThat(tag.inProgress()).isNull();
    assertThat(tag.inReview()).isNull();
    assertThat(tag.done()).isNull();
    assertThat(tag(fluss, leer.minusDays(1)).known()).isTrue();
    assertThat(tag(fluss, leer.minusDays(1)).ready()).isEqualTo(1);
    assertThat(tag(fluss, leer.plusDays(1)).known()).isTrue();
    assertThat(tag(fluss, leer.plusDays(1)).ready()).isEqualTo(1);
    assertThat(fluss.dataSince()).isNull();
  }

  @Test
  void unbekannteSpanneInnerhalbDesTagesLaesstIhnBekannt() {
    LocalDate tag = HEUTE.minusDays(40);
    Fluss fluss =
        ermittle(
            List.of(
                stay(1, CardStatus.READY, LANGE_VORHER, lokal(tag, 8, 0)),
                stay(1, null, lokal(tag, 8, 0), lokal(tag, 9, 0)),
                stay(1, CardStatus.IN_PROGRESS, lokal(tag, 9, 0), null)));

    assertThat(tag(fluss, tag).known()).isTrue();
    assertThat(tag(fluss, tag).inProgress()).isEqualTo(1);
  }

  @Test
  void datenbeginnIstDerErsteTagMitDaten() {
    LocalDate beginn = HEUTE.minusDays(50);
    Fluss fluss =
        ermittle(
            List.of(
                stay(1, null, LANGE_VORHER, lokal(beginn, 10, 0)),
                stay(1, BOARD, CardStatus.BACKLOG, lokal(beginn, 10, 0), null, false)));

    assertThat(tag(fluss, beginn.minusDays(1)).known()).isFalse();
    assertThat(fluss.tage().getFirst().known()).isFalse();
    assertThat(tag(fluss, beginn).known()).isTrue();
    assertThat(tag(fluss, beginn).backlog()).isEqualTo(1);
    assertThat(fluss.dataSince()).isEqualTo(beginn);
  }

  @Test
  void ohneTagMitDatenBleibtDerDatenbeginnLeer() {
    Fluss fluss = ermittle(List.of(stay(1, null, LANGE_VORHER, null)));

    assertThat(fluss.tage()).noneMatch(Tag::known);
    assertThat(fluss.dataSince()).isNull();
  }

  @Test
  void saatMitUnbekanntemEintrittZaehltErstAbIhremBeginn() {
    LocalDate saattag = HEUTE.minusDays(5);
    Instant saat = lokal(saattag, 14, 0);
    Fluss fluss = ermittle(List.of(stay(1, BOARD, CardStatus.IN_REVIEW, saat, null, false)));

    assertThat(tag(fluss, saattag.minusDays(1)).known()).isFalse();
    assertThat(tag(fluss, saattag.minusDays(1)).inReview()).isNull();
    assertThat(fluss.tage().getFirst().known()).isFalse();
    assertThat(tag(fluss, saattag).known()).isTrue();
    assertThat(tag(fluss, saattag).inReview()).isEqualTo(1);
    assertThat(fluss.dataSince()).isEqualTo(saattag);
  }

  @Test
  void beendeteSaatLaesstDieZeitDavorUnbekannt() {
    LocalDate saattag = HEUTE.minusDays(5);
    Instant saat = lokal(saattag, 14, 0);
    Instant wechsel = lokal(saattag.plusDays(2), 9, 0);
    Fluss fluss =
        ermittle(
            List.of(
                stay(1, BOARD, CardStatus.READY, saat, wechsel, false),
                stay(1, CardStatus.IN_PROGRESS, wechsel, null)));

    assertThat(tag(fluss, saattag.minusDays(1)).known()).isFalse();
    assertThat(tag(fluss, saattag).ready()).isEqualTo(1);
    assertThat(fluss.tage().getLast().inProgress()).isEqualTo(1);
    assertThat(fluss.dataSince()).isEqualTo(saattag);
  }

  @Test
  void unbekannterEintrittNachEinemFrueherenAufenthaltLaesstDieZeitDavorBekannt() {
    LocalDate raus = HEUTE.minusDays(30);
    LocalDate wieder = HEUTE.minusDays(20);
    Fluss fluss =
        ermittle(
            List.of(
                new FlussStay(
                    null,
                    1,
                    BOARD,
                    CardStatus.READY,
                    LANGE_VORHER,
                    lokal(raus, 10, 0),
                    true,
                    EndedBy.EXIT),
                stay(1, BOARD, CardStatus.IN_REVIEW, lokal(wieder, 10, 0), null, false)));

    assertThat(tag(fluss, raus.minusDays(1)).known()).isTrue();
    assertThat(tag(fluss, raus.minusDays(1)).ready()).isEqualTo(1);
    assertThat(tag(fluss, raus).known()).isFalse();
    assertThat(tag(fluss, wieder.minusDays(1)).known()).isFalse();
    assertThat(tag(fluss, wieder).known()).isTrue();
    assertThat(tag(fluss, wieder).inReview()).isEqualTo(1);
    assertThat(fluss.dataSince()).isNull();
  }

  @Test
  void ohneNachtragSindDieTageVorDemErfassungsbeginnLeer() {
    LocalDate erfassungstag = HEUTE.minusDays(3);
    Instant seit = lokal(erfassungstag, 9, 0);
    List<FlussStay> aufenthalte =
        List.of(stay(1, CardStatus.READY, lokal(HEUTE.minusDays(60), 9, 0), null));

    Fluss ohne = ermittle(JETZT, aufenthalte, false, seit);
    Fluss mit = ermittle(JETZT, aufenthalte, true, seit);

    assertThat(tag(ohne, erfassungstag.minusDays(1)).known()).isFalse();
    assertThat(tag(ohne, erfassungstag).known()).isTrue();
    assertThat(tag(ohne, erfassungstag).ready()).isEqualTo(1);
    assertThat(ohne.dataSince()).isEqualTo(erfassungstag);
    assertThat(tag(mit, erfassungstag.minusDays(1)).known()).isTrue();
    assertThat(mit.dataSince()).isNull();
  }

  @Test
  void ohneNachtragIstEinTagMitEndeGenauAmErfassungsbeginnLeer() {
    LocalDate erfassungstag = HEUTE.minusDays(3);
    Instant seit = lokal(erfassungstag, 0, 0);

    Fluss fluss = ermittle(JETZT, List.of(), false, seit);

    assertThat(tag(fluss, erfassungstag.minusDays(1)).known()).isFalse();
    assertThat(tag(fluss, erfassungstag).known()).isTrue();
  }

  @Test
  void nurAufenthalteDesUebergebenenBoardsZaehlen() {
    LocalDate wechsel = HEUTE.minusDays(10);
    Fluss fluss =
        ermittle(
            List.of(
                stay(1, ANDERES_BOARD, CardStatus.READY, LANGE_VORHER, lokal(wechsel, 10, 0), true),
                stay(1, BOARD, CardStatus.READY, lokal(wechsel, 10, 0), null, true),
                stay(2, ANDERES_BOARD, null, LANGE_VORHER, null, true),
                stay(3, ANDERES_BOARD, CardStatus.DONE, lokal(wechsel, 9, 0), null, true),
                stay(4, ANDERES_BOARD, CardStatus.IN_REVIEW, lokal(wechsel, 9, 0), null, false)));

    assertThat(fluss.tage()).allMatch(Tag::known);
    assertThat(tag(fluss, wechsel.minusDays(1)).ready()).isZero();
    assertThat(tag(fluss, wechsel).ready()).isEqualTo(1);
    assertThat(fluss.tage().getLast().done()).isZero();
    assertThat(fluss.tage().getLast().inReview()).isZero();
  }
}
