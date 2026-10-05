package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit-Tests des Status an den bestehenden Schreibpfaden und des effektiven Done-Maßstabs (Plan
 * #1294, Issue #1299): Anlegen, Verschieben, Übertragen und Umbenennen führen den Status eines
 * Arbeitspakets mit, und der Done-Zeitstempel sowie der Vorhaben-Fortschritt richten sich nach
 * {@code Arbeitspaket.effektivDone}.
 *
 * <p>Eigene Klasse und nicht ein weiterer Block in {@code CardServiceTest} — wie schon bei {@link
 * CardServiceCreateBatchTest}: Jene Klasse steht an ihrer PMD-Grenze ({@code NcssCount}).
 */
// PMD.TooManyMethods: je Schreibpfad und Statusregel ein kleiner @Test — dieselbe Begründung wie
// an CardServiceTest; ein Zerschneiden nach Methodenzahl verstreute die Regel über Dateien.
@SuppressWarnings("PMD.TooManyMethods")
class CardServiceStatusTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;

  private CardRepository cards;
  private BoardService boardService;
  private PermissionChecker permissions;
  private CardService service;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    boardService = mock(BoardService.class);
    permissions = mock(PermissionChecker.class);
    ActorContext actor = mock(ActorContext.class);
    when(actor.current()).thenReturn(ActorContext.ActorStamp.unknown());
    service =
        CardServiceAufbau.ausPorts(
            cards,
            mock(CardDependencyRepository.class),
            boardService,
            permissions,
            mock(ProjectService.class),
            mock(CardColumnTransitionRepository.class),
            new KartenZuordnung(
                mock(CardAssigneeRepository.class),
                mock(LabelRepository.class),
                mock(CardLabelRepository.class),
                mock(PermissionChecker.class)),
            mock(CardActivityRepository.class),
            actor,
            mock(ApplicationEventPublisher.class),
            Clock.fixed(FIXED, ZoneOffset.UTC));
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(cards.save(any(Card.class))).thenAnswer(inv -> mitId(inv.getArgument(0)));
  }

  /** Die gespeicherte Karte mit Id — alle übrigen Felder, auch der Status, bleiben erhalten. */
  private static Card mitId(Card c) {
    return new Card(
        c.id() == null ? 1L : c.id(),
        c.boardId(),
        c.columnId(),
        c.number(),
        c.title(),
        c.description(),
        c.positionInColumn(),
        c.archived(),
        c.movedToDoneAt(),
        c.createdBy(),
        c.createdAt(),
        c.updatedAt(),
        c.type(),
        c.parentId(),
        c.shortcode(),
        c.dueDate(),
        c.projectId(),
        c.externalKey(),
        c.derivedFromCardId(),
        c.requirementCardId(),
        c.status());
  }

  private static ColumnView column(long id, String name, int position) {
    return new ColumnView(id, name, position, null);
  }

  private static Card vorhaben(long id, long columnId) {
    return new Card(
        id,
        BOARD,
        columnId,
        1,
        "Vorhaben",
        null,
        0,
        false,
        null,
        1L,
        FIXED,
        FIXED,
        CardType.EPIC,
        null,
        "E",
        null,
        PROJECT,
        null,
        null,
        null,
        null);
  }

  /** Karte mit Titel, Status und Done-Zeitstempel — für die Statusregeln der Schreibpfade. */
  private static Card paket(
      long id, long columnId, String title, @Nullable CardStatus status, @Nullable Instant done) {
    return new Card(
        id,
        BOARD,
        columnId,
        1,
        title,
        null,
        0,
        false,
        done,
        1L,
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
        status);
  }

  private Card gespeichert() {
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    verify(cards).save(captor.capture());
    return captor.getValue();
  }

  private void stubAnlegen(String spaltenname) {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, spaltenname, 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);
  }

  @Test
  void create_arbeitspaketInProzessspalte_uebernimmtDerenStatus() {
    stubAnlegen("In Progress");

    service.create(1L, BOARD, 20L, "Paket", null, null, null);

    assertThat(gespeichert().status()).isEqualTo(CardStatus.IN_PROGRESS);
    assertThat(gespeichert().movedToDoneAt()).isNull();
  }

  @Test
  void create_arbeitspaketInEigenerSpalte_bekommtBacklog() {
    stubAnlegen("Anstehend");

    service.create(1L, BOARD, 20L, "Paket", null, null, null);

    assertThat(gespeichert().status()).isEqualTo(CardStatus.BACKLOG);
  }

  @Test
  void create_arbeitspaketInDoneSpalte_istErledigt() {
    stubAnlegen("Done");

    service.create(1L, BOARD, 20L, "Paket", null, null, null);

    assertThat(gespeichert().status()).isEqualTo(CardStatus.DONE);
    assertThat(gespeichert().movedToDoneAt()).isEqualTo(FIXED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"[Idee] Einfall", "[Fachlich] Anforderung", "  [plan] Entwurf"})
  void create_dokumentkarte_bekommtKeinenStatus(String titel) {
    stubAnlegen("Ready");

    service.create(1L, BOARD, 20L, titel, null, null, null);

    assertThat(gespeichert().status()).isNull();
  }

  @Test
  void create_dokumentkarteInDoneSpalte_folgtWeiterDerSpalte() {
    // Plan E6: Für Dokumentarten entscheidet weiter die Substring-Regel des Spaltennamens.
    stubAnlegen("Done (Archiv)");

    service.create(1L, BOARD, 20L, "[Idee] Einfall", null, null, null);

    assertThat(gespeichert().status()).isNull();
    assertThat(gespeichert().movedToDoneAt()).isEqualTo(FIXED);
  }

  @Test
  void createEpic_hatKeinenStatus() {
    when(boardService.firstColumn(BOARD)).thenReturn(column(20L, "Ready", 0));

    service.createEpic(1L, BOARD, "Vorhaben", null, null);

    assertThat(gespeichert().status()).isNull();
  }

  @Test
  void createDirect_arbeitspaket_uebernimmtDenStatusDerSpalte() {
    stubAnlegen("Ready");

    service.createDirect(
        1L, BOARD, 20L, new CardService.DirectCard("Finding", null, "sonar:x", null, null));

    assertThat(gespeichert().status()).isEqualTo(CardStatus.READY);
  }

  @Test
  void move_arbeitspaketInProzessspalte_uebernimmtDerenStatus() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(paket(1L, 20L, "Paket", CardStatus.BACKLOG, null)));
    when(boardService.requireColumn(21L, BOARD)).thenReturn(column(21L, "In Review", 3));

    service.move(1L, 1L, 21L, 0);

    assertThat(gespeichert().status()).isEqualTo(CardStatus.IN_REVIEW);
  }

  @Test
  void move_arbeitspaketInEigeneSpalte_behaeltSeinenStatus() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(paket(1L, 20L, "Paket", CardStatus.READY, null)));
    when(boardService.requireColumn(22L, BOARD)).thenReturn(column(22L, "Anstehend", 5));

    service.move(1L, 1L, 22L, 0);

    assertThat(gespeichert().status()).isEqualTo(CardStatus.READY);
    assertThat(gespeichert().movedToDoneAt()).isNull();
  }

  @Test
  void move_umsortierenInDerselbenSpalte_laesstDenStatusStehen() {
    // Status weicht von der Spalte ab (gesetzt über den Wechsler) — Umsortieren ändert ihn nicht.
    when(cards.findById(1L))
        .thenReturn(Optional.of(paket(1L, 20L, "Paket", CardStatus.READY, null)));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    service.move(1L, 1L, 20L, 3);

    assertThat(gespeichert().status()).isEqualTo(CardStatus.READY);
  }

  @Test
  void move_erledigtesArbeitspaketInEigeneSpalte_behaeltStatusUndZeitstempel() {
    Instant erledigt = FIXED.minusSeconds(10);
    when(cards.findById(1L))
        .thenReturn(Optional.of(paket(1L, 21L, "Paket", CardStatus.DONE, erledigt)));
    when(boardService.requireColumn(22L, BOARD)).thenReturn(column(22L, "Anstehend", 5));

    service.move(1L, 1L, 22L, 0);

    assertThat(gespeichert().status()).isEqualTo(CardStatus.DONE);
    assertThat(gespeichert().movedToDoneAt()).isEqualTo(erledigt);
  }

  @Test
  void move_arbeitspaketMitStatusDone_ohneZeitstempel_bekommtIhnAuchInEigenerSpalte() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(paket(1L, 20L, "Paket", CardStatus.DONE, null)));
    when(boardService.requireColumn(22L, BOARD)).thenReturn(column(22L, "Anstehend", 5));

    service.move(1L, 1L, 22L, 0);

    assertThat(gespeichert().movedToDoneAt()).isEqualTo(FIXED);
  }

  @Test
  void move_arbeitspaketMitStatusBacklog_inSpalteDoneArchiv_istNichtErledigt() {
    // „Done (Archiv)" ist keine Prozessspalte: Der Status bleibt, und er entscheidet (Plan E7).
    when(cards.findById(1L))
        .thenReturn(Optional.of(paket(1L, 20L, "Paket", CardStatus.BACKLOG, null)));
    when(boardService.requireColumn(22L, BOARD)).thenReturn(column(22L, "Done (Archiv)", 5));

    service.move(1L, 1L, 22L, 0);

    assertThat(gespeichert().status()).isEqualTo(CardStatus.BACKLOG);
    assertThat(gespeichert().movedToDoneAt()).isNull();
  }

  @Test
  void move_dokumentkarte_bleibtOhneStatusUndFolgtDerSpalte() {
    when(cards.findById(1L)).thenReturn(Optional.of(paket(1L, 20L, "[Plan] Entwurf", null, null)));
    when(boardService.requireColumn(21L, BOARD)).thenReturn(column(21L, "Done", 4));

    service.move(1L, 1L, 21L, 0);

    assertThat(gespeichert().status()).isNull();
    assertThat(gespeichert().movedToDoneAt()).isEqualTo(FIXED);
  }

  @Test
  void transfer_erledigtesArbeitspaketInEigeneSpalte_behaeltStatusUndZeitstempel() {
    // Plan E18: Der Zeitstempel wird neu abgeleitet statt bedingungslos gelöscht.
    Instant erledigt = FIXED.minusSeconds(10);
    when(cards.findById(100L))
        .thenReturn(Optional.of(paket(100L, 50L, "Paket", CardStatus.DONE, erledigt)));
    when(boardService.requireProjectId(20L)).thenReturn(PROJECT);
    when(boardService.requireColumn(60L, 20L))
        .thenReturn(new ColumnView(60L, "Anstehend", 0, null));

    service.transfer(1L, 100L, 20L, 60L);

    assertThat(gespeichert().status()).isEqualTo(CardStatus.DONE);
    assertThat(gespeichert().movedToDoneAt()).isEqualTo(erledigt);
  }

  @Test
  void transfer_arbeitspaketInProzessspalte_uebernimmtDerenStatus() {
    when(cards.findById(100L))
        .thenReturn(Optional.of(paket(100L, 50L, "Paket", CardStatus.DONE, FIXED)));
    when(boardService.requireProjectId(20L)).thenReturn(PROJECT);
    when(boardService.requireColumn(60L, 20L)).thenReturn(new ColumnView(60L, "Ready", 0, null));

    service.transfer(1L, 100L, 20L, 60L);

    assertThat(gespeichert().status()).isEqualTo(CardStatus.READY);
    assertThat(gespeichert().movedToDoneAt()).isNull();
  }

  @Test
  void transfer_arbeitspaketInDoneSpalte_bekommtDenZeitstempel() {
    when(cards.findById(100L))
        .thenReturn(Optional.of(paket(100L, 50L, "Paket", CardStatus.READY, null)));
    when(boardService.requireProjectId(20L)).thenReturn(PROJECT);
    when(boardService.requireColumn(60L, 20L)).thenReturn(new ColumnView(60L, "Done", 0, null));

    service.transfer(1L, 100L, 20L, 60L);

    assertThat(gespeichert().status()).isEqualTo(CardStatus.DONE);
    assertThat(gespeichert().movedToDoneAt()).isEqualTo(FIXED);
  }

  @Test
  void update_dokumentWirdArbeitspaket_bekommtDenStatusDerProzessspalte() {
    when(cards.findById(1L)).thenReturn(Optional.of(paket(1L, 20L, "[Idee] Einfall", null, null)));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Ready", 1));

    service.update(1L, 1L, "Einfall", null, null, null, null, null);

    assertThat(gespeichert().status()).isEqualTo(CardStatus.READY);
  }

  @Test
  void update_dokumentWirdArbeitspaket_inEigenerSpalte_bekommtBacklog() {
    Instant erledigt = FIXED.minusSeconds(10);
    // Die Dokumentkarte lag in „Done (Archiv)" und galt deshalb als erledigt; als Arbeitspaket
    // entscheidet ihr Status BACKLOG — der Zeitstempel folgt derselben Ableitung.
    when(cards.findById(1L))
        .thenReturn(Optional.of(paket(1L, 20L, "[Fachlich] Anf", null, erledigt)));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Done (Archiv)", 1));

    service.update(1L, 1L, "Anf", null, null, null, null, null);

    assertThat(gespeichert().status()).isEqualTo(CardStatus.BACKLOG);
    assertThat(gespeichert().movedToDoneAt()).isNull();
  }

  @Test
  void update_dokumentWirdArbeitspaket_inDoneSpalte_behaeltDenZeitstempel() {
    Instant erledigt = FIXED.minusSeconds(10);
    when(cards.findById(1L))
        .thenReturn(Optional.of(paket(1L, 20L, "[Plan] Entwurf", null, erledigt)));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Done", 4));

    service.update(1L, 1L, "Entwurf", null, null, null, null, null);

    assertThat(gespeichert().status()).isEqualTo(CardStatus.DONE);
    assertThat(gespeichert().movedToDoneAt()).isEqualTo(erledigt);
  }

  @Test
  void update_arbeitspaketWirdDokument_raeumtStatusUndZeitstempel() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(paket(1L, 20L, "Paket", CardStatus.DONE, FIXED.minusSeconds(10))));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Anstehend", 5));

    service.update(1L, 1L, "[Idee] Paket", null, null, null, null, null);

    assertThat(gespeichert().status()).isNull();
    assertThat(gespeichert().movedToDoneAt()).isNull();
  }

  @Test
  void update_ohneArtwechsel_laesstDenStatusStehen() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(paket(1L, 20L, "Paket", CardStatus.IN_REVIEW, null)));

    service.update(1L, 1L, "Paket umbenannt", null, null, null, null, null);

    assertThat(gespeichert().status()).isEqualTo(CardStatus.IN_REVIEW);
    verify(boardService, never()).requireColumn(anyLong(), anyLong());
  }

  @Test
  void updateContent_arbeitspaketWirdDokument_raeumtDenStatus() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(paket(1L, 20L, "Paket", CardStatus.READY, null)));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Ready", 1));

    service.updateContent(1L, 1L, "[Fachlich] Paket", null);

    assertThat(gespeichert().status()).isNull();
  }

  @Test
  void updateContent_dokumentWirdArbeitspaket_bekommtDenStatusDerProzessspalte() {
    when(cards.findById(1L)).thenReturn(Optional.of(paket(1L, 20L, "[Idee] Einfall", null, null)));
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "In Progress", 2));

    service.updateContent(1L, 1L, "Einfall", null);

    assertThat(gespeichert().status()).isEqualTo(CardStatus.IN_PROGRESS);
  }

  @Test
  void listEpics_zaehltArbeitspaketeNachIhremStatus() {
    // Pakete 6 und 8 stehen auf DONE in einer eigenen Spalte und sind erledigt; Paket 7 liegt in
    // der Done-Spalte, steht aber auf BACKLOG — für ein Arbeitspaket entscheidet der Status (E7).
    // Nach dem Spaltennamen wäre es umgekehrt: 1 von 3.
    when(boardService.listColumns(BOARD))
        .thenReturn(List.of(column(21L, "Done", 4), column(22L, "Anstehend", 5)));
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                vorhaben(5L, 21L),
                paket(6L, 22L, "Paket", CardStatus.DONE, FIXED).withParent(5L),
                paket(7L, 21L, "Paket", CardStatus.BACKLOG, null).withParent(5L),
                paket(8L, 22L, "Paket", CardStatus.DONE, FIXED).withParent(5L)));

    List<CardService.EpicView> result = service.listEpics(1L, BOARD);

    assertThat(result)
        .singleElement()
        .extracting(CardService.EpicView::done, CardService.EpicView::total)
        .containsExactly(2, 3);
  }

  // --- setStatus legt in die Prozessspalte (Issue #1326, Korrektur #787) -----

  /** Die Spalten des Boards in Board-Reihenfolge; jede ist über requireColumn auffindbar. */
  private void spalten(ColumnView... spalten) {
    when(boardService.listColumns(BOARD)).thenReturn(List.of(spalten));
    for (ColumnView spalte : spalten) {
      when(boardService.requireColumn(spalte.id(), BOARD)).thenReturn(spalte);
    }
  }

  private void paketIn(long columnId, CardStatus status) {
    when(cards.findById(5L)).thenReturn(Optional.of(paket(5L, columnId, "Paket", status, null)));
  }

  @Test
  void setStatus_legtDasPaketAnsEndeDerProzessspalteDesNeuenStatus() {
    paketIn(20L, CardStatus.READY);
    spalten(column(20L, "Ready", 1), column(30L, "In Review", 3));

    service.setStatus(1L, 5L, "IN_REVIEW");

    verify(cards).move(5L, 30L, Integer.MAX_VALUE);
    assertThat(gespeichert().status()).isEqualTo(CardStatus.IN_REVIEW);
  }

  @Test
  void setStatus_holtDasPaketAusEinerEigenenSpalteInDieProzessspalte() {
    paketIn(20L, CardStatus.BACKLOG);
    spalten(column(20L, "Anstehend", 0), column(30L, "Ready", 1));

    service.setStatus(1L, 5L, "READY");

    verify(cards).move(5L, 30L, Integer.MAX_VALUE);
    assertThat(gespeichert().status()).isEqualTo(CardStatus.READY);
  }

  @Test
  void setStatus_ohneSpalteFuerDenStatus_setztNurDenStatus() {
    paketIn(20L, CardStatus.READY);
    spalten(column(20L, "Ready", 1), column(40L, "Anstehend", 2));

    service.setStatus(1L, 5L, "IN_REVIEW");

    verify(cards, never()).move(anyLong(), anyLong(), anyInt());
    Card nachher = gespeichert();
    assertThat(nachher.status()).isEqualTo(CardStatus.IN_REVIEW);
    assertThat(nachher.columnId()).isEqualTo(20L);
  }

  @Test
  void setStatus_inDerPassendenProzessspalte_setztNurDenStatusUndLaesstDiePosition() {
    // Altbestand: Die Karte liegt schon in „In Review", trägt aber noch READY.
    paketIn(20L, CardStatus.READY);
    spalten(column(20L, "In Review", 3));

    service.setStatus(1L, 5L, "IN_REVIEW");

    verify(cards, never()).move(anyLong(), anyLong(), anyInt());
    assertThat(gespeichert().status()).isEqualTo(CardStatus.IN_REVIEW);
  }

  @Test
  void setStatus_beiZweiPassendenSpalten_nimmtDieErsteInBoardReihenfolge() {
    paketIn(20L, CardStatus.BACKLOG);
    spalten(column(20L, "Backlog", 0), column(30L, "Ready", 1), column(31L, "ready", 2));

    service.setStatus(1L, 5L, "READY");

    verify(cards).move(5L, 30L, Integer.MAX_VALUE);
    verify(cards, never()).move(5L, 31L, Integer.MAX_VALUE);
  }

  @Test
  void setStatus_ohneRecht_verschiebtNicht() {
    paketIn(20L, CardStatus.READY);
    spalten(column(20L, "Ready", 1), column(30L, "In Review", 3));
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(1L, PROJECT, Permission.CARD_MOVE);

    assertThatThrownBy(() -> service.setStatus(1L, 5L, "IN_REVIEW"))
        .isInstanceOf(ProjectAccessDeniedException.class);

    verify(cards, never()).move(anyLong(), anyLong(), anyInt());
    verify(cards, never()).save(any(Card.class));
  }
}
