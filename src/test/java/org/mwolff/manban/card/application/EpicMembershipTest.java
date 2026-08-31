package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;

/**
 * Unit-Tests der Vorhaben-Zugehörigkeit (Issue #632). Bewusst ohne Datenbank und ohne Ports: Der
 * Abstieg ist reine Rechnung, und PIT misst nur Unit-Tests — eine ausschliesslich per
 * Integrationstest belegte Ableitung hinterliesse trotz gruener Suite eine Mutationsluecke (Befund
 * aus Issue #605).
 *
 * <p>Jede Zusicherung haelt <b>beide</b> Seiten fest: Was zur Menge gehoert und was nicht. Eine
 * Fassung, die pauschal alle Karten oder pauschal keine liefert, muss rot werden.
 */
class EpicMembershipTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;

  private static Card vorhaben(long id, int number) {
    return karte(id, number, CardType.EPIC, null, null, false, false);
  }

  /**
   * Karte mit optionaler Vorhaben-Zuordnung ({@code parentId}) und Herkunft ({@code derivedFrom}).
   */
  private static Card karte(
      long id, int number, @Nullable Long parentId, @Nullable Long derivedFrom) {
    return karte(id, number, CardType.CARD, parentId, derivedFrom, false, false);
  }

  private static Card karte(
      long id,
      int number,
      CardType type,
      @Nullable Long parentId,
      @Nullable Long derivedFrom,
      boolean archived,
      boolean ideaStored) {
    return new Card(
        id,
        BOARD,
        100L,
        number,
        "Karte " + number,
        null,
        0,
        archived,
        ideaStored,
        null,
        null,
        FIXED,
        FIXED,
        type,
        parentId,
        null,
        null,
        PROJECT,
        null,
        null,
        derivedFrom,
        null);
  }

  private static Set<Integer> nummern(Map<Long, Set<Card>> ergebnis, long epicId) {
    return ergebnis.get(epicId).stream()
        .map(Card::requireNumber)
        .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
  }

  @Test
  void vorhabenOhneZuordnungLiefertLeereMengeUndBleibtAlsSchluesselErhalten() {
    Card epic = vorhaben(1L, 1);
    Card fremde = karte(2L, 2, null, null);

    Map<Long, Set<Card>> ergebnis = EpicMembership.compute(List.of(epic, fremde));

    // Beide Seiten: Das Vorhaben ist Schluessel, die gewoehnliche Karte ist es nicht. Ohne die
    // zweite Zusicherung bliebe eine Fassung gruen, die jeder Karte einen Schluessel gibt.
    assertThat(ergebnis).containsOnlyKeys(1L);
    assertThat(ergebnis.get(1L)).isEmpty();
  }

  @Test
  void wurzelOhneNachfahrenLiefertGenauDieWurzel() {
    Card epic = vorhaben(1L, 1);
    Card wurzel = karte(2L, 2, 1L, null);
    Card unbeteiligt = karte(3L, 3, null, null);

    Map<Long, Set<Card>> ergebnis = EpicMembership.compute(List.of(epic, wurzel, unbeteiligt));

    assertThat(nummern(ergebnis, 1L)).containsExactly(2);
    assertThat(nummern(ergebnis, 1L)).doesNotContain(3);
  }

  @Test
  void ketteUeberDreiEbenenHinausWirdVollstaendigEingesammelt() {
    Card epic = vorhaben(1L, 1);
    Card wurzel = karte(2L, 2, 1L, null);
    Card kind = karte(3L, 3, null, 2L);
    Card enkel = karte(4L, 4, null, 3L);
    Card urenkel = karte(5L, 5, null, 4L);
    Card fremdeKette = karte(6L, 6, null, null);

    Map<Long, Set<Card>> ergebnis =
        EpicMembership.compute(List.of(epic, wurzel, kind, enkel, urenkel, fremdeKette));

    assertThat(nummern(ergebnis, 1L)).containsExactly(2, 3, 4, 5);
    assertThat(nummern(ergebnis, 1L)).doesNotContain(6);
  }

  @Test
  void archivierterZwischenknotenZaehltNichtAberSeineNachfahrenSchon() {
    Card epic = vorhaben(1L, 1);
    Card wurzel = karte(2L, 2, 1L, null);
    Card archiviert = karte(3L, 3, CardType.CARD, null, 2L, true, false);
    Card kindDesArchivierten = karte(4L, 4, null, 3L);

    Map<Long, Set<Card>> ergebnis =
        EpicMembership.compute(List.of(epic, wurzel, archiviert, kindDesArchivierten));

    assertThat(nummern(ergebnis, 1L)).containsExactly(2, 4);
    assertThat(nummern(ergebnis, 1L)).doesNotContain(3);
  }

  @Test
  void archivierteWurzelZaehltNichtAberIhreNachfahrenSchon() {
    Card epic = vorhaben(1L, 1);
    Card archivierteWurzel = karte(2L, 2, CardType.CARD, 1L, null, true, false);
    Card kind = karte(3L, 3, null, 2L);

    Map<Long, Set<Card>> ergebnis = EpicMembership.compute(List.of(epic, archivierteWurzel, kind));

    assertThat(nummern(ergebnis, 1L)).containsExactly(3);
    assertThat(nummern(ergebnis, 1L)).doesNotContain(2);
  }

  @Test
  void ideaStoredZaehltNichtAberSeineNachfahrenZaehlenWeiter() {
    Card epic = vorhaben(1L, 1);
    Card wurzel = karte(2L, 2, 1L, null);
    Card idee = karte(3L, 3, CardType.CARD, null, 2L, false, true);
    Card kindDerIdee = karte(4L, 4, null, 3L);

    Map<Long, Set<Card>> ergebnis =
        EpicMembership.compute(List.of(epic, wurzel, idee, kindDerIdee));

    assertThat(nummern(ergebnis, 1L)).containsExactly(2, 4);
    assertThat(nummern(ergebnis, 1L)).doesNotContain(3);
  }

  /**
   * Entscheidend ist die Zuordnung <em>im</em> Ring: Die Wurzeln kommen hier aus {@code parentId}
   * und nicht wie bei {@link DerivationTree} aus der Herkunftslosigkeit. Ein freistehender Ring
   * wuerde nie betreten — der Test waere dann auch ohne Besuchtenmenge gruen (Issue-Review #632).
   */
  @Test
  @Timeout(5)
  void ringWirdBetretenTerminiertUndLiefertJedesMitgliedGenauEinmal() {
    Card epic = vorhaben(1L, 1);
    Card imRing = karte(2L, 2, 1L, 4L);
    Card ring2 = karte(3L, 3, null, 2L);
    Card ring3 = karte(4L, 4, null, 3L);
    Card ausserhalb = karte(5L, 5, null, null);

    Map<Long, Set<Card>> ergebnis =
        EpicMembership.compute(List.of(epic, imRing, ring2, ring3, ausserhalb));

    assertThat(ergebnis.get(1L)).hasSize(3);
    assertThat(nummern(ergebnis, 1L)).containsExactly(2, 3, 4);
    assertThat(nummern(ergebnis, 1L)).doesNotContain(5);
  }

  @Test
  void karteAusZweiVorhabenErreichbarZaehltZuBeiden() {
    Card epicA = vorhaben(1L, 1);
    Card epicB = vorhaben(2L, 2);
    Card wurzelA = karte(3L, 3, 1L, null);
    Card wurzelB = karte(4L, 4, 2L, null);
    Card gemeinsam = karte(5L, 5, null, 3L);
    Card nurB = karte(6L, 6, null, 4L);

    Map<Long, Set<Card>> ergebnis =
        EpicMembership.compute(List.of(epicA, epicB, wurzelA, wurzelB, gemeinsam, nurB));

    assertThat(nummern(ergebnis, 1L)).containsExactly(3, 5);
    assertThat(nummern(ergebnis, 2L)).containsExactly(4, 6);
    assertThat(nummern(ergebnis, 1L)).doesNotContain(4, 6);
    assertThat(nummern(ergebnis, 2L)).doesNotContain(3, 5);
  }

  @Test
  void direktZugeordnetUndUeberDieKetteErreichbarZaehltGenauEinmal() {
    Card epic = vorhaben(1L, 1);
    Card wurzel = karte(2L, 2, 1L, null);
    Card doppelt = karte(3L, 3, 1L, 2L);

    Map<Long, Set<Card>> ergebnis = EpicMembership.compute(List.of(epic, wurzel, doppelt));

    assertThat(ergebnis.get(1L)).hasSize(2);
    assertThat(nummern(ergebnis, 1L)).containsExactly(2, 3);
  }

  @Test
  void vorhabenInDerKetteZaehltNichtMitSeineNachfahrenSchon() {
    Card epic = vorhaben(1L, 1);
    Card wurzel = karte(2L, 2, 1L, null);
    Card fremdesVorhaben = karte(3L, 3, CardType.EPIC, null, 2L, false, false);
    Card kindDesVorhabens = karte(4L, 4, null, 3L);

    Map<Long, Set<Card>> ergebnis =
        EpicMembership.compute(List.of(epic, wurzel, fremdesVorhaben, kindDesVorhabens));

    assertThat(nummern(ergebnis, 1L)).containsExactly(2, 4);
    assertThat(nummern(ergebnis, 1L)).doesNotContain(3);
  }
}
