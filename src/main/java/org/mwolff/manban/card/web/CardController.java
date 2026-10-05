package org.mwolff.manban.card.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.ColumnNotFoundException;
import org.mwolff.manban.card.application.CardNumbers;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.CardView;
import org.mwolff.manban.card.application.EpicService;
import org.mwolff.manban.card.application.InvalidDerivedFromException;
import org.mwolff.manban.card.application.LabelAction;
import org.mwolff.manban.card.application.SortDirection;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.common.TextLimits;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Karten- und Vorhaben-Verwaltung eines Boards (Anlegen, Bearbeiten, Zuordnen). Archiv und
 * Papierkorb liegen seit Issue #1394 im {@link CardArchiveController}.
 */
// PMD.CouplingBetweenObjects: eingehender Adapter der gesamten Karten-/Vorhaben-API. Die Kopplung
// zählt im Wesentlichen die Request-/Response-Records der einzelnen Endpunkte plus die
// Application-Typen, an die delegiert wird — jeder Endpunkt bringt sie zwangsläufig mit. Eine
// Aufteilung würde eine zusammengehörige HTTP-Oberfläche über mehrere Controller zerreißen, ohne
// dass ein einziger Endpunkt einfacher würde.
@SuppressWarnings("PMD.CouplingBetweenObjects")
@RestController
class CardController {

  /**
   * Obergrenze für ein Stapel-Anlegen an einer Board-Spalte. Dieselbe Zahl wie an den bestehenden
   * Bulk-Endpunkten dieser Klasse, damit im Projekt genau eine Mengen-Obergrenze gilt statt
   * mehrerer divergierender.
   */
  static final int MAX_CARDS_PER_BATCH = 200;

  private final CardService cards;
  private final EpicService epics;

  CardController(CardService cards, EpicService epics) {
    this.cards = cards;
    this.epics = epics;
  }

  @PostMapping("/api/boards/{boardId}/cards")
  @ResponseStatus(HttpStatus.CREATED)
  CardView create(
      @AuthenticationPrincipal Long userId,
      @PathVariable long boardId,
      @Valid @RequestBody CreateCardRequest request) {
    // Getter je einmal in eine lokale Variable ziehen: der Null-Check verengt dann den (nun
    // @Nullable) Typ, statt bei einem zweiten Aufruf erneut als potenziell null zu gelten.
    CardType requestedType = request.type();
    CardType type = requestedType == null ? CardType.CARD : requestedType;
    if (type == CardType.EPIC) {
      // Vorhaben tragen keine Herkunft: `parentId` (Mitgliedschaft) und `derivedFrom` (Abstammung)
      // sind zwei getrennte Relationen (Plandokument #606, E9), und ein Vorhaben mit Herkunft
      // haette im Herkunftsbaum kein definiertes Verhalten. Ohne diese Ablehnung wuerde
      // `createEpic` das Feld still verschlucken.
      if (request.derivedFrom() != null) {
        throw new InvalidDerivedFromException("Ein Vorhaben kann keine Herkunft tragen");
      }
      return epics.createEpic(
          userId, boardId, request.title(), request.description(), request.shortcode());
    }
    Long columnId = request.columnId();
    if (columnId == null) {
      throw new ColumnNotFoundException();
    }
    return cards.create(
        userId,
        boardId,
        columnId,
        request.title(),
        request.description(),
        request.dependencies(),
        request.parentId(),
        request.dueDate(),
        request.assigneeIds(),
        request.labelIds(),
        request.derivedFrom());
  }

  /**
   * Legt mehrere Karten in einem Zug am Ende einer Spalte dieses Boards an (Issue #1200) — der
   * board-gebundene Weg des Spezifikations-Imports. Antwort: die angelegten Karten in
   * Eingabereihenfolge, jeweils mit {@code id} und vergebener {@code number}.
   *
   * <p>Alles-oder-nichts: Verletzt ein Element die Feldgrenzen, lehnt die Bean-Validation die ganze
   * Anfrage mit 400 ab, bevor der Service läuft — es entsteht keine einzige Karte. Begründung der
   * Entscheidung im Javadoc von {@link CardService#createCardsBatch}.
   */
  @PostMapping("/api/boards/{boardId}/cards/batch")
  @ResponseStatus(HttpStatus.CREATED)
  List<CardView> createBatch(
      @AuthenticationPrincipal Long userId,
      @PathVariable long boardId,
      @Valid @RequestBody CreateCardsBatchRequest request) {
    List<CardService.NewCard> neueKarten =
        request.cards().stream()
            .map(c -> new CardService.NewCard(c.title(), c.description()))
            .toList();
    return cards.createCardsBatch(userId, boardId, request.columnId(), neueKarten);
  }

  @GetMapping("/api/boards/{boardId}/cards")
  List<CardView> list(@AuthenticationPrincipal Long userId, @PathVariable long boardId) {
    return cards.listByBoard(userId, boardId);
  }

  @GetMapping("/api/cards/{cardId}")
  CardView get(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
    return cards.getCard(userId, cardId);
  }

  @PatchMapping("/api/cards/{cardId}")
  CardView update(
      @AuthenticationPrincipal Long userId,
      @PathVariable long cardId,
      @Valid @RequestBody UpdateCardRequest request) {
    return cards.update(
        userId,
        cardId,
        request.title(),
        request.description(),
        request.dependencies(),
        request.shortcode(),
        request.parentId(),
        request.dueDate());
  }

  @PostMapping("/api/cards/{cardId}/move")
  CardView move(
      @AuthenticationPrincipal Long userId,
      @PathVariable long cardId,
      @Valid @RequestBody MoveCardRequest request) {
    return cards.move(userId, cardId, request.columnId(), request.position());
  }

  /**
   * Setzt den Status eines Arbeitspakets, ohne es zu verschieben (Issue #1300). Ein eigener
   * Endpunkt statt eines Felds am {@code PATCH}: Der Status hängt an {@code CARD_MOVE}, das Patch
   * an {@code TICKET_UPDATE} — zwei Rechte in einem Endpunkt öffneten still zu weit (Plan #1294,
   * E9).
   */
  @PutMapping("/api/cards/{cardId}/status")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void setStatus(
      @AuthenticationPrincipal Long userId,
      @PathVariable long cardId,
      @Valid @RequestBody SetStatusRequest request) {
    cards.setStatus(userId, cardId, request.status());
  }

  /**
   * Ordnet die aktiven Karten einer Spalte nach Kartennummer. Die Richtung kommt bei jedem Aufruf
   * mit — das Backend merkt sich keinen Toggle-Zustand.
   */
  @PostMapping("/api/columns/{columnId}/cards/sort-by-number")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void sortByNumber(
      @AuthenticationPrincipal Long userId,
      @PathVariable long columnId,
      @Valid @RequestBody SortByNumberRequest request) {
    cards.sortColumnByNumber(userId, columnId, request.direction());
  }

  @PostMapping("/api/cards/{cardId}/transfer")
  CardView transfer(
      @AuthenticationPrincipal Long userId,
      @PathVariable long cardId,
      @Valid @RequestBody TransferCardRequest request) {
    return cards.transfer(userId, cardId, request.targetBoardId(), request.targetColumnId());
  }

  /** Verschiebt mehrere Karten in einer Transaktion auf ein anderes Board (alles-oder-nichts). */
  @PostMapping("/api/cards/bulk-transfer")
  List<CardView> bulkTransfer(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody BulkTransferRequest request) {
    return cards.bulkTransfer(
        userId, request.cardIds(), request.targetBoardId(), request.targetColumnId());
  }

  /**
   * Setzt ein Label an mehreren Karten oder nimmt es ihnen ab, in einer Transaktion
   * (alles-oder-nichts). Die übrigen Labels jeder Karte bleiben unberührt.
   */
  @PostMapping("/api/cards/bulk-labels")
  List<CardView> bulkLabels(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody BulkLabelsRequest request) {
    return cards.bulkLabels(userId, request.cardIds(), request.labelId(), request.action());
  }

  /** Ersetzt die Zuständigen der Karte (leere/fehlende Liste = keine Zuständigen). */
  @PutMapping("/api/cards/{cardId}/assignees")
  CardView setAssignees(
      @AuthenticationPrincipal Long userId,
      @PathVariable long cardId,
      @RequestBody AssigneesRequest request) {
    List<Long> ids = request.assignees() == null ? List.of() : request.assignees();
    return cards.setAssignees(userId, cardId, ids);
  }

  /** Ersetzt die Labels der Karte (leere/fehlende Liste = keine Labels). */
  @PutMapping("/api/cards/{cardId}/labels")
  CardView setLabels(
      @AuthenticationPrincipal Long userId,
      @PathVariable long cardId,
      @RequestBody LabelsRequest request) {
    List<Long> ids = request.labels() == null ? List.of() : request.labels();
    return cards.setLabels(userId, cardId, ids);
  }

  /** Aktivitätsverlauf einer Karte (chronologisch, Leserecht wie Board-Ansicht). */
  @GetMapping("/api/cards/{cardId}/activity")
  List<ActivityView> activity(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
    return cards.listActivityViews(userId, cardId).stream()
        .map(CardController::activityView)
        .toList();
  }

  // Die Domänen-Abbildung (Aufzählungen -> Namen) liegt seit #876 in der Fassade; hier bleibt die
  // reine Umhüllung in die Web-Form, damit die Antwortform unverändert an dieser Stelle steht.
  private static ActivityView activityView(CardService.ActivityView a) {
    return new ActivityView(
        a.id(),
        a.actorUserId(),
        a.type(),
        a.detail(),
        a.createdAt(),
        a.origin(),
        a.tokenName(),
        a.agent());
  }

  // Nur `title` ist Pflicht. Die übrigen Felder sind optional: Jackson lässt sie bei fehlendem
  // JSON-Feld `null`, weshalb sie unter @NullMarked als @Nullable deklariert sein müssen (sonst
  // hält der Nullness-Dataflow die Null-Prüfungen in `create` für tot — java:S2583).
  record CreateCardRequest(
      @Nullable Long columnId,
      @NotBlank @Size(max = 300) String title,
      @Nullable @Size(max = TextLimits.MAX_TEXT) String description,
      @Nullable List<Integer> dependencies,
      @Nullable CardType type,
      @Nullable Long parentId,
      @Nullable @Size(max = 16) String shortcode,
      @Nullable Instant dueDate,
      @Nullable List<Long> assigneeIds,
      @Nullable List<Long> labelIds,
      @Nullable @Positive @Max(CardNumbers.MAX) Integer derivedFrom) {}

  /**
   * Ein Element des Stapels (Issue #1200). Titel- und Beschreibungsgrenze wie am anderen Anlegeweg
   * ({@link CreateCardRequest}).
   */
  record BatchCardItem(
      @NotBlank @Size(max = 300) String title,
      @Nullable @Size(max = TextLimits.MAX_TEXT) String description) {}

  /**
   * Eine leere Liste ist eine Fehleingabe und keine leere Erfolgsantwort ({@code @NotEmpty} → 400)
   * — dasselbe Verhalten wie bei den bestehenden Bulk-Endpunkten. Die {@code columnId} gilt für
   * alle Elemente: Der Stapel füllt genau eine Spalte.
   */
  record CreateCardsBatchRequest(
      @NotNull Long columnId,
      @NotEmpty @Size(max = MAX_CARDS_PER_BATCH) List<@Valid @NotNull BatchCardItem> cards) {}

  record UpdateCardRequest(
      @NotBlank @Size(max = 300) String title,
      @Size(max = TextLimits.MAX_TEXT) String description,
      List<Integer> dependencies,
      @Size(max = 16) String shortcode,
      Long parentId,
      @Nullable Instant dueDate) {}

  record MoveCardRequest(
      @NotNull Long columnId, @jakarta.validation.constraints.PositiveOrZero int position) {}

  record SetStatusRequest(@NotBlank String status) {}

  record TransferCardRequest(@NotNull Long targetBoardId, @NotNull Long targetColumnId) {}

  record SortByNumberRequest(@NotNull SortDirection direction) {}

  record BulkTransferRequest(
      @NotEmpty @Size(max = 200) List<Long> cardIds,
      @NotNull Long targetBoardId,
      @NotNull Long targetColumnId) {}

  record BulkLabelsRequest(
      @NotEmpty @Size(max = 200) List<Long> cardIds,
      @NotNull Long labelId,
      @NotNull LabelAction action) {}

  record AssigneesRequest(@Nullable List<Long> assignees) {}

  record LabelsRequest(@Nullable List<Long> labels) {}

  /**
   * Ein Eintrag des Aktivitätsverlaufs. Die Felder {@code origin} und {@code tokenName} sind
   * serverseitig verifiziert, {@code agent} ist dagegen eine Selbstauskunft des Clients — siehe
   * Issue #517. Bei Alt-Einträgen aus der Zeit vor Migration V23 sind alle drei Felder null.
   */
  record ActivityView(
      @Nullable Long id,
      @Nullable Long actorUserId,
      String type,
      String detail,
      Instant createdAt,
      @Nullable String origin,
      @Nullable String tokenName,
      @Nullable String agent) {}
}
