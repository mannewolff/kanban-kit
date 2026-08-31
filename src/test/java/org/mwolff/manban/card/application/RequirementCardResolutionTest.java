package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die vier Ablehnungen der Anforderungs-Auflösung (Issue #639) und der gültige Fall.
 *
 * <p>Anders als bei der Herkunft ({@link DerivedFromResolutionTest}) tippt diese Nummer ein Mensch
 * in die Oberfläche — der Ingest-Fall aus #566, der unbekannte Nummern bewusst durchlässt, gilt
 * hier nicht. Jeder Ablehnungstest prüft <b>zusätzlich den Statuscode 400</b>: Die Exception trägt
 * ihn als Annotation, und genau darauf verlässt sich das Frontend beim Zuordnen der Meldung zum
 * Feld.
 *
 * <p>Jeder Fall hält beide Seiten fest — die Ablehnung <em>und</em> dass derselbe Aufbau ohne den
 * einen verletzten Punkt durchgeht. Eine Fassung, die pauschal ablehnt, muss rot werden.
 */
class RequirementCardResolutionTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final long PROJEKT = 1L;
  private static final long BOARD = 10L;

  private final Map<Long, Card> nachId = new HashMap<>();
  private final Map<Integer, Card> nachNummer = new HashMap<>();
  private CardRepository cards;

  @BeforeEach
  void setUp() {
    cards = org.mockito.Mockito.mock(CardRepository.class);
    when(cards.findById(anyLong()))
        .thenAnswer(i -> Optional.ofNullable(nachId.get(i.getArgument(0, Long.class))));
    when(cards.findByProjectIdAndNumber(anyLong(), anyInt()))
        .thenAnswer(i -> Optional.ofNullable(nachNummer.get(i.getArgument(1, Integer.class))));
  }

  private Card karte(long id, int nummer, CardType typ, @Nullable Long boardId) {
    Card c =
        new Card(
            id,
            boardId,
            boardId == null ? null : 20L,
            nummer,
            "T",
            null,
            nummer,
            false,
            false,
            null,
            null,
            NOW,
            NOW,
            typ,
            null,
            null,
            null,
            PROJEKT,
            null,
            null,
            null,
            null);
    nachId.put(id, c);
    nachNummer.put(nummer, c);
    return c;
  }

  /** Der Statuscode, den die Exception per Annotation ans Web trägt. */
  private static HttpStatus statusVon(Throwable fehler) {
    ResponseStatus annotation = fehler.getClass().getAnnotation(ResponseStatus.class);
    assertThat(annotation).as("Exception traegt @ResponseStatus").isNotNull();
    return annotation.value();
  }

  @Test
  void gueltigerFall_liefertDieIdDerAnforderung() {
    Card vorhaben = karte(1L, 1, CardType.EPIC, BOARD);
    Card anforderung = karte(2L, 2, CardType.CARD, BOARD);

    assertThat(RequirementCard.resolve(cards, vorhaben, 2)).isEqualTo(anforderung.requireId());
  }

  @Test
  void null_loeschtDieZuordnung() {
    Card vorhaben = karte(1L, 1, CardType.EPIC, BOARD);

    assertThat(RequirementCard.resolve(cards, vorhaben, null)).isNull();
  }

  @Test
  void unbekannteNummer_wirdMit400Abgelehnt() {
    Card vorhaben = karte(1L, 1, CardType.EPIC, BOARD);

    InvalidRequirementCardException fehler =
        assertThatExceptionOfType(InvalidRequirementCardException.class)
            .isThrownBy(() -> RequirementCard.resolve(cards, vorhaben, 99))
            .actual();

    assertThat(fehler).hasMessageContaining("99");
    assertThat(statusVon(fehler)).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void zielIstKeinVorhaben_wirdMit400Abgelehnt() {
    Card gewoehnlich = karte(1L, 1, CardType.CARD, BOARD);
    karte(2L, 2, CardType.CARD, BOARD);

    InvalidRequirementCardException fehler =
        assertThatExceptionOfType(InvalidRequirementCardException.class)
            .isThrownBy(() -> RequirementCard.resolve(cards, gewoehnlich, 2))
            .actual();

    assertThat(statusVon(fehler)).isEqualTo(HttpStatus.BAD_REQUEST);

    // Gegenprobe: Derselbe Aufbau mit einem Vorhaben als Ziel geht durch. Ohne sie bliebe eine
    // Fassung gruen, die jede Zuordnung ablehnt.
    Card vorhaben = karte(3L, 3, CardType.EPIC, BOARD);
    assertThat(RequirementCard.resolve(cards, vorhaben, 2)).isEqualTo(2L);
  }

  @Test
  void anforderungAufFremdemBoard_wirdMit400Abgelehnt() {
    Card vorhaben = karte(1L, 1, CardType.EPIC, BOARD);
    karte(2L, 2, CardType.CARD, 99L);

    InvalidRequirementCardException fehler =
        assertThatExceptionOfType(InvalidRequirementCardException.class)
            .isThrownBy(() -> RequirementCard.resolve(cards, vorhaben, 2))
            .actual();

    assertThat(statusVon(fehler)).isEqualTo(HttpStatus.BAD_REQUEST);

    // Gegenprobe: dieselbe Karte auf dem Board des Vorhabens wird angenommen.
    karte(3L, 3, CardType.CARD, BOARD);
    assertThat(RequirementCard.resolve(cards, vorhaben, 3)).isEqualTo(3L);
  }

  @Test
  void selbstbezug_wirdMit400Abgelehnt() {
    Card vorhaben = karte(1L, 1, CardType.EPIC, BOARD);

    InvalidRequirementCardException fehler =
        assertThatExceptionOfType(InvalidRequirementCardException.class)
            .isThrownBy(() -> RequirementCard.resolve(cards, vorhaben, 1))
            .actual();

    assertThat(statusVon(fehler)).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void derFeldnameTraegtDieMeldungAnsRichtigeFeld() {
    // Das Frontend ordnet die Meldung ueber diesen Namen zu (#641). Ein anderer Name zeigte den
    // Fehler an einem fremden Eingabefeld an.
    Card vorhaben = karte(1L, 1, CardType.EPIC, BOARD);

    InvalidRequirementCardException fehler =
        assertThatExceptionOfType(InvalidRequirementCardException.class)
            .isThrownBy(() -> RequirementCard.resolve(cards, vorhaben, 1))
            .actual();

    assertThat(fehler.field()).isEqualTo("requirementCardNumber");
  }
}
