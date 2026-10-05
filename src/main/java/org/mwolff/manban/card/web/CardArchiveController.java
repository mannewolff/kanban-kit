package org.mwolff.manban.card.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.mwolff.manban.card.application.CardArchiveService;
import org.mwolff.manban.card.application.CardView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Archiv und Papierkorb (Issue #1394, Plan #1387 E12): Karten archivieren und wiederherstellen,
 * einzeln und gesammelt in den Papierkorb legen, daraus zurückholen, endgültig entfernen und den
 * Papierkorb eines Boards auflisten. Pfade, HTTP-Methoden, Rechte und Antworten sind unverändert
 * die aus {@link CardController}; delegiert wird an den {@link CardArchiveService}.
 */
@RestController
class CardArchiveController {

  private final CardArchiveService archiveService;

  CardArchiveController(CardArchiveService archiveService) {
    this.archiveService = archiveService;
  }

  @PostMapping("/api/cards/{cardId}/archive")
  CardView archive(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
    return archiveService.archive(userId, cardId);
  }

  /** Archiviert mehrere Karten in einer Transaktion (alles-oder-nichts). */
  @PostMapping("/api/cards/bulk-archive")
  List<CardView> bulkArchive(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody BulkArchiveRequest request) {
    return archiveService.bulkArchive(userId, request.cardIds());
  }

  @PostMapping("/api/cards/{cardId}/restore")
  CardView restore(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
    return archiveService.restore(userId, cardId);
  }

  /** Verschiebt eine Karte in den Papierkorb (Soft-Delete, reversibel). */
  @DeleteMapping("/api/cards/{cardId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
    archiveService.delete(userId, cardId);
  }

  /** Verschiebt mehrere Karten in einer Transaktion in den Papierkorb (alles-oder-nichts). */
  @PostMapping("/api/cards/bulk-delete")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void bulkDelete(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody BulkDeleteRequest request) {
    archiveService.bulkDelete(userId, request.cardIds());
  }

  /** Papierkorb eines Boards. */
  @GetMapping("/api/boards/{boardId}/trash")
  List<CardView> trash(@AuthenticationPrincipal Long userId, @PathVariable long boardId) {
    return archiveService.listTrash(userId, boardId);
  }

  /** Holt eine Karte aus dem Papierkorb zurück. */
  @PostMapping("/api/cards/{cardId}/restore-deleted")
  CardView restoreDeleted(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
    return archiveService.restoreFromTrash(userId, cardId);
  }

  /** Entfernt eine Karte endgültig (nur Projekt-Admin/Owner). */
  @DeleteMapping("/api/cards/{cardId}/purge")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void purge(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
    archiveService.purge(userId, cardId);
  }

  record BulkArchiveRequest(@NotEmpty @Size(max = 200) List<Long> cardIds) {}

  record BulkDeleteRequest(@NotEmpty @Size(max = 200) List<Long> cardIds) {}
}
