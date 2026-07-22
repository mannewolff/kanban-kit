package org.mwolff.manban.card.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.CardService.CardView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Projektweiter Ideen-Pool: board-lose Ideen eines Projekts auflisten und anlegen. Session-Auth
 * erforderlich; Rechte prüft der {@link CardService} (Mitglied bzw. {@code TICKET_CREATE}).
 */
@RestController
class ProjectIdeaController {

  private final CardService cards;

  ProjectIdeaController(CardService cards) {
    this.cards = cards;
  }

  @GetMapping("/api/projects/{projectId}/ideas")
  List<CardView> list(@AuthenticationPrincipal Long userId, @PathVariable long projectId) {
    return cards.listProjectIdeas(userId, projectId);
  }

  @PostMapping("/api/projects/{projectId}/ideas")
  @ResponseStatus(HttpStatus.CREATED)
  CardView create(
      @AuthenticationPrincipal Long userId,
      @PathVariable long projectId,
      @Valid @RequestBody CreateIdeaRequest request) {
    return cards.createProjectIdea(
        userId, projectId, request.title(), request.description(), request.targetBoardId());
  }

  record CreateIdeaRequest(
      @NotBlank @Size(max = 300) String title,
      @Nullable String description,
      @Nullable Long targetBoardId) {}
}
