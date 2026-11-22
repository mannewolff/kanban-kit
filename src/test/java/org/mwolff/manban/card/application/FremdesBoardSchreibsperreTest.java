package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.BoardQuelle;
import org.mwolff.manban.project.application.FremdesBoardException;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Schreibsperre bei fremdem Board in den Karten-Diensten (Issue #2161, Plan #2152 A10): Jeder
 * Schreibweg lehnt nach der Rechteprüfung mit {@link FremdesBoardException} (409) ab, bevor er
 * etwas schreibt. Je Dienst mindestens ein Fall; die Bausteine entstehen echt aus den Port-Mocks
 * ({@link CardServiceAufbau}), damit auch die Prüfung in {@link KartenGrundlage} greift.
 *
 * <p>Ohne fremdes Board bleiben die Dienste unverändert — das zeigen ihre bisherigen Tests, die mit
 * {@link CardServiceAufbau#KEIN_FREMDES_BOARD} laufen.
 */
// PMD.TooManyMethods / PMD.CouplingBetweenObjects: ein Sperrfall je Schreibweg über alle Dienste
// des Pakets; die gemeinsame Fixture ist der Zweck der Klasse.
@SuppressWarnings({"PMD.TooManyMethods", "PMD.CouplingBetweenObjects"})
class FremdesBoardSchreibsperreTest {

  private static final Instant FIXED = Instant.parse("2026-11-15T10:00:00Z");
  private static final long USER = 7L;
  private static final long PROJEKT = 1L;
  private static final long BOARD = 10L;
  private static final long SPALTE = 100L;
  private static final long KARTEN_ID = 5L;
  private static final long ZIEL_PROJEKT = 2L;
  private static final long ZIEL_BOARD = 20L;
  private static final long ZIEL_SPALTE = 200L;

  private CardRepository cards;
  private CardDependencyRepository dependencies;
  private BoardService boardService;
  private PermissionChecker permissions;
  private CardColumnTransitionRepository transitions;
  private KartenZuordnung zuordnung;
  private CardActivityRepository activity;
  private ActorContext actor;
  private ApplicationEventPublisher events;
  private Clock clock;
  private Set<Long> fremdeProjekte;
  private BoardQuelle quelle;

  private static Card karte(long id, CardType type) {
    return new Card(
        id,
        BOARD,
        SPALTE,
        (int) id,
        "Karte " + id,
        null,
        0,
        false,
        null,
        USER,
        FIXED,
        FIXED,
        type,
        null,
        null,
        null,
        PROJEKT,
        null,
        null,
        null,
        null);
  }

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    dependencies = mock(CardDependencyRepository.class);
    boardService = mock(BoardService.class);
    permissions = mock(PermissionChecker.class);
    transitions = mock(CardColumnTransitionRepository.class);
    zuordnung = mock(KartenZuordnung.class);
    activity = mock(CardActivityRepository.class);
    actor = mock(ActorContext.class);
    when(actor.current()).thenReturn(ActorStamp.unknown());
    events = mock(ApplicationEventPublisher.class);
    clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    fremdeProjekte = new HashSet<>(Set.of(PROJEKT));
    quelle = fremdeProjekte::contains;

    when(boardService.requireProjectId(BOARD)).thenReturn(PROJEKT);
    when(boardService.requireProjectId(ZIEL_BOARD)).thenReturn(ZIEL_PROJEKT);
    when(boardService.boardIdOfColumn(SPALTE)).thenReturn(BOARD);
    when(boardService.requireColumn(SPALTE, BOARD))
        .thenReturn(new ColumnView(SPALTE, "Backlog", 0, null));
    when(boardService.requireColumn(ZIEL_SPALTE, ZIEL_BOARD))
        .thenReturn(new ColumnView(ZIEL_SPALTE, "Backlog", 0, null));
    when(cards.findById(KARTEN_ID)).thenReturn(Optional.of(karte(KARTEN_ID, CardType.CARD)));
  }

  private CardService cardService() {
    return CardServiceAufbau.ausPorts(
        cards,
        dependencies,
        boardService,
        permissions,
        transitions,
        zuordnung,
        activity,
        actor,
        events,
        clock,
        mock(UserLookup.class),
        mock(CardBestandRepository.class),
        quelle);
  }

  private CardMoveService moveService() {
    return CardServiceAufbau.moveAusPorts(
        cards,
        dependencies,
        boardService,
        permissions,
        transitions,
        zuordnung,
        activity,
        actor,
        events,
        clock,
        quelle);
  }

  private CardIngestService ingestService() {
    return CardServiceAufbau.ingestAusPorts(
        cards,
        dependencies,
        boardService,
        permissions,
        transitions,
        zuordnung,
        activity,
        actor,
        events,
        clock,
        quelle);
  }

  private CardArchiveService archiveService() {
    return CardServiceAufbau.archiveAusPorts(
        cards,
        mock(CardBestandRepository.class),
        dependencies,
        boardService,
        permissions,
        transitions,
        zuordnung,
        activity,
        actor,
        events,
        clock,
        quelle);
  }

  private EpicService epicService() {
    return CardServiceAufbau.epicAusPorts(
        cards,
        dependencies,
        boardService,
        permissions,
        transitions,
        zuordnung,
        activity,
        actor,
        events,
        clock,
        quelle);
  }

  private KartenGrundlage grundlage() {
    return new KartenGrundlage(
        cards,
        new KartenAbhaengigkeiten(dependencies, cards),
        boardService,
        permissions,
        transitions,
        zuordnung,
        activity,
        actor,
        events,
        clock,
        quelle,
        new Anbietersicht(cards, zuordnung, permissions));
  }

  private LabelService labelService() {
    return new LabelService(
        mock(LabelRepository.class),
        mock(CardLabelRepository.class),
        cards,
        boardService,
        permissions,
        events,
        actor,
        mock(CardLabelEventRepository.class),
        clock,
        grundlage());
  }

  /**
   * Die Zuordnung wurde nur gelesen: die Abfrage der Reparaturkarten, mit der die Sichtprüfung vor
   * der Schreibsperre läuft (Issue #2211). Geschrieben wurde nichts.
   */
  private void nurSichtbarkeitGeprueft() {
    verify(zuordnung).kartenMitLabel(anyLong(), anyString());
    verifyNoMoreInteractions(zuordnung);
  }

  /** Nichts wurde geschrieben: keine Karte, keine Nummer, kein Spaltenverlauf, kein Ereignis. */
  private void nichtsGeschrieben() {
    verify(cards, never()).save(any(Card.class));
    verify(cards, never()).allocateCardNumber(anyLong());
    verify(cards, never()).deleteById(anyLong());
    verifyNoInteractions(transitions, activity, events);
  }

  // --- CardService -------------------------------------------------------------------------

  @Test
  void cardService_create_lehntAb() {
    assertThatThrownBy(
            () ->
                cardService()
                    .createCardsBatch(
                        USER, BOARD, SPALTE, List.of(new CardService.NewCard("Neu", null))))
        .isInstanceOf(FremdesBoardException.class);
    verify(permissions).require(USER, PROJEKT, Permission.TICKET_CREATE);
    nichtsGeschrieben();
  }

  @Test
  void cardService_update_lehntAb() {
    assertThatThrownBy(
            () -> cardService().update(USER, KARTEN_ID, "Titel", null, null, null, null, null))
        .isInstanceOf(FremdesBoardException.class);
    nichtsGeschrieben();
  }

  @Test
  void cardService_setAssignees_lehntAb() {
    assertThatThrownBy(() -> cardService().setAssignees(USER, KARTEN_ID, List.of(USER)))
        .isInstanceOf(FremdesBoardException.class);
    nurSichtbarkeitGeprueft();
    nichtsGeschrieben();
  }

  @Test
  void cardService_setLabels_lehntAb() {
    assertThatThrownBy(() -> cardService().setLabels(USER, KARTEN_ID, List.of(3L)))
        .isInstanceOf(FremdesBoardException.class);
    nurSichtbarkeitGeprueft();
  }

  @Test
  void cardService_changeLabel_lehntAb() {
    assertThatThrownBy(() -> cardService().changeLabel(USER, KARTEN_ID, 3L, LabelAction.ADD))
        .isInstanceOf(FremdesBoardException.class);
    nurSichtbarkeitGeprueft();
  }

  // --- KartenGrundlage ---------------------------------------------------------------------

  @Test
  void kartenGrundlage_anlegen_lehntAb() {
    // Issue #2174: Die Sperre steht in anlegen selbst, nicht erst beim Aufrufer — der Batch prüft
    // vorab, die übrigen Anlege-Wege verlassen sich allein auf diese Stelle.
    assertThatThrownBy(
            () ->
                grundlage()
                    .anlegen(
                        USER, BOARD, SPALTE, "Neu", null, null, null, null, null, null, null, null,
                        null))
        .isInstanceOf(FremdesBoardException.class);
    verify(permissions).require(USER, PROJEKT, Permission.TICKET_CREATE);
    verify(boardService, never()).requireColumn(anyLong(), anyLong());
    nichtsGeschrieben();
  }

  // --- CardMoveService ---------------------------------------------------------------------

  @Test
  void cardMoveService_move_lehntAb() {
    assertThatThrownBy(() -> moveService().move(USER, KARTEN_ID, SPALTE, 0))
        .isInstanceOf(FremdesBoardException.class);
    verify(permissions).require(USER, PROJEKT, Permission.CARD_MOVE);
    verify(cards, never()).move(anyLong(), anyLong(), anyInt());
    nichtsGeschrieben();
  }

  @Test
  void cardMoveService_setStatus_lehntAb() {
    assertThatThrownBy(() -> moveService().setStatus(USER, KARTEN_ID, "READY"))
        .isInstanceOf(FremdesBoardException.class);
    nichtsGeschrieben();
  }

  @Test
  void cardMoveService_sortColumnByNumber_lehntAb() {
    assertThatThrownBy(() -> moveService().sortColumnByNumber(USER, SPALTE, SortDirection.ASC))
        .isInstanceOf(FremdesBoardException.class);
    verify(cards, never()).sortActiveByNumber(anyLong(), any());
  }

  @Test
  void cardMoveService_transfer_lehntAb_wennDasQuellprojektFremdIst() {
    assertThatThrownBy(() -> moveService().transfer(USER, KARTEN_ID, ZIEL_BOARD, ZIEL_SPALTE))
        .isInstanceOf(FremdesBoardException.class);
    verify(cards, never()).transfer(anyLong(), anyLong(), anyLong(), anyInt());
    nichtsGeschrieben();
  }

  @Test
  void cardMoveService_transfer_lehntAb_wennDasZielprojektFremdIst() {
    // Quelle eigen, Ziel fremd: Ein Umzug vergäbe dort eine Nummer (Plan #2152 A2).
    fremdeProjekte.clear();
    fremdeProjekte.add(ZIEL_PROJEKT);

    assertThatThrownBy(() -> moveService().transfer(USER, KARTEN_ID, ZIEL_BOARD, ZIEL_SPALTE))
        .isInstanceOf(FremdesBoardException.class);
    verify(permissions).requireOwner(USER, ZIEL_PROJEKT);
    verify(cards, never()).transfer(anyLong(), anyLong(), anyLong(), anyInt());
    nichtsGeschrieben();
  }

  @Test
  void cardMoveService_bulkTransfer_lehntAb_wennDasZielprojektFremdIst() {
    fremdeProjekte.clear();
    fremdeProjekte.add(ZIEL_PROJEKT);

    assertThatThrownBy(
            () -> moveService().bulkTransfer(USER, List.of(KARTEN_ID), ZIEL_BOARD, ZIEL_SPALTE))
        .isInstanceOf(FremdesBoardException.class);
    verify(cards, never()).transfer(anyLong(), anyLong(), anyLong(), anyInt());
    nichtsGeschrieben();
  }

  // --- CardIngestService -------------------------------------------------------------------

  @Test
  void cardIngestService_createDirect_lehntAb() {
    CardIngestService.DirectCard neu =
        new CardIngestService.DirectCard("Neu", null, "schluessel", null, null);

    assertThatThrownBy(() -> ingestService().createDirect(USER, BOARD, SPALTE, neu))
        .isInstanceOf(FremdesBoardException.class);
    // Auch der Idempotenz-Abgleich läuft nicht: Er gäbe eine bestehende Karte als Erfolg zurück.
    verify(cards, never()).findByProjectIdAndExternalKey(anyLong(), any());
    nichtsGeschrieben();
  }

  @Test
  void cardIngestService_updateContent_lehntAb() {
    assertThatThrownBy(() -> ingestService().updateContent(USER, KARTEN_ID, "Titel", null))
        .isInstanceOf(FremdesBoardException.class);
    nichtsGeschrieben();
  }

  // --- CardArchiveService ------------------------------------------------------------------

  @Test
  void cardArchiveService_archive_lehntAb() {
    assertThatThrownBy(() -> archiveService().archive(USER, KARTEN_ID))
        .isInstanceOf(FremdesBoardException.class);
    nichtsGeschrieben();
  }

  @Test
  void cardArchiveService_delete_lehntAb() {
    assertThatThrownBy(() -> archiveService().delete(USER, KARTEN_ID))
        .isInstanceOf(FremdesBoardException.class);
    verify(cards, never()).softDelete(anyLong(), any());
  }

  @Test
  void cardArchiveService_purge_lehntAb() {
    assertThatThrownBy(() -> archiveService().purge(USER, KARTEN_ID))
        .isInstanceOf(FremdesBoardException.class);
    verify(permissions).require(USER, PROJEKT, Permission.BOARD_DELETE);
    nichtsGeschrieben();
  }

  // --- EpicService und VorhabenMassenzuordnung ---------------------------------------------

  @Test
  void epicService_createEpic_lehntAb() {
    assertThatThrownBy(() -> epicService().createEpic(USER, BOARD, "Vorhaben", null, null))
        .isInstanceOf(FremdesBoardException.class);
    verify(permissions).require(USER, PROJEKT, Permission.EPIC_CREATE);
    nichtsGeschrieben();
  }

  @Test
  void epicService_openEpicFromCard_lehntAb() {
    assertThatThrownBy(
            () -> epicService().openEpicFromCard(USER, KARTEN_ID, null, "Vorhaben", null))
        .isInstanceOf(FremdesBoardException.class);
    nichtsGeschrieben();
  }

  @Test
  void epicService_assignParent_lehntAb() {
    assertThatThrownBy(() -> epicService().assignParent(USER, KARTEN_ID, null))
        .isInstanceOf(FremdesBoardException.class);
    nichtsGeschrieben();
  }

  @Test
  void vorhabenMassenzuordnung_ordneZu_lehntAb() {
    long epicId = 6L;
    when(cards.findById(epicId)).thenReturn(Optional.of(karte(epicId, CardType.EPIC)));

    assertThatThrownBy(() -> epicService().assignParentBulk(USER, List.of(KARTEN_ID), epicId))
        .isInstanceOf(FremdesBoardException.class);
    verify(permissions).require(USER, PROJEKT, Permission.TICKET_UPDATE);
    nichtsGeschrieben();
  }

  // --- LabelService ------------------------------------------------------------------------

  @Test
  void labelService_create_lehntAb() {
    assertThatThrownBy(() -> labelService().create(USER, BOARD, "Neu", "#fff"))
        .isInstanceOf(FremdesBoardException.class);
    verify(permissions).require(USER, PROJEKT, Permission.BOARD_UPDATE);
  }

  @Test
  void labelService_addToCard_lehntAb() {
    assertThatThrownBy(() -> labelService().addToCard(USER, KARTEN_ID, "Bug"))
        .isInstanceOf(FremdesBoardException.class);
    verifyNoInteractions(events);
  }

  @Test
  void labelService_removeFromCard_lehntAb() {
    assertThatThrownBy(() -> labelService().removeFromCard(USER, KARTEN_ID, "Bug"))
        .isInstanceOf(FremdesBoardException.class);
    verifyNoInteractions(events);
  }
}
