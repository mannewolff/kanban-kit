package org.mwolff.manban.board.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Spalten-Verwaltung eines Boards (anlegen, bearbeiten, umsortieren, löschen). */
@RestController
class ColumnController {

  private final BoardService boards;

  ColumnController(BoardService boards) {
    this.boards = boards;
  }

  @PostMapping("/api/boards/{boardId}/columns")
  @ResponseStatus(HttpStatus.CREATED)
  ColumnView add(
      @AuthenticationPrincipal Long userId,
      @PathVariable long boardId,
      @Valid @RequestBody ColumnRequest request) {
    return boards.addColumn(userId, boardId, request.name(), request.wipLimit());
  }

  @PatchMapping("/api/columns/{columnId}")
  ColumnView update(
      @AuthenticationPrincipal Long userId,
      @PathVariable long columnId,
      @Valid @RequestBody ColumnRequest request) {
    return boards.updateColumn(userId, columnId, request.name(), request.wipLimit());
  }

  @DeleteMapping("/api/columns/{columnId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@AuthenticationPrincipal Long userId, @PathVariable long columnId) {
    boards.deleteColumn(userId, columnId);
  }

  @PutMapping("/api/boards/{boardId}/columns/order")
  List<ColumnView> reorder(
      @AuthenticationPrincipal Long userId,
      @PathVariable long boardId,
      @Valid @RequestBody ReorderRequest request) {
    return boards.reorderColumns(userId, boardId, request.columnIds());
  }

  record ColumnRequest(@NotBlank @Size(max = 120) String name, @Positive Integer wipLimit) {}

  record ReorderRequest(@NotEmpty List<Long> columnIds) {}
}
