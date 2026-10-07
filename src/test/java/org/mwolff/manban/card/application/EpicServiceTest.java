package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.board.application.ColumnNotFoundException;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Verhaltenstests von Vorhaben und Herkunft (Mockito an den Ports). Die Testmethoden stammen
 * unverändert aus {@code CardServiceTest} und {@code CardServiceCreateBatchTest} (Issue #1393, Plan
 * #1387 E6); {@link KartenGrundlage}, {@link KartenSicht} und {@link KartenAbhaengigkeiten}
 * entstehen echt aus denselben Port-Mocks.
 */
// PMD.TooManyMethods und PMD.CouplingBetweenObjects: wandern mit den Testmethoden aus
// CardServiceTest (Issue #1393) — je Use-Case von Vorhaben und Herkunft Erfolgs- und Fehlerpfade,
// gebaut aus den Ports, die KartenGrundlage und KartenSicht echt brauchen (Plan #1387, E6). Ein
// Zerschneiden nach Methodenzahl verstreute die Use-Cases über Dateien, ohne etwas zu entkoppeln.
@SuppressWarnings({"PMD.TooManyMethods", "PMD.CouplingBetweenObjects"})
class EpicServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;

  private CardRepository cards;
  private CardDependencyRepository dependencies;
  private BoardService boardService;
  private PermissionChecker permissions;
  private CardColumnTransitionRepository transitions;
  private CardAssigneeRepository assignees;
  private LabelRepository labels;
  private CardLabelRepository cardLabels;
  private CardActivityRepository activity;
  private ActorContext actor;
  private ApplicationEventPublisher events;
  private EpicService service;

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
    transitions = mock(CardColumnTransitionRepository.class);
    assignees = mock(CardAssigneeRepository.class);
    labels = mock(LabelRepository.class);
    cardLabels = mock(CardLabelRepository.class);
    activity = mock(CardActivityRepository.class);
    actor = mock(ActorContext.class);
    when(actor.current()).thenReturn(ActorContext.ActorStamp.unknown());
    events = mock(ApplicationEventPublisher.class);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service =
        CardServiceAufbau.epicAusPorts(
            cards,
            dependencies,
            boardService,
            permissions,
            transitions,
            // Echte KartenZuordnung aus denselben Port-Mocks (Issue #1051): Die Prüfungen für
            // Zuständige und Labels sind nur aus dem Service herausgezogen, nicht ersetzt — ein
            // Mock der Zuordnung ließe die Tests zu InvalidAssigneeException/InvalidLabelException
            // ins Leere laufen.
            new KartenZuordnung(assignees, labels, cardLabels, permissions),
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

  @Test
  void createEpic_savesEpicType() {
    // Given
    when(boardService.firstColumn(BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.createEpic(1L, BOARD, "Epic", null, "SHC");

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().type()).isEqualTo(CardType.EPIC);
  }

  @Test
  void createEpic_trimsBlankShortcodeToNull() {
    // Given
    when(boardService.firstColumn(BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(1);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.createEpic(1L, BOARD, "Epic", null, "   ");

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().shortcode()).isNull();
  }

  @Test
  void createEpic_throwsBoardNotFound_whenBoardUnknown() {
    // Given
    when(boardService.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.createEpic(1L, BOARD, "Epic", null, null))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void createEpic_throwsColumnNotFound_whenBoardHasNoColumns() {
    // Given
    when(boardService.firstColumn(BOARD)).thenThrow(new ColumnNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.createEpic(1L, BOARD, "Epic", null, null))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  @Test
  void listEpics_countsDoneChildren() {
    // Given
    when(boardService.listColumns(BOARD))
        .thenReturn(List.of(column(20L, "Backlog", 0), column(21L, "Done", 1)));
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                card(5L, 20L, 1, false, null, CardType.EPIC, null, "E"),
                card(6L, 21L, 2, false, null, CardType.CARD, 5L, null),
                card(7L, 20L, 3, false, null, CardType.CARD, 5L, null)));

    // When
    List<EpicService.EpicView> result = service.listEpics(1L, BOARD);

    // Then
    assertThat(result)
        .singleElement()
        .extracting(EpicService.EpicView::done, EpicService.EpicView::total)
        .containsExactly(1, 2);
  }

  @Test
  void listEpics_zaehltNachfahrenDerZugeordnetenKarteMit() {
    // Given: Kette Wurzel -> Kind -> Enkel, nur die Wurzel ist dem Vorhaben zugeordnet.
    when(boardService.listColumns(BOARD))
        .thenReturn(List.of(column(20L, "Backlog", 0), column(21L, "Done", 1)));
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                kette(5L, 20L, 1, CardType.EPIC, null, null),
                kette(6L, 20L, 2, CardType.CARD, 5L, null),
                kette(7L, 21L, 3, CardType.CARD, null, 6L),
                kette(8L, 20L, 4, CardType.CARD, null, 7L),
                kette(9L, 20L, 5, CardType.CARD, null, null)));

    // When
    List<EpicService.EpicView> result = service.listEpics(1L, BOARD);

    // Then: drei statt einer Karte, die unbeteiligte Nummer 5 bleibt draußen.
    assertThat(result)
        .singleElement()
        .extracting(EpicService.EpicView::done, EpicService.EpicView::total)
        .containsExactly(1, 3);
    assertThat(result.getFirst().memberNumbers()).containsExactly(2, 3, 4);
    assertThat(result.getFirst().memberNumbers()).doesNotContain(5);
  }

  @Test
  void listEpics_liefertWurzelnAlsTeilmengeDerMitglieder() {
    // Given
    when(boardService.listColumns(BOARD)).thenReturn(List.of(column(20L, "Backlog", 0)));
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                kette(5L, 20L, 1, CardType.EPIC, null, null),
                kette(6L, 20L, 2, CardType.CARD, 5L, null),
                kette(7L, 20L, 3, CardType.CARD, null, 6L)));

    // When
    EpicService.EpicView view = service.listEpics(1L, BOARD).getFirst();

    // Then: Invariante — Wurzeln ⊆ Mitglieder, und total zählt die Mitglieder.
    assertThat(view.rootNumbers()).containsExactly(2);
    assertThat(view.memberNumbers()).containsExactly(2, 3);
    assertThat(view.memberNumbers()).containsAll(view.rootNumbers());
    assertThat(view.total()).isEqualTo(view.memberNumbers().size());
  }

  @Test
  void listEpics_throwsBoardNotFound_whenBoardUnknown() {
    // Given
    when(boardService.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.listEpics(1L, BOARD))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void assignParent_setsParent() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(cards.findById(30L))
        .thenReturn(Optional.of(card(30L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.assignParent(1L, 1L, 30L);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().parentId()).isEqualTo(30L);
  }

  @Test
  void assignParent_throwsInvalidDependency_whenCardIsEpic() {
    // Given
    when(cards.findById(5L))
        .thenReturn(Optional.of(card(5L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    // When / Then
    assertThatThrownBy(() -> service.assignParent(1L, 5L, 30L))
        .isInstanceOf(InvalidDependencyException.class);
  }

  @Test
  void listEpics_ignoresArchivedChildrenAndForeignChildren() {
    // Given: ein Epic mit einem gezählten Kind, einem archivierten Kind und einem fremden Kind
    when(boardService.listColumns(BOARD))
        .thenReturn(List.of(column(20L, "Backlog", 0), column(21L, "Done", 1)));
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                card(5L, 20L, 1, false, null, CardType.EPIC, null, "E"),
                card(6L, 20L, 2, false, null, CardType.CARD, 5L, null),
                card(7L, 20L, 3, true, null, CardType.CARD, 5L, null),
                card(8L, 20L, 4, false, null, CardType.CARD, 99L, null)));

    // When
    List<EpicService.EpicView> result = service.listEpics(1L, BOARD);

    // Then: nur das nicht-archivierte, zugehörige Kind zählt
    assertThat(result).singleElement().extracting(EpicService.EpicView::total).isEqualTo(1);
  }

  @Test
  void assignParent_clearsParent_whenParentNull() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, 30L, null)));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.assignParent(1L, 1L, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().parentId()).isNull();
  }

  @Test
  void listEpics_laesstArchivierteVorhabenAus() {
    // Given: ein aktives und ein archiviertes Vorhaben (Issue #1494, AK 4/AK 5)
    when(boardService.listColumns(BOARD)).thenReturn(List.of(column(20L, "Backlog", 0)));
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                card(5L, 20L, 1, false, null, CardType.EPIC, null, "A"),
                card(6L, 20L, 2, true, null, CardType.EPIC, null, "B")));

    // When
    List<EpicService.EpicView> result = service.listEpics(1L, BOARD);

    // Then
    assertThat(result).extracting(EpicService.EpicView::id).containsExactly(5L);
  }

  @Test
  void assignParent_archiviertesVorhaben_wirdAbgewiesen() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(cards.findById(30L))
        .thenReturn(Optional.of(card(30L, 20L, 5, true, null, CardType.EPIC, null, "E")));

    // When / Then
    assertThatThrownBy(() -> service.assignParent(1L, 1L, 30L))
        .isExactlyInstanceOf(InvalidDependencyException.class)
        .hasMessage("Das Vorhaben ist archiviert: 30");
    verify(cards, never()).save(any());
  }

  @Test
  void assignParent_unveraenderteZuordnungZuArchiviertemVorhaben_bleibtErlaubt() {
    // Given: Die Karte gehört schon zum archivierten Vorhaben 30 (Plan #1504, E4).
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, true, null, CardType.CARD, 30L, null)));
    when(cards.findById(30L))
        .thenReturn(Optional.of(card(30L, 20L, 5, true, null, CardType.EPIC, null, "E")));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.assignParent(1L, 1L, 30L);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().parentId()).isEqualTo(30L);
  }

  @Test
  void createEpic_allowsNullShortcode() {
    // Given
    when(boardService.firstColumn(BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.createEpic(1L, BOARD, "Epic", null, null);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().shortcode()).isNull();
  }

  @Test
  void createEpic_assignsNextBoardNumber() {
    // Given
    when(boardService.firstColumn(BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(5);

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.createEpic(1L, BOARD, "Epic", null, "SHC");

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().number()).isEqualTo(5);
  }

  @Test
  void createEpic_returnsViewOfPersistedEpic() {
    // Given
    when(boardService.firstColumn(BOARD)).thenReturn(column(20L, "Backlog", 0));

    // When
    CardView view = service.createEpic(1L, BOARD, "Epic", null, "SHC");

    // Then
    assertThat(view.title()).isEqualTo("Epic");
  }

  @Test
  void assignParent_returnsViewWithParent() {
    // Given
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));
    when(cards.findById(30L))
        .thenReturn(Optional.of(card(30L, 20L, 5, false, null, CardType.EPIC, null, "E")));

    // When
    CardView view = service.assignParent(1L, 1L, 30L);

    // Then
    assertThat(view.parentId()).isEqualTo(30L);
  }

  /**
   * Beleg fuer das mitgegebene {@code selfCardId}: Beim Aendern kennt die Aufloesung die eigene
   * Karte nur, wenn der Aufrufer ihre ID durchreicht. Ohne sie liefe der Selbstbezug durch, und
   * eine Karte koennte ihr eigener Vorfahr werden.
   *
   * <p>Ein Integrationstest allein genuegt hier nicht: PIT misst nur Unit-Tests (Befund aus Issue
   * #605), eine ausschliesslich per IT belegte Stelle hinterlaesst also eine Mutationsluecke.
   */
  @Test
  void assignDerivedFrom_selbstbezug_wirdAbgelehnt() {
    Card selbst = card(1L, 20L, 7, false, null, CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(selbst));
    when(cards.findByProjectIdAndNumber(PROJECT, 7)).thenReturn(Optional.of(selbst));

    assertThatThrownBy(() -> service.assignDerivedFrom(1L, 1L, 7))
        .isInstanceOf(InvalidDependencyException.class);

    verify(cards, never()).save(any());
  }

  /**
   * Gegenpol zum Selbstbezug: Eine fremde Nummer wird aufgeloest und gespeichert. Ohne diesen Test
   * ueberlebt eine Mutation, die {@code selfCardId} als "immer gleich" behandelt — sie waere durch
   * den Ablehnungstest allein nicht zu toeten.
   */
  @Test
  void assignDerivedFrom_fremdeNummer_wirdAufgeloestUndGespeichert() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 7, false, null, CardType.CARD, null, null)));
    when(cards.findByProjectIdAndNumber(PROJECT, 4))
        .thenReturn(Optional.of(card(9L, 20L, 4, false, null, CardType.CARD, null, null)));
    // `save` gibt hier die gespeicherte Karte zurueck, damit die Sicht darauf gebaut werden kann.
    // Die uebrigen Tests dieser Klasse pruefen nur den Captor und brauchen das nicht.
    when(cards.save(any())).thenAnswer(inv -> inv.getArgument(0));
    // Die Sicht loest die Herkunft ueber die ID zur Nummer auf (Issue #605).
    when(cards.findById(9L))
        .thenReturn(Optional.of(card(9L, 20L, 4, false, null, CardType.CARD, null, null)));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    CardView result = service.assignDerivedFrom(1L, 1L, 4);

    verify(cards).save(captor.capture());
    assertThat(captor.getValue().derivedFromCardId()).isEqualTo(9L);
    // Die Sicht wird zurueckgegeben, nicht nur gespeichert: Der Aufrufer zeigt sie unmittelbar an.
    assertThat(result).isNotNull();
    assertThat(result.derivedFrom()).isEqualTo(4);
    // Offene Boards ziehen ueber SSE nach — ohne das Ereignis sieht ein zweiter Betrachter die
    // geaenderte Herkunft erst nach dem naechsten Laden.
    verify(events).publishEvent(new CardBoardActivityEvent(BOARD, ActivityType.UPDATED, 1L));
  }

  /** {@code null} loescht die Herkunft — das Feld muss sich auch wieder leeren lassen. */
  @Test
  void assignDerivedFrom_null_loeschtDieHerkunft() {
    when(cards.findById(1L))
        .thenReturn(
            Optional.of(
                withHerkunft(card(1L, 20L, 7, false, null, CardType.CARD, null, null), 9L)));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.assignDerivedFrom(1L, 1L, null);

    verify(cards).save(captor.capture());
    assertThat(captor.getValue().derivedFromCardId()).isNull();
  }

  /**
   * Der Ablauf in einem Zug: Vorhaben entsteht, traegt die Quellkarte als Anforderung, und die
   * Quellkarte traegt das Vorhaben als {@code parentId}. Alle drei Wirkungen zusammen — eine
   * Fassung, die nur zwei davon herstellt, laesst genau den halben Zustand zurueck, den die
   * Anforderung abschaffen soll.
   */
  @Test
  void openEpicFromCard_legtVorhabenAn_setztAnforderungUndZuordnung() {
    Card quelle = card(1L, 20L, 7, false, null, CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(quelle));
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(boardService.firstColumn(BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(8);
    when(cards.findByProjectIdAndNumber(PROJECT, 7)).thenReturn(Optional.of(quelle));
    Card angelegtesEpic = card(50L, 20L, 8, false, null, CardType.EPIC, null, null);
    // Das Anlegen liefert die Karte MIT vergebener ID zurueck — ohne sie scheitert `requireId`
    // im weiteren Ablauf, so wie es auch in der Datenbank waere.
    when(cards.save(any()))
        .thenAnswer(
            inv -> {
              Card c = inv.getArgument(0);
              return c.id() == null ? angelegtesEpic : c;
            });
    when(cards.findById(50L)).thenReturn(Optional.of(angelegtesEpic));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    CardView ergebnis = service.openEpicFromCard(1L, 1L, "EP-1", "Vorhaben");

    verify(cards, times(3)).save(captor.capture());
    // 1. Das Vorhaben entsteht — auf dem Board der Quellkarte, ohne Beschreibung.
    Card epic =
        captor.getAllValues().stream()
            .filter(c -> c.type() == CardType.EPIC)
            .findFirst()
            .orElseThrow();
    assertThat(epic.boardId()).isEqualTo(BOARD);
    assertThat(epic.description()).isNull();
    assertThat(epic.shortcode()).isEqualTo("EP-1");
    // 2. Die Quellkarte wird seine Anforderung.
    assertThat(
            captor.getAllValues().stream()
                .filter(c -> c.requirementCardId() != null)
                .map(Card::requirementCardId))
        .containsExactly(1L);
    // 3. Und ist ihm zugeordnet.
    // `id()` statt `requireId()`: Im Captor liegt auch die frisch angelegte Karte ohne ID —
    // `requireId` wuerde an ihr scheitern, bevor der Filter greift.
    assertThat(
            captor.getAllValues().stream()
                .filter(c -> c.id() != null && c.id().longValue() == 1L)
                .map(Card::parentId))
        .containsExactly(50L);
    // Zurueck kommt die Sicht des neuen Vorhabens, nicht die der Quellkarte.
    assertThat(ergebnis.id()).isEqualTo(50L);
  }

  /** Das Kuerzel ist optional — wie bei {@code createEpic}. */
  @Test
  void openEpicFromCard_ohneKuerzel_istZulaessig() {
    Card quelle = card(1L, 20L, 7, false, null, CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(quelle));
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(boardService.firstColumn(BOARD)).thenReturn(column(20L, "Backlog", 0));
    when(cards.allocateCardNumber(PROJECT)).thenReturn(8);
    when(cards.findByProjectIdAndNumber(PROJECT, 7)).thenReturn(Optional.of(quelle));
    Card angelegtesEpic = card(50L, 20L, 8, false, null, CardType.EPIC, null, null);
    when(cards.save(any()))
        .thenAnswer(
            inv -> {
              Card c = inv.getArgument(0);
              return c.id() == null ? angelegtesEpic : c;
            });
    when(cards.findById(50L)).thenReturn(Optional.of(angelegtesEpic));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.openEpicFromCard(1L, 1L, null, "Vorhaben");

    verify(cards, times(3)).save(captor.capture());
    assertThat(captor.getAllValues())
        .filteredOn(c -> c.type() == CardType.EPIC)
        .allSatisfy(c -> assertThat(c.shortcode()).isNull());
  }

  /**
   * Jeder Ablehnungstest isoliert <b>genau eine</b> Bedingung: Die uebrigen sind so gestubbt, dass
   * sie nicht greifen, und die Meldung wird festgenagelt. Ohne diese Schaerfe blieben Fassungen
   * gruen, in denen die geprueft geglaubte Bedingung fehlt und eine andere die Ablehnung traegt —
   * genau das haben die PIT-Ueberlebenden dieses Pakets gezeigt.
   */
  @Test
  void openEpicFromCard_quelleIstVorhaben_wirdAbgelehnt() {
    Card quelle = card(1L, 20L, 7, false, null, CardType.EPIC, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(quelle));
    when(cards.findByProjectIdAndNumber(PROJECT, 7)).thenReturn(Optional.of(quelle));

    assertThatThrownBy(() -> service.openEpicFromCard(1L, 1L, null, "V"))
        .isExactlyInstanceOf(InvalidDependencyException.class)
        .hasMessageContaining("aus sich selbst");

    verify(cards, never()).save(any());
  }

  @Test
  void openEpicFromCard_archivierteQuelle_wirdAbgelehnt() {
    Card quelle = card(1L, 20L, 7, true, null, CardType.CARD, null, null);
    when(cards.findById(1L)).thenReturn(Optional.of(quelle));
    when(cards.findByProjectIdAndNumber(PROJECT, 7)).thenReturn(Optional.of(quelle));

    assertThatThrownBy(() -> service.openEpicFromCard(1L, 1L, null, "V"))
        .isExactlyInstanceOf(InvalidDependencyException.class)
        .hasMessageContaining("ruhende Karte");

    verify(cards, never()).save(any());
  }

  /**
   * Der Papierkorb ist im Domain-Record nicht abgebildet: {@code findById} liefert geloeschte
   * Karten, die Nummernsuche filtert sie. Findet die Suche nichts, liegt die Karte im Papierkorb.
   */
  @Test
  void openEpicFromCard_quelleImPapierkorb_wirdAbgelehnt() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 7, false, null, CardType.CARD, null, null)));
    when(cards.findByProjectIdAndNumber(PROJECT, 7)).thenReturn(Optional.empty());

    // `isExactlyInstanceOf`: Faellt die Papierkorb-Pruefung weg, laeuft der Ablauf weiter und
    // scheitert spaeter an derselben leeren Nummernsuche — dann aber mit der erbenden
    // InvalidRequirementCardException. Ein Test auf die Oberklasse bliebe dabei gruen.
    assertThatThrownBy(() -> service.openEpicFromCard(1L, 1L, null, "V"))
        .isExactlyInstanceOf(InvalidDependencyException.class)
        .hasMessageContaining("ruhende Karte");

    verify(cards, never()).save(any());
  }

  @Test
  void openEpicFromCard_quelleBereitsZugeordnet_wirdAbgelehnt() {
    Card quelle = card(1L, 20L, 7, false, null, CardType.CARD, 42L, null);
    when(cards.findById(1L)).thenReturn(Optional.of(quelle));
    when(cards.findByProjectIdAndNumber(PROJECT, 7)).thenReturn(Optional.of(quelle));

    assertThatThrownBy(() -> service.openEpicFromCard(1L, 1L, null, "V"))
        .isExactlyInstanceOf(InvalidDependencyException.class)
        .hasMessageContaining("bereits einem Vorhaben zugeordnet");

    verify(cards, never()).save(any());
  }

  @Test
  void openEpicFromCard_unbekannteKarte_wirdAbgelehnt() {
    when(cards.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.openEpicFromCard(1L, 1L, null, "V"))
        .isInstanceOf(CardNotFoundException.class);
  }

  /**
   * Der gueltige Fall: Uebergeben wird die Nummer, gespeichert die ID. Ohne diesen Test ueberlebt
   * eine Mutation, die die Nummer unaufgeloest durchreicht — sie waere durch die Ablehnungstests
   * allein nicht zu toeten.
   */
  @Test
  void assignRequirement_nummerWirdZurIdAufgeloestUndGespeichert() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 7, false, null, CardType.EPIC, null, null)));
    when(cards.findByProjectIdAndNumber(PROJECT, 4))
        .thenReturn(Optional.of(card(9L, 20L, 4, false, null, CardType.CARD, null, null)));
    when(cards.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    CardView result = service.assignRequirement(1L, 1L, 4);

    verify(cards).save(captor.capture());
    assertThat(captor.getValue().requirementCardId()).isEqualTo(9L);
    // Die Sicht wird zurueckgegeben, nicht nur gespeichert: Der Aufrufer zeigt sie unmittelbar an.
    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo(1L);
    // Offene Boards ziehen ueber SSE nach, wie beim Setzen der Herkunft.
    verify(events).publishEvent(new CardBoardActivityEvent(BOARD, ActivityType.UPDATED, 1L));
  }

  /** {@code null} loescht die Zuordnung — ein Vorhaben ohne Anforderung ist gueltig (#636). */
  @Test
  void assignRequirement_null_loeschtDieZuordnung() {
    when(cards.findById(1L))
        .thenReturn(
            Optional.of(
                card(1L, 20L, 7, false, null, CardType.EPIC, null, null).withRequirement(9L)));
    when(cards.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.assignRequirement(1L, 1L, null);

    verify(cards).save(captor.capture());
    assertThat(captor.getValue().requirementCardId()).isNull();
  }

  @Test
  void listEpics_mitAnforderung_liefertDerenNummer() {
    when(boardService.listColumns(BOARD)).thenReturn(List.of(column(20L, "Backlog", 0)));
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                kette(5L, 20L, 1, CardType.EPIC, null, null).withRequirement(6L),
                kette(6L, 20L, 2, CardType.CARD, 5L, null)));

    EpicService.EpicView view = service.listEpics(1L, BOARD).getFirst();

    assertThat(view.requirementCardNumber()).isEqualTo(2);
  }

  /**
   * Gegenstueck: ohne Anforderung {@code null} — nicht 0 und kein Platzhalter. 0 waere eine
   * gueltige Kartennummer, und die Kachel unterscheidet "keine Anforderung" von "Anforderung Nummer
   * 0" nur ueber diesen Wert.
   */
  @Test
  void listEpics_ohneAnforderung_liefertNull() {
    when(boardService.listColumns(BOARD)).thenReturn(List.of(column(20L, "Backlog", 0)));
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                kette(5L, 20L, 1, CardType.EPIC, null, null),
                kette(6L, 20L, 2, CardType.CARD, 5L, null)));

    EpicService.EpicView view = service.listEpics(1L, BOARD).getFirst();

    assertThat(view.requirementCardNumber()).isNull();
  }

  /**
   * Zeigt der Verweis auf eine Karte, die nicht mehr auf dem Board liegt (Papierkorb), ist {@code
   * null} die ehrliche Antwort. Eine Fassung, die hier die ID als Nummer ausgibt, waere gruen,
   * solange beide zufaellig gleich sind — hier sind sie es bewusst nicht.
   */
  @Test
  void listEpics_anforderungNichtMehrAufDemBoard_liefertNull() {
    when(boardService.listColumns(BOARD)).thenReturn(List.of(column(20L, "Backlog", 0)));
    when(cards.findByBoardId(BOARD))
        .thenReturn(List.of(kette(5L, 20L, 1, CardType.EPIC, null, null).withRequirement(99L)));

    EpicService.EpicView view = service.listEpics(1L, BOARD).getFirst();

    assertThat(view.requirementCardNumber()).isNull();
  }

  @Test
  void createEpic_publishesCreatedEvent() {
    when(boardService.firstColumn(BOARD)).thenReturn(column(20L, "Backlog", 0));

    service.createEpic(1L, BOARD, "Epic", null, "E");

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.CREATED, 1L));
  }

  @Test
  void assignParent_publishesUpdatedEvent() {
    when(cards.findById(1L))
        .thenReturn(Optional.of(card(1L, 20L, 1, false, null, CardType.CARD, null, null)));

    service.assignParent(1L, 1L, null);

    assertThat(onlyPublishedEvent())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.UPDATED, 1L));
  }

  /** Karte mit Herkunft — für die Zugehörigkeit über die Kette (Issue #633). */
  private static Card kette(
      long id, long columnId, int number, CardType type, Long parentId, Long derivedFrom) {
    return new Card(
        id,
        BOARD,
        columnId,
        number,
        "Titel",
        null,
        0,
        false,
        null,
        1L,
        FIXED,
        FIXED,
        type,
        parentId,
        null,
        null,
        PROJECT,
        null,
        derivedFrom,
        null,
        null);
  }

  /**
   * Setzt die Herkunft auf einer Testkarte — der Record-Wither bleibt die einzige Schreibstelle.
   */
  private static Card withHerkunft(Card c, long herkunft) {
    return c.withDerivedFrom(herkunft);
  }

  private CardBoardActivityEvent onlyPublishedEvent() {
    ArgumentCaptor<CardBoardActivityEvent> captor =
        ArgumentCaptor.forClass(CardBoardActivityEvent.class);
    verify(events).publishEvent(captor.capture());
    return captor.getValue();
  }
}
