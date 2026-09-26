package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.mwolff.manban.project.application.ProjectService;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit-Tests der Vorhaben-Zuordnung zu Kartennummern über die card-Fassade (Issue #936).
 *
 * <p>Eigene Klasse wie {@code CardServiceEpicTreeTest}: {@code CardServiceTest} steht an seinen
 * PMD-Grenzen. Als Unit-Test, weil PIT allein Unit-Tests misst.
 */
class CardServiceEpicsByCardNumberTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long PROJECT = 1L;
  private static final long BOARD_A = 10L;
  private static final long BOARD_B = 11L;

  private CardRepository cards;
  private CardService service;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    ActorContext actor = mock(ActorContext.class);
    when(actor.current()).thenReturn(ActorContext.ActorStamp.unknown());
    service =
        new CardService(
            cards,
            mock(CardDependencyRepository.class),
            mock(BoardService.class),
            mock(PermissionChecker.class),
            mock(ProjectService.class),
            mock(CardColumnTransitionRepository.class),
            // Echte KartenZuordnung aus Port-Mocks (Issue #1051), kein Mock der Zuordnung selbst.
            new KartenZuordnung(
                mock(CardAssigneeRepository.class),
                mock(LabelRepository.class),
                mock(CardLabelRepository.class),
                mock(PermissionChecker.class)),
            mock(CardActivityRepository.class),
            actor,
            mock(ApplicationEventPublisher.class),
            Clock.fixed(FIXED, ZoneOffset.UTC));
  }

  private static Card karte(
      long id,
      long board,
      int number,
      CardType type,
      @Nullable String shortcode,
      @Nullable Long parentId,
      @Nullable Long derivedFrom) {
    return new Card(
        id,
        board,
        20L,
        number,
        "Karte " + number,
        null,
        0,
        false,
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
        null);
  }

  private static Card vorhaben(long id, long board, int number, String kuerzel) {
    return karte(id, board, number, CardType.EPIC, kuerzel, null, null);
  }

  private static Card zugeordnet(long id, long board, int number, long epicId) {
    return karte(id, board, number, CardType.CARD, null, epicId, null);
  }

  private static Card abgeleitet(long id, long board, int number, long vorfahrId) {
    return karte(id, board, number, CardType.CARD, null, null, vorfahrId);
  }

  private static Card frei(long id, long board, int number) {
    return karte(id, board, number, CardType.CARD, null, null, null);
  }

  private static final EpicRef PLANEN = new EpicRef(1L, "PLANEN", "Karte 900");
  private static final EpicRef BACKUP = new EpicRef(2L, "BACKUP", "Karte 901");

  private Map<Integer, Set<EpicRef>> zuordnung(Integer... nummern) {
    return service.epicsByCardNumber(PROJECT, Set.of(nummern));
  }

  @Test
  void eineDirektZugeordneteKarteLiefertIhrVorhaben() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(
            List.of(vorhaben(1L, BOARD_A, 900, "PLANEN"), zugeordnet(10L, BOARD_A, 964, 1L)));

    assertThat(zuordnung(964)).containsExactly(Map.entry(964, Set.of(PLANEN)));
  }

  @Test
  void eineNurUeberDieHerkunftHaengendeKarteLiefertDasselbeVorhaben() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(
            List.of(
                vorhaben(1L, BOARD_A, 900, "PLANEN"),
                zugeordnet(10L, BOARD_A, 963, 1L),
                abgeleitet(11L, BOARD_A, 964, 10L)));

    assertThat(zuordnung(964)).containsExactly(Map.entry(964, Set.of(PLANEN)));
  }

  @Test
  void eineKarteInZweiVorhabenLiefertBeide() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(
            List.of(
                vorhaben(1L, BOARD_A, 900, "PLANEN"),
                vorhaben(2L, BOARD_A, 901, "BACKUP"),
                zugeordnet(10L, BOARD_A, 963, 1L),
                zugeordnet(12L, BOARD_A, 970, 2L),
                // ueber parentId in PLANEN, ueber die Herkunft zusaetzlich in BACKUP
                karte(11L, BOARD_A, 964, CardType.CARD, null, 1L, 12L)));

    assertThat(zuordnung(964)).containsExactly(Map.entry(964, Set.of(PLANEN, BACKUP)));
  }

  @Test
  void eineNummerOhneKarteLiefertKeinVorhabenUndWirftNicht() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(
            List.of(vorhaben(1L, BOARD_A, 900, "PLANEN"), zugeordnet(10L, BOARD_A, 964, 1L)));

    assertThat(zuordnung(4711)).isEmpty();
  }

  @Test
  void eineKarteOhneVorhabenLiefertKeinVorhabenUndWirftNicht() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(
            List.of(
                vorhaben(1L, BOARD_A, 900, "PLANEN"),
                zugeordnet(10L, BOARD_A, 964, 1L),
                frei(11L, BOARD_A, 965)));

    assertThat(zuordnung(965)).isEmpty();
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

    assertThat(zuordnung(964, 824))
        .containsOnly(Map.entry(964, Set.of(PLANEN)), Map.entry(824, Set.of(BACKUP)));
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
    assertThat(service.epicsByCardNumber(PROJECT, Set.of())).isEmpty();

    verifyNoInteractions(cards);
  }
}
