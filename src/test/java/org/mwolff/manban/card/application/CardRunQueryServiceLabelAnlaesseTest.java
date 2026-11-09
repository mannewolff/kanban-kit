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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardBestandRepository.KarteImBestand;
import org.mwolff.manban.card.application.CardRunQueryService.KartenStandView;
import org.mwolff.manban.card.application.CardRunQueryService.LabelAnlassView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.Label;
import org.mwolff.manban.card.domain.LabelEreignisZeile;
import org.mwolff.manban.project.application.PermissionChecker;

/**
 * Unit-Tests der Lesezugänge für die Karten-Anlässe der Benachrichtigung (Issue #2075, Plan #2070
 * E3, E4, E8).
 *
 * <p>Der Port ist nur für die gefragten Label-Namen und den gefragten Zeitpunkt vorbelegt: Reicht
 * die Fassade etwas anderes weiter, kommt nichts zurück. Die Filterung selbst belegt {@code
 * CardLabelEventRepositoryIT} gegen Postgres. Als Unit-Test, weil PIT allein Unit-Tests misst.
 */
class CardRunQueryServiceLabelAnlaesseTest {

  private static final Instant FIXED = Instant.parse("2026-11-09T12:00:00Z");
  private static final Instant SEIT = Instant.parse("2026-11-09T10:00:00Z");
  private static final Instant LAUF = Instant.parse("2026-11-09T09:00:00Z");
  private static final Set<String> FRAGE = Set.of("kit:klaeren", "kit:clarify");
  private static final long PROJECT = 1L;
  private static final long BOARD = 2L;

  private CardLabelEventRepository labelEvents;
  private CardBestandRepository bestand;
  private LabelRepository labels;
  private CardLabelRepository cardLabels;
  private CardRunQueryService service;

  @BeforeEach
  void setUp() {
    labelEvents = mock(CardLabelEventRepository.class);
    bestand = mock(CardBestandRepository.class);
    labels = mock(LabelRepository.class);
    cardLabels = mock(CardLabelRepository.class);
    service =
        new CardRunQueryService(
            mock(CardRepository.class),
            mock(CardActivityRepository.class),
            new KartenZuordnung(
                mock(CardAssigneeRepository.class),
                labels,
                cardLabels,
                mock(PermissionChecker.class),
                mock(CardLabelEventRepository.class),
                ActorContext.ActorStamp::unknown,
                Clock.systemUTC(),
                mock(ZuweisungsBenachrichtigung.class)),
            mock(BoardService.class),
            labelEvents,
            bestand);
  }

  private static Card karte(long id, boolean archiviert) {
    return new Card(
        id,
        BOARD,
        10L,
        (int) id + 100,
        "Karte " + id,
        null,
        0,
        archiviert,
        null,
        null,
        FIXED,
        FIXED,
        CardType.CARD,
        null,
        null,
        null,
        PROJECT,
        null,
        null,
        null,
        null);
  }

  private static KarteImBestand imBestand(long id, boolean archiviert, boolean imPapierkorb) {
    return new KarteImBestand(karte(id, archiviert), imPapierkorb, null);
  }

  @Test
  void labelAnlaesseLiefertZeilenIdUndLaufkennung() {
    when(labelEvents.findNachLabelNamenSeit(FRAGE, SEIT))
        .thenReturn(List.of(new LabelEreignisZeile(77L, 3L, "kit:clarify", SEIT, LAUF)));

    assertThat(service.labelAnlaesse(FRAGE, SEIT))
        .containsExactly(new LabelAnlassView(77L, 3L, "kit:clarify", SEIT, LAUF));
  }

  @Test
  void labelAnlaesseLiefertEreignisseOhneLaufkennung() {
    when(labelEvents.findNachLabelNamenSeit(FRAGE, SEIT))
        .thenReturn(List.of(new LabelEreignisZeile(78L, 4L, "kit:klaeren", SEIT, null)));

    assertThat(service.labelAnlaesse(FRAGE, SEIT))
        .containsExactly(new LabelAnlassView(78L, 4L, "kit:klaeren", SEIT, null));
  }

  @Test
  void labelAnlaesseFragtNurDieGenanntenNamenAbDemZeitpunkt() {
    when(labelEvents.findNachLabelNamenSeit(FRAGE, SEIT))
        .thenReturn(List.of(new LabelEreignisZeile(79L, 5L, "kit:clarify", SEIT, null)));

    assertThat(service.labelAnlaesse(Set.of("run:waiting"), SEIT)).isEmpty();
    assertThat(service.labelAnlaesse(FRAGE, SEIT.plusSeconds(1))).isEmpty();
  }

  @Test
  void kartenStandNenntLabelsUndKennzeichnetArchivUndPapierkorb() {
    when(bestand.findImBestand(Set.of(1L, 2L, 3L)))
        .thenReturn(
            List.of(
                imBestand(1L, false, false),
                imBestand(2L, true, false),
                imBestand(3L, false, true)));
    when(cardLabels.findByCardIds(List.of(1L, 2L, 3L)))
        .thenReturn(Map.of(1L, List.of(21L, 20L), 3L, List.of(20L)));
    when(labels.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                new Label(20L, BOARD, "kit:clarify", "#000", false),
                new Label(21L, BOARD, "bug", "#000", false)));

    assertThat(service.kartenStand(List.of(1L, 2L, 3L)))
        .containsExactly(
            new KartenStandView(
                1L, PROJECT, 101, "Karte 1", List.of("bug", "kit:clarify"), false, false),
            new KartenStandView(2L, PROJECT, 102, "Karte 2", List.of(), true, false),
            new KartenStandView(3L, PROJECT, 103, "Karte 3", List.of("kit:clarify"), false, true));
  }

  @Test
  void kartenStandLaesstUnbekannteIdsWeg() {
    when(bestand.findImBestand(Set.of(1L, 99L))).thenReturn(List.of(imBestand(1L, false, false)));
    when(cardLabels.findByCardIds(List.of(1L))).thenReturn(Map.of());

    assertThat(service.kartenStand(List.of(99L, 1L)))
        .containsExactly(new KartenStandView(1L, PROJECT, 101, "Karte 1", List.of(), false, false));
  }

  @Test
  void kartenStandFolgtDerReihenfolgeDerEingabe() {
    when(bestand.findImBestand(Set.of(1L, 2L)))
        .thenReturn(List.of(imBestand(1L, false, false), imBestand(2L, false, false)));
    when(cardLabels.findByCardIds(List.of(2L, 1L))).thenReturn(Map.of());

    assertThat(service.kartenStand(List.of(2L, 1L)))
        .extracting(KartenStandView::id)
        .containsExactly(2L, 1L);
  }

  @Test
  void kartenStandOhneIdsFragtKeinenPort() {
    assertThat(service.kartenStand(List.of())).isEmpty();

    verifyNoInteractions(bestand, cardLabels, labels);
  }
}
