package org.mwolff.manban.push.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Das Abo eines Geräts: Lebensdauer und Board-Schalter (Issue #1997, Plan #1989 E13, E14). */
class PushAboTest {

  private static final Instant JETZT = Instant.parse("2026-10-29T10:00:00Z");
  private static final Instant ENDE = JETZT.plus(Duration.ofDays(30));
  private static final String ENDPUNKT = "https://push.example/abc";

  private static PushAbo laufend() {
    return new PushAbo(
        5L, 42L, ENDPUNKT, "p-alt", "a-alt", JETZT.minus(Duration.ofDays(3)), ENDE, Set.of(7L));
  }

  @Test
  void neu_istFuerAlleBoardsAnUndBeginntJetzt() {
    // When
    PushAbo abo = PushAbo.neu(42L, ENDPUNKT, "p", "a", JETZT, ENDE);

    // Then
    assertThat(abo.id()).isNull();
    assertThat(abo.userId()).isEqualTo(42L);
    assertThat(abo.angelegtAt()).isEqualTo(JETZT);
    assertThat(abo.giltBis()).isEqualTo(ENDE);
    assertThat(abo.boardsAus()).isEmpty();
    assertThat(abo.istAn(7L)).isTrue();
  }

  @Test
  void boardsAus_wirdKopiert() {
    // Given
    Set<Long> aus = new HashSet<>(Set.of(7L));

    // When
    PushAbo abo = new PushAbo(5L, 42L, ENDPUNKT, "p", "a", JETZT, ENDE, aus);
    aus.add(8L);

    // Then
    assertThat(abo.boardsAus()).containsExactly(7L);
  }

  @Test
  void requireId_ohneId_wirft() {
    // Given
    PushAbo abo = PushAbo.neu(42L, ENDPUNKT, "p", "a", JETZT, ENDE);

    // When / Then
    assertThatThrownBy(abo::requireId).isInstanceOf(NullPointerException.class);
    assertThat(laufend().requireId()).isEqualTo(5L);
  }

  /** Grenze: Genau am Sitzungsende ist das Abo abgelaufen, eine Millisekunde davor nicht. */
  @Test
  void istAbgelaufen_abGiltBis() {
    assertThat(laufend().istAbgelaufen(ENDE)).isTrue();
    assertThat(laufend().istAbgelaufen(ENDE.minusMillis(1))).isFalse();
    assertThat(laufend().istAbgelaufen(ENDE.plusMillis(1))).isTrue();
  }

  @Test
  void aufgefrischt_laufend_behaeltBeginnUndSchalter() {
    // When
    PushAbo abo = laufend().aufgefrischt("p-neu", "a-neu", JETZT, ENDE.plus(Duration.ofDays(1)));

    // Then
    assertThat(abo)
        .isEqualTo(
            new PushAbo(
                5L,
                42L,
                ENDPUNKT,
                "p-neu",
                "a-neu",
                JETZT.minus(Duration.ofDays(3)),
                ENDE.plus(Duration.ofDays(1)),
                Set.of(7L)));
  }

  @Test
  void aufgefrischt_abgelaufen_beginntNeuOhneSchalter() {
    // Given
    Instant spaeter = ENDE.plus(Duration.ofDays(2));
    Instant neuesEnde = spaeter.plus(Duration.ofDays(30));

    // When
    PushAbo abo = laufend().aufgefrischt("p-neu", "a-neu", spaeter, neuesEnde);

    // Then
    assertThat(abo)
        .isEqualTo(new PushAbo(5L, 42L, ENDPUNKT, "p-neu", "a-neu", spaeter, neuesEnde, Set.of()));
  }

  @Test
  void neuBegonnen_wechseltKontoUndLeertSchalter() {
    // When
    PushAbo abo = laufend().neuBegonnen(43L, "p-neu", "a-neu", JETZT, ENDE);

    // Then
    assertThat(abo)
        .isEqualTo(new PushAbo(5L, 43L, ENDPUNKT, "p-neu", "a-neu", JETZT, ENDE, Set.of()));
  }

  @Test
  void geschaltet_ausUndWiederAn() {
    // When
    PushAbo aus = laufend().geschaltet(8L, false);
    PushAbo wiederAn = aus.geschaltet(7L, true);

    // Then
    assertThat(aus.boardsAus()).containsExactlyInAnyOrder(7L, 8L);
    assertThat(aus.istAn(8L)).isFalse();
    assertThat(wiederAn.boardsAus()).containsExactly(8L);
    assertThat(wiederAn.istAn(7L)).isTrue();
    assertThat(wiederAn.angelegtAt()).isEqualTo(laufend().angelegtAt());
    assertThat(wiederAn.giltBis()).isEqualTo(ENDE);
  }

  /** Zweimal abschalten bleibt aus, Einschalten eines eingeschalteten Boards ändert nichts. */
  @Test
  void geschaltet_istIdempotent() {
    assertThat(laufend().geschaltet(7L, false).boardsAus()).containsExactly(7L);
    assertThat(laufend().geschaltet(9L, true).boardsAus()).containsExactly(7L);
  }
}
