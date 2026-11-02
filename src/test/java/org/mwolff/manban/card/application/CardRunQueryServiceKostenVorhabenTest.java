package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;

/**
 * Unit-Tests des Kostenvorhabens je Kartennummer über die card-Fassade (Issue #2000, Plan #1990 E1;
 * ersetzt die Tests von {@code epicsByCardNumber} aus Issue #936).
 *
 * <p>Eigene Klasse wie {@code CardServiceEpicTreeTest}: {@code CardServiceTest} steht an seinen
 * PMD-Grenzen. Als Unit-Test, weil PIT allein Unit-Tests misst. Die Regel selbst prüft {@code
 * KostenzuordnungTest}; hier geht es um die Fassade: über alle Boards, Zugehörigkeit samt Archiv,
 * nur die gefragten Nummern, die Felder des {@link VorhabenRef}.
 */
class CardRunQueryServiceKostenVorhabenTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long PROJECT = 1L;
  private static final long BOARD_A = 10L;
  private static final long BOARD_B = 11L;

  private CardRepository cards;
  private CardRunQueryService service;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    service =
        new CardRunQueryService(
            cards,
            mock(CardActivityRepository.class),
            // Echte KartenZuordnung aus Port-Mocks (Issue #1051), kein Mock der Zuordnung selbst.
            new KartenZuordnung(
                mock(CardAssigneeRepository.class),
                mock(LabelRepository.class),
                mock(CardLabelRepository.class),
                mock(PermissionChecker.class),
                mock(CardLabelEventRepository.class),
                ActorContext.ActorStamp::unknown,
                Clock.systemUTC(),
                mock(ZuweisungsBenachrichtigung.class)),
            mock(BoardService.class),
            mock(CardLabelEventRepository.class));
  }

  private static Card karte(
      long id,
      long board,
      int number,
      CardType type,
      @Nullable String shortcode,
      @Nullable Long parentId,
      @Nullable Long derivedFrom,
      boolean archived) {
    return new Card(
        id,
        board,
        20L,
        number,
        "Karte " + number,
        null,
        0,
        archived,
        null,
        1L,
        FIXED,
        FIXED,
        type,
        parentId,
        shortcode,
        null,
        PROJECT,
        null,
        derivedFrom,
        null,
        null);
  }

  private static Card vorhaben(long id, long board, int number, String kuerzel) {
    return karte(id, board, number, CardType.EPIC, kuerzel, null, null, false);
  }

  private static Card zugeordnet(long id, long board, int number, long epicId) {
    return karte(id, board, number, CardType.CARD, null, epicId, null, false);
  }

  private static Card abgeleitet(long id, long board, int number, long vorfahrId) {
    return karte(id, board, number, CardType.CARD, null, null, vorfahrId, false);
  }

  private static final VorhabenRef PLANEN = new VorhabenRef(1L, 900, "PLANEN", "Karte 900", false);
  private static final VorhabenRef BACKUP = new VorhabenRef(2L, 901, "BACKUP", "Karte 901", false);

  private Map<Integer, VorhabenRef> zuordnung(Integer... nummern) {
    return service.kostenVorhabenJeKarte(PROJECT, Set.of(nummern));
  }

  @Test
  void eineDirektZugeordneteKarteLiefertIhrVorhaben() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(
            List.of(vorhaben(1L, BOARD_A, 900, "PLANEN"), zugeordnet(10L, BOARD_A, 964, 1L)));

    assertThat(zuordnung(964)).containsExactly(Map.entry(964, PLANEN));
  }

  @Test
  void eineNurUeberDieHerkunftHaengendeKarteLiefertDasselbeVorhaben() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(
            List.of(
                vorhaben(1L, BOARD_A, 900, "PLANEN"),
                zugeordnet(10L, BOARD_A, 963, 1L),
                abgeleitet(11L, BOARD_A, 964, 10L)));

    assertThat(zuordnung(964)).containsExactly(Map.entry(964, PLANEN));
  }

  /** Antwort 7: Eine Karte in zwei Vorhaben zählt genau einmal, beim unmittelbaren. */
  @Test
  void eineKarteInZweiVorhabenLiefertNurDasUnmittelbare() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(
            List.of(
                vorhaben(1L, BOARD_A, 900, "PLANEN"),
                vorhaben(2L, BOARD_A, 901, "BACKUP"),
                zugeordnet(12L, BOARD_A, 970, 2L),
                // ueber parentId in PLANEN, ueber die Herkunft zusaetzlich in BACKUP
                karte(11L, BOARD_A, 964, CardType.CARD, null, 1L, 12L, false)));

    assertThat(zuordnung(964, 970)).containsOnly(Map.entry(964, PLANEN), Map.entry(970, BACKUP));
  }

  /** Archivierte Pakete gehen nicht verloren, archivierte Vorhaben tragen den Zusatz. */
  @Test
  void archivierteKartenUndVorhabenZaehlenMit() {
    Card archiviertesVorhaben = karte(1L, BOARD_A, 900, CardType.EPIC, null, null, null, true);
    when(cards.findByProjectId(PROJECT))
        .thenReturn(
            List.of(
                archiviertesVorhaben,
                karte(10L, BOARD_A, 963, CardType.CARD, null, 1L, null, true),
                karte(11L, BOARD_A, 964, CardType.CARD, null, null, 10L, true)));

    VorhabenRef erwartet = new VorhabenRef(1L, 900, null, "Karte 900", true);
    assertThat(zuordnung(963, 964))
        .containsOnly(Map.entry(963, erwartet), Map.entry(964, erwartet));
  }

  @Test
  void eineNummerOhneKarteOderOhneVorhabenFehltUndWirftNicht() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(
            List.of(
                vorhaben(1L, BOARD_A, 900, "PLANEN"),
                zugeordnet(10L, BOARD_A, 964, 1L),
                karte(11L, BOARD_A, 965, CardType.CARD, null, null, null, false)));

    assertThat(zuordnung(4711, 965, 900)).isEmpty();
  }

  @Test
  void kartenAusMehrerenBoardsDesselbenProjektsErscheinenInEinerAntwort() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(
            List.of(
                vorhaben(1L, BOARD_A, 900, "PLANEN"),
                zugeordnet(10L, BOARD_A, 964, 1L),
                vorhaben(2L, BOARD_B, 901, "BACKUP"),
                zugeordnet(20L, BOARD_B, 824, 2L)));

    assertThat(zuordnung(964, 824)).containsOnly(Map.entry(964, PLANEN), Map.entry(824, BACKUP));
  }

  /** Nur die gefragten Nummern kommen zurueck — nicht die ganze Zuordnung des Projekts. */
  @Test
  void liefertNurDieGefragtenNummern() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(
            List.of(
                vorhaben(1L, BOARD_A, 900, "PLANEN"),
                zugeordnet(10L, BOARD_A, 964, 1L),
                zugeordnet(11L, BOARD_A, 965, 1L)));

    assertThat(zuordnung(965)).containsOnlyKeys(965);
  }

  @Test
  void eineLeereNummernmengeFragtDieDatenbankNicht() {
    assertThat(service.kostenVorhabenJeKarte(PROJECT, Set.of())).isEmpty();

    verifyNoInteractions(cards);
  }
}
