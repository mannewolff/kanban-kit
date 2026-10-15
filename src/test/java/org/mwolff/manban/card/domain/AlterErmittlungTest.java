package org.mwolff.manban.card.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.domain.AlterErmittlung.Karte;
import org.mwolff.manban.card.domain.AlterErmittlung.Zustand;
import org.mwolff.manban.card.domain.FlussStay.EndedBy;

/** Altersermittlung der Platte „Alter in Arbeit“ (Issue #1772, Plan #1765, E8, E10, E11, E12). */
class AlterErmittlungTest {

  private static final long BOARD = 1L;
  private static final long ANDERES_BOARD = 2L;
  private static final Instant JETZT = Instant.parse("2026-10-26T12:00:00Z");
  private static final Duration TAG = Duration.ofDays(1);
  private static final Instant GRENZE = JETZT.minus(Duration.ofDays(84));

  private static FlussStay offen(long karte, CardStatus status, Duration alter) {
    return new FlussStay(null, karte, BOARD, status, JETZT.minus(alter), null, true, null);
  }

  /** Ein abgeschlossener Aufenthalt, der vor einem Tag endete. */
  private static FlussStay fertig(long karte, CardStatus status, Duration dauer) {
    return fertig(karte, status, dauer, JETZT.minus(TAG));
  }

  private static FlussStay fertig(long karte, CardStatus status, Duration dauer, Instant ende) {
    return stay(karte, BOARD, status, ende.minus(dauer), ende, true, EndedBy.STATUS);
  }

  private static FlussStay stay(
      long karte,
      long board,
      @Nullable CardStatus status,
      Instant von,
      @Nullable Instant bis,
      boolean bekannt,
      @Nullable EndedBy endedBy) {
    return new FlussStay(null, karte, board, status, von, bis, bekannt, endedBy);
  }

  /** Abgeschlossene Aufenthalte der angegebenen Dauern in Tagen, je auf einer eigenen Karte. */
  private static List<FlussStay> vergleiche(CardStatus status, long... tage) {
    List<FlussStay> aufenthalte = new ArrayList<>();
    for (int i = 0; i < tage.length; i++) {
      aufenthalte.add(fertig(500L + i, status, Duration.ofDays(tage[i])));
    }
    return aufenthalte;
  }

  private static List<Zustand> ermittle(List<FlussStay> aufenthalte) {
    return AlterErmittlung.ermittle(JETZT, BOARD, aufenthalte);
  }

  private static Zustand zustand(List<FlussStay> aufenthalte, CardStatus status) {
    return ermittle(aufenthalte).stream()
        .filter(z -> z.status() == status)
        .findFirst()
        .orElseThrow();
  }

  @Test
  @DisplayName("Szenario: Eine Karte liegt auffällig lange")
  void eineKarteLiegtAuffaelligLange() {
    List<FlussStay> aufenthalte =
        new ArrayList<>(vergleiche(CardStatus.IN_REVIEW, 1, 1, 2, 1, 2, 2, 5));
    aufenthalte.add(offen(10, CardStatus.IN_REVIEW, Duration.ofDays(1)));
    aufenthalte.add(offen(11, CardStatus.IN_REVIEW, Duration.ofDays(8)));
    aufenthalte.add(offen(12, CardStatus.IN_REVIEW, Duration.ofHours(30)));

    Zustand inReview = zustand(aufenthalte, CardStatus.IN_REVIEW);

    assertThat(inReview.comparisonCount()).isEqualTo(7);
    assertThat(inReview.threshold()).isEqualTo(Duration.ofDays(2));
    assertThat(inReview.cards())
        .containsExactly(
            new Karte(11, Duration.ofDays(8), false, true),
            new Karte(12, Duration.ofHours(30), false, false),
            new Karte(10, Duration.ofDays(1), false, false));
  }

  @Test
  @DisplayName("Szenario: Unbekannter Eintritt")
  void unbekannterEintritt() {
    List<FlussStay> aufenthalte =
        new ArrayList<>(vergleiche(CardStatus.IN_REVIEW, 1, 2, 3, 1, 2, 3));
    aufenthalte.add(
        stay(20, BOARD, CardStatus.IN_REVIEW, JETZT.minus(Duration.ofDays(20)), null, false, null));

    Zustand inReview = zustand(aufenthalte, CardStatus.IN_REVIEW);

    assertThat(inReview.threshold()).isEqualTo(Duration.ofDays(3));
    assertThat(inReview.cards()).containsExactly(new Karte(20, Duration.ofDays(20), true, true));
  }

  @Test
  @DisplayName("Szenario: Zu wenig Vergleichskarten")
  void zuWenigVergleichskarten() {
    List<FlussStay> aufenthalte = new ArrayList<>(vergleiche(CardStatus.IN_PROGRESS, 1, 1, 1));
    aufenthalte.add(offen(30, CardStatus.IN_PROGRESS, Duration.ofDays(40)));
    aufenthalte.add(offen(31, CardStatus.IN_PROGRESS, Duration.ofDays(2)));

    Zustand inProgress = zustand(aufenthalte, CardStatus.IN_PROGRESS);

    assertThat(inProgress.comparisonCount()).isEqualTo(3);
    assertThat(inProgress.threshold()).isNull();
    assertThat(inProgress.cards())
        .containsExactly(
            new Karte(30, Duration.ofDays(40), false, false),
            new Karte(31, Duration.ofDays(2), false, false));
  }

  @Test
  @DisplayName("Nur Statuswechsel mit bekanntem Eintritt, Ende in 84 × 24 Stunden, zählen (AK 7)")
  void nurAbgeschlosseneAufenthalteMitBekanntemEintrittZaehlen() {
    Instant vor = JETZT.minus(Duration.ofDays(3));
    List<FlussStay> aufenthalte =
        List.of(
            fertig(1, CardStatus.READY, TAG),
            fertig(2, CardStatus.READY, TAG, GRENZE),
            fertig(3, CardStatus.READY, TAG, GRENZE.minusSeconds(1)),
            stay(4, BOARD, CardStatus.READY, vor, JETZT, true, EndedBy.BOARD),
            stay(5, BOARD, CardStatus.READY, vor, JETZT, true, EndedBy.EXIT),
            stay(6, BOARD, CardStatus.READY, vor, JETZT, false, EndedBy.STATUS),
            stay(7, ANDERES_BOARD, CardStatus.READY, vor, JETZT, true, EndedBy.STATUS),
            stay(8, BOARD, CardStatus.IN_PROGRESS, vor, JETZT, true, EndedBy.STATUS),
            stay(9, BOARD, null, vor, JETZT, true, EndedBy.STATUS),
            offen(10, CardStatus.READY, TAG));

    assertThat(zustand(aufenthalte, CardStatus.READY).comparisonCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("Mehrere Aufenthalte derselben Karte zählen einzeln (AK 7)")
  void mehrereAufenthalteDerselbenKarteZaehlenEinzeln() {
    List<FlussStay> aufenthalte = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      Instant ende = JETZT.minus(Duration.ofDays(10L * i + 1));
      aufenthalte.add(fertig(1, CardStatus.READY, Duration.ofDays(i + 1L), ende));
    }

    Zustand ready = zustand(aufenthalte, CardStatus.READY);

    assertThat(ready.comparisonCount()).isEqualTo(5);
    assertThat(ready.threshold()).isEqualTo(Duration.ofDays(5));
  }

  @Test
  @DisplayName("Schwelle ist der Wert an Rang ⌈0,85 · n⌉ (E10)")
  void schwelleNachRangverfahren() {
    long[] zwanzig = new long[20];
    for (int i = 0; i < zwanzig.length; i++) {
      zwanzig[i] = 20L - i;
    }

    assertThat(zustand(vergleiche(CardStatus.READY, zwanzig), CardStatus.READY).threshold())
        .isEqualTo(Duration.ofDays(17));
    assertThat(
            zustand(vergleiche(CardStatus.READY, 6, 1, 2, 3, 4, 5), CardStatus.READY).threshold())
        .isEqualTo(Duration.ofDays(6));
    assertThat(zustand(vergleiche(CardStatus.READY, 5, 4, 3, 2, 1), CardStatus.READY).threshold())
        .isEqualTo(Duration.ofDays(5));
  }

  @Test
  @DisplayName("Unter fünf Vergleichswerten keine Schwelle und kein Melder (AK 8)")
  void unterFuenfWertenKeineSchwelle() {
    List<FlussStay> aufenthalte = new ArrayList<>(vergleiche(CardStatus.READY, 1, 1, 1, 1));
    aufenthalte.add(offen(1, CardStatus.READY, Duration.ofDays(30)));

    Zustand ready = zustand(aufenthalte, CardStatus.READY);

    assertThat(ready.comparisonCount()).isEqualTo(4);
    assertThat(ready.threshold()).isNull();
    assertThat(ready.cards()).containsExactly(new Karte(1, Duration.ofDays(30), false, false));
  }

  @Test
  @DisplayName("Ein Alter gleich der Schwelle trägt keinen Melder (E10)")
  void gleichstandOhneMelder() {
    List<FlussStay> aufenthalte = new ArrayList<>(vergleiche(CardStatus.READY, 3, 3, 3, 3, 3));
    aufenthalte.add(offen(1, CardStatus.READY, Duration.ofDays(3)));
    aufenthalte.add(offen(2, CardStatus.READY, Duration.ofDays(3).plusSeconds(1)));
    aufenthalte.add(
        stay(3, BOARD, CardStatus.READY, JETZT.minus(Duration.ofDays(3)), null, false, null));

    assertThat(zustand(aufenthalte, CardStatus.READY).cards())
        .containsExactly(
            new Karte(2, Duration.ofDays(3).plusSeconds(1), false, true),
            new Karte(1, Duration.ofDays(3), false, false),
            new Karte(3, Duration.ofDays(3), true, false));
  }

  @Test
  @DisplayName(
      "Das Alter zählt ab dem letzten Eintritt; frühere Aufenthalte zählen nicht (AK 6, E12)")
  void alterAbDemLetztenEintritt() {
    List<FlussStay> aufenthalte =
        List.of(
            stay(
                1,
                BOARD,
                CardStatus.READY,
                JETZT.minus(Duration.ofDays(30)),
                JETZT.minus(Duration.ofDays(20)),
                true,
                EndedBy.STATUS),
            stay(
                1,
                ANDERES_BOARD,
                CardStatus.READY,
                JETZT.minus(Duration.ofDays(20)),
                JETZT.minus(Duration.ofDays(2)),
                true,
                EndedBy.BOARD),
            offen(1, CardStatus.READY, Duration.ofDays(2)));

    assertThat(zustand(aufenthalte, CardStatus.READY).cards())
        .containsExactly(new Karte(1, Duration.ofDays(2), false, false));
  }

  @Test
  @DisplayName("Ready, In Progress und In Review stehen immer da, Backlog und Done nie (AK 13)")
  void dreiZustaendeAuchLeer() {
    List<FlussStay> aufenthalte =
        List.of(
            offen(1, CardStatus.BACKLOG, TAG),
            offen(2, CardStatus.DONE, TAG),
            offen(3, CardStatus.IN_PROGRESS, TAG),
            stay(4, ANDERES_BOARD, CardStatus.READY, JETZT.minus(TAG), null, true, null),
            stay(5, BOARD, null, JETZT.minus(TAG), null, true, null));

    List<Zustand> zustaende = ermittle(aufenthalte);

    assertThat(zustaende)
        .containsExactly(
            new Zustand(CardStatus.READY, 0, null, List.of()),
            new Zustand(CardStatus.IN_PROGRESS, 0, null, List.of(new Karte(3, TAG, false, false))),
            new Zustand(CardStatus.IN_REVIEW, 0, null, List.of()));
  }

  @Test
  @DisplayName("Gleich alte Karten stehen nach Karten-Id")
  void gleichAlteKartenNachKartenId() {
    List<FlussStay> aufenthalte =
        List.of(offen(9, CardStatus.READY, TAG), offen(4, CardStatus.READY, TAG));

    assertThat(zustand(aufenthalte, CardStatus.READY).cards())
        .extracting(Karte::cardId)
        .containsExactly(4L, 9L);
  }
}
