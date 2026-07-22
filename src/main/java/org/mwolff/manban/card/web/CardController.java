package org.mwolff.manban.card.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.ColumnNotFoundException;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.CardService.CardView;
import org.mwolff.manban.card.application.CardService.EpicView;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Karten- und Epic-Verwaltung eines Boards (Anlegen, Bearbeiten, Zuordnen, Archivieren, Löschen).
 */
@RestController
class CardController {

  private final CardService cards;

  CardController(CardService cards) {
    this.cards = cards;
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
      return cards.createEpic(
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
        Boolean.TRUE.equals(request.ideaStored()),
        request.dueDate(),
        request.assigneeIds(),
        request.labelIds());
  }

  @GetMapping("/api/boards/{boardId}/cards")
  List<CardView> list(@AuthenticationPrincipal Long userId, @PathVariable long boardId) {
    return cards.listByBoard(userId, boardId);
  }

  @GetMapping("/api/boards/{boardId}/epics")
  List<EpicView> epics(@AuthenticationPrincipal Long userId, @PathVariable long boardId) {
    return cards.listEpics(userId, boardId);
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

  /**
   * Ordnet eine Karte einem Epic zu ({@code parentId}) oder löst die Zuordnung ({@code parentId:
   * null}).
   */
  @PatchMapping("/api/cards/{cardId}/parent")
  CardView assignParent(
      @AuthenticationPrincipal Long userId,
      @PathVariable long cardId,
      @RequestBody AssignParentRequest request) {
    return cards.assignParent(userId, cardId, request.parentId());
  }

  @PostMapping("/api/cards/{cardId}/move")
  CardView move(
      @AuthenticationPrincipal Long userId,
      @PathVariable long cardId,
      @Valid @RequestBody MoveCardRequest request) {
    return cards.move(userId, cardId, request.columnId(), request.position());
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

  @PostMapping("/api/cards/{cardId}/archive")
  CardView archive(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
    return cards.archive(userId, cardId);
  }

  /** Archiviert mehrere Karten in einer Transaktion (alles-oder-nichts). */
  @PostMapping("/api/cards/bulk-archive")
  List<CardView> bulkArchive(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody BulkArchiveRequest request) {
    return cards.bulkArchive(userId, request.cardIds());
  }

  @PostMapping("/api/cards/{cardId}/restore")
  CardView restore(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
    return cards.restore(userId, cardId);
  }

  /** Legt eine Karte in den Ideen-Speicher (Demotion, verschwindet aus dem aktiven Board). */
  @PostMapping("/api/cards/{cardId}/idea-storage")
  CardView moveToIdeaStorage(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
    return cards.moveToIdeaStorage(userId, cardId);
  }

  /** Holt eine Idee aus dem Ideen-Speicher zurück ins Backlog (Promotion). */
  @PostMapping("/api/cards/{cardId}/promote")
  CardView promote(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
    return cards.promoteToBacklog(userId, cardId);
  }

  /** Plant eine board-lose Pool-Idee ins Backlog eines Boards desselben Projekts ein. */
  @PutMapping("/api/cards/{cardId}/plan")
  CardView plan(
      @AuthenticationPrincipal Long userId,
      @PathVariable long cardId,
      @Valid @RequestBody PlanRequest request) {
    return cards.planOntoBoard(userId, cardId, request.targetBoardId());
  }

  /** Holt eine board-gebundene Karte zurück in den projektweiten Ideen-Pool (board-los). */
  @PutMapping("/api/cards/{cardId}/to-pool")
  CardView toPool(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
    return cards.moveBackToPool(userId, cardId);
  }

  /** Verschiebt eine Karte in den Papierkorb (Soft-Delete, reversibel). */
  @DeleteMapping("/api/cards/{cardId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
    cards.delete(userId, cardId);
  }

  /** Verschiebt mehrere Karten in einer Transaktion in den Papierkorb (alles-oder-nichts). */
  @PostMapping("/api/cards/bulk-delete")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void bulkDelete(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody BulkDeleteRequest request) {
    cards.bulkDelete(userId, request.cardIds());
  }

  /** Papierkorb eines Boards. */
  @GetMapping("/api/boards/{boardId}/trash")
  List<CardView> trash(@AuthenticationPrincipal Long userId, @PathVariable long boardId) {
    return cards.listTrash(userId, boardId);
  }

  /** Holt eine Karte aus dem Papierkorb zurück. */
  @PostMapping("/api/cards/{cardId}/restore-deleted")
  CardView restoreDeleted(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
    return cards.restoreFromTrash(userId, cardId);
  }

  /** Entfernt eine Karte endgültig (nur Projekt-Admin/Owner). */
  @DeleteMapping("/api/cards/{cardId}/purge")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void purge(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
    cards.purge(userId, cardId);
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
    return cards.listActivity(userId, cardId).stream().map(CardController::activityView).toList();
  }

  private static ActivityView activityView(CardActivity a) {
    return new ActivityView(a.id(), a.actorUserId(), a.type().name(), a.detail(), a.createdAt());
  }

  // Nur `title` ist Pflicht. Die übrigen Felder sind optional: Jackson lässt sie bei fehlendem
  // JSON-Feld `null`, weshalb sie unter @NullMarked als @Nullable deklariert sein müssen (sonst
  // hält der Nullness-Dataflow die Null-Prüfungen in `create` für tot — java:S2583).
  record CreateCardRequest(
      @Nullable Long columnId,
      @NotBlank @Size(max = 300) String title,
      @Nullable String description,
      @Nullable List<Integer> dependencies,
      @Nullable CardType type,
      @Nullable Long parentId,
      @Nullable @Size(max = 16) String shortcode,
      @Nullable Boolean ideaStored,
      @Nullable Instant dueDate,
      @Nullable List<Long> assigneeIds,
      @Nullable List<Long> labelIds) {}

  record UpdateCardRequest(
      @NotBlank @Size(max = 300) String title,
      String description,
      List<Integer> dependencies,
      @Size(max = 16) String shortcode,
      Long parentId,
      @Nullable Instant dueDate) {}

  record AssignParentRequest(Long parentId) {}

  record MoveCardRequest(
      @NotNull Long columnId, @jakarta.validation.constraints.PositiveOrZero int position) {}

  record TransferCardRequest(@NotNull Long targetBoardId, @NotNull Long targetColumnId) {}

  record PlanRequest(@NotNull Long targetBoardId) {}

  record BulkArchiveRequest(@NotEmpty @Size(max = 200) List<Long> cardIds) {}

  record BulkDeleteRequest(@NotEmpty @Size(max = 200) List<Long> cardIds) {}

  record BulkTransferRequest(
      @NotEmpty @Size(max = 200) List<Long> cardIds,
      @NotNull Long targetBoardId,
      @NotNull Long targetColumnId) {}

  record AssigneesRequest(@Nullable List<Long> assignees) {}

  record LabelsRequest(@Nullable List<Long> labels) {}

  record ActivityView(
      @Nullable Long id,
      @Nullable Long actorUserId,
      String type,
      String detail,
      Instant createdAt) {}
}
