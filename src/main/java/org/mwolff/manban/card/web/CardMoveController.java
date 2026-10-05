package org.mwolff.manban.card.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.mwolff.manban.card.application.CardMoveService;
import org.mwolff.manban.card.application.CardView;
import org.mwolff.manban.card.application.SortDirection;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verschieben, Status und Umzug (Issue #1395, Plan #1387 E12): Karten innerhalb eines Boards
 * verschieben, den Status eines Arbeitspakets setzen, eine Spalte nach Kartennummer ordnen und
 * Karten einzeln oder gesammelt auf ein anderes Board umziehen. Pfade, HTTP-Methoden, Rechte und
 * Antworten sind unverändert die aus {@link CardController}; delegiert wird an den {@link
 * CardMoveService}.
 */
@RestController
class CardMoveController {

  private final CardMoveService moveService;

  CardMoveController(CardMoveService moveService) {
    this.moveService = moveService;
  }

  @PostMapping("/api/cards/{cardId}/move")
  CardView move(
      @AuthenticationPrincipal Long userId,
      @PathVariable long cardId,
      @Valid @RequestBody MoveCardRequest request) {
    return moveService.move(userId, cardId, request.columnId(), request.position());
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
    moveService.setStatus(userId, cardId, request.status());
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
    moveService.sortColumnByNumber(userId, columnId, request.direction());
  }

  @PostMapping("/api/cards/{cardId}/transfer")
  CardView transfer(
      @AuthenticationPrincipal Long userId,
      @PathVariable long cardId,
      @Valid @RequestBody TransferCardRequest request) {
    return moveService.transfer(userId, cardId, request.targetBoardId(), request.targetColumnId());
  }

  /** Verschiebt mehrere Karten in einer Transaktion auf ein anderes Board (alles-oder-nichts). */
  @PostMapping("/api/cards/bulk-transfer")
  List<CardView> bulkTransfer(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody BulkTransferRequest request) {
    return moveService.bulkTransfer(
        userId, request.cardIds(), request.targetBoardId(), request.targetColumnId());
  }

  record MoveCardRequest(
      @NotNull Long columnId, @jakarta.validation.constraints.PositiveOrZero int position) {}

  record SetStatusRequest(@NotBlank String status) {}

  record TransferCardRequest(@NotNull Long targetBoardId, @NotNull Long targetColumnId) {}

  record SortByNumberRequest(@NotNull SortDirection direction) {}

  record BulkTransferRequest(
      @NotEmpty @Size(max = 200) List<Long> cardIds,
      @NotNull Long targetBoardId,
      @NotNull Long targetColumnId) {}
}
