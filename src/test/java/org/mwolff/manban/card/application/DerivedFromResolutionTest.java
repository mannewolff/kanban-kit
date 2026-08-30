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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;

/**
 * Die drei Ablehnungen der Herkunfts-Auflösung und die Tiefenbegrenzung.
 *
 * <p>Selbstverweis und Zyklus sind über die API <strong>nicht</strong> herstellbar: Die Herkunft
 * wird nur beim Anlegen gesetzt, und ein Aufrufer kennt die Nummer der neuen Karte vorher nicht.
 * Die Prüfungen schützen gegen Bestandskorruption (per SQL, aus einer Migration oder aus einem
 * späteren Änderungspfad). Der korrupte Zustand wird hier deshalb über das Repository aufgebaut.
 */
class DerivedFromResolutionTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final long PROJEKT = 1L;

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

  private Card karte(long id, int nummer, Long herkunft) {
    Card c =
        new Card(
            id,
            10L,
            20L,
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
            CardType.CARD,
            null,
            null,
            null,
            PROJEKT,
            null,
            null,
            herkunft,
            null);
    nachId.put(id, c);
    nachNummer.put(nummer, c);
    return c;
  }

  @Test
  void unbekannteNummer_wirdAbgelehnt() {
    assertThatExceptionOfType(InvalidDependencyException.class)
        .isThrownBy(() -> DerivedFrom.resolve(cards, PROJEKT, 999, null))
        .withMessageContaining("999");
  }

  @Test
  void selbstverweis_wirdAbgelehnt() {
    karte(1L, 1, null);

    assertThatExceptionOfType(InvalidDependencyException.class)
        .isThrownBy(() -> DerivedFrom.resolve(cards, PROJEKT, 1, 1L));
  }

  @Test
  void zyklus_ueberMehrereKarten_wirdAbgelehnt() {
    // A -> B -> C, und die zu prüfende Karte ist C: der Lauf von A aufwärts trifft C.
    karte(3L, 3, null);
    karte(2L, 2, 3L);
    karte(1L, 1, 2L);

    assertThatExceptionOfType(InvalidDependencyException.class)
        .isThrownBy(() -> DerivedFrom.resolve(cards, PROJEKT, 1, 3L));
  }

  @Test
  void bestandsZyklus_ohneDieGepruefteKarte_laeuftNichtEndlos() {
    // A -> B -> A, per SQL herbeigeführt. Die neue Karte zeigt auf A und ist selbst nicht Teil des
    // Zyklus — keine der beiden anderen Ablehnungen greift, nur die Tiefenbegrenzung.
    karte(1L, 1, 2L);
    karte(2L, 2, 1L);

    assertThatExceptionOfType(InvalidDependencyException.class)
        .isThrownBy(() -> DerivedFrom.resolve(cards, PROJEKT, 1, null));
  }

  @Test
  void ketteAnDerGrenze_wirdAbgelehnt() {
    // 101 Glieder: eine Kette dieser Tiefe ist pathologisch und wird abgelehnt.
    for (int i = 1; i <= 101; i++) {
      karte(i, i, i == 101 ? null : (long) (i + 1));
    }

    assertThatExceptionOfType(InvalidDependencyException.class)
        .isThrownBy(() -> DerivedFrom.resolve(cards, PROJEKT, 1, null));
  }

  @Test
  void ketteKnappUnterDerGrenze_wirdAufgeloest() {
    // 99 Schritte oberhalb des Vorfahren: knapp unter der Grenze, wird angenommen. Zusammen mit
    // dem Test darueber ist der Grenzwert von beiden Seiten gepinnt — PIT kann ihn nicht
    // wegmutieren, ohne dass einer der beiden Tests rot wird.
    for (int i = 1; i <= 100; i++) {
      karte(i, i, i == 100 ? null : (long) (i + 1));
    }

    assertThat(DerivedFrom.resolve(cards, PROJEKT, 1, null)).isEqualTo(1L);
  }

  @Test
  void kurzeKette_wirdAufgeloest() {
    karte(2L, 2, null);
    karte(1L, 1, 2L);

    assertThat(DerivedFrom.resolve(cards, PROJEKT, 1, null)).isEqualTo(1L);
  }

  @Test
  void verwaisterVerweisInDerKette_bricht_denLaufAb() {
    // Der Vorfahr zeigt auf eine Karte, die es nicht mehr gibt. Regulaer raeumt der
    // Fremdschluessel (ON DELETE SET NULL) das auf; die Kette muss den Zustand trotzdem
    // aushalten, statt am fehlenden Glied zu scheitern.
    karte(1L, 1, 999L);

    assertThat(DerivedFrom.resolve(cards, PROJEKT, 1, null)).isEqualTo(1L);
  }

  @Test
  void gesetzteEigeneKarte_ohneZyklus_wirdAufgeloest() {
    // Der Gegenpol zu den beiden Ablehnungen: selfCardId ist gesetzt, der Vorfahr ist ein anderer,
    // und die Kette trifft die eigene Karte nicht. Ohne diesen Fall erwarten ALLE Tests mit
    // gesetzter selfCardId eine Ablehnung — eine Mutation, die die Gleichheitspruefungen auf
    // "immer gleich" setzt, wuerfe dann ebenfalls und ueberlebte.
    karte(2L, 2, null);
    karte(1L, 1, 2L);
    karte(3L, 3, null);

    assertThat(DerivedFrom.resolve(cards, PROJEKT, 1, 3L)).isEqualTo(1L);
  }

  @Test
  void ohneAngabe_bleibtNull() {
    assertThat(DerivedFrom.resolve(cards, PROJEKT, null, null)).isNull();
  }
}
