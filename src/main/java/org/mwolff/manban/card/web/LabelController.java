package org.mwolff.manban.card.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.mwolff.manban.card.application.LabelService;
import org.mwolff.manban.card.domain.Label;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Verwaltung der board-scoped Labels (Anlegen/Umbenennen/Farbe/Löschen; Auflisten je Mitglied). */
@RestController
class LabelController {

  private final LabelService labels;

  LabelController(LabelService labels) {
    this.labels = labels;
  }

  @GetMapping("/api/boards/{boardId}/labels")
  List<LabelView> list(@AuthenticationPrincipal Long userId, @PathVariable long boardId) {
    return labels.list(userId, boardId).stream().map(LabelController::view).toList();
  }

  @PostMapping("/api/boards/{boardId}/labels")
  @ResponseStatus(HttpStatus.CREATED)
  LabelView create(
      @AuthenticationPrincipal Long userId,
      @PathVariable long boardId,
      @Valid @RequestBody LabelRequest request) {
    return view(labels.create(userId, boardId, request.name(), request.color()));
  }

  @PatchMapping("/api/labels/{labelId}")
  LabelView update(
      @AuthenticationPrincipal Long userId,
      @PathVariable long labelId,
      @Valid @RequestBody LabelRequest request) {
    return view(labels.update(userId, labelId, request.name(), request.color()));
  }

  @DeleteMapping("/api/labels/{labelId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@AuthenticationPrincipal Long userId, @PathVariable long labelId) {
    labels.delete(userId, labelId);
  }

  private static LabelView view(Label l) {
    return new LabelView(l.requireId(), l.boardId(), l.name(), l.color());
  }

  record LabelView(long id, long boardId, String name, String color) {}

  record LabelRequest(
      @NotBlank @Size(max = 60) String name, @NotBlank @Size(max = 20) String color) {}
}
