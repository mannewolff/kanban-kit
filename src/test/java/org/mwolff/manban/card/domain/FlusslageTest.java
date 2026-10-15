package org.mwolff.manban.card.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Wann eine Karte im Fluss ist, mit welchem Zustand und auf welchem Board (Plan #1765, E3, E5). */
class FlusslageTest {

  private static final Instant FIXED = Instant.parse("2026-10-01T00:00:00Z");
  private static final long BOARD = 7L;

  private static Card karte(
      String titel, CardType type, boolean archived, @Nullable CardStatus status) {
    return new Card(
        1L, BOARD, 3L, 1, titel, null, 0, archived, null, null, FIXED, FIXED, type, null, null,
        null, 11L, null, null, null, status);
  }

  @ParameterizedTest
  @EnumSource(CardStatus.class)
  void einArbeitspaketMitZustandAufDemBoardIstImFluss(CardStatus status) {
    assertThat(Flusslage.von(karte("[Task] Paket", CardType.CARD, false, status), false))
        .contains(new Flusslage(BOARD, status));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"[Plan] Weg", "[Requirement] Story", "[Idea] roh", "[Fachlich] Story", "Hand"})
  void eineKarteOhneZustandIstNieImFluss(String titel) {
    assertThat(Flusslage.von(karte(titel, CardType.CARD, false, null), false)).isEmpty();
  }

  @Test
  void einVorhabenIstNieImFluss() {
    assertThat(Flusslage.von(karte("Vorhaben", CardType.EPIC, false, null), false)).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(CardStatus.class)
  void imPapierkorbIstKeineKarteImFlussAuchKeineFertige(CardStatus status) {
    assertThat(Flusslage.von(karte("[Task] Paket", CardType.CARD, false, status), true)).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(value = CardStatus.class, names = "DONE", mode = EnumSource.Mode.EXCLUDE)
  void eineArchivierteNichtFertigeKarteIstNichtImFluss(CardStatus status) {
    assertThat(Flusslage.von(karte("[Task] Paket", CardType.CARD, true, status), false)).isEmpty();
  }

  @Test
  void eineArchivierteFertigeKarteBleibtImFluss() {
    assertThat(Flusslage.von(karte("[Task] Paket", CardType.CARD, true, CardStatus.DONE), false))
        .contains(new Flusslage(BOARD, CardStatus.DONE));
  }

  @Test
  void eineArchivierteFertigeKarteImPapierkorbIstNichtImFluss() {
    assertThat(Flusslage.von(karte("[Task] Paket", CardType.CARD, true, CardStatus.DONE), true))
        .isEmpty();
  }
}
