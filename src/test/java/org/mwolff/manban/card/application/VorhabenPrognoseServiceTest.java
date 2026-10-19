package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.EpicForecastView.Epic;
import org.mwolff.manban.card.application.EpicForecastView.PendingDocument;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.FlussStay;
import org.mwolff.manban.card.domain.FlussStay.EndedBy;
import org.mwolff.manban.card.domain.VorhabenLage.Lage;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectNotFoundException;

/** Dienst der Prognose der Platte „Vorhaben“ (Issue #1826; Ports gemockt, feste Uhr). */
class VorhabenPrognoseServiceTest {

  /** 10:00 in Berlin — vor der Tagesgrenze um 12 Uhr, der laufende Tag ist der 2026-10-15. */
  private static final Instant NOW = Instant.parse("2026-10-15T08:00:00Z");

  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

  /** 22:00 am selben Kalendertag — nach der Tagesgrenze, der laufende Tag ist der 2026-10-16. */
  private static final ZoneId KIRITIMATI = ZoneId.of("Pacific/Kiritimati");

  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;
  private static final long USER = 3L;
  private static final long OFFEN = 5L;
  private static final long DONE = 6L;

  private CardFlowStayRepository aufenthalte;
  private CardRepository cards;
  private BoardService boardService;
  private PermissionChecker permissions;
  private VorhabenPrognoseService service;

  private final List<Card> karten = new ArrayList<>();
  private final List<FlussStay> verlauf = new ArrayList<>();

  @BeforeEach
  void setUp() {
    aufenthalte = mock(CardFlowStayRepository.class);
    cards = mock(CardRepository.class);
    boardService = mock(BoardService.class);
    permissions = mock(PermissionChecker.class);
    service =
        new VorhabenPrognoseService(
            aufenthalte, cards, boardService, permissions, Clock.fixed(NOW, ZoneOffset.UTC));
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(boardService.listColumns(BOARD))
        .thenReturn(
            List.of(
                new ColumnView(OFFEN, "Backlog", 0, null), new ColumnView(DONE, "Done", 1, null)));
    when(cards.findByBoardId(BOARD)).thenReturn(karten);
    when(aufenthalte.findByBoardId(BOARD)).thenReturn(verlauf);
  }

  // --- Recht ---------------------------------------------------------------------------------

  @Test
  void einNichtmitgliedFindetNichts() {
    doThrow(new ProjectNotFoundException()).when(permissions).requireMembership(USER, PROJECT);

    assertThatThrownBy(() -> service.epicForecast(USER, BOARD, BERLIN))
        .isInstanceOf(ProjectNotFoundException.class);
    verifyNoInteractions(aufenthalte, cards);
  }

  @Test
  void einUnbekanntesBoardIstNichtGefunden() {
    when(boardService.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    assertThatThrownBy(() -> service.epicForecast(USER, BOARD, BERLIN))
        .isInstanceOf(BoardNotFoundException.class);
    verifyNoInteractions(aufenthalte, cards);
  }

  // --- Abbildung je Lage ---------------------------------------------------------------------

  @Test
  void bildetJedeLageAb() {
    einPaketJeTag();
    Card prognose = vorhaben("Mit Prognose", false);
    paket(prognose, null, CardStatus.READY);
    paket(prognose, null, CardStatus.BACKLOG);
    paket(prognose, null, CardStatus.IN_PROGRESS);
    paket(prognose, null, CardStatus.IN_REVIEW);
    Card ohnePakete = vorhaben("Ohne Pakete", false);
    Card fertig = vorhaben("Fertig", false);
    paket(fertig, null, CardStatus.IN_REVIEW);
    paket(fertig, null, CardStatus.DONE);

    EpicForecastView view = service.epicForecast(USER, BOARD, BERLIN);

    assertThat(view.throughputDays()).isEqualTo(30);
    assertThat(view.epics())
        .extracting(
            Epic::id,
            Epic::number,
            Epic::title,
            Epic::state,
            Epic::total,
            Epic::finished,
            Epic::open,
            Epic::p50,
            Epic::p85,
            Epic::earliest,
            Epic::pendingDocuments,
            Epic::otherEpicsSharing)
        .containsExactly(
            tuple(
                prognose.requireId(),
                prognose.number(),
                "Mit Prognose",
                Lage.FORECAST,
                4,
                1,
                3,
                // Ein Paket je Tag, drei offen: Tag 3 ab dem laufenden Tag (Tag 1).
                LocalDate.of(2026, 10, 17),
                LocalDate.of(2026, 10, 17),
                false,
                List.of(),
                0),
            tuple(
                ohnePakete.requireId(),
                ohnePakete.number(),
                "Ohne Pakete",
                Lage.NO_PACKAGES,
                0,
                0,
                0,
                null,
                null,
                false,
                List.of(),
                1),
            tuple(
                fertig.requireId(),
                fertig.number(),
                "Fertig",
                Lage.ALL_DONE,
                2,
                2,
                0,
                null,
                null,
                false,
                List.of(),
                1));
  }

  @Test
  void zuWenigDurchsatzOhneDaten() {
    Card epic = vorhaben("Vorhaben", false);
    paket(epic, null, CardStatus.READY);

    EpicForecastView view = service.epicForecast(USER, BOARD, BERLIN);

    assertThat(view.throughputDays()).isZero();
    assertThat(view.epics())
        .extracting(Epic::state, Epic::open, Epic::p50, Epic::p85)
        .containsExactly(tuple(Lage.INSUFFICIENT_THROUGHPUT, 1, null, null));
  }

  @Test
  void einAusstehenderPlanMachtFruehestensUndWirdGenannt() {
    einPaketJeTag();
    Card epic = vorhaben("Vorhaben", false);
    Card plan = karte(CardType.CARD, "[Plan] Erster Schnitt", epic.requireId(), null, null, false);
    paket(epic, null, CardStatus.READY);
    Card anderes = vorhaben("Anderes", false);
    paket(anderes, null, CardStatus.READY);

    EpicForecastView view = service.epicForecast(USER, BOARD, BERLIN);

    Epic erstes = view.epics().get(0);
    assertThat(erstes.state()).isEqualTo(Lage.FORECAST);
    assertThat(erstes.earliest()).isTrue();
    assertThat(erstes.pendingDocuments())
        .containsExactly(new PendingDocument(plan.number(), "[Plan] Erster Schnitt"));
    assertThat(erstes.p50()).isEqualTo(LocalDate.of(2026, 10, 15));
    assertThat(erstes.otherEpicsSharing()).isEqualTo(1);
  }

  @Test
  void einArchiviertesVorhabenFehlt() {
    Card sichtbar = vorhaben("Sichtbar", false);
    Card archiviert = vorhaben("Archiviert", true);
    paket(archiviert, null, CardStatus.READY);

    EpicForecastView view = service.epicForecast(USER, BOARD, BERLIN);

    assertThat(view.epics()).extracting(Epic::id).containsExactly(sichtbar.requireId());
  }

  // --- Uhr und Zone --------------------------------------------------------------------------

  @Test
  void uhrUndZoneBestimmenDenLaufendenTag() {
    einPaketJeTag();
    Card epic = vorhaben("Vorhaben", false);
    paket(epic, null, CardStatus.READY);
    paket(epic, null, CardStatus.READY);

    Epic berlin = service.epicForecast(USER, BOARD, BERLIN).epics().get(0);
    Epic kiritimati = service.epicForecast(USER, BOARD, KIRITIMATI).epics().get(0);

    // Zwei offene Pakete bei einem je Tag: der zweite Tag ab dem laufenden.
    assertThat(berlin.p50()).isEqualTo(LocalDate.of(2026, 10, 16));
    assertThat(berlin.p85()).isEqualTo(LocalDate.of(2026, 10, 16));
    assertThat(kiritimati.p50()).isEqualTo(LocalDate.of(2026, 10, 17));
    assertThat(kiritimati.p85()).isEqualTo(LocalDate.of(2026, 10, 17));
  }

  @Test
  void tagEinsIstDerLaufendeTag() {
    einPaketJeTag();
    Card epic = vorhaben("Vorhaben", false);
    paket(epic, null, CardStatus.READY);

    Epic e = service.epicForecast(USER, BOARD, BERLIN).epics().get(0);

    assertThat(e.p50()).isEqualTo(LocalDate.of(2026, 10, 15));
    assertThat(e.p85()).isEqualTo(LocalDate.of(2026, 10, 15));
  }

  // --- Hilfen --------------------------------------------------------------------------------

  /**
   * Je einer der letzten 30 abgeschlossenen Tage ein Paket ohne Vorhaben, das an diesem Tag In
   * Review erreichte und dort noch liegt.
   */
  private void einPaketJeTag() {
    for (int tag = 1; tag <= 30; tag++) {
      Card c = karte(CardType.CARD, "Fertig " + tag, null, null, CardStatus.IN_REVIEW, false);
      Instant erreicht = NOW.minus(Duration.ofDays(tag));
      verlauf.add(
          stay(c, CardStatus.READY, erreicht.minus(Duration.ofHours(1)), erreicht, EndedBy.STATUS));
      verlauf.add(stay(c, CardStatus.IN_REVIEW, erreicht, null, null));
    }
  }

  private static FlussStay stay(
      Card c,
      CardStatus status,
      Instant entered,
      @Nullable Instant left,
      @Nullable EndedBy endedBy) {
    return new FlussStay(null, c.requireId(), BOARD, status, entered, left, true, endedBy);
  }

  private Card vorhaben(String titel, boolean archiviert) {
    return karte(CardType.EPIC, titel, null, null, null, archiviert);
  }

  private Card paket(Card epic, @Nullable Long herkunft, CardStatus status) {
    return karte(CardType.CARD, "Paket", epic.requireId(), herkunft, status, false);
  }

  private Card karte(
      CardType type,
      String titel,
      @Nullable Long parentId,
      @Nullable Long herkunft,
      @Nullable CardStatus status,
      boolean archiviert) {
    long id = 100L + karten.size();
    Card c =
        new Card(
            id,
            BOARD,
            status == CardStatus.DONE ? DONE : OFFEN,
            (int) id,
            titel,
            null,
            0,
            archiviert,
            null,
            1L,
            NOW,
            NOW,
            type,
            parentId,
            null,
            null,
            PROJECT,
            null,
            herkunft,
            null,
            status);
    karten.add(c);
    return c;
  }
}
