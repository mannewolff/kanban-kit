package org.mwolff.manban.comment.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.mwolff.manban.comment.application.CommentService;
import org.mwolff.manban.comment.application.CommentService.CommentView;
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

/** Kommentare an Karten. */
@RestController
class CommentController {

  private final CommentService comments;

  CommentController(CommentService comments) {
    this.comments = comments;
  }

  @PostMapping("/api/cards/{cardId}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  CommentView create(
      @AuthenticationPrincipal Long userId,
      @PathVariable long cardId,
      @Valid @RequestBody CommentRequest request) {
    return comments.create(userId, cardId, request.body());
  }

  @GetMapping("/api/cards/{cardId}/comments")
  List<CommentView> list(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
    return comments.list(userId, cardId);
  }

  @PatchMapping("/api/comments/{commentId}")
  CommentView update(
      @AuthenticationPrincipal Long userId,
      @PathVariable long commentId,
      @Valid @RequestBody CommentRequest request) {
    return comments.update(userId, commentId, request.body());
  }

  @DeleteMapping("/api/comments/{commentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@AuthenticationPrincipal Long userId, @PathVariable long commentId) {
    comments.delete(userId, commentId);
  }

  record CommentRequest(@NotBlank @Size(max = 10_000) String body) {}
}
