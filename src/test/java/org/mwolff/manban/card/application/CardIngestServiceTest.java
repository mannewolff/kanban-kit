package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Verhaltenstests des Kanban-kompatiblen Einlieferns (Mockito an den Ports). Die Testmethoden
 * stammen unverändert aus {@code CardServiceTest} (Issue #1392, Plan #1387 E6); {@link
 * KartenGrundlage}, {@link KartenSicht} und {@link KartenAbhaengigkeiten} entstehen echt aus
 * denselben Port-Mocks.
 */
// PMD.TooManyMethods und PMD.CouplingBetweenObjects: wandern mit den Testmethoden aus
// CardServiceTest (Issue #1392) — je Use-Case des Einlieferns Erfolgs- und Fehlerpfade, gebaut aus
// den Ports, die KartenGrundlage und KartenSicht echt brauchen (Plan #1387, E6). Ein Zerschneiden
// nach Methodenzahl verstreute die Use-Cases über Dateien, ohne etwas zu entkoppeln.
@SuppressWarnings({"PMD.TooManyMethods", "PMD.CouplingBetweenObjects"})
class CardIngestServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;

  private CardRepository cards;
  private CardDependencyRepository dependencies;
  private BoardService boardService;
  private PermissionChecker permissions;
  private CardActivityRepository activity;
  private ApplicationEventPublisher events;
  private CardIngestService service;

  private static Card card(
      long id,
      long columnId,
      int number,
      boolean archived,
      Instant done,
      CardType type,
      Long parentId,
      String shortcode) {
    return new Card(
        id, BOARD, columnId, number, "Titel", null, 0, archived, done, 1L, FIXED, FIXED, type,
        parentId, shortcode, null, PROJECT, null, null, null, null);
  }

  private static ColumnView column(long id, String name, int position) {
    return new ColumnView(id, name, position, null);
  }

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    dependencies = mock(CardDependencyRepository.class);
    boardService = mock(BoardService.class);
    permissions = mock(PermissionChecker.class);
    activity = mock(CardActivityRepository.class);
    ActorContext actor = mock(ActorContext.class);
    when(actor.current()).thenReturn(ActorContext.ActorStamp.unknown());
    events = mock(ApplicationEventPublisher.class);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service =
        CardServiceAufbau.ingestAusPorts(
            cards,
            dependencies,
            boardService,
            permissions,
            mock(CardColumnTransitionRepository.class),
            new KartenZuordnung(
                mock(CardAssigneeRepository.class),
                mock(LabelRepository.class),
                mock(CardLabelRepository.class),
                permissions),
            activity,
            actor,
            events,
            clock);
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(cards.save(any(Card.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
  }

  private static Card withId(Card c) {
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
        null,
        null,
        null);
  }

  private static Card boardCard(
      long id, long columnId, int number, int position, boolean archived) {
    return new Card(
        id,
        BOARD,
        columnId,
        number,
        "Titel",
        "Body",
        position,
        archived,
        null,
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
        null);
  }

  private static Card paket(long id, String titel, CardType type, @Nullable CardStatus status) {
    return new Card(
        id, BOARD, 20L, 7, titel, null, 3, false, null, 1L, FIXED, FIXED, type, null, null, null,
        PROJECT, null, null, null, status);
  }

  private CardBoardActivityEvent onlyPublishedEvent() {
    ArgumentCaptor<CardBoardActivityEvent> captor =
        ArgumentCaptor.forClass(CardBoardActivityEvent.class);
    verify(events).publishEvent(captor.capture());
    return captor.getValue();
  }

  @Test
  void updateContent_keepsDescription_whenNull_andTrimsTitle() {
    // Given: der schmale Schreibweg (#571). description == null heißt „nicht ändern" — ein
    // umgedrehter Guard (Mutant) würde die vorhandene Beschreibung mit null überschreiben.
    when(cards.findById(1L))
        .thenReturn(
            Optional.of(
                card(1L, 20L, 1, false, null, CardType.CARD, null, null)
                    .withContent("Titel", "Bestand")));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    CardIngestService.BoardItemView view = service.updateContent(1L, 1L, "  Neuer Titel  ", null);

    // Then: Beschreibung steht, Titel ist getrimmt, Rückgabe trägt den neuen Stand.
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().description()).isEqualTo("Bestand");
    assertThat(captor.getValue().title()).isEqualTo("Neuer Titel");
    assertThat(view.title()).isEqualTo("Neuer Titel");
    assertThat(view.description()).isEqualTo("Bestand");
    assertThat(view.epic()).isFalse();

    // Audit und Live-Update laufen wie beim Voll-Update — sonst wäre dieser Weg ein Schlupfloch.
    verify(activity)
        .add(
            1L,
            1L,
            CardActivityType.UPDATED,
            "Karte bearbeitet",
            FIXED,
            ActorContext.ActorStamp.unknown());
    assertThat(onlyPublishedEvent().type()).isEqualTo(ActivityType.UPDATED);
  }

  @Test
  void updateContent_clearsDescription_whenBlank() {
    // Given: ein blanker Body löscht die Beschreibung (normalize) — die Gegenprobe zum null-Fall.
    when(cards.findById(1L))
        .thenReturn(
            Optional.of(
                card(1L, 20L, 1, false, null, CardType.CARD, null, null)
                    .withContent("Titel", "Bestand")));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.updateContent(1L, 1L, "Titel", "   ");

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().description()).isNull();
  }

  @Test
  void updateContent_setsDescription_andMarksEpic() {
    // Given: ein Epic — der Typ-Zweig der Rückgabe (epic=true) und der Setz-Fall der Beschreibung.
    when(cards.findById(5L))
        .thenReturn(Optional.of(card(5L, 20L, 5, false, null, CardType.EPIC, null, "EPX")));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    CardIngestService.BoardItemView view =
        service.updateContent(1L, 5L, "Neues Epic", "Neuer Rumpf");

    // Then: Inhalt gesetzt, Kürzel unangetastet (anders als beim Voll-Update).
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().description()).isEqualTo("Neuer Rumpf");
    assertThat(captor.getValue().shortcode()).isEqualTo("EPX");
    assertThat(view.epic()).isTrue();
    assertThat(view.number()).isEqualTo(5);
  }

  @Test
  void updateContent_throwsCardNotFound_whenCardUnknown() {
    // Given: keine Karte -> requireCardOp wirft, nichts wird geschrieben.
    when(cards.findById(99L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.updateContent(1L, 99L, "Neu", "Neu"))
        .isInstanceOf(CardNotFoundException.class);
    verify(cards, never()).save(any());
  }

  @Test
  void listBoardItems_loestDieHerkunftMitEinemEinzigenSammelzugriffAuf() {
    // Vier Karten mit drei VERSCHIEDENEN Vorfahren. Die Aufloesung darf genau einen
    // zusaetzlichen Port-Aufruf ausloesen, nicht einen je Vorfahr — sonst entstuende ein N+1
    // auf einer Liste, die ein ganzes Board umfasst.
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                card(1L, 20L, 1, false, null, CardType.CARD, null, null).withDerivedFrom(91L),
                card(2L, 20L, 2, false, null, CardType.CARD, null, null).withDerivedFrom(92L),
                card(3L, 20L, 3, false, null, CardType.CARD, null, null).withDerivedFrom(93L),
                card(4L, 20L, 4, false, null, CardType.CARD, null, null)));
    when(cards.findByIds(any()))
        .thenReturn(
            List.of(
                card(91L, 20L, 91, false, null, CardType.CARD, null, null),
                card(92L, 20L, 92, false, null, CardType.CARD, null, null),
                card(93L, 20L, 93, false, null, CardType.CARD, null, null)));

    List<CardIngestService.BoardItemView> items = service.listBoardItems(5L, BOARD);

    verify(cards, times(1)).findByIds(any());
    verify(cards, never()).findById(anyLong());
    assertThat(items)
        .extracting(CardIngestService.BoardItemView::derivedFrom)
        .containsExactly(91, 92, 93, null);
  }

  @Test
  void listBoardItems_fragtGarNichtNach_wennKeineKarteEineHerkunftHat() {
    // Der haeufige Fall auf einem Board ohne Herkunftsdaten: kein einziger Zugriff auf den Port.
    // Ohne diese Zusage waere die Abkuerzung wirkungslos und niemand merkte es.
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                card(1L, 20L, 1, false, null, CardType.CARD, null, null),
                card(2L, 20L, 2, false, null, CardType.CARD, null, null)));

    List<CardIngestService.BoardItemView> items = service.listBoardItems(5L, BOARD);

    verify(cards, never()).findByIds(any());
    assertThat(items).extracting(CardIngestService.BoardItemView::derivedFrom).containsOnlyNulls();
  }

  @Test
  void listBoardItems_requiresMembershipInBoardProject() {
    when(cards.findByBoardId(BOARD)).thenReturn(List.of());

    service.listBoardItems(5L, BOARD);

    verify(permissions).requireMembership(5L, PROJECT);
  }

  @Test
  void listBoardItems_throwsBoardNotFound_whenBoardUnknown() {
    when(boardService.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    assertThatThrownBy(() -> service.listBoardItems(5L, BOARD))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void listBoardItems_skipsArchivedCards() {
    when(cards.findByBoardId(BOARD)).thenReturn(List.of(boardCard(1L, 20L, 1, 0, true)));

    assertThat(service.listBoardItems(5L, BOARD)).isEmpty();
  }

  @Test
  void listBoardItems_sortsByPositionInColumn() {
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                boardCard(1L, 20L, 1, 2, false),
                boardCard(2L, 20L, 2, 0, false),
                boardCard(3L, 20L, 3, 1, false)));

    assertThat(service.listBoardItems(5L, BOARD))
        .extracting(CardIngestService.BoardItemView::id)
        .containsExactly(2L, 3L, 1L);
  }

  @Test
  void listBoardItems_projectsCardFieldsIntoView() {
    when(cards.findByBoardId(BOARD)).thenReturn(List.of(boardCard(1L, 20L, 7, 3, false)));

    assertThat(service.listBoardItems(5L, BOARD))
        .singleElement()
        .extracting(
            CardIngestService.BoardItemView::id,
            CardIngestService.BoardItemView::number,
            CardIngestService.BoardItemView::title,
            CardIngestService.BoardItemView::description,
            CardIngestService.BoardItemView::columnId,
            CardIngestService.BoardItemView::positionInColumn,
            CardIngestService.BoardItemView::epic)
        .containsExactly(1L, 7, "Titel", "Body", 20L, 3, false);
  }

  @Test
  void listBoardItems_marksEpicsAsEpic() {
    // Epics gehoeren zur Item-Liste (anders als bei listByBoard) und muessen als solche erkennbar
    // sein, ohne den Kartentyp aus card.domain nach aussen zu geben.
    when(cards.findByBoardId(BOARD))
        .thenReturn(List.of(card(5L, 20L, 3, false, null, CardType.EPIC, null, "E")));

    assertThat(service.listBoardItems(5L, BOARD))
        .singleElement()
        .extracting(CardIngestService.BoardItemView::epic)
        .isEqualTo(true);
  }

  @Test
  void createDirect_createsBoardCardWithExternalKey() {
    // #535: direct-Ingest läuft über den normalen Anlege-Pfad und persistiert den Schlüssel.
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(9);

    CardIngestService.CardCreation result =
        service.createDirect(
            1L,
            BOARD,
            20L,
            new CardIngestService.DirectCard("Finding", null, "sonar:abc", null, null));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().externalKey()).isEqualTo("sonar:abc");
    assertThat(captor.getValue().boardId()).isEqualTo(BOARD);
    assertThat(result.created()).isTrue();
  }

  @Test
  void createDirect_withExistingExternalKey_returnsExistingWithoutCreating() {
    when(cards.findByProjectIdAndExternalKey(PROJECT, "sonar:abc"))
        .thenReturn(Optional.of(boardCard(7L, 20L, 3, 0, false)));

    CardIngestService.CardCreation result =
        service.createDirect(
            1L,
            BOARD,
            20L,
            new CardIngestService.DirectCard("Finding", null, "sonar:abc", null, null));

    assertThat(result.created()).isFalse();
    assertThat(result.view().id()).isEqualTo(7L);
    verify(cards, never()).save(any(Card.class));
  }

  @Test
  void replaceDependenciesFromIngest_storesUnknownNumbers() {
    // #566: Der Import darf auf Karten verweisen, die noch nicht angekommen sind — sonst waere die
    // Importreihenfolge bindend. Die DB traegt das (kein Fremdschluessel).
    when(cards.findById(7L)).thenReturn(Optional.of(boardCard(7L, 20L, 3, 0, false)));

    service.replaceDependenciesFromIngest(1L, 7L, PROJECT, List.of(99, 1234));

    verify(dependencies).replaceDependencies(7L, List.of(99, 1234));
  }

  @Test
  void replaceDependenciesFromIngest_rejectsSelfReference() {
    // Die Selbstverweis-Pruefung bleibt geteilt: sie haengt nicht am Wissen ueber andere Karten.
    when(cards.findById(7L)).thenReturn(Optional.of(boardCard(7L, 20L, 3, 0, false)));

    assertThatThrownBy(() -> service.replaceDependenciesFromIngest(1L, 7L, PROJECT, List.of(3)))
        .isInstanceOf(InvalidDependencyException.class);
    verify(dependencies, never()).replaceDependencies(anyLong(), anyList());
  }

  @Test
  void replaceDependenciesFromIngest_deduplicates() {
    when(cards.findById(7L)).thenReturn(Optional.of(boardCard(7L, 20L, 3, 0, false)));

    service.replaceDependenciesFromIngest(1L, 7L, PROJECT, List.of(99, 99, 100));

    verify(dependencies).replaceDependencies(7L, List.of(99, 100));
  }

  @Test
  void replaceDependenciesFromIngest_clearsOnEmptyList() {
    when(cards.findById(7L)).thenReturn(Optional.of(boardCard(7L, 20L, 3, 0, false)));

    service.replaceDependenciesFromIngest(1L, 7L, PROJECT, List.of());

    verify(dependencies).replaceDependencies(7L, List.of());
  }

  @Test
  void replaceDependenciesFromIngest_clearsOnNull() {
    when(cards.findById(7L)).thenReturn(Optional.of(boardCard(7L, 20L, 3, 0, false)));

    service.replaceDependenciesFromIngest(1L, 7L, PROJECT, null);

    verify(dependencies).replaceDependencies(7L, List.of());
  }

  @Test
  void replaceDependenciesFromIngest_rejectsCardOfOtherProject() {
    // Der Token bindet an ein Projekt; eine Karte aus einem fremden bleibt unerreichbar.
    when(cards.findById(7L)).thenReturn(Optional.of(boardCard(7L, 20L, 3, 0, false)));

    assertThatThrownBy(
            () -> service.replaceDependenciesFromIngest(1L, 7L, PROJECT + 1, List.of(99)))
        .isInstanceOf(CardNotFoundException.class);
    verify(dependencies, never()).replaceDependencies(anyLong(), anyList());
  }

  @Test
  void replaceDependenciesFromIngest_throwsForUnknownCard() {
    when(cards.findById(7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.replaceDependenciesFromIngest(1L, 7L, PROJECT, List.of(99)))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void createDirect_withGivenNumber_usesItInsteadOfAllocating() {
    // #565: Die vorgegebene Nummer ersetzt die Vergabe — die Identität der migrierten Karte
    // bleibt erhalten.
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    CardIngestService.CardCreation result =
        service.createDirect(
            1L,
            BOARD,
            20L,
            new CardIngestService.DirectCard("Migriert", null, "github#278", 278, null));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().number()).isEqualTo(278);
    assertThat(result.created()).isTrue();
    verify(cards, never()).allocateCardNumber(anyLong());
  }

  @Test
  void createDirect_withGivenNumber_holdsLockBeforeChecking() {
    // Die Sperre bleibt, obwohl die Berechnung entfällt: sonst kollidiert ein Import mit einer
    // laufenden Anlage genau dann, wenn beide dieselbe Zahl treffen.
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));

    service.createDirect(
        1L,
        BOARD,
        20L,
        new CardIngestService.DirectCard("Migriert", null, "github#278", 278, null));

    InOrder order = inOrder(cards);
    order.verify(cards).lockCardNumbers(PROJECT);
    order.verify(cards).hasCardWithoutExternalKey(PROJECT);
    order.verify(cards).isNumberTaken(PROJECT, 278);
    order.verify(cards).save(any(Card.class));
  }

  @Test
  void createDirect_withTakenNumber_throwsConflict() {
    when(cards.isNumberTaken(PROJECT, 278)).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.createDirect(
                    1L,
                    BOARD,
                    20L,
                    new CardIngestService.DirectCard("Migriert", null, "k", 278, null)))
        .isInstanceOf(CardNumberConflictException.class);
    verify(cards, never()).save(any(Card.class));
  }

  @Test
  void createDirect_withGivenNumber_throwsConflict_whenProjectHasImportForeignCard() {
    // Vorbedingung: in ein gewachsenes Projekt wird nicht hineinimportiert.
    when(cards.hasCardWithoutExternalKey(PROJECT)).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.createDirect(
                    1L,
                    BOARD,
                    20L,
                    new CardIngestService.DirectCard("Migriert", null, "k", 278, null)))
        .isInstanceOf(CardNumberConflictException.class);
    verify(cards, never()).save(any(Card.class));
  }

  @Test
  void createDirect_withoutGivenNumber_skipsPreconditionAndAllocates() {
    // Ohne vorgegebene Nummer bleibt der Pfad unverändert — kein Vorbedingungs-Check.
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(9);

    service.createDirect(
        1L, BOARD, 20L, new CardIngestService.DirectCard("Karte", null, null, null, null));

    verify(cards).allocateCardNumber(PROJECT);
    verify(cards, never()).hasCardWithoutExternalKey(anyLong());
    verify(cards, never()).isNumberTaken(anyLong(), anyInt());
  }

  @Test
  void createDirect_existingKeyWithDifferentNumber_throwsConflict() {
    // Der Idempotenz-Treffer darf keine andere Identität zurückgeben als angefordert.
    when(cards.findByProjectIdAndExternalKey(PROJECT, "github#278"))
        .thenReturn(Optional.of(boardCard(7L, 20L, 3, 0, false)));

    assertThatThrownBy(
            () ->
                service.createDirect(
                    1L,
                    BOARD,
                    20L,
                    new CardIngestService.DirectCard("Migriert", null, "github#278", 278, null)))
        .isInstanceOf(CardNumberConflictException.class);
    verify(cards, never()).save(any(Card.class));
  }

  @Test
  void createDirect_existingKeyWithSameNumber_returnsExistingWithoutCreating() {
    when(cards.findByProjectIdAndExternalKey(PROJECT, "github#3"))
        .thenReturn(Optional.of(boardCard(7L, 20L, 3, 0, false)));

    CardIngestService.CardCreation result =
        service.createDirect(
            1L,
            BOARD,
            20L,
            new CardIngestService.DirectCard("Migriert", null, "github#3", 3, null));

    assertThat(result.created()).isFalse();
    assertThat(result.view().id()).isEqualTo(7L);
    verify(cards, never()).save(any(Card.class));
  }

  @Test
  void createDirect_withoutExternalKey_skipsLookup() {
    when(boardService.requireColumn(20L, BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(9);

    CardIngestService.CardCreation result =
        service.createDirect(
            1L, BOARD, 20L, new CardIngestService.DirectCard("Karte", null, null, null, null));

    assertThat(result.created()).isTrue();
    verify(cards, never()).findByProjectIdAndExternalKey(anyLong(), any());
  }

  @Test
  void createDirect_checksPermissionBeforeDuplicateLookup() {
    // Rechte vor dem Duplikat-Check: kein Existenz-Leak an Unberechtigte.
    doThrow(new ProjectNotFoundException())
        .when(permissions)
        .require(1L, PROJECT, Permission.TICKET_CREATE);

    assertThatThrownBy(
            () ->
                service.createDirect(
                    1L,
                    BOARD,
                    20L,
                    new CardIngestService.DirectCard("F", null, "sonar:abc", null, null)))
        .isInstanceOf(ProjectNotFoundException.class);
    verify(cards, never()).findByProjectIdAndExternalKey(anyLong(), any());
  }

  @Test
  void listBoardItems_liefertNull_wennDerVorfahrNichtInDerSammelantwortSteht() {
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null).withDerivedFrom(91L)));
    when(cards.findByIds(any())).thenReturn(List.of());

    assertThat(service.listBoardItems(5L, BOARD))
        .singleElement()
        .extracting(CardIngestService.BoardItemView::derivedFrom)
        .isNull();
  }

  @Test
  void requireOnBoard_passes_whenCardIsOnBoard() {
    when(cards.findById(1L)).thenReturn(Optional.of(boardCard(1L, 20L, 1, 0, false)));

    assertThatCode(() -> service.requireOnBoard(1L, BOARD)).doesNotThrowAnyException();
  }

  @Test
  void requireOnBoard_throwsCardNotFound_whenCardUnknown() {
    when(cards.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireOnBoard(1L, BOARD))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void requireOnBoard_throwsCardNotFound_whenCardOnOtherBoard() {
    Card otherBoard =
        new Card(
            1L,
            99L,
            20L,
            1,
            "T",
            null,
            0,
            false,
            null,
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
            null);
    when(cards.findById(1L)).thenReturn(Optional.of(otherBoard));

    assertThatThrownBy(() -> service.requireOnBoard(1L, BOARD))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void requireOnBoard_comparesBoardIdsByValue_beyondLongCache() {
    // Board-IDs jenseits des Long-Caches (> 127): ein Referenzvergleich ('!=') wuerde hier
    // faelschlich CardNotFound werfen und den kanbancompat-Zugriff auf grossen Boards zerlegen.
    long largeBoard = 5000L;
    Card onLargeBoard =
        new Card(
            1L,
            largeBoard,
            20L,
            1,
            "T",
            null,
            0,
            false,
            null,
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
            null);
    when(cards.findById(1L)).thenReturn(Optional.of(onLargeBoard));

    assertThatCode(() -> service.requireOnBoard(1L, largeBoard)).doesNotThrowAnyException();
  }

  @Test
  void listBoardItems_traegtDenStatusAlsText() {
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                paket(1L, "A", CardType.CARD, CardStatus.IN_PROGRESS),
                paket(2L, "Vorhaben", CardType.EPIC, null)));

    assertThat(service.listBoardItems(1L, BOARD))
        .extracting(CardIngestService.BoardItemView::status)
        .containsExactly("IN_PROGRESS", null);
  }
}
